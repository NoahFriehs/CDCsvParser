package at.msd.friehs_bicha.cdcsvparser.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.msd.friehs_bicha.cdcsvparser.BuildConfig
import at.msd.friehs_bicha.cdcsvparser.alerting.Notifier
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import java.util.concurrent.TimeUnit

/**
 * Periodic background price refresh (Phase 3.2).
 *
 * Keeps the on-device price cache warm for users who open the app after it
 * has been closed for hours: the in-app flows (parse screen,
 * `refreshPricesIfStale`) already drive prices while the app is visible -
 * this job is the "still fresh on open" part.
 *
 * Runs through [AssetValue.reloadCacheSync], so it obeys exactly the same
 * rate-limit state machine as everything else (429 escalation + blackout =
 * fast-fail; no sleeps ever). A cold cache (nothing parsed yet, or a fresh
 * install) is a no-op: there is nothing to refresh, and a file full of
 * unknown tickers must not turn the background job into a search storm -
 * the id-cache/search budget limits apply to the in-app flow, not here.
 *
 * The price pass is also the alert pass: after a pass that produced FRESH
 * values the core's alert state machine runs on the cpp-core thread (the
 * core owns the state, the math and the persistence) and every crossing
 * is delivered as a notification. A no-op pass (a 429 blackout tick, a
 * failed bulk fetch) invokes the check NOT at all - the alerts then wait
 * for the next real pass (documented; the state keeps latching in the core
 * meanwhile, so nothing is lost, at most the notification waits).
 *
 * Interval: 6 h in release (the WorkManager system job then also honors
 * charging/idle windows); the 15-minute minimum in debug builds so the
 * worker is observable on the emulator.
 */
class PriceRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val assetValue = AssetValue.getInstance()
        val symbols = assetValue.cacheKeys()
        if (symbols.isEmpty()) {
            FileLog.d(TAG, "Price cache empty; nothing to refresh in the background.")
            return Result.success()
        }
        val ok = assetValue.reloadCacheSync()
        FileLog.d(TAG, "Background price refresh: ${symbols.size} symbol(s), ok=$ok")
        if (shouldRunAlertCheck(ok)) {
            runAlertPass()
        }
        // Periodic + always success: the next tick retries, and the
        // 429 fast-fail gates inside the provider make a throttled retry
        // a cheap no-op instead of a network storm (an alert-pass
        // failure above is logged and swallowed the same way - the
        // core's latch state is never corrupted by a failed delivery).
        return Result.success()
    }

    /**
     * The alert pass (the core's crossing state machine): core-owned
     * state + evaluation on the cpp-core thread, the fired lines
     * delivered as notifications. Never fails the worker (always-success
     * contract) - a failure is logged, the state keeps latching in the
     * core.
     */
    private fun runAlertPass() {
        try {
            val fired = CoreService.alertPass()
            val sent = if (fired.isEmpty()) 0 else Notifier.sendAlertNotifications(applicationContext, fired)
            FileLog.i(TAG, "Alert pass: ${fired.size} crossing(es), $sent notification(s).")
        } catch (e: Exception) {
            FileLog.e(TAG, "Alert pass failed (the core's latch state stays intact): $e")
        }
    }

    companion object {
        const val TAG = "PriceRefreshWorker"
        const val UNIQUE_ID = "cdc_price_refresh"

        /**
         * The worker's alert-hook decision (pure, JVM-tested): the core's
         * state machine evaluates ONLY when the price pass produced
         * fresh values - a no-op tick (a blackout, a failed bulk fetch)
         * must not evaluate (the latency ceiling is the pass cadence).
         */
        internal fun shouldRunAlertCheck(passProducedFreshValues: Boolean): Boolean =
            passProducedFreshValues

        /** Enqueues (or keeps) the unique periodic refresh job. */
        fun enqueue(applicationContext: Context) {
            val minutes: Long = if (BuildConfig.DEBUG) 15 else 6 * 60
            val request = PeriodicWorkRequestBuilder<PriceRefreshWorker>(
                minutes,
                TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_ID, ExistingPeriodicWorkPolicy.KEEP, request)
            FileLog.i(TAG, "Enqueued price refresh periodic work (every $minutes min).")
        }

        /**
         * DEBUG builds only: runs the worker ONCE right now (a one-shot
         * beside the periodic job) so the price + alert pass is
         * testable without waiting for the interval. Release builds are
         * a no-op (the flag is also why the whole block strips).
         */
        fun runOnceNow(context: Context) {
            if (!BuildConfig.DEBUG) return
            val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_ID + "_once", ExistingWorkPolicy.REPLACE, request)
            FileLog.i(TAG, "Enqueued a one-shot price refresh (debug trigger).")
        }
    }
}
