package vx.engine;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Malha de triângulos não indexada com atributos float intercalados.
 * Layout padrão da cena ({@link #SCENE_LAYOUT}): pos3, normal3, cor3, ao1, material1, uv2.
 */
public final class Mesh implements AutoCloseable {
    public static final int[] SCENE_LAYOUT = {3, 3, 3, 1, 1, 2};
    public static final int SCENE_STRIDE = 13;

    private final int vao, vbo;
    public final int vertexCount;
    private final long bytes;
    /** Bytes de vértices vivos na GPU (todas as malhas); usado nas métricas do viewer. */
    public static long liveBytes, liveMeshes;
    /** Caixa delimitadora no espaço do modelo (min xyz, max xyz). */
    public final float[] bounds = new float[6];

    public Mesh(float[] data, int count, int[] layout) {
        int stride = 0;
        for (int s : layout) stride += s;
        vertexCount = count / stride;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        float[] exact = count == data.length ? data : java.util.Arrays.copyOf(data, count);
        glBufferData(GL_ARRAY_BUFFER, exact, GL_STATIC_DRAW);
        bytes = (long) exact.length * Float.BYTES;
        liveBytes += bytes;
        liveMeshes++;
        int off = 0;
        for (int i = 0; i < layout.length; i++) {
            glEnableVertexAttribArray(i);
            glVertexAttribPointer(i, layout[i], GL_FLOAT, false, stride * Float.BYTES, (long) off * Float.BYTES);
            off += layout[i];
        }
        glBindVertexArray(0);

        bounds[0] = bounds[1] = bounds[2] = Float.MAX_VALUE;
        bounds[3] = bounds[4] = bounds[5] = -Float.MAX_VALUE;
        for (int v = 0; v < vertexCount; v++) {
            for (int a = 0; a < 3; a++) {
                float p = exact[v * stride + a];
                bounds[a] = Math.min(bounds[a], p);
                bounds[3 + a] = Math.max(bounds[3 + a], p);
            }
        }
    }

    public Mesh(float[] data, int[] layout) { this(data, data.length, layout); }

    public void draw() {
        if (vertexCount == 0) return;
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
    }

    @Override
    public void close() {
        liveBytes -= bytes;              // o que foi somado (o layout não é sempre o de cena)
        liveMeshes--;
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
