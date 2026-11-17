package vx.engine;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Rodada 07.2 — lote de desenho 2D da interface (quads coloridos e texturizados) em pixels do framebuffer.
 * Uma textura por vez: trocar de textura descarrega o lote. Branco 1x1 para áreas chapadas. Sem estado de
 * layout: quem decide onde desenhar é a interface (viewer).
 */
public final class UiBatch implements AutoCloseable {
    private final Shader shader = new Shader("ui.vert", "ui.frag");
    private final Texture white;
    private float[] data = new float[8 * 6 * 512];
    private int n;
    private int texture;
    private int screenW, screenH;
    /** VAO/VBO persistentes (o conteúdo é reenviado a cada descarga; nada é criado por quadro). */
    private final int vao = glGenVertexArrays(), vbo = glGenBuffers();

    public UiBatch() {
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = 8 * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glBindVertexArray(0);
        ByteBuffer px = BufferUtils.createByteBuffer(4);
        px.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1).flip();
        white = new Texture(1, 1, px, true);
        texture = white.id;
    }

    public void begin(int w, int h) {
        screenW = w;
        screenH = h;
        n = 0;
        texture = white.id;
    }

    /** Retângulo chapado (cor 0xRRGGBBAA). */
    public void rect(float x, float y, float w, float h, int rgba) {
        quad(white.id, x, y, w, h, 0, 0, 1, 1, rgba);
    }

    /** Imagem (ou região de atlas) tingida. */
    public void quad(int tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int rgba) {
        if (tex != texture) {
            flush();
            texture = tex;
        }
        if (n + 48 > data.length) {
            if (data.length < (1 << 20)) data = java.util.Arrays.copyOf(data, data.length * 2);
            else flush();
        }
        float r = ((rgba >>> 24) & 255) / 255f, g = ((rgba >>> 16) & 255) / 255f, b = ((rgba >>> 8) & 255) / 255f, a = (rgba & 255) / 255f;
        float[][] v = {{x, y, u0, v0}, {x + w, y, u1, v0}, {x + w, y + h, u1, v1}, {x, y, u0, v0}, {x + w, y + h, u1, v1}, {x, y + h, u0, v1}};
        for (float[] p : v) {
            data[n++] = p[0]; data[n++] = p[1]; data[n++] = p[2]; data[n++] = p[3];
            data[n++] = r; data[n++] = g; data[n++] = b; data[n++] = a;
        }
    }

    public void flush() {
        if (n == 0) return;
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        shader.use().set("uScreen", (float) screenW, (float) screenH).set("uTex", 0);
        Texture.bind(0, texture);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        try (org.lwjgl.system.MemoryStack ms = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.FloatBuffer fb = n * 4 <= 32 * 1024 ? ms.mallocFloat(n) : org.lwjgl.BufferUtils.createFloatBuffer(n);
            fb.put(data, 0, n).flip();
            glBufferData(GL_ARRAY_BUFFER, (long) n * Float.BYTES, GL_STREAM_DRAW);   // órfão: o driver não espera o quadro anterior
            glBufferSubData(GL_ARRAY_BUFFER, 0, fb);
        }
        glDrawArrays(GL_TRIANGLES, 0, n / 8);
        glBindVertexArray(0);
        glDisable(GL_BLEND);
        n = 0;
    }

    public void end() { flush(); }

    @Override
    public void close() {
        shader.close();
        white.close();
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
