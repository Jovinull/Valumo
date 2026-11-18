package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import vx.engine.Mesh;
import vx.engine.Texture;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;
import vx.engine.VoxelSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cenário de validação ("clareira") de um estilo.
 *
 * <p>O relevo é uma função analítica determinística, igual para as quatro direções; o que muda por
 * estilo é a resolução do voxel de terreno, a paleta e os modelos. A disposição dos elementos vem de
 * {@code assets/scenes/clearing.json}, também compartilhada — assim as direções são comparadas sob a
 * mesma composição.
 */
public final class World {
    public static final float SIZE = 48f, Y_MIN = -3f, Y_MAX = 7f, WATER_LEVEL = -0.42f;

    /** Paleta e resolução do terreno de um estilo. */
    public record TerrainStyle(float voxel, int[] grass, int[] forest, int[] path, int[] sand, int dirt, int rock,
                               int water, float jitter) {
        public static TerrainStyle of(String key) {
            return switch (key) {
                case "b" -> new TerrainStyle(0.25f,
                        new int[]{0x92c264, 0x84b658, 0xa6cf74}, new int[]{0x6f9f58, 0x66944f},
                        new int[]{0xd9b98a, 0xcfae7f}, new int[]{0xead9a8, 0xe0cd9a}, 0xb08660, 0x9a9590, 0x6fb7c9, 0.02f);
                case "c" -> new TerrainStyle(0.5f,
                        new int[]{0x6aa84f, 0x64a04a, 0x72b257}, new int[]{0x4f8a42, 0x49823d},
                        new int[]{0xb9935f, 0xb08a57}, new int[]{0xdcc88e, 0xd4bf84}, 0x8a5f3c, 0x848890, 0x3f8fd0, 0f);
                // E: voxel de 1/8 u (degrau ~1/9 da altura do herói), sem jitter (cores em manchas contínuas,
                // que a fusão de faces aproveita) e paleta um pouco mais saturada que a B (B lavava no panorama).
                case "e" -> new TerrainStyle(0.125f,
                        new int[]{0x7eab58, 0x72a051, 0x8bb65f}, new int[]{0x66924f, 0x5e8949},
                        new int[]{0xdcbd8c, 0xd3b283}, new int[]{0xecd8a4, 0xe4cf98}, 0xa47a58, 0xa89da0, 0x63b2c6, 0f);
                case "d" -> new TerrainStyle(0.125f,
                        new int[]{0x63ad4e, 0x5aa347, 0x70b85a}, new int[]{0x3f8a45, 0x38803f},
                        new int[]{0xc2a06e, 0xb89565}, new int[]{0xe2d2a0, 0xd8c794}, 0x86603f, 0x7f8792, 0x3a9ad6, 0.035f);
                default -> new TerrainStyle(0.25f,
                        new int[]{0x66b84a, 0x58a842, 0x7cc658}, new int[]{0x469a44, 0x3e8e3d},
                        new int[]{0xcfa368, 0xc4985e}, new int[]{0xeedc9c, 0xe4d190}, 0x93633c, 0x8d939c, 0x3d9be0, 0.03f);
            };
        }
    }

    private record Placed(String name, Mesh mesh, Matrix4f model, Texture texture, float outline) { }
    private record Creature(String name, Actor actor, float x, float z, float yaw, Animator.Clip clip, float phase, float lift) { }

    /** Rodada 08: prop colocado (nome do asset, matriz de mundo e caixa da malha no espaço do modelo). */
    public record Placement(String name, Matrix4f model, float[] bounds) { }

    /** Rodada 08: personagem ou criatura da composição (nome do rig, posição). */
    public record Body(String name, float x, float z, float yaw) { }

    /** Props colocados (para a colisão do jogo; a geometria visual não muda). */
    public List<Placement> placements() {
        List<Placement> out = new ArrayList<>();
        for (Placed p : placed) out.add(new Placement(p.name, new Matrix4f(p.model), p.mesh.bounds.clone()));
        return out;
    }

    /** NPCs e criaturas posicionados na composição. */
    public List<Body> bodies() {
        List<Body> out = new ArrayList<>();
        for (Creature c : creatures) out.add(new Body(c.name, c.x, c.z, c.yaw));
        return out;
    }

    public final String key;
    private final TerrainStyle ts;
    private final Mesh terrain, water;
    private Texture terrainTexture;
    private final List<Placed> placed = new ArrayList<>();
    private final List<Creature> creatures = new ArrayList<>();
    private final List<float[]> clouds = new ArrayList<>();
    private Assets.Prop cloudProp;
    public float heroX = 0f, heroZ = 3f, heroYaw = 0.3f;

    // ------------------------------------------------------------------ relevo (compartilhado)
    static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    static float noise(float x, float z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float a = hash(xi, zi), b = hash(xi + 1, zi), c = hash(xi, zi + 1), d = hash(xi + 1, zi + 1);
        return (a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz) * 2 - 1;
    }

