package com.google.android.systemui.power.batteryevent.common.module

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData

class WiredIncompatibleChargingEventModule(
    private val context: Context,
    private val utils: WiredIncompatibleChargingUtilImpl,
) : BaseBatteryEventModule() {
    override val moduleType = BatteryEventType.WIRED_INCOMPATIBLE_CHARGING

    override val intentActions =
        listOf(UsbManager.ACTION_USB_PORT_COMPLIANCE_CHANGED, Intent.ACTION_BATTERY_CHANGED)

    override val eventDataTypes = emptyList<EventDataType>()

    override fun validate(systemEventData: SystemEventData): Boolean {
        if (systemEventData.intentAction == UsbManager.ACTION_USB_PORT_COMPLIANCE_CHANGED) {
            lastValidation = utils.containsIncompatibleChargers(context, "WiredIncompatibleEvent")
        }
        return lastValidation
    }
}
