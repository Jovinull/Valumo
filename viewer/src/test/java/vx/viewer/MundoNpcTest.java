package vx.viewer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Laboratório de conteúdo — NPCs de ofício do mundo procedural (Brásia, Quelmo, Arvel; tools/npcs_mundo_e.py). O
 * contrato é o dos NPCs de ofício ({@link OficiosNpcTest}: esqueleto da vila, altura narrativa por reamostragem, limites
 * na escala do rig), contra TODOS os NPCs aprovados. A diferença: cada um tem perfil próprio (npc_ferreira,
 * npc_feirante, npc_cacador) com o aceno idêntico ao npc_vila e um gesto de ofício curto, e acessórios (avental,
 * bolsa, gorro, coque) que precisam ficar fora do caminho dos membros: medido voxel a voxel nos clipes reais pelo
 * {@link NpcClipProbe}. Rode build_assets antes.
 */
class MundoNpcTest {
    static final List<String> NPCS = List.of("characters/npc/brasia", "characters/npc/quelmo", "characters/npc/arvel");
    static final List<String> APROVADOS = List.of("characters/npc/cartografa", "characters/npc/kelyra",
            "characters/npc/indira", "characters/npc/suvia", "characters/npc/bramor", "characters/npc/teodor",
            "characters/npc/pluma", "characters/npc/ondina", "characters/npc/lunara");
    static final Map<String, String> PERFIS = Map.of("characters/npc/brasia", "npc_ferreira",
            "characters/npc/quelmo", "npc_feirante", "characters/npc/arvel", "npc_cacador");

    static JsonObject meta(String rig) throws IOException {
        return ExpansionTest.json(rig + "/rig.json").getAsJsonObject("meta");
    }

    static JsonObject perfil(String name) throws IOException {
        return JsonParser.parseString(Files.readString(VilaNpcTest.A.resolve("anim/" + name + ".json"))).getAsJsonObject();
    }

    @Test
    void followTheNpcContractAtTheirOwnScale() throws IOException {
        for (String rig : NPCS) {
            JsonObject doc = ExpansionTest.json(rig + "/rig.json"), meta = doc.getAsJsonObject("meta");
            double heads = meta.get("headsTall").getAsDouble(), s = Math.max(1.0, meta.get("escala").getAsDouble());
            assertTrue(heads >= 2.0 && heads <= 3.0, rig + ": fora da faixa de NPC 2,0–3,0 cabeças: " + heads);
            assertTrue(heads > 2.65, rig + ": dentro da faixa congelada dos protagonistas: " + heads);
            assertEquals(1.0 / 40, doc.get("voxelSize").getAsDouble(), 1e-6, rig);
            assertTrue(meta.get("faces").getAsInt() <= 9000 * s * s,
                    rig + ": orçamento de faces na escala dele (" + meta.get("faces").getAsInt() + ")");
            assertTrue(meta.get("npc").getAsBoolean(), rig);
            assertEquals("none", meta.get("weaponMode").getAsString(), rig + ": mãos livres, sem item");
            assertEquals(PERFIS.get(rig), meta.get("weaponProfile").getAsString(), rig + ": perfil do ofício");
            Set<String> parts = new HashSet<>();
            JsonObject head = null;
            for (var p : doc.getAsJsonArray("parts")) {
                String n = p.getAsJsonObject().get("name").getAsString();
                parts.add(n);
                if (n.equals("head")) head = p.getAsJsonObject();
            }
            for (String p : List.of("torso", "head", "hat", "arm_l", "farm_l", "arm_r", "farm_r", "leg_l", "shin_l", "leg_r", "shin_r"))
                assertTrue(parts.contains(p), rig + ": parte ausente " + p);
            for (String e : List.of("joy", "determined", "surprise"))
                assertTrue(head.getAsJsonObject("alts").has(e), rig + ": expressão ausente " + e);
        }
    }

