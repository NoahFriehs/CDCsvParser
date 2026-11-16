package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.app.BaseApp
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wallet ctors used to crash on the previously `!!`-marked inputs
 * (write-only construction: every live caller passes fully set values,
 * the null-safe defaults keep a null input a non-crashing restore edge
 * instead of an NPE).
 */
class WalletCtorNullSafetyTest {

    private fun tx(desc: String, amount: Double, currency: String): Transaction =
        Transaction(
            "Wed Jul 20 18:19:24 GMT+02:00 2022",
            desc,
            currency,
            BigDecimal(amount),
            BigDecimal(amount),
            TransactionType.crypto_deposit
        )

    @Test
    fun cdcWalletCtorDefaultsNullOutsideFlag() {
        val w = CDCWallet("BTC", BigDecimal.ONE, BigDecimal.ZERO, null, null)
        assertFalse(w.isOutsideWallet)
    }

    @Test
    fun cdcWalletCtorKeepsExplicitFlags() {
        assertTrue(CDCWallet("BTC", BigDecimal.ONE, BigDecimal.ZERO, null, true).isOutsideWallet)
        assertFalse(CDCWallet("BTC", BigDecimal.ONE, BigDecimal.ZERO, null, false).isOutsideWallet)
    }

    @Test
    fun cdcWalletGetWalletDefaultsWithoutModel() {
        // Previously a guaranteed NPE at the `txApp!!` use site.
        val w = CDCWallet("BTC", BigDecimal.ONE, BigDecimal.ZERO, null, false)
        assertEquals(-1, w.getWallet("BTC"))
    }

    @Test
    fun cdcWalletGetWalletFindsCurrencyInModel() {
        val app = BaseApp()
        app.wallets.add(Wallet(1, "BTC", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, false))
        val w = CDCWallet("EUR", BigDecimal.ZERO, BigDecimal.ZERO, app, false)
        assertEquals(0, w.getWallet("BTC"))
        assertEquals(-1, w.getWallet("DOGE"))
    }

    @Test
    fun cdcWalletAddToWalletDeduplicates() {
        val w = CDCWallet("BTC", BigDecimal.ZERO, BigDecimal.ZERO, null, false)
        val same = tx("deposit", 1.0, "BTC")
        w.addToWallet(same)
        w.addToWallet(same)
        assertEquals(1, w.transactions.size)
        w.addToWallet(tx("second", 2.0, "BTC"))
        assertEquals(2, w.transactions.size)
        // The amount math applies per call; only the list insert dedupes.
        assertEquals(BigDecimal(4.0), w.amount)
    }

    @Test
    fun dbWalletCtorCopiesAllFields() {
        val source = CDCWallet("EUR", BigDecimal("100.0"), BigDecimal("42.5"), null, true)
        source.addToWallet(tx("spend", 5.0, "EUR"))
        val db = DBWallet(source)
        assertEquals(source.walletId.toLong(), db.walletId)
        assertEquals("EUR", db.currencyType)
        // addToWallet adds the transaction to amount + moneySpent too.
        assertEquals(105.0, db.amount, 1e-6)
        assertEquals(0.0, db.amountBonus, 1e-6)
        assertEquals(47.5, db.moneySpent, 1e-6)
        assertTrue(db.isOutsideWallet)
        assertEquals(1, db.transactions?.size)
    }

    @Test
    fun dbWalletCtorDefaultsOnNullSource() {
        // Previously a guaranteed NPE at the first `wallet!!` use.
        val db = DBWallet(null)
        assertEquals(0L, db.walletId)
        assertEquals("", db.currencyType)
        assertEquals(0.0, db.amount, 1e-6)
        assertEquals(0.0, db.amountBonus, 1e-6)
        assertEquals(0.0, db.moneySpent, 1e-6)
        assertFalse(db.isOutsideWallet)
        assertTrue(db.transactions.orEmpty().isEmpty())
    }

    @Test
    fun dbWalletCtorOverridTXKeepsTransactionsUninitialized() {
        assertNull(DBWallet(null, overridTX = true).transactions)
    }
}
