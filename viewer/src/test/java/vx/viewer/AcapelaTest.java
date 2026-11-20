package vx.viewer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acapela (NPC, E; tools/npc_acapela_e.py): criaturinha de rabo de lagarto e cabeça grande franjada. O contrato de NPC
 * vale inteiro (esqueleto da vila, voxel 1/40, 4 expressões, tronco sem volume extra, validador espacial), com o que é
 * dele: proporção chibi (2,0–2,35 cabeças: abaixo da faixa congelada dos protagonistas), a parte "tail" e o perfil
 * npc_acapela (o aceno passa pela frente das franjas). Rode build_assets antes.
 */
class AcapelaTest {
    static final String RIG = "characters/npc/acapela";
    static final List<String> OUTROS = List.of("characters/npc/cartografa", "characters/npc/kelyra", "characters/npc/indira",
            "characters/npc/suvia", "characters/npc/bramor", "characters/npc/teodor", "characters/npc/pluma",
            "characters/npc/ondina", "characters/npc/lunara", "characters/npc/brasia", "characters/npc/quelmo",
            "characters/npc/arvel");

    static JsonObject doc() throws IOException { return ExpansionTest.json(RIG + "/rig.json"); }

    static JsonObject meta() throws IOException { return doc().getAsJsonObject("meta"); }

    static JsonObject perfil(String name) throws IOException {
        return JsonParser.parseString(Files.readString(VilaNpcTest.A.resolve("anim/" + name + ".json"))).getAsJsonObject();
    }

    @Test
    void followsTheNpcContractWithChibiProportions() throws IOException {
        JsonObject d = doc(), m = meta();
        double heads = m.get("headsTall").getAsDouble();
        assertTrue(heads >= 2.0 && heads < 2.35, "proporção chibi de NPC (2,0–2,35, abaixo dos protagonistas): " + heads);
        assertEquals(1.0 / 40, d.get("voxelSize").getAsDouble(), 1e-6);
        double s = Math.max(1.0, m.get("escala").getAsDouble());
        assertTrue(m.get("faces").getAsInt() <= 9000 * s * s, "orçamento de faces: " + m.get("faces").getAsInt());
        assertTrue(m.get("npc").getAsBoolean());
        assertEquals("none", m.get("weaponMode").getAsString());
        assertEquals("npc_acapela", m.get("weaponProfile").getAsString());
        Set<String> parts = new HashSet<>();
        JsonObject head = null;
        for (var p : d.getAsJsonArray("parts")) {
            String n = p.getAsJsonObject().get("name").getAsString();
            parts.add(n);
            if (n.equals("head")) head = p.getAsJsonObject();
        }
        for (String p : List.of("torso", "head", "tail", "arm_l", "farm_l", "arm_r", "farm_r", "leg_l", "shin_l", "leg_r", "shin_r"))
            assertTrue(parts.contains(p), "parte ausente " + p);
        for (String e : List.of("joy", "determined", "surprise"))
            assertTrue(head.getAsJsonObject("alts").has(e), "expressão ausente " + e);
    }

    @Test
    void narrativeHeightFitsTheCast() throws IOException {
        JsonObject m = meta();
        double h = m.get("worldUnits").getAsDouble();
        assertEquals(m.get("metersNarrative").getAsDouble() * 0.75, h, 1.5 / 40, "altura no mundo");
        double min = Double.MAX_VALUE, max = 0;
        for (String o : OUTROS) {
            JsonObject om = ExpansionTest.json(o + "/rig.json").getAsJsonObject("meta");
            double oh = om.has("worldUnits") ? om.get("worldUnits").getAsDouble() : om.get("bodyHeightVoxels").getAsDouble() / 40;
            min = Math.min(min, oh);
            max = Math.max(max, oh);
        }
        assertTrue(h >= min - 1e-6 && h <= max, h + " u fora da faixa do elenco [" + min + ", " + max + "]");
    }

    @Test
    void noLooseFragments() throws IOException {
        try (var files = Files.list(VilaNpcTest.A.resolve(RIG))) {
            for (Path f : files.filter(q -> q.toString().endsWith(".vox")).toList()) ExpansionTest.noFragments(f);
        }
    }

    @Test
    void notARecolorOfAnyOtherNpc() throws IOException {
        for (String o : OUTROS) {
            double body = VilaNpcTest.jaccard(VilaNpcTest.rigCells(RIG, null), VilaNpcTest.rigCells(o, null));
            double head = VilaNpcTest.jaccard(VilaNpcTest.rigCells(RIG, Set.of("head", "hat")), VilaNpcTest.rigCells(o, Set.of("head", "hat")));
            assertTrue(body < 0.88, o + ": corpo quase idêntico (" + body + ")");
            assertTrue(head < 0.85, o + ": cabeça quase idêntica (" + head + ")");
        }
    }

