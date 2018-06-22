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

import android.app.ActivityManager;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.qs.customize.QSCustomizer;

/**
 * Wrapper view with background which contains {@link QSPanel} and {@link BaseStatusBarHeader}
 */
public class QSContainerImpl extends FrameLayout {

    private static final String TAG = "QSContainerImpl";

    private final Point mSizePoint = new Point();

    private int mHeightOverride = -1;
    protected View mQSPanel;
    private View mQSDetail;
    protected View mHeader;
    protected float mQsExpansion;
    private QSCustomizer mQSCustomizer;
    private View mQSFooter;
    private float mFullElevation;
    private Drawable mQsBackGround;
    private int mQsBackGroundAlpha;
    private int mQsBackGroundColor;
    private int mQsBackGroundColorWall;
    private int currentColor;
    private int userThemeSetting;
    private boolean setQsFromWall;
    private boolean useBlackTheme = false;
    private boolean useDarkTheme = false;
    private boolean setQsFromResources;
    private SysuiColorExtractor mColorExtractor;

    private IOverlayManager mOverlayManager;

    public QSContainerImpl(Context context, AttributeSet attrs) {
        super(context, attrs);
        mOverlayManager = IOverlayManager.Stub.asInterface(
                ServiceManager.getService(Context.OVERLAY_SERVICE));
        Handler mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQSPanel = findViewById(R.id.quick_settings_panel);
        mQSDetail = findViewById(R.id.qs_detail);
        mHeader = findViewById(R.id.header);
        mQSCustomizer = findViewById(R.id.qs_customize);
        mQSFooter = findViewById(R.id.qs_footer);
        mFullElevation = mQSPanel.getElevation();
        mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
        updateSettings();

        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
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
            getContext().getContentResolver().registerContentObserver(Settings.System
                    .getUriFor(Settings.System.SYSTEM_THEME_STYLE), false,
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
        setQsFromWall = userQsWallColorSetting == 1;
        int userQsFwSetting = Settings.System.getIntForUser(getContext().getContentResolver(),
                    Settings.System.QS_PANEL_BG_USE_FW, 1, UserHandle.USER_CURRENT);
        setQsFromResources = userQsFwSetting == 1;
        mQsBackGroundAlpha = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_PANEL_BG_ALPHA, 255,
                UserHandle.USER_CURRENT);
        mQsBackGroundColor = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_PANEL_BG_COLOR, Color.WHITE,
                UserHandle.USER_CURRENT);
        mQsBackGroundColorWall = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_PANEL_BG_COLOR_WALL, Color.WHITE,
                UserHandle.USER_CURRENT);
        userThemeSetting = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.SYSTEM_THEME_STYLE, 2, ActivityManager.getCurrentUser());
        if (userThemeSetting == 0) {
            // The system wallpaper defines if system theme should be light or dark.
            WallpaperColors systemColors = mColorExtractor
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            useDarkTheme = systemColors != null
                    && (systemColors.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_THEME) != 0;
        } else {
            useDarkTheme = userThemeSetting == 2;
            useBlackTheme = userThemeSetting == 3;
        }
        currentColor = setQsFromWall ? mQsBackGroundColorWall : mQsBackGroundColor;
        setQsBackground();
        setQsOverlay();
    }

    private void setQsBackground() {

        if (setQsFromResources) {
            mQsBackGround = getContext().getDrawable(R.drawable.qs_background_primary);
        } else {
            mQsBackGround.setColorFilter(currentColor, PorterDuff.Mode.SRC_ATOP);
            mQsBackGround.setAlpha(mQsBackGroundAlpha);
        }
        if (mQsBackGround != null) {
            setBackground(mQsBackGround);
        }
    }

    @Override
    public boolean performClick() {
        // Want to receive clicks so missing QQS tiles doesn't cause collapse, but
        // don't want to do anything with them.
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Since we control our own bottom, be whatever size we want.
        // Otherwise the QSPanel ends up with 0 height when the window is only the
        // size of the status bar.
        mQSPanel.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED));
        int width = mQSPanel.getMeasuredWidth();
        LayoutParams layoutParams = (LayoutParams) mQSPanel.getLayoutParams();
        int height = layoutParams.topMargin + layoutParams.bottomMargin
                + mQSPanel.getMeasuredHeight();
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));

        // QSCustomizer will always be the height of the screen, but do this after
        // other measuring to avoid changing the height of the QS.
        getDisplay().getRealSize(mSizePoint);
        mQSCustomizer.measure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(mSizePoint.y, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateExpansion();
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
    }

    protected int calculateContainerHeight() {
        int heightOverride = mHeightOverride != -1 ? mHeightOverride : getMeasuredHeight();
        return mQSCustomizer.isCustomizing() ? mQSCustomizer.getHeight()
                : Math.round(mQsExpansion * (heightOverride - mHeader.getHeight()))
                + mHeader.getHeight();
    }

    public void setExpansion(float expansion) {
        mQsExpansion = expansion;
        updateExpansion();
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - ( 0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color))/255;
        if (darkness < 0.5) {
            return false; // It's a light color
        } else {
            return true; // It's a dark color
        }
    }

    public void setQsOverlay() {

        // This is being done here so that we don't have issues with one class
        // enabling and other disabling the same function. We can manage QS
        // in one place entirely. Let's not bother StatusBar.java at all
        //
        // TODO: Commonise isUsingDarkTheme and isUsingDarkTheme to a new class.
        // Perhaps even more checks if necessary.

        String qsthemeDark = "com.android.systemui.qstheme.dark";
        String qsthemeBlack = "com.android.systemui.qstheme.black";
        String qstheme = null;

        if (setQsFromResources) {
            try {
                mOverlayManager.setEnabled(qsthemeDark, useDarkTheme, ActivityManager.getCurrentUser());
                mOverlayManager.setEnabled(qsthemeBlack, useBlackTheme, ActivityManager.getCurrentUser());
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change dark/black qs overlays", e);
            }
        } else {
            // Only set black qs for black themes
            if (useBlackTheme)
                    qstheme = qsthemeBlack;
            else
                    qstheme = qsthemeDark;
            try {
                mOverlayManager.setEnabled(qstheme, isColorDark(currentColor), ActivityManager.getCurrentUser());
            } catch (RemoteException e) {
                Log.w(TAG, "Can't change qs theme", e);
            }
        }
    }
}
