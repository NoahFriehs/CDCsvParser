package at.msd.friehs_bicha.cdcsvparser

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.HistoryDialogFragment
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.MainScreen
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker
import at.msd.friehs_bicha.cdcsvparser.util.FileUtil
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Main screen (Compose surface, P3.1): import a CSV (new or from history)
 * and jump into the overview. The SAF upload flow, the history dialog and
 * the progress dialog stay in the fragment.
 */
class MainFragment : Fragment() {

    var files: Array<File>? = null
    private var progressDialog: Dialog? = null

    /** Display name of the newest history file (recomputed on onResume). */
    private val _newestHistoryFile = mutableStateOf<String?>(null)
    private val _isSignedIn = mutableStateOf(FirebaseAuth.getInstance().currentUser != null)

    // SAF file picker (ActivityResult API): no storage permissions are
    // needed for ACTION_GET_CONTENT.
    private val pickFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) onFileSelected(uri) else hideProgressDialog()
        }

    companion object {
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
                    MainScreen(
                        isSignedIn = _isSignedIn.value,
                        newestHistoryFile = _newestHistoryFile.value,
                        onUploadClick = { onBtnUploadClick() },
                        onHistoryClick = {
                            HistoryDialogFragment().show(childFragmentManager, "history")
                        },
                        onLoadFromDbClick = { loadFromFireBaseDB() },
                        onSettingsClick = { findNavController().navigate(R.id.settingsFragment) },
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
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The launcher activity can pass "fastStart" to go straight to parsing.
        if (requireActivity().intent.hasExtra("fastStart")) {
            fastStart()
        }
    }

    override fun onResume() {
        super.onResume()
        // The parsed cards are strings from the last price pass; refresh them
        // in the background once the 5-minute cache TTL started to expire.
        CoreService.refreshPricesIfStale()
        _isSignedIn.value = FirebaseAuth.getInstance().currentUser != null
        updateFiles()
        _newestHistoryFile.value = newestHistoryDisplayName()
    }

    private fun fastStart() {
        findNavController().navigate(R.id.parseFragment)
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
            val selectedFile = files!!.first { it.name == fileName }
            Benchmarker.start()
            CoreService.startServiceWithData(
                null,
                PreferenceHelper.getSelectedType(requireContext()).ordinal,
                selectedFile.name
            )
            callParseView()
        } catch (e: Exception) {
            hideProgressDialog()
            view?.let { Snackbar.make(it, e.message ?: getString(R.string.error_empty_fields), Snackbar.LENGTH_SHORT).show() }
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
        try {
            requireContext().openFileOutput(filename, Context.MODE_PRIVATE).use { fos ->
                for (element in list) {
                    fos.write(element.toByteArray())
                    fos.write("\n".toByteArray())
                }
            }
        } catch (e: IOException) {
            FileLog.e("MainFragment", "Error while writing to file: $e")
        }
        updateFiles()
        while (files!!.size > 7) {
            files!![0].delete()
            updateFiles()
        }
        Benchmarker.start()
        CoreService.startServiceWithData(
            null,
            PreferenceHelper.getSelectedType(requireContext()).ordinal,
            filename
        )
        callParseView()
    }

    private fun callParseView(saveToDB: Boolean = true) {
        if (saveToDB && FirebaseAuth.getInstance().currentUser != null) {
            CoreService.saveDataToFirebase()
        }
        hideProgressDialog()
        findNavController().navigate(R.id.parseFragment)
    }

    private fun onBtnUploadClick() {
        showProgressDialog()
        pickFile.launch("*/*")
    }

    private fun loadFromFireBaseDB() {
        showProgressDialog()
        val intent = Intent(requireContext(), CoreService::class.java)
        intent.action = CoreService.ACTION_START_SERVICE_WITH_FIREBASE_DATA
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
