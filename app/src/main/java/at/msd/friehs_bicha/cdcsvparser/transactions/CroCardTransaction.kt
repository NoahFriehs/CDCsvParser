package at.msd.friehs_bicha.cdcsvparser.transactions

import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext

class CroCardTransaction(date: String?, description: String, currencyType: String, amount: BigDecimal?, nativeAmount: BigDecimal?, transactionType: String?) : Transaction(date, description, currencyType, amount, nativeAmount, TransactionType.STRING), Serializable {

    var transactionTypeString: String

    init {
        this.transactionTypeString = transactionType!!
    }

    override fun toString(): String {
        return """
            $date
            Description: $description
            Amount: ${nativeAmount.round(MathContext(5))}$currencyType
            transactionType: $transactionTypeString
            """.trimIndent()
    }
}