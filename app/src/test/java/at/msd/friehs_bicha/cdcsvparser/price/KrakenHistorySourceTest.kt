package at.msd.friehs_bicha.cdcsvparser.price

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM tests for the Kraken deep-history source: `parsePage` (OHLC JSON →
 * (UTC day, close) ascending) and the `EUR_PAIRS` mapping that decides
 * which symbols Kraken serves at all. No network.
 */
class KrakenHistorySourceTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun el(s: String): JsonElement = json.parseToJsonElement(s)

    private fun result(vararg candles: String): String =
        "{\"result\":{\"XXBTZEUR\":[${candles.joinToString(",")}],\"last\":\"1672538400.000\"}}"

    private fun candle(ts: Long, close: String): String =
        "[$ts,1,2,0.5,$close,101,10,3]"

    @Test
    fun `parsePage returns day-close pairs ascending`() {
        val page = KrakenHistorySource.parsePage(
            el(result(candle(1_672_531_200, "100.25"), candle(1_672_534_800, "50.5")))
        )
        // 1672531200 = 2023-01-01 00:00 UTC → day 19358; +3600 s is the same
        // day (intraday offsets floor to the day; the caller dedups by day).
        assertEquals(listOf<Pair<Long, Double>>(19_358L to 100.25, 19_358L to 50.5), page)
    }

    @Test
    fun `parsePage keeps full precision via the literal string`() {
        // toLong on a fractional number would truncate small caps to 0; the
        // code goes through contentOrNull.to*OrNull instead.
        val page = KrakenHistorySource.parsePage(el(result(candle(1_672_531_200, "0.7603746"))))
        assertEquals(listOf(19_358L to 0.7603746), page)
    }

    @Test
    fun `parsePage skips short and malformed candles`() {
        val page = KrakenHistorySource.parsePage(
            el(
                result(
                    "[1672531200,1,2,3]",                     // < 5 fields
                    "[\"x\",1,2,0.5,5,1,1,1]",                // bad timestamp
                    "[1672531200,1,2,0.5,\"abc\",1,1,1]",     // bad close
                    "[1672531200,1,2,0.5,-1,1,1,1]",          // negative close
                    candle(1_672_531_200, "5.5"),             // the only survivor
                )
            )
        )
        assertEquals(listOf(19_358L to 5.5), page)
    }

    @Test
    fun `parsePage returns null for unknown shapes`() {
        assertNull(KrakenHistorySource.parsePage(el("{}")))                       // no result
        assertNull(KrakenHistorySource.parsePage(el("{\"result\":{\"last\":\"1\"}}")))  // no pair key
        assertNull(KrakenHistorySource.parsePage(el("{\"result\":{\"XXBTZEUR\":null}}"))) // no array
    }

    @Test
    fun `EUR_PAIRS covers exactly the 14 Kraken EUR pairs`() {
        assertEquals(14, KrakenHistorySource.EUR_PAIRS.size)
        assertEquals("XXBTZEUR", KrakenHistorySource.EUR_PAIRS["BTC"])
        assertEquals("XETHZEUR", KrakenHistorySource.EUR_PAIRS["ETH"])
        assertEquals("XXMRZEUR", KrakenHistorySource.EUR_PAIRS["XMR"])
        assertEquals("2ZEUR", KrakenHistorySource.EUR_PAIRS["2Z"])
        // Unmapped symbols hand off to the next chain source, never a null deref.
        assertNull(KrakenHistorySource.EUR_PAIRS["DOGE"])
        assertNull(KrakenHistorySource.EUR_PAIRS["btc"])   // the caller uppercases
    }
}
