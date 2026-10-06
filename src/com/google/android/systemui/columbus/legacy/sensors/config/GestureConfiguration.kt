package com.google.android.systemui.columbus.legacy.sensors.config

import android.util.Range
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlin.math.abs

@SysUISingleton
class GestureConfiguration
@Inject
constructor(
    private val adjustments: List<@JvmSuppressWildcards LowSensitivitySettingAdjustment>,
    private val sensorConfiguration: SensorConfiguration,
) {
    var listener: Listener? = null
    var sensitivity = sensorConfiguration.defaultSensitivityValue
        private set

    fun interface Listener {
        fun onSensitivityChanged(sensitivity: Float)
    }

    init {
        adjustments.forEach { it.callback = { updateSensitivity() } }
        updateSensitivity()
    }

    private fun updateSensitivity() {
        var newSensitivity = sensorConfiguration.defaultSensitivityValue
        adjustments.forEach {
            newSensitivity = SENSITIVITY_RANGE.clamp(it.adjustSensitivity(newSensitivity))
        }
        if (abs(sensitivity - newSensitivity) >= SENSITIVITY_CHANGE_THRESHOLD) {
            sensitivity = newSensitivity
            listener?.onSensitivityChanged(newSensitivity)
        }
    }

    private companion object {
        val SENSITIVITY_RANGE: Range<Float> = Range.create(0f, 1f)
        const val SENSITIVITY_CHANGE_THRESHOLD = 0.05f
    }
}
