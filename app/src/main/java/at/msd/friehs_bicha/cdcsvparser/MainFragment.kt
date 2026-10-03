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
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
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
 * Main screen: import a CSV (new or from history) and jump into the overview.
 */
class MainFragment : Fragment() {

    var files: Array<File>? = null
    var user = FirebaseAuth.getInstance().currentUser
    private var progressDialog: Dialog? = null

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
        return inflater.inflate(R.layout.activity_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        user = FirebaseAuth.getInstance().currentUser

        val dropdown = view.findViewById<MaterialAutoCompleteTextView>(R.id.spinner_history)
        val btnParse = view.findViewById<Button>(R.id.btn_parse)
        val btnHistory = view.findViewById<Button>(R.id.btn_history)
        val btnLoadFromDB = view.findViewById<Button>(R.id.btn_loadFromDb)
        if (user == null) {
            btnLoadFromDB.visibility = View.GONE
        } else {
            btnLoadFromDB.visibility = View.VISIBLE
        }
        btnParse.setOnClickListener { onBtnUploadClick() }
        btnLoadFromDB.setOnClickListener { loadFromFireBaseDB() }
        view.findViewById<Button>(R.id.settings_button).setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }
        updateFiles()

        if (files!!.isEmpty()) {
            setHistory(view, "disabled", dropdown, btnHistory)
        } else {
            setSpinner(dropdown)
            setHistory(view, "enabled", dropdown, btnHistory)
            btnHistory.setOnClickListener { onBtnHistoryClick(dropdown) }
        }

        // The launcher activity can pass "fastStart" to go straight to parsing.
        if (requireActivity().intent.hasExtra("fastStart")) {
            fastStart()
        }
    }

    override fun onResume() {
        super.onResume()
        updateFiles()
        val view = view ?: return
        val dropdown = view.findViewById<MaterialAutoCompleteTextView>(R.id.spinner_history)
        val btnHistory = view.findViewById<Button>(R.id.btn_history)
        if (files!!.isEmpty()) {
            setHistory(view, "disabled", dropdown, btnHistory)
        } else {
            setHistory(view, "enabled", dropdown, btnHistory)
            btnHistory.setOnClickListener { onBtnHistoryClick(dropdown) }
        }
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
     * Fills the dropdown with parsed names of the global file var
     */
    @SuppressLint("SimpleDateFormat")
    private fun fileDisplayNames(): List<String> {
        val fileNames = ArrayList<String>()
        val sdf = SimpleDateFormat(HISTORY_FILE_PATTERN)
        val dateFormat = SimpleDateFormat("d.M HH:mm")
        var filename: String
        for (f in files!!) {
            if (!f.isFile || !f.name.endsWith(".csv")) continue
            filename = f.name
            filename = filename.substring(0, filename.length - 4)
            try {
                filename = dateFormat.format(sdf.parse(filename))
            } catch (e: ParseException) {
                FileLog.e("MainFragment", "setSpinner: Date parse error: $e")
            }
            fileNames.add(filename)
        }
        return fileNames
    }

    private fun setSpinner(dropdown: MaterialAutoCompleteTextView) {
        dropdown.setSimpleItems(fileDisplayNames().toTypedArray())
    }

    private fun setHistory(root: View, type: String, dropdown: MaterialAutoCompleteTextView, btnHistory: Button) {
        // The Material 3 button styles its disabled/enabled state itself
        when (type) {
            "disabled" -> {
                btnHistory.isEnabled = false
                dropdown.setSimpleItems(arrayOf(getString(R.string.no_history)))
                dropdown.isEnabled = false
            }

            "enabled" -> {
                btnHistory.isEnabled = true
                dropdown.isEnabled = true
                setSpinner(dropdown)
                dropdown.setText(getFirstItemOrNull(), false)
            }
        }
    }

    private fun getFirstItemOrNull(): String? = fileDisplayNames().firstOrNull()

    /**
     * Gets the file selected in the dropdown, reads it and starts the parse.
     */
    private fun onBtnHistoryClick(dropdown: MaterialAutoCompleteTextView) {
        showProgressDialog()
        try {
            val names = fileDisplayNames()
            val selected = dropdown.text.toString()
            val position = names.indexOf(selected).coerceAtLeast(0)
            val selectedFile = files!![position]
            val list = FileUtil.getFileContent(selectedFile)
            Benchmarker.start()
            CoreService.startServiceWithData(list, PreferenceHelper.getSelectedType(requireContext()).ordinal)
            callParseView()
        } catch (e: Exception) {
            hideProgressDialog()
            view?.let { Snackbar.make(it, e.message ?: getString(R.string.error_empty_fields), Snackbar.LENGTH_SHORT).show() }
        }
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
            list,
            PreferenceHelper.getSelectedType(requireContext()).ordinal
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
