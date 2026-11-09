package at.msd.friehs_bicha.cdcsvparser.price

import android.os.Looper
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.core.OfflinePriceNote
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The pinned price fallback hierarchy (pure, so the JVM tests can pin
 * the order without the executor / Looper / provider machinery):
 * fresh (5-minute TTL) -> stale (any age) -> static baseline -> 0.0.
 * A stored 0.0 (the endpoint's explicit "no value" answer) counts as
 * the stale entry and beats the static table: a symbol the provider
 * says is priceless keeps pricing at 0.0, not at an invented baseline.
 *
 * @param fresh a cache entry still inside the TTL, if any
 * @param stale the last stored entry of any age, if any (may be 0.0)
 * @param staticBaseline the static table's price (0.0 = unknown symbol)
 * @return the first available level of the hierarchy
 */
internal fun priceFallback(
    fresh: Double?,
    stale: Double?,
    staticBaseline: Double,
): Double = fresh ?: stale ?: staticBaseline

/**
 * Asset value class
 *
 * Price lookups that touch the network run on a shared daemon executor.
 * The volatile flags keep success/failure state consistent across threads.
 */
class AssetValue private constructor() : Serializable {
    internal val cache = PriceCache()

    /**
     * The newest known (possibly stale) price for [symbol] WITHOUT any
     * network access; null if the symbol is unknown to the cache.
     */
    fun cachedPrice(symbol: String): Double? = cache.getStale(symbol)
            ?.takeIf { it.isFinite() && it > 0.0 }
    @Volatile
    var isConnected = true
    @Volatile
    var isRunning: Boolean = true

    var priceProvider: BaseCryptoPrices = CryptoPricesCryptoCompare()

    /**
     * Memoized static baseline table (the C++ canon via the JNI binding):
     * the table is read-only for the process lifetime, so a per-symbol
     * memo is exact. 0.0 = unknown symbol.
     */
    private val staticPriceMemo = ConcurrentHashMap<String, Double>()

    /**
     * The static baseline lookup behind [staticPrice] (test seam: the
     * real one is the JNI binding into the C++ canon; the JVM tests
     * substitute a fake table, exactly like they do for [priceProvider]).
     */
    internal var staticLookup: (String) -> Double =
        { symbol -> CoreService.staticBaselinePrice(symbol) }

    /**
     * The static baseline EUR price for [symbol] from the C++ canon
     * (single source of the 144-symbol offline table); 0.0 when unknown.
     */
    private fun staticPrice(symbol: String): Double =
        staticPriceMemo.getOrPut(symbol) { staticLookup(symbol) }

    /**
     * The connectivity flag writer (result-based): a failed provider
     * fetch marks the app offline UNLESS the provider is in its 429
     * cooldown/blackout - a rate limit is not an outage, and the
     * existing cooldown semantics stay untouched either way. Successful
     * fetches reset the flag to true at their own call sites.
     */
    private fun markOfflineUnlessRateLimited() {
        if (priceProvider.isRateLimited()) {
            FileLog.e(
                "AssetValue",
                "Provider rate-limited (429 cooldown active); connectivity flag stays untouched."
            )
            return
        }
        isConnected = false
    }

    companion object {
        @Volatile
        private var instance: AssetValue? = null

        /**
         * Shared single daemon thread for price checks (network calls).
         */
        private val executor: ExecutorService = Executors.newSingleThreadExecutor {
            Thread(it, "price-check").apply { isDaemon = true }
        }

        /**
         * Returns the running instance of AssetValue
         *
         * @return a instance of AssetValue
         */
        @JvmStatic
        fun getInstance(): AssetValue {
            // Double-checked locking; the instance is final after construction.
            return instance
                ?: synchronized(AssetValue::class.java) {
                    instance
                        ?: AssetValue().also { instance = it }
                }
        }

        /**
         * Drops the in-memory 5-minute price cache (and the static
         * baseline memo - harmless for the read-only table) for the
         * Settings "delete cache" feature. The on-disk JSON caches live
         * in the price package and are deleted by the caller.
         */
        @JvmStatic
        fun clearPriceCache() {
            val instance = getInstance()
            instance.cache.clear()
            instance.staticPriceMemo.clear()
        }
    }

    /**
     * Returns the price of the entered symbol
     *
     * @param symbol_ the symbol for which the price is needed
     * @return the price of the symbol or 0 if an error occurred
     */
    fun getPrice(symbol_: String): Double {
        if (symbol_ == "EUR") return 1.0 //euro is always 1, replace with api if needed

        if (cache.testCache(symbol_)) {
            return cache.checkCache(symbol_)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Row-building on the UI thread must never hit the network (a
            // cache-cold file with many symbols would be one API call per
            // row). The price comes from the bulk pass or later checks; in
            // the TTL gap between passes the stale value is shown instead
            // of 0.0 (a 0.0 price reads as a -100% position), and a never-
            // cached symbol degrades to its static baseline before the 0.0.
            val price = priceFallback(null, cache.getStale(symbol_), staticPrice(symbol_))
            FileLog.d("AssetValue", "No fresh price for $symbol_ on the main thread; fallback chain yields $price.")
            return price
        }

        if (!isConnected) {
            FileLog.e("AssetValue", "No internet connection")
            isRunning = false
            // Offline: the same hierarchy as everywhere else (the fresh
            // check above already returned; stale of any age beats the
            // static baseline, which beats nothing).
            return priceFallback(null, cache.getStale(symbol_), staticPrice(symbol_))
        }

        when (val priceApi = priceProvider.getPrice(symbol_)) {
            null -> {
                isRunning = false
                markOfflineUnlessRateLimited()
                // Provider error: degrade through the hierarchy (stale
                // -> static -> 0.0) instead of a bare 0.0.
                return priceFallback(null, cache.getStale(symbol_), staticPrice(symbol_))
            }

            0.0 -> {
                // does not exist at API-Endpoint - an authoritative "no
                // value" answer: store it (it then counts as a stale 0.0
                // entry and beats the static table), and the connection
                // flag comes back up (the endpoint answered).
                cache.addPrice(symbol_, priceApi)
                isConnected = true
                return 0.0
            }

            -1.0 -> {
                FileLog.e("AssetValue", "API error")
                isRunning = false
                markOfflineUnlessRateLimited()
                return priceFallback(null, cache.getStale(symbol_), staticPrice(symbol_))
            }

            else -> {
                cache.addPrice(symbol_, priceApi)
                isConnected = true
                isRunning = true
                return priceApi
            }
        }
    }

    /**
     * @return true if the price cache still holds at least one entry within
     * the 5-minute TTL (i.e. per-row price lookups return fresh values)
     */
    fun hasFreshPrices(): Boolean = !cache.isStale()

    /** @return the symbols currently in the price cache (may be stale) */
    fun cacheKeys(): List<String> = cache.keys()

    /**
     * Returns the newest known price of the given symbols WITHOUT any
     * network access (phase 1 of the parse price pass).
     *
     * "EUR" is pinned to 1.0. A symbol is priced with its fresh cache
     * entry or - in the TTL gap between refreshes - with its stale entry
     * (stale prices are shown by design, never a blank). A symbol with
     * no cache entry at all falls back to the static baseline; a 0.0
     * entry counts as known (the endpoint answered "no value for this
     * symbol") and beats the static table.
     *
     * @param symbols the symbols for which prices are needed
     * @return a map from symbol to price; a missing key = no known price
     * (no cache entry AND no usable static table entry)
     */
    fun getPricesFromCache(symbols: List<String>): Map<String, Double> {
        val prices = LinkedHashMap<String, Double>()
        for (symbol in symbols) {
            when {
                symbol == "EUR" -> prices[symbol] = 1.0
                cache.testCache(symbol) -> prices[symbol] = cache.checkCache(symbol)
                else -> cache.getStale(symbol)?.let { prices[symbol] = it }
                    ?: run {
                        // No cache entry at all: the static baseline.
                        // A table price of 0.0 is a curated "no
                        // trustworthy price" (like an unknown symbol)
                        // and stays missing.
                        val static = staticPrice(symbol)
                        if (static > 0.0) prices[symbol] = static
                    }
            }
        }
        return prices
    }

    /**
     * The offline valuation note for [symbols] (the held currencies):
     * null when the connection flag says online, or when no degraded
     * price is in use. A cache entry of ANY age counts as "in use" (that
     * is the point of the stale layer); a symbol with no cache entry at
     * all counts as static-filled when the baseline table knows it with
     * a > 0.0 price. Call it from the post-parse paths (the cpp-core
     * thread): the static memo's miss path is a BLOCKING hop to that
     * thread (the JNI rule), so an off-thread call would block the
     * caller for up to the 30-second hop timeout.
     */
    fun offlinePriceNote(symbols: List<String>): OfflinePriceNote? {
        if (isConnected) return null
        val held = symbols.filter { it != "EUR" }
        val cacheUsed = held.filter { cache.getStale(it) != null }
        val staticUsed = held.filter { cache.getStale(it) == null && staticPrice(it) > 0.0 }
        if (cacheUsed.isEmpty() && staticUsed.isEmpty()) return null
        val oldestMs = cache.creationTimesFor(cacheUsed).minOrNull()
            ?.let { System.currentTimeMillis() - it.toEpochMilli() }
        return OfflinePriceNote(
            oldestAgeMinutes = oldestMs?.let { (it / 60_000L).toInt() },
            usesStaticBaseline = staticUsed.isNotEmpty(),
        )
    }

    /** One bulk fetch at a time; overlapping fires are no-ops. */
    @Volatile
    private var fetchInFlight = false

    /**
     * Fetches the prices of the symbols that have no FRESH cache entry
     * with one bulk provider call on the background executor, then
     * invokes [onCompleted] so the caller can re-post re-priced data.
     *
     * Stale entries are shown to the user by design during the TTL gap,
     * but they count as missing here so a re-run of the price pass
     * refreshes them. No-op WITHOUT [onCompleted] when there is nothing
     * to fetch (nothing changed - no re-post needed), when the network
     * flag says offline (the offline retry loop re-runs the pass), or
     * when a fetch is already in flight (its completion re-posts).
     *
     * @param symbols the symbols to check ("EUR" is never fetched)
     * @param onCompleted invoked on the price-check thread AFTER the
     * fetch finished, success or failure
     */
    fun fetchPricesBulkAsync(symbols: List<String>, onCompleted: () -> Unit) {
        val missing = symbols.filter { it != "EUR" && !cache.testCache(it) }.distinct()
        if (missing.isEmpty()) {
            // Everything is fresh: nothing changed, no re-post needed.
            return
        }
        if (!isConnected) {
            FileLog.e("AssetValue", "No internet connection")
            return
        }
        if (fetchInFlight) {
            // Set before the executor call, so overlapping fires from the
            // parse pass and the stale-price refresh cannot double-fetch.
            return
        }
        fetchInFlight = true
        executor.execute {
            try {
                val bulk = priceProvider.getPricesBulk(missing)
                if (bulk.isEmpty()) {
                    // Network failure: cache NOTHING. A failed fetch must
                    // not manufacture 0.0 prices that the re-post would
                    // turn into 0.00 card totals; the previous cache state
                    // stays the display fallback.
                    FileLog.e("AssetValue", "Bulk price fetch failed; keeping the previous cache state.")
                    isRunning = false
                    markOfflineUnlessRateLimited()
                } else {
                    // Cache every requested symbol: ones without a price
                    // are stored as 0.0 ("no value at endpoint") so the
                    // re-post and the per-wallet lookups afterwards are
                    // pure cache hits (see the main-thread guard in
                    // [getPrice]).
                    missing.forEach { symbol ->
                        cache.addPrice(symbol, bulk[symbol] ?: 0.0)
                    }
                    isConnected = true
                    isRunning = true
                }
            } catch (e: Exception) {
                FileLog.e("AssetValue", "Bulk price fetch failed: $e")
                isRunning = false
                markOfflineUnlessRateLimited()
            } finally {
                fetchInFlight = false
            }
            onCompleted()
        }
    }

    /**
     * Loads the prices of the given symbols with one bulk provider call on
     * the background executor. Symbols already covered by a fresh cache
     * entry are skipped.
     */
    fun loadCache(symbols: List<String>): Boolean {
        executor.execute {
            val missing = symbols.filter { it != "EUR" && !cache.testCache(it) }.distinct()
            if (missing.isEmpty()) return@execute
            val bulk = priceProvider.getPricesBulk(missing)
            bulk.forEach { (symbol, price) -> cache.addPrice(symbol, price) }
            if (bulk.isEmpty()) markOfflineUnlessRateLimited() else isConnected = true
            isRunning = bulk.isNotEmpty()
        }
        return isConnected && isRunning
    }

    /**
     * Reloads all cached prices with one bulk provider call on the
     * background executor. Symbols whose fresh price could not be fetched
     * keep their previous cache entry.
     */
    fun reloadCache(): Boolean {
        executor.execute {
            reloadCacheSync()
        }
        return isConnected && isRunning
    }

    /**
     * Same as [reloadCache] but runs the bulk call on the CALLING thread
     * and reports whether it actually refreshed anything. Used by the
     * WorkManager price-refresh worker (a Worker must observe its own
     * result; [reloadCache]'s fire-and-forget executor would outlive the
     * worker's lifetime).
     */
    fun reloadCacheSync(): Boolean {
        val symbols = cache.keys()
        if (symbols.isEmpty()) return false
        if (!isConnected) {
            FileLog.e("AssetValue", "No internet connection")
            isRunning = false
            return false
        }
        val bulk = priceProvider.getPricesBulk(symbols)
        bulk.forEach { (symbol, price) ->
            if (price != 0.0) cache.addPrice(symbol, price)
        }
        if (bulk.isEmpty()) markOfflineUnlessRateLimited() else isConnected = true
        isRunning = bulk.isNotEmpty()
        return bulk.isNotEmpty()
    }

    /**
     * Checks network connectivity by fetching a probe price on the
     * background executor.
     */
    fun check() {
        executor.execute {
            when (val priceApi = priceProvider.getPricesBulk(listOf("BTC"))["BTC"]) {
                null -> {
                    FileLog.e("AssetValue", "API error")
                    isRunning = false
                    markOfflineUnlessRateLimited()
                }

                0.0 -> {
                    FileLog.e("AssetValue", "No price found for: BTC")
                    // The endpoint still answered successfully.
                    isConnected = true
                }

                else -> {
                    cache.addPrice("BTC", priceApi)
                    isConnected = true
                    isRunning = true
                }
            }
        }
    }

    internal fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }
}
