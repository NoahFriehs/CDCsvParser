package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import at.msd.friehs_bicha.cdcsvparser.R

/**
 * Settings screen (Compose, P3.1). Mirrors the old `activity_settings.xml`;
 * all persistence (PreferenceHelper) + the Firebase confirm dialogs stay in
 * the fragment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appTypeOptions: List<String>,
    appTypeSelected: Int,
    onAppTypeSelected: (Int) -> Unit,
    coreModeOptions: List<String>,
    coreModeSelected: Int,
    onCoreModeSelected: (Int) -> Unit,
    useStrictType: Boolean,
    strictTypeEnabled: Boolean,
    onUseStrictTypeChange: (Boolean) -> Unit,
    dataLocal: Boolean,
    onDataLocalChange: (Boolean) -> Unit,
    fastStart: Boolean,
    fastStartEnabled: Boolean,
    onFastStartChange: (Boolean) -> Unit,
    isSignedIn: Boolean,
    coingeckoKey: String,
    onCoinGeckoKeyChange: (String) -> Unit,
    cryptocompareKey: String,
    onCryptoCompareKeyChange: (String) -> Unit,
    onDeletePriceCaches: () -> Unit,
    logVisible: Boolean,
    onLogVisibleChange: (Boolean) -> Unit,
    logFileLabels: List<String>,
    logFileSelected: Int,
    onLogFileSelected: (Int) -> Unit,
    logFilterLabels: List<String>,
    logFilterSelected: Int,
    onLogFilterSelected: (Int) -> Unit,
    logLines: List<String>,
    onCopyLog: () -> Unit,
    onClearLog: () -> Unit,
    onAbout: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var appTypeExpanded by remember { mutableStateOf(false) }
    var coreModeExpanded by remember { mutableStateOf(false) }
    var showDeleteCachesDialog by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = appTypeExpanded,
                onExpandedChange = { appTypeExpanded = it },
            ) {
                TextField(
                    value = appTypeOptions.getOrElse(appTypeSelected) { "" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.app_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(appTypeExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = appTypeExpanded,
                    onDismissRequest = { appTypeExpanded = false },
                ) {
                    appTypeOptions.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onAppTypeSelected(index)
                                appTypeExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = coreModeExpanded,
                onExpandedChange = { coreModeExpanded = it },
            ) {
                TextField(
                    value = coreModeOptions.getOrElse(coreModeSelected) { "" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.core_mode_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(coreModeExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = coreModeExpanded,
                    onDismissRequest = { coreModeExpanded = false },
                ) {
                    coreModeOptions.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onCoreModeSelected(index)
                                coreModeExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            SwitchRow(
                label = stringResource(R.string.use_strict_wallet_type),
                checked = useStrictType,
                enabled = strictTypeEnabled,
                onCheckedChange = onUseStrictTypeChange,
            )
            SwitchRow(
                label = stringResource(R.string.store_data_local_needed_for_faststart),
                checked = dataLocal,
                enabled = true,
                onCheckedChange = onDataLocalChange,
            )
            SwitchRow(
                label = stringResource(R.string.enable_faststart),
                checked = fastStart,
                enabled = fastStartEnabled,
                onCheckedChange = onFastStartChange,
            )

            Spacer(Modifier.height(24.dp))

            // Plan 004: runtime price API keys + the cache delete feature.
            Text(
                text = stringResource(R.string.api_keys_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.api_keys_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ApiKeyField(
                label = stringResource(R.string.cg_key_label),
                value = coingeckoKey,
                onValueChange = onCoinGeckoKeyChange,
                onClear = { onCoinGeckoKeyChange("") },
            )
            Spacer(Modifier.height(8.dp))
            ApiKeyField(
                label = stringResource(R.string.cc_key_label),
                value = cryptocompareKey,
                onValueChange = onCryptoCompareKeyChange,
                onClear = { onCryptoCompareKeyChange("") },
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showDeleteCachesDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.delete_price_caches))
            }

            Spacer(Modifier.height(24.dp))

            // Plan 010: the two app logs (Kotlin app log + C++ core log).
            // Read/filter/copy from the app itself - no adb needed; the
            // crash entries (plan 007 A2) show up here as ERROR lines.
            LogSection(
                visible = logVisible,
                onVisibleChange = onLogVisibleChange,
                fileLabels = logFileLabels,
                fileSelected = logFileSelected,
                onFileSelected = onLogFileSelected,
                filterLabels = logFilterLabels,
                filterSelected = logFilterSelected,
                onFilterSelected = onLogFilterSelected,
                lines = logLines,
                onCopy = onCopyLog,
                onClear = onClearLog,
            )

            Spacer(Modifier.height(24.dp))

            FilledTonalButton(
                onClick = onAbout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_us))
            }

            Spacer(Modifier.height(12.dp))

            if (isSignedIn) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.logout)) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onDeleteAccount,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.delete_account)) }
            } else {
                OutlinedButton(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.login)) }
            }
        }
    }

    if (showDeleteCachesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCachesDialog = false },
            title = { Text(stringResource(R.string.delete_price_caches)) },
            text = { Text(stringResource(R.string.delete_price_caches_question)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCachesDialog = false
                        onDeletePriceCaches()
                    },
                ) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCachesDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

/**
 * Plan 010: the log viewer. File + severity are chosen with compact
 * segmented rows; the body is a self-scrolling monospace tail that stays
 * at the newest lines (the log grows in time, the user reads from the
 * bottom). Copy works on the filtered tail; Clear confirms in the
 * fragment. Reading happens in the fragment (IO) - this composable is
 * pure presentation.
 */
@Composable
private fun LogSection(
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    fileLabels: List<String>,
    fileSelected: Int,
    onFileSelected: (Int) -> Unit,
    filterLabels: List<String>,
    filterSelected: Int,
    onFilterSelected: (Int) -> Unit,
    lines: List<String>,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    val logScroll = rememberScrollState(initial = Int.MAX_VALUE)
    // Stick to the newest lines: on first open (initial value clamped at
    // layout) and after every fresh tail.
    LaunchedEffect(visible, lines) {
        if (visible) logScroll.animateScrollTo(logScroll.maxValue)
    }

    Column {
        Text(
            text = stringResource(R.string.log_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.log_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            fileLabels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = fileSelected == index,
                    onClick = { if (fileSelected != index) onFileSelected(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, fileLabels.size),
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            filterLabels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = filterSelected == index,
                    onClick = { if (filterSelected != index) onFilterSelected(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, filterLabels.size),
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onVisibleChange(!visible) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(if (visible) R.string.log_hide else R.string.log_show))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onCopy,
                enabled = visible && lines.isNotEmpty(),
            ) { Text(stringResource(R.string.log_copy)) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onClear, enabled = visible) {
                Text(stringResource(R.string.log_clear))
            }
        }
        if (visible) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(logScroll),
            ) {
                Text(
                    text = if (lines.isEmpty()) {
                        stringResource(R.string.log_empty)
                    } else {
                        lines.joinToString("\n")
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Plan 004: a masked API-key field with a show/hide toggle and a clear
 * action. Persistence happens at every change (the value is a short
 * local settings string, not user data).
 */
@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            // Text toggle (the icons-core set of the current BOM carries no
            // visibility glyph; the word is clearer in any locale).
            TextButton(onClick = { visible = !visible }) {
                Text(stringResource(if (visible) R.string.hide_key else R.string.show_key))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (value.isNotEmpty()) {
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.clear_key))
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
