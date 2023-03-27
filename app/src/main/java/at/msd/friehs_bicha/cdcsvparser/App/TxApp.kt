package at.msd.friehs_bicha.cdcsvparser.App

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import at.msd.friehs_bicha.cdcsvparser.util.Converter
import at.msd.friehs_bicha.cdcsvparser.util.CurrencyType
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import java.io.Serializable
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.function.Consumer

/**
 * The main class of the parser
 */
class TxApp : BaseApp, Serializable {
    /**
     * Create a new TxApp object
     *
     * @param file the csv file as String list
     * @throws IllegalArgumentException when the file is not supported
     */
    constructor(file: ArrayList<String>) {
        transactions = try {
            transactionsFromList(file)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file seems to be not supported yet.")
        }
        println("We have " + transactions.size + " transaction(s).")
        createWallets()
        fillWallet(transactions)
        if (amountTxFailed > 0) {
            throw RuntimeException("$amountTxFailed transaction(s) failed")
        }
    }

    constructor(transactions: List<Transaction?>?, wallets: List<CDCWallet>) {
        this.transactions = transactions as ArrayList<Transaction>
        wallets.forEach(Consumer { wallet: CDCWallet ->
            wallet.txApp = this
            if (wallet.isOutsideWallet) {
                outsideWallets.add(wallet)
            } else {
                this.wallets.add(wallet)
            }
        })
        fillProcessedWallets(transactions)
    }

    constructor(tXs: MutableList<Transaction>, wTXs: MutableList<CDCWallet>, wTXsOutside: MutableList<CDCWallet>, amountTxFailed: Long)
    {
        this.transactions = tXs as ArrayList<Transaction>
        wTXs.forEach(Consumer { wallet: CDCWallet ->
            wallet.txApp = this
        })
        this.wallets = ArrayList(wTXs)
        this.outsideWallets = ArrayList(wTXsOutside)
        this.amountTxFailed = amountTxFailed
        //fillProcessedWallets(transactions)
    }

    /**
     * Csv file to Transaction list
     *
     * @param input csv file as String list
     * @return Transactions list
     * @throws IllegalArgumentException when the file is not supported
     */
    private fun transactionsFromList(input: ArrayList<String>): ArrayList<Transaction> {
        require(input[0] == "Timestamp (UTC),Transaction Description,Currency,Amount,To Currency,To Amount,Native Currency,Native Amount,Native Amount (in USD),Transaction Kind,Transaction Hash") { "This file seems to be not supported yet." }
        input.removeAt(0)
        val transactions = ArrayList<Transaction>()
        val symbols = DecimalFormatSymbols()
        symbols.groupingSeparator = ','
        symbols.decimalSeparator = '.'
        val pattern = "#,##"
        val decimalFormat = DecimalFormat(pattern, symbols)
        decimalFormat.isParseBigDecimal = true
        for (transaction in input) {
            try {
                val sa = transaction.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (sa.size == 10 || sa.size == 11) {
                    var t: Transaction
                    t = if (sa[3].toDouble() == 0.0) {
                        if (sa[7].toDouble() == 0.0) {
                            Transaction(sa[0], sa[1], sa[2], BigDecimal.ZERO, BigDecimal.ZERO, Converter.ttConverter(sa[9]))
                        } else {
                            Transaction(sa[0], sa[1], sa[2], BigDecimal.ZERO, decimalFormat.parse(sa[7]) as BigDecimal, Converter.ttConverter(sa[9]))
                        }
                    } else {
                        Transaction(sa[0], sa[1], sa[2], decimalFormat.parse(sa[3]) as BigDecimal, decimalFormat.parse(sa[7]) as BigDecimal, Converter.ttConverter(sa[9]))
                    }
                    if (sa.size == 11) t.transHash = sa[10]
                    if (Converter.ttConverter(sa[9]) == TransactionType.viban_purchase) {
                        t.toCurrency = sa[4]
                        t.toAmount = BigDecimal.valueOf(sa[5].toDouble())
                    }
                    transactions.add(t)
                } else {
                    println(Arrays.toString(sa))
                    println(sa.size)
                }
            } catch (e: Exception) {
                println("Error while processing the following transaction: " + transaction + " | " + e.message)
                amountTxFailed++
                //                throw new RuntimeException(e);
            }
        }
        return transactions
    }

    /**
     * Creates Wallets for every CurrencyType
     */
    private fun createWallets() {
        for (t in CurrencyType.currencys) {
            wallets.add(CDCWallet(t, BigDecimal.ZERO, BigDecimal.ZERO, this, false))
        }
        for (t in CurrencyType.currencys) {
            outsideWallets.add(CDCWallet(t, BigDecimal.ZERO, BigDecimal.ZERO, this, true))
        }
    }

    /**
     * Fills the Wallets with the given transaction list
     *
     * @param tr the transaction list to be processed
     */
    private fun fillWallet(tr: ArrayList<Transaction>) {
        for (t in tr) {
            wallets[0]!!.addTransaction(t)
        }
        println("We have " + wallets.size + " Wallets")
    }

    private fun fillProcessedWallets(txs: List<Transaction?>?) {
        for (t in txs!!) {
            if (t!!.isOutsideTransaction) {
                outsideWallets[t.walletId - 1]?.transactions!!.add(t)
            }
            if (t.fromWalletId == t.walletId) {
                wallets[t.walletId - 1]!!.transactions!!.add(t)
            } else {
                wallets[t.walletId - 1]!!.transactions!!.add(t)
                wallets[t.fromWalletId - 1]!!.transactions!!.add(t)
            }
        }
    }
}