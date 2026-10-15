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

/**
 * The single process-wide price-history pass (plan 008). Screen visits are
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Per-symbol history published so far (grows in; shared with screens). */
    val history: MutableState<Map<String, DailyPrices>> = mutableStateOf(emptyMap())

    /** (done, total) of the current pass; null = no pass started yet. */
    val progress: MutableState<Pair<Int, Int>?> = mutableStateOf(null)

    private var job: Job? = null
    private var passSymbols: List<String> = emptyList()
    private var passDays: Int = 0
    private var passCompletedDay: Long = -1L

    /**
     * Makes sure [symbols] have EUR price history covering the last [days]
     * days, either by attaching to the current pass (see the class
     * doc) or by superseding it and starting a new one, which then
     * publishes progress into [history] / [progress] in the background.
     */
    fun ensureAll(symbols: List<String>, days: Int) {
        if (symbols.isEmpty()) return
        val live = job?.isActive == true
        val covers = passSymbols.toHashSet().containsAll(symbols) && passDays >= days
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
            // Different export: the old map no longer describes this one.
            history.value = emptyMap()
        }
        val prices = AssetValue.getInstance().priceProvider as? CryptoPricesCryptoCompare
        if (prices == null) {
            FileLog.w(TAG, "Live price provider is not CryptoPricesCryptoCompare; no history pass.")
            return
        }
        passSymbols = symbols
        passDays = days
        progress.value = 0 to symbols.size
        job = scope.launch {
            try {
                PriceHistoryProvider(prices).ensureAll(
                    symbols = symbols,
                    days = days,
                    onProgress = { done, total -> progress.value = done to total },
                    onSymbol = { symbol, entry -> addSymbol(symbol, entry) },
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
     * Atomic map update: a superseded pass may still finish its in-flight
     * symbol while the replacement's cache burst is already publishing.
     */
    @Synchronized
    private fun addSymbol(symbol: String, entry: DailyPrices) {
        history.value = history.value + (symbol to entry)
    }
}
