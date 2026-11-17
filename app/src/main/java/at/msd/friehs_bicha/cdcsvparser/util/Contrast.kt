package at.msd.friehs_bicha.cdcsvparser.util

import kotlin.math.pow

/**
 * WCAG 2.x contrast math, kept PURE (no Android, no resource lookups) so the
 * brand-pair contrast checks run in the JVM unit-test lane and can be re-run
 * whenever a token changes.
 *
 * References:
 *  - Relative luminance: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 *  - Contrast ratio:     https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 */
object Contrast {

    /** WCAG threshold for body text (normal size). */
    const val MIN_TEXT_RATIO = 4.5

    /** WCAG threshold for large text AND non-text UI components (icons, borders, chart lines). */
    const val MIN_LARGE_TEXT_RATIO = 3.0

    /**
     * Relative luminance of a #RRGGBB / #AARRGGBB color. Alpha is ignored:
     * the ratio is defined over the color that is actually drawn, and the
     * brand tokens in this app are all opaque.
     */
    fun relativeLuminance(rgb: Int): Double {
        val r = ((rgb shr 16) and 0xFF) / 255.0
        val g = ((rgb shr 8) and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        fun lin(c: Double): Double =
            if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    /**
     * Contrast ratio between two opaque colors (1.0 .. 21.0), order-insensitive.
     * Both arguments are ARGB ints as produced by [Color.toArgb] / color resources.
     */
    fun ratio(fgArgb: Int, bgArgb: Int): Double {
        val a = relativeLuminance(fgArgb)
        val b = relativeLuminance(bgArgb)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** True when [fg] on [bg] clears the body-text (4.5:1) floor. */
    fun passesText(fgArgb: Int, bgArgb: Int): Boolean = ratio(fgArgb, bgArgb) >= MIN_TEXT_RATIO

    /** True when [fg] on [bg] clears the large-text / non-text (3:1) floor. */
    fun passesLargeText(fgArgb: Int, bgArgb: Int): Boolean =
        ratio(fgArgb, bgArgb) >= MIN_LARGE_TEXT_RATIO
}
