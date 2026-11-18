package vx.game;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import vx.viewer.Animator;
import vx.viewer.Appearance;
import vx.viewer.CharacterModel;
import vx.viewer.Composer;
import vx.viewer.WeaponCheck;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09.3 — polimento da esquiva e do salto: as animações mudaram, a função não. A esquiva R2 (escolhida pelo
 * proprietário) tem a distância, a invulnerabilidade e a duração da 09.2; de lado com a mira travada ela não vira o
 * corpo; nunca põe a cabeça no chão. O salto responde no mesmo passo e o pouso correndo não para a corrida; poses do
 * jogo (não só as do perfil) seguras com espada e escudo, no chão e contínuas; aparências com chapéu e capuz validadas.
 */
class DodgeJumpPolishTest {
    static final float H = GameLogic.TICK;

    static GameLogic setup() {
        GameLogic g = MobilityTest.quiet("C", 0f, 0f);
        float[] at = MobilityTest.clearSpot(g, 2.3f);
        g.player.place(at[0], at[1], 0f, g.collision);
        for (Enemy e : g.combat.enemies) { e.hp = 0; e.state = Enemy.State.DEAD; }
        return g;
    }

    @Test
    void theR2DodgeKeepsThe092DistanceInvulnerabilityAndDuration() {
        GameLogic g = setup();
        CombatDef d = g.combat.def;
        float[] r = MobilityTest.rollFrom(g, 0f, 1f);
        assertEquals("dodge_f", g.combat.hero.dodgeGesture, "a esquiva com direção é o salto baixo R2");
        assertEquals(1.9f, r[1], 0.25f, "distância (a da 09.2)");
        assertEquals(d.roll.invTo() - d.roll.invFrom(), r[2], 2 * H + 1e-4f, "tempo invulnerável (o da 09.2)");
        assertEquals(0.56f, g.combat.hero.anim.moveDuration("dodge_f"), 1e-4f, "duração do gesto = a da esquiva lógica");
        for (String side : new String[]{"dodge_l", "dodge_r"})
            assertEquals(0.56f, g.combat.hero.anim.moveDuration(side), 1e-4f, side);
    }

    /** Esquiva com a mira travada num alvo à frente: entrada (vx, vz) e o gesto/rumo resultantes. */
    static Object[] lockedDodge(float ix, float iz) {
        GameLogic g = MobilityTest.quiet("C", 0f, 0f);
        float[] at = MobilityTest.clearSpot(g, 2.3f);
        Player p = g.player;
        p.place(at[0], at[1], 0f, g.collision);
        Enemy e = g.combat.enemy;
        e.def.sight = 0f; e.def.hearing = 0f;
        for (Enemy o : g.combat.enemies) if (o != e) { o.hp = 0; o.state = Enemy.State.DEAD; }
        GroupCombatTest.put(e, g.collision, p.x, p.z + 3.0f, p.x, p.z, Enemy.State.IDLE);
        Hero h = g.combat.hero;
        float x0 = p.x, z0 = p.z;
        float[] maxOff = {0f};
        String[] gesture = {null};
        CombatTest.run(g, 0.9f, (t, c) -> {
            Hero.Intent i = new Hero.Intent().set(t < 0.05f ? ix * p.walk : 0f, t < 0.05f ? iz * p.walk : 0f, t < 0.05f, false, false, 0f);
            i.target = e;
            i.dodge = t < H * 0.5f;
            if (h.state == Hero.State.DODGE) {
                gesture[0] = h.dodgeGesture;
                maxOff[0] = Math.max(maxOff[0], Math.abs(Hero.angle(p.yaw - (float) Math.atan2(e.x - p.x, e.z - p.z))));
            }
            return i;
        });
        return new Object[]{gesture[0], maxOff[0], p.x - x0, p.z - z0};
    }

