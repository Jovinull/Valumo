package vx.game;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import vx.viewer.Animator;
import vx.viewer.CharacterModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09.2 — vários inimigos (ADR 0021), mira opcional, ensaio de chefe, morte por desagregação (ADR 0022), barras
 * de vida e preferências. Tudo pela lógica (sem OpenGL).
 */
class GroupCombatTest {
    static final float H = GameLogic.TICK;
    static final float CX = -1.6f, CZ = 11.0f;

    /** Arena com o encontro pedido, perfil C, herói no meio da clareira olhando para +z. */
    static GameLogic encounter(String name) {
        System.setProperty("vx.encontro", name);
        try {
            GameLogic g = CombatTest.arena();
            g.applyFeel("C");
            g.player.place(CX, CZ, 0f, g.collision);
            g.player.snapshot();
            return g;
        } finally { System.clearProperty("vx.encontro"); }
    }

    static void put(Enemy e, Collision c, float x, float z, float faceX, float faceZ, Enemy.State s) {
        e.x = x; e.z = z; e.y = c.ground.height(x, z);
        e.yaw = (float) Math.atan2(faceX - x, faceZ - z);
        e.state = s; e.stateT = 0f;
        e.snapshot();
    }

    static Hero.Intent click(boolean on) { return new Hero.Intent().set(0, 0, false, on, false, 0f); }

    // ------------------------------------------------------------------ grupo

    @Test
    void theGroupEncounterHasThreeIndependentEnemies() {
        GameLogic g = encounter("grupo");
        Combat c = g.combat;
        assertEquals(3, c.enemies.size());
        assertNotSame(c.enemies.get(0).def, c.enemies.get(1).def, "números próprios por inimigo (casa, coleira)");
        Enemy a = c.enemies.get(0), b = c.enemies.get(1);
        a.receive(2, g.player.x, g.player.z);
        assertEquals(a.def.enemyHp - 2, a.hp);
        assertEquals(b.def.enemyHp, b.hp, "o dano de um não passa ao outro");
        a.receive(99, g.player.x, g.player.z);
        assertFalse(a.alive());
        assertTrue(b.alive() && c.enemies.get(2).alive());
        assertEquals(2, c.alive());
        assertEquals(Combat.Outcome.NONE, c.outcome, "com dois vivos não há vitória");
    }

    @Test
    void aWideSlashHitsTwoEnemiesInItsPathOnceEachAndNobodyOutsideIt() {
        GameLogic g = encounter("grupo");
        Combat c = g.combat;
        Player p = g.player;
        Collision col = g.collision;
        // dois à frente (um de cada lado do arco do corte horizontal) e um atrás, fora da trajetória
        put(c.enemies.get(0), col, p.x + 0.42f, p.z + 0.62f, p.x, p.z, Enemy.State.RECOVER);
        put(c.enemies.get(1), col, p.x - 0.42f, p.z + 0.62f, p.x, p.z, Enemy.State.RECOVER);
        put(c.enemies.get(2), col, p.x, p.z - 0.85f, p.x, p.z, Enemy.State.RECOVER);
        for (Enemy e : c.enemies) { e.def.recover = 9f; e.def.sight = 0f; }
        int[] hp0 = {c.enemies.get(0).hp, c.enemies.get(1).hp, c.enemies.get(2).hp};
        CombatTest.run(g, 1.2f, (t, cc) -> click(t < H * 0.5f));
        assertEquals(1, c.hero.swing);
        assertEquals(hp0[0] - 1, c.enemies.get(0).hp, "inimigo da direita: um acerto");
        assertEquals(hp0[1] - 1, c.enemies.get(1).hp, "inimigo da esquerda: um acerto");
        assertEquals(hp0[2], c.enemies.get(2).hp, "o de trás não está na trajetória");
        long hits = c.events.stream().filter(e -> e.kind().equals("acerto")).count();
        assertEquals(2, hits, "dois acertos legítimos num golpe: " + c.events);
    }

