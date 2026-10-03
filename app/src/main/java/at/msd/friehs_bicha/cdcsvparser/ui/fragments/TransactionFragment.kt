package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A fragment representing a reactive list of transactions.
 */
class TransactionFragment(private val rows: StateFlow<List<TransactionRow>>) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_transaction_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = TransactionAdapter()
        val rvTransactions = view.findViewById<RecyclerView>(R.id.rvTransactions)
        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvTransactions.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                rows.collect { adapter.submit(it) }
            }
        }
    }
}
