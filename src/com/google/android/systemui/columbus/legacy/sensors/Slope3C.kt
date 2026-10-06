package com.google.android.systemui.columbus.legacy.sensors

class Slope3C {
    private val slopeX = Slope1C()
    private val slopeY = Slope1C()
    private val slopeZ = Slope1C()

    fun init(point: Point3f) {
        slopeX.init(point.x)
        slopeY.init(point.y)
        slopeZ.init(point.z)
    }

    fun update(point: Point3f, scale: Float) =
        Point3f(
            slopeX.update(point.x, scale),
            slopeY.update(point.y, scale),
            slopeZ.update(point.z, scale),
        )
}
