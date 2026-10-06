package com.google.android.systemui.columbus.legacy

import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.settings.UserTracker
import javax.inject.Inject

@SysUISingleton
class ColumbusSettings
@Inject
constructor(
    context: Context,
    private val userTracker: UserTracker,
    contentObserverFactory: ColumbusContentObserver.Factory,
) {
    private val backupPackage = context.basePackageName
    private val contentResolver = context.contentResolver
    private val listeners = mutableSetOf<ColumbusSettingsChangeListener>()

    private val callback: (Uri) -> Unit = { uri ->
        when (uri) {
            COLUMBUS_ENABLED_URI -> {
                val enabled = isColumbusEnabled()
                listeners.forEach { it.onColumbusEnabledChange(enabled) }
                dataChanged()
            }
            // No listener reacts to this; the sensor type is only chosen when it is created.
            COLUMBUS_AP_SENSOR_URI -> {}
            COLUMBUS_ACTION_URI -> {
                val action = selectedAction()
                listeners.forEach { it.onSelectedActionChange(action) }
                dataChanged()
            }
            COLUMBUS_LAUNCH_APP_URI -> {
                val app = selectedApp()
                listeners.forEach { it.onSelectedAppChange(app) }
                dataChanged()
            }
            COLUMBUS_LAUNCH_APP_SHORTCUT_URI -> {
                val shortcut = selectedAppShortcut()
                listeners.forEach { it.onSelectedAppShortcutChange(shortcut) }
                dataChanged()
            }
            COLUMBUS_LOW_SENSITIVITY_URI -> {
                val lowSensitivity = useLowSensitivity()
                listeners.forEach { it.onLowSensitivityChange(lowSensitivity) }
                dataChanged()
            }
            COLUMBUS_SILENCE_ALERTS_URI -> {
                val silenceAlerts = silenceAlertsEnabled()
                listeners.forEach { it.onAlertSilenceEnabledChange(silenceAlerts) }
            }
            else -> Log.w(TAG, "Unknown setting change: $uri")
        }
    }

    init {
        MONITORED_URIS.map { contentObserverFactory.create(it, callback) }.forEach { it.activate() }
    }

    fun registerColumbusSettingsChangeListener(listener: ColumbusSettingsChangeListener) {
        listeners.add(listener)
    }

    fun unregisterColumbusSettingsChangeListener(listener: ColumbusSettingsChangeListener) {
        listeners.remove(listener)
    }

    fun isColumbusEnabled(): Boolean =
        Settings.Secure.getIntForUser(contentResolver, COLUMBUS_ENABLED, 0, userTracker.userId) != 0

    fun useApSensor(): Boolean =
        Settings.Secure.getIntForUser(contentResolver, COLUMBUS_AP_SENSOR, 0, userTracker.userId) !=
            0

    fun selectedAction(): String =
        Settings.Secure.getStringForUser(contentResolver, COLUMBUS_ACTION, userTracker.userId) ?: ""

    fun selectedApp(): String =
        Settings.Secure.getStringForUser(contentResolver, COLUMBUS_LAUNCH_APP, userTracker.userId)
            ?: ""

    fun selectedAppShortcut(): String =
        Settings.Secure.getStringForUser(
            contentResolver,
            COLUMBUS_LAUNCH_APP_SHORTCUT,
            userTracker.userId,
        ) ?: ""

    fun useLowSensitivity(): Boolean =
        Settings.Secure.getIntForUser(
            contentResolver,
            COLUMBUS_LOW_SENSITIVITY,
            0,
            userTracker.userId,
        ) != 0

    fun silenceAlertsEnabled(): Boolean =
        Settings.Secure.getIntForUser(
            contentResolver,
            COLUMBUS_SILENCE_ALERTS,
            1,
            userTracker.userId,
        ) != 0

    private fun dataChanged() {
        BackupManager.dataChangedForUser(userTracker.userId, backupPackage)
    }

    interface ColumbusSettingsChangeListener {
        fun onColumbusEnabledChange(enabled: Boolean) {}

        fun onSelectedActionChange(action: String) {}

        fun onSelectedAppChange(app: String) {}

        fun onSelectedAppShortcutChange(shortcut: String) {}

        fun onLowSensitivityChange(lowSensitivity: Boolean) {}

        fun onAlertSilenceEnabledChange(enabled: Boolean) {}
    }

    companion object {
        private const val TAG = "Columbus/Settings"

        const val COLUMBUS_ENABLED = "columbus_enabled"
        const val COLUMBUS_AP_SENSOR = "columbus_ap_sensor"
        const val COLUMBUS_ACTION = "columbus_action"
        const val COLUMBUS_LAUNCH_APP = "columbus_launch_app"
        const val COLUMBUS_LAUNCH_APP_SHORTCUT = "columbus_launch_app_shortcut"
        const val COLUMBUS_LOW_SENSITIVITY = "columbus_low_sensitivity"
        const val COLUMBUS_SILENCE_ALERTS = "columbus_silence_alerts"

        private val COLUMBUS_ENABLED_URI = Settings.Secure.getUriFor(COLUMBUS_ENABLED)
        private val COLUMBUS_AP_SENSOR_URI = Settings.Secure.getUriFor(COLUMBUS_AP_SENSOR)
        private val COLUMBUS_ACTION_URI = Settings.Secure.getUriFor(COLUMBUS_ACTION)
        private val COLUMBUS_LAUNCH_APP_URI = Settings.Secure.getUriFor(COLUMBUS_LAUNCH_APP)
        private val COLUMBUS_LAUNCH_APP_SHORTCUT_URI =
            Settings.Secure.getUriFor(COLUMBUS_LAUNCH_APP_SHORTCUT)
        private val COLUMBUS_LOW_SENSITIVITY_URI =
            Settings.Secure.getUriFor(COLUMBUS_LOW_SENSITIVITY)
        private val COLUMBUS_SILENCE_ALERTS_URI = Settings.Secure.getUriFor(COLUMBUS_SILENCE_ALERTS)

        private val MONITORED_URIS =
            setOf(
                COLUMBUS_ENABLED_URI,
                COLUMBUS_AP_SENSOR_URI,
                COLUMBUS_ACTION_URI,
                COLUMBUS_LAUNCH_APP_URI,
                COLUMBUS_LAUNCH_APP_SHORTCUT_URI,
                COLUMBUS_LOW_SENSITIVITY_URI,
                COLUMBUS_SILENCE_ALERTS_URI,
            )
    }
}
