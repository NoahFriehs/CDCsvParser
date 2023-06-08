package at.msd.friehs_bicha.cdcsvparser.util

import java.text.DecimalFormat

object StringHelper {


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


    fun removeLastZeros(amount: String): String {
        val amountParts = amount.split(".")
        if (amountParts.size == 2) {
            val lastPart = amountParts[1]
            var lastZeroIndex = lastPart.length - 1
            while (lastZeroIndex >= 0 && lastPart[lastZeroIndex] == '0') {
                lastZeroIndex--
            }
            if (lastZeroIndex >= 0) {
                return amountParts[0] + "." + lastPart.substring(0, lastZeroIndex + 1)
            }
            //if string has only zeros after the point
            if (lastZeroIndex == -1)
                return amountParts[0] + ".00"
        }
        return amount
    }
}