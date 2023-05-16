package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import at.msd.friehs_bicha.cdcsvparser.R

import at.msd.friehs_bicha.cdcsvparser.ui.fragments.placeholder.PlaceholderContent.PlaceholderItem
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

/**
 * [RecyclerView.Adapter] that can display a [PlaceholderItem].
 * TODO: Replace the implementation with code for your data type.
 */
class WalletAdapter(val wallets: List<Wallet>) : RecyclerView.Adapter<WalletAdapter.WalletViewHolder>() {

    class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WalletViewHolder {
        return WalletViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.fragment_wallet,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: WalletViewHolder, position: Int) {
        val wallet = wallets[position]
        holder.itemView.findViewById<TextView>(R.id.walletId).text = wallet.walletId.toString()
        holder.itemView.findViewById<TextView>(R.id.currencyType).text = wallet.currencyType.toString()
        holder.itemView.findViewById<TextView>(R.id.amount).text = wallet.amount.toString()
    }

    override fun getItemCount() = wallets.size
}