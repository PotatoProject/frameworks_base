/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.navigationbar.gestural;

import android.animation.ArgbEvaluator;
import android.annotation.ColorInt;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;

import com.android.settingslib.Utils;
import com.android.systemui.R;
import com.android.systemui.navigationbar.buttons.ButtonInterface;

public class NavigationHandle extends View implements ButtonInterface {

    protected final Paint mPaint = new Paint();
    private @ColorInt final int mLightColor;
    private @ColorInt final int mDarkColor;
    protected final int mRadius;
    protected final int mBottom;
    private boolean mRequiresInvalidate;

    private int mWidth;

    private final Resources mRes;
    private final ContentResolver mResolver;
    private final String WIDTH_SETTING = "navigation_handle_width";

    private final class CustomSettingsObserver extends ContentObserver {
        public CustomSettingsObserver(Context context, Handler handler) {
            super(handler);
            context.getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NAVIGATION_HANDLE_WIDTH),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            invalidate();
        }
    }

    private CustomSettingsObserver mSettingsObserver;

    public NavigationHandle(Context context) {
        this(context, null);
    }

    public NavigationHandle(Context context, AttributeSet attr) {
        super(context, attr);
        mRes = context.getResources();
        mResolver = context.getContentResolver();
        mRadius = mRes.getDimensionPixelSize(R.dimen.navigation_handle_radius);
        mBottom = mRes.getDimensionPixelSize(R.dimen.navigation_handle_bottom);

        final int dualToneDarkTheme = Utils.getThemeAttr(context, R.attr.darkIconTheme);
        final int dualToneLightTheme = Utils.getThemeAttr(context, R.attr.lightIconTheme);
        Context lightContext = new ContextThemeWrapper(context, dualToneLightTheme);
        Context darkContext = new ContextThemeWrapper(context, dualToneDarkTheme);
        mLightColor = Utils.getColorAttrDefaultColor(lightContext, R.attr.homeHandleColor);
        mDarkColor = Utils.getColorAttrDefaultColor(darkContext, R.attr.homeHandleColor);
        mPaint.setAntiAlias(true);
        setFocusable(false);
        mSettingsObserver = new CustomSettingsObserver(context, new Handler(context.getMainLooper()));
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (alpha > 0f && mRequiresInvalidate) {
            mRequiresInvalidate = false;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw that bar
        int navHeight = getHeight();
        int height = mRadius * 2;
        mWidth = (int) getCustomWidth();
        int y = (navHeight - mBottom - height);
        int padding = (int) getCustomPadding();
        canvas.drawRoundRect(padding, y, mWidth + padding, y + height, mRadius, mRadius, mPaint);
    }

    private double getCustomPadding() {
        int basePadding = (int) (getWidth() / 2) - (int) (mWidth / 2);
        return basePadding;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
    }

    @Override
    public void abortCurrentGesture() {
    }

    @Override
    public void setVertical(boolean vertical) {
    }

    @Override
    public void setDarkIntensity(float intensity) {
        int color = (int) ArgbEvaluator.getInstance().evaluate(intensity, mLightColor, mDarkColor);
        if (mPaint.getColor() != color) {
            mPaint.setColor(color);
            if (getVisibility() == VISIBLE && getAlpha() > 0) {
                invalidate();
            } else {
                // If we are currently invisible, then invalidate when we are next made visible
                mRequiresInvalidate = true;
            }
        }
    }

    @Override
    public void setDelayTouchFeedback(boolean shouldDelay) {
    }

    private double getCustomWidth() {
        int baseWidth = mRes.getDimensionPixelSize(R.dimen.navigation_home_handle_width);
        int userSelection = Settings.System.getInt(mResolver, WIDTH_SETTING, 10);
        double result = ((double) baseWidth) * (userSelection  / ((double) 10));
        return result;
    }
}
