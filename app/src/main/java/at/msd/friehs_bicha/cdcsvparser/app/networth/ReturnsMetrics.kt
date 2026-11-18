package at.msd.friehs_bicha.cdcsvparser.app.networth

/**
 * The core's returns metric result, parsed thin for the Returns block.
 * The core's `computeReturns` owns the line format - and the honesty
 * rules: the core NEVER emits a percentage for an unqualified window
 * (span < 30 days, all periods skipped, an all-zero flow stream, or an
 * IRR without a bracketed root) - the `INSUFFICIENT` marker stays a
 * marker, the display renders the hint instead of inventing a number:
 *
 *   `TWR;<abs>;<ann>;<periods>;<skipped>`
 *   `TWR;INSUFFICIENT;<periods>;<skipped>`
 *   `MWR;<abs>;<ann>;<spandays>`
 *   `MWR;INSUFFICIENT;<spandays>`
 *
 * [twrAbsolute] / [mwrAbsolute] are FRACTIONS (0.21 = 21 %). A null
 * value = the metric is marked. [twrAnnualized] is additionally null for
 * the "-" marker (a negative TWR product has no real 365-geometric - the
 * absolute figure stays). [spanDays] / [twrSkipped] are the raw core
 * counts (the window label + the "days without valuation" caption).
 */
data class ReturnsMetrics(
    val twrAbsolute: Double?,
    val twrAnnualized: Double?,
    val twrPeriods: Int,
    val twrSkipped: Int,
    val mwrAbsolute: Double?,
    val mwrAnnualized: Double?,
    val spanDays: Int,
) {
    companion object {
        /** Parses the core's two metric lines; null when nothing valid
         * came back (empty input -> the core answers empty). */
        fun fromLines(lines: List<String>): ReturnsMetrics? {
            var twrAbs: Double? = null
            var twrAnn: Double? = null
            var periods = 0
            var skipped = 0
            var mwrAbs: Double? = null
            var mwrAnn: Double? = null
            var spanDays = 0
            var any = false
            for (line in lines) {
                val p = line.split(';', limit = 5)
                when (p.firstOrNull()) {
                    "TWR" -> {
                        any = true
                        if (p.getOrNull(1) == "INSUFFICIENT") {
                            // Marker form: the counts move one field left.
                            periods = p.getOrNull(2)?.toIntOrNull() ?: 0
                            skipped = p.getOrNull(3)?.toIntOrNull() ?: 0
                        } else {
                            twrAbs = p.getOrNull(1)?.toDoubleOrNull()
                            // The "-" marker (negative product) parses to
                            // null like the marker does: the absolute
                            // figure stays, the annualized does not.
                            twrAnn = p.getOrNull(2)?.toDoubleOrNull()
                            periods = p.getOrNull(3)?.toIntOrNull() ?: 0
                            skipped = p.getOrNull(4)?.toIntOrNull() ?: 0
                        }
                    }
                    "MWR" -> {
                        any = true
                        if (p.getOrNull(1) == "INSUFFICIENT") {
                            spanDays = p.getOrNull(2)?.toIntOrNull() ?: 0
                        } else {
                            mwrAbs = p.getOrNull(1)?.toDoubleOrNull()
                            mwrAnn = p.getOrNull(2)?.toDoubleOrNull()
                            spanDays = p.getOrNull(3)?.toIntOrNull() ?: 0
                        }
                    }
                }
            }
            if (!any) return null
            return ReturnsMetrics(twrAbs, twrAnn, periods, skipped, mwrAbs, mwrAnn, spanDays)
        }
    }
}
