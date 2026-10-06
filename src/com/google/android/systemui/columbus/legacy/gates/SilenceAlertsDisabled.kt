package com.google.android.systemui.columbus.legacy.gates

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.google.android.systemui.columbus.legacy.ColumbusSettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class SilenceAlertsDisabled
@Inject
constructor(
    private val columbusSettings: ColumbusSettings,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : Gate() {
    private val settingsChangeListener =
        object : ColumbusSettings.ColumbusSettingsChangeListener {
            override fun onAlertSilenceEnabledChange(enabled: Boolean) {
                updateSilenceAlertsEnabled(enabled)
            }
        }

    override fun onActivate() {
        columbusSettings.registerColumbusSettingsChangeListener(settingsChangeListener)
        coroutineScope.launch(mainDispatcher) {
            updateSilenceAlertsEnabled(
                withContext(bgDispatcher) { columbusSettings.silenceAlertsEnabled() }
            )
        }
    }

    override fun onDeactivate() {
        columbusSettings.unregisterColumbusSettingsChangeListener(settingsChangeListener)
    }

    private fun updateSilenceAlertsEnabled(enabled: Boolean) {
        coroutineScope.launch { setBlocking(!enabled) }
    }
}
