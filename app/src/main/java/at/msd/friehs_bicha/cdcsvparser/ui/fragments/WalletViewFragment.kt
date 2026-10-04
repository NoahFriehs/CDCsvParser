package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filter.FilterResults
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletRow
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.ui.display.WalletSortKey
import at.msd.friehs_bicha.cdcsvparser.ui.viewmodel.WalletsViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

/**
 * Wallet view screen: allocation donut, searchable and sortable list of all
 * wallets, backed by [WalletsViewModel] and hosted in the list fragment
 * container.
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
        // Rows render on the main thread from the 5-minute price cache; if
        // that gap started while this screen was inactive, refresh in the
        // background (stale values keep showing until the data posts back).
        CoreService.refreshPricesIfStale()

        if (!childFragmentManager.isStateSaved) {
            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WalletListFragment(viewModel.rows))
                .commit()
        }

        val values = listOf(
            getString(R.string.sort_amount),
            getString(R.string.sort_amount_asset),
            getString(R.string.sort_percent),
            getString(R.string.sort_transactions)
        )
        val directions = listOf(getString(R.string.sort_desc), getString(R.string.sort_asc))

        val spinnerValue: MaterialAutoCompleteTextView = view.findViewById(R.id.sorting_value)
        val spinnerType: MaterialAutoCompleteTextView = view.findViewById(R.id.sorting_type)
        spinnerValue.setAdapter(unfilteredAdapter(values))
        spinnerType.setAdapter(unfilteredAdapter(directions))
        spinnerValue.setText(values.first(), false)
        spinnerType.setText(directions.first(), false)
        spinnerValue.setOnItemClickListener { _, _, _, _ -> applySort(spinnerValue, spinnerType) }
        spinnerType.setOnItemClickListener { _, _, _, _ -> applySort(spinnerValue, spinnerType) }

        val search = view.findViewById<android.widget.EditText>(R.id.search_bar)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) {
                viewModel.onSearchChanged(s.toString())
            }
            override fun onTextChanged(s: CharSequence, before: Int, count: Int, after: Int) {}
        })

        val chart = view.findViewById<PieChart>(R.id.allocation_chart)
        configureChart(chart)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allRows.collect { rows ->
                    updateChart(view, chart, rows)
                }
            }
        }
    }

    /**
     * Pick-one dropdowns must never filter their items: the stock
     * [ArrayAdapter] filter is prefix-based and, after a focus round-trip
     * (e.g. entering a wallet and coming back), opens with only the currently
     * shown item left ("stuck" sort dropdown, bug report 2026-10-04).
     */
    private fun unfilteredAdapter(items: List<String>): ArrayAdapter<String> {
        val all = items.toList()
        return object : ArrayAdapter<String>(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, all
        ) {
            override fun getFilter(): Filter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val r = FilterResults()
                    r.values = all.toTypedArray()
                    r.count = all.size
                    return r
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    clear()
                    when (val values = results?.values) {
                        is Array<*> -> addAll(values.map { it.toString() })
                        is List<*> -> addAll(values.map { it.toString() })
                    }
                }
            }
        }
    }

    private fun applySort(valueSpinner: MaterialAutoCompleteTextView, typeSpinner: MaterialAutoCompleteTextView) {
        val valueIndex = valueSpinner.text.toString().let { text ->
            val names = listOf(
                getString(R.string.sort_amount),
                getString(R.string.sort_amount_asset),
                getString(R.string.sort_percent),
                getString(R.string.sort_transactions)
            )
            names.indexOf(text).coerceAtLeast(0)
        }
        viewModel.onSortChanged(
            when (valueIndex) {
                1 -> WalletSortKey.ASSET_VALUE
                2 -> WalletSortKey.PERCENT
                3 -> WalletSortKey.TRANSACTIONS
                else -> WalletSortKey.AMOUNT
            },
            typeSpinner.text.toString().equals(getString(R.string.sort_asc), ignoreCase = true)
        )
    }

    private fun configureChart(chart: PieChart) {
        chart.description.isEnabled = false
        chart.setUsePercentValues(true)
        chart.setDrawHoleEnabled(true)
        chart.holeRadius = 62f
        chart.transparentCircleRadius = 68f
        chart.legend.verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
        chart.legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
        chart.legend.orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
        chart.legend.setForm(com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE)
        chart.setRotationEnabled(false)
        chart.setHighlightPerTapEnabled(false)
        // the empty state is rendered by allocation_empty, not by the library default
        chart.setNoDataText("")
        chart.setNoDataTextColor(android.graphics.Color.TRANSPARENT)
    }

    private fun updateChart(view: View, chart: PieChart, rows: List<WalletRow>) {
        // The donut shows the allocation of held assets; outside wallets are
        // bookkeeping balances and stay in the list below only.
        val sliceable = rows.filter { !it.isOutside && it.assetValue > 0 }
        val hasData = sliceable.isNotEmpty()
        view.findViewById<View>(R.id.allocation_empty).visibility =
            if (hasData) View.GONE else View.VISIBLE
        if (!hasData) {
            chart.clear()
            chart.invalidate()
            return
        }
        val total = sliceable.sumOf { it.assetValue.toDouble() }
        // Entries (and the legend that follows their order) go by allocation:
        // biggest slice first.
        val entries = sliceable
            .sortedByDescending { it.assetValue }
            .map { PieEntry(it.assetValue.toFloat(), it.name) }
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = CHART_COLORS.map { ContextCompat.getColor(requireContext(), it) }.toMutableList()
        dataSet.valueTextSize = 11f
        // hide labels on small slices so they do not overlap
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            private val percent = PercentFormatter(chart)
            override fun getFormattedValue(value: Float): String {
                val share = if (total > 0) value.toDouble() / total else 0.0
                return if (share >= 0.03) percent.getFormattedValue(value) else ""
            }
        }
        chart.setDrawEntryLabels(false) // the legend names the slices
        val data = PieData(dataSet)
        data.setDrawValues(true)
        chart.setData(data)
        chart.setCenterText(if (total > 0) "%.0f €".format(total) else "")
        chart.setCenterTextSize(16f)
        chart.setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface))
        chart.invalidate()
    }

    companion object {
        private val CHART_COLORS = listOf(
            R.color.primary, R.color.secondary, R.color.tertiary,
            R.color.primary_container, R.color.secondary_container, R.color.tertiary_container,
            R.color.on_primary_container, R.color.on_secondary_container, R.color.on_tertiary_container,
            R.color.on_surface_variant,
        )
    }
}
