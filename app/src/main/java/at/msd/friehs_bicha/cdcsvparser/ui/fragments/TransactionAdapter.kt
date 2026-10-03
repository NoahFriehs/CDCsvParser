package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionIcon
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow

/**
 * [RecyclerView.Adapter] that displays pre-formatted [TransactionRow]s under
 * month headers. Rows are supplied via [submit]; item clicks navigate to the
 * detail screen.
 */
class TransactionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class MonthHeader(val month: String) : Item()
        data class RowItem(val row: TransactionRow) : Item()
    }

    private var items: List<Item> = emptyList()

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val month: TextView = itemView.findViewById(R.id.tv_month)
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.iv_icon)
        val description: TextView = itemView.findViewById(R.id.tv_descriptionValue)
        val date: TextView = itemView.findViewById(R.id.tv_date)
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

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is Item.MonthHeader -> TYPE_HEADER
            is Item.RowItem -> TYPE_ROW
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_month_header, parent, false))
        } else {
            TransactionViewHolder(inflater.inflate(R.layout.fragment_transaction, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.MonthHeader -> {
                (holder as HeaderViewHolder).month.text = item.month
                holder.itemView.isClickable = false
            }

            is Item.RowItem -> {
                val holder = holder as TransactionViewHolder
                val row = item.row
                holder.itemView.tag = row.id
                holder.description.text = row.description.ifEmpty { fallbackDescription(row) }
                holder.date.text = row.date
                holder.nativeAmount.text = row.nativeAmount
                holder.nativeAmount.setTextColor(nativeAmountColor(holder.itemView, row.nativeSigned))
                holder.assetAmount.text = row.assetAmount
                bindIcon(holder.itemView, holder.icon, row.icon)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    /** Groups the flat (date-descending) rows under their month headers. */
    fun submit(rows: List<TransactionRow>) {
        val grouped = ArrayList<Item>(rows.size)
        var lastMonth: String? = null
        for (row in rows) {
            if (row.monthKey != lastMonth) {
                grouped.add(Item.MonthHeader(row.monthKey))
                lastMonth = row.monthKey
            }
            grouped.add(Item.RowItem(row))
        }
        items = grouped
        notifyDataSetChanged()
    }

    private fun fallbackDescription(row: TransactionRow): String =
        when (row.icon) {
            TransactionIcon.CREDIT -> "Credit"
            TransactionIcon.DEBIT -> "Debit"
            TransactionIcon.PURCHASE -> "Purchase"
            TransactionIcon.OTHER -> "Transaction"
        }

    private fun nativeAmountColor(view: View, signed: Double): Int {
        return when {
            signed > 0 -> ContextCompat.getColor(view.context, R.color.trend_positive)
            signed < 0 -> ContextCompat.getColor(view.context, R.color.trend_negative)
            else -> ContextCompat.getColor(view.context, R.color.on_surface)
        }
    }

    private fun bindIcon(view: View, icon: ImageView, type: TransactionIcon) {
        val (drawable, tint) = when (type) {
            TransactionIcon.CREDIT ->
                R.drawable.ic_savings_24 to R.color.primary
            TransactionIcon.DEBIT ->
                R.drawable.ic_upload_24 to R.color.error
            TransactionIcon.PURCHASE ->
                R.drawable.ic_spending_24 to R.color.secondary
            TransactionIcon.OTHER ->
                R.drawable.ic_trending_24 to R.color.tertiary
        }
        icon.setImageResource(drawable)
        icon.setColorFilter(ContextCompat.getColor(view.context, tint))
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }
}
