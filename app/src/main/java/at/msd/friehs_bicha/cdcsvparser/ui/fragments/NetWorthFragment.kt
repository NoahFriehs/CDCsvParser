package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.GoalLine
import at.msd.friehs_bicha.cdcsvparser.app.GoalProgress
import at.msd.friehs_bicha.cdcsvparser.app.GoalProgressRow
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.app.NetWorthSnapshot
import at.msd.friehs_bicha.cdcsvparser.app.networth.ReturnSeriesBuilder
import at.msd.friehs_bicha.cdcsvparser.app.networth.ReturnsMetrics
import at.msd.friehs_bicha.cdcsvparser.core.ChartSeries
import at.msd.friehs_bicha.cdcsvparser.core.Core
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.core.CoreSource
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.price.PriceHistoryPass
import at.msd.friehs_bicha.cdcsvparser.price.PriceHistoryProvider
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.NetWorthScreen
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletSeries
import java.time.LocalDate

/**
 * Net-worth screen (Compose surface): hosts [NetWorthScreen]. The values
 * come from the core's snapshot contract ([CoreService.netWorthSnapshot]
 * on the cpp-core thread) and are re-read on every core data post (the
 * fast-start readiness + the price-refresh rhythm) - the screen holds
 * only the remembered values, the core stays the source of the figures.
 */
class NetWorthFragment : Fragment() {

    /** The core surface this fragment reads (test-injectable). */
    internal var core: CoreSource = Core
        private set

    /** The remembered snapshot (null = not ready / no live core yet). */
    private val _snapshot = mutableStateOf<NetWorthSnapshot?>(null)

    /**
     * The remembered goals card rows (the user's goal lines joined with
     * the core's GP progress lines from the same snapshot pass).
     */
    private val _goalRows = mutableStateOf<List<GoalProgressRow>>(emptyList())

    /** The remember returns block state (core metric lines parsed + the
     * priced series' first day for the caption); null = nothing to show. */
    private data class ReturnsState(
        val metrics: ReturnsMetrics,
        val firstDay: LocalDate,
    )

    /** The remembered returns block (null = no series yet / core answer
     * empty). */
    private val _returns = mutableStateOf<ReturnsState?>(null)

    /**
     * The last chart-series post (the per-wallet lines are the returns
     * builder's input; the daily lines carry the spent flow for the
     * cash-flow column).
     */
    private val _chartSeries = mutableStateOf<ChartSeries?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    // Reading the shared pass state inside the composition
                    // re-runs this block as the pass publishes symbols -
                    // the same observation rhythm as the chart panel.
                    val history = PriceHistoryPass.history.value
                    returnsEffect(_chartSeries.value, history)
                    NetWorthScreen(
                        snapshot = _snapshot.value,
                        goalRows = _goalRows.value,
                        returns = _returns.value?.metrics,
                        returnsFirstDay = _returns.value?.firstDay,
                        onTrackedAssetsClick = {
                            findNavController().navigate(R.id.walletViewFragment)
                        },
                        onSettingsClick = {
                            findNavController().navigate(R.id.settingsFragment)
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // This destination must never offer the settings action. The
        // gear itself comes from the view-scoped MenuProviders of the
        // other fragments; a navigation transition can briefly hold two
        // views alive at once, so this destination removes the item on
        // every menu build within its own view lifetime instead of
        // relying on the leaving fragment's provider to be gone first.
        // View-scoped: it is gone with this view.
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.findItem(R.id.action_settings)?.let { menu.removeItem(it.itemId) }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false
            },
            viewLifecycleOwner,
        )
        // Every core data post (the fast-start readiness + the price
        // refresh) re-reads the snapshot line: the tracked value is
        // priced, so a new price pass moves the net worth. The read on
        // the cpp-core thread is a fast JNI call (no state here of its
        // own to invalidate).
        core.chartSeriesLiveData.observe(viewLifecycleOwner) { series ->
            _chartSeries.value = series
            refreshSnapshot()
            // Join the process-wide deep-history pass (zero new work when
            // the same request already runs / finished today): the
            // returns builder prices from its cache, exactly like the
            // chart (the pass attach itself is non-blocking - it only
            // starts/attaches the process-scope job).
            attachReturnsPass(series)
        }
        refreshSnapshot()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the settings sections (a goal add/edit/delete
        // mutates the sheet without a core data post), so the rows are
        // re-read here - a fast JNI pair on the cpp-core thread.
        refreshSnapshot()
    }

