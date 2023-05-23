package at.msd.friehs_bicha.cdcsvparser.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.WalletListFragment
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet


class WalletViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet_view)

        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)

        val appModel = AppModelManager.getInstance()
        val wallets = appModel.txApp!!.wallets

        val spinnerValueSpinner = findViewById<Spinner>(R.id.sorting_value)
        val sortingValues = listOf<String>("amount €", "amount Asset","percent","transactions")
        val sortingValuesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,sortingValues)
        spinnerValueSpinner.adapter = sortingValuesAdapter

        val spinnerTypeSpinner = findViewById<Spinner>(R.id.sorting_type)
        val sortingTypes = listOf<String>("DESC", "ASC")
        val sortingTypesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,sortingTypes)
        spinnerTypeSpinner.adapter = sortingTypesAdapter

        var sortedWallets = wallets

        spinnerValueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val typePosition = spinnerTypeSpinner.selectedItemPosition
                sortedWallets = sortWallets(sortedWallets, sortingValues[position], sortingTypes[typePosition])
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, WalletListFragment(sortedWallets))
                    .commit()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                return
            }
        }

        spinnerTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val valuePosition = spinnerValueSpinner.selectedItemPosition
                sortedWallets = sortWallets(sortedWallets, sortingValues[valuePosition], sortingTypes[position])
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, WalletListFragment(sortedWallets))
                    .commit()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                return
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, WalletListFragment(sortWallets(wallets, "amount €", "DESC")))
            .commit()
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }


    fun sortWallets(wallets: ArrayList<Wallet>, sortingValue: String, sortingType: String): ArrayList<Wallet>
    {
        var sortedWallets = wallets
        val isDesc = sortingType == "DESC"
        when (sortingValue) {
            "amount €" -> {
                sortedWallets = sortedWallets.sortedByDescending {
                    AppModelManager.getInstance().getValueOfAssets(it)
                }.toList() as ArrayList<Wallet>
            }

            "amount Asset" -> {
                sortedWallets =
                    sortedWallets.sortedByDescending { it.amount }.toList() as ArrayList<Wallet>
            }

            "percent" -> {
                sortedWallets = sortedWallets.sortedWith(compareByDescending {
                    val assetValue = AppModelManager.getInstance().getValueOfAssets(it)
                    val percentProfit = assetValue / it.moneySpent.toDouble() * 100
                    percentProfit
                }).toList() as ArrayList<Wallet>
            }

            "transactions" -> {
                sortedWallets =
                    sortedWallets.sortedWith(compareByDescending { it.transactions?.size ?: 0 })
                        .toList() as ArrayList<Wallet>
            }
        }

        if (!isDesc)
            sortedWallets.reverse()
        return sortedWallets
    }
}