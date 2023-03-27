package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.transactions.DBTransaction
import java.io.Serializable

class DBWallet(wallet: Wallet?) : Serializable {

    var walletId: Long

    var transactions: MutableList<DBTransaction?>? = null

    var currencyType: String?

    var amount: Double

    var amountBonus: Double

    var moneySpent: Double

    var isOutsideWallet = false

    init {
        walletId = wallet!!.walletId.toLong()
        currencyType = wallet.currencyType
        amount = wallet.amount.toDouble()
        amountBonus = wallet.amountBonus.toDouble()
        moneySpent = wallet.moneySpent.toDouble()
        transactions = ArrayList()
        wallet.transactions?.forEach { transaction ->
            transactions?.add(transaction?.let { DBTransaction(it) }) }
        isOutsideWallet = wallet.isOutsideWallet
    }

}