package vx.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.VoxModel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 09.2 — os voxels de um rig agrupados em blocos de {@code k}³ (fragmentos da morte), sem OpenGL: para cada parte,
 * o centro de cada bloco no espaço da parte (unidades de mundo, a mesma convenção do {@code VoxelMesher}: (x + origem) ×
 * tamanho do voxel), a cor mais comum do bloco (a paleta e as bandas de sombra pintadas do próprio modelo) e quanto do
 * bloco está cheio. Lido uma vez por rig.
 */
public final class VoxelCloud {
    /** Bloco: parte, centro (espaço da parte), lado (u) já escalado pelo preenchimento, cor 0xRRGGBB. */
    public record Chunk(int part, float x, float y, float z, float size, int rgb) { }

    public final List<Chunk> chunks = new ArrayList<>();
    public final float voxelSize;

    private VoxelCloud(float voxelSize) { this.voxelSize = voxelSize; }

    private static final Map<String, VoxelCloud> CACHE = new HashMap<>();

    public static synchronized VoxelCloud of(Path rigDir, int k) {
        return CACHE.computeIfAbsent(rigDir.toAbsolutePath() + "#" + k, key -> load(rigDir, k));
    }

    static VoxelCloud load(Path dir, int k) {
        try {
            JsonObject doc = JsonParser.parseString(Files.readString(dir.resolve("rig.json"))).getAsJsonObject();
            VoxelCloud c = new VoxelCloud(doc.get("voxelSize").getAsFloat());
            JsonArray parts = doc.getAsJsonArray("parts");
            for (int i = 0; i < parts.size(); i++) {
                JsonObject p = parts.get(i).getAsJsonObject();
                if (!p.has("file")) continue;
                VoxModel m = VoxModel.load(dir.resolve(p.get("file").getAsString()));
                JsonArray o = p.getAsJsonArray("origin");
                float ox = o.get(0).getAsFloat(), oy = o.get(1).getAsFloat(), oz = o.get(2).getAsFloat(), vs = c.voxelSize;
                for (int bz = 0; bz < m.sz; bz += k)
                    for (int by = 0; by < m.sy; by += k)
                        for (int bx = 0; bx < m.sx; bx += k) {
                            Map<Integer, Integer> count = new HashMap<>();
                            int n = 0;
                            float sx = 0, sy = 0, sz = 0;
                            for (int z = bz; z < Math.min(bz + k, m.sz); z++)
                                for (int y = by; y < Math.min(by + k, m.sy); y++)
                                    for (int x = bx; x < Math.min(bx + k, m.sx); x++) {
                                        int v = m.get(x, y, z);
                                        if (v == 0) continue;
                                        n++;
                                        sx += x + 0.5f; sy += y + 0.5f; sz += z + 0.5f;
                                        count.merge(v & 0xffffff, 1, Integer::sum);
                                    }
                            if (n == 0) continue;
                            int rgb = count.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
                            float fill = (float) n / (k * k * k), side = k * vs * (float) Math.cbrt(Math.max(0.35f, fill));
                            c.chunks.add(new Chunk(i, (sx / n + ox) * vs, (sy / n + oy) * vs, (sz / n + oz) * vs, side, rgb));
                        }
            }
            return c;
        } catch (IOException e) {
            throw new UncheckedIOException("rig ilegível para fragmentos: " + dir, e);
        }
    }
}
