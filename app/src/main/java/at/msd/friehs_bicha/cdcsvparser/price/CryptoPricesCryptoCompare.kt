package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
 * Id resolution (map hit -> disk cache -> one API search) persists to
 * `symbol_id_cache.json` in the app's private storage, including negative
 * results (24 h) so junk symbols are only searched once. Bulk calls also
 * cap the number of searches ([BULK_SEARCH_LIMIT]) so a file full of unknown
 * tokens cannot turn into a search storm.
 *
 * @return null on failure, 0.0 when the symbol is unknown.
 */
class CryptoPricesCryptoCompare : BaseCryptoPrices() {
    /** Typed Retrofit endpoints (shared client keeps the old timeouts). */
    private val ccApi = PriceApi.cryptoCompare
    private val cgApi = PriceApi.coinGecko

    /**
     * Executes a Retrofit call, runs [block] on the response and releases
     * any unconsumed body afterwards. (Retrofit 3's `Response` is no longer
     * `Closeable`: a converted body has already drained its source, and any
     * error/early-exit path exposes the unconsumed stream as `errorBody()`.)
     */
    private inline fun <T, R> executeClosed(call: retrofit2.Call<T>, block: (retrofit2.Response<T>) -> R): R {
        val response = call.execute()
        return try {
            block(response)
        } finally {
            response.errorBody()?.close()
        }
    }

    private var ccBroken = false
    private val symbolToId = ConcurrentHashMap<String, String>()
    private val unknownSymbols = ConcurrentHashMap<String, Long>() // symbol -> first-seen ts
    private var nextCoinGeckoCallMs = 0L
    @Volatile
    private var coinGeckoCooldownUntilMs = 0L
    @Volatile
    private var consecutive429 = 0

    /**
     * CoinGecko rate-limit state.
     *
     * Cooldowns are fast-fail gates, never sleeps: callers (often the
     * cpp-core thread) must not be pinned waiting out a rate limit, and a
     * throttled egress IP must not keep retrying 429s in the background
     * after the user has left the screen. The app's existing
     * "no internet -> probe every 5 s" recovery loop re-checks and the
     * next probe after the cooldown runs is a no-network fast-fail until
     * the cooldown is actually over.
     */
    @Volatile
    private var cgBlackoutUntilMs = 0L

