package at.msd.friehs_bicha.cdcsvparser.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionIcon
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow
import at.msd.friehs_bicha.cdcsvparser.ui.display.transactionRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Flat, deduplicated, date-descending list of all (crypto + card)
 * transactions as pre-formatted rows.
 */
class TransactionsViewModel : ViewModel() {

    private val allTransactions: StateFlow<List<Transaction>> =
        combine(
            CoreService.transactionsLiveData.asFlow(),
            CoreService.cardTransactionsLiveData.asFlow()
        ) { crypto, card ->
            val all = (crypto ?: emptyList<Transaction>()) +
                (card ?: emptyList<Transaction>())
            all.distinctBy { it.transactionId }.sortedByDescending { it.date }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val rows: StateFlow<List<TransactionRow>> =
        allTransactions.map { list -> list.map { transactionRow(it) } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Free-text filter applied client-side over description and currency. */
    private val searchQuery = MutableStateFlow("")

    /** Currency filter for the chip row; null means "all". */
    private val assetFilter = MutableStateFlow<String?>(null)

    /** Type-family filter for the type chip row; empty means "all". */
    private val typeFilter = MutableStateFlow<Set<TransactionIcon>>(emptySet())

    /** The rows matching the current search query, asset chip and type chips. */
    val visibleRows: StateFlow<List<TransactionRow>> =
        combine(rows, searchQuery, assetFilter, typeFilter) { list, query, asset, types ->
            val q = query.trim().lowercase()
            list.filter { row ->
                val matchesQuery = q.isEmpty() ||
                    row.description.lowercase().contains(q) ||
                    row.currency.lowercase().contains(q)
                val matchesAsset = asset == null || row.currency == asset
                val matchesType = types.isEmpty() || row.icon in types
                matchesQuery && matchesAsset && matchesType
            }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchChanged(query: String) {
        searchQuery.value = query
    }

    fun onAssetFilterChanged(currency: String?) {
        assetFilter.value = currency
    }

    fun onTypeFilterChanged(types: Set<TransactionIcon>) {
        typeFilter.value = types
    }

    /** Rows of the transaction list for whatever wallet the [selectedWalletId] flow points to. */
    fun rowsForWallet(selectedWalletId: Flow<Int>): StateFlow<List<TransactionRow>> =
        combine(allTransactions, selectedWalletId) { all, walletId ->
            all.filter { it.walletId == walletId }.map { transactionRow(it) }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
