package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.transactions.CCDBTransaction

class CCDBWallet(wallet: Wallet) : DBWallet(wallet, true) {

    init {
        wallet as CroCardWallet
        wallet.transactions?.forEach { transaction ->
            transactions?.add(transaction?.let { CCDBTransaction(it) }) }
    }

}