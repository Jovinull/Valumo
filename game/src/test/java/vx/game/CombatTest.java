package vx.game;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import vx.engine.Window.InputEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09 — propriedades observáveis do primeiro combate (sem OpenGL). Cada teste monta uma situação na arena real
 * da cena jogável (ou numa colisão sintética quando precisa de uma parede exata) e verifica o que o jogador veria:
 * quem acerta quem, quando, quantas vezes, e o que bloqueia.
 */
class CombatTest {
    static final float H = GameLogic.TICK;

    /** Vila jogável já explorando; inimigo e herói onde o teste mandar. */
    static GameLogic arena() {
        GameLogic g = GameplayTest.village();
        g.state = GameLogic.State.EXPLORE;                               // como "Entrar na vila": sai do enquadramento da escolha
        g.cam.frame(false, null, 0, 0, 0);
        g.cam.pitch = 0.30f;
        return g;
    }

    static void face(Player p, float x, float z) { p.yaw = (float) Math.atan2(x - p.x, z - p.z); p.snapshot(); }

    static void putEnemy(Combat c, float x, float z, float yawToX, float yawToZ, Enemy.State s) {
        Enemy e = c.enemy;
        e.x = x; e.z = z; e.y = c.collision.ground.height(x, z);
        e.yaw = (float) Math.atan2(yawToX - x, yawToZ - z);
        e.state = s; e.stateT = 0f;
        e.snapshot();
    }

    /** Roda n segundos de combate com a intenção dada por passo (tempo, combate) → intenção. */
    static void run(GameLogic g, float secs, BiFunction<Float, Combat, Hero.Intent> in) {
        int n = Math.round(secs / H);
        for (int i = 0; i < n; i++) {
            g.player.snapshot();
            g.combat.tick(H, in.apply(i * H, g.combat), g.player);
        }
    }

    static Hero.Intent idle() { return new Hero.Intent(); }

    static Hero.Intent click() { return new Hero.Intent().set(0, 0, false, true, false, 0); }

    // ------------------------------------------------------------------ golpe do herói

    @Test
    void aSwingHitsAnEnemyInFrontOnlyDuringTheActiveWindowAndOnlyOnce() {
        GameLogic g = arena();
        Combat c = g.combat;
        Player p = g.player;
        p.place(-1.8f, 10.4f, 0, g.collision);
        putEnemy(c, -1.8f, 11.25f, p.x, p.z, Enemy.State.IDLE);
        face(p, c.enemy.x, c.enemy.z);
        c.enemy.def.sight = 0f;                                          // inimigo distraído: só o golpe importa
        CombatDef.Attack sh = c.def.attacks.get("slash_h");
        float[] exec = {sh.activeFrom(), sh.activeTo()};
        List<Float> hitAt = new ArrayList<>();
        int hp0 = c.enemy.hp;
        final boolean[] once = {false};
        run(g, 1.3f, (t, cc) -> {
            Hero.Intent i = !once[0] ? click() : idle();
            once[0] = true;
            int before = cc.enemy.hp;
            return i;
        });
        for (Combat.Event e : c.events) if (e.kind().equals("acerto")) hitAt.add(e.tick() * H);
        assertEquals(1, hitAt.size(), "um golpe, um acerto: " + c.events);
        assertEquals(hp0 - 1, c.enemy.hp);
        // o acerto cai dentro da execução do golpe (o golpe começou no passo 1)
        float t = hitAt.getFirst() - H;
        assertTrue(t >= exec[0] - 1e-3f && t <= exec[1] + H, "acerto fora da janela de dano: " + t + " (" + exec[0] + "–" + exec[1] + ")");
    }

    @Test
    void enemiesOutOfReachOrBehindTheHeroAreNotHit() {
        for (float[] where : new float[][]{{0f, 1.6f}, {0f, -0.7f}, {-0.75f, -0.45f}, {0.7f, -0.5f}}) {   // longe; atrás; atrás-lados
            GameLogic g = arena();
            Combat c = g.combat;
            Player p = g.player;
            p.place(-1.8f, 10.6f, 0, g.collision);
            p.yaw = 0f;                                                   // olha para +z
            p.snapshot();
            putEnemy(c, p.x + where[0], p.z + where[1], p.x, p.z, Enemy.State.IDLE);
            c.enemy.def.sight = 0f;
            c.hero.def.assistRange = 0f;                                  // sem giro de assistência: só a geometria
            final boolean[] once = {false};
            run(g, 1.3f, (t, cc) -> { Hero.Intent i = !once[0] ? click() : idle(); once[0] = true; return i; });
            assertEquals(c.def.enemyHp, c.enemy.hp, "acertou um inimigo em " + where[0] + "," + where[1] + ": " + c.events);
        }
    }

