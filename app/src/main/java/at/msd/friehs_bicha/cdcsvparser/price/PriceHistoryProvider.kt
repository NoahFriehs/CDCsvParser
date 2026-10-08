package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Historical EUR price series for the chart panels. Per symbol the
 * provider keeps the LAST KNOWN daily closes in the app-private
 * `history_prices.json`, so a re-parse (or a re-open of the screen)
 * re-values the buckets without any network.
 *
 * **Fetch chain (deep history):** when a window is wanted, the
 * [deepChain] (Bitstamp → Kraken; then the keyless CoinGecko 365-window as
 * the catch-all; and the budget-limited keyed CryptoCompare as the LAST
 * deep source) is asked for the missing days. Free keyless sources serve
 * the recent tail in one call each, so only first-time symbols beyond the
 * 365-day window spend the CC monthly budget — once, ever, because the
 * result is cached forever.
 *
 * Cache lifetime is incremental by design: every completed day's close is
 * FINAL, so a stale cache is never thrown away and refetched wholesale —
 * only the still-moving tail (fetched day .. today, or further back when
 * the wanted window is OLDER than the cache holds) is refetched and
 * merged, so relaunching the app never re-downloads weeks of settled data.
 *
 * The CoinGecko catch-all reuses the live-price path's shared 429
 * cooldown/blackout; a CoinGecko throttle no longer stops the whole pass
 * (the other chain sources keep working) and the next screen visit retries
 * only what is missing.
 */