    @Test
    void lockedSideDodgesStayFacingTheTargetAndTravelSideways() {
        // herói olhando para +z; +x é a ESQUERDA dele
        Object[] left = lockedDodge(1f, 0f), right = lockedDodge(-1f, 0f), fwd = lockedDodge(0f, 1f);
        assertEquals("dodge_l", left[0]);
        assertEquals("dodge_r", right[0]);
        assertEquals("dodge_f", fwd[0], "à frente com a mira travada: o salto para a frente");
        for (Object[] s : new Object[][]{left, right})
            assertTrue((float) s[1] < Math.toRadians(25), "de lado o corpo continua encarando o alvo: " + Math.toDegrees((float) s[1]) + "°");
        assertEquals(1.9f, (float) left[2], 0.3f, "esquerda percorre a distância do rolamento");
        assertEquals(-1.9f, (float) right[2], 0.3f, "direita percorre a distância do rolamento");
    }

    // ------------------------------------------------------------------ poses do JOGO: armas, chão, continuidade

    /** Avalia a pose desenhada do herói a cada passo e mede armas × corpo, chão e saltos da ponta. */
    static final class PoseProbe {
        final Hero h;
        final Animator an;
        final Animator.Pose pose;
        final Matrix4f[] mats;
        final List<WeaponCheck.Vol> bind;
        float worstItem, lowest = 99f, maxTipStep, bodyContactMin = 99f;
        String lowestWhat = "";
        org.joml.Vector3f lastTip;

        PoseProbe(Hero h, CharacterModel m) {
            this.h = h;
            an = new Animator(m, 1f);
            pose = an.newPose();
            mats = new Matrix4f[m.parts.size()];
            for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
            bind = WeaponCheck.volumes(m);
        }

        void see(Player p, Collision c) {
            Animator.Motion mo = h.motion(new Animator.Motion(), p, 1f);
            Matrix4f root = new Matrix4f().translate(p.x, p.visualY, p.z).rotateY(p.yaw);
            an.evaluate(mo, pose, root, c.ground);
            an.compose(pose, root, mats);
            CharacterModel m = an.model();
            List<WeaponCheck.Vol> pv = WeaponCheck.posed(bind, mats);
            for (WeaponCheck.Vol a : pv) {
                org.joml.Vector3f lo = a.b() == null || a.a().y <= a.b().y ? a.a() : a.b();   // ponta mais baixa do volume
                float gap = lo.y - a.r() - c.floor(lo.x, lo.z, p.y + 0.01f, Player.RADIUS);  // relevo sob ELA (degraus de voxel)
                if (gap < lowest) { lowest = gap; lowestWhat = a.tag() + " em " + h.state + " " + h.layer.move() + " t=" + h.layer.moveT(); }
                if (!WeaponCheck.isItem(a.tag())) {
                    if (h.state == Hero.State.DODGE) bodyContactMin = Math.min(bodyContactMin, gap);
                    continue;
                }
                for (WeaponCheck.Vol b : pv) {
                    float tol = WeaponCheck.pairTol(m, a, b);
                    if (Float.isNaN(tol)) continue;
                    worstItem = Math.max(worstItem, WeaponCheck.penetration(a, b) / m.voxelSize - tol);
                }
            }
            CharacterModel.Part w = m.parts.get(m.index.get("weapon"));
            org.joml.Vector3f tip = mats[m.index.get("weapon")].transformPosition(new org.joml.Vector3f(w.px, w.py + 26 * m.voxelSize, w.pz));
            if (lastTip != null) maxTipStep = Math.max(maxTipStep, tip.distance(lastTip) - (float) Math.hypot(p.vx, p.vz) * H - Math.abs(p.vy) * H);
            lastTip = tip;
        }
    }

