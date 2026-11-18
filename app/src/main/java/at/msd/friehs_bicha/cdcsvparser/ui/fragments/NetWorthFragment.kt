package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import at.msd.friehs_bicha.cdcsvparser.app.NetWorthSnapshot
import at.msd.friehs_bicha.cdcsvparser.core.Core
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.core.CoreSource
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.NetWorthScreen

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    NetWorthScreen(
                        snapshot = _snapshot.value,
                        goalRows = _goalRows.value,
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
        core.chartSeriesLiveData.observe(viewLifecycleOwner) { refreshSnapshot() }
        refreshSnapshot()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the settings sections (a goal add/edit/delete
        // mutates the sheet without a core data post), so the rows are
        // re-read here - a fast JNI pair on the cpp-core thread.
        refreshSnapshot()
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
