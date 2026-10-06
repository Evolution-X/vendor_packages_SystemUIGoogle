package com.google.android.systemui.power

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.SystemProperties
import android.util.Log
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.time.SystemClock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class PulsarController
@Inject
constructor(
    private val context: Context,
    private val activityStarter: ActivityStarter,
    private val uiEventLogger: UiEventLogger,
    private val notificationManager: NotificationManager,
    private val secureSettings: SecureSettings,
    private val systemClock: SystemClock,
    @Main private val mainDispatcher: CoroutineDispatcher,
    @Application private val backgroundCoroutineScope: CoroutineScope,
) {
    private val pulsarEnabledNotification by lazy {
        PulsarEnabledNotification(context, notificationManager, uiEventLogger)
    }
    private val pulsarReminderNotification by lazy {
        PulsarReminderNotification(context, notificationManager, uiEventLogger)
    }
    private val sharedPreferences: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences("pulsar_shared_prefs", Context.MODE_PRIVATE)
    }

    private val pulsarObserver =
        object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                if (selfChange) return
                val pulsarSysPropEnabled =
                    secureSettings.getInt(SETTING_PULSAR_SYSPROP_ENABLED, 1) == 1
                Log.d(TAG, "pulsarSysPropEnabled: $pulsarSysPropEnabled")
                if (
                    !pulsarSysPropEnabled ||
                        !sharedPreferences.getBoolean(KEY_DAY_THREE_NOTIFICATION_SHOWN, false)
                ) {
                    backgroundCoroutineScope.launch {
                        updatePulsarDisabledTimestamp(pulsarSysPropEnabled)
                    }
                } else {
                    sharedPreferences
                        .edit()
                        .putBoolean(KEY_DAY_THIRTY_NOTIFICATION_SHOWN, true)
                        .apply()
                    secureSettings.unregisterContentObserverAsync(this)
                    Log.d(TAG, "Unregister pulsar observer since user reactivates the feature.")
                }
            }
        }

    init {
        if (!sharedPreferences.getBoolean(KEY_DAY_THIRTY_NOTIFICATION_SHOWN, false)) {
            try {
                secureSettings.registerContentObserverAsync(
                    SETTING_PULSAR_SYSPROP_ENABLED,
                    false,
                    pulsarObserver,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register observer", e)
            }
        }
    }

    fun dispatchIntent(intent: Intent) {
        Log.d(TAG, "dispatchIntent: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED ->
                backgroundCoroutineScope.launch { checkAndSendNotifications() }
            ACTION_CLICK_PULSAR_ENABLED_NOTIFICATION ->
                onClickPulsarNotification(BatteryMetricEvent.CLICK_PULSAR_ENABLED_NOTIFICATION)
            ACTION_DISMISS_PULSAR_ENABLED_NOTIFICATION ->
                uiEventLogger.log(BatteryMetricEvent.DISMISS_PULSAR_ENABLED_NOTIFICATION)
            ACTION_CLICK_PULSAR_REMINDER_NOTIFICATION ->
                onClickPulsarNotification(BatteryMetricEvent.CLICK_PULSAR_REMINDER_NOTIFICATION)
            ACTION_DISMISS_PULSAR_REMINDER_NOTIFICATION ->
                uiEventLogger.log(BatteryMetricEvent.DISMISS_PULSAR_REMINDER_NOTIFICATION)
        }
    }

    private fun onClickPulsarNotification(event: BatteryMetricEvent) {
        val intent =
            Intent().apply {
                component =
                    ComponentName(
                        "com.google.android.settings.intelligence",
                        "com.google.android.settings.intelligence.modules.battery.impl.pulsar.PulsarActivity",
                    )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        activityStarter.startActivity(intent, true)
        uiEventLogger.log(event)
    }

    private fun updatePulsarDisabledTimestamp(enabled: Boolean) {
        val timestamp = if (enabled) Long.MAX_VALUE else systemClock.currentTimeMillis()
        sharedPreferences.edit().putLong(KEY_PULSAR_DISABLED_TIMESTAMP, timestamp).apply()
        Log.d(TAG, "updatePulsarDisabledTimestamp: $timestamp")
    }

    private suspend fun checkAndSendNotifications() {
        if (!isPulsarActivityEnabled()) {
            Log.d(TAG, "Pulsar activity is not enabled. Skip the notifications.")
            return
        }
        val pulsarEnabled = isPulsarEnabled()
        sendPulsarEnabledNotificationIfNeeded(pulsarEnabled)
        sendPulsarReminderNotificationIfNeeded(pulsarEnabled)
    }

    private fun isPulsarActivityEnabled(): Boolean {
        val intent =
            Intent("com.google.android.settings.intelligence.action.PULSAR")
                .addCategory(Intent.CATEGORY_DEFAULT)
        val component = intent.resolveActivity(context.packageManager) ?: return false
        return try {
            context.packageManager.getComponentEnabledSetting(
                ComponentName(component.packageName, component.className)
            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Pulsar is not available.")
            false
        }
    }

    private suspend fun sendPulsarEnabledNotificationIfNeeded(pulsarEnabled: Boolean) {
        Log.d(TAG, "sendPulsarEnabledNotificationIfNeeded")
        if (!pulsarEnabled) {
            Log.d(TAG, "Skip PulsarEnabledNotification since pulsar is disabled.")
            return
        }
        if (sharedPreferences.getBoolean(KEY_PULSAR_ENABLED_NOTIFICATION_SHOWN, false)) {
            Log.d(TAG, "Skip PulsarEnabledNotification since it's shown before.")
            return
        }
        sharedPreferences.edit().putBoolean(KEY_PULSAR_ENABLED_NOTIFICATION_SHOWN, true).apply()
        showNotification(TAG_PULSAR_ENABLED)
    }

    private suspend fun sendPulsarReminderNotificationIfNeeded(pulsarEnabled: Boolean) {
        Log.d(TAG, "sendPulsarReminderNotificationIfNeeded")
        if (
            pulsarEnabled || sharedPreferences.getBoolean(KEY_DAY_THIRTY_NOTIFICATION_SHOWN, false)
        ) {
            return
        }
        val disabledDuration =
            systemClock.currentTimeMillis() -
                sharedPreferences.getLong(KEY_PULSAR_DISABLED_TIMESTAMP, Long.MAX_VALUE)
        if (
            !sharedPreferences.getBoolean(KEY_DAY_THREE_NOTIFICATION_SHOWN, false) &&
                disabledDuration >= THREE_DAYS_MILLIS
        ) {
            showNotification(TAG_PULSAR_REMINDER)
            Log.d(TAG, "Show day 3 reminder notification.")
            sharedPreferences.edit().putBoolean(KEY_DAY_THREE_NOTIFICATION_SHOWN, true).apply()
        } else if (disabledDuration >= THIRTY_DAYS_MILLIS) {
            showNotification(TAG_PULSAR_REMINDER)
            Log.d(TAG, "Show day 30 reminder notification and unregister the observer.")
            sharedPreferences.edit().putBoolean(KEY_DAY_THIRTY_NOTIFICATION_SHOWN, true).apply()
            secureSettings.unregisterContentObserverAsync(pulsarObserver)
        }
    }

    private suspend fun showNotification(notificationTag: String) {
        withContext(mainDispatcher) {
            when (notificationTag) {
                TAG_PULSAR_ENABLED -> pulsarEnabledNotification.sendNotification()
                TAG_PULSAR_REMINDER -> pulsarReminderNotification.sendNotification()
                else -> Log.w(TAG, "Unknown notification tag: $notificationTag")
            }
        }
    }

    companion object {
        private const val TAG = "PulsarController"

        const val PROP_PULSAR_OPT_OUT = "persist.vendor.pulsar.opt_out"
        const val SETTING_PULSAR_SYSPROP_ENABLED = "pulsar_sysprop_enabled"

        const val ACTION_CLICK_PULSAR_ENABLED_NOTIFICATION =
            "systemui.power.action.clickPulsarEnabledNotification"
        const val ACTION_DISMISS_PULSAR_ENABLED_NOTIFICATION =
            "systemui.power.action.dismissPulsarEnabledNotification"
        const val ACTION_CLICK_PULSAR_REMINDER_NOTIFICATION =
            "systemui.power.action.clickPulsarReminderNotification"
        const val ACTION_DISMISS_PULSAR_REMINDER_NOTIFICATION =
            "systemui.power.action.dismissPulsarReminderNotification"

        private const val TAG_PULSAR_ENABLED = "pulsar_enabled"
        private const val TAG_PULSAR_REMINDER = "pulsar_reminder"

        private const val KEY_PULSAR_ENABLED_NOTIFICATION_SHOWN =
            "pulsar_enabled_notification_shown"
        private const val KEY_DAY_THREE_NOTIFICATION_SHOWN = "pulsar_day_three_notification_shown"
        private const val KEY_DAY_THIRTY_NOTIFICATION_SHOWN = "pulsar_day_thirty_notification_shown"
        private const val KEY_PULSAR_DISABLED_TIMESTAMP = "pulsar_disabled_timestamp"

        private val THREE_DAYS_MILLIS = Duration.ofDays(3).toMillis()
        private val THIRTY_DAYS_MILLIS = Duration.ofDays(30).toMillis()

        fun isPulsarEnabled(): Boolean =
            try {
                val optOut = SystemProperties.get(PROP_PULSAR_OPT_OUT, "0")
                Log.d(TAG, "getSystemProperty: key= $PROP_PULSAR_OPT_OUT, value= $optOut")
                optOut == "0"
            } catch (e: RuntimeException) {
                Log.e(TAG, "getSystemProperty: failed.", e)
                true
            }
    }
}
