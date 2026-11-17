package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartPoint
import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartValueSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.chartSeriesStats
import at.msd.friehs_bicha.cdcsvparser.ui.compose.donutStats
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The non-graphical data path of the charts: the summary builders must
 * describe EXACTLY the data the chart draws (same span, same values).
 */
class ChartA11yTest {

    @Test
    fun seriesStatsComputeSpanAndRange() {
        val stats = chartSeriesStats(
            listOf(
                ChartValueSeries(
                    "Spent",
                    listOf(
                        ChartPoint("2026-01", 10.0),
                        ChartPoint("2026-02", 5.0),
                        ChartPoint("2026-03", 20.0),
                    ),
                ),
            ),
        )
        assertEquals(1, stats.size)
        val s = stats[0]
        assertEquals("Spent", s.label)
        assertEquals("2026-01", s.firstKey)
        assertEquals("2026-03", s.lastKey)
        assertEquals(3, s.count)
        assertEquals(5.0, s.min, 0.0001)
        assertEquals(20.0, s.max, 0.0001)
        assertEquals(20.0, s.latest, 0.0001)
    }

    @Test
    fun seriesStatsDropEmptyAndKeepOrder() {
        val stats = chartSeriesStats(
            listOf(
                ChartValueSeries("A", emptyList()),
                ChartValueSeries("B", listOf(ChartPoint("k1", 1.0), ChartPoint("k2", 2.0))),
                ChartValueSeries("C", emptyList()),
                ChartValueSeries("D", listOf(ChartPoint("k1", -3.0))),
            ),
        )
        assertEquals(listOf("B", "D"), stats.map { it.label })
        assertEquals(-3.0, stats[1].min, 0.0001)
        assertEquals(-3.0, stats[1].max, 0.0001)
        assertEquals(-3.0, stats[1].latest, 0.0001)
    }

    @Test
    fun donutStatsTopNSortedWithShares() {
        val stats = donutStats(
            listOf("USD" to 30.0, "EUR" to 50.0, "BTC" to 15.0, "ETH" to 5.0),
            topN = 3,
        )
        assertEquals(100.0, stats.total, 0.0001)
        assertEquals(listOf("EUR", "USD", "BTC"), stats.top.map { it.name })
        assertEquals(0.5, stats.top[0].share, 0.0001)
        assertEquals(0.3, stats.top[1].share, 0.0001)
        assertEquals(0.15, stats.top[2].share, 0.0001)
        assertEquals(1, stats.moreCount)
    }

    @Test
    fun donutStatsClampsTopNAndZeroTotal() {
        val stats = donutStats(listOf("A" to 1.0, "B" to 2.0), topN = 10)
        assertEquals(2, stats.top.size)
        assertEquals(0, stats.moreCount)

        val zero = donutStats(listOf("A" to 0.0, "B" to 0.0), topN = 5)
        assertEquals(0.0, zero.top[0].share, 0.0001)
        assertEquals(0, donutStats(emptyList(), 3).top.size)
    }
}
