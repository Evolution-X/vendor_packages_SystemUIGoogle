package com.google.android.systemui.columbus.util

interface Listenable {
    fun interface Listener {
        fun onChange(listenable: Listenable)
    }

    fun registerListener(listener: Listener)

    fun unregisterListener(listener: Listener)
}
