package at.msd.friehs_bicha.cdcsvparser.ui.activity

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper

/**
 * Activity for the transaction page that shows the details of a transaction
 */
class TransactionActivity : AppCompatActivity() {

    lateinit var appModel: AppModel

    lateinit var transaction: Transaction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)

        // calling the action bar
        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)

        appModel = AppModelManager.getInstance()

        transaction = intent.getSerializableExtra("transaction") as Transaction

        val tvType = findViewById<TextView>(R.id.tv_transaction_type)
        val tvDate = findViewById<TextView>(R.id.tv_date)
        val tvDescription = findViewById<TextView>(R.id.tv_description)
        val tvAmountValue = findViewById<TextView>(R.id.tv_amountValue)
        val tvToAmount = findViewById<TextView>(R.id.tv_toAmount)
        val tvToAmountValue = findViewById<TextView>(R.id.tv_toAmountValue)
        val tvTxHash = findViewById<TextView>(R.id.tv_txHash)
        val tvTxHashValue = findViewById<TextView>(R.id.tv_txHashValue)

        tvType.text = if (transaction is CroCardTransaction) (transaction as CroCardTransaction).transactionTypeString else transaction.transactionType.toString()
        tvDate.text = transaction.date.toString()
        tvDescription.text = transaction.description
        tvAmountValue.text = StringHelper.formatAmountToString(transaction.amount, 6, transaction.currencyType)
        if (transaction.toAmount != null && transaction.toCurrency != null)
        {
            tvToAmountValue.text = StringHelper.formatAmountToString(transaction.toAmount!!, 6, transaction.toCurrency!!)
        }
        else
        {
            tvToAmount.visibility = TextView.GONE
        }
        if (transaction.transHash != null)
            tvTxHashValue.text = transaction.transHash
        else
            tvTxHash.visibility = TextView.GONE

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