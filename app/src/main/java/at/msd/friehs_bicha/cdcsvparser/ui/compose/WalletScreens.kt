package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.ProfitTrend
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletRow
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.core.content.ContextCompat

/**
 * Wallet view screen (Compose, P3.1): allocation donut (MPAndroidChart via
 * AndroidView, decision #4), search + sort controls and the wallet list.
 * Search/sort state stays in WalletsViewModel; this file renders and
 * forwards input.
 */

// region donut

private val CHART_COLOR_RES = listOf(
    R.color.primary, R.color.secondary, R.color.tertiary,
    R.color.primary_container, R.color.secondary_container, R.color.tertiary_container,
    R.color.on_primary_container, R.color.on_secondary_container, R.color.on_tertiary_container,
    R.color.on_surface_variant,
)

/** Compose Color -> ARGB int for the legacy (non-Compose) chart API. */
private fun toArgbInt(c: Color): Int {
    fun channel(v: Float) = (v * 255f + 0.5f).toInt() and 0xFF
    return (-1 shl 24) or
        (channel(c.red) shl 16) or
        (channel(c.green) shl 8) or
        channel(c.blue)
}

/**
 * The allocation donut. Empty state is plain text, not the chart library
 * default (as before).
 */
@Composable
fun AllocationDonut(rows: List<WalletRow>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The donut shows the allocation of held assets; outside wallets are
    // bookkeeping balances and stay in the list below only.
    val sliceable = rows.filter { !it.isOutside && it.assetValue > 0 }
    if (sliceable.isEmpty()) {
        Text(
            text = stringResource(R.string.no_allocation_data),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        )
        return
    }
    val total = sliceable.sumOf { it.assetValue }
    // Entries (and the legend that follows their order) go by allocation:
    // biggest slice first.
    val entries = sliceable
        .sortedByDescending { it.assetValue }
        .map { PieEntry(it.assetValue.toFloat(), it.name) }
    val colors = CHART_COLOR_RES.map { ContextCompat.getColor(context, it) }

    // Plan 006 C: MPAndroidChart legend text defaults to a light-mode
    // color; theme it from the Compose scheme (same as the line chart).
    val legendColor = toArgbInt(MaterialTheme.colorScheme.onSurfaceVariant)
    AndroidView(
        factory = { ctx ->
            PieChart(ctx).apply {
                description.isEnabled = false
                setUsePercentValues(true)
                setDrawHoleEnabled(true)
                holeRadius = 62f
                // The library's default transparent-circle color is a white
                // halo - invisible/ugly on the dark surface.
                setTransparentCircleRadius(68f)
                setTransparentCircleColor(android.graphics.Color.TRANSPARENT)
                legend.verticalAlignment =
                    com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                legend.horizontalAlignment =
                    com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                legend.orientation =
                    com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                legend.setForm(com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE)
                legend.textColor = legendColor
                setRotationEnabled(false)
                setHighlightPerTapEnabled(false)
                setNoDataText("")
                setNoDataTextColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { chart ->
            val dataSet = PieDataSet(entries, "")
            dataSet.colors = colors.toMutableList()
            dataSet.valueTextSize = 11f
            // hide labels on small slices so they do not overlap
            dataSet.valueFormatter = object : ValueFormatter() {
                private val percent = PercentFormatter(chart)
                override fun getFormattedValue(value: Float): String {
                    val share = if (total > 0) value.toDouble() / total else 0.0
                    return if (share >= 0.03) percent.getFormattedValue(value) else ""
                }
            }
            chart.setDrawEntryLabels(false) // the legend names the slices
            val data = PieData(dataSet)
            data.setDrawValues(true)
            chart.data = data
            chart.setCenterText(if (total > 0) "%.0f €".format(total) else "")
            chart.setCenterTextSize(16f)
            chart.setCenterTextColor(ContextCompat.getColor(chart.context, R.color.on_surface))
            chart.invalidate()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp),
    )
}

// endregion

// region list

@Composable
fun WalletList(
    rows: List<WalletRow>,
    onWalletClick: (walletId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // MOD 2.6: rows stagger in during the initial settle only.
    val settleStartedAt = rememberListSettleStart()
    LazyColumn(modifier = modifier) {
        itemsIndexed(rows) { index, row ->
            StaggeredListAppear(index, settleStartedAt) {
                WalletRowView(row) { onWalletClick(row.walletId) }
            }
        }
    }
}

@Composable
private fun WalletRowView(row: WalletRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Stable per-currency hue so a dot keeps its color across re-sorts.
            val hue = (Math.abs(row.name.hashCode().toLong()).toDouble() / Int.MAX_VALUE) * 360.0
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.hsv(hue.toFloat(), 0.35f, 0.55f), CircleShape),
            )
            Text(
                text = row.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(row.amountText, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.eg), style = MaterialTheme.typography.bodyMedium)
            Text(row.assetValueText, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = row.percentText,
                color = when (row.trend) {
                    ProfitTrend.POSITIVE -> Color(0xFF1B7F3B) // trend_positive
                    ProfitTrend.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                    ProfitTrend.NEGATIVE -> Color(0xFFBA1A1A) // trend_negative
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = row.transactionCount.toString() + " " + stringResource(R.string.transaction_s),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// endregion

// region screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletViewScreen(
    rows: List<WalletRow>,
    allRows: List<WalletRow>,
    sortKey: Int,
    sortDirection: Int,
    onWalletClick: (walletId: Int) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSortKeySelected: (Int) -> Unit,
    onSortDirectionSelected: (Int) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var sortKeyExpanded by remember { mutableStateOf(false) }
    var sortDirExpanded by remember { mutableStateOf(false) }

    val sortValues = listOf(
        stringResource(R.string.sort_amount),
        stringResource(R.string.sort_amount_asset),
        stringResource(R.string.sort_percent),
        stringResource(R.string.sort_transactions),
    )
    val directions = listOf(
        stringResource(R.string.sort_desc),
        stringResource(R.string.sort_asc),
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            TextField(
                value = search,
                onValueChange = { search = it; onSearchChanged(it) },
                placeholder = { Text(stringResource(R.string.filter_coin_list)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Text(
                text = stringResource(R.string.sort_by),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExposedDropdownMenuBox(
                    expanded = sortKeyExpanded,
                    onExpandedChange = { sortKeyExpanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                    TextField(
                        value = sortValues[sortKey],
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortKeyExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = sortKeyExpanded,
                        onDismissRequest = { sortKeyExpanded = false },
                    ) {
                        sortValues.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (index != sortKey) onSortKeySelected(index)
                                    sortKeyExpanded = false
                                },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = sortDirExpanded,
                    onExpandedChange = { sortDirExpanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                    TextField(
                        value = directions[sortDirection],
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortDirExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = sortDirExpanded,
                        onDismissRequest = { sortDirExpanded = false },
                    ) {
                        directions.forEachIndexed { index, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (index != sortDirection) onSortDirectionSelected(index)
                                    sortDirExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.allocation_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            AllocationDonut(
                rows = allRows,
                modifier = Modifier.padding(top = 8.dp),
            )

            WalletList(
                rows = rows,
                onWalletClick = onWalletClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
            )
        }
    }
}

// endregion
