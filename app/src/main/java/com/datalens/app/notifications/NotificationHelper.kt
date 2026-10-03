package com.datalens.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.datalens.app.MainActivity
import com.datalens.app.R
import com.datalens.app.util.ByteFormatter

/**
 * All notification posting goes through here. Channels are created once at app start.
 * Nothing is ever posted unless the user enabled the corresponding toggle *and*
 * Android's notification permission is granted.
 */
object NotificationHelper {

    const val CHANNEL_DAILY = "daily_summary"
    const val CHANNEL_LIMIT = "limit_warnings"
    const val CHANNEL_HIGH = "high_usage"

    /** Silent, low-importance channel for the persistent usage tracker. */
    const val CHANNEL_TRACKING = "usage_tracking"

    const val NOTIF_ID_DAILY = 1001
    const val NOTIF_ID_LIMIT = 1002
    const val NOTIF_ID_HIGH = 1003
    const val NOTIF_ID_TRACKING = 1004

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val daily = NotificationChannel(
            CHANNEL_DAILY,
            context.getString(R.string.channel_daily_summary_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_daily_summary_desc) }

        val limit = NotificationChannel(
            CHANNEL_LIMIT,
            context.getString(R.string.channel_limit_warning_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_limit_warning_desc) }

        val high = NotificationChannel(
            CHANNEL_HIGH,
            context.getString(R.string.channel_high_usage_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_high_usage_desc) }

        // Persistent usage tracker: low importance (no heads-up), and explicitly
        // silent — routine usage updates must never ring or vibrate.
        val tracking = NotificationChannel(
            CHANNEL_TRACKING,
            context.getString(R.string.channel_usage_tracking_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_usage_tracking_desc)
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannels(listOf(daily, limit, high, tracking))
    }

    /** True when Android will actually show our notifications. */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showDailySummary(
        context: Context,
        usedBytes: Long,
        topAppName: String?,
        topAppBytes: Long,
    ) {
        val text = if (usedBytes <= 0L) {
            context.getString(R.string.notif_daily_zero_text)
        } else {
            val base = "Your apps used ${ByteFormatter.format(usedBytes)} of mobile data today."
            if (topAppName != null && topAppBytes > 0L) {
                "$base Top: $topAppName (${ByteFormatter.format(topAppBytes)})."
            } else {
                base
            }
        }
        post(
            context,
            channel = CHANNEL_DAILY,
            id = NOTIF_ID_DAILY,
            title = context.getString(R.string.notif_daily_title),
            text = text,
        )
    }

    fun showLimitWarning(context: Context, percentUsed: Int, usedBytes: Long, allowanceBytes: Long) {
        val exceeded = percentUsed >= 100
        val title = if (exceeded) {
            context.getString(R.string.notif_limit_reached_title)
        } else {
            context.getString(R.string.notif_limit_title)
        }
        val text = if (exceeded) {
            "You've used ${ByteFormatter.format(usedBytes)} of your " +
                "${ByteFormatter.format(allowanceBytes)} monthly allowance."
        } else {
            "You've used $percentUsed% of your monthly allowance " +
                "(${ByteFormatter.format(usedBytes)} of ${ByteFormatter.format(allowanceBytes)})."
        }
        post(context, CHANNEL_LIMIT, NOTIF_ID_LIMIT, title, text)
    }

    fun showHighUsage(context: Context, title: String, text: String) {
        post(context, CHANNEL_HIGH, NOTIF_ID_HIGH, title, text)
    }

    private fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canPost(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_datalens)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post — ignore.
        }
    }
}