    static float smooth(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static final float[][] PATH = {{-6.5f, -4.6f}, {-5.5f, -2.0f}, {-2.5f, 0.5f}, {0.5f, 2.2f}, {1.8f, 6.5f}, {0.2f, 11f}, {-1.5f, 16f}, {-0.5f, 24f}};
    private static final float POND_X = 7.8f, POND_Z = 4.6f;
    private static final float HOUSE_X = -6.5f, HOUSE_Z = -8.0f;

    private static float pathDistance(float x, float z) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i + 1 < PATH.length; i++) {
            float ax = PATH[i][0], az = PATH[i][1], bx = PATH[i + 1][0], bz = PATH[i + 1][1];
            float dx = bx - ax, dz = bz - az;
            float t = Math.clamp(((x - ax) * dx + (z - az) * dz) / (dx * dx + dz * dz), 0f, 1f);
            float px = ax + dx * t - x, pz = az + dz * t - z;
            best = Math.min(best, (float) Math.sqrt(px * px + pz * pz));
        }
        return best;
    }

    /** Rodada 09.4: centro do lago (fonte posicional da água no jogo). */
    public static float[] pondCenter() { return new float[]{POND_X, WATER_LEVEL, POND_Z}; }

    private static float pondDistance(float x, float z) {
        float dx = x - POND_X, dz = (z - POND_Z) * 1.2f;
        return (float) Math.sqrt(dx * dx + dz * dz) + noise(x * 0.6f, z * 0.6f) * 0.35f;
    }

    /** Altura contínua do terreno em unidades de mundo. */
    public static float height(float x, float z) { return height(x, z, true); }

    /**
     * @param carvePath rebaixa o caminho (A–D). A E não rebaixa: quantizado em 1/8 u o rebaixo vira um
     *                  degrau serrilhado com faces na sombra; a faixa de grama gasta faz a transição.
     */
    public static float height(float x, float z, boolean carvePath) {
        float r = (float) Math.sqrt(x * x + z * z);
        float n = noise(x * 0.11f, z * 0.11f) * 1.1f + noise(x * 0.31f + 7, z * 0.31f + 3) * 0.3f;
        float open = smooth(16f, 8f, r);
        float h = n * (0.18f + 0.82f * (1 - open));
        h += smooth(9f, 23f, -z) * 3.6f;
        h += smooth(13f, 23f, Math.abs(x)) * 2.1f;
        h -= smooth(9f, 24f, z) * 0.9f;
        float dh = (float) Math.hypot(x - HOUSE_X, z - HOUSE_Z);
        h += (0.75f - h) * smooth(7.0f, 3.8f, dh);
        float dp = pondDistance(x, z);
        h += (-1.25f - h) * smooth(4.6f, 1.6f, dp);
        if (carvePath) h -= smooth(0.9f, 0.3f, pathDistance(x, z)) * 0.08f * smooth(3.0f, 5.0f, dp);
        return h;
    }

    // ------------------------------------------------------------------ construção
    private final int n;
    private final int[] top;      // índice de voxel do topo de cada coluna (de terrainGrid)
    /** Colunas do relevo, sem OpenGL (rodada 08: colisão e testes do jogo usam o mesmo chão). */
    public final Terrain terrainGrid;
    private final int[] topColor;

    /** Tratamento de relevo da E (rodada 03): 0 = rodada 02, 1 = voxel refinado, 2 = orgânico, 3 = híbrido,
     *  4 = combinação (patamares do 2 + chanfros do 3). */
    /** Relevo padrão da E. Rodada 03: T1 (limiar com ruído). O T3 (chanfros) segue disponível em
     *  {@code terrain=3}, mas gera "dentes" nas bordas em zigue-zague; ver docs/historico/experimentos/rodada-03-estudos.md. */
    public static final int E_TERRAIN = 1;
    public final int terrainMode;
    private Mesh chamfer;

    public World(Assets assets, StyleProfile style) { this(assets, style, E_TERRAIN); }

    public World(Assets assets, StyleProfile style, int terrainMode) {
        this.key = style.key;
        this.terrainMode = key.equals("e") ? terrainMode : 0;
        this.ts = TerrainStyle.of(key);
        float tv = ts.voxel;
        n = Math.round(SIZE / tv);
        int ny = Math.round((Y_MAX - Y_MIN) / tv);
        terrainGrid = Terrain.of(key, this.terrainMode);       // rodada 08: as mesmas colunas servem à colisão do jogo
        top = terrainGrid.top;
        topColor = new int[n * n];
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                float wx = (x + 0.5f) * tv - SIZE / 2, wz = (z + 0.5f) * tv - SIZE / 2;
                float h = height(wx, wz, !key.equals("e"));
                topColor[x + z * n] = surfaceColor(wx, wz, h, x, z);
            }
        Path tex = assets.root().resolve("textures/" + key + "/terrain.png");
        if (Files.exists(tex)) {
            terrainTexture = Texture.load(tex, true);
            terrain = buildTexturedTerrain(tv, tex.resolveSibling("terrain.json"));
        } else {
            terrain = VoxelMesher.build(new VoxelSource() {
                public int sizeX() { return n; }
                public int sizeY() { return ny; }
                public int sizeZ() { return n; }
                public int get(int x, int y, int z) {
                    if (x < 0 || z < 0 || x >= n || z >= n || y < 0 || y >= ny) return 0;
                    int t = top[x + z * n];
                    if (y > t) return 0;
                    if (y == t) return (1 << 24) | topColor[x + z * n];
                    if ((terrainMode == 2 || terrainMode == 4) && y >= t - 4) {
                        // rupturas do relevo orgânico: face de pedra em faixas largas (2 tons), sem ruído
                        int drop = t - minNeighbor(x, z);
                        if (drop >= 2) return (1 << 24) | (((y + (x + z) / 3) % 3 == 0) ? E_ROCK_D : E_ROCK);
                    }
                    float j = 1f + (hash(x * 7 + y * 13, z * 5 - y * 3) - 0.5f) * 2f * ts.jitter;
                    return (1 << 24) | scale(y >= t - Math.round(0.75f / tv) ? ts.dirt : ts.rock, j);
                }
            }, tv, -n / 2f, Y_MIN / tv, -n / 2f);
            if (terrainMode == 3 || terrainMode == 4) chamfer = buildChamfer(tv);
        }
        water = buildWater();
        loadLayout(assets, style);
    }

    private static final int E_ROCK = 0xa99c98, E_ROCK_D = 0x93868a, E_LIP = 0x9cc46a;

    private int minNeighbor(int x, int z) {
        int m = top[x + z * n];
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int t = topAt(x + d[0], z + d[1]);
            if (t >= 0) m = Math.min(m, t);
        }
        return m;
    }

    /**
     * Altura (em voxels) do topo de uma coluna. T0: degrau a cada 1/8 u, seguindo curvas de nível.
     * T1: o limiar de cada degrau oscila com ruído suave de baixa frequência (±0,45 voxel), então as
     * bordas deixam de ser curvas de nível perfeitas sem virar ruído. T2: patamares largos de 0,4 u e
     * rupturas curtas (o relevo sobe em "saltos" de pedra entre áreas planas). T3: alturas do T1.
     */
    static int quantize(int terrainMode, float h, float wx, float wz, float tv) {
        float base = (h - Y_MIN) / tv;
        if (terrainMode == 0) return (int) Math.floor(base);
        float wob = noise(wx * 0.35f + 31, wz * 0.35f - 17) * 0.45f + noise(wx * 1.1f, wz * 1.1f) * 0.12f;
        if (terrainMode == 2 || terrainMode == 4) {
            float T = 0.4f, u = (h - Y_MIN) / T + 0.30f * noise(wx * 0.3f - 5, wz * 0.3f + 9);
            float fl = (float) Math.floor(u), fr = u - fl;
            float hh = (fl + smooth(0.62f, 0.98f, fr)) * T;   // plano por ~60% do intervalo, depois sobe
            return (int) Math.floor(hh / tv + wob * 0.4f);
        }
        return (int) Math.floor(base + wob);
    }

    /**
     * T3 — chanfros: cada degrau de 1 voxel ganha uma rampa (0,6 voxel de largura) apoiada no topo da
     * coluna mais baixa, que cobre a face vertical. Rampas vizinhas na mesma linha viram um quad só.
     * Degraus maiores continuam verticais (voxel evidente); topos continuam planos e fundidos.
     */
    /** Trecho reto mínimo (em colunas) para ganhar rampa; zigue-zagues curtos ficam verticais. */
    static int CHAMFER_MIN_RUN = Integer.getInteger("vx.chamferMinRun", 3);

    private Mesh buildChamfer(float tv) {
        float[] buf = new float[1 << 16];
        int k = 0;
        float w = 0.6f * tv;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            boolean alongZ = d[0] != 0;                   // borda perpendicular a X percorre Z
            for (int a = 0; a < n; a++) {
                int runStart = -1, runT = 0, runC = 0;
                for (int b = 0; b <= n; b++) {
                    int x = alongZ ? a : b, z = alongZ ? b : a;
                    boolean ok = false;
                    int t = 0, c = 0;
                    if (b < n) {
                        t = top[x + z * n];
                        int nt = topAt(x + d[0], z + d[1]);
                        ok = nt == t - 1;
                        c = topColor[x + z * n];
                    }
                    if (ok && runStart >= 0 && t == runT && c == runC) continue;
                    if (runStart >= 0 && b - runStart >= CHAMFER_MIN_RUN) {
                        if (buf.length < k + 18 * Mesh.SCENE_STRIDE) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                        k = chamferQuad(buf, k, d, alongZ, a, runStart, b, runT, runC, tv, w);
                        runStart = -1;
                    }
                    runStart = -1;
                    if (ok) { runStart = b; runT = t; runC = c; }
                }
            }
        }
        // quinas externas: quando as duas vizinhas perpendiculares estão 1 voxel abaixo, as rampas de X e
        // de Z deixam um vão no canto (aparecia como "dente" nos degraus em diagonal); um triângulo
        // inclinado do topo da quina até as bases das duas rampas dobra o chanfro na esquina
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                int t = top[x + z * n];
                for (int dx = -1; dx <= 1; dx += 2)
                    for (int dz = -1; dz <= 1; dz += 2) {
                        if (CHAMFER_MIN_RUN > 1 || topAt(x + dx, z) != t - 1 || topAt(x, z + dz) != t - 1) continue;
                        if (buf.length < k + 6 * Mesh.SCENE_STRIDE) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                        float cx = (x - n / 2f + (dx > 0 ? 1 : 0)) * tv, cz = (z - n / 2f + (dz > 0 ? 1 : 0)) * tv;
                        float yTop = (t + 1) * tv + Y_MIN, yLow = yTop - tv;
                        float[][] tri = {{cx, yTop, cz}, {cx + dx * w, yLow, cz}, {cx, yLow, cz + dz * w}};
                        float len = (float) Math.sqrt(2 * tv * tv + w * w);
                        int col = scale(topColor[x + z * n], 0.93f);
                        for (int i : new int[]{0, 1, 2, 0, 2, 1})
                            k = vert(buf, k, tri[i], dx * tv / len, w / len, dz * tv / len, col, i == 0 ? 1f : 0.82f, 0, 0);
                    }
            }
        return new Mesh(buf, k, Mesh.SCENE_LAYOUT);
    }

    private int chamferQuad(float[] buf, int k, int[] d, boolean alongZ, int a, int b0, int b1, int t, int c, float tv, float w) {
        float yTop = (t + 1) * tv + Y_MIN, yLow = yTop - tv;
        // borda entre a coluna (alta) e a vizinha (baixa), em coordenadas de mundo
        float edge = alongZ ? (a - n / 2f + (d[0] > 0 ? 1 : 0)) * tv : (a - n / 2f + (d[1] > 0 ? 1 : 0)) * tv;
        float s0 = (b0 - n / 2f) * tv, s1 = (b1 - n / 2f) * tv;
        float sg = alongZ ? d[0] : d[1];
        float out = edge + sg * w;
        float[][] p;
        if (alongZ) p = new float[][]{{edge, yTop, s0}, {edge, yTop, s1}, {out, yLow, s1}, {out, yLow, s0}};
        else p = new float[][]{{s0, yTop, edge}, {s1, yTop, edge}, {s1, yLow, out}, {s0, yLow, out}};
        float len = (float) Math.sqrt(tv * tv + w * w);
        float nh = tv / len, nv = w / len;
        float nx = alongZ ? sg * nh : 0, nz = alongZ ? 0 : sg * nh;
        int col = scale(c, 0.93f);
        float[] ao = {1f, 1f, 0.82f, 0.82f};
        // ordem anti-horária vista de fora (normal para cima e para fora)
        int[] order = (sg > 0) == alongZ ? new int[]{0, 1, 2, 0, 2, 3} : new int[]{0, 2, 1, 0, 3, 2};
        for (int i : order) k = vert(buf, k, p[i], nx, nv, nz, col, ao[i], 0, 0);
        // tampas triangulares nas duas pontas da rampa (sem elas o lado aberto da cunha vira um
        // triângulo escuro nos degraus do caminho); emitidas nos dois sentidos, a de trás é descartada
        int side = scale(c, 0.88f);
        for (int end = 0; end < 2; end++) {
            float sv = end == 0 ? s0 : s1, sgn = end == 0 ? -1 : 1;
            float[][] tri = alongZ
                    ? new float[][]{{edge, yTop, sv}, {out, yLow, sv}, {edge, yLow, sv}}
                    : new float[][]{{sv, yTop, edge}, {sv, yLow, out}, {sv, yLow, edge}};
            float cx = alongZ ? 0 : sgn, cz = alongZ ? sgn : 0;
            for (int i : new int[]{0, 1, 2, 0, 2, 1}) k = vert(buf, k, tri[i], cx, 0, cz, side, 0.9f, 0, 0);
        }
        return k;
    }

    private static int scale(int rgb, float f) {
        int r = Math.clamp(Math.round(((rgb >> 16) & 0xFF) * f), 0, 255);
        int g = Math.clamp(Math.round(((rgb >> 8) & 0xFF) * f), 0, 255);
        int b = Math.clamp(Math.round((rgb & 0xFF) * f), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    /** Superfícies do relevo (índices de {@link #surfaceAt}). */
    public static final int SURF_GRASS = 0, SURF_FOREST = 1, SURF_PATH = 2, SURF_SAND = 3, SURF_ROCK = 4;

    /**
     * Rodada 09.4 — classe de superfície no ponto do relevo da E (a mesma que escolhe a cor do chão): 0 grama,
     * 1 floresta, 2 caminho, 3 areia, 4 rocha. Gancho genérico para o jogo (som de passo), sem regra de jogo.
     */
    public static int surfaceAt(float x, float z) { return surfaceClass(x, z, height(x, z, false)); }

    /** Classe de superfície: 0 grama, 1 floresta, 2 caminho, 3 areia, 4 rocha. */
    private static int surfaceClass(float wx, float wz, float h) {
        float dp = pondDistance(wx, wz);
        if (h < WATER_LEVEL + 0.22f && dp < 6.5f) return 3;
        float pd = pathDistance(wx, wz) + noise(wx * 1.3f, wz * 1.3f) * 0.18f;
        if (pd < 0.62f) return 2;
        float slope = Math.abs(height(wx + 0.4f, wz) - height(wx - 0.4f, wz)) + Math.abs(height(wx, wz + 0.4f) - height(wx, wz - 0.4f));
        if (slope > 1.05f) return 4;
        float r = (float) Math.sqrt(wx * wx + wz * wz);
        if (r + noise(wx * 0.25f, wz * 0.25f) * 2.5f > 14.5f) return 1;
        return 0;
    }

    /** Gramado gasto na borda do caminho (só E): uma faixa de transição em vez de borda serrilhada. */
    private static final int E_WORN = 0xb8bd7c, E_WORN2 = 0xc9bc86, E_DRY = 0x9fbf66;

    private int surfaceColor(float wx, float wz, float h, int x, int z) {
        int cls = surfaceClass(wx, wz, h);
        if (key.equals("e")) {
            float patch = noise(wx * 0.21f + 11, wz * 0.21f - 5) * 0.8f + noise(wx * 0.55f - 3, wz * 0.55f + 8) * 0.35f;
            if (cls == 0 || cls == 1) {
                float pd = pathDistance(wx, wz) + noise(wx * 1.3f, wz * 1.3f) * 0.18f;
                if (pd < 0.80f) return pd < 0.70f ? E_WORN2 : E_WORN;
            }
            if (cls == 2) return ts.path[noise(wx * 0.9f, wz * 0.9f) > 0.35f ? 1 : 0];
            if (cls == 0) return patch > 0.42f ? E_DRY : ts.grass[patch > 0.12f ? 2 : (patch < -0.30f ? 1 : 0)];
            if (cls == 1) return ts.forest[patch > 0.05f ? 1 : 0];
        }
        float patch = noise(wx * 0.33f + 11, wz * 0.33f - 5) * 0.75f + noise(wx * 0.9f - 3, wz * 0.9f + 8) * 0.45f;
        float j = 1f + (hash(x, z) - 0.5f) * 2f * ts.jitter;
        int c = switch (cls) {
            case 1 -> ts.forest[patch > 0.1f ? 1 : 0];
            case 2 -> ts.path[hash(x * 3, z * 3) > 0.6f ? 1 : 0];
            case 3 -> ts.sand[hash(x * 5, z * 2) > 0.7f ? 1 : 0];
            case 4 -> ts.rock;
            default -> ts.grass[patch > 0.28f ? 2 : (patch < -0.25f ? 1 : 0)];
        };
        return scale(c, j);
    }

    /** Terreno em blocos texturizados (direção C): tiles de 16 px em textures/c/terrain.png (faixa horizontal). */
    private Mesh buildTexturedTerrain(float tv, Path json) {
        JsonObject doc;
        try {
            doc = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("terrain.json ausente para o estilo " + key, e);
        }
        JsonObject tiles = doc.getAsJsonObject("tiles");
        String[] topNames = {"grass_top", "forest_top", "path", "sand", "rock"};
        int[][] topTiles = new int[5][];
        for (int i = 0; i < 5; i++) topTiles[i] = ints(tiles.getAsJsonArray(topNames[i]));
        int[] grassSide = ints(tiles.getAsJsonArray("grass_side")), forestSide = ints(tiles.getAsJsonArray("forest_side"));
        int[] dirt = ints(tiles.getAsJsonArray("dirt")), rock = ints(tiles.getAsJsonArray("rock"));
        int[] sandT = ints(tiles.getAsJsonArray("sand")), pathT = ints(tiles.getAsJsonArray("path"));
        float tw = (float) doc.get("tile").getAsInt() / terrainTexture.width;
        float[] buf = new float[n * n * 6 * Mesh.SCENE_STRIDE * 3];
        int k = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                int t = top[x + z * n];
                float wx = (x + 0.5f) * tv - SIZE / 2, wz = (z + 0.5f) * tv - SIZE / 2;
                int cls = surfaceClass(wx, wz, height(wx, wz));
                float x0 = (x - n / 2f) * tv, z0 = (z - n / 2f) * tv, y1 = (t + 1) * tv + Y_MIN;
                int[] tt = topTiles[cls];
                int tile = tt[(int) (hash(x * 3 + 1, z * 7 + 2) * tt.length) % tt.length];
                int c = topColor[x + z * n];
                float[] ao = new float[4];
                int[][] corner = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                for (int i = 0; i < 4; i++) {
                    int sxn = corner[i][0] == 1 ? 1 : -1, szn = corner[i][1] == 1 ? 1 : -1;
                    boolean a = topAt(x + sxn, z) > t, b = topAt(x, z + szn) > t, d = topAt(x + sxn, z + szn) > t;
                    ao[i] = 1f - ((a && b) ? 3 : (a ? 1 : 0) + (b ? 1 : 0) + (d ? 1 : 0)) / 3f;
                }
                if (buf.length < k + 5 * 6 * Mesh.SCENE_STRIDE * 8) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                // topo: (x0,z0) (x1,z0) (x1,z1) (x0,z1)
                float[][] p = {{x0, y1, z0}, {x0 + tv, y1, z0}, {x0 + tv, y1, z0 + tv}, {x0, y1, z0 + tv}};
                float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                for (int i : new int[]{0, 3, 2, 0, 2, 1})
                    k = vert(buf, k, p[i], 0, 1, 0, c, ao[i], (tile + uv[i][0]) * tw, uv[i][1]);
                // laterais: desce até o topo do vizinho
                for (int[] d : dirs) {
                    int nt = topAt(x + d[0], z + d[1]);
                    for (int y = t; y > nt; y--) {
                        int[] set = y == t ? (cls == 0 ? grassSide : cls == 1 ? forestSide : cls == 2 ? pathT : cls == 3 ? sandT : rock)
                                : (y >= t - 1 ? dirt : rock);
                        int st = set[(int) (hash(x * 5 + y, z * 3 - y) * set.length) % set.length];
                        float ya = y * tv + Y_MIN, yb = ya + tv;
                        float[][] q;
                        if (d[0] == 1) q = new float[][]{{x0 + tv, yb, z0 + tv}, {x0 + tv, yb, z0}, {x0 + tv, ya, z0}, {x0 + tv, ya, z0 + tv}};
                        else if (d[0] == -1) q = new float[][]{{x0, yb, z0}, {x0, yb, z0 + tv}, {x0, ya, z0 + tv}, {x0, ya, z0}};
                        else if (d[1] == 1) q = new float[][]{{x0, yb, z0 + tv}, {x0 + tv, yb, z0 + tv}, {x0 + tv, ya, z0 + tv}, {x0, ya, z0 + tv}};
                        else q = new float[][]{{x0 + tv, yb, z0}, {x0, yb, z0}, {x0, ya, z0}, {x0 + tv, ya, z0}};
                        float sideAo = y == nt + 1 ? 0.55f : 1f;
                        float[] aos = {1f, 1f, sideAo, sideAo};
                        int sc = y == t ? c : (y >= t - 1 ? ts.dirt : ts.rock);
                        if (buf.length < k + 18 * Mesh.SCENE_STRIDE) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                        for (int i : new int[]{0, 3, 2, 0, 2, 1})
                            k = vert(buf, k, q[i], d[0], 0, d[1], sc, aos[i], (st + uv[i][0]) * tw, uv[i][1]);
                    }
                }
            }
        return new Mesh(buf, k, Mesh.SCENE_LAYOUT);
    }

    private static int[] ints(JsonArray a) {
        int[] o = new int[a.size()];
        for (int i = 0; i < o.length; i++) o[i] = a.get(i).getAsInt();
        return o;
    }

    private int topAt(int x, int z) {
        if (x < 0 || z < 0 || x >= n || z >= n) return -1;
        return top[x + z * n];
    }

    private static int vert(float[] b, int k, float[] p, float nx, float ny, float nz, int rgb, float ao, float u, float v) {
        b[k++] = p[0]; b[k++] = p[1]; b[k++] = p[2];
        b[k++] = nx; b[k++] = ny; b[k++] = nz;
        b[k++] = ((rgb >> 16) & 0xFF) / 255f; b[k++] = ((rgb >> 8) & 0xFF) / 255f; b[k++] = (rgb & 0xFF) / 255f;
        b[k++] = ao; b[k++] = 0f;
        b[k++] = u; b[k++] = v;
        return k;
    }

    private Mesh buildWater() {
        float x0 = POND_X - 6.5f, x1 = POND_X + 6.5f, z0 = POND_Z - 6f, z1 = POND_Z + 6f, y = WATER_LEVEL;
        float[][] p = {{x0, y, z0}, {x1, y, z0}, {x1, y, z1}, {x0, y, z1}};
        float[] buf = new float[6 * Mesh.SCENE_STRIDE];
        int k = 0;
        for (int i : new int[]{0, 3, 2, 0, 2, 1}) {
            k = vert(buf, k, p[i], 0, 1, 0, ts.water, 1f, 0, 0);
            buf[k - 3] = VoxModel.MAT_WATER;
        }
        return new Mesh(buf, k, Mesh.SCENE_LAYOUT);
    }

    /** Altura do chão (topo do voxel) no ponto, em unidades de mundo. */
    public float groundAt(float x, float z) { return terrainGrid.groundAt(x, z); }

    /** Altura suavizada (média de vizinhos) para o personagem não "pular" entre degraus. */
    public float groundSmooth(float x, float z) { return terrainGrid.groundSmooth(x, z); }

    /** Rodada 06: composição alternativa (assets/scenes/&lt;layout&gt;.json), ex.: a vitrine da expansão.
     *  null = a clareira de sempre (clearing_&lt;estilo&gt;.json); a cena canônica não muda. */
    public static String layout = null;

    private void loadLayout(Assets assets, StyleProfile style) {
        Map<String, Assets.Prop> props = assets.props(key);
        JsonObject doc;
        try {
            // Cada direção pode ter a sua composição (clearing_<estilo>.json); senão usa a compartilhada.
            Path own = assets.root().resolve(layout != null ? "scenes/" + layout + ".json" : "scenes/clearing_" + key + ".json");
            doc = JsonParser.parseString(Files.readString(Files.exists(own) ? own : assets.root().resolve("scenes/clearing.json"))).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("assets/scenes/clearing.json ausente", e);
        }
        JsonArray hero = doc.getAsJsonArray("hero");
        heroX = hero.get(0).getAsFloat();
        heroZ = hero.get(1).getAsFloat();
        heroYaw = (float) Math.toRadians(hero.get(2).getAsFloat());

        for (JsonElement el : doc.getAsJsonArray("props")) {
            JsonObject o = el.getAsJsonObject();
            place(props, o.get("n").getAsString(), o.get("x").getAsFloat(), o.get("z").getAsFloat(),
                    o.has("r") ? o.get("r").getAsFloat() : 0f, o.has("dy") ? o.get("dy").getAsFloat() : 0f,
                    o.has("abs") && o.get("abs").getAsBoolean());
        }
        // O espalhamento procedural não pode cair sobre elementos posicionados à mão nem sobre criaturas.
        List<float[]> taken = new ArrayList<>();
        for (JsonElement el : doc.getAsJsonArray("props")) {
            JsonObject o = el.getAsJsonObject();
            Assets.Prop p = props.get(o.get("n").getAsString());
            taken.add(new float[]{o.get("x").getAsFloat(), o.get("z").getAsFloat(), p == null ? 0.8f : Math.clamp(p.width() * 0.45f, 0.5f, 2.2f)});
        }
        if (doc.has("creatures")) for (JsonElement el : doc.getAsJsonArray("creatures")) {
            JsonObject o = el.getAsJsonObject();
            taken.add(new float[]{o.get("x").getAsFloat(), o.get("z").getAsFloat(), o.has("clear") ? o.get("clear").getAsFloat() : 0.9f});
        }
        taken.add(new float[]{heroX, heroZ, 1.0f});
        for (JsonElement el : doc.getAsJsonArray("scatter")) {
            JsonObject o = el.getAsJsonObject();
            JsonArray names = o.getAsJsonArray("props");
            Random rnd = new Random(o.get("seed").getAsLong());
            float r0 = o.getAsJsonArray("ring").get(0).getAsFloat(), r1 = o.getAsJsonArray("ring").get(1).getAsFloat();
            float minDist = o.get("minDist").getAsFloat();
            boolean avoidPath = !o.has("onPath");
            int count = o.get("count").getAsInt(), tries = 0, done = 0;
            while (done < count && tries++ < count * 40) {
                double ang = rnd.nextDouble() * Math.PI * 2, rad = Math.sqrt(r0 * r0 + rnd.nextDouble() * (r1 * r1 - r0 * r0));
                float x = (float) (Math.cos(ang) * rad), z = (float) (Math.sin(ang) * rad);
                String name = names.get(rnd.nextInt(names.size())).getAsString();
                int rot = rnd.nextInt(4) * 90;
                if (Math.abs(x) > SIZE / 2 - 1.5f || Math.abs(z) > SIZE / 2 - 1.5f) continue;
                if (avoidPath && pathDistance(x, z) < 1.3f) continue;
                if (pondDistance(x, z) < 4.9f) continue;
                if (Math.hypot(x - HOUSE_X, z - HOUSE_Z) < 4.6f) continue;
                if (o.has("frontGap") && z > 6 && Math.abs(x) < o.get("frontGap").getAsFloat()) continue;
                boolean ok = true;
                for (float[] t : taken) if (Math.hypot(t[0] - x, t[1] - z) < Math.max(minDist, t[2])) { ok = false; break; }
                if (!ok) continue;
                if (minDist > 0) taken.add(new float[]{x, z, minDist});
                place(props, name, x, z, rot, 0f, false);
                done++;
                // Touceiras (rodada 02): "cluster": n coloca n-1 vizinhos do mesmo tipo a até "clusterR" u;
                // plantas pequenas em grupos deixam áreas de descanso entre eles.
                if (o.has("cluster")) {
                    int extra = o.get("cluster").getAsInt() - 1;
                    float cr = o.has("clusterR") ? o.get("clusterR").getAsFloat() : 0.8f;
                    for (int c = 0; c < extra; c++) {
                        double a2 = rnd.nextDouble() * Math.PI * 2, r2 = cr * (0.35 + 0.65 * rnd.nextDouble());
                        float cx = x + (float) (Math.cos(a2) * r2), cz = z + (float) (Math.sin(a2) * r2);
                        String n2 = rnd.nextFloat() < 0.75f ? name : names.get(rnd.nextInt(names.size())).getAsString();
                        if (avoidPath && pathDistance(cx, cz) < 1.0f) continue;
                        if (pondDistance(cx, cz) < 4.6f) continue;
                        place(props, n2, cx, cz, rnd.nextInt(4) * 90, 0f, false);
                    }
                }
            }
        }
        if (doc.has("creatures")) for (JsonElement el : doc.getAsJsonArray("creatures")) {
            JsonObject o = el.getAsJsonObject();
            String dir = "creatures/" + key + "/" + o.get("n").getAsString();
            if (!assets.exists(dir)) continue;
            creatures.add(new Creature(dir, new Actor(assets.rig(dir), style.animExaggeration),
                    o.get("x").getAsFloat(), o.get("z").getAsFloat(), (float) Math.toRadians(o.get("r").getAsFloat()),
                    Animator.Clip.valueOf(o.get("clip").getAsString().toUpperCase()), creatures.size() * 0.37f,
                    o.has("lift") ? o.get("lift").getAsFloat() : 0f));
        }
        // rodada 06: NPCs/personagens posicionados na composição (caminho do rig), animados como as criaturas
        if (doc.has("npcs")) for (JsonElement el : doc.getAsJsonArray("npcs")) {
            JsonObject o = el.getAsJsonObject();
            String dir = o.get("rig").getAsString();
            if (!assets.exists(dir)) continue;
            creatures.add(new Creature(dir, new Actor(assets.rig(dir), style.animExaggeration),
                    o.get("x").getAsFloat(), o.get("z").getAsFloat(), (float) Math.toRadians(o.get("r").getAsFloat()),
                    Animator.Clip.valueOf(o.get("clip").getAsString().toUpperCase()), creatures.size() * 0.37f,
                    o.has("lift") ? o.get("lift").getAsFloat() : 0f));
        }
        cloudProp = props.get("cloud");
        if (doc.has("clouds")) for (JsonElement el : doc.getAsJsonArray("clouds")) {
            JsonArray a = el.getAsJsonArray();
            clouds.add(new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
        }
    }

    private void place(Map<String, Assets.Prop> props, String name, float x, float z, float rotDeg, float dy, boolean absolute) {
        Assets.Prop p = props.get(name);
        if (p == null) return;
        float y = absolute ? dy : groundAt(x, z) + dy;
        // Plantas pequenas (flores, tufos) não recebem contorno na E: em grupo viram ruído de tinta.
        float ow = key.equals("e") && p.height() < 0.7f ? 0.25f : 1f;
        placed.add(new Placed(name, p.mesh(), new Matrix4f().translate(x, y, z).rotateY((float) Math.toRadians(rotDeg)), p.texture(), ow));
    }

    public void submit(Renderer.Frame f, float time) {
        f.draws.add(new Renderer.Draw(terrain, new Matrix4f(), terrainTexture, false, true).outline(f.style.outlineTerrain).terrain());
        if (chamfer != null) f.draws.add(new Renderer.Draw(chamfer, new Matrix4f(), null, false, true).outline(f.style.outlineTerrain).terrain());
        for (Placed p : placed) f.draws.add(new Renderer.Draw(p.mesh, p.model, p.texture, false, true).outline(p.outline));
        for (Creature c : creatures) {
            Matrix4f m = new Matrix4f().translate(c.x, groundAt(c.x, c.z) + c.lift, c.z).rotateY(c.yaw);
            c.actor.submit(f, m, c.clip, time + c.phase * 5f, 1f);
        }
        if (cloudProp != null) for (float[] c : clouds) {
            float x = ((c[0] + time * 0.25f + 40f) % 80f) - 40f;
            f.draws.add(new Renderer.Draw(cloudProp.mesh(), new Matrix4f().translate(x, c[1], c[2]), cloudProp.texture(), false, false));
        }
        f.draws.add(new Renderer.Draw(water, new Matrix4f(), null, true, false));
    }

    /** Disco de terreno usado como base nos modos de inspeção; dá a escala do voxel de terreno. */
    public static Mesh pedestal(String styleKey, float radius) {
        TerrainStyle t = TerrainStyle.of(styleKey);
        float voxel = Math.min(t.voxel, 0.25f);
        int r = Math.round(radius / voxel), h = Math.max(2, Math.round(0.5f / voxel));
        VoxelSource src = new VoxelSource() {
            public int sizeX() { return 2 * r; }
            public int sizeY() { return h; }
            public int sizeZ() { return 2 * r; }
            public int get(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= 2 * r || y >= h || z >= 2 * r) return 0;
                float dx = x + 0.5f - r, dz = z + 0.5f - r;
                if (dx * dx + dz * dz > (float) r * r) return 0;
                float j = 1f + (hash(x, z) - 0.5f) * 2f * t.jitter;
                int c = y == h - 1 ? t.grass[noise(x * 0.4f, z * 0.4f) > 0.3f ? 2 : 0] : t.dirt;
                return (1 << 24) | scale(c, j);
            }
        };
        return VoxelMesher.build(src, voxel, -r, -h, -r);
    }
}
