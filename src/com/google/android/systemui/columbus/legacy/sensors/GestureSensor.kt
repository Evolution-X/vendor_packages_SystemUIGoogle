package com.google.android.systemui.columbus.legacy.sensors

import java.io.Closeable
import java.util.Random

abstract class GestureSensor : Closeable {
    protected var listener: Listener? = null
        private set

    fun interface Listener {
        fun onGestureDetected(flags: Int, detectionProperties: DetectionProperties?)
    }

    class DetectionProperties(val isHapticConsumed: Boolean) {
        val actionId = Random().nextLong()
    }

    open fun setGestureListener(listener: Listener?) {
        this.listener = listener
    }

    abstract fun isListening(): Boolean

    abstract fun startListening()

    abstract fun stopListening()

    override fun close() {}
}
