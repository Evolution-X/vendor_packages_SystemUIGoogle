package com.google.android.systemui.columbus

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log

class ColumbusServiceProxy : Service() {
    private val columbusServiceListeners = mutableListOf<ColumbusServiceListener>()

    private val binder =
        object : IColumbusService.Stub() {
            override fun registerGestureListener(token: IBinder?, listener: IBinder?) {
                enforceConfigurePermission()
                for (i in columbusServiceListeners.indices.reversed()) {
                    val serviceListener = columbusServiceListeners[i].listener
                    if (serviceListener == null) {
                        columbusServiceListeners.removeAt(i)
                        continue
                    }
                    try {
                        serviceListener.setListener(token, listener)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Cannot set listener", e)
                        columbusServiceListeners.removeAt(i)
                    }
                }
            }

            override fun registerServiceListener(token: IBinder?, listener: IBinder?) {
                enforceConfigurePermission()
                if (token == null) {
                    Log.e(TAG, "Binder token must not be null")
                    return
                }
                if (listener == null) {
                    columbusServiceListeners.removeAll {
                        if (it.token == token) {
                            it.token?.unlinkToDeath(it, 0)
                            true
                        } else {
                            false
                        }
                    }
                    return
                }
                val serviceListener =
                    ColumbusServiceListener(
                        token,
                        IColumbusServiceListener.Stub.asInterface(listener),
                    )
                try {
                    token.linkToDeath(serviceListener, 0)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Unable to linkToDeath", e)
                }
                columbusServiceListeners.add(serviceListener)
            }
        }

    private fun enforceConfigurePermission() {
        enforceCallingOrSelfPermission(
            PERMISSION_CONFIGURE_COLUMBUS_GESTURE,
            "Must have $PERMISSION_CONFIGURE_COLUMBUS_GESTURE permission",
        )
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        START_STICKY_COMPATIBILITY

    private class ColumbusServiceListener(
        var token: IBinder?,
        var listener: IColumbusServiceListener?,
    ) : IBinder.DeathRecipient {
        override fun binderDied() {
            Log.w(TAG, "ColumbusServiceListener binder died")
            token = null
            listener = null
        }
    }

    private companion object {
        const val TAG = "Columbus/ColumbusProxy"
        const val PERMISSION_CONFIGURE_COLUMBUS_GESTURE =
            "com.google.android.columbus.permission.CONFIGURE_COLUMBUS_GESTURE"
    }
}
