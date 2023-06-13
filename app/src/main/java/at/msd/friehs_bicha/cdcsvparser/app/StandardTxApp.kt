package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionManager
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import java.io.Serializable
import java.util.function.Consumer

/**
 * Standard transaction app
 */
class StandardTxApp : BaseApp, Serializable {

    /**
     * Constructor
     *
     * @param file csv file as String list
     * @param appType app type
     */
    constructor(file: ArrayList<String>, appType: AppType) {

        transactions = TransactionManager.txFromCsvList(file, appType, this)
        FileLog.i("TxApp", "Transactions: " + transactions.size)
        FileLog.i("TxApp", "Wallets: " + wallets.size)
        FileLog.i("TxApp", "Outside Wallets: " + outsideWallets.size)
        FileLog.i("TxApp", "Failed Transactions: $amountTxFailed")
        if (amountTxFailed > 0) {
            FileLog.w("TxApp", "Failed Transactions: $failedTxs")
        }
    }

    /**
     * Constructor
     *
     * @param tXs transactions
     * @param wTXs wallets
     * @param wTXsOutside outside wallets
     * @param amountTxFailed amount of failed transactions
     * @param appType app type
     */
    constructor(
        tXs: MutableList<Transaction>,
        wTXs: MutableList<CDCWallet>,
        wTXsOutside: MutableList<CDCWallet>,
        amountTxFailed: Long,
        appType: AppType
    ) {
        this.transactions = tXs as ArrayList<Transaction>
        when (appType) {
            AppType.CdCsvParser -> {
                wTXs.forEach(Consumer { wallet: CDCWallet ->
                    wallet.txApp = this
                })
            }
            else -> throw NotImplementedError("AppType $appType not implemented")
        }

        this.wallets = ArrayList(wTXs)
        this.outsideWallets = ArrayList(wTXsOutside)
        this.amountTxFailed = amountTxFailed
    }


}