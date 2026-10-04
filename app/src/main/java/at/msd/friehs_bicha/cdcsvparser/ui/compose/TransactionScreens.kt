package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionIcon
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow

/**
 * Transactions screens (Compose, P3.1): the all-transactions list with the
 * search field, the currency chips (single-select) and the type chips
 * (multi-select), plus the transaction detail. All filtering logic stays in
 * TransactionsViewModel; this file only renders and forwards input.
 */

// region list

/** Grouped item model, same semantics as the old adapter (month headers + rows). */
sealed interface TransactionListItem {
    data class MonthHeader(val month: String) : TransactionListItem
    data class RowItem(val row: TransactionRow) : TransactionListItem
}

/** Groups the flat (date-descending) rows under their month headers. */
fun groupTransactionItems(rows: List<TransactionRow>): List<TransactionListItem> {
    val grouped = ArrayList<TransactionListItem>(rows.size)
    var lastMonth: String? = null
    for (row in rows) {
        if (row.monthKey != lastMonth) {
            grouped.add(TransactionListItem.MonthHeader(row.monthKey))
            lastMonth = row.monthKey
        }
        grouped.add(TransactionListItem.RowItem(row))
    }
    return grouped
}

/** Static trend colors, same values as res/values/colors.xml (trend_*). */
private val TrendPositive = Color(0xFF1B7F3B)
private val TrendNegative = Color(0xFFBA1A1A)

@Composable
fun TransactionList(
    rows: List<TransactionRow>,
    onRowClick: (id: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(rows) { groupTransactionItems(rows) }
    LazyColumn(
        modifier = modifier,
        state = rememberLazyListState(),
    ) {
        items(items) { item ->
            when (item) {
                is TransactionListItem.MonthHeader -> MonthHeaderRow(item.month)
                is TransactionListItem.RowItem ->
                    TransactionRowView(item.row) { onRowClick(item.row.id) }
            }
        }
    }
}

@Composable
private fun MonthHeaderRow(month: String) {
    Text(
        text = month,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun TransactionRowView(row: TransactionRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tonal circular icon container, like the old drawable background.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val (iconRes, tint) = when (row.icon) {
                    TransactionIcon.CREDIT ->
                        R.drawable.ic_savings_24 to MaterialTheme.colorScheme.primary
                    TransactionIcon.DEBIT ->
                        R.drawable.ic_upload_24 to MaterialTheme.colorScheme.error
                    TransactionIcon.PURCHASE ->
                        R.drawable.ic_spending_24 to MaterialTheme.colorScheme.secondary
                    TransactionIcon.OTHER ->
                        R.drawable.ic_trending_24 to MaterialTheme.colorScheme.tertiary
                }
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = stringResource(R.string.transaction_type),
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp),
            ) {
                Text(
                    text = row.description.ifEmpty { row.fallbackDescription() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = row.nativeAmount,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        row.nativeSigned > 0 -> TrendPositive
                        row.nativeSigned < 0 -> TrendNegative
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = row.assetAmount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 64.dp, top = 10.dp),
        )
    }
}

private fun TransactionRow.fallbackDescription(): String = when (icon) {
    TransactionIcon.CREDIT -> "Credit"
    TransactionIcon.DEBIT -> "Debit"
    TransactionIcon.PURCHASE -> "Purchase"
    TransactionIcon.OTHER -> "Transaction"
}

// endregion

// region all-transactions screen

private const val TRANSACTIONS_MAX_CHIPS = 12

@Composable
fun TransactionsScreen(
    rows: List<TransactionRow>,
    onRowClick: (id: Int) -> Unit,
    onSearchChanged: (String) -> Unit,
    onAssetFilterChanged: (String?) -> Unit,
    onTypeFilterChanged: (Set<TransactionIcon>) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selectedAsset by remember { mutableStateOf<String?>(null) }
    var selectedTypes by remember { mutableStateOf<Set<TransactionIcon>>(emptySet()) }

    // The chip options come from whatever is currently visible; anything
    // beyond the top N is still reachable via search (as before).
    val currencies = rows
        .map { it.currency }
        .distinct()
        .sorted()
        .take(TRANSACTIONS_MAX_CHIPS)

    TransactionScreen(
        search = search,
        onSearch = { search = it; onSearchChanged(it) },
        assetOptions = currencies,
        selectedAsset = selectedAsset,
        onAssetSelected = { currency ->
            // Re-tapping the active chip clears the filter (old ChipGroup behavior).
            val next = if (currency == selectedAsset) null else currency
            selectedAsset = next
            onAssetFilterChanged(next)
        },
        typeOptions = listOf(
            TransactionIcon.CREDIT to stringResource(R.string.type_income),
            TransactionIcon.DEBIT to stringResource(R.string.type_debit),
            TransactionIcon.PURCHASE to stringResource(R.string.type_purchase),
            TransactionIcon.OTHER to stringResource(R.string.type_other),
        ),
        selectedTypes = selectedTypes,
        onTypeToggled = { type ->
            selectedTypes =
                if (type in selectedTypes) selectedTypes - type else selectedTypes + type
            onTypeFilterChanged(selectedTypes)
        },
        rows = rows,
        onRowClick = onRowClick,
    )
}

/**
 * The transactions UI body. Search/chip state is owned by the caller so it
 * can be pushed into (or read from) a ViewModel, e.g. the wallet detail
 * screen which filters the same rows differently.
 */
@Composable
fun TransactionScreen(
    search: String,
    onSearch: (String) -> Unit,
    assetOptions: List<String>,
    selectedAsset: String?,
    onAssetSelected: (String?) -> Unit,
    typeOptions: List<Pair<TransactionIcon, String>>,
    selectedTypes: Set<TransactionIcon>,
    onTypeToggled: (TransactionIcon) -> Unit,
    rows: List<TransactionRow>,
    onRowClick: (id: Int) -> Unit,
    showSearch: Boolean = true,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showSearch) {
                TextField(
                    value = search,
                    onValueChange = onSearch,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }

            // Asset chips: single-select, the explicit "All" clears the filter.
            if (assetOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = selectedAsset == null,
                        onClick = { onAssetSelected(null) },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                    assetOptions.forEach { currency ->
                        FilterChip(
                            selected = selectedAsset == currency,
                            onClick = { onAssetSelected(currency) },
                            label = { Text(currency) },
                        )
                    }
                }
            }

            // Type chips: multi-select.
            if (typeOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    typeOptions.forEach { (type, label) ->
                        FilterChip(
                            selected = type in selectedTypes,
                            onClick = { onTypeToggled(type) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            TransactionList(
                rows = rows,
                onRowClick = onRowClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

// endregion

// region detail screen

/**
 * Transaction detail (argument: transactionID, resolved by the fragment).
 * All the "show only if present" rules mirror the old screen.
 */
@Composable
fun TransactionDetailScreen(
    type: String,
    date: String,
    description: String,
    amount: String,
    toAmount: String?,
    txHash: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = type, style = MaterialTheme.typography.titleLarge)
        Text(text = date, style = MaterialTheme.typography.titleLarge)
        if (description.isNotEmpty()) {
            Text(text = description, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.amount), style = MaterialTheme.typography.titleMedium)
        Text(amount, style = MaterialTheme.typography.titleLarge)
        if (toAmount != null) {
            Text(
                stringResource(R.string.to_amount),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(toAmount, style = MaterialTheme.typography.titleLarge)
        }
        if (txHash != null) {
            Text(
                stringResource(R.string.transaction_hash),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                txHash,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// endregion
