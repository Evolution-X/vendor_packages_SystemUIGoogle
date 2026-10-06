package com.google.android.systemui.columbus.legacy.sensors

import android.app.ambientcontext.AmbientContextEvent
import android.app.ambientcontext.AmbientContextManager
import android.os.Handler
import android.util.Log
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import dagger.Lazy
import java.io.PrintWriter
import java.util.concurrent.Executor
import java.util.function.Consumer
import javax.inject.Inject

/**
 * Uses [AiAiCHREGestureSensor] while Private Compute Services supports back double tap events, and
 * falls back to [CHREGestureSensor] otherwise.
 */
@SysUISingleton
class CHREGestureSensorDelegator
@Inject
constructor(
    private val ambientContextManager: AmbientContextManager?,
    private val chreGestureSensor: CHREGestureSensor,
    private val aiAiCHREGestureSensor: Lazy<AiAiCHREGestureSensor>,
    @Background private val bgExecutor: Executor,
    @Background private val bgHandler: Handler,
) : GestureSensor(), Dumpable {
    private var gestureSensor: GestureSensor = chreGestureSensor

    private val statusConsumer =
        Consumer<Int> { statusCode ->
            Log.i(TAG, "CHREGestureSensorDelegator received statusCode = $statusCode")
            bgHandler.post {
                val aiAiSupported = statusCode == AmbientContextManager.STATUS_SUCCESS
                if (!aiAiSupported && gestureSensor is AiAiCHREGestureSensor) {
                    switchSensor(useAiAi = false)
                }
                if (aiAiSupported && gestureSensor is CHREGestureSensor) {
                    switchSensor(useAiAi = true)
                }
            }
        }

    private fun switchSensor(useAiAi: Boolean) {
        Log.i(TAG, "CHREGestureSensorDelegator switchSensor, AiAi = $useAiAi")
        val wasListening = gestureSensor.isListening()
        if (wasListening) {
            gestureSensor.stopListening()
        }
        gestureSensor.setGestureListener(null)
        gestureSensor.close()
        gestureSensor = if (useAiAi) aiAiCHREGestureSensor.get() else chreGestureSensor
        gestureSensor.setGestureListener(listener)
        if (wasListening) {
            gestureSensor.startListening()
        }
    }

    override fun setGestureListener(listener: Listener?) {
        Log.i(TAG, "CHREGestureSensorDelegator setGestureListener to $gestureSensor")
        super.setGestureListener(listener)
        bgHandler.post { gestureSensor.setGestureListener(listener) }
    }

    override fun isListening() = gestureSensor.isListening()

    override fun startListening() {
        ambientContextManager?.queryAmbientContextServiceStatus(
            setOf(AmbientContextEvent.EVENT_BACK_DOUBLE_TAP),
            bgExecutor,
            statusConsumer,
        )
        Log.i(TAG, "CHREGestureSensorDelegator startListening, gestureSensor = $gestureSensor")
        bgHandler.post { gestureSensor.startListening() }
    }

    override fun stopListening() {
        Log.i(TAG, "CHREGestureSensorDelegator stopListening, gestureSensor = $gestureSensor")
        bgHandler.post { gestureSensor.stopListening() }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        (gestureSensor as? Dumpable)?.dump(pw, args)
    }

    private companion object {
        const val TAG = "Columbus/GestureSensor"
    }
}
