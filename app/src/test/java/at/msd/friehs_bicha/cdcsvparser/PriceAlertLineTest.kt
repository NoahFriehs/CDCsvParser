package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.AlertDirection
import at.msd.friehs_bicha.cdcsvparser.app.PriceAlertLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The thin JVM mirror of the core's price-alert contract: the AL serde
 * line (build + parse, the direction vocabulary, the latch invariant
 * both ways, the shape rejections - the automaton itself is core-side).
 */
class PriceAlertLineTest {

    @Test
    fun buildLineExactShape() {
        // The SET form is always the fresh state: fired = 0, stamp "-"
        // (creation and a user edit are both a deliberate re-arm).
        assertEquals(
            "AL;a1;BTC;ABOVE;60000;0;-",
            PriceAlertLine.buildLine("a1", "BTC", AlertDirection.Above, 60000.0),
        )
        assertEquals(
            "AL;a2;ETH;BELOW;3000.5;0;-",
            PriceAlertLine.buildLine("a2", "ETH", AlertDirection.Below, 3000.5),
        )
    }

    @Test
    fun buildParseRoundTrip() {
        // build -> parse is lossless on every field (the build form is
        // the SET form: fired 0, stamp "-").
        val built = PriceAlertLine.buildLine("u1", "SOL", AlertDirection.Below, 1234.5)
        val parsed = PriceAlertLine.parse(built)
        assertNotNull(parsed)
        assertEquals("u1", parsed!!.id)
        assertEquals("SOL", parsed.symbol)
        assertEquals(AlertDirection.Below, parsed.direction)
        assertEquals(1234.5, parsed.thresholdEur, 0.0)
        assertFalse(parsed.fired)
        assertNull(parsed.firedIso)
        // And the parse -> rebuild cycle is byte-stable (the state the
        // core reports is the line the app would re-send on an edit).
        assertEquals(built, PriceAlertLine.buildLine(
            parsed.id,
            parsed.symbol,
            parsed.direction,
            parsed.thresholdEur,
        ))
    }

    @Test
    fun parseFields() {
        val unfired = PriceAlertLine.parse("AL;a1;BTC;ABOVE;60000;0;-")
        assertTrue(unfired != null)
        assertEquals("a1", unfired!!.id)
        assertEquals("BTC", unfired.symbol)
        assertEquals(AlertDirection.Above, unfired.direction)
        assertEquals(60000.0, unfired.thresholdEur, 0.0)
        assertFalse(unfired.fired)
        assertNull(unfired.firedIso)

        val fired = PriceAlertLine.parse("AL;a2;ETH;BELOW;3000;1;2026-03-01T10:00:00")
        assertTrue(fired != null)
        assertEquals(AlertDirection.Below, fired!!.direction)
        assertEquals(3000.0, fired.thresholdEur, 0.0)
        assertTrue(fired.fired)
        assertEquals("2026-03-01T10:00:00", fired.firedIso)
    }

    @Test
    fun parseRejectsBadShape() {
        assertNull(PriceAlertLine.parse("AL;a1;BTC;ABOVE;1;0"))            // underflow
        assertNull(PriceAlertLine.parse("AL;a1;BTC;SIDEWAYS;1;0;-"))       // bad direction
        assertNull(PriceAlertLine.parse("AL;a1;B TC;ABOVE;1;0;-"))         // bad symbol
        assertNull(PriceAlertLine.parse("AL;a1;;ABOVE;1;0;-"))             // empty symbol
        assertNull(PriceAlertLine.parse("AL;a1;BTC;ABOVE;-1;0;-"))         // negative threshold
        assertNull(PriceAlertLine.parse("AL;a1;BTC;ABOVE;many;0;-"))       // not a number
        assertNull(PriceAlertLine.parse("AL;a1;BTC;ABOVE;nan;0;-"))        // NaN
        assertNull(PriceAlertLine.parse("AL;a1;BTC;ABOVE;1;2;-"))          // bad fired token
        assertNull(PriceAlertLine.parse("AL;a1;BTC;ABOVE;1;1;-"))          // fired w/o stamp
        assertNull(PriceAlertLine.parse("AL;a1;BTC;ABOVE;1;0;nope"))       // unfired w/ stamp
        assertNull(PriceAlertLine.parse("A;a1;BTC;ABOVE;1;0;-"))           // wrong kind
    }

    @Test
    fun directionVocabulary() {
        assertEquals(2, AlertDirection.entries.size)
        assertEquals("ABOVE", AlertDirection.Above.token)
        assertEquals("BELOW", AlertDirection.Below.token)
        assertEquals(AlertDirection.Above, AlertDirection.fromToken("ABOVE"))
        assertEquals(AlertDirection.Below, AlertDirection.fromToken("BELOW"))
        assertNull(AlertDirection.fromToken("above"))     // case-sensitive (the core's rule)
    }
}
