package at.msd.friehs_bicha.cdcsvparser

import android.app.Application
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.db.AppDatabase
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.work.PriceRefreshWorker
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Application entry point: boots the crash hook, then the app
 * instances (logging + Room DB + the core library probe: this class is
 * their single owner — the old `InstanceVars` holder is gone),
 * the [PreferenceHelper] (DataStore) cache and the periodic
 * price-refresh [PriceRefreshWorker] before any activity runs.
 */
class App : Application() {

    /** The Room database (owned here; single source for all DAO access). */
    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        // First, so an early-startup failure still leaves a line behind.
        installCrashHook()
        initAppInstances()
        PreferenceHelper.init(this)
        PriceRefreshWorker.enqueue(this)
    }

    /**
     * The app-wide instances, in the old `InstanceVars.init` order
     * (logging, DB, core-library probe). Runs in `onCreate`, i.e. before
     * the first activity — strictly earlier than the old
     * activity-driven init, which is what every consumer already
     * assumed.
     */
    private fun initAppInstances() {
        instance = this
        FileLog.init()
        val db = AppDatabase.getInstance(applicationContext)
        if (db == null) {
            throw Exception("Database not initialized")
        }
        this.db = db
        CoreService.isInitialized // init core service companion to check for .so
    }

    /**
     * Plan 007 A2: before rethrowing, the full uncaught-exception stack is
     * written to the app log (`files/log/CDCsvParser.log`, tag "Crash") —
     * visible in the settings log view (plan 010). The app still crashes
     * EXACTLY as before (the previous handler is called, default or not);
     * this only guarantees that the next crash on ANY device leaves the
     * exact stack in a pullable file, no logcat needed. Early-startup
     * crashes (before `FileLog.init` ran) land in logcat only — FileLog
     * degrades to logcat when not initialized, so this is safe either way.
     */
    private fun installCrashHook() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val out = StringWriter()
                throwable.printStackTrace(PrintWriter(out))
                FileLog.e(
                    "Crash",
                    "uncaught on '${thread.name}': ${throwable.javaClass.name}: ${throwable.message}\n$out",
                )
            } catch (_: Exception) {
                // Logging must never mask the crash.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /** The process-wide application instance (the `App.instance` context). */
        lateinit var instance: App
            private set
    }
}
