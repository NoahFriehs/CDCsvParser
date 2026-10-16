package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Intraday EUR price REFINEMENT for the intraday chart frames (plan 009b).
 *
 * A 15-minute chart frame cannot be priced from the daily closes alone, so
 * the pass fetches real sub-daily candles here:
 *
 *  - **Kraken** (keyless, only its [KrakenHistorySource.EUR_PAIRS]): two
 *    calls per symbol - 15-min candles since the 15m frame's window start
 *    (7 d <= 720 candles) and 6-hour candles since the 6h frame's window
 *    (30 d = 120 candles) - merged into one ascending (tsSec, close) list;
 *  - **CoinGecko hourly** `market_chart?days=30` as the catch-all for
 *    non-Kraken pairs (the public tier returns hourly points for 2..90 d;
 *    coarser than the frame, but a real price path).
 *
 * The chart FLATTENS the result: every frame slot takes the last refined
 * price on or before the slot; slots the refinement does not reach fall
 * back to the daily-close-held-flat + live pin (the dashed "estimated"
 * region), so a symbol without any refinement still draws.
 *
 * **The window ends at TODAY**: the intraday frames are a time span ending
 * at now for EVERY export - fresh or stale: a held position older than the
 * frame window must still ride REAL intraday candles (the core's opening
 * line carries it into the window). Refinement therefore runs for
 * every non-zero symbol - at most one refetch per day (anchor = today, so
 * the cache refetches exactly at the day edge); 429 / rate walls just
 * leave a symbol at daily granularity for one pass (it recovers next).
 *
 * The refined points are disk-cached per symbol in
 * `files/history_prices_intraday.json` and refetched when the ANCHOR DAY
 * (today) changes (the cache stores the anchor day with the points).
 * Every failure just leaves the symbol to the flatten fallback - the
 * intraday frames must never be worse than "daily prices held flat
 * intraday".
 */
class IntradayHistory(private val prices: CryptoPricesCryptoCompare) {

    /** One symbol's refined points; [anchorDay] is the epoch day the
     *  window ended at (today - the frames' end day). */
    data class IntradayData(val anchorDay: Long, val points: List<Pair<Long, Double>>)

    private val cacheFile: File
        get() = File(InstanceVars.applicationContext.filesDir, "history_prices_intraday.json")

    private val memo = HashMap<String, IntradayData>()
    private var loaded = false

    /**
     * Makes sure [symbol] has intraday refinement for the window ending at
     * [anchorDay] (today - the frames' end day; refetching when the cache
     * was anchored at another day), and returns the best
     * available data - the refined points, the cache from another anchor,
     * or null. [daily] is the cached daily closes: a refinement that
     * contradicts the LAST daily close (today, after the daily phase) by
     * more than a factor 2 is the same-ticker-different-coin trap (the
     * BOOST incident) and is rejected, same policy as the daily deep
     * chain (with the anchor at today, "nearest the anchor" is just the
     * last close).
     */
    fun ensure(symbol: String, daily: List<Pair<Long, Double>>, anchorDay: LocalDate): IntradayData? {
        if (symbol.uppercase() in CryptoPricesCryptoCompare.NO_LIVE_PRICE_SYMBOLS) {
            return memo[symbol] // ambiguous tickers stay refinement-free
        }
        loadCache()
        // A cached set only counts while it reaches NOW: refinement data
        // must end near the anchor (the frames end at today); a set that
        // stops days short is stale junk from a bad response mode.
        val minFreshTsSec = minFreshTs(anchorDay)
        val cached = memo[symbol]
        if (cached != null && cached.anchorDay == anchorDay.toEpochDay() &&
            cached.points.isNotEmpty() &&
            cached.points.last().first >= minFreshTsSec
        ) return cached

        val refined = fetchRefined(symbol, anchorDay)
        if (refined != null && refined.first.isNotEmpty()) {
            val lastDailyClose = daily.lastOrNull()?.second
            if (lastDailyClose != null && lastDailyClose > 0.0) {
                val last = refined.first.last().second
                val ratio = last / lastDailyClose
                if (ratio > 2.0 || ratio < 0.5) {
                    FileLog.w(
                        TAG,
                        "Intraday refinement for $symbol (last=$last) contradicts the last daily " +
                            "close ($lastDailyClose, ratio=$ratio); rejecting it " +
                            "(mismatched-asset guard).",
                    )
                    return cached
                }
            }
            val entry = IntradayData(anchorDay.toEpochDay(), refined.first)
            memo[symbol] = entry
            saveCache()
            FileLog.i(TAG, "Intraday for $symbol: ${entry.points.size} points (via ${refined.second}).")
            return entry
        }
        // No refinement this call: a cache from another anchor still beats
        // nothing (the flatten fallback works off it too).
        return cached
    }

