package vx.game;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import vx.viewer.Animator;
import vx.viewer.CharacterModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09.2 — mobilidade (ADRs 0017–0019): andar durante o golpe, esquiva e salto. Propriedades medidas no corpo
 * lógico (posição, velocidade, estados) e na pose desenhada (pé plantado), sempre em passos fixos de 1/120 s.
 */
class MobilityTest {
    static final float H = GameLogic.TICK;

    /** Arena sem inimigos por perto (cegos e surdos) com o perfil pedido; herói em (x, z) olhando para +z. */
    static GameLogic quiet(String feel, float x, float z) {
        GameLogic g = CombatTest.arena();
        g.applyFeel(feel);
        for (Enemy e : g.combat.enemies) { e.def.sight = 0f; e.def.hearing = 0f; }
        g.player.place(x, z, 0f, g.collision);
        g.player.snapshot();
        return g;
    }

    static void run(GameLogic g, float secs, BiFunction<Float, Combat, Hero.Intent> in) { CombatTest.run(g, secs, in); }

    static Hero.Intent move(float vx, float vz, boolean attack) {
        float l = (float) Math.hypot(vx, vz);
        return new Hero.Intent().set(vx, vz, l > 0.01f, attack, false, 0f);
    }

    /** Deslocamento durante um corte horizontal com o jogador empurrando (vx, vz) o tempo todo. */
    static float[] attackTravel(String feel, float dirX, float dirZ) {
        GameLogic g = quiet(feel, -1.0f, 8.6f);
        Player p = g.player;
        float sp = p.walk;
        run(g, 0.5f, (t, c) -> move(dirX * sp, dirZ * sp, false));          // já andando
        float x0 = p.x, z0 = p.z;
        float[] got = {0, 0, 0};
        run(g, 1.12f, (t, c) -> move(dirX * sp, dirZ * sp, t < H * 0.5f));     // um clique, num passo só
        got[0] = p.x - x0;
        got[1] = p.z - z0;
        got[2] = g.combat.hero.swing;
        return got;
    }

    @Test
    void theAgileProfileKeepsMovingThroughTheSlashAndTheOldOneStops() {
        float[] a = attackTravel("A", 0f, 1f), c = attackTravel("C", 0f, 1f), b = attackTravel("B", 0f, 1f);
        System.out.printf("MEDIDA golpe andando para a frente: A %.2f u, B %.2f u, C %.2f u em 1,12 s%n", a[1], b[1], c[1]);
        assertEquals(1f, a[2]);
        assertEquals(1f, c[2]);
        // A (rodada 09.1): só o avanço autorado (0,22 u) e o embalo freando; C: deslocamento real durante o golpe todo
        assertTrue(a[1] < 0.55f, "perfil A anda " + a[1] + " u no golpe (esperado: só o avanço)");
        assertTrue(c[1] > 1.0f, "perfil C anda " + c[1] + " u no golpe (esperado > 1 u)");
        assertTrue(c[1] > a[1] * 2.5f, "C bem mais móvel que A: " + c[1] + " × " + a[1]);
    }

    @Test
    void sidewaysAndBackwardInputStillMoveTheBodyDuringTheSlash() {
        float[] side = attackTravel("C", 1f, 0f), back = attackTravel("C", 0f, -1f), sideA = attackTravel("A", 1f, 0f), backA = attackTravel("A", 0f, -1f);
        System.out.printf("MEDIDA golpe de lado: A %.2f u, C %.2f u; recuando: A %.2f u, C %.2f u%n", sideA[0], side[0], backA[1], back[1]);
        // A: o corpo vira para o rumo pedido e só o avanço autorado + o embalo levam; C: o jogador continua andando
        assertTrue(Math.abs(side[0]) > Math.abs(sideA[0]) + 0.4f, "de lado no golpe: C " + side[0] + " × A " + sideA[0]);
        assertTrue(back[1] < backA[1] - 0.3f, "recuando no golpe: C " + back[1] + " × A " + backA[1]);
    }

