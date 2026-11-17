package vx.engine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Modelo voxel carregado de um arquivo MagicaVoxel {@code .vox} (primeiro modelo do arquivo).
 *
 * <p>Convenção de eixos: o .vox é Z-para-cima; a engine é Y-para-cima com +Z = frente do modelo.
 * Conversão: engine(x, y, z) = vox(x, z, sizeY-1-y).
 *
 * <p>Convenção de materiais por faixa de índice da paleta (ver skills/asset-pipeline):
 * 1–175 fosco, 176–199 metal, 200–219 emissivo, 220–249 folhagem, 250–255 água/vidro.
 */
public final class VoxModel implements VoxelSource {
    public static final int MAT_MATTE = 0, MAT_METAL = 1, MAT_EMISSIVE = 2, MAT_FOLIAGE = 3, MAT_WATER = 4;

    public final int sx, sy, sz;
    private final byte[] cells;             // índice de paleta (0 = vazio), ordem x + sx*(y + sy*z)
    private final int[] palette = new int[256]; // 0xRRGGBB por índice

    private VoxModel(int sx, int sy, int sz) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.cells = new byte[sx * sy * sz];
    }

    public static int materialOf(int paletteIndex) {
        if (paletteIndex >= 250) return MAT_WATER;
        if (paletteIndex >= 220) return MAT_FOLIAGE;
        if (paletteIndex >= 200) return MAT_EMISSIVE;
        if (paletteIndex >= 176) return MAT_METAL;
        return MAT_MATTE;
    }

    @Override public int sizeX() { return sx; }
    @Override public int sizeY() { return sy; }
    @Override public int sizeZ() { return sz; }

    @Override
    public int get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return 0;
        int idx = cells[x + sx * (y + sy * z)] & 0xFF;
        if (idx == 0) return 0;
        return ((materialOf(idx) + 1) << 24) | palette[idx];
    }

    public static VoxModel load(Path file) {
        try {
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
            if (b.get() != 'V' || b.get() != 'O' || b.get() != 'X' || b.get() != ' ')
                throw new IOException("cabeçalho .vox inválido");
            b.getInt(); // versão
            int vx = 0, vy = 0, vz = 0;
            VoxModel model = null;
            int[] pal = null;
            while (b.remaining() >= 12) {
                byte[] idb = new byte[4];
                b.get(idb);
                String id = new String(idb);
                int contentSize = b.getInt();
                b.getInt(); // tamanho dos filhos (percorremos os filhos em sequência)
                int end = b.position() + contentSize;
                switch (id) {
                    case "SIZE" -> {
                        if (model == null) {
                            vx = b.getInt();
                            vy = b.getInt();
                            vz = b.getInt();
                        }
                    }
                    case "XYZI" -> {
                        if (model == null) {
                            model = new VoxModel(vx, vz, vy);
                            int n = b.getInt();
                            for (int i = 0; i < n; i++) {
                                int x = b.get() & 0xFF, y = b.get() & 0xFF, z = b.get() & 0xFF;
                                byte c = b.get();
                                int ex = x, ey = z, ez = vy - 1 - y;
                                model.cells[ex + model.sx * (ey + model.sy * ez)] = c;
                            }
                        }
                    }
                    case "RGBA" -> {
                        pal = new int[256];
                        for (int i = 0; i < 256; i++) {
                            int r = b.get() & 0xFF, g = b.get() & 0xFF, bl = b.get() & 0xFF;
                            b.get();
                            if (i + 1 < 256) pal[i + 1] = (r << 16) | (g << 8) | bl;
                        }
                    }
                    default -> { }
                }
                if (!id.equals("MAIN")) b.position(end);
            }
            if (model == null) throw new IOException("arquivo sem chunk XYZI");
            if (pal == null) throw new IOException("arquivo sem paleta RGBA (obrigatória neste projeto)");
            System.arraycopy(pal, 0, model.palette, 0, 256);
            return model;
        } catch (IOException e) {
            throw new IllegalStateException("falha ao ler " + file + ": " + e.getMessage(), e);
        }
    }
}
