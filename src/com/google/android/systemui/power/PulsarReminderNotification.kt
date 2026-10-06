package com.google.android.systemui.power

import android.app.NotificationManager
import android.content.Context
import android.os.UserHandle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.internal.logging.UiEventLogger
import com.google.android.systemui.res.R

class PulsarReminderNotification(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val uiEventLogger: UiEventLogger,
) {
    fun sendNotification() {
        Log.d(TAG, "sendNotification")
        val text = context.getString(R.string.pulsar_reminder_notification_text)
        val builder =
            NotificationCompat.Builder(context, "BAT")
                .setSmallIcon(R.drawable.ic_settings_gear)
                .setContentTitle(context.getString(R.string.pulsar_reminder_notification_title))
                .setContentText(text)
                .setContentIntent(
                    PowerUtils.createPendingIntent(
                        context,
                        PulsarController.ACTION_CLICK_PULSAR_REMINDER_NOTIFICATION,
                        null,
                    )
                )
                .setDeleteIntent(
                    PowerUtils.createPendingIntent(
                        context,
                        PulsarController.ACTION_DISMISS_PULSAR_REMINDER_NOTIFICATION,
                        null,
                    )
                )
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setOngoing(true)
                .addAction(
                    0,
                    context.getString(R.string.battery_health_notify_learn_more),
                    PowerUtils.createHelpArticlePendingIntentAsUser(
                        R.string.pulsar_enabled_notification_help_url,
                        context,
                    ),
                )
                .setLocalOnly(true)
        PowerUtils.overrideNotificationAppName(context, builder)
        notificationManager.notifyAsUser(
            "pulsar_reminder",
            R.string.pulsar_reminder_notification_title,
            builder.build(),
            UserHandle.CURRENT,
        )
        uiEventLogger.log(BatteryMetricEvent.SEND_PULSAR_REMINDER_NOTIFICATION)
    }

    companion object {
        // Stock reuses the ChargeLimitDiscoveryNotification tag here.
        private const val TAG = "ChargeLimitDiscoveryNotification"
    }
}
