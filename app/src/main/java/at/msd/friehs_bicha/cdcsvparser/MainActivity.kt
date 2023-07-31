package at.msd.friehs_bicha.cdcsvparser

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.app.AppSettings
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.grpc.internal.SharedResourceHolder.Resource
import java.io.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    var context: Context? = null
    var appModel: AppModel? = null
    var files: Array<File>? = null
    var user = FirebaseAuth.getInstance().currentUser
    private lateinit var progressDialog: Dialog

    companion object {
        private const val PICKFILE_REQUEST_CODE = 1
        const val readExternalStorageRequestCode: Int = 102
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
        val drawable = ResourcesCompat.getDrawable(resources, R.drawable.round_button_layer_list, null)
        when (type) {
            "disabled" -> {
                // Disable the button
                btnHistory.isEnabled = false
                btnHistory.setBackgroundColor(Color.LTGRAY)
                btnHistory.setTextColor(Color.DKGRAY)
                btnHistory.background = drawable
                //Disable the dropdown
                val items = arrayOf(getResources().getString(R.string.no_history))
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
    private fun setSpinner(spinner: Spinner) {
        val fileNames = ArrayList<String>()
        val sdf = SimpleDateFormat("M-d-yyyy-hh-mm-ss")
        val dateFormat = SimpleDateFormat("d.M hh:mm")
        var filename: String
        var date: Date?
        for (f in files!!) {
            if (!f.isFile || !f.name.endsWith(".csv")) continue
            filename = f.name
            filename = filename.substring(0, filename.length - 4)
            try {
                date = sdf.parse(filename)
                filename = dateFormat.format(date)
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
        val list = getFileContent(selectedFile)
        try {
            appModel = AppModel(
                list,
                PreferenceHelper.getSelectedType(this),
                PreferenceHelper.getUseStrictType(this)
            )
            callParseView()
        } catch (e: Exception) {
            hideProgressDialog()
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
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICKFILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            showProgressDialog()
            // Get the URI of the selected file
            val fileUri = data.data
            //create filename with format M-d-y-H-m-s
            val dateFormat = SimpleDateFormat("M-d-y-H-m-s")
            val now = Date()
            val time = dateFormat.format(now)
            val filename = "$time.csv"
            val list = getFileContentFromUri(fileUri)
            try {
                applicationContext.openFileOutput(filename, MODE_APPEND).use { fos ->
                    for (element in list) {
                        fos.write(element.toByteArray())
                        fos.write("\n".toByteArray()) // add a newline after each element
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                FileLog.e("MainActivity", ":  Error while writing to file : $e")
            }
            //delete oldest file if already 7 files in array
            updateFiles()
            while (files!!.size > 7) {
                files!![0].delete()
                updateFiles()
            }
            try {
                appModel = AppModel(
                    list,
                    PreferenceHelper.getSelectedType(this),
                    PreferenceHelper.getUseStrictType(this)
                )
                callParseView()
            } catch (e: IllegalArgumentException) {
                FileLog.e("MainActivity", ":  Error while loading files : $e")
                hideProgressDialog()
                context = applicationContext
                val text: CharSequence? = e.message
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(context, text, duration)
                toast.show()
            } catch (e: RuntimeException) {
                FileLog.e("MainActivity", ":  Error while loading files : $e")
                val context = applicationContext
                val text: CharSequence? = e.message
                val duration = Toast.LENGTH_SHORT
                val toast = Toast.makeText(context, text, duration)
                toast.show()
                callParseView()
            }

        }
    }

    private fun callParseView(saveToDB: Boolean = true) {
        if (appModel == null) {
            Toast.makeText(context, "No valid data loaded", Toast.LENGTH_LONG).show()
            FileLog.e("MainActivity", "No valid data loaded")
            return
        }
        if (saveToDB) {
            if (user != null) saveToFireBaseDB()
        }
        AppModelManager.setInstance(appModel!!)
        val intent = Intent(this@MainActivity, ParseActivity::class.java)
        hideProgressDialog()
        startActivity(intent)
    }


    /**
     * start action to let the user select a file
     */
    private fun onBtnUploadClick() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                // Show an explanation to the user
                Toast.makeText(
                    this,
                    "Permission needed to read files to be able to load the file",
                    Toast.LENGTH_SHORT
                ).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    readExternalStorageRequestCode
                )
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    readExternalStorageRequestCode
                )
            }
        } else {
            startChooseFile()
        }
    }


    private fun startChooseFile() {
        // Create an Intent object to allow the user to select a file
        val chooseFile = Intent(Intent.ACTION_GET_CONTENT)

        // Set the type of file that the user can select
        chooseFile.type = "*/*"

        // Start the activity to let the user select a file
        startActivityForResult(chooseFile, PICKFILE_REQUEST_CODE)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == readExternalStorageRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted. Continue the action or workflow
                // in your app.
                startChooseFile()
            } else {
                FileLog.w("MainActivity", ": permission denied for external storage access")
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * gets the file from the url then reads it and returns it
     *
     * @param uri url to file
     * @return the content of the file in a ArrayList<Sting>
    </Sting> */
    private fun getFileContentFromUri(uri: Uri?): ArrayList<String> {
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
            FileLog.e("MainActivity", ":  Error while getting file content from Uri : $e")

        }
        return fileContents
    }

    /**
     * reads a file and returns it
     *
     * @param file a file
     * @return the content of the file in a ArrayList<Sting>
    </Sting> */
    private fun getFileContent(file: File?): ArrayList<String> {
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
            FileLog.e("MainActivity", ":  Error while reading file : $e")
        }
        return fileContents
    }

    private fun settingsButton() {
        val settingsButton = findViewById<Button>(R.id.settings_button)
        settingsButton.setOnClickListener {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            startActivity(intent)
        }
    }


    private fun saveToFireBaseDB() {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val db = Firebase.firestore

        var userMap = hashMapOf<String, Any>()

        db.collection("user").document(uid).get().addOnSuccessListener {
            if (it != null) {
                userMap = it.data as HashMap<String, Any>
            }

            val appSettings = AppSettings(
                uid,
                PreferenceHelper.getSelectedType(this),
                PreferenceHelper.getUseStrictType(this)
            )
            val appSettingsMap = appSettings.toHashMap()

            userMap.putAll( if (appModel?.appType!! != AppType.CdCsvParser) {
                hashMapOf(
                    "appModelCard" to appModel!!.toHashMap(),
                    "appSettings" to appSettingsMap
                )
            } else {
                hashMapOf(
                    "appModel" to appModel!!.toHashMap(),
                    "appSettings" to appSettingsMap
                )
            })


            db.collection("user").document(uid).set(userMap).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Data saved successfully
                    Toast.makeText(this, "Data saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    // Handle database error
                    Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show()
                    FileLog.e("MainActivity", "Error saving data to database ${task.exception}")
                }
            }
        }


    }

    private fun loadFromFireBaseDB() {
        showProgressDialog()
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        //val database = FirebaseDatabase.getInstance()
        val db = Firebase.firestore
        // Add data to the txApp object

        val user = db.collection("user").document(uid)
        user.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val userMap = document.data as HashMap<String, Any>?
                    if (userMap == null) {
                        Toast.makeText(this, "Error loading data", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    val appSettings = userMap["appSettings"] as HashMap<String, Any>?

                    val dbVersion = appSettings?.get("dbVersion")

                    if (StringHelper.compareVersions(dbVersion as String, "1.0.0")) {
                        //when lower than this than it does not work with the db, has to switch to older version
                        context = applicationContext
                        val text =
                            "Your database is not compatible with this version of the app. Please downgrade the app or override the database with a new upload."
                        val duration = Toast.LENGTH_LONG
                        val toast = Toast.makeText(context, text, duration)
                        toast.show()
                        return@addOnSuccessListener
                    }

                    var txAppMap = userMap["appModel"] as HashMap<String, Any>?
                    if (txAppMap == null) {
                        txAppMap = userMap["appModelCard"] as HashMap<String, Any>?
                    }

                    txAppMap = if (PreferenceHelper.getSelectedType(applicationContext) == AppType.CdCsvParser || userMap["appModelCard"] == null) {
                        userMap["appModel"] as HashMap<String, Any>?
                    } else {
                        userMap["appModelCard"] as HashMap<String, Any>?
                    }

                    val appType = AppType.valueOf(appSettings["appType"] as String)

                    val useStrictType = appSettings["useStrictType"] as Boolean

                    var dbOutsideWallets: ArrayList<HashMap<String, *>>? = null

                    if (txAppMap != null) {
                        val dbWallets = txAppMap["wallets"]
                        if (appType == AppType.CdCsvParser) {
                            dbOutsideWallets =
                                txAppMap["outsideWallets"] as ArrayList<HashMap<String, *>>?
                        }
                        val dbTransactions =
                            txAppMap["transactions"]
                        val amountTxFailed = txAppMap["amountTxFailed"] as Long? ?: 0
                        val appTypeString = txAppMap["appType"] as String? ?: ""
                        val appType = AppType.valueOf(appTypeString)

                        //TODO check if data is valid
                        this.appModel = AppModel(
                            dbWallets as ArrayList<HashMap<String, *>>?,
                            dbOutsideWallets,
                            dbTransactions as ArrayList<HashMap<String, *>>?,
                            appType,
                            amountTxFailed,
                            useStrictType
                        )

                        PreferenceHelper.setSelectedType(this, appType)
                        PreferenceHelper.setUseStrictType(
                            this,
                            appSettings["useStrictType"] as Boolean
                        )
                        callParseView(false)
                    } else {
                        FileLog.w(
                            "MainActivity",
                            "loadFromFireBaseDB: txAppMap is null: userMap: $userMap"
                        )
                    }
                } else {
                    // Handle database error
                    FileLog.e("MainActivity", ":  Error document is null")

                }
            }.addOnFailureListener { exception ->
                FileLog.e("MainActivity", ":  Error while loading document : ${exception.message}")

            }
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