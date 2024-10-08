package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.TransactionFragment

class TransactionsActivity : AppCompatActivity() {

    /**
     * Create list of all Transactions on create this view
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)
        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)

        val mTransactionList: ArrayList<Transaction> =
            CoreService.transactionsLiveData.value?.let { ArrayList(CoreService.transactionsLiveData.value!!) }
                ?: ArrayList()
        CoreService.cardTransactionsLiveData.value?.let { mTransactionList.addAll(CoreService.cardTransactionsLiveData.value!!) }
        val mTransactionSet = mTransactionList.toSet()
        mTransactionList.clear()
        mTransactionList.addAll(mTransactionSet)
        mTransactionList.sortByDescending { it.date }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, TransactionFragment(mTransactionList))
            .commit()
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