    @Test
    void expressionsActDifferently() throws IOException {
        List<Set<Long>> sets = new ArrayList<>();
        for (String e : List.of("joy", "determined", "surprise")) {
            Set<Long> d = QualityTest.changedCells(RIG, e);
            assertTrue(d.size() >= 6, e + ": só " + d.size() + " voxels mudam");
            sets.add(d);
        }
        for (int i = 0; i < sets.size(); i++)
            for (int j = i + 1; j < sets.size(); j++)
                assertTrue(VilaNpcTest.jaccard(sets.get(i), sets.get(j)) < 0.6, "duas expressões pintam o mesmo desenho");
    }

    @Test
    void torsoHasNoExtraFrontVolume() throws IOException {
        int maxZ = Integer.MIN_VALUE;
        for (long k : VilaNpcTest.rigCells(RIG, Set.of("torso"))) maxZ = Math.max(maxZ, QualityTest.z(k));
        int limit = (int) Math.ceil(5 * Math.max(1.0, meta().get("escala").getAsDouble()));
        assertTrue(maxZ <= limit, "tronco projeta até z = " + maxZ + " (limite " + limit + ")");
    }

    @Test
    void tailIsAttachedToTheBody() throws IOException {
        // a parte "tail" (filha do tronco) encosta no tronco: nada de rabo flutuando atrás do corpo
        Set<Long> torso = VilaNpcTest.rigCells(RIG, Set.of("torso")), tail = VilaNpcTest.rigCells(RIG, Set.of("tail"));
        assertFalse(tail.isEmpty(), "sem rabo");
        long contact = tail.stream().filter(k -> MundoNpcTest.touches(k, torso) || torso.contains(k)).count();
        assertTrue(contact >= 6, "o rabo encosta no tronco em só " + contact + " voxels");
    }

    @Test
    void gesturesPassTheSpatialValidator() {
        CharacterModel m = CharacterModel.loadSkeleton(VilaNpcTest.A.resolve(RIG));
        for (String seq : List.of("emote", "clip:WALK", "clip:RUN", "seq:walk:0.8,emote,walk:0.8", "seq:run:1.0,emote,run:1.0")) {
            WeaponCheck.Report r = WeaponCheck.validate(m, seq);
            assertTrue(r.ok(), seq + ": " + r);
        }
    }

    @Test
    void ownProfileOnlyChangesTheWave() throws IOException {
        // npc_acapela = npc_vila com o aceno trazido para a frente das franjas: repouso, balanço e defaults iguais
        JsonObject vila = perfil("npc_vila"), a = perfil("npc_acapela");
        assertEquals(vila.getAsJsonObject("poses").get("rest"), a.getAsJsonObject("poses").get("rest"), "pose de repouso");
        assertEquals(vila.get("carry"), a.get("carry"), "balanço das mãos livres");
        assertEquals(vila.get("defaults"), a.get("defaults"));
        var keys = a.getAsJsonObject("moves").getAsJsonObject("emote").getAsJsonArray("keys");
        assertEquals("rest", keys.get(0).getAsJsonObject().get("pose").getAsString());
        assertEquals("rest", keys.get(keys.size() - 1).getAsJsonObject().get("pose").getAsString());
    }

    @Test
    void limbsTailAndHeadDoNotPassThroughEachOther() throws Exception {
        // mesma medida dos NPCs de ofício (NpcClipProbe): nenhum grupo acima do pior NPC aprovado; braço × tronco e
        // membro × rabo no máximo 1 voxel na escala do rig (0,025 u); o rabo nunca entra no chão
        Map<String, Float> limit = new HashMap<>();
        for (String o : OficiosNpcTest.OUTROS)
            for (var e : NpcClipProbe.measure(VilaNpcTest.A.resolve(o)).entrySet()) limit.merge(e.getKey(), e.getValue().depthU(), Math::max);
        for (String o : OficiosNpcTest.NPCS)
            for (var e : NpcClipProbe.measure(VilaNpcTest.A.resolve(o)).entrySet()) limit.merge(e.getKey(), e.getValue().depthU(), Math::max);
        Map<String, NpcClipProbe.Worst> got = NpcClipProbe.measure(VilaNpcTest.A.resolve(RIG));
        assertTrue(got.containsKey("membro × rabo") && got.containsKey("rabo × chão"), "a sonda não mediu o rabo");
        for (var e : got.entrySet()) {
            float d = e.getValue().depthU();
            String what = String.format(Locale.ROOT, "%s: %.3f u em %s", e.getKey(), d, e.getValue().where());
            switch (e.getKey()) {
                case "membro × rabo" -> assertTrue(d <= 0.025f + 1e-4f, what);
                case "rabo × chão" -> assertEquals(0f, d, 1e-4f, what);
                default -> assertTrue(d <= limit.get(e.getKey()) + 1e-4f, what + " (aprovados: " + limit.get(e.getKey()) + ")");
            }
        }
    }

    @Test
    void metadataSaysOnlyWhatItIsInTheGame() throws IOException {
        String d = Files.readString(VilaNpcTest.A.resolve(RIG).resolve("rig.json")).toLowerCase(Locale.ROOT);
        for (String w : List.of("foto", "refer", "homenag", "baseado", "baseada", "inspirad", "desenho d", "semelhan", "fanart", "fan art"))
            assertFalse(d.contains(w), "metadado menciona \"" + w + "\"");
    }
}
