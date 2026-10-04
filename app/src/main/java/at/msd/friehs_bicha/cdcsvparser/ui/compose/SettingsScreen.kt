package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    onAbout: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var appTypeExpanded by remember { mutableStateOf(false) }
    var coreModeExpanded by remember { mutableStateOf(false) }

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
