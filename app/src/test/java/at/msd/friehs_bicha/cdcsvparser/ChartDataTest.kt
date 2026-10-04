package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartPoint
import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.DailyPoint
import at.msd.friehs_bicha.cdcsvparser.ui.compose.TimeFrame
import at.msd.friehs_bicha.cdcsvparser.ui.compose.bucketize
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseDailySeries
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests for the overview chart panel data (G35). */
class ChartDataTest {

    private fun d(day: Int) = LocalDate.parse("2025-03-%02d".format(day))

    @Test
    fun `parse splits the four series and skips malformed lines`() {
        val parsed = parseDailySeries(
            listOf(
                "spent;2025-03-01;100.000000",
                "value;2025-03-01;250.000000",
                "pl;2025-03-01;150.000000",
                "bonus;2025-03-01;0.000000",
                "spent;2025-03-02;-20.000000",
                "nonsense;2025-03-02;5",
                "spent;not-a-date;5",
                "spent;2025-03-03;oops",
                "spent",
            ),
        )
        assertEquals(ChartSeries.entries.size, parsed.size)
        assertEquals(2, parsed[ChartSeries.SPENT]!!.size) // malformed 3rd row skipped
        assertEquals(1, parsed[ChartSeries.VALUE]!!.size)
        assertEquals(100.0, parsed[ChartSeries.SPENT]!!.first().value, 1e-9)
        assertEquals(-20.0, parsed[ChartSeries.SPENT]!!.last().value, 1e-9)
    }

    @Test
    fun `empty input gives empty maps`() {
        assertTrue(parseDailySeries(emptyList()).isEmpty())
        assertTrue(bucketize(emptyList(), TimeFrame.MONTH, true).isEmpty())
    }

    @Test
    fun `flow series sums per month bucket`() {
        val points = listOf(
            DailyPoint(d(1), 100.0),
            DailyPoint(d(15), -20.0),
            DailyPoint(d(28), 5.0),
            DailyPoint(LocalDate.parse("2025-04-02"), 75.0),
        )
        val bucketed = bucketize(points, TimeFrame.MONTH, isFlow = true)
        assertEquals(
            listOf(ChartPoint("2025-03", 85.0), ChartPoint("2025-04", 75.0)),
            bucketed,
        )
    }

    @Test
    fun `stock series takes the last day of each bucket`() {
        val points = listOf(
            DailyPoint(d(1), 100.0),
            DailyPoint(d(15), 120.0),
            DailyPoint(d(28), 90.0),
            DailyPoint(LocalDate.parse("2025-04-02"), 300.0),
        )
        val bucketed = bucketize(points, TimeFrame.MONTH, isFlow = false)
        assertEquals(
            listOf(ChartPoint("2025-03", 90.0), ChartPoint("2025-04", 300.0)),
            bucketed,
        )
    }

    @Test
    fun `week buckets follow the ISO-8601 week and survive the year boundary`() {
        // 2025-12-29 (Mon) starts ISO week 2026-W01 - the year of a week
        // point is the week's year, not the calendar year.
        val points = listOf(
            DailyPoint(LocalDate.parse("2025-12-29"), 10.0),
            DailyPoint(LocalDate.parse("2025-12-30"), 20.0),
            DailyPoint(LocalDate.parse("2025-12-31"), 15.0),
            DailyPoint(LocalDate.parse("2026-01-01"), 40.0),
            DailyPoint(LocalDate.parse("2026-01-05"), 50.0),
        )
        val bucketed = bucketize(points, TimeFrame.WEEK, isFlow = false)
        // 29.12-01.04 (Mon-Sun) is one ISO week (2026-W01); 05.01 starts W02.
        assertEquals(
            listOf(ChartPoint("2026-W01", 40.0), ChartPoint("2026-W02", 50.0)),
            bucketed,
        )
    }

    @Test
    fun `quarter and year buckets group correctly`() {
        val points = listOf(
            DailyPoint(LocalDate.parse("2025-01-15"), 1.0),
            DailyPoint(LocalDate.parse("2025-03-31"), 2.0),
            DailyPoint(LocalDate.parse("2025-04-01"), 3.0),
            DailyPoint(LocalDate.parse("2025-06-30"), 4.0),
            DailyPoint(LocalDate.parse("2026-02-01"), 5.0),
        )
        val quarters = bucketize(points, TimeFrame.QUARTER, isFlow = false)
        assertEquals(
            listOf(
                ChartPoint("2025-Q1", 2.0),
                ChartPoint("2025-Q2", 4.0),
                ChartPoint("2026-Q1", 5.0),
            ),
            quarters,
        )
        val years = bucketize(points, TimeFrame.YEAR, isFlow = true)
        assertEquals(
            listOf(ChartPoint("2025", 10.0), ChartPoint("2026", 5.0)),
            years,
        )
    }

    @Test
    fun `bucket keys stay chronological for ascending input`() {
        // Across a year/ISO-week boundary the keys must not sort backwards.
        val points = (1..120).map { DailyPoint(LocalDate.parse("2025-09-01").plusDays(it.toLong()), 1.0) }
        val bucketed = bucketize(points, TimeFrame.WEEK, isFlow = true)
        val keys = bucketed.map { it.key }
        assertEquals(keys, keys.sorted())
    }
}
