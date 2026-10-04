package at.msd.friehs_bicha.cdcsvparser

import android.app.Application
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper

/**
 * Application entry point: boots [App] singletons (prefs) before any
 * activity runs. `InstanceVars` stays owned by the activities/fragments
 * that need the DB (unchanged behavior).
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferenceHelper.init(this)
    }
}
