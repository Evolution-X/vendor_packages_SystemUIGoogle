/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.google.android.systemui.keyguard.data.quickaffordance;

import android.content.Context;
import android.content.Intent;

import com.android.systemui.animation.Expandable;
import com.android.systemui.common.shared.model.ContentDescription;
import com.android.systemui.common.shared.model.Icon;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.keyguard.data.quickaffordance.KeyguardQuickAffordanceConfig;
import com.android.systemui.keyguard.shared.quickaffordance.ActivationState;
import com.android.systemui.shade.ShadeDisplayAware;
import com.google.android.systemui.res.R;

import javax.inject.Inject;

import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;

/**
 * Lock-screen quick affordance that opens the device's default calculator app.
 *
 * <p>Self-contained: it relies only on the public {@link Intent#CATEGORY_APP_CALCULATOR}
 * application category resolved through {@link Intent#makeMainSelectorActivity}, so it works on any
 * build that ships a calculator, with no private hardware or Pixel-exclusive backend. AOSP does not
 * ship this affordance (see {@code BuiltInKeyguardQuickAffordanceKeys}).
 *
 * <p>Implemented in Java against the Kotlin {@code KeyguardQuickAffordanceConfig} interface, mirroring
 * the shape of the stock (decompiled) {@code ThermometerQuickAffordanceConfig}: property getters map
 * to {@code getX()}, the {@code suspend} picker method maps to a {@code Continuation}-taking method
 * that returns the value directly, and the {@code lockScreenState} flow is a constant flow.
 */
@SysUISingleton
public final class CalculatorQuickAffordanceConfig implements KeyguardQuickAffordanceConfig {

    /** Globally-unique key persisted in the user's affordance selection. */
    public static final String KEY = "calculator";

    private final Context mContext;

    // ACTION_MAIN intent carrying a {action=MAIN, category=APP_CALCULATOR} selector: the documented
    // way to launch the calculator without hard-coding a package name.
    private final Intent mLaunchIntent =
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR);

    @Inject
    public CalculatorQuickAffordanceConfig(@ShadeDisplayAware Context context) {
        mContext = context;
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public int getPickerIconResourceId() {
        return R.drawable.ic_calculator_lockscreen;
    }

    @Override
    public String pickerName() {
        return mContext.getString(R.string.calculator_quick_affordance_label);
    }

    @Override
    public Flow getLockScreenState() {
        final LockScreenState state;
        if (isTargetAvailable()) {
            state = new LockScreenState.Visible(
                    new Icon.Resource(
                            R.drawable.ic_calculator_lockscreen,
                            new ContentDescription.Resource(
                                    R.string.calculator_quick_affordance_label)),
                    ActivationState.NotSupported.INSTANCE);
        } else {
            state = LockScreenState.Hidden.INSTANCE;
        }
        return FlowKt.flowOf(state);
    }

    @Override
    public Object getPickerScreenState(Continuation continuation) {
        if (isTargetAvailable()) {
            return new PickerScreenState.Default(null);
        }
        return PickerScreenState.UnavailableOnDevice.INSTANCE;
    }

    @Override
    public OnTriggeredResult onTriggered(Expandable expandable) {
        return new OnTriggeredResult.StartActivity(mLaunchIntent, /* canShowWhileLocked= */ false);
    }

    private boolean isTargetAvailable() {
        return mContext.getPackageManager().resolveActivity(mLaunchIntent, /* flags= */ 0) != null;
    }
}
