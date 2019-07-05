package com.android.systemui.blur.opengl.program;

import com.android.systemui.blur.api.IProgram;

public class ProgramFactory {

    public static IProgram create(String vertexShaderCode, String fragmentShaderCode) {
        return new Program(vertexShaderCode, fragmentShaderCode);
    }
}