    /** App-private `symbol_id_cache.json`; null when no context is available yet. */
    private val idCacheFile: File? by lazy {
        try {
            File(InstanceVars.applicationContext.filesDir, "symbol_id_cache.json")
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "No app context yet; the id cache stays in memory only.")
            null
        }
    }

    init {
        loadIdCacheFile()
    }

    /**
     * Returns the remaining CoinGecko wait time in ms if any call must skip
     * the network right now (cooldown or blackout), else 0.
     */
    private fun coinGeckoSkipMs(): Long {
        val now = System.currentTimeMillis()
        val until = maxOf(coinGeckoCooldownUntilMs, cgBlackoutUntilMs)
        return (until - now).coerceAtLeast(0)
    }

    /** Result of searching CoinGecko for a symbol's id. */
    private sealed interface IdSearchResult {
        data class Found(val id: String) : IdSearchResult

        /** 200 response without a matching symbol - safe to remember. */
        data object NotInCatalog : IdSearchResult

        /** 429 / network / parse error - must NOT be cached as unknown. */
        data object TransientError : IdSearchResult
    }

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
        // Pass 2: everything left goes to one bulk CoinGecko call per batch;
        // the search budget covers the whole call, not just one batch.
        val searchBudget = AtomicInteger(BULK_SEARCH_LIMIT)
        for (chunk in remaining.chunked(BULK_BATCH_SIZE)) {
            fetchCoinGeckoBulk(chunk, searchBudget)?.forEach { (symbol, price) -> prices[symbol] = price }
        }
        return prices
    }

    private fun fetchCryptoCompare(symbol: String): Double? {
        return try {
            executeClosed(ccApi.price(symbol, "EUR")) { response ->
                if (response.code() == 401 || response.code() == 403) {
                    ccBroken = true
                    FileLog.w(TAG_CC, "CryptoCompare requires an API key now; falling back to CoinGecko.")
                    return null
                }
                val body = response.body()
                when {
                    // {"EUR": 42000.5}
                    body?.EUR != null -> body.EUR
                    // {"Data": "-1"} -> symbol unknown
                    body?.Data != null -> 0.0
                    else -> null
                }
            }
        } catch (e: Exception) {
            FileLog.d(TAG_CC, "Failed to get price for $symbol from CryptoCompare: $e")
            null
        }
    }

    private fun fetchCoinGecko(symbol: String): Double? {
        val skip = coinGeckoSkipMs()
        if (skip > 0) {
            FileLog.d(TAG_CG, "CoinGecko cooldown/blackout active; skipping $symbol for now (${skip / 1000} s left).")
            return null
        }
        return try {
            spacing()
            val coinId = resolveCoinGeckoId(symbol) ?: return 0.0
            executeClosed(cgApi.simplePrice(coinId, "eur")) { response ->
                if (response.code() == 429) {
                    noteCoinGecko429()
                    return null
                }
                noteCoinGeckoSuccess()
                val json = response.body()?.jsonObject
                val price = (json?.get(coinId) as? JsonObject)?.get("eur")?.jsonPrimitive?.doubleOrNull
                if (price != null && price.isFinite()) price else null
            }
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "Failed to get price for $symbol from CoinGecko: $e")
            null
        }
    }

    /**
     * Resolves a ticker symbol to its CoinGecko id (map, disk cache, then one
     * search). [searchBudget] limits how many searches this resolution chain
     * may spend (bulk calls pass one, single lookups pass null = unlimited).
     */
    private fun resolveCoinGeckoId(symbol: String, searchBudget: AtomicInteger? = null): String? {
        val upper = symbol.uppercase()
        if (NO_LIVE_PRICE_SYMBOLS.contains(upper)) {
            FileLog.d(TAG_CG, "No live price for $symbol (ambiguous ticker, see NO_LIVE_PRICE_SYMBOLS).")
            return null
        }
        symbolToId[symbol]?.let { return it }
        KEY_MAPPINGS[upper]?.let { id ->
            rememberId(symbol, id)
            return id
        }
        unknownSymbols[symbol]?.let { seenAt ->
            if (System.currentTimeMillis() - seenAt < NEGATIVE_CACHE_TTL_MS) return null
            unknownSymbols.remove(symbol) // stale; search again
        }
        if (searchBudget != null && searchBudget.decrementAndGet() < 0) {
            FileLog.d(TAG_CG, "Id search budget exhausted; not searching for $symbol")
            return null
        }
        return when (val result = searchCoinGeckoId(upper)) {
            is IdSearchResult.Found -> {
                rememberId(symbol, result.id)
                result.id
            }

            IdSearchResult.NotInCatalog -> {
                rememberUnknown(symbol)
                null
            }

            IdSearchResult.TransientError -> null
        }
    }

    private fun rememberId(symbol: String, id: String) {
        symbolToId[symbol] = id
        saveIdCacheFile()
    }

    private fun rememberUnknown(symbol: String) {
        unknownSymbols[symbol] = System.currentTimeMillis()
        FileLog.d(TAG_CG, "No CoinGecko id found for $symbol (remembered for ${NEGATIVE_CACHE_TTL_MS / 3_600_000} h)")
        saveIdCacheFile()
    }

    /** Loads the persisted symbol -> id cache (positive + still-fresh negative). */
    private fun loadIdCacheFile() {
        val file = idCacheFile ?: return
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            for (key in json.keys()) {
                val value = json.optString(key, "")
                when {
                    value.isEmpty() -> {}

                    value.startsWith("!") -> {
                        val seenAt = value.drop(1).toLongOrNull() ?: continue
                        if (System.currentTimeMillis() - seenAt < NEGATIVE_CACHE_TTL_MS) {
                            unknownSymbols[key] = seenAt
                        }
                    }

                    else -> symbolToId[key] = value
                }
            }
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "Failed to load the symbol id cache: $e")
        }
    }

    /** Persists the symbol -> id cache (atomically: tmp file + rename). */
    private fun saveIdCacheFile() {
        val file = idCacheFile ?: return
        try {
            val json = JSONObject()
            symbolToId.forEach { (key, id) -> json.put(key, id) }
            unknownSymbols.forEach { (key, seenAt) -> json.put(key, "!$seenAt") }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "Failed to save the symbol id cache: $e")
        }
    }

    private fun searchCoinGeckoId(symbol: String): IdSearchResult {
        if (coinGeckoSkipMs() > 0) {
            FileLog.d(TAG_CG, "CoinGecko cooldown/blackout active; not searching for $symbol yet.")
            return IdSearchResult.TransientError
        }
        return try {
            spacing()
            executeClosed(cgApi.search(symbol)) { response ->
                if (response.code() == 429) {
                    noteCoinGecko429()
                    return IdSearchResult.TransientError
                }
                noteCoinGeckoSuccess()
                val found = response.body()?.coins
                    ?.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                    ?.id
                    ?.ifEmpty { null }
                found?.let { IdSearchResult.Found(it) } ?: IdSearchResult.NotInCatalog
            }
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "CoinGecko search failed for $symbol: $e")
            IdSearchResult.TransientError
        }
    }

    /**
     * True while the shared CoinGecko cooldown/blackout is active (callers
     * should stop queuing further requests and retry later, e.g. on the
     * next screen visit).
     */
    fun isCoinGeckoInCooldown(): Boolean = coinGeckoSkipMs() > 0

    /**
     * CoinGecko daily EUR price history for [symbol] over the last [days]
     * days, ascending, one point per UTC calendar day (sub-daily replies -
     * days = 1 carries ~5-minute resolution - are reduced to the last point
     * of each day). Reuses the shared id resolution and the 429 cooldown /
     * blackout state machine of the live price path.
     *
     * @return (utcEpochDay, price) pairs, or null when the id cannot be
     *         resolved (unknown symbol), the call failed, or the cooldown
     *         / blackout is active (transient - retry later).
     */
    fun getHistory(symbol: String, days: Int): List<Pair<Long, Double>>? {
        if (days < 1) return null
        if (coinGeckoSkipMs() > 0) {
            FileLog.d(TAG_CG, "CoinGecko cooldown/blackout active; skipping history for $symbol.")
            return null
        }
        return try {
            val coinId = resolveCoinGeckoId(symbol) ?: return null
            spacing()
            executeClosed(cgApi.marketChart(coinId, "eur", days)) { response ->
                when {
                    response.code() == 429 -> {
                        noteCoinGecko429()
                        null
                    }

                    response.isSuccessful -> {
                        noteCoinGeckoSuccess()
                        val byDay = LinkedHashMap<Long, Double>()
                        for (entry in response.body()?.prices.orEmpty()) {
                            if (entry.size != 2) continue
                            val ts = entry[0]
                            val price = entry[1]
                            if (!ts.isFinite() || !price.isFinite() || price < 0.0) continue
                            byDay[(ts / 86_400_000L).toLong()] = price
                        }
                        byDay.entries.map { it.key to it.value }
                    }

                    else -> {
                        FileLog.w(TAG_CG, "CoinGecko market_chart failed for $symbol (HTTP ${response.code()}, days=$days).")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            FileLog.d(TAG_CG, "Failed to get history for $symbol: $e")
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
    private fun fetchCoinGeckoBulk(symbols: List<String>, searchBudget: AtomicInteger): Map<String, Double>? {
        if (coinGeckoSkipMs() > 0) {
            FileLog.d(TAG_CG, "CoinGecko cooldown/blackout active; skipping the bulk call.")
            return null
        }
        return try {
            spacing()
            val idToSymbol = LinkedHashMap<String, String>()
            val idList = StringBuilder()
            for (symbol in symbols) {
                val coinId = resolveCoinGeckoId(symbol, searchBudget) ?: continue // unresolved: no price
                idToSymbol.putIfAbsent(coinId, symbol)
                if (idList.isNotEmpty()) idList.append(',')
                idList.append(coinId)
            }
            if (idList.isEmpty()) return null
            executeClosed(cgApi.simplePrice(idList.toString(), "eur")) { response ->
                if (response.code() == 429) {
                    noteCoinGecko429()
                    return null
                }
                noteCoinGeckoSuccess()
                val json = response.body()?.jsonObject
                val error = (json?.get("error") as? JsonPrimitive)?.contentOrNull
                if (error != null) {
                    FileLog.w(TAG_CG, "CoinGecko bulk error: $error")
                    return null
                }
                val prices = LinkedHashMap<String, Double>()
                for ((coinId, symbol) in idToSymbol) {
                    val price = (json?.get(coinId) as? JsonObject)?.get("eur")?.jsonPrimitive?.doubleOrNull
                    if (price != null && price.isFinite()) prices[symbol] = price
                }
                prices
            }
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
        if (consecutive429 >= BLACKOUT_AFTER_429S) {
            cgBlackoutUntilMs = System.currentTimeMillis() + BLACKOUT_MS
            FileLog.w(
                TAG_CG,
                "Rate limited (consecutive #$consecutive429); backing off for ${BLACKOUT_MS / 60_000} min instead of retrying."
            )
        } else {
            FileLog.w(TAG_CG, "Rate limited (consecutive #$consecutive429); cooling down ${cooldownMs / 1000} s.")
        }
    }

    private fun noteCoinGeckoSuccess() {
        if (consecutive429 > 0) FileLog.d(TAG_CG, "CoinGecko recovered after $consecutive429 rate limit(s).")
        if (cgBlackoutUntilMs != 0L) FileLog.d(TAG_CG, "CoinGecko blackout ended early.")
        consecutive429 = 0
        cgBlackoutUntilMs = 0L
    }

    /** Spacing for the key-free CoinGecko tier (~1 request/s). */
    private fun spacing() {
        val wait = nextCoinGeckoCallMs - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        nextCoinGeckoCallMs = System.currentTimeMillis() + 700L
    }

    companion object {
        private const val TAG_CC = "CryptoCompare"
        private const val TAG_CG = "CoinGecko"

        /** Max ids per bulk `simple/price` call; larger lists are chunked. */
        private const val BULK_BATCH_SIZE = 30

        /** Max `/search` calls per bulk resolution; prevents a search storm. */
        private const val BULK_SEARCH_LIMIT = 5

        /** How long a "not in CoinGecko catalog" result is remembered. */
        private const val NEGATIVE_CACHE_TTL_MS = 24L * 3_600_000

        /** After this many consecutive 429s, stop touching CoinGecko at all... */
        private const val BLACKOUT_AFTER_429S = 3

        /** ...for this long (mirrors the tester's "too many rate limits" abort). */
        private const val BLACKOUT_MS = 10L * 60_000

        /**
         * Uppercase tickers whose CoinGecko search match is known to be wrong
         * (ambiguous tickers / non-crypto assets; cf. the comments in the
         * tester's StaticPrices.h). They get a 0.0 price instead of a value
         * from the wrong coin. The DEEP history chain must skip exactly this
         * list, too (its plain-ticker lookups would hit same-named different
         * coins — see the 2026-10-08 BOOST incident: 471 M tokens priced at
         * the wrong BOOST, a 764 k€ phantom position).
         */
        internal val NO_LIVE_PRICE_SYMBOLS = setOf("XAU", "NFT", "XVVS", "BOOST", "CAT")
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
