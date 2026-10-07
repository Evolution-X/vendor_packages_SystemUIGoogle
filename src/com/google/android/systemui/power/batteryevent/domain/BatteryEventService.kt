package com.google.android.systemui.power.batteryevent.domain

import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.UserHandle
import android.util.ArrayMap
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.UserTracker
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.aidl.IBatteryEventService
import com.google.android.systemui.power.batteryevent.aidl.IBatteryEventsDataListener
import com.google.android.systemui.power.batteryevent.aidl.IBatteryEventsListener
import com.google.android.systemui.power.batteryevent.aidl.SurfaceType
import com.google.android.systemui.power.batteryevent.common.BatteryEvents
import com.google.android.systemui.power.batteryevent.repository.BatteryTimePredictionRepository
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatteryEventService
@Inject
constructor(
    private val eventStateController: BatteryEventStateController,
    private val batteryTimePredictionRepository: BatteryTimePredictionRepository,
    private val broadcastSender: BroadcastSender,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val userTracker: UserTracker,
) : LifecycleService() {

    private val broadcastIntentBatteryEventsListener =
        CopyOnWriteArraySet<BatteryEventsBroadcastData>()
    private val batteryEventsBroadcastCache = ArrayMap<String, CachedBatteryEvents>()
    private val batteryEventsCallbackCache = ArrayMap<IBatteryEventsListener, CachedBatteryEvents>()
    private val batteryEventsDataCallbackCache =
        ArrayMap<IBatteryEventsDataListener, CachedBatteryEventsData?>()

    private val aidlBatteryEventsCallbackListener =
        object : RemoteCallbackList<IBatteryEventsListener>() {
            override fun onCallbackDied(callback: IBatteryEventsListener, cookie: Any?) {
                super.onCallbackDied(callback, cookie)
                synchronized(batteryEventsCallbackCache) {
                    batteryEventsCallbackCache.remove(callback)
                }
            }

            override fun unregister(callback: IBatteryEventsListener): Boolean {
                val unregistered = super.unregister(callback)
                synchronized(batteryEventsCallbackCache) {
                    batteryEventsCallbackCache.remove(callback)
                }
                return unregistered
            }
        }

    private val aidlBatteryEventsDataCallbackListener =
        object : RemoteCallbackList<IBatteryEventsDataListener>() {
            override fun onCallbackDied(callback: IBatteryEventsDataListener, cookie: Any?) {
                super.onCallbackDied(callback, cookie)
                synchronized(batteryEventsDataCallbackCache) {
                    batteryEventsDataCallbackCache.remove(callback)
                }
            }

            override fun unregister(callback: IBatteryEventsDataListener): Boolean {
                val unregistered = super.unregister(callback)
                synchronized(batteryEventsDataCallbackCache) {
                    batteryEventsDataCallbackCache.remove(callback)
                }
                return unregistered
            }
        }

    private val binder =
        object : IBatteryEventService.Stub() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                ensureSupportedCallers()
                return super.onTransact(code, data, reply, flags)
            }

            override fun registerBatteryEventsCallback(
                listener: IBatteryEventsListener?,
                events: List<BatteryEventType>?,
                surfaceType: SurfaceType?,
            ) {
                if (listener == null) {
                    Log.w(TAG, "register fail. listener can't be null")
                    return
                }
                val callbackData =
                    BatteryEventsCallbackData(events?.toSet() ?: emptySet(), surfaceType)
                aidlBatteryEventsCallbackListener.register(listener, callbackData)
                lifecycleScope.launch(backgroundDispatcher) {
                    eventStateController.batteryEventsFlow.value?.let {
                        notifyAidlListenerBatteryEventUpdate(listener, callbackData, it)
                    }
                }
            }

            override fun unregisterBatteryEventsCallback(listener: IBatteryEventsListener?) {
                if (listener != null) {
                    aidlBatteryEventsCallbackListener.unregister(listener)
                }
            }

            override fun registerBatteryEventsUpdate(
                packageName: String?,
                className: String?,
                events: List<BatteryEventType>?,
                userId: Int,
            ) {
                if (packageName == null || className == null) {
                    Log.w(
                        TAG,
                        "registerBatteryEventsUpdate failed: packageName=$packageName, " +
                            "className=$className",
                    )
                    return
                }
                if (events != null && events.isEmpty()) {
                    Log.w(TAG, "no battery events to subscribe")
                    return
                }
                val broadcastData =
                    BatteryEventsBroadcastData(
                        ComponentName(packageName, className),
                        events?.toSet() ?: emptySet(),
                        userId,
                        "$packageName/$className-$userId",
                    )
                broadcastIntentBatteryEventsListener.add(broadcastData)
                lifecycleScope.launch(backgroundDispatcher) {
                    eventStateController.batteryEventsFlow.value?.let {
                        notifyBroadcastListenerBatteryEventUpdate(broadcastData, it)
                    }
                }
            }

            override fun unregisterBatteryEventsUpdate(
                packageName: String?,
                className: String?,
                userId: Int,
            ) {
                if (packageName == null || className == null) {
                    Log.w(
                        TAG,
                        "unregisterBatteryEventsUpdate failed. packageName=$packageName, " +
                            "className=$className",
                    )
                    return
                }
                broadcastIntentBatteryEventsListener
                    .firstOrNull {
                        it.componentName.packageName == packageName &&
                            it.componentName.className == className &&
                            it.userId == userId
                    }
                    ?.let {
                        broadcastIntentBatteryEventsListener.remove(it)
                        synchronized(batteryEventsBroadcastCache) {
                            batteryEventsBroadcastCache.remove(it.indexKey)
                        }
                    }
                Log.i(
                    TAG,
                    "unregisterBatteryEventsUpdate:packageName: $packageName, " +
                        "className: $className, userId: $userId",
                )
            }

            override fun reserved() {}

            override fun registerBatteryEventsDataCallback(
                listener: IBatteryEventsDataListener?,
                events: List<BatteryEventType>?,
                timeToFullSocs: IntArray?,
            ) {
                if (listener == null) {
                    Log.w(TAG, "registerBatteryEventsDataCallback fail. listener can't be null")
                    return
                }
                val callbackData =
                    BatteryEventsDataCallbackData(
                        events?.toSet() ?: emptySet(),
                        timeToFullSocs ?: IntArray(0),
                    )
                aidlBatteryEventsDataCallbackListener.register(listener, callbackData)
                synchronized(batteryEventsDataCallbackCache) {
                    batteryEventsDataCallbackCache[listener] = null
                }
                lifecycleScope.launch(backgroundDispatcher) {
                    eventStateController.batteryEventsFlow.value?.let {
                        notifyAidlListenerBatteryEventsDataUpdate(listener, callbackData, it)
                    }
                }
            }

            override fun unregisterBatteryEventsDataCallback(
                listener: IBatteryEventsDataListener?
            ) {
                if (listener != null) {
                    aidlBatteryEventsDataCallbackListener.unregister(listener)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch(backgroundDispatcher) {
            eventStateController.batteryEventsFlow.collect { batteryEvents ->
                Log.d(TAG, "collect BatteryEvents: $batteryEvents")
                if (batteryEvents != null) {
                    withContext(backgroundDispatcher) {
                        notifyForBatteryEventsUpdate(batteryEvents)
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Log.i(TAG, "BatteryEventService bound")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.cancel(CancellationException("BatteryEventService destroyed"))
        broadcastIntentBatteryEventsListener.clear()
        aidlBatteryEventsCallbackListener.kill()
        aidlBatteryEventsDataCallbackListener.kill()
    }

    private fun ensureSupportedCallers() {
        val callingUid = Binder.getCallingUid()
        Log.d(TAG, "ensureSupportedCallers: uid=$callingUid")
        val packages = applicationContext.packageManager.getPackagesForUid(callingUid) ?: return
        if (packages.none { supportedCallers.contains(it) }) {
            throw SecurityException("ensureSupportedCallers: ${packages.contentToString()}")
        }
    }

    private suspend fun notifyForBatteryEventsUpdate(events: BatteryEvents) {
        Log.d(TAG, "notifyForBatteryEventsUpdate: $events")
        withContext(backgroundDispatcher) { notifyAidlBatteryEventsCallbacks(events) }
        withContext(backgroundDispatcher) { notifyBroadcastBatteryEventsUpdate(events) }
        withContext(backgroundDispatcher) { notifyAidlBatteryEventsDataCallbacks(events) }
    }

    private suspend fun notifyAidlBatteryEventsCallbacks(events: BatteryEvents) {
        val startTime = System.currentTimeMillis()
        val count = aidlBatteryEventsCallbackListener.beginBroadcast()
        Log.d(TAG, "AIDL callback listeners count: $count")
        try {
            for (i in 0 until count) {
                val listenerStartTime = System.currentTimeMillis()
                val callbackData =
                    aidlBatteryEventsCallbackListener.getBroadcastCookie(i)
                        as BatteryEventsCallbackData
                notifyAidlListenerBatteryEventUpdate(
                    aidlBatteryEventsCallbackListener.getBroadcastItem(i),
                    callbackData,
                    events,
                )
                // Stock casts the cookie to SurfaceType and always logs "null"; log the
                // registered surface instead.
                Log.d(
                    TAG,
                    "notify AIDL callback to ${callbackData.surfaceType?.name}, cost: " +
                        "${System.currentTimeMillis() - listenerStartTime} ms",
                )
            }
        } finally {
            aidlBatteryEventsCallbackListener.finishBroadcast()
        }
        Log.i(TAG, "notify all AIDL callbacks, cost: ${System.currentTimeMillis() - startTime} ms")
    }

    private suspend fun notifyBroadcastBatteryEventsUpdate(events: BatteryEvents) {
        val startTime = System.currentTimeMillis()
        Log.d(
            TAG,
            "BatteryEventsListener (broadcast) count: ${broadcastIntentBatteryEventsListener.size}",
        )
        for (broadcastData in broadcastIntentBatteryEventsListener) {
            notifyBroadcastListenerBatteryEventUpdate(broadcastData, events)
        }
        Log.i(
            TAG,
            "notify all broadcast intent, cost: ${System.currentTimeMillis() - startTime} ms",
        )
    }

    private suspend fun notifyAidlBatteryEventsDataCallbacks(events: BatteryEvents) {
        val startTime = System.currentTimeMillis()
        val count = aidlBatteryEventsDataCallbackListener.beginBroadcast()
        Log.d(TAG, "AIDL data callback listeners count: $count")
        if (count == 0) {
            aidlBatteryEventsDataCallbackListener.finishBroadcast()
            return
        }
        try {
            for (i in 0 until count) {
                try {
                    notifyAidlListenerBatteryEventsDataUpdate(
                        aidlBatteryEventsDataCallbackListener.getBroadcastItem(i),
                        aidlBatteryEventsDataCallbackListener.getBroadcastCookie(i)
                            as BatteryEventsDataCallbackData,
                        events,
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to notify listener at index $i", e)
                }
            }
        } finally {
            aidlBatteryEventsDataCallbackListener.finishBroadcast()
        }
        Log.i(
            TAG,
            "run notifyAidlBatteryEventsDataCallbacks() in " +
                "${System.currentTimeMillis() - startTime} ms",
        )
    }

    private fun notifyAidlListenerBatteryEventUpdate(
        listener: IBatteryEventsListener,
        callbackData: BatteryEventsCallbackData,
        events: BatteryEvents,
    ) {
        try {
            val subscribedEvents =
                BatteryEvents(
                    callbackData.subscribedEvents.intersect(events.eventTypes),
                    events.batteryLevel,
                    events.pluggedType,
                )
            val cachedEvents =
                updateBatteryEventsCallbackCache(callbackData, subscribedEvents, listener) ?: return
            Log.d(
                TAG,
                "aidlCallback: ${callbackData.surfaceType}, " +
                    "events: ${cachedEvents.needNotifiedEvents}",
            )
            listener.onBatteryEventChanged(
                cachedEvents.needNotifiedEvents.toList(),
                cachedEvents.batteryLevel,
                cachedEvents.pluggedType,
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "unexpected exception", e)
        }
    }

    private suspend fun notifyAidlListenerBatteryEventsDataUpdate(
        listener: IBatteryEventsDataListener,
        callbackData: BatteryEventsDataCallbackData,
        events: BatteryEvents,
    ) {
        try {
            val timeToFullBundle =
                batteryTimePredictionRepository.getTimeToFullBundle(
                    callbackData.subscribedTimeToFullSocs
                )
            val batteryInfoBundle =
                Bundle(timeToFullBundle.size() + 2).apply {
                    putAll(timeToFullBundle)
                    putInt("current_battery_level", events.batteryLevel)
                    putInt("plugged_type", events.pluggedType)
                }
            val cachedEventsData =
                updateBatteryEventsDataCallbackCache(
                    listener,
                    callbackData.subscribedEvents.intersect(events.eventTypes),
                    batteryInfoBundle,
                ) ?: return
            listener.onBatteryEventDataChanged(
                cachedEventsData.needNotifiedEvents.toList(),
                cachedEventsData.batteryInfoBundle,
            )
        } catch (e: RemoteException) {
            Log.w(TAG, "Unexpected remote exception", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying data callback", e)
        }
    }

    private fun notifyBroadcastListenerBatteryEventUpdate(
        broadcastData: BatteryEventsBroadcastData,
        events: BatteryEvents,
    ) {
        if (userTracker.userId != broadcastData.userId) {
            Log.d(TAG, "non-current user(uid:${broadcastData.userId}), skip broadcast")
            return
        }
        val subscribedEvents = broadcastData.subscribedEvents.intersect(events.eventTypes)
        val cachedEvents =
            synchronized(batteryEventsBroadcastCache) {
                val cached = batteryEventsBroadcastCache[broadcastData.indexKey]
                if (
                    cached == null ||
                        !cached.isEqual(subscribedEvents, events.batteryLevel, events.pluggedType)
                ) {
                    CachedBatteryEvents(subscribedEvents, events.batteryLevel, events.pluggedType)
                        .also { batteryEventsBroadcastCache[broadcastData.indexKey] = it }
                } else {
                    null
                }
            } ?: return
        val intent =
            Intent(ACTION_BATTERY_EVENTS_UPDATE)
                .setComponent(broadcastData.componentName)
                .putStringArrayListExtra(
                    "battery_event.event_name_list",
                    ArrayList(cachedEvents.needNotifiedEvents.map { it.typeName }),
                )
                .putExtra("battery_event.battery_level", cachedEvents.batteryLevel)
                .putExtra("battery_event.battery_plugged", cachedEvents.pluggedType)
        Log.d(
            TAG,
            "broadcastIntent(uid:${broadcastData.userId}): $intent, events:$subscribedEvents",
        )
        broadcastSender.sendBroadcastAsUser(intent, UserHandle.of(broadcastData.userId))
    }

    private fun updateBatteryEventsCallbackCache(
        callbackData: BatteryEventsCallbackData,
        events: BatteryEvents,
        listener: IBatteryEventsListener,
    ): CachedBatteryEvents? =
        synchronized(batteryEventsCallbackCache) {
            val subscribedEvents = callbackData.subscribedEvents.intersect(events.eventTypes)
            val cached = batteryEventsCallbackCache[listener]
            if (
                cached == null ||
                    !cached.isEqual(subscribedEvents, events.batteryLevel, events.pluggedType)
            ) {
                CachedBatteryEvents(subscribedEvents, events.batteryLevel, events.pluggedType)
                    .also { batteryEventsCallbackCache[listener] = it }
            } else {
                null
            }
        }

    private fun updateBatteryEventsDataCallbackCache(
        listener: IBatteryEventsDataListener,
        events: Set<BatteryEventType>,
        batteryInfoBundle: Bundle,
    ): CachedBatteryEventsData? =
        synchronized(batteryEventsDataCallbackCache) {
            if (!batteryEventsDataCallbackCache.containsKey(listener)) {
                return null
            }
            val cached = batteryEventsDataCallbackCache[listener]
            if (cached == null || !cached.isEqual(events, batteryInfoBundle)) {
                CachedBatteryEventsData(events, batteryInfoBundle).also {
                    batteryEventsDataCallbackCache[listener] = it
                }
            } else {
                null
            }
        }

    data class BatteryEventsBroadcastData(
        val componentName: ComponentName,
        val subscribedEvents: Set<BatteryEventType>,
        val userId: Int,
        val indexKey: String,
    )

    data class BatteryEventsCallbackData(
        val subscribedEvents: Set<BatteryEventType>,
        val surfaceType: SurfaceType?,
    )

    class BatteryEventsDataCallbackData(
        val subscribedEvents: Set<BatteryEventType>,
        val subscribedTimeToFullSocs: IntArray,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is BatteryEventsDataCallbackData &&
                    subscribedEvents == other.subscribedEvents &&
                    subscribedTimeToFullSocs.contentEquals(other.subscribedTimeToFullSocs))

        override fun hashCode(): Int =
            subscribedEvents.hashCode() * 31 + subscribedTimeToFullSocs.contentHashCode()

        override fun toString(): String =
            "BatteryEventsDataCallbackData(subscribedEvents=$subscribedEvents, " +
                "subscribedTimeToFullSocs=${subscribedTimeToFullSocs.contentToString()})"
    }

    data class CachedBatteryEvents(
        val needNotifiedEvents: Set<BatteryEventType>,
        val batteryLevel: Int,
        val pluggedType: Int,
    ) {
        fun isEqual(events: Set<BatteryEventType>, batteryLevel: Int, pluggedType: Int): Boolean =
            needNotifiedEvents.size == events.size &&
                needNotifiedEvents.containsAll(events) &&
                this.batteryLevel == batteryLevel &&
                this.pluggedType == pluggedType
    }

    data class CachedBatteryEventsData(
        val needNotifiedEvents: Set<BatteryEventType>,
        val batteryInfoBundle: Bundle,
    ) {
        @Suppress("DEPRECATION")
        fun isEqual(events: Set<BatteryEventType>, bundle: Bundle): Boolean {
            if (
                needNotifiedEvents.size != events.size ||
                    !needNotifiedEvents.containsAll(events) ||
                    batteryInfoBundle.size() != bundle.size()
            ) {
                return false
            }
            return batteryInfoBundle.keySet().all { batteryInfoBundle.get(it) == bundle.get(it) }
        }
    }

    companion object {
        private const val TAG = "BatteryEventService"
        const val ACTION_BATTERY_EVENTS_UPDATE =
            "com.google.android.battery_event.BATTERY_EVENTS_UPDATE"

        private val supportedCallers =
            setOf(
                "com.android.settings",
                "com.android.systemui",
                "com.google.android.apps.dreamliner",
                "com.google.android.apps.pixel.support",
                "com.google.android.apps.turbo",
                "com.google.android.settings.intelligence",
            )
    }
}
