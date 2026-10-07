package com.google.android.systemui.power.batteryevent.repository

import android.os.Bundle

interface BatteryTimePredictionRepository {
    suspend fun getTimeToFullBundle(socs: IntArray): Bundle
}
