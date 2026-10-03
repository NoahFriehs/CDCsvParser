package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.Fragment
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

/**
 * Wallet view screen: searchable, sortable list of all wallets (hosting the
 * existing WalletListFragment in the container).
 */
class WalletViewFragment : Fragment() {

    private var wallets = ArrayList<Wallet>()
    private var sortedWallets = ArrayList<Wallet>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_wallet_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wallets = CoreService.allWalletsLiveData.value ?: ArrayList<Wallet>()
        if (wallets.isEmpty()) {
            wallets = CoreService.walletsLiveData.value ?: ArrayList()
        }
        sortedWallets = ArrayList(wallets)

        val spinnerValueSpinner = view.findViewById<Spinner>(R.id.sorting_value)
        val sortingValues = listOf(
            getString(R.string.sort_amount),
            getString(R.string.sort_amount_asset),
            getString(R.string.sort_percent),
            getString(R.string.sort_transactions)
        )
        spinnerValueSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortingValues)

        val spinnerTypeSpinner = view.findViewById<Spinner>(R.id.sorting_type)
        val sortingTypes = listOf(
            getString(R.string.sort_desc),
            getString(R.string.sort_asc)
        )
        spinnerTypeSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortingTypes)

        spinnerValueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val typePosition = spinnerTypeSpinner.selectedItemPosition
                sortedWallets =
                    sortWallets(sortedWallets, sortingValues[position], sortingTypes[typePosition])
                showList()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        spinnerTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val valuePosition = spinnerValueSpinner.selectedItemPosition
                sortedWallets =
                    sortWallets(sortedWallets, sortingValues[valuePosition], sortingTypes[position])
                showList()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

        val editText = view.findViewById<EditText>(R.id.search_bar)
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                val text = editText.text.toString()
                sortedWallets = if (text == "") {
                    sortWallets(
                        wallets,
                        spinnerValueSpinner.selectedItem.toString(),
                        spinnerTypeSpinner.selectedItem.toString()
                    )
                } else {
                    sortedWallets.clear()
                    sortedWallets.addAll(filterWalletsByUserSearch(wallets, text))
                    sortWallets(
                        sortedWallets,
                        spinnerValueSpinner.selectedItem.toString(),
                        spinnerTypeSpinner.selectedItem.toString()
                    )
                }
                showList()
            }
        })

        sortedWallets = sortWallets(
            wallets,
            getString(R.string.sort_amount),
            getString(R.string.sort_desc)
        )
        showList()
    }

    private fun showList() {
        if (isAdded && !childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WalletListFragment(sortedWallets))
                .commit()
        }
    }

    private fun sortWallets(
        wallets: ArrayList<Wallet>,
        sortingValue: String,
        sortingType: String
    ): ArrayList<Wallet> {
        if (wallets.size <= 1) {
            return wallets
        }
        var sorted = wallets
        val isDesc = sortingType == getString(R.string.sort_desc)
        when (sortingValue) {
            getString(R.string.sort_amount) -> {
                sorted = sorted.sortedByDescending {
                    CoreService.onCoreThread { CoreService.getValueOfAssetsFromWID(it.walletId) }
                }.toList() as ArrayList<Wallet>
            }

            getString(R.string.sort_amount_asset) -> {
                sorted = sorted.sortedByDescending { it.amount }.toList() as ArrayList<Wallet>
            }

            getString(R.string.sort_percent) -> {
                sorted = sorted.sortedWith(compareByDescending {
                    val assetValue =
                        CoreService.onCoreThread { CoreService.getValueOfAssetsFromWID(it.walletId) }
                    assetValue / it.moneySpent.toDouble() * 100
                }).toList() as ArrayList<Wallet>
            }

            getString(R.string.sort_transactions) -> {
                sorted = sorted.sortedWith(compareByDescending { it.transactions.size })
                    .toList() as ArrayList<Wallet>
            }
        }
        if (!isDesc) sorted.reverse()
        return sorted
    }

    private fun filterWalletsByUserSearch(wallets: ArrayList<Wallet>, query: String): List<Wallet> {
        if (wallets.isEmpty()) return emptyList()
        if (wallets[0] is CroCardWallet) {
            return wallets.filter { (it as CroCardWallet).transactionType!!.contains(query, ignoreCase = true) }
        }
        return wallets.filter { it.currencyType.contains(query, ignoreCase = true) }
    }
}
