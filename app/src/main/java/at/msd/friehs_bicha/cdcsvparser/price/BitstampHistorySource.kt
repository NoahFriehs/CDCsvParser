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
 * Bitstamp deep history — free, keyless, NATIVE EUR pairs, full daily
 * history per coin from its listing (BTC/EUR back to 2011, verified
 * 2026-10-08). Endpoint:
 * `GET api/v2/ohlc/{pair}/?step=86400&limit<=1000[&end=<unix s>]`
 * replies ascending; `timestamp` is at midnight UTC, `close` is the day's
 * close. `end` acts as the backwards pagination cursor.
 *
 * Pair name: lowercase ticker + "eur" (rare renames in [PAIR_ALIASES]); an
 * unknown pair 404s and the symbol simply moves on down the chain.
 */
class BitstampHistorySource(
    private val api: BitstampApi = PriceApi.bitstamp,
) : DeepHistorySource {

    override val name = "Bitstamp"
    override var passExhausted = false

    private var nextCallMs = AtomicLong(0L)

    override fun dailyEur(symbol: String, fromDay: Long, toDay: Long): List<Pair<Long, Double>>? {
        val pair = PAIR_ALIASES[symbol.uppercase()] ?: symbol.lowercase() + "eur"
        if (pair.length > 16) return null   // Bitstamp pair names are short; sanity guard
        val byDay = HashMap<Long, Double>()
        var end: Long = (toDay + 1) * DAY_SECONDS   // candles with ts <= end
        repeat(MAX_PAGES) { page ->
            space()
            val candles = fetchPage(pair, end) ?: return null
            if (candles.isEmpty()) return null
            for ((day, close) in candles) byDay[day] = close
            val oldest = candles.minOf { it.first }
            if (oldest <= fromDay) return trim(fromDay, toDay, byDay)   // reached the window
            if (candles.size < PAGE_SIZE) return trim(fromDay, toDay, byDay)   // listed after fromDay: no more data
            end = (oldest - 1) * DAY_SECONDS
        }
        FileLog.d(TAG, "Bitstamp $pair: $MAX_PAGES pages not enough for the window; taking what we have.")
        return trim(fromDay, toDay, byDay)
    }

    /** One `ohlc` call → (UTC day, close) ascending, or null (hand-off / exhausted). */
    private fun fetchPage(pair: String, end: Long): List<Pair<Long, Double>>? {
        return try {
            val response = api.ohlc(pair, STEP_DAY_SECONDS, PAGE_SIZE, end).execute()
            try {
                when (response.code()) {
                    200 -> response.body()?.let { parsePage(it) }
                    401, 403, 429 -> {
                        passExhausted = true
                        FileLog.w(TAG, "Bitstamp auth/rate wall (HTTP ${response.code()}); skipping Bitstamp for this pass.")
                        null
                    }

                    else -> {
                        // 404/400 = unknown pair / bad request: just not served here.
                        null
                    }
                }
            } finally {
                response.errorBody()?.close()
            }
        } catch (e: Exception) {
            FileLog.d(TAG, "Bitstamp ohlc failed for $pair: $e")
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
        private const val TAG = "DeepBitstamp"
        private const val DAY_SECONDS = 86_400L
        private const val STEP_DAY_SECONDS = 86_400

        /** `limit` is required and capped at 1000 candles per call (verified). */
        private const val PAGE_SIZE = 1000

        /** 1000 days/page * 10 pages ≈ 27 years — far beyond any export. */
        private const val MAX_PAGES = 10

        /** Polite spacing for the keyless tier (the shared egress IP gets throttled). */
        private const val SPACING_MS = 500L

        /**
         * Tickers where the Bitstamp pair name differs from lowercase+eur.
         * MATIC was rebranded POL 1:1 on Bitstamp (the old pair is gone);
         * anything not listed here resolves to lowercase+eur and 404s into
         * the next chain source when wrong.
         */
        val PAIR_ALIASES = mapOf(
            "MATIC" to "pol",
        )

        /**
         * Parses one `ohlc` reply → (UTC day, close) ascending.
         * `{"data":{"ohlc":[{"timestamp":"17…","close":"76…",…},…]}}`
         * (all values are JSON STRINGS on this endpoint).
         */
        internal fun parsePage(json: JsonElement): List<Pair<Long, Double>>? {
            val ohlc: JsonArray = runCatching {
                json.jsonObject["data"]!!.jsonObject["ohlc"]!!.jsonArray
            }.getOrNull() ?: return null
            val out = ArrayList<Pair<Long, Double>>(ohlc.size)
            for (candle in ohlc) {
                val o = runCatching { candle.jsonObject }.getOrNull() ?: continue
                val ts = runCatching { o["timestamp"]!!.jsonPrimitive.contentOrNull!!.toLong() }.getOrNull() ?: continue
                val close = runCatching { o["close"]!!.jsonPrimitive.contentOrNull!!.toDouble() }.getOrNull() ?: continue
                if (!close.isFinite() || close < 0.0) continue
                out.add(ts / DAY_SECONDS to close)
            }
            return out.ifEmpty { null }
        }
    }
}