    /**
     * Joins the process-wide deep-history pass for the held currencies.
     * The EUR cash buffer sits in the currency set (the per-wallet series
     * is buffer-inclusive) but the pass FIAT-SKIPS it (face value, never
     * fetched); no intraday refinement (anchor day null) - the return
     * metric is daily-granularity.
     */
    private fun attachReturnsPass(series: ChartSeries) {
        val walletDays = parseWalletSeries(series.wallet)
        val currencies = walletDays.keys.toList()
        if (currencies.isEmpty()) return
        val startDay = walletDays.values.flatten().minOf { it.date }
        val rangeDays = ((LocalDate.now().toEpochDay() - startDay.toEpochDay()).coerceAtLeast(0) + 1).toInt()
        PriceHistoryPass.ensureAll(currencies, rangeDays, null)
    }

    /**
     * (Re)builds the remembered returns block: the builder prices the
     * core's unpriced per-wallet series with the pass's published cache
     * (no network of its own), the core computes the metric lines on the
     * cpp-core thread. Re-runs on every series post and every pass
     * publish; the work is pure composition (the chart's rhythm).
     */
    @Composable
    private fun returnsEffect(
        series: ChartSeries?,
        history: Map<String, PriceHistoryProvider.DailyPrices>,
    ) {
        LaunchedEffect(series, history) {
            if (series == null) {
                _returns.value = null
                return@LaunchedEffect
            }
            val walletDays = parseWalletSeries(series.wallet)
            val currencies = walletDays.keys.toList()
            if (currencies.isEmpty()) {
                _returns.value = null
                return@LaunchedEffect
            }
            // The pass's prices (cache-aware, the EUR buffer pinned to
            // 1.0, no network) - the same source the core's price push
            // uses, so the last V equals the tracked-assets figure.
            val livePrices = AssetValue.getInstance().getPricesFromCache(currencies)
            val priced = ReturnSeriesBuilder.build(
                walletSeries = series.wallet,
                dailyRows = series.daily,
                history = history.mapValues { it.value.points },
                livePrices = livePrices,
                today = LocalDate.now(),
            )
            val lines = priced.lines
            val firstDay = priced.firstDay
            // Cross-check transport (debug): the exact lines sent to the
            // core metric - pulled from the device log, re-verified on
            // the host (the host's core sees byte-identical input).
            // DEBUG level: the full series at every rebuild must not
            // flood the file log (the level can be raised in settings).
            if (lines.isNotEmpty()) {
                FileLog.d("NetWorthReturns", "Returns series ${lines.size} lines:\n" + lines.joinToString("\n"))
            }
            _returns.value = firstDay?.let {
                lines.takeIf { l -> l.isNotEmpty() }
                    ?.let { CoreService.returnMetrics(it) }
                    ?.let { ReturnsMetrics.fromLines(it) }
                    ?.let { ReturnsState(it, firstDay) }
            }
        }
    }

    /** The snapshot lines off the core (cpp-core thread), parsed thin. */
    private fun refreshSnapshot() {
        val lines = CoreService.netWorthSnapshot()
        _snapshot.value = lines.firstOrNull()?.let { NetWorthSnapshot.parse(it) }
        // The goals card: the user's goal lines (label, target date,
        // metric) joined with the core's GP lines (the progress from the
        // same pass) - both reads on the cpp-core thread.
        val goals = CoreService.sheetLines(GoalLine.KIND).mapNotNull { GoalLine.parse(it) }
        val progress = lines.mapNotNull { GoalProgress.parse(it) }
        _goalRows.value = GoalProgressRow.from(goals, progress)
    }
}
