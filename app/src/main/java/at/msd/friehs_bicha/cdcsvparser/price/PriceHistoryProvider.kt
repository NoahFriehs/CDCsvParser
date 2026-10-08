package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Historical EUR price series (CoinGecko `market_chart`) for the chart
 * panels. One fetch per symbol, persisted in the app-private
 * `history_prices.json` so a re-parse (or a re-open of the screen)
 * re-values the buckets without any network.
 *
 * Cache lifetime is incremental by design: every completed day's close is
 * FINAL, so a stale cache is never thrown away and refetched wholesale —
 * only the still-moving tail (fetched day .. today, or further back when a
 * call needs an older window than the cache holds) is refetched and merged,
 * so relaunching the app never re-downloads weeks of settled data.
 *
 * The fetch loop reuses the live-price path's shared CoinGecko 429
 * cooldown/blackout (see [CryptoPricesCryptoCompare.getHistory]): when the
 * shared egress IP gets throttled the loop stops early after reporting the
 * partial result, and the next screen visit retries only what is missing.
 */
class PriceHistoryProvider(private val prices: CryptoPricesCryptoCompare) {

    /** One symbol's daily prices; [fetchedDay] is the UTC day of the fetch. */
    class DailyPrices(val fetchedDay: Long, val points: List<Pair<Long, Double>>)

    private val cacheFile: File by lazy {
        File(InstanceVars.applicationContext.filesDir, "history_prices.json")
    }

    /** In-memory copy of the cache file, loaded on demand. */
    private val memo = HashMap<String, DailyPrices>()
    private var cacheLoaded = false

    companion object {
        private const val TAG = "PriceHistory"
        private const val DAY_MS = 86_400_000L
        /**
         * A cached series is reused while it was fetched today or yesterday:
         * every completed day's close is final, and the still-open day is
         * pinned to the LIVE prices by the chart code (card parity), so the
         * cached last point does not go stale.
         */
        private const val CACHE_TTL_DAYS = 1L

        /**
         * The public (demo) CoinGecko API only keeps the last ~365 days of
         * market_chart data - a wider range fails with "Your request
         * exceeds the allowed time range" (verified empirically). Older
         * buckets are back-filled with the first known price by the chart
         * code instead.
         */
        private const val MAX_PUBLIC_HISTORY_DAYS = 365

        /**
         * Merges an old cached series with a freshly fetched tail (days
         * >= [cutDay]). From [cutDay] on the fetched [tail] wins (the
         * cached points there may be intraday snapshots of a still-open
         * day); days the tail is missing for — and everything before
         * [cutDay] — come from [old], because a completed day's close is
         * final and a gap must not erase settled data. Result: unique
         * days, ascending order.
         */
        fun merge(
            old: List<Pair<Long, Double>>,
            tail: List<Pair<Long, Double>>,
            cutDay: Long,
        ): List<Pair<Long, Double>> {
            val byDay = HashMap<Long, Double>()
            for ((day, price) in old) byDay[day] = price
            for ((day, price) in tail) if (day >= cutDay) byDay[day] = price
            return byDay.entries.sortedBy { it.key }.map { it.key to it.value }
        }
    }

    fun todayUtcDay(): Long = System.currentTimeMillis() / DAY_MS



    private fun loadCache() {
        if (cacheLoaded) return
        cacheLoaded = true
        if (!cacheFile.exists()) return
        try {
            val root = JSONObject(cacheFile.readText())
            for (key in root.keys()) {
                val obj = root.optJSONObject(key) ?: continue
                val day = obj.optLong("d", -1L)
                if (day < 0) continue
                val arr = obj.optJSONArray("p") ?: continue
                val points = ArrayList<Pair<Long, Double>>(arr.length())
                for (i in 0 until arr.length()) {
                    val pt = arr.optJSONArray(i) ?: continue
                    if (pt.length() != 2) continue
                    points.add(pt.getLong(0) to pt.getDouble(1))
                }
                memo[key] = DailyPrices(day, points)
            }
        } catch (e: Exception) {
            FileLog.w(TAG, "Failed to load the history cache: $e (refetching instead)")
        }
    }

