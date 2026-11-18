package at.msd.friehs_bicha.cdcsvparser.app

/**
 * The core's direction vocabulary for price alerts (the C++ core OWNS
 * the tokens and validates them at parse - the app hardcodes the same
 * two to build lines and to drive the picker; no drift channel).
 */
enum class AlertDirection(val token: String) {
    Above("ABOVE"),
    Below("BELOW"),
    ;

    companion object {
        fun fromToken(token: String): AlertDirection? =
            entries.firstOrNull { it.token == token }
    }
}

/**
 * ONE price alert of the user-owned balance sheet, as ONE line of the
 * core's serde (the single source of the contract is the C++ core -
 * this class is the thin JVM mirror that builds and parses the line;
 * the crossing automaton, the state and the persistence are core-side).
 *
 * Line form (the kind prefix selects the collection, "AL" = alert):
 * `AL;<id>;<symbol>;<ABOVE|BELOW>;<thresholdEur>;<fired>;<firedISO|->`
 * The [fired] latch + fired stamp are CORE-owned state: the app builder
 * always emits `0;-` (creation or a user edit = a deliberate re-arm);
 * the parse reads whatever the core reports (fired alerts carry their
 * stamp, unfired ones "-").
 */
data class PriceAlertLine(
    val id: String,
    val symbol: String,
    val direction: AlertDirection,
    val thresholdEur: Double,
    val fired: Boolean,
    val firedIso: String?,
) {
    companion object {
        const val KIND = "AL"
        private const val MAX_ID_LENGTH = 64
        private const val MAX_SYMBOL_LENGTH = 16

        /**
         * Builds the SET form of the line in its fresh state (fired = 0,
         * no stamp). The caller must already have the values validated
         * (the fields come from the dialog input); the core stays the
         * validator of record.
         */
        fun buildLine(
            id: String,
            symbol: String,
            direction: AlertDirection,
            thresholdEur: Double,
        ): String = buildString {
            append(KIND).append(';')
            append(id).append(';')
            append(symbol).append(';')
            append(direction.token).append(';')
            append(LiabilityLine.formatDecimal(thresholdEur)).append(';')
            append("0;-")
        }

        /**
         * Parses one core line into its fields. null = the line does not
         * follow the contract (the core never emits such a line, so a
         * null here is a contract drift, not an expected UI state).
         */
        fun parse(line: String): PriceAlertLine? {
            val f = LiabilityLine.splitLine(line)
            if (f.size != 7) return null
            if (f[0] != KIND) return null
            val id = f[1]
            if (id.isEmpty() || id.length > MAX_ID_LENGTH || id.any { !it.isLetterOrDigit() && it != '-' }) return null
            val symbol = f[2]
            if (symbol.isEmpty() || symbol.length > MAX_SYMBOL_LENGTH || symbol.any { !it.isLetterOrDigit() }) return null
            val direction = AlertDirection.fromToken(f[3]) ?: return null
            // The core rejects non-finite and negative thresholds; note
            // the "nan" token: toDoubleOrNull gives it back as NaN, so
            // both arms of the guard matter.
            val threshold = f[4].toDoubleOrNull() ?: return null
            if (!threshold.isFinite() || threshold < 0.0) return null
            // The latch invariant BOTH ways (the core enforces the same):
            // fired alerts carry their stamp (a 19-char wall-clock ISO),
            // unfired ones carry "-".
            val fired = when (f[5]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val stamp = f[6]
            val firedIso: String? = if (fired) {
                if (stamp == "-" || stamp.length != 19) return null
                stamp
            } else {
                if (stamp != "-") return null
                null
            }
            return PriceAlertLine(id, symbol, direction, threshold, fired, firedIso)
        }
    }
}
