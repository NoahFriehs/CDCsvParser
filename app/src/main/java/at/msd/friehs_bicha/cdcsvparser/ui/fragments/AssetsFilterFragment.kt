package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletRow
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionsViewModel
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.WalletsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Asset filter screen: per-wallet aggregate values + the wallet's
 * transactions (argument: walletID, optional).
 */
class AssetsFilterFragment : Fragment() {

    private val walletsViewModel: WalletsViewModel by viewModels()
    private val transactionsViewModel: TransactionsViewModel by viewModels()

    private val selectedWalletId = MutableStateFlow(-1)
    private var wallets: List<WalletRow> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_assets_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    TransactionFragment(transactionsViewModel.rowsForWallet(selectedWalletId))
                )
                .commit()
        }

        val dropdown = view.findViewById<Spinner>(R.id.asset_spinner)
        val allRegardingTx = view.findViewById<TextView>(R.id.all_regarding_tx)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                walletsViewModel.rows.collect { rows ->
                    if (!isAdded) return@collect
                    wallets = rows
                    if (rows.isEmpty()) return@collect
                    dropdown.adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        rows.map { it.name }.toTypedArray()
                    )
                    val intentWalletId = arguments?.getInt("walletID", -1) ?: -1
                    val selectedIndex = if (intentWalletId != -1) {
                        rows.indexOfFirst { it.walletId == intentWalletId }
                    } else {
                        0
                    }
                    val index = if (selectedIndex >= 0) selectedIndex else 0
                    dropdown.setSelection(index)
                    selectWallet(view, rows[index], allRegardingTx)
                }
            }
        }

        dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View,
                position: Int,
                id: Long
            ) {
                if (position in wallets.indices) {
                    selectWallet(view, wallets[position], allRegardingTx)
                }
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
    }

    private fun selectWallet(view: View, wallet: WalletRow, allRegardingTx: TextView) {
        selectedWalletId.value = wallet.walletId
        allRegardingTx.text = getString(R.string.all_transactions_regarding, wallet.name)
        val texts = CoreService.onCoreThread { CoreService.getAssetMap(wallet.walletId) }
        displayTexts(view, texts)
    }

    private fun displayTexts(view: View, texts: Map<String, String?>) {
        texts.forEach { (key, value) ->
            val textView = view.findViewById<TextView>(
                resources.getIdentifier(key, "id", requireContext().packageName)
            )
            if (textView == null) {
                FileLog.e(TAG, "textView is null for key: $key")
                return@forEach
            }
            if (value == null) {
                textView.visibility = View.INVISIBLE
            } else {
                textView.text = value
            }
        }
    }

    companion object {
        private const val TAG = "AssetsFilter"
    }
}
