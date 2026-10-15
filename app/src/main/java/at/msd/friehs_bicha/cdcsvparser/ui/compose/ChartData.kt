package at.msd.friehs_bicha.cdcsvparser.ui.compose

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
// Order = chip order in the UI row (plan 009a: W/M/Q/J + T; plan 009b:
// + 6 h / 15 m, the window-limited intraday frames).
enum class TimeFrame { WEEK, MONTH, QUARTER, YEAR, DAY, HOURS_6, MINUTES_15 }

/** An intraday frame's data window (plan 009b scope decisions). */
data class IntradayFrame(val windowSeconds: Int, val windowDays: Int)

/** The intraday data window of an intraday frame; null for the daily ones. */
val TimeFrame.intradayFrame: IntradayFrame?
    get() = when (this) {
        TimeFrame.HOURS_6 -> IntradayFrame(6 * 3600, 30)
        TimeFrame.MINUTES_15 -> IntradayFrame(15 * 60, 7)
        else -> null
    }

/**
 * Preset display ranges for the overview chart (plan 006): each preset is
 * a start-only limit relative to today; a custom (user-picked) range is
 * applied separately via [limitRange] with both bounds set.
 */
enum class ChartRange {
    ALL,
    THREE_MONTHS,
    SIX_MONTHS,
    ONE_YEAR,
    TWO_YEARS,
    FIVE_YEARS;

    /** The oldest date still shown, relative to [today]; null = unlimited. */
    fun startDate(today: LocalDate): LocalDate? = when (this) {
        ALL -> null
        THREE_MONTHS -> today.minusMonths(3)
        SIX_MONTHS -> today.minusMonths(6)
        ONE_YEAR -> today.minusYears(1)
        TWO_YEARS -> today.minusYears(2)
        FIVE_YEARS -> today.minusYears(5)
    }
}

/** One daily point of a series (already EUR-valued where applicable). */
data class DailyPoint(val date: LocalDate, val value: Double)

/** One bucketed point for the chart ([key] feeds [bucketLabel]). */
data class ChartPoint(val key: String, val value: Double)

/**
 * The three stock-series bucket lists + the count of leading ESTIMATED
 * points (first-price back-fill before real history, see
 * [historicalStockSeries]); the bonus series never has an estimated count
 * (it rides the same pricing, so the count applies to value and P/L only).
 */
data class HistoricalSeries(
    val value: List<ChartPoint>,
    val pl: List<ChartPoint>,
    val bonus: List<ChartPoint>,
    val estimatedCount: Int,
)

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
    // Plan 009a: one bucket per calendar day (the core's native resolution).
    TimeFrame.DAY,
    // Plan 009b: intraday points already carry the core's window keys -
    // this arm only keeps the compiler happy (never bucketed).
    TimeFrame.HOURS_6,
    TimeFrame.MINUTES_15 -> date.toString()

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
 * @return the bucket points (value, P/L, bonus, chronological) plus
 *         [HistoricalSeries.estimatedCount] — the number of LEADING points
 *         priced by first-price back-fill (an estimate) rather than real
 *         history: a bucket is estimated when it ends before the earliest
 *         day all priced symbols have data (or when there is no history at
 *         all, except the live-price-pinned last bucket). The chart draws
 *         that region dashed.
 */
