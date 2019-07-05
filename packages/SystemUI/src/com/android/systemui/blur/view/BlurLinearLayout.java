package com.android.systemui.blur.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.android.systemui.blur.opengl.drawable.BlurDrawable;

/**
 * Created by yuxfzju on 16/11/9.
 */
public class BlurLinearLayout extends LinearLayout{
    private BlurDrawable mBlurDrawable;

    public BlurLinearLayout(Context context) {
        super(context);
        init();
    }

    public BlurLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlurLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBlurDrawable = new BlurDrawable();
        setBackground(mBlurDrawable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBlurDrawable.freeGLResource();
    }


    public BlurDrawable getBlurDrawable() {
        return mBlurDrawable;
    }
}
