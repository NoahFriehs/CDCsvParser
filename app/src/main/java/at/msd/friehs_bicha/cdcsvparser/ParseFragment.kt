package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import at.msd.friehs_bicha.cdcsvparser.core.Core
import at.msd.friehs_bicha.cdcsvparser.core.CoreSource
import at.msd.friehs_bicha.cdcsvparser.core.ParseData
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.price.PriceHistoryPass
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.HistoryDialogFragment
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import java.io.File
import java.time.LocalDate
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.ParseScreen
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletSeries
import kotlinx.coroutines.launch

/**
 * Parse/overview screen (Compose surface). An in-screen progress
 * indicator while the core processes the CSV, then the aggregate values as
 * metric cards.
 *
 * A watchdog stops the progress indicator if the data cannot finish
 * arriving - but the screen stays up: parsed data (transaction list,
 * wallets, money spent) is usable without live prices, and a late arrival
 * still updates the metric cards through the same observer.
 */
class ParseFragment : Fragment() {

    /** The core surface this fragment reads. */
    internal var core: CoreSource = Core
        private set

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val parseTimeout = Runnable {
        if (!isResumed) return@Runnable
        // Gate on the live state, not on time alone: both observers cancel
        // this Runnable the moment data or the error signal arrives, so a
        // stay-on-screen (or a slow late post) can never fire the warning
        // after the parse actually settled (the 7-of-8 false positives, #54).
        if (!_isParsing.value) return@Runnable
        FileLog.w(TAG, "Parsing did not finish within $PARSE_TIMEOUT_MS ms; staying on the screen with the data we have.")
        _isParsing.value = false
        view?.let { Snackbar.make(it, R.string.parsing_timeout, Snackbar.LENGTH_LONG).show() }
    }

    // Latched in onViewCreated from the navigation argument; only the
    // data observer shows it (see there) - see ARG_DEDUP_NOTE.
    private var pendingDedupNote = false
    private val dedupNoteHandler = Handler(Looper.getMainLooper())

    /**
     * The counted-duplicate note of a COMBINED import: the core's skip
     * counter is read once per combined run (latched in the service),
     * shown debounced exactly like the dedup re-upload note - both notes
     * are one-shot and mutually exclusive (a single re-upload never
     * dedups, a combined import never arrives as a re-upload note).
     */
    private var pendingDuplicateSkips = 0L
    private val dupNoteHandler = Handler(Looper.getMainLooper())

    /**
     * Shows the pending dedup note. Lives on its own handler (not
     * [timeoutHandler]) because it is re-debounced on every data post;
     * the delay outlasts the skip note's LENGTH_LONG (2700 ms) so the
     * note lands in a free snack slot after the last post of the run.
     */
    private val dedupNoteRunnable = Runnable {
        val v = view  // local: Fragment.view is open - no smart cast
        if (isAdded && v != null) showDedupNote(v)
    }

    private val dupNoteRunnable = Runnable {
        val v = view  // local: Fragment.view is open - no smart cast
        if (isAdded && v != null) showDuplicateNote(v)
    }

