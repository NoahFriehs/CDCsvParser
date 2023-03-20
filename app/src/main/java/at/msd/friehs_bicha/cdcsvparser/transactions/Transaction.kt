package at.msd.friehs_bicha.cdcsvparser.transactions

import androidx.room.*
import at.msd.friehs_bicha.cdcsvparser.util.Converter
import at.msd.friehs_bicha.cdcsvparser.util.CurrencyType
import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.util.*

/**
 * Represents a Transaction object
 */
@Entity(tableName = "transactions")
@TypeConverters(Converter::class)
open class Transaction : Serializable {
    @JvmField
    @PrimaryKey
    var transactionId: Int

    @JvmField
    @ColumnInfo(name = "description")
    var description: String

    @JvmField
    @ColumnInfo(name = "walletId")
    var walletId = 0

    @JvmField
    @ColumnInfo(name = "fromWalletId")
    var fromWalletId = 0

    @ColumnInfo(name = "date")
    var date: Date?

    @ColumnInfo(name = "currencyType")
    var currencyType: String

    @ColumnInfo(name = "amount", typeAffinity = ColumnInfo.TEXT)
    var amount: BigDecimal

    @ColumnInfo(name = "nativeAmount", typeAffinity = ColumnInfo.TEXT)
    var nativeAmount: BigDecimal

    @ColumnInfo(name = "amountBonus", typeAffinity = ColumnInfo.TEXT)
    var amountBonus: BigDecimal?

    @ColumnInfo(name = "transactionType", typeAffinity = ColumnInfo.TEXT)
    var transactionType: TransactionType?

    @ColumnInfo(name = "transHash")
    var transHash: String? = null

    @ColumnInfo(name = "toCurrency")
    var toCurrency: String? = null

    @ColumnInfo(name = "toAmount", typeAffinity = ColumnInfo.TEXT)
    var toAmount: BigDecimal? = null

    @ColumnInfo(name = "isOutsideTransaction")
    var isOutsideTransaction = false

    @Ignore
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

    fun setWalletId(uid: Int) {
        walletId = uid
    }

    fun setFromWalletId(uid: Int) {
        fromWalletId = uid
    }

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