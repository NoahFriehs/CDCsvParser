package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.DeltaBasis
import at.msd.friehs_bicha.cdcsvparser.app.SummaryDelta
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import java.util.Locale

/**
 * Main screen (Compose). Import a CSV (new or from history) and jump
 * into the overview. The SAF upload flow, the history dialog and the
 * progress dialog stay in the fragment.
 */
@Composable
fun MainScreen(
    /**
     * The resolved summary hero state (headline + delta chip), built by
     * the pure point-in-time builder (null = no live core state yet:
     * the card stays hidden, never a placeholder number).
     */
    summary: SummaryDelta?,
    isSignedIn: Boolean,
    /** Display name of the newest history file, or null when there is none. */
    newestHistoryFile: String?,
    onUploadClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLoadFromDbClick: () -> Unit,
    onNetWorthClick: () -> Unit,
) {
    // #47: single header bar — the settings action lives in the
    // activity's action bar (view-scoped menu provider in MainFragment),
    // like on every other screen; the old Compose gear row doubled the
    // header.
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 32.dp),
            )

            // The summary card: the ONE core-computed headline on this
            // screen (the full breakdown lives on its destination) +
            // the point-in-time delta chip of the tracked series.
            if (summary != null) {
                val headline = summary.headlineEur
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = HeroCardCorners,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    onClick = onNetWorthClick,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        // The empty sheet cannot claim a net worth: the
                        // headline degrades to the tracked figure and the
                        // hint row offers the sheet's entry point.
                        Text(
                            text = stringResource(
                                if (summary.isNetWorth) {
                                    R.string.networth_title
                                } else {
                                    R.string.networth_row_tracked
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        // The hero number: the emphasized (hero) type layer.
                        Text(
                            text = StringHelper.formatAmountToString(headline),
                            style = HeroValueStyle,
                            color = if (headline < 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        DeltaChip(summary)
                        if (!summary.isNetWorth) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.summary_hint_networth),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            FilledTonalButton(
                onClick = onUploadClick,
                modifier = Modifier.fillMaxWidth().a11yTarget(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_upload_24),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp),
                )
                Text(stringResource(R.string.upload_file))
            }

            AnimatedVisibility(
                visible = newestHistoryFile != null,
                enter = Motion.sectionEnter(),
                exit = Motion.sectionExit(),
            ) {
                val historyFile = newestHistoryFile.orEmpty()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.history_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.fillMaxWidth().a11yTarget(),
                ) {
                    Text(historyFile)
                }
            }

            AnimatedVisibility(
                visible = isSignedIn,
                enter = Motion.sectionEnter(),
                exit = Motion.sectionExit(),
            ) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onLoadFromDbClick,
                    modifier = Modifier.fillMaxWidth().a11yTarget(),
                ) {
                    Text(stringResource(R.string.load_latest_from_database))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The summary delta chip: the tracked series' point-in-time change vs
 * the active basis, as a small tonal surface. Static on purpose (no
 * spring of its own - it inherits the theme's motion gate by living in
 * the themed surface). The VISIBLE text is minimal; the full value is
 * announced via the content description (the non-graphical data path:
 * the delta is announced, not painted). A missing previous point
 * renders "-" - never a fabricated 0.0 %.
 */
@Composable
private fun DeltaChip(summary: SummaryDelta) {
    val up = (summary.deltaEur ?: 0.0) > 0.0
    val down = (summary.deltaEur ?: 0.0) < 0.0
    val hasDelta = summary.deltaEur != null
    val basisLabel = stringResource(
        if (summary.basis == DeltaBasis.THIRTY_DAYS) {
            R.string.summary_delta_basis_30d
        } else {
            R.string.summary_delta_basis_day
        },
    )
    // The direction is carried by the arrow + the tint: the amount stays
    // a magnitude (no sign doubling), the percent an absolute value.
    val deltaEur = summary.deltaEur ?: 0.0
    val amountText = when {
        down -> StringHelper.formatAmountToString(-deltaEur)
        up -> StringHelper.formatAmountToString(deltaEur, 2, "€", writePlusIfPositive = true)
        else -> StringHelper.formatAmountToString(deltaEur)
    }
    // Locale captured once per composition (a per-app locale switch
    // recreates the activity, so the captured value stays truthful).
    val locale = remember { Locale.getDefault() }
    // Absolute percent (the arrow + the tint carry the direction - no
    // sign doubling in the visible chip or the announced sentence).
    val pctText = summary.deltaPercent?.let {
        String.format(locale, "%.1f", kotlin.math.abs(it))
    }

    // The announced sentence carries the whole value incl. the active
    // basis (the visible chip is a glance, the description is the data).
    val a11y = when {
        !hasDelta -> stringResource(R.string.summary_delta_missing_a11y, basisLabel)
        up && pctText != null ->
            stringResource(R.string.summary_delta_up_a11y, pctText, amountText, basisLabel)

        up -> stringResource(R.string.summary_delta_up_abs_a11y, amountText, basisLabel)
        down && pctText != null ->
            stringResource(R.string.summary_delta_down_a11y, pctText, amountText, basisLabel)

        down -> stringResource(R.string.summary_delta_down_abs_a11y, amountText, basisLabel)
        else -> stringResource(R.string.summary_delta_flat_a11y, amountText, basisLabel)
    }
    val arrow = when {
        !hasDelta -> "\u2013" // –
        up -> "\u25B2" // ▲
        down -> "\u25BC" // ▼
        else -> "\u2192" // →
    }
    // Missing state: only the dash + the basis (en-dash + the basis label).
    val visible = if (!hasDelta) {
        listOf("\u2013", basisLabel).joinToString(" ")
    } else {
        buildList {
            add(arrow)
            if (pctText != null) add("$pctText %")
            add(amountText)
            add(basisLabel)
        }.joinToString(" ")
    }

    // A flat exactly-zero change is neutral (the error tint would read
    // as a loss).
    val neutral = !hasDelta || summary.deltaEur == 0.0
    val container = when {
        neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        up -> chartPlColor().copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
    }
    val content = when {
        neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        up -> chartPlColor()
        else -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = visible,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier
                .background(container, RoundedCornerShape(999.dp))
                .semantics { contentDescription = a11y }
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}
