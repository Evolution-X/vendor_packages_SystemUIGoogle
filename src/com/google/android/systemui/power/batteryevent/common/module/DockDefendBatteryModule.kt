package com.google.android.systemui.power.batteryevent.common.module

import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.HalDataType
import com.google.android.systemui.power.batteryevent.common.SettingsDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData
import vendor.google.google_battery.DockDefendStatus

class DockDefendBatteryModule : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.DOCK_DEFEND_BATTERY

    override val intentActions = listOf(Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes: List<EventDataType> =
        listOf(HalDataType.GOOGLE_BATTERY_DOCK_DEFEND_STATUS, SettingsDataType.DOCK_DEFENDER_BYPASS)

    override fun validate(systemEventData: SystemEventData): Boolean {
        val plugged = systemEventData.plugged
        val chargingStatus = systemEventData.chargingStatus
        val dockDefenderBypass = systemEventData.settingsEventData.dockDefenderBypass
        val dockDefendStatus = systemEventData.halEventData.dockDefendStatus
        if (
            plugged.isChanged ||
                chargingStatus.isChanged ||
                dockDefenderBypass.isChanged ||
                dockDefendStatus.isChanged
        ) {
            lastValidation =
                when {
                    plugged.value != BatteryManager.BATTERY_PLUGGED_DOCK -> {
                        Log.d(TAG, "not DockDefend -> plugged: ${plugged.value}")
                        false
                    }
                    chargingStatus.value != BatteryManager.CHARGING_POLICY_ADAPTIVE_LONGLIFE -> {
                        Log.d(TAG, "not DockDefend -> chargingStatus: ${chargingStatus.value}")
                        false
                    }
                    dockDefenderBypass.value == 1 -> {
                        Log.d(
                            TAG,
                            "not DockDefend -> dockDefendBypass: ${dockDefenderBypass.value}",
                        )
                        false
                    }
                    dockDefendStatus.value != DockDefendStatus.TRIGGERED -> {
                        Log.d(TAG, "not DockDefend -> dockDefendStatus: ${dockDefendStatus.value}")
                        false
                    }
                    else -> true
                }
        }
        return lastValidation
    }

    private companion object {
        const val TAG = "DockDefendBatteryModule"
    }
}
