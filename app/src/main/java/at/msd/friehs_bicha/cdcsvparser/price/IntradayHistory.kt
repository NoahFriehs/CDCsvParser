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
 * **The window is anchored to the export's LAST TRANSACTION DAY** (not
 * "now"): this app analyses exports, and every export older than the
 * frame's window (30 d for 6 h) would otherwise chart empty air (see
 * plan 009, open-questions entry 9 - discovered in the smoke test).
 *
 * The refined points are disk-cached per symbol in
 * `files/history_prices_intraday.json` and refetched when the ANCHOR DAY
 * changes (the cache stores the anchor day with the points). Every
 * failure just leaves the symbol to the flatten fallback - the intraday
 * frames must never be worse than "daily prices held flat intraday".
 */
class IntradayHistory(private val prices: CryptoPricesCryptoCompare) {

    /** One symbol's refined points; [anchorDay] is the epoch day the
     *  window was anchored to (the export's last transaction day). */
    data class IntradayData(val anchorDay: Long, val points: List<Pair<Long, Double>>)

    private val cacheFile: File
        get() = File(InstanceVars.applicationContext.filesDir, "history_prices_intraday.json")

    private val memo = HashMap<String, IntradayData>()
    private var loaded = false

    /**
     * Makes sure [symbol] has intraday refinement for the window anchored
     * at [anchorDay] (the export's last transaction day; refetching when
     * the cache was anchored elsewhere), and returns the best available
     * data - the refined points, the cache from another anchor, or null.
     * [daily] is the cached daily closes: a refinement that contradicts
     * the close NEAREST THE ANCHOR by more than a factor 2 is the
     * same-ticker-different-coin trap (the BOOST incident) and is
     * rejected, same policy as the daily deep chain. (The check must not
     * compare against today: for an old export the two can legitimately
     * differ by a lot.)
     */
    fun ensure(symbol: String, daily: List<Pair<Long, Double>>, anchorDay: LocalDate): IntradayData? {
        if (symbol.uppercase() in CryptoPricesCryptoCompare.NO_LIVE_PRICE_SYMBOLS) {
            return memo[symbol] // ambiguous tickers stay refinement-free
        }
        loadCache()
        val cached = memo[symbol]
        if (cached != null && cached.anchorDay == anchorDay.toEpochDay()) return cached

        val refined = fetchRefined(symbol, anchorDay)
        if (refined != null && refined.first.isNotEmpty()) {
            val nearAnchor = daily.lastOrNull { it.first <= anchorDay.toEpochDay() }?.second
            if (nearAnchor != null && nearAnchor > 0.0) {
                val last = refined.first.last().second
                val ratio = last / nearAnchor
                if (ratio > 2.0 || ratio < 0.5) {
                    FileLog.w(
                        TAG,
                        "Intraday refinement for $symbol (last=$last) contradicts the daily close " +
                            "nearest the anchor ($nearAnchor, ratio=$ratio); rejecting it " +
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
        val pair = KrakenHistorySource.EUR_PAIRS[symbol.uppercase()]
        if (pair != null) {
            val fine = krakenCandles(pair, FRAME_15M.windowSeconds, daysBeforeMs(anchorDay, FRAME_15M.windowDays))
            val coarse = krakenCandles(pair, FRAME_6H.windowSeconds, daysBeforeMs(anchorDay, FRAME_6H.windowDays))
            val merged = (fine.orEmpty() + coarse.orEmpty())
                .distinctBy { it.first }
                .sortedBy { it.first }
            if (merged.isNotEmpty()) return merged to "Kraken"
        }
        val anchorEpochDay = anchorDay.toEpochDay()
        val hourly = prices.getHistoryRawHours(symbol, anchorEpochDay - FRAME_6H.windowDays, anchorEpochDay + 1)
        if (hourly != null && hourly.isNotEmpty()) return hourly to "CoinGecko hourly"
        return null
    }

    private fun daysBeforeMs(day: LocalDate, days: Int): Long =
        (day.toEpochDay() - days) * 86_400_000L

    /**
     * One keyless Kraken `0/public/OHLC` call at [intervalSeconds] since
     * [sinceMs] -> (tsSec, close) ascending, or null (unknown pair /
     * rate wall / error - the caller falls through to the CG catch-all).
     * Kraken asks for ~1.5 s between calls: the shared spacer keeps the
     * pass a good guest (two calls per symbol at worst).
     */
    private fun krakenCandles(pair: String, intervalSeconds: Int, sinceMs: Long): List<Pair<Long, Double>>? {
        space()
        return try {
            val response = PriceApi.kraken.ohlc(pair, intervalSeconds, sinceMs).execute()
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

        /** The frame windows (plan 009b scope decisions). */
        data class FrameSpec(val windowSeconds: Int, val windowDays: Int)

        val FRAME_15M = FrameSpec(15 * 60, 7)
        val FRAME_6H = FrameSpec(6 * 3600, 30)
    }
}
