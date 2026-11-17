package vx.engine;

/**
 * Converte um {@link VoxelSource} em malha de faces visíveis com oclusão de ambiente por vértice.
 *
 * <p>Dois caminhos:
 * <ul>
 *   <li>{@link #buildData}: um quad por face de voxel (referência, usado nos testes de geometria);</li>
 *   <li>{@link #buildDataMerged}: fusão gulosa <b>exata</b> — só junta faces coplanares vizinhas com a
 *   mesma cor, o mesmo material e AO idêntico nos quatro cantos. Como todos os atributos do quad
 *   fundido são constantes, a imagem é a mesma; só cai o número de triângulos. Folhagem nunca é
 *   fundida (o vento desloca cada vértice e quads grandes abririam frestas).</li>
 * </ul>
 * {@link #build} usa a fusão, salvo com {@code -Dvx.merge=false} (para comparar). Ver ADR 0006.
 */
public final class VoxelMesher {
    private VoxelMesher() { }

    /** Margem (em voxels) com que cada quad fundido avança sobre os vizinhos para cobrir frestas de
     *  T-junction. 0,06 voxel ≈ 1 pixel no terreno a 7 u na câmera de exploração (rodada 03). */
    public static float crackMargin = 0.06f;

    /** Liga a fusão exata de faces em {@link #build}. */
    public static boolean merge = !"false".equals(System.getProperty("vx.merge"));

