package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.util.Converter.doubleToStringConverter

data class WalletData(
    var walletId: Int = 0,
    var currencyType: String = "",
    var balance: Double = 0.0,
    var nativeBalance: Double = 0.0,
    var bonusBalance: Double = 0.0,
    var moneySpent: Double = 0.0,
    var isOutsideWallet: Boolean = false,
    var notes: String = ""
) {
    companion object {
        fun fromWallet(fromDb: Wallet): WalletData {
            return WalletData(
                fromDb.walletId,
                fromDb.currencyType,
                fromDb.amount.toDouble(),
                fromDb.amount.toDouble(),
                fromDb.amountBonus.toDouble(),
                fromDb.moneySpent.toDouble(),
                fromDb.isOutsideWallet,
                ""
            )
        }
    }
}

class WalletXmlSerializer {
    fun serializeToXml(wallet: WalletData): String {
        return buildString {
            append("<WalletData>")
            append("<walletId>${wallet.walletId}</walletId>")
            append("<currencyType>${wallet.currencyType}</currencyType>")
            append("<balance>${doubleToStringConverter(wallet.balance)}</balance>")
            append("<nativeBalance>${doubleToStringConverter(wallet.nativeBalance)}</nativeBalance>")
            append("<bonusBalance>${doubleToStringConverter(wallet.bonusBalance)}</bonusBalance>")
            append("<moneySpent>${doubleToStringConverter(wallet.moneySpent)}</moneySpent>")
            append("<isOutsideWallet>${wallet.isOutsideWallet}</isOutsideWallet>")
            append("<notes>${wallet.notes}</notes>")
            append("</WalletData>")
        }
    }

    fun deserializeFromXml(xml: String): WalletData {
        val wallet = WalletData()
        val regex = Regex("<(\\w+)>(.*?)</\\1>")
        regex.findAll(xml).forEach { matchResult ->
            val (tag, value) = matchResult.destructured
            when (tag) {
                "walletId" -> wallet.walletId = value.toInt()
                "currencyType" -> wallet.currencyType = value
                "balance" -> wallet.balance = value.toDouble()
                "nativeBalance" -> wallet.nativeBalance = value.toDouble()
                "bonusBalance" -> wallet.bonusBalance = value.toDouble()
                "moneySpent" -> wallet.moneySpent = value.toDouble()
                "isOutsideWallet" -> wallet.isOutsideWallet = value.toBoolean()
                "notes" -> wallet.notes = value
            }
        }
        return wallet
    }
}