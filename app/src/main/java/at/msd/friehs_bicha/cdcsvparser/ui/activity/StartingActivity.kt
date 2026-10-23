package at.msd.friehs_bicha.cdcsvparser.ui.activity

import at.msd.friehs_bicha.cdcsvparser.App

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import at.msd.friehs_bicha.cdcsvparser.MainActivity
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.core.Core
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.core.CoreSource
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.Benchmarker
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import at.msd.friehs_bicha.cdcsvparser.util.EdgeToEdge

/**
 * Activity for the starting page/ splash screen
 */
class StartingActivity : ComponentActivity() {

    /** The core surface this activity drives. */
    private val core: CoreSource = Core

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starting)
        EdgeToEdge.enable(this, findViewById(android.R.id.content))

        if (PreferenceHelper.getFastStartEnabled(App.instance) ||
            !PreferenceHelper.getIsFirstStart(App.instance)
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

    /**
     * Always the single-activity host: it shows the login screen itself when
     * the user is not signed in.
     */
    private fun determineNextActivity(): Intent = Intent(this, MainActivity::class.java)

    private fun fastStart() {
        // Fast start (plan 003): with "Store data local" + "Enable fast
        // start" on and a local model present, the last parse is shown
        // directly. The C++ core mode loads its own persisted state (the
        // core saves it after every parse since the save fix); the Room
        // local model (built here) is the data for Kotlin core mode and
        // the fallback source when the C++ save state is missing.
        val isLocal = PreferenceHelper.getFastStartEnabled(App.instance) &&
            PreferenceHelper.getIsDataLocal(App.instance) &&
            PreferenceHelper.getIsAppModelSavedLocal(App.instance)
        if (isLocal) {
            Benchmarker.start()
            CoreService.appModel = AppModel()
            // No core-mode persist: fast start must not flip the user's
            // "Core Mode" setting (the old startService(false) did).
            core.startServiceFromLocal()
        }

        val intent = Intent(this, MainActivity::class.java)

        if (isLocal) intent.putExtra("fastStart", true)

        FileLog.d("StartingActivity", "Quick start, local model: $isLocal")

        lifecycleScope.launch {
            delay(1000)
            startActivity(intent)
            finish()
        }
    }
}
