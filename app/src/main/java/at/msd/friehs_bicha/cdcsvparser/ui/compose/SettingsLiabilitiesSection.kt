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
data class LiabilityDialogState(val initial: LiabilityLine?)

/**
 * The balance-sheet LIABILITIES section of the settings screen: the
 * user-maintained debt entries are CORE-owned (the serde line is the
 * contract, the core computes the current balance and persists the
 * sheet in its own save - the app builds and renders lines only).
 */
@Composable
fun SettingsLiabilitiesSection() {
    val scope = rememberCoroutineScope()

    // Hoisted before any coroutine lambda (stringResource is
    // composable-only).
    val title = stringResource(R.string.liabilities_section)
    val help = stringResource(R.string.liabilities_help)
    val addLabel = stringResource(R.string.liabilities_add)
    val emptyText = stringResource(R.string.liabilities_empty)
    val editLabel = stringResource(R.string.liabilities_edit)
    val deleteLabel = stringResource(R.string.liabilities_delete)
    val perMonth = stringResource(R.string.liabilities_per_month)

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var dialog by remember { mutableStateOf<LiabilityDialogState?>(null) }
    var coreRejected by remember { mutableStateOf(false) }

    val rows = remember(lines) { lines.mapNotNull { LiabilityLine.parse(it) } }

    LaunchedEffect(Unit) {
        // The sheet read hops the cpp-core thread (a blocking join):
        // keep it off the UI thread.
        lines = withContext(Dispatchers.IO) { CoreService.sheetLines(LiabilityLine.KIND) }
    }

    suspend fun reload() {
        lines = withContext(Dispatchers.IO) { CoreService.sheetLines(LiabilityLine.KIND) }
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
                onClick = { dialog = LiabilityDialogState(null) },
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
                LiabilityEntryRow(
                    row = row,
                    perMonth = perMonth,
                    editLabel = editLabel,
                    deleteLabel = deleteLabel,
                    onEdit = { dialog = LiabilityDialogState(row) },
                    onDelete = {
                        scope.launch {
                            // Delete = core upsert + auto-save (a full state
                            // file write): the blocking hop stays off the UI.
                            val ok = withContext(Dispatchers.IO) {
                                CoreService.sheetDeleteEntry(LiabilityLine.KIND + row.id)
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
    LiabilityDialog(
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
private fun LiabilityEntryRow(
    row: LiabilityLine,
    perMonth: String,
    editLabel: String,
    deleteLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
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
                text = StringHelper.formatAmountToString(row.balanceEur ?: 0.0),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val detail = buildString {
            append(LiabilityLine.formatDecimal(row.annualRatePct)).append(" % p.a.")
            append(" \u00B7 ").append(row.start)
            row.end?.let { append(" \u00B7 ").append(it) }
            row.monthlyPaymentEur?.let {
                append(" \u00B7 ")
                append(StringHelper.formatAmountToString(it))
                append(' ').append(perMonth)
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
@Composable
private fun LiabilityDialog(
    state: LiabilityDialogState,
    coreRejected: Boolean,
    onCoreRejectedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val labelPlaceholder = stringResource(R.string.liabilities_field_label)
    val principalPlaceholder = stringResource(R.string.liabilities_field_principal)
    val ratePlaceholder = stringResource(R.string.liabilities_field_rate)
    val startPlaceholder = stringResource(R.string.liabilities_field_start)
    val endPlaceholder = stringResource(R.string.liabilities_field_end)
    val paymentPlaceholder = stringResource(R.string.liabilities_field_payment)
    val saveLabel = stringResource(R.string.liabilities_save)
    val cancelLabel = stringResource(R.string.liabilities_cancel)
    val invalidHint = stringResource(R.string.liabilities_invalid)

    val initial = state.initial
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var principal by remember(initial) {
        mutableStateOf(initial?.principalEur?.let { LiabilityLine.formatDecimal(it) } ?: "")
    }
    var rate by remember(initial) {
        mutableStateOf(initial?.annualRatePct?.let { LiabilityLine.formatDecimal(it) } ?: "")
    }
    var start by remember(initial) { mutableStateOf(initial?.start?.toString() ?: "") }
    var end by remember(initial) { mutableStateOf(initial?.end?.toString() ?: "") }
    var payment by remember(initial) {
        mutableStateOf(
            if (initial?.monthlyPaymentEur != null) {
                LiabilityLine.formatDecimal(initial.monthlyPaymentEur)
            } else {
                ""
            },
        )
    }
    var error by remember { mutableStateOf<InvalidField?>(null) }

    fun clearFlag() {
        error = null
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
                    label = principalPlaceholder,
                    value = principal,
                    onValueChange = {
                        principal = it
                        clearFlag()
                    },
                    numeric = true,
                )
                LabeledField(
                    label = ratePlaceholder,
                    value = rate,
                    onValueChange = {
                        rate = it
                        clearFlag()
                    },
                    numeric = true,
                )
                LabeledField(
                    label = startPlaceholder,
                    value = start,
                    onValueChange = {
                        start = it
                        clearFlag()
                    },
                    singleLine = true,
                )
                LabeledField(
                    label = endPlaceholder,
                    value = end,
                    onValueChange = {
                        end = it
                        clearFlag()
                    },
                    singleLine = true,
                )
                LabeledField(
                    label = paymentPlaceholder,
                    value = payment,
                    onValueChange = {
                        payment = it
                        clearFlag()
                    },
                    numeric = true,
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
                        text = stringResource(R.string.liabilities_save_failed),
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
                    // core parses.
                    val fieldError = errorOf(label, principal, rate, start, end, payment)
                    if (fieldError != null) {
                        error = fieldError
                    } else {
                        val line = LiabilityLine.buildLine(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            label = label.trim(),
                            principalEur = principal.toDouble(),
                            annualRatePct = rate.toDouble(),
                            start = LocalDate.parse(start),
                            end = if (end.isBlank()) null else LocalDate.parse(end),
                            monthlyPaymentEur = if (payment.isBlank()) {
                                null
                            } else {
                                payment.toDouble()
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
    principal: String,
    rate: String,
    start: String,
    end: String,
    payment: String,
): InvalidField? {
    if (label.isBlank()) return InvalidField.Label
    for (v in listOf(principal, rate)) {
        val d = v.toDoubleOrNull() ?: return InvalidField.Number
        if (!d.isFinite() || d < 0.0) return InvalidField.Number
    }
    try {
        LocalDate.parse(start)
    } catch (e: DateTimeParseException) {
        return InvalidField.Date
    }
    if (end.isNotBlank()) {
        try {
            LocalDate.parse(end)
        } catch (e: DateTimeParseException) {
            return InvalidField.Date
        }
    }
    if (payment.isNotBlank()) {
        val d = payment.toDoubleOrNull() ?: return InvalidField.Number
        if (!d.isFinite() || d < 0.0) return InvalidField.Number
    }
    return null
}

/** The failing dialog field (the hint text points at the input as a whole). */
private enum class InvalidField { Label, Number, Date }

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
