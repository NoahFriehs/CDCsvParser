package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.transactions.TransactionType
import at.msd.friehs_bicha.cdcsvparser.util.Converter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.util.Locale

class ConverterTest {

    @Test
    fun doubleToStringConverterBasic() {
        assertEquals("12.34", Converter.doubleToStringConverter(12.34))
        assertEquals("100.0", Converter.doubleToStringConverter(100.0))
        assertEquals("0.0", Converter.doubleToStringConverter(0.0))
        // trailing zeros are not padded like the old "%.20f" formatter
        assertEquals("1.5", Converter.doubleToStringConverter(1.5))
        assertNull(Converter.doubleToStringConverter(null))
    }

    @Test
    fun doubleToStringConverterIsLocaleIndependent() {
        // Regression: the old String.format("%.20f", ...) used the device
        // locale, producing "12,5" on de_DE devices.
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val out = Converter.doubleToStringConverter(12.5)
            assertEquals("12.5", out)
            assertEquals(false, out!!.contains(','))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test
    fun ttConverterAcceptsValidTypes() {
        assertEquals(TransactionType.crypto_purchase, Converter.ttConverter("crypto_purchase"))
        // the converter trims and lowercases before matching the constants
        assertEquals(TransactionType.crypto_purchase, Converter.ttConverter("  CRYPTO_PURCHASE "))
        assertEquals(TransactionType.reimbursement, Converter.ttConverter("REIMBURSEMENT"))
    }

    @Test
    fun ttConverterRejectsUnknownTypes() {
        try {
            Converter.ttConverter("definitely_not_a_type")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected - and no crash on the happy contract
        }
    }
}
