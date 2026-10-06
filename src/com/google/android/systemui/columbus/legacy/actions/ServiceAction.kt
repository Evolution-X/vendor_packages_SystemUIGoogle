package com.google.android.systemui.columbus.legacy.actions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.google.android.systemui.columbus.ColumbusServiceProxy
import com.google.android.systemui.columbus.IColumbusService
import com.google.android.systemui.columbus.IColumbusServiceGestureListener
import com.google.android.systemui.columbus.IColumbusServiceListener
import com.google.android.systemui.columbus.legacy.sensors.GestureSensor
import java.util.concurrent.Executor

abstract class ServiceAction(context: Context, executor: Executor?) :
    Action(context, executor, null), IBinder.DeathRecipient {
    protected abstract val supportedCallerPackages: Set<String>

    private var columbusService: IColumbusService? = null
    private var columbusServiceGestureListener: IColumbusServiceGestureListener? = null
    private val token: IBinder = Binder()

    private val columbusServiceListener =
        object : IColumbusServiceListener.Stub() {
            override fun setListener(token: IBinder?, listener: IBinder?) {
                val callerPackages =
                    context.packageManager.getPackagesForUid(Binder.getCallingUid()) ?: return
                if (supportedCallerPackages.none { it in callerPackages }) return
                if (listener == null && columbusServiceGestureListener == null) return

                columbusServiceGestureListener = listener?.let {
                    IColumbusServiceGestureListener.Stub.asInterface(it)
                }
                updateAvailable()
                if (token == null) return
                try {
                    if (listener == null) {
                        token.unlinkToDeath(this@ServiceAction, 0)
                    } else {
                        token.linkToDeath(this@ServiceAction, 0)
                    }
                } catch (e: RemoteException) {
                    Log.e(TAG, "RemoteException during linkToDeath", e)
                } catch (e: NoSuchElementException) {
                    Log.e(TAG, "NoSuchElementException during linkToDeath", e)
                }
            }
        }

    private val columbusServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder?) {
                columbusService = IColumbusService.Stub.asInterface(service)
                try {
                    columbusService?.registerServiceListener(token, columbusServiceListener)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Error registering listener", e)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                columbusService = null
            }
        }

    init {
        try {
            val intent =
                Intent().setComponent(ComponentName(context, ColumbusServiceProxy::class.java))
            context.bindService(intent, columbusServiceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to bind to ColumbusServiceProxy", e)
        }
        updateAvailable()
    }

    override fun onTrigger(detectionProperties: GestureSensor.DetectionProperties?) {
        try {
            columbusServiceGestureListener?.onTrigger()
        } catch (e: DeadObjectException) {
            Log.e(TAG, "Listener crashed or closed without unregistering", e)
            columbusServiceGestureListener = null
            updateAvailable()
        } catch (e: RemoteException) {
            Log.e(TAG, "Unable to send trigger, setting listener to null", e)
            columbusServiceGestureListener = null
            updateAvailable()
        }
    }

    override fun binderDied() {
        Log.w(TAG, "Binder died")
        columbusServiceGestureListener = null
        updateAvailable()
    }

    private fun updateAvailable() {
        setAvailable(columbusServiceGestureListener != null)
    }

    private companion object {
        const val TAG = "Columbus/ServiceAction"
    }
}