    @Test
    void theSpeedInEachPhaseRespectsTheProfileCap() {
        GameLogic g = quiet("C", -1.0f, 8.6f);
        Player p = g.player;
        Hero h = g.combat.hero;
        CombatDef.Feel f = h.feel;
        float[] maxBy = new float[4];
        run(g, 1.1f, (t, c) -> {
            if (h.state == Hero.State.ATTACK) {
                int ph = Hero.phase(h.prof.moves.get(h.layer.move()), h.layer.moveT());
                float[] ex = h.prof.moves.get(h.layer.move()).phases.get("exec");
                if (ph != 1 && h.layer.moveT() > ex[1] + 0.25f || ph == 0 && h.layer.moveT() > 0.2f)
                    maxBy[ph] = Math.max(maxBy[ph], p.speed());
            }
            return move(-p.run, 0f, t < H * 0.5f);                         // correndo de lado (Shift): o teto manda
        });
        for (int ph : new int[]{0, 3}) {
            float cap = f.move()[ph] * f.walk();
            assertTrue(maxBy[ph] <= cap + 0.05f, "fase " + ph + ": " + maxBy[ph] + " u/s > teto " + cap);
        }
    }

    /** Altura e posição (mundo) dos dois tornozelos na pose desenhada no fim do passo. */
    static float[][] feet(Hero h, Player p, Animator an, Animator.Pose pose, Matrix4f[] mats, Collision c) {
        Animator.Motion mo = h.motion(new Animator.Motion(), p, 1f);
        Matrix4f root = new Matrix4f().translate(p.x, p.visualY, p.z).rotateY(p.yaw);
        an.evaluate(mo, pose, root, c.ground);
        an.compose(pose, root, mats);
        CharacterModel m = an.model();
        float[][] out = new float[2][];
        int k = 0;
        for (String sh : new String[]{"shin_l", "shin_r"}) {
            CharacterModel.Part part = m.parts.get(m.index.get(sh));
            Vector3f v = mats[m.index.get(sh)].transformPosition(new Vector3f(part.px, 1.5f * m.voxelSize, part.pz));
            out[k++] = new float[]{v.x, v.y, v.z};
        }
        return out;
    }

