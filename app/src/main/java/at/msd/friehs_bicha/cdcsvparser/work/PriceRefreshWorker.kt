package at.msd.friehs_bicha.cdcsvparser.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import at.msd.friehs_bicha.cdcsvparser.BuildConfig
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.price.AssetValue
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
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
        // Periodic + always success: the next tick retries, and the
        // 429 fast-fail gates inside the provider make a throttled retry
        // a cheap no-op instead of a network storm.
        return Result.success()
    }

    companion object {
        const val TAG = "PriceRefreshWorker"
        const val UNIQUE_ID = "cdc_price_refresh"

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
    }
}