    @Test
    void theGroupTakesTurnsSpreadsOutAndNeverStacks() {
        GameLogic g = encounter("grupo");
        Combat c = g.combat;
        int[] maxBusy = {0};
        float[] minGap = {99f}, minSep = {99f};
        List<Float> starts = new ArrayList<>();
        Enemy.State[] last = new Enemy.State[3];
        CombatTest.run(g, 14f, (t, cc) -> {
            int busy = 0;
            for (int i = 0; i < 3; i++) {
                Enemy e = cc.enemies.get(i);
                if (e.state == Enemy.State.WINDUP && last[i] != Enemy.State.WINDUP) starts.add(t);
                last[i] = e.state;
                if (e.state == Enemy.State.WINDUP || e.state == Enemy.State.ATTACK) busy++;
                for (int j = i + 1; j < 3; j++) {
                    Enemy o = cc.enemies.get(j);
                    if (t > 3f) minSep[0] = Math.min(minSep[0], (float) Math.hypot(e.x - o.x, e.z - o.z));
                }
            }
            maxBusy[0] = Math.max(maxBusy[0], busy);
            cc.hero.hp = cc.def.heroHp;                                      // o herói só aguenta (guarda) — mede o grupo
            return new Hero.Intent().set(0, 0, false, false, true, 0f);
        });
        for (int i = 1; i < starts.size(); i++) minGap[0] = Math.min(minGap[0], starts.get(i) - starts.get(i - 1));
        assertTrue(starts.size() >= 4, "o grupo ataca: " + starts.size() + " preparações em 14 s");
        assertTrue(maxBusy[0] <= c.def.maxAttackers, "no máximo " + c.def.maxAttackers + " atacando juntos: " + maxBusy[0]);
        assertTrue(minGap[0] >= c.def.attackGap - H, "preparações com intervalo ≥ " + c.def.attackGap + ": " + minGap[0]);
        assertTrue(minSep[0] >= 2 * c.enemy.def.enemyRadius - 0.02f, "corpos sobrepostos: " + minSep[0]);
    }

    @Test
    void deadEnemiesStopEverythingAndRestartBringsTheWholeGroupBack() {
        GameLogic g = encounter("grupo");
        Combat c = g.combat;
        Enemy a = c.enemies.get(0);
        a.receive(99, g.player.x, g.player.z);
        float x = a.x, z = a.z;
        CombatTest.run(g, 3f, (t, cc) -> new Hero.Intent());
        assertEquals(Enemy.State.DEAD, a.state);
        assertEquals(0f, (float) Math.hypot(a.x - x, a.z - z), 0.3f, "morto quase não se move (só o empurrão do golpe)");
        assertTrue(a.debugHit.isEmpty(), "morto não ataca");
        g.action("recomecar");
        for (Enemy e : c.enemies) { assertTrue(e.alive()); assertEquals(e.def.enemyHp, e.hp); assertEquals(Enemy.State.IDLE, e.state); }
    }

    @Test
    void theBossRehearsalIsMarkedAsATemporaryPrototypeWithItsOwnBar() {
        GameLogic g = encounter("chefe");
        Combat c = g.combat;
        Enemy boss = c.boss();
        assertNotNull(boss);
        assertTrue(boss.def.enemyPrototype, "recurso temporário de teste");
        assertTrue(boss.name().contains("protótipo"));
        assertEquals(1.6f, boss.scale, 1e-4f);
        assertTrue(boss.def.enemyHurt.r() > c.enemies.get(1).def.enemyHurt.r(), "hurtbox acompanha a escala");
        assertEquals(1, c.enemies.stream().filter(Enemy::boss).count());
        boss.hurtT = 0f;
        boss.state = Enemy.State.CHASE;
        assertTrue(GameLogic.bossBar(boss));
        assertFalse(GameLogic.smallBar("sempre", boss, null, 3f), "chefe não tem a barra pequena");
        Enemy minion = c.enemies.get(1);
        assertFalse(GameLogic.bossBar(minion), "comum nunca tem a barra de chefe");
        GameLogic one = encounter("um");
        one.combat.enemy.state = Enemy.State.CHASE;
        assertFalse(GameLogic.bossBar(one.combat.enemy), "o fungo sozinho não vira chefe");
    }

