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

    /**
     * Fluxo só de posição para o passo de sombra ({@code -Dvx.sombra.vbo=false} desliga). O passo de profundidade lê
     * apenas {@code aPos}; no buffer intercalado isso é 12 bytes úteis a cada 52, e a GPU percorre a memória inteira.
     */
    public static final boolean DEPTH_STREAM = !"false".equals(System.getProperty("vx.sombra.vbo"));

    private final int vao, vbo;
    private int depthVao, depthVbo;
    private long depthBytes;
    public int vertexCount;
    private long bytes;
    private final int stride;
    /** Bytes de vértices vivos na GPU (todas as malhas); usado nas métricas do viewer. */
    public static long liveBytes, liveMeshes;
    /** Caixa delimitadora no espaço do modelo (min xyz, max xyz). */
    public final float[] bounds = new float[6];

    public Mesh(float[] data, int count, int[] layout) {
        int stride = 0;
        for (int s : layout) stride += s;
        this.stride = stride;
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
        if (DEPTH_STREAM && layout.length > 1 && layout[0] == 3 && vertexCount > 0) {
            float[] pos = new float[vertexCount * 3];
            for (int v = 0; v < vertexCount; v++) System.arraycopy(exact, v * stride, pos, v * 3, 3);
            depthVao = glGenVertexArrays();
            depthVbo = glGenBuffers();
            glBindVertexArray(depthVao);
            glBindBuffer(GL_ARRAY_BUFFER, depthVbo);
            glBufferData(GL_ARRAY_BUFFER, pos, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);
            depthBytes = (long) pos.length * Float.BYTES;
            liveBytes += depthBytes;
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

    /**
     * Rodada 09.2 — troca os vértices (malha dinâmica: fragmentos de efeito, reconstruídos a cada quadro sem criar outro
     * objeto na GPU). {@code bounds} é responsabilidade de quem chama.
     */
    public void update(float[] data, int count) {
        dropDepthStream();                                                 // malha dinâmica: o passo de sombra usa o buffer principal
        if (dyn == null || dyn.capacity() < count) dyn = org.lwjgl.BufferUtils.createFloatBuffer(Math.max(count, 1024));
        dyn.clear();
        dyn.put(data, 0, count).flip();                                    // sem alocar por quadro (buffer reaproveitado)
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, dyn, GL_DYNAMIC_DRAW);
        liveBytes += (long) count * Float.BYTES - bytes;
        bytes = (long) count * Float.BYTES;
        vertexCount = count / stride;
    }

    private java.nio.FloatBuffer dyn;

    public void draw() {
        if (vertexCount == 0) return;
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
    }

    /** Desenha só a posição (passo de sombra): mesmos triângulos de {@link #draw()}, 12 bytes por vértice em vez de 52. */
    public void drawDepth() {
        if (depthVao == 0) { draw(); return; }
        if (vertexCount == 0) return;
        glBindVertexArray(depthVao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
    }

    private void dropDepthStream() {
        if (depthVao == 0) return;
        glDeleteBuffers(depthVbo);
        glDeleteVertexArrays(depthVao);
        liveBytes -= depthBytes;
        depthVao = depthVbo = 0;
        depthBytes = 0;
    }

    @Override
    public void close() {
        dropDepthStream();
        liveBytes -= bytes;              // o que foi somado (o layout não é sempre o de cena)
        liveMeshes--;
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
