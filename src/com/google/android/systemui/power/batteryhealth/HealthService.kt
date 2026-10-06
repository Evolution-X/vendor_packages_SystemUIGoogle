package com.google.android.systemui.power.batteryhealth

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Binder
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.SystemProperties
import android.util.Log
import com.android.settingslib.Utils
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.settings.SecureSettings
import com.google.android.systemui.power.PulsarController
import com.google.android.systemui.res.R
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HealthService
@Inject
constructor(
    private val context: Context,
    private val healthManager: HealthManager,
    private val secureSettings: SecureSettings,
    @Main resources: Resources,
) : Service() {

    private val healthFeatureEnabled = resources.getBoolean(R.bool.config_battery_index_enabled)
    private val healthListeners = RemoteCallbackList<IHealthListener>()
    private val mainScope = MainScope()
    private var subscribeJob: Job? = null

    private var registeredListenerNum by
        Delegates.observable(0) { _, oldValue, newValue ->
            Log.i(TAG, "registered listeners num from $oldValue to $newValue")
            if (oldValue == 0 && newValue == 1) {
                subscribeJob = mainScope.launch { subscribeListeners() }
            }
            if (newValue == 0) {
                // Stock uses a lateinit job here, which crashes on an unmatched unregister.
                subscribeJob?.cancel()
            }
        }

    private val binder =
        object : IHealthService.Stub() {
            override fun getHealthData(): HealthData {
                val callerPackage = ensureSupportedCallers()
                return runBlocking {
                    Log.i(TAG, "getHealthData: ${callerPackage.contentToString()}")
                    if (callerPackage?.contains(DIAGNOSTICS_TOOL_PACKAGE) == true) {
                        healthManager.getHealthData(DIAGNOSTICS_TOOL_HEALTH_ALGO)
                    } else {
                        healthManager.getAndUpdateHealthData()
                    }
                }
            }

            override fun registerHealthListener(listener: IHealthListener?) {
                val callerPackage = ensureSupportedCallers()
                mainScope.launch {
                    Log.i(TAG, "registerHealthListener: ${callerPackage.contentToString()}")
                    healthListeners.register(listener)
                    registeredListenerNum = healthListeners.registeredCallbackCount
                }
            }

            override fun unregisterHealthListener(listener: IHealthListener?) {
                val callerPackage = ensureSupportedCallers()
                mainScope.launch {
                    Log.i(TAG, "unregisterHealthListener: ${callerPackage.contentToString()}")
                    healthListeners.unregister(listener)
                    registeredListenerNum = healthListeners.registeredCallbackCount
                }
            }

            override fun getIncompatibleChargerData(): IncompatibleChargerData {
                val callerPackage = ensureSupportedCallers()
                Log.i(TAG, "getIncompatibleChargingState: ${callerPackage.contentToString()}")
                val prefs =
                    context.applicationContext.getSharedPreferences(
                        "incompatible_charger_shared_prefs",
                        Context.MODE_PRIVATE,
                    )
                return IncompatibleChargerData(
                    prefs.getLong("last_compatible_charger_time", 0L),
                    prefs.getLong("last_incompatible_charger_time", 0L),
                    Utils.containsIncompatibleChargers(context, TAG),
                )
            }

            override fun getHealthDataWithAlgo(healthAlgo: Int): HealthData {
                val callerPackage = ensureSupportedCallers()
                return runBlocking {
                    Log.i(
                        TAG,
                        "getHealthData with algo $healthAlgo: ${callerPackage.contentToString()}",
                    )
                    healthManager.getHealthData(healthAlgo)
                }
            }

            override fun setChargingPolicy(policy: Int): Boolean {
                val callerPackage = ensureSupportedCallers()
                return runBlocking {
                    Log.i(
                        TAG,
                        "setChargingPolicy with policy $policy: ${callerPackage.contentToString()}",
                    )
                    healthManager.setChargingPolicy(policy)
                }
            }

            override fun isPulsarEnabled(): Boolean {
                val callerPackage = ensureSupportedCallers()
                Log.i(TAG, "isPulsarEnabled: ${callerPackage.contentToString()}")
                return PulsarController.isPulsarEnabled()
            }

            override fun setPulsarEnabled(enabled: Boolean) {
                val callerPackage = ensureSupportedCallers()
                Log.i(TAG, "setPulsarEnabled: ${callerPackage.contentToString()}")
                try {
                    val optOut = if (enabled) "0" else "1"
                    SystemProperties.set(PulsarController.PROP_PULSAR_OPT_OUT, optOut)
                    Log.d(
                        TAG,
                        "setSystemProperty: key= ${PulsarController.PROP_PULSAR_OPT_OUT}, " +
                            "value= $optOut",
                    )
                    val value = if (enabled) 1 else 0
                    secureSettings.putInt(PulsarController.SETTING_PULSAR_SYSPROP_ENABLED, value)
                    Log.d(
                        TAG,
                        "putSettingsSecure: key= " +
                            "${PulsarController.SETTING_PULSAR_SYSPROP_ENABLED}, value= $value",
                    )
                } catch (e: RuntimeException) {
                    Log.e(TAG, "setSystemProperty: failed.", e)
                }
            }

            override fun setRescheduled(rescheduled: Boolean) {}

            override fun isRescheduled(): Boolean = false
        }

    override fun onCreate() {
        super.onCreate()
        healthManager.registerHealthDebugReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        healthManager.unregisterHealthDebugReceiver()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "HealthService bound")
        return if (healthFeatureEnabled) binder else Binder()
    }

    private suspend fun subscribeListeners() {
        Log.d(TAG, "Subscribe listeners")
        healthManager.getHealthDataFlow().collect { (prefs, key) ->
            if (key == null) return@collect
            if (!prefs.contains(key)) {
                Log.i(TAG, "Key: $key, not from prefs")
                return@collect
            }
            Log.i(TAG, "Notify data update for key: $key")
            when (key) {
                HealthManager.KEY_CAPACITY_INDEX ->
                    notifyListeners { it.onCapacityIndexChanged(prefs.getInt(key, -1)) }
                HealthManager.KEY_HEALTH_INDEX ->
                    notifyListeners { it.onHealthIndexChanged(prefs.getInt(key, -1)) }
                HealthManager.KEY_HEALTH_STATUS ->
                    notifyListeners { it.onHealthStatusChanged(prefs.getInt(key, -1)) }
                HealthManager.KEY_PERF_INDEX ->
                    notifyListeners { it.onPerformanceIndexChanged(prefs.getInt(key, -1)) }
                else -> Log.i(TAG, "Unknown prefs key")
            }
        }
    }

    private suspend fun notifyListeners(block: (IHealthListener) -> Unit) = coroutineScope {
        val count = healthListeners.beginBroadcast()
        Log.d(TAG, "On BHI updates, listener num: $count")
        for (i in 0 until count) {
            try {
                block(healthListeners.getBroadcastItem(i))
            } catch (e: RemoteException) {
                Log.w(TAG, "Fail to callback registered listener: ", e)
            }
        }
        healthListeners.finishBroadcast()
    }

    private fun ensureSupportedCallers(): Array<String>? {
        val callingUid = Binder.getCallingUid()
        Log.d(TAG, "ensureSupportedCallers: pkg=$callingUid")
        val packages = context.packageManager.getPackagesForUid(callingUid)
        if (packages != null && packages.none { it in SUPPORTED_CALLERS }) {
            throw SecurityException("ensureSupportedCallers: ${packages.contentToString()}")
        }
        return packages
    }

    companion object {
        private const val TAG = "HealthService"
        private const val DIAGNOSTICS_TOOL_PACKAGE = "com.google.android.apps.diagnosticstool"
        // ??? Not part of BatteryHealthAlgo in google_battery V2.
        private const val DIAGNOSTICS_TOOL_HEALTH_ALGO = 8

        private val SUPPORTED_CALLERS =
            setOf(
                "com.android.settings",
                "com.android.systemui",
                DIAGNOSTICS_TOOL_PACKAGE,
                "com.google.android.apps.pixel.support",
                "com.google.android.pixelsystemservice",
                "com.google.android.settings.intelligence",
            )
    }
}
