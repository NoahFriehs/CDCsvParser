package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat.startActivity
import at.msd.friehs_bicha.cdcsvparser.AssetsFilterActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.ui.activity.WalletViewActivity

import at.msd.friehs_bicha.cdcsvparser.ui.fragments.placeholder.PlaceholderContent.PlaceholderItem
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
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
        val appModel = AppModelManager.getInstance()
        val assetValue = appModel.getValueOfAssets(wallet)
        val percentProfit = assetValue / wallet.moneySpent.toDouble() * 100;
        val assetValueString = StringHelper.formatAmountToString(assetValue,5)
        val amountString = StringHelper.formatAmountToString(wallet.amount.toDouble(),5,wallet.currencyType!!,)
        if(percentProfit > 100){
            holder.itemView.findViewById<TextView>(R.id.percentProfit).setTextColor(Color.GREEN)
        }else if(percentProfit == 100.0 ||  percentProfit == 0.0){
            holder.itemView.findViewById<TextView>(R.id.percentProfit).setTextColor(Color.GRAY)
        }else{
            holder.itemView.findViewById<TextView>(R.id.percentProfit).setTextColor(Color.RED)
        }

        holder.itemView.findViewById<TextView>(R.id.walletId).text = wallet.walletId.toString()
        holder.itemView.findViewById<TextView>(R.id.currencyType).text = wallet.currencyType
        holder.itemView.findViewById<TextView>(R.id.amount).text = amountString
        holder.itemView.findViewById<TextView>(R.id.amountValue).text = assetValueString
        holder.itemView.findViewById<TextView>(R.id.percentProfit).text = StringHelper.formatAmountToString(percentProfit - 100,2,"%")
        holder.itemView.findViewById<TextView>(R.id.amountTransactions).text = wallet.transactions?.count().toString()

    }

    override fun getItemCount() = wallets.size
}