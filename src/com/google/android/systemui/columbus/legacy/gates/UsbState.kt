package com.google.android.systemui.columbus.legacy.gates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.TRANSIENT_GATE_DURATION
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.launch

@SysUISingleton
class UsbState
@Inject
constructor(
    private val context: Context,
    @Named(TRANSIENT_GATE_DURATION) private val gateDuration: Long,
) : TransientGate() {
    private var usbConnected = false

    private val usbReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent == null) return
                val newUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)
                coroutineScope.launch {
                    if (newUsbConnected != usbConnected) {
                        usbConnected = newUsbConnected
                        blockForMillis(gateDuration)
                    }
                }
            }
        }

    override fun onActivate() {
        val state = context.registerReceiver(usbReceiver, IntentFilter(UsbManager.ACTION_USB_STATE))
        coroutineScope.launch {
            usbConnected = state?.getBooleanExtra(UsbManager.USB_CONNECTED, false) ?: false
        }
    }

    override fun onDeactivate() {
        context.unregisterReceiver(usbReceiver)
    }
}
