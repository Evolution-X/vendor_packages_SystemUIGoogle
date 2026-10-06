package com.google.android.systemui.columbus.legacy.gates

import com.android.app.tracing.coroutines.runBlockingTraced as runBlocking
import com.google.android.systemui.columbus.util.Listenable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class Gate : Listenable {
    protected val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    private val mainPostDispatcher: CoroutineDispatcher = Dispatchers.Main
    protected val coroutineScope = CoroutineScope(mainDispatcher)
    private val listeners = mutableSetOf<Listenable.Listener>()
    private var active = false
    private var isBlocked = false

    fun isActive(): Boolean = runBlocking(context = mainDispatcher) { active }

    fun isBlocking(): Boolean = runBlocking(context = mainDispatcher) { active && isBlocked }

    protected abstract fun onActivate()

    protected abstract fun onDeactivate()

    override fun registerListener(listener: Listenable.Listener) {
        coroutineScope.launch {
            listeners.add(listener)
            if (!active && listeners.isNotEmpty()) {
                active = true
                onActivate()
            }
        }
    }

    override fun unregisterListener(listener: Listenable.Listener) {
        coroutineScope.launch {
            listeners.remove(listener)
            if (active && listeners.isEmpty()) {
                active = false
                onDeactivate()
            }
        }
    }

    protected fun setBlocking(blocking: Boolean) {
        coroutineScope.launch {
            if (isBlocked != blocking) {
                isBlocked = blocking
                if (active) {
                    listeners.forEach { launch(mainPostDispatcher) { it.onChange(this@Gate) } }
                }
            }
        }
    }

    override fun toString(): String = javaClass.simpleName
}
