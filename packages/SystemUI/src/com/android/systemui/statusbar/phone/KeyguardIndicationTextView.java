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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.TextView;

import com.android.systemui.Interpolators;

/**
 * A view to show hints on Keyguard ("Swipe up to unlock", "Tap again to open").
 */
public class KeyguardIndicationTextView extends TextView {

    private static final long ANIMATION_DURATION = 500;
    private static final long MIN_INDICATION_DURATION = 1500 /* 1.5s */;
    private boolean isShowing = false;
    private long showingSince;
    private int queued = 0;

    public KeyguardIndicationTextView(Context context) {
        super(context);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private Runnable fadeInCallback = new Runnable(){
        @Override
        public void run() {
            isShowing = true;
            showingSince = System.currentTimeMillis();
            queued--;
        }
    };


    private Runnable fadeOutCallback = new Runnable(){
        @Override
        public void run() {
            isShowing = false;
        }
    };

    /**
     * Changes the text with an animation and makes sure a single indication is shown long enough.
     *
     * @param text The text to show.
     */
    public void switchIndication(CharSequence text) {
        long delay = 0;
        long now = System.currentTimeMillis();
        long difference = showingSince + MIN_INDICATION_DURATION - now;
        if(isShowing && difference > 0){
            delay = difference;
            if(queued > 0){
                delay += queued * MIN_INDICATION_DURATION;
            }
        }
        if (TextUtils.isEmpty(text) && getAlpha() == 1f) {
            animateAlpha(0f, ANIMATION_DURATION, queued * MIN_INDICATION_DURATION, Interpolators.ALPHA_OUT, fadeOutCallback);
        } else if(getAlpha() == 0f) {
            queued++;
            animateAlpha(1f, ANIMATION_DURATION, 0, Interpolators.ALPHA_IN, fadeInCallback);
        } else {
            queued++;
            // Fade out the current indication
            animateAlpha(0f, ANIMATION_DURATION / 2, delay, Interpolators.ALPHA_OUT, new Runnable() {
                @Override
                public void run() {
                    // Fade in the new indication
                    setText(text);
                    animateAlpha(1f, ANIMATION_DURATION / 2, 0, Interpolators.ALPHA_IN, fadeInCallback);
                }
            });
        }
    }

    private void animateAlpha(float targetAlpha, long duration, long delay, Interpolator interpolator, Runnable endAction) {
        animate()
        .alpha(targetAlpha)
        .setDuration(duration)
        .setStartDelay(delay)
        .setInterpolator(interpolator)
        .withEndAction(new Runnable() {
            @Override
            public void run() {
                if(endAction != null){
                    endAction.run();
                }
            }
        });
    }

    /**
     * See {@link #switchIndication}.
     */
    public void switchIndication(int textResId) {
        switchIndication(getResources().getText(textResId));
    }
}
