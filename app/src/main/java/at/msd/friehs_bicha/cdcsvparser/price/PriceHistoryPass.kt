package at.msd.friehs_bicha.cdcsvparser.price

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import at.msd.friehs_bicha.cdcsvparser.price.PriceHistoryProvider.DailyPrices
import java.time.LocalDate

/**
 * The single process-wide price-history pass. Screen visits are
 * OBSERVERS: [ensureAll] with the current export's currencies either
 * attaches to a pass that already covers the request (live, or completed
 * earlier today — every entry is fresh then) or supersedes the current
 * pass and starts a new one. The pass runs in the process scope, so it
 * survives screen navigation — a return re-joins the SAME live counters
 * instead of starting a second run.
 *
 * Why not view-scoped: the provider pass is fully blocking (its sources
 * answer without suspending), so a view-scope cancellation on navigation
 * was never observed — the old pass kept running while the returning visit
 * started a second one, and both wrote into the screen's Compose state:
 * the jumping "80 of xx / 7 of xx" counter and the duplicate fetches.
 *
 * [history] / [progress] live here on purpose: every visit passes the same
 * Compose state objects to the screens, so mid-pass values are live.
 */
object PriceHistoryPass {
    private const val TAG = "PriceHistoryPass"

    /** The internal cash buffer (fiat - face value, never fetched). */
    private const val FIAT_SYMBOL = "EUR"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Per-symbol history published so far (grows in; shared with screens). */
    val history: MutableState<Map<String, DailyPrices>> = mutableStateOf(emptyMap())

    /**
     * Per-symbol intraday refinement published by the pass's
     * phase 3; the intraday chart frames flatten it (missing entry = the
     * symbol only has the daily-held-flat fallback).
     */
    val intraday: MutableState<Map<String, IntradayHistory.IntradayData>> = mutableStateOf(emptyMap())

    /** (done, total) of the current pass; null = no pass started yet. */
    val progress: MutableState<Pair<Int, Int>?> = mutableStateOf(null)

    private var job: Job? = null
    private var passSymbols: List<String> = emptyList()
    private var passDays: Int = 0
    private var passAnchorDay: Long = Long.MIN_VALUE
    private var passCompletedDay: Long = -1L

    /**
     * Makes sure [symbols] have EUR price history covering the last [days]
     * days, either by attaching to the current pass (see the class
     * doc) or by superseding it and starting a new one, which then
     * publishes progress into [history] / [progress] in the background.
     * [anchorDay] (today - the frames' end day) anchors the intraday
     * refinement (it runs for ALL non-zero symbols, no activity gate);
     * a pass anchored elsewhere does not cover the request.
     * [intradaySymbols] (default = [symbols]) is the refinement's symbol
     * set: the caller filters it to the positions the ledger still holds -
     * zero symbols contribute nothing to any frame; the daily phase
     * always covers [symbols].
     *
     * The internal EUR cash buffer may sit in [symbols] (the per-wallet
     * series is buffer-inclusive) but is FIAT-SKIPPED: it is never
     * fetched or refined (its price is 1.0 by definition) and instead
     * gets a synthetic face-value daily series published into
     * [history], so a buffer-scoped consumer can price every day.
     */
    fun ensureAll(
        symbols: List<String>,
        days: Int,
        anchorDay: LocalDate?,
        intradaySymbols: List<String>? = null,
    ) {
        if (symbols.isEmpty()) return
        // The fiat-skip split (see the doc): the request's currency set
        // stays authoritative (covers / pass identity run on the FULL
        // set - a buffer-inclusive and a crypto-only request for the
        // same export attach to each other), only the fetch and the
        // refinement drop the fiat side.
        val fetchSymbols = symbols.filterNot { it == FIAT_SYMBOL }
        val live = job?.isActive == true
        val covers = passSymbols.toHashSet().containsAll(symbols) &&
            passDays >= days &&
            (anchorDay == null || passAnchorDay == anchorDay.toEpochDay())
        if (covers && (live || passCompletedDay >= PriceHistoryProvider.todayUtcDay())) {
            FileLog.d(
                TAG,
                "Attaching to the ${if (live) "live" else "completed"} history pass " +
                    "(${symbols.size} symbols, ${days} d).",
            )
            return
        }
        if (live) {
            FileLog.i(
                TAG,
                "Superseding the live history pass (${passSymbols.size} symbols, ${passDays} d) " +
                    "with a new request (${symbols.size} symbols, ${days} d).",
            )
            // A superseded pass stops after the symbol in flight (its
            // per-symbol disk saves keep everything it finished).
            job?.cancel()
        }
        if (passSymbols != symbols) {
            // Different export: the old maps no longer describe this one.
            // (The reset also drops a previously published face series -
            // it is re-published below with the new window.)
            history.value = emptyMap()
            intraday.value = emptyMap()
        }
        passSymbols = symbols
        passDays = days
        passAnchorDay = anchorDay?.toEpochDay() ?: Long.MIN_VALUE
        publishFaceSeries(symbols - fetchSymbols, days)
        if (fetchSymbols.isEmpty()) {
            // A fiat-only request never touches the chain.
            passCompletedDay = PriceHistoryProvider.todayUtcDay()
            progress.value = symbols.size to symbols.size
            return
        }
        val prices = AssetValue.getInstance().priceProvider as? CryptoPricesCryptoCompare
        if (prices == null) {
            FileLog.w(TAG, "Live price provider is not CryptoPricesCryptoCompare; no history pass.")
            return
        }
        progress.value = 0 to symbols.size
        job = scope.launch {
            try {
                PriceHistoryProvider(prices).ensureAll(
                    symbols = fetchSymbols,
                    days = days,
                    anchorDay = anchorDay,
                    intradaySymbols = (intradaySymbols ?: fetchSymbols)
                        .filterNot { it == FIAT_SYMBOL },
                    onProgress = { done, total -> progress.value = done to total },
                    onSymbol = { symbol, entry -> addSymbol(symbol, entry) },
                    onIntraday = { symbol, entry -> addIntraday(symbol, entry) },
                )
            } catch (e: CancellationException) {
                // Superseded by a newer request: the replacement owns the
                // counters now.
                return@launch
            }
            passCompletedDay = PriceHistoryProvider.todayUtcDay()
            progress.value = symbols.size to symbols.size
            FileLog.i(
                TAG,
                "History pass complete: ${history.value.size}/${symbols.size} symbols priced " +
                    "(range ${days} d).",
            )
        }
    }

    /**
     * The fiat side's published state: one 1.0 close per day of the
     * requested window (ascending). Completes synchronously (no
     * network); a consumer pricing a day BEFORE the series starts
     * back-fills to the first close - 1.0 likewise - so the window
     * only needs to cover the requested range, not a wider pass.
     */
    private fun publishFaceSeries(fiat: List<String>, days: Int) {
        if (fiat.isEmpty()) return
        val today = PriceHistoryProvider.todayUtcDay()
        val range = days.coerceAtLeast(1)
        val points = ArrayList<Pair<Long, Double>>(range)
        for (d in today - range + 1L..today) points.add(d to 1.0)
        val entry = DailyPrices(today, points)
        for (symbol in fiat) addSymbol(symbol, entry)
    }

    /**
     * Atomic map update: a superseded pass may still finish its in-flight
     * symbol while the replacement's cache burst is already publishing.
     */
    @Synchronized
    private fun addSymbol(symbol: String, entry: DailyPrices) {
        history.value = history.value + (symbol to entry)
    }

    @Synchronized
    private fun addIntraday(symbol: String, entry: IntradayHistory.IntradayData) {
        intraday.value = intraday.value + (symbol to entry)
    }
}
