package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker
import com.github.mikephil.charting.charts.LineChart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch

/**
 * Parse/overview screen: an in-screen progress indicator while the core
 * processes the CSV, then the aggregate values as metric cards.
 *
 * A watchdog stops the progress indicator if the data cannot finish
 * arriving - but the screen stays up: parsed data (transaction list,
 * wallets, money spent) is usable without live prices, and a late arrival
 * still updates the metric cards through the same observer.
 */
class ParseFragment : Fragment() {

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val parseTimeout = Runnable {
        val v = view ?: return@Runnable
        if (!isResumed) return@Runnable
        FileLog.w(TAG, "Parsing did not finish within $PARSE_TIMEOUT_MS ms; staying on the screen with the data we have.")
        setParsingState(v, false)
        Snackbar.make(v, R.string.parsing_timeout, Snackbar.LENGTH_LONG).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_parse, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setParsingState(view, true)
        timeoutHandler.postDelayed(parseTimeout, PARSE_TIMEOUT_MS)

        view.findViewById<Button>(R.id.btn_filter).setOnClickListener {
            findNavController().navigate(R.id.walletViewFragment)
        }
        view.findViewById<Button>(R.id.btn_all_tx).setOnClickListener {
            findNavController().navigate(R.id.transactionsFragment)
        }
        displayInformation(view)
    }

    override fun onDestroyView() {
        timeoutHandler.removeCallbacks(parseTimeout)
        super.onDestroyView()
    }

    private fun setParsingState(view: View, parsing: Boolean) {
        view.findViewById<LinearProgressIndicator>(R.id.parse_progress).visibility =
            if (parsing) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.parse_status).visibility =
            if (parsing) View.VISIBLE else View.GONE
    }

    private fun popBack() {
        if (!isAdded) return
        findNavController().popBackStack()
    }

    private fun displayInformation(view: View) {
        // The counter is cumulative across runs; only a value newer than the
        // one already replayed to this observer counts as an error of this run.
        val initialErrors = CoreService.errorCounter.value ?: 0
        CoreService.parsedDataLiveData.observe(viewLifecycleOwner) {
            Benchmarker.stop()
            displayTexts(view, it)
            FileLog.d(TAG, "parsedDataLiveData changed")
            renderSpendChart(view)
            setParsingState(view, false)
            if (CoreService.lastFailedLines > 0) {
                Snackbar.make(
                    view,
                    getString(R.string.unparsable_lines_skipped, CoreService.lastFailedLines),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
        CoreService.errorCounter.observe(viewLifecycleOwner) { count ->
            if (count == null || count <= initialErrors) return@observe
            FileLog.w(TAG, "errorCounterLiveData changed: $count")
            Snackbar.make(view, R.string.error_while_parsing, Snackbar.LENGTH_LONG).show()
            setParsingState(view, false)
            popBack()
        }
    }

    private fun displayTexts(view: View, texts: Map<String, String?>?) {
        if (texts == null) {
            FileLog.e(TAG, "texts is null")
            return
        }
        texts.forEach { (key, value) ->
            val textView = view.findViewById<TextView>(
                resources.getIdentifier(key, "id", requireContext().packageName)
            )
            if (textView == null) {
                FileLog.e(TAG, "textView is null for key: $key")
                return
            }
            when (value) {
                "no internet connection" -> {
                    textView.text = resources.getString(R.string.no_internet_connection)
                }

                null -> {
                    textView.visibility = View.INVISIBLE
                }

                else -> {
                    textView.text = value
                    if (key == "profit_loss_value") {
                        textView.setTextColor(trendColor(view, firstNumber(value)))
                    }
                }
            }
        }
    }

    /** Extracts the first number of a formatted value like "-123.4 €". */
    private fun firstNumber(formatted: String): Double? {
        val match = Regex("-?[\\d.]+").find(formatted) ?: return null
        return match.value.replace(",", ".").toDoubleOrNull()
    }

    private fun trendColor(view: View, value: Double?): Int {
        val default = ContextCompat.getColor(requireContext(), R.color.on_surface)
        return when {
            value == null -> default
            value > 0 -> ContextCompat.getColor(
                requireContext(), R.color.trend_positive
            )

            value < 0 -> ContextCompat.getColor(
                requireContext(), R.color.trend_negative
            )

            else -> default
        }
    }

    /**
     * The "Geld ausgegeben" history as a monthly line chart, below the metric
     * cards. The numbers come from the C++ core (same accounting as the card
     * total, EUR inner wallet excluded like there).
     */
    private fun renderSpendChart(view: View) {
        val chart = view.findViewById<LineChart>(R.id.spend_chart)
        val label = view.findViewById<TextView>(R.id.spend_chart_label)
        viewLifecycleOwner.lifecycleScope.launch {
            val series = CoreService.moneySpentSeries()
            if (series.isEmpty()) {
                chart.visibility = View.GONE
                label.visibility = View.GONE
                return@launch
            }
            val months = ArrayList<String>(series.size)
            val entries = ArrayList<Entry>(series.size)
            series.forEachIndexed { index, raw ->
                val parts = raw.split(";")
                if (parts.size != 2) return@forEachIndexed
                val value = parts[1].toDoubleOrNull() ?: return@forEachIndexed
                months.add(parts[0])
                entries.add(Entry(index.toFloat(), value.toFloat()))
            }
            if (entries.isEmpty()) {
                chart.visibility = View.GONE
                label.visibility = View.GONE
                return@launch
            }
            val color = ContextCompat.getColor(requireContext(), R.color.primary)
            val dataset = LineDataSet(entries, "").apply {
                setDrawValues(false)
                lineWidth = 2f
                circleRadius = 3f
                setCircleColor(color)
                this.color = color
            }
            chart.apply {
                visibility = View.VISIBLE
                legend.isEnabled = false
                description.isEnabled = false
                setData(LineData(dataset))
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                xAxis.labelCount = 8
                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        val key = months.getOrNull(index) ?: return ""
                        // "YYYY-MM" -> e.g. "Mär 25" (the app locale applies)
                        return runCatching {
                            val parts = key.split("-").map { it.toInt() }
                            LocalDate.of(parts[0], parts[1], 1)
                                .format(DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()))
                        }.getOrDefault(key)
                    }
                }
                axisRight.isEnabled = false
                invalidate()
            }
            label.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val TAG = "ParseFragment"
        // Parsing itself takes well under a second; the budget mostly covers
        // the price fetch, which can wait out CoinGecko's first (worst case
        // 300 s) rate-limit cooldown on a throttled egress IP.
        private const val PARSE_TIMEOUT_MS = 300_000L
    }
}
