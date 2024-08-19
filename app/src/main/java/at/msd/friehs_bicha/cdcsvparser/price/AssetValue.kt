package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import java.io.Serializable

/**
 * Asset value class
 */
class AssetValue : Serializable {
    private val cache = PriceCache()
    var isConnected = true
    var isRunning: Boolean
    var priceProvider: BaseCryptoPrices = CryptoPricesCryptoCompare()

    init {
        isRunning = true
    }

    companion object {
        private lateinit var instance: AssetValue

        /**
         * Returns the running instance of AssetValue
         *
         * @return a instance of AssetValue
         */
        fun getInstance(): AssetValue {
            if (this::instance.isInitialized) return instance
            instance = AssetValue()
            return instance
        }
    }

    /**
     * Returns the price of the entered symbol
     *
     * @param symbol_ the symbol for which the price is needed
     * @return the price of the symbol or 0 if an error occurred
     */
    fun getPrice(symbol_: String): Double {
        //val prices = StaticPrices()
        //return prices.prices[symbol]!!    //use this if api does nor work

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
                //FileLog.e("AssetValue", "API error")
                isRunning = false
                return 0.0
            }

            0.0 -> {
                //FileLog.e("AssetValue", "No price found for: $symbol_")   //does not exist at API-Endpoint
                cache.addPrice(symbol_, priceApi)
                return 0.0
            }
            -1.0 -> {
                FileLog.e("AssetValue", "API error")
                isRunning = false
                //return 0.0
            }
            else -> {
                cache.addPrice(symbol_, priceApi)
                isRunning = true
                return priceApi
            }
        }


        var symbol = symbol_
        symbol = overrideSymbol(symbol)
        val price = cache.checkCache(symbol)
        if (price != -1.0) {
            isRunning = true
            return price
        }

        val prices = StaticPrices()
        if (prices.prices.containsKey(symbol)) {
            return prices.prices[symbol]!!
        }

        FileLog.e("AssetValue", "No price found for: $symbol")
        return 0.0
    }


    /**
     * Replaces symbols with the right ones
     *
     * @param symbol the symbol to be checked and if needed replaced
     * @return the if needed replaced symbol
     */
    private fun overrideSymbol(symbol: String): String {
        if (symbol == "LUNA") return "terra-luna"
        return if (symbol == "LUNA2") "terra-luna-2" else symbol
    }


    fun loadCache(symbols: List<String>): Boolean {
        Thread {
            symbols.forEach { getPrice(it) }
        }.start()
        if (!isConnected || !isRunning) return false
        return true
    }

    fun reloadCache(): Boolean {
        Thread {
            cache.reloadCache(this)
        }.start()
        if (!isConnected || !isRunning) return false
        return true
    }

    fun check() {
        Thread {
            when (val priceApi = priceProvider.getPrice("BTC")) {
                null -> {
                    //FileLog.e("AssetValue", "API error")
                    isRunning = false
                }

                0.0 -> {
                    FileLog.e(
                        "AssetValue",
                        "No price found for: BTC"
                    )   //does not exist at API-Endpoint
                }

                -1.0 -> {
                    FileLog.e("AssetValue", "API error")
                    isRunning = false
                    //return 0.0
                }

                else -> {
                    cache.addPrice("BTC", priceApi)
                    isRunning = true
                }
            }
        }.start()
    }

}