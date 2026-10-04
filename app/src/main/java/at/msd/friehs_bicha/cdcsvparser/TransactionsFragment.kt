package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionIcon
import at.msd.friehs_bicha.cdcsvparser.ui.display.TransactionRow
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.TransactionFragment
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.TransactionsViewModel
import kotlinx.coroutines.launch

/**
 * All-transactions screen: search + currency filter chips on top of the
 * (hosted) transaction list, all fed by [TransactionsViewModel].
 */
class TransactionsFragment : Fragment() {

    private val viewModel: TransactionsViewModel by viewModels()
    private var chipKey = ""

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
                .replace(R.id.fragment_container, TransactionFragment(viewModel.visibleRows))
                .commit()
        }

        view.findViewById<TextInputEditText>(R.id.et_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, before: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) = viewModel.onSearchChanged(s.toString())
        })

        wireChips(view.findViewById(R.id.chip_group))

        // Type chips (multi-select): built in code because the XML attribute
        // for checkable chips is not available; each tag carries the icon.
        val typeChipGroup = view.findViewById<ChipGroup>(R.id.type_chip_group)
        for (type in TransactionIcon.entries) {
            typeChipGroup.addView(
                Chip(requireContext()).apply {
                    text = getString(TYPE_STRING.getValue(type))
                    isCheckable = true
                    tag = type
                }
            )
        }
        typeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val types = checkedIds
                .mapNotNull { group.findViewById<Chip>(it)?.tag as? TransactionIcon }
                .toSet()
            viewModel.onTypeFilterChanged(types)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.visibleRows.collect { rows ->
                    rebuildChips(view.findViewById(R.id.chip_group), rows)
                }
            }
        }
    }

    private fun wireChips(chipGroup: ChipGroup) {
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val tag = chipGroup.findViewById<Chip>(checkedId)?.tag
            viewModel.onAssetFilterChanged(tag as String?)
        }
    }

    /** Rebuilds the chip row (All + one chip per currency) when the data changes. */
    private fun rebuildChips(chipGroup: ChipGroup, rows: List<TransactionRow>) {
        // Cap the chip row; anything beyond the top N is still reachable via search.
        val currencies = rows.map { it.currency }.distinct().sorted().take(MAX_CHIPS)
        val key = currencies.joinToString("|")
        if (key == chipKey) return
        chipKey = key

        val checkedId = chipGroup.checkedChipId
        chipGroup.removeAllViews()
        chipGroup.addView(makeChip(getString(R.string.filter_all), null))
        currencies.forEach { chipGroup.addView(makeChip(it, it)) }
        // Keep the previous selection when it still exists, else "All".
        val previous = if (checkedId != 0) chipGroup.findViewById<Chip>(checkedId) else null
        chipGroup.check(if (previous != null) previous.id else chipGroup.getChildAt(0).id)
    }

    private fun makeChip(text: String, tag: String?): Chip =
        Chip(requireContext()).apply {
            this.text = text
            isCheckable = true
            this.tag = tag
        }

    companion object {
        private const val MAX_CHIPS = 12

        private val TYPE_STRING = mapOf(
            TransactionIcon.PURCHASE to R.string.type_purchase,
            TransactionIcon.CREDIT to R.string.type_income,
            TransactionIcon.DEBIT to R.string.type_debit,
            TransactionIcon.OTHER to R.string.type_other,
        )
    }
}
