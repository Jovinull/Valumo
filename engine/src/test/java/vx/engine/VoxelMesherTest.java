package vx.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geometria do mesher, sem contexto OpenGL. */
class VoxelMesherTest {
    private static VoxelSource grid(int sx, int sy, int sz, int... solid) {
        int[] cells = new int[sx * sy * sz];
        for (int i = 0; i + 2 < solid.length; i += 3) cells[solid[i] + sx * (solid[i + 1] + sy * solid[i + 2])] = (1 << 24) | 0xFF8040;
        return new VoxelSource() {
            public int sizeX() { return sx; }
            public int sizeY() { return sy; }
            public int sizeZ() { return sz; }
            public int get(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return 0;
                return cells[x + sx * (y + sy * z)];
            }
        };
    }

    @Test
    void singleVoxelHasSixFaces() {
        float[] d = VoxelMesher.buildData(grid(1, 1, 1, 0, 0, 0), 1f, 0, 0, 0);
        assertEquals(6 * 6 * Mesh.SCENE_STRIDE, d.length);
    }

    @Test
    void sharedFacesAreCulled() {
        float[] d = VoxelMesher.buildData(grid(2, 1, 1, 0, 0, 0, 1, 0, 0), 1f, 0, 0, 0);
        assertEquals(10 * 6 * Mesh.SCENE_STRIDE, d.length);
    }

    @Test
    void voxelSizeAndOffsetAreApplied() {
        float[] d = VoxelMesher.buildData(grid(1, 1, 1, 0, 0, 0), 0.25f, -0.5f, 0, -0.5f);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < d.length; i += Mesh.SCENE_STRIDE) {
            minX = Math.min(minX, d[i]);
            maxX = Math.max(maxX, d[i]);
            maxY = Math.max(maxY, d[i + 1]);
        }
        assertEquals(-0.125f, minX, 1e-6);
        assertEquals(0.125f, maxX, 1e-6);
        assertEquals(0.25f, maxY, 1e-6);
    }

    @Test
    void cornerAmbientOcclusionDarkensInnerCorner() {
        // Degrau: o voxel (1,1,0) fica acima e ao lado de (0,0,0); o topo de (0,0,0) deve ter AO < 1 na borda.
        float[] d = VoxelMesher.buildData(grid(2, 2, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0), 1f, 0, 0, 0);
        boolean occluded = false, open = false;
        for (int i = 0; i < d.length; i += Mesh.SCENE_STRIDE) {
            boolean topOfFirst = d[i + 4] == 1f && d[i + 1] == 1f && d[i] <= 1f;
            if (!topOfFirst) continue;
            if (d[i + 9] < 0.99f) occluded = true;
            if (d[i + 9] > 0.99f) open = true;
        }
        assertTrue(occluded, "vértices junto ao degrau devem ser ocluídos");
        assertTrue(open, "vértices afastados do degrau devem ficar abertos");
    }

    @Test
    void colorAndMaterialAreCarried() {
        float[] d = VoxelMesher.buildData(grid(1, 1, 1, 0, 0, 0), 1f, 0, 0, 0);
        assertEquals(1f, d[6], 1e-6);
        assertEquals(0x80 / 255f, d[7], 1e-6);
        assertEquals(0f, d[10], 1e-6);
    }

    private static double area(float[] d) {
        double a = 0;
        for (int i = 0; i < d.length; i += 3 * Mesh.SCENE_STRIDE) {
            double[] p0 = {d[i], d[i + 1], d[i + 2]};
            int j = i + Mesh.SCENE_STRIDE, k = i + 2 * Mesh.SCENE_STRIDE;
            double[] e1 = {d[j] - p0[0], d[j + 1] - p0[1], d[j + 2] - p0[2]};
            double[] e2 = {d[k] - p0[0], d[k + 1] - p0[1], d[k + 2] - p0[2]};
            double cx = e1[1] * e2[2] - e1[2] * e2[1], cy = e1[2] * e2[0] - e1[0] * e2[2], cz = e1[0] * e2[1] - e1[1] * e2[0];
            a += 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
        }
        return a;
    }

    @Test
    void mergedMeshKeepsSurfaceAndUsesFewerTriangles() {
        // laje 4x1x3 de mesma cor: mesma área exposta, bem menos triângulos
        java.util.List<Integer> s = new java.util.ArrayList<>();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 3; z++) { s.add(x); s.add(0); s.add(z); }
        VoxelSource src = grid(4, 1, 3, s.stream().mapToInt(Integer::intValue).toArray());
        float[] plain = VoxelMesher.buildData(src, 1f, 0, 0, 0);
        float saved = VoxelMesher.crackMargin;
        VoxelMesher.crackMargin = 0f;                      // área exata sem a margem anti-fresta
        float[] merged = VoxelMesher.buildDataMerged(src, 1f, 0, 0, 0);
        VoxelMesher.crackMargin = saved;
        assertEquals(area(plain), area(merged), 1e-4);
        assertTrue(merged.length < plain.length / 3, "fusão deveria reduzir os triângulos");
    }

    @Test
    void mergedMeshKeepsWindingOfEveryFace() {
        // a normal geométrica (pela ordem dos vértices) deve coincidir com a normal gravada
        VoxelSource src = grid(3, 2, 2, 0, 0, 0, 1, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 1);
        float[] d = VoxelMesher.buildDataMerged(src, 1f, 0, 0, 0);
        for (int i = 0; i < d.length; i += 3 * Mesh.SCENE_STRIDE) {
            int j = i + Mesh.SCENE_STRIDE, k = i + 2 * Mesh.SCENE_STRIDE;
            float[] e1 = {d[j] - d[i], d[j + 1] - d[i + 1], d[j + 2] - d[i + 2]};
            float[] e2 = {d[k] - d[i], d[k + 1] - d[i + 1], d[k + 2] - d[i + 2]};
            float cx = e1[1] * e2[2] - e1[2] * e2[1], cy = e1[2] * e2[0] - e1[0] * e2[2], cz = e1[0] * e2[1] - e1[1] * e2[0];
            float dot = cx * d[i + 3] + cy * d[i + 4] + cz * d[i + 5];
            assertTrue(dot > 0, "triângulo com orientação invertida em " + i);
        }
    }

    @Test
    void mergedQuadsNeverExtendBeyondTheirFaces() {
        // rodada 08.1: malha conforme, sem margem — nenhum vértice sai da caixa do volume (a margem antiga
        // sobrepunha faces coplanares e causava z-fighting; ver MeshSeamTest e ADR 0014)
        VoxelSource src = grid(4, 1, 1, 0, 0, 0, 1, 0, 0, 2, 0, 0, 3, 0, 0);
        float[] d = VoxelMesher.buildDataMerged(src, 1f, 0, 0, 0);
        for (int i = 0; i < d.length; i += Mesh.SCENE_STRIDE) {
            assertTrue(d[i] >= 0 && d[i] <= 4 && d[i + 1] >= 0 && d[i + 1] <= 1 && d[i + 2] >= 0 && d[i + 2] <= 1, "vértice fora do volume");
        }
    }
}
