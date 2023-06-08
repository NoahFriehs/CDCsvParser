package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import java.io.Serializable
import java.util.*

class AssetValue : Serializable {
    val cache: MutableList<PriceCache>
    var isRunning: Boolean

    init {
        cache = ArrayList()
        isRunning = true
    }

    companion object {
        private var instance: AssetValue? = null

        fun getInstance(): AssetValue? {
            if (instance == null) {
                instance = AssetValue()
            }
            return instance
        }
    }

    /**
     * Returns the price of the entered symbol
     *
     * @param symbol the symbol for which the price is needed
     * @return the price of the symbol
     */
    @Throws(InterruptedException::class)
    fun getPrice(symbol: String): Double? {
        //val prices = StaticPrices()
        //return prices.prices[symbol]    //use this if api does nor work

        if (symbol == "EUR") return 1.0

        var price = checkCache(symbol)
        if (price != -1.0) {
            isRunning = true
            return price
        }

        val cryptoPrices = CryptoPricesCryptoCompare()
        val priceApi = cryptoPrices.getPrice(symbol)
        if (priceApi != 0.0) {
            cache.add(PriceCache(symbol, priceApi))
            return priceApi
        }
        var symbol = symbol
        symbol = overrideSymbol(symbol)
        price = checkCache(symbol)
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

    /**
     * Checks if the price of the symbol is stored
     *
     * @param symbol the symbol to be checked for
     * @return a price if it has it it else -1
     */
    private fun checkCache(symbol: String): Double {
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
    }
}