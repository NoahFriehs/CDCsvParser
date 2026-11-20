package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.transactions.DBTransaction
import java.io.Serializable

/**
 * Represents a basic DBWallet
 */
open class DBWallet(wallet: Wallet?, overridTX: Boolean = false) : Serializable {

    var walletId: Long

    var transactions: MutableList<DBTransaction?>? = null

    var currencyType: String

    var amount: Double

    var amountBonus: Double

    var moneySpent: Double

    var isOutsideWallet = false

    init {
        // Write-only construction: every surviving caller hands in a fully
        // set wallet from the posted state (the cloud export); the
        // null-safe defaults keep a null input a zeroed DB row instead of
        // a crash at export edge. [overridTX] leaves the transaction list
        // uninitialized: the card export (CCDBWallet) fills it with its own
        // row type.
        walletId = wallet?.walletId?.toLong() ?: 0L
        currencyType = wallet?.currencyType ?: ""
        amount = wallet?.amount?.toDouble() ?: 0.0
        amountBonus = wallet?.amountBonus?.toDouble() ?: 0.0
        moneySpent = wallet?.moneySpent?.toDouble() ?: 0.0
        if (!overridTX) {
            transactions = ArrayList()
            wallet?.transactions?.forEach { transaction ->
                transactions?.add(transaction?.let { DBTransaction(it) })
            }
        }
        isOutsideWallet = wallet?.isOutsideWallet ?: false
    }

}