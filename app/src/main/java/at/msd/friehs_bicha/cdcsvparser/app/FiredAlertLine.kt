package at.msd.friehs_bicha.cdcsvparser.app

/**
 * ONE FIRED line of the core's alert pass - the ONLY part of the alert
 * check that crosses the C boundary (the state, the prices and the math
 * never do). The core decides, the app renders: this class only parses
 * the line, it never recomputes a value the core already owns.
 *
 * `FIRED;PRICE;<id>;<symbol>;<ABOVE|BELOW>;<thresholdEur>;<priceEur>`
 * `FIRED;GOAL;<id>;<label>;<metric>;<currentEur>;<targetEur>`
 * (the goal [Goal.label] is `;;`-escaped, the single shared escape
 * rule of the line format; both kinds carry exactly 7 fields.)
 */
sealed interface FiredLine {
    val id: String

    data class Price(
        override val id: String,
        val symbol: String,
        val direction: AlertDirection,
        val thresholdEur: Double,
        val priceEur: Double,
    ) : FiredLine

    data class Goal(
        override val id: String,
        val label: String,
        val metric: GoalMetric,
        /** SIGNED: the metric's value can legitimately be negative. */
        val currentEur: Double,
        val targetEur: Double,
    ) : FiredLine

    companion object {
        const val PREFIX = "FIRED"
        private const val KIND_PRICE = "PRICE"
        private const val KIND_GOAL = "GOAL"
        private const val MAX_ID_LENGTH = 64
        private const val MAX_SYMBOL_LENGTH = 16
        private const val MAX_LABEL_LENGTH = 512

        /**
         * Parses one core FIRED line. null = not a FIRED line, or the
         * shape violates the contract (the core never emits one - a
         * null here is a contract drift, not an expected state).
         */
        fun parse(line: String): FiredLine? {
            val f = LiabilityLine.splitLine(line)
            if (f.size != 7 || f[0] != PREFIX) return null
            val kind = f[1]
            if (kind != KIND_PRICE && kind != KIND_GOAL) return null
            val id = f[2]
            if (id.isEmpty() || id.length > MAX_ID_LENGTH || id.any { !it.isLetterOrDigit() && it != '-' }) return null
            if (kind == KIND_PRICE) {
                val symbol = f[3]
                if (symbol.isEmpty() || symbol.length > MAX_SYMBOL_LENGTH || symbol.any { !it.isLetterOrDigit() }) return null
                val direction = AlertDirection.fromToken(f[4]) ?: return null
                val threshold = parseFinite(f[5]) ?: return null
                val price = parseFinite(f[6]) ?: return null
                return Price(id, symbol, direction, threshold, price)
            }
            val label = f[3]
            if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return null
            val metric = GoalMetric.fromToken(f[4]) ?: return null
            val current = parseFinite(f[5]) ?: return null
            val target = parseFinite(f[6]) ?: return null
            return Goal(id, label, metric, current, target)
        }

        private fun parseFinite(token: String): Double? {
            val value = token.toDoubleOrNull() ?: return null
            return if (value.isFinite()) value else null
        }
    }
}
