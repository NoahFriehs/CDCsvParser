package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import java.io.Serializable

/**
 * Asset value class
 */
class AssetValue : Serializable {
    private val cache = PriceCache()
    var isRunning: Boolean

    init {
        isRunning = true
    }

    companion object {
        private var instance: AssetValue? = null

        /**
         * Returns the instance of the AssetValue class
         *
         * @return the instance of the AssetValue class
         */
        fun getInstance(): AssetValue {
            if (instance == null) {
                instance = AssetValue()
            }
            return instance!!
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

        var price = cache.checkCache(symbol_)
        if (price != -1.0) {
            return price
        }

        val cryptoPrices = CryptoPricesCryptoCompare()
        val priceApi = cryptoPrices.getPrice(symbol_)
        if (priceApi == null) {
            //FileLog.e("AssetValue", "No price found for: $symbol")    //happens in getPrice() already
            isRunning = false
            return 0.0
        }
        if (priceApi != 0.0) {
            cache.addPrice(symbol_, priceApi)
            return priceApi
        }
        var symbol = symbol_
        symbol = overrideSymbol(symbol)
        price = cache.checkCache(symbol)
        if (price != -1.0) {
            isRunning = true
            return price
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


    /*private fun checkCache(symbol: String): Double {
        var i = 0
        while (i < cache.size) {
            if (cache[i].isOlderThanFiveMinutes) {
                FileLog.d("AssetValue", "removed cache for ${cache[i].id}")
                cache.removeAt(i)
                i--
                continue
            }
            if (cache[i].id == symbol) {
                FileLog.d("AssetValue", "used cache for $symbol")
                return cache[i].price
            }
            i++
        }
        return (-1).toDouble()
    }*/
}