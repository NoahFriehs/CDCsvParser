package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.GoalLine
import at.msd.friehs_bicha.cdcsvparser.app.GoalMetric
import at.msd.friehs_bicha.cdcsvparser.app.LiabilityLine
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/** The dialog state: [initial] = null is an add, a parsed row is an edit. */
data class GoalDialogState(val initial: GoalLine?)

/**
 * The balance-sheet GOALS section of the settings screen: the
 * user-maintained targets are CORE-owned (the serde line is the
 * contract, the core computes the progress on the net-worth snapshot
 * and persists the sheet in its own save - the app builds and renders
 * lines only).
 */
@Composable
fun SettingsGoalsSection() {
    val scope = rememberCoroutineScope()

    // Hoisted before any coroutine lambda (stringResource is
    // composable-only).
    val title = stringResource(R.string.goals_section)
    val help = stringResource(R.string.goals_help)
    val addLabel = stringResource(R.string.goals_add)
    val emptyText = stringResource(R.string.goals_empty)
    val editLabel = stringResource(R.string.goals_edit)
    val deleteLabel = stringResource(R.string.goals_delete)

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var dialog by remember { mutableStateOf<GoalDialogState?>(null) }
    var coreRejected by remember { mutableStateOf(false) }

    val rows = remember(lines) { lines.mapNotNull { GoalLine.parse(it) } }

    LaunchedEffect(Unit) {
        // The sheet read hops the cpp-core thread (a blocking join):
        // keep it off the UI thread.
        lines = withContext(Dispatchers.IO) { CoreService.sheetLines(GoalLine.KIND) }
    }

    suspend fun reload() {
        lines = withContext(Dispatchers.IO) { CoreService.sheetLines(GoalLine.KIND) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = { dialog = GoalDialogState(null) },
                modifier = Modifier.a11yTarget(),
            ) {
                Text(addLabel)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rows.forEach { row ->
                GoalEntryRow(
                    row = row,
                    editLabel = editLabel,
                    deleteLabel = deleteLabel,
                    onEdit = { dialog = GoalDialogState(row) },
                    onDelete = {
                        scope.launch {
                            // Delete = core upsert + auto-save (a full state
                            // file write): the blocking hop stays off the UI.
                            val ok = withContext(Dispatchers.IO) {
                                CoreService.sheetDeleteEntry(GoalLine.KIND + row.id)
                            }
                            if (ok) reload()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    val openDialog = dialog ?: return
    GoalDialog(
        state = openDialog,
        coreRejected = coreRejected,
        onCoreRejectedChange = { coreRejected = it },
        onDismiss = {
            dialog = null
            coreRejected = false
        },
        onSave = { line: String ->
            scope.launch {
                // The core hop blocks on the caller (upsert + auto-save,
                // a full state file write): run it off the UI thread.
                val ok = withContext(Dispatchers.IO) { CoreService.sheetUpsert(line) }
                if (ok) {
                    dialog = null
                    coreRejected = false
                    reload()
                } else {
                    // The core validator rejected the line: keep the dialog
                    // open with the input intact and flag it.
                    coreRejected = true
                }
            }
        },
    )
}

@Composable
private fun GoalEntryRow(
    row: GoalLine,
    editLabel: String,
    deleteLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val byDate = stringResource(R.string.goals_by_date)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = StringHelper.formatAmountToString(row.targetEur),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val detail = buildString {
            append(stringResource(goalMetricLabelRes(row.metric)))
            row.targetDate?.let {
                append(" \u00B7 ")
                append(String.format(byDate, it))
            }
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit, modifier = Modifier.a11yTarget()) {
                Text(editLabel)
            }
            TextButton(onClick = onDelete, modifier = Modifier.a11yTarget()) {
                Text(deleteLabel)
            }
        }
    }
}

/**
 * The add/edit dialog. Field validation is a thin app-side pre-check of
 * the same contract the core enforces (the core stays the source of
 * truth: a line the pre-check passes but the core rejects is reported
 * without losing the input).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDialog(
    state: GoalDialogState,
    coreRejected: Boolean,
    onCoreRejectedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val labelPlaceholder = stringResource(R.string.goals_field_label)
    val targetPlaceholder = stringResource(R.string.goals_field_target)
    val datePlaceholder = stringResource(R.string.goals_field_date)
    val metricPlaceholder = stringResource(R.string.goals_field_metric)
    val saveLabel = stringResource(R.string.goals_save)
    val cancelLabel = stringResource(R.string.goals_cancel)
    val invalidHint = stringResource(R.string.goals_invalid)

    val initial = state.initial
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var target by remember(initial) {
        mutableStateOf(initial?.targetEur?.let { LiabilityLine.formatDecimal(it) } ?: "")
    }
    var date by remember(initial) { mutableStateOf(initial?.targetDate?.toString() ?: "") }
    var metric by remember(initial) {
        mutableStateOf(initial?.metric ?: GoalMetric.NetWorth)
    }
    var metricExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun clearFlag() {
        error = false
        onCoreRejectedChange(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labelPlaceholder) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LabeledField(
                    label = labelPlaceholder,
                    value = label,
                    onValueChange = {
                        label = it
                        clearFlag()
                    },
                    singleLine = true,
                )
                LabeledField(
                    label = targetPlaceholder,
                    value = target,
                    onValueChange = {
                        target = it
                        clearFlag()
                    },
                    numeric = true,
                )
                LabeledField(
                    label = datePlaceholder,
                    value = date,
                    onValueChange = {
                        date = it
                        clearFlag()
                    },
                    singleLine = true,
                )
                // The metric picker: the two core tokens, rendered from
                // the (en+de) string resources - the core is the
                // vocabulary owner, this is the display channel.
                val metricLabel = stringResource(goalMetricLabelRes(metric))
                ExposedDropdownMenuBox(
                    expanded = metricExpanded,
                    onExpandedChange = { metricExpanded = it },
                ) {
                    TextField(
                        value = metricLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(metricPlaceholder) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(metricExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = metricExpanded,
                        onDismissRequest = { metricExpanded = false },
                    ) {
                        GoalMetric.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(stringResource(goalMetricLabelRes(entry))) },
                                onClick = {
                                    metric = entry
                                    metricExpanded = false
                                    clearFlag()
                                },
                            )
                        }
                    }
                }
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = invalidHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (coreRejected) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.goals_save_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // The thin app-side pre-check of the core's contract,
                    // then the exact line the core parses.
                    if (label.isBlank() || !validNumber(target) || !validDate(date)) {
                        error = true
                    } else {
                        val line = GoalLine.buildLine(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            label = label.trim(),
                            targetEur = target.toDouble(),
                            targetDate = if (date.isBlank()) null else LocalDate.parse(date),
                            metric = metric,
                        )
                        onSave(line)
                    }
                },
                modifier = Modifier.a11yTarget(),
            ) {
                Text(saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.a11yTarget()) {
                Text(cancelLabel)
            }
        },
    )
}

/** A finite, non-negative decimal (the core's target contract). */
private fun validNumber(value: String): Boolean {
    if (value.isBlank()) return false
    val d = value.toDoubleOrNull() ?: return false
    return d.isFinite() && d >= 0.0
}

/** Blank (no date) or a real ISO wall-clock date (YYYY-MM-DD). */
private fun validDate(value: String): Boolean {
    if (value.isBlank()) return true
    return try {
        LocalDate.parse(value)
        true
    } catch (e: DateTimeParseException) {
        false
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    numeric: Boolean = false,
    singleLine: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The (en+de) string resource of a metric token - the display channel of
 * the core-vocabulary owner.
 */
private fun goalMetricLabelRes(metric: GoalMetric): Int =
    when (metric) {
        GoalMetric.NetWorth -> R.string.goal_metric_net_worth
        GoalMetric.Assets -> R.string.goal_metric_assets
    }
