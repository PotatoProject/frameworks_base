package com.android.systemui.blur.task;

import com.android.systemui.blur.api.IBlurResultDispatcher;
import com.android.systemui.blur.util.SingleMainHandler;

import java.util.concurrent.Executor;

/**
 * Created by yuxfzju on 2017/2/7.
 */

public class AndroidBlurResultDispatcher implements IBlurResultDispatcher {

    static final IBlurResultDispatcher MAIN_THREAD_DISPATCHER = new AndroidBlurResultDispatcher(SingleMainHandler.get());

    private Executor mResultPoster;

    public AndroidBlurResultDispatcher(final android.os.Handler handler) {
        mResultPoster = new Executor() {
            @Override
            public void execute(Runnable command) {
                if (handler != null) {
                    handler.post(command);
                }
            }
        };
    }

    @Override
    public void dispatch(BlurResult result) {
        mResultPoster.execute(new BlurResultDeliveryRunnable(result));
    }
}
