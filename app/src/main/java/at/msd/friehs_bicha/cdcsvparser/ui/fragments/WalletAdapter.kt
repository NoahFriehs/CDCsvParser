package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.AssetsFilterActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

/**
 *
 * [RecyclerView.Adapter<WalletAdapter.WalletViewHolder>] that can display a [List<Wallet>].
 */
class WalletAdapter(val wallets: List<Wallet>) : RecyclerView.Adapter<WalletAdapter.WalletViewHolder>() {

    class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    {
        init {
            itemView.setOnClickListener {
                val walletId = itemView.findViewById<TextView>(R.id.walletId).text.toString().toInt()
                val appModel = AppModelManager.getInstance()
                val wallet = appModel.txApp!!.wallets.find { it.walletId == walletId }
                val intent = Intent(itemView.context, AssetsFilterActivity::class.java)
                intent.putExtra("wallet", wallet)
                itemView.context.startActivity(intent)
            }
        }
    }

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
        val appModel = AppModelManager.getInstance()
        val assetValue = appModel.getValueOfAssets(wallet)
        val percentProfit = assetValue / wallet.moneySpent.toDouble() * 100
        val assetValueString = StringHelper.formatAmountToString(assetValue,5)
        val amountString = StringHelper.formatAmountToString(wallet.amount.toDouble(),5,wallet.currencyType!!)
        if(percentProfit > 100){
            holder.itemView.findViewById<TextView>(R.id.percentProfit).setTextColor(Color.GREEN)
        }else if(percentProfit == 100.0 ||  percentProfit == 0.0){
            holder.itemView.findViewById<TextView>(R.id.percentProfit).setTextColor(Color.GRAY)
        }else{
            holder.itemView.findViewById<TextView>(R.id.percentProfit).setTextColor(Color.RED)
        }

        holder.itemView.findViewById<TextView>(R.id.walletId).text = wallet.walletId.toString()
        if (wallet is CroCardWallet) holder.itemView.findViewById<TextView>(R.id.currencyType).text = wallet.transactionType
        else
        holder.itemView.findViewById<TextView>(R.id.currencyType).text = wallet.currencyType
        holder.itemView.findViewById<TextView>(R.id.amount).text = amountString
        holder.itemView.findViewById<TextView>(R.id.amountValue).text = assetValueString
        holder.itemView.findViewById<TextView>(R.id.percentProfit).text = StringHelper.formatAmountToString(percentProfit - 100,2,"%")
        holder.itemView.findViewById<TextView>(R.id.amountTransactions).text = wallet.transactions?.count().toString()

    }

    override fun getItemCount() = wallets.size
}