    @Test
    void inGameDodgesAndJumpsKeepWeaponsSafeOnTheGroundAndContinuous() {
        for (float[] dir : new float[][]{{0, 1}, {1, 0}, {-1, 0}, {0, -1}, {0, 0}})
                for (boolean lock : new boolean[]{false, true}) {
                    GameLogic g = setup();
                    Player p = g.player;
                    Enemy e = g.combat.enemy;
                    e.hp = e.def.enemyHp; e.state = Enemy.State.IDLE; e.def.sight = 0f; e.def.hearing = 0f;
                    GroupCombatTest.put(e, g.collision, p.x, p.z + 3.0f, p.x, p.z, Enemy.State.IDLE);
                    PoseProbe pr = new PoseProbe(g.combat.hero, g.combat.hero.skel);
                    CombatTest.run(g, 1.2f, (t, c) -> {
                        Hero.Intent i = new Hero.Intent().set(t < 0.05f ? dir[0] * p.walk : 0f, t < 0.05f ? dir[1] * p.walk : 0f,
                                t < 0.05f && (dir[0] != 0 || dir[1] != 0), false, false, 0f);
                        if (lock) i.target = e;
                        i.dodge = t < H * 0.5f;
                        i.jump = Math.abs(t - 0.8f) < H * 0.5f;
                        pr.see(p, c.collision);
                        return i;
                    });
                    String k = (lock ? "travado " : "livre ") + dir[0] + "," + dir[1];
                    assertTrue(pr.worstItem <= 1e-3f, k + ": arma/escudo atravessando o corpo " + pr.worstItem + " vox além da tolerância");
                    assertTrue(pr.lowest > walkingLowest() - 0.01f, k + ": " + pr.lowestWhat + " afunda no chão " + pr.lowest + " u (os pés andando: " + walkingLowest() + ")");
                    // continuidade: o validador aceita até 6 voxels por 1/480 s (0,6 u por passo de 1/120 s); aqui, 0,45 u
                    assertTrue(pr.maxTipStep < 0.45f, k + ": a ponta da espada salta " + pr.maxTipStep + " u num passo");
                }
    }

    private static float walkLow = Float.NaN;

    /** Quanto o corpo andando no mesmo lugar já afunda no relevo de voxel (degraus + suavização da altura): a referência. */
    static float walkingLowest() {
        if (!Float.isNaN(walkLow)) return walkLow;
        GameLogic g = setup();
        PoseProbe pr = new PoseProbe(g.combat.hero, g.combat.hero.skel);
        Player p = g.player;
        CombatTest.run(g, 1.5f, (t, c) -> { Hero.Intent i = new Hero.Intent().set(t < 0.7f ? 0f : 0f, (t < 0.75f ? 1 : -1) * p.walk, true, false, false, 0f); pr.see(p, c.collision); return i; });
        return walkLow = Math.min(0f, pr.lowest);
    }

    @Test
    void theR2DodgeNeverPutsTheHeadOnTheGround() {
        // o defeito da 09.2 era a "bananeira" (cabeça plantada no chão no meio do giro); o R2 não inverte o corpo
        for (float[] dir : new float[][]{{0, 1}, {1, 0}, {-1, 0}, {0, -1}})
            for (boolean lock : new boolean[]{false, true}) {
                GameLogic g = setup();
                Player p = g.player;
                Hero h = g.combat.hero;
                Enemy e = g.combat.enemy;
                e.hp = e.def.enemyHp; e.state = Enemy.State.IDLE; e.def.sight = 0f; e.def.hearing = 0f;
                GroupCombatTest.put(e, g.collision, p.x, p.z + 3.0f, p.x, p.z, Enemy.State.IDLE);
                PoseProbe pr = new PoseProbe(h, h.skel);
                float[] head = {99f};
                CombatTest.run(g, 0.7f, (t, c) -> {
                    Hero.Intent i = new Hero.Intent().set(t < 0.05f ? dir[0] * p.walk : 0f, t < 0.05f ? dir[1] * p.walk : 0f, t < 0.05f, false, false, 0f);
                    if (lock) i.target = e;
                    i.dodge = t < H * 0.5f;
                    pr.see(p, c.collision);
                    if (h.state == Hero.State.DODGE)
                        for (WeaponCheck.Vol a : WeaponCheck.posed(pr.bind, pr.mats))
                            if (a.tag().equals("head")) {
                                org.joml.Vector3f lo = a.b() == null || a.a().y <= a.b().y ? a.a() : a.b();
                                head[0] = Math.min(head[0], lo.y - a.r() - c.collision.floor(lo.x, lo.z, p.y + 0.01f, Player.RADIUS));
                            }
                    return i;
                });
                assertTrue(head[0] > 0.15f, (lock ? "travado " : "livre ") + dir[0] + "," + dir[1] + ": a cabeça chega a " + head[0] + " u do chão");
            }
    }

