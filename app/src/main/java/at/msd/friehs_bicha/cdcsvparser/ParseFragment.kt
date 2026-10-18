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
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.price.PriceHistoryPass
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker
import java.time.LocalDate
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.ParseScreen
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletSeries
import kotlinx.coroutines.launch

/**
 * Parse/overview screen (Compose surface, P3.1). An in-screen progress
 * indicator while the core processes the CSV, then the aggregate values as
 * metric cards.
 *
 * A watchdog stops the progress indicator if the data cannot finish
 * arriving - but the screen stays up: parsed data (transaction list,
 * wallets, money spent) is usable without live prices, and a late arrival
 * still updates the metric cards through the same observer.
 */
class ParseFragment : Fragment() {

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val parseTimeout = Runnable {
        if (!isResumed) return@Runnable
        FileLog.w(TAG, "Parsing did not finish within $PARSE_TIMEOUT_MS ms; staying on the screen with the data we have.")
        _isParsing.value = false
        view?.let { Snackbar.make(it, R.string.parsing_timeout, Snackbar.LENGTH_LONG).show() }
    }

    private val _isParsing = mutableStateOf(true)
    /** Core map keys (old layout ids) -> display value; null = hidden. */
    private val _values = mutableStateMapOf<String, String?>()
    private val _profitLossColor = mutableStateOf(androidx.compose.ui.graphics.Color.Unspecified)
    private val _dailySeries = mutableStateOf<List<String>>(emptyList())
    /** "CUR;YYYY-MM-DD;balance;bonus" wallet lines (G36 historical pricing). */
    private val _walletSeries = mutableStateOf<List<String>>(emptyList())
    private val _currentPrices = mutableStateOf<Map<String, Double>>(emptyMap())
    private val _noInternet = mutableStateOf(false)
    private val _attributionVisible = mutableStateOf(true)

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
                        values = _values.toMap(),
                        profitLossColor = _profitLossColor.value,
                        attributionVisible = _attributionVisible.value,
                        dailySeries = _dailySeries.value,
                        walletSeries = _walletSeries.value,
                        // Plan 008: the single process-wide pass publishes these
                        // shared states; every visit observes the same objects,
                        // so a return re-joins the live counter (no second
                        // pass, no jumping counts).
                        history = PriceHistoryPass.history.value,
                        intraday = PriceHistoryPass.intraday.value,
                        historyProgress = PriceHistoryPass.progress.value,
                        currentPrices = _currentPrices.value,
                        noInternet = _noInternet.value,
                        onFilterClick = { findNavController().navigate(R.id.walletViewFragment) },
                        onAllTransactionsClick = { findNavController().navigate(R.id.transactionsFragment) },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _isParsing.value = true
        timeoutHandler.postDelayed(parseTimeout, PARSE_TIMEOUT_MS)
        displayInformation(view)
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
        super.onDestroyView()
    }

    private fun displayInformation(view: View) {
        // The counter is cumulative across runs; only a value newer than the
        // one already replayed to this observer counts as an error of this run.
        val initialErrors = CoreService.errorCounter.value ?: 0
        CoreService.parsedDataLiveData.observe(viewLifecycleOwner) {
            Benchmarker.stop()
            fillFromMap(it)
            FileLog.d(TAG, "parsedDataLiveData changed")
            loadDailySeries()
            _isParsing.value = false
            if (CoreService.lastFailedLines > 0) {
                Snackbar.make(
                    view,
                    getString(R.string.unparsable_lines_skipped, CoreService.lastFailedLines),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
        CoreService.errorCounter.observe(viewLifecycleOwner) { count ->
            if (count == null || count <= initialErrors) return@observe
            FileLog.w(TAG, "errorCounterLiveData changed: $count")
            Snackbar.make(view, R.string.error_while_parsing, Snackbar.LENGTH_LONG).show()
            _isParsing.value = false
            popBack()
        }
    }

    private fun fillFromMap(texts: Map<String, String?>?) {
        if (texts == null) {
            FileLog.e(TAG, "texts is null")
            return
        }
        _values.clear()
        texts.forEach { (key, value) ->
            // The core signals "hide the API attribution" with an explicit
            // null under this key (no crypto transactions in the file).
            if (key == R.id.coinGeckoApiLabel.toString()) {
                _attributionVisible.value = value != null
            }
            if (value == null) return@forEach
            if (key == R.id.assets_valueP.toString() && value == "no internet connection") {
                _noInternet.value = true
            }
            _values[key] = value
            if (key == R.id.profit_loss_value.toString()) {
                _profitLossColor.value =
                    androidx.compose.ui.graphics.Color.trendColor(firstNumber(value), this)
            }
        }
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

    private fun loadDailySeries() {
        viewLifecycleOwner.lifecycleScope.launch {
            _dailySeries.value = CoreService.dailySeries()
            _walletSeries.value = CoreService.dailyWalletSeries()
            attachHistoryPass()
        }
    }

    /**
     * G36 + plan 008: make sure the chart has the daily EUR price history
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
        // Parsing itself takes well under a second; the budget mostly covers
        // the price fetch, which can wait out CoinGecko's first (worst case
        // 300 s) rate-limit cooldown on a throttled egress IP.
        private const val PARSE_TIMEOUT_MS = 300_000L
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
