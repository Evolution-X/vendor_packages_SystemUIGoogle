package com.google.android.systemui.columbus.legacy.sensors

import android.hardware.Sensor

/** Real-time back tap recognizer fed with accelerometer and gyroscope samples. */
class TapRT(private val classifier: TfClassifier) {
    private val accXs = ArrayDeque<Float>()
    private val accYs = ArrayDeque<Float>()
    private val accZs = ArrayDeque<Float>()
    private val gyroXs = ArrayDeque<Float>()
    private val gyroYs = ArrayDeque<Float>()
    private val gyroZs = ArrayDeque<Float>()
    private var featureVector = ArrayList<Float>()

    private val resampleAcc = Resample3C()
    private val resampleGyro = Resample3C()
    private val slopeAcc = Slope3C()
    private val slopeGyro = Slope3C()
    private val highpassAcc = Highpass3C()
    private val highpassGyro = Highpass3C()
    private val peakDetector = PeakDetector()
    private val valleyDetector = PeakDetector()
    private val timestampsBackTap = ArrayDeque<Long>()

    private var gotAcc = false
    private var gotGyro = false
    private var syncTime = 0L
    private var wasPeakApproaching = true
    private var result = TAP_CLASS_OTHERS

    init {
        setHighpassPara()
    }

    fun reset() {
        setHighpassPara()
        peakDetector.minNoiseTolerate = PEAK_MIN_NOISE_TOLERATE
        peakDetector.windowSize = PEAK_WINDOW_SIZE
        valleyDetector.minNoiseTolerate = VALLEY_MIN_NOISE_TOLERATE
        valleyDetector.windowSize = PEAK_WINDOW_SIZE
        accXs.clear()
        accYs.clear()
        accZs.clear()
        gyroXs.clear()
        gyroYs.clear()
        gyroZs.clear()
        gotAcc = false
        gotGyro = false
        syncTime = 0
        featureVector = ArrayList(List(NUMBER_FEATURE) { 0f })
    }

