package at.msd.friehs_bicha.cdcsvparser.ui.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.WalletListFragment
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet


/**
 * Activity for the wallet view page (wallet list)
 */
class WalletViewActivity : AppCompatActivity() {

    private var isCacheloaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_view)

        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)

        var wallets = CoreService.allWalletsLiveData.value ?: ArrayList<Wallet>()

        if (wallets.isEmpty()) {
            CoreService.walletsLiveData.value?.let {
                wallets = it
            }
            isCacheloaded = true
        }

        val spinnerValueSpinner = findViewById<Spinner>(R.id.sorting_value)
        val sortingValues = listOf<String>(resources.getString(R.string.sort_amount), resources.getString(R.string.sort_amount_asset), resources.getString(R.string.sort_percent), resources.getString(R.string.sort_transactions))
        val sortingValuesAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortingValues)
        spinnerValueSpinner.adapter = sortingValuesAdapter

        val spinnerTypeSpinner = findViewById<Spinner>(R.id.sorting_type)
        val sortingTypes = listOf<String>(resources.getString(R.string.sort_desc), resources.getString(R.string.sort_asc))
        val sortingTypesAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortingTypes)
        spinnerTypeSpinner.adapter = sortingTypesAdapter

        var sortedWallets = wallets

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
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, WalletListFragment(sortedWallets))
                    .commit()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                return
            }
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
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, WalletListFragment(sortedWallets))
                    .commit()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                return
            }
        }

        val editText = findViewById<EditText>(R.id.search_bar)


        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // This method is invoked before the text is changed.
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                // This method is invoked while the text is being changed.
            }

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

                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, WalletListFragment(sortedWallets))
                    .commit()
            }
        })

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                WalletListFragment(sortWallets(wallets, resources.getString(R.string.sort_amount), resources.getString(R.string.sort_desc)))
            )
            .commit()
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }


    fun sortWallets(
        wallets: ArrayList<Wallet>,
        sortingValue: String,
        sortingType: String
    ): ArrayList<Wallet> {

        if (wallets.size <= 1) {
            return wallets
        }
        var sortedWallets = wallets
        val isDesc = sortingType == resources.getString(R.string.sort_desc)
        when (sortingValue) {
            resources.getString(R.string.sort_amount) -> {
                sortedWallets = sortedWallets.sortedByDescending {
                    CoreService.getValueOfAssetsFromWID(it.walletId)
                }.toList() as ArrayList<Wallet>
            }

            resources.getString(R.string.sort_amount_asset) -> {
                sortedWallets =
                    sortedWallets.sortedByDescending { it.amount }.toList() as ArrayList<Wallet>
            }

            resources.getString(R.string.sort_percent) -> {
                sortedWallets = sortedWallets.sortedWith(compareByDescending {
                    val assetValue = CoreService.getValueOfAssetsFromWID(it.walletId)
                    val percentProfit = assetValue / it.moneySpent.toDouble() * 100
                    percentProfit
                }).toList() as ArrayList<Wallet>
            }

            resources.getString(R.string.sort_transactions) -> {
                sortedWallets =
                    sortedWallets.sortedWith(compareByDescending { it.transactions.size })
                        .toList() as ArrayList<Wallet>
            }
        }

        if (!isDesc)
            sortedWallets.reverse()
        return sortedWallets
    }

    fun filterWalletsByUserSearch(wallets: ArrayList<Wallet>, query: String): List<Wallet> {
        if (wallets[0] is CroCardWallet) {
            return wallets.filter { (it as CroCardWallet).transactionType!!.contains(query, ignoreCase = true) }
        }
        return wallets.filter { it.currencyType.contains(query, ignoreCase = true) }
    }
}