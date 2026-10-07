package com.google.android.systemui.power.batteryevent.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.util.Log
import com.android.systemui.broadcast.BroadcastDispatcher
import com.google.android.systemui.power.batteryevent.aidl.BatteryEventType
import com.google.android.systemui.power.batteryevent.common.BatteryEventSubscriber
import com.google.android.systemui.power.batteryevent.common.EventDataType
import com.google.android.systemui.power.batteryevent.common.FrameworkApiDataType
import com.google.android.systemui.power.batteryevent.common.HalDataType
import com.google.android.systemui.power.batteryevent.common.SettingsDataType
import com.google.android.systemui.power.batteryevent.common.data.EventData
import com.google.android.systemui.power.batteryevent.common.data.FrameworkApiEventData
import com.google.android.systemui.power.batteryevent.common.data.HalEventData
import com.google.android.systemui.power.batteryevent.common.data.SettingsEventData
import com.google.android.systemui.power.batteryevent.common.data.SystemEventData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemEventDataSource(
    private val halDataSource: HalDataSource,
    private val settingsDataSource: SettingsDataSource,
    private val frameworkDataSource: FrameworkDataSource,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val backgroundDispatcher: CoroutineDispatcher,
    private val applicationScope: CoroutineScope,
) : BroadcastReceiver() {
    private var subscribers: List<BatteryEventSubscriber> = emptyList()
    private var onEventSourceUpdate: (SystemEventData, List<BatteryEventType>) -> Unit = { _, _ -> }
    private val actionToEventDataTypeCache = mutableMapOf<String, List<EventDataType>>()
    private var lastSystemEventData =
        SystemEventData(
            intentAction = "",
            plugged = EventData(0),
            batteryScale = EventData(0),
            batteryLevel = EventData(0),
            chargingStatus = EventData(0),
            maxChargingCurrent = EventData(0),
            maxChargingVoltage = EventData(0),
            batteryStatus = EventData(0),
            halEventData = HalEventData(EventData(0), EventData(false), EventData(false)),
            settingsEventData =
                SettingsEventData(EventData(0), EventData(0), EventData(false), EventData(false)),
            frameworkApiEventData = FrameworkApiEventData(EventData(false), EventData(false)),
        )

    fun subscribe(
        subscribers: List<BatteryEventSubscriber>,
        onEventSourceUpdate: (SystemEventData, List<BatteryEventType>) -> Unit,
    ) {
        this.subscribers = subscribers
        this.onEventSourceUpdate = onEventSourceUpdate
        val filter = IntentFilter()
        subscribers.forEach { subscriber -> subscriber.actions.forEach { filter.addAction(it) } }
        broadcastDispatcher.registerReceiver(this, filter)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val intentAction = intent?.action
        if (intent == null || intentAction.isNullOrEmpty()) {
            Log.w(TAG, "onReceive, unexpected intent $intent")
            return
        }
        Log.d(TAG, "onReceive: intentAction")
        applicationScope.launch {
            withContext(backgroundDispatcher) { processIntent(context, intent, intentAction) }
        }
    }

    private fun processIntent(context: Context, receivedIntent: Intent, intentAction: String) {
        val batteryIntent =
            if (intentAction == Intent.ACTION_BATTERY_CHANGED) {
                receivedIntent
            } else {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }
        if (batteryIntent == null) {
            Log.i(TAG, "no battery sticky intent")
            return
        }
        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val chargingStatus =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_CHARGING_STATUS,
                BatteryManager.CHARGING_POLICY_DEFAULT,
            )
        val maxChargingCurrent =
            batteryIntent.getIntExtra(BatteryManager.EXTRA_MAX_CHARGING_CURRENT, 0)
        val maxChargingVoltage =
            batteryIntent.getIntExtra(BatteryManager.EXTRA_MAX_CHARGING_VOLTAGE, 0)
        val status =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN,
            )

        val eventDataTypes = getAllEventDataType(intentAction)
        halDataSource.update(eventDataTypes.filterIsInstance<HalDataType>(), plugged)
        settingsDataSource.update(eventDataTypes.filterIsInstance<SettingsDataType>())
        frameworkDataSource.update(eventDataTypes.filterIsInstance<FrameworkApiDataType>())

        val last = lastSystemEventData
        val updatedEventData =
            SystemEventData(
                intentAction = intentAction,
                plugged = last.plugged.update(plugged),
                batteryScale = last.batteryScale.update(scale),
                batteryLevel = last.batteryLevel.update(level),
                chargingStatus = last.chargingStatus.update(chargingStatus),
                maxChargingCurrent = last.maxChargingCurrent.update(maxChargingCurrent),
                maxChargingVoltage = last.maxChargingVoltage.update(maxChargingVoltage),
                batteryStatus = last.batteryStatus.update(status),
                halEventData =
                    HalEventData(
                        last.halEventData.dockDefendStatus.update(
                            halDataSource.lastGoogleBatteryDockDefendStatus
                        ),
                        last.halEventData.tempDefendEventData.update(
                            halDataSource.lastTempDefendStatus
                        ),
                        last.halEventData.dwellDefendEventData.update(
                            halDataSource.lastDwellDefendStatus
                        ),
                    ),
                settingsEventData =
                    SettingsEventData(
                        last.settingsEventData.dockDefenderBypass.update(
                            settingsDataSource.lastDockDefenderByPass
                        ),
                        last.settingsEventData.chargingLimitSettings.update(
                            settingsDataSource.lastChargingLimitSettings
                        ),
                        last.settingsEventData.dndState.update(settingsDataSource.lastDndState),
                        last.settingsEventData.airplaneState.update(
                            settingsDataSource.lastAirplaneState
                        ),
                    ),
                frameworkApiEventData =
                    FrameworkApiEventData(
                        last.frameworkApiEventData.batterySaverState.update(
                            frameworkDataSource.lastBatterySaverState
                        ),
                        last.frameworkApiEventData.extremeBatterySaverState.update(
                            frameworkDataSource.lastExtremeBatterySaverState
                        ),
                    ),
            )
        Log.d(TAG, "updatedEventData: $updatedEventData")
        if (
            intentAction != UsbManager.ACTION_USB_PORT_COMPLIANCE_CHANGED &&
                updatedEventData == lastSystemEventData
        ) {
            Log.d(TAG, "extra doesn't changed, no need to onEventSourceUpdate")
            return
        }
        lastSystemEventData = updatedEventData
        onEventSourceUpdate(updatedEventData, BatteryEventType.entries)
    }

    private fun getAllEventDataType(intentAction: String): List<EventDataType> =
        actionToEventDataTypeCache.getOrPut(intentAction) {
            subscribers
                .filter { it.actions.contains(intentAction) }
                .flatMap { it.eventDataType }
                .distinct()
        }

    private fun <T : Any> EventData<T>.update(newValue: T): EventData<T> =
        if (value == newValue) {
            apply { isChanged = false }
        } else {
            EventData(newValue).apply { isChanged = true }
        }

    private companion object {
        const val TAG = "SystemEventDataSource"
    }
}
