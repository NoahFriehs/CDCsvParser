package at.msd.friehs_bicha.cdcsvparser.app.networth

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The returns builder is the metric's ONLY app-side logic - its output
 * lines are the core's input, so they are pinned byte-for-byte against
 * small synthetic caches (the metric math itself is core-suite-pinned;
 * the JVM does not re-assert it).
 */
class ReturnSeriesBuilderTest {

    private val d0 = LocalDate.of(2023, 1, 1)
    private val d1 = LocalDate.of(2023, 1, 2)
    private val d2 = LocalDate.of(2023, 1, 3)

    @Test
    fun pinsPricedLinesAgainstCachedHistory() {
        // The chart's rule, byte-pinned: last close at or before the day
        // (first-close back-fill), EUR valued at face, external spent
        // negative, quiet days carried forward.
        val history = mapOf(
            "BTC" to listOf(d0.toEpochDay() to 100.0, d2.toEpochDay() to 130.0),
        )
        val priced = ReturnSeriesBuilder.build(
            walletSeries = listOf(
                "BTC;$d0;1.0;0.0",
                "BTC;$d2;1.5;0.0",
                "EUR;$d0;200.0;0.0",
                "EUR;$d2;350.0;0.0",
            ),
            dailyRows = listOf("spent;$d1;40.0"),
            history = history,
            livePrices = emptyMap(),
            today = d2,
        )
        assertEquals(d0, priced.firstDay)
        assertEquals(
            listOf(
                "R;$d0;300.000000;300.000000",   // 1 BTC x 100 + 200 buffer; CF = 100 + 200
                "R;$d1;300.000000;-40.000000",   // quiet day; external spent
                "R;$d2;545.000000;215.000000",   // 1.5 BTC x 130 + 350; CF = 0.5 x 130 + 150
            ),
            priced.lines,
        )
    }

    @Test
    fun pinsLiveValueAndDropsSymbolsWithoutAnyPriceSource() {
        // The last day pins the LIVE pass price (card parity: the last V
        // is the tracked figure), and a symbol with no history and no
        // live price contributes nothing - to the value AND to the flow.
        val history = mapOf("ETH" to listOf(d0.toEpochDay() to 20.0))
        val priced = ReturnSeriesBuilder.build(
            walletSeries = listOf(
                "ETH;$d0;3.0;0.0",
                "BTC;$d0;1.0;0.0",   // no history, no live price
            ),
            dailyRows = emptyList(),
            history = history,
            livePrices = mapOf("ETH" to 25.0),
            today = d1,
        )
        assertEquals(
            listOf(
                "R;$d0;60.000000;60.000000",   // 3 ETH x 20 (history); BTC unvalued
                "R;$d1;75.000000;0.000000",    // 3 ETH x 25 (LIVE pin, not the 20)
            ),
            priced.lines,
        )
    }

    @Test
    fun cashFlowSignsFollowMoneyNotTokens() {
        // Pure-fiat series: the deposit is a positive external inflow,
        // the withdrawal a negative one (fiat exact, no pricing), quiet
        // days carry the balance flat.
        val priced = ReturnSeriesBuilder.build(
            walletSeries = listOf(
                "EUR;$d0;100.0;0.0",   // deposit -> running balance 100
                "EUR;$d1;50.0;0.0",    // withdraw 50 -> running balance 50 (delta -50)
            ),
            dailyRows = emptyList(),
            history = emptyMap(),
            livePrices = emptyMap(),
            today = d2,
        )
        assertEquals(
            listOf(
                "R;$d0;100.000000;100.000000",
                "R;$d1;50.000000;-50.000000",
                "R;$d2;50.000000;0.000000",
            ),
            priced.lines,
        )
    }

    @Test
    fun internalTransferNetsToZeroCashFlow() {
        // The fiat leg minus and the token leg plus of ONE internal
        // conversion (day 2: the buffer buys 0.001 BTC at 50000 = 50
        // EUR) cancel: no external cash moved that day, so its CF is the
        // zero of the net - the point of summing the ledger.
        val history = mapOf("BTC" to listOf(d0.toEpochDay() to 50000.0))
        val priced = ReturnSeriesBuilder.build(
            walletSeries = listOf(
                "EUR;$d0;500.0;0.0",    // day 1: external deposit
                "BTC;$d1;0.001;0.0",    // day 2: the token leg (+50 EUR)
                "EUR;$d1;450.0;0.0",    // day 2: the fiat leg (-50 EUR)
            ),
            dailyRows = emptyList(),
            history = history,
            livePrices = emptyMap(),
            today = d1,
        )
        assertEquals(
            listOf(
                "R;$d0;500.000000;500.000000",   // the external deposit
                "R;$d1;500.000000;0.000000",     // internal: -50 + 50 nets
            ),
            priced.lines,
        )
    }

    @Test
    fun emptyInputYieldsNoLines() {
        val priced = ReturnSeriesBuilder.build(
            walletSeries = emptyList(),
            dailyRows = emptyList(),
            history = emptyMap(),
            livePrices = emptyMap(),
            today = d0,
        )
        assertEquals(emptyList<String>(), priced.lines)
        assertNull(priced.firstDay)
    }
}
