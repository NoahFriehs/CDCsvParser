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
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.compose.AssetsFilterScreen
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletRow
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionsViewModel
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.WalletsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Asset filter screen (Compose surface, P3.1): per-wallet aggregate values
 * + the wallet's transactions (argument: walletID, optional).
 */
class AssetsFilterFragment : Fragment() {

    private val walletsViewModel: WalletsViewModel by viewModels()
    private val transactionsViewModel: TransactionsViewModel by viewModels()

    private val selectedWalletId = MutableStateFlow(-1)
    private val _wallets = mutableStateOf<List<WalletRow>>(emptyList())
    private val _selectedIndex = mutableStateOf(-1)
    private val _values = mutableStateOf<Map<String, String?>>(emptyMap())
    private val _walletTransactions = mutableStateOf<List<TransactionRow>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // One rows-for-wallet flow for the life of the fragment; the
        // selected wallet rides along in [selectedWalletId].
        val walletRows = transactionsViewModel.rowsForWallet(selectedWalletId)

        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    AssetsFilterScreen(
                        wallets = _wallets.value,
                        selectedWallet = _wallets.value.getOrNull(_selectedIndex.value),
                        onWalletSelected = { walletId ->
                            selectWallet(_wallets.value.first { it.walletId == walletId })
                        },
                        values = _values.value,
                        walletTransactions = _walletTransactions.value,
                        onRowClick = { id ->
                            findNavController().navigate(
                                R.id.transactionDetailFragment,
                                Bundle().apply { putInt("transactionID", id) },
                            )
                        },
                    )
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    walletsViewModel.rows.collect { rows ->
                        if (!isAdded) return@collect
                        _wallets.value = rows
                        if (rows.isEmpty()) return@collect
                        val intentWalletId = arguments?.getInt("walletID", -1) ?: -1
                        val selectedIndex = if (intentWalletId != -1) {
                            rows.indexOfFirst { it.walletId == intentWalletId }
                        } else {
                            0
                        }
                        _selectedIndex.value = if (selectedIndex >= 0) selectedIndex else 0
                        selectWallet(rows[_selectedIndex.value])
                    }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    walletRows.collect { _walletTransactions.value = it }
                }
            }
        }
    }

    private fun selectWallet(wallet: WalletRow) {
        selectedWalletId.value = wallet.walletId
        _values.value = CoreService.onCoreThread { CoreService.getAssetMap(wallet.walletId) }
        FileLog.d(TAG, "showing aggregates for wallet ${wallet.walletId} (${wallet.name})")
    }

    companion object {
        private const val TAG = "AssetsFilter"
    }
}
