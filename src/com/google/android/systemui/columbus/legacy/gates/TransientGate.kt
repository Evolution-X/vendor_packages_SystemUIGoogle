package com.google.android.systemui.columbus.legacy.gates

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class TransientGate : Gate() {
    private var currentJob: Job? = null

    protected fun blockForMillis(blockDuration: Long) {
        currentJob?.cancel()
        currentJob = coroutineScope.launch {
            setBlocking(true)
            delay(blockDuration)
            setBlocking(false)
            currentJob = null
        }
    }
}
