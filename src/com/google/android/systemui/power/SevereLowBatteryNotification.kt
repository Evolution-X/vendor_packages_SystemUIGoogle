package com.google.android.systemui.power

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.UserHandle
import android.util.Log
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

@SysUISingleton
class SevereLowBatteryNotification
@Inject
constructor(
    val context: Context,
    val uiEventLogger: UiEventLogger,
    val keyguardManager: KeyguardManager,
) {
    val notificationManager: NotificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    fun logEvent(event: BatteryMetricEvent) {
        uiEventLogger.log(event)
        Log.d(TAG, "logEvent $event")
    }

    fun cancel() {
        Log.d(TAG, "cancel()")
        notificationManager.cancelAsUser("low_battery", 3, UserHandle.ALL)
    }

    private companion object {
        private const val TAG = "SevereLowBatteryNotification"
    }
}
