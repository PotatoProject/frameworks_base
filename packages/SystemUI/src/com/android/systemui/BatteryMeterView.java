/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.systemui;

import static android.provider.Settings.System.SHOW_BATTERY_PERCENT;
import static android.provider.Settings.Secure.STATUS_BAR_BATTERY_STYLE;

import android.animation.ArgbEvaluator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.settingslib.Utils;
import com.android.settingslib.graph.BatteryMeterDrawableBase;
import com.android.systemui.settings.CurrentUserTracker;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.statusbar.policy.IconLogger;

import java.text.NumberFormat;

public class BatteryMeterView extends LinearLayout implements
        BatteryStateChangeCallback, DarkReceiver, ConfigurationListener {
        
    // Use the high voltage symbol ⚡ (u26A1 unicode) but prevent the system
    // to load its emoji colored variant with the uFE0E flag
    private static final String bolt = "\u26A1\uFE0E";

    private BatteryMeterDrawableBase mDrawable;
    private ImageView mBatteryIconView;
    private final CurrentUserTracker mUserTracker;
    private TextView mBatteryPercentView;

    private BatteryController mBatteryController;
    private int mTextColor;
    private int mLevel;
    private boolean mForceShowPercent;

    private int mDarkModeBackgroundColor;
    private int mDarkModeFillColor;

    private int mLightModeBackgroundColor;
    private int mLightModeFillColor;
    private float mDarkIntensity;
    private int mUser;
    private boolean mCharging;

    private final Context mContext;
    private final int mFrameColor;

    private int mPercentStyle;
    private int mBatteryIconStyle;
    private boolean mAttached;

    private int mClockStyle;

    public BatteryMeterView(Context context) {
        this(context, null, 0);
    }

    public BatteryMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryMeterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;

        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);

        TypedArray atts = context.obtainStyledAttributes(attrs, R.styleable.BatteryMeterView,
                defStyle, 0);
        final int frameColor = atts.getColor(R.styleable.BatteryMeterView_frameColor,
                context.getColor(R.color.meter_background_color));
        mFrameColor = frameColor;
        mDrawable = new BatteryMeterDrawableBase(context, frameColor);
        atts.recycle();

        mBatteryIconView = new ImageView(context);
        mBatteryIconView.setImageDrawable(mDrawable);
        final MarginLayoutParams mlp = new MarginLayoutParams(
                getResources().getDimensionPixelSize(R.dimen.status_bar_battery_icon_width),
                getResources().getDimensionPixelSize(R.dimen.status_bar_battery_icon_height));
        mlp.setMargins(0, 0, 0,
                getResources().getDimensionPixelOffset(R.dimen.battery_margin_bottom));
        addView(mBatteryIconView, mlp);

        updateShowPercent();

        Context dualToneDarkTheme = new ContextThemeWrapper(context,
                Utils.getThemeAttr(context, R.attr.darkIconTheme));
        Context dualToneLightTheme = new ContextThemeWrapper(context,
                Utils.getThemeAttr(context, R.attr.lightIconTheme));
        mDarkModeBackgroundColor = Utils.getColorAttr(dualToneDarkTheme, R.attr.backgroundColor);
        mDarkModeFillColor = Utils.getColorAttr(dualToneDarkTheme, R.attr.fillColor);
        mLightModeBackgroundColor = Utils.getColorAttr(dualToneLightTheme, R.attr.backgroundColor);
        mLightModeFillColor = Utils.getColorAttr(dualToneLightTheme, R.attr.fillColor);

        // Init to not dark at all.
        onDarkChanged(new Rect(), 0, DarkIconDispatcher.DEFAULT_ICON_TINT);
        mUserTracker = new CurrentUserTracker(mContext) {
            @Override
            public void onUserSwitched(int newUserId) {
                mUser = newUserId;
            }
        };
        updateSettings(false);
    }

    public void setForceShowPercent(boolean show) {
        mForceShowPercent = show;
        updateShowPercent();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mBatteryController = Dependency.get(BatteryController.class);
        mBatteryController.addCallback(this);
        mUser = ActivityManager.getCurrentUser();
        updateBatteryStyle();
        Dependency.get(ConfigurationController.class).addCallback(this);
        mUserTracker.startTracking();
        mAttached = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mUserTracker.stopTracking();
        mBatteryController.removeCallback(this);
        Dependency.get(ConfigurationController.class).removeCallback(this);
        mAttached = false;
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        mCharging = pluggedIn;
        mDrawable.setBatteryLevel(level);
        mDrawable.setCharging(pluggedIn);
        mLevel = level;
        updatePercentText();
        setContentDescription(
                getContext().getString(charging ? R.string.accessibility_battery_level_charging
                        : R.string.accessibility_battery_level, level));
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        mDrawable.setPowerSave(isPowerSave);
    }

    private TextView loadPercentView() {
        return (TextView) LayoutInflater.from(getContext())
                .inflate(R.layout.battery_percentage_view, null);
    }

    private void updatePercentText() {
        if (mBatteryPercentView != null) {
            CharSequence mChargeIndicator =
                    mCharging && mBatteryIconStyle == BatteryMeterDrawableBase.BATTERY_STYLE_TEXT ? (bolt + " ") : "";
            mBatteryPercentView.setText(mChargeIndicator +
                    NumberFormat.getPercentInstance().format(mLevel / 100f));
        }
    }

    private void updateShowPercent() {
        final boolean showing = mBatteryPercentView != null;
        int style = mPercentStyle;
        if (mForceShowPercent) {
            style = 1; // Default view
        }
        switch (style) {
            case 1:
                if (!showing) {
                    mBatteryPercentView = loadPercentView();
                    if (mTextColor != 0) mBatteryPercentView.setTextColor(mTextColor);
                    updatePercentText();
                    addView(mBatteryPercentView,
                            0,
                            new ViewGroup.LayoutParams(
                                    LayoutParams.WRAP_CONTENT,
                                    LayoutParams.MATCH_PARENT));
                }
                mDrawable.setShowPercent(false);
                break;
            case 2:
                if (showing) {
                    removeView(mBatteryPercentView);
                    mBatteryPercentView = null;
                }
                mDrawable.setShowPercent(true);
                break;
            default:
                if (showing) {
                    removeView(mBatteryPercentView);
                    mBatteryPercentView = null;
                }
                mDrawable.setShowPercent(false);
                break;
        }
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        scaleBatteryMeterViews();
    }

    /**
     * Looks up the scale factor for status bar icons and scales the battery view by that amount.
     */
    private void scaleBatteryMeterViews() {
        Resources res = getContext().getResources();
        TypedValue typedValue = new TypedValue();

        res.getValue(R.dimen.status_bar_icon_scale_factor, typedValue, true);
        float iconScaleFactor = typedValue.getFloat();

        int batteryHeight = res.getDimensionPixelSize(R.dimen.status_bar_battery_icon_height);
        int batteryWidth = res.getDimensionPixelSize(R.dimen.status_bar_battery_icon_width);
        int marginBottom = res.getDimensionPixelSize(R.dimen.battery_margin_bottom);
        int endPadding = res.getDimensionPixelSize(R.dimen.battery_level_padding_start);
        boolean addPadding = (mClockStyle == 0 &&
                mDrawable.getMeterStyle() >= BatteryMeterDrawableBase.BATTERY_STYLE_TEXT);

        LinearLayout.LayoutParams scaledLayoutParams = new LinearLayout.LayoutParams(
                (int) (batteryWidth * iconScaleFactor), (int) (batteryHeight * iconScaleFactor));
        scaledLayoutParams.setMargins(0, 0, (addPadding ? endPadding : 0), marginBottom);

        mBatteryIconView.setLayoutParams(scaledLayoutParams);
        FontSizeUtils.updateFontSize(mBatteryPercentView, R.dimen.qs_time_expanded_size);

        if (mBatteryPercentView != null) {
            mBatteryPercentView.setPaddingRelative(0, 0, (mClockStyle == 0 ? endPadding : 0), 0);
        }
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        mDarkIntensity = darkIntensity;
        float intensity = DarkIconDispatcher.isInArea(area, this) ? darkIntensity : 0;
        int foreground = getColorForDarkIntensity(intensity, mLightModeFillColor,
                mDarkModeFillColor);
        int background = getColorForDarkIntensity(intensity, mLightModeBackgroundColor,
                mDarkModeBackgroundColor);
        mDrawable.setColors(foreground, background);
        setTextColor(foreground);
    }

    public void setTextColor(int color) {
        mTextColor = color;
        if (mBatteryPercentView != null) {
            mBatteryPercentView.setTextColor(color);
        }
    }

    public void setFillColor(int color) {
        if (mLightModeFillColor == color) {
            return;
        }
        mLightModeFillColor = color;
        onDarkChanged(new Rect(), mDarkIntensity, DarkIconDispatcher.DEFAULT_ICON_TINT);
    }

    private int getColorForDarkIntensity(float darkIntensity, int lightColor, int darkColor) {
        return (int) ArgbEvaluator.getInstance().evaluate(darkIntensity, lightColor, darkColor);
    }

    private void updateBatteryStyle() {
        mForceShowPercent = false;

        switch (mBatteryIconStyle) {
            case BatteryMeterDrawableBase.BATTERY_STYLE_TEXT:
                if (mBatteryIconView != null) {
                    mBatteryIconView.setVisibility(View.GONE);
                }
                mForceShowPercent = true;
                break;
            case BatteryMeterDrawableBase.BATTERY_STYLE_HIDDEN:
                if (mBatteryIconView != null) {
                    mBatteryIconView.setVisibility(View.GONE);
                }
                break;
            default:
                mDrawable.setMeterStyle(mBatteryIconStyle);
                if (mBatteryIconView != null) {
                    mBatteryIconView.setVisibility(View.VISIBLE);
                }
                break;
        }


        updateShowPercent();
    }

    public void updateSettings(boolean force) {
        mPercentStyle = Settings.System.getIntForUser(getContext().getContentResolver(),
                SHOW_BATTERY_PERCENT, 0, mUser);
        mBatteryIconStyle = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                STATUS_BAR_BATTERY_STYLE, BatteryMeterDrawableBase.BATTERY_STYLE_PORTRAIT, mUser);
        mClockStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUSBAR_CLOCK_STYLE, 0 /* STYLE_CLOCK_RIGHT */, mUser);
        if (force && mAttached) {
            updateBatteryStyle();
        }
        scaleBatteryMeterViews();
    }
}
