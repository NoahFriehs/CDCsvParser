package at.msd.friehs_bicha.cdcsvparser.price

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the deep-history chain: per-source response parsing and
 * pagination cursors (no network), and the chain's ordering / budget
 * semantics. The parse fixtures are real reply shapes captured 2026-10-08.
 */
class DeepHistoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun el(s: String): JsonElement = json.parseToJsonElement(s)

    // ------------------------------------------------ Bitstamp

    @Test
    fun `bitstamp page parses days and closes from string fields`() {
        val page = BitstampHistorySource.parsePage(
            el(
                "{\"data\":{\"pair\":\"BTC/EUR\",\"ohlc\":[" +
                    "{\"timestamp\":\"1609459200\",\"open\":\"11000\",\"high\":\"11400\"," +
                    "\"low\":\"10900\",\"close\":\"11234.56\",\"volume\":\"1.2\"}," +
                    "{\"timestamp\":\"1609545600\",\"open\":\"11234.56\",\"high\":\"12000\"," +
                    "\"low\":\"11100\",\"close\":\"11987.01\",\"volume\":\"2.3\"}" +
                    "]}}"
            )
        )
        assertEquals(
            listOf((1609459200L / 86_400L) to 11234.56, (1609545600L / 86_400L) to 11987.01),
            page,
        )
    }

    @Test
    fun `bitstamp page rejects malformed and unsigned shapes`() {
        assertNull(BitstampHistorySource.parsePage(el("{\"code\":\"validation-error\"}")))
        assertNull(BitstampHistorySource.parsePage(el("{\"data\":{\"ohlc\":[]}}")))
        assertNull(
            BitstampHistorySource.parsePage(
                el("{\"data\":{\"ohlc\":[{\"timestamp\":\"1609459200\",\"close\":\"-5\"}]}}")
            )
        )
    }

    // ------------------------------------------------ Kraken

    @Test
    fun `kraken page parses close at index 4 with full precision`() {
        val page = KrakenHistorySource.parsePage(
            el(
                "{\"error\":[],\"result\":{\"XXBTZEUR\":[" +
                    "[1609459200, 11000.0, 11400.0, 10900.0, 11234.5678, 11100.0, 1.5, 70]," +
                    "[1609545600, 11234.5678, 12000.0, 11100.0, 11987.0123, 11600.0, 2.5, 90]" +
                    "],\"last\":1609545600}}"
            )
        )
        assertEquals(
            listOf((1609459200L / 86_400L) to 11234.5678, (1609545600L / 86_400L) to 11987.0123),
            page,
        )
    }

    @Test
    fun `kraken pair map covers the 14 EUR pairs and nothing else`() {
        assertEquals("XXBTZEUR", KrakenHistorySource.EUR_PAIRS["BTC"])
        assertEquals("XETHZEUR", KrakenHistorySource.EUR_PAIRS["ETH"])
        assertEquals(14, KrakenHistorySource.EUR_PAIRS.size)   // exactly the 14 EUR pair keys
        assertNull(KrakenHistorySource.EUR_PAIRS["DOGE"])
        assertNull(KrakenHistorySource.EUR_PAIRS["SOL"])
    }

    @Test
    fun `kraken page rejects error replies`() {
        assertNull(
            KrakenHistorySource.parsePage(el("{\"error\":[\"EQuery:Unknown asset pair\"],\"result\":{}}"))
        )
    }

    // ------------------------------------------------ CryptoCompare

    @Test
    fun `cc page parses close and day from the Data array`() {
        val page = CryptoCompareHistorySource.parsePage(
            el(
                "{\"Response\":\"Success\",\"Data\":{\"Data\":[" +
                    "{\"time\":1609459200,\"high\":11400,\"low\":10900,\"open\":11000," +
                    "\"close\":11234.56,\"volumefrom\":1.2,\"volumeto\":13000," +
                    "\"conversionType\":\"direct\"}," +
                    "{\"time\":1609545600,\"high\":12000,\"low\":11100,\"open\":11234.56," +
                    "\"close\":11987.01,\"volumefrom\":2.2,\"volumeto\":24000," +
                    "\"conversionType\":\"direct\"}" +
                    "]}}"
            )
        )
        assertEquals(
            listOf((1609459200L / 86_400L) to 11234.56, (1609545600L / 86_400L) to 11987.01),
            page,
        )
    }

    @Test
    fun `cc page rejects missing Data`() {
        assertNull(CryptoCompareHistorySource.parsePage(el("{\"Response\":\"Error\"}")))
    }

    // ------------------------------------------------ chain semantics

    private class FakeSource(
        override val name: String,
        private val exhaustOnCall: Boolean = false,
        private val answer: (String, Long, Long) -> List<Pair<Long, Double>>?,
    ) : DeepHistorySource {
        override var passExhausted = false
        var calls = 0
        override fun onPassStart() {
            calls = 0
        }

        override fun dailyEur(symbol: String, fromDay: Long, toDay: Long): List<Pair<Long, Double>>? {
            if (exhaustOnCall && calls == 0) passExhausted = true
            calls++
            return answer(symbol, fromDay, toDay)
        }
    }

    private fun seriesTo(toDay: Long, days: Int) =
        List(days) { (toDay - days + 1 + it) to (100.0 + it) }

    @Test
    fun `chain walks to the first source reaching the present`() {
        val a = FakeSource("A") { _, _, toDay -> seriesTo(toDay, 3).dropLast(5) }   // ends well before today → rejected
        val b = FakeSource("B") { _, fromDay, toDay -> seriesTo(toDay, (toDay - fromDay + 1).toInt()) }
        val c = FakeSource("C") { _, fromDay, toDay -> seriesTo(toDay, (toDay - fromDay + 1).toInt()) }
        val chain = DeepHistoryChain(listOf(a, b, c))
        val result = chain.fetch("X", fromDay = 10, toDay = 20)
        assertEquals("B", result?.second)   // C must not even be tried
        assertEquals(0, c.calls)
        assertEquals(11, result?.first?.size)
    }

    @Test
    fun `exhausted source is skipped for the rest of the pass`() {
        val a = FakeSource("A", exhaustOnCall = true) { _, fromDay, toDay -> null }
        val b = FakeSource("B") { _, fromDay, toDay -> seriesTo(toDay, (toDay - fromDay + 1).toInt()) }
        val chain = DeepHistoryChain(listOf(a, b))
        assertEquals("B", chain.fetch("X", 1, 5)?.second)
        assertEquals(1, a.calls)   // a called once, then stayed out
        assertEquals("B", chain.fetch("Y", 1, 5)?.second)
        assertTrue(a.calls <= 1)
        assertFalse(a.passExhausted.not())
    }

    @Test
    fun `no usable series gives null`() {
        val a = FakeSource("A") { _, _, _ -> null }
        val b = FakeSource("B") { _, _, _ -> seriesTo(5, 3) }   // ends at day 5, today is 1000
        val chain = DeepHistoryChain(listOf(a, b))
        assertNull(chain.fetch("X", 0, 1000))
    }

    @Test
    fun `blocklisted ambiguous tickers are skipped without touching any source`() {
        val a = FakeSource("A") { _, fromDay, toDay -> seriesTo(toDay, (toDay - fromDay + 1).toInt()) }
        val chain = DeepHistoryChain(listOf(a))
        // BOOST is in CryptoPricesCryptoCompare.NO_LIVE_PRICE_SYMBOLS (the
        // 2026-10-08 incident: a deep fsym=BOOST fetch priced 471 M tokens
        // at a same-named different coin, a 764 kEUR phantom position).
        assertNull(chain.fetch("BOOST", 0, 1000))
        assertEquals(0, a.calls)   // no source may even see it
    }

    @Test
    fun `consistentWith rejects a same-ticker different coin on settled days`() {
        // old cache: 300 settled days @ 0.059 (the final closes)
        val old = (0L..299L).toList().reversed().map { d -> d to 0.059 }
        // fresh deep source: 22x on every other overlapping day (the wrong coin)
        val fresh = (50L..399L).map { day -> day to (if (day % 2L == 0L) 1.33 else 0.059) }
        assertFalse(PriceHistoryProvider.consistentWith(old, fresh))
    }

    @Test
    fun `consistentWith accepts same data, tiny real moves and sparse overlap`() {
        val base = (0L..399L).map { d -> d to (1.0 + (d % 7L) * 0.01) }
        assertTrue(PriceHistoryProvider.consistentWith(base, base))
        // a 1-day 3x jump in 400 days (e.g. rebase/split recorded once): fine
        val rebased = base.map { (day, p) -> day to (if (day == 200L) p * 3.0 else p) }
        assertTrue(PriceHistoryProvider.consistentWith(base.take(300), rebased))
        // fewer than 30 shared days: nothing to contradict yet
        val disjoint = (0L..9L).map { d -> d to 1.0 }
        val far      = (100L..2000L).map { d -> d to 99.0 }
        assertTrue(PriceHistoryProvider.consistentWith(disjoint, far))
    }

    @Test
    fun `beginPass resets the per-pass counters of sources`() {
        val a = FakeSource("A") { _, fromDay, toDay -> seriesTo(toDay, (toDay - fromDay + 1).toInt()) }
        val chain = DeepHistoryChain(listOf(a))
        chain.fetch("X", 0, 5)
        assertEquals(1, a.calls)
        chain.beginPass()
        assertEquals(0, a.calls)
    }
}
