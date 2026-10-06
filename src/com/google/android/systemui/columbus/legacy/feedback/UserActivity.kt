package com.google.android.systemui.columbus.legacy.feedback

import android.os.PowerManager
import android.os.SystemClock
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
class UserActivity @Inject constructor(private val powerManager: Lazy<PowerManager>) :
    FeedbackEffect {
    override fun onGestureDetected(
        flags: Int,
        detectionProperties: GestureSensor.DetectionProperties?,
    ) {
        if (flags != 0) {
            powerManager
                .get()
                .userActivity(SystemClock.uptimeMillis(), PowerManager.USER_ACTIVITY_EVENT_OTHER, 0)
        }
    }
}
