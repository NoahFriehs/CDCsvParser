package at.msd.friehs_bicha.cdcsvparser.price

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM tests for the plan 004 key precedence. */
class PriceApiKeyTest {

    @Test
    fun `runtime key wins over the build-time key`() {
        assertEquals("user-key", PriceApiKey.resolve("user-key", "demo-key"))
    }

    @Test
    fun `blank runtime key falls back to the build-time key`() {
        assertEquals("demo-key", PriceApiKey.resolve("", "demo-key"))
        assertEquals("demo-key", PriceApiKey.resolve("   ", "demo-key"))
    }

    @Test
    fun `whitespace around the runtime key is trimmed`() {
        assertEquals("user-key", PriceApiKey.resolve("  user-key  ", ""))
    }

    @Test
    fun `both empty stays keyless`() {
        assertEquals("", PriceApiKey.resolve("", ""))
    }

    @Test
    fun `release build config is keyless - runtime key is the only channel`() {
        // BuildConfig of a release build is "" for both providers.
        assertEquals("user-key", PriceApiKey.resolve("user-key", ""))
        assertEquals("", PriceApiKey.resolve("", ""))
    }
}
