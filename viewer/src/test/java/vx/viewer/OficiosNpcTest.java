package vx.viewer;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NPCs de ofício (Bramor, Teodor, Pluma, Ondina, Lunara; tools/npcs_oficios_e.py). É o mesmo contrato dos NPCs da
 * vila ({@link VilaNpcTest}), com uma diferença: estes têm ALTURA NARRATIVA, que vem de reamostrar o rig pronto
 * ({@code meta.escala}). A proporção em cabeças e o voxel não mudam. Os limites que dependem do tamanho escalam com o
 * rig: o orçamento de faces pela área (escala²) e a profundidade do tronco pela escala. Num NPC de tamanho normal os
 * limites são os mesmos da vila. Rode build_assets antes.
 */
class OficiosNpcTest {
    static final List<String> NPCS = List.of("characters/npc/bramor", "characters/npc/teodor", "characters/npc/pluma",
            "characters/npc/ondina", "characters/npc/lunara");
    static final List<String> OUTROS = List.of("characters/npc/cartografa", "characters/npc/kelyra",
            "characters/npc/indira", "characters/npc/suvia");

    static JsonObject meta(String rig) throws IOException {
        return ExpansionTest.json(rig + "/rig.json").getAsJsonObject("meta");
    }

    static double escala(String rig) throws IOException {
        JsonObject m = meta(rig);
        return m.has("escala") ? m.get("escala").getAsDouble() : 1.0;
    }

    @Test
    void followTheNpcContractAtTheirOwnScale() throws IOException {
        for (String rig : NPCS) {
            JsonObject doc = ExpansionTest.json(rig + "/rig.json"), meta = doc.getAsJsonObject("meta");
            double heads = meta.get("headsTall").getAsDouble(), s = Math.max(1.0, escala(rig));
            assertTrue(heads >= 2.0 && heads <= 3.0, rig + ": fora da faixa de NPC 2,0–3,0 cabeças: " + heads);
            assertTrue(heads > 2.65, rig + ": dentro da faixa congelada dos protagonistas: " + heads);
            assertEquals(1.0 / 40, doc.get("voxelSize").getAsDouble(), 1e-6, rig);
            assertTrue(meta.get("faces").getAsInt() <= 9000 * s * s,
                    rig + ": orçamento de faces na escala dele (" + meta.get("faces").getAsInt() + " > " + Math.round(9000 * s * s) + ")");
            assertTrue(meta.get("npc").getAsBoolean(), rig);
            assertEquals("npc_vila", meta.get("weaponProfile").getAsString(), rig + ": gesto compartilhado");
            Set<String> parts = new HashSet<>();
            JsonObject head = null;
            for (var p : doc.getAsJsonArray("parts")) {
                String n = p.getAsJsonObject().get("name").getAsString();
                parts.add(n);
                if (n.equals("head")) head = p.getAsJsonObject();
            }
            for (String p : List.of("torso", "head", "arm_l", "farm_l", "arm_r", "farm_r", "leg_l", "shin_l", "leg_r", "shin_r"))
                assertTrue(parts.contains(p), rig + ": parte ausente " + p);
            for (String e : List.of("joy", "determined", "surprise"))
                assertTrue(head.getAsJsonObject("alts").has(e), rig + ": expressão ausente " + e);
        }
    }

    @Test
    void narrativeHeightsKeepTheirRatio() throws IOException {
        // 0,75 u por metro narrativo; os três homens mantêm a relação 2,30 : 1,80 : 1,60
        for (String rig : NPCS) {
            JsonObject m = meta(rig);
            double want = m.get("metersNarrative").getAsDouble() * 0.75;
            assertEquals(want, m.get("worldUnits").getAsDouble(), 1.5 / 40, rig + ": altura no mundo");
        }
        double b = meta(NPCS.get(0)).get("worldUnits").getAsDouble(), t = meta(NPCS.get(1)).get("worldUnits").getAsDouble(),
                p = meta(NPCS.get(2)).get("worldUnits").getAsDouble();
        assertTrue(b > t && t > p, "Bramor > Teodor > Pluma");
        assertEquals(2.30 / 1.80, b / t, 0.03);
        assertEquals(1.80 / 1.60, t / p, 0.03);
    }

    @Test
    void noLooseFragments() throws IOException {
        for (String rig : NPCS)
            try (var files = Files.list(VilaNpcTest.A.resolve(rig))) {
                for (Path f : files.filter(q -> q.toString().endsWith(".vox")).toList()) ExpansionTest.noFragments(f);
            }
    }

