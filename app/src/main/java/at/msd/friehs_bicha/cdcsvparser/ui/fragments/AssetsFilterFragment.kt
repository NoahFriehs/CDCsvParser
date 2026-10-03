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
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

/**
 * Asset filter screen: per-wallet aggregate values + the wallet's
 * transactions (argument: walletID, optional).
 */
class AssetsFilterFragment : Fragment() {

    private var walletList: MutableList<Wallet> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_assets_filter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dropdown = view.findViewById<Spinner>(R.id.asset_spinner)
        val items = CoreService.walletNames.value ?: arrayOf()
        walletList = CoreService.allWalletsLiveData.value ?: mutableListOf()

        if (items.isEmpty()) {
            FileLog.e(TAG, "items is empty")
            return
        }

        dropdown.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, items)

        val indexObj = dropdown.selectedItem ?: return
        var specificWallet = walletList.find { it.getTypeString() == indexObj }

        val intentWalletId = arguments?.getInt("walletID", -1) ?: -1
        if (intentWalletId != -1) {
            val wallet = walletList.find { it.walletId == intentWalletId }
            if (wallet != null) {
                specificWallet = wallet
            } else {
                FileLog.e(TAG, "Wallet not found")
            }
            walletList.indexOf(wallet).let { if (it >= 0) dropdown.setSelection(it) }
        }

        displayInformation(view, specificWallet, view.findViewById(R.id.all_regarding_tx))

        if (specificWallet != null) showTransactions(specificWallet)

        dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View,
                position: Int,
                id: Long
            ) {
                val specificWallet = walletList[position]
                showTransactions(specificWallet)
                displayInformation(view, specificWallet, view.findViewById(R.id.all_regarding_tx))
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
    }

    private fun showTransactions(specificWallet: Wallet) {
        if (!childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    TransactionFragment(specificWallet.transactions as ArrayList<Transaction>)
                )
                .commit()
        }
    }

    private fun displayInformation(view: View, specificWallet: Wallet?, allRegardingTx: TextView) {
        if (specificWallet == null) {
            FileLog.e(TAG, "specificWallet is null")
            return
        }

        allRegardingTx.text =
            getString(R.string.all_transactions_regarding, specificWallet.getTypeString())

        val texts = CoreService.onCoreThread {
            CoreService.getAssetMap(specificWallet.walletId)
        }
        displayTexts(view, texts)
    }

    private fun displayTexts(view: View, texts: Map<String, String?>) {
        texts.forEach { (key, value) ->
            val textView = view.findViewById<TextView>(
                resources.getIdentifier(key, "id", requireContext().packageName)
            )
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
