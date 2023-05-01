package at.msd.friehs_bicha.cdcsvparser.transactions

import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.util.Date

open class CroCardTransaction(date: String?, description: String, currencyType: String, amount: BigDecimal?, nativeAmount: BigDecimal?, transactionType: String?) : Transaction(date, description, currencyType, amount, nativeAmount, TransactionType.STRING), Serializable {
    constructor(
        transactionId: Long,
        description: String,
        walletID: Int,
        fromWalletID: Int,
        date: Date,
        currencyType: String,
        amount: Double,
        nativeAmount: Double,
        amountBonus: Double,
        transactionType: String,
        transHash: String?,
        toCurrency: String?,
        toAmount: Double?,
        isOutsideTransaction: Boolean,
        transactionTypeString: String
    ) : this(
        date.toString(),
        description,
        currencyType,
        BigDecimal(amount),
        BigDecimal(nativeAmount),
        transactionType
    )
    {
        this.transactionId = transactionId.toInt()
        this.walletId = walletID
        this.fromWalletId = fromWalletID
        this.date = date
        this.currencyType = currencyType
        this.amount = BigDecimal(amount)
        this.nativeAmount = BigDecimal(nativeAmount)
        this.amountBonus = BigDecimal(amountBonus)
        this.transactionType = TransactionType.STRING
        this.transHash = transHash
        this.toCurrency = toCurrency
        this.toAmount = toAmount?.let { BigDecimal(it) }
        this.isOutsideTransaction = isOutsideTransaction
        this.transactionTypeString = transactionTypeString
    }

    var transactionTypeString: String

    init {
        this.transactionTypeString = transactionType!!
    }

    override fun toString(): String {
        if (currencyType != "EUR") {
            return """
                $date
                Description: $description
                Amount: ${nativeAmount.round(MathContext(5))}$toCurrency
                transactionType: $transactionTypeString
                """.trimIndent()
        }
        return """
            $date
            Description: $description
            Amount: ${nativeAmount.round(MathContext(5))}$currencyType
            transactionType: $transactionTypeString
            """.trimIndent()
    }
}