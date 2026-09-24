package at.msd.friehs_bicha.cdcsvparser.util

import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Helper-class to manipulate Strings
 */
object StringHelper {

    /**
     * Splits a CSV line with RFC-4180 quote handling: a quoted field keeps
     * embedded delimiters, doubled quotes inside a quoted field are an
     * escaped quote, and quotes not at the start of a field are literals.
     * For lines without quotes the result matches the old
     * split(",").dropLastWhile { it.isEmpty() } behavior.
     */
    fun splitCsvLine(input: String, delimiter: Char = ','): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < input.length) {
            val c = input[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < input.length && input[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(c)
                }
            } else {
                // A quote only opens a quoted field at the start of a field,
                // so unquoted quotes stay literal.
                if (c == '"' && current.isEmpty()) {
                    inQuotes = true
                } else if (c == delimiter) {
                    result.add(current.toString())
                    current.clear()
                } else {
                    current.append(c)
                }
            }
            i++
        }
        result.add(current.toString())

        // Match the legacy split(...).dropLastWhile { it.isEmpty() } behavior:
        // trailing empty tokens (e.g. from a trailing delimiter) are dropped.
        while (result.isNotEmpty() && result.last().isEmpty()) {
            result.removeAt(result.size - 1)
        }
        return result
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
        // Tolerate non-numeric suffixes like "1.0.0-beta".
        val toPart: (String) -> Int = { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val parts1 = version1.split(".").map(toPart)
        val parts2 = version2.split(".").map(toPart)
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
        val chars = amount.toCharArray()
        var index = amount.length - 1
        while (index >= 0) {
            if (chars[index] != '0') {
                break
            }
            index--
        }
        var ret = if (index == amount.length - 1) {
            amount
        } else {
            amount.substring(0, index + 1)
        }
        if (ret.endsWith(".") || ret.endsWith(",")) {
            ret = ret.substring(0, ret.length - 1)
        }
        return ret
    }
}