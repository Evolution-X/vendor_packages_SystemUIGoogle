package com.google.android.systemui.power.batteryhealth

import android.os.Parcel
import android.os.Parcelable

data class IncompatibleChargerData(
    val lastCompatibleChargerTime: Long,
    val lastIncompatibleChargerTime: Long,
    val isIncompatibleCharger: Boolean,
) : Parcelable {

    override fun describeContents(): Int = 0

    // Stock writes the fields in a different order than createFromParcel() reads them.
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeBoolean(isIncompatibleCharger)
        parcel.writeLong(lastCompatibleChargerTime)
        parcel.writeLong(lastIncompatibleChargerTime)
    }

    companion object CREATOR : Parcelable.Creator<IncompatibleChargerData> {
        override fun createFromParcel(parcel: Parcel): IncompatibleChargerData =
            IncompatibleChargerData(parcel.readLong(), parcel.readLong(), parcel.readBoolean())

        override fun newArray(size: Int): Array<IncompatibleChargerData?> = arrayOfNulls(size)
    }
}
