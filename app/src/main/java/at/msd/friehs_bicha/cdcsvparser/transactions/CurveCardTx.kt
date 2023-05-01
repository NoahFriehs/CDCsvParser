package at.msd.friehs_bicha.cdcsvparser.transactions

import java.math.BigDecimal

class CurveCardTx(date: String, transactionTypeString: String, amount: BigDecimal, currencyType: String, nativeAmount: BigDecimal?, toCurrency: String?, transHash: String, txType: String?, notes: String?) : CroCardTransaction(date, transactionTypeString, currencyType, amount, nativeAmount, transactionTypeString) {

    var txType: String = ""

    init {
        if (txType != null) {
            this.txType = txType
        }
        this.toCurrency = toCurrency
        this.transHash = transHash
        if (notes != null) {
            this.notes = notes
        }
    }

    override fun toString(): String {
        return "${super.toString()} TxType='$txType'"
    }


}