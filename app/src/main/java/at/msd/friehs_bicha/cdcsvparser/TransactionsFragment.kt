package at.msd.friehs_bicha.cdcsvparser

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
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.TransactionsScreen
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionsViewModel
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch

/**
 * All-transactions screen (Compose surface, P3.1): search + currency filter
 * chips on top of the transaction list, all fed by [TransactionsViewModel].
 */
class TransactionsFragment : Fragment() {

    private val viewModel: TransactionsViewModel by viewModels()
    private val _rows = mutableStateOf<List<TransactionRow>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    TransactionsScreen(
                        rows = _rows.value,
                        onRowClick = { id ->
                            findNavController().navigate(
                                R.id.transactionDetailFragment,
                                Bundle().apply { putInt("transactionID", id) },
                            )
                        },
                        onSearchChanged = { viewModel.onSearchChanged(it) },
                        onAssetFilterChanged = { viewModel.onAssetFilterChanged(it) },
                        onTypeFilterChanged = { viewModel.onTypeFilterChanged(it) },
                    )
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.visibleRows.collect { _rows.value = it }
                }
            }
        }
    }
}
