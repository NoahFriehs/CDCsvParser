package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.ui.compose.PriceSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.WalletDay
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletSeries
import java.time.LocalDate
import kotlin.math.abs

/**
 * The delta basis of the Main summary chip: the previous point the
 * headline is compared against. No Android imports on purpose (JVM-tested).
 */
enum class DeltaBasis(val days: Long) {
    /** The 24 h basis (the default). */
    DAY(1),

    /** The "30 days ago" basis (the optional setting). */
    THIRTY_DAYS(30),
}

/**
 * The resolved state of the Main summary hero block: the headline figure
 * and the point-in-time delta chip.
 *
 * Pure (JVM-tested) result of [SummaryDelta.Companion.deltaChipSnapshot].
 * The chip is self-describing: the active [basis] is part of its label, so
 * the rendered number always says what it measures.
 *
 * @property headlineEur the headline figure in EUR: the core's NET WORTH
 *   when the sheet carries any entry, and the (degraded) tracked-assets
 *   figure when it does not (the caller then shows the add-liabilities
 *   hint row).
 * @property isNetWorth true = the sheet is non-empty (the headline is a
 *   net worth); false = the degraded tracked-assets state.
 * @property deltaEur the headline's tracked part vs the [basis] point in
 *   EUR (signed); null = no honest delta can be shown (the chip renders
 *   "–", never a fabricated 0.0 %).
 * @property deltaPercent the change over the previous point in percent of
 *   |previous| (signed); null = [deltaEur] is null or the previous point
 *   is exactly 0.0 (a percent over zero is undefined, not zero).
 * @property basis the active comparison basis.
 */