    /**
     * @return (merged refined points, source name) or null when no source
     *         served the symbol this time. The windows end at [anchorDay]
     *         (candles after it are harmless - the flatten grid stops at
     *         the anchor anyway).
     */
    private fun fetchRefined(symbol: String, anchorDay: LocalDate): Pair<List<Pair<Long, Double>>, String>? {
        // The newest set must end near the anchor (the frames end at today).
        val minFreshTsSec = minFreshTs(anchorDay)
        val pair = KrakenHistorySource.EUR_PAIRS[symbol.uppercase()]
        if (pair != null) {
            // Kraken's `interval` is in MINUTES and only accepts
            // {1, 5, 15, 30, 60, 240, 1440, 10080, 21600}, and `since` is
            // UNIX SECONDS; the response is capped at its NEWEST <= 720
            // candles, so with since=0 we get exactly the newest window:
            // pick the interval so that 720 candles cover it.
            // must be fine enough that 720 candles cover the frame window:
            // 30 m -> 15 d (covers the 7 d frame), 60 m -> 30 d (covers the
            // 30 d frame). (The old code passed the frame's SECONDS values,
            // which are invalid / absurdly wide minutes - 21600 "minutes" is
            // a 14.6-day candle, which is how a pair's listing-date history
            // ended up in the cache.) The response must actually reach NOW:
            // a set that stops days short is the full-history junk mode,
            // not refinement (same bar as the cache check in ensure).
            val fine = krakenCandles(pair, KRAKEN_INTERVAL_15M_MS, 0L)
            val coarse = krakenCandles(pair, KRAKEN_INTERVAL_6H_MS, 0L)
            val merged = (fine.orEmpty() + coarse.orEmpty())
                .distinctBy { it.first }
                .sortedBy { it.first }
            if (merged.isNotEmpty()) {
                if (merged.last().first >= minFreshTsSec) {
                    return merged to "Kraken"
                }
                FileLog.w(
                    TAG,
                    "Kraken intraday set for $pair ends at ${merged.last().first} " +
                        "(expected near ${minFreshTsSec}); it looks like the full-history " +
                        "junk mode - rejecting it.",
                )
            }
        }
        val anchorEpochDay = anchorDay.toEpochDay()
        val hourly = prices.getHistoryRawHours(symbol, anchorEpochDay - FRAME_6H.windowDays, anchorEpochDay + 1)
        if (hourly != null && hourly.isNotEmpty() && hourly.last().first >= minFreshTsSec) {
            return hourly to "CoinGecko hourly"
        }
        return null
    }

    private fun daysBeforeMs(day: LocalDate, days: Int): Long =
        (day.toEpochDay() - days) * 86_400_000L

    /**
     * The newest intraday set must end near the anchor (the frames end at
     * today) - this is the earliest timestamp (epoch seconds) a last point
     * may have and still be usable. Guards against stale sets that stop
     * days short (the Kraken full-history junk mode, and cache entries
     * written from it).
     */
    private fun minFreshTs(anchorDay: LocalDate): Long =
        (anchorDay.toEpochDay() - 2L) * 86_400L

