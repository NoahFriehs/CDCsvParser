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
        // its year gives the week number.
        val thursday = date.plusDays(((4 - date.dayOfWeek.value) % 7 + 7) % 7L)
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
