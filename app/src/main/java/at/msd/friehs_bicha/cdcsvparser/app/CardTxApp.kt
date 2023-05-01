package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.CurveCardTx
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import java.io.Serializable
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class CardTxApp(file: ArrayList<String>, useStrictWallet: Boolean, fastInit: Boolean = false) : BaseApp(), Serializable {

    var croTxString = "Timestamp (UTC),Transaction Description,Currency,Amount,To Currency,To Amount,Native Currency,Native Amount,Native Amount (in USD),Transaction Kind,Transaction Hash"

    var curveTxString = "Date (YYYY-MM-DD as UTC),Merchant,Txn Amount (Funding Card),Txn Currency (Funding Card),Txn Amount (Foreign Spend),Txn Currency (Foreign Spend),Card Name,Card Last 4 Digits,Type,Category,Notes"

    init {
        if (!fastInit) {
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
    }

    /**
     * Csv file to CroCardTransaction list
     *
     * @param input csv file as String list
     * @return CroCardTransaction list
     */
    private fun getTransactions(input: ArrayList<String>): ArrayList<CroCardTransaction> {
        return when (input[0]) {
            croTxString -> parseCroCard(input)
            curveTxString -> {
                parseCurveCard(input)
                ArrayList<CroCardTransaction>()
            }
            else -> throw RuntimeException("Wrong file format")
        }
 }

    private fun parseCurveCard(input: ArrayList<String>) {
        input.removeAt(0)
        val transactions = ArrayList<CurveCardTx>()

        // Create a DecimalFormat that fits your requirements
        val symbols = DecimalFormatSymbols()
        symbols.groupingSeparator = ','
        symbols.decimalSeparator = '.'
        val pattern = "#,##"
        val decimalFormat = DecimalFormat(pattern, symbols)
        decimalFormat.isParseBigDecimal = true
        for (transaction in input) {
            try {
                transaction.replace(",,", ", ,")
                val sa = transaction.split(",".toRegex())
                if (sa.size == 11) {
                    val t = CurveCardTx(
                        sa[0],
                        sa[1],
                        decimalFormat.parse(sa[2]) as BigDecimal,
                        sa[3],
                        if (sa[4] != "") decimalFormat.parse(sa[4]) as BigDecimal else decimalFormat.parse(sa[2]) as BigDecimal,
                        sa[5],
                        sa[6] + sa[7],
                        sa[8],
                        sa[9] + ": " + sa[10])
                    transactions.add(t)
                } else {
                    println(sa.toString())
                    println(sa.size)
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        this.transactions.addAll(transactions)
    }

    private fun parseCroCard(input: java.util.ArrayList<String>): java.util.ArrayList<CroCardTransaction> {
        input.removeAt(0)
        val transactions = java.util.ArrayList<CroCardTransaction>()

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
                    println(sa.contentToString())
                    println(sa.size)
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        return transactions
    }


    private fun fillWallet(walletsExisting: Boolean = false) {
        println("Filling Wallets")
        if (!walletsExisting) {
            wallets.add(CroCardWallet("Test", BigDecimal.ZERO, "Test -> Test", this))
            for (t in transactions) {
                if ((t as CroCardTransaction).transactionTypeString == "EUR -> EUR") {
                    (wallets[0] as CroCardWallet).addToWallet(t)
                } else {
                    wallets[0]?.addTransaction(t)
                }
            }
            if (wallets[0]?.getWallet("Test -> Test") != -1)
                wallets.remove(wallets[wallets[0]?.getWallet("Test -> Test")!!])
        }
        else
        {
            for (t in transactions) {
                for (w in wallets)
                    if (w != null) {
                        if (t.walletId == w.walletId)
                        {
                            w.addTransaction(t)
                            break
                        }
                    }
            }
        }
        println("Wallets filled")
    }



}