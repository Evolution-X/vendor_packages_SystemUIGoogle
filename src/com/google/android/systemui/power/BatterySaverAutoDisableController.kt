package com.google.android.systemui.power

import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.UserTracker
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.SecureSettings
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SysUISingleton
class BatterySaverAutoDisableController
@Inject
constructor(
    val secureSettings: SecureSettings,
    val globalSettings: GlobalSettings,
    val userTracker: UserTracker,
    @Background val backgroundExecutor: Executor,
    @Application val backgroundCoroutineScope: CoroutineScope,
) {
    private val chargeLimitModeObserver =
        object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                updateAutoDisableLevel()
            }
        }

    private val userCallback =
        object : UserTracker.Callback {
            override fun onUserChanged(newUser: Int, userContext: Context) {
                Log.i(TAG, "onUserChanged: $newUser")
                updateAutoDisableLevelInternal()
            }
        }

    fun start() {
        userTracker.addCallback(userCallback, backgroundExecutor)
        try {
            secureSettings.registerContentObserverAsync(
                Settings.Secure.getUriFor("charge_optimization_mode"),
                false,
                chargeLimitModeObserver,
            )
        } catch (e: Exception) {
            Log.e(TAG, "registerChargeLimitModeObserver() failed", e)
        }
        Log.i(TAG, "registerChargeLimitModeObserver() force update")
        updateAutoDisableLevel()
    }

    fun updateAutoDisableLevel() {
        backgroundCoroutineScope.launch { updateAutoDisableLevelInternal() }
    }

    private fun updateAutoDisableLevelInternal() {
        val chargeLimit = PowerUtils.isChargeLimitEnabledForUser(secureSettings, userTracker.userId)
        val currentLevel = globalSettings.getInt("low_power_sticky_auto_disable_level", 90)
        val targetLevel = if (chargeLimit) 80 else 90
        Log.i(
            TAG,
            "updateAutoDisableLevel: chargeLimit=$chargeLimit, " +
                "currentLevel=$currentLevel, targetLevel=$targetLevel",
        )
        if (currentLevel == targetLevel) {
            return
        }
        globalSettings.putInt("low_power_sticky_auto_disable_level", targetLevel)
    }

    companion object {
        private const val TAG = "BatterySaverAutoDisableController"
    }
}