    @Test
    void smallBarsFollowThePreference() {
        GameLogic g = encounter("grupo");
        Enemy e = g.combat.enemies.get(0), other = g.combat.enemies.get(1);
        e.state = Enemy.State.CHASE;
        e.hurtT = 99f;
        assertFalse(GameLogic.smallBar("nunca", e, e, 3f));
        assertFalse(GameLogic.smallBar("dano", e, null, 3f), "sem dano e sem mira: escondida");
        assertTrue(GameLogic.smallBar("dano", e, e, 3f), "travado: aparece");
        e.hurtT = 1f;
        assertTrue(GameLogic.smallBar("dano", e, other, 3f), "ferido há pouco: aparece");
        e.hurtT = 3.5f;
        assertFalse(GameLogic.smallBar("dano", e, other, 3f), "passou o tempo: some");
        assertTrue(GameLogic.smallBar("sempre", e, null, 3f), "engajado: sempre");
        e.receive(99, 0, 0);
        assertFalse(GameLogic.smallBar("sempre", e, null, 3f), "morto: nunca");
    }

    @Test
    void preferencesPersistApartFromTheAppearance(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path f = dir.resolve("preferencias.json");
        Prefs p = new Prefs(f, "dano", "C", "um");
        p.cycleBars();
        assertEquals("sempre", p.bars);
        p.feel = "B";
        p.save();
        Prefs q = new Prefs(f, "dano", "C", "um").load();
        assertEquals("sempre", q.bars);
        assertEquals("B", q.feel);
        Files.writeString(f, "{ quebrado");
        Prefs r = new Prefs(f, "dano", "C", "um").load();
        assertEquals("dano", r.bars, "arquivo ilegível = padrões");
        Files.writeString(f, "{\"barrasInimigos\": \"talvez\"}");
        assertEquals("dano", new Prefs(f, "dano", "C", "um").load().bars, "valor inválido = padrão");
        assertFalse(Files.readString(dir.resolve("preferencias.json")).contains("cabelo"), "nada de aparência aqui");
        Prefs none = new Prefs(null, "dano", "C", "um");
        none.save();                                                      // sem arquivo (roteiros, testes): não grava
    }

    // ------------------------------------------------------------------ mira

    /** Rumo do chão para onde a câmera olharia com o herói de costas para ela olhando para +z. */
    static final float FWD = 0f;

    @Test
    void qLocksTheBestVisibleLivingEnemyNeverADeadOne() {
        GameLogic g = encounter("grupo");
        Combat c = g.combat;
        Player p = g.player;
        Collision col = g.collision;
        put(c.enemies.get(0), col, p.x + 2.4f, p.z + 0.6f, p.x, p.z, Enemy.State.CHASE);    // perto, mas de lado
        put(c.enemies.get(1), col, p.x - 0.2f, p.z + 2.2f, p.x, p.z, Enemy.State.CHASE);    // à frente
        put(c.enemies.get(2), col, p.x - 0.4f, p.z + 6.0f, p.x, p.z, Enemy.State.CHASE);    // longe à frente
        Targeting t = new Targeting();
        assertSame(c.enemies.get(1), t.toggle(p, c, FWD), "à frente e perto ganha");
        assertNull(t.toggle(p, c, FWD), "Q de novo solta");
        c.enemies.get(1).receive(99, p.x, p.z);
        assertNotSame(c.enemies.get(1), t.toggle(p, c, FWD), "morto nunca é escolhido");
        t.clear();
        put(c.enemies.get(0), col, p.x + 30f, p.z, p.x, p.z, Enemy.State.CHASE);
        put(c.enemies.get(2), col, p.x + 25f, p.z, p.x, p.z, Enemy.State.CHASE);
        assertNull(t.toggle(p, c, FWD), "ninguém no alcance: continua livre");
    }

    @Test
    void lineOfSightBlocksTheLock() {
        GameLogic g = encounter("um");
        Combat c = g.combat;
        Collision col = g.collision;
        Collision.Shape wall = null;
        for (Collision.Shape s : col.shapes) if (s.body && s.name.startsWith("house")) { wall = s; break; }
        assertNotNull(wall);
        Player p = g.player;
        float r = Math.max(wall.hx, wall.hz) + 0.9f;
        p.place(wall.cx, wall.cz - r, 0f, col);
        put(c.enemy, col, wall.cx, wall.cz + r, p.x, p.z, Enemy.State.CHASE);
        Targeting t = new Targeting();
        assertNull(t.best(p, c, FWD), "atrás da casa não trava");
    }