class PriceHistoryProvider(
    private val prices: CryptoPricesCryptoCompare,
    private val deepChain: DeepHistoryChain = DeepHistoryChain(
        listOf(
            BitstampHistorySource(),
            KrakenHistorySource(),
            CryptoCompareHistorySource(),
        ),
    ),
) {

    /** One symbol's daily prices; [fetchedDay] is the UTC day of the fetch. */
    class DailyPrices(val fetchedDay: Long, val points: List<Pair<Long, Double>>) {
        /** Oldest cached day (== fetch day for an empty series). */
        val firstDay: Long get() = points.firstOrNull()?.first ?: fetchedDay
    }

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
        /**
         * The keyless CoinGecko tier only keeps the last ~365 days of
         * market_chart data — the CATCH-ALL part of the chain is capped
         * there; the deep sources (Bitstamp/Kraken/CC) have no such cap.
         */
        private const val MAX_PUBLIC_HISTORY_DAYS = 365

        /**
         * True when the freshly fetched [fresh] series is consistent with the
         * already-settled [old] cache on the days both cover: fewer than 25%
         * of the overlap may deviate by more than a factor 2. Protects the
         * final daily closes from a deep source that served a SAME-NAMED
         * DIFFERENT COIN (or any unit/scale error) — a 22x price on settled
         * days is never a market move. With fewer than 30 overlapping days
         * there is nothing to contradict yet, and the check passes.
         */
        internal fun consistentWith(
            old: List<Pair<Long, Double>>,
            fresh: List<Pair<Long, Double>>,
        ): Boolean {
            val oldByDay = HashMap<Long, Double>(old.size)
            for ((day, price) in old) oldByDay[day] = price
            var overlap = 0
            var mismatch = 0
            for ((day, price) in fresh) {
                val cached = oldByDay[day] ?: continue
                if (cached <= 0.0 || price <= 0.0) continue
                overlap++
                val ratio = price / cached
                if (ratio > 2.0 || ratio < 0.5) mismatch++
            }
            return overlap < MIN_OVERLAP_DAYS || mismatch * 4 <= overlap
        }

        /** Below this many shared days no source can yet be proven wrong. */
        private const val MIN_OVERLAP_DAYS = 30

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
     * [days] days. Two-phase: every FRESH cached entry (fetched today or
     * yesterday — zero network) is published FIRST via [onSymbol], so a
     * relaunch re-values the chart from disk within milliseconds; only
     * afterwards does the network phase fetch/tail-refresh what is not
     * fresh, publishing each symbol as soon as it lands.
     *
     * A fresh entry only counts when it COVERS the wanted window: a cache
     * built for a smaller window (or a shorter export) is deep-backed in
     * phase 2 instead.
     *
     * @param onProgress invoked with (done, total) after each symbol.
     * @param onSymbol invoked per entry as soon as it is available — the
     *        chart should re-value incrementally, never wait for the pass.
     * @return symbol -> history for everything available (a symbol is
     *         missing from the map only when it has no cache at all and
     *         every chain source declined it this pass).
     */
    suspend fun ensureAll(
        symbols: List<String>,
        days: Int,
        onProgress: ((Int, Int) -> Unit)? = null,
        onSymbol: ((String, DailyPrices) -> Unit)? = null,
    ): Map<String, DailyPrices> = withContext(Dispatchers.IO) {
        loadCache()
        deepChain.beginPass()
        val range = days.coerceAtLeast(1)
        val today = todayUtcDay()
        val windowStart = today - range + 1
        val result = LinkedHashMap<String, DailyPrices>()
        var cgThrottleLogged = false
        var done = 0

        fun publish(symbol: String, entry: DailyPrices) {
            result[symbol] = entry
            onSymbol?.invoke(symbol, entry)
            done++
            onProgress?.invoke(done, symbols.size)
        }

        // Phase 1 (no network): fresh disk cache entries that COVER the
        // wanted window, published at once.
        for (symbol in symbols) {
            val entry = memo[symbol]
            if (entry != null && (today - entry.fetchedDay) in 0..CACHE_TTL_DAYS &&
                entry.firstDay <= windowStart
            ) {
                publish(symbol, entry)
            }
        }

        // Phase 2 (network): every symbol not published above — stale
        // caches (tail refresh OR deep backfill when the wanted window is
        // older than the cache) and uncached symbols.
        for (symbol in symbols) {
            if (result.containsKey(symbol)) continue
            val entry = memo[symbol]
            // Refetch from here: the window start when the cache does not
            // reach it yet, else only the still-moving tail (fetched day ..
            // today). Settled days before that stay on disk, forever.
            val fromDay =
                if (entry == null || entry.firstDay > windowStart) windowStart else entry.fetchedDay
            val refreshed = fetchAndMerge(symbol, entry, fromDay, today)
            if (refreshed != null) {
                publish(symbol, refreshed)
            } else if (entry != null) {
                // Every source failed: the stale cache beats nothing.
                publish(symbol, entry)
                if (!cgThrottleLogged && prices.isCoinGeckoInCooldown()) {
                    FileLog.i(TAG, "Deep chain empty for $symbol and CoinGecko throttled; keeping the stale cache (retry on the next visit).")
                    cgThrottleLogged = true
                }
            } else {
                FileLog.d(TAG, "No history for $symbol (every chain source declined it this pass).")
                done++
                onProgress?.invoke(done, symbols.size)
            }
        }
        onProgress?.invoke(done, symbols.size)
        result
    }

    /**
     * Fetches [fromDay]..[today] for [symbol] through the deep chain
     * (Bitstamp → Kraken → keyed CC, deep-only) with the keyless CoinGecko
     * 365-window as the catch-all below, and merges the result over
     * [entry]'s cached past.
     *
     * @return the merged entry, or null when every source declined (the old
     *         entry then stays in place instead of being discarded).
     */
    private fun fetchAndMerge(
        symbol: String,
        entry: DailyPrices?,
        fromDay: Long,
        today: Long,
    ): DailyPrices? {
        val merged = deepChain.fetch(symbol, fromDay, today)?.let { (series, sourceName) ->
            when {
                entry != null && !consistentWith(entry.points, series) -> {
                    FileLog.w(TAG, "Deep history from $sourceName for $symbol contradicts the already-settled cached days (mismatched overlap); rejecting it, keeping the old cache.")
                    null
                }

                else -> DailyPrices(today, merge(entry?.points ?: emptyList(), series, fromDay))
                    .also {
                        if (entry == null) {
                            FileLog.i(TAG, "Deep history for $symbol from $sourceName: ${series.size} days (${fromDay}..$today).")
                        }
                    }
            }
        }
        if (merged != null) {
            memo[symbol] = merged
            saveCache()
            return merged
        }
        // Catch-all: the keyless CoinGecko window (365 days) covers the TAIL
        // even when a deep source has no pair for the symbol.
        if (prices.isCoinGeckoInCooldown()) return null
        val cgDays = (today - fromDay + 1).toInt().coerceIn(1, MAX_PUBLIC_HISTORY_DAYS)
        val cgFrom = today - cgDays + 1L
        val tail = prices.getHistory(symbol, cgDays)
        if (tail == null || tail.isEmpty()) {
            FileLog.d(TAG, "Tail fetch failed for $symbol (fromDay=$fromDay); keeping the ${if (entry == null) "" else "${today - entry.fetchedDay}-day-old "}cache.")
            return null
        }
        val result = DailyPrices(today, merge(entry?.points ?: emptyList(), tail, maxOf(cgFrom, fromDay)))
        memo[symbol] = result
        saveCache()
        return result
    }
}
