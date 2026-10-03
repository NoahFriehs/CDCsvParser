package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.TransactionFragment
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionsViewModel

/**
 * All-transactions screen: container that hosts (the existing) transaction
 * list fragment fed by [TransactionsViewModel].
 */
class TransactionsFragment : Fragment() {

    private val viewModel: TransactionsViewModel by viewModels()

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
                .replace(R.id.fragment_container, TransactionFragment(viewModel.rows))
                .commit()
        }
    }
}
