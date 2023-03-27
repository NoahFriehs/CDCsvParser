package at.msd.friehs_bicha.cdcsvparser.util

import java.text.DecimalFormat

object StringHelper {
    fun formatAmountToString(amount: Double?): String {
        val df = DecimalFormat("#0.00")
        return df.format(amount) + " €"
    }
}