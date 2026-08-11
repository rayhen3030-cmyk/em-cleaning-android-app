package de.emcleaning.app;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CameraRenderer {

    private int textureId = -1;
    private int program;

    private int positionAttribute;
    private int texCoordAttribute;
    private int textureUniform;

    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
    private FloatBuffer transformedTexCoordBuffer;

    private boolean texCoordsInitialized = false;

    private static final float[] QUAD_COORDS = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
    };

    private static final float[] TEX_COORDS = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    };

    private static final String VERTEX_SHADER =
            "attribute vec4 a_Position;\n" +
            "attribute vec2 a_TexCoord;\n" +
            "varying vec2 v_TexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = a_Position;\n" +
            "    v_TexCoord = a_TexCoord;\n" +
            "}";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES u_Texture;\n" +
            "varying vec2 v_TexCoord;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(u_Texture, v_TexCoord);\n" +
            "}";

    public void createOnGlThread() {

        int[] textures = new int[1];

        GLES20.glGenTextures(
                1,
                textures,
                0
        );

        textureId = textures[0];

        GLES20.glBindTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                textureId
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );

        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );

        vertexBuffer = createFloatBuffer(
                QUAD_COORDS
        );

        texCoordBuffer = createFloatBuffer(
                TEX_COORDS
        );

        transformedTexCoordBuffer =
                ByteBuffer
                        .allocateDirect(
                                TEX_COORDS.length * 4
                        )
                        .order(
                                ByteOrder.nativeOrder()
                        )
                        .asFloatBuffer();

        int vertexShader =
                compileShader(
                        GLES20.GL_VERTEX_SHADER,
                        VERTEX_SHADER
                );

        int fragmentShader =
                compileShader(
                        GLES20.GL_FRAGMENT_SHADER,
                        FRAGMENT_SHADER
                );

        program =
                GLES20.glCreateProgram();

        GLES20.glAttachShader(
                program,
                vertexShader
        );

        GLES20.glAttachShader(
                program,
                fragmentShader
        );

        GLES20.glLinkProgram(
                program
        );

        positionAttribute =
                GLES20.glGetAttribLocation(
                        program,
                        "a_Position"
                );

        texCoordAttribute =
                GLES20.glGetAttribLocation(
                        program,
                        "a_TexCoord"
                );

        textureUniform =
                GLES20.glGetUniformLocation(
                        program,
                        "u_Texture"
                );
    }

    public int getTextureId() {
        return textureId;
    }

    public void draw(Frame frame) {

        if (frame == null) {
            return;
        }

        if (
                frame.hasDisplayGeometryChanged()
                        ||
                !texCoordsInitialized
        ) {

            texCoordBuffer.position(0);
            transformedTexCoordBuffer.position(0);

            frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    vertexBuffer,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    transformedTexCoordBuffer
            );

            texCoordsInitialized = true;
        }

        GLES20.glDisable(
                GLES20.GL_DEPTH_TEST
        );

        GLES20.glDepthMask(
                false
        );

        GLES20.glUseProgram(
                program
        );

        vertexBuffer.position(0);

        GLES20.glVertexAttribPointer(
                positionAttribute,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
        );

        GLES20.glEnableVertexAttribArray(
                positionAttribute
        );

        transformedTexCoordBuffer.position(0);

        GLES20.glVertexAttribPointer(
                texCoordAttribute,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                transformedTexCoordBuffer
        );

        GLES20.glEnableVertexAttribArray(
                texCoordAttribute
        );

        GLES20.glActiveTexture(
                GLES20.GL_TEXTURE0
        );

        GLES20.glBindTexture(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                textureId
        );

        GLES20.glUniform1i(
                textureUniform,
                0
        );

        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                0,
                4
        );

        GLES20.glDisableVertexAttribArray(
                positionAttribute
        );

        GLES20.glDisableVertexAttribArray(
                texCoordAttribute
        );

        GLES20.glDepthMask(
                true
        );
    }

    private FloatBuffer createFloatBuffer(
            float[] data
    ) {

        FloatBuffer buffer =
                ByteBuffer
                        .allocateDirect(
                                data.length * 4
                        )
                        .order(
                                ByteOrder.nativeOrder()
                        )
                        .asFloatBuffer();

        buffer.put(
                data
        );

        buffer.position(
                0
        );

        return buffer;
    }

    private int compileShader(
            int type,
            String shaderCode
    ) {

        int shader =
                GLES20.glCreateShader(
                        type
                );

        GLES20.glShaderSource(
                shader,
                shaderCode
        );

        GLES20.glCompileShader(
                shader
        );

        return shader;
    }
}
