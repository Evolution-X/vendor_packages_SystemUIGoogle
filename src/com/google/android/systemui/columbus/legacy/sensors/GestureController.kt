package com.google.android.systemui.columbus.legacy.sensors

import android.os.SystemClock
import android.util.Log
import android.util.SparseLongArray
import com.android.internal.logging.UiEventLogger
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.commandline.Command
import com.android.systemui.statusbar.commandline.CommandRegistry
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.COLUMBUS_SOFT_GATES
import com.google.android.systemui.columbus.legacy.gates.Gate
import com.google.android.systemui.columbus.util.Listenable
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Named

@SysUISingleton
class GestureController
@Inject
constructor(
    private val gestureSensor: GestureSensor,
    @Named(COLUMBUS_SOFT_GATES) private val softGates: Set<@JvmSuppressWildcards Gate>,
    commandRegistry: CommandRegistry,
    private val uiEventLogger: UiEventLogger,
) : Dumpable {
    var gestureListener: GestureSensor.Listener? = null

    private val lastTimestampMap = SparseLongArray()
    private var softGateBlockCount = 0L

    // Soft gates are polled when a gesture arrives, so registering only needs to activate them.
    private val softGateListener = Listenable.Listener {}

    private val gestureSensorListener = GestureSensor.Listener { flags, detectionProperties ->
        onGestureDetected(flags, detectionProperties)
    }

    init {
        gestureSensor.setGestureListener(gestureSensorListener)
        commandRegistry.registerCommand("quick-tap") { ColumbusCommand() }
    }

    private fun onGestureDetected(
        flags: Int,
        detectionProperties: GestureSensor.DetectionProperties?,
    ) {
        val now = SystemClock.uptimeMillis()
        val lastTimestamp = lastTimestampMap.get(flags)
        lastTimestampMap.put(flags, now)
        if (now - lastTimestamp <= GESTURE_THROTTLE_MS) {
            Log.w(TAG, "Gesture $flags throttled")
            return
        }
        val blockingGate = softGates.firstOrNull { it.isBlocking() }
        if (blockingGate != null) {
            softGateBlockCount++
            Log.i(TAG, "Gesture blocked by $blockingGate")
            return
        }
        if (flags == 1) {
            uiEventLogger.log(ColumbusEvent.COLUMBUS_DOUBLE_TAP_DETECTED)
        }
        gestureListener?.onGestureDetected(flags, detectionProperties)
    }

    fun startListening(): Boolean {
        if (gestureSensor.isListening()) return false
        softGates.forEach { it.registerListener(softGateListener) }
        gestureSensor.startListening()
        return true
    }

    fun stopListening(): Boolean {
        if (!gestureSensor.isListening()) return false
        gestureSensor.stopListening()
        softGates.forEach { it.unregisterListener(softGateListener) }
        return true
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("  Soft Blocks: $softGateBlockCount")
        pw.println("  Gesture Sensor: $gestureSensor")
        (gestureSensor as? Dumpable)?.dump(pw, args)
    }

    private inner class ColumbusCommand : Command {
        override fun execute(pw: PrintWriter, args: List<String>) {
            if (args.firstOrNull() == "trigger") {
                gestureSensorListener.onGestureDetected(1, null)
            } else {
                help(pw)
            }
        }

        override fun help(pw: PrintWriter) {
            pw.println("usage: quick-tap <command>")
            pw.println("Available commands:")
            pw.println("  trigger")
        }
    }

    private companion object {
        const val TAG = "Columbus/GestureControl"
        const val GESTURE_THROTTLE_MS = 500L
    }
}
