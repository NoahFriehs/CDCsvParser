package at.msd.friehs_bicha.cdcsvparser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {
    lateinit var appTypeSpinner: Spinner
    lateinit var useStrictTypeCheckbox: CheckBox
    lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        appTypeSpinner = findViewById(R.id.wallet_type_spinner)
        useStrictTypeCheckbox = findViewById(R.id.use_strict_wallet_type_checkbox)
        btnLogout = findViewById(R.id.btn_logout)
        val settings = getSharedPreferences(PREFS_NAME, 0)
        // load the stored values for the spinner and checkbox
        val storedType = settings.getInt(TYPE_KEY, 0)
        useStrictType = settings.getBoolean(USE_STRICT_TYPE_KEY, false)

        // set the selected app type
        selectedType = AppType.values()[storedType]
        appTypeSpinner.setSelection(storedType)
        useStrictTypeCheckbox.isChecked = useStrictType
        useStrictTypeCheckbox.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            useStrictType = isChecked
            val settings = getSharedPreferences(PREFS_NAME, 0)
            val editor = settings.edit()
            editor.putBoolean(USE_STRICT_TYPE_KEY, useStrictType)
            editor.apply()
        })
        appTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                selectedType = AppType.values()[position]
                val settings = getSharedPreferences(PREFS_NAME, 0)
                val editor = settings.edit()
                editor.putInt(TYPE_KEY, position)
                editor.apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // do nothing
            }
        }
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        private const val PREFS_NAME = "settings_prefs"
        private const val TYPE_KEY = "app_type"
        private const val USE_STRICT_TYPE_KEY = "use_strict_app_type"
        var useStrictType = false
        var selectedType: AppType? = null
    }
}