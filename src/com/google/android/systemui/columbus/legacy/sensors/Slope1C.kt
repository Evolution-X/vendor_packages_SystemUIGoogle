package com.google.android.systemui.columbus.legacy.sensors

class Slope1C {
    private var rawLastX = 0f

    fun init(x: Float) {
        rawLastX = x
    }

    fun update(x: Float, scale: Float): Float {
        val scaled = x * scale
        val delta = scaled - rawLastX
        rawLastX = scaled
        return delta
    }
}
