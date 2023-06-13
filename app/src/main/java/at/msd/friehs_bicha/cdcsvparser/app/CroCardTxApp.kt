package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import java.io.Serializable
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.function.Consumer

/**
 * CroCardTxApp class
 *
 * @param file csv file as String list
 * @param useStrictWallet use strict wallet type
 * @param fastInit fast init, use only if you set the transactions and wallets manually
 */
class CroCardTxApp(file: ArrayList<String>, useStrictWallet: Boolean, fastInit: Boolean = false) :
    BaseApp(), Serializable {

    init {
        if (!fastInit) {
            isUseStrictWalletType = useStrictWallet
            try {
                this.transactions.addAll(getTransactions(file))
            } catch (e: Exception) {
                FileLog.e("CroCardTxApp", " Error: $e")
                e.printStackTrace()
            }
            println("We have " + this.transactions.size + " transaction(s).")
            try {
                fillWallet()
            } catch (e: Exception) {
                FileLog.e("CroCardTxApp", " Error: $e")
                e.printStackTrace()
            }
            println("we have " + this.wallets.size + " different transactions.")
            //((CroCardWallet)wallets.get(0)).writeAmount();
        }
    }

    /**
     * CroCardTxApp constructor
     *
     * @param tXs CroCardTransaction list
     * @param wTXs CroCardWallet list
     * @param amountTxFailed amount of failed transactions
     */
    constructor(
        tXs: MutableList<CroCardTransaction>,
        wTXs: MutableList<CroCardWallet>,
        amountTxFailed: Long
    ) : this(ArrayList(), false, true) {
        this.transactions = tXs as ArrayList<Transaction>
        wTXs.forEach(Consumer { wallet: CroCardWallet ->
            wallet.txApp = this
        })
        this.wallets = ArrayList(wTXs)
        try {
            fillWallet(true)
        } catch (e: Exception) {
            FileLog.e("CroCardTxApp", " Error: $e")
            e.printStackTrace()
        }
        this.amountTxFailed = amountTxFailed
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
                val sa =
                    transaction.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (sa.size == 9) {
                    val t = CroCardTransaction(
                        sa[0],
                        sa[1],
                        sa[2],
                        decimalFormat.parse(sa[7]) as BigDecimal,
                        decimalFormat.parse(sa[7]) as BigDecimal,
                        sa[1]
                    )
                    transactions.add(t)
                } else {
                    println(sa.contentToString())
                    println(sa.size)
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        return transactions
    }

    /**
     * Fill wallets with transactions
     *
     * @param walletsExisting wallets existing
     */
    private fun fillWallet(walletsExisting: Boolean = false) {
        println("Filling Wallets")
        if (!walletsExisting) {
            wallets.add(CroCardWallet("EUR", BigDecimal.ZERO, "EUR -> EUR", this))
            for (t in transactions) {
                if ((t as CroCardTransaction).transactionTypeString == "EUR -> EUR") {
                    (wallets[0] as CroCardWallet).addToWallet(t)
                } else {
                    wallets[0].addTransaction(t)
                }
            }
        } else {
            for (t in transactions) {
                for (w in wallets)
                    if (w != null) {
                        if (t.walletId == w.walletId) {
                            w.addTransaction(t)
                            break
                        }
                    }
            }
        }
        println("Wallets filled")
    }


}