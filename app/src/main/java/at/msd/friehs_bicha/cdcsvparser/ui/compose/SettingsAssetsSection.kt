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
import at.msd.friehs_bicha.cdcsvparser.app.AssetCategory
import at.msd.friehs_bicha.cdcsvparser.app.LiabilityLine
import at.msd.friehs_bicha.cdcsvparser.app.NonFinancialAssetLine
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** The dialog state: [initial] = null is an add, a parsed row is an edit. */
data class AssetDialogState(val initial: NonFinancialAssetLine?)

/**
 * The balance-sheet ASSETS section of the settings screen: the
 * user-maintained non-financial asset entries are CORE-owned (the serde
 * line is the contract, the core persists the sheet in its own save -
 * the app builds and renders lines only). The declared value is a PURE
 * STATIC number (the user updates it when it changes), so the section
 * shows the "last updated" stamp as the honesty signal and the total
 * (the sum of the declared values) as a read-only row.
 */
@Composable
fun SettingsAssetsSection() {
    val scope = rememberCoroutineScope()

    // Hoisted before any coroutine lambda (stringResource is
    // composable-only).
    val title = stringResource(R.string.assets_section)
    val help = stringResource(R.string.assets_help)
    val addLabel = stringResource(R.string.assets_add)
    val emptyText = stringResource(R.string.assets_empty)
    val totalLabel = stringResource(R.string.assets_total)

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var dialog by remember { mutableStateOf<AssetDialogState?>(null) }
    var coreRejected by remember { mutableStateOf(false) }

    val rows = remember(lines) { lines.mapNotNull { NonFinancialAssetLine.parse(it) } }
    val total = remember(rows) { rows.sumOf { it.valueEur } }

    LaunchedEffect(Unit) {
        // The sheet read hops the cpp-core thread (a blocking join):
        // keep it off the UI thread.
        lines = withContext(Dispatchers.IO) { CoreService.sheetLines(NonFinancialAssetLine.KIND) }
    }

    suspend fun reload() {
        lines = withContext(Dispatchers.IO) { CoreService.sheetLines(NonFinancialAssetLine.KIND) }
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
                onClick = { dialog = AssetDialogState(null) },
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
                AssetEntryRow(
                    row = row,
                    onEdit = { dialog = AssetDialogState(row) },
                    onDelete = {
                        scope.launch {
                            // Delete = core upsert + auto-save (a full state
                            // file write): the blocking hop stays off the UI.
                            val ok = withContext(Dispatchers.IO) {
                                CoreService.sheetDeleteEntry(NonFinancialAssetLine.KIND + row.id)
                            }
                            if (ok) reload()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
            // The total (the sum of the declared values) - the
            // non-financial part of the later net-worth snapshot.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = totalLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = StringHelper.formatAmountToString(total),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }

    val openDialog = dialog ?: return
    AssetDialog(
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
private fun AssetEntryRow(
    row: NonFinancialAssetLine,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editLabel = stringResource(R.string.assets_edit)
    val deleteLabel = stringResource(R.string.assets_delete)
    val categoryLabel = stringResource(categoryLabelRes(row.category))
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
                text = StringHelper.formatAmountToString(row.valueEur),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val detail = buildString {
            append(categoryLabel)
            append(" \u00B7 ").append(row.updated.format(UPDATED_DISPLAY))
            row.note?.let { append(" \u00B7 ").append(it) }
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
private fun AssetDialog(
    state: AssetDialogState,
    coreRejected: Boolean,
    onCoreRejectedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val labelPlaceholder = stringResource(R.string.assets_field_label)
    val categoryPlaceholder = stringResource(R.string.assets_field_category)
    val valuePlaceholder = stringResource(R.string.assets_field_value)
    val notePlaceholder = stringResource(R.string.assets_field_note)
    val saveLabel = stringResource(R.string.assets_save)
    val cancelLabel = stringResource(R.string.assets_cancel)
    val invalidHint = stringResource(R.string.assets_invalid)

    val initial = state.initial
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var category by remember(initial) { mutableStateOf(initial?.category ?: AssetCategory.Other) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var value by remember(initial) {
        mutableStateOf(initial?.valueEur?.let { LiabilityLine.formatDecimal(it) } ?: "")
    }
    var note by remember(initial) { mutableStateOf(initial?.note ?: "") }
    var error by remember { mutableStateOf<AssetInvalidField?>(null) }

    fun clearFlag() {
        error = null
        onCoreRejectedChange(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labelPlaceholder) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AssetLabeledField(
                    label = labelPlaceholder,
                    value = label,
                    onValueChange = {
                        label = it
                        clearFlag()
                    },
                    singleLine = true,
                )
                // The category picker: the six core tokens, rendered from
                // the (en+de) string resources - the core is the
                // vocabulary owner, this is the display channel.
                val categoryLabel = stringResource(categoryLabelRes(category))
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    TextField(
                        value = categoryLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(categoryPlaceholder) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        AssetCategory.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(stringResource(categoryLabelRes(entry))) },
                                onClick = {
                                    category = entry
                                    categoryExpanded = false
                                    clearFlag()
                                },
                            )
                        }
                    }
                }
                AssetLabeledField(
                    label = valuePlaceholder,
                    value = value,
                    onValueChange = {
                        value = it
                        clearFlag()
                    },
                    numeric = true,
                )
                AssetLabeledField(
                    label = notePlaceholder,
                    value = note,
                    onValueChange = {
                        note = it
                        clearFlag()
                    },
                    singleLine = true,
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = invalidHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (coreRejected) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.assets_save_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // The thin app-side pre-check of the core's contract
                    // (which field to point at), then the exact line the
                    // core parses. The "updated" stamp is the user's
                    // action just now (local wall clock).
                    val fieldError = errorOf(label, value)
                    if (fieldError != null) {
                        error = fieldError
                    } else {
                        val line = NonFinancialAssetLine.buildLine(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            label = label.trim(),
                            category = category,
                            valueEur = value.toDouble(),
                            updated = LocalDateTime.now(),
                            note = if (note.isBlank()) {
                                null
                            } else {
                                note.trim()
                            },
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

/** Which dialog field fails the pre-check (null = all valid). */
private fun errorOf(
    label: String,
    value: String,
): AssetInvalidField? {
    if (label.isBlank()) return AssetInvalidField.Label
    val d = value.toDoubleOrNull() ?: return AssetInvalidField.Number
    if (!d.isFinite() || d < 0.0) return AssetInvalidField.Number
    return null
}

/** The failing dialog field (the hint text points at the input as a whole). */
private enum class AssetInvalidField { Label, Number }

/**
 * The (en+de) string resource of a category token - the display channel
 * of the core-owned vocabulary (the serde tokens stay the tokens).
 */
private fun categoryLabelRes(category: AssetCategory): Int =
    when (category) {
        AssetCategory.RealEstate -> R.string.asset_category_real_estate
        AssetCategory.Vehicle -> R.string.asset_category_vehicle
        AssetCategory.Art -> R.string.asset_category_art
        AssetCategory.Collectible -> R.string.asset_category_collectible
        AssetCategory.Commodity -> R.string.asset_category_commodity
        AssetCategory.Other -> R.string.asset_category_other
    }

@Composable
private fun AssetLabeledField(
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

/** The list-row rendering of the "last updated" stamp. */
private val UPDATED_DISPLAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
