package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.Mesh;
import vx.engine.Texture;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Personagem ou criatura: partes rígidas com pivôs e hierarquia, lidas de {@code rig.json}.
 * As malhas ficam no espaço do modelo, já em unidades de mundo (voxel × voxelSize).
 */
public final class CharacterModel {
    public static final class Part {
        public String name;
        public int parent = -1;
        public Mesh mesh;
        public float px, py, pz;   // pivô em unidades de mundo
        /** Malhas alternativas da parte por nome (ex.: expressões faciais da cabeça). */
        public final Map<String, Mesh> alts = new HashMap<>();
    }

    public String name, anim;
    public float voxelSize;
    public float height, width, depth;    // em unidades de mundo
    public int heightVoxels;
    public JsonObject meta;
    public final List<Part> parts = new ArrayList<>();
    public final Map<String, Integer> index = new HashMap<>();
    /** Atlas de pixel art (apenas rigs do tipo "cuboid"); null para rigs voxel. */
    public Texture atlas;

    public static CharacterModel load(Path dir) { return load(dir, true); }

    /** Só o esqueleto (pivôs, hierarquia, meta), sem malhas nem OpenGL: testes e validação offline. */
    public static CharacterModel loadSkeleton(Path dir) { return load(dir, false); }

    private static CharacterModel load(Path dir, boolean meshes) {
        try {
            JsonObject doc = JsonParser.parseString(Files.readString(dir.resolve("rig.json"))).getAsJsonObject();
            CharacterModel m = new CharacterModel();
            m.name = doc.get("name").getAsString();
            m.anim = doc.get("anim").getAsString();
            m.voxelSize = doc.get("voxelSize").getAsFloat();
            m.meta = doc.has("meta") ? doc.getAsJsonObject("meta") : new JsonObject();
            JsonArray size = doc.getAsJsonArray("sizeVoxels");
            m.heightVoxels = size.get(1).getAsInt();
            m.height = m.heightVoxels * m.voxelSize;
            m.width = size.get(0).getAsInt() * m.voxelSize;
            m.depth = size.get(2).getAsInt() * m.voxelSize;
            boolean cuboid = "cuboid".equals(doc.get("type").getAsString());
            boolean affine = doc.has("affineWind") && doc.get("affineWind").getAsBoolean();
            if (cuboid && meshes) m.atlas = Texture.load(dir.resolve(doc.get("atlas").getAsString()), true);
            JsonArray parts = doc.getAsJsonArray("parts");
            for (int i = 0; i < parts.size(); i++) {
                JsonObject p = parts.get(i).getAsJsonObject();
                Part part = new Part();
                part.name = p.get("name").getAsString();
                JsonArray pv = p.getAsJsonArray("pivot");
                part.px = pv.get(0).getAsFloat() * m.voxelSize;
                part.py = pv.get(1).getAsFloat() * m.voxelSize;
                part.pz = pv.get(2).getAsFloat() * m.voxelSize;
                if (!meshes) {
                    // sem malha
                } else if (cuboid) {
                    part.mesh = CuboidMesh.build(p.getAsJsonArray("cuboids"), m.voxelSize, m.atlas.width, m.atlas.height);
                } else {
                    JsonArray o = p.getAsJsonArray("origin");
                    part.mesh = VoxelMesher.build(VoxModel.load(dir.resolve(p.get("file").getAsString())), m.voxelSize,
                            o.get(0).getAsFloat(), o.get(1).getAsFloat(), o.get(2).getAsFloat(), affine);
                    if (p.has("alts")) {
                        JsonObject alts = p.getAsJsonObject("alts");
                        for (String key : alts.keySet()) {
                            JsonObject a = alts.getAsJsonObject(key);
                            JsonArray ao = a.getAsJsonArray("origin");
                            part.alts.put(key, VoxelMesher.build(VoxModel.load(dir.resolve(a.get("file").getAsString())), m.voxelSize,
                                    ao.get(0).getAsFloat(), ao.get(1).getAsFloat(), ao.get(2).getAsFloat()));
                        }
                    }
                }
                m.index.put(part.name, i);
                m.parts.add(part);
            }
            for (int i = 0; i < parts.size(); i++) {
                JsonObject p = parts.get(i).getAsJsonObject();
                if (p.has("parent") && !p.get("parent").isJsonNull()) {
                    Integer pi = m.index.get(p.get("parent").getAsString());
                    if (pi == null) throw new IOException("parte pai inexistente: " + p.get("parent"));
                    if (pi >= i) throw new IOException("a parte pai deve vir antes da filha: " + m.parts.get(i).name);
                    m.parts.get(i).parent = pi;
                }
            }
            return m;
        } catch (IOException e) {
            throw new IllegalStateException("falha ao carregar rig em " + dir + ": " + e.getMessage(), e);
        }
    }
}
