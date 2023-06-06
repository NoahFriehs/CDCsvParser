package at.msd.friehs_bicha.cdcsvparser.transactions

import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.AppTypeIdentifier
import at.msd.friehs_bicha.cdcsvparser.app.BaseApp
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import java.math.BigDecimal
import java.util.Date

class TransactionManager(private val transactions: MutableList<Transaction>?) {
    private val cumulativeAmounts: MutableMap<Date, BigDecimal> = mutableMapOf()

    init {
        calculateCumulativeAmounts()
    }

    private fun calculateCumulativeAmounts() {
        var balance = BigDecimal.ZERO
        if (transactions != null) {
            for (transaction in transactions)
            {
                    balance += transaction.amount
                    cumulativeAmounts[transaction.date!!] = balance
            }
        }
    }

    fun getBalanceAtDate(date: Date): BigDecimal? {
        return cumulativeAmounts[date]
    }

    fun getGraphDataSet(): LineData {
        val entries = mutableListOf<Entry>()
        for ((index, transaction) in transactions?.withIndex()!!) {
            val cumulativeAmount = cumulativeAmounts[transaction.date] ?: 0
            entries.add(Entry(index.toFloat(), cumulativeAmount.toFloat()))
        }

        val lineDataSet = LineDataSet(entries, "Cumulative Amount")
        lineDataSet.setColors(*ColorTemplate.COLORFUL_COLORS)
        lineDataSet.valueTextSize = 12f

        return LineData(lineDataSet)
    }

    companion object {

        fun txFromCsvList(input: ArrayList<String>, appType: AppType?, app : BaseApp): ArrayList<Transaction> {
            val transactions = ArrayList<Transaction>()
            val currencys = ArrayList<String>()
            var appType = appType
            if (appType == null) {
                appType = AppTypeIdentifier.getAppType(input)
            }
            for (line in input) {
                val tx = Transaction.fromCsvLine(line, appType)
                if (tx != null) {
                    transactions.add(tx)
                    if (!currencys.contains(tx.currencyType)) currencys.add(tx.currencyType)
                } else {
                    app.addFailedTx(line)
                }
            }

            createWallets(currencys, app)

            return transactions
        }

        private fun createWallets(
            currencys: ArrayList<String>,
            app: BaseApp
        ) {
            when (app.appType) {
                AppType.CdCsvParser -> createCDCWallets(currencys, app)
                else -> throw IllegalArgumentException("Unknown app type")
            }
        }

        private fun createCDCWallets(currencys: ArrayList<String>, app: BaseApp) {
            for (t in currencys) {
                app.wallets.add(CDCWallet(t, BigDecimal.ZERO, BigDecimal.ZERO, app, false))
                app.outsideWallets.add(CDCWallet(t, BigDecimal.ZERO, BigDecimal.ZERO, app, true))
            }
        }

    }
}