    @Test
    void aFastSwingIsSampledBetweenFramesSoAThinTargetIsNotSkipped() {
        // a ponta anda até ~4 voxels (0,1 u) por 1/480 s; um alvo fino de 2 cm entre duas amostras de 1/120 s seria pulado
        GameLogic g = arena();
        Hero h = g.combat.hero;
        Player p = g.player;
        p.place(0, 0, 0, g.collision);
        p.snapshot();
        h.layer.start("slash_h");
        float[] ex = h.prof.moves.get("slash_h").phases.get("exec");
        // a ponta passa por (0, y, ~0.97) no meio da execução; alvo fino ali
        float[] mid = h.bladeAt((ex[0] + ex[1]) / 2, 0, p.y, 0, 0, g.collision);
        Vector3f c0 = new Vector3f(mid[3], mid[4] - 0.2f, mid[5]), c1 = new Vector3f(mid[3], mid[4] + 0.2f, mid[5]);
        float r = 0.01f;
        boolean perFrame = false, perSub = false;
        for (float t = 0; t <= 1.1f; t += H) {
            float[] s = h.bladeAt(t, 0, p.y, 0, 0, g.collision);
            if (t >= ex[0] && t <= ex[1] && Hero.segDist(s, c0, c1) <= r + h.def.bladeR) perFrame = true;
        }
        for (float t = 0; t <= 1.1f; t += Hero.SUB) {
            float[] s = h.bladeAt(t, 0, p.y, 0, 0, g.collision);
            if (t >= ex[0] && t <= ex[1] && Hero.segDist(s, c0, c1) <= r + h.def.bladeR) perSub = true;
        }
        assertTrue(perSub, "as subamostras de 1/480 s pegam o alvo fino");
        // a afirmação forte (o que justifica as subamostras) só vale se a amostragem por quadro de fato o perder
        if (perFrame) System.out.println("aviso: o alvo fino escolhido também é pego a 120 Hz neste perfil");
    }

    @Test
    void aWallBetweenHeroAndEnemyStopsTheSwing() {
        GameLogic g = arena();
        Combat c = g.combat;
        Player p = g.player;
        // a pedra grande da arena: herói de um lado, inimigo do outro, perto o bastante para a lâmina alcançar por cima
        Collision.Shape rock = g.collision.named("rock_l").getFirst();
        p.place(rock.cx + rock.r + 0.30f, rock.cz, 0, g.collision);
        putEnemy(c, rock.cx - rock.r + 0.55f, rock.cz, p.x, p.z, Enemy.State.IDLE);
        face(p, c.enemy.x, c.enemy.z);
        c.enemy.def.sight = 0f;
        final boolean[] once = {false};
        run(g, 1.3f, (t, cc) -> { Hero.Intent i = !once[0] ? click() : idle(); once[0] = true; return i; });
        assertEquals(c.def.enemyHp, c.enemy.hp, "golpe atravessou a pedra: " + c.events);
    }

    @Test
    void theThreeCutsChainOnlyWithClicksInsideTheWindow() {
        GameLogic g = arena();
        Combat c = g.combat;
        Hero h = c.hero;
        Player p = g.player;
        p.place(-1.8f, 10.4f, 0, g.collision);
        c.enemy.state = Enemy.State.DEAD;                                // sem inimigo vivo
        List<String> moves = new ArrayList<>();
        // clica a cada 0,35 s por 2,4 s: encadeia h → d → v
        run(g, 2.4f, (t, cc) -> {
            String m = cc.hero.layer.move();
            if (m != null && (moves.isEmpty() || !moves.getLast().equals(m))) moves.add(m);
            return Math.round(t / H) % Math.round(0.35f / H) == 0 ? click() : idle();
        });
        assertEquals(List.of("slash_h", "slash_d", "slash_v"), moves.subList(0, 3), "sequência " + moves);
        // um clique só: um golpe só (sem encadear sozinho)
        GameLogic g2 = arena();
        g2.combat.enemy.state = Enemy.State.DEAD;
        List<String> one = new ArrayList<>();
        final boolean[] once = {false};
        run(g2, 1.6f, (t, cc) -> {
            String m = cc.hero.layer.move();
            if (m != null && (one.isEmpty() || !one.getLast().equals(m))) one.add(m);
            Hero.Intent i = !once[0] ? click() : idle(); once[0] = true; return i;
        });
        assertEquals(List.of("slash_h"), one);
    }

