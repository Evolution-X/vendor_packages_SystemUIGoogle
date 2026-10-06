package com.google.android.systemui.columbus.legacy.gates

import com.android.app.tracing.coroutines.runBlockingTraced as runBlocking
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.google.android.systemui.columbus.legacy.ColumbusSettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class FlagEnabled
@Inject
constructor(
    private val columbusSettings: ColumbusSettings,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : Gate() {
    private var columbusEnabled = false

    private val settingsChangeListener =
        object : ColumbusSettings.ColumbusSettingsChangeListener {
            override fun onColumbusEnabledChange(enabled: Boolean) {
                coroutineScope.launch {
                    columbusEnabled = enabled
                    updateBlocking()
                }
            }
        }

    override fun onActivate() {
        columbusSettings.registerColumbusSettingsChangeListener(settingsChangeListener)
        coroutineScope.launch {
            columbusEnabled = withContext(bgDispatcher) { columbusSettings.isColumbusEnabled() }
            updateBlocking()
        }
    }

    override fun onDeactivate() {
        columbusSettings.unregisterColumbusSettingsChangeListener(settingsChangeListener)
    }

    private fun updateBlocking() {
        coroutineScope.launch { setBlocking(!columbusEnabled) }
    }

    override fun toString(): String =
        super.toString() +
            runBlocking(context = mainDispatcher) { " [columbusEnabled -> $columbusEnabled]" }
}
