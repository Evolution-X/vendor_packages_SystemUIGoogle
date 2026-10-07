package com.google.android.systemui.power.batteryevent.common.module

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

@SysUISingleton
class BatteryEventModuleProvider @Inject constructor(context: Context) {
    val eventModuleList: List<BaseBatteryEventModule> =
        listOf(
            ChargingLimitEventModule(),
            DwellDefendBatteryModule(),
            DndOnEventModule(),
            DockDefendBatteryModule(),
            ExtremeLowBatteryEventModule(),
            FullChargedEventModule(),
            LowBatteryEventModule(),
            NotChargingEventModule(),
            AirplaneOnEventModule(),
            ScreenOnEventModule(),
            SevereLowBatteryEventModule(),
            TempDefendBatteryModule(),
            RegularChargingEventModule(context),
            SlowChargingEventModule(context),
            FastChargingEventModule(context),
            WiredIncompatibleChargingEventModule(context, WiredIncompatibleChargingUtilImpl()),
        )
}
