package com.google.android.systemui.columbus.legacy.sensors

/** Linearly resamples a 3-axis signal to a fixed interval. */
class Resample3C {
    var interval = 0L
        private set

    var resampledLastT = 0L

    private var rawLastT = 0L
    private var rawLastX = 0f
    private var rawLastY = 0f
    private var rawLastZ = 0f
    private var resampledThisX = 0f
    private var resampledThisY = 0f
    private var resampledThisZ = 0f

    val results: Sample3C
        get() = Sample3C(Point3f(resampledThisX, resampledThisY, resampledThisZ), resampledLastT)

    fun init(x: Float, y: Float, z: Float, t: Long, interval: Long) {
        rawLastX = x
        rawLastY = y
        rawLastZ = z
        rawLastT = t
        resampledThisX = x
        resampledThisY = y
        resampledThisZ = z
        resampledLastT = t
        this.interval = interval
    }

    fun update(x: Float, y: Float, z: Float, t: Long): Boolean {
        if (t == rawLastT) return false
        val interval = if (interval > 0) interval else t - rawLastT
        val nextT = resampledLastT + interval
        if (t < nextT) {
            setRawLast(x, y, z, t)
            return false
        }
        val ratio = (nextT - rawLastT).toFloat() / (t - rawLastT)
        resampledThisX = (x - rawLastX) * ratio + rawLastX
        resampledThisY = (y - rawLastY) * ratio + rawLastY
        resampledThisZ = (z - rawLastZ) * ratio + rawLastZ
        resampledLastT = nextT
        if (rawLastT < nextT) {
            setRawLast(x, y, z, t)
        }
        return true
    }

    private fun setRawLast(x: Float, y: Float, z: Float, t: Long) {
        rawLastT = t
        rawLastX = x
        rawLastY = y
        rawLastZ = z
    }
}
