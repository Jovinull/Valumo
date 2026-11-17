package vx.viewer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rodada 08 — colunas do relevo (topo de cada coluna de voxels) sem OpenGL. É exatamente a grade que o
 * {@link World} desenha (o World usa esta classe), então a colisão do jogo e os testes pisam no mesmo chão
 * que aparece na tela. Altura = topo do voxel da coluna (T1 na direção E).
 */
public final class Terrain implements Animator.Ground {
    public final String key;
    public final int mode, n;
    public final float voxel;
    final int[] top;
    private static final Map<String, Terrain> CACHE = new ConcurrentHashMap<>();

    private Terrain(String key, int mode) {
        this.key = key;
        this.mode = mode;
        this.voxel = World.TerrainStyle.of(key).voxel();
        float tv = voxel;
        n = Math.round(World.SIZE / tv);
        int ny = Math.round((World.Y_MAX - World.Y_MIN) / tv);
        top = new int[n * n];
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                float wx = (x + 0.5f) * tv - World.SIZE / 2, wz = (z + 0.5f) * tv - World.SIZE / 2;
                float h = World.height(wx, wz, !key.equals("e"));
                top[x + z * n] = Math.clamp(World.quantize(mode, h, wx, wz, tv), 0, ny - 1);
            }
    }

    /** Grade do estilo (E: modo de relevo padrão, T1). Compartilhada e imutável. */
    public static Terrain of(String key, int mode) {
        int m = key.equals("e") ? mode : 0;
        return CACHE.computeIfAbsent(key + "#" + m, k -> new Terrain(key, m));
    }

    public static Terrain e() { return of("e", World.E_TERRAIN); }

    /** Altura do chão (topo do voxel) no ponto. */
    public float groundAt(float x, float z) {
        int xi = Math.clamp((int) Math.floor((x + World.SIZE / 2) / voxel), 0, n - 1);
        int zi = Math.clamp((int) Math.floor((z + World.SIZE / 2) / voxel), 0, n - 1);
        return (top[xi + zi * n] + 1) * voxel + World.Y_MIN;
    }

    /** Altura suavizada (média de vizinhos) — a mesma de {@link World#groundSmooth}. */
    public float groundSmooth(float x, float z) {
        float tv = voxel, s = 0;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) s += groundAt(x + dx * tv * 0.75f, z + dz * tv * 0.75f);
        return Math.max(s / 9f, groundAt(x, z) - tv * 0.5f);
    }

    @Override
    public float height(float x, float z) { return groundAt(x, z); }
}
