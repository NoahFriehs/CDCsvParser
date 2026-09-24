package at.msd.friehs_bicha.cdcsvparser.price

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
     * Loads the prices of the given symbols on the background executor.
     */
    fun loadCache(symbols: List<String>): Boolean {
        executor.execute {
            symbols.forEach { getPrice(it) }
        }
        return isConnected && isRunning
    }

    /**
     * Reloads all cached prices on the background executor.
     */
    fun reloadCache(): Boolean {
        executor.execute {
            cache.reloadCache(this)
        }
        return isConnected && isRunning
    }

    /**
     * Checks network connectivity by fetching a probe price on the
     * background executor.
     */
    fun check() {
        executor.execute {
            when (val priceApi = priceProvider.getPrice("BTC")) {
                null -> {
                    isRunning = false
                }

                0.0 -> {
                    FileLog.e("AssetValue", "No price found for: BTC")
                }

                -1.0 -> {
                    FileLog.e("AssetValue", "API error")
                    isRunning = false
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
