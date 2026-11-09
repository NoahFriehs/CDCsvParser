package at.msd.friehs_bicha.cdcsvparser.price

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pins the price fallback order: fresh (TTL) -> stale (any age) ->
 * static baseline -> 0.0, against a fake cache + fake static table
 * (the JVM cannot reach the JNI canon; the chain itself is pure).
 * Also pins the result-based connectivity flag: a failed fetch flips
 * it offline, a 429 rate-limit does not.
 */
class PriceFallbackTest {

    /** Fake static baseline table (the C++ canon stand-in). */
    private val fakeStatic = mapOf(
        "XLT" to 42.0,        // known, positive baseline
        "ZZR" to 1.5,         // known, positive baseline
        "ZEROBASE" to 0.0,    // curated "no trustworthy price" entry
    )

    /** Recording provider: empty bulk = fetch failure, [rateLimited] = a 429 cause. */
    private class FakeProvider : BaseCryptoPrices() {
        @Volatile
        var bulkResult: Map<String, Double> = emptyMap()

        @Volatile
        var rateLimited: Boolean = false

        override fun getPrice(symbol: String): Double? = bulkResult[symbol]

        override fun getPricesBulk(symbols: List<String>): Map<String, Double> = bulkResult

        override fun isRateLimited(): Boolean = rateLimited
    }

    private val assetValue = AssetValue.getInstance()
    private val provider = FakeProvider()

    @Before
    fun setUp() {
        // AssetValue is a process-wide singleton: reset everything the
        // previous test could have left behind.
        AssetValue.clearPriceCache()
        assetValue.priceProvider = provider
        assetValue.staticLookup = { symbol -> fakeStatic[symbol] ?: 0.0 }
        assetValue.isConnected = true
        assetValue.isRunning = true
        provider.bulkResult = emptyMap()
        provider.rateLimited = false
    }

    // -- the pure hierarchy (the order itself) --------------------------

    @Test
    fun `fresh wins over stale and static (regression)`() {
        assertEquals(100.0, priceFallback(100.0, 90.0, 80.0), 0.0)
        assertEquals(0.0, priceFallback(0.0, 90.0, 80.0), 0.0) // fresh 0.0 still wins
    }

    @Test
    fun `stale (any age) beats the static baseline`() {
        assertEquals(90.0, priceFallback(null, 90.0, 80.0), 0.0)
    }

    @Test
    fun `a stored zero beats the static baseline`() {
        // The endpoint's explicit "no value" answer is not an unknown:
        // it keeps pricing at 0.0, not at an invented baseline.
        assertEquals(0.0, priceFallback(null, 0.0, 80.0), 0.0)
    }

    @Test
    fun `static baseline is used only when nothing was cached`() {
        assertEquals(80.0, priceFallback(null, null, 80.0), 0.0)
        assertEquals(0.0, priceFallback(null, null, 0.0), 0.0) // unknown symbol
    }

    // -- getPrice on the UI branch (JVM: the Looper check takes the
    // main-thread arm, which is exactly the cache-only path) ------------

    @Test
    fun `an expired entry degrades to the stale price, not the static baseline`() {
        assetValue.cache.addPrice("ZZR", 7.25, Instant.now().minusSeconds(90 * 60))
        assertEquals(7.25, assetValue.getPrice("ZZR"), 0.0)
    }

    @Test
    fun `a never-cached symbol degrades to its static baseline`() {
        assertEquals(42.0, assetValue.getPrice("XLT"), 0.0)
    }

    @Test
    fun `a never-cached unknown symbol prices at zero`() {
        assertEquals(0.0, assetValue.getPrice("TOTALLYUNKNOWN"), 0.0)
    }

    @Test
    fun `a stored no-value entry (zero price, any age) beats the static baseline`() {
        assetValue.cache.addPrice("ZZR", 0.0, Instant.now().minusSeconds(90 * 60))
        assertEquals(0.0, assetValue.getPrice("ZZR"), 0.0)
    }

    // -- pass phase 1: getPricesFromCache --------------------------------

