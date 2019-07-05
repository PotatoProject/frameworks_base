package com.android.systemui.blur.processor;

import android.graphics.Bitmap;
import android.util.Log;

import com.android.systemui.blur.HokoBlur;
import com.android.systemui.blur.filter.OriginBlurFilter;
import com.android.systemui.blur.task.BlurSubTask;
import com.android.systemui.blur.task.BlurTaskManager;
import com.android.systemui.blur.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yuxfzju on 16/9/7.
 */
class OriginBlurProcessor extends BlurProcessor {
    private static final String TAG = OriginBlurProcessor.class.getSimpleName();

    OriginBlurProcessor(Builder builder) {
        super(builder);
    }

    @Override
    protected Bitmap doInnerBlur(Bitmap scaledInBitmap, boolean concurrent) {
        Preconditions.checkNotNull(scaledInBitmap, "scaledInBitmap == null");
        try {
            if (concurrent) {
                int cores = BlurTaskManager.getWorkersNum();
                List<BlurSubTask> hTasks = new ArrayList<>(cores);
                List<BlurSubTask> vTasks = new ArrayList<>(cores);

                for (int i = 0; i < cores; i++) {
                    hTasks.add(new BlurSubTask(HokoBlur.SCHEME_JAVA, mMode, scaledInBitmap, mRadius, cores, i, HokoBlur.HORIZONTAL));
                    vTasks.add(new BlurSubTask(HokoBlur.SCHEME_JAVA, mMode, scaledInBitmap, mRadius, cores, i, HokoBlur.VERTICAL));
                }

                BlurTaskManager.getInstance().invokeAll(hTasks);
                BlurTaskManager.getInstance().invokeAll(vTasks);
            } else {
                OriginBlurFilter.doFullBlur(mMode, scaledInBitmap, mRadius);
            }
        } catch (Throwable e) {
            Log.e(TAG, "Blur the bitmap error", e);
        }
        return scaledInBitmap;
    }

}

