package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.GoalLine
import at.msd.friehs_bicha.cdcsvparser.app.GoalMetric
import at.msd.friehs_bicha.cdcsvparser.app.GoalProgress
import at.msd.friehs_bicha.cdcsvparser.app.GoalProgressRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The thin JVM mirrors of the core's goal contracts: the G serde line
 * (build + parse, the escape rule, the metric vocabulary) and the GP
 * progress line of the snapshot (the sign rule, the insufficient marker,
 * the shape rejections - the math itself is core-side).
 */
class GoalLineTest {

    // ---------------------------------------------------------------------
    // The G line (the user's collection)
    // ---------------------------------------------------------------------

    @Test
    fun buildLineExactShape() {
        assertEquals(
            "G;u1;Bike;1000;-;ASSETS",
            GoalLine.buildLine("u1", "Bike", 1000.0, null, GoalMetric.Assets),
        )
        assertEquals(
            "G;u1;Bike;1000;2030-12-31;NET_WORTH",
            GoalLine.buildLine("u1", "Bike", 1000.0, LocalDate.parse("2030-12-31"), GoalMetric.NetWorth),
        )
    }

    @Test
    fun buildLineEscapesTheSemicolonInTheLabel() {
        assertEquals(
            "G;u1;Mort;;gage;500;-;ASSETS",
            GoalLine.buildLine("u1", "Mort;gage", 500.0, null, GoalMetric.Assets),
        )
    }

    @Test
    fun parseRoundTrip() {
        val line = "G;u2;First house;350000;2030-12-31;ASSETS"
        val g = GoalLine.parse(line)
        assertNotNull(g)
        assertEquals("u2", g!!.id)
        assertEquals("First house", g.label)
        assertEquals(350000.0, g.targetEur, 0.0)
        assertEquals(LocalDate.parse("2030-12-31"), g.targetDate)
        assertEquals(GoalMetric.Assets, g.metric)
        assertEquals(line, GoalLine.buildLine(g.id, g.label, g.targetEur, g.targetDate, g.metric))
    }

    @Test
    fun parseDegenerateTargetZero() {
        val g = GoalLine.parse("G;u3;Open goal;0;-;NET_WORTH")
        assertNotNull(g)
        assertEquals(0.0, g!!.targetEur, 0.0)
        assertNull(g.targetDate)
        assertEquals(GoalMetric.NetWorth, g.metric)
    }

    @Test
    fun parseRejectsContractDrift() {
        // Wrong kind, wrong field count, bad metric, negative target,
        // impossible date, bad id, empty label.
        assertNull(GoalLine.parse("L;u1;Bike;1000;-;ASSETS"))
        assertNull(GoalLine.parse("G;u1;Bike;1000;ASSETS"))
        assertNull(GoalLine.parse("G;u1;Bike;1000;2030-12-31;NET_WORTH;extra"))
        assertNull(GoalLine.parse("G;u1;Bike;1000;-;CRYPTO"))
        assertNull(GoalLine.parse("G;u1;Bike;-5;-;ASSETS"))
        assertNull(GoalLine.parse("G;u1;Bike;1000;2026-02-30;ASSETS"))
        assertNull(GoalLine.parse("G;u 1;Bike;1000;-;ASSETS"))
        assertNull(GoalLine.parse("G;u1;;1000;-;ASSETS"))
    }

    // ---------------------------------------------------------------------
    // The GP line (the core-computed progress of the snapshot)
    // ---------------------------------------------------------------------

    @Test
    fun progressParseReached() {
        val p = GoalProgress.parse("GP;p1;1234.56;1000;1.23456;reached")
        assertNotNull(p)
        assertEquals("p1", p!!.id)
        assertEquals(1234.56, p.currentEur, 0.0)
        assertEquals(1000.0, p.targetEur, 0.0)
        assertEquals(1.23456, p.progress!!, 1e-9)
        assertTrue(p.reached)
    }

    @Test
    fun progressParseUnreached() {
        val p = GoalProgress.parse("GP;p2;1234.56;2500;0.493824;-")
        assertNotNull(p)
        assertEquals(0.493824, p!!.progress!!, 1e-9)
        assertEquals(2500.0, p.targetEur, 0.0)
        assertEquals(false, p.reached)
    }

    @Test
    fun progressParseNegativeCurrentStaysSigned() {
        // The NET_WORTH metric reads the net field: debts above assets
        // keep it negative - the mirror accepts it, never clamps.
        val p = GoalProgress.parse("GP;p3;-93858.41925;100000;-0.9385841925;-")
        assertNotNull(p)
        assertEquals(-93858.41925, p!!.currentEur, 1e-9)
        assertEquals(-0.9385841925, p.progress!!, 1e-12)
        assertEquals(false, p.reached)
    }

    @Test
    fun progressParseDegenerateTargetCarriesTheMarker() {
        // The core never divides: progress = the insufficient marker
        // (null here), no crossing either.
        val p = GoalProgress.parse("GP;p4;1234.56;0;-;-")
        assertNotNull(p)
        assertNull(p!!.progress)
        assertEquals(0.0, p.targetEur, 0.0)
        assertEquals(false, p.reached)
    }

    @Test
    fun progressRejectsContractDrift() {
        assertNull(GoalProgress.parse("NW;0;0;0;0"))
        assertNull(GoalProgress.parse("GP;p1;1234.56;1000;1.23456"))
        assertNull(GoalProgress.parse("GP;p1;1234.56;1000;1.23456;reached;x"))
        assertNull(GoalProgress.parse("GP;p1;nan;1000;1;-"))
        assertNull(GoalProgress.parse("GP;p1;1234.56;1000;infinity;-"))
        assertNull(GoalProgress.parse("GP;p1;1234.56;-4;1;-"))
        assertNull(GoalProgress.parse("GP;p1;1234.56;1000;1;maybe"))
    }

    // ---------------------------------------------------------------------
    // The join (goal line + GP line -> one card row)
    // ---------------------------------------------------------------------

    @Test
    fun joinMatchesOnTheGoalIdAndKeepsTheStoredOrder() {
        val goals = listOf(
            GoalLine("a", "First", 1000.0, null, GoalMetric.NetWorth),
            GoalLine("b", "Second", 2500.0, LocalDate.parse("2030-12-31"), GoalMetric.Assets),
        )
        // Out of order on purpose: the join is on the id, not the index.
        val progress = listOf(
            GoalProgress("b", 1234.56, 2500.0, 0.493824, false),
            GoalProgress("a", 4455.86, 1000.0, 4.45586, true),
        )
        val rows = GoalProgressRow.from(goals, progress)
        assertEquals(2, rows.size)
        assertEquals("First", rows[0].label)   // stored goal order, not GP order
        assertEquals(4455.86, rows[0].currentEur, 0.0)
        assertTrue(rows[0].reached)
        assertEquals("Second", rows[1].label)
        assertEquals(LocalDate.parse("2030-12-31"), rows[1].targetDate)
        assertEquals(GoalMetric.Assets, rows[1].metric)
    }

    @Test
    fun joinSkipsAGoalWithoutAGoalProgressLine() {
        val goals = listOf(GoalLine("a", "First", 1000.0, null, GoalMetric.NetWorth))
        val rows = GoalProgressRow.from(goals, emptyList())
        assertTrue(rows.isEmpty())
        assertTrue(GoalProgressRow.from(emptyList(), emptyList()).isEmpty())
    }
}
