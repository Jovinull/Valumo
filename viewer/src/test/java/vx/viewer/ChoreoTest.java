package vx.viewer;

import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 04 — coreografia de armas. Os testes avaliam as animações de verdade (rigs gerados + perfis em
 * assets/anim) sem OpenGL, e medem geometria: se um golpe passar a atravessar a cabeça, se a mão soltar o
 * cabo, se a flecha desalinhar ou se a pose "teleportar", algum destes falha. Rode build_assets antes.
 */
class ChoreoTest {
    static final String[] RIGS = {"characters/e/adventurer", "characters/r4/adventurer_shield", "characters/r4/adventurer_longblade",
            "characters/e/ranger", "characters/e/mage"};

    static CharacterModel rig(String dir) { return CharacterModel.loadSkeleton(Path.of("assets").resolve(dir)); }

    /** Avalia um golpe isolado no tempo t e devolve as matrizes das partes no espaço do modelo. */
    static Matrix4f[] pose(Animator an, String move, float t) {
        CharacterModel m = an.model();
        Animator.Motion mo = new Animator.Motion().set(Animator.Clip.IDLE, t);
        mo.move = move;
        mo.moveT = t;
        mo.weight = 1f;
        Animator.Pose p = an.newPose();
        an.evaluate(mo, p, new Matrix4f(), null);
        Matrix4f[] mats = new Matrix4f[m.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        an.compose(p, new Matrix4f(), mats);
        return mats;
    }

    static Vector3f at(CharacterModel m, Matrix4f[] mats, String part, float x, float y, float z) {
        return mats[m.index.get(part)].transformPosition(new Vector3f(x, y, z).mul(m.voxelSize));
    }

    // ------------------------------------------------------------------ 1. hierarquia e pontos de fixação

    @Test
    void rigHierarchyHoldsItemsInTheHands() {
        for (String r : RIGS) {
            CharacterModel m = rig(r);
            for (int i = 0; i < m.parts.size(); i++)
                assertTrue(m.parts.get(i).parent < i, r + ": pai antes da filha");
            String[][] attach = {{"weapon", "farm_r"}, {"shield", "farm_l"}, {"bow", "farm_l"}, {"string_u", "bow"}, {"string_d", "bow"}, {"arrow", "bow"}};
            for (String[] a : attach)
                if (m.index.containsKey(a[0]))
                    assertEquals(a[1], m.parts.get(m.parts.get(m.index.get(a[0])).parent).name, r + ": " + a[0] + " preso a " + a[1]);
            assertTrue(m.meta.has("weaponProfile") && m.meta.has("hands") && m.meta.has("volumes"), r + ": meta da rodada 04");
            for (WeaponCheck.Vol v : WeaponCheck.volumes(m)) assertTrue(v.part() >= 0 && v.r() > 0, r + ": volume válido");
        }
    }

    @Test
    void itemStaysRigidlyInTheHandWhileTheArmIsSolved() {
        // o pivô do item (empunhadura) tem de coincidir com o ponto da mão do antebraço em qualquer instante:
        // se a decomposição matriz → Euler/deslocamento do IK errar, o item "escorrega" da mão
        CharacterModel m = rig("characters/e/adventurer");
        Animator an = new Animator(m, 1f);
        CharacterModel.Part w = m.parts.get(m.index.get("weapon"));
        for (String mv : an.prof.moves.keySet())
            for (float t = 0; t <= an.moveDuration(mv); t += 0.05f) {
                Matrix4f[] mats = pose(an, mv, t);
                Vector3f byItem = mats[m.index.get("weapon")].transformPosition(new Vector3f(w.px, w.py, w.pz));
                Vector3f byArm = mats[m.index.get("farm_r")].transformPosition(new Vector3f(w.px, w.py, w.pz));
                assertEquals(0f, byItem.distance(byArm) / m.voxelSize, 0.05f, mv + " em " + t);
            }
    }

    // ------------------------------------------------------------------ 2. interseções e alcance (todas as animações)

    @Test
    void everyWeaponAnimationPassesTheSpatialCheck() {
        for (String r : RIGS) {
            CharacterModel m = rig(r);
            Animator an = new Animator(m, 1f);
            assertNotNull(an.prof, r + ": perfil de arma");
            for (String mv : an.prof.moves.keySet()) {
                WeaponCheck.Report rep = WeaponCheck.validate(m, mv);
                assertTrue(rep.ok(), rep.toString());
            }
        }
    }

    @Test
    void transitionsBetweenLocomotionAndAttacksPassTheCheck() {
        for (String r : RIGS) {
            CharacterModel m = rig(r);
            Animator an = new Animator(m, 1f);
            for (String sq : new String[]{"walk:0.8,%s,run:0.8", "run:0.8,%s,idle:0.5", "idle:0.4,walk:0.7,run:0.7,%s,walk:0.6,idle:0.5"}) {
                WeaponCheck.Report rep = WeaponCheck.validate(m, "seq:" + sq.formatted(an.prof.attack));
                assertTrue(rep.ok(), rep.toString());
            }
        }
    }

    @Test
    void validatorCatchesABladeThroughTheHead() throws IOException {
        // autoteste: um golpe que leva a lâmina para dentro do crânio TEM de ser reprovado
        Path tmp = Files.createTempDirectory("choreo");
        Files.writeString(tmp.resolve("bad.json"), """
                {"name":"bad","rightItem":"weapon","tipLength":26,
                 "poses":{"rest":{"r":[-8.6,13.2,2.4],"rDir":[-0.3,-0.42,0.86],"lMode":"free"},
                          "through":{"r":[-6.0,25.0,3.0],"rDir":[0.95,0.30,0.0],"lMode":"free"}},
                 "moves":{"bad":{"keys":[{"t":0,"pose":"rest","stop":true},{"t":0.4,"pose":"through","stop":true},{"t":0.8,"pose":"rest","stop":true}]}}}
                """);
        Path saved = Choreo.root;
        try {
            Choreo.root = tmp;
            CharacterModel m = rig("characters/e/adventurer");
            m.meta.addProperty("weaponProfile", "bad");
            WeaponCheck.Report rep = WeaponCheck.validate(m, "bad");
            assertFalse(rep.ok(), "a validação precisa acusar a lâmina na cabeça");
            assertTrue(rep.items.get("blade × head").worst > 2f, rep.toString());
        } finally {
            Choreo.root = saved;
            Choreo.clearCache();
        }
    }

    // ------------------------------------------------------------------ 3. trajetórias com intenção

    @Test
    void horizontalSlashSweepsAcrossTheFrontFromRightToLeft() {
        CharacterModel m = rig("characters/e/adventurer");
        Animator an = new Animator(m, 1f);
        float[] exec = an.prof.moves.get("slash_h").phases.get("exec");
        Vector3f start = tip(an, "slash_h", exec[0]), mid = tip(an, "slash_h", (exec[0] + exec[1]) / 2), end = tip(an, "slash_h", exec[1] + 0.06f);
        float vs = m.voxelSize;
        assertTrue(start.x < -8 * vs, "começa do lado direito (x < 0): " + start);
        assertTrue(mid.z > 15 * vs, "passa à frente do corpo: " + mid);
        assertTrue(end.x > 8 * vs, "termina do lado esquerdo: " + end);
        for (Vector3f p : new Vector3f[]{start, mid, end})
            assertTrue(p.y > 8 * vs && p.y < 40 * vs, "corte lateral, não vertical: y = " + p.y / vs);
    }

    @Test
    void descendingSlashRisesBesideTheHeadAndCutsLowInFront() {
        CharacterModel m = rig("characters/e/adventurer");
        Animator an = new Animator(m, 1f);
        float[] prep = an.prof.moves.get("slash_v").phases.get("prep"), exec = an.prof.moves.get("slash_v").phases.get("exec");
        float vs = m.voxelSize;
        Vector3f up = tip(an, "slash_v", prep[1]), low = tip(an, "slash_v", exec[1]);
        assertTrue(up.y > 46 * vs, "ponta acima da cabeça na preparação: " + up.y / vs);
        assertTrue(Math.abs(up.x) > 9 * vs, "erguida AO LADO da cabeça, fora da silhueta: x = " + up.x / vs);
        assertTrue(low.y < 16 * vs && low.z > 15 * vs, "termina baixo e à frente: " + low);
    }

    @Test
    void longBladeComboAlternatesSides() {
        CharacterModel m = rig("characters/r4/adventurer_longblade");
        Animator an = new Animator(m, 1f);
        // L1 termina à direita (x < 0) e L2 à esquerda (x > 0): os cortes alternam os lados
        assertTrue(tip(an, "long_l1", 0.66f).x < 0, "L1 termina à direita");
        assertTrue(tip(an, "long_l2", 0.66f).x > 0, "L2 termina à esquerda");
    }

    static Vector3f tip(Animator an, String move, float t) {
        CharacterModel m = an.model();
        Matrix4f[] mats = pose(an, move, t);
        CharacterModel.Part w = m.parts.get(m.index.get("weapon"));
        return mats[m.index.get("weapon")].transformPosition(new Vector3f(w.px, w.py + an.prof.tipLength * m.voxelSize, w.pz));
    }

    // ------------------------------------------------------------------ 4. arco e magia

    @Test
    void arrowIsAlignedWithTheShotAtFullDraw() {
        CharacterModel m = rig("characters/e/ranger");
        Animator an = new Animator(m, 1f);
        Choreo.Move shot = an.prof.moves.get("bow_shot");
        float hold = shot.release - 0.05f;
        Matrix4f[] mats = pose(an, "bow_shot", hold);
        Vector3f nock = at(m, mats, "arrow", 9.5f, 14.5f, 0.5f), head = at(m, mats, "arrow", 9.5f, 14.5f, 15f);
        Vector3f dir = new Vector3f(head).sub(nock).normalize();
        double ang = Math.toDegrees(Math.acos(Math.clamp(dir.dot(0, 0, 1), -1f, 1f)));
        assertTrue(ang < 12, "flecha alinhada com a frente do personagem: " + ang + "°");
        Vector3f hand = WeaponCheck.handWorld(an, mats, "r");
        assertTrue(hand.distance(nock) / m.voxelSize < 1.5f, "entalhe na mão que puxa");
        // a flecha em voo segue o próprio eixo
        an.evaluate(motion("bow_shot", shot.release + 0.1f), an.newPose(), new Matrix4f(), null);
        Animator.Fx fly = an.fx.stream().filter(f -> f.kind == Animator.Fx.ARROW).findFirst().orElseThrow();
        Vector3f p1 = fly.m.getTranslation(new Vector3f());
        an.evaluate(motion("bow_shot", shot.release + 0.2f), an.newPose(), new Matrix4f(), null);
        Vector3f p2 = an.fx.stream().filter(f -> f.kind == Animator.Fx.ARROW).findFirst().orElseThrow().m.getTranslation(new Vector3f());
        assertTrue(new Vector3f(p2).sub(p1).normalize().z > 0.95f, "voa para a frente");
    }

    @Test
    void magicIsEmittedFromTheCrystalTowardsTheFront() {
        CharacterModel m = rig("characters/e/mage");
        Animator an = new Animator(m, 1f);
        for (String mv : new String[]{"staff_cast", "staff_cast_wide"}) {
            float te = an.prof.moves.get(mv).emit;
            an.evaluate(motion(mv, te + 0.05f), an.newPose(), new Matrix4f(), null);
            Vector3f a = orb(an);
            an.evaluate(motion(mv, te + 0.25f), an.newPose(), new Matrix4f(), null);
            Vector3f b = orb(an);
            assertTrue(new Vector3f(b).sub(a).normalize().z > 0.95f, mv + ": orbe vai para a frente");
            Vector3f head = new Vector3f(0, 35, 0).mul(m.voxelSize);
            assertTrue(a.distance(head) > 9 * m.voxelSize, mv + ": nasce fora da cabeça");
            assertTrue(a.z > 8 * m.voxelSize, mv + ": nasce à frente do corpo");
        }
    }

    static Vector3f orb(Animator an) {
        return an.fx.stream().filter(f -> f.kind == Animator.Fx.ORB).findFirst().orElseThrow().m.getTranslation(new Vector3f());
    }

    static Animator.Motion motion(String mv, float t) {
        Animator.Motion mo = new Animator.Motion().set(Animator.Clip.IDLE, t);
        mo.move = mv;
        mo.moveT = t;
        mo.weight = 1f;
        return mo;
    }

    // ------------------------------------------------------------------ 5. duas mãos (mecanismo)

    @Test
    void twoHandedGripKeepsTheLeftHandOnTheHandle() throws IOException {
        // as proporções da E não comportam a lâmina longa em duas mãos (ver guia); o MECANISMO é testado
        // numa pose sintética alcançável: com lGripW = 1 a mão esquerda tem de estar no cabo
        Path tmp = Files.createTempDirectory("choreo");
        Files.writeString(tmp.resolve("twohand.json"), """
                {"name":"twohand","rightItem":"weapon","tipLength":26,
                 "poses":{"low":{"r":[-1.0,18.5,7.0],"rDir":[-0.3,-0.2,0.93],"lMode":"grip","lGrip":3.0,"lElbow":[1,-1,0]},
                          "low2":{"base":"low","r":[-1.5,18.5,7.0],"rDir":[-0.2,-0.3,0.93]}},
                 "moves":{"hold":{"keys":[{"t":0,"pose":"low","stop":true},{"t":0.5,"pose":"low2","stop":true}]}}}
                """);
        Path saved = Choreo.root;
        try {
            Choreo.root = tmp;
            CharacterModel m = rig("characters/r4/adventurer_longblade");
            m.meta.addProperty("weaponProfile", "twohand");
            Animator an = new Animator(m, 1f);
            for (float t = 0; t <= 0.5f; t += 0.05f) {
                an.evaluate(motion("hold", t), an.newPose(), new Matrix4f(), null);
                assertTrue(an.reachErrL < WeaponCheck.TOL_REACH, "mão esquerda no cabo em " + t + ": " + an.reachErrL);
                Vector3f handle = new Vector3f(an.dbgTip).sub(an.dbgHandR).normalize().mul(-3.0f * m.voxelSize).add(an.dbgHandR);
                assertEquals(0f, an.dbgHandL.distance(handle) / m.voxelSize, 0.6f, "esquerda 3 voxels abaixo da direita no cabo");
            }
        } finally {
            Choreo.root = saved;
            Choreo.clearCache();
        }
    }

    // ------------------------------------------------------------------ 6. continuidade, determinismo, taxa de quadros

    @Test
    void sequencesAreDeterministicAndIndependentOfFrameRate() {
        CharacterModel m = rig("characters/e/adventurer");
        Animator an = new Animator(m, 1f);
        Sequencer seq = new Sequencer("run:0.9,slash_h,slash_v,walk:0.6", an);
        float T = 1.7f;
        Animator.Pose a = an.newPose(), b = an.newPose();
        // "30 FPS" e "60 FPS" chegam ao mesmo instante por caminhos diferentes: a pose tem de ser idêntica
        Animator.Motion mo = new Animator.Motion();
        for (float t = 0; t < T; t += 1f / 30f) an.evaluate(seq.eval(t, mo), a, new Matrix4f(), null);
        an.evaluate(seq.eval(T, mo), a, new Matrix4f(), null);
        for (float t = 0; t < T; t += 1f / 60f) an.evaluate(seq.eval(t, mo), b, new Matrix4f(), null);
        an.evaluate(seq.eval(T, mo), b, new Matrix4f(), null);
        assertArrayEquals(a.v, b.v, 1e-6f);
        assertEquals(seq.distance(T), new Sequencer("run:0.9,slash_h,slash_v,walk:0.6", an).distance(T), 1e-6f);
    }

    @Test
    void chainedMovesNeverTeleportAPart() {
        // golpe → golpe e locomoção → golpe: nenhuma parte pode saltar entre duas amostras próximas
        CharacterModel m = rig("characters/e/adventurer");
        Animator an = new Animator(m, 1f);
        Sequencer seq = new Sequencer("walk:0.7,slash_h,slash_d,run:0.7,slash_v,idle:0.4", an);
        Animator.Motion mo = new Animator.Motion();
        Matrix4f[] prev = null;
        float dt = 1f / 240f, worst = 0f;
        for (float t = 0; t < seq.total; t += dt) {
            Animator.Pose p = an.newPose();
            an.evaluate(seq.eval(t, mo), p, new Matrix4f(), null);
            Matrix4f[] mats = new Matrix4f[m.parts.size()];
            for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
            an.compose(p, new Matrix4f(), mats);
            if (prev != null)
                for (int i = 0; i < mats.length; i++) {
                    CharacterModel.Part pt = m.parts.get(i);
                    Vector3f c = new Vector3f(pt.px, pt.py, pt.pz);
                    worst = Math.max(worst, mats[i].transformPosition(new Vector3f(c)).distance(prev[i].transformPosition(new Vector3f(c))));
                }
            prev = mats;
        }
        assertTrue(worst / m.voxelSize < 2.0f, "maior salto de um pivô em 1/240 s: " + worst / m.voxelSize + " voxels");
    }

    // ------------------------------------------------------------------ 7. perfis (configuração persistida)

    @Test
    void profilesParseInheritAndReferenceRealPoses() throws IOException {
        List<Path> files;
        try (var s = Files.list(Path.of("assets/anim"))) {
            files = s.filter(f -> f.toString().endsWith(".json")).toList();
        }
        assertTrue(files.size() >= 5, "espada, espada+escudo, lâmina longa, arco, cajado");
        for (Path f : files) {
            assertDoesNotThrow(() -> JsonParser.parseString(Files.readString(f)), f + " é JSON");
            Choreo c = Choreo.load(f);
            assertTrue(c.moves.containsKey(c.attack), f + ": golpe padrão existe");
            for (Choreo.Move mv : c.moves.values()) assertTrue(mv.duration() > 0.2f, f + "/" + mv.name);
        }
        Choreo shield = Choreo.load(Path.of("assets/anim/sword_shield.json"));
        assertTrue(shield.moves.containsKey("slash_h") && shield.moves.containsKey("shield_block"), "herança: golpes da espada + do escudo");
        assertEquals("shield", shield.leftItem);
    }

    @Test
    void withTempoScalesOnlyTheChosenMoveAndLeavesTheCachedProfileIntact() {
        Choreo base = Choreo.get("npc_vila");
        float d0 = base.moves.get("emote").duration();
        Choreo slow = base.withTempo("emote", 1.25f);
        assertEquals(d0 * 1.25f, slow.moves.get("emote").duration(), 1e-4f, "aceno 25% mais lento");
        assertEquals(d0, Choreo.get("npc_vila").moves.get("emote").duration(), 1e-6f, "perfil em cache não muda");
        assertSame(base, base.withTempo("emote", 1f), "andamento 1 devolve o mesmo perfil");
        float[] a = new float[Choreo.N], b = new float[Choreo.N];
        int[] da = new int[Choreo.ND], db = new int[Choreo.ND];
        base.sample(base.moves.get("emote"), 0.6f * d0, a, da);
        slow.sample(slow.moves.get("emote"), 0.6f * d0 * 1.25f, b, db);
        for (int i = 0; i < Choreo.N; i++) assertEquals(a[i], b[i], 1e-3f, "mesma pose no mesmo ponto relativo do gesto");
    }
}
