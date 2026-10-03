package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import at.msd.friehs_bicha.cdcsvparser.ui.display.ProfitTrend
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletSortKey
import at.msd.friehs_bicha.cdcsvparser.ui.display.sortWalletRows
import at.msd.friehs_bicha.cdcsvparser.ui.display.transactionRow
import at.msd.friehs_bicha.cdcsvparser.ui.display.walletRow
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.CDCWallet
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RowModelsTest {

    private fun tx(desc: String, amount: Double, currency: String): Transaction =
        Transaction(
            "Wed Jul 20 18:19:24 GMT+02:00 2022",
            desc,
            currency,
            BigDecimal(amount),
            BigDecimal(amount),
            TransactionType.crypto_deposit
        )

    private fun cardWallet(name: String, amount: Double, txCount: Int): CroCardWallet {
        val card = CroCardWallet("EUR", BigDecimal(amount), name, null)
        repeat(txCount) { card.transactions.add(tx("t$it", 1.0, "EUR")) }
        return card
    }

    @Test
    fun transactionRowFormatsAllFields() {
        val row = transactionRow(tx("Card Cashback", 0.25, "EUR"))
        assertEquals("Card Cashback", row.description)
        assertTrue(row.assetAmount.contains("0.25"))
        assertTrue(row.date.length > 0)
    }

    @Test
    fun cardWalletRowUsesFiatValue() {
        val row = walletRow(cardWallet("CASHBACK", 100.0, 1))
        assertEquals("CASHBACK", row.name)
        assertEquals(100.0, row.assetValue, 0.0001)
        assertEquals(1, row.transactionCount)
    }

    @Test
    fun profitTrendBoundaries() {
        // Card wallet value == its amount (100 EUR); percent = 100/spent*100.
        val positive = walletRow(cardWallet("A", 100.0, 0)).let {
            val w = cardWallet("A", 100.0, 0); w.moneySpent = BigDecimal(50.0); walletRow(w)
        }
        val neutralWallet = cardWallet("B", 100.0, 0); neutralWallet.moneySpent = BigDecimal(100.0)
        val negativeWallet = cardWallet("C", 100.0, 0); negativeWallet.moneySpent = BigDecimal(200.0)
        assertEquals(ProfitTrend.POSITIVE, positive.trend)
        assertEquals(ProfitTrend.NEUTRAL, walletRow(neutralWallet).trend)
        assertEquals(ProfitTrend.NEGATIVE, walletRow(negativeWallet).trend)
    }

    @Test
    fun cryptoWalletRowFallsBackToZeroWithoutPrices() {
        // No native price cache in JVM tests -> price 0 -> asset value 0.
        val w = CDCWallet("BTC", BigDecimal(2.0), BigDecimal(0.0), null, false)
        val row = walletRow(w)
        assertEquals("BTC", row.name)
        assertEquals(0.0, row.assetValue, 0.0001)
    }

    @Test
    fun sortWalletRowsByTransactionCount() {
        val a = walletRow(cardWallet("A", 10.0, 3))
        val b = walletRow(cardWallet("B", 10.0, 1))
        val c = walletRow(cardWallet("C", 10.0, 7))
        val desc = sortWalletRows(listOf(a, b, c), WalletSortKey.TRANSACTIONS, false)
        assertEquals(listOf(c.transactionCount, a.transactionCount, b.transactionCount),
            desc.map { it.transactionCount })
        val asc = sortWalletRows(listOf(a, b, c), WalletSortKey.TRANSACTIONS, true)
        assertEquals(listOf(1, 3, 7), asc.map { it.transactionCount })
    }
}
