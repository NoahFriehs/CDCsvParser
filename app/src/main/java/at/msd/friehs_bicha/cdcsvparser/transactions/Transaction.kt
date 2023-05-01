package at.msd.friehs_bicha.cdcsvparser.transactions

import at.msd.friehs_bicha.cdcsvparser.util.Converter
import at.msd.friehs_bicha.cdcsvparser.util.CurrencyType
import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.util.Date

/**
 * Represents a Transaction object
 */
open class Transaction : Serializable {

    var transactionId: Int

    var description: String

    var walletId = 0

    var fromWalletId = 0

    var date: Date?

    var currencyType: String

    var amount: BigDecimal

    var nativeAmount: BigDecimal

    var amountBonus: BigDecimal?

    var transactionType: TransactionType?

    var transHash: String? = null

    var toCurrency: String? = null

    var toAmount: BigDecimal? = null

    var isOutsideTransaction = false

    var notes: String = ""

    constructor(date: String?, description: String, currencyType: String, amount: BigDecimal?, nativeAmount: BigDecimal?, transactionType: TransactionType?) {
        if (!CurrencyType.currencys.contains(currencyType)) CurrencyType.currencys.add(currencyType)
        this.date = Converter.dateConverter(date)
        this.description = description
        this.currencyType = currencyType
        this.amount = BigDecimal.ZERO
        this.amount = this.amount.add(amount)
        this.nativeAmount = BigDecimal.ZERO
        this.nativeAmount = this.nativeAmount.add(nativeAmount)
        this.transactionType = transactionType
        amountBonus = BigDecimal.ZERO
        transactionId = ++uidCounter
    }

    constructor(transactionId: Int, date: Date?, description: String, currencyType: String, amount: BigDecimal, nativeAmount: BigDecimal, amountBonus: BigDecimal?, transactionType: TransactionType?, transHash: String?, toCurrency: String?, toAmount: BigDecimal?, walletId: Int) {
        if (!CurrencyType.currencys.contains(currencyType)) CurrencyType.currencys.add(currencyType)
        this.transactionId = transactionId
        this.date = date
        this.description = description
        this.currencyType = currencyType
        this.amount = amount
        this.nativeAmount = nativeAmount
        this.amountBonus = amountBonus
        this.transactionType = transactionType
        this.transHash = transHash
        this.toCurrency = toCurrency
        this.toAmount = toAmount
        this.walletId = walletId
    }

    constructor(transaction: DBTransaction) {
        if (!CurrencyType.currencys.contains(transaction.currencyType)) CurrencyType.currencys.add(transaction.currencyType)
        this.transactionId = transaction.transactionId.toInt()
        this.date = transaction.date
        this.description = transaction.description
        this.currencyType = transaction.currencyType
        this.amount = BigDecimal(transaction.amount)
        this.nativeAmount = BigDecimal(transaction.nativeAmount)
        this.amountBonus = transaction.amountBonus?.let { BigDecimal(it) }
        this.transactionType = transaction.transactionType
        this.transHash = transaction.transHash
        this.toCurrency = transaction.toCurrency
        this.toAmount = transaction.toAmount?.let { BigDecimal(it) }
        this.walletId = transaction.walletId
    }

    constructor(
        transactionId: Long,
        description: String,
        walletId: Int,
        fromWalletId: Int,
        date: Date?,
        currencyType: String,
        amount: Double,
        nativeAmount: Double,
        amountBonus: Double,
        transactionType: TransactionType?,
        transHash: String?,
        toCurrency: String?,
        toAmount: Double?,
        outsideTransaction: Boolean
    )
    {
        if (!CurrencyType.currencys.contains(currencyType)) CurrencyType.currencys.add(currencyType)
        this.transactionId = transactionId.toInt()
        this.date = date
        this.description = description
        this.currencyType = currencyType
        this.amount = BigDecimal(amount)
        this.nativeAmount = BigDecimal(nativeAmount)
        this.amountBonus = BigDecimal(amountBonus)
        this.transactionType = transactionType
        this.transHash = transHash
        this.toCurrency = toCurrency
        this.toAmount = toAmount?.let { BigDecimal(it) }
        this.walletId = walletId
        this.fromWalletId = fromWalletId
        this.isOutsideTransaction = outsideTransaction
    }

//    fun setWalletId(uid: Int) {
//        walletId = uid
//    }
//
//    fun setFromWalletId(uid: Int) {
//        fromWalletId = uid
//    }

    override fun toString(): String {
        return """${date.toString()}
Description: $description
Amount: ${nativeAmount.round(MathContext(5))} €
AssetAmount: ${amount.round(MathContext(5))} $currencyType"""
    }

    companion object {
        var uidCounter = 0
    }
}