package vx.viewer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import vx.engine.VoxModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 06 — primeira expansão controlada: propriedades REAIS dos quatro assets novos (cartógrafa,
 * mariposa, salgueiro, posto de trilha) e a garantia de que a cena canônica não foi tocada. Rode build_assets
 * antes. Os testes dos assets aprovados continuam intactos; nada aqui afrouxa critério deles.
 */
class ExpansionTest {
    static final Path A = Path.of("assets");
    static final String NPC = "characters/npc/cartografa", MOTH = "creatures/e/mariposa";

    static JsonObject json(String rel) throws IOException {
        return JsonParser.parseString(Files.readString(A.resolve(rel))).getAsJsonObject();
    }

    /** Voxels ocupados de um .vox (chave compacta) e quantos por material. */
    static Set<Long> cells(VoxModel m, int[] perMaterial) {
        Set<Long> s = new HashSet<>();
        for (int x = 0; x < m.sx; x++)
            for (int y = 0; y < m.sy; y++)
                for (int z = 0; z < m.sz; z++) {
                    int c = m.get(x, y, z);
                    if (c == 0) continue;
                    s.add(QualityTest.key(x, y, z));
                    if (perMaterial != null) perMaterial[(c >>> 24) - 1]++;
                }
        return s;
    }

    static void noFragments(Path f) {
        List<Integer> comps = QualityTest.components(cells(VoxModel.load(f), null));
        for (int i = 1; i < comps.size(); i++)
            assertTrue(comps.get(i) >= 4, f + ": fragmento solto de " + comps.get(i) + " voxel(s)");
    }

    // ------------------------------------------------------------------ NPC

    @Test
    void npcFollowsTheNpcContractNotTheProtagonistOne() throws IOException {
        JsonObject doc = json(NPC + "/rig.json"), meta = doc.getAsJsonObject("meta");
        double heads = meta.get("headsTall").getAsDouble();
        assertTrue(heads >= 2.0 && heads <= 3.0, "NPC fora da faixa 2,0–3,0 cabeças: " + heads);
        assertTrue(heads > 2.60 + 0.05, "proporção própria (fora da faixa congelada dos protagonistas 2,35–2,60): " + heads);
        assertEquals(1.0 / 40, doc.get("voxelSize").getAsDouble(), 1e-6, "mesma densidade dos humanoides");
        assertTrue(meta.get("faces").getAsInt() <= 9000, "orçamento de faces");
        assertTrue(meta.get("npc").getAsBoolean());
        Set<String> parts = new HashSet<>();
        for (var p : doc.getAsJsonArray("parts")) parts.add(p.getAsJsonObject().get("name").getAsString());
        for (String p : List.of("torso", "head", "arm_l", "farm_l", "arm_r", "farm_r", "leg_l", "shin_l", "leg_r", "shin_r",
                "skirt_l", "skirt_r", "pack", "tool"))
            assertTrue(parts.contains(p), "parte obrigatória ausente: " + p);
        JsonObject head = null;
        for (var p : doc.getAsJsonArray("parts")) if (p.getAsJsonObject().get("name").getAsString().equals("head")) head = p.getAsJsonObject();
        for (String e : List.of("joy", "determined", "surprise"))
            assertTrue(head.getAsJsonObject("alts").has(e), "expressão ausente: " + e);
    }

    @Test
    void npcHeadIsStructurallyDifferentFromEveryProtagonistHead() {
        VoxModel npc = VoxModel.load(A.resolve(NPC + "/head.vox"));
        for (String h : List.of("adventurer", "ranger", "mage")) {
            VoxModel hero = VoxModel.load(A.resolve("characters/e/" + h + "/head.vox"));
            boolean sameBox = npc.sx == hero.sx && npc.sy == hero.sy && npc.sz == hero.sz;
            assertFalse(sameBox, "cabeça do NPC com as mesmas dimensões da de " + h + " (recoloração?)");
        }
    }

