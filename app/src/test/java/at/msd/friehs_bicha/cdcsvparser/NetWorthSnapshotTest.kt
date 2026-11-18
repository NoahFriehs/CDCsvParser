package at.msd.friehs_bicha.cdcsvparser

import at.msd.friehs_bicha.cdcsvparser.app.NetWorthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The thin JVM mirror of the core's net-worth snapshot contract: the
 * NW line parses into its four values, the sign rule (only the net may
 * be negative), and the shape rejections (the math itself is core-side).
 */
class NetWorthSnapshotTest {

    @Test
    fun parseFullLine() {
        val s = NetWorthSnapshot.parse("NW;1234.56;355000;104092.9792;252141.5808")
        assertNotNull(s)
        assertEquals(1234.56, s!!.trackedAssetsEur, 0.0)
        assertEquals(355000.0, s.nonFinancialEur, 0.0)
        assertEquals(104092.9792, s.liabilitiesEur, 1e-6)
        assertEquals(252141.5808, s.netWorthEur, 1e-6)
    }

    @Test
    fun parseZeroLine() {
        val s = NetWorthSnapshot.parse("NW;0;0;0;0")
        assertNotNull(s)
        assertEquals(0.0, s!!.netWorthEur, 0.0)
    }

    @Test
    fun parseNegativeNetStaysSigned() {
        val s = NetWorthSnapshot.parse("NW;0;500;2500;-2000")
        assertNotNull(s)
        assertEquals(-2000.0, s!!.netWorthEur, 0.0)
    }

    @Test
    fun rejectWrongKind() {
        assertNull(NetWorthSnapshot.parse("L;1234;355000;104092;2521"))
        assertNull(NetWorthSnapshot.parse("A;1;2;3;4;5"))
    }

    @Test
    fun rejectWrongFieldCount() {
        assertNull(NetWorthSnapshot.parse("NW;1;2;3"))
        assertNull(NetWorthSnapshot.parse("NW;1;2;3;4;5"))
    }

    @Test
    fun rejectBrokenNumbers() {
        assertNull(NetWorthSnapshot.parse("NW;1;2;3;abc"))
        assertNull(NetWorthSnapshot.parse("NW;1;2;3;nan"))
        assertNull(NetWorthSnapshot.parse("NW;1;2;3;"))
    }

    @Test
    fun rejectNegativeSumsButNotTheSignedFields() {
        // The asset sum and the liability sum are sums of non-negative
        // values; the tracked value (a negative EUR buffer or wallet) and
        // the net are signed.
        assertNull(NetWorthSnapshot.parse("NW;1;-2;3;0"))
        assertNull(NetWorthSnapshot.parse("NW;1;2;-3;0"))
        val s = NetWorthSnapshot.parse("NW;1;2;3;-99")
        assertNotNull(s)
        assertEquals(-99.0, s!!.netWorthEur, 0.0)
        val t = NetWorthSnapshot.parse("NW;-44.05;0;2500;-2544.05")
        assertNotNull(t)
        assertEquals(-44.05, t!!.trackedAssetsEur, 1e-9)
    }
}
