package at.msd.friehs_bicha.cdcsvparser.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG contrast math + the brand-pair pins: the tokens measured at the
 * accessibility pass must keep clearing the floors (4.5:1 body text,
 * 3:1 large text / non-graphical). A token change that breaks a pair
 * fails here before it reaches a device.
 */
class ContrastTest {

    @Test
    fun extremes() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.05)
        assertEquals(1.0, Contrast.ratio(0xFF808080.toInt(), 0xFF808080.toInt()), 0.001)
    }

    @Test
    fun symmetric() {
        val a = 0xFF5B21B6.toInt()
        val b = 0xFFFAF8FF.toInt()
        assertEquals(Contrast.ratio(a, b), Contrast.ratio(b, a), 0.0001)
    }

    @Test
    fun wcagReferencePair() {
        // #777777 on #FFFFFF is the canonical 4.48:1 example.
        assertEquals(4.48, Contrast.ratio(0xFF777777.toInt(), 0xFFFFFFFF.toInt()), 0.02)
    }

    @Test
    fun darkerForegroundRisesAgainstWhite() {
        val white = 0xFFFFFFFF.toInt()
        assertTrue(
            Contrast.ratio(0xFF555555.toInt(), white) > Contrast.ratio(0xFF777777.toInt(), white),
        )
    }

    // ---- Brand pair pins (measured, both themes) ----------------------

    // Light theme surfaces.
    @Test
    fun brandPairsLight() {
        val surface = 0xFFFAF8FF.toInt()
        val surfaceVariant = 0xFFE6E0EE.toInt()
        // Button / chip fills (onContainer on container)
        assertTrue(Contrast.passesText(0xFFFFFFFF.toInt(), 0xFF5B21B6.toInt())) // onPrimary 8.98
        assertTrue(Contrast.passesText(0xFFFFFFFF.toInt(), 0xFF0F766E.toInt())) // onSecondary 5.47
        assertTrue(Contrast.passesText(0xFFFFFFFF.toInt(), 0xFFB45309.toInt())) // onTertiary 5.02
        assertTrue(Contrast.passesText(0xFF27116B.toInt(), 0xFFE9E2FB.toInt())) // onPC/PC 12.14
        assertTrue(Contrast.passesText(0xFF0A4A41.toInt(), 0xFFCFF3EC.toInt())) // onSC/SC 8.55
        assertTrue(Contrast.passesText(0xFF3F2602.toInt(), 0xFFFDE8CB.toInt())) // onTC/TC 11.80
        // Text on surfaces
        assertTrue(Contrast.passesText(0xFF1C1726.toInt(), surface)) // onSurface 16.61
        assertTrue(Contrast.passesText(0xFF4B4557.toInt(), surfaceVariant)) // onSurfaceVariant 7.11
        assertTrue(Contrast.passesText(0xFF5B21B6.toInt(), surface)) // primary text 8.53
        assertTrue(Contrast.passesText(0xFF0F766E.toInt(), surface)) // secondary text 5.20
        assertTrue(Contrast.passesText(0xFFB45309.toInt(), surface)) // tertiary text 4.77
        assertTrue(Contrast.passesText(0xFFBA1A1A.toInt(), surface)) // error text 6.13
        assertTrue(Contrast.passesText(0xFF1B7F3B.toInt(), surface)) // trend positive 4.81
        // Non-graphical
        assertTrue(Contrast.passesLargeText(0xFF16A34A.toInt(), surface)) // chart green 3.13
    }

    // Dark theme surfaces.
    @Test
    fun brandPairsDark() {
        val surface = 0xFF121017.toInt()
        val surfaceVariant = 0xFF464053.toInt()
        assertTrue(Contrast.passesText(0xFF34126B.toInt(), 0xFFCBBDFB.toInt())) // onPrimary 8.45
        assertTrue(Contrast.passesText(0xFF05443B.toInt(), 0xFF7FDCCE.toInt())) // onSecondary 6.87
        assertTrue(Contrast.passesText(0xFF3F2602.toInt(), 0xFFF2BA6C.toInt())) // onTertiary 8.07
        assertTrue(Contrast.passesText(0xFFE9E0FF.toInt(), 0xFF433085.toInt())) // onPC/PC 8.28
        assertTrue(Contrast.passesText(0xFFC8F5EC.toInt(), 0xFF11554B.toInt())) // onSC/SC 7.31
        assertTrue(Contrast.passesText(0xFFFFE4B5.toInt(), 0xFF66460F.toInt())) // onTC/TC 6.95
        assertTrue(Contrast.passesText(0xFFE6E1EF.toInt(), surface)) // onSurface 14.73
        assertTrue(Contrast.passesText(0xFFC8C2D6.toInt(), surfaceVariant)) // onSurfaceVariant 5.73
        assertTrue(Contrast.passesText(0xFFCBBDFB.toInt(), surface)) // primary text 10.98
        assertTrue(Contrast.passesText(0xFF7FDCCE.toInt(), surface)) // secondary text 11.72
        assertTrue(Contrast.passesText(0xFFF2BA6C.toInt(), surface)) // tertiary text 10.80
        assertTrue(Contrast.passesText(0xFF7DD48A.toInt(), surface)) // trend positive 10.48
        // Non-graphical
        assertTrue(Contrast.passesLargeText(0xFF4ADE80.toInt(), surface)) // chart green 10.84
    }
}
