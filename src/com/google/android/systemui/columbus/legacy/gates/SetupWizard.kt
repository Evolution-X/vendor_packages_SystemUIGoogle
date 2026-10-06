package com.google.android.systemui.columbus.legacy.gates

import com.android.app.tracing.coroutines.runBlockingTraced as runBlocking
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.google.android.systemui.columbus.legacy.ColumbusModule.Companion.SETUP_WIZARD_EXCEPTIONS
import com.google.android.systemui.columbus.legacy.actions.Action
import com.google.android.systemui.columbus.util.Listenable
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@SysUISingleton
class SetupWizard
@Inject
constructor(
    @Named(SETUP_WIZARD_EXCEPTIONS) private val exceptions: Set<@JvmSuppressWildcards Action>,
    private val provisionedController: Lazy<DeviceProvisionedController>,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : Gate() {
    private var exceptionActive = false
    private var setupComplete = false

    private val provisionedListener =
        object : DeviceProvisionedController.DeviceProvisionedListener {
            override fun onDeviceProvisionedChanged() {
                updateSetupComplete()
            }

            override fun onUserSetupChanged() {
                updateSetupComplete()
            }
        }

    private val actionListener = Listenable.Listener {
        coroutineScope.launch {
            exceptionActive = exceptions.any { it.isAvailable }
            updateBlocking()
        }
    }

    override fun onActivate() {
        provisionedController.get().addCallback(provisionedListener)
        coroutineScope.launch(mainDispatcher) {
            exceptionActive = false
            exceptions.forEach {
                it.registerListener(actionListener)
                exceptionActive = exceptionActive || it.isAvailable
            }
            setupComplete = isSetupComplete()
            updateBlocking()
        }
    }

    override fun onDeactivate() {
        exceptions.forEach { it.unregisterListener(actionListener) }
        provisionedController.get().removeCallback(provisionedListener)
    }

    private fun updateSetupComplete() {
        coroutineScope.launch {
            setupComplete = isSetupComplete()
            updateBlocking()
        }
    }

    private suspend fun isSetupComplete(): Boolean {
        val isDeviceProvisioned =
            coroutineScope.async(bgDispatcher) { provisionedController.get().isDeviceProvisioned }
        val isCurrentUserSetup =
            coroutineScope.async(bgDispatcher) { provisionedController.get().isCurrentUserSetup }
        return isDeviceProvisioned.await() && isCurrentUserSetup.await()
    }

    private fun updateBlocking() {
        coroutineScope.launch { setBlocking(!exceptionActive && !setupComplete) }
    }

    override fun toString(): String =
        super.toString() +
            runBlocking(context = mainDispatcher) {
                " [setupComplete -> $setupComplete; exceptionActive -> $exceptionActive]"
            }
}