    @Test
    void anEarlyClickIsBufferedButNotForever() {
        GameLogic g = arena();
        Combat c = g.combat;
        c.enemy.state = Enemy.State.DEAD;
        Hero h = c.hero;
        // clique 0,15 s antes da janela de encadear: ainda vale (buffer 0,25 s)
        float acc0 = c.def.attacks.get("slash_h").acceptFrom();
        List<String> moves = new ArrayList<>();
        run(g, 2.0f, (t, cc) -> {
            String m = cc.hero.layer.move();
            if (m != null && (moves.isEmpty() || !moves.getLast().equals(m))) moves.add(m);
            boolean press = Math.abs(t) < H / 2 || Math.abs(t - (acc0 - 0.15f)) < H / 2;
            return press ? click() : idle();
        });
        assertTrue(moves.contains("slash_d"), "clique pouco antes da janela foi esquecido: " + moves);
    }

    // ------------------------------------------------------------------ inimigo

    @Test
    void theEnemyNoticesInFrontButNotBehindOrThroughTheRock() {
        GameLogic g = arena();
        Combat c = g.combat;
        Enemy e = c.enemy;
        Player p = g.player;
        e.reset(g.collision);
        float fx = (float) Math.sin(e.yaw), fz = (float) Math.cos(e.yaw);
        p.place(e.x + fx * 3.0f, e.z + fz * 3.0f, 0, g.collision);
        assertTrue(e.sees(p, g.collision), "não viu o herói 3 u à frente");
        p.place(e.x - fx * 3.0f, e.z - fz * 3.0f, 0, g.collision);
        assertFalse(e.sees(p, g.collision), "viu pelas costas a 3 u");
        p.place(e.x - fx * 1.2f, e.z - fz * 1.2f, 0, g.collision);
        assertTrue(e.sees(p, g.collision), "não ouviu o herói colado nas costas");
        // pedra grande entre os dois: não vê
        Collision.Shape rock = g.collision.named("rock_l").getFirst();
        e.x = rock.cx + rock.r + 0.45f; e.z = rock.cz; e.y = g.collision.ground.height(e.x, e.z);
        p.place(rock.cx - rock.r - 0.6f, rock.cz, 0, g.collision);
        e.yaw = (float) Math.atan2(p.x - e.x, p.z - e.z);
        assertFalse(e.sees(p, g.collision), "viu através da pedra");
    }

    @Test
    void theAttackIsTelegraphedAndCanBeAvoidedByWalkingAway() {
        // parado: leva a cabeçada, e o dano só chega depois de toda a preparação
        GameLogic g = arena();
        Combat c = g.combat;
        Player p = g.player;
        p.place(-1.8f, 10.9f, 0, g.collision);
        putEnemy(c, -1.8f, 11.85f, p.x, p.z, Enemy.State.CHASE);
        face(p, c.enemy.x, c.enemy.z);
        List<Enemy.State> seen = new ArrayList<>();
        final float[] windupAt = {-1}, hurtAt = {-1};
        run(g, 2.0f, (t, cc) -> {
            if (cc.enemy.state == Enemy.State.WINDUP && windupAt[0] < 0) windupAt[0] = t;
            if (cc.hero.hp < cc.def.heroHp && hurtAt[0] < 0) hurtAt[0] = t;
            return idle();
        });
        assertTrue(windupAt[0] >= 0 && hurtAt[0] > 0, "sem preparação ou sem dano: " + c.events);
        assertTrue(hurtAt[0] - windupAt[0] >= c.def.windup, "dano chegou antes do fim da preparação");
        // recuando ao ver a preparação: escapa
        GameLogic g2 = arena();
        Combat c2 = g2.combat;
        Player p2 = g2.player;
        p2.place(-1.8f, 10.9f, 0, g2.collision);
        putEnemy(c2, -1.8f, 11.85f, p2.x, p2.z, Enemy.State.CHASE);
        face(p2, c2.enemy.x, c2.enemy.z);
        final boolean[] fleeing = {false};
        run(g2, 2.0f, (t, cc) -> {
            if (cc.enemy.state == Enemy.State.WINDUP) fleeing[0] = true;
            return fleeing[0] && t < 1.4f ? new Hero.Intent().set(0, -Player.WALK, true, false, false, 0) : idle();
        });
        assertEquals(c2.def.heroHp, c2.hero.hp, "não deu para fugir da cabeçada telegrafada: " + c2.events);
    }