fun historicalStockSeries(
    walletSeries: Map<String, List<WalletDay>>,
    prices: Map<String, PriceSeries>,
    spentPoints: List<DailyPoint>,
    frame: TimeFrame,
    today: LocalDate,
    currentPrices: Map<String, Double> = emptyMap(),
): HistoricalSeries {
    val valuePoints = mutableListOf<ChartPoint>()
    val plPoints = mutableListOf<ChartPoint>()
    val bonusPoints = mutableListOf<ChartPoint>()
    val allDates = walletSeries.values.flatten().map { it.date } + spentPoints.map { it.date }
    if (allDates.isEmpty()) return HistoricalSeries(valuePoints, plPoints, bonusPoints, 0)
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

        // Plan 009a: a day bucket ends on the day itself.
        TimeFrame.DAY -> { d -> d }

        // The intraday frames (009b) never reach this daily mapper (they
        // use [historicalWindowState]); identity keeps the when exhaustive.
        TimeFrame.HOURS_6, TimeFrame.MINUTES_15 -> { d -> d }
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

    // Earliest day on which EVERY priced symbol has real history: the max
    // of each series' first point day. Buckets ending before it ride the
    // first-price back-fill (an estimate); with no history at all every
    // non-pined bucket is estimated.
    val realStartEpoch = prices.values
        .mapNotNull { s -> s.points.firstOrNull()?.first }
        .maxOrNull()

    // Per currency: one cursor into its ascending running-balance list.
    val cursor = HashMap<String, Int>().apply { walletSeries.keys.forEach { put(it, 0) } }
    var spentSoFar = 0.0
    var estimatedCount = 0
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
        // The live-price-pinned bucket is never estimated (it carries the
        // card prices); everything else before real history is an estimate.
        if (at != pinDay && (realStartEpoch == null || at.toEpochDay() < realStartEpoch)) estimatedCount++
        valuePoints.add(ChartPoint(key, value))
        plPoints.add(ChartPoint(key, value - spentSoFar))
        bonusPoints.add(ChartPoint(key, bonus))
    }
    return HistoricalSeries(valuePoints, plPoints, bonusPoints, estimatedCount)
}

/**
 * The first day of the [timeFrame] bucket with the given [key] - the
 * inverse of [bucketKey] (both use the hand-rolled ISO-week logic: the
 * week is owned by its Thursday). Null when the key is malformed.
 */
fun bucketStartDate(key: String, timeFrame: TimeFrame): LocalDate? = when (timeFrame) {
    // Plan 009a: the key IS the ISO date (inverse of [bucketKey]).
    TimeFrame.DAY -> runCatching { LocalDate.parse(key) }.getOrNull()

    // Plan 009b: the window start is embedded in the ISO date prefix.
    TimeFrame.HOURS_6, TimeFrame.MINUTES_15 ->
        runCatching { LocalDate.parse(key.substring(0, 10)) }.getOrNull()

    TimeFrame.WEEK -> {
        val m = """(\d{4})-W(\d{2})""".toRegex().find(key) ?: return null
        val year = m.groupValues[1].toInt()
        val week = m.groupValues[2].toInt()
        // ISO week 1 always contains Jan 4; its owning Thursday has a day
        // number in 1..7, hence week number (day-1)/7+1 == 1. The weekly
        // Thursdays of one ISO year therefore form a 7-day chain starting
        // at week 1's Thursday.
        val jan4 = LocalDate.of(year, 1, 4)
        val week1Thursday = jan4.plusDays((4 - jan4.dayOfWeek.value).toLong())
        week1Thursday
            .minusDays(week1Thursday.dayOfWeek.value - 1L) // Monday of week 1
            .plusWeeks((week - 1).toLong())
    }

    TimeFrame.MONTH -> runCatching { YearMonth.parse(key).atDay(1) }.getOrNull()

    TimeFrame.QUARTER -> {
        val m = """(\d{4})-Q(\d)""".toRegex().find(key) ?: return null
        val year = m.groupValues[1].toInt()
        val month = (m.groupValues[2].toInt() - 1) * 3 + 1
        runCatching { LocalDate.of(year, month, 1) }.getOrNull()
    }

    TimeFrame.YEAR -> runCatching { LocalDate.of(key.toInt(), 1, 1) }.getOrNull()
}

/**
 * Keeps the buckets STARTING on/after [start] and on/before [end] (either
 * side nullable; null + null returns the input unchanged, as the pair is
 * the identity). Returns the kept points plus the adjusted estimated count
 * (how many of the LEADING estimated points survived the filter - see
 * [historicalStockSeries]).
 */
