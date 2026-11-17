package at.msd.friehs_bicha.cdcsvparser.app

import java.time.LocalDate

/**
 * ONE liability entry of the user-owned balance sheet, as one line of the
 * core's serde (the single source of the contract is the C++ core - this
 * class is the thin JVM mirror that builds and parses the line; the math
 * and the persistence are core-side).
 *
 * Line form (the kind prefix selects the collection, "L" = liability):
 * `L;<id>;<label>;<principalEur>;<annualRatePct>;<start>;<end|->;<payment|->`
 * plus the core-computed trailer `;bal;<balanceEur>` on list lines (the
 * value is ignored when a line is set again). A `;` inside a text field
 * is escaped as `;;` (the single escape rule).
 */
data class LiabilityLine(
    val id: String,
    val label: String,
    val principalEur: Double,
    val annualRatePct: Double,
    val start: LocalDate,
    val end: LocalDate?,
    val monthlyPaymentEur: Double?,
    /** The core-computed current balance (list lines only). */
    val balanceEur: Double?,
) {
    companion object {
        const val KIND = "L"
        private const val MAX_ID_LENGTH = 64
        private const val MAX_LABEL_LENGTH = 512

        /** Escapes a text field for the line form (`;` -> `;;`). */
        fun escapeField(value: String): String =
            value.replace(";", ";;")

        /**
         * Splits ONE line on its UNESCAPED separators (a lone `;` ends the
         * field, a `;;` is a literal `;` inside it) - the inverse of
         * [escapeField].
         */
        fun splitLine(line: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0
            while (i < line.length) {
                if (line[i] == ';') {
                    if (i + 1 < line.length && line[i + 1] == ';') {
                        current.append(';')
                        i += 2
                        continue
                    }
                    fields.add(current.toString())
                    current.clear()
                } else {
                    current.append(line[i])
                }
                i++
            }
            fields.add(current.toString())
            return fields
        }

        /**
         * Builds the SET form of the line (no balance trailer - the core
         * recomputes it). The caller must already have the values
         * validated (the fields come from the dialog input).
         */
        fun buildLine(
            id: String,
            label: String,
            principalEur: Double,
            annualRatePct: Double,
            start: LocalDate,
            end: LocalDate?,
            monthlyPaymentEur: Double?,
        ): String = buildString {
            append(KIND).append(';')
            append(id).append(';')
            append(escapeField(label)).append(';')
            append(formatDecimal(principalEur)).append(';')
            append(formatDecimal(annualRatePct)).append(';')
            append(start).append(';')
            append(end?.toString() ?: "-").append(';')
            append(if (monthlyPaymentEur != null && monthlyPaymentEur > 0.0) formatDecimal(monthlyPaymentEur) else "-")
        }

        /**
         * Parses one core line (set or list form) into its fields.
         * null = the line does not follow the contract (the core never
         * emits such a line, so a null here is a contract drift, not an
         * expected UI state).
         */
        fun parse(line: String): LiabilityLine? {
            val f = splitLine(line)
            if (f.size != 8 && f.size != 10) return null
            if (f[0] != KIND) return null
            if (f.size == 10 && f[8] != "bal") return null
            val id = f[1]
            if (id.isEmpty() || id.length > MAX_ID_LENGTH || id.any { !it.isLetterOrDigit() && it != '-' }) return null
            val label = f[2]
            if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return null
            val principal = f[3].toDoubleOrNull() ?: return null
            val rate = f[4].toDoubleOrNull() ?: return null
            if (!principal.isFinite() || !rate.isFinite() || principal < 0.0 || rate < 0.0) return null
            val start = try {
                LocalDate.parse(f[5])   // ISO YYYY-MM-DD (the core validates the day)
            } catch (e: java.time.format.DateTimeParseException) {
                return null
            }
            val end = if (f[6] == "-") null else try {
                LocalDate.parse(f[6])
            } catch (e: java.time.format.DateTimeParseException) {
                return null
            }
            // The core rejects non-finite and negative values; note the
            // "nan"/"infinity" tokens: Kotlin's toDoubleOrNull gives them
            // back as NaN or null, so both arms of the guard matter.
            val payment = if (f[7] == "-") null else {
                val p = f[7].toDoubleOrNull() ?: return null
                if (!p.isFinite()) return null
                p
            }
            if (payment != null && payment <= 0.0) return null
            val balance = if (f.size == 10) {
                val b = f[9].toDoubleOrNull() ?: return null
                if (!b.isFinite()) return null
                b
            } else null
            return LiabilityLine(id, label, principal, rate, start, end, payment, balance)
        }

        /**
         * The 10-significant-digit form the core's C `%.10g` emits (compact,
         * re-parses exact). Java's %g PADS the precision with trailing zeros
         * that C strips, so they come off again here (not inside an
         * exponent part).
         */
        fun formatDecimal(value: Double): String {
            val v = if (value.isFinite() && value >= 0.0) value else 0.0
            val s = String.format(java.util.Locale.US, "%.10g", v)
            if (s.contains('e') || s.contains('E')) return s
            return s.trimEnd('0').trimEnd('.')
        }
    }
}
