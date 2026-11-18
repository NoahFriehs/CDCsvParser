package at.msd.friehs_bicha.cdcsvparser.app.networth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The metric-line parse (the core's two-line `computeReturns` output ->
 * display state). The math is core-suite-pinned; this pins the LINE
 * FORMAT contract, incl. the marker forms (the honesty rule's data side):
 * a marked metric must land as nulls, never as a zero.
 */
class ReturnsMetricsTest {

    @Test
    fun parsesTheNumberedTwoLineResult() {
        val m = ReturnsMetrics.fromLines(
            listOf(
                "TWR;0.4400;0.2000;365;2",
                "MWR;0.4200;0.1900;730",
            ),
        )
        assertNotNull(m)
        assertEquals(0.44, m!!.twrAbsolute!!, 1e-9)
        assertEquals(0.20, m.twrAnnualized!!, 1e-9)
        assertEquals(365, m.twrPeriods)
        assertEquals(2, m.twrSkipped)
        assertEquals(0.42, m.mwrAbsolute!!, 1e-9)
        assertEquals(0.19, m.mwrAnnualized!!, 1e-9)
        assertEquals(730, m.spanDays)
    }

    @Test
    fun markedMetricsLandAsNullsWithTheirCounts() {
        // INSUFFICIENT forms: the TWR marker keeps the period/skip counts
        // (one field left of the numbered form); the MWR marker keeps the
        // span. The display renders the hint from the nulls.
        val m = ReturnsMetrics.fromLines(
            listOf(
                "TWR;INSUFFICIENT;0;3",
                "MWR;INSUFFICIENT;12",
            ),
        )
        assertNotNull(m)
        assertNull(m!!.twrAbsolute)
        assertNull(m.twrAnnualized)
        assertEquals(0, m.twrPeriods)
        assertEquals(3, m.twrSkipped)
        assertNull(m.mwrAbsolute)
        assertNull(m.mwrAnnualized)
        assertEquals(12, m.spanDays)

        // The "-" marker (a negative TWR product): the ABSOLUTE figure
        // stays, the annualized does not (no real 365-geometric).
        val m2 = ReturnsMetrics.fromLines(
            listOf(
                "TWR;-0.5000;-;100;0",
                "MWR;INSUFFICIENT;100",
            ),
        )
        assertNotNull(m2)
        assertEquals(-0.5, m2!!.twrAbsolute!!, 1e-9)
        assertNull(m2.twrAnnualized)
        assertEquals(100, m2.spanDays)
    }

    @Test
    fun emptyOrGarbageYieldsNull() {
        assertNull(ReturnsMetrics.fromLines(emptyList()))
        assertNull(ReturnsMetrics.fromLines(listOf("garbage;1;2")))
    }

    @Test
    fun negativeReturnsParse() {
        val m = ReturnsMetrics.fromLines(
            listOf(
                "TWR;-0.0500;-0.0490;30;0",
                "MWR;-0.0400;-0.0390;30",
            ),
        )
        assertNotNull(m)
        assertTrue(m!!.twrAbsolute!! < 0.0)
        assertTrue(m.mwrAbsolute!! < 0.0)
        assertFalse(m.spanDays < 30)
    }
}
