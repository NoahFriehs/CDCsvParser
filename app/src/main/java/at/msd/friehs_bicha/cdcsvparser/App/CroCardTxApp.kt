package at.msd.friehs_bicha.cdcsvparser.App

import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import java.io.Serializable
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

class CroCardTxApp(file: ArrayList<String>, useStrictWallet: Boolean) : BaseApp(), Serializable {
    /**
     * Can lead to some false wallets bc of Currencies TODO
     */
    var isUseStrictWalletType = false

    init {
        isUseStrictWalletType = useStrictWallet
        try {
            this.transactions.addAll(getTransactions(file))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        println("We have " + this.transactions.size + " transaction(s).")
        try {
            fillWallet()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        println("we have " + this.wallets.size + " different transactions.")
        //((CroCardWallet)wallets.get(0)).writeAmount();
    }

    /**
     * Csv file to CroCardTransaction list
     *
     * @param input csv file as String list
     * @return CroCardTransaction list
     */
    private fun getTransactions(input: ArrayList<String>): ArrayList<CroCardTransaction> {
        input.removeAt(0)
        val transactions = ArrayList<CroCardTransaction>()

        // Create a DecimalFormat that fits your requirements
        val symbols = DecimalFormatSymbols()
        symbols.groupingSeparator = ','
        symbols.decimalSeparator = '.'
        val pattern = "#,##"
        val decimalFormat = DecimalFormat(pattern, symbols)
        decimalFormat.isParseBigDecimal = true
        for (transaction in input) {
            try {
                val sa = transaction.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (sa.size == 9) {
                    val t = CroCardTransaction(sa[0], sa[1], sa[2], decimalFormat.parse(sa[7]) as BigDecimal, decimalFormat.parse(sa[7]) as BigDecimal, sa[1])
                    transactions.add(t)
                } else {
                    println(Arrays.toString(sa))
                    println(sa.size)
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        return transactions
    }

    private fun fillWallet() {
        println("Filling Wallets")
        wallets.add(CroCardWallet("EUR", BigDecimal.ZERO, "EUR -> EUR", this))
        for (t in transactions) {
            if ((t as CroCardTransaction).transactionTypeString == "EUR -> EUR") {
                (wallets[0] as CroCardWallet).addToWallet(t)
            } else {
                wallets[0]?.addTransaction(t)
            }
        }
        println("Wallets filled")
    }


}