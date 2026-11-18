package at.msd.friehs_bicha.cdcsvparser.alerting

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AlertDirection
import at.msd.friehs_bicha.cdcsvparser.app.FiredLine
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.activity.StartingActivity
import at.msd.friehs_bicha.cdcsvparser.util.StringHelper

/**
 * The delivery channel of the core's alert pass: ONE notification
 * channel ("Price & goal alerts") and the rendering of the core's FIRED
 * lines. The core owns the state, the thresholds and the math - the
 * lines are the whole contract, and each line carries the values to
 * show (the app never recomputes anything). Tapping a notification
 * launches the app (the existing behavior - no deep links invented).
 *
 * Posting from a WorkManager worker is a plain local post (no network,
 * no alarm) - the always-success worker contract is unaffected.
 */
object Notifier {
    private const val TAG = "AlertNotifier"
    private const val CHANNEL_ID = "price_goal_alerts"

    /**
     * Creates the channel when it does not exist yet (idempotent; called
     * once per delivery batch).
     */
    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
            .setName(context.getString(R.string.alert_channel_name))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun launchIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, StartingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    /**
     * A stable notification id per alert (kind + alert id): a re-fire of
     * the SAME alert after its re-arm replaces the old notification
     * instead of stacking one on top (the state is edge-triggered, so at
     * most one live notification per alert exists at a time).
     */
    private fun notificationId(kind: String, alertId: String): Int =
        (kind.hashCode() * 31 + alertId.hashCode()) and 0x7FFFFFFF

    /**
     * Renders + posts the core's FIRED lines: one notification per
     * crossing of this pass (the latched automaton is core-side, so a
     * pass never fires the same alert twice). Returns the number of
     * notifications posted. A line that does not parse is logged and
     * skipped (a contract drift, not an expected state).
     */
    fun sendAlertNotifications(context: Context, firedLines: List<String>): Int {
        val appContext = context.applicationContext
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) {
            // Delivery is blocked (permission denied or notifications
            // switched off in the system settings): the crossings stay
            // LATCHED in the core - only the notification is missed,
            // and granting later resumes delivery at the next crossing.
            FileLog.i(TAG, "Notifications blocked: ${firedLines.size} crossing(s) latched in the core; the notifications wait for the permission.")
            return 0
        }
        ensureChannel(appContext)
        var sent = 0
        for (line in firedLines) {
            val fired = FiredLine.parse(line) ?: run {
                FileLog.e(TAG, "Unparseable FIRED line (contract drift?): $line")
                continue
            }
            val title: String
            val body: String
            val id: Int
            when (fired) {
                is FiredLine.Price -> {
                    val directionText = appContext.getString(
                        if (fired.direction == AlertDirection.Above)
                            R.string.alert_direction_above
                        else
                            R.string.alert_direction_below,
                    )
                    title = appContext.getString(R.string.alert_price_title, fired.symbol)
                    body = appContext.getString(
                        R.string.alert_price_body,
                        fired.symbol,
                        StringHelper.formatAmountToString(fired.priceEur),
                        directionText,
                        StringHelper.formatAmountToString(fired.thresholdEur),
                    )
                    id = notificationId("P", fired.id)
                }
                is FiredLine.Goal -> {
                    title = appContext.getString(R.string.alert_goal_title)
                    body = appContext.getString(
                        R.string.alert_goal_body,
                        fired.label,
                        StringHelper.formatAmountToString(fired.currentEur),
                        StringHelper.formatAmountToString(fired.targetEur),
                    )
                    id = notificationId("G", fired.id)
                }
            }
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(launchIntent(appContext, id))
                .build()
            try {
                manager.notify(id, notification)
            } catch (e: SecurityException) {
                // The permission can be revoked between the check above
                // and the post (the state keeps latching in the core).
                FileLog.w(TAG, "Posting rejected (permission revoked during the pass): $e")
                continue
            }
            FileLog.i(TAG, "Posted ${fired::class.simpleName} notification for ${fired.id}")
            sent++
        }
        return sent
    }
}
