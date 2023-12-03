package at.msd.friehs_bicha.cdcsvparser

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.Core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.TransactionFragment
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

class AssetsFilterActivity : AppCompatActivity() {
    var context: Context? = null
    private var walletList: MutableList<Wallet> = mutableListOf()
    private var walletNamesArray: Array<String?> = arrayOf()

    /**
     * sets the spinner and context the first time
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assets_filter)
        // calling the action bar
        val actionBar = supportActionBar
        context = applicationContext

        // showing the back button in action bar
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        val dropdown = findViewById<Spinner>(R.id.asset_spinner)
        // make List with all Wallets
        val items = CoreService.walletNames.value ?: arrayOf()
        walletList = CoreService.walletsLiveData.value ?: mutableListOf()

        if (items.isEmpty()) {
            FileLog.e("AssetsFilterActivity", "items is empty")
            return
        }

        walletNamesArray = items.copyOf()
        //create an adapter to describe how the items are displayed
        val assetNamesAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        //set the spinners adapter to the previously created one.
        dropdown.adapter = assetNamesAdapter

        //get the specific wallet
        val indexObj = dropdown.selectedItem ?: return
        var specificWallet = walletList.find { it.getTypeString() == indexObj }

        val intentWalletId = intent.extras?.getInt("walletID", -1)
        if (intentWalletId != null && intentWalletId != -1) {
            val wallet = walletList.find { it.walletId == intentWalletId }
            if (wallet != null) {
                specificWallet = wallet
            } else {
                FileLog.e("AssetsFilterActivity", "Wallet not found")
            }
            val index = walletList.indexOf(wallet)
            dropdown.setSelection(index)
        }


        // display prices
        displayInformation(specificWallet, findViewById(R.id.all_regarding_tx))

        //displays transactions
        if (specificWallet != null) supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                TransactionFragment(specificWallet.transactions as ArrayList<Transaction>)
            )
            .commit()

        //if spinner item gets changed
        dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View,
                position: Int,
                id: Long
            ) {
                //get the specific wallet
                val specificWallet =
                    walletList[position]

                //display Transactions
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragment_container,
                        TransactionFragment(specificWallet.transactions as ArrayList<Transaction>)
                    )
                    .commit()
                //display prices
                displayInformation(specificWallet, findViewById(R.id.all_regarding_tx))
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
    }


    /**
     * Displays the prices of specificWallet
     *
     * @param specificWallet   which should be displayed
     * @param all_regarding_tx the TextView which should be set
     */
    private fun displayInformation(specificWallet: Wallet?, all_regarding_tx: TextView) {

        if (specificWallet == null) {
            FileLog.e("AssetsFilterActivity", "specificWallet is null")
            return
        }

        all_regarding_tx.text =
            resources.getString(R.string.all_transactions_regarding) + specificWallet.getTypeString()


        //get and set prices
        val t = Thread { displayTexts(CoreService.getAssetMap(specificWallet.walletId)) }
        t.start()
    }

    /**
     * Displays the prices of specificWallet
     *
     * @param texts the Map<String></String>, String> which should be displayed with id of View and text to set pairs
     */
    private fun displayTexts(texts: Map<String, String?>) {
        texts.forEach { (key: String?, value: String?) ->
            val textView = findViewById<TextView>(resources.getIdentifier(key, "id", packageName))
            if (value == null) {
                runOnUiThread { textView.visibility = View.INVISIBLE }
            } else {
                runOnUiThread { textView.text = value }
            }
        }
    }

    /**
     * Set the back button in action bar
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}