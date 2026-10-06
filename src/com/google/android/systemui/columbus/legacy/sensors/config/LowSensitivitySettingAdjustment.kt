package com.google.android.systemui.columbus.legacy.sensors.config

import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.columbus.legacy.ColumbusSettings
import javax.inject.Inject

@SysUISingleton
class LowSensitivitySettingAdjustment
@Inject
constructor(
    columbusSettings: ColumbusSettings,
    private val sensorConfiguration: SensorConfiguration,
) {
    var callback: ((LowSensitivitySettingAdjustment) -> Unit)? = null
    private var useLowSensitivity = columbusSettings.useLowSensitivity()

    private val settingsChangeListener =
        object : ColumbusSettings.ColumbusSettingsChangeListener {
            override fun onLowSensitivityChange(lowSensitivity: Boolean) {
                if (useLowSensitivity != lowSensitivity) {
                    useLowSensitivity = lowSensitivity
                    callback?.invoke(this@LowSensitivitySettingAdjustment)
                }
            }
        }

    init {
        columbusSettings.registerColumbusSettingsChangeListener(settingsChangeListener)
    }

    fun adjustSensitivity(sensitivity: Float): Float =
        if (useLowSensitivity) sensorConfiguration.lowSensitivityValue else sensitivity
}
