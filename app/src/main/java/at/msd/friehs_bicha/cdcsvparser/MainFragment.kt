package at.msd.friehs_bicha.cdcsvparser

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.HistoryDialogFragment
import at.msd.friehs_bicha.cdcsvparser.core.ChartSeries
import at.msd.friehs_bicha.cdcsvparser.core.Core
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.core.CoreSource
import at.msd.friehs_bicha.cdcsvparser.general.HistoryFileUtil
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.app.DeltaBasis
import at.msd.friehs_bicha.cdcsvparser.app.SummaryDelta
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import at.msd.friehs_bicha.cdcsvparser.price.PriceHistoryPass
import at.msd.friehs_bicha.cdcsvparser.price.PriceHistoryProvider
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.MainScreen
import at.msd.friehs_bicha.cdcsvparser.ui.compose.parseWalletSeries
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker
import at.msd.friehs_bicha.cdcsvparser.util.FileUtil
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Main screen (Compose surface): import a CSV (new or from history)
 * and jump into the overview. The SAF upload flow, the history dialog and
 * the progress dialog stay in the fragment.
 */
class MainFragment : Fragment() {

    /**
     * The core surface this fragment reads: the process-wide
     * implementation by default, injectable in tests.
     */
    internal var core: CoreSource = Core
        private set

    var files: Array<File>? = null
    private var progressDialog: Dialog? = null

    /** Display name of the newest history file (recomputed on onResume). */
    private val _newestHistoryFile = mutableStateOf<String?>(null)
    private val _isSignedIn = mutableStateOf(FirebaseAuth.getInstance().currentUser != null)
    /**
     * The core's chart-series post (the unpriced per-wallet lines are
     * the delta builder's input). Null before the first core data post.
     */
    private val _chartSeries = mutableStateOf<ChartSeries?>(null)

    /**
     * The core's snapshot lines, re-read on every core data post (the
     * fast-start readiness + the price-refresh rhythm - the tracked
     * value is priced, so a price pass moves the figure). Consumed by
     * the summary builder with the priced series.
     */
    private val _sheetLines = mutableStateOf<List<String>>(emptyList())

    /**
     * The active delta basis of the summary chip (the Settings toggle,
     * re-read on resume - the toggle lives on another destination).
     */
    private val _basis = mutableStateOf(DeltaBasis.DAY)

    /**
     * The resolved summary hero state (headline + delta chip), built by
     * the pure point-in-time builder from the same data the charts
     * plot. null = no live core state yet (the card stays hidden,
     * never a placeholder number).
     */
    private val _summary = mutableStateOf<SummaryDelta?>(null)

