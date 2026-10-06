package com.google.android.systemui.power.batteryhealth

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.google.android.systemui.googlebattery.GoogleBatteryManager
import com.google.android.systemui.res.R
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vendor.google.google_battery.IGoogleBattery

@SysUISingleton
class HealthManager
@Inject
constructor(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val broadcastDispatcher: BroadcastDispatcher,
    @Background private val bgDispatcher: CoroutineDispatcher,
    @Application private val mainScope: CoroutineScope,
) {
    private val periodicUpdateEnabled =
        context.resources.getBoolean(R.bool.config_battery_health_periodic_update_enabled)
    private var googleBattery: IGoogleBattery? = null
    private val deathRecipient = IBinder.DeathRecipient { Log.w(TAG, "HW binder died") }
    private val initializer: Job = mainScope.launch {
        withContext(bgDispatcher) { initHalInterface() }
    }

    private val bootCompletedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "onReceive: $intent")
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    broadcastDispatcher.unregisterReceiver(this)
                    mainScope.launch {
                        withContext(bgDispatcher) { updateHealthDataPeriodically() }
                    }
                }
            }
        }

    private val healthDebugReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "onReceive: $intent")
                if (healthDebugEnabled && intent.action == ACTION_BATTERY_HEALTH_DEBUG) {
                    mainScope.launch { getAndUpdateHealthData() }
                }
            }
        }

    fun registerBootCompletedReceiver() {
        if (periodicUpdateEnabled) {
            Log.i(TAG, "Enable BHI")
            broadcastDispatcher.registerReceiver(
                bootCompletedReceiver,
                IntentFilter(Intent.ACTION_BOOT_COMPLETED),
            )
        }
    }

    fun registerHealthDebugReceiver() {
        if (healthDebugEnabled) {
            Log.d(TAG, "register healthDebugReceiver")
            broadcastDispatcher.registerReceiver(
                healthDebugReceiver,
                IntentFilter(ACTION_BATTERY_HEALTH_DEBUG),
            )
        }
    }

    fun unregisterHealthDebugReceiver() {
        if (healthDebugEnabled) {
            Log.d(TAG, "unregister healthDebugReceiver")
            broadcastDispatcher.unregisterReceiver(healthDebugReceiver)
        }
    }

    private fun updateHealthDataPeriodically() {
        Log.i(TAG, "Start BHI periodic update")
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime(),
            updatePeriod.toMillis(),
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, HealthUpdateReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    private fun initHalInterface() {
        Log.d(TAG, "initHalInterface")
        googleBattery = GoogleBatteryManager.initHalInterface(deathRecipient)
    }

    private suspend fun getHealthIndex(): Int? {
        initializer.join()
        return try {
            googleBattery?.healthIndex.also { Log.i(TAG, "getHealthIdx: $it") }
        } catch (e: Exception) {
            Log.w(TAG, "getHealthIdx: $e")
            null
        }
    }

    private suspend fun getHealthImpedanceIndex(): Int? {
        initializer.join()
        return try {
            googleBattery?.healthImpedanceIndex.also { Log.i(TAG, "getImpedanceIdx: $it") }
        } catch (e: Exception) {
            Log.w(TAG, "getImpedanceIdx: $e")
            null
        }
    }

    private suspend fun getHealthCapacityIndex(): Int? {
        initializer.join()
        return try {
            googleBattery?.healthCapacityIndex.also { Log.i(TAG, "getCapacityIdx: $it") }
        } catch (e: Exception) {
            Log.w(TAG, "getCapacityIdx: $e")
            null
        }
    }

    private suspend fun getHealthStatus(): Int? {
        initializer.join()
        return try {
            googleBattery?.healthStatus.also { Log.i(TAG, "getHealthStatus: $it") }
        } catch (e: Exception) {
            Log.w(TAG, "getHealthStatus: $e")
            null
        }
    }

    suspend fun getAndUpdateHealthData(): HealthData =
        withContext(bgDispatcher) {
            val healthPrefs = getHealthPrefs()
            val health = getHealthIndex()
            val performance = getHealthImpedanceIndex()
            val capacity = getHealthCapacityIndex()
            val status = getHealthStatus()
            saveAsHealthData(healthPrefs, health, performance, capacity, status)
        }

    suspend fun getHealthData(healthAlgo: Int): HealthData =
        withContext(bgDispatcher) {
            initializer.join()
            try {
                val healthStats = googleBattery?.getHealthStats(healthAlgo)
                HealthData(
                        healthStats?.healthIndex ?: -1,
                        healthStats?.healthImpedanceIndex ?: -1,
                        healthStats?.healthCapacityIndex ?: -1,
                        healthStats?.healthStatus ?: -1,
                    )
                    .also { Log.i(TAG, "getHealthData: $it, algo: $healthAlgo") }
            } catch (e: Exception) {
                Log.w(TAG, "getHealthData: $e, algo: $healthAlgo")
                HealthData(-1, -1, -1, -1)
            }
        }

    suspend fun setChargingPolicy(policy: Int): Boolean {
        initializer.join()
        return try {
            googleBattery?.setChargingPolicy(policy)
            Log.i(TAG, "setChargingPolicy: $policy")
            true
        } catch (e: Exception) {
            Log.w(TAG, "setChargingPolicy: $e")
            false
        }
    }

    fun getHealthDataFlow(): Flow<Pair<SharedPreferences, String?>> =
        callbackFlow {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (prefs != null) {
                        trySend(prefs to key)
                    }
                }
                getHealthPrefs().registerOnSharedPreferenceChangeListener(listener)
                awaitClose { getHealthPrefs().unregisterOnSharedPreferenceChangeListener(listener) }
            }
            .flowOn(bgDispatcher)

    private fun saveAsHealthData(
        healthPrefs: SharedPreferences,
        health: Int?,
        performance: Int?,
        capacity: Int?,
        status: Int?,
    ): HealthData {
        Log.i(TAG, "Got BHI, hi:$health, pi:$performance, ci:$capacity, hs:$status")
        healthPrefs.edit().apply {
            health?.let { putInt(KEY_HEALTH_INDEX, it) }
            performance?.let { putInt(KEY_PERF_INDEX, it) }
            capacity?.let { putInt(KEY_CAPACITY_INDEX, it) }
            status?.let { putInt(KEY_HEALTH_STATUS, it) }
            apply()
        }
        return getHealthDataFromPrefs(healthPrefs)
    }

    private fun getHealthDataFromPrefs(healthPrefs: SharedPreferences): HealthData =
        HealthData(
                healthPrefs.getInt(KEY_HEALTH_INDEX, -1),
                healthPrefs.getInt(KEY_PERF_INDEX, -1),
                healthPrefs.getInt(KEY_CAPACITY_INDEX, -1),
                healthPrefs.getInt(KEY_HEALTH_STATUS, -1),
            )
            .also { Log.i(TAG, "Get BHI from prefs: $it") }

    private fun getHealthPrefs(): SharedPreferences =
        context.applicationContext.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "HealthManager"
        private const val ACTION_BATTERY_HEALTH_DEBUG =
            "com.google.android.systemui.BATTERY_HEALTH_DEBUG"
        private val healthDebugEnabled = Build.IS_DEBUGGABLE
        private val updatePeriod = Duration.ofDays(1)

        const val KEY_HEALTH_INDEX = "health_index"
        const val KEY_PERF_INDEX = "perf_index"
        const val KEY_CAPACITY_INDEX = "capacity_index"
        const val KEY_HEALTH_STATUS = "health_status"
    }
}
