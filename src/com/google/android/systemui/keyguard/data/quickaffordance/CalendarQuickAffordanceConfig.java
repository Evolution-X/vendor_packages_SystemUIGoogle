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
 * Lock-screen quick affordance that opens the device's default calendar app.
 *
 * <p>Self-contained: it relies only on the public {@link Intent#CATEGORY_APP_CALENDAR}
 * application category resolved through {@link Intent#makeMainSelectorActivity}, so it works on any
 * build that ships a calendar, with no private hardware or Pixel-exclusive backend. AOSP does not
 * ship this affordance (see {@code BuiltInKeyguardQuickAffordanceKeys}).
 *
 * <p>See {@link CalculatorQuickAffordanceConfig} for notes on the Java-over-Kotlin-interface mapping.
 */
@SysUISingleton
public final class CalendarQuickAffordanceConfig implements KeyguardQuickAffordanceConfig {

    /** Globally-unique key persisted in the user's affordance selection. */
    public static final String KEY = "calendar";

    private final Context mContext;

    private final Intent mLaunchIntent =
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR);

    @Inject
    public CalendarQuickAffordanceConfig(@ShadeDisplayAware Context context) {
        mContext = context;
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public int getPickerIconResourceId() {
        return R.drawable.ic_calendar_lockscreen;
    }

    @Override
    public String pickerName() {
        return mContext.getString(R.string.calendar_quick_affordance_label);
    }

    @Override
    public Flow getLockScreenState() {
        final LockScreenState state;
        if (isTargetAvailable()) {
            state = new LockScreenState.Visible(
                    new Icon.Resource(
                            R.drawable.ic_calendar_lockscreen,
                            new ContentDescription.Resource(
                                    R.string.calendar_quick_affordance_label)),
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
