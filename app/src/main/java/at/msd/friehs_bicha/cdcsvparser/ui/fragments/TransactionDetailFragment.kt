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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.TransactionDetailScreen
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionDetailState
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionDetailViewModel
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import java.math.BigDecimal
import kotlinx.coroutines.launch

/**
 * Transaction detail screen (Compose surface; argument: transactionID).
 */
class TransactionDetailFragment : Fragment() {

    private var transactionId = -1
    private lateinit var viewModel: TransactionDetailViewModel
    private val _state = mutableStateOf<TransactionDetailState?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    when (val state = _state.value) {
                        null, is TransactionDetailState.Loading -> {}
                        is TransactionDetailState.Missing -> {}
                        is TransactionDetailState.Found -> {
                            val tx = state.transaction
                            TransactionDetailScreen(
                                type = tx.getTxTypeString()?.toString().orEmpty(),
                                date = tx.date.toString(),
                                description = tx.description,
                                amount = StringHelper.formatAmountToString(
                                    tx.amount, 6, tx.currencyType
                                ),
                                toAmount = if (tx.toAmount != null && tx.toCurrency != null
                                    && tx.toAmount != BigDecimal.ZERO && tx.toCurrency != ""
                                ) {
                                    StringHelper.formatAmountToString(
                                        tx.toAmount!!, 6, tx.toCurrency!!
                                    )
                                } else {
                                    null
                                },
                                txHash = tx.transHash,
                            )
                        }
                    }
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

                override fun onMenuItemSelected(menuItem: MenuItem) = false
            },
            viewLifecycleOwner,
        )
        transactionId = arguments?.getInt("transactionID", -1) ?: -1
        if (transactionId == -1) {
            popBack()
            return
        }

        viewModel = ViewModelProvider(this, TransactionDetailViewModel.factory(transactionId))
            .get(TransactionDetailViewModel::class.java)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    _state.value = state
                    if (state is TransactionDetailState.Missing) {
                        FileLog.e(TAG, "No transaction found for id $transactionId")
                        popBack()
                    }
                }
            }
        }
    }

    private fun popBack() {
        if (!isAdded) return
        findNavController().popBackStack()
    }

    companion object {
        private const val TAG = "TransactionDetail"
    }
}
