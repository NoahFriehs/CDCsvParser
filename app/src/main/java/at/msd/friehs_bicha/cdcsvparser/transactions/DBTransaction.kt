package at.msd.friehs_bicha.cdcsvparser.transactions

import java.io.Serializable
import java.util.*

open class DBTransaction(transaction: Transaction) : Serializable {

    var transactionId: Long

    var description: String

    var walletId = 0

    var fromWalletId = 0

    var date: Date?

    var currencyType: String

    var amount: Double

    var nativeAmount: Double

    var amountBonus: Double?

    var transactionType: TransactionType?

    var transHash: String? = null

    var toCurrency: String? = null

    var toAmount: Double? = null

    var isOutsideTransaction = false

    init {
        transactionId = transaction.transactionId.toLong()
        description = transaction.description
        walletId = transaction.walletId
        fromWalletId = transaction.fromWalletId
        date = transaction.date
        currencyType = transaction.currencyType
        amount = transaction.amount.toDouble()
        nativeAmount = transaction.nativeAmount.toDouble()
        amountBonus = transaction.amountBonus?.toDouble()
        transactionType = transaction.transactionType
        transHash = transaction.transHash
        toCurrency = transaction.toCurrency
        toAmount = transaction.toAmount?.toDouble()
        isOutsideTransaction = transaction.isOutsideTransaction
    }
}