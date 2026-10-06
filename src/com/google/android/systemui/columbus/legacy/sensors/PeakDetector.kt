package com.google.android.systemui.columbus.legacy.sensors

class PeakDetector {
    var minNoiseTolerate = 0f
    var windowSize = 0
    var peakId = -1
        private set

    var amplitude = 0f
        private set

    private var noiseTolerate = 0f
    private var timestamp = 0L
    private var amplitudeReference = 0f

    fun update(value: Float, t: Long) {
        peakId--
        if (peakId < 0) {
            amplitude = 0f
            amplitudeReference = 0f
            timestamp = 0
            peakId = 0
        }
        noiseTolerate = maxOf(minNoiseTolerate, maxOf(amplitude, value) / 5f)
        val drop = amplitudeReference - value
        if (drop >= 0f) {
            if (drop > noiseTolerate) {
                amplitudeReference = value
            }
            return
        }
        amplitudeReference = value
        if (
            (timestamp == 0L || (t - timestamp < MAX_TAP_DURATION_NS && amplitude < value)) &&
                value >= noiseTolerate
        ) {
            peakId = windowSize - 1
            amplitude = value
            timestamp = t
        }
    }

    private companion object {
        const val MAX_TAP_DURATION_NS = 120_000_000L
    }
}
