package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.DeltaBasis
import at.msd.friehs_bicha.cdcsvparser.app.SummaryDelta
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure point-in-time delta builder of the Main summary hero: the
 * headline decision (net worth vs the empty-sheet degrade) and the
 * delta math on the tracked series priced with the chart's rule (last
 * close at or before the day, the pin-day live pin, the EUR buffer at
 * face). Honesty pins: a basis older than the series, a held symbol
 * without any price source, and the zero-previous percent all render
 * "–" (null) instead of a fabricated number.
 */
class SummaryDeltaTest {

    // 2026-11-20 -> 2026-11-22 fixture: buffer 100 EUR, 0.5 -> 0.6 BTC.
    private val wallet = listOf(
        "EUR;2026-11-20;100.0;0.0",
        "BTC;2026-11-20;0.5;0.0",
        "BTC;2026-11-21;0.6;0.0",
    )
    private val today = LocalDate.parse("2026-11-22")
    private val btcHistory = mapOf(
        "BTC" to listOf(
            LocalDate.parse("2026-11-20").toEpochDay() to 900.0,
            LocalDate.parse("2026-11-21").toEpochDay() to 1000.0,
        ),
    )

    @Test
    fun normal24hDelta() {
        // V(11-21) = 100 + 0.6 * 1000 = 700; V(11-22, live 1100) = 100 + 0.6 * 1100 = 760.
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = wallet,
            history = btcHistory,
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;760.0;0.0;0.0;760.0"),
        )
        assertNotNull(d)
        assertEquals(60.0, d!!.deltaEur!!, 1e-9)
        assertEquals(60.0 / 700.0 * 100.0, d.deltaPercent!!, 1e-9)
        assertEquals(DeltaBasis.DAY, d.basis)
    }

    @Test
    fun normal30dDelta() {
        // Wallet opened 2026-10-15 (38 days before 11-22): the 30-day
        // basis point (10-23) is inside the series.
        val wallet30 = listOf(
            "EUR;2026-10-15;50.0;0.0",
            "BTC;2026-10-15;0.2;0.0",
        )
        val history30 = mapOf(
            "BTC" to listOf(LocalDate.parse("2026-10-23").toEpochDay() to 500.0),
        )
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = wallet30,
            history = history30,
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.THIRTY_DAYS,
            sheetLines = listOf("NW;270.0;0.0;0.0;270.0"),
        )
        // V(10-23) = 50 + 0.2 * 500 = 150; V(11-22) = 50 + 0.2 * 1100 = 270.
        assertNotNull(d)
        assertEquals(120.0, d!!.deltaEur!!, 1e-9)
        assertEquals(80.0, d.deltaPercent!!, 1e-9)
        assertEquals(DeltaBasis.THIRTY_DAYS, d.basis)
    }

    @Test
    fun basisYoungerThanSeriesIsMissing() {
        // A 10-day-old export with the 30-day basis: the previous point
        // predates the series -> "–", not a number.
        val walletYoung = listOf("BTC;2026-11-12;0.5;0.0")
        val historyYoung = mapOf(
            "BTC" to listOf(LocalDate.parse("2026-11-12").toEpochDay() to 900.0),
        )
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = walletYoung,
            history = historyYoung,
            livePrices = mapOf("BTC" to 1000.0),
            today = today,
            basis = DeltaBasis.THIRTY_DAYS,
            sheetLines = listOf("NW;500.0;0.0;0.0;500.0"),
        )
        assertNotNull(d)
        assertNull(d!!.deltaEur)
        assertNull(d.deltaPercent)
    }

    @Test
    fun freshOneDayExportIsMissing() {
        // Data only for today: the 24 h basis has no previous day at all.
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = listOf("BTC;2026-11-22;0.5;0.0"),
            history = mapOf(
                "BTC" to listOf(LocalDate.parse("2026-11-22").toEpochDay() to 1100.0),
            ),
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;550.0;0.0;0.0;550.0"),
        )
        assertNotNull(d)
        assertNull(d!!.deltaEur)
        assertNull(d.deltaPercent)
    }

    @Test
    fun emptySheetDegradesToTrackedAssets() {
        // No liabilities, no assets: the headline is the tracked figure
        // and the net-worth flag falls (the hint row is the caller's).
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = wallet,
            history = btcHistory,
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;760.0;0.0;0.0;760.0"),
        )
        assertNotNull(d)
        assertEquals(760.0, d!!.headlineEur, 1e-9)
        assertFalse(d.isNetWorth)
    }

    @Test
    fun nonEmptySheetHeadlineIsNetWorth() {
        // With a sheet entry the core's net stands; the delta stays the
        // tracked part's (the only time-varying component).
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = wallet,
            history = btcHistory,
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;760.0;3000.0;500.0;3260.0"),
        )
        assertNotNull(d)
        assertEquals(3260.0, d!!.headlineEur, 1e-9)
        assertTrue(d.isNetWorth)
        assertEquals(60.0, d.deltaEur!!, 1e-9)
    }

    @Test
    fun emptyOrMalformedSheetLineIsNull() {
        assertNull(
            SummaryDelta.deltaChipSnapshot(
                walletSeries = wallet,
                history = btcHistory,
                livePrices = mapOf("BTC" to 1100.0),
                today = today,
                basis = DeltaBasis.DAY,
                sheetLines = emptyList(),
            ),
        )
        assertNull(
            SummaryDelta.deltaChipSnapshot(
                walletSeries = wallet,
                history = btcHistory,
                livePrices = mapOf("BTC" to 1100.0),
                today = today,
                basis = DeltaBasis.DAY,
                sheetLines = listOf("NW;1"),
            ),
        )
    }

    @Test
    fun heldSymbolWithoutHistoryIsMissing() {
        // A held symbol with no price history at all: the previous point
        // would silently drop it out of the value -> "–".
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = listOf("BTC;2026-11-20;0.5;0.0"),
            history = emptyMap(),
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;550.0;0.0;0.0;550.0"),
        )
        assertNotNull(d)
        assertNull(d!!.deltaEur)
    }

    @Test
    fun symbolAppearingTodayWithoutAnyPriceIsMissing() {
        // The position exists only on the pin day and neither live nor
        // history prices it: the last point would be partial -> "–".
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = listOf("BTC;2026-11-22;0.1;0.0"),
            history = emptyMap(),
            livePrices = emptyMap(),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;0.0;0.0;0.0;0.0"),
        )
        assertNotNull(d)
        assertNull(d!!.deltaEur)
    }

    @Test
    fun zeroPreviousValueHasNoPercent() {
        // The position was flushed to zero before the previous day (the
        // series point at 0.0 IS emitted) and reappears on the pin day:
        // the previous value is exactly 0.0 - the amount stands, the
        // percent is undefined (not a fabricated 0.0 %).
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = listOf(
                "BTC;2026-11-20;0.5;0.0",
                "BTC;2026-11-21;0.0;0.0",
                "BTC;2026-11-22;0.5;0.0",
            ),
            history = mapOf(
                "BTC" to listOf(
                    LocalDate.parse("2026-11-20").toEpochDay() to 900.0,
                    LocalDate.parse("2026-11-21").toEpochDay() to 1000.0,
                ),
            ),
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;550.0;0.0;0.0;550.0"),
        )
        assertNotNull(d)
        assertEquals(550.0, d!!.deltaEur!!, 1e-9)
        assertNull(d.deltaPercent)
    }

    @Test
    fun seriesStartingTodayHasNoPreviousPoint() {
        // A position that exists only on today's series date: "vs
        // yesterday" has no comparison point at all -> "-" (honesty:
        // no fabricated baseline of 0).
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = listOf("BTC;2026-11-22;0.5;0.0"),
            history = emptyMap(),
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;550.0;0.0;0.0;550.0"),
        )
        assertNotNull(d)
        assertNull(d!!.deltaEur)
        assertNull(d.deltaPercent)
    }

    @Test
    fun eurBufferOnly() {
        // The buffer alone needs no price source (face value).
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = listOf(
                "EUR;2026-11-20;100.0;0.0",
                "EUR;2026-11-21;120.0;0.0",
            ),
            history = emptyMap(),
            livePrices = emptyMap(),
            today = LocalDate.parse("2026-11-21"),
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;120.0;0.0;0.0;120.0"),
        )
        assertNotNull(d)
        assertEquals(20.0, d!!.deltaEur!!, 1e-9)
        assertEquals(20.0, d.deltaPercent!!, 1e-9)
    }

    @Test
    fun quietDayCarriesTheRunningBalanceForward() {
        // No point on 11-21 (quiet day): the 11-20 balance stands at
        // the 11-21 close for the previous point.
        val walletQuiet = listOf("BTC;2026-11-20;0.5;0.0")
        val historyQuiet = mapOf(
            "BTC" to listOf(
                LocalDate.parse("2026-11-20").toEpochDay() to 800.0,
                LocalDate.parse("2026-11-21").toEpochDay() to 1000.0,
            ),
        )
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = walletQuiet,
            history = historyQuiet,
            livePrices = mapOf("BTC" to 1100.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;550.0;0.0;0.0;550.0"),
        )
        // V(11-21) = 0.5 * 1000 = 500; V(11-22) = 0.5 * 1100 = 550.
        assertNotNull(d)
        assertEquals(50.0, d!!.deltaEur!!, 1e-9)
        assertEquals(10.0, d.deltaPercent!!, 1e-9)
    }

    @Test
    fun negativeDelta() {
        // A falling live price turns the delta negative (signed, the
        // arrow color is the caller's decision).
        val walletQuiet = listOf("BTC;2026-11-20;0.5;0.0")
        val historyQuiet = mapOf(
            "BTC" to listOf(
                LocalDate.parse("2026-11-20").toEpochDay() to 800.0,
                LocalDate.parse("2026-11-21").toEpochDay() to 1000.0,
            ),
        )
        val d = SummaryDelta.deltaChipSnapshot(
            walletSeries = walletQuiet,
            history = historyQuiet,
            livePrices = mapOf("BTC" to 500.0),
            today = today,
            basis = DeltaBasis.DAY,
            sheetLines = listOf("NW;250.0;0.0;0.0;250.0"),
        )
        assertNotNull(d)
        assertEquals(-250.0, d!!.deltaEur!!, 1e-9)
        assertEquals(-50.0, d.deltaPercent!!, 1e-9)
    }
}
