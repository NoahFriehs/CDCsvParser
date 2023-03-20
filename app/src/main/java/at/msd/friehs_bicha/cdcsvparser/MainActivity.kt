package at.msd.friehs_bicha.cdcsvparser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import java.io.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    var context: Context? = null
    var appModel: AppModel? = null
    var files: Array<File>? = null

    /**
     * sets the buttons and spinner and also fills the global vars files and context the first time
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = applicationContext

        //get the elements from the xml.
        val dropdown = findViewById<Spinner>(R.id.spinner_history)
        val btnParse = findViewById<Button>(R.id.btn_parse)
        val btnHistory = findViewById<Button>(R.id.btn_history)
        btnParse.setOnClickListener { view -> onBtnUploadClick(view) }
        settingsButton()
        updateFiles()

        //disable spinner and history or fill spinner
        if (files!!.isEmpty()) {
            setHistory("disabled", dropdown, btnHistory)
        } else {
            setSpinner(dropdown)
            btnHistory.setOnClickListener { onBtnHistoryClick(dropdown) }
        }
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
        if (files!!.size == 0) {
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
        val res = resources
        val drawable = res.getDrawable(R.drawable.round_button_layer_list)
        when (type) {
            "disabled" -> {
                // Disable the button
                btnHistory.isEnabled = false
                btnHistory.setBackgroundColor(Color.LTGRAY)
                btnHistory.setTextColor(Color.DKGRAY)
                btnHistory.background = drawable
                //Disable the dropdown
                val items = arrayOf("No History")
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
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
    }

    /**
     * Fills a spinner with parsed names of the global file var
     *
     * @param spinner spinner to fill
     */
    private fun setSpinner(spinner: Spinner) {
        val fileNames = arrayOfNulls<String>(files!!.size)
        val sdf = SimpleDateFormat("M-d-yyyy-hh-mm-ss")
        val dateFormat = SimpleDateFormat("d.M hh:mm")
        var filename: String
        var date: Date?
        for (i in files!!.indices) {
            filename = files!![i].name
            filename = filename.substring(0, filename.length - 4)
            try {
                date = sdf.parse(filename)
                filename = dateFormat.format(date)
            } catch (e: ParseException) {
                e.printStackTrace()
                // TODO error message
            }
            fileNames[i] = filename
        }
        val fileNamesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fileNames)
        spinner.adapter = fileNamesAdapter
    }

    /**
     * Gets the file selected in spinner and reads it to call the parse view
     *
     * @param spinner the spinner to look at
     */
    private fun onBtnHistoryClick(spinner: Spinner) {
        val position = spinner.selectedItemPosition
        val selectedFile = files!![position]
        val list = getFileContent(selectedFile)
        try {
            appModel = AppModel(list, PreferenceHelper.getSelectedType(this), PreferenceHelper.getUseStrictType(this))
            callParseView()
        } catch (e: Exception) {
            val text: CharSequence? = e.message
            val duration = Toast.LENGTH_SHORT
            val toast = Toast.makeText(context, text, duration)
            toast.show()
        }
    }

    /**
     * is called on successful file select and saves it to storage.
     * Also reads the file and then calls the parse view
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICKFILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Get the URI of the selected file
            val fileUri = data.data
            //creat filename with format M-d-y-H-m-s
            val dateFormat = SimpleDateFormat("M-d-y-H-m-s")
            val now = Date()
            val time = dateFormat.format(now)
            val filename = "$time.csv"
            val list = getFileContentFromUri(fileUri)
            try {
                context!!.openFileOutput(filename, MODE_APPEND).use { fos ->
                    for (element in list) {
                        fos.write(element.toByteArray())
                        fos.write("\n".toByteArray()) // add a newline after each element
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            //delete oldest file if alredy 7 files in array
            updateFiles()
            while (files!!.size > 7) {
                files!![0].delete()
                updateFiles()
            }
            try {
                appModel = AppModel(list, PreferenceHelper.getSelectedType(this), PreferenceHelper.getUseStrictType(this))
                callParseView()
            } catch (e: IllegalArgumentException) {
                context = applicationContext
                val text: CharSequence? = e.message
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(context, text, duration)
                toast.show()
            } catch (e: RuntimeException) {
                val context = applicationContext
                val text: CharSequence? = e.message
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(context, text, duration)
                toast.show()
                println(e.message)
                callParseView()
            }
        }
    }

    private fun callParseView() {
        saveToDB()
        val intent = Intent(this@MainActivity, ParseActivity::class.java)
        intent.putExtra("AppModel", appModel)
        startActivity(intent)
    }

    /**
     * start action to let the user select a file
     */
    fun onBtnUploadClick(view: View?) {
        // Create an Intent object to allow the user to select a file
        val chooseFile = Intent(Intent.ACTION_GET_CONTENT)

        // Set the type of file that the user can select
        chooseFile.type = "*/*"

        // Start the activity to let the user select a file
        startActivityForResult(chooseFile, PICKFILE_REQUEST_CODE)
    }

    /**
     * gets the file from the url then reads it and returns it
     *
     * @param uri url to file
     * @return the content of the file in a ArrayList<Sting>
    </Sting> */
    fun getFileContentFromUri(uri: Uri?): ArrayList<String> {
        val fileContents = ArrayList<String>()
        try {
            // Get the ContentResolver for the current context.
            val resolver = contentResolver

            // Open an InputStream for the file represented by the Uri.
            val inputStream = resolver.openInputStream(uri!!)

            // Create a BufferedReader to read the file contents.
            val reader = BufferedReader(InputStreamReader(inputStream))

            // Read the file line by line and add each line to the fileContents list.
            var line: String? = ""
            while (line != null) {
                line = reader.readLine()
                if (line == null) break
                fileContents.add(line)
            }

            // Close the BufferedReader and InputStream.
            reader.close()
            inputStream!!.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return fileContents
    }

    /**
     * reads a file and returns it
     *
     * @param file a file
     * @return the content of the file in a ArrayList<Sting>
    </Sting> */
    fun getFileContent(file: File?): ArrayList<String> {
        val fileContents = ArrayList<String>()
        try {

            // Create a BufferedReader to read the file contents.
            val reader = BufferedReader(FileReader(file))

            // Read the file line by line and add each line to the fileContents list.
            var line: String? = ""
            while (line != null) {
                line = reader.readLine()
                if (line == null) break
                fileContents.add(line)
            }

            // Close the BufferedReader and InputStream.
            reader.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return fileContents
    }

    fun settingsButton() {
        val settingsButton = findViewById<Button>(R.id.settings_button)
        settingsButton.setOnClickListener { view: View? ->
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun saveToDB(): Boolean {
        return appModel!!.setInAndroidDB(applicationContext)
    }

    companion object {
        private const val PICKFILE_REQUEST_CODE = 1
    }
}