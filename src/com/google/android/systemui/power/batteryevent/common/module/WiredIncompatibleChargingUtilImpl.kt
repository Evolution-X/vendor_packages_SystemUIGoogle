package com.google.android.systemui.power.batteryevent.common.module

import android.content.Context
import com.android.settingslib.Utils

class WiredIncompatibleChargingUtilImpl {
    fun containsIncompatibleChargers(context: Context, tag: String): Boolean =
        Utils.containsIncompatibleChargers(context, tag)
}