    @Test
    void aSidestepOnceTheAimLocksAvoidsTheHeadbutt() {
        // a mira trava em travaPreparo × preparo: correr de lado a partir daí escapa; parado, leva
        for (int side : new int[]{1, -1}) {
            GameLogic g = arena();
            Combat c = g.combat;
            Player p = g.player;
            p.place(-1.8f, 9.4f, 0, g.collision);
            putEnemy(c, -1.8f, 10.4f, p.x, p.z, Enemy.State.WINDUP);
            face(p, c.enemy.x, c.enemy.z);
            float lock = c.def.windup * c.def.prepLock;
            run(g, 1.6f, (t, cc) -> t >= lock && t < lock + 0.6f ? new Hero.Intent().set(side * Player.RUN, 0, true, false, false, 0) : idle());
            assertEquals(c.def.heroHp, c.hero.hp, "a esquiva para " + (side > 0 ? "a esquerda" : "a direita") + " não escapou: " + c.events);
        }
    }

    @Test
    void theEnemyNeverLeavesItsLeashAndGoesHomeHealed() {
        GameLogic g = arena();
        Combat c = g.combat;
        Enemy e = c.enemy;
        Player p = g.player;
        p.place(-1.8f, 10.2f, 0, g.collision);
        putEnemy(c, -1.8f, 12.0f, p.x, p.z, Enemy.State.CHASE);
        e.hp = 2;
        // o herói foge correndo para a vila (norte)
        float maxFromHome = 0;
        for (int i = 0; i < Math.round(8f / H); i++) {
            p.snapshot();
            c.tick(H, new Hero.Intent().set(0, -Player.RUN, true, false, false, 0), p);
            maxFromHome = Math.max(maxFromHome, (float) Math.hypot(e.x - e.homeX(), e.z - e.homeZ()));
        }
        assertTrue(maxFromHome <= c.def.leash + 1e-3f, "saiu da coleira: " + maxFromHome);
        run(g, 8f, (t, cc) -> idle());
        assertEquals(Enemy.State.IDLE, e.state, "não voltou para casa: " + e.state);
        assertEquals(c.def.enemyHp, e.hp, "não recuperou a vida em casa");
        // e nunca chega perto das moradoras
        for (GameLogic.Npc n : g.npcs)
            assertTrue(Math.hypot(n.def.x() - e.homeX(), n.def.z() - e.homeZ()) > c.def.leash + 4f, "coleira alcança " + n.def.name());
    }

    @Test
    void theEnemyGoesAroundOrGivesUpOnAnObstacleInsteadOfPushingForever() {
        GameLogic g = arena();
        Combat c = g.combat;
        Enemy e = c.enemy;
        Player p = g.player;
        Collision.Shape rock = g.collision.named("rock_l").getFirst();
        // herói do outro lado da pedra, dentro da coleira; o inimigo começa colado na pedra perseguindo
        p.place(rock.cx - rock.r - 0.5f, rock.cz, 0, g.collision);
        putEnemy(c, rock.cx + rock.r + 0.35f, rock.cz, p.x, p.z, Enemy.State.CHASE);
        e.seenAgo = 0f;
        float lastX = e.x, lastZ = e.z;
        int still = 0;
        for (int i = 0; i < Math.round(6f / H); i++) {
            p.snapshot();
            c.tick(H, idle(), p);
            for (Collision.Shape s : g.collision.shapes) if (s.body && s.push(e.x, e.z, c.def.enemyRadius - 0.02f) != null && s.y1 > e.y + 0.3f)
                fail("inimigo dentro de " + s.name);
            if (Math.hypot(e.x - lastX, e.z - lastZ) < 1e-4f && (e.state == Enemy.State.CHASE)) still++; else still = 0;
            assertTrue(still < Math.round(1.6f / H), "empurrou a pedra parado por mais de 1,6 s");
            lastX = e.x; lastZ = e.z;
        }
    }

    // ------------------------------------------------------------------ defesa, dano, morte, recomeço