    /**
     * One keyless Kraken `0/public/OHLC` call at [intervalMs] (the API's
     * `interval` is MINUTES and only accepts specific resolutions) ->
     * (tsSec, close) ascending, or null (unknown pair / rate wall / error -
     * the caller falls through to the CG catch-all). The API's `since` is
     * UNIX SECONDS (NOT milliseconds) and the response is capped at its
     * NEWEST <= 720 candles - with since=0 that is exactly "the newest
     * window", so the caller picks [intervalMs] such that 720 candles cover
     * the wanted window. Kraken asks for ~1.5 s between calls: the shared
     * spacer keeps the pass a good guest (two calls per symbol at worst).
     */
    private fun krakenCandles(pair: String, intervalMs: Long, sinceSec: Long): List<Pair<Long, Double>>? {
        space()
        return try {
            val intervalMinutes = (intervalMs / 60_000L).toInt()
            val response = PriceApi.kraken.ohlc(pair, intervalMinutes, sinceSec).execute()
            try {
                val body = if (response.isSuccessful) response.body() else null
                if (body == null) {
                    FileLog.d(TAG, "Kraken intraday declined for $pair (HTTP ${response.code()}).")
                    return null
                }
                val errors = runCatching { body.jsonObject["error"]?.jsonArray }.getOrNull()
                if (errors != null && errors.isNotEmpty()) {
                    val first = errors.getOrNull(0)?.jsonPrimitive?.content ?: "unknown"
                    if (first.contains("rate", ignoreCase = true)) {
                        FileLog.w(TAG, "Kraken intraday rate wall ($first); falling back to CoinGecko hourly.")
                        return null
                    }
                    return null // "EQuery:Unknown asset pair" & co
                }
                parseCandles(body)
            } finally {
                response.errorBody()?.close()
            }
        } catch (e: Exception) {
            FileLog.d(TAG, "Kraken intraday OHLC failed for $pair: $e")
            null
        }
    }

    private fun parseCandles(json: JsonElement): List<Pair<Long, Double>>? {
        val result = json.jsonObject["result"]?.jsonObject ?: return null
        val key = result.keys.firstOrNull { it != "last" } ?: return null
        val candles = result[key]?.jsonArray ?: return null
        val out = ArrayList<Pair<Long, Double>>(candles.size)
        for (candle in candles) {
            val arr = candle.jsonArray
            if (arr.size < 5) continue
            val tsSec = arr[0].jsonPrimitive.contentOrNull?.toLongOrNull() ?: continue
            val close = arr[4].jsonPrimitive.contentOrNull?.toDoubleOrNull()
            if (close == null || !close.isFinite() || close <= 0.0) continue
            out.add(tsSec to close)
        }
        return out
    }

    private val nextCallMs = AtomicLong(0L)

    private fun space() {
        val now = System.currentTimeMillis()
        val target = nextCallMs.get()
        if (now < target) Thread.sleep(target - now)
        nextCallMs.set(maxOf(now, target) + SPACING_MS)
    }

    private fun loadCache() {
        if (loaded) return
        loaded = true
        if (!cacheFile.exists()) return
        try {
            val root = JSONObject(cacheFile.readText())
            for (key in root.keys()) {
                val obj = root.optJSONObject(key) ?: continue
                val day = obj.optLong("a", -1L) // "a" = the anchor epoch day
                if (day < 0) continue
                val arr = obj.optJSONArray("p") ?: continue
                val points = ArrayList<Pair<Long, Double>>(arr.length())
                for (i in 0 until arr.length()) {
                    val pt = arr.optJSONArray(i) ?: continue
                    if (pt.length() != 2) continue
                    points.add(pt.getLong(0) to pt.getDouble(1))
                }
                memo[key] = IntradayData(day, points)
            }
        } catch (e: Exception) {
            FileLog.w(TAG, "Failed to load the intraday cache: $e (fetching instead)")
        }
    }

    private fun saveCache() {
        try {
            val root = JSONObject()
            memo.forEach { (symbol, entry) ->
                val pts = JSONArray()
                entry.points.forEach { (ts, price) ->
                    pts.put(JSONArray().put(ts).put(price))
                }
                root.put(symbol, JSONObject().put("a", entry.anchorDay).put("p", pts))
            }
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(cacheFile)) {
                tmp.copyTo(cacheFile, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            FileLog.w(TAG, "Failed to save the intraday cache: $e")
        }
    }

    companion object {
        private const val TAG = "IntradayHistory"

        /** Same courtesy spacing as the daily Kraken source. */
        private const val SPACING_MS = 2500L

        /** The frame windows. */
        data class FrameSpec(val windowSeconds: Int, val windowDays: Int)

        val FRAME_15M = FrameSpec(15 * 60, 7)
        val FRAME_6H = FrameSpec(6 * 3600, 30)

        /** Kraken candle resolutions in MILLISECONDS (the API counts
         *  minutes): 30 m x 720 candles = 15 d >= the 7 d frame; 60 m x 720
         *  = 30 d >= the 30 d frame. (900 - "15 minutes" in seconds - is
         *  NOT a Kraken resolution at all. */
        const val KRAKEN_INTERVAL_15M_MS = 30 * 60_000L
        const val KRAKEN_INTERVAL_6H_MS = 60 * 60_000L
    }
}
