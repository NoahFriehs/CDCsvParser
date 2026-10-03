package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionDetailState
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionDetailViewModel
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import java.math.BigDecimal
import kotlinx.coroutines.launch

/**
 * Transaction detail screen (argument: transactionID).
 */
class TransactionDetailFragment : Fragment() {

    private var transactionId = -1
    private lateinit var viewModel: TransactionDetailViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transactionId = arguments?.getInt("transactionID", -1) ?: -1
        if (transactionId == -1) {
            popBack()
            return
        }

        viewModel = ViewModelProvider(this, TransactionDetailViewModel.factory(transactionId))
            .get(TransactionDetailViewModel::class.java)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(view, it) }
            }
        }
    }

    private fun render(view: View, state: TransactionDetailState) {
        val tx = when (state) {
            TransactionDetailState.Loading -> return
            is TransactionDetailState.Missing -> {
                FileLog.e(TAG, "No transaction found for id $transactionId")
                popBack()
                return
            }

            is TransactionDetailState.Found -> state.transaction
        }
        view.findViewById<TextView>(R.id.tv_transaction_type).text = tx.getTxTypeString()
        view.findViewById<TextView>(R.id.tv_date).text = tx.date.toString()
        view.findViewById<TextView>(R.id.tv_description).text = tx.description
        view.findViewById<TextView>(R.id.tv_amountValue).text =
            StringHelper.formatAmountToString(tx.amount, 6, tx.currencyType)
        if (tx.toAmount != null && tx.toCurrency != null
            && tx.toAmount != BigDecimal.ZERO && tx.toCurrency != ""
        ) {
            view.findViewById<TextView>(R.id.tv_toAmountValue).text =
                StringHelper.formatAmountToString(tx.toAmount!!, 6, tx.toCurrency!!)
        } else {
            view.findViewById<TextView>(R.id.tv_toAmount).visibility = View.GONE
        }
        if (tx.transHash != null) {
            view.findViewById<TextView>(R.id.tv_txHashValue).text = tx.transHash
        } else {
            view.findViewById<TextView>(R.id.tv_txHash).visibility = View.GONE
        }
    }

    private fun popBack() {
        if (!isAdded) return
        findNavController().popBackStack()
    }

    companion object {
        private const val TAG = "TransactionDetail"
    }
}