    @Test
    void theShieldBlocksFrontalHeadbuttsOnlyWhenRaisedInTime() {
        // guarda erguida antes e de frente: bloqueia (sem dano, inimigo ricocheteia)
        GameLogic g = arena();
        Combat c = g.combat;
        Player p = g.player;
        p.place(-1.8f, 10.9f, 0, g.collision);
        putEnemy(c, -1.8f, 11.85f, p.x, p.z, Enemy.State.CHASE);
        face(p, c.enemy.x, c.enemy.z);
        float camYaw = p.yaw;
        run(g, 2.0f, (t, cc) -> new Hero.Intent().set(0, 0, false, false, true, camYaw));
        assertEquals(c.def.heroHp, c.hero.hp, "guarda de frente não bloqueou: " + c.events);
        assertTrue(c.events.stream().anyMatch(e -> e.kind().equals("defesa")), "nenhum bloqueio registrado");
        // guarda de costas para o inimigo: não bloqueia
        GameLogic g2 = arena();
        Combat c2 = g2.combat;
        Player p2 = g2.player;
        p2.place(-1.8f, 10.9f, 0, g2.collision);
        putEnemy(c2, -1.8f, 11.85f, p2.x, p2.z, Enemy.State.CHASE);
        float away = (float) Math.atan2(p2.x - c2.enemy.x, p2.z - c2.enemy.z);
        p2.yaw = away;
        run(g2, 2.0f, (t, cc) -> new Hero.Intent().set(0, 0, false, false, true, away));
        assertTrue(c2.hero.hp < c2.def.heroHp, "bloqueou um golpe pelas costas");
        // guarda erguida tarde demais (no último passo antes do impacto): não bloqueia
        GameLogic g3 = arena();
        Combat c3 = g3.combat;
        Player p3 = g3.player;
        p3.place(-1.8f, 10.9f, 0, g3.collision);
        putEnemy(c3, -1.8f, 11.85f, p3.x, p3.z, Enemy.State.WINDUP);
        face(p3, c3.enemy.x, c3.enemy.z);
        float yaw3 = p3.yaw;
        float late = c3.def.windup + c3.def.strike * c3.def.activeFrom - 0.05f;   // menos que a subida do escudo (0,12 s)
        run(g3, 1.6f, (t, cc) -> new Hero.Intent().set(0, 0, false, false, t >= late, yaw3));
        assertTrue(c3.hero.hp < c3.def.heroHp, "guarda erguida 0,05 s antes bloqueou (subida é " + c3.def.shieldRaise + " s)");
    }

    @Test
    void takingAHitReactsStunsAndProtectsForAMoment() {
        GameLogic g = arena();
        Combat c = g.combat;
        Hero h = c.hero;
        Player p = g.player;
        p.place(-1.8f, 10.6f, 0, g.collision);
        assertEquals(Hero.Outcome.HURT, h.receive(1, p.x, p.z + 1f, p, c));
        assertEquals(Hero.State.HIT, h.state);
        assertTrue(h.layer.move().startsWith("hit"), "sem reação: " + h.layer.move());
        assertEquals(Hero.Outcome.IGNORED, h.receive(1, p.x, p.z + 1f, p, c), "dois danos no mesmo instante");
        assertEquals(c.def.heroHp - 1, h.hp);
        assertTrue(p.speed() > 0.5f, "sem recuo");
        c.enemy.state = Enemy.State.DEAD;
        run(g, c.def.heroStun + 0.05f, (t, cc) -> idle());
        assertEquals(Hero.State.FREE, h.state, "o controle não voltou depois do atordoamento");
    }

    @Test
    void anAttackInterruptedByAHitUsesTheAuthoredExit() {
        GameLogic g = arena();
        Combat c = g.combat;
        Hero h = c.hero;
        Player p = g.player;
        p.place(-1.8f, 10.6f, 0, g.collision);
        c.enemy.state = Enemy.State.DEAD;
        final boolean[] once = {false};
        run(g, 0.55f, (t, cc) -> { Hero.Intent i = !once[0] ? click() : idle(); once[0] = true; return i; });
        assertEquals("slash_h", h.layer.move());
        h.receive(1, p.x, p.z + 1f, p, c);
        assertEquals("hit_cross_h", h.layer.move(), "logo depois do corte a reação deve ser a saída autorada");
    }

    @Test
    void enemyDiesStopsFightingAndTheFightRestarts() {
        GameLogic g = arena();
        Combat c = g.combat;
        Enemy e = c.enemy;
        Player p = g.player;
        p.place(-1.8f, 10.6f, 0, g.collision);
        e.hp = 1;
        assertTrue(e.receive(1, p.x, p.z));
        assertEquals(Enemy.State.DEAD, e.state);
        assertFalse(c.engaged());
        run(g, 2.0f, (t, cc) -> idle());
        assertEquals(Combat.Outcome.VICTORY, c.outcome);
        assertEquals(c.def.heroHp, c.hero.hp, "inimigo morto ainda feriu");
        g.action("recomecar");
        assertEquals(Combat.Outcome.NONE, c.outcome);
        assertEquals(Enemy.State.IDLE, e.state);
        assertEquals(c.def.enemyHp, e.hp);
        assertEquals(c.def.enemyX, e.x, 1e-5f);
    }

