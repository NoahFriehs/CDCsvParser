package at.msd.friehs_bicha.cdcsvparser.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Detail screen state for a single transaction (looked up by id). */
sealed interface TransactionDetailState {
    /** The flows have not produced a result yet. */
    data object Loading : TransactionDetailState

    data class Found(val transaction: Transaction) : TransactionDetailState

    /** Result resolved, no such transaction. */
    data object Missing : TransactionDetailState
}

class TransactionDetailViewModel(private val transactionId: Int) : ViewModel() {

    val state: StateFlow<TransactionDetailState> =
        combine(
            CoreService.transactionsLiveData.asFlow(),
            CoreService.cardTransactionsLiveData.asFlow()
        ) { crypto, card ->
            val all: List<Transaction> =
                (crypto ?: emptyList()) + (card ?: emptyList())
            all.firstOrNull { it.transactionId == transactionId }
                ?.let { TransactionDetailState.Found(it) }
                ?: TransactionDetailState.Missing
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, TransactionDetailState.Loading)

    companion object {
        fun factory(transactionId: Int): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TransactionDetailViewModel(transactionId) as T
            }
    }
}