    // SAF file picker (ActivityResult API): no storage permissions are
    // needed for ACTION_GET_CONTENT.
    private val pickFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) onFileSelected(uri) else hideProgressDialog()
        }

    companion object {
        private const val TAG = "MainFragment"
        // Single source for the history file name pattern (write + parse).
        const val HISTORY_FILE_PATTERN = "yyyy-MM-dd-HH-mm-ss"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        updateFiles()
        _isSignedIn.value = FirebaseAuth.getInstance().currentUser != null
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    // Reading the shared pass state inside the composition
                    // re-runs this block as the pass publishes symbols -
                    // the same observation rhythm as the charts.
                    val history = PriceHistoryPass.history.value
                    summaryEffect(_chartSeries.value, history, _basis.value, _sheetLines.value)
                    MainScreen(
                        summary = _summary.value,
                        isSignedIn = _isSignedIn.value,
                        newestHistoryFile = _newestHistoryFile.value,
                        onUploadClick = { onBtnUploadClick() },
                        onHistoryClick = {
                            HistoryDialogFragment().show(childFragmentManager, "history")
                        },
                        onLoadFromDbClick = { loadFromFireBaseDB() },
                        onNetWorthClick = {
                            findNavController().navigate(R.id.networthFragment)
                        },
                    )
                }
            }
            // History dialog results (select / delete), as before.
            childFragmentManager.setFragmentResultListener(
                HistoryDialogFragment.REQUESTED_SELECT, this@MainFragment.viewLifecycleOwner
            ) { _, bundle ->
                val fileName = bundle.getString(HistoryDialogFragment.KEY_FILE)
                    ?: return@setFragmentResultListener
                startParseFromFile(fileName)
            }
            childFragmentManager.setFragmentResultListener(
                HistoryDialogFragment.REQUESTED_DELETE, this@MainFragment.viewLifecycleOwner
            ) { _, _ -> onHistoryDeleted() }
            childFragmentManager.setFragmentResultListener(
                HistoryDialogFragment.REQUESTED_SELECT_COMBINED, this@MainFragment.viewLifecycleOwner
            ) { _, bundle ->
                val fileNames =
                    bundle.getStringArrayList(HistoryDialogFragment.KEY_FILES)
                        ?: return@setFragmentResultListener
                startParseFromCombinedSelection(fileNames)
            }
            childFragmentManager.setFragmentResultListener(
                HistoryDialogFragment.REQUESTED_PURGE, this@MainFragment.viewLifecycleOwner
            ) { _, bundle ->
                // The purge already happened in the dialog; sync this
                // screen's state and report the ACTUAL deleted count.
                val count = bundle.getInt(HistoryDialogFragment.KEY_PURGE_COUNT)
                updateFiles()
                _newestHistoryFile.value = newestHistoryDisplayName()
                view?.let {
                    Snackbar.make(it, getString(R.string.history_purged_count, count), Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // #47: the settings gear lives in the activity action bar (one
        // header bar, like every other screen). View-scoped provider:
        // it is attached for the lifetime of THIS fragment's view and
        // removed with it, so other destinations keep a plain bar.
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    // A navigation transition can briefly hold both
                    // views (old + new) alive at once — never add the
                    // gear twice to the same menu.
                    if (menu.findItem(R.id.action_settings) != null) return
                    menuInflater.inflate(R.menu.menu_settings, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                    when (menuItem.itemId) {
                        R.id.action_settings -> {
                            findNavController().navigate(R.id.settingsFragment)
                            true
                        }

                        else -> false
                    }
            },
            viewLifecycleOwner,
        )

        // The summary card rides the same data posts as the charts:
        // every core data post (fast-start readiness, a new import, a
        // price pass) re-reads the snapshot lines and (re)builds the
        // priced delta. The read is a fast JNI call on the cpp-core
        // thread; the core stays the source of the figure (the
        // buffer-inclusion semantic is pinned there).
        core.chartSeriesLiveData.observe(viewLifecycleOwner) { series ->
            _chartSeries.value = series
            _sheetLines.value = CoreService.netWorthSnapshot()
            attachSummaryHistoryPass(series)
        }
        _sheetLines.value = CoreService.netWorthSnapshot()
        readDeltaBasis()

        // The launcher activity passes "fastStart" to go straight to the
        // overview (fast start). The extra is consumed ONCE: a recreated view
        // (pop-back delivers a fresh view with savedInstanceState == null,
        // and process restore / rotation re-deliver the same intent) must
        // never fire the navigation a second time - that is what made the
        // overview screen impossible to leave after a fast start.
        val intent = requireActivity().intent
        if (savedInstanceState == null && intent.hasExtra("fastStart")) {
            intent.removeExtra("fastStart")
            fastStart()
        }
    }

    override fun onResume() {
        super.onResume()
        // The parsed cards are strings from the last price pass; refresh them
        // in the background once the 5-minute cache TTL started to expire.
        core.refreshPricesIfStale()
        _isSignedIn.value = FirebaseAuth.getInstance().currentUser != null
        updateFiles()
        _newestHistoryFile.value = newestHistoryDisplayName()
        // The delta-basis toggle lives on the settings destination:
        // coming back here must pick up the choice the user made there.
        readDeltaBasis()
    }

    private fun fastStart() {
        findNavController().navigate(R.id.parseFragment)
    }

    /** The Settings preference of the active delta basis. */
    private fun readDeltaBasis() {
        _basis.value = if (PreferenceHelper.getDeltaBasis30d(requireContext())) {
            DeltaBasis.THIRTY_DAYS
        } else {
            DeltaBasis.DAY
        }
    }

    /**
     * Joins the process-wide deep-history pass for the held currencies
     * (zero new work when the same request already runs / finished
     * today): the delta builder prices from its cache, exactly like the
     * charts - the pass attach itself is non-blocking. The EUR cash
     * buffer sits in the currency set (the per-wallet series is
     * buffer-inclusive) but the pass FIAT-SKIPS it (face value, never
     * fetched).
     */
    private fun attachSummaryHistoryPass(series: ChartSeries) {
        val walletDays = parseWalletSeries(series.wallet)
        val currencies = walletDays.keys.toList()
        if (currencies.isEmpty()) return
        val startDay = walletDays.values.flatten().minOf { it.date }
        val rangeDays = ((LocalDate.now().toEpochDay() - startDay.toEpochDay()).coerceAtLeast(0) + 1).toInt()
        PriceHistoryPass.ensureAll(currencies, rangeDays, null)
    }

    /**
     * (Re)builds the remembered summary hero state: the pure builder
     * prices the core's unpriced per-wallet series with the pass's
     * published cache (no network of its own) - the last point at the
     * live pin, the previous point at the price of its day. Re-runs on
     * every series post, every pass publish and every basis change;
     * the work is pure composition (the charts' rhythm).
     */
    @Composable
    private fun summaryEffect(
        series: ChartSeries?,
        history: Map<String, PriceHistoryProvider.DailyPrices>,
        basis: DeltaBasis,
        sheetLines: List<String>,
    ) {
        LaunchedEffect(series, history, basis, sheetLines) {
            if (series == null) {
                _summary.value = null
                return@LaunchedEffect
            }
            val walletDays = parseWalletSeries(series.wallet)
            val currencies = walletDays.keys.toList()
            if (currencies.isEmpty()) {
                _summary.value = null
                return@LaunchedEffect
            }
            // The pass's prices (cache-aware, the EUR buffer pinned to
            // 1.0, no network) - the same source the charts value with.
            val livePrices = AssetValue.getInstance().getPricesFromCache(currencies)
            _summary.value = SummaryDelta.deltaChipSnapshot(
                walletSeries = series.wallet,
                history = history.mapValues { it.value.points },
                livePrices = livePrices,
                today = LocalDate.now(),
                basis = basis,
                sheetLines = sheetLines,
            )
        }
    }

    /**
     * gets all files from internal file storage and updates them
     */
    private fun updateFiles() {
        val appDir = requireContext().filesDir
        val all = appDir.listFiles() ?: arrayOf()
        files = all.filter { it.name.endsWith(".csv") }.toTypedArray()
    }

    /**
     * Same name format as the history dialog rows: newest file first in
     * "d.M. HH:mm"; null when there is no history at all (proper empty
     * state - the whole section is hidden).
     */
    @SuppressLint("SimpleDateFormat")
    private fun newestHistoryDisplayName(): String? {
        val newest = files?.filter { it.isFile && it.name.endsWith(".csv") }
            ?.maxByOrNull { it.lastModified() }?.name ?: return null
        return displayNameFor(newest)
    }

    @SuppressLint("SimpleDateFormat")
    private fun displayNameFor(fileName: String?): String? {
        val raw = fileName?.removeSuffix(".csv") ?: return null
        val sdf = SimpleDateFormat(HISTORY_FILE_PATTERN)
        val display = SimpleDateFormat("d.M. HH:mm")
        return runCatching { display.format(sdf.parse(raw)) }.getOrDefault(raw)
    }

    /**
     * Starts the parse for a file chosen in the history dialog.
     */
    private fun startParseFromFile(fileName: String) {
        showProgressDialog()
        try {
            // The entry came from this very directory; a miss means the file
            // vanished since the list was drawn - fail with a message the
            // user can read (the catch below turns it into a snackbar).
            val selectedFile = files?.find { it.name == fileName }
                ?: throw IllegalStateException(
                    getString(R.string.history_file_missing)
                )
            val mode = PreferenceHelper.getSelectedType(requireContext()).ordinal
            // Trigger-side pairing point for the stall adjudication (#54):
            // logcat/file then shows dispatched → start command → parsing.
            FileLog.d(TAG, "Import dispatched: ${selectedFile.name} (${selectedFile.length()} bytes, mode $mode)")
            Benchmarker.start()
            core.startServiceWithData(null, mode, selectedFile.name)
            callParseView()
        } catch (e: Exception) {
            hideProgressDialog()
            view?.let { Snackbar.make(it, e.message ?: getString(R.string.error_empty_fields), Snackbar.LENGTH_SHORT).show() }
        }
    }

    /**
     * Starts the combined (union) parse for the checked history files.
     * The names came from this very directory; a miss means a file
     * vanished since the list was drawn - below two survivors there is
     * no union left to import.
     */
    private fun startParseFromCombinedSelection(fileNames: List<String>) {
        showProgressDialog()
        try {
            val selection = fileNames.filter { name ->
                files?.any { it.name == name } == true
            }
            if (selection.size < 2) {
                throw IllegalStateException(getString(R.string.history_file_missing))
            }
            val mode = PreferenceHelper.getSelectedType(requireContext()).ordinal
            FileLog.d(
                TAG,
                "Import dispatched: combined ${selection.size} file(s) (mode $mode)"
            )
            Benchmarker.start()
            core.startCombinedSelection(selection, mode)
            callParseView()
        } catch (e: Exception) {
            hideProgressDialog()
            view?.let {
                Snackbar.make(it, e.message ?: getString(R.string.error_empty_fields), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun onHistoryDeleted() {
        updateFiles()
        _newestHistoryFile.value = newestHistoryDisplayName()
        view?.let { Snackbar.make(it, R.string.history_deleted, Snackbar.LENGTH_SHORT).show() }
    }

    /**
     * is called on successful file selection and saves it to storage.
     */
    private fun onFileSelected(fileUri: Uri) {  // dialog is already up from the upload click
        try {
            handleFileSelected(fileUri)
        } catch (e: Exception) {
            hideProgressDialog()
            FileLog.e("MainFragment", "Error handling selected file: $e")
            view?.let { Snackbar.make(it, e.message ?: getString(R.string.error_while_parsing), Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun handleFileSelected(fileUri: Uri) {
        val dateFormat = SimpleDateFormat(HISTORY_FILE_PATTERN)
        val now = Date()
        val time = dateFormat.format(now)
        val filename = "$time.csv"
        val list = FileUtil.getFileContentFromUri(requireContext(), fileUri)
        // The EXACT bytes the write loop below produces (each line + "\n"),
        // knowable BEFORE touching the disk: a re-upload of a
        // content-identical file must UPDATE the existing history entry
        // (mtime refresh, newest sort) instead of creating a duplicate row

        val incomingBytes = (list.joinToString("\n") + "\n").toByteArray()
        val duplicate = HistoryFileUtil.findIdentical(
            files.orEmpty().filter { it.isFile && it.name.endsWith(".csv") },
            incomingBytes
        )
        val importFile = if (duplicate != null) {
            duplicate.setLastModified(System.currentTimeMillis())
            FileLog.i(TAG, "Dedup: re-upload content-identical to ${duplicate.name}; refreshing the entry.")
            duplicate
        } else {
            try {
                requireContext().openFileOutput(filename, Context.MODE_PRIVATE).use { fos ->
                    for (element in list) {
                        fos.write(element.toByteArray())
                        fos.write("\n".toByteArray())
                    }
                }
            } catch (e: IOException) {
                FileLog.e(TAG, "Error while writing to file: $e")
            }
            File(requireContext().filesDir, filename)
        }
        updateFiles()
        val mode = PreferenceHelper.getSelectedType(requireContext()).ordinal
        FileLog.d(TAG, "Import dispatched: ${importFile.name} (${importFile.length()} bytes, mode $mode)")
        Benchmarker.start()
        core.startServiceWithData(null, mode, importFile.name)
        callParseView(showDedupNote = duplicate != null)
    }

    private fun callParseView(saveToDB: Boolean = true, showDedupNote: Boolean = false) {
        if (saveToDB && FirebaseAuth.getInstance().currentUser != null) {
            core.saveDataToFirebase()
        }
        hideProgressDialog()
        findNavController().navigate(
            R.id.parseFragment,
            if (showDedupNote) bundleOf(ParseFragment.ARG_DEDUP_NOTE to true) else null
        )
    }

    private fun onBtnUploadClick() {
        showProgressDialog()
        pickFile.launch("*/*")
    }

    private fun loadFromFireBaseDB() {
        showProgressDialog()
        val intent = Intent(requireContext(), CoreService::class.java)
        intent.action = core.ACTION_START_SERVICE_WITH_FIREBASE_DATA
        requireContext().startService(intent)
        callParseView(saveToDB = false)
    }

    fun showProgressDialog() {
        // Idempotent: a second call must not create a second dialog - the
        // first one would then be leaked (non-cancelable and never dismissed).
        if (progressDialog?.isShowing == true) return
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.progress_icon)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        progressDialog = dialog
    }

    fun hideProgressDialog() {
        val dialog = progressDialog ?: return
        progressDialog = null
        if (dialog.isShowing) dialog.dismiss()
    }
}
