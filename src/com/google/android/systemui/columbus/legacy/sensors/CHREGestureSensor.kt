package com.google.android.systemui.columbus.legacy.sensors

import android.content.Context
import android.hardware.location.ContextHubClient
import android.hardware.location.ContextHubClientCallback
import android.hardware.location.ContextHubManager
import android.hardware.location.ContextHubTransaction
import android.hardware.location.NanoAppMessage
import android.os.SystemClock
import android.util.Log
import android.util.StatsEvent
import android.util.StatsLog
import com.android.internal.logging.UiEventLogger
import com.android.internal.util.RingBuffer
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.google.android.systemui.columbus.ColumbusEvent
import com.google.android.systemui.columbus.legacy.sensors.config.GestureConfiguration
import com.google.android.systemui.columbus.proto.nano.ColumbusProto
import com.google.protobuf.nano.InvalidProtocolBufferNanoException
import com.google.protobuf.nano.MessageNano
import java.io.PrintWriter
import java.util.concurrent.Executor
import javax.inject.Inject

@SysUISingleton
class CHREGestureSensor
@Inject
constructor(
    private val context: Context,
    private val uiEventLogger: UiEventLogger,
    private val gestureConfiguration: GestureConfiguration,
    private val statusBarStateController: StatusBarStateController,
    private val wakefulnessLifecycle: WakefulnessLifecycle,
    @Background private val bgExecutor: Executor,
) : GestureSensor(), Dumpable {
    private var contextHubClient: ContextHubClient? = null
    private val featureVectorDumper = FeatureVectorDumper()
    private var isInitialized = false
    private var isListening = false
    private var isDozing = statusBarStateController.isDozing
    private var isAwake = wakefulnessLifecycle.wakefulness == WakefulnessLifecycle.WAKEFULNESS_AWAKE
    private var screenOn = isAwake && !isDozing
    private var screenStateUpdated = true

    private val contextHubClientCallback =
        object : ContextHubClientCallback() {
            override fun onMessageFromNanoApp(client: ContextHubClient, message: NanoAppMessage) {
                if (message.nanoAppId != NANOAPP_ID) return
                try {
                    when (message.messageType) {
                        MSG_GESTURE_DETECTED ->
                            handleGestureDetection(
                                ColumbusProto.GestureDetected.parseFrom(message.messageBody)
                            )
                        MSG_NANOAPP_EVENTS ->
                            handleNanoappEvents(
                                ColumbusProto.NanoappEvents.parseFrom(message.messageBody)
                            )
                        else -> Log.e(TAG, "Unknown message type: ${message.messageType}")
                    }
                } catch (e: InvalidProtocolBufferNanoException) {
                    Log.e(TAG, "Invalid protocol buffer", e)
                }
            }

            override fun onHubReset(client: ContextHubClient) {
                Log.d(TAG, "HubReset: ${client.attachedHub.id}")
            }

            override fun onNanoAppAborted(
                client: ContextHubClient,
                nanoAppId: Long,
                abortCode: Int,
            ) {
                if (nanoAppId == NANOAPP_ID) {
                    Log.e(TAG, "Nanoapp aborted, code: $abortCode")
                }
            }

            override fun onNanoAppLoaded(client: ContextHubClient, nanoAppId: Long) {
                if (nanoAppId == NANOAPP_ID && isListening) {
                    Log.d(TAG, "Nanoapp loaded")
                    updateScreenState()
                    startRecognizer()
                }
            }
        }

    private val statusBarStateListener =
        object : StatusBarStateController.StateListener {
            override fun onDozingChanged(isDozing: Boolean) {
                if (this@CHREGestureSensor.isDozing != isDozing) {
                    this@CHREGestureSensor.isDozing = isDozing
                    updateScreenState()
                }
            }
        }

    private val wakefulnessLifecycleObserver =
        object : WakefulnessLifecycle.Observer {
            override fun onStartedWakingUp() {
                setAwake(false)
            }

            override fun onFinishedWakingUp() {
                setAwake(true)
            }

            override fun onStartedGoingToSleep() {
                setAwake(false)
            }

            override fun onFinishedGoingToSleep() {
                setAwake(false)
            }
        }

    private fun setAwake(awake: Boolean) {
        if (isAwake != awake) {
            isAwake = awake
            updateScreenState()
        }
    }

    override fun isListening() = isListening

    override fun startListening() {
        if (!isInitialized) {
            Log.i(TAG, "Legacy CHREGestureSensor initialize")
            isInitialized = true
            gestureConfiguration.listener = GestureConfiguration.Listener { sensitivity ->
                val message =
                    ColumbusProto.SensitivityUpdate().apply { this.sensitivity = sensitivity }
                sendMessageToNanoApp(MSG_SENSITIVITY_UPDATE, MessageNano.toByteArray(message))
            }
            statusBarStateController.addCallback(statusBarStateListener)
            wakefulnessLifecycle.addObserver(wakefulnessLifecycleObserver)
            initializeContextHubClientIfNull()
        }
        Log.i(TAG, "Legacy CHREGestureSensor startListening")
        isListening = true
        startRecognizer()
        sendScreenState()
    }

    override fun stopListening() {
        Log.i(TAG, "Legacy CHREGestureSensor stopListening")
        sendMessageToNanoApp(
            MSG_RECOGNIZER_STOP,
            ByteArray(0),
            onSuccess = { uiEventLogger.log(ColumbusEvent.COLUMBUS_MODE_INACTIVE) },
        )
        isListening = false
    }

    override fun close() {
        Log.i(TAG, "Legacy CHREGestureSensor close")
        if (!isInitialized) return
        gestureConfiguration.listener = null
        statusBarStateController.removeCallback(statusBarStateListener)
        wakefulnessLifecycle.removeObserver(wakefulnessLifecycleObserver)
        contextHubClient?.close()
        contextHubClient = null
        isInitialized = false
    }

    private fun initializeContextHubClientIfNull() {
        if (contextHubClient != null) return
        val contextHubManager = context.getSystemService(ContextHubManager::class.java)
        val contextHubs = contextHubManager?.contextHubs
        if (contextHubs.isNullOrEmpty()) {
            Log.e(TAG, "No context hubs found")
            return
        }
        contextHubClient =
            contextHubManager.createClient(contextHubs[0], contextHubClientCallback, bgExecutor)
    }

    private fun startRecognizer() {
        val message =
            ColumbusProto.RecognizerStart().apply { sensitivity = gestureConfiguration.sensitivity }
        sendMessageToNanoApp(
            MSG_RECOGNIZER_START,
            MessageNano.toByteArray(message),
            onSuccess = { uiEventLogger.log(ColumbusEvent.COLUMBUS_MODE_LOW_POWER_ACTIVE) },
        )
    }

    private fun updateScreenState() {
        val screenOn = isAwake && !isDozing
        if (this.screenOn == screenOn && screenStateUpdated) return
        this.screenOn = screenOn
        if (isListening) {
            sendScreenState()
        }
    }

    private fun sendScreenState() {
        val message =
            ColumbusProto.ScreenStateUpdate().apply {
                screenState =
                    if (screenOn) {
                        ColumbusProto.ScreenStateUpdate.SCREEN_ON
                    } else {
                        ColumbusProto.ScreenStateUpdate.SCREEN_OFF
                    }
            }
        sendMessageToNanoApp(
            MSG_SCREEN_STATE_UPDATE,
            MessageNano.toByteArray(message),
            onSuccess = { screenStateUpdated = true },
            onFail = { screenStateUpdated = false },
        )
    }

    private fun sendMessageToNanoApp(
        messageType: Int,
        bytes: ByteArray,
        onSuccess: (() -> Unit)? = null,
        onFail: (() -> Unit)? = null,
    ) {
        initializeContextHubClientIfNull()
        if (contextHubClient == null) {
            Log.w(TAG, "ContextHubClient null")
            return
        }
        bgExecutor.execute {
            val client = contextHubClient
            if (client == null) {
                Log.w(TAG, "ContextHubClient null")
                return@execute
            }
            val message = NanoAppMessage.createMessageToNanoApp(NANOAPP_ID, messageType, bytes)
            val result = client.sendMessageToNanoApp(message)
            if (result == ContextHubTransaction.RESULT_SUCCESS) {
                onSuccess?.invoke()
            } else {
                Log.e(TAG, "Unable to send message $messageType to nanoapp, error code $result")
                onFail?.invoke()
            }
        }
    }

    private fun handleGestureDetection(gestureDetected: ColumbusProto.GestureDetected) {
        val flags =
            when (gestureDetected.gestureType) {
                ColumbusProto.GestureDetected.GESTURE_DOUBLE_TAP -> 1
                ColumbusProto.GestureDetected.GESTURE_SINGLE_TAP -> 2
                else -> 0
            }
        val isSingleTap =
            gestureDetected.gestureType == ColumbusProto.GestureDetected.GESTURE_SINGLE_TAP
        listener?.onGestureDetected(flags, DetectionProperties(isSingleTap))
        featureVectorDumper.onGestureDetected(gestureDetected)
    }

    private fun handleNanoappEvents(nanoappEvents: ColumbusProto.NanoappEvents) {
        nanoappEvents.batchedEvents.forEach {
            StatsLog.write(
                StatsEvent.newBuilder()
                    .setAtomId(DOUBLE_TAP_NANOAPP_EVENT_REPORTED)
                    .writeLong(it.timestamp)
                    .writeInt(toAtomEventType(it.type))
                    .build()
            )
        }
    }

    // PixelAtoms.DoubleTapNanoappEventReported.Type mirrors the nanoapp event types one-to-one.
    private fun toAtomEventType(type: Int): Int =
        when (type) {
            in ColumbusProto.NanoappEvent.GATE_START..ColumbusProto.NanoappEvent.DOUBLE_TAP -> type
            else -> ColumbusProto.NanoappEvent.UNKNOWN
        }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        featureVectorDumper.dump(pw, args)
    }

    private class FeatureVector(gestureDetected: ColumbusProto.GestureDetected) : Dumpable {
        private val vector = gestureDetected.featureVector
        private val gesture = gestureDetected.gestureType
        private val timestamp = SystemClock.elapsedRealtime()

        override fun dump(pw: PrintWriter, args: Array<out String>) {
            pw.println("      Gesture: $gesture Time: ${timestamp - SystemClock.elapsedRealtime()}")
            pw.println("      " + vector.joinToString(", "))
        }
    }

    private class FeatureVectorDumper : Dumpable {
        private val featureVectors = RingBuffer(FeatureVector::class.java, 10)
        private var lastSingleTapFeatureVector: FeatureVector? = null

        fun onGestureDetected(gestureDetected: ColumbusProto.GestureDetected) {
            when (gestureDetected.gestureType) {
                ColumbusProto.GestureDetected.GESTURE_SINGLE_TAP ->
                    lastSingleTapFeatureVector = FeatureVector(gestureDetected)
                ColumbusProto.GestureDetected.GESTURE_DOUBLE_TAP -> {
                    val singleTap = lastSingleTapFeatureVector
                    lastSingleTapFeatureVector = null
                    if (singleTap == null) {
                        Log.w(
                            TAG,
                            "Received double tap without single taps, event will not appear in " +
                                "sysdump",
                        )
                    } else {
                        featureVectors.append(singleTap)
                        featureVectors.append(FeatureVector(gestureDetected))
                    }
                }
            }
        }

        override fun dump(pw: PrintWriter, args: Array<out String>) {
            pw.println("    Feature Vectors:")
            featureVectors.toArray().forEach { it.dump(pw, args) }
        }
    }

    private companion object {
        const val TAG = "Columbus/GestureSensor"
        const val NANOAPP_ID = 0x476f6f676c001019L

        const val MSG_RECOGNIZER_START = 100
        const val MSG_RECOGNIZER_STOP = 101
        const val MSG_SENSITIVITY_UPDATE = 200
        const val MSG_GESTURE_DETECTED = 300
        const val MSG_SCREEN_STATE_UPDATE = 400
        const val MSG_NANOAPP_EVENTS = 500

        // PixelAtoms.DoubleTapNanoappEventReported; pixelatoms-java is vendor-only.
        const val DOUBLE_TAP_NANOAPP_EVENT_REPORTED = 100051
    }
}
