package com.google.android.systemui.power.batteryhealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class HealthUpdateReceiver @Inject constructor(private val healthManager: HealthManager) :
    BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Start new BHI update")
        MainScope().launch { healthManager.getAndUpdateHealthData() }
    }

    companion object {
        private const val TAG = "HealthUpdateReceiver"
    }
}