    private fun saveCache() {
        try {
            val root = JSONObject()
            memo.forEach { (symbol, entry) ->
                val pts = JSONArray()
                entry.points.forEach { (day, price) ->
                    pts.put(JSONArray().put(day).put(price))
                }
                root.put(symbol, JSONObject().put("d", entry.fetchedDay).put("p", pts))
            }
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(cacheFile)) {
                tmp.copyTo(cacheFile, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            FileLog.w(TAG, "Failed to save the history cache: $e")
        }
    }

    /**
     * Makes sure [symbols] have a usable cached history covering the last
     * [days] days. Only what is missing is fetched (one request per
     * symbol); the loop stops early while the shared CoinGecko cooldown /
     * blackout is active (the remainder is picked up on the next call).
     *
     * @param onProgress invoked with (done, total) after each symbol.
     * @return symbol -> history for everything available (may be empty when
     *         every request was rate limited).
     */
    suspend fun ensureAll(
        symbols: List<String>,
        days: Int,
        onProgress: ((Int, Int) -> Unit)? = null,
    ): Map<String, DailyPrices> = withContext(Dispatchers.IO) {
        loadCache()
        val range = days.coerceAtMost(MAX_PUBLIC_HISTORY_DAYS)
        val today = todayUtcDay()
        val windowStart = today - range + 1
        val result = LinkedHashMap<String, DailyPrices>()
        var throttleLogged = false
        var done = 0
        for (symbol in symbols) {
            onProgress?.invoke(done, symbols.size)
            val entry = memo[symbol]
            when {
                // Fetched today or yesterday: every completed day is final on
                // disk and the still-open day is pinned to the live prices by
                // the chart code — no network at all (app relaunch case).
                entry != null && (today - entry.fetchedDay) in 0..CACHE_TTL_DAYS ->
                    result[symbol] = entry

                // Stale, but it holds the immutable past: keep everything
                // before its fetch day from disk and refetch ONLY the
                // still-moving tail (fetched day .. today — or further back
                // when this call needs an older window than the cache keeps).
                entry != null -> {
                    val refreshed = if (prices.isCoinGeckoInCooldown()) {
                        if (!throttleLogged) {
                            FileLog.i(TAG, "CoinGecko throttled; keeping the stale cache (tail refresh on the next visit).")
                            throttleLogged = true
                        }
                        null
                    } else {
                        refreshTail(symbol, entry, windowStart, today)
                    }
                    result[symbol] = refreshed ?: entry
                }

                // No cache for this symbol yet.
                else -> {
                    if (prices.isCoinGeckoInCooldown()) {
                        FileLog.i(TAG, "CoinGecko throttled; stopping the history pass at $done/${symbols.size} (retry on the next visit).")
                        break
                    }
                    val fetched = prices.getHistory(symbol, range)
                    if (fetched == null || fetched.isEmpty()) {
                        // Unknown symbol (no CoinGecko id) or a failed call: the
                        // symbol simply contributes no history for now.
                        FileLog.d(TAG, "No history for $symbol (days=$range).")
                    } else {
                        val fresh = DailyPrices(today, fetched)
                        memo[symbol] = fresh
                        saveCache()
                        result[symbol] = fresh
                    }
                }
            }
            done++
        }
        onProgress?.invoke(done, symbols.size)
        result
    }

    /**
     * Refetches only the still-moving part of a stale cached [entry] and
     * merges it in. The fetched range is `[max(windowStart, fetchedDay) ..
     * today]` — CoinGecko's `days` parameter is relative (last N days
     * incl. today), which maps onto that exactly.
     *
     * @return the merged entry, or null when the tail fetch failed (the old
     *         entry then stays in place instead of being discarded).
     */
    private fun refreshTail(
        symbol: String,
        entry: DailyPrices,
        windowStart: Long,
        today: Long,
    ): DailyPrices? {
        val cutDay = maxOf(windowStart, entry.fetchedDay)
        val tailDays = (today - cutDay + 1).toInt().coerceAtLeast(1)
        val tail = prices.getHistory(symbol, tailDays)
        if (tail == null || tail.isEmpty()) {
            FileLog.d(TAG, "Tail refresh failed for $symbol (tailDays=$tailDays); keeping the ${today - entry.fetchedDay}-day-old cache.")
            return null
        }
        val merged = DailyPrices(today, merge(entry.points, tail, cutDay))
        memo[symbol] = merged
        saveCache()
        return merged
    }
}
