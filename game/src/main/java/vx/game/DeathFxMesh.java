package vx.game;

import vx.engine.Mesh;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;
import vx.engine.VoxelSource;

import java.util.Collection;

/**
 * Rodada 09.2 — desenho dos fragmentos de morte: TODOS os fragmentos de todas as mortes em andamento numa malha dinâmica
 * só (um desenho por quadro, sem refazer malha de voxel do modelo). Cada fragmento é o cubo de um voxel do
 * {@link VoxelMesher} (mesmo formato de vértice, mesma conversão de cor da cena), girado, escalado e pintado com a cor do
 * bloco; as sementes de luz (D2) usam o material emissivo.
 */
final class DeathFxMesh {
    private static final int STRIDE = Mesh.SCENE_STRIDE;
    private final float[] cube = template(VoxModel.MAT_MATTE), glow = template(VoxModel.MAT_EMISSIVE);
    private float[] buf = new float[cube.length * 64];
    final Mesh mesh = new Mesh(new float[0], Mesh.SCENE_LAYOUT);
    private final float[] r = new float[9];

    /** Cubo de um voxel branco centrado na origem (lado 1). */
    static float[] template(int mat) {
        return VoxelMesher.buildData(new VoxelSource() {
            public int sizeX() { return 1; }
            public int sizeY() { return 1; }
            public int sizeZ() { return 1; }
            public int get(int x, int y, int z) { return x == 0 && y == 0 && z == 0 ? ((mat + 1) << 24) | 0xffffff : 0; }
        }, 1f, -0.5f, -0.5f, -0.5f);
    }

    void build(Collection<DeathFx> fxs) {
        int need = 0;
        for (DeathFx fx : fxs) need += (fx.n + fx.seeds) * cube.length;
        if (buf.length < need) buf = new float[need + cube.length * 32];
        int k = 0;
        float minX = 1e9f, minY = 1e9f, minZ = 1e9f, maxX = -1e9f, maxY = -1e9f, maxZ = -1e9f;
        for (DeathFx fx : fxs) {
            for (int i = 0; i < fx.n; i++) {
                float sc = fx.scaleAt(i) * fx.size[i];
                if (sc < 1e-3f) continue;
                rotation(fx.ax[i], fx.ay[i], fx.az[i], fx.ang[i]);
                int c = fx.rgb[i];
                float l = fx.lighten(i);
                float cr = ((c >> 16) & 0xff) / 255f, cg = ((c >> 8) & 0xff) / 255f, cb = (c & 0xff) / 255f;
                cr += (0.98f - cr) * l; cg += (0.94f - cg) * l; cb += (0.84f - cb) * l;
                k = emit(cube, k, fx.x[i], fx.y[i], fx.z[i], sc, cr, cg, cb);
                minX = Math.min(minX, fx.x[i] - sc); minY = Math.min(minY, fx.y[i] - sc); minZ = Math.min(minZ, fx.z[i] - sc);
                maxX = Math.max(maxX, fx.x[i] + sc); maxY = Math.max(maxY, fx.y[i] + sc); maxZ = Math.max(maxZ, fx.z[i] + sc);
            }
            for (int i = 0; i < fx.seeds; i++) {
                float g = fx.seedGlow(i);
                if (g < 0.02f) continue;
                rotation(0f, 1f, 0f, fx.t * 2f + fx.sph[i]);
                float sc = 0.045f * g;
                k = emit(glow, k, fx.sx[i], fx.sy[i], fx.sz[i], sc, 0.62f, 0.94f, 0.78f);
                minX = Math.min(minX, fx.sx[i] - sc); minY = Math.min(minY, fx.sy[i] - sc); minZ = Math.min(minZ, fx.sz[i] - sc);
                maxX = Math.max(maxX, fx.sx[i] + sc); maxY = Math.max(maxY, fx.sy[i] + sc); maxZ = Math.max(maxZ, fx.sz[i] + sc);
            }
        }
        mesh.update(buf, k);
        if (k > 0) {
            mesh.bounds[0] = minX; mesh.bounds[1] = minY; mesh.bounds[2] = minZ;
            mesh.bounds[3] = maxX; mesh.bounds[4] = maxY; mesh.bounds[5] = maxZ;
        }
    }

    private void rotation(float x, float y, float z, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a), t = 1 - c;
        r[0] = t * x * x + c;     r[1] = t * x * y - s * z; r[2] = t * x * z + s * y;
        r[3] = t * x * y + s * z; r[4] = t * y * y + c;     r[5] = t * y * z - s * x;
        r[6] = t * x * z - s * y; r[7] = t * y * z + s * x; r[8] = t * z * z + c;
    }

    private int emit(float[] tpl, int k, float px, float py, float pz, float sc, float cr, float cg, float cb) {
        for (int v = 0; v < tpl.length; v += STRIDE) {
            float x = tpl[v] * sc, y = tpl[v + 1] * sc, z = tpl[v + 2] * sc;
            float nx = tpl[v + 3], ny = tpl[v + 4], nz = tpl[v + 5];
            buf[k] = r[0] * x + r[1] * y + r[2] * z + px;
            buf[k + 1] = r[3] * x + r[4] * y + r[5] * z + py;
            buf[k + 2] = r[6] * x + r[7] * y + r[8] * z + pz;
            buf[k + 3] = r[0] * nx + r[1] * ny + r[2] * nz;
            buf[k + 4] = r[3] * nx + r[4] * ny + r[5] * nz;
            buf[k + 5] = r[6] * nx + r[7] * ny + r[8] * nz;
            buf[k + 6] = cr * tpl[v + 6];
            buf[k + 7] = cg * tpl[v + 7];
            buf[k + 8] = cb * tpl[v + 8];
            for (int j = 9; j < STRIDE; j++) buf[k + j] = tpl[v + j];
            k += STRIDE;
        }
        return k;
    }
}
