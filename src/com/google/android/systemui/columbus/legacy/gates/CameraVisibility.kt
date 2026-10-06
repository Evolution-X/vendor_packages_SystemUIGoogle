package com.google.android.systemui.columbus.legacy.gates

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.IActivityManager
import android.app.TaskStackListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.RemoteException
import android.util.Log
import com.android.app.tracing.coroutines.runBlockingTraced as runBlocking
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.FULLSCREEN_ACTIONS
import com.google.android.systemui.columbus.legacy.actions.Action
import com.google.android.systemui.columbus.util.Listenable
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class CameraVisibility
@Inject
constructor(
    context: Context,
    @Named(FULLSCREEN_ACTIONS) private val exceptions: List<@JvmSuppressWildcards Action>,
    private val keyguardGate: KeyguardVisibility,
    private val powerState: PowerState,
    private val activityManager: Lazy<IActivityManager>,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : Gate() {
    private val packageManager = context.packageManager
    private var cameraShowing = false
    private var exceptionActive = false

    private val gateListener = Listenable.Listener { updateCameraIsShowing() }

    private val actionListener = Listenable.Listener {
        coroutineScope.launch {
            exceptionActive = exceptions.any { it.isAvailable }
            updateBlocking()
        }
    }

    private val taskStackListener =
        object : TaskStackListener() {
            override fun onTaskStackChanged() {
                updateCameraIsShowing()
            }
        }

    override fun onActivate() {
        keyguardGate.registerListener(gateListener)
        powerState.registerListener(gateListener)
        try {
            activityManager.get().registerTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Could not register task stack listener", e)
        }
        coroutineScope.launch {
            exceptionActive = false
            exceptions.forEach {
                it.registerListener(actionListener)
                exceptionActive = exceptionActive or it.isAvailable
            }
            updateCameraIsShowing()
        }
    }

    override fun onDeactivate() {
        keyguardGate.unregisterListener(gateListener)
        powerState.unregisterListener(gateListener)
        exceptions.forEach { it.unregisterListener(actionListener) }
        try {
            activityManager.get().unregisterTaskStackListener(taskStackListener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Could not unregister task stack listener", e)
        }
    }

    private fun updateCameraIsShowing() {
        coroutineScope.launch {
            cameraShowing = isCameraShowing()
            updateBlocking()
        }
    }

    private fun updateBlocking() {
        coroutineScope.launch { setBlocking(!exceptionActive && cameraShowing) }
    }

    private suspend fun isCameraShowing(): Boolean =
        !powerState.isBlocking() && isCameraTopActivity() && isCameraInForeground()

    private suspend fun isCameraTopActivity(): Boolean =
        try {
            withContext(bgDispatcher) {
                val tasks = activityManager.get().getTasks(1)
                tasks.isNotEmpty() &&
                    tasks[0]
                        .topActivity
                        ?.packageName
                        .equals(GOOGLE_CAMERA_PACKAGE, ignoreCase = true)
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "unable to check task stack", e)
            false
        }

    private suspend fun isCameraInForeground(): Boolean =
        try {
            withContext(bgDispatcher) {
                val uid =
                    packageManager
                        .getApplicationInfoAsUser(
                            GOOGLE_CAMERA_PACKAGE,
                            0,
                            activityManager.get().currentUser.id,
                        )
                        .uid
                activityManager
                    .get()
                    .runningAppProcesses
                    .firstOrNull {
                        it.uid == uid &&
                            it.processName.equals(GOOGLE_CAMERA_PACKAGE, ignoreCase = true)
                    }
                    ?.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: RemoteException) {
            Log.e(TAG, "Could not check camera foreground status", e)
            false
        }

    override fun toString(): String =
        super.toString() +
            runBlocking(context = mainDispatcher) {
                " [cameraShowing -> $cameraShowing; exceptionActive -> $exceptionActive]"
            }

    private companion object {
        const val TAG = "Columbus/CameraVis"
        const val GOOGLE_CAMERA_PACKAGE = "com.google.android.GoogleCamera"
    }
}
