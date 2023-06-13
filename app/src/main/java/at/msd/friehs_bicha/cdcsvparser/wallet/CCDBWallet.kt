package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.transactions.CCDBTransaction

/**
 * Saves the wallet data to a DBWallet object
 */
class CCDBWallet(wallet: Wallet) : DBWallet(wallet, true) {

    var transactionType: String

    init {
        wallet as CroCardWallet
        transactionType = wallet.transactionType!!
        wallet.transactions?.forEach { transaction ->
            transactions?.add(transaction?.let { CCDBTransaction(it) })
        }
    }

}