    @Test
    void tabCyclesAcrossTheScreenNotByCreationOrder() {
        GameLogic g = encounter("grupo");
        Combat c = g.combat;
        Player p = g.player;
        Collision col = g.collision;
        // criação: 0 = direita, 1 = esquerda, 2 = centro (na tela, câmera olhando para +z: direita = −x)
        put(c.enemies.get(0), col, p.x - 1.6f, p.z + 2.0f, p.x, p.z, Enemy.State.CHASE);
        put(c.enemies.get(1), col, p.x + 1.6f, p.z + 2.0f, p.x, p.z, Enemy.State.CHASE);
        put(c.enemies.get(2), col, p.x, p.z + 2.2f, p.x, p.z, Enemy.State.CHASE);
        Targeting t = new Targeting();
        assertSame(c.enemies.get(2), t.toggle(p, c, FWD), "centro primeiro");
        assertSame(c.enemies.get(0), t.cycle(1, p, c, FWD), "Tab: o da direita");
        assertSame(c.enemies.get(1), t.cycle(1, p, c, FWD), "Tab de novo: dá a volta para a esquerda");
        assertSame(c.enemies.get(0), t.cycle(-1, p, c, FWD), "Shift+Tab volta");
        assertTrue(t.switches >= 3);
    }

    @Test
    void theLockLetsGoOnDeathDistanceOrWhenHiddenTooLong() {
        GameLogic g = encounter("grupo");
        Combat c = g.combat;
        Player p = g.player;
        Collision col = g.collision;
        put(c.enemies.get(0), col, p.x, p.z + 1.5f, p.x, p.z, Enemy.State.CHASE);
        put(c.enemies.get(1), col, p.x + 1.2f, p.z + 1.2f, p.x, p.z, Enemy.State.CHASE);
        put(c.enemies.get(2), col, p.x + 40f, p.z, p.x, p.z, Enemy.State.IDLE);
        Targeting t = new Targeting();
        t.toggle(p, c, FWD);
        assertSame(c.enemies.get(0), t.target);
        c.enemies.get(0).receive(99, p.x, p.z);
        t.update(H, p, c, FWD);
        assertSame(c.enemies.get(1), t.target, "alvo morreu: passa ao engajado mais perto (aoMorrer = proximo)");
        c.enemies.get(1).receive(99, p.x, p.z);
        t.update(H, p, c, FWD);
        assertNull(t.target, "ninguém engajado perto: solta");
        assertEquals("morreu", t.lastRelease);
        GameLogic g2 = encounter("um");
        Enemy e = g2.combat.enemy;
        put(e, g2.collision, g2.player.x, g2.player.z + 2f, g2.player.x, g2.player.z, Enemy.State.CHASE);
        Targeting t2 = new Targeting();
        t2.toggle(g2.player, g2.combat, FWD);
        e.x = g2.player.x + g2.combat.def.lockKeep + 1f;
        t2.update(H, g2.player, g2.combat, FWD);
        assertNull(t2.target);
        assertEquals("longe", t2.lastRelease);
    }

    @Test
    void theLockedCameraTurnsSmoothlyShowsTheTargetAndReleasingKeepsTheWasdReference() {
        GameLogic g = encounter("um");
        Combat c = g.combat;
        Player p = g.player;
        Enemy e = c.enemy;
        e.def.sight = 0f; e.def.hearing = 0f;
        put(e, g.collision, p.x + 1.8f, p.z - 1.0f, p.x, p.z, Enemy.State.IDLE);    // atrás e à direita da vista
        g.cam.yaw = p.yaw + (float) Math.PI;
        g.cam.reset();
        Input in = new Input();
        for (int f = 0; f < 30; f++) { in.feed(List.of()); g.update(1f / 60f, in); }
        in.feed(List.of(MobilityTest.key(org.lwjgl.glfw.GLFW.GLFW_KEY_Q, true)));
        g.update(1f / 60f, in);
        assertSame(e, g.targeting.target);
        float maxRate = 0f, lastYaw = g.cam.curYaw, maxPitch = 0f;
        for (int f = 0; f < 120; f++) {
            in.feed(List.of());
            g.update(1f / 60f, in);
            maxRate = Math.max(maxRate, Math.abs(Hero.angle(g.cam.curYaw - lastYaw)) * 60f);
            lastYaw = g.cam.curYaw;
            maxPitch = Math.max(maxPitch, g.cam.curPitch);
        }
        assertTrue(maxRate <= FollowCamera.LOCK_TURN * 1.15f, "giro da câmera travada " + maxRate + " rad/s (limite " + FollowCamera.LOCK_TURN + ")");
        assertTrue(maxPitch <= FollowCamera.LOCK_MAX_PITCH + 0.05f, "terceira pessoa, não vista de cima: " + maxPitch);
        CameraFraming.Result fr = CameraFraming.measure(g.cam, p.x, p.visualY, p.z, e.x, e.y, e.z, g.collision);
        assertTrue(fr.enemyShown() > 0.6f, "alvo visível: " + fr);
        assertTrue(fr.heroCover() < 0.2f, "o herói não domina a tela: " + fr);
        float before = g.cam.inputYaw;
        in.feed(List.of(MobilityTest.key(org.lwjgl.glfw.GLFW.GLFW_KEY_Q, true)));
        g.update(1f / 60f, in);
        assertNull(g.targeting.target);
        float maxJump = 0f, prev = before;
        for (int f = 0; f < 60; f++) {
            in.feed(List.of());
            g.update(1f / 60f, in);
            maxJump = Math.max(maxJump, Math.abs(Hero.angle(g.cam.inputYaw - prev)));
            prev = g.cam.inputYaw;
        }
        assertTrue(maxJump < 0.06f, "soltar a mira não vira o WASD de uma vez: " + maxJump + " rad num quadro");
    }

