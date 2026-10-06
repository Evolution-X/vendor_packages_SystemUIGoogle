package com.google.android.systemui.columbus.legacy.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.util.Log
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.google.android.systemui.columbus.ColumbusEvent
import javax.inject.Inject

/** Detects back taps on the application processor from the accelerometer and gyroscope. */
@SysUISingleton
class GestureSensorImpl
@Inject
constructor(
    context: Context,
    private val uiEventLogger: UiEventLogger,
    @Main private val handler: Handler,
) : GestureSensor() {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val tap: TapRT
    private var isListening = false

    private val sensorEventListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val values = event.values
                tap.updateData(
                    event.sensor.type,
                    values[0],
                    values[1],
                    values[2],
                    event.timestamp,
                    SAMPLING_INTERVAL_NS,
                )
                when (tap.checkDoubleTapTiming(event.timestamp)) {
                    1 -> handler.post { listener?.onGestureDetected(2, DetectionProperties(true)) }
                    2 -> handler.post { listener?.onGestureDetected(1, DetectionProperties(false)) }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

    init {
        // Stock ships none of these models, so the classifier never loads on Pixel 7 either.
        val modelFileName =
            when (Build.MODEL) {
                "Pixel 4a (5G)" -> "tap7cls_bramble.tflite"
                "Pixel 5" -> "tap7cls_redfin.tflite"
                "Pixel 3 XL" -> "tap7cls_crosshatch.tflite"
                "Pixel 4 XL" -> "tap7cls_coral.tflite"
                else -> "tap7cls_flame.tflite"
            }
        Log.d(TAG, "TapRT loaded $modelFileName")
        tap = TapRT(TfClassifier(context.assets, modelFileName))
    }

    override fun isListening() = isListening

    override fun startListening() {
        setListening(true)
        tap.reset()
        uiEventLogger.log(ColumbusEvent.COLUMBUS_MODE_HIGH_POWER_ACTIVE)
    }

    override fun stopListening() {
        setListening(false)
        uiEventLogger.log(ColumbusEvent.COLUMBUS_MODE_INACTIVE)
    }

    private fun setListening(listening: Boolean) {
        if (listening && accelerometer != null && gyroscope != null) {
            sensorManager.registerListener(
                sensorEventListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_FASTEST,
                handler,
            )
            sensorManager.registerListener(
                sensorEventListener,
                gyroscope,
                SensorManager.SENSOR_DELAY_FASTEST,
                handler,
            )
            isListening = true
        } else {
            sensorManager.unregisterListener(sensorEventListener)
            isListening = false
        }
    }

    private companion object {
        const val TAG = "Columbus"
        const val SAMPLING_INTERVAL_NS = 2_400_000L
    }
}
