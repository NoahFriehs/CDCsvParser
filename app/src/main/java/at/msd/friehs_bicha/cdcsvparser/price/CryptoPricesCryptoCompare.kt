package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Crypto prices with a two-layer strategy:
 *
 * 1. CryptoCompare (keyless public API).
 * 2. CoinGecko (key-free) fallback, used when CryptoCompare fails or is
 *    rate limited. Once CryptoCompare answers with an auth error it is
 *    skipped for the rest of the session (circuit break).
 *
 * @return null on failure, 0.0 when the symbol is unknown.
 */
class CryptoPricesCryptoCompare : BaseCryptoPrices() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val ccBaseUrl = "https://min-api.cryptocompare.com/data/"
    private val cgBaseUrl = "https://api.coingecko.com/api/v3/"

    private var ccBroken = false
    private val symbolToId = ConcurrentHashMap<String, String>()
    private var nextCoinGeckoCallMs = 0L
    @Volatile
    private var coinGeckoCooldownUntilMs = 0L

    override fun getPrice(symbol: String): Double? {
        val ccPrice = if (ccBroken) null else fetchCryptoCompare(symbol)
        if (ccPrice != null) return ccPrice
        return fetchCoinGecko(symbol)
    }

    private fun fetchCryptoCompare(symbol: String): Double? {
        var response: okhttp3.Response? = null
        return try {
            val url = "${ccBaseUrl}price?fsym=$symbol&tsyms=EUR"
            response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.code == 401 || response.code == 403) {
                ccBroken = true
                FileLog.w(TAG_CC, "CryptoCompare requires an API key now; falling back to CoinGecko.")
                return null
            }
            val json = JSONObject(response.body?.string().orEmpty())
            if (json.has("EUR")) json.optDouble("EUR")
            else if (json.has("Data")) 0.0 // symbol unknown
            else null
        } catch (e: Exception) {
            FileLog.d(TAG_CC, "Failed to get price for $symbol from CryptoCompare: $e")
            null
        } finally {
            response?.close()
        }
    }

    private fun fetchCoinGecko(symbol: String): Double? {
        return try {
            waitOutIfCoolingDown()
            spacing()
            val coinId = resolveCoinGeckoId(symbol) ?: return 0.0
            val url = "${cgBaseUrl}simple/price?ids=$coinId&vs_currencies=eur"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.code == 429) {
                coinGeckoCooldownUntilMs = System.currentTimeMillis() + 45_000L
                FileLog.w(TAG_CG, "Rate limited; cooling down for 45 s.")
                return null
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val price = json.optJSONObject(coinId)?.optDouble("eur")
            if (price != null && price.isFinite()) price else null
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "Failed to get price for $symbol from CoinGecko: $e")
            null
        }
    }

    /** Resolves a ticker symbol to its CoinGecko id (map, cache, then search). */
    private fun resolveCoinGeckoId(symbol: String): String? {
        val upper = symbol.uppercase()
        symbolToId[symbol]?.let { return it }
        KEY_MAPPINGS[upper]?.let {
            symbolToId[symbol] = it
            return it
        }
        return searchCoinGeckoId(upper)
    }

    private fun searchCoinGeckoId(symbol: String): String? {
        return try {
            waitOutIfCoolingDown()
            spacing()
            val url = "${cgBaseUrl}search?query=$symbol"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.code == 429) {
                coinGeckoCooldownUntilMs = System.currentTimeMillis() + 60_000L
                FileLog.w(TAG_CG, "Search rate limited; cooling down for 60 s.")
                return null
            }
            val json = JSONObject(response.body?.string().orEmpty())
            var found: String? = null
            json.optJSONArray("coins")?.let { coins ->
                for (i in 0 until coins.length()) {
                    val coin = coins.optJSONObject(i) ?: continue
                    if (coin.optString("symbol").equals(symbol, ignoreCase = true)) {
                        found = coin.optString("id").ifEmpty { null }
                        break
                    }
                }
            }
            found?.let { symbolToId[symbol] = it }
            if (found == null) FileLog.d(TAG_CG, "No CoinGecko id found for $symbol")
            found
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "CoinGecko search failed for $symbol: $e")
            null
        }
    }

    /** Spacing for the key-free CoinGecko tier (~1 request/s). */
    private fun spacing() {
        val wait = nextCoinGeckoCallMs - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        nextCoinGeckoCallMs = System.currentTimeMillis() + 700L
    }

    /** Waits until the cooldown expires (at most once per request, on the price thread). */
    private fun waitOutIfCoolingDown() {
        val wait = coinGeckoCooldownUntilMs - System.currentTimeMillis()
        if (wait > 0) {
            FileLog.d(TAG_CG, "Waiting ${wait / 1000}s for the CoinGecko cooldown.")
            Thread.sleep(wait)
        }
    }

    companion object {
        private const val TAG_CC = "CryptoCompare"
        private const val TAG_CG = "CoinGecko"
        private val KEY_MAPPINGS = mapOf(
            "BTC" to "bitcoin", "ETH" to "ethereum", "DOGE" to "dogecoin",
            "CRO" to "crypto-com-chain", "EUR" to "eur", "ETHW" to "ethereum-pow-iou",
            "LUNA2" to "terra-luna-2", "LUNC" to "terra-luna", "ALGO" to "algorand",
            "XRP" to "ripple", "SOL" to "solana", "BNB" to "binancecoin",
            "DOT" to "polkadot", "ADA" to "cardano", "LTC" to "litecoin",
            "TRX" to "tron", "SHIB" to "shiba-inu", "XMR" to "monero",
            "USDT" to "tether", "BCH" to "bitcoin-cash", "MATIC" to "polygon",
            "LINK" to "chainlink", "UNI" to "uniswap", "ATOM" to "cosmos",
            "AVAX" to "avalanche-2", "FIL" to "filecoin", "APT" to "aptos",
            "ARB" to "arbitrum", "OP" to "optimism", "SUI" to "sui",
            "PEPE" to "pepe", "WIF" to "dogwifcoin", "BONK" to "bonk",
            "CAKE" to "pancakeswap-token", "VET" to "vechain", "ICP" to "internet-computer",
            "ETC" to "ethereum-classic", "XLM" to "stellar", "NEAR" to "near",
            "FTM" to "fantom", "HBAR" to "hedera-hashgraph", "FLOW" to "flow",
            "IMX" to "immutable-x", "MANA" to "decentraland", "SAND" to "the-sandbox",
            "AAVE" to "aave", "GRT" to "the-graph", "ENJ" to "enjincoin",
            "CHZ" to "chiliz", "THETA" to "theta-network", "AXS" to "axie-infinity",
            "XTZ" to "tezos", "EGLD" to "elrond-erd-2", "ZEC" to "zcash",
            "DASH" to "dash", "RUNE" to "thorchain", "KSM" to "kusama",
            "CELO" to "celo", "MINA" to "mina-protocol", "ZIL" to "zilliqa",
            "WAVES" to "waves", "ROSE" to "oasis-network", "KLAY" to "klay-token",
            "ONE" to "harmony", "BAT" to "basic-attention-token",
        )
    }
}
