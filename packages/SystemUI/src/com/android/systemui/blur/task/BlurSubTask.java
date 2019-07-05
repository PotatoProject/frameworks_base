package com.android.systemui.blur.task;

import android.graphics.Bitmap;

import com.android.systemui.blur.HokoBlur;
import com.android.systemui.blur.anno.Direction;
import com.android.systemui.blur.anno.Mode;
import com.android.systemui.blur.anno.Scheme;
import com.android.systemui.blur.filter.OriginBlurFilter;
import com.android.systemui.blur.filter.NativeBlurFilter;
import com.android.systemui.blur.util.Preconditions;

import java.util.concurrent.Callable;

/**
 * Every bitmap blur sub task only process a partition of bitmap.
 * Just blur a bitmap in parallel
 *
 * Created by yuxfzju on 2017/2/17.
 */

public class BlurSubTask implements Callable<Void> {

    @Scheme
    private final int mScheme;
    @Mode
    private final int mMode;
    private final Bitmap mBitmapOut;
    private final int mRadius;
    private final int mIndex;
    private final int mCores;

    @Direction
    private final int mDirection;

    public BlurSubTask(@Scheme int scheme, @Mode int mode, Bitmap bitmapOut, int radius, int cores, int index, @Direction int direction) {
        mScheme = scheme;
        mMode = mode;
        mBitmapOut = bitmapOut;
        mRadius = radius;
        mIndex = index;
        mCores = cores;
        mDirection = direction;
    }

    @Override
    public Void call() {
        Preconditions.checkNotNull(mBitmapOut, "mBitmapOut == null");
        Preconditions.checkArgument(!mBitmapOut.isRecycled(), "You must input an unrecycled bitmap !");
        Preconditions.checkArgument(mCores > 0, "mCores < 0");

        applyPixelsBlur();

        return null;
    }

    private void applyPixelsBlur() {
        switch (mScheme) {
            case HokoBlur.SCHEME_NATIVE:
                NativeBlurFilter.doBlur(mMode, mBitmapOut, mRadius, mCores, mIndex, mDirection);
                break;

            case HokoBlur.SCHEME_JAVA:
                OriginBlurFilter.doBlur(mMode, mBitmapOut, mRadius, mCores, mIndex, mDirection);
                break;

            case HokoBlur.SCHEME_OPENGL:
                throw new UnsupportedOperationException("Blur in parallel not supported !");
            default:
                break;

        }

    }
}
