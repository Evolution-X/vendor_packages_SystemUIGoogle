package com.google.android.systemui.columbus.legacy.feedback

import com.google.android.systemui.columbus.legacy.sensors.GestureSensor

interface FeedbackEffect {
    fun onGestureDetected(flags: Int, detectionProperties: GestureSensor.DetectionProperties?)
}
