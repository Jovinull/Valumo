package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import vx.engine.Mesh;
import vx.engine.VoxModel;
import vx.engine.VoxelMesher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Carrega e mantém em cache os assets de {@code assets/} (rigs, props). */
public final class Assets {
    /** Modelo estático: malha com origem no ponto de apoio (x,z centrados, y=0 no chão). */
    public record Prop(Mesh mesh, float voxelSize, float height, float width, vx.engine.Texture texture) { }

    public static final String[] STYLE_KEYS = {"a", "b", "c", "d", "e"};
    /** Ordem de apresentação dos personagens; os que existirem no estilo aparecem nesta ordem. */
    private static final List<String> CHARACTER_ORDER = List.of("adventurer", "ranger", "mage");

    private final Path root;
    private final Map<String, CharacterModel> rigs = new HashMap<>();
    private final Map<String, Map<String, Prop>> props = new HashMap<>();

    public Assets(Path root) { this.root = root; }

    public Path root() { return root; }

    public CharacterModel rig(String relativeDir) {
        return rigs.computeIfAbsent(relativeDir, d -> CharacterModel.load(root.resolve(d)));
    }

    public boolean exists(String relative) { return Files.exists(root.resolve(relative)); }

    /** Nomes dos personagens jogáveis disponíveis no estilo, na ordem de apresentação. */
    public List<String> characters(String styleKey) {
        List<String> found = subdirs("characters/" + styleKey);
        List<String> out = new ArrayList<>();
        for (String c : CHARACTER_ORDER) if (found.contains(c)) out.add(c);
        for (String c : found) if (!out.contains(c)) out.add(c);
        return out;
    }

    public List<String> subdirs(String relative) {
        Path dir = root.resolve(relative);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> Files.exists(p.resolve("rig.json")))
                    .map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Props do diretório {@code environment/<estilo>} (lidos de props.json). */
    public Map<String, Prop> props(String styleKey) {
        return props.computeIfAbsent(styleKey, k -> {
            Map<String, Prop> out = new HashMap<>();
            Path dir = root.resolve("environment/" + k);
            Path manifest = dir.resolve("props.json");
            if (!Files.exists(manifest)) return out;
            try {
                JsonObject doc = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
                // Props de cuboides texturizados (direção C) compartilham environment/<estilo>/atlas.png.
                vx.engine.Texture atlas = Files.exists(dir.resolve("atlas.png"))
                        ? vx.engine.Texture.load(dir.resolve("atlas.png"), true) : null;
                for (String name : doc.keySet()) {
                    JsonObject e = doc.getAsJsonObject(name);
                    float vs = e.get("voxelSize").getAsFloat();
                    JsonArray sz = e.getAsJsonArray("sizeVoxels");
                    float h = sz.get(1).getAsFloat() * vs, w = sz.get(0).getAsFloat() * vs;
                    if (e.has("cuboids")) {
                        if (atlas == null) throw new IOException("atlas.png ausente em " + dir);
                        out.put(name, new Prop(CuboidMesh.build(e.getAsJsonArray("cuboids"), vs, atlas.width, atlas.height), vs, h, w, atlas));
                    } else {
                        JsonArray o = e.getAsJsonArray("origin");
                        boolean affine = e.has("affineWind") && e.get("affineWind").getAsBoolean();
                        Mesh mesh = VoxelMesher.build(VoxModel.load(dir.resolve(e.get("file").getAsString())), vs,
                                o.get(0).getAsFloat(), o.get(1).getAsFloat(), o.get(2).getAsFloat(), affine);
                        out.put(name, new Prop(mesh, vs, h, w, null));
                    }
                }
            } catch (IOException ex) {
                throw new IllegalStateException("falha ao ler " + manifest, ex);
            }
            return out;
        });
    }
}
