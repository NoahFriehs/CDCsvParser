package at.msd.friehs_bicha.cdcsvparser

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker
import at.msd.friehs_bicha.cdcsvparser.util.FileUtil
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import java.io.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    var context: Context? = null
    var files: Array<File>? = null
    var user = FirebaseAuth.getInstance().currentUser
    private lateinit var progressDialog: Dialog

    // SAF file picker (ActivityResult API): no storage permissions are
    // needed for ACTION_GET_CONTENT, so the old READ_EXTERNAL_STORAGE /
    // READ_MEDIA_* permission dance is gone.
    private val pickFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) onFileSelected(uri) else hideProgressDialog()
        }

    companion object {
        // Single source for the history file name pattern (write + parse).
        // Older versions wrote "M-d-y-H-m-s" which is still tolerated on read
        // failures (raw file name is shown instead).
        const val HISTORY_FILE_PATTERN = "yyyy-MM-dd-HH-mm-ss"
    }


    /**
     * sets the buttons and spinner and also fills the global vars files and context the first time
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = applicationContext

        user = FirebaseAuth.getInstance().currentUser

        //get the elements from the xml.
        val dropdown = findViewById<Spinner>(R.id.spinner_history)
        val btnParse = findViewById<Button>(R.id.btn_parse)
        val btnHistory = findViewById<Button>(R.id.btn_history)
        val btnLoadFromDB = findViewById<Button>(R.id.btn_loadFromDb)
        if (user == null) {
            btnLoadFromDB.visibility = View.GONE
        } else {
            btnLoadFromDB.visibility = View.VISIBLE
        }
        btnParse.setOnClickListener { onBtnUploadClick() }
        btnLoadFromDB.setOnClickListener { loadFromFireBaseDB() }
        settingsButton()
        updateFiles()

        //disable spinner and history or fill spinner
        if (files!!.isEmpty()) {
            setHistory("disabled", dropdown, btnHistory)
        } else {
            setSpinner(dropdown)
            btnHistory.setOnClickListener { onBtnHistoryClick(dropdown) }
        }

        if (intent.hasExtra("fastStart")) {
            fastStart()
        }
    }

    private fun fastStart() {
        val intent = Intent(this@MainActivity, ParseActivity::class.java)
        startActivity(intent)
    }


    /**
     * gets all files from internal file storage and updates it
     * if there are files enable history otherwise disable
     */
    override fun onRestart() {
        super.onRestart()
        updateFiles()
        val dropdown = findViewById<Spinner>(R.id.spinner_history)
        val btnHistory = findViewById<Button>(R.id.btn_history)
        if (files!!.isEmpty()) {
            setHistory("disabled", dropdown, btnHistory)
        } else {
            setHistory("enabled", dropdown, btnHistory)
            btnHistory.setOnClickListener { view: View? -> onBtnHistoryClick(dropdown) }
        }
    }


    /**
     * disables or enbales and fills a spinner
     *
     * @param type       "disabled" or "enabled"
     * @param dropdown   the Spinner element
     * @param btnHistory the button to de/activate
     */
    private fun setHistory(type: String, dropdown: Spinner, btnHistory: Button) {
        val drawable = ResourcesCompat.getDrawable(resources, R.drawable.round_button_layer_list, null)
        when (type) {
            "disabled" -> {
                // Disable the button
                btnHistory.isEnabled = false
                btnHistory.setBackgroundColor(Color.LTGRAY)
                btnHistory.setTextColor(Color.DKGRAY)
                btnHistory.background = drawable
                //Disable the dropdown
                val items = arrayOf(resources.getString(R.string.no_history))
                val adapter =
                    ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
                dropdown.adapter = adapter
                dropdown.isEnabled = false
            }

            "enabled" -> {
                // Enable the button
                btnHistory.isEnabled = true
                btnHistory.setTextColor(Color.WHITE)
                btnHistory.background = drawable
                //Enable the dropdown
                dropdown.isEnabled = true
                setSpinner(dropdown)
            }
        }
    }

    /**
     * updates the global file array to the newest
     */
    private fun updateFiles() {
        // Get the app's internal file directory
        val appDir = filesDir
        // Get a list of all files in the app's internal file directory
        files = appDir.listFiles() as Array<File>
        files = files!!.filter { it.name.endsWith(".csv") }.toTypedArray()
    }

    /**
     * Fills a spinner with parsed names of the global file var
     *
     * @param spinner spinner to fill
     */
    @SuppressLint("SimpleDateFormat")
    private fun setSpinner(spinner: Spinner) {
        val fileNames = ArrayList<String>()
        // Files imported by this version use HISTORY_FILE_PATTERN. Files from
        // older versions are simply shown with their raw file name.
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
                e.printStackTrace()
                FileLog.e("MainActivity", "setSpinner: Date parse error: $e")
            }
            fileNames.add(filename)
        }
        val fileNamesAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fileNames)
        spinner.adapter = fileNamesAdapter
    }

    /**
     * Gets the file selected in spinner and reads it to call the parse view
     *
     * @param spinner the spinner to look at
     */
    private fun onBtnHistoryClick(spinner: Spinner) {
        showProgressDialog()
        val position = spinner.selectedItemPosition
        val selectedFile = files!![position]
        val list = FileUtil.getFileContent(selectedFile)
        try {
            Benchmarker.start()
            CoreService.startServiceWithData(list, PreferenceHelper.getSelectedType(this).ordinal)
            callParseView()
        } catch (e: Exception) {
            hideProgressDialog()
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * is called on successful file select and saves it to storage.
     * Also reads the file and then calls the parse view
     */
    private fun onFileSelected(fileUri: Uri) {
        showProgressDialog()
        // create filename with format yyyy-MM-dd-HH-mm-ss (see HISTORY_FILE_PATTERN)
        val dateFormat = SimpleDateFormat(HISTORY_FILE_PATTERN)
        val now = Date()
        val time = dateFormat.format(now)
        val filename = "$time.csv"
        val list = FileUtil.getFileContentFromUri(this, fileUri)
        try {
            applicationContext.openFileOutput(filename, MODE_PRIVATE).use { fos ->
                for (element in list) {
                    fos.write(element.toByteArray())
                    fos.write("\n".toByteArray()) // add a newline after each element
                }
            }
        } catch (e: IOException) {
            FileLog.e("MainActivity", ":  Error while writing to file : $e")
        }
        //delete oldest file if already 7 files in array
        updateFiles()
        while (files!!.size > 7) {
            files!![0].delete()
            updateFiles()
        }
        Benchmarker.start()
        CoreService.startServiceWithData(
            list,
            PreferenceHelper.getSelectedType(this).ordinal
        )
        callParseView()
    }

    private fun callParseView(saveToDB: Boolean = true) {
        if (saveToDB) {
            CoreService.saveDataToFirebase()
        }
        val intent = Intent(this@MainActivity, ParseActivity::class.java)
        hideProgressDialog()
        startActivity(intent)
    }


    /**
     * start action to let the user select a file (SAF, no permissions)
     */
    private fun onBtnUploadClick() {
        showProgressDialog()
        pickFile.launch("*/*")
    }


    private fun settingsButton() {
        val settingsButton = findViewById<Button>(R.id.settings_button)
        settingsButton.setOnClickListener {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            startActivity(intent)
        }
    }


    private fun loadFromFireBaseDB() {
        showProgressDialog()
//start ACTION_START_SERVICE_WITH_FIREBASE_DATA
        val intent = Intent(this@MainActivity, CoreService::class.java)
        intent.action = CoreService.ACTION_START_SERVICE_WITH_FIREBASE_DATA
        startService(intent)

        callParseView(saveToDB = false)

    }

    fun showProgressDialog() {
        progressDialog = Dialog(this)
        progressDialog.setContentView(R.layout.progress_icon)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.show()
    }

    fun hideProgressDialog() {
        progressDialog.dismiss()
    }

}