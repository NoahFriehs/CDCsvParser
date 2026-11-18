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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.NetWorthSnapshot
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper

/**
 * Net-worth screen (Compose surface): the core-computed headline figure
 * and the four breakdown lines (tracked assets / non-financial assets /
 * liabilities / net worth). ALL the math is core-side (the lines come
 * from the core's snapshot contract, the buffer-inclusion semantic
 * pinned there); this screen renders the remembered values only - no
 * computation, no persistence.
 */
@Composable
fun NetWorthScreen(
    /** The remembered snapshot, null while the core is not ready. */
    snapshot: NetWorthSnapshot?,
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
        }
    }
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
