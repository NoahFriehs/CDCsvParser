package at.msd.friehs_bicha.cdcsvparser.wallet

import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import at.msd.friehs_bicha.cdcsvparser.util.Converter
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DBWallet ctor used to crash on the previously `!!`-marked inputs
 * (write-only construction: every live caller passes a fully set wallet
 * from the cloud export, the null-safe defaults keep a null input a
 * zeroed DB row instead of a crash at export edge).
 */
class WalletCtorNullSafetyTest {

    private fun tx(desc: String, amount: Double, currency: String): Transaction =
        Transaction(
            1L,
            desc,
            0,
            0,
            Converter.dateConverter("Wed Jul 20 18:19:24 GMT+02:00 2022"),
            currency,
            amount,
            amount,
            0.0,
            TransactionType.crypto_deposit,
            null,
            null,
            null,
            false
        )

    @Test
    fun dbWalletCtorCopiesAllFields() {
        val txs = ArrayList<Transaction?>()
        txs.add(tx("spend", 5.0, "EUR"))
        val source = CDCWallet(1L, "EUR", 105.0, 0.0, 47.5, true, txs)
        val db = DBWallet(source)
        assertEquals(1L, db.walletId)
        assertEquals("EUR", db.currencyType)
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
