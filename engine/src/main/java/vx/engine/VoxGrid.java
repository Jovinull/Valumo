package vx.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Volume de voxels em memória, no ESPAÇO DO MODELO (coordenadas inteiras, podem ser negativas), para compor
 * peças antes do meshing (rodada 07: personalização). Guarda a cor empacotada do {@link VoxelSource}
 * ({@code ((material+1) << 24) | 0xRRGGBB}). {@link #source()} expõe a caixa mínima como VoxelSource com a
 * origem em {@link #origin()} — a mesma convenção de {@code rig.json} ("origin" = canto mínimo), então uma
 * grade com os mesmos voxels de um .vox gera exatamente a mesma malha.
 */
public final class VoxGrid {
    private final Map<Long, Integer> cells = new HashMap<>();

    public static long key(int x, int y, int z) {
        return ((long) (x + (1 << 19)) << 40) | ((long) (y + (1 << 19)) << 20) | (z + (1 << 19));
    }

    public static int kx(long k) { return (int) ((k >>> 40) & 0xFFFFF) - (1 << 19); }
    public static int ky(long k) { return (int) ((k >>> 20) & 0xFFFFF) - (1 << 19); }
    public static int kz(long k) { return (int) (k & 0xFFFFF) - (1 << 19); }

    /** Lê um .vox carregado e o desloca para o espaço do modelo pela origem do rig. */
    public static VoxGrid of(VoxModel m, int ox, int oy, int oz) {
        VoxGrid g = new VoxGrid();
        for (int x = 0; x < m.sx; x++)
            for (int y = 0; y < m.sy; y++)
                for (int z = 0; z < m.sz; z++) {
                    int c = m.get(x, y, z);
                    if (c != 0) g.cells.put(key(x + ox, y + oy, z + oz), c);
                }
        return g;
    }

    public VoxGrid copy() {
        VoxGrid g = new VoxGrid();
        g.cells.putAll(cells);
        return g;
    }

    public int get(int x, int y, int z) { return cells.getOrDefault(key(x, y, z), 0); }
    public int get(long k) { return cells.getOrDefault(k, 0); }
    public boolean has(long k) { return cells.containsKey(k); }
    public void put(long k, int packed) { cells.put(k, packed); }
    public void remove(long k) { cells.remove(k); }
    public int size() { return cells.size(); }
    public Map<Long, Integer> cells() { return cells; }

    /** Sobrepõe outra grade (como Vox.merge do gerador: quem vem depois vence). */
    public VoxGrid overlay(VoxGrid o) {
        cells.putAll(o.cells);
        return this;
    }

    /** Canto mínimo {x, y, z} (origem para o mesher). */
    public int[] origin() {
        int mx = Integer.MAX_VALUE, my = Integer.MAX_VALUE, mz = Integer.MAX_VALUE;
        for (long k : cells.keySet()) {
            mx = Math.min(mx, kx(k));
            my = Math.min(my, ky(k));
            mz = Math.min(mz, kz(k));
        }
        return new int[]{mx, my, mz};
    }

    /** Caixa mínima como VoxelSource (índices locais a partir de {@link #origin()}). */
    public VoxelSource source() {
        int[] o = origin();
        int ax = Integer.MIN_VALUE, ay = Integer.MIN_VALUE, az = Integer.MIN_VALUE;
        for (long k : cells.keySet()) {
            ax = Math.max(ax, kx(k));
            ay = Math.max(ay, ky(k));
            az = Math.max(az, kz(k));
        }
        int sx = ax - o[0] + 1, sy = ay - o[1] + 1, sz = az - o[2] + 1;
        int[] dense = new int[sx * sy * sz];
        for (var e : cells.entrySet())
            dense[(kx(e.getKey()) - o[0]) + sx * ((ky(e.getKey()) - o[1]) + sy * (kz(e.getKey()) - o[2]))] = e.getValue();
        return new VoxelSource() {
            public int sizeX() { return sx; }
            public int sizeY() { return sy; }
            public int sizeZ() { return sz; }
            public int get(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return 0;
                return dense[x + sx * (y + sy * z)];
            }
        };
    }
}
