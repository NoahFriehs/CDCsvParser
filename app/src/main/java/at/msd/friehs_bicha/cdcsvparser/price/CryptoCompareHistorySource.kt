package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.BuildConfig
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicLong

/**
 * CryptoCompare **deep** history — the budgeted last fallback: the demo key
 * ships **100 calls per MONTH** (user decision 2026-10-08), so this source
 * is deliberately scarcest:
 *
 * - DEEP-ONLY: it declines any window inside the last [DEEP_HORIZON_DAYS]
 *   days — recent tails are one cheap call on every free source (Bitstamp,
 *   Kraken, keyless CoinGecko 365), and the cache keeps the settled CC days
 *   forever, so a symbol only ever spends CC budget ONCE (for its deep
 *   backfill), not per refresh.
 * - Budgets: [MAX_CALLS_PER_PASS] per pass, [MAX_CALLS_PER_PROCESS] per app
 *   process, plus per-page accounting below; hitting any of them exhausts
 *   the source for the pass (the rest of the day/next visit continues on
 *   the free sources).
 *
 * Endpoint (keyed, verified 2026-10-08): `v2/histoday?fsym=..&tsym=EUR&limit<=2000&toTs=<ms>`
 * → `{"Response":"Success","Data":{"Data":[{"time":<s>,…,"close":…},…]}}`
 * ascending from the pair's listing day. Pagination walks BACKWARDS via
 * `toTs`. 401 without a valid key, 429 at the rate limit.
 *
 * Without a key in the build the source is inert (zero requests — a 401
 * would needlessly burn the shared endpoint's error paths).
 */
class CryptoCompareHistorySource(
    private val api: CryptoCompareDeepApi = PriceApi.cryptoCompareDeep,
    private val apiKey: String = BuildConfig.CC_API_KEY,
) : DeepHistorySource {

    override val name = "CryptoCompare"
    override var passExhausted = false

    private var callsThisPass = 0
    @Volatile
    private var callsThisProcess = 0
    private var nextCallMs = AtomicLong(0L)

    override fun onPassStart() {
        callsThisPass = 0
    }

    override fun dailyEur(symbol: String, fromDay: Long, toDay: Long): List<Pair<Long, Double>>? {
        if (apiKey.isBlank()) return null   // keyless build: never burn a request
        if (passExhausted) return null
        if (fromDay > toDay - DEEP_HORIZON_DAYS) {
            return null   // recent-only window: the free sources handle it
        }
        if (callsThisPass >= MAX_CALLS_PER_PASS || callsThisProcess >= MAX_CALLS_PER_PROCESS) {
            passExhausted = true
            FileLog.w(TAG, "CC budget exhausted (${callsThisPass} this pass / ${callsThisProcess} this process); skipping CC for this pass.")
            return null
        }
        val byDay = HashMap<Long, Double>()
        var toTs = (toDay + 1) * DAY_SECONDS * 1000L
        var pages = 0
        while (pages < MAX_PAGES) {
            pages++
            callBudget() ?: return null    // 401/429/budget → exhausted or decline
            space()
            val page = fetchPage(symbol, toTs) ?: return null
            if (page.isEmpty()) return null
            for ((day, close) in page) byDay[day] = close
            val oldestMs = page.minOf { it.first } * DAY_SECONDS * 1000L
            if (oldestMs / 1000L / DAY_SECONDS <= fromDay) break
            if (page.size < PAGE_SIZE) break   // reached the listing day
            toTs = (oldestMs - 1)
        }
        val out = byDay.entries.filter { it.key in fromDay..toDay }.sortedBy { it.key }.map { it.key to it.value }
        if (out.isEmpty()) {
            FileLog.d(TAG, "CC $symbol: pages came back without data for the window.")
            return null
        }
        FileLog.d(TAG, "CC deep history for $symbol: ${out.size} days in $pages call(s) (process total now ${callsThisProcess}/100 per month).")
        return out
    }

    /** Counts the call; null when the budget is gone (and exhausts the pass). */
    private fun callBudget(): Boolean? {
        if (callsThisPass >= MAX_CALLS_PER_PASS || callsThisProcess >= MAX_CALLS_PER_PROCESS) {
            passExhausted = true
            FileLog.w(TAG, "CC budget exhausted mid-series; stopping at ${callsThisProcess} calls this process.")
            return null
        }
        callsThisPass++
        callsThisProcess++
        return true
    }

    private fun fetchPage(fsym: String, toTs: Long): List<Pair<Long, Double>>? {
        return try {
            val response = api.histoday(
                fsym = fsym, tsym = "EUR", limit = PAGE_SIZE, toTs = toTs, apiKey = apiKey,
            ).execute()
            try {
                when (response.code()) {
                    200 -> response.body()?.let {
                        if (it.jsonObject["Response"]?.jsonPrimitive?.content != "Success") {
                            FileLog.d(TAG, "CC $fsym: non-success reply (${it.jsonObject["Response"]?.jsonPrimitive?.content}).")
                            return null
                        }
                        parsePage(it)
                    }

                    401, 403 -> {
                        passExhausted = true
                        FileLog.w(TAG, "CC key rejected (HTTP ${response.code()}); skipping CC for this pass.")
                        null
                    }

                    429 -> {
                        passExhausted = true
                        FileLog.w(TAG, "CC rate limited (429); skipping CC for this pass.")
                        null
                    }

                    else -> null
                }
            } finally {
                response.errorBody()?.close()
            }
        } catch (e: Exception) {
            FileLog.d(TAG, "CC histoday failed for $fsym: $e")
            null
        }
    }

    private fun space() {
        val now = System.currentTimeMillis()
        val target = nextCallMs.get()
        if (now < target) Thread.sleep(target - now)
        nextCallMs.set(maxOf(now, target) + SPACING_MS)
    }

    companion object {
        private const val TAG = "DeepCC"
        private const val DAY_SECONDS = 86_400L

        /** CC caps `limit` at 2000 points per call. */
        private const val PAGE_SIZE = 2000

        /** 2000 d/page * 2 pages ≈ 11 years; a 3rd only for 10-year exports. */
        private const val MAX_PAGES = 2

        /**
         * Windows ending inside the last 400 days are NOT CC's job: Bitstamp,
         * Kraken and the keyless CoinGecko 365-window each serve them in one
         * free call. CC spends its 100/month only on deep backfills.
         */
        private const val DEEP_HORIZON_DAYS = 400L

        /** Hard caps — the monthly 100-call budget is the real constraint. */
        private const val MAX_CALLS_PER_PASS = 20
        private const val MAX_CALLS_PER_PROCESS = 60

        private const val SPACING_MS = 1000L

        /**
         * Parses one `histoday` reply → (UTC day, close) ascending.
         * `{"Data":{"Data":[{"time":1609…, "close":76000.1, …},…]}}`.
         */
        internal fun parsePage(json: JsonElement): List<Pair<Long, Double>>? {
            val data: JsonArray = json.jsonObject["Data"]?.jsonObject?.get("Data")?.jsonArray
                ?: return null
            val out = ArrayList<Pair<Long, Double>>(data.size)
            for (candle in data) {
                val o = runCatching { candle.jsonObject }.getOrNull() ?: continue
                val timeSec = o["time"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                val close = o["close"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: continue
                if (!close.isFinite() || close < 0.0) continue
                out.add((timeSec / DAY_SECONDS) to close)
            }
            return out
        }
    }
}
