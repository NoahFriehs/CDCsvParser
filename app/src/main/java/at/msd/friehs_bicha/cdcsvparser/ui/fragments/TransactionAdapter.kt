package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.content.Context
import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import at.msd.friehs_bicha.cdcsvparser.AssetsFilterActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager

import at.msd.friehs_bicha.cdcsvparser.placeholder.PlaceholderContent.PlaceholderItem
import at.msd.friehs_bicha.cdcsvparser.databinding.FragmentTransactionBinding
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction

/**
 * [RecyclerView.Adapter] that can display a [PlaceholderItem].
 * TODO: Replace the implementation with code for your data type.
 */
class TransactionAdapter(private val transactions: List<Transaction>) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                val transactionId =
                    itemView.findViewById<TextView>(R.id.tv_transactionId).text.toString().toInt()
                val appModel = AppModelManager.getInstance()
                val transaction = appModel.txApp!!.transactions.find{ it.transactionId == transactionId }
                val intent = Intent(itemView.context, AssetsFilterActivity::class.java)
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
        val transaction = transactions[position]
        val waMap = AppModelManager.getInstance().getTransactionAdapter(transaction)
        displayTexts(waMap, holder, holder.itemView.context)
    }

    override fun getItemCount(): Int = transactions.size

    private fun displayTexts(
        texts: Map<String, String?>?,
        holder: TransactionAdapter.TransactionViewHolder,
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