    @Test
    fun `phase 1 fills never-cached symbols from the static table`() {
        val prices = assetValue.getPricesFromCache(listOf("XLT", "ZZR", "NOPE"))
        assertEquals(42.0, prices["XLT"]!!, 0.0)
        assertEquals(1.5, prices["ZZR"]!!, 0.0)
        assertFalse("an unknown symbol stays missing (no invented price)", prices.containsKey("NOPE"))
    }

    @Test
    fun `a curated zero table entry stays missing like an unknown`() {
        val prices = assetValue.getPricesFromCache(listOf("ZEROBASE"))
        assertFalse(prices.containsKey("ZEROBASE"))
    }

    @Test
    fun `a cached entry (fresh, stale, or zero) beats the static table in phase 1`() {
        assetValue.cache.addPrice("XLT", 33.0, Instant.now().minusSeconds(6 * 60))
        assetValue.cache.addPrice("ZZR", 0.0)
        val prices = assetValue.getPricesFromCache(listOf("XLT", "ZZR"))
        assertEquals(33.0, prices["XLT"]!!, 0.0) // stale beats the table
        assertEquals(0.0, prices["ZZR"]!!, 0.0)  // stored 0.0 beats the table
    }

    // -- the offline note -------------------------------------------------

    @Test
    fun `the note is absent while online`() {
        assetValue.cache.addPrice("ZZR", 1.0, Instant.now().minusSeconds(90 * 60))
        assertNull(assetValue.offlinePriceNote(listOf("ZZR", "XLT")))
    }

    @Test
    fun `the note names the oldest cached price in use (any age) when offline`() {
        assetValue.isConnected = false
        assetValue.cache.addPrice("ZZR", 1.0, Instant.now().minusSeconds(90 * 60))
        assetValue.cache.addPrice("EURX", 2.0) // fresh
        val note = assetValue.offlinePriceNote(listOf("ZZR", "EURX", "XLT"))!!
        assertTrue(note.usesStaticBaseline)          // XLT fell back to the table
        assertTrue(note.oldestAgeMinutes in 88..92)  // the 90-min-old entry sets the era
    }

    @Test
    fun `the note distinguishes a fully static valuation from a cached one`() {
        assetValue.isConnected = false
        val note = assetValue.offlinePriceNote(listOf("XLT", "ZZR"))!!
        assertTrue(note.usesStaticBaseline)
        assertNull(note.oldestAgeMinutes)            // nothing cached at all
    }

    @Test
    fun `the note is absent when offline but nothing degraded was used`() {
        assetValue.isConnected = false
        // Only unknown symbols: no cache entry, no table price.
        assertNull(assetValue.offlinePriceNote(listOf("NOPE", "NOPEX")))
    }

    // -- the connectivity flag writer (result-based, 429 untouched) ------

    @Test
    fun `a failed bulk fetch marks the app offline`() {
        provider.bulkResult = emptyMap()
        val done = CountDownLatch(1)
        assetValue.fetchPricesBulkAsync(listOf("NOPE")) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertFalse(assetValue.isConnected)
    }

    @Test
    fun `a 429 rate-limited failure does NOT mark the app offline`() {
        provider.bulkResult = emptyMap()
        provider.rateLimited = true
        val done = CountDownLatch(1)
        assetValue.fetchPricesBulkAsync(listOf("NOPE")) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue("rate-limited is not offline: the flag must not flip", assetValue.isConnected)
        assertFalse(assetValue.isRunning)            // the failure itself is recorded
    }

    @Test
    fun `a successful probe flips the flag back online`() {
        // The offline recovery loop probes via check(); a successful
        // answer resets the connection flag.
        assetValue.isConnected = false
        provider.bulkResult = mapOf("BTC" to 50_000.0)
        assetValue.check()
        var waitedMs = 0
        while (!assetValue.isConnected && waitedMs < 5000) {
            Thread.sleep(50)
            waitedMs += 50
        }
        assertTrue(assetValue.isConnected)
        assertTrue(assetValue.isRunning)
        assertEquals(50_000.0, assetValue.cachedPrice("BTC")!!, 0.0)
    }
}