data class SummaryDelta(
    val headlineEur: Double,
    val isNetWorth: Boolean,
    val deltaEur: Double?,
    val deltaPercent: Double?,
    val basis: DeltaBasis,
) {
    companion object {
        /**
         * The point-in-time delta builder of the Main summary hero. Inputs
         * are EXACTLY the same data the charts plot:
         * the core's unpriced per-wallet daily series (buffer-inclusive
         * scope, the EUR line in there) valued with the price valid at
         * each day - the chart's rule imported verbatim: the last close
         * at or before the day (first-close back-fill), the last day
         * additionally pinned at the live pass price (card parity), the
         * EUR buffer at face value.
         *
         * HONESTY RULE: the chip shows "–" (null) instead of a number
         * whenever the previous point cannot be FULLY priced - the basis
         * predates the series' first day, or a held (non-zero balance,
         * non-EUR) symbol has no price history at all. A partially priced
         * point would silently drop symbols out of the value and fake a
         * delta (the chart may do this, a single number must not).
         *
         * The delta is measured on the TRACKED series: the point-in-time
         * EUR value of every wallet incl. the buffer - the only part of
         * the net worth that moves with time. The headline itself (net
         * worth vs the degraded tracked figure) comes from the core's
         * snapshot line; the math is never re-computed here.
         *
         * @param walletSeries the core's per-wallet daily lines
         *   ("CURRENCY;YYYY-MM-DD;runningBalance;runningBonus")
         * @param history the deep-history cache: currency -> ascending
         *   (utcEpochDay, daily EUR close)
         * @param livePrices the pass's prices for the last day (card
         *   parity pin; EUR is valued at face regardless)
         * @param today the device's current day (pins the last point)
         * @param basis the active comparison basis
         * @param sheetLines the core's snapshot lines (the first line is
         *   the NW line; empty/malformed = no live state, null)
         *
         * @return the resolved hero state, or null when the snapshot line
         *   is absent/malformed (the caller hides the card).
         */
        fun deltaChipSnapshot(
            walletSeries: List<String>,
            history: Map<String, List<Pair<Long, Double>>>,
            livePrices: Map<String, Double>,
            today: LocalDate,
            basis: DeltaBasis,
            sheetLines: List<String>,
        ): SummaryDelta? {
            val snap = sheetLines.firstOrNull()?.let { NetWorthSnapshot.parse(it) }
                ?: return null
            val walletDays = parseWalletSeries(walletSeries)
            val series = history.mapValues { PriceSeries(it.value) }
            val dates = walletDays.values.flatten().map { it.date }
            val firstDate = dates.minOrNull() ?: return base(snap, basis, null, null)

            // The domain always spans first activity .. today (the last
            // day pins the live prices, card parity - the chart's rule);
            // a fully future-dated export ends at its last data day.
            val pinDay = if (today >= firstDate) today else dates.max()
            val prevDay = pinDay.minusDays(basis.days)

            var deltaEur: Double? = null
            var deltaPercent: Double? = null
            if (prevDay >= firstDate) {
                val prevBalances = balancesAtEnd(walletDays, prevDay)
                val lastBalances = balancesAtEnd(walletDays, pinDay)
                if (
                    fullyPriced(prevBalances, prevDay, false, series, livePrices) &&
                    fullyPriced(lastBalances, pinDay, true, series, livePrices)
                ) {
                    val valuePrev = valueAt(prevBalances, prevDay, false, series, livePrices)
                    val valueLast = valueAt(lastBalances, pinDay, true, series, livePrices)
                    deltaEur = valueLast - valuePrev
                    // A percent over an exactly zero previous value is
                    // undefined (not 0.0): the amount still stands, the
                    // percent renders "–".
                    deltaPercent = if (valuePrev != 0.0) {
                        deltaEur / abs(valuePrev) * 100.0
                    } else {
                        null
                    }
                }
            }
            return base(snap, basis, deltaEur, deltaPercent)
        }

        /**
         * The headline decision (pure): an EMPTY sheet (no liabilities, no
         * non-financial assets) cannot claim a net worth - it degrades to
         * the tracked-assets figure (the hint row is the caller's);
         * otherwise the core's net stands as the headline.
         */
        private fun base(
            snap: NetWorthSnapshot,
            basis: DeltaBasis,
            deltaEur: Double?,
            deltaPercent: Double?,
        ): SummaryDelta {
            val sheetEmpty = snap.nonFinancialEur == 0.0 && snap.liabilitiesEur == 0.0
            return SummaryDelta(
                headlineEur = if (sheetEmpty) snap.trackedAssetsEur else snap.netWorthEur,
                isNetWorth = !sheetEmpty,
                deltaEur = deltaEur,
                deltaPercent = deltaPercent,
                basis = basis,
            )
        }

        /**
         * The running balance per currency at the END of [day]: the last
         * emitted point at or before [day] (the series carries running
         * values, quiet days carry the last one forward); a currency
         * without such a point holds 0.0 (the core emits no zero state).
         */
        private fun balancesAtEnd(
            walletDays: Map<String, List<WalletDay>>,
            day: LocalDate,
        ): Map<String, Double> {
            val out = HashMap<String, Double>()
            for ((cur, days) in walletDays) {
                var balance = 0.0
                for (point in days) {
                    if (point.date > day) break
                    balance = point.balance
                }
                out[cur] = balance
            }
            return out
        }

        /**
         * The chart's price rule for ONE symbol at [day]: face value for
         * the EUR buffer; the last day additionally pinned at the live
         * pass price (fallback: the history close - the chart's pin-day
         * rule); any other day: the last history close at or before it
         * (first-close back-fill inside [PriceSeries.priceAt]).
         */
        private fun priceAt(
            cur: String,
            day: LocalDate,
            isPinDay: Boolean,
            series: Map<String, PriceSeries>,
            livePrices: Map<String, Double>,
        ): Double? {
            if (cur == "EUR") return 1.0
            if (isPinDay) livePrices[cur]?.let { return it }
            return series[cur]?.priceAt(day.toEpochDay())
        }

        /**
         * Every HELD non-EUR symbol at [day] (non-zero running balance)
         * must have a price source for that day: history for a plain day,
         * live OR history for the pin day (the chart's fallback).
         */
        private fun fullyPriced(
            balances: Map<String, Double>,
            day: LocalDate,
            isPinDay: Boolean,
            series: Map<String, PriceSeries>,
            livePrices: Map<String, Double>,
        ): Boolean {
            for ((cur, balance) in balances) {
                if (balance == 0.0 || cur == "EUR") continue
                val hasLive = livePrices[cur] != null
                val hasHistory = !series[cur]?.points.isNullOrEmpty()
                if (isPinDay) {
                    if (!hasLive && !hasHistory) return false
                } else if (!hasHistory) {
                    return false
                }
            }
            return true
        }

        /**
         * The point's EUR value: sum over the held balances at [day] with
         * the day's price (a symbol without a price contributes 0 - the
         * chart's "contributes 0" rule; [fullyPriced] is the guard that
         * keeps a partial value from ever reaching the chip).
         */
        private fun valueAt(
            balances: Map<String, Double>,
            day: LocalDate,
            isPinDay: Boolean,
            series: Map<String, PriceSeries>,
            livePrices: Map<String, Double>,
        ): Double {
            var value = 0.0
            for ((cur, balance) in balances) {
                if (balance == 0.0) continue
                priceAt(cur, day, isPinDay, series, livePrices)?.let { value += balance * it }
            }
            return value
        }
    }
}
