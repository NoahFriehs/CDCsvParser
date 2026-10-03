package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow

/**
 * [RecyclerView.Adapter] that displays pre-formatted [TransactionRow]s.
 * Rows are supplied via [submit]; item clicks navigate to the detail screen.
 */
class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var rows: List<TransactionRow> = emptyList()

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val date: TextView = itemView.findViewById(R.id.tv_date)
        val description: TextView = itemView.findViewById(R.id.tv_descriptionValue)
        val nativeAmount: TextView = itemView.findViewById(R.id.tv_amountValue)
        val assetAmount: TextView = itemView.findViewById(R.id.tv_assetAmountValue)

        init {
            itemView.setOnClickListener {
                (itemView.tag as? Int)?.let { id ->
                    (itemView.context as? FragmentActivity)?.let {
                        it.findNavController(R.id.nav_host_fragment).navigate(
                            R.id.transactionDetailFragment,
                            Bundle().apply { putInt("transactionID", id) }
                        )
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_transaction, parent, false)
        return TransactionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val row = rows[position]
        holder.itemView.tag = row.id
        holder.date.text = row.date
        holder.description.text = row.description
        holder.nativeAmount.text = row.nativeAmount
        holder.assetAmount.text = row.assetAmount
    }

    override fun getItemCount(): Int = rows.size

    fun submit(newRows: List<TransactionRow>) {
        rows = newRows
        notifyDataSetChanged()
    }
}