    fun updateData(type: Int, x: Float, y: Float, z: Float, t: Long, interval: Long) {
        result = TAP_CLASS_OTHERS
        when (type) {
            Sensor.TYPE_ACCELEROMETER -> {
                gotAcc = true
                if (syncTime == 0L) resampleAcc.init(x, y, z, t, interval)
                if (!gotGyro) return
            }
            Sensor.TYPE_GYROSCOPE -> {
                gotGyro = true
                if (syncTime == 0L) resampleGyro.init(x, y, z, t, interval)
                if (!gotAcc) return
            }
        }
        if (syncTime == 0L) {
            syncTime = t
            resampleAcc.resampledLastT = t
            resampleGyro.resampledLastT = t
            slopeAcc.init(resampleAcc.results.point)
            slopeGyro.init(resampleGyro.results.point)
            highpassAcc.init(Point3f(0f, 0f, 0f))
            highpassGyro.init(Point3f(0f, 0f, 0f))
            return
        }
        when (type) {
            Sensor.TYPE_ACCELEROMETER -> {
                while (resampleAcc.update(x, y, z, t)) {
                    processAcc()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                while (resampleGyro.update(x, y, z, t)) {
                    processGyro()
                    recognizeTapML()
                }
                if (result == TAP_CLASS_BACK) {
                    timestampsBackTap.addLast(t)
                }
            }
        }
    }

    /** Returns 0 for no tap, 1 for a single tap and 2 for a double tap. */
    fun checkDoubleTapTiming(timestamp: Long): Int {
        timestampsBackTap.removeAll { timestamp - it > MAX_TIME_GAP_NS }
        if (timestampsBackTap.isEmpty()) return 0
        if (timestampsBackTap.any { timestampsBackTap.last() - it > MIN_TIME_GAP_NS }) {
            timestampsBackTap.clear()
            return 2
        }
        return 1
    }

    private fun processAcc() {
        val update = filter(resampleAcc, slopeAcc, highpassAcc)
        accXs.addLast(update.x)
        accYs.addLast(update.y)
        accZs.addLast(update.z)
        val size = (SIZE_WINDOW_NS / resampleAcc.interval).toInt()
        while (accXs.size > size) {
            accXs.removeFirst()
            accYs.removeFirst()
            accZs.removeFirst()
        }
        val t = resampleAcc.results.t
        peakDetector.update(accZs.last(), t)
        valleyDetector.update(-accZs.last(), t)
    }

    private fun processGyro() {
        val update = filter(resampleGyro, slopeGyro, highpassGyro)
        gyroXs.addLast(update.x)
        gyroYs.addLast(update.y)
        gyroZs.addLast(update.z)
        val size = (SIZE_WINDOW_NS / resampleGyro.interval).toInt()
        while (gyroXs.size > size) {
            gyroXs.removeFirst()
            gyroYs.removeFirst()
            gyroZs.removeFirst()
        }
    }

    private fun filter(resample: Resample3C, slope: Slope3C, highpass: Highpass3C): Point3f =
        highpass.update(slope.update(resample.results.point, SLOPE_SCALE / resample.interval))

    private fun recognizeTapML() {
        val gyroOffset =
            ((resampleAcc.results.t - resampleGyro.results.t) / resampleAcc.interval).toInt()
        val peakIndex =
            if (peakDetector.amplitude > valleyDetector.amplitude) {
                maxOf(0, peakDetector.peakId)
            } else {
                maxOf(0, valleyDetector.peakId)
            }
        if (peakIndex > FRAME_ALIGN_PEAK) {
            wasPeakApproaching = true
        }
        val accStart = peakIndex - FRAME_PRIOR_PEAK
        val gyroStart = accStart - gyroOffset
        val size = accZs.size
        if (
            accStart < 0 ||
                gyroStart < 0 ||
                accStart + SIZE_FEATURE_WINDOW > size ||
                gyroStart + SIZE_FEATURE_WINDOW > size ||
                !wasPeakApproaching ||
                peakIndex > FRAME_ALIGN_PEAK
        ) {
            return
        }
        wasPeakApproaching = false
        addToFeatureVector(accXs, accStart, 0)
        addToFeatureVector(accYs, accStart, SIZE_FEATURE_WINDOW)
        addToFeatureVector(accZs, accStart, SIZE_FEATURE_WINDOW * 2)
        addToFeatureVector(gyroXs, gyroStart, SIZE_FEATURE_WINDOW * 3)
        addToFeatureVector(gyroYs, gyroStart, SIZE_FEATURE_WINDOW * 4)
        addToFeatureVector(gyroZs, gyroStart, SIZE_FEATURE_WINDOW * 5)
        for (i in featureVector.size / 2 until featureVector.size) {
            featureVector[i] = featureVector[i] * GYRO_SCALE
        }
        val prediction = classifier.predict(featureVector, NUM_CLASSES)
        if (prediction.isNotEmpty()) {
            result = prediction[0].indices.maxByOrNull { prediction[0][it] } ?: 0
        }
    }

    private fun addToFeatureVector(values: ArrayDeque<Float>, start: Int, offset: Int) {
        values.drop(start).take(SIZE_FEATURE_WINDOW).forEachIndexed { i, value ->
            featureVector[offset + i] = value
        }
    }

    private fun setHighpassPara() {
        highpassAcc.setPara(HIGHPASS_PARA)
        highpassGyro.setPara(HIGHPASS_PARA)
    }

    private companion object {
        const val TAP_CLASS_BACK = 1
        const val TAP_CLASS_OTHERS = 6
        const val NUM_CLASSES = 7

        const val SIZE_WINDOW_NS = 153_600_000L
        const val SIZE_FEATURE_WINDOW = 50
        const val NUMBER_FEATURE = SIZE_FEATURE_WINDOW * 6
        const val FRAME_ALIGN_PEAK = 12
        const val FRAME_PRIOR_PEAK = 6
        const val MIN_TIME_GAP_NS = 100_000_000L
        const val MAX_TIME_GAP_NS = 500_000_000L

        const val SLOPE_SCALE = 2_400_000f
        const val HIGHPASS_PARA = 0.05f
        const val GYRO_SCALE = 10f
        const val PEAK_MIN_NOISE_TOLERATE = 0.03f
        const val VALLEY_MIN_NOISE_TOLERATE = 0.015f
        const val PEAK_WINDOW_SIZE = 64
    }
}
