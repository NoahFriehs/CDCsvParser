package at.msd.friehs_bicha.cdcsvparser.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletRow
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletSortKey
import at.msd.friehs_bicha.cdcsvparser.ui.display.sortWalletRows
import at.msd.friehs_bicha.cdcsvparser.ui.display.walletRow
import at.msd.friehs_bicha.cdcsvparser.wallet.CroCardWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.Wallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * All wallets (outside wallets included when present) as pre-formatted
 * rows, with user-driven sorting and search applied on top.
 */
class WalletsViewModel : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortKey = MutableStateFlow(WalletSortKey.AMOUNT)
    private val sortAscending = MutableStateFlow(false)

    private val walletSource: Flow<List<Wallet>> =
        combine(
            CoreService.allWalletsLiveData.asFlow(),
            CoreService.walletsLiveData.asFlow()
        ) { all, plain ->
            if (all?.isNullOrEmpty() == true) (plain ?: emptyList()) else (all ?: emptyList())
        }

    val rows: StateFlow<List<WalletRow>> =
        combine(walletSource, searchQuery, sortKey, sortAscending) { source, query, key, ascending ->
            val filtered = if (query.isEmpty()) source else filterBySearch(source, query)
            filtered.map { walletRow(it) }
                .let { sortWalletRows(it, key, ascending) }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Unfiltered, unsorted rows — feeds the allocation overview. */
    val allRows: StateFlow<List<WalletRow>> =
        walletSource.map { source -> source.map { walletRow(it) } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchChanged(query: String) {
        searchQuery.value = query.trim()
    }

    fun onSortChanged(key: WalletSortKey, ascending: Boolean) {
        sortKey.value = key
        sortAscending.value = ascending
    }

    private fun filterBySearch(wallets: List<Wallet>, query: String): List<Wallet> {
        if (wallets.isEmpty()) return emptyList()
        return if (wallets.first() is CroCardWallet) {
            wallets.filter {
                (it as CroCardWallet).transactionType.toString().contains(query, ignoreCase = true)
            }
        } else {
            wallets.filter { it.currencyType.contains(query, ignoreCase = true) }
        }
    }
}
