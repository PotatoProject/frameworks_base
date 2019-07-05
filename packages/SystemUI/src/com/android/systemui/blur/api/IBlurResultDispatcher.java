package com.android.systemui.blur.api;

import com.android.systemui.blur.task.BlurResult;

public interface IBlurResultDispatcher {
    void dispatch(BlurResult result);
}
