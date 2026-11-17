package at.msd.friehs_bicha.cdcsvparser.app

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * The core's category vocabulary for non-financial assets (the C++ core
 * OWNS the tokens and validates them at parse - the app hardcodes the
 * same six to build lines and to drive the picker; no drift channel).
 */
enum class AssetCategory(val token: String) {
    RealEstate("REAL_ESTATE"),
    Vehicle("VEHICLE"),
    Art("ART"),
    Collectible("COLLECTIBLE"),
    Commodity("COMMODITY"),
    Other("OTHER"),
    ;

    companion object {
        fun fromToken(token: String): AssetCategory? =
            entries.firstOrNull { it.token == token }
    }
}

/**
 * ONE non-financial asset entry of the user-owned balance sheet, as one
 * line of the core's serde (the single source of the contract is the C++
 * core - this class is the thin JVM mirror that builds and parses the
 * line; the values, the categories and the persistence are core-side).
 *
 * Line form (the kind prefix selects the collection, "A" = asset; there
 * is NO trailer - nothing is core-computed for a static value):
 * `A;<id>;<label>;<category>;<valueEur>;<updatedISO>;<note|->`
 * A `;` inside a text field is escaped as `;;` (the single escape rule,
 * shared with [LiabilityLine]). [updated] is the user-declared
 * "last updated" wall-clock stamp (local time), the honesty signal of
 * how fresh the declared value is - the core stores it verbatim.
 */
data class NonFinancialAssetLine(
    val id: String,
    val label: String,
    val category: AssetCategory,
    val valueEur: Double,
    val updated: LocalDateTime,
    val note: String?,
) {
    companion object {
        const val KIND = "A"
        private const val MAX_ID_LENGTH = 64
        private const val MAX_LABEL_LENGTH = 512
        // STRICT + uuuu: the core validates the date as a real calendar
        // day (SMART would CLAMP 2026-02-30 to 02-28, the core rejects
        // it - the app's parse must be at least as strict).
        private val UPDATED_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
                .withResolverStyle(ResolverStyle.STRICT)

        /** The core's wall-clock form `YYYY-MM-DDTHH:MM:SS`. */
        fun formatUpdated(value: LocalDateTime): String =
            UPDATED_FORMATTER.format(value)

        /**
         * Builds the line (there is no set/list distinction for assets -
         * nothing is core-computed). The caller must already have the
         * values validated (the fields come from the dialog input).
         */
        fun buildLine(
            id: String,
            label: String,
            category: AssetCategory,
            valueEur: Double,
            updated: LocalDateTime,
            note: String?,
        ): String = buildString {
            append(KIND).append(';')
            append(id).append(';')
            append(LiabilityLine.escapeField(label)).append(';')
            append(category.token).append(';')
            append(LiabilityLine.formatDecimal(valueEur)).append(';')
            append(formatUpdated(updated)).append(';')
            append(if (note.isNullOrBlank()) "-" else LiabilityLine.escapeField(note))
        }

        /**
         * Parses one core line into its fields.
         * null = the line does not follow the contract (the core never
         * emits such a line, so a null here is a contract drift, not an
         * expected UI state).
         */
        fun parse(line: String): NonFinancialAssetLine? {
            val f = LiabilityLine.splitLine(line)
            if (f.size != 7) return null
            if (f[0] != KIND) return null
            val id = f[1]
            if (id.isEmpty() || id.length > MAX_ID_LENGTH || id.any { !it.isLetterOrDigit() && it != '-' }) return null
            val label = f[2]
            if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return null
            val category = AssetCategory.fromToken(f[3]) ?: return null
            val value = f[4].toDoubleOrNull() ?: return null
            // The core rejects non-finite and negative values; note the
            // "nan"/"infinity" tokens: Kotlin's toDoubleOrNull gives them
            // back as NaN or null, so both arms of the guard matter.
            if (!value.isFinite() || value < 0.0) return null
            val updated = try {
                LocalDateTime.parse(f[5], UPDATED_FORMATTER)
            } catch (e: DateTimeParseException) {
                return null
            }
            val note = if (f[6] == "-") null else f[6]
            if (note != null && note.length > MAX_LABEL_LENGTH) return null
            return NonFinancialAssetLine(id, label, category, value, updated, note)
        }
    }
}
