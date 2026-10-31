package at.msd.friehs_bicha.cdcsvparser.app

import at.msd.friehs_bicha.cdcsvparser.transactions.CroCardTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Line-shape coverage for the Kotlin card parser: the current export
 * shape (11 raw columns, the trailing Transaction Hash empty -> 10
 * after the drop) and the legacy 9-column shape both parse, other
 * column counts stay rejected. The spend totals mirror the C++ card
 * path's money-spent figure for the same rows.
 */
class CardTxAppTest {

    private val cdcHeader =
        "Timestamp (UTC),Transaction Description,Currency,Amount,To Currency,To Amount,Native Currency,Native Amount,Native Amount (in USD),Transaction Kind,Transaction Hash"

    /** The card-scope spend total: the sum over the card wallet balances. */
    private fun walletTotal(app: CardTxApp): BigDecimal =
        app.wallets.fold(BigDecimal.ZERO) { sum, wallet -> sum.add(wallet.amount) }

    @Test
    fun currentShapeParsesFieldsAndSpendTotal() {
        val lines = ArrayList(
            listOf(
                cdcHeader,
                "2024-04-01 12:00:00,Supermarket,VISA,10.00,EUR,-10.00,EUR,-10.00,-11.00,0,",
                "2024-04-02 08:30:00,Cafeteria,Visa,15.00,EUR,-15.00,EUR,-15.00,-16.50,0,",
                "2024-04-03 09:00:00,Streaming,Master,5.00,EUR,-5.00,EUR,-5.00,-5.50,0,"
            )
        )
        val app = CardTxApp(lines, false)

        assertEquals(3, app.transactions.size)
        val first = app.transactions[0] as CroCardTransaction
        assertEquals("Supermarket", first.description)
        assertEquals("VISA", first.currencyType)
        assertEquals(0, BigDecimal("-10.00").compareTo(first.amount))
        assertEquals(0, BigDecimal("-10.00").compareTo(first.nativeAmount))
        assertEquals("Supermarket", first.transactionTypeString)

        // One wallet per merchant, seeded and aggregated on the signed
        // Native Amount column: -30, the C++ card path's money-spent
        // total for these rows.
        assertEquals(0, BigDecimal("-30.00").compareTo(walletTotal(app)))
    }

    @Test
    fun legacyNineColumnShapeStillParses() {
        val lines = ArrayList(
            listOf(
                cdcHeader,
                "2020-03-01 10:00:00,Coffee Shop,Master,EUR,4.00,EUR,-4.00,-4.00,x",
                "2020-03-02 11:00:00,Grocery,Visa,EUR,23.50,EUR,-23.50,-23.50,y"
            )
        )
        val app = CardTxApp(lines, false)

        assertEquals(2, app.transactions.size)
        val first = app.transactions[0] as CroCardTransaction
        assertEquals("Coffee Shop", first.description)
        assertEquals("Master", first.currencyType)
        assertEquals(0, BigDecimal("-4.00").compareTo(first.amount))
        assertEquals(0, BigDecimal("-4.00").compareTo(first.nativeAmount))
        assertEquals("Coffee Shop", first.transactionTypeString)

        assertEquals(0, BigDecimal("-27.50").compareTo(walletTotal(app)))
    }

    @Test
    fun unsupportedColumnCountIsRejected() {
        val lines = ArrayList(
            listOf(
                cdcHeader,
                "2024-01-01 00:00:00,Broken,VISA,1.00,EUR,-1.00,EUR,-1.00"
            )
        )
        val app = CardTxApp(lines, false)
        assertTrue(app.transactions.isEmpty())
    }
}
