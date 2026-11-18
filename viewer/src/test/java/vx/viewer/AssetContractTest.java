package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vx.engine.VoxModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica, sem OpenGL, que os assets gerados obedecem ao contrato (docs/art/contrato-de-assets.md).
 * Rode `python3 tools/build_assets.py` antes.
 */
class AssetContractTest {
    private static final Path ASSETS = Path.of("assets");
    private static final String[] STYLES = {"a", "b", "c", "d", "e"};

    private static List<Path> rigDirs() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String top : new String[]{"characters", "creatures"}) {
            Path root = ASSETS.resolve(top);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> s = Files.walk(root, 2)) {
                s.filter(p -> Files.exists(p.resolve("rig.json"))).forEach(out::add);
            }
        }
        return out;
    }

    @Test
    void everyRigIsWellFormed() throws IOException {
        List<Path> dirs = rigDirs();
        assertTrue(dirs.size() >= 7, "esperados ao menos os rigs da direção A; rode tools/build_assets.py");
        for (Path dir : dirs) {
            JsonObject doc = JsonParser.parseString(Files.readString(dir.resolve("rig.json"))).getAsJsonObject();
            assertTrue(doc.get("voxelSize").getAsFloat() > 0, dir + ": voxelSize");
            assertTrue(Set.of("biped", "quadruped", "hopper", "floater", "flutter", "brawler").contains(doc.get("anim").getAsString()), dir + ": anim");
            boolean cuboid = "cuboid".equals(doc.get("type").getAsString());
            Set<String> seen = new HashSet<>();
            JsonArray parts = doc.getAsJsonArray("parts");
            assertTrue(parts.size() > 0, dir + ": sem partes");
            for (int i = 0; i < parts.size(); i++) {
                JsonObject p = parts.get(i).getAsJsonObject();
                String name = p.get("name").getAsString();
                assertTrue(seen.add(name), dir + ": parte duplicada " + name);
                if (p.has("parent") && !p.get("parent").isJsonNull())
                    assertTrue(seen.contains(p.get("parent").getAsString()), dir + ": pai de " + name + " deve vir antes");
                assertEquals(3, p.getAsJsonArray("pivot").size(), dir + ": pivot de " + name);
                if (cuboid) {
                    assertTrue(p.getAsJsonArray("cuboids").size() > 0, dir + ": parte sem cuboides " + name);
                } else {
                    VoxModel m = VoxModel.load(dir.resolve(p.get("file").getAsString()));
                    assertTrue(m.sizeX() > 0 && m.sizeY() > 0 && m.sizeZ() > 0, dir + ": " + name);
                }
            }
            if ("biped".equals(doc.get("anim").getAsString()))
                for (String need : new String[]{"torso", "head", "arm_l", "arm_r", "leg_l", "leg_r"})
                    assertTrue(seen.contains(need), dir + ": bípede sem a parte " + need);
        }
    }

    @Test
    void heroesAreCompact() throws IOException {
        // Critério central da rodada: personagens jogáveis com no máximo 3 cabeças de altura.
        for (String s : STYLES)
            for (String c : new String[]{"adventurer", "ranger", "mage"}) {
                Path rig = ASSETS.resolve("characters/" + s + "/" + c + "/rig.json");
                if (!Files.exists(rig)) continue;
                JsonObject meta = JsonParser.parseString(Files.readString(rig)).getAsJsonObject().getAsJsonObject("meta");
                assertTrue(meta.has("headsTall"), rig + ": meta.headsTall ausente");
                double heads = meta.get("headsTall").getAsDouble();
                assertTrue(heads >= 2.0 && heads <= 3.0, rig + ": " + heads + " cabeças (esperado entre 2 e 3)");
            }
    }

    @Test
    void propManifestsPointToExistingFiles() throws IOException {
        for (String s : STYLES) {
            Path manifest = ASSETS.resolve("environment/" + s + "/props.json");
            if (!Files.exists(manifest)) continue;
            JsonObject doc = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            for (String name : doc.keySet()) {
                JsonObject e = doc.getAsJsonObject(name);
                assertTrue(e.get("voxelSize").getAsFloat() > 0, manifest + ": " + name);
                if (e.has("cuboids")) assertTrue(Files.exists(manifest.resolveSibling("atlas.png")), manifest + ": atlas.png");
                else assertTrue(Files.exists(manifest.resolveSibling(e.get("file").getAsString())), manifest + ": " + name);
            }
        }
    }

    @Test
    void terrainIsDeterministicAndShared() {
        assertEquals(World.height(3.25f, -7.5f), World.height(3.25f, -7.5f));
        // o lago fica abaixo do nível da água e a base da casa é um platô
        assertTrue(World.height(7.8f, 4.6f) < World.WATER_LEVEL);
        assertEquals(World.height(-6.5f, -8.0f), World.height(-6.0f, -8.2f), 0.12f);
    }

    // ------------------------------------------------------------------ rodada 02: direção E

    private static JsonObject rig(String dir) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve(dir).resolve("rig.json"))).getAsJsonObject();
    }

    @Test
    void consolidatedHeroesFollowTheIdentity() throws IOException {
        // docs/art/sistema-de-proporcoes.md: 2,35–2,60 cabeças (chapéu/capuz fora), ≤ 1,25 u, ≤ 9 mil faces,
        // as quatro expressões e o modo de arma declarado.
        for (String c : new String[]{"adventurer", "ranger", "mage"}) {
            JsonObject doc = rig("characters/e/" + c);
            JsonObject meta = doc.getAsJsonObject("meta");
            double heads = meta.get("headsTall").getAsDouble();
            assertTrue(heads >= 2.35 && heads <= 2.60, c + ": " + heads + " cabeças (alvo 2,35–2,60)");
            double h = doc.getAsJsonArray("sizeVoxels").get(1).getAsInt() * doc.get("voxelSize").getAsDouble();
            assertTrue(h <= 1.251, c + ": altura " + h + " u (máx. 1,25)");
            assertTrue(meta.get("faces").getAsInt() <= 9000, c + ": orçamento de faces");
            assertEquals(1.0 / 40, doc.get("voxelSize").getAsDouble(), 1e-6, c + ": voxel do herói E");
            assertTrue(meta.has("weaponMode"), c + ": meta.weaponMode");
            JsonObject head = null;
            for (var p : doc.getAsJsonArray("parts")) if (p.getAsJsonObject().get("name").getAsString().equals("head")) head = p.getAsJsonObject();
            assertTrue(head != null && head.has("alts"), c + ": cabeça sem expressões");
            for (String e : new String[]{"joy", "determined", "surprise"}) {
                assertTrue(head.getAsJsonObject("alts").has(e), c + ": falta a expressão " + e);
                assertTrue(Files.exists(ASSETS.resolve("characters/e/" + c).resolve(head.getAsJsonObject("alts").getAsJsonObject(e).get("file").getAsString())));
            }
        }
    }

    @Test
    void adventurerVariantsExistForComparison() throws IOException {
        for (String v : new String[]{"v1", "v2", "v3"}) {
            double heads = rig("characters/variants/" + v).getAsJsonObject("meta").get("headsTall").getAsDouble();
            assertTrue(heads >= 2.0 && heads <= 3.0, v + ": " + heads);
        }
    }

    @Test
    void consolidatedEnvironmentHasTheTreeLibrary() throws IOException {
        JsonObject doc = JsonParser.parseString(Files.readString(ASSETS.resolve("environment/e/props.json"))).getAsJsonObject();
        for (String n : new String[]{"tree_oak", "tree_oak2", "tree_oak3", "tree_oak4", "tree_pine", "tree_birch", "tree_big", "house"})
            assertTrue(doc.has(n), "environment/e sem " + n);
        // folhagem só pode ser fundida com vento afim declarado
        assertTrue(doc.getAsJsonObject("tree_oak").get("affineWind").getAsBoolean());
        assertTrue(Files.exists(ASSETS.resolve("scenes/clearing_e.json")), "composição da E");
    }

    // ------------------------------------------------------------------ rodada 03: rig articulado

    @Test
    void consolidatedHeroesHaveElbowsAndKnees() throws IOException {
        // cotovelo e joelho: farm_* filho de arm_*, shin_* filho de leg_*; armas presas ao antebraço
        for (String c : new String[]{"adventurer", "ranger", "mage"}) {
            JsonObject doc = rig("characters/e/" + c);
            java.util.Map<String, String> parent = new java.util.HashMap<>();
            for (var p : doc.getAsJsonArray("parts")) {
                JsonObject o = p.getAsJsonObject();
                parent.put(o.get("name").getAsString(), o.get("parent").isJsonNull() ? null : o.get("parent").getAsString());
            }
            for (String side : new String[]{"l", "r"}) {
                assertEquals("arm_" + side, parent.get("farm_" + side), c + ": farm_" + side);
                assertEquals("leg_" + side, parent.get("shin_" + side), c + ": shin_" + side);
            }
            if (parent.containsKey("weapon")) assertEquals("farm_r", parent.get("weapon"), c + ": arma na mão (antebraço)");
            if (parent.containsKey("bow")) assertEquals("farm_l", parent.get("bow"), c + ": arco na mão esquerda");
            assertTrue(doc.getAsJsonObject("meta").get("joints").getAsBoolean(), c + ": meta.joints");
        }
        assertEquals("bow", rigParent("characters/e/ranger", "arrow"), "flecha encaixada no arco");
    }

    private static String rigParent(String dir, String part) throws IOException {
        for (var p : rig(dir).getAsJsonArray("parts")) {
            JsonObject o = p.getAsJsonObject();
            if (o.get("name").getAsString().equals(part)) return o.get("parent").getAsString();
        }
        return null;
    }

    @Test
    void creaturesDoNotShareTheHeroFace() throws IOException {
        // bestiário da rodada 03: cada criatura declara seu nível de complexidade e fica no orçamento
        int[] budget = {0, 3000, 6000, 15000};
        for (String c : new String[]{"pompom", "lumi", "fungo", "tortuga"}) {
            JsonObject meta = rig("creatures/e/" + c).getAsJsonObject("meta");
            int tier = meta.get("tier").getAsInt();
            assertTrue(meta.get("faces").getAsInt() <= budget[tier], c + ": " + meta.get("faces") + " faces no nível " + tier);
        }
    }
}