    // ------------------------------------------------------------------ salto

    @Test
    void theJumpLeavesTheGroundOnTheFirstStepAndShowsTheTakeoff() {
        GameLogic g = setup();
        Player p = g.player;
        float y0 = p.y;
        Hero.Intent i = new Hero.Intent();
        i.jump = true;
        p.snapshot();
        g.combat.tick(H, i, p);
        assertTrue(p.y > y0, "subiu no mesmo passo do aperto (sem segurar no chão)");
        assertEquals(0f, p.takeoffPhase(), 0.1f, "o impulso visual começa agora");
        Animator.Motion mo = g.combat.hero.motion(new Animator.Motion(), p, 1f);
        assertTrue(mo.takeoff >= 0f && mo.takeoff < 1f, "o desenho recebe a fase do impulso");
        CombatTest.run(g, 0.2f, (t, c) -> new Hero.Intent());
        assertEquals(-1f, p.takeoffPhase(), 1e-6f, "o impulso dura só o começo");
    }

    @Test
    void landingWhileRunningDoesNotStopTheRun() {
        GameLogic g = setup();
        Player p = g.player;
        float[] minAfter = {99f};
        boolean[] landed = {false};
        int[] land0 = {p.landings};
        CombatTest.run(g, 1.0f, (t, c) -> {
            Hero.Intent i = new Hero.Intent().set(0f, p.run, true, false, false, 0f);
            i.run = true;
            i.jump = Math.abs(t - 0.45f) < H * 0.5f;
            if (p.landings > land0[0] && p.landT < 0.2f) { landed[0] = true; minAfter[0] = Math.min(minAfter[0], p.speed()); }
            return i;
        });
        assertTrue(landed[0]);
        assertTrue(minAfter[0] > 0.9f * p.run, "correndo, o pouso não freia: " + minAfter[0] + " de " + p.run + " u/s");
        assertTrue(p.landDur < p.landTime, "absorção correndo mais curta (" + p.landDur + " s)");
    }

    // ------------------------------------------------------------------ acessórios

    @Test
    void appearancesWithHatsHoodsAndPacksStaySafeInEveryDodgeAndTheJump() {
        Composer comp = new Composer(GameplayTest.A, 4);
        comp.kit = "characters/r4/adventurer_shield";
        List<Appearance> looks = new ArrayList<>();
        for (String pr : comp.catalog.presets.keySet()) looks.add(Appearance.preset(pr));
        Appearance hat = Appearance.preset("aventureiro");
        hat.cover = "chapeu.maga";
        Appearance hood = Appearance.preset("maga");
        hood.cover = "capuz.c3";
        looks.add(hat);
        looks.add(hood);
        for (String pr : new String[]{"aventureiro", "maga"}) {               // "Preso" fora da exploradora: rabo centrado (09.4)
            Appearance tied = Appearance.preset(pr);
            tied.hair = "cabelo.rabo";
            tied.cover = Appearance.NO_COVER;
            looks.add(tied);
        }
        List<String> bad = new ArrayList<>();
        for (Appearance a : looks) {
            CharacterModel m = comp.build(comp.compose(a), false);
            for (String s : new String[]{"seq:idle:0.3,dodge_f,idle:0.4", "seq:run:0.4,dodge_f,run:0.4", "seq:walk:0.3,dodge_l,walk:0.3",
                    "seq:walk:0.3,dodge_r,walk:0.3", "seq:run:0.3,dodge_f,slash_h,idle:0.3", "seq:idle:0.3,backstep,idle:0.4"}) {
                WeaponCheck.Report r = WeaponCheck.validate(m, s);
                if (!r.ok()) bad.add(a.signature() + " " + r);
            }
        }
        assertTrue(bad.isEmpty(), bad.size() + " casos:\n" + String.join("\n", bad.stream().map(x -> x.lines().filter(l -> l.contains("FALHA") || l.contains("<--")).reduce("", (q, w) -> q + w + "\n")).toList()));
    }
}
