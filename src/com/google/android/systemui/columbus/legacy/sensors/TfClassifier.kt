package com.google.android.systemui.columbus.legacy.sensors

import android.content.res.AssetManager
import android.util.Log
import java.io.FileInputStream
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

class TfClassifier(assetManager: AssetManager, assetFileName: String) {
    private val interpreter: Interpreter? =
        try {
            assetManager
                .openFd(assetFileName)
                .use { fd ->
                    FileInputStream(fd.fileDescriptor).channel.use { channel ->
                        Interpreter(
                            channel.map(
                                FileChannel.MapMode.READ_ONLY,
                                fd.startOffset,
                                fd.declaredLength,
                            )
                        )
                    }
                }
                .also { Log.d(TAG, "tflite file loaded: $assetFileName") }
        } catch (e: Exception) {
            Log.d(TAG, "load tflite file error: $assetFileName")
            Log.e(TAG, "tflite file:$e")
            null
        }

    /** Returns the class scores for [input], or an empty list if no model is loaded. */
    fun predict(input: List<Float>, numClasses: Int): List<List<Float>> {
        val interpreter = interpreter ?: return emptyList()
        val inputs = Array(1) { Array(input.size) { Array(1) { FloatArray(1) } } }
        input.forEachIndexed { i, value -> inputs[0][i][0][0] = value }
        val output = Array(1) { FloatArray(numClasses) }
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(inputs), mapOf<Int, Any>(0 to output))
        return listOf(output[0].toList())
    }

    private companion object {
        const val TAG = "Columbus"
    }
}
