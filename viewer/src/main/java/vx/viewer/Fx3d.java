package vx.viewer;

import vx.engine.Mesh;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;
import vx.engine.VoxelSource;

/**
 * Malhas pequenas geradas em código para efeitos e depuração (rodada 04): o orbe de magia verde-água
 * (motivo P1 "sementes de luz", emissivo, ~5 voxels de 1/40 u) e cubos coloridos para o modo de depuração.
 */
final class Fx3d {
    private static Mesh orb;
    private static final java.util.Map<Integer, Mesh> CUBES = new java.util.HashMap<>();

    private Fx3d() { }

    /** Orbe: núcleo claro + casca verde-água, centrado na origem. */
    static Mesh orb() {
        if (orb == null) {
            int r = 3;
            orb = VoxelMesher.build(new VoxelSource() {
                public int sizeX() { return 2 * r; }
                public int sizeY() { return 2 * r; }
                public int sizeZ() { return 2 * r; }
                public int get(int x, int y, int z) {
                    if (x < 0 || y < 0 || z < 0 || x >= 2 * r || y >= 2 * r || z >= 2 * r) return 0;
                    float dx = x + 0.5f - r, dy = y + 0.5f - r, dz = z + 0.5f - r;
                    float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > r) return 0;
                    int rgb = d < r - 1.3f ? 0xe6fff8 : 0x9ff0c8;
                    return ((VoxModel.MAT_EMISSIVE + 1) << 24) | rgb;
                }
            }, 1f / 40f, -r, -r, -r);
        }
        return orb;
    }

    private static final java.util.Map<Integer, Mesh> MATTE = new java.util.HashMap<>();

    /** Cubo unitário centrado na origem, material fosco (bancada de terreno). */
    static Mesh matteCube(int rgb) {
        return MATTE.computeIfAbsent(rgb, c -> VoxelMesher.build(new VoxelSource() {
            public int sizeX() { return 1; }
            public int sizeY() { return 1; }
            public int sizeZ() { return 1; }
            public int get(int x, int y, int z) { return x == 0 && y == 0 && z == 0 ? (1 << 24) | c : 0; }
        }, 1f, -0.5f, -0.5f, -0.5f));
    }

    /** Cubo unitário centrado na origem, cor emissiva (para o modo de depuração ficar legível na sombra). */
    static Mesh cube(int rgb) {
        return CUBES.computeIfAbsent(rgb, c -> VoxelMesher.build(new VoxelSource() {
            public int sizeX() { return 1; }
            public int sizeY() { return 1; }
            public int sizeZ() { return 1; }
            public int get(int x, int y, int z) { return x == 0 && y == 0 && z == 0 ? ((VoxModel.MAT_EMISSIVE + 1) << 24) | c : 0; }
        }, 1f, -0.5f, -0.5f, -0.5f));
    }
}
