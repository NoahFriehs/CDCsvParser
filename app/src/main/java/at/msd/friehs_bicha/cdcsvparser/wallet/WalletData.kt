package at.msd.friehs_bicha.cdcsvparser.wallet

data class WalletData(
    var walletId: Int = 0,
    var currencyType: String = "",
    var balance: Double = 0.0,
    var nativeBalance: Double = 0.0,
    var bonusBalance: Double = 0.0,
    var moneySpent: Double = 0.0,
    var isOutsideWallet: Boolean = false,
    var notes: String = ""
)

class WalletXmlSerializer {
    fun serializeToXml(wallet: WalletData): String {
        return buildString {
            append("<WalletData>")
            append("<walletId>${wallet.walletId}</walletId>")
            append("<currencyType>${wallet.currencyType}</currencyType>")
            append("<balance>${wallet.balance}</balance>")
            append("<nativeBalance>${wallet.nativeBalance}</nativeBalance>")
            append("<bonusBalance>${wallet.bonusBalance}</bonusBalance>")
            append("<moneySpent>${wallet.moneySpent}</moneySpent>")
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