    @Test
    void theFreeCameraNeverTurnsOrClimbsOnItsOwnBecauseOfEnemies() {
        GameLogic g = encounter("grupo");
        float yaw0 = g.cam.yaw;
        Input in = new Input();
        float maxPitch = 0f, maxCombatYaw = 0f;
        for (int f = 0; f < 6 * 60; f++) {                                 // 6 s de luta com o mouse parado
            in.feed(List.of());
            g.update(1f / 60f, in);
            g.combat.hero.hp = g.combat.def.heroHp;
            maxPitch = Math.max(maxPitch, g.cam.curPitch - g.cam.avoidPitch);
            maxCombatYaw = Math.max(maxCombatYaw, Math.abs(g.cam.combatYaw));
        }
        assertEquals(0f, maxCombatYaw, 1e-6f, "nenhum giro automático durante a luta");
        assertTrue(g.combat.engaged(), "houve luta");
        assertEquals(yaw0, g.cam.yaw, 1e-5f, "o rumo do jogador só muda com o mouse");
        assertTrue(maxPitch <= g.cam.pitch + 1e-3f, "sem subir para vista de cima: " + maxPitch);
        assertEquals(0f, g.cam.combatYaw, 1e-6f, "sem o giro de combate da 09.1 na mira livre (só ombro e busca de vista)");
    }

    @Test
    void lockedHeroWalksSidewaysFacingTheTarget() {
        GameLogic g = encounter("um");
        Combat c = g.combat;
        Player p = g.player;
        Enemy e = c.enemy;
        e.def.sight = 0f; e.def.hearing = 0f;
        put(e, g.collision, p.x, p.z + 2.2f, p.x, p.z, Enemy.State.IDLE);
        CombatTest.run(g, 1.5f, (t, cc) -> {
            Hero.Intent i = new Hero.Intent().set(p.walk, 0f, true, false, false, 0f);   // empurra de lado (+x)
            i.target = e;
            return i;
        });
        float face = Hero.angle(p.yaw - (float) Math.atan2(e.x - p.x, e.z - p.z));
        assertTrue(Math.abs(face) < 0.2f, "de frente para o alvo: " + Math.toDegrees(face) + "°");
        assertTrue(p.x - CX > 1.0f, "andou de lado: " + (p.x - CX));
        assertTrue(Math.abs(c.hero.locoDir) > 1.0f, "a passada é de lado (rumo relativo " + c.hero.locoDir + " rad)");
    }

    // ------------------------------------------------------------------ morte por desagregação