    @Test
    void npcPartsHaveNoStraySpecks() throws IOException {
        try (var files = Files.list(A.resolve(NPC))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".vox")).toList()) noFragments(f);
        }
    }

    @Test
    void npcAnimationsPassTheSpatialCheckAndHaveNoCombat() {
        CharacterModel m = CharacterModel.loadSkeleton(A.resolve(NPC));
        Animator an = new Animator(m, 1f);
        assertNotNull(an.prof, "perfil do NPC");
        assertEquals(Set.of("emote"), an.prof.moves.keySet(), "o NPC só tem o gesto característico (sem ataques, sem HIT de combate)");
        for (String w : List.of("emote", "clip:IDLE", "clip:WALK", "seq:idle:0.4,walk:1.0,emote,idle:0.6")) {
            WeaponCheck.Report r = WeaponCheck.validate(m, w);
            assertTrue(r.ok(), r.toString());
        }
    }

    // ------------------------------------------------------------------ criatura

    @Test
    void mothIsATierOneFlutterCreatureWithFourWings() throws IOException {
        JsonObject doc = json(MOTH + "/rig.json"), meta = doc.getAsJsonObject("meta");
        assertEquals("flutter", doc.get("anim").getAsString());
        assertEquals(1, meta.get("tier").getAsInt());
        assertEquals(1.0 / 32, doc.get("voxelSize").getAsDouble(), 1e-6);
        assertTrue(meta.get("faces").getAsInt() <= 3000, "nível 1: ≤ 3 mil faces");
        Set<String> parts = new HashSet<>();
        for (var p : doc.getAsJsonArray("parts")) parts.add(p.getAsJsonObject().get("name").getAsString());
        assertTrue(parts.containsAll(List.of("body", "head", "abdomen", "wing_fl", "wing_fr", "wing_bl", "wing_br")), parts.toString());
        // escala: criatura PEQUENA — envergadura entre 0,5 e 1,2 u (herói: 1,18 u de altura)
        int w = doc.getAsJsonArray("sizeVoxels").get(0).getAsInt();
        double span = w * doc.get("voxelSize").getAsDouble();
        assertTrue(span >= 0.5 && span <= 1.2, "envergadura " + span + " u");
    }

    @Test
    void mothPartsAreCleanAndLightIsOnlyAnAccent() throws IOException {
        int[] mat = new int[8];
        int total = 0;
        try (var files = Files.list(A.resolve(MOTH))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".vox")).toList()) {
                noFragments(f);
                total += cells(VoxModel.load(f), mat).size();
            }
        }
        assertTrue(mat[VoxModel.MAT_EMISSIVE] > 0, "semente de luz P1 presente");
        assertTrue(mat[VoxModel.MAT_EMISSIVE] <= 0.03 * total, "P1 discreto: " + mat[VoxModel.MAT_EMISSIVE] + " de " + total);
    }

    @Test
    void mothWingsBeatSlowlyEnoughNotToFlicker() {
        CharacterModel m = CharacterModel.loadSkeleton(A.resolve(MOTH));
        Animator an = new Animator(m, 1f);
        Animator.Pose p = an.newPose();
        Matrix4f id = new Matrix4f();
        Matrix4f[] mats = new Matrix4f[m.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        int wing = m.index.get("wing_fl"), body = m.index.get("body");
        for (Animator.Clip c : List.of(Animator.Clip.IDLE, Animator.Clip.WALK, Animator.Clip.HIT, Animator.Clip.EMOTE)) {
            float prev = Float.NaN, prevD = 0f;
            int turns = 0, lastTurn = -999, minGap = 999, frame = 0;
            float maxStep = 0f, dur = 4f;
            for (float t = 0; t < dur; t += 1f / 30f, frame++) {     // amostragem de vídeo real (30 fps)
                Animator.Motion mo = new Animator.Motion();
                mo.set(c, t);
                an.evaluate(mo, p, id, null);
                an.compose(p, id, mats);
                Matrix4f rel = new Matrix4f(mats[body]).invert().mul(mats[wing]);   // batida EM RELAÇÃO AO CORPO
                float a = (float) Math.atan2(rel.m01(), rel.m00());                 // (o giro do corpo no EMOTE não conta)
                if (!Float.isNaN(prev)) {
                    float d = a - prev;
                    maxStep = Math.max(maxStep, Math.abs(d));
                    if (d * prevD < 0) {
                        turns++;
                        minGap = Math.min(minGap, frame - lastTurn);
                        lastTurn = frame;
                    }
                    prevD = d;
                }
                prev = a;
            }
            float hz = turns / 2f / dur;
            System.out.printf("mariposa %s: %.2f inversões/2 por s, menor intervalo entre inversões %d quadros, passo máx %.2f rad%n", c, hz, minGap, maxStep);
            // cintilação = inversões muito próximas: exige ≥ 5 quadros (a 30 fps) entre um sobe e um desce
            assertTrue(minGap >= 5, c + ": inversões a " + minGap + " quadros");
            // voo e pairar: no máximo 2,6 batidas/s (≥ 11 quadros por batida)
            if (c == Animator.Clip.IDLE || c == Animator.Clip.WALK) assertTrue(hz <= 2.6f, c + ": " + hz + " batidas/s");
            assertTrue(maxStep <= 0.5f, c + ": salto de asa de " + maxStep + " rad entre quadros");
        }
    }

    // ------------------------------------------------------------------ árvore e estrutura

    @Test
    void willowHangsItsFoliageAndStaysOneSolidPiece() throws IOException {
        JsonObject props = json("environment/e/props.json");
        JsonObject w = props.getAsJsonObject("tree_willow"), oak = props.getAsJsonObject("tree_oak");
        assertNotNull(w, "salgueiro registrado no props.json");
        assertTrue(w.get("affineWind").getAsBoolean(), "folhagem com vento afim (fusão de faces da E)");
        double h = w.getAsJsonArray("sizeVoxels").get(1).getAsInt() * 0.125, width = w.getAsJsonArray("sizeVoxels").get(0).getAsInt() * 0.125;
        assertTrue(h >= 3.0 && h <= 5.0 && width >= 2.5 && width <= 4.5, "dimensões " + width + " × " + h + " u");
        int[] mat = new int[8];
        VoxModel m = VoxModel.load(A.resolve("environment/e/tree_willow.vox"));
        Set<Long> c = cells(m, mat);
        assertTrue(mat[VoxModel.MAT_FOLIAGE] >= 0.5 * c.size(), "copa dominante em folhagem");
        noFragments(A.resolve("environment/e/tree_willow.vox"));
        // silhueta pendente: o salgueiro tem folhagem perto do chão (cortinas); o carvalho O3 não
        int lowW = lowestFoliage(m), lowOak = lowestFoliage(VoxModel.load(A.resolve("environment/e/tree_oak.vox")));
        int yw = -w.getAsJsonArray("origin").get(1).getAsInt(), yo = -oak.getAsJsonArray("origin").get(1).getAsInt();
        assertTrue(lowW - yw <= 10 && lowOak - yo >= 15, "cortinas até ~1 u do chão (salgueiro " + (lowW - yw) + ", carvalho " + (lowOak - yo) + ")");
    }

    static int lowestFoliage(VoxModel m) {
        for (int y = 0; y < m.sy; y++)
            for (int x = 0; x < m.sx; x++)
                for (int z = 0; z < m.sz; z++) {
                    int c = m.get(x, y, z);
                    if (c != 0 && (c >>> 24) - 1 == VoxModel.MAT_FOLIAGE) return y;
                }
        return Integer.MAX_VALUE;
    }

    @Test
    void trailPostIsSmallerThanTheHouseAndBuiltFromTheSameMaterials() throws IOException {
        JsonObject props = json("environment/e/props.json");
        JsonObject p = props.getAsJsonObject("trail_post"), house = props.getAsJsonObject("house");
        assertNotNull(p, "posto de trilha registrado");
        int[] sp = new int[3], sh = new int[3];
        for (int i = 0; i < 3; i++) {
            sp[i] = p.getAsJsonArray("sizeVoxels").get(i).getAsInt();
            sh[i] = house.getAsJsonArray("sizeVoxels").get(i).getAsInt();
        }
        assertTrue(sp[1] < sh[1] && sp[0] * sp[2] < sh[0] * sh[2], "menor que a casa");
        double h = sp[1] * 0.125;
        assertTrue(h >= 2.5 && h <= 4.0, "altura " + h + " u (herói 1,18 u, porta da casa 1,5 u)");
        int[] mat = new int[8];
        VoxModel m = VoxModel.load(A.resolve("environment/e/trail_post.vox"));
        Set<Long> c = cells(m, mat);
        assertTrue(mat[VoxModel.MAT_EMISSIVE] > 0 && mat[VoxModel.MAT_EMISSIVE] <= 0.01 * c.size(), "uma luz quente, pequena");
        noFragments(A.resolve("environment/e/trail_post.vox"));
    }

    @Test
    void showcaseSceneUsesTheNewAssetsAndTheCanonicalClearingDoesNot() throws IOException {
        String vit = Files.readString(A.resolve("scenes/vitrine_e.json")), clr = Files.readString(A.resolve("scenes/clearing_e.json"));
        for (String n : List.of("cartografa", "mariposa", "tree_willow", "trail_post")) {
            assertTrue(vit.contains(n), "vitrine sem " + n);
            assertFalse(clr.contains(n), "a clareira canônica não pode receber " + n);
        }
        JsonObject props = json("environment/e/props.json");
        for (var el : JsonParser.parseString(vit).getAsJsonObject().getAsJsonArray("props"))
            assertTrue(props.has(el.getAsJsonObject().get("n").getAsString()), "prop inexistente na vitrine: " + el);
        assertTrue(Files.exists(A.resolve(NPC + "/rig.json")) && Files.exists(A.resolve(MOTH + "/rig.json")));
    }
}
