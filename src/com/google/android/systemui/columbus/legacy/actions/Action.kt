package com.google.android.systemui.columbus.legacy.actions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.systemui.columbus.legacy.feedback.FeedbackEffect
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import com.google.android.systemui.columbus.util.Listenable
import java.util.concurrent.Executor

abstract class Action(
    protected val context: Context,
    private val executor: Executor?,
    private val feedbackEffects: Set<FeedbackEffect>?,
) : Listenable {
    private val listeners = mutableSetOf<Listenable.Listener>()
    private val handler = Handler(Looper.getMainLooper())

    var isAvailable = true
        private set

    abstract val tag: String

    protected abstract fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?)

    open fun onGestureDetected(
        flags: Int,
        detectionProperties: GestureSensor.DetectionProperties?,
    ) {
        updateFeedbackEffects(flags, detectionProperties)
        if (flags == 1) {
            Log.i(tag, "Triggering")
            executeOnTrigger(detectionProperties)
        }
    }

    open fun executeOnTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        if (executor == null) {
            onTrigger(detectionProperties)
        } else {
            executor.execute { onTrigger(detectionProperties) }
        }
    }

    open fun updateFeedbackEffects(
        flags: Int,
        detectionProperties: GestureSensor.DetectionProperties?,
    ) {
        feedbackEffects?.forEach { it.onGestureDetected(flags, detectionProperties) }
    }

    protected fun setAvailable(available: Boolean) {
        if (isAvailable == available) return
        isAvailable = available
        listeners.forEach { handler.post { it.onChange(this) } }
        if (!isAvailable) {
            handler.post { updateFeedbackEffects(0, null) }
        }
    }

    override fun registerListener(listener: Listenable.Listener) {
        listeners.add(listener)
    }

    override fun unregisterListener(listener: Listenable.Listener) {
        listeners.remove(listener)
    }

    override fun toString(): String = javaClass.simpleName
}
