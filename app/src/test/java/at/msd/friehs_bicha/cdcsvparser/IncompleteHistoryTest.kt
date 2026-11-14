package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.core.IncompleteWalletNote
import at.msd.friehs_bicha.cdcsvparser.ui.display.incompleteHistory
import at.msd.friehs_bicha.cdcsvparser.ui.display.incompleteHistoryNotes
import at.msd.friehs_bicha.cdcsvparser.ui.display.isIncompleteHistory
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Incomplete-history detector: a held token wallet whose
 * FINAL balance is negative is the partial-export signature - the early
 * rows (e.g. the first purchase) are missing, so the debits outrun the
 * credits. Never flagged: the fiat EUR buffer (an overdraft is a
 * different story), card wallets and outside (bookkeeping) wallets,
 * where a negative balance is normal on a COMPLETE export.
 */
class IncompleteHistoryTest {

    private fun tokenWallet(currencyType: String, amount: String): Wallet =
        Wallet(currencyType, BigDecimal(amount), BigDecimal.ZERO, BigDecimal.ZERO)

    /** The same formatting the production note uses (locale-proof). */
    private fun expectedBalanceText(amount: String): String =
        StringHelper.removeLastZeros(
            DecimalFormat("#0.00000").format(BigDecimal(amount).toDouble())
        )

    @Test
    fun `negative token wallet is the partial-export signature`() {
        // The documented case: a BlockPit export without the initial
        // ADA purchase rows leaves ADA at -0.18.
        val ada = tokenWallet("ADA", "-0.18")
        assertTrue(isIncompleteHistory(ada))
    }

    @Test
    fun `zero balance is not incomplete`() {
        assertFalse(isIncompleteHistory(tokenWallet("ADA", "0")))
    }

    @Test
    fun `all positive set yields no note at all`() {
        val wallets = listOf(tokenWallet("BTC", "1.5"), tokenWallet("ADA", "120"))
        assertEquals(emptyList<Wallet>(), incompleteHistory(wallets))
        assertEquals(emptyList<IncompleteWalletNote>(), incompleteHistoryNotes(wallets))
    }

    @Test
    fun `negative fiat buffer is an overdraft, not a partial export`() {
        // Scope rule: the internal fiat EUR buffer stays out of the hint.
        assertFalse(isIncompleteHistory(tokenWallet("EUR", "-42.10")))
    }

    @Test
    fun `outside wallets are never flagged`() {
        // Outside wallets hold the counter entry of external movements;
        // plain deposits credit them and withdrawals debit them, so a
        // complete export can legitimately land them negative.
        val atm = tokenWallet("ATM", "-300").apply { isOutsideWallet = true }
        assertFalse(isIncompleteHistory(atm))
    }

    @Test
    fun `card wallets are out of scope`() {
        val card = CroCardWallet("EUR", BigDecimal("-12.5"), "credit", null)
        assertFalse(isIncompleteHistory(card))
    }

    @Test
    fun `notes carry name and negative balance for flagged wallets only`() {
        val wallets = listOf(
            tokenWallet("ETH", "0.3"),
            tokenWallet("ADA", "-0.18"),
            tokenWallet("EUR", "-9"),
        )
        val notes = incompleteHistoryNotes(wallets)
        assertEquals(1, notes.size)
        assertEquals("ADA", notes[0].name)
        assertEquals(expectedBalanceText("-0.18"), notes[0].balanceText)
        assertTrue(notes[0].balanceText.startsWith("-"))
    }

    @Test
    fun `notes are sorted by wallet name`() {
        val wallets = listOf(tokenWallet("ZEC", "-1"), tokenWallet("ADA", "-0.5"))
        val notes = incompleteHistoryNotes(wallets)
        assertEquals(listOf("ADA", "ZEC"), notes.map { it.name })
    }
}
