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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker

/**
 * Parse/overview screen: an in-screen progress indicator while the core
 * processes the CSV, then the aggregate values as metric cards. A watchdog
 * pops the screen if parsing cannot finish.
 */
class ParseFragment : Fragment() {

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val parseTimeout = Runnable {
        if (!isResumed) return@Runnable
        FileLog.e(TAG, "Parsing did not finish within $PARSE_TIMEOUT_MS ms - giving up.")
        view?.let {
            Snackbar.make(it, R.string.parsing_timeout, Snackbar.LENGTH_LONG).show()
        }
        popBack()
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

    companion object {
        private const val TAG = "ParseFragment"
        private const val PARSE_TIMEOUT_MS = 90_000L
    }
}
