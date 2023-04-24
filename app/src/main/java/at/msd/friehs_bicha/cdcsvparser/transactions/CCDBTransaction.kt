package at.msd.friehs_bicha.cdcsvparser.transactions

class CCDBTransaction(transaction: Transaction) : DBTransaction(transaction) {

    var transactionTypeString: String? = null

    init {
        transaction as CroCardTransaction
        transactionTypeString = transaction.transactionTypeString
    }

}