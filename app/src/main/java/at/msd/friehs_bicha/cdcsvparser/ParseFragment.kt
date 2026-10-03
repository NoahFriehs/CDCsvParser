package at.msd.friehs_bicha.cdcsvparser

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker

/**
 * Parse/overview screen: progress while the core processes the CSV, then the
 * aggregate values. Watchdog + cancelable progress dialog prevent being stuck
 * on the progress screen forever.
 */
class ParseFragment : Fragment() {

    private lateinit var progressDialog: Dialog

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val parseTimeout = Runnable {
        if (!isResumed) return@Runnable
        FileLog.e(TAG, "Parsing did not finish within $PARSE_TIMEOUT_MS ms - giving up.")
        Toast.makeText(requireContext(), R.string.parsing_timeout, Toast.LENGTH_LONG).show()
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
        showProgressDialog()
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
        try {
            if (::progressDialog.isInitialized && progressDialog.isShowing) {
                progressDialog.setOnCancelListener(null)
                progressDialog.dismiss()
            }
        } catch (ignored: IllegalStateException) {
        }
        super.onDestroyView()
    }

    private fun popBack() {
        if (!isAdded) return
        findNavController().popBackStack()
    }

    private fun displayInformation(view: View) {
        CoreService.parsedDataLiveData.observe(viewLifecycleOwner) {
            Benchmarker.stop()
            displayTexts(view, it)
            FileLog.d(TAG, "parsedDataLiveData changed")
            hideProgressDialog()
            if (CoreService.lastFailedLines > 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.unparsable_lines_skipped, CoreService.lastFailedLines),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        CoreService.errorCounter.observe(viewLifecycleOwner) {
            FileLog.w(TAG, "errorCounterLiveData changed")
            Toast.makeText(requireContext(), R.string.error_while_parsing, Toast.LENGTH_LONG).show()
            hideProgressDialog()
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
                }
            }
        }
    }

    private fun showProgressDialog() {
        progressDialog = Dialog(requireContext())
        progressDialog.setContentView(R.layout.progress_icon)
        // Cancelable so the user always has an escape hatch; a user cancel
        // leaves the screen. onCancel only (user action) - the programmatic
        // dismiss on success must not pop the screen.
        progressDialog.setCancelable(true)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setOnCancelListener { popBack() }
        progressDialog.show()
    }

    fun hideProgressDialog() {
        timeoutHandler.removeCallbacks(parseTimeout)
        progressDialog.dismiss()
    }

    companion object {
        private const val TAG = "ParseFragment"
        private const val PARSE_TIMEOUT_MS = 90_000L
    }
}
