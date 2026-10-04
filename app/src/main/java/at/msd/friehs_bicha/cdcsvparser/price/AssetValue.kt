package at.msd.friehs_bicha.cdcsvparser.price

import android.os.Looper
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import java.io.Serializable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Asset value class
 *
 * Price lookups that touch the network run on a shared daemon executor.
 * The volatile flags keep success/failure state consistent across threads.
 */
class AssetValue private constructor() : Serializable {
    private val cache = PriceCache()

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
            // the TTL gap between passes, the stale value is shown instead
            // of 0.0 (a 0.0 price reads as a -100% position).
            val stale = cache.getStale(symbol_)
            if (stale != null) {
                FileLog.d("AssetValue", "No fresh price for $symbol_ on the main thread; using stale $stale.")
                return stale
            }
            FileLog.d("AssetValue", "No price known for $symbol_ on the main thread; using 0.0.")
            return 0.0
        }

        if (!isConnected) {
            FileLog.e("AssetValue", "No internet connection")
            isRunning = false
            return 0.0
        }

        when (val priceApi = priceProvider.getPrice(symbol_)) {
            null -> {
                isRunning = false
                return 0.0
            }

            0.0 -> {
                // does not exist at API-Endpoint
                cache.addPrice(symbol_, priceApi)
                return 0.0
            }

            -1.0 -> {
                FileLog.e("AssetValue", "API error")
                isRunning = false
                return 0.0
            }

            else -> {
                cache.addPrice(symbol_, priceApi)
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
     * Returns the prices of the given symbols on the calling thread.
     *
     * Fresh cache entries are served directly; everything else is resolved
     * with one bulk provider call and cached for five minutes.
     *
     * @param symbols the symbols for which prices are needed ("EUR" is 1.0)
     * @return a map from symbol to price; a missing key means no price is known
     */
    fun getPricesBulk(symbols: List<String>): Map<String, Double> {
        val prices = LinkedHashMap<String, Double>()
        val missing = linkedSetOf<String>()
        for (symbol in symbols) {
            when {
                symbol == "EUR" -> prices[symbol] = 1.0
                cache.testCache(symbol) -> prices[symbol] = cache.checkCache(symbol)
                else -> missing.add(symbol)
            }
        }
        if (missing.isEmpty()) return prices
        if (!isConnected) {
            FileLog.e("AssetValue", "No internet connection")
            isRunning = false
            return prices
        }
        val bulk = priceProvider.getPricesBulk(missing.toList())
        // Cache every requested symbol: ones without a price are stored as
        // 0.0 so per-wallet lookups afterwards are pure cache hits instead
        // of one network call per row (see the main-thread guard in
        // [getPrice]).
        missing.forEach { symbol ->
            val price = bulk[symbol] ?: 0.0
            cache.addPrice(symbol, price)
            prices[symbol] = price
        }
        isRunning = bulk.isNotEmpty()
        return prices
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
                }

                0.0 -> {
                    FileLog.e("AssetValue", "No price found for: BTC")
                }

                else -> {
                    cache.addPrice("BTC", priceApi)
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
