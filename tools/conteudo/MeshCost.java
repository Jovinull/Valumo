import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

/**
 * Laboratório de conteúdo — custo de malha REAL de cada prop da E (o mesmo leitor e o mesmo mesher do jogo, fusão
 * exata ligada; folhagem fundida só com affineWind), sem OpenGL. CSV: nome, conteudo (laboratório?), voxels,
 * triângulos, KB de vértices, ms para ler+malhar (média de 5).
 *
 *   java -cp "viewer/build/install/viewer/lib/*" tools/conteudo/MeshCost.java > output/conteudo/custos.csv
 */
public class MeshCost {
    public static void main(String[] a) throws Exception {
        Path env = Path.of(a.length > 0 ? a[0] : "assets/environment/e");
        JsonObject props = JsonParser.parseString(Files.readString(env.resolve("props.json"))).getAsJsonObject();
        System.out.println("nome,conteudo,voxels,triangulos,kb_vertices,ms_ler_malhar");
        for (String name : new TreeMap<>(props.asMap()).keySet()) {
            JsonObject e = props.getAsJsonObject(name);
            if (!e.has("file")) continue;
            var o = e.getAsJsonArray("origin");
            boolean wind = e.has("affineWind") && e.get("affineWind").getAsBoolean();
            float vs = e.get("voxelSize").getAsFloat();
            float[] data = null;
            int voxels = 0;
            long t0 = 0;
            for (int i = -2; i < 5; i++) {                     // 2 aquecimentos (JIT)
                if (i == 0) t0 = System.nanoTime();
                VoxModel m = VoxModel.load(env.resolve(e.get("file").getAsString()));
                data = VoxelMesher.buildDataMerged(m, vs, o.get(0).getAsFloat(), o.get(1).getAsFloat(), o.get(2).getAsFloat(), wind);
                if (i == 4) for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) if (m.get(x, y, z) != 0) voxels++;
            }
            double ms = (System.nanoTime() - t0) / 5e6;
            System.out.printf(java.util.Locale.ROOT, "%s,%s,%d,%d,%.1f,%.2f%n", name, e.has("conteudo") ? "sim" : "nao", voxels,
                    data.length / 13 / 3, data.length * 4 / 1024.0, ms);
        }
    }
}
