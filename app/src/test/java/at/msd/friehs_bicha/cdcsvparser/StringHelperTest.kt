package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringHelperTest {

    private val h = StringHelper

    // ---------- splitCsvLine ----------

    @Test
    fun splitCsvLineWithoutQuotesMatchesOldBehavior() {
        assertEquals(listOf("a", "b", "c"), h.splitCsvLine("a,b,c"))
        // trailing delimiter: no extra empty token (legacy splitString behavior)
        assertEquals(listOf("a", "b"), h.splitCsvLine("a,b,"))
        assertEquals(ArrayList<String>(), h.splitCsvLine(""))
        assertEquals(listOf("x"), h.splitCsvLine("x"))
    }

    @Test
    fun splitCsvLineKeepsCommasInQuotedField() {
        assertEquals(listOf("a", "b, c", "d"), h.splitCsvLine("a,\"b, c\",d"))
    }

    @Test
    fun splitCsvLineEscapedQuotes() {
        assertEquals(listOf("He said \"hi\"", "b"), h.splitCsvLine("\"He said \"\"hi\"\"\",b"))
    }

    @Test
    fun splitCsvLineQuoteMidFieldStaysLiteral() {
        assertEquals(listOf("ab\"cd", "e"), h.splitCsvLine("ab\"cd,e"))
    }

    @Test
    fun splitCsvLineCustomDelimiter() {
        assertEquals(listOf("a", "b; c"), h.splitCsvLine("a;\"b; c\"", ';'))
    }

    // ---------- compareVersions ----------

    @Test
    fun compareVersionsOrdersCorrectly() {
        assertEquals(h.compareVersions("2.0.0", "1.0.0"), true)
        assertEquals(h.compareVersions("1.0.0", "1.0.0"), false)
        assertEquals(h.compareVersions("1.0.1", "1.0.0"), true)
        assertEquals(h.compareVersions("1.0.0", "1.0.1"), false)
        // digit-tolerant: longer numbers must not compare lexicographically
        assertEquals(h.compareVersions("1.10.0", "1.9.0"), true)
        // suffixes are tolerated
        assertEquals(h.compareVersions("1.0.0-beta", "1.0.0"), false)
    }

    // ---------- formatAmountToString ----------

    @Test
    fun formatAmountToStringBasic() {
        val out = h.formatAmountToString(12.34)
        assertTrue(out.contains("12,34") || out.contains("12.34")) // locale dependent symbol/decimal
        assertTrue(h.formatAmountToString(12.0, writePlusIfPositive = true).startsWith("+"))
    }
}
