package com.google.android.systemui.columbus.legacy.gates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.TRANSIENT_GATE_DURATION
import javax.inject.Inject
import javax.inject.Named

@SysUISingleton
class ChargingState
@Inject
constructor(
    private val broadcastDispatcher: BroadcastDispatcher,
    @Named(TRANSIENT_GATE_DURATION) private val gateDuration: Long,
) : TransientGate() {
    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                blockForMillis(gateDuration)
            }
        }

    override fun onActivate() {
        val intentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        broadcastDispatcher.registerReceiver(powerReceiver, intentFilter)
    }

    override fun onDeactivate() {
        broadcastDispatcher.unregisterReceiver(powerReceiver)
    }
}