    @Test
    void narrativeHeightsFitTheCast() throws IOException {
        // 0,75 u por metro narrativo, como os NPCs de ofício; dentro da faixa do elenco aprovado (Pluma a Bramor)
        double min = Double.MAX_VALUE, max = 0;
        for (String rig : APROVADOS) {
            JsonObject m = meta(rig);
            double h = m.has("worldUnits") ? m.get("worldUnits").getAsDouble() : m.get("bodyHeightVoxels").getAsDouble() / 40;
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        for (String rig : NPCS) {
            JsonObject m = meta(rig);
            double h = m.get("worldUnits").getAsDouble();
            assertEquals(m.get("metersNarrative").getAsDouble() * 0.75, h, 1.5 / 40, rig + ": altura no mundo");
            assertTrue(h >= min && h <= max, rig + ": " + h + " u fora da faixa do elenco [" + min + ", " + max + "]");
        }
        double b = meta(NPCS.get(0)).get("worldUnits").getAsDouble(), q = meta(NPCS.get(1)).get("worldUnits").getAsDouble(),
                a = meta(NPCS.get(2)).get("worldUnits").getAsDouble();
        assertTrue(a > b && b > q, "Arvel > Brásia > Quelmo");
    }

    @Test
    void noLooseFragments() throws IOException {
        for (String rig : NPCS)
            try (var files = Files.list(VilaNpcTest.A.resolve(rig))) {
                for (Path f : files.filter(q -> q.toString().endsWith(".vox")).toList()) ExpansionTest.noFragments(f);
            }
    }

    @Test
    void notRecolorsOfEachOtherNorOfAnyApprovedNpc() throws IOException {
        List<String> todos = new ArrayList<>(NPCS);
        todos.addAll(APROVADOS);
        for (int i = 0; i < NPCS.size(); i++)
            for (int j = i + 1; j < todos.size(); j++) {
                String a = todos.get(i), c = todos.get(j);
                double body = VilaNpcTest.jaccard(VilaNpcTest.rigCells(a, null), VilaNpcTest.rigCells(c, null));
                double head = VilaNpcTest.jaccard(VilaNpcTest.rigCells(a, Set.of("head", "hat")), VilaNpcTest.rigCells(c, Set.of("head", "hat")));
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
        // linguagem dos NPCs da E: tronco em massa única; o que vai na frente (martelo, bolsa, corda) sai no máximo
        // até z = 5 na escala do NPC, o mesmo limite dos NPCs de ofício
        for (String rig : NPCS) {
            int maxZ = Integer.MIN_VALUE;
            for (long k : VilaNpcTest.rigCells(rig, Set.of("torso"))) maxZ = Math.max(maxZ, QualityTest.z(k));
            int limit = (int) Math.ceil(5 * Math.max(1.0, meta(rig).get("escala").getAsDouble()));
            assertTrue(maxZ <= limit, rig + ": tronco projeta até z = " + maxZ + " (limite na escala dele: " + limit + ")");
        }
    }

    @Test
    void hatSitsOnTheHeadWithoutAGap() throws IOException {
        // chapéu, boina e coque são partes próprias (balançam de leve com a cabeça); cada voxel deles pertence a um
        // bloco que ENCOSTA na cabeça: nada flutua, nenhuma faixa de ar entre o chapéu e o cabelo
        for (String rig : NPCS) {
            Set<Long> head = VilaNpcTest.rigCells(rig, Set.of("head")), hat = VilaNpcTest.rigCells(rig, Set.of("hat"));
            assertFalse(hat.isEmpty(), rig + ": sem chapéu/coque");
            Set<Long> reached = new HashSet<>();
            List<Long> todo = new ArrayList<>();
            for (long k : hat)
                if (touches(k, head)) { reached.add(k); todo.add(k); }
            while (!todo.isEmpty()) {
                long k = todo.removeLast();
                for (long n : neighbors(k))
                    if (hat.contains(n) && reached.add(n)) todo.add(n);
            }
            assertEquals(hat.size(), reached.size(), rig + ": " + (hat.size() - reached.size()) + " voxels do chapéu sem ligação com a cabeça");
            long contact = hat.stream().filter(k -> touches(k, head)).count();
            assertTrue(contact >= 20, rig + ": chapéu encosta na cabeça em só " + contact + " voxels");
        }
    }

    static boolean touches(long k, Set<Long> other) {
        for (long n : neighbors(k)) if (other.contains(n)) return true;
        return false;
    }

    static long[] neighbors(long k) {
        int x = (int) (k >>> 40) - 512, y = QualityTest.y(k), z = QualityTest.z(k);
        return new long[]{QualityTest.key(x + 1, y, z), QualityTest.key(x - 1, y, z), QualityTest.key(x, y + 1, z),
                QualityTest.key(x, y - 1, z), QualityTest.key(x, y, z + 1), QualityTest.key(x, y, z - 1)};
    }

    @Test
    void gesturesPassTheSpatialValidator() {
        for (String rig : NPCS) {
            CharacterModel m = CharacterModel.loadSkeleton(VilaNpcTest.A.resolve(rig));
            for (String seq : List.of("emote", "oficio", "clip:WALK", "clip:RUN", "seq:walk:0.8,emote,walk:0.8",
                    "seq:walk:0.8,oficio,walk:0.8", "seq:oficio,emote")) {
                WeaponCheck.Report r = WeaponCheck.validate(m, seq);
                assertTrue(r.ok(), rig + " / " + seq + ": " + r);
            }
        }
    }

    @Test
    void waveIsTheSharedOneAndTheTradeGestureIsShort() throws IOException {
        JsonObject vila = perfil("npc_vila");
        for (String name : PERFIS.values()) {
            JsonObject p = perfil(name);
            assertEquals(vila.getAsJsonObject("moves").get("emote"), p.getAsJsonObject("moves").get("emote"),
                    name + ": o aceno é o mesmo dos NPCs da vila");
            for (String pose : List.of("rest", "wave_up", "wave_a", "wave_b"))
                assertEquals(vila.getAsJsonObject("poses").get(pose), p.getAsJsonObject("poses").get(pose), name + ": pose " + pose);
            assertEquals(vila.get("carry"), p.get("carry"), name + ": balanço das mãos livres");
            JsonObject of = p.getAsJsonObject("moves").getAsJsonObject("oficio");
            assertNotNull(of, name + ": sem gesto de ofício");
            var keys = of.getAsJsonArray("keys");
            double end = keys.get(keys.size() - 1).getAsJsonObject().get("t").getAsDouble();
            assertTrue(end >= 2.0 && end <= 4.0, name + ": gesto de ofício com " + end + " s (curto: 2 a 4 s)");
            assertEquals("rest", keys.get(0).getAsJsonObject().get("pose").getAsString(), name + ": começa no repouso");
            assertEquals("rest", keys.get(keys.size() - 1).getAsJsonObject().get("pose").getAsString(), name + ": volta ao repouso");
            double prev = 0;
            for (String ph : List.of("prep", "exec", "follow", "recover")) {
                var r = of.getAsJsonObject("phases").getAsJsonArray(ph);
                assertEquals(prev, r.get(0).getAsDouble(), 1e-9, name + ": fase " + ph + " não emenda na anterior");
                assertTrue(r.get(1).getAsDouble() > r.get(0).getAsDouble(), name + ": fase " + ph + " vazia");
                prev = r.get(1).getAsDouble();
            }
            assertEquals(end, prev, 1e-9, name + ": as fases cobrem o gesto inteiro");
        }
    }

    @Test
    void limbsDoNotPassThroughAccessories() throws Exception {
        // braço × tronco (avental, bolsa, corda, gola), braço × cabeça (chapéu, coque) e perna × tronco: nenhum NPC
        // novo atravessa mais fundo que o pior NPC aprovado; braço × tronco, onde os acessórios são deles, no máximo
        // 1 voxel na escala do rig
        Map<String, Float> limit = new java.util.HashMap<>();
        for (String rig : APROVADOS)
            for (var e : NpcClipProbe.measure(VilaNpcTest.A.resolve(rig)).entrySet())
                limit.merge(e.getKey(), e.getValue().depthU(), Math::max);
        for (String rig : NPCS) {
            double s = meta(rig).get("escala").getAsDouble();
            for (var e : NpcClipProbe.measure(VilaNpcTest.A.resolve(rig)).entrySet()) {
                float d = e.getValue().depthU();
                assertTrue(d <= limit.get(e.getKey()) + 1e-4f, String.format(Locale.ROOT, "%s / %s: %.3f u (aprovados: %.3f u) em %s",
                        rig, e.getKey(), d, limit.get(e.getKey()), e.getValue().where()));
                if (e.getKey().equals("braço × tronco"))
                    assertTrue(d <= s / 40 + 1e-4, String.format(Locale.ROOT, "%s: braço entra %.3f u no tronco em %s",
                            rig, d, e.getValue().where()));
            }
        }
    }

    @Test
    void actingIsSubtleAndTheRolesAreNew() throws IOException {
        Set<String> existentes = new HashSet<>();
        for (String rig : APROVADOS) {
            JsonObject m = meta(rig);
            if (m.has("role")) existentes.add(m.get("role").getAsString());
        }
        Set<String> roles = new HashSet<>();
        for (String rig : NPCS) {
            JsonObject m = meta(rig), ac = m.getAsJsonObject("acting");
            for (String k : List.of("headPitch", "headRoll", "torsoPitch", "hipRoll"))
                if (ac.has(k)) assertTrue(Math.abs(ac.get(k).getAsDouble()) <= 8, rig + ": " + k + " exagerado");
            if (ac.has("emoteTempo")) {
                double t = ac.get("emoteTempo").getAsDouble();
                assertTrue(t >= 0.75 && t <= 1.3, rig + ": andamento do aceno " + t);
            }
            String role = m.get("role").getAsString();
            assertFalse(existentes.contains(role), rig + ": o ofício " + role + " já tem morador aprovado");
            roles.add(role);
        }
        assertEquals(NPCS.size(), roles.size(), "cada NPC tem um ofício próprio");
    }

    @Test
    void metadataSaysOnlyWhatTheyAreInTheGame() throws IOException {
        for (String rig : NPCS) {
            String doc = Files.readString(VilaNpcTest.A.resolve(rig).resolve("rig.json")).toLowerCase(Locale.ROOT);
            for (String w : List.of("foto", "refer", "homenag", "baseado", "baseada", "inspirad", "desenho d", "semelhan"))
                assertFalse(doc.contains(w), rig + ": metadado menciona \"" + w + "\"");
        }
    }
}
