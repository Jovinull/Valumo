package vx.viewer;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 06.1 — prova técnica de personalização modular (tools/custom_e.py → assets/characters/custom/).
 * Prova o que a documentação marca como "validado experimentalmente": o preset recomposto por componentes é
 * idêntico ao canônico; cor e estilo de cabelo trocam só o cabelo; o chapéu esconde o cabelo sem interseção;
 * a animação continua válida. NÃO prova composição em tempo de execução (planejada). Rode build_assets antes.
 */
class ModularTest {
    static final Path A = Path.of("assets");
    static final String CANON = "characters/e/adventurer", C = "characters/custom/";

    /** Voxels de partes (ou de uma expressão da cabeça) no espaço do modelo → cor. */
    static Map<Long, Integer> cells(String rig, List<String> names, String expr) throws IOException {
        Map<Long, Integer> out = new HashMap<>();
        JsonObject doc = ExpansionTest.json(rig + "/rig.json");
        for (var pe : doc.getAsJsonArray("parts")) {
            JsonObject p = pe.getAsJsonObject();
            String n = p.get("name").getAsString();
            if (!names.contains(n)) continue;
            String file = p.get("file").getAsString();
            var o = p.getAsJsonArray("origin");
            if (expr != null && n.equals("head")) {
                JsonObject alt = p.getAsJsonObject("alts").getAsJsonObject(expr);
                file = alt.get("file").getAsString();
                o = alt.getAsJsonArray("origin");
            }
            var m = vx.engine.VoxModel.load(A.resolve(rig).resolve(file));
            for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++)
                if (m.get(x, y, z) != 0)
                    out.put(QualityTest.key(x + o.get(0).getAsInt(), y + o.get(1).getAsInt(), z + o.get(2).getAsInt()), m.get(x, y, z));
        }
        return out;
    }

    static String sha(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    @Test
    void presetRecomposedFromComponentsIsIdenticalToTheCanonicalHero() throws Exception {
        for (String e : new String[]{null, "joy", "determined", "surprise"})
            assertEquals(cells(CANON, List.of("head"), e), cells(C + "c0_preset", List.of("head", "hair"), e),
                    "cabeça + cabelo recompostos = cabeça canônica (" + (e == null ? "neutral" : e) + ")");
        for (String n : List.of("torso", "pack", "scarf", "arm_l", "farm_l", "arm_r", "farm_r", "leg_l", "shin_l", "leg_r", "shin_r", "weapon"))
            assertEquals(sha(A.resolve(CANON).resolve(n + ".vox")), sha(A.resolve(C + "c0_preset").resolve(n + ".vox")), n + " copiado sem mudança");
        JsonObject a = ExpansionTest.json(CANON + "/rig.json").getAsJsonObject("meta"), b = ExpansionTest.json(C + "c0_preset/rig.json").getAsJsonObject("meta");
        assertEquals(a.get("headsTall").getAsDouble(), b.get("headsTall").getAsDouble(), 1e-9, "mesma proporção");
    }

    @Test
    void hairColorChangesOnlyColorsAndHairStyleChangesOnlyTheHair() throws IOException {
        var c0 = cells(C + "c0_preset", List.of("hair"), null);
        var c1 = cells(C + "c1_cabelo_castanho", List.of("hair"), null);
        var c2 = cells(C + "c2_cabelo_natural", List.of("hair"), null);
        assertEquals(c0.keySet(), c1.keySet(), "trocar a cor não muda a forma");
        assertNotEquals(c0, c1, "a cor mudou");
        assertNotEquals(c0.keySet(), c2.keySet(), "o estilo muda a forma");
        assertEquals(cells(C + "c0_preset", List.of("torso", "arm_l", "leg_l"), null), cells(C + "c2_cabelo_natural", List.of("torso", "arm_l", "leg_l"), null),
                "o corpo não muda com o cabelo");
    }

    @Test
    void hatHidesHairInsteadOfOverlappingOrDeformingIt() throws Exception {
        for (String c : List.of("c3_chapeu", "c4_natural_castanho_chapeu")) {
            var hat = cells(C + c, List.of("hat"), null);
            var hair = cells(C + c, List.of("hair"), null);
            assertEquals(sha(A.resolve("characters/e/mage/hat.vox")), sha(A.resolve(C + c).resolve("hat.vox")), "chapéu é o componente da maga, sem ajuste");
            int y0 = hat.keySet().stream().mapToInt(QualityTest::y).min().orElseThrow();
            java.util.Set<Long> cols = new java.util.HashSet<>();
            for (long k : hat.keySet()) cols.add(QualityTest.key(VilaNpcTest.xOf(k), 0, QualityTest.z(k)));
            for (long k : hair.keySet()) {
                assertFalse(hat.containsKey(k), c + ": cabelo e chapéu no mesmo voxel");
                assertFalse(QualityTest.y(k) >= y0 && cols.contains(QualityTest.key(VilaNpcTest.xOf(k), 0, QualityTest.z(k))),
                        c + ": cabelo dentro da coluna do chapéu acima da aba (variante sob_chapeu não aplicada)");
            }
            var sem = cells(C + (c.startsWith("c3") ? "c0_preset" : "c2_cabelo_natural"), List.of("hair"), null).keySet();
            assertTrue(sem.containsAll(hair.keySet()), c + ": a variante só remove cabelo, não acrescenta nem desloca");
        }
    }

    @Test
    void composedCharactersKeepValidAnimation() {
        CharacterModel m = CharacterModel.loadSkeleton(A.resolve(C + "c4_natural_castanho_chapeu"));
        for (String seq : List.of("clip:WALK", "clip:RUN", "slash_h", "emote", "hit"))
            assertTrue(WeaponCheck.validate(m, seq).ok(), "c4 / " + seq);
    }
}
