package at.msd.friehs_bicha.cdcsvparser

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import java.util.function.Consumer

class AssetsFilterActivity : AppCompatActivity() {
    var appModel: AppModel? = null
    var context: Context? = null

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
        //create a list of items for the spinner.
        getAppModel()

        //test Internet connection
        //testConnection();
        while (!appModel!!.isRunning) { //TODO: add loading icon
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }

        // make List with all Wallets
        val items = walletNames
        //create an adapter to describe how the items are displayed
        val assetNamesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        //set the spinners adapter to the previously created one.
        dropdown.adapter = assetNamesAdapter

        //get the specific wallet
        val indexObj = dropdown.selectedItem ?: return
        var specificWallet = appModel!!.txApp!!.wallets[appModel!!.txApp!!.wallets[0].getWallet(indexObj.toString())]

        val wallet = intent.extras?.get("wallet")
        if (wallet is Wallet)
        {
            specificWallet = wallet
            if (wallet is CroCardWallet) dropdown.setSelection(items.indexOf(wallet.transactionType))
            else dropdown.setSelection(items.indexOf(specificWallet.currencyType))
        }

        // display prices
        displayInformation(specificWallet, findViewById(R.id.all_regarding_tx))
        displayTxs(specificWallet)

        //if spinner item gets changed
        dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                //get the specific wallet
                val specificWallet = appModel!!.txApp!!.wallets[appModel!!.txApp!!.wallets[0].getWallet(dropdown.selectedItem.toString())]

                //display Transactions
                displayTxs(specificWallet)
                //display prices
                displayInformation(specificWallet, findViewById(R.id.all_regarding_tx))
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
    }

    private fun getAppModel() {
        appModel = AppModelManager.getInstance()    //intent.extras!!["AppModel"] as AppModel?
    }

    /**
     * Gets the wallet names for the selected appType
     *
     * @return the wallet names as String[]
     */
    private val walletNames: Array<String?>
        get() {
            val wallets = ArrayList<String?>()
            when (appModel!!.appType) {
                AppType.CdCsvParser -> {
                    appModel!!.txApp!!.wallets.forEach(Consumer { wallet: Wallet? -> wallets.add(wallet?.currencyType) })
                    wallets.remove("EUR")
                }
                AppType.CroCard -> appModel!!.txApp!!.wallets.forEach(Consumer { wallet: Wallet? -> wallets.add((wallet as CroCardWallet?)?.transactionType) })
                else -> wallets.add("This should not happen")
            }
            return wallets.toTypedArray()
        }


    /**
     * Displays the prices of specificWallet
     *
     * @param specificWallet   which should be displayed
     * @param all_regarding_tx the TextView which should be set
     */
    private fun displayInformation(specificWallet: Wallet?, all_regarding_tx: TextView) {
        all_regarding_tx.text = "All transactions regarding " + specificWallet?.currencyType
        if (appModel!!.appType == AppType.CroCard) {
            all_regarding_tx.text = "All transactions regarding " + (specificWallet as CroCardWallet?)?.transactionType
        }

        //get and set prices
        val t = Thread { displayTexts(appModel!!.getAssetMap(specificWallet)) }
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
     * Displays the Transactions of specificWallet
     *
     * @param specificWallet the CDCWallet which should be displayed
     */
    private fun displayTxs(specificWallet: Wallet?) {
        // Get a reference to the ListView
        val listView = findViewById<ListView>(R.id.lv_txs)
        val transactions: List<Transaction?>? = specificWallet!!.transactions

        val transactionsStringList: MutableList<String?> = ArrayList()
        for (tx in transactions!!) {
            transactionsStringList.add(tx.toString())
        }

        // Create an adapter for the ListView
        val adapterLV = ArrayAdapter(context!!, android.R.layout.simple_list_item_1, transactionsStringList)

        // Set the adapter on the ListView
        listView.adapter = adapterLV
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