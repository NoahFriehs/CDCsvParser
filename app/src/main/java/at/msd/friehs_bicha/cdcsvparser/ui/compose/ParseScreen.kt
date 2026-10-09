package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.formatter.ValueFormatter
import androidx.compose.ui.text.LinkAnnotation
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.price.PriceHistoryProvider
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper.formatAmountToString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Parse/overview screen (Compose, P3.1). Metric cards + the chart panel
 * (G35: selectable series + time frame, data from the core's daily ledger
 * series) drawn with MPAndroidChart via AndroidView (decision #4 - a custom
 * Compose chart only if everything works). All LiveData observation stays in
 * the fragment, which pushes plain values down.
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
    /** Raw "series;YYYY-MM-DD;value" daily lines from the core (all 4 series). */
    dailySeries: List<String>,
    /** Raw "CUR;YYYY-MM-DD;balance;bonus" per-wallet daily lines (G36): the
     *  basis for historical (point-in-time) pricing of the stock series. */
    walletSeries: List<String>,
    /** Fetched daily EUR price history per currency (may arrive in parts). */
    history: Map<String, PriceHistoryProvider.DailyPrices>,
    /** (done, total) of the history fetch pass, or null before it starts. */
    historyProgress: Pair<Int, Int>?,
    /** Live prices (the ones the cards use) - pin the bucket that contains
     *  today so its point equals the card total. */
    currentPrices: Map<String, Double>,
    /** The asset cards show the offline placeholder. */
    noInternet: Boolean,
    onFilterClick: () -> Unit,
    onAllTransactionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    // Same key expressions the data layer (CoreService/AppModel) writes with.
    val keyAssets = R.id.assets_valueP.toString()
    val keySpent = R.id.money_spent_value.toString()
    val keyPl = R.id.profit_loss_value.toString()
    val keyRewards = R.id.rewards_value.toString()

    // 005: settings reach from the overview, same gear as the main screen.
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_24),
                        contentDescription = stringResource(R.string.settings),
                    )
                }
            }
        },
    ) { padding ->
        // 001: the content is only guaranteed to fit in portrait phone
        // height - in landscape / split / small windows it must scroll
        // instead of clipping the chart + buttons away forever.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // MOD 2.6: the bar fades out when the parse settles.
            AnimatedVisibility(
                visible = isParsing,
                enter = Motion.sectionEnter(),
                exit = Motion.sectionExit(),
            ) {
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

            // MOD 2.6: the panel reveals after the data posts.
            AnimatedVisibility(
                visible = dailySeries.isNotEmpty(),
                enter = Motion.sectionEnter(),
                exit = Motion.sectionExit(),
            ) {
                Spacer(Modifier.height(16.dp))
                ChartPanel(
                    dailySeries = dailySeries,
                    walletSeries = walletSeries,
                    history = history,
                    historyProgress = historyProgress,
                    currentPrices = currentPrices,
                    noInternet = noInternet,
                )
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
 * Builds the API-attribution text from the string resource, turning every
 * `<a href=...>` link into a real Compose URL annotation. The string shape
 * is under our control (values/strings.xml).
 */
private fun attributionAnnotatedString(context: android.content.Context): AnnotatedString {
    val raw = context.getString(R.string.priceAttribution)
    val matches = Regex("<a href=\"([^\"]+)\">([^<]*)</a>").findAll(raw).toList()
    if (matches.isEmpty()) return AnnotatedString(raw)
    return buildAnnotatedString {
        var pos = 0
        for (m in matches) {
            append(raw.substring(pos, m.range.first))
            withLink(LinkAnnotation.Url(m.groupValues[1])) { append(m.groupValues[2]) }
            pos = m.range.last + 1
        }
        append(raw.substring(pos))
    }
}

/**
 * The chart panel (G35, historical pricing G36, plan 006): series picker +
 * time-frame picker + range picker (presets and a custom date range, all
 * M3) and a selection tooltip above the chart. The spent series buckets the
 * core's daily flow; the stock series (value / P/L / rewards) value every
 * bucket with the prices valid at its end date (see
 * [historicalStockSeries]), using the fetched daily price history plus the
 * live prices for today's bucket. Selection state is local UI state;
 * switching is network-free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartPanel(
    dailySeries: List<String>,
    walletSeries: List<String>,
    history: Map<String, PriceHistoryProvider.DailyPrices>,
    historyProgress: Pair<Int, Int>?,
    currentPrices: Map<String, Double>,
    noInternet: Boolean,
) {
    val seriesData = remember(dailySeries) { parseDailySeries(dailySeries) }
    val walletData = remember(walletSeries) { parseWalletSeries(walletSeries) }
    var selectedSeries by remember { mutableStateOf(ChartSeries.SPENT) }
    var timeFrame by remember { mutableStateOf(TimeFrame.MONTH) }
    var range by remember { mutableStateOf(ChartRange.ALL) }
    var customRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    /** The touched bucket (plan 006 tooltip); null = nothing selected. */
    var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }
    var showRangeDialog by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val priceSeriesMap = remember(history) {
        history.mapValues { PriceSeries(it.value.points) }
    }
    val historical = remember(walletData, priceSeriesMap, seriesData, timeFrame, currentPrices) {
        historicalStockSeries(
            walletSeries = walletData,
            prices = priceSeriesMap,
            spentPoints = seriesData[ChartSeries.SPENT].orEmpty(),
            frame = timeFrame,
            today = today,
            currentPrices = currentPrices,
        )
    }
    val fullPoints = remember(seriesData, historical, selectedSeries, timeFrame) {
        if (selectedSeries.isFlow) {
            bucketize(seriesData[selectedSeries].orEmpty(), timeFrame, isFlow = true)
        } else {
            when (selectedSeries) {
                ChartSeries.VALUE -> historical.value
                ChartSeries.PL -> historical.pl
                ChartSeries.BONUS -> historical.bonus

                else -> emptyList()
            }
        }
    }
    // Plan 006 B: the visible span. The custom range (when active) wins
    // over the preset row; presets are start-only limits.
    val rangeStart: LocalDate?
    val rangeEnd: LocalDate?
    // Delegated state: no smart cast - copy to a local first.
    val custom = customRange
    if (custom != null) {
        rangeStart = custom.first
        rangeEnd = custom.second
    } else {
        rangeStart = range.startDate(today)
        rangeEnd = null
    }
    val (points, estimatedCount) = remember(
        fullPoints, timeFrame, rangeStart, rangeEnd, selectedSeries,
    ) {
        limitRange(
            fullPoints,
            if (selectedSeries.isFlow) 0 else historical.estimatedCount,
            timeFrame,
            rangeStart,
            rangeEnd,
        )
    }
    val historyIncomplete = !selectedSeries.isFlow &&
        historyProgress != null &&
        historyProgress.first < historyProgress.second

    // Plan 006 C: theme the chart explicitly - MPAndroidChart's renderers
    // hard-code light-mode colors (black axis labels), unreadable on the
    // dark surface.
    val lineColor = toArgbInt(MaterialTheme.colorScheme.primary)
    val labelColor = toArgbInt(MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = toArgbInt(MaterialTheme.colorScheme.surfaceVariant)

    // The custom range never goes before the first activity day.
    val firstActivity = remember(seriesData, walletData) {
        (walletData.values.flatten().map { it.date } +
            seriesData.values.flatten().map { it.date }).minOrNull() ?: today
    }

    Column {
        Text(
            text = stringResource(R.string.overview_charts),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChartSeries.entries.forEachIndexed { index, series ->
                SegmentedButton(
                    selected = selectedSeries == series,
                    onClick = {
                        selectedSeries = series
                        selectedPoint = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, ChartSeries.entries.size),
                ) {
                    Text(stringResource(series.labelRes))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TimeFrame.entries.forEachIndexed { index, frame ->
                SegmentedButton(
                    selected = timeFrame == frame,
                    onClick = {
                        timeFrame = frame
                        selectedPoint = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, TimeFrame.entries.size),
                ) {
                    Text(stringResource(frame.labelRes))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChartRange.entries.forEachIndexed { index, r ->
                SegmentedButton(
                    selected = customRange == null && range == r,
                    onClick = {
                        range = r
                        customRange = null
                        selectedPoint = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, ChartRange.entries.size),
                ) {
                    Text(stringResource(r.labelRes))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { showRangeDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (customRange != null) {
                    stringResource(
                        R.string.range_active,
                        shortDate(customRange!!.first),
                        shortDate(customRange!!.second),
                    )
                } else {
                    stringResource(R.string.range_custom)
                },
            )
        }
        if (historyIncomplete) {
            val done = historyProgress!!.first
            val total = historyProgress!!.second
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.chart_history_loading, done, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            // Offline, and no price history to value the stock series from:
            // nothing to show (a cached history is still drawable offline).
            noInternet && !selectedSeries.isFlow && history.isEmpty() -> Text(
                text = stringResource(R.string.no_internet_connection),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(vertical = 48.dp),
            )

            points.isEmpty() -> Text(
                text = stringResource(R.string.chart_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(vertical = 48.dp),
            )

            else -> {
                // Plan 006 A: the tooltip is a caption row ABOVE the chart
                // (fixed height, so the chart never jumps while it shows).
                Box(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                    selectedPoint?.let { point ->
                        Text(
                            text = "${bucketLabel(point.key, timeFrame)}  ·  ${formatAmountToString(point.value)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                }
                ChartLine(
                    points = points,
                    timeFrame = timeFrame,
                    estimatedCount = estimatedCount,
                    lineColor = lineColor,
                    labelColor = labelColor,
                    gridColor = gridColor,
                    onPointSelected = { selectedPoint = it },
                )
                if (estimatedCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.chart_history_estimated_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showRangeDialog) {
        ChartRangeDialog(
            today = today,
            minDate = firstActivity,
            initial = customRange,
            onDismiss = { showRangeDialog = false },
            onApply = { applied ->
                customRange = applied
                selectedPoint = null
                showRangeDialog = false
            },
        )
    }
}

/** String resource for a series button (P/L reuses the card label). */
private val ChartSeries.labelRes: Int
    get() = when (this) {
        ChartSeries.SPENT -> R.string.series_spent
        ChartSeries.VALUE -> R.string.series_portfolio_value
        ChartSeries.PL -> R.string.profit_loss
        ChartSeries.BONUS -> R.string.series_bonus
    }

private val TimeFrame.labelRes: Int
    get() = when (this) {
        TimeFrame.WEEK -> R.string.timeframe_week
        TimeFrame.MONTH -> R.string.timeframe_month
        TimeFrame.QUARTER -> R.string.timeframe_quarter
        TimeFrame.YEAR -> R.string.timeframe_year
    }

private val ChartRange.labelRes: Int
    get() = when (this) {
        ChartRange.ALL -> R.string.range_all
        ChartRange.THREE_MONTHS -> R.string.range_three_months
        ChartRange.SIX_MONTHS -> R.string.range_six_months
        ChartRange.ONE_YEAR -> R.string.range_one_year
        ChartRange.TWO_YEARS -> R.string.range_two_years
        ChartRange.FIVE_YEARS -> R.string.range_five_years
    }

/** Device-locale short date (range button + active-range label). */
private fun shortDate(date: LocalDate): String =
    date.format(
        java.time.format.DateTimeFormatter.ofLocalizedDate(
            java.time.format.FormatStyle.SHORT,
        ),
    )

/** At most this many buckets draw a per-point circle (plan 006 A+): on a
 *  dense series the circles are pure clutter - the line and the selection
 *  highlight carry the reading. */
private const val MAX_CIRCLE_BUCKETS = 24

/**
 * MPAndroidChart line chart for one bucketed series (plan 006: themed,
 * selection callback, sparse circles). The first [estimatedCount] points
 * (leading, chronological) are priced by first-price back-fill rather than
 * real history and are drawn dashed in a faded shade; the rest solid.
 *
 * Data + theme + listener live in [applyChartState] and are applied from
 * BOTH `factory` and `update` (see there) - plan 001.
 */
@Composable
private fun ChartLine(
    points: List<ChartPoint>,
    timeFrame: TimeFrame,
    estimatedCount: Int,
    lineColor: Int,
    labelColor: Int,
    gridColor: Int,
    onPointSelected: (ChartPoint?) -> Unit,
) {
    // Rendering signature: the apply happens (re)only when the data or the
    // theme actually changed. A selection-only change (the tooltip state)
    // must NOT reset the chart, or the fresh highlight would be cleared in
    // the very recomposition it triggered.
    // Cheap to rebuild on every composition: it only guards the (costly)
    // chart re-apply below.
    val dataSignature = System.identityHashCode(points).toString() +
        "|" + estimatedCount + "|" + timeFrame +
        "|" + lineColor + "|" + labelColor + "|" + gridColor

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                applyChartState(
                    points, timeFrame, estimatedCount, lineColor, labelColor, gridColor, onPointSelected,
                )
                setTag(dataSignature)
            }
        },
        update = { chart ->
            if (chart.getTag() != dataSignature) {
                chart.applyChartState(
                    points, timeFrame, estimatedCount, lineColor, labelColor, gridColor, onPointSelected,
                )
                chart.setTag(dataSignature)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    )
}

/**
 * Data + theme + selection handling for the line chart. Called from BOTH
 * the `factory` and the `update` block (plan 001): on rotation / resize the
 * AndroidView is recreated and only `factory` runs - if the dataset lived
 * only in `update`, a fresh chart could stay empty forever (the reported
 * "messed up forever" bug). The x-axis label formatter is (re)assigned here
 * too, so it always resolves labels through the CURRENT points list.
 *
 * MPAndroidChart's renderers hard-code light-mode colors (the axis labels
 * are black in their constructors, plan 006 C) - every visible element is
 * themed explicitly from the Compose scheme.
 */
private fun LineChart.applyChartState(
    points: List<ChartPoint>,
    timeFrame: TimeFrame,
    estimatedCount: Int,
    lineColor: Int,
    labelColor: Int,
    gridColor: Int,
    onPointSelected: (ChartPoint?) -> Unit,
) {
    legend.isEnabled = false
    description.isEnabled = false
    axisRight.isEnabled = false
    setNoDataTextColor(labelColor)
    xAxis.setDrawGridLines(false)
    xAxis.granularity = 1f
    xAxis.labelCount = 8
    xAxis.axisLineColor = gridColor
    xAxis.textColor = labelColor
    axisLeft.axisLineColor = gridColor
    axisLeft.gridColor = gridColor
    axisLeft.textColor = labelColor
    xAxis.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            // The chart may ask beyond the data range when zoomed.
            val point = points.getOrNull(value.toInt()) ?: return ""
            return bucketLabel(point.key, timeFrame)
        }
    }

    val estimated = points.take(estimatedCount)
    val real = points.drop(estimatedCount)
    val sets = ArrayList<LineDataSet>(2)
    if (estimated.isNotEmpty()) {
        sets.add(LineDataSet(entriesOf(estimated, offset = 0), "").apply {
            enableDashedLine(6f, 4f, 0f)
            lineWidth = 1.5f
            setDrawValues(false)
            setDrawCircles(false)
            color = ColorTemplate.colorWithAlpha(140, lineColor)
        })
    }
    if (real.isNotEmpty()) {
        sets.add(LineDataSet(entriesOf(real, offset = estimated.size), "").apply {
            setDrawValues(false)
            lineWidth = 2f
            if (real.size <= MAX_CIRCLE_BUCKETS) {
                circleRadius = 3f
                setCircleColor(lineColor)
                setDrawCircles(true)
            } else {
                setDrawCircles(false)
            }
            this.color = lineColor
        })
    }
    data = LineData(*sets.toTypedArray())

    // Selection -> the Compose tooltip above the chart (plan 006 A). The
    // listener is (re)assigned on every apply so it always captures the
    // CURRENT points list. (v3.1.0 touches highlight by default - the
    // touch-enabled flag below is the only gate needed.)
    setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
        override fun onValueSelected(entry: Entry, highlight: Highlight) {
            onPointSelected(points.getOrNull(entry.x.toInt()))
        }

        override fun onNothingSelected() {
            onPointSelected(null)
        }
    })
    setTouchEnabled(true)
    invalidate()
}

/**
 * Plan 006 B: the custom date range. Two M3 date pickers (start, end);
 * nothing before the first activity day or after today is pickable, and
 * end is clamped to >= start.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartRangeDialog(
    today: LocalDate,
    minDate: LocalDate,
    initial: Pair<LocalDate, LocalDate>?,
    onDismiss: () -> Unit,
    onApply: (Pair<LocalDate, LocalDate>) -> Unit,
) {
    var start by remember { mutableStateOf(initial?.first ?: minDate) }
    var end by remember { mutableStateOf(initial?.second ?: today) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.range_custom)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { pickingStart = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.range_start))
                    Spacer(Modifier.width(8.dp))
                    Text(shortDate(start))
                }
                OutlinedButton(
                    onClick = { pickingEnd = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.range_end))
                    Spacer(Modifier.width(8.dp))
                    Text(shortDate(end))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(start to end.coerceAtLeast(start)) }) {
                Text(stringResource(R.string.range_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.range_cancel)) }
        },
    )

    if (pickingStart) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = utcDateMillis(start),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcDateOf(utcTimeMillis) in minDate..today
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        start = utcDateOf(it)
                        if (end < start) end = start
                    }
                    pickingStart = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false }) {
                    Text(stringResource(R.string.range_cancel))
                }
            },
        ) { DatePicker(state = state) }
    }

    if (pickingEnd) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = utcDateMillis(end),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcDateOf(utcTimeMillis) in start..today
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { end = utcDateOf(it) }
                    pickingEnd = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingEnd = false }) {
                    Text(stringResource(R.string.range_cancel))
                }
            },
        ) { DatePicker(state = state) }
    }
}

/** M3 DatePicker dates are UTC-midnight epoch millis. */
private fun utcDateMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** Entries with GLOBAL x positions (index-in-[points] + [offset]) so the
 *  dashed/solid split keeps the x-axis and the label formatter aligned. */
private fun entriesOf(points: List<ChartPoint>, offset: Int): ArrayList<Entry> =
    ArrayList<Entry>(points.size).apply {
        points.forEachIndexed { index, point ->
            add(Entry((index + offset).toFloat(), point.value.toFloat()))
        }
    }
