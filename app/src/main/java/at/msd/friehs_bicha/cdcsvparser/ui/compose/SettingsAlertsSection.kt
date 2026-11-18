package at.msd.friehs_bicha.cdcsvparser.ui.compose

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import at.msd.friehs_bicha.cdcsvparser.BuildConfig
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AlertDirection
import at.msd.friehs_bicha.cdcsvparser.app.LiabilityLine
import at.msd.friehs_bicha.cdcsvparser.app.PriceAlertLine
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import at.msd.friehs_bicha.cdcsvparser.work.PriceRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** The dialog state: [initial] = null is an add, a parsed row is an edit. */
data class AlertDialogState(val initial: PriceAlertLine?)

/**
 * The balance-sheet ALERTS section of the settings screen: user-maintained
 * price alerts (per held symbol, ABOVE/BELOW a threshold) + the implicit
 * goal-achieved alert per goal. The CORE owns everything that is state:
 * the serde line, the latched crossing automaton, the evaluation on the
 * worker's price pass and the persistence in its own save. The app owns
 * the delivery: this UI (the CRUD via the sheet lines, the
 * [android.Manifest.permission.POST_NOTIFICATIONS] permission, the
 * notification rendering in the alerting package).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAlertsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Hoisted before any coroutine lambda (stringResource is
    // composable-only).
    val title = stringResource(R.string.alerts_section)
    val help = stringResource(R.string.alerts_help)
    val addLabel = stringResource(R.string.alerts_add)
    val emptyText = stringResource(R.string.alerts_empty)
    val editLabel = stringResource(R.string.alerts_edit)
    val deleteLabel = stringResource(R.string.alerts_delete)
    val permissionHint = stringResource(R.string.alerts_permission_hint)
    val openSettings = stringResource(R.string.alerts_permission_open_settings)
    val armedText = stringResource(R.string.alerts_armed)
    val firedLabel = stringResource(R.string.alerts_fired)

    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var heldSymbols by remember { mutableStateOf<List<String>>(emptyList()) }
    var dialog by remember { mutableStateOf<AlertDialogState?>(null) }
    var coreRejected by remember { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val rows = remember(lines) { lines.mapNotNull { PriceAlertLine.parse(it) } }

    LaunchedEffect(Unit) {
        // The sheet reads hop the cpp-core thread (a blocking join):
        // keep them off the UI thread.
        withContext(Dispatchers.IO) {
            lines = CoreService.sheetLines(PriceAlertLine.KIND)
            heldSymbols = CoreService.heldAlertSymbols()
        }
    }

    suspend fun reload() {
        lines = withContext(Dispatchers.IO) { CoreService.sheetLines(PriceAlertLine.KIND) }
    }

    // The POST_NOTIFICATIONS request (API 33+): fired contextually on the
    // first add. Denied = the hint row below (the CRUD keeps working and
    // the core keeps latching - granting later resumes delivery, at most
    // a missed notification while blocked). Below 33 the permission does
    // not exist (always treated as granted).
    var pendingAdd by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsEnabled =
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (pendingAdd) {
                pendingAdd = false
                dialog = AlertDialogState(null)
            }
        }

    fun requestAdd() {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingAdd = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            dialog = AlertDialogState(null)
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        context.startActivity(intent)
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
                onClick = { requestAdd() },
                enabled = heldSymbols.isNotEmpty() || (dialog != null),
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
        if (!notificationsEnabled) {
            // The delivery is blocked: the state still latches in the
            // core, the CRUD works, the notifications wait for the grant.
            Text(
                text = permissionHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { openNotificationSettings() }, modifier = Modifier.a11yTarget()) {
                Text(openSettings)
            }
        }
        if (rows.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            rows.forEach { row ->
                AlertEntryRow(
                    row = row,
                    editLabel = editLabel,
                    deleteLabel = deleteLabel,
                    armedText = armedText,
                    firedLabel = firedLabel,
                    onEdit = { dialog = AlertDialogState(row) },
                    onDelete = {
                        scope.launch {
                            // Delete = core upsert + auto-save (a full state
                            // file write): the blocking hop stays off the UI.
                            val ok = withContext(Dispatchers.IO) {
                                CoreService.sheetDeleteEntry(PriceAlertLine.KIND + row.id)
                            }
                            if (ok) reload()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (BuildConfig.DEBUG) {
            // The one-shot price pass (debug builds only): runs the
            // worker's full path - the price refresh and the core's
            // alert check - without waiting for the interval.
            OutlinedButton(
                onClick = { PriceRefreshWorker.runOnceNow(context) },
                modifier = Modifier.fillMaxWidth().a11yTarget(),
            ) {
                Text(stringResource(R.string.alerts_run_now_debug))
            }
        }
    }

    val openDialog = dialog ?: return
    AlertDialog(
        state = openDialog,
        heldSymbols = heldSymbols,
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
                    // The core validator rejected the line: keep the
                    // dialog open with the input intact and flag it.
                    coreRejected = true
                }
            }
        },
    )
}

@Composable
private fun AlertEntryRow(
    row: PriceAlertLine,
    editLabel: String,
    deleteLabel: String,
    armedText: String,
    firedLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val directionText = stringResource(alertDirectionLabelRes(row.direction))
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = row.symbol,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = StringHelper.formatAmountToString(row.thresholdEur),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val detail = buildString {
            append(directionText)
            append(" \u00B7 ")
            if (row.fired && row.firedIso != null) {
                append(String.format(firedLabel, row.firedIso))
            } else {
                append(armedText)
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
 * without losing the input). Saving builds the line with fired = 0 -
 * creation and a user edit are BOTH a deliberate re-arm (the automaton
 * owns everything else).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertDialog(
    state: AlertDialogState,
    heldSymbols: List<String>,
    coreRejected: Boolean,
    onCoreRejectedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val symbolPlaceholder = stringResource(R.string.alerts_field_symbol)
    val directionPlaceholder = stringResource(R.string.alerts_field_direction)
    val thresholdPlaceholder = stringResource(R.string.alerts_field_threshold)
    val saveLabel = stringResource(R.string.alerts_save)
    val cancelLabel = stringResource(R.string.alerts_cancel)
    val invalidHint = stringResource(R.string.alerts_invalid)
    val noSymbolsHint = stringResource(R.string.alerts_no_symbols)

    val initial = state.initial
    // The held symbols; an edited alert on a symbol the core no longer
    // holds keeps its stale entry selectable (the core will simply skip
    // it until the user retargets or deletes it).
    val symbolOptions = remember(heldSymbols, initial) {
        val options = heldSymbols.toMutableList()
        initial?.symbol?.let { if (!options.contains(it)) options.add(0, it) }
        options
    }
    var symbol by remember(initial) {
        mutableStateOf(initial?.symbol ?: symbolOptions.firstOrNull() ?: "")
    }
    var direction by remember(initial) {
        mutableStateOf(initial?.direction ?: AlertDirection.Above)
    }
    var threshold by remember(initial) {
        mutableStateOf(initial?.thresholdEur?.let { LiabilityLine.formatDecimal(it) } ?: "")
    }
    var symbolExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    fun clearFlag() {
        error = false
        onCoreRejectedChange(false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alerts_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (symbolOptions.isEmpty()) {
                    // No held symbols yet: an alert needs a price, the
                    // core has no state to price the alert against.
                    Text(
                        text = noSymbolsHint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                ExposedDropdownMenuBox(
                    expanded = symbolExpanded,
                    onExpandedChange = { symbolExpanded = it },
                ) {
                    TextField(
                        value = symbol,
                        onValueChange = {},
                        readOnly = true,
                        enabled = symbolOptions.isNotEmpty(),
                        label = { Text(symbolPlaceholder) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(symbolExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = symbolExpanded,
                        onDismissRequest = { symbolExpanded = false },
                    ) {
                        symbolOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    symbol = option
                                    symbolExpanded = false
                                    clearFlag()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val aboveText = stringResource(R.string.alert_direction_above)
                val belowText = stringResource(R.string.alert_direction_below)
                Text(
                    text = directionPlaceholder,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = direction == AlertDirection.Above,
                        onClick = {
                            direction = AlertDirection.Above
                            clearFlag()
                        },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.a11yTarget(),
                    ) {
                        Text(aboveText)
                    }
                    SegmentedButton(
                        selected = direction == AlertDirection.Below,
                        onClick = {
                            direction = AlertDirection.Below
                            clearFlag()
                        },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.a11yTarget(),
                    ) {
                        Text(belowText)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = threshold,
                    onValueChange = {
                        threshold = it
                        clearFlag()
                    },
                    label = { Text(thresholdPlaceholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
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
                        text = stringResource(R.string.alerts_save_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = symbolOptions.isNotEmpty(),
                onClick = {
                    // The thin app-side pre-check of the core's contract,
                    // then the exact line the core parses (fired = 0:
                    // creation or a deliberate re-arm by edit).
                    if (symbol.isBlank() || !validThreshold(threshold)) {
                        error = true
                    } else {
                        val line = PriceAlertLine.buildLine(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            symbol = symbol,
                            direction = direction,
                            thresholdEur = threshold.toDouble(),
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

/** A finite, non-negative decimal (the core's threshold contract). */
private fun validThreshold(value: String): Boolean {
    if (value.isBlank()) return false
    val d = value.toDoubleOrNull() ?: return false
    return d.isFinite() && d >= 0.0
}

/**
 * The (en+de) string resource of a direction token - the display channel
 * of the core's vocabulary.
 */
private fun alertDirectionLabelRes(direction: AlertDirection): Int =
    when (direction) {
        AlertDirection.Above -> R.string.alert_direction_above
        AlertDirection.Below -> R.string.alert_direction_below
    }
