package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction

/**
 * A fragment representing a list of Transactions.
 */
class TransactionFragment(val transactions: List<Transaction>) : Fragment() {

    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_transaction_list, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transactionAdapter = TransactionAdapter(transactions)
        val rvTransactions = view.findViewById<RecyclerView>(R.id.rvTransactions)
        rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        rvTransactions.adapter = transactionAdapter
    }
}