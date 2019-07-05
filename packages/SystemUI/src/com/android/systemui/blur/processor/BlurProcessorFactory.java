package com.android.systemui.blur.processor;

import com.android.systemui.blur.HokoBlur;
import com.android.systemui.blur.anno.Scheme;

class BlurProcessorFactory {

    static BlurProcessor getBlurProcessor(@Scheme int scheme, BlurProcessor.Builder builder) {

        BlurProcessor generator = null;

        switch (scheme) {
            case HokoBlur.SCHEME_OPENGL:
                generator = new OpenGLBlurProcessor(builder);
                break;
            case HokoBlur.SCHEME_NATIVE:
                generator = new NativeBlurProcessor(builder);
                break;
            case HokoBlur.SCHEME_JAVA:
                generator = new OriginBlurProcessor(builder);
                break;
            default:
                throw new IllegalArgumentException("Unsupported blur scheme!");
        }

        return generator;
    }
}
