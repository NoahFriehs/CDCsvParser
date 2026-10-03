package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletSortKey
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.WalletListFragment
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.WalletsViewModel

/**
 * Wallet view screen: searchable, sortable list of all wallets, backed by
 * [WalletsViewModel] and hosted in the list fragment container.
 */
class WalletViewFragment : Fragment() {

    private val viewModel: WalletsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_wallet_view, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WalletListFragment(viewModel.rows))
                .commit()
        }

        val spinnerValue = view.findViewById<Spinner>(R.id.sorting_value)
        spinnerValue.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.sort_amount),
                getString(R.string.sort_amount_asset),
                getString(R.string.sort_percent),
                getString(R.string.sort_transactions)
            )
        )

        val spinnerType = view.findViewById<Spinner>(R.id.sorting_type)
        spinnerType.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.sort_desc),
                getString(R.string.sort_asc)
            )
        )

        val onValueSelected = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View?,
                position: Int,
                id: Long
            ) {
                applySort(spinnerValue.selectedItemPosition, spinnerType.selectedItemPosition)
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
        val onTypeSelected = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parentView: AdapterView<*>?,
                selectedItemView: View?,
                position: Int,
                id: Long
            ) {
                applySort(spinnerValue.selectedItemPosition, spinnerType.selectedItemPosition)
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
        spinnerValue.onItemSelectedListener = onValueSelected
        spinnerType.onItemSelectedListener = onTypeSelected

        val search = view.findViewById<EditText>(R.id.search_bar)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) {
                viewModel.onSearchChanged(s.toString())
            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }

    private fun applySort(valuePosition: Int, typePosition: Int) {
        val key = when (valuePosition) {
            0 -> WalletSortKey.AMOUNT
            1 -> WalletSortKey.ASSET_VALUE
            2 -> WalletSortKey.PERCENT
            3 -> WalletSortKey.TRANSACTIONS
            else -> WalletSortKey.AMOUNT
        }
        val ascending = typePosition == 1
        viewModel.onSortChanged(key, ascending)
    }
}
