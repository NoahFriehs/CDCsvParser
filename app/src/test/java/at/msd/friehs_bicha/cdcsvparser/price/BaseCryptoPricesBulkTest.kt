package at.msd.friehs_bicha.cdcsvparser.price

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the default [BaseCryptoPrices.getPricesBulk] implementation, which
 * falls back to a per-symbol [BaseCryptoPrices.getPrice] loop.
 */
class BaseCryptoPricesBulkTest {

    private class FixedProvider : BaseCryptoPrices() {
        private val prices = mapOf("BTC" to 100_000.0, "ZED" to 0.0) // 0.0 = known zero

        override fun getPrice(symbol: String): Double? = prices[symbol]
    }

    @Test
    fun `bulk falls back to per-symbol lookups`() {
        val provider = FixedProvider()
        val result = provider.getPricesBulk(listOf("BTC", "ZED", "MYSTERY"))

        assertEquals(100_000.0, result["BTC"]!!, 0.0)
        assertEquals(0.0, result["ZED"]!!, 0.0) // known zero is a real entry
        assertFalse("no price must be a missing key, not an entry", result.containsKey("MYSTERY"))
    }

    @Test
    fun `empty bulk returns empty map`() {
        assertTrue(FixedProvider().getPricesBulk(emptyList()).isEmpty())
    }
}
