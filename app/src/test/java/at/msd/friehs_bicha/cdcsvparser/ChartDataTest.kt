package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartPoint
import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.DailyPoint
import at.msd.friehs_bicha.cdcsvparser.ui.compose.TimeFrame
import at.msd.friehs_bicha.cdcsvparser.ui.compose.PriceSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.WalletDay
import at.msd.friehs_bicha.cdcsvparser.ui.compose.ChartRange
import at.msd.friehs_bicha.cdcsvparser.ui.compose.bucketLabel
import at.msd.friehs_bicha.cdcsvparser.ui.compose.bucketStartDate
import at.msd.friehs_bicha.cdcsvparser.ui.compose.bucketKey
import at.msd.friehs_bicha.cdcsvparser.ui.compose.bucketize
import at.msd.friehs_bicha.cdcsvparser.ui.compose.historicalStockSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.limitRange
import at.msd.friehs_bicha.cdcsvparser.ui.compose.IntradayFrame
import at.msd.friehs_bicha.cdcsvparser.ui.compose.IntradayPricing
import at.msd.friehs_bicha.cdcsvparser.ui.compose.OverlaySeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.intradayFrame
import at.msd.friehs_bicha.cdcsvparser.ui.compose.intradayGridStart
import at.msd.friehs_bicha.cdcsvparser.ui.compose.WalletWindow
import at.msd.friehs_bicha.cdcsvparser.ui.compose.alignOverlay
import at.msd.friehs_bicha.cdcsvparser.ui.compose.flattenIntraday
import at.msd.friehs_bicha.cdcsvparser.ui.compose.historicalWindowState
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseDailySeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseSubDailySpent
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletSeries
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletWindows
import at.msd.friehs_bicha.cdcsvparser.ui.compose.toEpochSecond
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

    // Plan 009a: the 1-day frame - one bucket per calendar day (the core's
    // native resolution), full history, no windowing.
    @Test
    fun dayFrameBucketizesOneBucketPerCalendarDay() {
        val points = listOf(
            DailyPoint(day(2026, 1, 15), 1.0),
            DailyPoint(day(2026, 1, 16), 2.0),
            DailyPoint(day(2026, 1, 16), 3.0), // same day, second entry
            DailyPoint(day(2026, 2, 1), 4.0),
        )
        // Flow: same-day entries SUM within the day bucket.
        val flow = bucketize(points, TimeFrame.DAY, isFlow = true)
        assertEquals(
            listOf(
                ChartPoint("2026-01-15", 1.0),
                ChartPoint("2026-01-16", 5.0),
                ChartPoint("2026-02-01", 4.0),
            ),
            flow,
        )
        // Stock: the LAST value of the day wins.
        val stock = bucketize(points, TimeFrame.DAY, isFlow = false)
        assertEquals(
            listOf(
                ChartPoint("2026-01-15", 1.0),
                ChartPoint("2026-01-16", 3.0),
                ChartPoint("2026-02-01", 4.0),
            ),
            stock,
        )
    }

    @Test
    fun dayFrameKeyStartLabelRoundTrip() {
        assertEquals("2026-02-10", bucketKey(day(2026, 2, 10), TimeFrame.DAY))
        assertEquals(day(2026, 2, 10), bucketStartDate("2026-02-10", TimeFrame.DAY))
        assertNull(bucketStartDate("garbage", TimeFrame.DAY))
        // The label is the device-locale SHORT date - locale-neutral in
        // digits, so assert the day/month/year parts are all present.
        val label = bucketLabel("2026-02-10", TimeFrame.DAY)
        assertTrue("label should contain 10: $label", label.contains("10"))
        assertTrue("label should contain 2: $label", label.contains("2"))
        assertTrue("label should contain 26: $label", label.contains("26"))
    }

    @Test
    fun dayFrameHistoricalSeriesRunsPerCalendarDay() {
        // One wallet, one symbol, history covering 2026-01-15..17, live price
        // for the pin day.
        val wallet = mapOf(
            "BTC" to listOf(
                WalletDay(day(2026, 1, 15), 2.0, 0.0),
                WalletDay(day(2026, 1, 16), 2.5, 0.0),
                WalletDay(day(2026, 1, 17), 3.0, 5.0),
            ),
        )
        val prices = mapOf(
            "BTC" to PriceSeries(
                listOf(ed(2026, 1, 15) to 100.0, ed(2026, 1, 16) to 102.0, ed(2026, 1, 17) to 104.0),
            ),
        )
        val series = historicalStockSeries(
            wallet, prices, emptyList(), TimeFrame.DAY, day(2026, 1, 17),
            mapOf("BTC" to 104.0),
        )
        // One point per day (15, 16, 17 = pin day, pinned live).
        assertEquals(listOf("2026-01-15", "2026-01-16", "2026-01-17"), series.value.map { it.key })
        assertEquals(listOf(200.0, 255.0, 312.0), series.value.map { it.value })
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
    fun `buckets before the real-history start are counted as estimated`() {
        // Wallet active since January, price history only from June: the
        // Jan..May buckets ride the first-price back-fill (estimate); the
        // open last bucket is pinned to live prices and stays real.
        val wallet =
            mapOf("BTC" to (1..7).map { WalletDay(day(2023, it, 2), 1.0, 0.0) })
        val prices =
            mapOf("BTC" to PriceSeries(listOf(ed(2023, 6, 1) to 20000.0, ed(2023, 7, 1) to 25000.0)))
        val (value, _, _, estimatedCount) = historicalStockSeries(
            wallet, prices, emptyList(), TimeFrame.MONTH, day(2023, 7, 15),
            currentPrices = mapOf("BTC" to 25000.0),
        )
        assertEquals(7, value.size)
        assertEquals(5, estimatedCount)   // Jan, Feb, Mar, Apr, May
    }

    @Test
    fun `no history at all - only the pinned bucket is real`() {
        val wallet =
            mapOf("BTC" to listOf(WalletDay(day(2023, 1, 2), 1.0, 0.0)))
        val (_, _, _, estimatedCount) = historicalStockSeries(
            wallet, emptyMap(), emptyList(), TimeFrame.MONTH, day(2023, 1, 20),
        )
        assertEquals(0, estimatedCount)   // single bucket IS the pinned one
    }

    @Test
    fun `empty wallet series yields empty stock series`() {
        val (value, pl, bonus) = historicalStockSeries(
            emptyMap(), emptyMap(), emptyList(), TimeFrame.MONTH, day(2023, 1, 15),
        )
        assertTrue(value.isEmpty() && pl.isEmpty() && bonus.isEmpty())
    }

    // ----------------------------------------------------------------
    // Plan 006: bucketStartDate (inverse of bucketKey) + limitRange
    // ----------------------------------------------------------------

    @Test
    fun `bucketStartDate inverts month quarter year keys`() {
        assertEquals(day(2025, 3, 1), bucketStartDate("2025-03", TimeFrame.MONTH))
        assertEquals(day(2025, 4, 1), bucketStartDate("2025-Q2", TimeFrame.QUARTER))
        assertEquals(day(2025, 10, 1), bucketStartDate("2025-Q4", TimeFrame.QUARTER))
        assertEquals(day(2025, 1, 1), bucketStartDate("2025", TimeFrame.YEAR))
        assertNull(bucketStartDate("garbage", TimeFrame.MONTH))
        assertNull(bucketStartDate("2025-13", TimeFrame.MONTH))
    }

    @Test
    fun `bucketStartDate inverts week keys incl the ISO edge cases`() {
        // The week is owned by its Thursday: every day of an ISO week must
        // map to the Monday of that week.
        for (offset in 0..6) {
            val anyDay = LocalDate.parse("2025-03-03").plusDays(offset.toLong()) // Mon 2025-03-03 .. Sun
            val key = bucketKey(anyDay, TimeFrame.WEEK)
            assertEquals(anyDay.minusDays((anyDay.dayOfWeek.value - 1).toLong()), bucketStartDate(key, TimeFrame.WEEK))
        }
        // 2020-W53: the only 53-week year of the 2015-2070 range. Its last
        // week is Mon 2020-12-28 .. Sun 2021-01-03; the key uses the ISO
        // year (2020) even though the Sunday is in January 2021.
        assertEquals("2020-W53", bucketKey(day(2020, 12, 30), TimeFrame.WEEK))
        val w53Monday = bucketStartDate("2020-W53", TimeFrame.WEEK)
        assertEquals(day(2020, 12, 28), w53Monday)
        assertEquals(day(2021, 1, 3), w53Monday?.plusDays(6))
        // Week 1 of a year that starts on a Thursday (2021): Mon 2021-01-04.
        assertEquals("2021-W01", bucketKey(day(2021, 1, 5), TimeFrame.WEEK))
        assertEquals(day(2021, 1, 4), bucketStartDate("2021-W01", TimeFrame.WEEK))
        // A Wednesday that belongs to the PREVIOUS ISO year's week: Sun
        // 2021-01-03 is owned by the Thursday 2020-12-31 -> 2020-W53, and
        // Jan 1/2 2021 (Fri/Sat) too.
        assertEquals("2020-W53", bucketKey(day(2021, 1, 2), TimeFrame.WEEK))
        assertEquals(day(2020, 12, 28), bucketStartDate(bucketKey(day(2021, 1, 2), TimeFrame.WEEK), TimeFrame.WEEK))
    }

    @Test
    fun `bucketStartDate round-trips for every daily bucket over a year span`() {
        for (offset in 0 until 400) {
            val date = day(2024, 1, 1).plusDays(offset.toLong())
            for (frame in TimeFrame.entries) {
                val key = bucketKey(date, frame)
                val start = bucketStartDate(key, frame) ?: continue
                assertTrue("$date $frame: start after date", !start.isAfter(date))
                // The bucket's start must re-key to the same bucket.
                assertEquals("$date $frame", key, bucketKey(start, frame))
            }
        }
    }

    @Test
    fun `limitRange keeps buckets starting inside the window`() {
        val points = (0 until 8).map { ChartPoint("2025-%02d".format(it + 1), it.toDouble()) } // Jan..Aug 2025
        // No window = identity (same instance pair values).
        val (all, allEst) = limitRange(points, 0, TimeFrame.MONTH, null, null)
        assertEquals(points, all)
        assertEquals(0, allEst)

        // Start-only: from April keeps Apr..Aug.
        val (from, _) = limitRange(points, 0, TimeFrame.MONTH, day(2025, 4, 1), null)
        assertEquals(listOf("2025-04", "2025-05", "2025-06", "2025-07", "2025-08"), from.map { it.key })

        // Both bounds: Mar..May.
        val (win, _) = limitRange(points, 0, TimeFrame.MONTH, day(2025, 3, 1), day(2025, 5, 31))
        assertEquals(listOf("2025-03", "2025-04", "2025-05"), win.map { it.key })

        // A window before the data ends up empty (the "no data" text shows).
        val (none, _) = limitRange(points, 0, TimeFrame.MONTH, day(2020, 1, 1), day(2020, 12, 31))
        assertTrue(none.isEmpty())
    }

    @Test
    fun `limitRange adjusts the leading estimated count`() {
        val points = (0 until 6).map { ChartPoint("2025-%02d".format(it + 1), it.toDouble()) }
        // The first 3 buckets are estimated.
        val (kept, keptEst) = limitRange(points, 3, TimeFrame.MONTH, day(2025, 3, 1), null)
        assertEquals(listOf("2025-03", "2025-04", "2025-05", "2025-06"), kept.map { it.key })
        // March (index 2) was the last estimated bucket that survived.
        assertEquals(1, keptEst)

        // Cutting OFF all estimated buckets -> 0.
        val (kept2, keptEst2) = limitRange(points, 3, TimeFrame.MONTH, day(2025, 4, 1), null)
        assertEquals(0, keptEst2)
        assertTrue(kept2.isNotEmpty())
    }

    @Test
    fun `preset ranges are start-only limits relative to today`() {
        val today = day(2025, 6, 15)
        assertNull(ChartRange.ALL.startDate(today))
        assertEquals(day(2025, 3, 15), ChartRange.THREE_MONTHS.startDate(today))
        assertEquals(day(2024, 12, 15), ChartRange.SIX_MONTHS.startDate(today))
        assertEquals(day(2024, 6, 15), ChartRange.ONE_YEAR.startDate(today))
        assertEquals(day(2023, 6, 15), ChartRange.TWO_YEARS.startDate(today))
        assertEquals(day(2020, 6, 15), ChartRange.FIVE_YEARS.startDate(today))
    }

    // ------------------------------------------------------------------
    // Plan 009b: the sub-daily (intraday) pure functions.

    @Test
    fun subDailyWindowKeyParsesToEpochSecond() {
        assertEquals(86_400L + 6 * 3600L, toEpochSecond("1970-01-02 06:00"))
        assertEquals(86_400L + 2 * 60L, toEpochSecond("1970-01-02 00:02"))
        assertEquals(-1L, toEpochSecond("1970-01-02 25:00"))
        assertEquals(-1L, toEpochSecond("16:00;3.5"))
        assertEquals(-1L, toEpochSecond("1970-02-30 06:00")) // invalid calendar day
    }

    @Test
    fun subDailyRowParsersSkipMalformedLines() {
        val wallet = parseWalletWindows(
            listOf(
                "EUR;1970-01-02 06:00;2.5;-0.25",
                "BTC;1970-01-02 06:00;0.01;0",
                "BTC;1970-01-02 12:00;0.02;0.5",
                "BTC;garbled",
                "EUR;garbled;1;2",
            ),
        )
        assertEquals(setOf("EUR", "BTC"), wallet.keys)
        assertEquals(1, wallet["EUR"]!!.size)
        assertEquals(2, wallet["BTC"]!!.size)
        assertEquals(0.02, wallet["BTC"]!![1].balance, 1e-9)
        assertEquals(0.5, wallet["BTC"]!![1].bonus, 1e-9)

        val spent = parseSubDailySpent(
            listOf("1970-01-02 06:00;-10.5", "1970-01-02 12:00;3", "garbled", "1970-01-02 18:00;NaN"),
        )
        assertEquals(2, spent.size)
        assertEquals(-10.5, spent[0].value, 1e-9)
        assertEquals("1970-01-02 12:00", spent[1].key)
    }

    @Test
    fun flattenIntradayHoldsLastRefinedPricePerWindow() {
        // 1 h grid over the 1-day window (24 points), now on a grid edge.
        val now = 3_600_000L
        val firstStart = now - 86_400
        val frame = IntradayFrame(3600, 1)
        val refined = listOf(
            3_516_000L to 10.0, // window 0
            3_523_200L to 11.0, // window 2
            3_527_200L to 12.0, // window 3, then held flat
        )

        val out = flattenIntraday(refined, emptyList(), null, now, frame)
            ?: error("flatten failed")

        assertEquals(24, out.points.size)
        assertEquals(firstStart, out.points.first().first)
        assertEquals(10.0, out.points[0].second, 1e-9)
        assertEquals(10.0, out.points[1].second, 1e-9) // same price, next window
        assertEquals(11.0, out.points[2].second, 1e-9)
        assertEquals(12.0, out.points[3].second, 1e-9)
        assertEquals(12.0, out.points[23].second, 1e-9) // held to the latest window
        assertEquals(0, out.firstRealIndex)
    }

    @Test
    fun flattenIntradayFallsBackToDailyThenLivePin() {
        val now = 3_600_000L // day 41 of the epoch
        val frame = IntradayFrame(3600, 1)
        // Day 40 closes at 5.0; the (partial) day-41 cache says 6.0, live is 7.5.
        val daily = listOf(40L to 5.0, 41L to 6.0)

        val out = flattenIntraday(emptyList(), daily, 7.5, now, frame)
            ?: error("flatten failed")

        assertEquals(5.0, out.points[0].second, 1e-9) // day 40 -> daily close
        // window 8 is the first window of day 41 -> the LIVE pin
        assertEquals(7.5, out.points[8].second, 1e-9)
        assertEquals(7.5, out.points[23].second, 1e-9)
        assertEquals(out.points.size, out.firstRealIndex) // nothing real -> all estimated
    }

    @Test
    fun flattenIntradayReturnsNullWithoutAnyPriceSource() {
        assertNull(flattenIntraday(emptyList(), emptyList(), null, 3_600_000L, IntradayFrame(3600, 1)))
    }

    @Test
    fun historicalWindowStateValuesRunningPositionsPerWindow() {
        val k0 = "1970-01-02 06:00"
        val k1 = "1970-01-02 12:00"
        val wallet = mapOf(
            "EUR" to listOf(
                WalletWindow(k0, 2.0, 0.5),
            ),
        )
        val pricing = mapOf(
            "EUR" to IntradayPricing(
                points = listOf(
                    108_000L to 10.0, // 1970-01-02 06:00
                    129_600L to 12.0, // 1970-01-02 12:00
                ),
                firstRealIndex = 1, // only window 1 is "real"
            ),
        )
        val spent = listOf(ChartPoint(k0, 5.0), ChartPoint(k1, 3.0))

        val series = historicalWindowState(spent, wallet, pricing)

        assertEquals(listOf(k0, k1), series.value.map { it.key })
        assertEquals(20.0, series.value[0].value, 1e-9) // 2.0 * 10
        assertEquals(24.0, series.value[1].value, 1e-9) // 2.0 * 12
        assertEquals(15.0, series.pl[0].value, 1e-9)   // 20 - 5
        assertEquals(16.0, series.pl[1].value, 1e-9)   // 24 - (5+3)
        assertEquals(5.0, series.bonus[0].value, 1e-9)  // 0.5 * 10
        assertEquals(6.0, series.bonus[1].value, 1e-9)  // 0.5 * 12
        assertEquals(1, series.estimatedCount)           // only window 0 estimated
    }

    @Test
    fun alignOverlayProjectsSeriesOntoTheirUnionGrid() {
        // The overlay must not drift: two series with different active
        // buckets live on the union grid (flows fill 0, stocks carry).
        val spent = OverlaySeries(
            ChartSeries.SPENT,
            listOf(ChartPoint("2026-01-05", 10.0), ChartPoint("2026-01-19", 5.0)),
            estimated = 0,
        )
        val value = OverlaySeries(
            ChartSeries.VALUE,
            listOf(ChartPoint("2026-01-01", 100.0), ChartPoint("2026-01-12", 200.0)),
            estimated = 1, // 2026-01-01 is estimated, 2026-01-12 is real
        )

        val (grid, aligned) = alignOverlay(listOf(spent, value))

        assertEquals(
            listOf("2026-01-01", "2026-01-05", "2026-01-12", "2026-01-19"),
            grid,
        )
        val spentAligned = aligned.first { it.series == ChartSeries.SPENT }
        val valueAligned = aligned.first { it.series == ChartSeries.VALUE }
        // carried forward (the single-series charts connect the dots too),
        // 0 before the first point:
        assertEquals(listOf(0.0, 10.0, 10.0, 5.0), spentAligned.values)
        assertEquals(listOf(100.0, 100.0, 200.0, 200.0), valueAligned.values) // stock carry
        assertEquals(0, spentAligned.estimated)
        // everything before the first REAL key (01-12) is estimated:
        assertEquals(2, valueAligned.estimated)
    }

    @Test
    fun alignOverlayKeepsFullyEstimatedPrefixDashed() {
        val allReal = OverlaySeries(
            ChartSeries.VALUE,
            listOf(ChartPoint("a", 1.0), ChartPoint("b", 2.0)),
            estimated = 2, // both points estimated
        )
        val (_, aligned) = alignOverlay(listOf(allReal))
        assertEquals(2, aligned.first().estimated)

        val none = alignOverlay(emptyList())
        assertTrue(none.first.isEmpty())
    }

    // --------------------------------------------------------------------
    // The intraday frames end at NOW - the full window grid.

    @Test
    fun intradayGridStartFloorsToTheFrameGrid() {
        val now = 1_700_000_401L
        // 6h frame (21600 s slots, 30 d window):
        val s6 = intradayGridStart(now, TimeFrame.HOURS_6.intradayFrame!!)
        assertEquals(1_697_392_800L, s6) // 30 d back, floored to the 6h grid
        assertEquals(0L, s6 % 21_600L)
        // 15m frame (900 s slots, 7 d window):
        val s15 = intradayGridStart(now, TimeFrame.MINUTES_15.intradayFrame!!)
        assertEquals(1_699_395_300L, s15)
        assertEquals(0L, s15 % 900L)
        // an already-aligned "now": the window start stays on the grid
        assertEquals(21_600L, intradayGridStart(21_600L + 30L * 86_400L, TimeFrame.HOURS_6.intradayFrame!!))
    }

    /** 24 one-hour slots of 1970-01-02 (the shared test grid).
     *  (86_400 = the epoch second of 1970-01-02 00:00 UTC.) */
    private fun grid(i: Int) = 86_400L + i * 3_600L

    private fun gridKeys(n: Int) = (0 until n).map {
        "1970-01-02 " + String.format("%02d:00", it)
    }

    @Test
    fun historicalWindowStateStaleOpeningLineOnlyRidesTheFullGrid() {
        // Stale export: the core emits ONLY the opening line (the position
        // held at the window start), no in-window change. With refinement
        // (real candles across the whole window):
        val wallet = mapOf("BTC" to listOf(WalletWindow("1970-01-02 00:00", 2.0, 0.0)))
        val refined = (0 until 24).map { grid(it) to (10.0 + it * 0.5) }
        val pricing = mapOf("BTC" to IntradayPricing(refined, 0))

        val series = historicalWindowState(emptyList(), wallet, pricing)

        assertEquals(gridKeys(24), series.value.map { it.key })
        assertEquals(20.0, series.value[0].value, 1e-9)   // 2.0 x 10.0
        assertEquals(2 * (10.0 + 11 * 0.5), series.value[11].value, 1e-9)
        assertEquals(2 * (10.0 + 23 * 0.5), series.value[23].value, 1e-9) // last slot = live-pin value
        assertEquals(0, series.estimatedCount) // refined across the whole window
        assertEquals(0.0, series.bonus.last().value, 1e-9)
    }

    @Test
    fun historicalWindowStateStaleWithoutRefinementIsFullyEstimated() {
        // Same stale export, refinement unavailable (429 wall / offline):
        // daily-held-flat pricing -> the whole line is the estimated style.
        val wallet = mapOf("BTC" to listOf(WalletWindow("1970-01-02 00:00", 2.0, 0.0)))
        val flat = (0 until 24).map { grid(it) to 10.0 }
        val pricing = mapOf("BTC" to IntradayPricing(flat, 24)) // nothing "real"

        val series = historicalWindowState(emptyList(), wallet, pricing)

        assertEquals(gridKeys(24), series.value.map { it.key })
        assertEquals(24, series.value.size.toLong())
        assertTrue(series.value.all { it.value == 20.0 })
        assertEquals(24, series.estimatedCount) // degraded mode, one pass
    }

    @Test
    fun historicalWindowStateCarriesPositionOverQuietSlots() {
        // Partial overlap: opening line (2.0) + one in-window change at
        // slot 5 (-0.5 -> 1.5); spent flow lands on the FULL grid at its
        // own slot and stays cumulative.
        val wallet = mapOf(
            "BTC" to listOf(
                WalletWindow("1970-01-02 00:00", 2.0, 0.0),
                WalletWindow("1970-01-02 05:00", 1.5, 0.0),
            ),
        )
        val flat = (0 until 24).map { grid(it) to 10.0 }
        val pricing = mapOf("BTC" to IntradayPricing(flat, 0))
        val spent = listOf(ChartPoint("1970-01-02 05:00", 10.0))

        val series = historicalWindowState(spent, wallet, pricing)

        assertEquals(gridKeys(24), series.value.map { it.key })
        assertEquals(20.0, series.value[4].value, 1e-9) // carried: 2.0 x 10 before slot 5
        assertEquals(15.0, series.value[5].value, 1e-9) // changed at slot 5
        assertEquals(15.0, series.value[23].value, 1e-9) // carried again after
        assertEquals(20.0, series.pl[4].value, 1e-9) // spent lands at its slot
        assertEquals(5.0, series.pl[5].value, 1e-9) // 15 - 10
        assertEquals(5.0, series.pl[23].value, 1e-9) // cumulative flow stays deducted
    }

    @Test
    fun historicalWindowStateFreshExportCreatesAndClosesMidWindow() {
        // Fresh export: nothing held at the window start (NO opening line),
        // one purchase at slot 3, one sale at slot 10 - regression-equivalent
        // to the old union-grid values, now on the full window grid.
        val wallet = mapOf(
            "BTC" to listOf(
                WalletWindow("1970-01-02 03:00", 1.0, 0.2),
                WalletWindow("1970-01-02 10:00", 0.0, 0.0),
            ),
        )
        val flat = (0 until 24).map { grid(it) to 10.0 }
        val pricing = mapOf("BTC" to IntradayPricing(flat, 0))

        val series = historicalWindowState(emptyList(), wallet, pricing)

        assertEquals(gridKeys(24), series.value.map { it.key })
        assertEquals(0.0, series.value[0].value, 1e-9) // no rows before creation
        assertEquals(0.0, series.value[2].value, 1e-9)
        assertEquals(10.0, series.value[3].value, 1e-9) // 1.0 x 10 from slot 3
        assertEquals(2.0, series.bonus[3].value, 1e-9) // 0.2 x 10
        assertEquals(10.0, series.value[9].value, 1e-9)
        assertEquals(0.0, series.value[10].value, 1e-9) // closed: carry holds 0
        assertEquals(0.0, series.value[23].value, 1e-9)
        assertEquals(0.0, series.bonus[10].value, 1e-9)
    }

    @Test
    fun historicalWindowStateWithoutPricingIsEmpty() {
        val wallet = mapOf("BTC" to listOf(WalletWindow("1970-01-02 00:00", 2.0, 0.0)))
        val series = historicalWindowState(emptyList(), wallet, emptyMap())
        assertTrue(series.value.isEmpty())
        assertTrue(series.pl.isEmpty())
        assertTrue(series.bonus.isEmpty())
    }
}
