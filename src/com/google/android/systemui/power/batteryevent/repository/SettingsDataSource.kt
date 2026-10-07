package com.google.android.systemui.power.batteryevent.repository

import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.settings.UserTracker
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.SecureSettings
import com.google.android.systemui.power.batteryevent.common.SettingsDataType
import javax.inject.Inject

@SysUISingleton
class SettingsDataSource
@Inject
constructor(
    private val globalSettings: GlobalSettings,
    private val secureSettings: SecureSettings,
    private val userTracker: UserTracker,
) {
    var lastDockDefenderByPass = 0
        private set

    var lastChargingLimitSettings = 0
        private set

    var lastDndState = false
        private set

    var lastAirplaneState = false
        private set

    fun update(dataTypes: List<SettingsDataType>) {
        for (dataType in dataTypes) {
            when (dataType) {
                SettingsDataType.AIRPLANE_STATE ->
                    lastAirplaneState =
                        globalSettings.getInt(Settings.Global.AIRPLANE_MODE_ON, 0) != 0
                SettingsDataType.CHARGING_LIMIT_SETTINGS ->
                    lastChargingLimitSettings =
                        secureSettings.getIntForUser(
                            "charge_optimization_mode",
                            0,
                            userTracker.userId,
                        )
                SettingsDataType.DND_STATE ->
                    lastDndState = globalSettings.getInt(Settings.Global.ZEN_MODE, 0) != 0
                SettingsDataType.DOCK_DEFENDER_BYPASS ->
                    lastDockDefenderByPass = globalSettings.getInt("dock_defender_bypass", 0)
            }
        }
    }
}
