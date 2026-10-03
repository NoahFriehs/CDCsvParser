package at.msd.friehs_bicha.cdcsvparser.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow
import at.msd.friehs_bicha.cdcsvparser.ui.display.transactionRow
import kotlinx.coroutines.flow.Flow
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

    /** Rows of the transaction list for whatever wallet the [selectedWalletId] flow points to. */
    fun rowsForWallet(selectedWalletId: Flow<Int>): StateFlow<List<TransactionRow>> =
        combine(allTransactions, selectedWalletId) { all, walletId ->
            all.filter { it.walletId == walletId }.map { transactionRow(it) }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
