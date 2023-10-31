package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.ui.activity.TransactionActivity

/**
 * [TransactionAdapter] that can display a [List<Transaction>].
 * The [TransactionAdapter] is used in [TransactionFragment].
 */
class TransactionAdapter(private val transactions: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                val transactionId =
                    itemView.findViewById<TextView>(R.id.tv_transactionId).text.toString().toInt()
                val appModel = AppModelManager.getInstance()!!  //TODO: check if null
                val transaction =
                    appModel.txApp!!.transactions.find { it.transactionId == transactionId }
                val intent = Intent(itemView.context, TransactionActivity::class.java)
                intent.putExtra("transaction", transaction)
                itemView.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {

        return TransactionViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_transaction,
                parent,
                false
            )
        )

    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
//        Thread {
        val transaction = transactions[position]
        val waMap = AppModelManager.getInstance()!!
            .getTransactionAdapter(transaction)  //TODO: check if null
        displayTexts(waMap, holder, holder.itemView.context)
//        }.start()
    }

    override fun getItemCount(): Int = transactions.size

    private fun displayTexts(
        texts: Map<String, String?>?,
        holder: TransactionViewHolder,
        context: Context
    ) {
        texts!!.forEach { (key: String?, value: String?) ->
            val textView = holder.itemView.findViewById<TextView>(
                holder.itemView.resources.getIdentifier(
                    key,
                    "id",
                    context.packageName
                )
            )
            if (textView == null) {
                FileLog.e("TransactionAdapter", "textView is null for key: $key")
                return@forEach
            }
            if (value == null) {
                textView.visibility = View.INVISIBLE
            } else {
                textView.text = value
            }
        }
    }

}