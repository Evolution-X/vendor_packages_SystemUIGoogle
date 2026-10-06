package com.google.android.systemui.columbus.legacy.sensors

import android.app.ambientcontext.AmbientContextCallback
import android.app.ambientcontext.AmbientContextEvent
import android.app.ambientcontext.AmbientContextEventRequest
import android.app.ambientcontext.AmbientContextManager
import android.frameworks.stats.IStats
import android.frameworks.stats.VendorAtom
import android.frameworks.stats.VendorAtomValue
import android.os.Handler
import android.os.ServiceManager
import android.util.Log
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.time.SystemClock
import com.google.android.systemui.columbus.ColumbusEvent
import java.util.concurrent.Executor
import javax.inject.Inject

/** Receives Quick Tap events through Private Compute Services instead of the nanoapp directly. */
@SysUISingleton
class AiAiCHREGestureSensor
@Inject
constructor(
    private val uiEventLogger: UiEventLogger,
    private val ambientContextManager: AmbientContextManager?,
    @Background private val bgHandler: Handler,
    @Main private val mainExecutor: Executor,
    private val systemClock: SystemClock,
) : GestureSensor() {
    private var isListening = false

    private val ambientContextCallback =
        object : AmbientContextCallback {
            override fun onEvents(events: List<AmbientContextEvent>) {
                Log.i(TAG, "Received events from AmbientContextManager: $events")
                val event = events.firstOrNull {
                    it.eventType == AmbientContextEvent.EVENT_BACK_DOUBLE_TAP
                }
                if (event == null) {
                    Log.e(TAG, "Receiving events but not EVENT_BACK_DOUBLE_TAP")
                    return
                }
                reportLatency(systemClock.currentTimeMillis() - event.startTime.toEpochMilli())
                listener?.onGestureDetected(1, DetectionProperties(false))
            }

            override fun onRegistrationComplete(statusCode: Int) {
                Log.i(TAG, "registerObserver completes with status: $statusCode")
            }
        }

    override fun isListening() = isListening

    override fun startListening() {
        isListening = true
        Log.i(TAG, "startListening with AmbientContextManager.registerObserver")
        val request =
            AmbientContextEventRequest.Builder()
                .addEventType(AmbientContextEvent.EVENT_BACK_DOUBLE_TAP)
                .build()
        if (ambientContextManager == null) {
            Log.e(TAG, "AmbientContextManager not found.")
            return
        }
        bgHandler.post {
            ambientContextManager.registerObserver(request, mainExecutor, ambientContextCallback)
            uiEventLogger.log(ColumbusEvent.COLUMBUS_MODE_LOW_POWER_ACTIVE)
        }
    }

    override fun stopListening() {
        Log.i(TAG, "stopListening with AmbientContextManager.unregisterObserver")
        if (ambientContextManager == null) {
            Log.e(TAG, "AmbientContextManager not found.")
            return
        }
        bgHandler.post {
            ambientContextManager.unregisterObserver()
            uiEventLogger.log(ColumbusEvent.COLUMBUS_MODE_INACTIVE)
        }
        isListening = false
    }

    private fun reportLatency(latencyMillis: Long) {
        val vendorAtom =
            VendorAtom().apply {
                reverseDomainName = ""
                atomId = QUICK_TAP_LATENCY_REPORTED
                values = arrayOf(VendorAtomValue.longValue(latencyMillis))
            }
        try {
            val stats =
                if (ServiceManager.isDeclared(ISTATS_INSTANCE_NAME)) {
                    IStats.Stub.asInterface(
                        ServiceManager.waitForDeclaredService(ISTATS_INSTANCE_NAME)
                    )
                } else {
                    Log.e(METRICS_TAG, "IStats is not registered")
                    null
                }
            if (stats != null) {
                stats.reportVendorAtom(vendorAtom)
                if (Log.isLoggable(METRICS_TAG, Log.DEBUG)) {
                    Log.d(METRICS_TAG, "Report vendor atom OK, $vendorAtom")
                }
            }
        } catch (e: Exception) {
            Log.e(METRICS_TAG, "Failed to log atom to IStats service, $e")
        }
    }

    private companion object {
        const val TAG = "Columbus/GestureSensor"
        const val METRICS_TAG = "Columbus/Metrics"
        val ISTATS_INSTANCE_NAME = "${IStats.DESCRIPTOR}/default"

        // ??? Pixel vendor atom ID for the PCC delivery latency; its name is not in our tree.
        const val QUICK_TAP_LATENCY_REPORTED = 100139
    }
}
