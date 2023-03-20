package at.msd.friehs_bicha.cdcsvparser.price

import com.litesoftwares.coingecko.CoinGeckoApiClient
import com.litesoftwares.coingecko.constant.Currency
import com.litesoftwares.coingecko.domain.Coins.CoinList
import com.litesoftwares.coingecko.domain.Coins.CoinMarkets
import com.litesoftwares.coingecko.impl.CoinGeckoApiClientImpl
import java.io.Serializable
import java.time.Instant
import java.util.*

class AssetValue : Serializable {
    val cache: MutableList<PriceCache>
    var isRunning: Boolean
    var coinLists: List<CoinList>? = null
    var coinMarkets: List<CoinMarkets>? = null
    private var coinMarketsCreationTime: Instant? = null
    private var tries = 0

    init {
        cache = ArrayList()
        isRunning = true
    }

    /**
     * Returns the price of the entered symbol
     *
     * @param symbol the symbol for which the price is needed
     * @return the price of the symbol
     */
    @Throws(InterruptedException::class)
    fun getPrice(symbol: String?): Double? {
        var symbol = symbol
        symbol = overrideSymbol(symbol)
        val price = checkCache(symbol)
        if (price != -1.0) {
            isRunning = true
            return price
        }
        return try {
            val client: CoinGeckoApiClient = CoinGeckoApiClientImpl()
            //client.ping();
            if (coinMarkets == null || Instant.now().isAfter(coinMarketsCreationTime!!.plusSeconds(300))) {
                coinMarkets = client.getCoinMarkets(Currency.EUR)
                coinMarketsCreationTime = Instant.now()
            }
            try {
                //if (coinMarkets == null) coinMarkets = client.getCoinMarkets(Currency.EUR);
                for (coinMarket in coinMarkets!!) {
                    if (coinMarket.symbol.contains(symbol!!.lowercase(Locale.getDefault())) || coinMarket.id.contains(symbol.lowercase(Locale.getDefault()))) {
                        cache.add(PriceCache(symbol, coinMarket.currentPrice))
                        return coinMarket.currentPrice
                    }
                }
            } catch (e: Exception) {
                println("|" + e.message)
            }
            isRunning = true
            getPriceTheOtherWay(symbol)
        } catch (e: Exception) {
            if (e.message!!.contains("com.litesoftwares.coingecko.exception.CoinGeckoApiException: CoinGeckoApiError(code=1015, message=Rate limited)")) {
                Thread.sleep(1000)
                return getPrice(symbol)
            }
            if (tries < 3) {
                tries++
                Thread.sleep(1000)
                return getPrice(symbol)
            }
            isRunning = false
            tries = 0
            0.0
        }
    }

    /**
     * Returns the price of the entered symbol in a more complicate way
     *
     * @param symbol the symbol for which the price is needed
     * @return the price of the symbol
     */
    private fun getPriceTheOtherWay(symbol: String?): Double? {
        val client: CoinGeckoApiClient = CoinGeckoApiClientImpl()
        if (coinLists == null) coinLists = client.coinList
        for (coinList in coinLists!!) {
            if (coinList.symbol.contains(symbol!!.lowercase(Locale.getDefault())) || coinList.id.contains(symbol.lowercase(Locale.getDefault())) || coinList.name.contains(symbol.lowercase(Locale.getDefault()))) {
                val bitcoinInfo = client.getCoinById(coinList.id)
                val data = bitcoinInfo.marketData
                val dataPrice = data.currentPrice
                cache.add(PriceCache(symbol, dataPrice["eur"]!!))
                return dataPrice["eur"]
            }
        }
        println("No price found for: $symbol")
        return 0.0
    }

    /**
     * Replaces symbols with the right ones
     *
     * @param symbol the symbol to be checked and if needed replaced
     * @return the if needed replaced symbol
     */
    private fun overrideSymbol(symbol: String?): String? {
        if (symbol == "LUNA") return "terra-luna"
        return if (symbol == "LUNA2") "terra-luna-2" else symbol
    }

    /**
     * Checks if the price of the symbol is stored
     *
     * @param symbol the symbol to be checked for
     * @return a price if it has it it else -1
     */
    private fun checkCache(symbol: String?): Double {
        var i = 0
        while (i < cache.size) {
            if (cache[i].isOlderThanFiveMinutes) {
                cache.removeAt(i)
                i--
                i++
                continue
            }
            if (cache[i].id == symbol) {
                println("used cache")
                return cache[i].price
            }
            i++
        }
        return (-1).toDouble()
    }
}