package at.msd.friehs_bicha.cdcsvparser.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import at.msd.friehs_bicha.cdcsvparser.LoginActivity
import at.msd.friehs_bicha.cdcsvparser.MainActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity for the starting page/ splash screen
 */
class StartingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starting)

        InstanceVars.init(applicationContext)

        if (PreferenceHelper.getFastStartEnabled(applicationContext) ||
            !PreferenceHelper.getIsFirstStart(applicationContext)
        ) {
            return fastStart()
        } else {
            //continue old way
        }

        val intent = determineNextActivity()

        lifecycleScope.launch {
            delay(1000)
            startActivity(intent)
            finish()
        }
    }

    private fun determineNextActivity(): Intent {
        val user = getCurrentUser()
        return if (user != null) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
    }

    private fun getCurrentUser(): FirebaseUser? {
        return FirebaseAuth.getInstance().currentUser
    }

    private fun fastStart() {
        val isLocal =
            PreferenceHelper.getFastStartEnabled(applicationContext) && PreferenceHelper.getIsAppModelSavedLocal(
                applicationContext
            ) && false
        if (isLocal) {
            Benchmarker.start()
            AppModelManager.setInstance(AppModel())
            CoreService.appModel = AppModelManager.getInstance()
            CoreService.startService(false)
        }

        val intent = Intent(this, MainActivity::class.java)

        if (isLocal) intent.putExtra("fastStart", true)

        FileLog.d("StartingActivity", "Quick start, fast enabled: $isLocal")

        lifecycleScope.launch {
            delay(1000)
            startActivity(intent)
            finish()
        }
    }
}
