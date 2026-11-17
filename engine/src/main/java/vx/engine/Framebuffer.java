package vx.engine;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL20.glDrawBuffers;
import static org.lwjgl.opengl.GL30.*;

/** FBO com N anexos de cor (formatos internos dados) e, opcionalmente, textura de profundidade. */
public final class Framebuffer implements AutoCloseable {
    public final int id, width, height;
    public final int[] color;
    public final int depth;

    public Framebuffer(int width, int height, boolean withDepth, int... colorFormats) {
        this.width = width;
        this.height = height;
        id = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, id);
        color = new int[colorFormats.length];
        int[] bufs = new int[colorFormats.length];
        for (int i = 0; i < colorFormats.length; i++) {
            color[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, color[i]);
            boolean isFloat = colorFormats[i] == GL_RGBA16F;
            glTexImage2D(GL_TEXTURE_2D, 0, colorFormats[i], width, height, 0, GL_RGBA,
                    isFloat ? GL_FLOAT : GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, color[i], 0);
            bufs[i] = GL_COLOR_ATTACHMENT0 + i;
        }
        if (withDepth) {
            depth = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, depth);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
            glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, new float[]{1, 1, 1, 1});
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
        } else {
            depth = 0;
        }
        if (bufs.length == 0) {
            glDrawBuffer(GL_NONE);
            glReadBuffer(GL_NONE);
        } else {
            glDrawBuffers(bufs);
        }
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("FBO incompleto: 0x" + Integer.toHexString(status));
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, id);
        glViewport(0, 0, width, height);
    }

    public static void bindDefault(int width, int height) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
    }

    @Override
    public void close() {
        glDeleteFramebuffers(id);
        for (int c : color) glDeleteTextures(c);
        if (depth != 0) glDeleteTextures(depth);
    }
}
