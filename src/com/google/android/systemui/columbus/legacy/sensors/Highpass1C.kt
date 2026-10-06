package com.google.android.systemui.columbus.legacy.sensors

class Highpass1C {
    var para = 1f
    private var lastX = 0f
    private var lastY = 0f

    fun init(x: Float) {
        lastX = x
        lastY = x
    }

    fun update(x: Float): Float {
        if (para == 1f) return x
        val y = (x - lastX) * para + lastY * para
        lastY = y
        lastX = x
        return y
    }
}
