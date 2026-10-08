package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicLong

/**
 * Kraken deep history — free, keyless, but only its **14 EUR counter
 * pairs** (verified 2026-10-08 via `0/public/AssetPairs`). Endpoint:
 * `0/public/OHLC?pair=<KEY>&interval=1440[&since=<unix ms>]` — daily
 * candles, ascending, **max 720 per call**; `since` makes the call start at
 * that instant, so pagination walks FORWARDS from the window start.
 *
 * Candle shape: `[ts_s, open, high, low, close, vwap, volume, count]` —
 * index 4 is the day's close.
 */
class KrakenHistorySource(
    private val api: KrakenApi = PriceApi.kraken,
) : DeepHistorySource {

    override val name = "Kraken"
    override var passExhausted = false

    private var nextCallMs = AtomicLong(0L)

    override fun dailyEur(symbol: String, fromDay: Long, toDay: Long): List<Pair<Long, Double>>? {
        val pair = EUR_PAIRS[symbol.uppercase()] ?: return null   // not listed on Kraken: hand-off
        val byDay = HashMap<Long, Double>()
        var since = fromDay * DAY_SECONDS * 1000L
        repeat(MAX_PAGES) {
            space()
            val candles = fetchPage(pair, since) ?: return null
            if (candles.isEmpty()) return null
            var newest = Long.MIN_VALUE
            for ((day, close) in candles) {
                if (day > toDay) break
                byDay[day] = close
                newest = day
            }
            if (newest >= toDay) return trim(fromDay, toDay, byDay)
            if (candles.size < PAGE_SIZE) return trim(fromDay, toDay, byDay)   // no more data
            if (newest < fromDay) return trim(fromDay, toDay, byDay)          // whole page before the window (shouldn't happen with since)
            since = (newest * DAY_SECONDS + 1) * 1000L
        }
        FileLog.d(TAG, "Kraken $pair: $MAX_PAGES pages not enough for the window; taking what we have.")
        return trim(fromDay, toDay, byDay)
    }

    /** One `OHLC` call → (UTC day, close) ascending, or null (hand-off / exhausted). */
    private fun fetchPage(pair: String, since: Long): List<Pair<Long, Double>>? {
        return try {
            val response = api.ohlc(pair, INTERVAL_DAY_SECONDS, since).execute()
            try {
                val body = if (response.isSuccessful) response.body() else null
                if (body != null) {
                    val errors = runCatching { body.jsonObject["error"]?.jsonArray }.getOrNull()
                    if (errors != null && errors.isNotEmpty()) {
                        val first = errors.getOrNull(0)?.jsonPrimitive?.content ?: "unknown"
                        if (first.contains("rate", ignoreCase = true) ||
                            first.contains("EGeneral:API key", ignoreCase = true)
                        ) {
                            passExhausted = true
                            FileLog.w(TAG, "Kraken rate wall ($first); skipping Kraken for this pass.")
                            return null
                        }
                        // "EQuery:Unknown asset pair" & co: just not served here.
                        return null
                    }
                    parsePage(body)
                } else {
                    if (response.code() == 429 || response.code() == 403) {
                        passExhausted = true
                        FileLog.w(TAG, "Kraken auth/rate wall (HTTP ${response.code()}); skipping Kraken for this pass.")
                    }
                    null
                }
            } finally {
                response.errorBody()?.close()
            }
        } catch (e: Exception) {
            FileLog.d(TAG, "Kraken OHLC failed for $pair: $e")
            null
        }
    }

    private fun trim(fromDay: Long, toDay: Long, byDay: HashMap<Long, Double>): List<Pair<Long, Double>>? {
        val out = byDay.entries.filter { it.key in fromDay..toDay }.sortedBy { it.key }.map { it.key to it.value }
        return out.ifEmpty { null }
    }

    private fun space() {
        val now = System.currentTimeMillis()
        val target = nextCallMs.get()
        if (now < target) Thread.sleep(target - now)
        nextCallMs.set(maxOf(now, target) + SPACING_MS)
    }

    companion object {
        private const val TAG = "DeepKraken"
        private const val DAY_SECONDS = 86_400L
        private const val INTERVAL_DAY_SECONDS = 1440

        /** Kraken caps OHLC at 720 candles per call (verified). */
        private const val PAGE_SIZE = 720

        /** 720 days/page * 12 pages ≈ 23 years. */
        private const val MAX_PAGES = 12

        /** Kraken asks for ~1.5 s between calls; be a good guest at 2.5 s. */
        private const val SPACING_MS = 2500L

        /**
         * The 14 EUR counter pairs (pair KEYs as returned by
         * `0/public/AssetPairs`, verified 2026-10-08: pair names
         * 2Z/AIOZ/CHZ/REZ/ETC/ETH/LTC/MLN/XTZ/XBT/XLM/XMR/XRP/ZEC vs EUR).
         * App tickers map to the pair key; unmapped symbols 404 into the next
         * chain source.
         */
        val EUR_PAIRS = mapOf(
            "BTC" to "XXBTZEUR",
            "ETH" to "XETHZEUR",
            "ETC" to "XETCZEUR",
            "LTC" to "XLTCZEUR",
            "MLN" to "XMLNZEUR",
            "XTZ" to "XTZEUR",
            "XLM" to "XXLMZEUR",
            "XMR" to "XXMRZEUR",
            "XRP" to "XXRPZEUR",
            "ZEC" to "XZECZEUR",
            "CHZ" to "CHZEUR",
            "AIOZ" to "AIOZEUR",
            "REZ" to "REZEUR",
            "2Z" to "2ZEUR",
        )

        /**
         * Parses one `OHLC` reply → (UTC day, close) ascending.
         * `{"error":[],"result":{"<pair>":[[ts_s, o, h, l, CLOSE, …],…],"last":…}}`
         * — the pair key is the only non-"last" member.
         */
        internal fun parsePage(json: JsonElement): List<Pair<Long, Double>>? {
            val result = json.jsonObject["result"]?.jsonObject ?: return null
            val key = result.keys.firstOrNull { it != "last" } ?: return null
            val candles = result[key]?.jsonArray ?: return null
            val out = ArrayList<Pair<Long, Double>>(candles.size)
            for (candle in candles) {
                val arr = candle.jsonArray
                if (arr.size < 5) continue
                val tsSec = arr[0].jsonPrimitive.contentOrNull?.toLongOrNull() ?: continue
                // Kraken emits numbers (76037.46) — go through the literal
                // string to keep full precision (toLong would truncate small caps to 0).
                val close = arr[4].jsonPrimitive.contentOrNull?.toDoubleOrNull()
                if (close == null || !close.isFinite() || close < 0.0) continue
                out.add(tsSec / DAY_SECONDS to close)
            }
            return out
        }
    }
}
