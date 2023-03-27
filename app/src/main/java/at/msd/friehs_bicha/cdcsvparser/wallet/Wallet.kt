package at.msd.friehs_bicha.cdcsvparser.wallet

import androidx.room.*
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.util.Converter
import java.io.Serializable
import java.math.BigDecimal

/**
 * Represents a basic Wallet
 */
@Entity(tableName = "wallet")
@TypeConverters(Converter::class)
open class Wallet : Serializable {
    @JvmField
    @PrimaryKey
    var walletId: Int

    @Relation(parentColumn = "walletId", entityColumn = "transactionId", associateBy = Junction(WalletTransactionCrossRef::class))
    @Ignore
    var transactions: MutableList<Transaction?>? = null

    @ColumnInfo(name = "currencyType")
    var currencyType: String?

    @ColumnInfo(name = "amount", typeAffinity = ColumnInfo.TEXT)
    var amount: BigDecimal

    @ColumnInfo(name = "amountBonus", typeAffinity = ColumnInfo.TEXT)
    var amountBonus: BigDecimal

    @ColumnInfo(name = "moneySpent", typeAffinity = ColumnInfo.TEXT)
    var moneySpent: BigDecimal

    @ColumnInfo(name = "isOutsideWallet")
    var isOutsideWallet = false

    @Ignore
    constructor(currencyType: String?, amount: BigDecimal?, nativeAmount: BigDecimal?) {
        this.currencyType = currencyType
        this.amount = BigDecimal(0)
        this.amount = this.amount.add(amount)
        moneySpent = BigDecimal(0)
        moneySpent = moneySpent.add(nativeAmount)
        amountBonus = BigDecimal(0)
        transactions = ArrayList()
        walletId = ++uidCounter
    }

    @Ignore
    constructor(currencyType: String?, amount: BigDecimal, amountBonus: BigDecimal, moneySpent: BigDecimal) {
        this.currencyType = currencyType
        this.amount = amount
        this.amountBonus = amountBonus
        this.moneySpent = moneySpent
        transactions = ArrayList()
        walletId = ++uidCounter
    }

    @Ignore
    constructor(currencyType: String?, amount: BigDecimal, amountBonus: BigDecimal, moneySpent: BigDecimal, transactions: ArrayList<Transaction?>?) {
        this.currencyType = currencyType
        this.amount = amount
        this.amountBonus = amountBonus
        this.moneySpent = moneySpent
        this.transactions = transactions
        walletId = ++uidCounter
    }

    constructor(walletId: Int, currencyType: String?, amount: BigDecimal, amountBonus: BigDecimal, moneySpent: BigDecimal, transactions: MutableList<Transaction?>?) {
        this.walletId = walletId
        this.currencyType = currencyType
        this.amount = amount
        this.amountBonus = amountBonus
        this.moneySpent = moneySpent
        this.transactions = transactions
    }

    //@Ignore
    constructor(walletId: Int, currencyType: String?, amount: BigDecimal, amountBonus: BigDecimal, moneySpent: BigDecimal) {
        this.walletId = walletId
        this.currencyType = currencyType
        this.amount = amount
        this.amountBonus = amountBonus
        this.moneySpent = moneySpent
    }

    constructor(wallet: Wallet?) {
        walletId = wallet!!.walletId
        currencyType = wallet.currencyType
        amount = wallet.amount
        amountBonus = wallet.amountBonus
        moneySpent = wallet.moneySpent
        transactions = wallet.transactions
        if (transactions == null) transactions = ArrayList()
        isOutsideWallet = wallet.isOutsideWallet
    }

    constructor(wallet: DBWallet?) {
        walletId = wallet!!.walletId.toInt()
        currencyType = wallet.currencyType
        amount = BigDecimal(wallet.amount)
        amountBonus = BigDecimal(wallet.amountBonus)
        moneySpent = BigDecimal(wallet.moneySpent)
        wallet.transactions?.forEach { transaction ->
            transactions?.add(transaction?.let { Transaction(it) }) }
        if (transactions == null) transactions = ArrayList()
        isOutsideWallet = wallet.isOutsideWallet
    }

    constructor(walletId: Long, currencyType: String?, amount: Double, amountBonus: Double, moneySpent: Double, outsideWallet: Boolean, transactions: ArrayList<Transaction?>)
    {
        this.walletId = walletId.toInt()
        this.currencyType = currencyType
        this.amount = BigDecimal(amount)
        this.amountBonus = BigDecimal(amountBonus)
        this.moneySpent = BigDecimal(moneySpent)
        this.transactions = transactions
        this.isOutsideWallet = outsideWallet
    }

    /**
     * Get Wallet from CurrencyType String
     *
     * @param ct the CurrencyType as String
     * @return the index of the wallet
     */
    open fun getWallet(ct: String?): Int {
        throw UnsupportedOperationException()
    }

    /**
     * Remove a transaction from the Wallet
     *
     * @param amount       the amount to remove
     * @param nativeAmount the amount in native currency to remove
     */
    open fun removeFromWallet(amount: BigDecimal?, nativeAmount: BigDecimal?) {
        this.amount = this.amount.subtract(amount)
        moneySpent = moneySpent.subtract(nativeAmount)
    }

    /**
     * Adds a transactions to the respective Wallet
     *
     * @param transaction the transaction to be added
     */
    open fun addTransaction(transaction: Transaction) {
        throw UnsupportedOperationException()
    }


    companion object {
        var uidCounter = 0
    }
}