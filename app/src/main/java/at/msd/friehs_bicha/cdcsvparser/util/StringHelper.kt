package at.msd.friehs_bicha.cdcsvparser.util

import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Helper-class to manipulate Strings
 */
object StringHelper {


    /**
     * Formats an amount to a String
     *
     * @param amount the amount to be formatted
     * @param decimalNumbers the amount of decimal numbers
     * @param symbol the symbol of the currency
     * @param writePlusIfPositive if true, a plus will be written if the amount is positive
     * @return the formatted amount as String
     */
    fun formatAmountToString(
        amount: Double,
        decimalNumbers: Int = 2,
        symbol: String = "€",
        writePlusIfPositive: Boolean = false
    ): String {
        val df = DecimalFormat("#0." + "0".repeat(decimalNumbers))
        if (writePlusIfPositive && amount > 0)
            return "+" + removeLastZeros(df.format(amount)) + " $symbol"
        return removeLastZeros(df.format(amount)) + " $symbol"
    }


    /**
     * Formats an amount to a String
     *
     * @param amount the amount to be formatted
     * @param decimalNumbers the amount of decimal numbers
     * @param symbol the symbol of the currency
     * @param writePlusIfPositive if true, a plus will be written if the amount is positive
     * @return the formatted amount as String
     */
    fun formatAmountToString(
        amount: BigDecimal,
        decimalNumbers: Int = 2,
        symbol: String = "€",
        writePlusIfPositive: Boolean = false
    ): String {
        return formatAmountToString(amount.toDouble(), decimalNumbers, symbol, writePlusIfPositive)
    }


    /**
     * Compare versions
     *
     * @param version1 the first version
     * @param version2 the second version
     * @return true if version1 is greater than version2
     */
    fun compareVersions(version1: String, version2: String): Boolean {
        val parts1 = version1.split(".").map { it.toInt() }
        val parts2 = version2.split(".").map { it.toInt() }
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val part1 = parts1.getOrElse(i) { 0 }
            val part2 = parts2.getOrElse(i) { 0 }
            if (part1 < part2) {
                return false
            } else if (part1 > part2) {
                return true
            }
        }
        return false
    }


    /**
     * Removes the last zeros of a String
     *
     * @param amount the String to be manipulated
     * @return the manipulated String
     */
    fun removeLastZeros(amount: String): String {
        var amount = amount

        val delimiter = if (amount.contains(",")) "," else "."

        var amountParts = amount.split(delimiter)
        if (amountParts.size == 2) {
            if (amount.startsWith("0")) amount = amount.removePrefix("0")
            if (amount.startsWith(delimiter)) amount = "0$amount"
            amountParts = amount.split(delimiter)
            if (amount.endsWith("0")) amount =
                amountParts[0] + delimiter + amountParts[1].replace("0", "")
            if (amount.endsWith(delimiter)) amount += "0"
        }
        return amount
    }
}