package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.SettingsDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class ChargingLimitEventModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.CHARGING_LIMIT

    override val intentActions = listOf(Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes: List<EventDataType> =
        listOf(SettingsDataType.CHARGING_LIMIT_SETTINGS)

    override fun validate(systemEventData: SystemEventData): Boolean {
        val chargingStatus = systemEventData.chargingStatus.value
        if (chargingStatus != BatteryManager.CHARGING_POLICY_ADAPTIVE_LONGLIFE) {
            Log.d(TAG, "validate()=false, chargingStatus=$chargingStatus")
            return false
        }
        val chargingLimitSettings = systemEventData.settingsEventData.chargingLimitSettings.value
        if (chargingLimitSettings == 1) {
            return true
        }
        Log.d(TAG, "validate()=false, chargingLimitSettings=$chargingLimitSettings")
        return false
    }

    private companion object {
        const val TAG = "ChargingLimitEventModule"
    }
}
