package at.msd.friehs_bicha.cdcsvparser.ui.compose

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure (JVM-testable) helpers for the overview chart panel: parse the raw
 * "series;YYYY-MM-DD;value" lines produced by the C++ core
 * (TransactionManager::getDailySeries) and bucket the daily points into the
 * user-selected time frame. No Android imports on purpose.
 */

/** The four daily series the core provides. */
enum class ChartSeries(
    val rawKey: String,
    /**
     * Flow series (money spent) are summed per bucket; stock series (assets,
     * P/L, rewards) use the last daily point inside the bucket.
     */
    val isFlow: Boolean,
) {
    SPENT("spent", true),
    VALUE("value", false),
    PL("pl", false),
    BONUS("bonus", false);

    companion object {
        val fromRaw: (String) -> ChartSeries? = { key ->
            entries.firstOrNull { it.rawKey == key }
        }
    }
}

/** User-selectable time frames for the overview charts. */
enum class TimeFrame { WEEK, MONTH, QUARTER, YEAR }

/** One daily point of a series (already EUR-valued where applicable). */
data class DailyPoint(val date: LocalDate, val value: Double)

/** One bucketed point for the chart ([key] feeds [bucketLabel]). */
data class ChartPoint(val key: String, val value: Double)

/**
 * Parses the raw core lines into per-series daily points, in the order the
 * core produced them (one ascending daily block per series). Malformed lines
 * are skipped, not fatal.
 */
fun parseDailySeries(rows: List<String>): Map<ChartSeries, List<DailyPoint>> {
    val result = LinkedHashMap<ChartSeries, MutableList<DailyPoint>>()
    for (row in rows) {
        val parts = row.split(';', limit = 3)
        if (parts.size != 3) continue
        val series = ChartSeries.fromRaw(parts[0]) ?: continue
        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: continue
        val value = parts[2].toDoubleOrNull() ?: continue
        result.getOrPut(series) { mutableListOf() }.add(DailyPoint(date, value))
    }
    return result
}

/**
 * Buckets daily [points] (in ascending date order, as the core emits them)
 * into [timeFrame] buckets. Flow series sum per bucket; stock series take
 * the last day of the bucket. Buckets appear in first-occurrence order,
 * which is chronological for ascending input.
 */
fun bucketize(
    points: List<DailyPoint>,
    timeFrame: TimeFrame,
    isFlow: Boolean,
): List<ChartPoint> {
    val buckets = LinkedHashMap<String, Double>()
    for (point in points) {
        val key = bucketKey(point.date, timeFrame)
        buckets[key] = if (isFlow) (buckets[key] ?: 0.0) + point.value else point.value
    }
    return buckets.map { ChartPoint(it.key, it.value) }
}

/** Fixed-format bucket key (see [bucketLabel] for the display form). */
fun bucketKey(date: LocalDate, timeFrame: TimeFrame): String = when (timeFrame) {
    TimeFrame.WEEK -> {
        // java.time.YearWeek is not on Android: ISO-8601 week derived by
        // hand - the week is owned by its Thursday, whose ordinal day in
        // its year gives the week number. `4 - dow` is already in the
        // range [-3, +3] (Mon..Sun), i.e. the offset to the OWNING
        // Thursday; no modulo needed (reducing mod 7 would map Fri/Sat/Sun
        // to offsets +6/+5/+4 = NEXT week's Thursday).
        val thursday = date.plusDays((4 - date.dayOfWeek.value).toLong())
        "%04d-W%02d".format(thursday.year, (thursday.dayOfYear - 1) / 7 + 1)
    }

    TimeFrame.MONTH -> {
        val ym = YearMonth.from(date)
        "%04d-%02d".format(ym.year, ym.monthValue)
    }

    TimeFrame.QUARTER -> {
        // java.time.YearQuarter is Java 21+ and not on Android - derive it.
        "%04d-Q%d".format(date.year, (date.monthValue - 1) / 3 + 1)
    }

    TimeFrame.YEAR -> "%04d".format(date.year)
}

/** One line of the core's per-wallet daily series (G36): running token
 *  `balance` / `bonus` of one currency after one active day. */
data class WalletDay(val date: LocalDate, val balance: Double, val bonus: Double)

/**
 * Parses the core's `getDailyWalletSeries` lines ("CUR;YYYY-MM-DD;
 * balance;bonus", ascending per currency) into per-currency points.
 * Malformed lines are skipped, not fatal.
 */
fun parseWalletSeries(rows: List<String>): Map<String, List<WalletDay>> {
    val out = LinkedHashMap<String, MutableList<WalletDay>>()
    for (row in rows) {
        val parts = row.split(';', limit = 4)
        if (parts.size != 4) continue
        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: continue
        val balance = parts[2].toDoubleOrNull() ?: continue
        val bonus = parts[3].toDoubleOrNull() ?: continue
        out.getOrPut(parts[0]) { mutableListOf() }.add(WalletDay(date, balance, bonus))
    }
    return out
}

/**
 * Ascending daily EUR prices of one symbol ((utcEpochDay, price), one point
 * per calendar day, as [CryptoPricesCryptoCompare.getHistory] yields them).
 */
class PriceSeries(val points: List<Pair<Long, Double>>) {

