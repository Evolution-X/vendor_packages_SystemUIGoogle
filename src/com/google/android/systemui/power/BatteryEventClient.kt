package com.google.android.systemui.power

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.aidl.IBatteryEventService
import com.google.android.systemui.power.batteryevent.aidl.IBatteryEventsListener
import com.google.android.systemui.power.batteryevent.aidl.SurfaceType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BatteryEventClient
@Inject
constructor(
    private val context: Context,
    @Application private val coroutineScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val emptyCallback: (List<BatteryEventType>, Int, Int) -> Unit = { _, _, _ ->
        logWithCaller.d("No callback for battery event update")
    }

    private var callerTag = "--"
    private var onBatteryEventUpdate = emptyCallback
    private val subscribedBatteryEvents = ArrayList<BatteryEventType>()
    private var surfaceType: SurfaceType? = null
    private var service: IBatteryEventService? = null

    private val logWithCaller =
        object {
            fun d(msg: String) = Log.d(TAG, "[$callerTag] $msg")

            fun w(msg: String) = Log.w(TAG, "[$callerTag] $msg")
        }

    private val listener =
        object : IBatteryEventsListener.Stub() {
            override fun onBatteryEventChanged(
                events: List<BatteryEventType>?,
                batteryLevel: Int,
                pluggedType: Int,
            ) {
                logWithCaller.d("onBatteryEventChanged: $events, batteryLevel: $batteryLevel")
                onBatteryEventUpdate(events ?: emptyList(), batteryLevel, pluggedType)
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = IBatteryEventService.Stub.asInterface(binder)
                try {
                    val service = service
                    if (service != null) {
                        service.registerBatteryEventsCallback(
                            listener,
                            subscribedBatteryEvents,
                            surfaceType,
                        )
                        return
                    }
                    logWithCaller.w("bound service for ${surfaceType?.name} failed")
                } catch (e: RemoteException) {
                    Log.e(
                        TAG,
                        "[$callerTag] unexpected exception for registerBatteryEventCallback",
                        e,
                    )
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                logWithCaller.d("onServiceDisconnected")
                callerTag = "--"
                onBatteryEventUpdate = emptyCallback
                subscribedBatteryEvents.clear()
                surfaceType = null
                service = null
            }
        }

    fun registerBatteryEventCallback(
        surfaceType: SurfaceType,
        callerTag: String,
        batteryEvents: List<BatteryEventType>,
        onBatteryEventUpdate: (List<BatteryEventType>, Int, Int) -> Unit,
    ) {
        if (service != null) {
            logWithCaller.w(
                "already registered for ${surfaceType.name}, need to unregister before " +
                    "register again"
            )
            return
        }
        this.surfaceType = surfaceType
        this.callerTag = callerTag
        subscribedBatteryEvents.addAll(batteryEvents)
        this.onBatteryEventUpdate = onBatteryEventUpdate
        coroutineScope.launch(backgroundDispatcher) {
            val intent =
                Intent().setComponent(ComponentName(SYSTEMUI_PACKAGE, BATTERY_EVENT_SERVICE))
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private companion object {
        const val TAG = "BatteryEventClient"
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        const val BATTERY_EVENT_SERVICE =
            "com.google.android.systemui.power.batteryevent.domain.BatteryEventService"
    }
}