    /** Escorregamento (u/s) do pé de apoio durante um golpe andando, com as pernas na passada (legW) ou forçadas na base. */
    static float[] slip(boolean legs) {
        GameLogic g = quiet("C", -1.0f, 8.6f);
        Player p = g.player;
        Hero h = g.combat.hero;
        Animator an = new Animator(h.skel, 1f);
        Animator.Pose pose = an.newPose();
        Matrix4f[] mats = new Matrix4f[h.skel.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        List<Float> v = new ArrayList<>();
        float[][] prev = null;
        run(g, 0.6f, (t, c) -> move(0f, p.walk, false));
        for (int i = 0; i < Math.round(1.0f / H); i++) {
            p.snapshot();
            g.combat.tick(H, move(0f, p.walk, i == 0), p);
            if (!legs) { h.legW = 0f; h.pLegW = 0f; }
            float[][] f = feet(h, p, an, pose, mats, g.collision);
            if (prev != null && h.state == Hero.State.ATTACK) {
                float g0 = g.collision.ground.height(p.x, p.z);
                int low = f[0][1] < f[1][1] ? 0 : 1;                       // pé de apoio = o mais baixo
                if (f[low][1] - g0 < 0.0375f + 0.02f) v.add((float) Math.hypot(f[low][0] - prev[low][0], f[low][2] - prev[low][2]) / H);
            }
            prev = f;
        }
        float[] a = new float[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        Arrays.sort(a);
        return new float[]{a[a.length / 2], a[(int) (a.length * 0.9f)], p.walk};
    }

    @Test
    void walkingUnderTheSlashPlantsTheFeetInsteadOfSkating() {
        float[] with = slip(true), without = slip(false);
        System.out.printf("MEDIDA pé de apoio no golpe andando (perfil C, %.2f u/s): mediana %.3f u/s (p90 %.3f) com a passada; %.3f (p90 %.3f) com a base do golpe%n", with[2], with[0], with[1], without[0], without[1]);
        // sem a passada (pernas na base do golpe) o pé de apoio desliza com o corpo; com ela, fica no chão
        assertTrue(without[0] > 0.6f * with[2], "sem passada o pé deveria patinar: mediana " + without[0]);
        assertTrue(with[0] < 0.35f * without[0], "com passada: mediana " + with[0] + " u/s × sem: " + without[0]);
    }

    @Test
    void movementAndDodgeAreTheSameAtAnyFrameRate() {
        List<String> ref = null;
        for (float fps : new float[]{30f, 60f, 144f}) {
            GameLogic g = quiet("C", -1.0f, 8.6f);
            Input in = new Input();
            float dt = 1f / fps;
            int frames = Math.round(3.0f * fps);
            List<String> trace = new ArrayList<>();
            for (int f = 0; f < frames; f++) {
                List<vx.engine.Window.InputEvent> ev = new ArrayList<>();
                if (f == 0) ev.add(key(org.lwjgl.glfw.GLFW.GLFW_KEY_W, true));      // instantes múltiplos de 1/2 s: o mesmo
                if (f == Math.round(0.5f * fps)) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.DOWN, 0, 0, 0, 0, 0));
                if (f == Math.round(1.5f * fps)) ev.add(key(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, true));   // passo lógico em
                if (f == Math.round(2.5f * fps)) ev.add(key(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, true));      // qualquer FPS
                in.feed(ev);
                g.update(dt, in);
                in.down.remove(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT);
                in.down.remove(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
            }
            for (Combat.Event e : g.combat.events) trace.add(e.tick() + " " + e.text());
            trace.add(String.format("pos %.4f %.4f %.4f", g.player.x, g.player.z, g.player.y));
            if (ref == null) ref = trace;
            else assertEquals(ref, trace, "trajetória a " + fps + " FPS");
        }
        assertTrue(ref.stream().anyMatch(s -> s.contains("esquiva")), "a esquiva aconteceu: " + ref);
    }

    static boolean cross(float t, float dt, float at) { return t <= at && t + dt > at; }

    static vx.engine.Window.InputEvent key(int k, boolean down) {
        return new vx.engine.Window.InputEvent(down ? vx.engine.Window.InputEvent.Kind.KEY : vx.engine.Window.InputEvent.Kind.KEY_UP, 0, 0, 0, 0, k);
    }

    // ------------------------------------------------------------------ esquiva

    /** Rola na direção (dx, dz) a partir do repouso; devolve o deslocamento, o tempo invulnerável e o estado final. */
    static float[] rollFrom(GameLogic g, float dx, float dz) {
        Player p = g.player;
        Hero h = g.combat.hero;
        float x0 = p.x, z0 = p.z;
        float[] inv = {0f, -1f, -1f};
        run(g, 0.9f, (t, c) -> {
            if (h.evading()) { inv[0] += H; if (inv[1] < 0) inv[1] = t; inv[2] = t; }
            Hero.Intent i = move(dx * p.walk, dz * p.walk, false);
            i.dodge = t < H * 1.5f;
            if (t > 0.05f) i.vx = i.vz = 0f;                              // solta a direção: o rolamento segue sozinho
            i.moving = t <= 0.05f;
            return i;
        });
        return new float[]{p.x - x0, p.z - z0, inv[0], inv[1], inv[2]};
    }

    @Test
    void theRollTravelsItsDistanceInTheHeldDirectionWithABoundedInvulnerability() {
        CombatDef d = CombatTest.arena().combat.def;
        for (float[] dir : new float[][]{{0, 1}, {1, 0}, {-1, 0}, {0, -1}, {0.7071f, 0.7071f}}) {
            GameLogic g = quiet("C", 0f, 0f);
            float[] at = clearSpot(g, 2.3f);                               // chão livre e plano em volta (nada no caminho)
            g.player.place(at[0], at[1], 0f, g.collision);
            for (Enemy e : g.combat.enemies) { e.hp = 0; e.state = Enemy.State.DEAD; }
            float[] r = rollFrom(g, dir[0], dir[1]);
            float along = r[0] * dir[0] + r[1] * dir[1], across = Math.abs(r[0] * dir[1] - r[1] * dir[0]);
            assertEquals(d.roll.distance(), along, 0.25f, "distância na direção " + Arrays.toString(dir));
            assertTrue(across < 0.12f, "sai de lado: " + across + " na direção " + Arrays.toString(dir) + " (" + r[0] + ", " + r[1] + ")");
            float win = d.roll.invTo() - d.roll.invFrom();
            assertEquals(win, r[2], 2 * H + 1e-4f, "janela de invulnerabilidade");
            assertTrue(r[2] < 0.6f * h(g).anim.moveDuration(d.roll.move()), "invulnerável só em parte do gesto: " + r[2] + " s");
        }
    }

    static Hero h(GameLogic g) { return g.combat.hero; }

    /** Ponto da vila com chão pisável, quase plano e sem forma de corpo num raio r (busca determinística em grade). */
    static float[] clearSpot(GameLogic g, float r) {
        Collision c = g.collision;
        for (float z = 2f; z < 14f; z += 0.25f)
            for (float x = -6f; x < 6f; x += 0.25f) {
                float g0 = c.ground.height(x, z);
                boolean ok = true;
                for (int k = 0; k < 24 && ok; k++) {
                    double a = k * Math.PI / 12;
                    for (float d = 0.3f; d <= r && ok; d += 0.3f) {
                        float px = x + (float) Math.cos(a) * d, pz = z + (float) Math.sin(a) * d;
                        if (!c.walkable(px, pz) || Math.abs(c.ground.height(px, pz) - g0) > 0.26f) ok = false;
                    }
                }
                for (Collision.Shape s : c.shapes) if (ok && s.body && Math.hypot(s.cx - x, s.cz - z) < r + s.bound + 0.3f) ok = false;
                if (ok) return new float[]{x, z};
            }
        throw new IllegalStateException("sem chão livre de " + r + " u na vila");
    }

    @Test
    void withoutADirectionTheDodgeIsABackstepFacingForward() {
        GameLogic g = quiet("C", -1.0f, 8.0f);
        Player p = g.player;
        float yaw0 = p.yaw;
        float[] r = rollFrom(g, 0f, 0f);
        assertTrue(r[1] < -0.6f, "recua para trás: " + r[1]);
        assertEquals(0f, Hero.angle(p.yaw - yaw0), 0.05f, "continua de frente");
        assertEquals(1, g.combat.events.stream().filter(e -> e.text().contains("recuo")).count());
    }

    @Test
    void invulnerabilityProtectsFromDamageButNeverFromWalls() {
        // parede: rola contra o poço/casa — o corpo para na colisão (não atravessa), mesmo invulnerável
        GameLogic g = quiet("C", -1.0f, 8.0f);
        Collision.Shape wall = null;
        for (Collision.Shape s : g.collision.shapes) if (s.body && s.name.startsWith("house")) { wall = s; break; }
        assertNotNull(wall, "a vila tem casa");
        Player p = g.player;
        float r = wall.circle ? wall.r : Math.max(wall.hx, wall.hz);
        float sx = wall.cx, sz = wall.cz - r - 0.9f;
        p.place(sx, sz, 0f, g.collision);
        if (!g.collision.walkable(sx, sz)) return;                        // casa encostada no limite: sem caso aqui
        rollFrom(g, 0f, 1f);
        float[] q = {p.x, p.z};
        float[] push = g.collision.resolve(q, Player.RADIUS - 0.01f, p.y, Player.HEIGHT, Player.STEP);
        assertTrue(Math.hypot(push[0], push[1]) < 1e-3f, "o rolamento terminou dentro da " + wall.name);
    }

    @Test
    void anAttackInsideTheEvasionWindowIsDodgedAndOutsideItHurts() {
        CombatDef d = CombatTest.arena().combat.def;
        for (float at = 0.0f; at < 0.56f; at += 0.04f) {
            GameLogic g = quiet("C", -1.0f, 8.0f);
            Player p = g.player;
            Hero h = g.combat.hero;
            final float when = at;
            Hero.Outcome[] got = {null};
            float[] tAt = {-1f};
            run(g, 0.7f, (t, c) -> {
                if (got[0] == null && h.state == Hero.State.DODGE && h.layer.moveT() >= when) {
                    tAt[0] = h.layer.moveT();
                    got[0] = h.receive(1, p.x + 0.6f, p.z, p, c);         // golpe válido chegando AGORA
                }
                Hero.Intent i = move(0f, t < 0.05f ? p.walk : 0f, false);
                i.dodge = t < H * 1.5f;
                return i;
            });
            if (got[0] == null) continue;
            boolean inside = tAt[0] >= d.roll.invFrom() && tAt[0] <= d.roll.invTo();
            assertEquals(inside ? Hero.Outcome.DODGED : Hero.Outcome.HURT, got[0], "golpe em t=" + tAt[0] + " do rolamento");
        }
    }

    @Test
    void dodgeSpamCannotKeepTheHeroInvulnerable() {
        GameLogic g = quiet("C", -1.0f, 8.0f);
        Hero h = g.combat.hero;
        float[] inv = {0f};
        float secs = 6f;
        run(g, secs, (t, c) -> {
            if (h.evading()) inv[0] += H;
            Hero.Intent i = move(t % 2f < 1f ? g.player.walk : -g.player.walk, 0f, false);
            i.dodge = true;                                               // aperta a cada passo
            return i;
        });
        float frac = inv[0] / secs;
        assertTrue(frac < 0.45f, "invulnerável " + Math.round(frac * 100) + "% do tempo apertando sem parar");
        assertTrue(h.dodges >= 6, "mas esquiva com frequência: " + h.dodges);
    }

    @Test
    void dodgeCancelsOnlyInsideTheMeasuredWindowsOfTheSlash() {
        CombatDef d = CombatTest.arena().combat.def;
        for (float at = 0.02f; at < 1.0f; at += 0.04f) {
            GameLogic g = quiet("C", -1.0f, 8.0f);
            Hero h = g.combat.hero;
            final float when = at;
            float[] cutAt = {-1f};
            run(g, 1.3f, (t, c) -> {
                Hero.Intent i = new Hero.Intent().set(0, 0, false, t < H * 1.5f, false, 0f);
                if (h.state == Hero.State.ATTACK && "slash_h".equals(h.layer.move()) && h.layer.moveT() >= when && cutAt[0] < 0) i.dodge = true;
                if (h.state == Hero.State.DODGE && cutAt[0] < 0) cutAt[0] = h.layer.prev != null ? h.layer.prev.dur : -2f;
                return i;
            });
            if (cutAt[0] >= 0f) assertTrue(d.dodgeAllowed("slash_h", cutAt[0]), "esquiva cortou o corte horizontal em " + cutAt[0] + " (fora das janelas medidas)");
        }
    }

    // ------------------------------------------------------------------ salto

    @Test
    void theJumpRisesToItsHeightAndLandsByGravity() {
        GameLogic g = quiet("C", -1.0f, 8.0f);
        Player p = g.player;
        float y0 = p.y;
        float[] top = {y0}, air = {0f};
        run(g, 1.2f, (t, c) -> {
            top[0] = Math.max(top[0], p.y);
            if (!p.grounded) air[0] += H;
            Hero.Intent i = new Hero.Intent();
            i.jump = t < H * 1.5f;
            return i;
        });
        CombatDef d = g.combat.def;
        assertEquals(d.jumpHeight, top[0] - y0, 0.04f, "altura do salto");
        float expect = (float) (Math.sqrt(2 * d.jumpHeight / d.gravityUp) + Math.sqrt(2 * d.jumpHeight / d.gravityDown));
        assertEquals(expect, air[0], 0.04f, "tempo no ar");
        assertTrue(p.grounded);
        assertEquals(y0, p.y, 1e-3f, "volta ao chão");
        assertEquals(1, p.jumps);
        assertEquals(1, p.landings);
    }

    @Test
    void aClickInTheAirComesOutOnLandingAndTheJumpIsRefusedMidSlash() {
        GameLogic g = quiet("C", -1.0f, 8.0f);
        Player p = g.player;
        Hero h = g.combat.hero;
        boolean[] attackedInAir = {false};
        run(g, 1.0f, (t, c) -> {
            if (!p.grounded && h.state == Hero.State.ATTACK) attackedInAir[0] = true;
            Hero.Intent i = new Hero.Intent();
            i.jump = t < H * 0.5f;
            i.attack = Math.abs(t - 0.2f) < H * 0.6f;                      // clique no meio do salto
            return i;
        });
        assertFalse(attackedInAir[0], "golpe no ar não existe nesta rodada");
        assertEquals(1, h.swing, "o clique do ar saiu ao pousar");
        int jumps = p.jumps;
        run(g, 0.4f, (t, c) -> { Hero.Intent i = new Hero.Intent(); i.jump = true; i.attack = t < H * 1.5f; return i; });
        assertTrue(h.state == Hero.State.ATTACK || p.jumps == jumps, "no meio do golpe o salto não sai");
    }

    @Test
    void coyoteAndBufferMakeTheJumpForgiving() {
        GameLogic g = quiet("C", -1.0f, 8.0f);
        Player p = g.player;
        // buffer: aperta um pouco antes de pousar e o segundo salto sai no pouso
        run(g, 1.2f, (t, c) -> {
            Hero.Intent i = new Hero.Intent();
            i.jump = t < H * 1.5f || (p.vy < 0f && p.y - c.collision.ground.height(p.x, p.z) < 0.08f && p.jumps == 1);
            return i;
        });
        assertEquals(2, p.jumps, "o aperto logo antes do pouso vale");
    }

    /**
     * Rodada 12.1 — todo sólido sustenta quem está acima dele (antes só o perfil "pisavel": quem pulava sobre uma cerca
     * caía dentro dela); pessoa nunca vira plataforma; e o que é alto demais (a casa) fica fora do alcance do salto.
     */
    @Test
    void everySolidCatchesAJumpButPeopleNeverDoAndHousesAreOutOfReach() {
        GameLogic g = quiet("C", -1.0f, 8.0f);
        Collision.Shape crate = null, house = null, low = null, person = null;
        for (Collision.Shape s : g.collision.shapes) {
            if (s.stand && s.body && crate == null && s.y1 - s.y0 < 0.65f) crate = s;
            if (!s.stand && s.body && !s.person && house == null && s.name.startsWith("house")) house = s;
            // o sólido NÃO marcado pisável mais baixo (o que o salto alcança e antes engolia quem pousava nele)
            if (!s.stand && s.body && !s.person && (low == null || s.y1 - s.y0 < low.y1 - low.y0)) low = s;
            if (s.person && person == null) person = s;
        }
        assertNotNull(crate, "há prop pisável na vila");
        float top = g.collision.floor(crate.cx, crate.cz, crate.y1 + 0.05f, Player.RADIUS);
        assertEquals(crate.y1, top, 1e-4f, "o topo do caixote é chão para quem está acima dele");
        assertTrue(g.collision.floor(crate.cx, crate.cz, crate.y0, Player.RADIUS) < crate.y1, "de baixo, não");
        assertNotNull(low, "há sólido não marcado pisável na vila");
        CombatDef jd = g.combat.def;
        assertTrue(low.y1 - low.y0 <= jd.jumpHeight + Player.STEP, low.name + " fora do alcance do salto: o caso não seria exercitado");
        assertEquals(low.y1, g.collision.floor(low.cx, low.cz, low.y1 + 0.05f, Player.RADIUS), 1e-4f, low.name + " segura quem está em cima");
        assertNotNull(person, "há NPC na vila");
        assertTrue(g.collision.floor(person.cx, person.cz, person.y1 + 0.05f, Player.RADIUS) < person.y1, "pessoa não vira plataforma");
        assertNotNull(house);
        assertTrue(house.y1 - house.y0 > jd.jumpHeight + Player.STEP + 0.2f, "a casa é alta demais para o salto (não vira atalho para o telhado)");
    }
}