    private val _isParsing = mutableStateOf(true)
    /**
     * The combined-view badge (file count, null = hidden): the live
     * model is a union of N persisted history files, not a plain single
     * import - the badge makes the fast-start rehydrated state
     * explainable after every restart. Source of truth = the DataStore
     * selection, re-read at view creation AND on every data post (the
     * import pipeline updates it right before its post).
     */
    private val _combinedFileCount = mutableStateOf<Int?>(null)
    /** Typed parse cards (null = not posted yet); a null field = hidden row. */
    private val _parseData = mutableStateOf<ParseData?>(null)
    private val _profitLossColor = mutableStateOf(androidx.compose.ui.graphics.Color.Unspecified)
    private val _dailySeries = mutableStateOf<List<String>>(emptyList())
    /** "CUR;YYYY-MM-DD;balance;bonus" wallet lines (historical pricing). */
    private val _walletSeries = mutableStateOf<List<String>>(emptyList())
    private val _currentPrices = mutableStateOf<Map<String, Double>>(emptyMap())
    private val _noInternet = mutableStateOf(false)
    private val _attributionVisible = mutableStateOf(true)
    /**
     * The chart panel's wallet scope (currency); null = the aggregate view.
     * Fragment-instance state on purpose (like the chart rows around it):
     * a recreated view re-reads it from [onCreateView], so the selection
     * survives deep navigation back and theme toggles. Process death
     * resets it to the aggregate like every other screen state.
     */
    private val _chartWalletScope = mutableStateOf<String?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    ParseScreen(
                        isParsing = _isParsing.value,
                        data = _parseData.value,
                        profitLossColor = _profitLossColor.value,
                        attributionVisible = _attributionVisible.value,
                        dailySeries = _dailySeries.value,
                        walletSeries = _walletSeries.value,
                        // The single process-wide pass publishes these
                        // shared states; every visit observes the same objects,
                        // so a return re-joins the live counter (no second
                        // pass, no jumping counts).
                        history = PriceHistoryPass.history.value,
                        intraday = PriceHistoryPass.intraday.value,
                        historyProgress = PriceHistoryPass.progress.value,
                        currentPrices = _currentPrices.value,
                        noInternet = _noInternet.value,
                        chartWalletScope = _chartWalletScope.value,
                        onChartWalletScopeChanged = { _chartWalletScope.value = it },
                        onFilterClick = { findNavController().navigate(R.id.walletViewFragment) },
                        onAllTransactionsClick = { findNavController().navigate(R.id.transactionsFragment) },
                        combinedFileCount = _combinedFileCount.value,
                        onCombinedBadgeClick = { onCombinedBadgeClick() },
                        core = core,
                    )
                }
            }
            // The badge re-open: the pre-checked multi-select dialog.
            childFragmentManager.setFragmentResultListener(
                HistoryDialogFragment.REQUESTED_SELECT_COMBINED, viewLifecycleOwner
            ) { _, bundle ->
                val fileNames =
                    bundle.getStringArrayList(HistoryDialogFragment.KEY_FILES)
                        ?: return@setFragmentResultListener
                startCombinedImportFromBadge(fileNames)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _isParsing.value = true
        timeoutHandler.postDelayed(parseTimeout, PARSE_TIMEOUT_MS)
        displayInformation(view)
        // One-shot note from the import side: this navigation was a
        // re-upload of a content-identical file (the history entry got
        // refreshed, not duplicated). It rides the navigation arguments
        // - NOT a LiveData observer, whose replay/re-posts fire at
        // unpredictable moments relative to this screen. Consumed
        // exactly once (removed after read, like the "fastStart" extra):
        // a recreated view must never re-show it.
        // One-shot note from the import side: this navigation was a
        // re-upload of a content-identical file (the history entry got
        // refreshed, not duplicated). It rides the navigation arguments -
        // NOT a LiveData value. Consumed exactly once (removed after read,
        // like the "fastStart" extra): a recreated view must never
        // re-show it. The ACTUAL showing is scheduled in the data observer
        // (snackbars replace whatever is showing - the replay post fires
        // its skip note on resume and would steal the slot otherwise).
        if (arguments?.getBoolean(ARG_DEDUP_NOTE, false) == true) {
            arguments?.remove(ARG_DEDUP_NOTE)
            pendingDedupNote = true
        }
        // Fast-start landing: the selection may already be persisted
        // (model rehydrated from the v3 save) - the data observer keeps
        // it fresh when an import in flight updates it.
        refreshCombinedBadge()
        // #47: the settings gear lives in the activity action bar (one
        // header bar, like every other screen). View-scoped provider:
        // it is attached for the lifetime of THIS fragment's view and
        // removed with it, so other destinations keep a plain bar.
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    // A navigation transition can briefly hold both
                    // views (old + new) alive at once — never add the
                    // gear twice to the same menu.
                    if (menu.findItem(R.id.action_settings) != null) return
                    menuInflater.inflate(R.menu.menu_settings, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
                        R.id.action_settings -> {
                            findNavController().navigate(R.id.settingsFragment)
                            true
                        }

                        else -> false
                    }
            },
            viewLifecycleOwner,
        )
    }

    override fun onDestroyView() {
        timeoutHandler.removeCallbacks(parseTimeout)
        dedupNoteHandler.removeCallbacks(dedupNoteRunnable)
        dupNoteHandler.removeCallbacks(dupNoteRunnable)
        super.onDestroyView()
    }

    /** Shows (and consumes) the pending dedup note. */
    private fun showDedupNote(view: View) {
        if (!pendingDedupNote) return
        pendingDedupNote = false
        Snackbar.make(view, R.string.history_updated_existing, Snackbar.LENGTH_SHORT).show()
    }

    /** Shows (and consumes) the pending counted-duplicate note. */
    private fun showDuplicateNote(view: View) {
        if (pendingDuplicateSkips <= 0L) return
        val count = pendingDuplicateSkips
        pendingDuplicateSkips = 0L
        Snackbar.make(
            view,
            getString(R.string.duplicates_skipped, count),
            Snackbar.LENGTH_LONG,
        ).show()
    }

    /**
     * Re-reads the persisted combined selection; null hides the badge
     * (a plain single model is never "combined · 0 files").
     */
    private fun refreshCombinedBadge() {
        val count = PreferenceHelper.getCombinedSelection(requireContext()).size
        _combinedFileCount.value = count.takeIf { it > 0 }
    }

    /**
     * The badge tap: the same multi-select dialog, the persisted
     * selection pre-checked - "Open combined" re-imports it (possibly
     * edited), a row tap is the plain single re-import.
     */
    private fun onCombinedBadgeClick() {
        HistoryDialogFragment.prechecked().show(childFragmentManager, "combined_selection")
    }

    /**
     * Combined import dispatched from this screen. The in-screen parse
     * contract is the same as the entry parse: the data observer settles
     * the progress (this screen stays up, unlike the main screen's
     * dialog + navigation), so re-arm the watchdog for this run.
     */
    private fun startCombinedImportFromBadge(fileNames: List<String>) {
        val filesDir = requireContext().filesDir
        val selection = fileNames.filter { File(filesDir, it).isFile }
        if (selection.size < 2) {
            Snackbar.make(
                requireView(),
                getString(R.string.history_file_missing),
                Snackbar.LENGTH_LONG,
            ).show()
            refreshCombinedBadge()
            return
        }
        val mode = PreferenceHelper.getSelectedType(requireContext()).ordinal
        FileLog.d(TAG, "Import dispatched: combined ${selection.size} file(s) from the badge (mode $mode)")
        Benchmarker.start()
        core.startCombinedSelection(selection, mode)
        _isParsing.value = true
        timeoutHandler.removeCallbacks(parseTimeout)
        timeoutHandler.postDelayed(parseTimeout, PARSE_TIMEOUT_MS)
    }

    private fun displayInformation(view: View) {
        // The counter is cumulative across runs; only a value newer than the
        // one already replayed to this observer counts as an error of this run.
        val initialErrors = core.errorCounter.value ?: 0
        core.parsedDataLiveData.observe(viewLifecycleOwner) {
            timeoutHandler.removeCallbacks(parseTimeout)  // data arrived: nothing left to watch
            Benchmarker.stop()
            fillFromData(it)
            FileLog.d(TAG, "parsedDataLiveData changed")
            _isParsing.value = false
            // The pipeline updates the selection right before its post, so
            // this read is always the one the posted model describes.
            refreshCombinedBadge()
            if (core.lastFailedLines > 0) {
                Snackbar.make(
                    view,
                    getString(R.string.unparsable_lines_skipped, core.lastFailedLines),
                    Snackbar.LENGTH_LONG
                ).show()
            }
            val duplicateSkips = core.lastDuplicateSkips
            if (duplicateSkips > 0L) {
                // Same one-slot discipline as the dedup note: debounce it
                // past the skip note's LENGTH_LONG so it lands in a free
                // snack slot after the last post of the run.
                pendingDuplicateSkips = duplicateSkips
                dupNoteHandler.removeCallbacks(dupNoteRunnable)
                dupNoteHandler.postDelayed(dupNoteRunnable, DEDUP_NOTE_DELAY_MS)
            }
            if (pendingDedupNote) {
                // Snackbars replace, they don't queue - and the skip note
                // above holds the slot for LENGTH_LONG (2700 ms). The
                // observer can re-fire (replay / re-posts), so DEBOUNCE
                // the dedup note off the LAST firing of the run: by the
                // time it shows, the slot is guaranteed to be free.
                dedupNoteHandler.removeCallbacks(dedupNoteRunnable)
                dedupNoteHandler.postDelayed(dedupNoteRunnable, DEDUP_NOTE_DELAY_MS)
            }
        }
        // The chart rows ride the card post (see CoreService): observing
        // them here refills the panel from the replayed post on EVERY
        // view recreation, exactly like the cards. The writes mirror the
        // post - the rows are the truth for the current file, so a
        // genuinely empty series hides the panel again on file switches.
        core.chartSeriesLiveData.observe(viewLifecycleOwner) { series ->
            _dailySeries.value = series.daily
            _walletSeries.value = series.wallet
            FileLog.d(
                TAG,
                "chartSeriesLiveData changed: ${series.daily.size} daily, " +
                    "${series.wallet.size} wallet rows"
            )
            // The dropped one-shot side query used to trigger this; it
            // attaches to the process-wide price-history pass (zero new
            // work when the same request already runs / finished), so
            // calling it on every arrival is safe and cheap.
            viewLifecycleOwner.lifecycleScope.launch {
                attachHistoryPass()
            }
        }
        core.errorCounter.observe(viewLifecycleOwner) { count ->
            if (count == null || count <= initialErrors) return@observe
            timeoutHandler.removeCallbacks(parseTimeout)  // the error signal ended the run: nothing left to watch
            FileLog.w(TAG, "errorCounterLiveData changed: $count")
            Snackbar.make(view, R.string.error_while_parsing, Snackbar.LENGTH_LONG).show()
            _isParsing.value = false
            popBack()
        }
    }

    private fun fillFromData(data: ParseData?) {
        if (data == null) {
            FileLog.e(TAG, "parseData is null")
            return
        }
        // The producer owns the attribution flag explicitly: false for
        // files without crypto transactions (the old contract carried that
        // as an explicit null under R.id.coinGeckoApiLabel).
        _attributionVisible.value = data.attributionVisible
        // Set-once, like before: a no-internet card keeps the flag for the
        // life of this fragment (new import = new fragment = fresh state).
        if (data.assets == "no internet connection") {
            _noInternet.value = true
        }
        if (data.profitLoss != null) {
            _profitLossColor.value =
                androidx.compose.ui.graphics.Color.trendColor(firstNumber(data.profitLoss), this)
        }
        _parseData.value = data
    }

    /** Extracts the first number of a formatted value like "-123.4 €". */
    private fun firstNumber(formatted: String): Double? {
        val match = Regex("-?[\\d.]+").find(formatted) ?: return null
        return match.value.replace(",", ".").toDoubleOrNull()
    }

    private fun popBack() {
        if (!isAdded) return
        findNavController().popBackStack()
    }

    /**
     * Make sure the chart has the daily EUR price history
     * for every currency that holds assets — by ATTACHING to the single
     * process-wide pass ([PriceHistoryPass]): already running (or completed
     * today) with the same request = zero new work, the screen just reads
     * the shared states; otherwise the pass (re)starts in the background,
     * cached symbols are published immediately (zero network, so a
     * relaunch shows the historical shape at once instead of a long
     * flatline) and the remaining fetches land incrementally.
     */
    private fun attachHistoryPass() {
        val walletData = parseWalletSeries(_walletSeries.value)
        val currencies = walletData.keys.toList()
        if (currencies.isEmpty()) return
        val points = walletData.values.flatten()
        val startDay = points.minOf { it.date.toEpochDay() }
        // The intraday frames end at NOW (today) for EVERY export - fresh
        // or stale: a held position older than the frame window must still
        // ride REAL intraday candles. The core's opening line carries the
        // position into the window, the chart re-prices it per slot, and
        // refinement fetches real intraday candles ending at today - so a
        // stale export charts exactly like a fresh one.
        val anchorDay = LocalDate.now()
        val rangeDays = ((LocalDate.now().toEpochDay() - startDay).coerceAtLeast(0) + 1).toInt()
        // The intraday REFINEMENT runs only for symbols the ledger still
        // holds (non-zero balance OR bonus at the last row - the current
        // position): a zero position contributes nothing to any frame, so
        // refining it is pure network cost. The daily phase
        // keeps its existing symbol set (its tail is cheap and cached).
        val heldSymbols = walletData
            .filter { entry -> entry.value.last().balance != 0.0 || entry.value.last().bonus != 0.0 }
            .keys
            .toList()
        // The live prices the cards were valued with (pin for today's bucket).
        val assets = AssetValue.getInstance()
        _currentPrices.value = currencies.associateWith { assets.cachedPrice(it) }
            .filterValues { it != null }
            .mapValues { it.value!! }
        PriceHistoryPass.ensureAll(currencies, rangeDays, anchorDay, intradaySymbols = heldSymbols)
    }

    companion object {
        private const val TAG = "ParseFragment"
        /** Navigation argument: the import that opened this screen was a deduped re-upload. */
        internal const val ARG_DEDUP_NOTE = "dedupNote"
        // Parsing itself takes well under a second; the budget mostly covers
        // the price fetch, which can wait out CoinGecko's first (worst case
        // 300 s) rate-limit cooldown on a throttled egress IP.
        private const val PARSE_TIMEOUT_MS = 300_000L

        // Skip note (LENGTH_LONG = 2700 ms) + margin: the dedup note is
        // scheduled this far after the last data post of a run.
        private const val DEDUP_NOTE_DELAY_MS = 2_800L
    }
}

/** Maps a +/- value to the trend color (or the default on-surface color). */
private fun androidx.compose.ui.graphics.Color.Companion.trendColor(
    value: Double?,
    fragment: ParseFragment,
): androidx.compose.ui.graphics.Color {
    val ctx = fragment.requireContext()
    return when {
        value == null -> androidx.compose.ui.graphics.Color(
            ContextCompat.getColor(ctx, R.color.on_surface)
        )

        value > 0 -> androidx.compose.ui.graphics.Color(
            ContextCompat.getColor(ctx, R.color.trend_positive)
        )

        value < 0 -> androidx.compose.ui.graphics.Color(
            ContextCompat.getColor(ctx, R.color.trend_negative)
        )

        else -> androidx.compose.ui.graphics.Color(
            ContextCompat.getColor(ctx, R.color.on_surface)
        )
    }
}
