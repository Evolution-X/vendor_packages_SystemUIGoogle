package com.google.android.systemui.power.batteryhealth

import android.content.res.Resources
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.qualifiers.Main
import com.google.android.systemui.res.R
import dagger.Lazy
import javax.inject.Inject

// Stock registers the boot receiver from CentralSurfacesGoogle.start() through a separately
// constructed Optional<HealthManager>. There is no CentralSurfacesGoogle here, so this startable
// does it on the HealthManager singleton instead.
class HealthManagerStartable
@Inject
constructor(
    @Main private val resources: Resources,
    private val healthManager: Lazy<HealthManager>,
) : CoreStartable {

    override fun start() {
        if (resources.getBoolean(R.bool.config_battery_index_enabled)) {
            healthManager.get().registerBootCompletedReceiver()
        }
    }
}
