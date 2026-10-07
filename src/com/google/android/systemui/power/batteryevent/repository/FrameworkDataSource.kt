package com.google.android.systemui.power.batteryevent.repository

import android.content.Context
import android.os.PowerManager
import com.google.android.systemui.power.batteryevent.common.FrameworkApiDataType
import javax.inject.Inject

class FrameworkDataSource
@Inject
constructor(private val context: Context, private val powerManager: PowerManager) {
    var lastBatterySaverState = false
        private set

    var lastExtremeBatterySaverState = false
        private set

    fun update(dataTypes: List<FrameworkApiDataType>) {
        for (dataType in dataTypes) {
            when (dataType) {
                FrameworkApiDataType.BATTERY_SAVER_STATE ->
                    lastBatterySaverState = powerManager.isPowerSaveMode
                FrameworkApiDataType.EXTREME_BATTERY_SAVER_STATE -> {
                    val state =
                        if (powerManager.isPowerSaveMode) {
                            context.contentResolver.call(
                                "com.google.android.flipendo.api",
                                "get_flipendo_state",
                                null,
                                null,
                            )
                        } else {
                            null
                        }
                    lastExtremeBatterySaverState = state?.getBoolean("flipendo_state") == true
                }
            }
        }
    }
}
