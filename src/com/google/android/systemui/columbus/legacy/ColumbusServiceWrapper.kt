package com.google.android.systemui.columbus.legacy

import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.columbus.legacy.actions.SettingsAction
import dagger.Lazy
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
class ColumbusServiceWrapper
@Inject
constructor(
    private val columbusSettings: ColumbusSettings,
    private val columbusService: Lazy<ColumbusService>,
    settingsAction: Lazy<SettingsAction>,
    columbusStructuredDataManager: Lazy<ColumbusStructuredDataManager>,
) : Dumpable {
    private var started = false

    private val settingsChangeListener =
        object : ColumbusSettings.ColumbusSettingsChangeListener {
            override fun onColumbusEnabledChange(enabled: Boolean) {
                if (enabled) {
                    startService()
                }
            }
        }

    init {
        if (columbusSettings.isColumbusEnabled()) {
            startService()
        } else {
            columbusSettings.registerColumbusSettingsChangeListener(settingsChangeListener)
            // Binds SettingsAction to ColumbusServiceProxy ahead of the service starting.
            settingsAction.get()
        }
        columbusStructuredDataManager.get()
    }

    private fun startService() {
        columbusSettings.unregisterColumbusSettingsChangeListener(settingsChangeListener)
        started = true
        columbusService.get()
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        if (started) {
            columbusService.get().dump(pw, args)
        }
    }
}
