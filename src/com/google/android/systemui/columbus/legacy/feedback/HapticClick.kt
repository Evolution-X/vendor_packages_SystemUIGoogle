package com.google.android.systemui.columbus.legacy.feedback

import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
class HapticClick @Inject constructor(private val vibrator: Lazy<Vibrator?>) : FeedbackEffect {
    override fun onGestureDetected(
        flags: Int,
        detectionProperties: GestureSensor.DetectionProperties?,
    ) {
        if (detectionProperties?.isHapticConsumed != true && flags == 1) {
            vibrator
                .get()
                ?.vibrate(GESTURE_DETECTED_VIBRATION_EFFECT, SONIFICATION_AUDIO_ATTRIBUTES)
        }
    }

    private companion object {
        val GESTURE_DETECTED_VIBRATION_EFFECT: VibrationEffect =
            VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK)
        val SONIFICATION_AUDIO_ATTRIBUTES: AudioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build()
    }
}
