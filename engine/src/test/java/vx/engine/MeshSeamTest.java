package vx.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 08.1 — propriedades da malha fundida que importam para a imagem (sem OpenGL):
 * <ol>
 *   <li><b>Cobertura exata</b>: todo ponto de amostra de toda face visível é coberto exatamente UMA vez por
 *       triângulos do mesmo plano e normal — 0 = fresta (ponto claro), 2+ = sobreposição coplanar (z-fighting).
 *       Há amostras a 0,02 voxel da borda, onde a margem antiga sobrepunha.</li>
 *   <li><b>Estanque</b>: em superfície fechada, cada aresta dirigida a→b tem a oposta b→a na mesma quantidade —
 *       T-junction deixa aresta sem par (a rasterização abre fresta ali).</li>
 *   <li><b>Área</b> igual à malha de referência (um quad por face).</li>
 * </ol>
 * O modo legado (rodada 03: sem conformidade + margem) precisa FALHAR as duas primeiras — prova que o teste
 * pega o defeito que causava tremor e frestas.
 */
class MeshSeamTest {
    @AfterEach
    void restore() {
        VoxelMesher.conforming = true;
        VoxelMesher.crackMargin = 0f;
    }

    // ------------------------------------------------------------------ casos sintéticos

    record Grid(int sx, int sy, int sz, int[] cells) implements VoxelSource {
        public int sizeX() { return sx; }
        public int sizeY() { return sy; }
        public int sizeZ() { return sz; }
        public int get(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return 0;
            return cells[x + sx * (y + sy * z)];
        }
        Grid set(int x, int y, int z, int rgb) { cells[x + sx * (y + sy * z)] = (1 << 24) | rgb; return this; }
    }

    static Grid grid(int sx, int sy, int sz) { return new Grid(sx, sy, sz, new int[sx * sy * sz]); }

