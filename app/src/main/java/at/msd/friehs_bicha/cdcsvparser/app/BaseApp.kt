package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import java.io.Serializable

open class BaseApp : Serializable {
    var appType: AppType = AppType.Default
    var id: Int = 0
    open var wallets = ArrayList<Wallet>()
    var outsideWallets = ArrayList<Wallet>()
    open var transactions = ArrayList<Transaction>()
    var failedTxs = ArrayList<String>()
    var amountTxFailed: Long = 0
    var isUseStrictWalletType = false


    fun addFailedTx(tx: String) {
        failedTxs.add(tx)
        amountTxFailed++
    }

}