    static Matrix4f[] posed(CharacterModel m, float x, float y, float z) {
        Animator an = new Animator(m, 1f);
        Animator.Pose pose = an.newPose();
        Matrix4f[] mats = new Matrix4f[m.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        Matrix4f root = new Matrix4f().translate(x, y, z);
        an.evaluate(new Animator.Motion().set(Animator.Clip.HIT, DeathFx.BREAK), pose, root, null);
        an.compose(pose, root, mats);
        return mats;
    }

    @Test
    void theCreatureBreaksIntoItsOwnBlocksThatFallFadeAndEnd() {
        CombatDef d = CombatTest.arena().combat.def;
        CharacterModel m = CharacterModel.loadSkeleton(GameplayTest.A.resolve(d.enemyRig));
        VoxelCloud cloud = VoxelCloud.of(GameplayTest.A.resolve(d.enemyRig), 2);
        Matrix4f[] mats = posed(m, 0f, 0f, 0f);
        DeathFx fx = new DeathFx(cloud, mats, 1f, 0f, 0.35f, 0f, 0f, -1f, 42L, DeathFx.Style.D2, null);
        assertTrue(fx.n > 40 && fx.n <= DeathFx.MAX, "fragmentos: " + fx.n);
        java.util.Set<Integer> palette = new java.util.HashSet<>();
        for (VoxelCloud.Chunk ch : cloud.chunks) palette.add(ch.rgb());
        for (int i = 0; i < fx.n; i++) {
            assertTrue(palette.contains(fx.rgb[i]), "cor que existe no modelo");
            assertTrue(Math.hypot(fx.x[i], fx.z[i]) < 0.5f && fx.y[i] > -0.05f && fx.y[i] < m.height + 0.1f,
                    "nasce dentro do corpo: " + fx.x[i] + " " + fx.y[i] + " " + fx.z[i]);
        }
        assertTrue(fx.seeds > 0 && fx.seeds <= DeathFx.SEEDS, "D2: poucas sementes de luz");
        float minY = 99f, maxR = 0f;
        while (!fx.done()) {
            fx.update(1f / 60f);
            for (int i = 0; i < fx.n; i++) { minY = Math.min(minY, fx.lowest(i)); maxR = Math.max(maxR, (float) Math.hypot(fx.x[i], fx.z[i])); }
        }
        assertTrue(minY > -0.01f, "nenhum fragmento atravessa o chão: " + minY);
        assertTrue(maxR < 2.2f, "espalha pouco (não explode pela tela): " + maxR + " u");
        assertEquals(0, fx.visible(), "termina: nada fica flutuando");
        assertTrue(fx.t <= DeathFx.LIFE + DeathFx.STEP + 1e-4f, "duração fixa: " + fx.t);
        DeathFx again = new DeathFx(cloud, mats, 1f, 0f, 0.35f, 0f, 0f, -1f, 42L, DeathFx.Style.D2, null);
        again.update(0.5f);
        DeathFx ref = new DeathFx(cloud, mats, 1f, 0f, 0.35f, 0f, 0f, -1f, 42L, DeathFx.Style.D2, null);
        for (int k = 0; k < 30; k++) ref.update(1f / 60f);
        assertEquals(ref.x[7], again.x[7], 1e-5f, "determinístico e independente da taxa de quadros");
        DeathFx d1 = new DeathFx(cloud, mats, 1f, 0f, 0.35f, 0f, 0f, -1f, 42L, DeathFx.Style.D1, null);
        assertEquals(0, d1.seeds, "D1: só os blocos");
    }

    @Test
    void severalDeathsAtOnceStayCheap() {
        CombatDef d = CombatTest.arena().combat.def;
        CharacterModel m = CharacterModel.loadSkeleton(GameplayTest.A.resolve(d.enemyRig));
        VoxelCloud cloud = VoxelCloud.of(GameplayTest.A.resolve(d.enemyRig), 2);
        List<DeathFx> all = new ArrayList<>();
        for (int k = 0; k < 4; k++) all.add(new DeathFx(cloud, posed(m, k * 0.8f, 0f, 0f), k == 0 ? 1.6f : 1f, k * 0.8f, 0.35f, 0f, 0f, -1f, k, DeathFx.Style.D2, null));
        for (int f = 0; f < 30; f++) for (DeathFx fx : all) fx.update(1f / 60f);   // aquece
        long t0 = System.nanoTime();
        int frames = 0;
        while (all.stream().anyMatch(fx -> !fx.done())) { for (DeathFx fx : all) fx.update(1f / 60f); frames++; }
        double perFrame = (System.nanoTime() - t0) / 1e6 / Math.max(1, frames);
        assertTrue(perFrame < 0.5, "4 mortes juntas: " + perFrame + " ms por quadro (lógica)");
        assertTrue(all.stream().mapToInt(fx -> fx.n).sum() <= 4 * DeathFx.MAX);
    }
}