    /** Parede 8×6×1 de duas cores lado a lado (reboco | madeira). */
    static Grid twoMaterials() {
        Grid g = grid(8, 6, 1);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 6; y++) g.set(x, y, 0, x < 5 ? 0xe8e0d0 : 0x5a3a28);
        return g;
    }

    /** Laje grande de uma cor com uma faixa de cubos de outra cor no meio (quad grande junto a vários pequenos). */
    static Grid bigNextToSmall() {
        Grid g = grid(9, 1, 9);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) g.set(x, 0, z, (x == 4 && z % 2 == 0) ? 0x8a5a3a : 0x7eab58);
        return g;
    }

    /** Degraus de 1 voxel (relevo T1). */
    static Grid steps() {
        Grid g = grid(10, 5, 6);
        for (int x = 0; x < 10; x++) for (int z = 0; z < 6; z++) {
            int top = (x + z / 2) / 2;
            for (int y = 0; y <= top && y < 5; y++) g.set(x, y, z, y == top ? 0x7eab58 : 0xa47a58);
        }
        return g;
    }

    /** Fachada: reboco com vigas de madeira e janelas recuadas (bordas entre cores e degraus de profundidade). */
    static Grid facade() {
        Grid g = grid(16, 12, 3);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 12; y++) {
            boolean beam = x % 5 == 0 || y % 6 == 0;
            g.set(x, y, 0, beam ? 0x5a3a28 : 0xe8e0d0);
            g.set(x, y, 1, 0x5a3a28);
            boolean window = (x % 5 == 2 || x % 5 == 3) && (y % 6 >= 2 && y % 6 <= 3);
            if (!window) g.set(x, y, 2, beam ? 0x5a3a28 : 0xe8e0d0);
            else g.set(x, y, 2, 0xfff0c0);
        }
        return g;
    }

    /** Placa facial com olhos, sobrancelhas, boca e dentes (decalques de 1 voxel na placa). */
    static Grid facePlate() {
        Grid g = grid(14, 12, 2);
        for (int x = 0; x < 14; x++) for (int y = 0; y < 12; y++) { g.set(x, y, 0, 0xc98a5e); g.set(x, y, 1, 0xf2c9a0); }
        int[][] eye = {{3, 6}, {4, 6}, {3, 7}, {4, 7}, {9, 6}, {10, 6}, {9, 7}, {10, 7}};
        for (int[] e : eye) g.set(e[0], e[1], 1, 0x2a1c34);
        g.set(3, 7, 1, 0xfffaf0);
        g.set(9, 7, 1, 0xfffaf0);
        for (int x : new int[]{3, 4, 9, 10}) g.set(x, 9, 1, 0x3a2238);
        for (int x = 5; x <= 8; x++) g.set(x, 3, 1, 0xa9524a);
        g.set(6, 2, 1, 0xdccbbb);
        g.set(7, 2, 1, 0xdccbbb);
        return g;
    }

    /** Relevo de alturas variadas (encostas e patamares) — a superfície onde as frestas apareciam. */
    static Grid terrain() {
        Grid g = grid(24, 8, 24);
        for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) {
            double h = 3 + 2 * Math.sin(x * 0.35) * Math.cos(z * 0.27) + (x > 12 ? 1 : 0);
            int top = (int) Math.floor(h);
            for (int y = 0; y <= top && y < 8; y++) g.set(x, y, z, y == top ? ((x * 7 + z * 3) % 3 == 0 ? 0x72a051 : 0x7eab58) : 0xa47a58);
        }
        return g;
    }

    static List<VoxelSource> cases() {
        return List.of(twoMaterials(), bigNextToSmall(), steps(), facade(), facePlate(), terrain());
    }

    // ------------------------------------------------------------------ propriedades

    /** Faces visíveis esperadas: (plano, normal, célula da face) a partir da malha de referência (1 quad por face). */
    record Face(int f, int d, int planeCoord, int a, int b) { }

    static final float[] OFFS = {0.02f, 0.25f, 0.5f, 0.75f, 0.97f};

    static int axis(float nx, float ny, float nz) { return Math.abs(nx) > 0.5f ? 0 : Math.abs(ny) > 0.5f ? 1 : 2; }

    /** Pior desvio de cobertura: devolve {amostras com 0, amostras com ≥ 2}. */
    static int[] coverage(VoxelSource src) {
        float[] ref = VoxelMesher.buildData(src, 1f, 0, 0, 0), mesh = VoxelMesher.buildDataMerged(src, 1f, 0, 0, 0);
        // triângulos da malha fundida agrupados por (eixo, sinal, coordenada do plano)
        Map<String, List<float[]>> tris = new HashMap<>();
        int S = Mesh.SCENE_STRIDE;
        for (int i = 0; i < mesh.length; i += 3 * S) {
            int d = axis(mesh[i + 3], mesh[i + 4], mesh[i + 5]);
            int sign = (mesh[i + 3 + d] > 0) ? 1 : -1;
            int u = (d + 1) % 3, v = (d + 2) % 3;
            String k = d + ":" + sign + ":" + Math.round(mesh[i + d] * 1000);
            tris.computeIfAbsent(k, x -> new ArrayList<>()).add(new float[]{
                    mesh[i + u], mesh[i + v], mesh[i + S + u], mesh[i + S + v], mesh[i + 2 * S + u], mesh[i + 2 * S + v]});
        }
        int holes = 0, overlaps = 0;
        for (int i = 0; i < ref.length; i += 6 * S) {                 // cada quad de referência = 1 face de voxel
            int d = axis(ref[i + 3], ref[i + 4], ref[i + 5]);
            int sign = (ref[i + 3 + d] > 0) ? 1 : -1;
            int u = (d + 1) % 3, v = (d + 2) % 3;
            float minU = Float.MAX_VALUE, minV = Float.MAX_VALUE;
            for (int k = 0; k < 6; k++) { minU = Math.min(minU, ref[i + k * S + u]); minV = Math.min(minV, ref[i + k * S + v]); }
            List<float[]> ts = tris.getOrDefault(d + ":" + sign + ":" + Math.round(ref[i + d] * 1000), List.of());
            for (float ou : OFFS) for (float ov : OFFS) {
                float pu = minU + ou + 0.0031f, pv = minV + ov + 0.0057f;   // fora das diagonais dos triângulos
                int c = 0;
                for (float[] t : ts) if (inside(t, pu, pv)) c++;
                if (c == 0) holes++;
                if (c >= 2) overlaps++;
            }
        }
        return new int[]{holes, overlaps};
    }

    static boolean inside(float[] t, float px, float py) {
        float d1 = cross(px, py, t[0], t[1], t[2], t[3]), d2 = cross(px, py, t[2], t[3], t[4], t[5]), d3 = cross(px, py, t[4], t[5], t[0], t[1]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    static float cross(float px, float py, float ax, float ay, float bx, float by) { return (px - bx) * (ay - by) - (ax - bx) * (py - by); }

    /** Arestas dirigidas sem par (T-junction) na superfície sólida (a água é aberta por desenho e fica de fora). */
    static int unmatchedEdges(VoxelSource src) {
        float[] m = VoxelMesher.buildDataMerged(src, 1f, 0, 0, 0);
        int S = Mesh.SCENE_STRIDE;
        Map<String, Integer> bal = new HashMap<>();
        for (int i = 0; i < m.length; i += 3 * S) {
            if (Math.round(m[i + 10]) == VoxModel.MAT_WATER) continue;   // espelho d'água: superfície aberta por desenho
            for (int k = 0; k < 3; k++) {
                int a = i + k * S, b = i + ((k + 1) % 3) * S;
                String ka = Math.round(m[a] * 1000) + "," + Math.round(m[a + 1] * 1000) + "," + Math.round(m[a + 2] * 1000);
                String kb = Math.round(m[b] * 1000) + "," + Math.round(m[b + 1] * 1000) + "," + Math.round(m[b + 2] * 1000);
                if (ka.compareTo(kb) < 0) bal.merge(ka + "|" + kb, 1, Integer::sum);
                else bal.merge(kb + "|" + ka, -1, Integer::sum);
            }
        }
        int bad = 0;
        for (int v : bal.values()) if (v != 0) bad++;
        return bad;
    }

    static float area(float[] d) {
        float a = 0;
        int S = Mesh.SCENE_STRIDE;
        for (int i = 0; i < d.length; i += 3 * S) {
            float[] e1 = {d[i + S] - d[i], d[i + S + 1] - d[i + 1], d[i + S + 2] - d[i + 2]};
            float[] e2 = {d[i + 2 * S] - d[i], d[i + 2 * S + 1] - d[i + 1], d[i + 2 * S + 2] - d[i + 2]};
            float cx = e1[1] * e2[2] - e1[2] * e2[1], cy = e1[2] * e2[0] - e1[0] * e2[2], cz = e1[0] * e2[1] - e1[1] * e2[0];
            a += 0.5f * (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        }
        return a;
    }

    // ------------------------------------------------------------------ testes

    @Test
    void conformingMeshCoversEveryFaceExactlyOnce() {
        for (VoxelSource s : cases()) {
            int[] c = coverage(s);
            assertEquals(0, c[0], "fresta (amostra sem cobertura) em " + s);
            assertEquals(0, c[1], "sobreposição coplanar (z-fighting) em " + s);
        }
    }

    @Test
    void conformingMeshIsWatertightWithoutTJunctions() {
        for (VoxelSource s : cases()) assertEquals(0, unmatchedEdges(s), "aresta sem par (T-junction) em " + s);
    }

    @Test
    void conformingMeshKeepsAreaAndMostOfTheMergeGain() {
        for (VoxelSource s : cases()) {
            float[] plain = VoxelMesher.buildData(s, 1f, 0, 0, 0), merged = VoxelMesher.buildDataMerged(s, 1f, 0, 0, 0);
            assertEquals(area(plain), area(merged), 1e-3f * area(plain), "área diferente");
            assertTrue(merged.length <= plain.length, "a fusão nunca pode gerar mais triângulos que um quad por face");
        }
        // laje lisa: continua 2 triângulos por face visível fundida (6 faces → 12 triângulos)
        Grid slab = grid(8, 1, 8);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) slab.set(x, 0, z, 0x7eab58);
        assertEquals(12, VoxelMesher.buildDataMerged(slab, 1f, 0, 0, 0).length / Mesh.SCENE_STRIDE / 3);
    }

    @Test
    void legacyMarginMeshShowsTheDefectTheseTestsCatch() {
        VoxelMesher.conforming = false;
        VoxelMesher.crackMargin = 0.06f;
        int overlaps = 0, unmatched = 0;
        for (VoxelSource s : cases()) { overlaps += coverage(s)[1]; unmatched += unmatchedEdges(s); }
        assertTrue(overlaps > 0, "a malha antiga sobrepunha faces coplanares (z-fighting)");
        assertTrue(unmatched > 0, "a malha antiga tinha T-junctions (frestas)");
        // e sem a margem, as T-junctions viram frestas abertas: a correção não pode ser só "tirar a margem"
        VoxelMesher.crackMargin = 0f;
        int unmatchedNoMargin = 0;
        for (VoxelSource s : cases()) unmatchedNoMargin += unmatchedEdges(s);
        assertTrue(unmatchedNoMargin > 0);
    }

    @Test
    void realAssetsAreWatertightAndCoveredOnce() throws Exception {
        Path dir = Path.of("assets/environment/e");
        assumeAssets(dir);
        for (String name : new String[]{"house.vox", "trail_post.vox", "well.vox", "crate.vox", "rock_m.vox", "tree_oak.vox"}) {
            Path f = dir.resolve(name);
            if (!Files.exists(f)) continue;
            VoxModel m = VoxModel.load(f);
            int[] c = coverage(m);
            assertEquals(0, c[0], "fresta em " + name);
            assertEquals(0, c[1], "sobreposição em " + name);
            assertEquals(0, unmatchedEdges(m), "aresta sem par (fresta) em " + name);
        }
        Path hero = Path.of("assets/characters/e/adventurer/head.vox");
        if (Files.exists(hero)) {
            VoxModel m = VoxModel.load(hero);
            assertArrayEquals(new int[]{0, 0}, coverage(m), "rosto do aventureiro");
            assertEquals(0, unmatchedEdges(m), "rosto do aventureiro estanque");
        }
    }

    static void assumeAssets(Path dir) {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(dir), "assets gerados ausentes (rode ./gradlew assets)");
    }
}
