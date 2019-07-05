package com.android.systemui.blur.opengl.cache;

import com.android.systemui.blur.api.ITexture;
import com.android.systemui.blur.opengl.texture.TextureFactory;
import com.android.systemui.blur.opengl.util.Size;

/**
 * Created by yuxfzju on 17/1/20.
 */

public class TextureCache {

    private static volatile TextureCache sInstance;

    private CachePool<Size, ITexture> mCache;

    private TextureCache() {
        mCache = new CachePool<Size, ITexture>() {

            @Override
            protected ITexture create(Size size) {
                if (size == null) {
                    return null;
                }
                return TextureFactory.create(size.width(), size.height());
            }


            @Override
            protected void entryDeleted(ITexture texture) {
                if (texture != null) {
                    texture.delete();
                }
            }

            @Override
            protected boolean checkHit(Size a, ITexture b) {
                return a != null && b != null && a.width() == b.width() && a.height() == b.height();
            }
        };
    }

    public static TextureCache getInstance() {
        if (sInstance == null) {
            synchronized (TextureCache.class) {
                if (sInstance == null) {
                    sInstance = new TextureCache();
                }
            }
        }

        return sInstance;
    }

    public ITexture getTexture(int width, int height) {

        if (mCache != null) {
            return mCache.get(new Size(width, height));
        }

        return null;
    }

    public void recycleTexture(ITexture texture) {
        if (texture != null) {
            mCache.put(texture);
        }
    }

    public void deleteTextures() {
        if (mCache != null) {
            mCache.evictAll();
        }
    }
}
