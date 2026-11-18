package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.AlertDirection
import at.msd.friehs_bicha.cdcsvparser.app.FiredLine
import at.msd.friehs_bicha.cdcsvparser.app.GoalMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JVM parse of the core's FIRED lines (the only part of the alert
 * pass that crosses the C boundary): the two shapes (price / goal), the
 * ';;' escape of the goal label, the SIGNED current value, and the
 * shape rejections (a line that fails to parse is a contract drift -
 * the core never emits one).
 */
class FiredAlertLineTest {

    @Test
    fun parsePriceLine() {
        val fired = FiredLine.parse("FIRED;PRICE;a1;BTC;ABOVE;60000;65432.1")
        assertTrue(fired is FiredLine.Price)
        val price = fired as FiredLine.Price
        assertEquals("a1", price.id)
        assertEquals("BTC", price.symbol)
        assertEquals(AlertDirection.Above, price.direction)
        assertEquals(60000.0, price.thresholdEur, 0.0)
        assertEquals(65432.1, price.priceEur, 0.001)
    }

    @Test
    fun parseGoalLine() {
        val fired = FiredLine.parse("FIRED;GOAL;g1;Fund;ASSETS;150;100")
        assertTrue(fired is FiredLine.Goal)
        val goal = fired as FiredLine.Goal
        assertEquals("g1", goal.id)
        assertEquals("Fund", goal.label)
        assertEquals(GoalMetric.Assets, goal.metric)
        assertEquals(150.0, goal.currentEur, 0.0)
        assertEquals(100.0, goal.targetEur, 0.0)
    }

    @Test
    fun parseGoalLineWithEscapedLabel() {
        // The single escape rule of the line format: a ';' inside the
        // label travels as ';;' and comes back as one ';'.
        val fired = FiredLine.parse("FIRED;GOAL;g2;Mort;;gage free;NET_WORTH;1234.56;2500")
        assertTrue(fired is FiredLine.Goal)
        assertEquals("Mort;gage free", (fired as FiredLine.Goal).label)
        assertEquals(GoalMetric.NetWorth, fired.metric)
        assertEquals(1234.56, fired.currentEur, 0.0001)
    }

    @Test
    fun parseGoalLineSignedCurrentIsShapeOnly() {
        // The parser is a SHAPE check: it accepts a signed current
        // without recomputing any crossing math (the core pins the
        // firing invariant in its fixtures - a fire carries current >=
        // target > 0, so a realistic line is positive).
        val fired = FiredLine.parse("FIRED;GOAL;g3;Solvency;NET_WORTH;-500;100")
        assertTrue(fired is FiredLine.Goal)
        val goal = fired as FiredLine.Goal
        assertEquals(-500.0, goal.currentEur, 0.0)
        assertEquals(100.0, goal.targetEur, 0.0)
    }

    @Test
    fun parseRejectsBadShape() {
        assertNull(FiredLine.parse("FIRED;a1;BTC;ABOVE;1;2;3"))        // missing kind
        assertNull(FiredLine.parse("FIRED;NOPE;a1;BTC;ABOVE;1;2"))     // unknown kind
        assertNull(FiredLine.parse("FIRED;PRICE;a1;BTC;ABOVE;1"))      // price underflow
        assertNull(FiredLine.parse("FIRED;PRICE;a1;B TC;ABOVE;1;2"))   // bad symbol
        assertNull(FiredLine.parse("FIRED;PRICE;a1;BTC;SIDE;1;2"))     // bad direction
        assertNull(FiredLine.parse("FIRED;PRICE;a1;BTC;ABOVE;many;2")) // bad number
        assertNull(FiredLine.parse("FIRED;PRICE;a1;BTC;ABOVE;1;nan"))  // NaN price
        assertNull(FiredLine.parse("FIRED;GOAL;;Fund;ASSETS;1;2"))     // empty id
        assertNull(FiredLine.parse("FIRED;GOAL;g1;Fund;CRYPTO;1;2"))   // bad metric
        assertNull(FiredLine.parse("FIRED;PRICE;a1;BTC;ABOVE;1;2;3"))  // price overflow
        assertNull(FiredLine.parse("NW;1;2;3;4"))                      // not a FIRED line
    }
}
