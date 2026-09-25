package com.google.android.systemui.gesture;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Trace;
import android.util.Log;

import com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * TensorFlow Lite-based classifier provider for back gesture evaluation.
 */
public class BackGestureTfClassifierProviderGoogle extends BackGestureTfClassifierProvider {
    private static final String TAG = "BackGestureTfClassifier";
    public static final Object sModelLoadingLock = new Object();

    public Interpreter mInterpreter;
    public String mModelFile;
    public boolean mModelLoaded;
    public float[][] mOutput;
    public Map<Integer, Object> mOutputMap;
    public Map<String, Integer> mVocab;
    public String mVocabFile;

    public BackGestureTfClassifierProviderGoogle() {
        this("backgesture");
    }

    public BackGestureTfClassifierProviderGoogle(String modelName) {
        mInterpreter = null;
        mModelLoaded = false;
        mOutputMap = new HashMap<>();
        mOutput = (float[][]) Array.newInstance(Float.TYPE, 1, 1);
        mOutputMap.put(0, mOutput);
        mModelFile = modelName + ".tflite";
        mVocabFile = modelName + ".vocab";
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Map<String, Integer> loadVocab(AssetManager assetManager) {
        synchronized (sModelLoadingLock) {
            if (!mModelLoaded) {
                loadModel(assetManager);
            }
            if (mVocab == null) {
                mVocab = readVocab(assetManager);
            }
            return mVocab;
        }
    }

    @Override
    public float predict(Object[] featuresVector) {
        if (!mModelLoaded || mInterpreter == null) {
            Log.e(TAG, "cannot predict; model not loaded");
            return -1.0f;
        }
        mInterpreter.runForMultipleInputsOutputs(featuresVector, mOutputMap);
        return mOutput[0][0];
    }

    @Override
    public void release() {
        mVocab = null;
        mModelLoaded = false;
        if (mInterpreter != null) {
            try {
                mInterpreter.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing interpreter: ", e);
            }
            mInterpreter = null;
        }
    }

    public void loadModel(AssetManager assetManager) {
        try {
            Trace.beginSection("BackGestureTfClassifierProviderGoogle#modelLoading");
            try (AssetFileDescriptor afd = assetManager.openFd(mModelFile)) {
                mInterpreter = new Interpreter(
                        afd.createInputStream().getChannel().map(
                                FileChannel.MapMode.READ_ONLY,
                                afd.getStartOffset(),
                                afd.getDeclaredLength()
                        )
                );
                mModelLoaded = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Load TFLite file error:", e);
            mModelLoaded = false;
        } finally {
            Trace.endSection();
        }
    }

    public Map<String, Integer> readVocab(AssetManager assetManager) {
        Map<String, Integer> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(mVocabFile)))) {
            String line;
            int idx = 0;
            while ((line = reader.readLine()) != null) {
                map.put(line, idx++);
            }
        } catch (Exception e) {
            Log.e(TAG, "Load vocab file error: ", e);
        }
        return map;
    }
}
