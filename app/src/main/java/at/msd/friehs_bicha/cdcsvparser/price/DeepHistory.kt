package at.msd.friehs_bicha.cdcsvparser.price

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog

/**
 * A deep daily-EUR price source (history beyond CoinGecko's public 365-day
 * window). [dailyEur] answers on the Dispatchers.IO thread during the
 * history pass and must FAIL FAST (no sleeping on a hot path, no throwing):
 *
 * @return the symbol's daily closes on UTC epoch days [fromDay]..[toDay]
 *         (ascending, one point per day — sub-daily candles reduced to the
 *         day's close), or null when this source cannot serve the symbol:
 *         unknown pair/id, rejected request (401/403/429), the series ends
 *         far before [toDay], or any network error.
 *
 * A source that meets an auth/rate wall sets [passExhausted]; the
 * [DeepHistoryChain] then skips it for the rest of the pass.
 */
interface DeepHistorySource {
    val name: String

    var passExhausted: Boolean

    /** Hook at pass start (per-process budgets reset their pass counter). */
    fun onPassStart() {}

    fun dailyEur(symbol: String, fromDay: Long, toDay: Long): List<Pair<Long, Double>>?
}

/**
 * The fallback chain over the deep sources. Per symbol, the first source
 * returning a USABLE series wins; nulls and failures just walk the chain on.
 * "Usable" = non-empty AND reaching within a day of [toDay] (a series ending
 * at an old delisting cannot price the present and must not win).
 *
 * Chain order encodes the budget policy: free keyless sources first
 * (Bitstamp, Kraken), the keyless 365-day CoinGecko live path is tried by
 * the caller when the chain comes up empty, and the budget-limited keyed
 * source (CryptoCompare, 100 calls/month) is the LAST deep source.
 */
class DeepHistoryChain(private val sources: List<DeepHistorySource>) {

    private companion object {
        private const val TAG = "DeepChain"
    }

    fun beginPass() {
        sources.forEach { it.onPassStart() }
    }

    /** @return (series, winning source name) or null when every source declined. */
    fun fetch(symbol: String, fromDay: Long, toDay: Long): Pair<List<Pair<Long, Double>>, String>? {
        // Same ambiguous-ticker list as the live price path: these symbols
        // must stay UNPRICED everywhere (the deep sources' plain-ticker
        // lookups would pick a same-named different coin — see BOOST).
        if (symbol.uppercase() in CryptoPricesCryptoCompare.NO_LIVE_PRICE_SYMBOLS) {
            FileLog.d(TAG, "Deep history skipped for $symbol (ambiguous ticker, see NO_LIVE_PRICE_SYMBOLS).")
            return null
        }
        for (source in sources) {
            if (source.passExhausted) continue
            val series = source.dailyEur(symbol, fromDay, toDay)
            if (series != null && series.isNotEmpty() && series.last().first >= toDay - 1) {
                return series to source.name
            }
        }
        return null
    }
}
