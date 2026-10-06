package com.google.android.systemui.columbus.legacy.sensors.config

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.res.R
import javax.inject.Inject

@SysUISingleton
class SensorConfiguration @Inject constructor(context: Context) {
    val defaultSensitivityValue =
        context.resources.getInteger(R.integer.columbus_default_sensitivity_percent) * 0.01f
    val lowSensitivityValue =
        context.resources.getInteger(R.integer.columbus_low_sensitivity_percent) * 0.01f
}