fun limitRange(
    points: List<ChartPoint>,
    estimatedCount: Int,
    timeFrame: TimeFrame,
    start: LocalDate?,
    end: LocalDate? = null,
): Pair<List<ChartPoint>, Int> {
    if (start == null && end == null) return points to estimatedCount
    val keptIndexes = ArrayList<Int>(points.size)
    points.forEachIndexed { index, point ->
        val startOfBucket = bucketStartDate(point.key, timeFrame) ?: return@forEachIndexed
        val keep = (start == null || startOfBucket >= start) &&
            (end == null || startOfBucket <= end)
        if (keep) keptIndexes.add(index)
    }
    val kept = keptIndexes.map { points[it] }
    val keptEstimated = keptIndexes.count { it < estimatedCount }
    return kept to keptEstimated
}

/**
 * Compact, mostly locale-neutral display label for a bucket key ("MMM yy"
 * follows the device locale like the old monthly chart did).
 */
fun bucketLabel(key: String, timeFrame: TimeFrame): String = when (timeFrame) {
    // Plan 009a: the device-locale short date ("15.1.26" in de).
    TimeFrame.DAY -> runCatching {
        LocalDate.parse(key).format(
            // Android's java.time has no 2-arg ofLocalizedDate(style, locale).
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )
    }.getOrDefault(key)

    // Plan 009b intraday keys are the core's window starts ("YYYY-MM-DD
    // HH:MM"): the 6h frame shows the date + hour, the 15m frame the hour
    // (the dates sit far apart on that axis, the label count is low).
    TimeFrame.HOURS_6 -> runCatching {
        val (day, hour) = key.split(" ")
        "${day.takeLast(5)} $hour"
    }.getOrDefault(key)

    TimeFrame.MINUTES_15 -> runCatching {
        key.substringAfterLast(' ').substring(0, 5)
    }.getOrDefault(key)

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

// ------------------------------------------------------------------------
// Plan 009b: the intraday (sub-daily) series.

/** One core sub-daily wallet line: the RUNNING unpriced token/bonus
 *  position at the start of one fixed [window]. */
data class WalletWindow(val key: String, val balance: Double, val bonus: Double)

/**
 * Parses the core's `getSubDailyWalletSeries` lines ("CUR;YYYY-MM-DD
 * HH:MM;balance;bonus", oldest first per currency) into per-currency
 * window points. Malformed lines are skipped.
 */
fun parseWalletWindows(rows: List<String>): Map<String, List<WalletWindow>> {
    val out = LinkedHashMap<String, MutableList<WalletWindow>>()
    for (row in rows) {
        val parts = row.split(';', limit = 4)
        if (parts.size != 4) continue
        if (toEpochSecond(parts[1]) < 0) continue
        val balance = parts[2].toDoubleOrNull() ?: continue
        val bonus = parts[3].toDoubleOrNull() ?: continue
        if (!balance.isFinite() || !bonus.isFinite()) continue
        out.getOrPut(parts[0]) { mutableListOf() }.add(WalletWindow(parts[1], balance, bonus))
    }
    return out
}

/** Parses the core's `getSubDailySpentSeries` lines ("YYYY-MM-DD HH:MM;
 *  flow") into per-window spent points. */
fun parseSubDailySpent(rows: List<String>): List<ChartPoint> {
    val out = mutableListOf<ChartPoint>()
    for (row in rows) {
        val parts = row.split(';', limit = 2)
        if (parts.size != 2) continue
        if (toEpochSecond(parts[0]) < 0) continue
        val value = parts[1].toDoubleOrNull() ?: continue
        if (!value.isFinite()) continue
        out.add(ChartPoint(parts[0], value))
    }
    return out
}

/** The UTC epoch second of a sub-daily window start key ("YYYY-MM-DD
 *  HH:MM"), or -1 when malformed. */
fun toEpochSecond(key: String): Long {
    val m = """(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})""".toRegex().find(key) ?: return -1L
    val date = runCatching {
        LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }.getOrNull() ?: return -1L
    if (m.groupValues[4].toInt() > 23 || m.groupValues[5].toInt() > 59) return -1L
    return date.toEpochDay() * 86_400L + m.groupValues[4].toLong() * 3600L + m.groupValues[5].toLong() * 60L
}

/**
 * One symbol's intraday pricing on a frame's window grid (plan 009b):
 * (windowStartTsSec, price) pairs covering the whole frame window.
 * [firstRealIndex] is the first pair backed by a REAL intraday price
 * (not the daily-held-flat / live fallback) - the leading pairs form the
 * dashed estimated region, exactly like [historicalStockSeries].estimat
 * edCount does for the daily frames.
 */
data class IntradayPricing(
    val points: List<Pair<Long, Double>>,
    val firstRealIndex: Int,
)

/**
 * Flattens one symbol's data onto the frame's window grid: every window
 * takes the last refined price valid AT THE WINDOW (on or before its end);
 * windows the refinement does not reach fall back to the daily close of
 * the window's calendar day (held flat intraday), and windows on or after
 * today pin the LIVE price (the same pin that keeps the daily frames at
 * card parity).
 *
 * @param refined sub-daily (tsSec, price) ascending - may be empty.
 * @param daily daily closes (utcEpochDay, close) ascending - may be empty.
 * @param livePrice the current live price - may be null (keyless/static
 *        symbol with no cached history).
 * @param nowTsSec the current UTC epoch second (defines the grid).
 * @return the grid pricing, or null when the symbol has NO price source
 *        at all (it then cannot be valued in any frame).
 */
fun flattenIntraday(
    refined: List<Pair<Long, Double>>,
    daily: List<Pair<Long, Double>>,
    livePrice: Double?,
    nowTsSec: Long,
    frame: IntradayFrame,
): IntradayPricing? {
    val lastDaily = daily.lastOrNull()
    if (refined.isEmpty() && lastDaily == null && livePrice == null) return null
    val ws = frame.windowSeconds.toLong()
    val windowStart = nowTsSec - frame.windowDays * 86_400L
    val firstStart = windowStart - (windowStart % ws) // grid aligned to the epoch
    val today = nowTsSec / 86_400L

    val points = ArrayList<Pair<Long, Double>>()
    var firstRealIndex = Int.MAX_VALUE
    var r = 0
    var d = 0
    for (start in firstStart until nowTsSec step ws) {
        val end = start + ws - 1L
        while (r + 1 < refined.size && refined[r + 1].first <= end) r++
        val real = refined.isNotEmpty() && refined[r].first <= end
        while (d + 1 < daily.size && daily[d + 1].first <= end / 86_400L) d++
        val price = when {
            real -> refined[r].second
            lastDaily != null && (end / 86_400L) <= lastDaily.first ->
                if ((end / 86_400L) == today) livePrice ?: lastDaily.second else daily[d].second
            else -> livePrice ?: lastDaily?.second ?: return null
        }
        if (real && firstRealIndex == Int.MAX_VALUE) firstRealIndex = points.size
        points.add(start to price)
    }
    return IntradayPricing(points, if (firstRealIndex == Int.MAX_VALUE) points.size else firstRealIndex)
}

/**
 * The intraday counterpart of [historicalStockSeries]: values the core's
 * sub-daily wallet + spent series per window. One x point per window key
 * present in the data (oldest first; the ISO keys sort chronologically),
 * the running unpriced positions valued with the prices valid at the
 * window ([pricing] uses the SAME window grid as the core keys), pl
 * against the cumulative spent flow so far.
 *
 * A leading window is "estimated" while ANY valued currency is still in
 * its fallback region (before its [IntradayPricing.firstRealIndex] ts).
 * Currencies with no [pricing] entry at all cannot be valued and are
 * excluded (the panel only draws what has prices, as on the daily frames).
 */
fun historicalWindowState(
    spent: List<ChartPoint>,
    wallet: Map<String, List<WalletWindow>>,
    pricing: Map<String, IntradayPricing>,
): HistoricalSeries {
    val keys = LinkedHashSet<String>()
    for (p in spent) keys.add(p.key)
    wallet.values.forEach { list -> list.forEach { w -> keys.add(w.key) } }
    if (keys.isEmpty()) return HistoricalSeries(emptyList(), emptyList(), emptyList(), 0)
    val sorted = keys.sorted()

    val spentByWindow = HashMap<String, Double>(spent.size)
    spent.forEach { spentByWindow[it.key] = it.value }
    val posIdx = HashMap<String, Int>()
    val priceIdx = HashMap<String, Int>()
    val balances = HashMap<String, Double>()
    val bonuses = HashMap<String, Double>()
    val valuePoints = mutableListOf<ChartPoint>()
    val plPoints = mutableListOf<ChartPoint>()
    val bonusPoints = mutableListOf<ChartPoint>()
    // First ts at which each currency's pricing is REAL (estimated before).
    val realFrom = HashMap<String, Long>()
    for ((cur, p) in pricing) {
        realFrom[cur] = if (p.firstRealIndex < p.points.size) {
            p.points[p.firstRealIndex].first
        } else {
            Long.MAX_VALUE
        }
    }
    var spentSoFar = 0.0
    for (key in sorted) {
        val slotTs = toEpochSecond(key)
        for ((cur, list) in wallet) {
            var idx = posIdx[cur] ?: 0
            while (idx < list.size && toEpochSecond(list[idx].key) <= slotTs) {
                balances[cur] = list[idx].balance
                bonuses[cur] = list[idx].bonus
                idx++
            }
            posIdx[cur] = idx
        }
        spentSoFar += spentByWindow[key] ?: 0.0

        var value = 0.0
        var bonusValue = 0.0
        for ((cur, p) in pricing) {
            val balance = balances[cur] ?: continue
            val bonus = bonuses[cur] ?: 0.0
            if (balance == 0.0 && bonus == 0.0) continue
            // The core keys and the pricing grid share the window
            // alignment (epoch multiples of the frame) - advance the
            // pointer instead of scanning (both lists are ascending).
            var idx = priceIdx[cur] ?: 0
            while (idx + 1 < p.points.size && p.points[idx + 1].first <= slotTs) idx++
            priceIdx[cur] = idx
            if (p.points[idx].first > slotTs) continue // grid drift (not expected)
            val price = p.points[idx].second
            value += balance * price
            bonusValue += bonus * price
        }
        valuePoints.add(ChartPoint(key, value))
        plPoints.add(ChartPoint(key, value - spentSoFar))
        bonusPoints.add(ChartPoint(key, bonusValue))
    }
    // Estimated = the leading windows in which any valued currency is
    // before its "real" region; refined regions end at now by
    // construction, so the region is a single prefix.
    var estimatedCount = 0
    for (point in valuePoints) {
        val slotTs = toEpochSecond(point.key)
        val anyEstimated = wallet.keys.any { cur ->
            cur in realFrom && (balances[cur] ?: 0.0) != 0.0 && slotTs < (realFrom[cur] ?: 0L)
        }
        if (!anyEstimated) break
        estimatedCount++
    }
    return HistoricalSeries(valuePoints, plPoints, bonusPoints, estimatedCount)
}

/**
 * The STOCK series (value/pl/bonus) of one intraday frame (plan 009b) from
 * the raw core rows: the per-currency symbols of the wallet rows are priced
 * with [flattenIntraday] (refinement first, then daily-close-held-flat,
 * then the live pin), then [historicalWindowState] values the running
 * positions per window. The spent (flow) series needs none of the pricing
 * and comes straight from [parseSubDailySpent].
 *
 * @param intraday refined sub-daily points per symbol (may be empty).
 * @param daily daily closes per symbol (the flatten fallback source).
 * @param livePrices the live prices (the same map the cards use).
 */
fun intradaySeries(
    subSpentRows: List<String>,
    subWalletRows: List<String>,
    intraday: Map<String, List<Pair<Long, Double>>>,
    daily: Map<String, List<Pair<Long, Double>>>,
    livePrices: Map<String, Double>,
    nowTsSec: Long,
    frame: IntradayFrame,
): HistoricalSeries {
    val wallet = parseWalletWindows(subWalletRows)
    val pricing = HashMap<String, IntradayPricing>()
    for (cur in wallet.keys) {
        flattenIntraday(
            intraday[cur].orEmpty(),
            daily[cur].orEmpty(),
            livePrices[cur],
            nowTsSec,
            frame,
        )?.let { pricing[cur] = it }
    }
    return historicalWindowState(parseSubDailySpent(subSpentRows), wallet, pricing)
}

