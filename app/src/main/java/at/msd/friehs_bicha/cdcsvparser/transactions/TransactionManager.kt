package at.msd.friehs_bicha.cdcsvparser.transactions

import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import java.math.BigDecimal
import java.util.Date

class TransactionManager(private val transactions: MutableList<Transaction?>?) {
    private val cumulativeAmounts: MutableMap<Date, BigDecimal> = mutableMapOf()

    init {
        calculateCumulativeAmounts()
    }

    private fun calculateCumulativeAmounts() {
        var balance = BigDecimal.ZERO
        if (transactions != null) {
            for (transaction in transactions) if (transaction != null) {
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
            val cumulativeAmount = cumulativeAmounts[transaction?.date] ?: 0
            entries.add(Entry(index.toFloat(), cumulativeAmount.toFloat()))
        }

        val lineDataSet = LineDataSet(entries, "Cumulative Amount")
        lineDataSet.setColors(*ColorTemplate.COLORFUL_COLORS)
        lineDataSet.valueTextSize = 12f

        return LineData(lineDataSet)
    }
}
