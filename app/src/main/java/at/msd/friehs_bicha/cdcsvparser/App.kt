package at.msd.friehs_bicha.cdcsvparser

import android.app.Application
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.db.AppDatabase
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.CloudMigration
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.work.PriceRefreshWorker
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    /** Fire-and-forget cloud maintenance (never core-touching). */
    private val cloudScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // First, so an early-startup failure still leaves a line behind.
        installCrashHook()
        initAppInstances()
        PreferenceHelper.init(this)
        PriceRefreshWorker.enqueue(this)
        // The one-shot post-update cloud migration (re-encrypts a legacy
        // plaintext document under the key model in force): it is a
        // silent no-op on a signed-out start and retries on the next
        // launch until the document was seen or is handled.
        cloudScope.launch { CloudMigration.runFirstLaunchMigration() }
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
     * Before rethrowing, the full uncaught-exception stack is
     * written to the app log (`files/log/CDCsvParser.log`, tag "Crash") —
     * visible in the settings log view. The app still crashes
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