    @Test
    void heroDefeatLocksControlsShowsTheDefeatAndRestartsAtTheEntrance() {
        GameLogic g = arena();
        Combat c = g.combat;
        Hero h = c.hero;
        Player p = g.player;
        p.place(-1.8f, 10.6f, 0, g.collision);
        h.hp = 1;
        assertEquals(Hero.Outcome.KILLED, h.receive(1, p.x, p.z + 1f, p, c));
        c.enemy.def.sight = 0f;
        run(g, 3.5f, (t, cc) -> click());                                 // cliques não fazem nada caído
        assertEquals(Combat.Outcome.DEFEAT, c.outcome);
        assertEquals("defeat", h.layer.move(), "sem animação de derrota");
        assertEquals(Hero.State.DEAD, h.state);
        g.action("recomecar");
        assertEquals(Hero.State.FREE, h.state);
        assertEquals(c.def.heroHp, h.hp);
        assertEquals(c.def.restartX, p.x, 1e-4f);
        assertEquals(c.def.restartZ, p.z, 1e-4f);
    }

    // ------------------------------------------------------------------ jogo inteiro

    /** Confronto roteirizado pela FILA DE EVENTOS (o caminho do jogo), a fps quadros por segundo. */
    static List<String> scriptedFight(float fps) { return scriptedFight(fps, 0f); }

    /**
     * snapFps > 0: os instantes dos eventos são primeiro levados ao começo do quadro em que uma janela a snapFps os
     * entregaria — a mesma chegada, simulada a fps.
     */
    static List<String> scriptedFight(float fps, float snapFps) { return scriptedFight(fps, snapFps, g -> { }); }

    /** Rodada 09.1: idem, com uma montagem extra (ligar o som, por exemplo) antes da luta. */
    static List<String> scriptedFight(float fps, float snapFps, java.util.function.Consumer<GameLogic> setup) {
        GameLogic g = arena();
        setup.accept(g);
        Combat c = g.combat;
        Player p = g.player;
        p.place(-1.8f, 9.6f, 0, g.collision);
        p.yaw = 0f;
        p.snapshot();
        g.cam.yaw = (float) Math.PI;                                      // câmera atrás do herói, olhando para +z, já assentada
        g.cam.curYaw = (float) Math.PI;
        Input in = new Input();
        double dt = 1.0 / fps;
        // linha do tempo (s): anda 0,9 s, clica a cada 0,4 s até 5 s, segura a guarda de 5,2 a 7,5 s. Cada evento chega no
        // PRIMEIRO quadro que começa no instante dele ou depois (como uma janela real entrega a fila no começo do quadro).
        List<double[]> timeline = new ArrayList<>();                      // {instante, tipo}: 0 W↓, 1 W↑, 2 clique, 3 guarda↓, 4 guarda↑
        timeline.add(new double[]{0.0, 0});
        timeline.add(new double[]{0.9, 1});
        for (int k = 0; k < 10; k++) timeline.add(new double[]{1.0 + 0.4 * k, 2});
        timeline.add(new double[]{5.2, 3});
        timeline.add(new double[]{7.5, 4});
        if (snapFps > 0) for (double[] ev : timeline) {                   // começo do quadro dessa taxa → o passo lógico que o contém
            double arrive = Math.ceil(ev[0] * snapFps - 1e-9) / snapFps;
            ev[0] = Math.floor(arrive * 120 + 1e-6) / 120.0;
        }
        int next = 0;
        for (int f = 0; f * dt < 9.0; f++) {
            double t0 = f * dt;
            List<InputEvent> ev = new ArrayList<>();
            while (next < timeline.size() && timeline.get(next)[0] <= t0 + 1e-9) {
                int kind = (int) timeline.get(next++)[1];
                switch (kind) {
                    case 0 -> ev.add(new InputEvent(InputEvent.Kind.KEY, 0, 0, 0, 0, org.lwjgl.glfw.GLFW.GLFW_KEY_W));
                    case 1 -> ev.add(new InputEvent(InputEvent.Kind.KEY_UP, 0, 0, 0, 0, org.lwjgl.glfw.GLFW.GLFW_KEY_W));
                    case 2 -> ev.add(new InputEvent(InputEvent.Kind.DOWN, 0, 0, 0, 0, 0));
                    case 3 -> ev.add(new InputEvent(InputEvent.Kind.DOWN, 0, 0, 1, 0, 0));
                    default -> ev.add(new InputEvent(InputEvent.Kind.UP, 0, 0, 1, 0, 0));
                }
            }
            in.feed(ev);
            g.update((float) dt, in);
        }
        List<String> out = new ArrayList<>();
        for (Combat.Event e : c.events) out.add(e.tick() + " " + e.text());
        out.add("fim: herói " + c.hero.hp + " inimigo " + c.enemy.hp + " " + c.enemy.state);
        return out;
    }