    // Para cada face: normal, e os 4 cantos (em offsets 0/1) em ordem anti-horária vista de fora.
    private static final int[][] NORMALS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int[][][] CORNERS = {
            {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}},
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
            {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
            {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
    };

    private static final class Buf {
        float[] d = new float[1 << 16];
        int n;

        void put(float... v) {
            if (n + v.length > d.length) d = java.util.Arrays.copyOf(d, Math.max(d.length * 2, n + v.length));
            System.arraycopy(v, 0, d, n, v.length);
            n += v.length;
        }
    }

    /**
     * @param src       volume
     * @param voxelSize aresta do voxel em unidades de mundo
     * @param ox,oy,oz  deslocamento (em voxels) somado a cada posição antes da escala; use para
     *                  posicionar a origem/pivô do modelo
     */
    public static Mesh build(VoxelSource src, float voxelSize, float ox, float oy, float oz) {
        return build(src, voxelSize, ox, oy, oz, false);
    }

    /**
     * @param mergeFoliage funde também a folhagem — só é seguro quando o estilo usa vento afim
     *                     (o asset declara {@code "affineWind": true} no manifesto)
     */
    public static Mesh build(VoxelSource src, float voxelSize, float ox, float oy, float oz, boolean mergeFoliage) {
        return new Mesh(merge ? buildDataMerged(src, voxelSize, ox, oy, oz, mergeFoliage) : buildData(src, voxelSize, ox, oy, oz),
                Mesh.SCENE_LAYOUT);
    }

    /**
     * Fusão gulosa exata. Para cada direção de face e cada fatia, monta uma máscara 2D com a "chave"
     * de cada face visível (cor, material e AO dos quatro cantos); faces com AO não uniforme ou de
     * folhagem são emitidas sozinhas; as demais crescem em retângulos de chave igual.
     */
    public static float[] buildDataMerged(VoxelSource src, float voxelSize, float ox, float oy, float oz) {
        return buildDataMerged(src, voxelSize, ox, oy, oz, false);
    }

    public static float[] buildDataMerged(VoxelSource src, float voxelSize, float ox, float oy, float oz, boolean mergeFoliage) {
        Buf buf = new Buf();
        int[] size = {src.sizeX(), src.sizeY(), src.sizeZ()};
        float[] ao = new float[4];
        int[] p = new int[3];
        for (int f = 0; f < 6; f++) {
            int[] n = NORMALS[f];
            int d = n[0] != 0 ? 0 : (n[1] != 0 ? 1 : 2);
            int u = (d + 1) % 3, v = (d + 2) % 3;
            int su = size[u], sv = size[v];
            long[] mask = new long[su * sv];
            int[] rgbMat = new int[su * sv];
            boolean[] done = new boolean[su * sv];
            for (int s = 0; s < size[d]; s++) {
                java.util.Arrays.fill(mask, 0L);
                java.util.Arrays.fill(done, false);
                for (int j = 0; j < sv; j++)
                    for (int i = 0; i < su; i++) {
                        p[d] = s; p[u] = i; p[v] = j;
                        int val = src.get(p[0], p[1], p[2]);
                        if (val == 0) continue;
                        int nb = src.get(p[0] + n[0], p[1] + n[1], p[2] + n[2]);
                        if (nb != 0 && !(isWater(nb) && !isWater(val))) continue;
                        int[][] c = CORNERS[f];
                        int aoKey = 0;
                        boolean uniform = true;
                        for (int k = 0; k < 4; k++) {
                            ao[k] = vertexAo(src, p[0], p[1], p[2], n, c[k]);
                            int q = Math.round(ao[k] * 3f);
                            aoKey |= q << (k * 2);
                            if (k > 0 && ao[k] != ao[0]) uniform = false;
                        }
                        int mat = ((val >>> 24) & 0xFF) - 1;
                        // bit 41 marca "não fundir": AO variável ou folhagem
                        boolean solo = !uniform || (mat == VoxModel.MAT_FOLIAGE && !mergeFoliage);
                        mask[i + j * su] = 1L << 40 | (solo ? 1L << 41 : 0L) | ((long) aoKey << 32) | (val & 0xFFFFFFFFL);
                        rgbMat[i + j * su] = val;
                    }
                for (int j = 0; j < sv; j++)
                    for (int i = 0; i < su; i++) {
                        int idx = i + j * su;
                        long key = mask[idx];
                        if (key == 0 || done[idx]) continue;
                        int w = 1, h = 1;
                        if ((key & (1L << 41)) == 0) {
                            while (i + w < su && mask[idx + w] == key && !done[idx + w]) w++;
                            grow:
                            while (j + h < sv) {
                                for (int k = 0; k < w; k++) {
                                    int id2 = idx + k + h * su;
                                    if (mask[id2] != key || done[id2]) break grow;
                                }
                                h++;
                            }
                        }
                        for (int b = 0; b < h; b++) for (int a = 0; a < w; a++) done[idx + a + b * su] = true;
                        p[d] = s; p[u] = i; p[v] = j;
                        emitQuad(buf, src, f, d, u, v, p, w, h, rgbMat[idx], voxelSize, ox, oy, oz, ao);
                    }
            }
        }
        return java.util.Arrays.copyOf(buf.d, buf.n);
    }

    private static void emitQuad(Buf buf, VoxelSource src, int f, int d, int u, int v, int[] p, int w, int h, int val,
                                 float vs, float ox, float oy, float oz, float[] ao) {
        int[] n = NORMALS[f];
        int[][] c = CORNERS[f];
        float r = ((val >> 16) & 0xFF) / 255f, g = ((val >> 8) & 0xFF) / 255f, b = (val & 0xFF) / 255f;
        float mat = ((val >>> 24) & 0xFF) - 1;
        for (int k = 0; k < 4; k++) ao[k] = vertexAo(src, p[0], p[1], p[2], n, c[k]);
        int[] order = (ao[0] + ao[2] >= ao[1] + ao[3]) ? new int[]{0, 1, 2, 0, 2, 3} : new int[]{1, 2, 3, 1, 3, 0};
        float[] q = new float[3];
        // Quads fundidos ganham uma margem de 0,01 voxel: cobre as frestas de T-junction (vértice de um
        // quad pequeno no meio da aresta de um grande), vistas como pontos claros no gramado (rodada 03).
        float e = (w > 1 || h > 1) ? crackMargin : 0f;
        for (int k : order) {
            q[d] = p[d] + c[k][d];
            q[u] = p[u] + (c[k][u] == 1 ? w + e : -e);
            q[v] = p[v] + (c[k][v] == 1 ? h + e : -e);
            buf.put((q[0] + ox) * vs, (q[1] + oy) * vs, (q[2] + oz) * vs, n[0], n[1], n[2], r, g, b, ao[k], mat, 0f, 0f);
        }
    }

    /** Só a geometria (sem OpenGL): vértices no layout {@link Mesh#SCENE_LAYOUT}. Testável sem contexto gráfico. */
    public static float[] buildData(VoxelSource src, float voxelSize, float ox, float oy, float oz) {
        Buf buf = new Buf();
        int sx = src.sizeX(), sy = src.sizeY(), sz = src.sizeZ();
        float[] ao = new float[4];
        for (int z = 0; z < sz; z++)
            for (int y = 0; y < sy; y++)
                for (int x = 0; x < sx; x++) {
                    int v = src.get(x, y, z);
                    if (v == 0) continue;
                    float r = ((v >> 16) & 0xFF) / 255f, g = ((v >> 8) & 0xFF) / 255f, b = (v & 0xFF) / 255f;
                    float mat = ((v >>> 24) & 0xFF) - 1;
                    for (int f = 0; f < 6; f++) {
                        int[] n = NORMALS[f];
                        int nb = src.get(x + n[0], y + n[1], z + n[2]);
                        if (nb != 0 && !(isWater(nb) && !isWater(v))) continue;
                        int[][] c = CORNERS[f];
                        for (int i = 0; i < 4; i++) ao[i] = vertexAo(src, x, y, z, n, c[i]);
                        // Vira a diagonal para evitar interpolação assimétrica do AO.
                        int[] order = (ao[0] + ao[2] >= ao[1] + ao[3])
                                ? new int[]{0, 1, 2, 0, 2, 3} : new int[]{1, 2, 3, 1, 3, 0};
                        for (int k : order) {
                            buf.put((x + c[k][0] + ox) * voxelSize, (y + c[k][1] + oy) * voxelSize, (z + c[k][2] + oz) * voxelSize,
                                    n[0], n[1], n[2], r, g, b, ao[k], mat, 0f, 0f);
                        }
                    }
                }
        return java.util.Arrays.copyOf(buf.d, buf.n);
    }

    private static boolean isWater(int v) {
        return ((v >>> 24) & 0xFF) - 1 == VoxModel.MAT_WATER;
    }

    private static boolean solid(VoxelSource s, int x, int y, int z) {
        int v = s.get(x, y, z);
        return v != 0 && !isWater(v);
    }

    /** AO clássico de 3 vizinhos: 1 = totalmente aberto, 0 = canto fechado. */
    private static float vertexAo(VoxelSource s, int x, int y, int z, int[] n, int[] corner) {
        // Célula à frente da face, e os dois eixos tangentes.
        int bx = x + n[0], by = y + n[1], bz = z + n[2];
        int[] t1 = new int[3], t2 = new int[3];
        int ti = 0;
        for (int a = 0; a < 3; a++) {
            if (n[a] != 0) continue;
            int dir = corner[a] == 1 ? 1 : -1;
            if (ti == 0) t1[a] = dir; else t2[a] = dir;
            ti++;
        }
        boolean s1 = solid(s, bx + t1[0], by + t1[1], bz + t1[2]);
        boolean s2 = solid(s, bx + t2[0], by + t2[1], bz + t2[2]);
        boolean cc = solid(s, bx + t1[0] + t2[0], by + t1[1] + t2[1], bz + t1[2] + t2[2]);
        int occ = (s1 && s2) ? 3 : (s1 ? 1 : 0) + (s2 ? 1 : 0) + (cc ? 1 : 0);
        return 1f - occ / 3f;
    }
}
