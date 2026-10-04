package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartPoint
import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.DailyPoint
import at.msd.friehs_bicha.cdcsvparser.ui.compose.TimeFrame
import at.msd.friehs_bicha.cdcsvparser.ui.compose.PriceSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.WalletDay
import at.msd.friehs_bicha.cdcsvparser.ui.compose.bucketize
import at.msd.friehs_bicha.cdcsvparser.ui.compose.historicalStockSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseDailySeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletSeries
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ------------------------------------------------------------ G36 ----
    // Historical (point-in-time) pricing of the stock chart series.

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)
    private fun ed(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun `wallet series parsing splits per currency and skips malformed lines`() {
        val parsed = parseWalletSeries(
            listOf(
                "BTC;2023-01-05;1.000000;0.000000",
                "BTC;2023-06-10;0.500000;0.000000",
                "ETH;2023-03-01;2.000000;2.000000",
                "no-semicolon",
                "BAD;not-a-date;1;1",
                "BAD;2023-01-01;oops;1",
            ),
        )
        assertEquals(2, parsed.size)
        assertEquals(2, parsed["BTC"]!!.size)
        assertEquals(WalletDay(day(2023, 6, 10), 0.5, 0.0), parsed["BTC"]!!.last())
        assertEquals(2.0, parsed["ETH"]!!.single().bonus, 1e-12)
    }

    @Test
    fun `price lookup is on-or-before with backfill before the first point`() {
        val series = PriceSeries(listOf(ed(2023, 1, 1) to 1.0, ed(2023, 1, 3) to 3.0))
        assertEquals(1.0, series.priceAt(ed(2023, 1, 1) - 1)!!, 1e-12) // backfill first
        assertEquals(1.0, series.priceAt(ed(2023, 1, 1))!!, 1e-12)
        assertEquals(1.0, series.priceAt(ed(2023, 1, 2))!!, 1e-12)  // last at or before
        assertEquals(3.0, series.priceAt(ed(2023, 1, 3))!!, 1e-12)
        assertEquals(3.0, series.priceAt(ed(2023, 1, 3) + 99)!!, 1e-12)
        assertNull(PriceSeries(emptyList()).priceAt(ed(2023, 1, 1)))
    }

    @Test
    fun `stock series values each bucket with the prices of its end date`() {
        // BTC bought in January, half sold into a price rise in June;
        // ETH earned as bonus in March. Today's bucket is pinned to the
        // live prices (card parity).
        val wallet =
            mapOf(
                "BTC" to listOf(
                    WalletDay(day(2023, 1, 5), 1.0, 0.0),
                    WalletDay(day(2023, 6, 10), 0.5, 0.0),
                ),
                "ETH" to listOf(WalletDay(day(2023, 3, 1), 2.0, 2.0)),
            )
        val prices =
            mapOf(
                "BTC" to PriceSeries(listOf(ed(2023, 1, 1) to 20000.0, ed(2023, 6, 1) to 30000.0)),
                "ETH" to PriceSeries(listOf(ed(2023, 3, 1) to 100.0)),
            )
        val spent =
            listOf(
                DailyPoint(day(2023, 1, 6), 100.0),
                DailyPoint(day(2023, 6, 10), -20.0),
            )
        val (value, pl, bonus) = historicalStockSeries(
            wallet, prices, spent, TimeFrame.MONTH, day(2023, 7, 15),
            currentPrices = mapOf("BTC" to 30000.0, "ETH" to 120.0),
        )
        assertEquals(listOf("2023-01", "2023-02", "2023-03", "2023-04", "2023-05", "2023-06", "2023-07"), value.map { it.key })
        // January/February: 1 BTC at the January price; ETH arrives in March.
        assertEquals(20000.0, value[0].value, 1e-6)
        assertEquals(20000.0, value[1].value, 1e-6)
        assertEquals(20200.0, value[2].value, 1e-6)   // + 2 ETH x 100
        // June: 0.5 BTC at the June price + ETH
        assertEquals(15200.0, value[5].value, 1e-6)
        // July (still open): pinned to the LIVE prices.
        assertEquals(0.5 * 30000.0 + 2.0 * 120.0, value[6].value, 1e-6)
        // P/L = value - cumulative spent (100 in January, -20 in June).
        assertEquals(19900.0, pl[0].value, 1e-6)
        assertEquals(15120.0, pl[5].value, 1e-6)
        assertEquals(15240.0 - 80.0, pl[6].value, 1e-6)
        // Bonus follows the ETH bonus with the same pricing.
        assertEquals(0.0, bonus[0].value, 1e-9)
        assertEquals(200.0, bonus[5].value, 1e-6)
        assertEquals(240.0, bonus[6].value, 1e-6)
    }

    @Test
    fun `positions before the first price point use the first known price`() {
        // LUNA held since January, CoinGecko data only from May: the early
        // buckets backfill the first known price instead of showing 0.
        val wallet = mapOf("LUNA" to listOf(WalletDay(day(2023, 1, 2), 5.0, 0.0)))
        val prices = mapOf("LUNA" to PriceSeries(listOf(ed(2023, 5, 1) to 0.01)))
        val (value, _, _) = historicalStockSeries(
            wallet, prices, emptyList(), TimeFrame.MONTH, day(2023, 5, 10),
        )
        assertEquals(listOf("2023-01", "2023-02", "2023-03", "2023-04", "2023-05"), value.map { it.key })
        value.forEach { assertEquals(0.05, it.value, 1e-9) }
    }

    @Test
    fun `symbols without any history contribute zero`() {
        val wallet = mapOf("DOGE" to listOf(WalletDay(day(2023, 1, 2), 100.0, 0.0)))
        val (value, pl, bonus) = historicalStockSeries(
            wallet, emptyMap(), emptyList(), TimeFrame.MONTH, day(2023, 1, 20),
        )
        assertEquals(1, value.size)
        assertEquals(0.0, value[0].value, 1e-12)
        assertEquals(0.0, pl[0].value, 1e-12)
        assertEquals(0.0, bonus[0].value, 1e-12)
    }

    @Test
    fun `week buckets are ISO weeks and stay chronological`() {
        // 2023-03-06 is the Monday of ISO week 10, 2023-03-13 of week 11.
        val wallet = mapOf(
            "BTC" to listOf(
                WalletDay(day(2023, 3, 6), 1.0, 0.0),
                WalletDay(day(2023, 3, 9), 2.0, 0.0),
                WalletDay(day(2023, 3, 13), 3.0, 0.0),
            ),
        )
        val prices = mapOf("BTC" to PriceSeries(listOf(ed(2023, 3, 1) to 10.0)))
        val (value, _, _) = historicalStockSeries(
            wallet, prices, emptyList(), TimeFrame.WEEK, day(2023, 3, 15),
        )
        assertEquals(listOf("2023-W10", "2023-W11"), value.map { it.key })
        assertEquals(20.0, value[0].value, 1e-9)   // 2 BTC held as of W10 end
        assertEquals(30.0, value[1].value, 1e-9)   // 3 BTC, open week pinned at today
    }

    @Test
    fun `empty wallet series yields empty stock series`() {
        val (value, pl, bonus) = historicalStockSeries(
            emptyMap(), emptyMap(), emptyList(), TimeFrame.MONTH, day(2023, 1, 15),
        )
        assertTrue(value.isEmpty() && pl.isEmpty() && bonus.isEmpty())
    }
}