    @Test
    void theSameInputGivesTheSameFightAtAnyFrameRate() {
        List<String> base = scriptedFight(60);
        assertTrue(base.stream().anyMatch(s -> s.contains("acerto")), "o roteiro não produziu acerto: " + base);
        for (float fps : new float[]{30, 120, 240})                       // entradas caem nos mesmos passos: idêntico
            assertEquals(base, scriptedFight(fps), "luta difere a " + fps + " FPS");
        // taxas que não dividem 120: a janela entrega o clique até um quadro depois (a 17 FPS, até 58 ms) — isso muda O QUE o
        // jogador fez, não a lógica. Com a mesma chegada, a luta simulada a 120 FPS é idêntica à simulada nessa taxa.
        for (float fps : new float[]{45, 144, 17.3f})
            assertEquals(scriptedFight(120, fps), scriptedFight(fps), "a lógica depende da taxa de quadros a " + fps + " FPS");
    }

    @Test
    void noTalkingDuringTheFightAndNoFightingDuringTalks() {
        GameLogic g = arena();
        Combat c = g.combat;
        Player p = g.player;
        // conversa com a Kelyra: o combate não anda
        GameLogic.Npc k = g.npcs.getFirst();
        p.place(k.def.x() + 1.0f, k.def.z(), 0, g.collision);
        face(p, k.def.x(), k.def.z());
        g.startDialog(k);
        long ticks = c.ticks;
        Input in = new Input();
        in.feed(List.of(new InputEvent(InputEvent.Kind.DOWN, 0, 0, 0, 0, 0)));
        g.update(0.5f, in);
        assertEquals(ticks, c.ticks, "o combate andou durante a conversa");
        assertNull(c.hero.layer.move(), "clique na conversa virou golpe");
        g.endDialog();
        // inimigo em combate (perto e de olho no herói): ninguém para conversar
        c.enemy.def.leash = 100f;
        c.enemy.def.sight = 100f;
        c.enemy.def.loseAfter = 100f;
        c.enemy.state = Enemy.State.CHASE;
        in.feed(List.of());
        g.update(1 / 60f, in);
        assertNull(g.candidate, "conversa oferecida no meio da luta");
    }

    /** Avança o jogo inteiro (lógica + câmera) por segundos, sem entrada. */
    static void play(GameLogic g, float secs) {
        Input in = new Input();
        for (int i = 0; i < Math.round(secs * 60); i++) { in.feed(List.of()); g.update(1 / 60f, in); }
    }

    @Test
    void theCombatCameraKeepsTheEnemyBesideTheHeroAndLetsGoAfterTheFight() {
        // rodada 09.2: o ajuste automático da 09.1 virou modo de comparação (-Dvx.camera=091); este teste o mantém honesto
        FollowCamera.combatAssist = true;
        try { combatCamera091(); } finally { FollowCamera.combatAssist = "091".equals(System.getProperty("vx.camera")); }
    }

