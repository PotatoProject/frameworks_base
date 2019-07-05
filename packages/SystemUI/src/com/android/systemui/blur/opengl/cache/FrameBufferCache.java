package com.android.systemui.blur.opengl.cache;

import com.android.systemui.blur.opengl.framebuffer.FrameBufferFactory;
import com.android.systemui.blur.api.IFrameBuffer;

/**
 * Created by yuxfzju on 2017/1/21.
 */

public class FrameBufferCache {

    private static volatile FrameBufferCache sInstance;

    private CachePool<Object, IFrameBuffer> mCache;

    private IFrameBuffer sDisplayFrameBuffer;

    private FrameBufferCache() {
        mCache = new CachePool<Object, IFrameBuffer>() {
            @Override
            protected IFrameBuffer create(Object key) {
                return FrameBufferFactory.create();
            }

            @Override
            protected boolean checkHit(Object a, IFrameBuffer b) {
                return true;
            }

            @Override
            protected void entryDeleted(IFrameBuffer frameBuffer) {
                if (frameBuffer != null) {
                    frameBuffer.delete();
                }
            }
        };

    }

    public static FrameBufferCache getInstance() {
        if (sInstance == null) {
            synchronized (FrameBufferCache.class) {
                if (sInstance == null) {
                    sInstance = new FrameBufferCache();
                }
            }
        }

        return sInstance;
    }

    public IFrameBuffer getFrameBuffer() {
        if (mCache != null) {
            return mCache.get(new Object());
        }
        return null;
    }

    public IFrameBuffer getDisplayFrameBuffer() {
        if (sDisplayFrameBuffer == null) {
            sDisplayFrameBuffer = FrameBufferFactory.getDisplayFrameBuffer();
        }
        return sDisplayFrameBuffer;
    }

    public void recycleFrameBuffer(IFrameBuffer frameBuffer) {
        if (frameBuffer != null) {
            mCache.put(frameBuffer);
        }
    }

    public void deleteFrameBuffers() {
        if (mCache != null) {
            mCache.evictAll();
        }

        if (sDisplayFrameBuffer != null) {
            sDisplayFrameBuffer.delete();
            sDisplayFrameBuffer = null;
        }
    }

}
