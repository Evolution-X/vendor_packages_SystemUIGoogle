package com.google.android.systemui.power.batteryhealth

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log
import com.google.android.systemui.power.ChargeLimitController
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class HealthService
@Inject
constructor(private val context: Context, private val chargeLimitController: ChargeLimitController) :
    Service() {

    private val binder =
        object : Binder(), IInterface {
            init {
                attachInterface(this, DESCRIPTOR)
            }

            override fun asBinder(): IBinder = this

            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == TRANSACTION_SET_CHARGING_POLICY) {
                    data.enforceInterface(DESCRIPTOR)
                    val policy = data.readInt()
                    data.enforceNoDataAvail()
                    val callers = ensureSupportedCallers()
                    Log.i(TAG, "setChargingPolicy with policy $policy: ${callers.contentToString()}")
                    val result = runBlocking { chargeLimitController.applyChargingPolicy(policy) }
                    reply?.writeNoException()
                    reply?.writeBoolean(result)
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "HealthService bound")
        return binder
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
        private const val DESCRIPTOR = "com.google.android.systemui.power.batteryhealth.IHealthService"
        private const val TRANSACTION_SET_CHARGING_POLICY = IBinder.FIRST_CALL_TRANSACTION + 5

        private val SUPPORTED_CALLERS =
            setOf(
                "com.android.settings",
                "com.android.systemui",
                "com.google.android.apps.diagnosticstool",
                "com.google.android.apps.pixel.support",
                "com.google.android.pixelsystemservice",
                "com.google.android.settings.intelligence",
            )
    }
}
