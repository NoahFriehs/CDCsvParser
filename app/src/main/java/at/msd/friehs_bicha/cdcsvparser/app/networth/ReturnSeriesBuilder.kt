package at.msd.friehs_bicha.cdcsvparser.app.networth

import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.PriceSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseDailySeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletSeries
import java.time.LocalDate
import java.util.Locale

/**
 * The pricing step of the return metric: values the core's UNPRICED
 * per-wallet daily series into the priced `R;<YYYY-MM-DD>;<V>;<CF>` lines
 * that the core's `computeReturns` consumes. The math (TWR, the MWR/IRR
 * bisection, the guards) is core-side; this builder only prices, and it
 * prices with the chart's rule imported verbatim (last close at or before
 * the day + first-close back-fill before a symbol's first data point +
 * the live pin of the last day from the pass's prices) - so the last V
 * equals the net-worth screen's live tracked-assets figure by
 * construction: same asset basis (every inner wallet INCLUDING the EUR
 * cash buffer, which is fiat and valued at face - no history, no fetch;
 * the bonus balance, which the tracked figure excludes, is excluded
 * here, too).
 *
 * CF_t is the day's net EXTERNAL EUR inflow: the day's spent flow (money
 * spent from outside the tracked basis) counts negative, each wallet's
 * balance delta counts at its flow-day price (fiat exact, the buffer at
 * face). An internal transfer (the fiat leg plus the token leg of one
 * flow) nets to ~0 by construction - it is not an external cash flow.
 *
 * Pure (no network, no core access): the price history arrives as passed
 * in (the deep-history chain cache), exactly like the chart values with.
 * One line per calendar day from the earliest known state to [today]
 * (quiet days carried forward: the price movement stays visible, and the
 * core's period math needs the unbroken day sequence).
 */
object ReturnSeriesBuilder {

    /** The built series: the core-ready lines + the window's first day
     * (the display's "since" label). */
    data class PricedSeries(val lines: List<String>, val firstDay: LocalDate?)

    /**
     * @param walletSeries the core's per-wallet daily lines
     *        ("CURRENCY;YYYY-MM-DD;runningBalance;runningBonus", the
     *        buffer-inclusive scope: the EUR line is in here)
     * @param dailyRows the core's 4-line daily series; only the
     *        "spent;Y-M-D;flow" lines are read (the day's external spent
     *        flow)
     * @param history the deep-history cache: currency -> ascending
     *        (utcEpochDay, daily EUR close)
     * @param livePrices the pass's prices for the last day (card parity
     *        pin; EUR is valued at face regardless)
     * @param today the device's current day (pins the last line)
     */
    fun build(
        walletSeries: List<String>,
        dailyRows: List<String>,
        history: Map<String, List<Pair<Long, Double>>>,
        livePrices: Map<String, Double>,
        today: LocalDate,
    ): PricedSeries {
        val walletDays = parseWalletSeries(walletSeries)
        val spentByDay = HashMap<LocalDate, Double>()
        for (point in parseDailySeries(dailyRows)[ChartSeries.SPENT].orEmpty()) {
            spentByDay[point.date] = (spentByDay[point.date] ?: 0.0) + point.value
        }
        val dates = walletDays.values.flatten().map { it.date } + spentByDay.keys
        if (dates.isEmpty()) return PricedSeries(emptyList(), null)
        val firstDate = dates.min()
        // The domain always spans first activity .. today (the last day
        // pins the live prices, card parity - the chart's rule); a fully
        // future-dated export ends at its last data day instead.
        val pinDay = if (today >= firstDate) today else dates.max()
        val priceSeries = history.mapValues { PriceSeries(it.value) }

        /** The chart's rule: last close at or before [day] (with the
         * first-close back-fill inside [PriceSeries.priceAt]); the last
         * day additionally pins the live pass price. The EUR buffer is
         * fiat: face value, no source of its own. */
        fun priceAt(cur: String, day: LocalDate): Double? = when {
            cur == "EUR" -> 1.0
            day == pinDay -> livePrices[cur] ?: priceSeries[cur]?.priceAt(day.toEpochDay())
            else -> priceSeries[cur]?.priceAt(day.toEpochDay())
        }

        val capacity = (pinDay.toEpochDay() - firstDate.toEpochDay() + 1).toInt()
        val lines = ArrayList<String>(capacity)
        // Per currency: the running balance at the END of the last
        // processed day (the series carries running values, not deltas) +
        // the cursor into its ascending day list.
        val lastBalance = HashMap<String, Double>()
        val cursor = HashMap<String, Int>()
        var day = firstDate
        while (day <= pinDay) {
            var cf = 0.0
            for ((cur, days) in walletDays) {
                var i = cursor[cur] ?: 0
                while (i < days.size && days[i].date <= day) {
                    // The point was emitted ON its date (the calendar
                    // cursor walks day by day): the flow is delta x the
                    // flow-day price. A currency without ANY price source
                    // contributes nothing - to the value AND to the flow
                    // (the measured basis excludes it, the chart's
                    // "contributes 0" rule).
                    val balance = days[i].balance
                    val previous = lastBalance[cur] ?: 0.0
                    priceAt(cur, days[i].date)?.let { p ->
                        if (balance != previous) cf += (balance - previous) * p
                    }
                    lastBalance[cur] = balance
                    i++
                }
                cursor[cur] = i
            }
            // The day's external money spent (outside basis -> negative).
            cf -= spentByDay[day] ?: 0.0
            var value = 0.0
            for ((cur, balance) in lastBalance) {
                val p = priceAt(cur, day) ?: continue
                value += balance * p
            }
            lines.add("R;" + day + ";" + format(value) + ";" + format(cf))
            day = day.plusDays(1)
        }
        return PricedSeries(lines, firstDate)
    }

    /** Six decimals - the house's std::to_string convention; the core's
     * strict double parser reads it back verbatim. */
    private fun format(v: Double): String = String.format(Locale.US, "%.6f", v)
}
