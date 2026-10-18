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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests the two-phase price pass on [AssetValue]: the synchronous
 * cache read ([AssetValue.getPricesFromCache]) is what the parse price
 * pass posts first on the core thread, and
 * [AssetValue.fetchPricesBulkAsync] drives the background fetch whose
 * completion re-posts the fresh values.
 */
class AssetValuePricePassTest {

    private class RecordingProvider : BaseCryptoPrices() {
        val bulkCalls = AtomicInteger(0)
        val requested = Collections.synchronizedList(mutableListOf<String>())

        @Volatile
        var result: Map<String, Double> = emptyMap()

        /** When non-null, [getPricesBulk] blocks on it (holds a fetch in flight). */
        @Volatile
        var block: (() -> Unit)? = null

        override fun getPrice(symbol: String): Double? = result[symbol]

        override fun getPricesBulk(symbols: List<String>): Map<String, Double> {
            bulkCalls.incrementAndGet()
            requested.addAll(symbols)
            block?.invoke()
            return result
        }
    }

    private val assetValue = AssetValue.getInstance()
    private val provider = RecordingProvider()

    @Before
    fun setUp() {
        // AssetValue is a process-wide singleton: reset everything the
        // previous test could have left behind.
        AssetValue.clearPriceCache()
        assetValue.priceProvider = provider
        assetValue.isConnected = true
        assetValue.isRunning = true
        provider.result = mapOf("BTC" to 100_000.0)
        provider.block = null
    }

    // -- phase 1: getPricesFromCache ------------------------------------

    @Test
    fun `cache read pins EUR and serves fresh entries`() {
        assetValue.cache.addPrice("BTC", 50_000.0)
        val prices = assetValue.getPricesFromCache(listOf("EUR", "BTC", "UNSEEN"))
        assertEquals(1.0, prices["EUR"]!!, 0.0)
        assertEquals(50_000.0, prices["BTC"]!!, 0.0)
        assertFalse("unseen symbols must be absent, not 0.0", prices.containsKey("UNSEEN"))
    }

    @Test
    fun `stale entries are still served as known prices`() {
        assetValue.cache.addPrice("BTC", 50_000.0, Instant.now().minusSeconds(301))
        val prices = assetValue.getPricesFromCache(listOf("BTC"))
        assertEquals(50_000.0, prices["BTC"]!!, 0.0)
    }

    @Test
    fun `a zero entry is a known price`() {
        assetValue.cache.addPrice("ZED", 0.0)
        val prices = assetValue.getPricesFromCache(listOf("ZED"))
        assertEquals(0.0, prices["ZED"]!!, 0.0)
    }

    // -- phase 2: fetchPricesBulkAsync ----------------------------------

    @Test
    fun `all fresh symbols neither call the provider nor fire the callback`() {
        assetValue.cache.addPrice("BTC", 100_000.0)
        var completed = 0
        assetValue.fetchPricesBulkAsync(listOf("EUR", "BTC")) { completed++ }
        assertEquals(0, provider.bulkCalls.get())
        assertEquals(0, completed)
    }

    @Test
    fun `only symbols without a fresh entry are fetched, then the callback fires`() {
        assetValue.cache.addPrice("BTC", 100_000.0)
        provider.result = mapOf("ETH" to 3_000.0)
        val done = CountDownLatch(1)
        assetValue.fetchPricesBulkAsync(listOf("EUR", "BTC", "ETH")) { done.countDown() }
        assertTrue("fetch must complete", done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("ETH"), provider.requested.toList())
        assertEquals(3_000.0, assetValue.cachedPrice("ETH")!!, 0.0)
    }

    @Test
    fun `overlapping fires result in a single provider call`() {
        val gate = CountDownLatch(1)
        provider.block = { gate.await(10, TimeUnit.SECONDS) }
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        assetValue.fetchPricesBulkAsync(listOf("BTC")) { first.countDown() }
        // The in-flight flag is set before the executor starts, so this
        // fire is a deterministic no-op.
        assetValue.fetchPricesBulkAsync(listOf("BTC")) { second.countDown() }
        gate.countDown()
        assertTrue("first fetch must complete", first.await(5, TimeUnit.SECONDS))
        assertFalse("overlapping fire must no-op without a callback", second.await(1, TimeUnit.SECONDS))
        assertEquals(1, provider.bulkCalls.get())
    }

    @Test
    fun `a failed bulk fetch marks isRunning false, caches nothing, still fires the callback`() {
        provider.result = emptyMap()
        val done = CountDownLatch(1)
        assetValue.fetchPricesBulkAsync(listOf("BTC")) { done.countDown() }
        assertTrue("callback must fire although the fetch failed", done.await(5, TimeUnit.SECONDS))
        assertFalse("empty bulk means the provider is unreachable", assetValue.isRunning)
        assertFalse(
            "a failed fetch must not manufacture 0.0 prices",
            assetValue.getPricesFromCache(listOf("BTC")).containsKey("BTC"),
        )
    }

    @Test
    fun `a provider answer of zero is cached as a known zero`() {
        provider.result = mapOf("ZED" to 0.0)
        val done = CountDownLatch(1)
        assetValue.fetchPricesBulkAsync(listOf("ZED")) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(assetValue.isRunning)
        assertEquals(0.0, assetValue.getPricesFromCache(listOf("ZED"))["ZED"]!!, 0.0)
    }

    @Test
    fun `an offline fire is a no-op for the offline backstop`() {
        assetValue.isConnected = false
        var completed = false
        assetValue.fetchPricesBulkAsync(listOf("BTC")) { completed = true }
        assertEquals(0, provider.bulkCalls.get())
        assertFalse(completed)
    }

    @Test
    fun `a stale-only cache still counts as missing (refresh path)`() {
        assetValue.cache.addPrice("BTC", 100_000.0, Instant.now().minusSeconds(301))
        provider.result = mapOf("BTC" to 101_000.0)
        val done = CountDownLatch(1)
        assetValue.fetchPricesBulkAsync(listOf("BTC")) { done.countDown() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("BTC"), provider.requested.toList())
        assertEquals(101_000.0, assetValue.cachedPrice("BTC")!!, 0.0)
    }
}
