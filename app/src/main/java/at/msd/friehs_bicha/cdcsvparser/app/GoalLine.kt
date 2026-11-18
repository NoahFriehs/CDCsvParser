package at.msd.friehs_bicha.cdcsvparser.app

import java.time.LocalDate

/**
 * The core's metric vocabulary for goals (the C++ core OWNS the tokens
 * and validates them at parse - the app hardcodes the same two to build
 * lines and to drive the picker; no drift channel).
 */
enum class GoalMetric(val token: String) {
    NetWorth("NET_WORTH"),
    Assets("ASSETS"),
    ;

    companion object {
        fun fromToken(token: String): GoalMetric? =
            entries.firstOrNull { it.token == token }
    }
}

/**
 * ONE goal entry of the user-owned balance sheet, as one line of the
 * core's serde (the single source of the contract is the C++ core - this
 * class is the thin JVM mirror that builds and parses the line; the
 * progress math and the persistence are core-side).
 *
 * Line form (the kind prefix selects the collection, "G" = goal; there
 * is NO trailer - the progress lives in the snapshot, not the line):
 * `G;<id>;<label>;<targetEur>;<targetDate|->;<metric>`
 * A `;` inside a text field is escaped as `;;` (the single escape rule,
 * shared with [LiabilityLine]). [targetDate] is a reference point only:
 * the core shows it, never consumes it in the math.
 */
data class GoalLine(
    val id: String,
    val label: String,
    val targetEur: Double,
    val targetDate: LocalDate?,
    val metric: GoalMetric,
) {
    companion object {
        const val KIND = "G"
        private const val MAX_ID_LENGTH = 64
        private const val MAX_LABEL_LENGTH = 512

        /**
         * Builds the SET form of the line. The caller must already have
         * the values validated (the fields come from the dialog input).
         */
        fun buildLine(
            id: String,
            label: String,
            targetEur: Double,
            targetDate: LocalDate?,
            metric: GoalMetric,
        ): String = buildString {
            append(KIND).append(';')
            append(id).append(';')
            append(LiabilityLine.escapeField(label)).append(';')
            append(LiabilityLine.formatDecimal(targetEur)).append(';')
            append(targetDate?.toString() ?: "-").append(';')
            append(metric.token)
        }

        /**
         * Parses one core line into its fields. null = the line does not
         * follow the contract (the core never emits such a line, so a
         * null here is a contract drift, not an expected UI state).
         */
        fun parse(line: String): GoalLine? {
            val f = LiabilityLine.splitLine(line)
            if (f.size != 6) return null
            if (f[0] != KIND) return null
            val id = f[1]
            if (id.isEmpty() || id.length > MAX_ID_LENGTH || id.any { !it.isLetterOrDigit() && it != '-' }) return null
            val label = f[2]
            if (label.isEmpty() || label.length > MAX_LABEL_LENGTH) return null
            // The core rejects non-finite and negative targets; note the
            // "nan" token: toDoubleOrNull gives it back as NaN, so both
            // arms of the guard matter.
            val target = f[3].toDoubleOrNull() ?: return null
            if (!target.isFinite() || target < 0.0) return null
            val date = if (f[4] == "-") null else try {
                LocalDate.parse(f[4])   // ISO YYYY-MM-DD (the core validates the day)
            } catch (e: java.time.format.DateTimeParseException) {
                return null
            }
            val metric = GoalMetric.fromToken(f[5]) ?: return null
            return GoalLine(id, label, target, date, metric)
        }
    }
}

/**
 * ONE core-computed goal-progress line of the net-worth snapshot
 * contract (one per goal, AFTER the NW line):
 * `GP;<id>;<currentEur>;<targetEur>;<progress|->;<reached|->`
 * ALL the math is core-side: [currentEur] is the goal's METRIC value
 * read from the same snapshot pass (the net field or the tracked value,
 * SIGNED), progress = current / target, null when the core emits the
 * insufficient marker (a degenerate target <= 0: the core never
 * divides), and [reached] = progress >= 1. This class only parses - it
 * never re-computes a value the core already owns.
 */
data class GoalProgress(
    val id: String,
    val currentEur: Double,
    val targetEur: Double,
    /** null = the core's insufficient marker (a degenerate target). */
    val progress: Double?,
    val reached: Boolean,
) {
    companion object {
        const val KIND = "GP"
        private const val MAX_ID_LENGTH = 64

        fun parse(line: String): GoalProgress? {
            val f = LiabilityLine.splitLine(line)
            if (f.size != 6 || f[0] != KIND) return null
            val id = f[1]
            if (id.isEmpty() || id.length > MAX_ID_LENGTH || id.any { !it.isLetterOrDigit() && it != '-' }) return null
            // SIGNED: the metric's value can legitimately be negative (a
            // negative buffer, debts above assets). Non-finite input is
            // a contract drift: toDoubleOrNull gives NaN or null, both
            // arms guarded.
            val current = f[2].toDoubleOrNull() ?: return null
            if (!current.isFinite()) return null
            val target = f[3].toDoubleOrNull() ?: return null
            if (!target.isFinite() || target < 0.0) return null
            val progress = if (f[4] == "-") {
                null   // the insufficient marker (degenerate target)
            } else {
                val p = f[4].toDoubleOrNull() ?: return null
                if (!p.isFinite()) return null
                p
            }
            val reached = when (f[5]) {
                "reached" -> true
                "-" -> false
                else -> return null
            }
            return GoalProgress(id, current, target, progress, reached)
        }
    }
}

/**
 * ONE row of the net-worth screen's goals card: the user-facing
 * metadata (the goal line: label + target date + metric) joined with
 * the core-computed progress (the GP line of the same snapshot pass).
 * The join is on the goal id; the math stays core-side end to end.
 */
data class GoalProgressRow(
    val label: String,
    val metric: GoalMetric,
    val targetDate: LocalDate?,
    val currentEur: Double,
    val targetEur: Double,
    val progress: Double?,
    val reached: Boolean,
) {
    companion object {
        /**
         * Joins the goal lines (the user's collection, stored order)
         * with the core's GP progress lines by id. A goal WITHOUT a GP
         * line is skipped - the core emits one per goal, so its absence
         * is contract drift, not an expected state (a row would need a
         * fabricated figure to render).
         */
        fun from(
            goalLines: List<GoalLine>,
            progressLines: List<GoalProgress>,
        ): List<GoalProgressRow> {
            val byId = progressLines.associateBy { it.id }
            return goalLines.mapNotNull { goal ->
                val progress = byId[goal.id] ?: return@mapNotNull null
                GoalProgressRow(
                    label = goal.label,
                    metric = goal.metric,
                    targetDate = goal.targetDate,
                    currentEur = progress.currentEur,
                    targetEur = progress.targetEur,
                    progress = progress.progress,
                    reached = progress.reached,
                )
            }
        }
    }
}
