/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.systemui.gesture;

import android.provider.DeviceConfig;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.navigationbar.gestural.BackGestureTfClassifierProvider;
import com.android.systemui.util.DeviceConfigProxy;

import dagger.Module;
import dagger.Provides;

import javax.inject.Named;

/**
 * Dagger module for Google-specific gesture navigation dependencies.
 */
@Module
public interface GestureModuleGoogle {
    String BACK_GESTURE_ML_MODEL_NAME = "back_gesture_ml_model_name";

    /**
     * Provides the machine learning model name for back gesture evaluation.
     */
    @Provides
    @Named(BACK_GESTURE_ML_MODEL_NAME)
    static String provideBackGestureMlModelName(DeviceConfigProxy proxy) {
        String modelName = proxy.getString(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                BACK_GESTURE_ML_MODEL_NAME,
                "backgesture"
        );
        return modelName != null ? modelName : "backgesture";
    }

    /**
     * Provides the TensorFlow Lite back gesture classifier provider instance.
     */
    @Provides
    @SysUISingleton
    static BackGestureTfClassifierProvider provideBackGestureTfClassifierProvider(
            @Named(BACK_GESTURE_ML_MODEL_NAME) String modelName) {
        return new BackGestureTfClassifierProviderGoogle(modelName);
    }
}
