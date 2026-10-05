package com.google.android.systemui.power

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.UserTracker
import com.android.systemui.util.settings.SecureSettings
import com.google.android.systemui.googlebattery.GoogleBatteryManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vendor.google.google_battery.IGoogleBattery

@SysUISingleton
class ChargeLimitController
@Inject
constructor(
    val secureSettings: SecureSettings,
    val userTracker: UserTracker,
    @Background val backgroundDispatcher: CoroutineDispatcher,
    @Application val backgroundCoroutineScope: CoroutineScope,
) {
    private fun withGoogleBattery(action: (IGoogleBattery) -> Unit): Boolean {
        val deathRecipient = IBinder.DeathRecipient { Log.e(TAG, "Service died!!") }
        val googleBattery = GoogleBatteryManager.initHalInterface(deathRecipient)
        if (googleBattery == null) {
            Log.w(TAG, "withGoogleBattery: googleBattery is null")
            return true
        }
        return try {
            action(googleBattery)
            true
        } catch (e: Exception) {
            Log.e(TAG, "withGoogleBattery: failed to run action", e)
            false
        } finally {
            try {
                GoogleBatteryManager.destroyHalInterface(googleBattery, deathRecipient)
            } catch (e2: Exception) {
                Log.w(TAG, "withGoogleBattery: destroyHalInterface failed: ", e2)
            }
        }
    }

    fun setChargingPolicy(policy: Int) {
        backgroundCoroutineScope.launch { applyChargingPolicy(policy) }
    }

    suspend fun applyChargingPolicy(policy: Int): Boolean =
        withContext(backgroundDispatcher) {
            withGoogleBattery { battery -> battery.setChargingPolicy(policy) }
        }

    fun onBootCompleted(intent: Intent) {
        Log.d(TAG, "dispatchIntent: ${intent.action}")
        if (PowerUtils.isChargeLimitEnabledForUser(secureSettings, userTracker.userId)) {
            Log.d(TAG, "Enable charge limit upon boot completed.")
            setChargingPolicy(2)
        }
    }

    companion object {
        private const val TAG = "ChargeLimitController"
    }
}
