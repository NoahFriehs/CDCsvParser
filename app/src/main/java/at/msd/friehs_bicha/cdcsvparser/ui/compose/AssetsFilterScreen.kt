package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletRow

/**
 * Asset filter screen (Compose, P3.1): per-wallet aggregates above the
 * wallet's transactions. The wallet list + value map come from
 * WalletsViewModel/CoreService (same R.id-keyed contract as the parse
 * screen); the transaction list is the shared Compose list component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsFilterScreen(
    wallets: List<WalletRow>,
    selectedWallet: WalletRow?,
    onWalletSelected: (walletId: Int) -> Unit,
    /** Core map keys -> display value; null = hidden. */
    values: Map<String, String?>,
    walletTransactions: List<TransactionRow>,
    onRowClick: (id: Int) -> Unit,
) {
    val context = LocalContext.current
    val keyAssets = R.id.assets_value.toString()
    val keySpent = R.id.money_spent_value.toString()
    val keyPl = R.id.profit_loss_value.toString()
    val keyRewards = R.id.rewards_value.toString()

    var expanded by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.choose_asset),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                TextField(
                    value = selectedWallet?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    wallets.forEach { wallet ->
                        DropdownMenuItem(
                            text = { Text(wallet.name) },
                            onClick = {
                                onWalletSelected(wallet.walletId)
                                expanded = false
                            },
                        )
                    }
                }
            }

            ValueBlock(stringResource(R.string.amount_of_asset), displayAssetValue(context, values[keyAssets]))
            ValueBlock(stringResource(R.string.money_spent_label), displayAssetValue(context, values[keySpent]))
            ValueBlock(
                stringResource(R.string.profit_loss),
                displayAssetValue(context, values[keyPl]),
                valueColor = plValueColor(values[keyPl]),
            )
            ValueBlock(stringResource(R.string.rewards_earned), displayAssetValue(context, values[keyRewards]))

            selectedWallet?.let { wallet ->
                Text(
                    text = stringResource(R.string.all_transactions_regarding, wallet.name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            TransactionList(
                rows = walletTransactions,
                onRowClick = onRowClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ValueBlock(label: String, value: String?, valueColor: Color? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = MaterialTheme.typography.headlineSmall.fontFamily,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun displayAssetValue(context: android.content.Context, raw: String?): String? {
    if (raw == null) return null
    return if (raw == "no internet connection") {
        context.getString(R.string.no_internet_connection)
    } else {
        raw
    }
}

/** P/L block is colored by the sign of its first number, like the overview. */
private fun plValueColor(raw: String?): Color? {
    if (raw == null || raw == "no internet connection") return null
    val number = Regex("-?[\\d.]+").find(raw)?.value
        ?.replace(",", ".")?.toDoubleOrNull() ?: return null
    return when {
        number > 0 -> Color(0xFF1B7F3B) // trend_positive
        number < 0 -> Color(0xFFBA1A1A) // trend_negative
        else -> null
    }
}
