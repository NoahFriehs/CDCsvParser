package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction

class TransactionsActivity : AppCompatActivity() {
    var appModel: AppModel? = null

    /**
     * Create list of all Transactions on creat this view
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)
        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)
        getAppModel()
        while (!appModel!!.isRunning) {
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        val mTransactionList: List<Transaction?> = appModel!!.txApp!!.transactions

        val transactionsStringList: MutableList<String?> = ArrayList()
        for (tx in mTransactionList) {
            transactionsStringList.add(tx.toString())
        }

        // Get a reference to the ListView
        val listView = findViewById<ListView>(R.id.lv_tx)
        // Create an adapter for the ListView
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, transactionsStringList)
        // Set the adapter on the ListView
        listView.adapter = adapter
    }

    private fun getAppModel() {
        appModel = intent.extras!!["AppModel"] as AppModel?
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