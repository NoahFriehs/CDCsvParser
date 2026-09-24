package at.msd.friehs_bicha.cdcsvparser

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.databinding.ActivitySettingsBinding
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.activity.AboutUsActivity
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    lateinit var appTypeSpinner: Spinner
    lateinit var coreModeSpinner: Spinner
    lateinit var useStrictTypeCheckbox: CheckBox
    lateinit var cbStoreDataLocal: CheckBox
    lateinit var cbEnableFastStart: CheckBox
    lateinit var btnLogout: Button
    lateinit var btnLogin: Button
    lateinit var btnDeleteUser: Button
    lateinit var btnAboutUs: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val actionBar = supportActionBar
        actionBar!!.setDisplayHomeAsUpEnabled(true)

        appTypeSpinner = binding.walletTypeSpinner
        coreModeSpinner = binding.coreModeSpinner
        useStrictTypeCheckbox = binding.useStrictWalletTypeCheckbox
        cbStoreDataLocal = findViewById(R.id.cb_store_data_local)
        cbEnableFastStart = findViewById(R.id.cb_enable_fast_start)
        btnAboutUs = findViewById(R.id.btn_about_us)
        btnLogout = findViewById(R.id.btn_logout)
        btnLogin = findViewById(R.id.btn_login)
        btnDeleteUser = findViewById(R.id.btn_delete_account)

        // load the stored values for the spinner and checkbox
        selectedType = PreferenceHelper.getSelectedType(this)
        useStrictType = PreferenceHelper.getUseStrictType(this)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        // set the selected app type
        appTypeSpinner.setSelection(selectedType.ordinal)
        coreModeSpinner.setSelection(0)
        if (!PreferenceHelper.getUseCpp(this)) {
            coreModeSpinner.setSelection(1)
        }
        useStrictTypeCheckbox.isEnabled = selectedType != AppType.CroCard
        useStrictTypeCheckbox.isChecked = useStrictType
        useStrictTypeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            useStrictType = isChecked
            PreferenceHelper.setUseStrictType(this@SettingsActivity, isChecked)
        }

        cbStoreDataLocal.isChecked = PreferenceHelper.getIsDataLocal(this)
        cbStoreDataLocal.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.setIsDataLocal(this@SettingsActivity, isChecked)
            cbEnableFastStart.isEnabled = cbStoreDataLocal.isEnabled && cbStoreDataLocal.isChecked
        }

        cbEnableFastStart.isChecked = PreferenceHelper.getFastStartEnabled(this)
        cbEnableFastStart.isEnabled = cbStoreDataLocal.isEnabled && cbStoreDataLocal.isChecked
        cbEnableFastStart.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.setFastStartEnabled(this@SettingsActivity, isChecked)
        }

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
                useStrictTypeCheckbox.isEnabled = position != 0
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        coreModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int,
                id: Long
            ) {
                useCpp = position == 0
                PreferenceHelper.setUseCpp(
                    this@SettingsActivity,
                    useCpp
                )
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
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

        btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
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

    override fun onResume() {
        super.onResume()

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            btnLogout.visibility = View.GONE
            btnDeleteUser.visibility = View.GONE
            btnLogin.visibility = View.VISIBLE
        } else {
            btnLogout.visibility = View.VISIBLE
            btnDeleteUser.visibility = View.VISIBLE
            btnLogin.visibility = View.GONE
        }
    }

    private fun deleteUser(user: FirebaseUser?) {
        if (user == null) {
            FileLog.e("Settings-DeleteUser", "deleteUser: user is null")
            return
        }
        val db = Firebase.firestore
        // Delete the data document first and do NOT write a placeholder before
        // it: if the delete fails (offline, rules, quota) the user's document
        // must stay untouched instead of being wiped by the placeholder write.
        db.collection("user").document(user.uid).delete().addOnCompleteListener {
            if (it.isSuccessful) {
                FileLog.d("Settings-DeleteUser", "User deleted from database.")
            } else {
                FileLog.e("Settings-DeleteUser", "User could not be deleted from database: ${it.exception}")
            }
        }

        user.delete().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                FileLog.d("Settings-DeleteUser", "User account deleted.")
                Toast.makeText(this, resources.getString(R.string.user_deleted), Toast.LENGTH_LONG)
                    .show()
            } else {
                val exception = task.exception
                if (exception != null) {
                    FileLog.d("Settings-DeleteUser", exception.toString())
                }
                if (exception?.toString()?.contains("requires recent authentication") == true) {
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




    companion object {
        var useStrictType = false
        var selectedType: AppType = AppType.CdCsvParser
        var useCpp = true
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