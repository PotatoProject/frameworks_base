package com.android.systemui.blur.anno;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.android.systemui.blur.HokoBlur.BOTH;
import static com.android.systemui.blur.HokoBlur.HORIZONTAL;
import static com.android.systemui.blur.HokoBlur.VERTICAL;

/**
 * Created by yuxfzju on 2017/2/20.
 */

@IntDef({HORIZONTAL, VERTICAL, BOTH})
@Retention(RetentionPolicy.SOURCE)
public @interface Direction {
}
