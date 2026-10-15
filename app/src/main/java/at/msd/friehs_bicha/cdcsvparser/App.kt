package at.msd.friehs_bicha.cdcsvparser

import android.app.Application
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import at.msd.friehs_bicha.cdcsvparser.work.PriceRefreshWorker
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Application entry point: boots the crash hook (plan 007 A2), the
 * [PreferenceHelper] (DataStore) cache and the periodic price-refresh
 * [PriceRefreshWorker] before any activity runs. `InstanceVars` stays
 * owned by the activities/fragments that need the DB (unchanged behavior).
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // First, so an early-startup failure still leaves a line behind.
        installCrashHook()
        PreferenceHelper.init(this)
        PriceRefreshWorker.enqueue(this)
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
}
