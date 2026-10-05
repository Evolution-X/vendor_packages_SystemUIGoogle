package com.google.android.systemui.power

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.devicestate.DeviceStateManager
import android.os.UserHandle
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.internal.logging.UiEventLogger
import com.android.settingslib.fuelgauge.BatteryStatus
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.util.Utils
import com.android.systemui.util.settings.SecureSettings
import com.google.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class ChargeLimitDiscoveryNotification
@Inject
constructor(
    val context: Context,
    val activityStarter: ActivityStarter,
    val uiEventLogger: UiEventLogger,
    val chargeLimitController: ChargeLimitController,
    val secureSettings: SecureSettings,
    val userTracker: UserTracker,
    val deviceProvisionedController: DeviceProvisionedController,
    val deviceStateManager: DeviceStateManager,
    val subscriptionManager: SubscriptionManager,
    val notificationManager: NotificationManager,
    @Main val mainDispatcher: CoroutineDispatcher,
    @Background val backgroundDispatcher: CoroutineDispatcher,
    @Application val backgroundCoroutineScope: CoroutineScope,
) {
    var isPluggedIn: Boolean = false
    val sharedPreferences: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(
            "charge_limit_shared_prefs",
            Context.MODE_PRIVATE,
        )
    }

    fun dispatchIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                if (!deviceProvisionedController.isDeviceProvisioned) {
                    Log.d(TAG, "[dispatchIntent] skip since device is not provisioned.")
                    return
                }
                val wasPluggedIn = isPluggedIn
                val plugged = BatteryStatus.isPluggedIn(intent.getIntExtra("plugged", 0))
                isPluggedIn = plugged
                Log.d(TAG, "isPluggedIn = $plugged")
                if (isPluggedIn && !wasPluggedIn) {
                    backgroundCoroutineScope.launch(backgroundDispatcher) {
                        sendNotificationIfNeeded()
                    }
                }
            }
            "systemui.power.action.enableChargeLimitFeature" -> {
                Log.d(TAG, "Enable charge limit manually.")
                chargeLimitController.setChargingPolicy(2)
                secureSettings.putIntForUser("charge_optimization_mode", 1, userTracker.userId)
                uiEventLogger.log(BatteryMetricEvent.ENABLE_CHARGE_LIMIT_FEATURE)
                notificationManager.cancelAsUser(
                    "charge_limit",
                    R.string.charge_limit_discovery_notification_title,
                    UserHandle.CURRENT,
                )
            }
            "systemui.power.action.dismissChargeLimitNotification" -> {
                uiEventLogger.log(BatteryMetricEvent.DISMISS_CHARGE_LIMIT_DISCOVERY_NOTIFICATION)
            }
            "systemui.power.action.clickChargeLimitNotification" -> {
                val settingsIntent =
                    Intent().apply {
                        component =
                            ComponentName(
                                "com.google.android.settings.intelligence",
                                "com.google.android.settings.intelligence.modules.battery.impl.chargingoptimization.ChargingOptimizationActivity",
                            )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                activityStarter.startActivity(settingsIntent, true)
                uiEventLogger.log(BatteryMetricEvent.CLICK_CHARGE_LIMIT_DISCOVERY_NOTIFICATION)
            }
        }
    }

    private suspend fun sendNotificationIfNeeded() {
        val key = "${ActivityManager.getCurrentUser()}|last_charge_limit_notification_time"
        var shouldShow = false
        if (sharedPreferences.getLong(key, -1L) == -1L) {
            if (
                Utils.isDeviceFoldable(context.resources, deviceStateManager) &&
                    !PowerUtils.isSimInEuCountry(subscriptionManager)
            ) {
                putChargeLimitNotificationTimestamp()
            } else {
                shouldShow = true
            }
        }
        Log.d(TAG, "showNotification: $shouldShow")
        if (shouldShow) {
            putChargeLimitNotificationTimestamp()
            withContext(mainDispatcher) { sendNotification() }
        }
    }

    private fun sendNotification() {
        Log.d(TAG, "sendNotification")
        val text = context.getString(R.string.charge_limit_discovery_notification_text)
        val builder =
            NotificationCompat.Builder(context, "BAT")
                .setSmallIcon(R.drawable.ic_battery_charging)
                .setContentTitle(
                    context.getString(R.string.charge_limit_discovery_notification_title)
                )
                .setContentText(text)
                .setContentIntent(
                    PowerUtils.createPendingIntent(
                        context,
                        "systemui.power.action.clickChargeLimitNotification",
                        null,
                    )
                )
                .setDeleteIntent(
                    PowerUtils.createPendingIntent(
                        context,
                        "systemui.power.action.dismissChargeLimitNotification",
                        null,
                    )
                )
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setOngoing(true)
                .setSilent(true)
                .addAction(
                    0,
                    context.getString(R.string.battery_health_notify_learn_more),
                    PowerUtils.createHelpArticlePendingIntentAsUser(
                        R.string.charge_limit_discovery_notification_help_url,
                        context,
                    ),
                )
                .addAction(
                    0,
                    context.getString(R.string.charge_limit_discovery_notification_enable_button),
                    PowerUtils.createPendingIntent(
                        context,
                        "systemui.power.action.enableChargeLimitFeature",
                        null,
                    ),
                )
                .setLocalOnly(true)
        PowerUtils.overrideNotificationAppName(context, builder)
        notificationManager.notifyAsUser(
            "charge_limit",
            R.string.charge_limit_discovery_notification_title,
            builder.build(),
            UserHandle.CURRENT,
        )
        uiEventLogger.log(BatteryMetricEvent.SEND_CHARGE_LIMIT_DISCOVERY_NOTIFICATION)
    }

    fun putChargeLimitNotificationTimestamp() {
        val now = System.currentTimeMillis()
        val key = "${ActivityManager.getCurrentUser()}|last_charge_limit_notification_time"
        Log.d(TAG, "putTimestamp: $now, key: $key")
        sharedPreferences.edit().putLong(key, now).apply()
    }

    companion object {
        private const val TAG = "ChargeLimitDiscoveryNotification"
    }
}
