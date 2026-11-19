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

    /** Maior arquivo .vox aceito (bytes): o maior asset do projeto tem ~1 MB; acima disto é arquivo errado, não modelo. */
    public static final long MAX_BYTES = 32L << 20;
    /** Lado máximo de um modelo no formato .vox (o próprio formato guarda coordenadas em 1 byte). */
    public static final int MAX_SIDE = 256;

    /**
     * Lê um .vox (MagicaVoxel). Arquivo malformado — tamanho de bloco negativo ou além do fim, dimensão fora de 1–256,
     * voxel fora do tamanho declarado, bloco truncado, sem XYZI ou sem paleta — vira {@link IllegalStateException} com o
     * caminho e o motivo; nunca laço infinito (antes, um tamanho negativo voltava a posição) nem alocação descontrolada.
     */
    public static VoxModel load(Path file) {
        try {
            if (Files.size(file) > MAX_BYTES) throw new IOException("arquivo grande demais (" + Files.size(file) + " bytes)");
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
            if (b.remaining() < 8 || b.get() != 'V' || b.get() != 'O' || b.get() != 'X' || b.get() != ' ')
                throw new IOException("cabeçalho .vox inválido");
            b.getInt(); // versão
            int vx = 0, vy = 0, vz = 0;
            VoxModel model = null;
            int[] pal = null;
            while (b.remaining() >= 12) {
                byte[] idb = new byte[4];
                b.get(idb);
                String id = new String(idb, java.nio.charset.StandardCharsets.US_ASCII);
                int contentSize = b.getInt(), childSize = b.getInt();   // os filhos são percorridos em sequência
                if (contentSize < 0 || childSize < 0 || contentSize > b.remaining())
                    throw new IOException("bloco " + id + " com tamanho inválido (" + contentSize + ", restam " + b.remaining() + ")");
                int end = b.position() + contentSize;
                switch (id) {
                    case "SIZE" -> {
                        if (contentSize < 12) throw new IOException("bloco SIZE truncado");
                        if (model == null) {
                            vx = b.getInt();
                            vy = b.getInt();
                            vz = b.getInt();
                            if (vx < 1 || vy < 1 || vz < 1 || vx > MAX_SIDE || vy > MAX_SIDE || vz > MAX_SIDE)
                                throw new IOException("dimensão fora de 1–" + MAX_SIDE + ": " + vx + "×" + vy + "×" + vz);
                        }
                    }
                    case "XYZI" -> {
                        if (model == null) {
                            if (vx == 0) throw new IOException("bloco XYZI antes de SIZE");
                            if (contentSize < 4) throw new IOException("bloco XYZI truncado");
                            model = new VoxModel(vx, vz, vy);
                            int n = b.getInt();
                            if (n < 0 || n > (contentSize - 4) / 4) throw new IOException("XYZI declara " + n + " voxels em " + contentSize + " bytes");
                            for (int i = 0; i < n; i++) {
                                int x = b.get() & 0xFF, y = b.get() & 0xFF, z = b.get() & 0xFF;
                                byte c = b.get();
                                if (x >= vx || y >= vy || z >= vz) throw new IOException("voxel (" + x + "," + y + "," + z + ") fora de " + vx + "×" + vy + "×" + vz);
                                int ex = x, ey = z, ez = vy - 1 - y;
                                model.cells[ex + model.sx * (ey + model.sy * ez)] = c;
                            }
                        }
                    }
                    case "RGBA" -> {
                        if (contentSize < 1024) throw new IOException("paleta RGBA truncada");
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
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("falha ao ler " + file + ": " + e.getMessage(), e);
        }
    }
}
