package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.WalletViewScreen
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletRow
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletSortKey
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.WalletsViewModel
import kotlinx.coroutines.launch

/**
 * Wallet view screen (Compose surface, P3.1): allocation donut, searchable
 * and sortable list of all wallets, backed by [WalletsViewModel].
 */
class WalletViewFragment : Fragment() {

    private val viewModel: WalletsViewModel by viewModels()
    private val _rows = mutableStateOf<List<WalletRow>>(emptyList())
    private val _allRows = mutableStateOf<List<WalletRow>>(emptyList())
    private val _sortKey = mutableStateOf(0) // 0..3, see sortKeyFor()
    private val _sortDirection = mutableStateOf(0) // 0 = desc, 1 = asc

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Rows render on the main thread from the 5-minute price cache; if
        // that gap started while this screen was inactive, refresh in the
        // background (stale values keep showing until the data posts back).
        CoreService.refreshPricesIfStale()

        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    WalletViewScreen(
                        rows = _rows.value,
                        allRows = _allRows.value,
                        sortKey = _sortKey.value,
                        sortDirection = _sortDirection.value,
                        onWalletClick = { walletId ->
                            findNavController().navigate(
                                R.id.assetsFilterFragment,
                                Bundle().apply { putInt("walletID", walletId) },
                            )
                        },
                        onSearchChanged = { viewModel.onSearchChanged(it) },
                        onSortKeySelected = { index ->
                            _sortKey.value = index
                            viewModel.onSortChanged(sortKeyFor(index), _sortDirection.value == 1)
                        },
                        onSortDirectionSelected = { direction ->
                            _sortDirection.value = direction
                            viewModel.onSortChanged(
                                sortKeyFor(_sortKey.value), direction == 1
                            )
                        },
                    )
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.rows.collect { _rows.value = it }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.allRows.collect { _allRows.value = it }
                }
            }
        }
    }

    /** Mirrors the old spinner->sort mapping. */
    private fun sortKeyFor(index: Int): WalletSortKey = when (index) {
        1 -> WalletSortKey.ASSET_VALUE
        2 -> WalletSortKey.PERCENT
        3 -> WalletSortKey.TRANSACTIONS
        else -> WalletSortKey.AMOUNT
    }
}
