package at.msd.friehs_bicha.cdcsvparser.app

/**
 * The net-worth snapshot, as one line of the core's contract. The single
 * source of the contract is the C++ core (the snapshot method of its
 * balance sheet) - this class is the thin JVM mirror that parses the
 * line; the math (and the buffer-inclusion semantic: the tracked value
 * counts the internal EUR cash buffer) is core-side, so the display can
 * never drift from it.
 *
 * Line form:
 * `NW;<trackedAssetsEur>;<nonFinancialEur>;<liabilitiesEur>;<netWorthEur>`
 * (the core's 10-significant-digit decimal form; the tracked value and
 * the net may carry a minus sign - a negative EUR buffer or wallet makes
 * the tracked part negative, debts above assets keep the net negative).
 */
data class NetWorthSnapshot(
    /**
     * The core's own current EUR wallet value, internal EUR cash buffer
     * INCLUDED. SIGNED: the buffer (or a wallet) can run below zero.
     */
    val trackedAssetsEur: Double,
    /** The sum of the declared non-financial asset values. */
    val nonFinancialEur: Double,
    /** The sum of the core-computed current liability balances. */
    val liabilitiesEur: Double,
    /** tracked + nonFinancial - liabilities (signed, never clamped). */
    val netWorthEur: Double,
) {
    companion object {
        const val KIND = "NW"

        /**
         * Parses ONE snapshot line into its values. null = the line does
         * not follow the contract (the core never emits such a line, so a
         * null here is a contract drift, not an expected UI state).
         */
        fun parse(line: String): NetWorthSnapshot? {
            val f = LiabilityLine.splitLine(line)
            if (f.size != 5 || f[0] != KIND) return null
            // The core's %.10g never emits an exponent at these magnitudes,
            // but the contract is "a finite decimal" - guard anyway (the
            // "nan"/"infinity" tokens: toDoubleOrNull gives NaN or null,
            // so both arms of the guard matter).
            val tracked = f[1].toDoubleOrNull() ?: return null
            val nonFinancial = f[2].toDoubleOrNull() ?: return null
            val liabilities = f[3].toDoubleOrNull() ?: return null
            val net = f[4].toDoubleOrNull() ?: return null
            if (
                !tracked.isFinite() || !nonFinancial.isFinite() ||
                !liabilities.isFinite() || !net.isFinite()
            ) {
                return null
            }
            // The asset sum and the liability sum are sums of non-negative
            // values; the tracked value (a negative EUR buffer or wallet)
            // and the net are signed. (The identity net = tracked +
            // nonFinancial - liabilities is computed in the core -
            // re-checking it here would fight the line's 10-significant-digit
            // rounding.)
            if (nonFinancial < 0.0 || liabilities < 0.0) return null
            return NetWorthSnapshot(tracked, nonFinancial, liabilities, net)
        }
    }
}
