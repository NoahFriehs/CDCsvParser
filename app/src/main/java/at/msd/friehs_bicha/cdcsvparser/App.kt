package at.msd.friehs_bicha.cdcsvparser

import android.app.Application
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.work.PriceRefreshWorker

/**
 * Application entry point: boots the [PreferenceHelper] (DataStore) cache
 * and enqueues the periodic price-refresh [PriceRefreshWorker] before any
 * activity runs. `InstanceVars` stays owned by the activities/fragments
 * that need the DB (unchanged behavior).
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferenceHelper.init(this)
        PriceRefreshWorker.enqueue(this)
    }
}
