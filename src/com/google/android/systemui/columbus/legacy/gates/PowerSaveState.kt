package com.google.android.systemui.columbus.legacy.gates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.PowerManager.ServiceType
import com.android.app.tracing.coroutines.runBlockingTraced as runBlocking
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@SysUISingleton
class PowerSaveState
@Inject
constructor(
    private val broadcastDispatcher: BroadcastDispatcher,
    private val powerManager: Lazy<PowerManager>,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : Gate() {
    private var batterySaverEnabled = false
    private var isDeviceInteractive = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshStatus()
            }
        }

    override fun onActivate() {
        val intentFilter =
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        broadcastDispatcher.registerReceiver(receiver, intentFilter)
        refreshStatus()
    }

    override fun onDeactivate() {
        broadcastDispatcher.unregisterReceiver(receiver)
    }

    private fun refreshStatus() {
        coroutineScope.launch {
            val newBatterySaverEnabled =
                async(bgDispatcher) {
                    powerManager
                        .get()
                        .getPowerSaveState(ServiceType.OPTIONAL_SENSORS)
                        ?.batterySaverEnabled == true
                }
            val newIsDeviceInteractive = async(bgDispatcher) { powerManager.get().isInteractive }
            batterySaverEnabled = newBatterySaverEnabled.await()
            isDeviceInteractive = newIsDeviceInteractive.await()
            setBlocking(batterySaverEnabled && !isDeviceInteractive)
        }
    }

    override fun toString(): String =
        super.toString() +
            runBlocking(context = mainDispatcher) {
                "[batterySaverEnabled -> $batterySaverEnabled; " +
                    "isDeviceInteractive -> $isDeviceInteractive]"
            }
}
