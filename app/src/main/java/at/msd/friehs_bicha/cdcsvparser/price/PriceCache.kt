package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import java.io.Serializable
import java.time.Instant

/**
 * Object to store prices for 5 mins
 */
class PriceCache() : Serializable {
    private val cache: HashMap<String, Cache> = HashMap()

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


}


/**
 * Cache class to store a symbol with the price and the time of creation
 */
class Cache(id: String?, price: Double) : Serializable {
    val id: String?
    val price: Double
    private val creationTime: Instant = Instant.now()

    init {
        this.id = id
        this.price = price
    }

    /**
     * Checks if the object is older than five minutes
     *
     * @return true if it is older than five minutes
     */
    val isOlderThanFiveMinutes: Boolean
        get() = Instant.now().isAfter(creationTime.plusSeconds(300))
}