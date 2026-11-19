package vx.viewer;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import vx.engine.VoxModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NPCs da vila (Kelyra, Índira, Súvia; tools/npcs_vila_e.py): contrato de NPC, partes, expressões com atuação
 * própria, nada de recoloração entre elas, tronco sem volume extra, gesto compartilhado validado e cena da
 * vila separada da clareira canônica. Rode build_assets antes.
 */
class VilaNpcTest {
    static final Path A = Path.of("assets");
    static final List<String> NPCS = List.of("characters/npc/kelyra", "characters/npc/indira", "characters/npc/suvia");

    /** Voxels do rig inteiro (ou só das partes pedidas) no espaço do modelo, via origin do rig.json. */
    static Set<Long> rigCells(String rig, Set<String> only) throws IOException {
        JsonObject doc = ExpansionTest.json(rig + "/rig.json");
        Set<Long> out = new HashSet<>();
        for (var pe : doc.getAsJsonArray("parts")) {
            JsonObject p = pe.getAsJsonObject();
            if (only != null && !only.contains(p.get("name").getAsString())) continue;
            var o = p.getAsJsonArray("origin");
            VoxModel m = VoxModel.load(A.resolve(rig).resolve(p.get("file").getAsString()));
            for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++)
                if (m.get(x, y, z) != 0)
                    out.add(QualityTest.key(x + o.get(0).getAsInt(), y + o.get(1).getAsInt(), z + o.get(2).getAsInt()));
        }
        return out;
    }

    static double jaccard(Set<Long> a, Set<Long> b) {
        Set<Long> i = new HashSet<>(a);
        i.retainAll(b);
        Set<Long> u = new HashSet<>(a);
        u.addAll(b);
        return i.size() / (double) u.size();
    }

    @Test
    void followTheNpcContract() throws IOException {
        for (String rig : NPCS) {
            JsonObject doc = ExpansionTest.json(rig + "/rig.json"), meta = doc.getAsJsonObject("meta");
            double heads = meta.get("headsTall").getAsDouble();
            assertTrue(heads >= 2.0 && heads <= 3.0, rig + ": fora da faixa de NPC 2,0–3,0 cabeças: " + heads);
            assertTrue(heads > 2.65, rig + ": dentro da faixa congelada dos protagonistas: " + heads);
            assertEquals(1.0 / 40, doc.get("voxelSize").getAsDouble(), 1e-6, rig);
            assertTrue(meta.get("faces").getAsInt() <= 9000, rig + ": orçamento de faces");
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
    void foxEarsAreHeadChildrenOutsideTheHeadMeasure() throws IOException {
        JsonObject doc = ExpansionTest.json("characters/npc/suvia/rig.json");
        int ears = 0;
        for (var p : doc.getAsJsonArray("parts")) {
            JsonObject o = p.getAsJsonObject();
            if (o.get("name").getAsString().startsWith("ear_")) {
                ears++;
                assertEquals("head", o.get("parent").getAsString(), "orelha articulada na cabeça");
            }
        }
        assertEquals(2, ears);
        int top = 0;
        for (long k : rigCells("characters/npc/suvia", Set.of("ear_l", "ear_r"))) top = Math.max(top, QualityTest.y(k));
        int bodyTop = doc.getAsJsonObject("meta").get("bodyHeightVoxels").getAsInt();
        assertTrue(top >= bodyTop, "orelhas acima do topo medido (acessório fora da contagem de cabeças)");
    }

    @Test
    void noLooseFragments() throws IOException {
        for (String rig : NPCS)
            try (var files = Files.list(A.resolve(rig))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".vox")).toList()) ExpansionTest.noFragments(f);
            }
    }

    @Test
    void notRecolorsOfEachOther() throws IOException {
        // mesmo esqueleto de NPC é permitido; a forma (cabelo, roupa, acessório) tem de mudar.
        // Referência: a cartógrafa, aceita como distinta, fica em 0,71–0,75 contra elas.
        for (int i = 0; i < NPCS.size(); i++)
            for (int j = i + 1; j < NPCS.size(); j++) {
                double body = jaccard(rigCells(NPCS.get(i), null), rigCells(NPCS.get(j), null));
                double head = jaccard(rigCells(NPCS.get(i), Set.of("head")), rigCells(NPCS.get(j), Set.of("head")));
                assertTrue(body < 0.88, NPCS.get(i) + " × " + NPCS.get(j) + ": corpo quase idêntico (" + body + ")");
                assertTrue(head < 0.85, NPCS.get(i) + " × " + NPCS.get(j) + ": cabeça quase idêntica (" + head + ")");
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
            for (int i = 0; i < 3; i++)
                for (int j = i + 1; j < 3; j++)
                    assertTrue(jaccard(sets.get(i), sets.get(j)) < 0.6, e + ": " + NPCS.get(i) + " × " + NPCS.get(j) + " pintam o mesmo desenho");
        }
    }

    @Test
    void torsoHasNoExtraFrontVolume() throws IOException {
        // linguagem dos NPCs da E: tronco em massa única; nada projeta além da frente da cartógrafa (z ≤ 5 com adorno)
        for (String rig : NPCS) {
            int maxZ = Integer.MIN_VALUE;
            for (long k : rigCells(rig, Set.of("torso"))) maxZ = Math.max(maxZ, QualityTest.z(k));
            assertTrue(maxZ <= 5, rig + ": tronco projeta até z = " + maxZ);
        }
    }

    @Test
    void sharedGesturePassesTheSpatialValidator() {
        for (String rig : NPCS) {
            CharacterModel m = CharacterModel.loadSkeleton(A.resolve(rig));
            for (String seq : List.of("emote", "clip:WALK", "clip:RUN", "seq:walk:0.8,emote,walk:0.8")) {
                WeaponCheck.Report r = WeaponCheck.validate(m, seq);
                assertTrue(r.ok(), rig + " / " + seq + ": " + r);
            }
        }
    }

    @Test
    void villageSceneIsSeparateFromTheCanonicalClearing() throws IOException {
        String vila = Files.readString(A.resolve("scenes/vila_e.json"));
        String clearing = Files.readString(A.resolve("scenes/clearing_e.json"));
        String vitrine = Files.readString(A.resolve("scenes/vitrine_e.json"));
        for (String rig : NPCS) {
            assertTrue(vila.contains(rig), "vila usa " + rig);
            assertFalse(clearing.contains(rig), "clareira canônica não recebe " + rig);
            assertFalse(vitrine.contains(rig), "vitrine da rodada 06 não muda");
        }
    }

    // ------------------------------------------------------------------ rodada 06.1: refinamento

    /** Voxels de uma parte no espaço do modelo → cor. */
    static java.util.Map<Long, Integer> part(String rig, String name) throws IOException {
        JsonObject doc = ExpansionTest.json(rig + "/rig.json");
        java.util.Map<Long, Integer> out = new java.util.HashMap<>();
        for (var pe : doc.getAsJsonArray("parts")) {
            JsonObject p = pe.getAsJsonObject();
            if (!p.get("name").getAsString().equals(name)) continue;
            var o = p.getAsJsonArray("origin");
            VoxModel m = VoxModel.load(A.resolve(rig).resolve(p.get("file").getAsString()));
            for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++)
                if (m.get(x, y, z) != 0)
                    out.put(QualityTest.key(x + o.get(0).getAsInt(), y + o.get(1).getAsInt(), z + o.get(2).getAsInt()), m.get(x, y, z));
        }
        return out;
    }

    static int xOf(long k) { return (int) ((k >>> 40) & 0xFFFFF) - 512; }

    /** VoxModel.get devolve (material + 1) << 24 | r << 16 | g << 8 | b. */
    static double lum(int c) { return 0.3 * ((c >>> 16) & 0xFF) + 0.59 * ((c >>> 8) & 0xFF) + 0.11 * (c & 0xFF); }

    @Test
    void longHairLocksHangFromTheTorsoNotTheHead() throws IOException {
        // mecha da Kelyra e trança da Índira à frente do peito: presas ao tronco, não atravessam o peito quando a cabeça gira
        for (String rig : List.of("characters/npc/kelyra", "characters/npc/indira")) {
            String parent = null;
            for (var p : ExpansionTest.json(rig + "/rig.json").getAsJsonArray("parts"))
                if (p.getAsJsonObject().get("name").getAsString().equals("lock")) parent = p.getAsJsonObject().get("parent").getAsString();
            assertEquals("torso", parent, rig + ": parte lock presa ao tronco");
        }
    }

    @Test
    void silhouetteSignaturesStayStructural() throws IOException {
        // "pintadas de preto, ainda se reconhecem?" — assinaturas de FORMA (não de cor nem de acessório solto)
        int neck = 29;
        int[] backHairBottom = new int[3];
        double[] shoulder = new double[3];
        for (int i = 0; i < 3; i++) {
            int low = Integer.MAX_VALUE;
            for (long k : part(NPCS.get(i), "head").keySet()) if (QualityTest.z(k) < 0) low = Math.min(low, QualityTest.y(k));
            backHairBottom[i] = low;
            double w = 0;
            for (String arm : List.of("arm_l", "arm_r"))
                for (long k : part(NPCS.get(i), arm).keySet())
                    if (QualityTest.y(k) >= 23 && QualityTest.y(k) <= 27) w = Math.max(w, Math.abs(xOf(k) + 0.5));
            shoulder[i] = w;
        }
        assertTrue(backHairBottom[0] <= 18, "Kelyra: cabelo até a cintura (ponta em y " + backHairBottom[0] + ")");
        assertTrue(backHairBottom[1] >= neck - 4, "Índira: cabelo curto na nuca (ponta em y " + backHairBottom[1] + ")");
        assertTrue(backHairBottom[2] > backHairBottom[0] + 2 && backHairBottom[2] < backHairBottom[1] - 2,
                "Súvia: comprimento intermediário com pontas abertas (y " + backHairBottom[2] + ")");
        assertTrue(shoulder[1] >= Math.max(shoulder[0], shoulder[2]) + 1.5, "Índira: mangas bufantes alargam os ombros");
        int earTop = Integer.MIN_VALUE, headTop = Integer.MIN_VALUE;
        for (long k : part("characters/npc/suvia", "ear_l").keySet()) earTop = Math.max(earTop, QualityTest.y(k));
        for (long k : part("characters/npc/suvia", "head").keySet()) headTop = Math.max(headTop, QualityTest.y(k));
        assertTrue(earTop >= headTop + 3, "Súvia: orelhas saem do contorno da cabeça");
    }

    @Test
    void suviaClothingReadsAgainstHerDarkHair() throws IOException {
        // problema da rodada 06: mangas e corpo quase da cor do cabelo (sumia na sombra). Valor, não brilho.
        // cabelo VISÍVEL de trás: o voxel mais ao fundo de cada coluna (x, y) da cabeça acima do queixo
        java.util.Map<Long, long[]> back = new java.util.HashMap<>();
        for (var e : part("characters/npc/suvia", "head").entrySet()) {
            long k = e.getKey();
            if (QualityTest.y(k) < 34) continue;
            long col = QualityTest.key(xOf(k), QualityTest.y(k), 0);
            long[] cur = back.get(col);
            if (cur == null || QualityTest.z(k) < cur[0]) back.put(col, new long[]{QualityTest.z(k), e.getValue()});
        }
        double hair = 0; int nh = 0;
        for (long[] v : back.values()) { hair += lum((int) v[1]); nh++; }
        double sleeve = 0; int ns = 0;
        for (String arm : List.of("arm_l", "arm_r"))
            for (int c : part("characters/npc/suvia", arm).values()) { sleeve += lum(c); ns++; }
        hair /= nh; sleeve /= ns;
        assertTrue(sleeve - hair >= 25, "mangas (" + sleeve + ") precisam separar do cabelo (" + hair + ")");
    }

    @Test
    void actingIsSubtleAndPersonal() throws IOException {
        java.util.Set<Double> tempos = new java.util.HashSet<>();
        java.util.Set<String> roles = new java.util.HashSet<>();
        for (String rig : NPCS) {
            JsonObject meta = ExpansionTest.json(rig + "/rig.json").getAsJsonObject("meta");
            JsonObject ac = meta.getAsJsonObject("acting");
            for (String k : List.of("headPitch", "headRoll", "torsoPitch", "hipRoll"))
                if (ac.has(k)) assertTrue(Math.abs(ac.get(k).getAsDouble()) <= 8, rig + ": " + k + " exagerado");
            double t = ac.get("emoteTempo").getAsDouble();
            assertTrue(t >= 0.75 && t <= 1.3, rig + ": andamento do aceno fora do sutil");
            tempos.add(t);
            roles.add(meta.get("role").getAsString());
        }
        assertEquals(3, tempos.size(), "cada NPC acena no seu andamento");
        assertEquals(3, roles.size(), "cada NPC tem um papel próprio na vila");
    }
}
