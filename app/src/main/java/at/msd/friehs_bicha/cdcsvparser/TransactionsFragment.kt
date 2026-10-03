package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.TransactionFragment

/**
 * All-transactions screen: container that hosts the (existing) transaction
 * list fragment with the combined, sorted list of all transactions.
 */
class TransactionsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_transactions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TransactionFragment(buildList()))
                .commit()
        }
    }

    /**
     * Create list of all Transactions on create this view
     */
    private fun buildList(): ArrayList<Transaction> {
        val list = ArrayList<Transaction>(
            CoreService.transactionsLiveData.value ?: emptyList()
        )
        CoreService.cardTransactionsLiveData.value?.let { list.addAll(it) }
        // Transaction does not override equals(), so deduplicate explicitly
        // by transaction id.
        val unique = list.distinctBy { it.transactionId }
        list.clear()
        list.addAll(unique)
        list.sortByDescending { it.date }
        return list
    }
}
