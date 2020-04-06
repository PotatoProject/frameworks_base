/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.res.ColorUtils;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.android.systemui.AprilEasterBroadcast;
import com.android.systemui.R;
import com.android.systemui.qs.customize.QSCustomizer;

import java.util.Calendar;
import java.util.Date;
import java.lang.System;

/**
 * Wrapper view with background which contains {@link QSPanel} and {@link BaseStatusBarHeader}
 */
public class QSContainerImpl extends FrameLayout {

    private static final String TAG = "QSContainerImpl";

    private final Point mSizePoint = new Point();

    private int mHeightOverride = -1;
    private QSPanel mQSPanel;
    private View mQSDetail;
    private QuickStatusBarHeader mHeader;
    private float mQsExpansion;
    private QSCustomizer mQSCustomizer;
    private View mQSFooter;

    private View mBackground;
    private View mBackgroundGradient;
    private View mStatusBarBackground;

    private int mSideMargins;
    private boolean mQsDisabled;

    private Drawable mQsBackGround;
    private int mQsBackGroundAlpha;
    private int mQsBackGroundColor;
    private int mQsBackGroundColorWall;
    private boolean mSetQsFromWall;
    private boolean mSetQsFromResources;

    private IOverlayManager mOverlayManager;

    private ValueAnimator mDiscoAnim;

