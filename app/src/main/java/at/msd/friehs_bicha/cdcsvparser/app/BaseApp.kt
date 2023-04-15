package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import java.io.Serializable

open class BaseApp : Serializable {
    open var wallets = ArrayList<Wallet?>()
    var outsideWallets = ArrayList<Wallet?>()
    open var transactions = ArrayList<Transaction>()
    var amountTxFailed : Long = 0
}