    @Test
    void notRecolorsOfEachOtherNorOfTheVillage() throws IOException {
        // mesmo esqueleto de NPC é permitido; a forma (cabelo, roupa, acessório) tem de mudar — também contra os da vila
        List<String> todos = new ArrayList<>(NPCS);
        todos.addAll(OUTROS);
        for (int i = 0; i < NPCS.size(); i++)
            for (int j = i + 1; j < todos.size(); j++) {
                String a = todos.get(i), c = todos.get(j);
                double body = VilaNpcTest.jaccard(VilaNpcTest.rigCells(a, null), VilaNpcTest.rigCells(c, null));
                double head = VilaNpcTest.jaccard(VilaNpcTest.rigCells(a, Set.of("head")), VilaNpcTest.rigCells(c, Set.of("head")));
                assertTrue(body < 0.88, a + " × " + c + ": corpo quase idêntico (" + body + ")");
                assertTrue(head < 0.85, a + " × " + c + ": cabeça quase idêntica (" + head + ")");
            }
    }

    @Test
    void expressionsActDifferently() throws IOException {
        for (String e : List.of("joy", "determined", "surprise")) {
            List<Set<Long>> sets = new ArrayList<>();
            for (String rig : NPCS) {
                Set<Long> d = QualityTest.changedCells(rig, e);
                assertTrue(d.size() >= 6, rig + " " + e + ": só " + d.size() + " voxels mudam em relação ao neutro");
                sets.add(d);
            }
            for (int i = 0; i < sets.size(); i++)
                for (int j = i + 1; j < sets.size(); j++)
                    assertTrue(VilaNpcTest.jaccard(sets.get(i), sets.get(j)) < 0.6,
                            e + ": " + NPCS.get(i) + " × " + NPCS.get(j) + " pintam o mesmo desenho");
        }
    }

    @Test
    void torsoHasNoExtraFrontVolume() throws IOException {
        // linguagem dos NPCs da E: tronco em massa única, sem volume de busto; z ≤ 5 na escala do NPC
        for (String rig : NPCS) {
            int maxZ = Integer.MIN_VALUE;
            for (long k : VilaNpcTest.rigCells(rig, Set.of("torso"))) maxZ = Math.max(maxZ, QualityTest.z(k));
            int limit = (int) Math.ceil(5 * Math.max(1.0, escala(rig)));
            assertTrue(maxZ <= limit, rig + ": tronco projeta até z = " + maxZ + " (limite na escala dele: " + limit + ")");
        }
    }

    @Test
    void sharedGesturePassesTheSpatialValidator() {
        for (String rig : NPCS) {
            CharacterModel m = CharacterModel.loadSkeleton(VilaNpcTest.A.resolve(rig));
            for (String seq : List.of("emote", "clip:WALK", "clip:RUN", "seq:walk:0.8,emote,walk:0.8")) {
                WeaponCheck.Report r = WeaponCheck.validate(m, seq);
                assertTrue(r.ok(), rig + " / " + seq + ": " + r);
            }
        }
    }

    @Test
    void actingIsSubtleAndEachHasARole() throws IOException {
        Set<String> roles = new HashSet<>();
        for (String rig : NPCS) {
            JsonObject m = meta(rig), ac = m.getAsJsonObject("acting");
            for (String k : List.of("headPitch", "headRoll", "torsoPitch", "hipRoll"))
                if (ac.has(k)) assertTrue(Math.abs(ac.get(k).getAsDouble()) <= 8, rig + ": " + k + " exagerado");
            roles.add(m.get("role").getAsString());
        }
        assertEquals(NPCS.size(), roles.size(), "cada NPC tem um ofício próprio");
    }

    @Test
    void metadataSaysOnlyWhatTheyAreInTheGame() throws IOException {
        // regra do proprietário: nada no repositório liga um personagem a uma pessoa real. Aqui só palavras genéricas
        // de vínculo (nunca nomes reais em arquivo); a varredura com os termos reais é feita no terminal.
        for (String rig : NPCS) {
            String doc = Files.readString(VilaNpcTest.A.resolve(rig).resolve("rig.json")).toLowerCase(Locale.ROOT);
            for (String w : List.of("foto", "refer", "homenag", "baseado", "baseada", "inspirad", "desenho d", "semelhan"))
                assertFalse(doc.contains(w), rig + ": metadado menciona \"" + w + "\"");
        }
    }
}
