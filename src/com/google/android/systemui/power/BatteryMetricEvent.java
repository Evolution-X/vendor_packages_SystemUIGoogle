package com.google.android.systemui.power;

import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;

public enum BatteryMetricEvent implements UiEventLogger.UiEventEnum {
    @UiEvent(doc = "Adaptive charging notification displayed")
    ADAPTIVE_CHARGING_NOTIFICATION(1274),

    @UiEvent(doc = "Adaptive charging notification dismissed")
    DELETE_ADAPTIVE_CHARGING_NOTIFICATION(1275),

    @UiEvent(doc = "Adaptive charging notification charge normally clicked")
    ADAPTIVE_CHARGING_NOTIFICATION_BYPASS(1346),

    @UiEvent(doc = "Battery saver confirmation dialog displayed")
    SAVER_CONFIRMATION_DIALOG(1347),

    @UiEvent(doc = "Battery saver confirmation dialog turn on clicked")
    SAVER_CONFIRMATION_DIALOG_TURN_ON(1348),

    @UiEvent(doc = "Battery saver confirmation dialog cancel clicked")
    SAVER_CONFIRMATION_DIALOG_CANCEL(1349),

    @UiEvent(doc = "Battery saver confirmation dialog setup clicked")
    SAVER_CONFIRMATION_DIALOG_SETUP(1350),

    @UiEvent(doc = "Extreme low battery notification displayed")
    EXTREME_LOW_BATTERY_NOTIFICATION(1351),

    @UiEvent(doc = "Battery saver enabled")
    BATTERY_SAVER_ENABLED(1359),

    @UiEvent(doc = "Battery saver enabled reason")
    BATTERY_SAVER_ENABLED_REASON(1360),

    @UiEvent(doc = "Battery saver disabled")
    BATTERY_SAVER_DISABLED(1372),

    @UiEvent(doc = "Battery saver disabled reason")
    BATTERY_SAVER_DISABLED_REASON(1373),

    @UiEvent(doc = "Charge limit discovery notification displayed")
    SEND_CHARGE_LIMIT_DISCOVERY_NOTIFICATION(1707),

    @UiEvent(doc = "Charge limit discovery notification dismissed")
    DISMISS_CHARGE_LIMIT_DISCOVERY_NOTIFICATION(1708),

    @UiEvent(doc = "Charge limit enabled from discovery notification")
    ENABLE_CHARGE_LIMIT_FEATURE(1709),

    @UiEvent(doc = "Charge limit discovery notification clicked")
    CLICK_CHARGE_LIMIT_DISCOVERY_NOTIFICATION(1712),

    @UiEvent(doc = "Severe low battery notification turn on extreme battery saver displayed")
    SEVERE_LOW_BATTERY_NOTIFICATION_TURN_ON_EBS(1834),

    @UiEvent(doc = "Severe low battery notification switch to extreme battery saver displayed")
    SEVERE_LOW_BATTERY_NOTIFICATION_SWITCH_TO_EBS(1835),

    @UiEvent(doc = "Severe low battery notification turn on EBS positive button clicked")
    SEVERE_LOW_BATTERY_NOTIFICATION_TURN_ON_EBS_CLICK_TURN_ON(1836),

    @UiEvent(doc = "Severe low battery notification switch to EBS positive button clicked")
    SEVERE_LOW_BATTERY_NOTIFICATION_SWITCH_TO_EBS_CLICK_SWITCH(1837),

    @UiEvent(doc = "Severe low battery notification turn on EBS dismissed")
    SEVERE_LOW_BATTERY_NOTIFICATION_TURN_ON_EBS_DISMISS(1838),

    @UiEvent(doc = "Severe low battery notification switch to EBS dismissed")
    SEVERE_LOW_BATTERY_NOTIFICATION_SWITCH_TO_EBS_DISMISS(1839),

    @UiEvent(doc = "Battery health assistance enabled notification displayed")
    SEND_PULSAR_ENABLED_NOTIFICATION(2189),

    @UiEvent(doc = "Battery health assistance enabled notification dismissed")
    DISMISS_PULSAR_ENABLED_NOTIFICATION(2190),

    @UiEvent(doc = "Battery health assistance enabled notification clicked")
    CLICK_PULSAR_ENABLED_NOTIFICATION(2191),

    @UiEvent(doc = "Battery health assistance reminder notification displayed")
    SEND_PULSAR_REMINDER_NOTIFICATION(2207),

    @UiEvent(doc = "Battery health assistance reminder notification dismissed")
    DISMISS_PULSAR_REMINDER_NOTIFICATION(2208),

    @UiEvent(doc = "Battery health assistance reminder notification clicked")
    CLICK_PULSAR_REMINDER_NOTIFICATION(2209);

    private final int mId;

    BatteryMetricEvent(int id) {
        mId = id;
    }

    @Override
    public int getId() {
        return mId;
    }
}
