package at.msd.friehs_bicha.cdcsvparser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SettingsActivity : AppCompatActivity() {
    lateinit var appTypeSpinner: Spinner
    lateinit var useStrictTypeCheckbox: CheckBox
    lateinit var btnLogout: Button
    lateinit var btnDeleteUser: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        appTypeSpinner = findViewById(R.id.wallet_type_spinner)
        useStrictTypeCheckbox = findViewById(R.id.use_strict_wallet_type_checkbox)
        btnLogout = findViewById(R.id.btn_logout)
        btnDeleteUser = findViewById(R.id.btn_delete_account)

        // load the stored values for the spinner and checkbox
        selectedType = PreferenceHelper.getSelectedType(this)
        useStrictType = PreferenceHelper.getUseStrictType(this)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        // set the selected app type
        appTypeSpinner.setSelection(selectedType!!.ordinal)
        useStrictTypeCheckbox.isEnabled = selectedType != AppType.CroCard
        useStrictTypeCheckbox.isChecked = useStrictType
        useStrictTypeCheckbox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            useStrictType = isChecked
            PreferenceHelper.setUseStrictType(this@SettingsActivity, isChecked)
        })

        appTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                selectedType = AppType.values()[position]
                if (selectedType is AppType)PreferenceHelper.setSelectedType(this@SettingsActivity,
                    selectedType!!
                )
                useStrictTypeCheckbox.isEnabled = position != 0
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        if (user == null) {
            btnLogout.visibility = View.GONE
            btnDeleteUser.visibility = View.GONE
        }

        btnDeleteUser.setOnClickListener {
            val db = Firebase.firestore
            db.collection("user").document(user!!.uid).set(hashMapOf("deleted" to true))

            db.collection("user").document(user.uid).delete().addOnCompleteListener {
                if (it.isSuccessful) {  //TODO does not work yet
                    FileLog.d("Settings-DeleteUser", "User deleted from database.")
                }
                else
                {
                    FileLog.d("Settings-DeleteUser", "User could not be deleted from database.")
                }
            }

            user.delete().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    FileLog.d("Settings-DeleteUser", "User account deleted.")
                } else {
                    if (task.exception != null) {
                        FileLog.d("Settings-DeleteUser", task.exception.toString())
                    }
                    if (task.exception.toString().contains("requires recent authentication")) {
                        FileLog.d("Settings-DeleteUser", "User needs to reauthenticate.")   //TODO handle this(user has to relogin)
                        Toast.makeText(this, "User needs to reauthenticate.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        var useStrictType = false
        var selectedType: AppType = AppType.CdCsvParser
    }
}