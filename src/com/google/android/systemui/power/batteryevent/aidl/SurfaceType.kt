package com.google.android.systemui.power.batteryevent.aidl

import android.os.Parcel
import android.os.Parcelable
import android.util.Log

enum class SurfaceType(val typeName: String) : Parcelable {
    UNKNOWN("unknown"),
    BATTERY_WIDGET("battery_widget"),
    NOTIFICATION("notification"),
    SYSTEM_DIALOG("system_dialog"),
    BATTERY_BANNER_TIPS("battery_banner_tips"),
    BATTERY_STATE_SUBSTRING("battery_state_substring"),
    KEYGUARD_AOD("keyguard_aod"),
    STATUS_BAR("status_bar"),
    QUICK_SETTINGS("quick_settings");

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(typeName)
    }

    companion object {
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<SurfaceType> {
                override fun createFromParcel(parcel: Parcel?): SurfaceType {
                    val name = parcel?.readString()
                    if (name == null) {
                        Log.w("SurfaceType", "null parameter for createFromParcel")
                        return UNKNOWN
                    }
                    return entries.firstOrNull { it.typeName == name } ?: UNKNOWN
                }

                override fun newArray(size: Int): Array<SurfaceType?> = arrayOfNulls(size)
            }
    }
}
