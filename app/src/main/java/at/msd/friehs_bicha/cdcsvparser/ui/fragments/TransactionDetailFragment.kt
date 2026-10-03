package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.Transaction
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import java.math.BigDecimal

/**
 * Transaction detail screen (argument: transactionID).
 */
class TransactionDetailFragment : Fragment() {

    private var transaction: Transaction? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_transaction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val transactionId = arguments?.getInt("transactionID", -1) ?: -1
        if (transactionId == -1) {
            popBack()
            return
        }

        val tx = CoreService.getTransaction(transactionId) ?: run {
            FileLog.e(TAG, "No transaction found for id $transactionId")
            popBack()
            return
        }
        transaction = tx

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