    private void combatCamera091() {
        GameLogic g = arena();
        Combat c = g.combat;
        Player p = g.player;
        p.place(-1.8f, 10.0f, 0, g.collision);
        p.yaw = 0f;
        p.snapshot();
        g.cam.yaw = (float) Math.PI;                                      // atrás do herói: o inimigo bem na linha da vista
        g.cam.curYaw = g.cam.yaw;
        float playerPitch = g.cam.pitch;
        putEnemy(c, -1.8f, 10.8f, p.x, p.z, Enemy.State.CHASE);
        c.enemy.def.windup = 99f;                                          // segura o inimigo em preparação: só a câmera importa
        c.enemy.def.range = 0f;
        play(g, 2.0f);
        assertTrue(c.engaged());
        float fwd = (float) Math.atan2(g.cam.forwardX(), g.cam.forwardZ());
        float toE = (float) Math.atan2(c.enemy.x - p.x, c.enemy.z - p.z);
        float ang = Hero.angle(toE - fwd);
        // rodada 09.1: viés de um lado só — com o mouse no inimigo, ele fica BIAS à DIREITA do herói (ângulo negativo)
        assertTrue(ang <= -(FollowCamera.BIAS - Math.toRadians(3)), "inimigo atrás do herói na tela: " + Math.toDegrees(ang) + "°");
        CameraFraming.Result fr = CameraFraming.measure(g.cam, p.x, p.visualY, p.z, c.enemy.x, c.enemy.y, c.enemy.z, g.collision);
        assertTrue(fr.enemyShown() >= 0.5f && !fr.heroDominates(), "enquadramento: " + fr);   // mesmo critério de CombatCameraTest
        assertTrue(g.cam.curPitch >= FollowCamera.COMBAT_PITCH - 0.03f, "câmera não olhou de cima: " + g.cam.curPitch);
        assertEquals(playerPitch, g.cam.pitch, 1e-6f, "o ajuste mexeu na escolha do jogador");
        // luta acabou: a câmera volta à da rodada 08
        c.enemy.receive(99, p.x, p.z);
        play(g, 4.0f);
        assertEquals(0f, g.cam.assist, 0.01f);
        assertEquals(0f, g.cam.combatYaw, 0.01f);
        assertEquals(playerPitch, g.cam.curPitch, 0.02f);
    }

    /** Quadros com a câmera dentro de uma forma de câmera, girando pelo mouse em volta do herói colado no obstáculo. */
    static int insideFrames(String obstacle, boolean fight) {
        GameLogic g = arena();
        Combat c = g.combat;
        Player p = g.player;
        Collision.Shape s = g.collision.named(obstacle).getFirst();
        float side = obstacle.equals("rock_l") ? 1 : -1;
        p.place(s.cx + (s.r + 0.6f) * side, s.cz, 0, g.collision);
        if (fight) { putEnemy(c, p.x + 0.9f * side, p.z + 0.2f, p.x, p.z, Enemy.State.CHASE); c.enemy.def.windup = 99f; }
        else c.enemy.state = Enemy.State.DEAD;
        Input in = new Input();
        int inside = 0;
        float mx = 0;
        for (int i = 0; i < 60 * 8; i++) {                                // uma volta inteira em 8 s, pelo mouse
            mx += (float) (2 * Math.PI / (60 * 8)) / g.cam.sensitivity;
            in.feed(List.of(new InputEvent(InputEvent.Kind.MOVE, mx, 0, 0, 0, 0)));
            g.update(1 / 60f, in);
            org.joml.Vector3f cp = g.cam.position(new org.joml.Vector3f());
            for (Collision.Shape sh : g.collision.shapes) if (sh.camera && sh.contains(cp.x, cp.y, cp.z, 0.05f)) { inside++; break; }
            assertTrue(cp.y > g.collision.ground.height(cp.x, cp.z) + 0.1f, "câmera no chão");
        }
        return inside;
    }

    @Test
    void theCombatCameraAdjustmentNeverPutsTheCameraInsideTheRockOrTheTree() {
        // limite da rodada 08 (aceito e documentado): colado num obstáculo, a distância mínima de 0,9 u pode deixar a câmera
        // dentro dele por alguns quadros até o desvio girar. O ajuste de combate não pode piorar isso.
        for (String obstacle : List.of("rock_l", "tree_oak3")) {
            int base = insideFrames(obstacle, false), fight = insideFrames(obstacle, true);
            System.out.printf("câmera dentro de %s girando em volta: sem luta %d, com luta %d quadros (de 480)%n", obstacle, base, fight);
            assertTrue(fight <= base, "o ajuste de combate pôs a câmera dentro de " + obstacle + ": " + fight + " × " + base);
        }
    }

    @Test
    void theEnemyNeverTargetsTheVillagers() {
        GameLogic g = arena();
        Combat c = g.combat;
        Player p = g.player;
        p.place(g.scene.startX, g.scene.startZ, 0, g.collision);         // herói na vila
        run(g, 20f, (t, cc) -> idle());
        assertEquals(Enemy.State.IDLE, c.enemy.state, "inimigo saiu de casa sem o herói por perto");
        assertTrue(c.events.isEmpty(), "eventos de combate sem o herói na arena: " + c.events);
    }
}
