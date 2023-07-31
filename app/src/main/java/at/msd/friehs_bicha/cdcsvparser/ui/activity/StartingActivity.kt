package at.msd.friehs_bicha.cdcsvparser.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import at.msd.friehs_bicha.cdcsvparser.LoginActivity
import at.msd.friehs_bicha.cdcsvparser.MainActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity for the starting page/ splash screen
 */
class StartingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starting)

        InstanceVars.init(applicationContext)

        if (PreferenceHelper.getFastStartEnabled(applicationContext) && PreferenceHelper.getIsAppModelSavedLocal(applicationContext)) {
            return fastStart()
        } else {
            //continue old way
        }

        val user = FirebaseAuth.getInstance().currentUser
        val intent = if (user != null) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(intent)
            finish()
        }, 1000)
    }

    private fun fastStart() {

        AppModelManager.setInstance(AppModel())

        val intent = Intent(this, MainActivity::class.java)

        intent.putExtra("fastStart", true)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(intent)
            finish()
        }, 1000)

    }
}
