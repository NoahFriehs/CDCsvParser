package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import java.io.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Object to store prices for 5 mins
 *
 * Backed by a ConcurrentHashMap: the cache is written from the background
 * price-check thread and read from the core/UI threads.
 */
class PriceCache : Serializable {
    private val cache: ConcurrentHashMap<String, Cache> = ConcurrentHashMap()

    /**
     * Checks if the price of the symbol is stored
     *
     * @param symbol the symbol to be checked for
     * @return a price if it has it it else -1
     */
    fun checkCache(symbol: String): Double {
        val cacheToCheck = cache[symbol]
        if (cacheToCheck != null)
        {
            if (cacheToCheck.isOlderThanFiveMinutes) {
                FileLog.d("PriceCache", "removed cache for ${cacheToCheck.id}")
                cache.remove(symbol)
                return -1.0
            }
            return cacheToCheck.price
        }
        return -1.0
    }

    fun testCache(symbol: String): Boolean {
        return cache.containsKey(symbol) && !cache[symbol]!!.isOlderThanFiveMinutes
    }

    /**
     * Adds a price to the cache
     *
     * @param symbol the symbol of the price
     * @param price the price to be added
     */
    fun addPrice(symbol: String, price: Double) {
        val cacheToAdd = Cache(symbol, price)
        cache[symbol] = cacheToAdd
        FileLog.d("PriceCache", "added cache for ${cacheToAdd.id}")
    }

    /**
     * Returns the last stored price even if it is past the 5-minute TTL.
     * UI rows fall back to it instead of showing 0.0 while a background
     * refresh is running.
     *
     * @return the stale price, or null if the symbol was never cached
     */
    fun getStale(symbol: String): Double? = cache[symbol]?.price

    /**
     * @return true if no cache entry is within the TTL anymore (empty cache
     * or everything older than five minutes)
     */
    fun isStale(): Boolean = cache.values.none { !it.isOlderThanFiveMinutes }

    /**
     * Returns a snapshot of the cached symbols.
     *
     * @return all symbols that currently have a cache entry (may be stale)
     */
    fun keys(): List<String> = cache.keys.toList()

    fun reloadCache(assetValue: AssetValue) {
        cache.forEach {
            val price = assetValue.getPrice(it.key)
            if (price != 0.0) {
                cache[it.key] = Cache(it.key, price)
            }
        }
    }


}


/**
 * Cache class to store a symbol with the price and the time of creation
 */
class Cache(id: String?, price: Double) : Serializable {
    val id: String?
    val price: Double
    private val creationTime: Instant

    init {
        this.id = id
        this.price = price
        creationTime = Instant.now()
    }

    /**
     * Checks if the object is older than five minutes
     *
     * @return true if it is older than five minutes
     */
    val isOlderThanFiveMinutes: Boolean
        get() = Instant.now().isAfter(creationTime.plusSeconds(300))
}