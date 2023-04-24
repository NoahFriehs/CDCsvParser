package at.msd.friehs_bicha.cdcsvparser.util

import java.text.DecimalFormat

object StringHelper {


    fun formatAmountToString(amount: Double?): String {
        val df = DecimalFormat("#0.00")
        return df.format(amount) + " €"
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


}