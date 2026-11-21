package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import vx.engine.Camera;
import vx.engine.Framebuffer;
import vx.engine.Mesh;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;
import vx.viewer.Renderer;
import vx.viewer.StyleProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * Rodada 13.1 — os itens da mochila desenhados com os modelos voxel de verdade ({@code assets/items/e/}, gerados por
 * {@code tools/itens_e.py}), pelo MESMO renderizador da cena, com fundo transparente ({@link Renderer.Toggles#alpha}).
 * <ul>
 *   <li><b>Atlas:</b> um FBO com uma célula por modelo, em 3/4 de frente, feito UMA vez (na primeira vez que a mochila ou
 *   o cartão do item recebido aparece). A grade e o cartão são quads desse atlas, num lote só.</li>
 *   <li><b>Prévia:</b> o item selecionado, maior e girando devagar, num FBO próprio — só enquanto a mochila está aberta.</li>
 *   <li><b>Renderizador próprio:</b> nunca redimensiona os alvos do mundo (o {@link Renderer} do jogo fica no tamanho da
 *   janela).</li>
 * </ul>
 * Thread do GL; o desenho só lê a lógica. Quem cria fecha ({@link #close}).
 */
final class ItemRenders implements AutoCloseable {
    /** Lado da célula do atlas (px): o espaço da grade em 4K mostra até ~130 px; a célula é maior para sobrar nitidez. */
    static final int CELL = 192, PREVIEW = 384;
    private static final int COLS = 4;

    /** Um modelo: malha e caixa (u) no espaço do modelo; a distância de câmera que o enquadra parado e girando. */
    private record Model(Mesh mesh, float cx, float cy, float cz, float[] box, float still, float turning) { }

    private final Path dir;
    private final List<String> names = new ArrayList<>();
    private final Map<String, JsonObject> entries = new LinkedHashMap<>();
    private final Map<String, Model> models = new HashMap<>();
    private Renderer renderer;
    private Framebuffer atlas, preview;
    private String previewModel;
    private float previewAngle = Float.NaN;
    /** Contagem (QA): células do atlas e quadros da prévia renderizados. */
    int atlasRenders, previewRenders;
    /** Tempo (ns) da montagem do atlas (QA de desempenho). */
    long atlasNanos;

    ItemRenders(Path assets) {
        dir = assets.resolve("items/e");
        Path m = dir.resolve("itens.json");
        if (!Files.exists(m)) return;                                     // sem modelos: os espaços ficam sem desenho
        try {
            JsonObject doc = JsonParser.parseString(Files.readString(m)).getAsJsonObject();
            for (String n : doc.keySet()) { names.add(n); entries.put(n, doc.getAsJsonObject(n)); }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean has(String model) { return entries.containsKey(model); }

    private Model model(String name) {
        Model m = models.get(name);
        if (m != null) return m;
        JsonObject e = entries.get(name);
        float vs = e.get("voxelSize").getAsFloat();
        var o = e.getAsJsonArray("origin");
        var sz = e.getAsJsonArray("sizeVoxels");
        float ox = o.get(0).getAsFloat(), oy = o.get(1).getAsFloat(), oz = o.get(2).getAsFloat();
        Mesh mesh = VoxelMesher.build(VoxModel.load(dir.resolve(e.get("file").getAsString())), vs, ox, oy, oz);
        float w = sz.get(0).getAsFloat() * vs, h = sz.get(1).getAsFloat() * vs, d = sz.get(2).getAsFloat() * vs;
        // centro da caixa: a malha vai de origem·vs até (origem + tamanho)·vs em cada eixo
        float[] box = {ox * vs, oy * vs, oz * vs, ox * vs + w, oy * vs + h, oz * vs + d};
        float cx = (box[0] + box[3]) / 2, cy = (box[1] + box[4]) / 2, cz = (box[2] + box[5]) / 2;
        m = new Model(mesh, cx, cy, cz, box, fit(box, cx, cy, cz, STILL_YAW, STILL_YAW), fit(box, cx, cy, cz, 0f, (float) (2 * Math.PI)));
        models.put(name, m);
        return m;
    }

    static final float FOV = 20f, PITCH = 0.40f, STILL_YAW = 0.62f, MARGIN = 0.94f;

    /**
     * A menor distância de câmera em que os 8 cantos da caixa cabem na vista (com {@link #MARGIN} de folga) para todo rumo
     * entre {@code yaw0} e {@code yaw1} — o enquadramento justo da célula (parado) e da prévia (girando, sem cortar nunca).
     */
    static float fit(float[] b, float cx, float cy, float cz, float yaw0, float yaw1) {
        float lo = 0.05f, hi = 20f, t = (float) Math.tan(Math.toRadians(FOV / 2)) * MARGIN;
        Camera c = new Camera();
        c.fovDeg = FOV;
        c.pitch = PITCH;
        c.target.set(cx, cy, cz);
        org.joml.Vector3f p = new org.joml.Vector3f();
        int steps = yaw1 > yaw0 ? 24 : 1;
        for (int it = 0; it < 32; it++) {
            float dist = (lo + hi) / 2;
            boolean fits = true;
            for (int k = 0; k < steps && fits; k++) {
                c.yaw = yaw0 + (yaw1 - yaw0) * k / steps;
                c.distance = dist;
                c.update(1f);
                for (int i = 0; i < 8 && fits; i++) {
                    p.set(b[(i & 1) == 0 ? 0 : 3], b[(i & 2) == 0 ? 1 : 4], b[(i & 4) == 0 ? 2 : 5]);
                    c.view().transformPosition(p);
                    if (-p.z <= 0.01f || Math.abs(p.x) / -p.z > t || Math.abs(p.y) / -p.z > t) fits = false;
                }
            }
            if (fits) hi = dist; else lo = dist;
        }
        return hi;
    }

    /** O estilo da E com luz de estúdio, sem vinheta nem brilho (o painel é quem dá o fundo). */
    private static StyleProfile style() {
        StyleProfile st = StyleProfile.of(4, 0);
        st.vignette = 0f;
        st.bloom = 0f;
        st.outlineFadeStart = 50f;
        st.outlineFadeEnd = 60f;
        return st;
    }

    /**
     * Uma vista do modelo no retângulo (vx, vy, size, size) do alvo. A câmera e a luz ficam paradas (3/4 de frente, luz de
     * estúdio) e o MODELO gira {@code yaw} (rad) em torno do próprio eixo, como num prato giratório: o lado que a câmera vê
     * está sempre iluminado do mesmo jeito.
     */
    private void draw(String name, Framebuffer target, int vx, int vy, int size, float yaw, boolean turning) {
        if (renderer == null) renderer = new Renderer();
        Model m = model(name);
        Renderer.Frame f = new Renderer.Frame();
        Camera c = new Camera();
        c.fovDeg = FOV;
        c.yaw = STILL_YAW;
        c.pitch = PITCH;
        c.target.set(m.cx, m.cy, m.cz);
        c.distance = turning ? m.turning : m.still;
        c.near = 0.02f;
        c.far = 20f;
        c.update(1f);
        f.camera = c;
        f.style = style();
        f.time = 0f;
        f.toggles = new Renderer.Toggles();
        f.toggles.studio = true;
        f.toggles.alpha = true;
        f.toggles.fog = false;
        f.add(m.mesh, new Matrix4f().translate(m.cx, 0, m.cz).rotateY(STILL_YAW - yaw).translate(-m.cx, 0, -m.cz));
        f.shadowFocus.set(m.cx, m.cy, m.cz);
        f.shadowRadius = (m.box[3] - m.box[0] + m.box[4] - m.box[1] + m.box[5] - m.box[2]) * 0.6f;
        renderer.render(f, target, vx, vy, size, size, 2f);
    }

    /** Thread do GL: o atlas com todos os modelos (uma vez). */
    void ensureAtlas() {
        if (atlas != null || names.isEmpty()) return;
        long t0 = System.nanoTime();
        int rows = (names.size() + COLS - 1) / COLS;
        atlas = new Framebuffer(COLS * CELL, rows * CELL, false, GL_RGBA8);
        atlas.bind();
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        for (int i = 0; i < names.size(); i++) {
            int col = i % COLS, row = i / COLS;
            draw(names.get(i), atlas, col * CELL, row * CELL, CELL, STILL_YAW, false);
            atlasRenders++;
        }
        atlasNanos = System.nanoTime() - t0;
    }

    /** Textura do atlas (0 antes de {@link #ensureAtlas}). */
    int atlasTexture() { return atlas == null ? 0 : atlas.color[0]; }

    /**
     * Coordenadas do modelo no atlas para o {@code UiBatch} ({u0, v0, u1, v1}; o FBO tem a origem embaixo, então v vai de cima
     * para baixo invertido), ou {@code null} se o modelo não existe.
     */
    float[] uv(String name) {
        int i = names.indexOf(name);
        if (i < 0 || atlas == null) return null;
        int col = i % COLS, row = i / COLS;
        float u0 = (float) col * CELL / atlas.width, u1 = (float) (col + 1) * CELL / atlas.width;
        float vb = (float) row * CELL / atlas.height, vt = (float) (row + 1) * CELL / atlas.height;
        return new float[]{u0, vt, u1, vb};
    }

    /** Thread do GL: a prévia do modelo girando ({@code angle} em rad). Não refaz se nada mudou. */
    void renderPreview(String name, float angle) {
        if (!has(name)) return;
        if (name.equals(previewModel) && angle == previewAngle) return;
        if (preview == null) preview = new Framebuffer(PREVIEW, PREVIEW, false, GL_RGBA8);
        draw(name, preview, 0, 0, PREVIEW, angle, true);
        previewModel = name;
        previewAngle = angle;
        previewRenders++;
    }

    int previewTexture() { return preview == null ? 0 : preview.color[0]; }

    /** Modelos com malha viva (QA de recursos). */
    int meshes() { return models.size(); }

    /** Nomes dos modelos, na ordem do manifesto. */
    List<String> names() { return List.copyOf(names); }

    /** Lê o atlas (RGBA, com o alfa de verdade) — prancha de revisão e testes de roteiro. */
    java.awt.image.BufferedImage readAtlas() {
        ensureAtlas();
        if (atlas == null) return null;
        atlas.bind();
        int w = atlas.width, h = atlas.height;
        java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = (x + (h - 1 - y) * w) * 4;
                img.setRGB(x, y, ((buf.get(i + 3) & 255) << 24) | ((buf.get(i) & 255) << 16) | ((buf.get(i + 1) & 255) << 8) | (buf.get(i + 2) & 255));
            }
        return img;
    }

    /**
     * Prancha de revisão dos modelos: cada item sobre o fundo do painel (tinta translúcida sobre um chão claro e um escuro),
     * sobre o pergaminho, em silhueta preta e nos tamanhos pequenos da tela (o espaço da grade em 720p, o cartão).
     */
    void sheet(Path file) {
        var a = readAtlas();
        if (a == null) throw new IllegalStateException("sem modelos de item");
        int n = names.size(), cw = CELL, pad = 12;
        String[] rows = {"painel sobre grama", "painel sobre pedra", "pergaminho", "silhueta", "64 e 40 px"};
        int[] bg = {0x6f9a4a, 0xa7a3a0, 0xf3e6c8, 0xf3e6c8, 0x2a201a};
        var out = new java.awt.image.BufferedImage(n * (cw + pad) + pad + 180, rows.length * (cw + pad) + pad, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setColor(new java.awt.Color(0x2a201a));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int r = 0; r < rows.length; r++) {
            int y = pad + r * (cw + pad);
            g.setColor(new java.awt.Color(0xf3e6c8));
            g.drawString(rows[r], 10, y + cw / 2);
            for (int i = 0; i < n; i++) {
                int x = 180 + pad + i * (cw + pad), col = i % COLS, row = i / COLS;
                var cell = a.getSubimage(col * CELL, a.getHeight() - (row + 1) * CELL, CELL, CELL);   // linha 0 do FBO = embaixo
                g.setColor(new java.awt.Color(bg[r]));
                g.fillRect(x, y, cw, cw);
                if (r < 2) {                                              // o painel translúcido por cima do chão
                    g.setColor(new java.awt.Color(0x1c, 0x14, 0x10, 190));
                    g.fillRect(x, y, cw, cw);
                }
                if (r == 3) {
                    var sil = new java.awt.image.BufferedImage(CELL, CELL, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int yy = 0; yy < CELL; yy++)
                        for (int xx = 0; xx < CELL; xx++) sil.setRGB(xx, yy, ((cell.getRGB(xx, yy) >>> 24) << 24) | 0x1a1210);
                    g.drawImage(sil, x, y, null);
                } else if (r == 4) {
                    g.drawImage(cell, x, y, 64, 64, null);
                    g.drawImage(cell, x + 80, y + 12, 40, 40, null);
                } else g.drawImage(cell, x, y, null);
            }
        }
        g.dispose();
        vx.engine.Screenshot.save(out, file);
    }

    @Override
    public void close() {
        for (Model m : models.values()) m.mesh.close();
        models.clear();
        if (atlas != null) atlas.close();
        if (preview != null) preview.close();
        atlas = preview = null;
        if (renderer != null) renderer.close();
        renderer = null;
    }
}