    public QSContainerImpl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
        Handler handler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(handler);
        settingsObserver.observe();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setAprilEasterAlarm();
        mQSPanel = findViewById(R.id.quick_settings_panel);
        mQSDetail = findViewById(R.id.qs_detail);
        mHeader = findViewById(R.id.header);
        mQSCustomizer = findViewById(R.id.qs_customize);
        mQSFooter = findViewById(R.id.qs_footer);
        mBackground = findViewById(R.id.quick_settings_background);
        mStatusBarBackground = findViewById(R.id.quick_settings_status_bar_background);
        mBackgroundGradient = findViewById(R.id.quick_settings_gradient_view);
        mSideMargins = getResources().getDimensionPixelSize(R.dimen.notification_side_paddings);
        mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
        updateSettings();

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setMargins();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setBackgroundGradientVisibility(newConfig);
        updateResources();
        mSizePoint.set(0, 0); // Will be retrieved on next measure pass.
    }

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            getContext().getContentResolver().registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_ALPHA), false,
                    this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_COLOR), false,
                    this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_COLOR_WALL), false,
                    this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_USE_WALL), false,
                    this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QS_PANEL_BG_USE_FW), false,
                    this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private void updateSettings() {
        int userQsWallColorSetting = Settings.System.getIntForUser(getContext().getContentResolver(),
                    Settings.System.QS_PANEL_BG_USE_WALL, 0, UserHandle.USER_CURRENT);
        mSetQsFromWall = userQsWallColorSetting == 1;
        int userQsFwSetting = Settings.System.getIntForUser(getContext().getContentResolver(),
                    Settings.System.QS_PANEL_BG_USE_FW, 1, UserHandle.USER_CURRENT);
        mSetQsFromResources = userQsFwSetting == 1;
        mQsBackGroundAlpha = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_PANEL_BG_ALPHA, 255,
                UserHandle.USER_CURRENT);
        mQsBackGroundColor = ColorUtils.getValidQsColor(Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_PANEL_BG_COLOR, ColorUtils.genRandomQsColor(),
                UserHandle.USER_CURRENT));
        mQsBackGroundColorWall = ColorUtils.getValidQsColor(Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_PANEL_BG_COLOR_WALL, ColorUtils.genRandomQsColor(),
                UserHandle.USER_CURRENT));
        setQsBackground();
    }

    private void setQsBackground() {
        int currentColor = mSetQsFromWall ? mQsBackGroundColorWall : mQsBackGroundColor;

        if (mSetQsFromResources) {
            stopDiscoMode();
            mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
            try {
                mOverlayManager.setEnabled("com.android.systemui.qstheme.color",
                        false, ActivityManager.getCurrentUser());
            } catch (RemoteException e) {
                Log.w("QSContainerImpl", "Can't change qs theme", e);
            }
        } else {
            startDiscoMode();
            if (mQsBackGround != null) {
                if (mDiscoAnim == null || (mDiscoAnim != null && !mDiscoAnim.isStarted() && !mDiscoAnim.isRunning())) {
                    mQsBackGround.setColorFilter(currentColor, PorterDuff.Mode.SRC_ATOP);
                    mQsBackGround.setAlpha(mQsBackGroundAlpha);
                }
            }
            try {
                mOverlayManager.setEnabled("com.android.systemui.qstheme.color",
                        true, ActivityManager.getCurrentUser());
            } catch (RemoteException e) {
                Log.w("QSContainerImpl", "Can't change qs theme", e);
            }
        }

        if (mQsBackGround != null && mBackground != null) {
            mBackground.setBackground(mQsBackGround);
        }
    }

    private void startDiscoMode() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        if (cal.get(Calendar.MONTH) != 3 || cal.get(Calendar.DAY_OF_MONTH) != 1) {
            stopDiscoMode();
            Settings.Secure.putInt(getContext().getContentResolver(), "disco_mode_triggered", 0);
            return;
        }
        final float from = 0f;
        final float to = 360f;
        if (mDiscoAnim != null)
            mDiscoAnim.cancel();
        mDiscoAnim = ValueAnimator.ofFloat(0, 1);
        final float[] hsl = {0f, 1f, 0.5f};
        mDiscoAnim.setDuration(5000);
        mDiscoAnim.setRepeatCount(ValueAnimator.INFINITE);
        mDiscoAnim.setRepeatMode(ValueAnimator.RESTART);
        mDiscoAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                hsl[0] = from + (to - from)*animation.getAnimatedFraction();
                mQsBackGround.setColorFilter(com.android.internal.graphics.ColorUtils.HSLToColor(hsl), PorterDuff.Mode.SRC_ATOP);
                mQsBackGround.setAlpha(mQsBackGroundAlpha);
                if (mQsBackGround != null && mBackground != null) {
                    mBackground.setBackground(mQsBackGround);
                }
            }
        });
        mDiscoAnim.start();
    }

    private void stopDiscoMode() {
        if (mDiscoAnim != null)
            mDiscoAnim.cancel();
        mDiscoAnim = null;
    }

    @Override
    public boolean performClick() {
        // Want to receive clicks so missing QQS tiles doesn't cause collapse, but
        // don't want to do anything with them.
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // QSPanel will show as many rows as it can (up to TileLayout.MAX_ROWS) such that the
        // bottom and footer are inside the screen.
        Configuration config = getResources().getConfiguration();
        boolean navBelow = config.smallestScreenWidthDp >= 600
                || config.orientation != Configuration.ORIENTATION_LANDSCAPE;
        MarginLayoutParams layoutParams = (MarginLayoutParams) mQSPanel.getLayoutParams();

        // The footer is pinned to the bottom of QSPanel (same bottoms), therefore we don't need to
        // subtract its height. We do not care if the collapsed notifications fit in the screen.
        int maxQs = getDisplayHeight() - layoutParams.topMargin - layoutParams.bottomMargin
                - getPaddingBottom();
        if (navBelow) {
            maxQs -= getResources().getDimensionPixelSize(R.dimen.navigation_bar_height);
        }
        // Measure with EXACTLY. That way, PagedTileLayout will only use excess height and will be
        // measured last, after other views and padding is accounted for.
        mQSPanel.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxQs, MeasureSpec.EXACTLY));
        int width = mQSPanel.getMeasuredWidth();
        int height = layoutParams.topMargin + layoutParams.bottomMargin
                + mQSPanel.getMeasuredHeight() + getPaddingBottom();
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        // QSCustomizer will always be the height of the screen, but do this after
        // other measuring to avoid changing the height of the QS.
        mQSCustomizer.measure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(getDisplayHeight(), MeasureSpec.EXACTLY));
    }


    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        // Do not measure QSPanel again when doing super.onMeasure.
        // This prevents the pages in PagedTileLayout to be remeasured with a different (incorrect)
        // size to the one used for determining the number of rows and then the number of pages.
        if (child != mQSPanel) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed,
                    parentHeightMeasureSpec, heightUsed);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateExpansion();
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        setBackgroundGradientVisibility(getResources().getConfiguration());
        mBackground.setVisibility(mQsDisabled ? View.GONE : View.VISIBLE);
    }

    private void updateResources() {
        LayoutParams layoutParams = (LayoutParams) mQSPanel.getLayoutParams();
        layoutParams.topMargin = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.quick_qs_offset_height);

        mQSPanel.setLayoutParams(layoutParams);
    }

    /**
     * Overrides the height of this view (post-layout), so that the content is clipped to that
     * height and the background is set to that height.
     *
     * @param heightOverride the overridden height
     */
    public void setHeightOverride(int heightOverride) {
        mHeightOverride = heightOverride;
        updateExpansion();
    }

    public void updateExpansion() {
        int height = calculateContainerHeight();
        setBottom(getTop() + height);
        mQSDetail.setBottom(getTop() + height);
        // Pin QS Footer to the bottom of the panel.
        mQSFooter.setTranslationY(height - mQSFooter.getHeight());
        mBackground.setTop(mQSPanel.getTop());
        mBackground.setBottom(height);
    }

    protected int calculateContainerHeight() {
        int heightOverride = mHeightOverride != -1 ? mHeightOverride : getMeasuredHeight();
        return mQSCustomizer.isCustomizing() ? mQSCustomizer.getHeight()
                : Math.round(mQsExpansion * (heightOverride - mHeader.getHeight()))
                + mHeader.getHeight();
    }

    private void setBackgroundGradientVisibility(Configuration newConfig) {
        if (newConfig.orientation == ORIENTATION_LANDSCAPE) {
            mBackgroundGradient.setVisibility(View.INVISIBLE);
            mStatusBarBackground.setVisibility(View.INVISIBLE);
        } else {
            mBackgroundGradient.setVisibility(mQsDisabled ? View.INVISIBLE : View.VISIBLE);
            mStatusBarBackground.setVisibility(View.VISIBLE);
        }
    }

    public void setExpansion(float expansion) {
        mQsExpansion = expansion;
        updateExpansion();
    }

    private void setMargins() {
        setMargins(mQSDetail);
        setMargins(mBackground);
        setMargins(mQSFooter);
        mQSPanel.setMargins(mSideMargins);
        mHeader.setMargins(mSideMargins);
    }

    private void setMargins(View view) {
        FrameLayout.LayoutParams lp = (LayoutParams) view.getLayoutParams();
        lp.rightMargin = mSideMargins;
        lp.leftMargin = mSideMargins;
    }

    private int getDisplayHeight() {
        if (mSizePoint.y == 0) {
            getDisplay().getRealSize(mSizePoint);
        }
        return mSizePoint.y;
    }

    private void setAprilEasterAlarm() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.YEAR, 2020);
        cal.set(Calendar.MONTH, 3);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        AlarmManager am = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(getContext(), AprilEasterBroadcast.class);
        PendingIntent pi = PendingIntent.getBroadcast(getContext(), 0, i, PendingIntent.FLAG_CANCEL_CURRENT);
        am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }
}
