package com.google.android.systemui.power.batteryhealth

import android.os.Parcel
import android.os.Parcelable

data class HealthData(
    val healthIndex: Int,
    val performanceIndex: Int,
    val capacityIndex: Int,
    val healthStatus: Int,
) : Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(healthIndex)
        parcel.writeInt(performanceIndex)
        parcel.writeInt(capacityIndex)
        parcel.writeInt(healthStatus)
    }

    override fun toString(): String =
        "hi: $healthIndex, pi: $performanceIndex, ci: $capacityIndex, hs: $healthStatus"

    companion object CREATOR : Parcelable.Creator<HealthData> {
        override fun createFromParcel(parcel: Parcel): HealthData =
            HealthData(parcel.readInt(), parcel.readInt(), parcel.readInt(), parcel.readInt())

        override fun newArray(size: Int): Array<HealthData?> = arrayOfNulls(size)
    }
}
