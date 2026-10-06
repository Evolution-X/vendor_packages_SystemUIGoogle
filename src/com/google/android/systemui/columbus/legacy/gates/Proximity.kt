package com.google.android.systemui.columbus.legacy.gates

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.util.sensors.ProximitySensor
import com.android.systemui.util.sensors.ThresholdSensor
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
class Proximity @Inject constructor(private val proximitySensor: Lazy<ProximitySensor>) : Gate() {
    private val proximityListener = ThresholdSensor.Listener { updateBlocking() }

    override fun onActivate() {
        proximitySensor.get().register(proximityListener)
        updateBlocking()
    }

    override fun onDeactivate() {
        proximitySensor.get().unregister(proximityListener)
    }

    private fun updateBlocking() {
        setBlocking(proximitySensor.get().isNear() == true)
    }
}
