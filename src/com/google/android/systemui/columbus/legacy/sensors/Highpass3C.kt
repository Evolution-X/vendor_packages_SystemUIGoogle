package com.google.android.systemui.columbus.legacy.sensors

class Highpass3C {
    private val highpassX = Highpass1C()
    private val highpassY = Highpass1C()
    private val highpassZ = Highpass1C()

    fun init(point: Point3f) {
        highpassX.init(point.x)
        highpassY.init(point.y)
        highpassZ.init(point.z)
    }

    fun setPara(para: Float) {
        highpassX.para = para
        highpassY.para = para
        highpassZ.para = para
    }

    fun update(point: Point3f) =
        Point3f(highpassX.update(point.x), highpassY.update(point.y), highpassZ.update(point.z))
}
