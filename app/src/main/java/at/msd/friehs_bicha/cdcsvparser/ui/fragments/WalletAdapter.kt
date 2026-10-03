package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.ProfitTrend
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletRow

/**
 * [RecyclerView.Adapter] that displays pre-formatted [WalletRow]s.
 * Rows are supplied via [submit]; item clicks navigate to the asset filter.
 */
class WalletAdapter : RecyclerView.Adapter<WalletAdapter.WalletViewHolder>() {

    private var rows: List<WalletRow> = emptyList()

    class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.currencyType)
        val amount: TextView = itemView.findViewById(R.id.amount)
        val assetValue: TextView = itemView.findViewById(R.id.amountValue)
        val percentProfit: TextView = itemView.findViewById(R.id.percentProfit)
        val transactionCount: TextView = itemView.findViewById(R.id.amountTransactions)
        val walletIdView: TextView = itemView.findViewById(R.id.walletId)

        init {
            itemView.setOnClickListener {
                (itemView.tag as? Int)?.let { walletId ->
                    (itemView.context as? FragmentActivity)?.let {
                        it.findNavController(R.id.nav_host_fragment).navigate(
                            R.id.assetsFilterFragment,
                            Bundle().apply { putInt("walletID", walletId) }
                        )
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_wallet, parent, false)
        return WalletViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        val row = rows[position]
        holder.itemView.tag = row.walletId
        holder.name.text = row.name
        holder.amount.text = row.amountText
        holder.assetValue.text = row.assetValueText
        holder.percentProfit.text = row.percentText
        holder.transactionCount.text = row.transactionCount.toString()
        holder.percentProfit.setTextColor(
            when (row.trend) {
                ProfitTrend.POSITIVE -> Color.GREEN
                ProfitTrend.NEUTRAL -> Color.GRAY
                ProfitTrend.NEGATIVE -> Color.RED
            }
        )
    }

    override fun getItemCount(): Int = rows.size

    fun submit(newRows: List<WalletRow>) {
        rows = newRows
        notifyDataSetChanged()
    }
}
