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
 * Bulk lookups prefer a single CoinGecko `simple/price` call per batch of
 * ids (mirrors the core tester's CoinGeckoPriceProvider) instead of one
 * call per symbol; 429 cooldowns scale up (45 s x count, cap 5 min)
 * instead of hammering the rate limit.
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
    @Volatile
    private var consecutive429 = 0

    override fun getPrice(symbol: String): Double? {
        val ccPrice = if (ccBroken) null else fetchCryptoCompare(symbol)
        if (ccPrice != null) return ccPrice
        return fetchCoinGecko(symbol)
    }

    override fun getPricesBulk(symbols: List<String>): Map<String, Double> {
        if (symbols.isEmpty()) return emptyMap()
        // Pass 1: CryptoCompare, one call per symbol, while the circuit is closed.
        val prices = LinkedHashMap<String, Double>()
        val remaining = linkedSetOf<String>()
        for (symbol in symbols) {
            val ccPrice = if (ccBroken) null else fetchCryptoCompare(symbol)
            if (ccPrice == null) remaining.add(symbol) else prices[symbol] = ccPrice
        }
        if (remaining.isEmpty()) return prices
        // Pass 2: everything left goes to one bulk CoinGecko call per batch.
        for (chunk in remaining.chunked(BULK_BATCH_SIZE)) {
            fetchCoinGeckoBulk(chunk)?.forEach { (symbol, price) -> prices[symbol] = price }
        }
        return prices
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
                noteCoinGecko429()
                return null
            }
            noteCoinGeckoSuccess()
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
                noteCoinGecko429()
                return null
            }
            noteCoinGeckoSuccess()
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

    /**
     * One CoinGecko `simple/price` call for a batch of symbols.
     *
     * @return a map from symbol to price with entries only for symbols whose
     *         id could be resolved and that came back in the response
     *         (0.0 = known id without a price); null if the call failed.
     */
    private fun fetchCoinGeckoBulk(symbols: List<String>): Map<String, Double>? {
        return try {
            waitOutIfCoolingDown()
            spacing()
            val idToSymbol = LinkedHashMap<String, String>()
            val idList = StringBuilder()
            for (symbol in symbols) {
                val coinId = resolveCoinGeckoId(symbol) ?: continue // unresolved: no price
                idToSymbol.putIfAbsent(coinId, symbol)
                if (idList.isNotEmpty()) idList.append(',')
                idList.append(coinId)
            }
            if (idList.isEmpty()) return null
            val url = "${cgBaseUrl}simple/price?ids=$idList&vs_currencies=eur"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.code == 429) {
                noteCoinGecko429()
                return null
            }
            noteCoinGeckoSuccess()
            val json = JSONObject(response.body?.string().orEmpty())
            if (json.has("error")) {
                FileLog.w(TAG_CG, "CoinGecko bulk error: ${json.optString("error")}")
                return null
            }
            val prices = LinkedHashMap<String, Double>()
            for ((coinId, symbol) in idToSymbol) {
                val price = json.optJSONObject(coinId)?.optDouble("eur")
                if (price != null && price.isFinite()) prices[symbol] = price
            }
            prices
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "Failed to get bulk prices from CoinGecko: $e")
            null
        }
    }

    /**
     * Escalating cooldown for CoinGecko rate limits: 45 s per consecutive 429,
     * capped at 5 min. Reset on the first successful response.
     */
    private fun noteCoinGecko429() {
        consecutive429++
        val cooldownMs = minOf(45_000L * consecutive429, 300_000L)
        coinGeckoCooldownUntilMs = System.currentTimeMillis() + cooldownMs
        FileLog.w(
            TAG_CG,
            "Rate limited (consecutive #$consecutive429); cooling down ${cooldownMs / 1000} s."
        )
    }

    private fun noteCoinGeckoSuccess() {
        if (consecutive429 > 0) FileLog.d(TAG_CG, "CoinGecko recovered after $consecutive429 rate limit(s).")
        consecutive429 = 0
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

        /** Max ids per bulk `simple/price` call; larger lists are chunked. */
        private const val BULK_BATCH_SIZE = 30
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
