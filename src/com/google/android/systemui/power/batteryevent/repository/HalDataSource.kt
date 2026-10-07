package com.google.android.systemui.power.batteryevent.repository

import android.os.BatteryManager
import android.os.IBinder
import android.util.Log
import com.google.android.systemui.googlebattery.GoogleBatteryManager
import com.google.android.systemui.power.batteryevent.common.HalDataType
import vendor.google.google_battery.Feature
import vendor.google.google_battery.IGoogleBattery

class HalDataSource {
    private val deathRecipient = IBinder.DeathRecipient { Log.e(TAG, "Service died!!") }

    var lastGoogleBatteryDockDefendStatus = DOCK_DEFEND_STATUS_UNKNOWN
        private set

    var lastTempDefendStatus = false
        private set

    var lastDwellDefendStatus = false
        private set

    fun update(dataTypes: List<HalDataType>, plugged: Int) {
        val googleBattery = GoogleBatteryManager.initHalInterface(deathRecipient)
        for (dataType in dataTypes) {
            when (dataType) {
                HalDataType.GOOGLE_BATTERY_DOCK_DEFEND_STATUS ->
                    lastGoogleBatteryDockDefendStatus =
                        if (plugged == BatteryManager.BATTERY_PLUGGED_DOCK) {
                            fetchDockDefendStatus(googleBattery)
                        } else {
                            DOCK_DEFEND_STATUS_UNKNOWN
                        }
                HalDataType.GOOGLE_BATTERY_TEMP_DEFEND_STATUS -> {
                    val status = fetchFeatureStatus(googleBattery, Feature.TEMP_DEFEND, true)
                    Log.d(TAG, "fetchTempDefendStatus: $status")
                    lastTempDefendStatus = status.contains(" t=1")
                }
                HalDataType.GOOGLE_BATTERY_DWELL_DEFEND_STATUS -> {
                    val status = fetchFeatureStatus(googleBattery, Feature.DWELL_DEFEND, true)
                    Log.d(TAG, "fetchDwellDefendStatus: $status")
                    lastDwellDefendStatus = status == "ACTIVE"
                }
            }
        }
        destroyGoogleBattery(googleBattery)
    }

    private fun fetchDockDefendStatus(googleBattery: IGoogleBattery?): Int {
        if (googleBattery == null) {
            Log.w(TAG, "getDockDefendStatus failed. googleBattery is null")
            return DOCK_DEFEND_STATUS_UNKNOWN
        }
        return try {
            googleBattery.dockDefendStatus.also {
                Log.i(TAG, "fetchDockDefendStatus: dockDefendStatus:$it")
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchDockDefendStatus failed.", e)
            DOCK_DEFEND_STATUS_UNKNOWN
        }
    }

    private fun fetchFeatureStatus(
        googleBattery: IGoogleBattery?,
        feature: Int,
        retry: Boolean,
    ): String {
        if (googleBattery == null) {
            return "null googleBattery"
        }
        return try {
            googleBattery.getStringProperty(feature, PROPERTY_FEATURE_STATUS)
                ?: "null googleBattery"
        } catch (e: Exception) {
            Log.w(TAG, "retry fetchFeatureStatus: ${FEATURE_NAME[feature]}")
            if (retry) {
                val newGoogleBattery =
                    GoogleBatteryManager.initHalInterface(deathRecipient)
                        ?: return "init google battery failed"
                fetchFeatureStatus(newGoogleBattery, feature, false).also {
                    destroyGoogleBattery(newGoogleBattery)
                }
            } else {
                Log.w(TAG, "fetchFeatureStatus: ${FEATURE_NAME[feature]} failed", e)
                e.message ?: ""
            }
        }
    }

    private fun destroyGoogleBattery(googleBattery: IGoogleBattery?) {
        try {
            GoogleBatteryManager.destroyHalInterface(googleBattery, deathRecipient)
        } catch (e: Exception) {
            Log.w(TAG, "destroyHalInterface failed: ", e)
        }
    }

    private companion object {
        const val TAG = "GoogleBatteryDataSource"
        const val DOCK_DEFEND_STATUS_UNKNOWN = -3
        // ???: property id passed to getStringProperty(); IGoogleBattery has no named constant.
        const val PROPERTY_FEATURE_STATUS = 18
        val FEATURE_NAME =
            mapOf(Feature.TEMP_DEFEND to "Temp defend", Feature.DWELL_DEFEND to "Dwell defend")
    }
}
