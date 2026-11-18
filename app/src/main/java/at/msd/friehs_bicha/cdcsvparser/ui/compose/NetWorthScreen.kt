package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.GoalMetric
import at.msd.friehs_bicha.cdcsvparser.app.GoalProgressRow
import at.msd.friehs_bicha.cdcsvparser.app.NetWorthSnapshot
import at.msd.friehs_bicha.cdcsvparser.app.networth.ReturnsMetrics
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import java.time.LocalDate

/**
 * Net-worth screen (Compose surface): the core-computed headline figure,
 * the four breakdown lines (tracked assets / non-financial assets /
 * liabilities / net worth) and the goals card (one row per goal: the
 * core's GP progress line joined with the goal's own label/date/metric).
 * ALL the math is core-side (the lines come from the core's snapshot
 * contract, the buffer-inclusion semantic pinned there); this screen
 * renders the remembered values only - no computation, no persistence.
 */
@Composable
fun NetWorthScreen(
    /** The remembered snapshot, null while the core is not ready. */
    snapshot: NetWorthSnapshot?,
    /** The goals card rows (goal line + core GP progress line). */
    goalRows: List<GoalProgressRow>,
    /** The Returns block (the core's metric lines, parsed thin); null =
     * nothing to show yet (no series, or the core returned nothing). */
    returns: ReturnsMetrics?,
    /** The priced series' first day (the caption's "since" date); null
     * with [returns] null. */
    returnsFirstDay: LocalDate?,
    /** Tracked assets row tap: the existing per-wallet screen. */
    onTrackedAssetsClick: () -> Unit,
    /** Liabilities & assets row tap: the settings sections. */
    onSettingsClick: () -> Unit,
) {
    val title = stringResource(R.string.networth_title)
    val trackedLabel = stringResource(R.string.networth_row_tracked)
    val assetsLabel = stringResource(R.string.networth_row_assets)
    val liabilitiesLabel = stringResource(R.string.networth_row_liabilities)
    val netLabel = stringResource(R.string.networth_row_net)
    val emptyText = stringResource(R.string.networth_empty)
    val goalsTitle = stringResource(R.string.networth_goals_title)
    val returnsTitle = stringResource(R.string.networth_returns_title)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 32.dp),
            )
            Spacer(Modifier.height(16.dp))
            if (snapshot == null) {
                // The core has no live state yet (the not-ready window):
                // the screen itself is fine, there is just nothing to show.
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            // The headline: the single core-computed number, this screen's
            // reason to exist.
            Text(
                text = StringHelper.formatAmountToString(snapshot.netWorthEur),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (snapshot.netWorthEur < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(12.dp))
            Card(
                shape = HeroCardCorners,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    SnapshotRow(
                        label = trackedLabel,
                        amount = StringHelper.formatAmountToString(snapshot.trackedAssetsEur),
                        onClick = onTrackedAssetsClick,
                    )
                    SnapshotRow(
                        label = assetsLabel,
                        amount = StringHelper.formatAmountToString(snapshot.nonFinancialEur),
                        onClick = onSettingsClick,
                    )
                    SnapshotRow(
                        // Shown as a negative (a liability reduces the net
                        // worth); the row label says which side it sits on.
                        label = liabilitiesLabel,
                        amount = StringHelper.formatAmountToString(-snapshot.liabilitiesEur),
                        onClick = onSettingsClick,
                    )
                    SnapshotRow(
                        label = netLabel,
                        amount = StringHelper.formatAmountToString(snapshot.netWorthEur),
                        bold = true,
                        onClick = null,
                    )
                }
            }
            // The Returns block: hidden until the core has answered with
            // a metric result (same no-silent-figures rule - nothing to
            // show, no placeholder numbers).
            if (returns != null && returnsFirstDay != null) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = returnsTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                ReturnsBlock(returns = returns, firstDay = returnsFirstDay)
            }
            // The goals card: hidden until the user has a goal (no goals
            // = nothing to show, no empty-state noise on the headline
            // screen).
            if (goalRows.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = goalsTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = HeroCardCorners,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        goalRows.forEach { row ->
                            GoalRowEntry(row = row)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Returns block card: the two core-computed metric lines (TWR,
 * MWR/IRR - absolute + annualized where the core numbers it), the
 * honesty hint whenever a metric is marked INSUFFICIENT, and the window
 * caption ("since first record (date, N days)", gaining the skipped-day
 * note when the core reports unvalued periods). Everything rendered is
 * core output - the block never computes a figure of its own.
 */
@Composable
private fun ReturnsBlock(returns: ReturnsMetrics, firstDay: LocalDate) {
    val twrLabel = stringResource(R.string.networth_returns_twr)
    val irrLabel = stringResource(R.string.networth_returns_irr)
    val sinceFormat = stringResource(R.string.networth_returns_since)
    val noValuationFormat = stringResource(R.string.networth_returns_no_valuation)
    val insuffHint = stringResource(R.string.networth_returns_insufficient)
    val annSuffix = stringResource(R.string.networth_returns_annualized)

    // Fraction -> "21.00 %" (the house's amount format with the percent
    // symbol; the plus on gains is the display house rule for trends).
    fun percent(v: Double): String =
        StringHelper.formatAmountToString(v * 100.0, 2, "%", true)

    // One metric line: the absolute figure + the annualized one when the
    // core numbered it (the "-" marker / INSUFFICIENT both parse to a
    // null annualized); a null ABSOLUTE = the metric is marked and the
    // hint below takes over.
    fun metricText(absolute: Double?, annualized: Double?): String? {
        val abs = absolute ?: return null
        if (annualized == null) return percent(abs)
        return percent(abs) + " (" + percent(annualized) + " " + annSuffix + ")"
    }

    val caption = String.format(
        sinceFormat, firstDay.toString(), returns.spanDays,
    ) + if (returns.twrSkipped > 0) {
        " " + String.format(noValuationFormat, returns.twrSkipped)
    } else {
        ""
    }

    Card(
        shape = HeroCardCorners,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            MetricRow(label = twrLabel, value = metricText(returns.twrAbsolute, returns.twrAnnualized))
            MetricRow(label = irrLabel, value = metricText(returns.mwrAbsolute, returns.mwrAnnualized))
            // The honesty rule's display side: a marked metric (span < 30
            // days / no computable period / IRR without a root) gets the
            // hint instead of a number - the core never numbered it.
            if (returns.twrAbsolute == null || returns.mwrAbsolute == null) {
                Text(
                    text = insuffHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                )
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** One metric line: label left, value right. A null value (a marked
 * metric) leaves the side empty - the hint above the caption explains. */
@Composable
private fun MetricRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * ONE goals-card row: the label + the state line, the clamped progress
 * bar, and the metric + target-date caption. The state line renders the
 * CORE's GP line (the reached crossing / the remaining amount / the
 * insufficient marker for a degenerate target) - never an app-side
 * recomputation.
 */
@Composable
private fun GoalRowEntry(row: GoalProgressRow) {
    val toGoLabel = stringResource(R.string.networth_goal_to_go)
    val reachedLabel = stringResource(R.string.networth_goal_reached)
    val noTargetLabel = stringResource(R.string.networth_goal_no_target)
    val metricLabel = stringResource(goalMetricLabelRes(row.metric))
    val byDate = stringResource(R.string.goals_by_date)

    val stateText = when {
        row.reached -> reachedLabel
        row.progress != null ->
            String.format(toGoLabel, StringHelper.formatAmountToString(row.targetEur - row.currentEur))
        else -> noTargetLabel   // the core's insufficient marker (degenerate target)
    }
    // The bar is a DISPLAY clamp (0..1): the core's own value is
    // unclamped (it can sit below zero or above one), reached comes from
    // the core's crossing flag, not from the bar fraction.
    val fraction = (row.progress ?: 0.0).coerceIn(0.0, 1.0).toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stateText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.reached) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = metricLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            row.targetDate?.let {
                Text(
                    text = String.format(byDate, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The (en+de) string resource of a metric token - the display channel of
 * the core-vocabulary owner (same mapping as the settings section).
 */
private fun goalMetricLabelRes(metric: GoalMetric): Int =
    when (metric) {
        GoalMetric.NetWorth -> R.string.goal_metric_net_worth
        GoalMetric.Assets -> R.string.goal_metric_assets
    }

/**
 * One breakdown line: the label on the left, the amount on the right.
 * [onClick] = null makes the line inert (the net row: the headline above
 * is the figure, a second tap would go nowhere).
 */
@Composable
private fun SnapshotRow(
    label: String,
    amount: String,
    bold: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick).a11yTarget()
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
