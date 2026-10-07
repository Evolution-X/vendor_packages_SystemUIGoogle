package com.google.android.systemui.power.batteryevent.repository

import android.os.Bundle
import javax.inject.Inject

class NoOpBatteryTimePredictionRepository @Inject constructor() : BatteryTimePredictionRepository {
    override suspend fun getTimeToFullBundle(socs: IntArray): Bundle = Bundle()
}
