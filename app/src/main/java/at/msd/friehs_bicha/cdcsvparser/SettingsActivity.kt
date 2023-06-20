package at.msd.friehs_bicha.cdcsvparser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import at.msd.friehs_bicha.cdcsvparser.MainActivity.Companion.readExternalStorageRequestCode
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.activity.AboutUsActivity
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SettingsActivity : AppCompatActivity() {
    lateinit var appTypeSpinner: Spinner
    lateinit var useStrictTypeCheckbox: CheckBox
    lateinit var btnLogout: Button
    lateinit var btnDeleteUser: Button
    lateinit var btnPermissionRequest: Button
    lateinit var btnAboutUs: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)

        appTypeSpinner = findViewById(R.id.wallet_type_spinner)
        useStrictTypeCheckbox = findViewById(R.id.use_strict_wallet_type_checkbox)
        btnPermissionRequest = findViewById(R.id.btn_permission_request)
        btnAboutUs = findViewById(R.id.btn_about_us)
        btnLogout = findViewById(R.id.btn_logout)
        btnDeleteUser = findViewById(R.id.btn_delete_account)

        // load the stored values for the spinner and checkbox
        selectedType = PreferenceHelper.getSelectedType(this)
        useStrictType = PreferenceHelper.getUseStrictType(this)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        // set the selected app type
        appTypeSpinner.setSelection(selectedType.ordinal)
        useStrictTypeCheckbox.isEnabled = false//selectedType != AppType.CroCard
        useStrictTypeCheckbox.isChecked = useStrictType
        useStrictTypeCheckbox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            useStrictType = isChecked
            PreferenceHelper.setUseStrictType(this@SettingsActivity, isChecked)
        })

        appTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                selectedType = AppType.values()[position]
                PreferenceHelper.setSelectedType(
                    this@SettingsActivity,
                    selectedType
                )
                //useStrictTypeCheckbox.isEnabled = position != 0
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            btnPermissionRequest.visibility = View.GONE
        } else {
            btnPermissionRequest.visibility = View.VISIBLE
            btnPermissionRequest.setOnClickListener {
                requestExternalStoragePermission()
            }
        }


        btnAboutUs.setOnClickListener {
            val intent = Intent(this, AboutUsActivity::class.java)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            val alertDialog = AlertDialog.Builder(this)
                .setTitle(resources.getString(R.string.logout))
                .setMessage(resources.getString(R.string.logoutQuestion))
                .setPositiveButton(resources.getString(R.string.yes)) { _, _ ->
                    logout(auth)
                }
                .setNegativeButton(resources.getString(R.string.no), null)
                .create()

            alertDialog.show()

        }

        if (user == null) {
            btnLogout.visibility = View.GONE
            btnDeleteUser.visibility = View.GONE
        }

        btnDeleteUser.setOnClickListener {
            val alertDialog = AlertDialog.Builder(this)
                .setTitle(resources.getString(R.string.delete_account))
                .setMessage(resources.getString(R.string.deleteQuestion))
                .setPositiveButton(resources.getString(R.string.yes)) { _, _ ->
                    deleteUser(user)
                }
                .setNegativeButton(resources.getString(R.string.no), null)
                .create()

            alertDialog.show()
        }
    }

    private fun deleteUser(user: FirebaseUser?) {
        val db = Firebase.firestore
        db.collection("user").document(user!!.uid).set(hashMapOf("deleted" to true))

        db.collection("user").document(user.uid).delete().addOnCompleteListener {
            if (it.isSuccessful) {
                FileLog.d("Settings-DeleteUser", "User deleted from database.")
            } else {
                FileLog.d("Settings-DeleteUser", "User could not be deleted from database.")
            }
        }

        user.delete().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                FileLog.d("Settings-DeleteUser", "User account deleted.")
                Toast.makeText(this, resources.getString(R.string.user_deleted), Toast.LENGTH_LONG)
                    .show()
            } else {
                if (task.exception != null) {
                    FileLog.d("Settings-DeleteUser", task.exception.toString())
                }
                if (task.exception.toString().contains("requires recent authentication")) {
                    FileLog.d(
                        "Settings-DeleteUser",
                        "User needs to reauthenticate."
                    )
                    Toast.makeText(
                        this,
                        resources.getString(R.string.user_needs_to_autheticate),
                        Toast.LENGTH_LONG
                    )
                        .show()
                }
            }
        }
        logout(FirebaseAuth.getInstance())
    }

    private fun logout(auth: FirebaseAuth) {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun requestExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission already granted
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
        } else {
            // Permission not granted
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                // Display a rationale for the user to grant the permission
                AlertDialog.Builder(this)
                    .setTitle(resources.getString(R.string.permission_needed))
                    .setMessage(resources.getString(R.string.permission_needed_text))
                    .setPositiveButton(
                        "ok"
                    ) { _, _ ->
                        //Prompt the user once explanation has been shown
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                            readExternalStorageRequestCode
                        )
                    }
                    .create()
                    .show()

            } else {
                // Request the permission directly without showing the rationale
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    readExternalStorageRequestCode
                )
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == readExternalStorageRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                // Permission denied
                Toast.makeText(
                    this,
                    resources.getString(R.string.permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    companion object {
        var useStrictType = false
        var selectedType: AppType = AppType.CdCsvParser
    }


    /**
     * Set the back button in action bar
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}