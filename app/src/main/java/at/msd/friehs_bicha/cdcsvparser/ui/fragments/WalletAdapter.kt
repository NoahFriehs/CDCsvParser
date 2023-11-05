package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.AssetsFilterActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.wallet.IWalletAdapterCallback
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet

/**
 *
 * [RecyclerView.Adapter<WalletAdapter.WalletViewHolder>] that can display a [List<Wallet>].
 */
class WalletAdapter(val wallets: List<Wallet>) :
    RecyclerView.Adapter<WalletAdapter.WalletViewHolder>() {

    class WalletViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.setOnClickListener {
                val walletId =
                    itemView.findViewById<TextView>(R.id.walletId).text.toString().toInt()
                val appModel = AppModelManager.getInstance()!!  //TODO: check if null
                val app = appModel.txApp ?: appModel.cardApp
                val wallet = app!!.wallets.find { it.walletId == walletId }
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
        val appModel = AppModelManager.getInstance()!!
            val waMap = appModel.getWalletAdapter(wallet)
            //  appModel.getWalletAdapter(wallet, readData, holder) //TODO: this better

            displayTexts(waMap, holder, holder.itemView.context)
    }


    val readData = (object : IWalletAdapterCallback {
        override fun onCallback(value: MutableMap<String, String?>, holder: WalletViewHolder) {
            displayTexts(value, holder, holder.itemView.context)
        }
    })


    /**
     * Displays the prices of wallet
     *
     * @param texts the Map<String></String>, String> which should be displayed with id of View and text to set pairs
     */
    private fun displayTexts(
        texts: Map<String, String?>?,
        holder: WalletViewHolder,
        context: Context
    ) {
        texts!!.forEach { (key: String?, value: String?) ->
            if (key == "COLOR") {
                val color = value!!.toInt()
                holder.itemView.findViewById<TextView>(R.id.percentProfit).setTextColor(color)
                return@forEach
            }
            val textView = holder.itemView.findViewById<TextView>(
                holder.itemView.resources.getIdentifier(
                    key,
                    "id",
                    context.packageName
                )
            )
            if (textView == null) {
                FileLog.e("WalletAdapter", "textView is null for key: $key")
                return@forEach
            }
            if (value == null) {
                textView.visibility = View.INVISIBLE
            } else {
                textView.text = value
            }
        }
    }


    override fun getItemCount() = wallets.size
}