    /**
     * Price applicable to [day]: the last point at or before it. When the
     * position existed BEFORE the first known data point, the first known
     * price is back-filled (0.0 would read as a dead position).
     * Null when the symbol has no prices at all.
     */
    fun priceAt(day: Long): Double? {
        if (points.isEmpty()) return null
        var lo = 0
        var hi = points.size - 1
        var last = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (points[mid].first <= day) { last = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (last >= 0) points[last].second else points.first().second
    }
}

/**
 * Historical stock series for the value / P/L / rewards chart buckets:
 * each calendar bucket is valued at the prices valid at its END date
 * ("the price of the time it is computed for"), the bucket containing
 * [today] additionally pinned to the live [currentPrices] (card parity),
 * with back-filling before a symbol's first data point.
 *
 * Buckets span the whole range from the first active day to [today]: days
 * without transactions carry the last running balance forward, so the
 * price movement is still visible through quiet periods.
 *
 * @param walletSeries per-currency running token balance / bonus (core's
 *        `getDailyWalletSeries`, ascending per currency)
 * @param prices per-currency daily EUR price history (may be partial while
 *        the fetch pass is still running; symbols without a series
 *        contribute 0)
 * @param spentPoints the core's daily spent flow (the "spent" lines of the
 *        daily series)
 * @param frame the bucket frame
 * @param today the device's current day (pins the last bucket)
 * @return value, P/L and bonus bucket points, chronological
 */
fun historicalStockSeries(
    walletSeries: Map<String, List<WalletDay>>,
    prices: Map<String, PriceSeries>,
    spentPoints: List<DailyPoint>,
    frame: TimeFrame,
    today: LocalDate,
    currentPrices: Map<String, Double> = emptyMap(),
): Triple<List<ChartPoint>, List<ChartPoint>, List<ChartPoint>> {
    val valuePoints = mutableListOf<ChartPoint>()
    val plPoints = mutableListOf<ChartPoint>()
    val bonusPoints = mutableListOf<ChartPoint>()
    val allDates = walletSeries.values.flatten().map { it.date } + spentPoints.map { it.date }
    if (allDates.isEmpty()) return Triple(valuePoints, plPoints, bonusPoints)
    val firstDate = allDates.min()
    // The domain always spans first activity .. today: the bucket that
    // contains today is pinned to the LIVE prices (see below), which keeps
    // the chart's last point equal to the asset cards. (For exports that
    // ended long ago the tail is the constant final balance priced over
    // the last year of available history - the honest reading of "held
    // through today".)
    val pinDay = if (today >= firstDate) today else allDates.max()

    // Calendar end of the bucket containing [date].
    val bucketEndOf: (LocalDate) -> LocalDate = when (frame) {
        TimeFrame.WEEK -> { d ->
            // Sunday of the ISO week (the week is owned by its Thursday).
            d.plusDays(((7 - d.dayOfWeek.value) % 7).toLong())
        }

        TimeFrame.MONTH -> { d -> YearMonth.from(d).atEndOfMonth() }

        TimeFrame.QUARTER -> { d ->
            YearMonth.of(d.year, ((d.monthValue - 1) / 3) * 3 + 3).atEndOfMonth()
        }

        TimeFrame.YEAR -> { d -> LocalDate.of(d.year, 12, 31) }
    }

    // One entry per calendar bucket, first active bucket .. pinDay's bucket.
    val ends = mutableListOf(bucketEndOf(firstDate))
    val lastEnd = bucketEndOf(pinDay)
    while (ends.last() < lastEnd) {
        ends.add(bucketEndOf(ends.last().plusDays(1)))
    }

    // Spent flow grouped per bucket (the domain covers every bucket, so a
    // running sum over the bucket keys is the correct cumulative spent).
    val spentPerBucket = HashMap<String, Double>()
    for (point in spentPoints) {
        val key = bucketKey(point.date, frame)
        spentPerBucket[key] = (spentPerBucket[key] ?: 0.0) + point.value
    }

    // Per currency: one cursor into its ascending running-balance list.
    val cursor = HashMap<String, Int>().apply { walletSeries.keys.forEach { put(it, 0) } }
    var spentSoFar = 0.0
    for (end in ends) {
        val at = minOf(end, pinDay)   // the still-open bucket ends at today
        val key = bucketKey(at, frame)
        var value = 0.0
        var bonus = 0.0
        for ((cur, days) in walletSeries) {
            var i = cursor.getValue(cur)
            while (i < days.size && days[i].date <= at) i++
            cursor[cur] = i
            val last = days.getOrNull(i - 1) ?: continue   // no position yet
            val price = if (at == pinDay) {
                // The still-open bucket is valued with the live card prices
                // (card parity), falling back to the history for symbols
                // without a cached live price.
                currentPrices[cur] ?: prices[cur]?.priceAt(at.toEpochDay())
            } else {
                prices[cur]?.priceAt(at.toEpochDay())
            } ?: continue   // no history available for this symbol (yet)
            value += last.balance * price
            bonus += last.bonus * price
        }
        spentSoFar += spentPerBucket[key] ?: 0.0
        valuePoints.add(ChartPoint(key, value))
        plPoints.add(ChartPoint(key, value - spentSoFar))
        bonusPoints.add(ChartPoint(key, bonus))
    }
    return Triple(valuePoints, plPoints, bonusPoints)
}

/**
 * Compact, mostly locale-neutral display label for a bucket key ("MMM yy"
 * follows the device locale like the old monthly chart did).
 */
fun bucketLabel(key: String, timeFrame: TimeFrame): String = when (timeFrame) {
    TimeFrame.WEEK -> {
        val m = """(\d{4})-W(\d{2})""".toRegex().find(key) ?: return key
        "W${m.groupValues[2]} ${m.groupValues[1].substring(2)}"
    }

    TimeFrame.MONTH -> runCatching {
        YearMonth.parse(key)
            .format(DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()))
    }.getOrDefault(key)

    TimeFrame.QUARTER -> {
        val m = """(\d{4})-Q(\d)""".toRegex().find(key) ?: return key
        "Q${m.groupValues[2]} ${m.groupValues[1].substring(2)}"
    }

    TimeFrame.YEAR -> key
}
