package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.compose.ui.text.LinkAnnotation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import at.msd.friehs_bicha.cdcsvparser.R

/**
 * Parse/overview screen (Compose, P3.1). Metric cards + the monthly spend
 * line chart (MPAndroidChart via AndroidView, decision #4 - a custom Compose
 * chart only if everything works). All LiveData observation stays in the
 * fragment, which pushes plain values down.
 */
@Composable
fun ParseScreen(
    isParsing: Boolean,
    /**
     * Core map keys (the same ids as the old layout) to display values;
     * null = hidden, the special value "no internet connection" is
     * re-mapped to a friendly string.
     */
    values: Map<String, String?>,
    /** P/L value rendered in this color (already resolved for the sign). */
    profitLossColor: Color,
    /** Attribution footer stays up unless the core explicitly hid it (no crypto). */
    attributionVisible: Boolean,
    /** Raw "YYYY-MM;amount" series of the money spent over time. */
    spendSeries: List<String>,
    onFilterClick: () -> Unit,
    onAllTransactionsClick: () -> Unit,
) {
    val context = LocalContext.current
    // Same key expressions the data layer (CoreService/AppModel) writes with.
    val keyAssets = R.id.assets_valueP.toString()
    val keySpent = R.id.money_spent_value.toString()
    val keyPl = R.id.profit_loss_value.toString()
    val keyRewards = R.id.rewards_value.toString()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (isParsing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.parsing_text),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_savings_24,
                    tint = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.assets_value_label),
                    value = displayValue(context, values[keyAssets]),
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_spending_24,
                    tint = MaterialTheme.colorScheme.secondary,
                    label = stringResource(R.string.money_spent_label),
                    value = displayValue(context, values[keySpent]),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_trending_24,
                    tint = MaterialTheme.colorScheme.tertiary,
                    label = stringResource(R.string.profit_loss),
                    value = displayValue(context, values[keyPl]),
                    valueColor = if (values[keyPl] != null) profitLossColor else null,
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    iconRes = R.drawable.ic_rewards_24,
                    tint = MaterialTheme.colorScheme.tertiary,
                    label = stringResource(R.string.rewards_earned),
                    value = displayValue(context, values[keyRewards]),
                )
            }

            Spacer(Modifier.height(24.dp))

            FilledTonalButton(
                onClick = onFilterClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.filter_by_asset))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAllTransactionsClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.all_transactions))
            }

            if (spendSeries.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.spend_over_time),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                SpendLineChart(series = spendSeries)
            }

            if (attributionVisible) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = attributionAnnotatedString(context),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun displayValue(context: android.content.Context, raw: String?): String? {
    if (raw == null) return null
    return if (raw == "no internet connection") {
        context.getString(R.string.no_internet_connection)
    } else {
        raw
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    tint: Color,
    label: String,
    value: String?,
    valueColor: Color? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (value != null) {
                Text(
                    text = value,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Compose Color -> ARGB int for the legacy (non-Compose) chart API. */
private fun toArgbInt(c: Color): Int {
    fun channel(v: Float) = (v * 255f + 0.5f).toInt() and 0xFF
    return (-1 shl 24) or
        (channel(c.red) shl 16) or
        (channel(c.green) shl 8) or
        channel(c.blue)
}

/**
 * Builds the API-attribution text ("… CryptoCompare API") from the string
 * resource, turning its single <a href=...> link into a real Compose URL
 * annotation. The string shape is under our control (values/strings.xml).
 */
private fun attributionAnnotatedString(context: android.content.Context): AnnotatedString {
    val raw = context.getString(R.string.coinGeckoAttribution)
    val m = Regex("<a href=\"([^\"]+)\">([^<]*)</a>").find(raw)
        ?: return AnnotatedString(raw)
    val before = raw.substring(0, m.range.first)
    val linkText = m.groupValues[2]
    val after = raw.substring(m.range.last + 1)
    return buildAnnotatedString {
        append(before)
        withLink(LinkAnnotation.Url(m.groupValues[1])) { append(linkText) }
        append(after)
    }
}

/** MPAndroidChart line chart for the monthly money-spent series. */
@Composable
private fun SpendLineChart(series: List<String>) {
    val color = MaterialTheme.colorScheme.primary
    val data = remember(series, color) {
        val months = ArrayList<String>(series.size)
        val entries = ArrayList<Entry>(series.size)
        series.forEachIndexed { index, raw ->
            val parts = raw.split(";")
            if (parts.size != 2) return@forEachIndexed
            val value = parts[1].toDoubleOrNull() ?: return@forEachIndexed
            months.add(parts[0])
            entries.add(Entry(index.toFloat(), value.toFloat()))
        }
        Triple(months, entries, color)
    }
    if (data.second.isEmpty()) return

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                legend.isEnabled = false
                description.isEnabled = false
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                xAxis.labelCount = 8
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        val key = data.first.getOrNull(index) ?: return ""
                        // "YYYY-MM" -> e.g. "Mär 25" (the app locale applies)
                        return runCatching {
                            val parts = key.split("-").map { it.toInt() }
                            LocalDate.of(parts[0], parts[1], 1)
                                .format(DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()))
                        }.getOrDefault(key)
                    }
                }
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val argb = toArgbInt(data.third)
            val dataset = LineDataSet(data.second, "").apply {
                setDrawValues(false)
                lineWidth = 2f
                circleRadius = 3f
                setCircleColor(argb)
                this.color = argb
            }
            chart.data = LineData(dataset)
            chart.setTouchEnabled(true)
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    )
}
