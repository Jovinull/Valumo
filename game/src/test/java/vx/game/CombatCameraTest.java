package vx.game;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09.1 — a câmera de combate na arena real, medida como o jogador veria ({@link CameraFraming}): nada de
 * posições escolhidas a dedo. A varredura cobre a arena inteira em grade, o fungo em 8 direções e 2 distâncias e a
 * vista do jogador em 5 desvios em relação ao fungo (de "fungo atrás do herói" a 70° de lado). Também: sequências de
 * movimento (contornar o carvalho, giro rápido do mouse), estabilidade e independência da taxa de quadros.
 */
class CombatCameraTest {
    /**
     * Rodada 09.2: estes testes medem o AJUSTE AUTOMÁTICO da câmera de luta da 09.1 (viés de 35°, inclinação com o fungo
     * colado) contra a câmera da 09 — hoje um modo de comparação ({@code -Dvx.camera=091}), com o perfil de sensação A
     * (o movimento da 09.1). A câmera padrão da 09.2 (mira livre = exploração; mira travada) tem os próprios testes em
     * {@code TargetingTest}.
     */
    @org.junit.jupiter.api.BeforeEach
    void mode091() { FollowCamera.combatAssist = true; System.setProperty("vx.sensacao", "A"); System.setProperty("vx.encontro", "um"); }

    @org.junit.jupiter.api.AfterEach
    void back() { FollowCamera.combatAssist = "091".equals(System.getProperty("vx.camera")); System.clearProperty("vx.sensacao"); System.clearProperty("vx.encontro"); }

    static final float H = GameLogic.TICK;
    static final float[] ARENA = {-1.6f, 11.2f, 3.4f};
    static final float SETTLE = 3.5f;
    /** Diagnóstico: VX_TRACE="3 carvalho" imprime o cenário quadro a quadro (./gradlew :game:test --rerun ...). */
    static final String TRACE = System.getenv("VX_TRACE");

    /** r = assentada (SETTLE s); worst = pior momento a partir de 0,3 s (cobertura do herói); badTime = s com o herói dominando ou o fungo escondido. */
    record Case(float hx, float hz, float ex, float ez, float viewOff, CameraFraming.Result r, CameraFraming.Result worst, float badTime,
                float yawJitter, float distJitter) {
        @Override public String toString() {
            return String.format("herói (%.1f, %.1f) fungo (%.1f, %.1f) vista %+.0f°: %s; pior momento: %s; %.2f s ruins; oscilação final %.4f rad / %.4f u",
                    hx, hz, ex, ez, Math.toDegrees(viewOff), r, worst, badTime, yawJitter, distJitter);
        }
    }

    static boolean free(Collision c, float x, float z, float rad) {
        if (!c.walkable(x, z)) return false;
        float[] d = c.resolve(new float[]{x, z}, rad, c.ground.height(x, z), 1.0f, 0.12f);
        return Math.abs(d[0]) + Math.abs(d[1]) < 1e-4f;
    }

    /** Câmera de combate assentada (SETTLE s) com o herói parado em (hx, hz), o fungo em (ex, ez) e o jogador olhando viewOff do fungo. */
    static Case settle(GameLogic g, float hx, float hz, float ex, float ez, float viewOff, float pitch) {
        FollowCamera cam = g.cam;
        Collision c = g.collision;
        float hy = c.ground.height(hx, hz), ey = c.ground.height(ex, ez);
        cam.yaw = (float) Math.atan2(ex - hx, ez - hz) + viewOff + (float) Math.PI;    // atrás do herói, olhando para o fungo
        cam.pitch = pitch;
        cam.wantDist = FollowCamera.DEFAULT_DIST;
        cam.reset();
        int n = Math.round(SETTLE / H);
        float y0 = 0, y1 = 0, d0 = 9, d1 = 0, bad = 0;
        CameraFraming.Result worst = null;
        for (int i = 0; i < n; i++) {
            cam.combatFocus(ex, ey, ez, 1f);
            cam.update(H, hx, hy, hz, c);
            if (i >= Math.round(0.3f / H) && i % 6 == 0) {                 // 20 vezes por segundo, depois da entrada
                CameraFraming.Result m = CameraFraming.measure(cam, hx, hy, hz, ex, ey, ez, c);
                if (worst == null || m.heroCover() > worst.heroCover()) worst = m;
                if (m.heroDominates() || m.enemyShown() < 0.5f) bad += 6 * H;
            }
            if (i >= n - Math.round(0.5f / H)) {                           // último meio segundo: deve estar parada
                if (y0 == 0 && y1 == 0) { y0 = y1 = cam.curYaw; }
                y0 = Math.min(y0, cam.curYaw); y1 = Math.max(y1, cam.curYaw);
                d0 = Math.min(d0, cam.dist); d1 = Math.max(d1, cam.dist);
            }
        }
        return new Case(hx, hz, ex, ez, viewOff, CameraFraming.measure(cam, hx, hy, hz, ex, ey, ez, c), worst, bad, y1 - y0, d1 - d0);
    }

    /** Todos os casos da varredura. */
    static List<Case> sweep(GameLogic g) {
        Collision c = g.collision;
        List<Case> out = new ArrayList<>();
        float[] offs = {0f, (float) Math.toRadians(35), (float) Math.toRadians(-35), (float) Math.toRadians(70), (float) Math.toRadians(-70)};
        for (float x = ARENA[0] - ARENA[2]; x <= ARENA[0] + ARENA[2] + 1e-3f; x += 0.6f)
            for (float z = ARENA[1] - ARENA[2]; z <= ARENA[1] + ARENA[2] + 1e-3f; z += 0.6f) {
                if (Math.hypot(x - ARENA[0], z - ARENA[1]) > ARENA[2] || !free(c, x, z, Player.RADIUS)) continue;
                for (float d : new float[]{0.95f, 1.5f})
                    for (int a = 0; a < 8; a++) {
                        float ex = x + d * (float) Math.sin(a * Math.PI / 4), ez = z + d * (float) Math.cos(a * Math.PI / 4);
                        if (!free(c, ex, ez, 0.28f)) continue;
                        if (c.solidBetween(new Vector3f(x, c.ground.height(x, z) + 0.5f, z), new Vector3f(ex, c.ground.height(ex, ez) + 0.5f, ez)) != null) continue;
                        for (float off : offs) out.add(settle(g, x, z, ex, ez, off, 0.30f));
                    }
            }
        return out;
    }

    static String summary(List<Case> cs) {
        int dom = 0, hidden = 0, inside = 0, shaky = 0, everDom = 0;
        double dist = 0, bad = 0;
        for (Case k : cs) {
            if (k.worst().heroDominates()) everDom++;
            bad += k.badTime();
            if (k.r().heroDominates()) dom++;
            if (k.r().enemyShown() < 0.5f) hidden++;
            if (k.r().inside()) inside++;
            if (k.yawJitter() > 0.01f || k.distJitter() > 0.02f) shaky++;
            dist += k.r().dist();
        }
        return String.format("%d casos: herói domina a tela em %d (%.1f%%), fungo escondido (<50%% visível) em %d (%.1f%%), câmera dentro de algo em %d, "
                        + "oscilando parada em %d, distância média %.2f u; no caminho: herói domina em algum momento em %d (%.1f%%), tempo ruim médio %.2f s",
                cs.size(), dom, 100.0 * dom / cs.size(), hidden, 100.0 * hidden / cs.size(), inside, shaky, dist / cs.size(), everDom, 100.0 * everDom / cs.size(), bad / cs.size());
    }

    static int count(List<Case> cs, java.util.function.Predicate<Case> f) { return (int) cs.stream().filter(f).count(); }

    /**
     * A arena inteira: com a câmera assentada, o herói não domina a tela, o fungo aparece e a câmera não fica dentro de
     * nada nem oscilando — e isso é melhor que a câmera da rodada 09 medida do mesmo jeito.
     */
    @Test
    void sweepTheWholeArena() {
        GameLogic g = CombatTest.arena();
        FollowCamera.legacy09 = true;
        List<Case> before;
        try { before = sweep(g); } finally { FollowCamera.legacy09 = false; }
        List<Case> cs = sweep(g);
        System.out.println("varredura ANTES  (câmera da rodada 09): " + summary(before));
        System.out.println("varredura DEPOIS (rodada 09.1):         " + summary(cs));
        cs.stream().sorted(Comparator.comparingDouble(k -> -k.badTime())).limit(4).forEach(k -> System.out.println("  pior caso depois: " + k));
        int n = cs.size(), dom = count(cs, k -> k.r().heroDominates()), hid = count(cs, k -> k.r().enemyShown() < 0.5f);
        int inside = count(cs, k -> k.r().inside()), shaky = count(cs, k -> k.yawJitter() > 0.01f || k.distJitter() > 0.02f);
        assertTrue(n > 4000, "varredura pequena demais: " + n);
        assertTrue(dom <= n / 200, "herói domina a tela em " + dom + " de " + n);
        assertTrue(hid <= n / 200, "fungo escondido em " + hid + " de " + n);
        assertEquals(0, inside, "câmera assentada dentro de pedra/tronco/relevo");
        assertTrue(shaky <= n / 50, "câmera parada oscilando em " + shaky + " de " + n);
        assertTrue(dom < count(before, k -> k.r().heroDominates()) / 10, "não melhorou o domínio do herói em relação à rodada 09");
        double badB = before.stream().mapToDouble(Case::badTime).sum(), badA = cs.stream().mapToDouble(Case::badTime).sum();
        assertTrue(badA < badB / 3, "o tempo ruim no caminho não caiu: " + badA + " × " + badB);
    }

    // ------------------------------------------------------------------ sequências de jogo (lógica completa + entrada)

    /** O que o jogador faz em cada cenário: teclas seguradas, desvio da mira em relação ao fungo e se ataca/defende. */
    interface Plan { void at(float t, Set<Integer> keys, float[] aimOffset, boolean[] attackGuard); }

    record Run(String name, float badFrac, int insideFrames, float maxJump, int snaps, int frames, float minDist, float meanDist,
               float domFrac, float offFrac, float heroFrac, float scenFrac) {
        @Override public String toString() {
            return String.format("%-28s %4.1f%% do tempo ruim (herói domina %.1f%%; fungo fora da tela %.1f%%, atrás do herói %.1f%%, atrás do cenário %.1f%%), câmera dentro em %d de %d quadros, maior salto %.2f u/quadro, %d encolhidas bruscas, distância mín %.2f média %.2f",
                    name, badFrac * 100, domFrac * 100, offFrac * 100, heroFrac * 100, scenFrac * 100, insideFrames, frames, maxJump, snaps, minDist, meanDist);
        }
    }

    /*
     * Joga um cenário a 60 quadros/s pela entrada real (GameLogic.update): o jogador mira o fungo com o mouse
     * como uma pessoa (corrige 8×/s, com o desvio do plano), segura as teclas do plano e ataca/defende. O fungo luta de
     * verdade. Mede o enquadramento a cada quadro.
     */
    /** Rodada 09.2 (CameraModesTest): trava a mira (Q) aos 0,3 s; desvio fixo extra da mira do jogador (rad). */
    static boolean LOCK;
    static float AIM_BIAS;
    /** Rodada 09.2: o jogador centraliza o fungo NA TELA (mira pelo pivô da câmera, como quem joga), não alinha herói e fungo. */
    static boolean AIM_SCREEN;

    static Run play(String name, float hx, float hz, float ex, float ez, float secs, float aimGain, Plan plan) {
        GameLogic g = CombatTest.arena();
        Combat c = g.combat;
        Player p = g.player;
        p.place(hx, hz, (float) Math.atan2(ex - hx, ez - hz), g.collision);
        p.snapshot();
        CombatTest.putEnemy(c, ex, ez, hx, hz, Enemy.State.CHASE);
        c.hero.hp = 999;                                                  // montagem: o cenário não termina em derrota
        g.cam.yaw = (float) Math.atan2(ex - hx, ez - hz) + (float) Math.PI;
        g.cam.reset();
        Input in = new Input();
        float dt = 1f / 60f, mx = 640;
        in.feed(List.of(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.MOVE, mx, 360, 0, 0, 0)));
        Set<Integer> held = new HashSet<>();
        boolean guard = false;
        float lastAim = 0f;
        int bad = 0, inside = 0, snaps = 0, n = 0, dom = 0, off = 0, byHero = 0, scen = 0;
        float maxJump = 0, minDist = 99, sumDist = 0, lastDist = -1;
        Vector3f eye = new Vector3f(), last = null;
        for (int f = 0; f * dt < secs; f++) {
            float t = f * dt;
            Set<Integer> keys = new HashSet<>();
            float[] aim = {0f};
            boolean[] ag = {false, false};
            plan.at(t, keys, aim, ag);
            List<vx.engine.Window.InputEvent> ev = new ArrayList<>();
            for (int k : keys) if (!held.contains(k)) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY, mx, 360, 0, 0, k));
            for (int k : held) if (!keys.contains(k)) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY_UP, mx, 360, 0, 0, k));
            held.clear();
            held.addAll(keys);
            if (LOCK && f == Math.round(0.3f / dt)) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY, mx, 360, 0, 0, org.lwjgl.glfw.GLFW.GLFW_KEY_Q));
            float ax = AIM_SCREEN ? g.cam.pivot.x : p.x, az = AIM_SCREEN ? g.cam.pivot.z : p.z;
            float want = (float) Math.atan2(ax - c.enemy.x, az - c.enemy.z) + aim[0] + AIM_BIAS;
            float d = (float) Math.atan2(Math.sin(want - g.cam.yaw), Math.cos(want - g.cam.yaw));
            if (LOCK && g.targeting.locked()) mx += -(aim[0] - lastAim) / g.cam.sensitivity;   // travada: só o gesto do plano (giro)
            else mx += -d / g.cam.sensitivity * aimGain;
            lastAim = aim[0];
            ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.MOVE, mx, 360, 0, 0, 0));
            if (ag[0] && f % 42 == 0) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.DOWN, mx, 360, 0, 0, 0));
            if (ag[1] != guard) {
                ev.add(new vx.engine.Window.InputEvent(ag[1] ? vx.engine.Window.InputEvent.Kind.DOWN : vx.engine.Window.InputEvent.Kind.UP, mx, 360, 1, 0, 0));
                guard = ag[1];
            }
            in.feed(ev);
            g.update(dt, in);
            if (t < 0.3f) { last = null; continue; }                      // entrada na luta (montagem)
            CameraFraming.Result m = CameraFraming.measure(g.cam, p.x, p.visualY, p.z, c.enemy.x, c.enemy.y, c.enemy.z, g.collision);
            if (TRACE != null && name.startsWith(TRACE) && f % 6 == 0) {
                float base = g.cam.yaw, ang = (float) Math.atan2(c.enemy.x - p.x, c.enemy.z - p.z) - (float) Math.atan2(-Math.sin(base), -Math.cos(base));
                ang = (float) Math.atan2(Math.sin(ang), Math.cos(ang));
                System.out.printf("TRACE %s t=%.2f a=%+.0f° comb=%+.0f° avoid=%+.0f° vista-final×fungo=%+.0f° fungo a %.2f u %s | %s%n", FollowCamera.legacy09 ? "antes " : "depois", t,
                        Math.toDegrees(ang), Math.toDegrees(g.cam.combatYaw), Math.toDegrees(g.cam.avoidYaw),
                        Math.toDegrees(Math.atan2(Math.sin(ang - (g.cam.curYaw - base)), Math.cos(ang - (g.cam.curYaw - base)))),
                        Math.hypot(c.enemy.x - p.x, c.enemy.z - p.z), c.enemy.state, m + " | vista cortada por "
                                + g.collision.blocker(g.cam.pivot, g.cam.position(g.cam.wantDist, new Vector3f()), 0.18f));
            }
            n++;
            boolean hid = c.enemy.alive() && m.enemyShown() < 0.5f;
            if (m.heroDominates() || hid) bad++;
            if (m.heroDominates()) dom++;
            if (hid) {                                                    // o motivo principal da perda
                float mx2 = Math.max(m.offscreen(), Math.max(m.byHero(), m.byScenery()));
                if (mx2 == m.offscreen()) off++; else if (mx2 == m.byHero()) byHero++; else scen++;
            }
            if (m.inside()) {
                inside++;
                if (TRACE != null && name.startsWith(TRACE)) {
                    Vector3f e2 = g.cam.position(new Vector3f());
                    String what = e2.y < g.collision.ground.height(e2.x, e2.z) ? "relevo" : "?";
                    for (Collision.Shape sh : g.collision.shapes) if (sh.camera && sh.contains(e2.x, e2.y, e2.z, 0f)) what = sh.name;
                    System.out.printf("DENTRO t=%.2f %s olho (%.2f, %.2f, %.2f) dist %.2f incl %.2f%n", t, what, e2.x, e2.y, e2.z, g.cam.dist, g.cam.curPitch);
                }
            }
            g.cam.position(eye);
            if (last != null) maxJump = Math.max(maxJump, eye.distance(last));
            if (lastDist >= 0 && lastDist - g.cam.dist > 0.5f) snaps++;
            lastDist = g.cam.dist;
            last = new Vector3f(eye);
            minDist = Math.min(minDist, g.cam.dist);
            sumDist += g.cam.dist;
        }
        return new Run(name, (float) bad / n, inside, maxJump, snaps, n, minDist, sumDist / n, (float) dom / n, (float) off / n, (float) byHero / n, (float) scen / n);
    }

    static final int W = org.lwjgl.glfw.GLFW.GLFW_KEY_W, A = org.lwjgl.glfw.GLFW.GLFW_KEY_A, S = org.lwjgl.glfw.GLFW.GLFW_KEY_S,
            D = org.lwjgl.glfw.GLFW.GLFW_KEY_D;

    /** Os cenários pedidos na rodada 09.1 (casos 1–10), todos em movimento e com o fungo lutando. */
    static List<Run> scenarios() {
        List<Run> out = new ArrayList<>();
        Plan strafe = (t, k, aim, ag) -> { k.add(((int) (t / 2.5f)) % 2 == 0 ? D : A); ag[0] = true; };
        out.add(play("1 campo aberto", -1.8f, 9.8f, -1.8f, 11.0f, 7f, 0.13f, strafe));
        out.add(play("2 pedra grande", -3.4f, 11.2f, -2.2f, 11.4f, 7f, 0.13f,
                (t, k, aim, ag) -> { if (t < 1.2f) k.add(S); else k.add(((int) (t / 2f)) % 2 == 0 ? D : A); ag[0] = true; }));
        out.add(play("3 carvalho", 0.3f, 12.5f, -0.8f, 12.1f, 7f, 0.13f,
                (t, k, aim, ag) -> { if (t < 1.0f) k.add(S); else k.add(((int) (t / 2f)) % 2 == 0 ? A : D); ag[0] = true; }));
        out.add(play("4 toco", -3.0f, 10.0f, -2.0f, 10.6f, 7f, 0.13f,
                (t, k, aim, ag) -> { if (t < 1.0f) k.add(S); else k.add(((int) (t / 2f)) % 2 == 0 ? D : A); ag[0] = true; }));
        out.add(play("5 encosta", -1.6f, 12.6f, -1.6f, 13.6f, 7f, 0.13f, strafe));
        out.add(play("6 inimigo muito próximo", -1.8f, 10.6f, -1.8f, 11.2f, 6f, 0.13f, (t, k, aim, ag) -> ag[0] = true));
        out.add(play("7 inimigo atrás do herói", -1.0f, 11.0f, -1.0f, 12.0f, 6f, 0.13f,
                (t, k, aim, ag) -> { aim[0] = (float) Math.PI * Math.min(1f, t / 1.5f); ag[0] = t > 2f; }));   // o jogador se vira de costas
        out.add(play("8 giro rápido do mouse", -3.3f, 11.0f, -2.2f, 11.3f, 5f, 1f,
                (t, k, aim, ag) -> aim[0] = t < 1f ? 0f : t < 1.5f ? (float) (2 * Math.PI) * (t - 1f) / 0.5f : t < 2.5f ? 0f
                        : t < 3f ? -(float) (2 * Math.PI) * (t - 2.5f) / 0.5f : 0f));
        out.add(play("9 defesa junto à pedra", -3.4f, 11.0f, -2.3f, 11.3f, 6f, 0.13f, (t, k, aim, ag) -> ag[1] = true));
        out.add(play("10 dano junto à pedra", -3.4f, 11.0f, -2.3f, 11.3f, 6f, 0.13f, (t, k, aim, ag) -> { }));
        return out;
    }

    /**
     * Os 10 casos da rodada 09.1 em movimento, contra a câmera da rodada 09 nas mesmas entradas. Limites: a média do
     * tempo ruim cai a menos da metade e fica abaixo de 10%; nenhum caso piora mais que 2 pontos; a câmera não entra em
     * nada mais vezes que antes; casos sem obstáculo perto (campo aberto, de costas, defesa e dano junto à pedra) ficam
     * abaixo de 5%.
     */
    @Test
    void movingFightsAroundEveryObstacle() {
        FollowCamera.legacy09 = true;
        List<Run> before;
        try { before = scenarios(); } finally { FollowCamera.legacy09 = false; }
        List<Run> after = scenarios();
        double sb = 0, sa = 0;
        for (int i = 0; i < after.size(); i++) {
            System.out.println("cenário ANTES  " + before.get(i));
            System.out.println("cenário DEPOIS " + after.get(i));
            sb += before.get(i).badFrac();
            sa += after.get(i).badFrac();
        }
        sb /= after.size();
        sa /= after.size();
        System.out.printf("cenários: tempo ruim médio antes %.1f%%, depois %.1f%%%n", 100 * sb, 100 * sa);
        assertTrue(sa < 0.10 && sa < sb / 2, String.format("tempo ruim médio %.1f%% (antes %.1f%%)", 100 * sa, 100 * sb));
        for (int i = 0; i < after.size(); i++) {
            Run a = after.get(i), b = before.get(i);
            assertTrue(a.badFrac() <= b.badFrac() + 0.02f, "piorou: " + a + " × " + b);
            assertTrue(a.insideFrames() <= b.insideFrames(), "câmera entra em obstáculo mais que antes: " + a + " × " + b);
        }
        for (int i : new int[]{0, 6, 8, 9}) assertTrue(after.get(i).badFrac() < 0.05f, "caso simples ruim: " + after.get(i));
    }

    /**
     * O mouse manda: sem obstáculos, a vista final cresce junto com o rumo do mouse em toda a volta (sem zona morta nem
     * salto de lado como o giro em cone da rodada 09), e cada grau do mouse vira pelo menos meio grau na tela.
     */
    @Test
    void theMouseAlwaysMovesTheViewWithoutDeadZonesOrJumps() {
        for (int i = -720; i <= 720; i++) {
            float a = (float) Math.toRadians(i / 4f), b = (float) Math.toRadians((i + 1) / 4f);
            float slope = (FollowCamera.biasedAngle(b) - FollowCamera.biasedAngle(a)) / (b - a);
            assertTrue(slope >= 0.5f - 1e-3f && slope <= 1.6f, "inclinação " + slope + " em " + i / 4f + "°");
        }
        FollowCamera cam = new FollowCamera();
        float prev = Float.NaN;
        for (int k = 0; k <= 72; k++) {                                   // a volta inteira em passos de 5°, câmera assentada
            cam.yaw = (float) Math.toRadians(k * 5);
            cam.pitch = 0.30f;
            cam.reset();
            for (int i = 0; i < Math.round(3f / H); i++) {
                cam.combatFocus(0f, 0f, 1f, 1f);                          // fungo a 1 u à frente (+z) do herói na origem
                cam.update(H, 0f, 0f, 0f, null);
            }
            float view = (float) Math.toDegrees(FollowCamera.unwrapNear(cam.curYaw, cam.yaw));
            if (!Float.isNaN(prev)) {
                float step = view - prev;
                assertTrue(step >= 2.4f && step <= 8.1f, "5° de mouse viraram " + step + "° de vista em " + k * 5 + "°");
            }
            prev = view;
        }
    }

    /** Fora da luta (nenhum inimigo engajado) a câmera é a da rodada 08/09, passo a passo, inclusive junto a obstáculos. */
    @Test
    void explorationCameraIsUnchanged() {
        float[][] runs = new float[2][];
        for (int v = 0; v < 2; v++) {
            FollowCamera.legacy09 = v == 0;
            try {
                GameLogic g = CombatTest.arena();
                g.combat.enemy.def.sight = 0f;                            // o fungo não percebe: nada de luta
                g.combat.enemy.def.hearing = 0f;
                g.player.place(-2.6f, 10.6f, 0f, g.collision);
                g.cam.yaw = (float) Math.PI * 0.7f;
                g.cam.reset();
                Input in = new Input();
                List<Float> out = new ArrayList<>();
                for (int f = 0; f < 480; f++) {                            // anda junto à pedra e gira a câmera
                    List<vx.engine.Window.InputEvent> ev = new ArrayList<>();
                    if (f == 0) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY, 0, 0, 0, 0, A));
                    if (f == 200) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY_UP, 0, 0, 0, 0, A));
                    ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.MOVE, f * 3f, 360, 0, 0, 0));
                    in.feed(ev);
                    g.update(1f / 60f, in);
                    assertFalse(g.combat.engaged());
                    out.add(g.cam.curYaw); out.add(g.cam.curPitch); out.add(g.cam.dist); out.add(g.cam.pivot.x); out.add(g.cam.pivot.z);
                }
                runs[v] = new float[out.size()];
                for (int i = 0; i < out.size(); i++) runs[v][i] = out.get(i);
            } finally { FollowCamera.legacy09 = false; }
        }
        assertArrayEquals(runs[0], runs[1], "a câmera de exploração mudou");
    }

    /** A câmera de luta anda no passo fixo: as mesmas entradas dão a mesma câmera a 30, 60, 120 e 240 quadros/s. */
    @Test
    void theFightCameraIsTheSameAtAnyFrameRate() {
        float[] base = null;
        for (float fps : new float[]{60, 30, 120, 240}) {
            GameLogic g = CombatTest.arena();
            g.player.place(-3.0f, 10.0f, 0f, g.collision);
            g.player.snapshot();
            CombatTest.putEnemy(g.combat, -2.0f, 10.6f, -3.0f, 10.0f, Enemy.State.CHASE);
            g.combat.hero.hp = 999;
            g.cam.yaw = (float) Math.atan2(1.0, 0.6) + (float) Math.PI;
            g.cam.reset();
            Input in = new Input();
            int frames = Math.round(4f * fps), per = Math.round(fps / 30f);   // entradas a cada 1/30 s: chegam no mesmo passo
            for (int f = 0; f < frames; f++) {
                List<vx.engine.Window.InputEvent> ev = new ArrayList<>();
                if (f % per == 0) {
                    int k30 = f / per;
                    if (k30 == 0) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY, 0, 0, 0, 0, S));
                    if (k30 == 30) {
                        ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY_UP, 0, 0, 0, 0, S));
                        ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY, 0, 0, 0, 0, D));
                    }
                    if (k30 % 20 == 5) ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.DOWN, 0, 0, 0, 0, 0));
                    ev.add(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.MOVE, 640 + 6f * k30, 360, 0, 0, 0));
                }
                in.feed(ev);
                g.update(1f / fps, in);
            }
            float[] st = {g.cam.curYaw, g.cam.curPitch, g.cam.dist, g.cam.pivot.x, g.cam.pivot.y, g.cam.pivot.z, g.player.x, g.player.z};
            if (base == null) base = st;
            else assertArrayEquals(base, st, "câmera difere a " + fps + " quadros/s");
        }
    }

    /** O filtro rápido do raio da câmera não muda nenhum resultado (raios aleatórios pela vila e pela arena). */
    @Test
    void theRayFilterGivesExactlyTheSameAnswer() {
        GameLogic g = CombatTest.arena();
        java.util.Random r = new java.util.Random(91);
        int blocked = 0;
        for (int i = 0; i < 4000; i++) {
            Vector3f a = new Vector3f(-8 + r.nextFloat() * 14, 0.3f + r.nextFloat() * 3, -4 + r.nextFloat() * 20);
            Vector3f b = new Vector3f(a).add(-4 + r.nextFloat() * 8, -1.5f + r.nextFloat() * 4, -4 + r.nextFloat() * 8);
            float m = r.nextBoolean() ? 0.18f : 0f;
            float fast = g.collision.rayClear(a, b, m), all = g.collision.rayClear(a, b, m, true);
            assertEquals(all, fast, "raio " + a + " → " + b);
            if (all < 1f) blocked++;
        }
        assertTrue(blocked > 500, "poucos raios bloqueados para o teste valer: " + blocked);
    }

    /** O caso da captura camera/06 da rodada 09 (carvalho): reproduzido na lógica, a câmera não encosta no herói. */
    @Test
    void theRound09OakShotNoLongerHugsTheHero() {
        GameLogic g = CombatTest.arena();
        FollowCamera.legacy09 = true;
        Case before;
        try { before = settleFromClose(g); } finally { FollowCamera.legacy09 = false; }
        Case after = settleFromClose(g);
        System.out.println("carvalho (camera/06 da rodada 09) ANTES:  " + before);
        System.out.println("carvalho (camera/06 da rodada 09) DEPOIS: " + after);
        assertTrue(before.worst().heroDominates(), "a montagem não reproduziu o defeito da rodada 09: " + before);
        assertFalse(after.r().heroDominates(), "depois: " + after);
        assertTrue(after.badTime() < before.badTime() / 2, "depois: " + after + " × " + before);
    }

    /**
     * Como na captura: a câmera chega encostada (vinha de outro enquadramento) e o fungo prepara a cabeçada. Mede o
     * primeiro 1,5 s (a captura foi tirada a 0,62 s).
     */
    static Case settleFromClose(GameLogic g) {
        FollowCamera cam = g.cam;
        Collision c = g.collision;
        float hx = 0.4f, hz = 12.2f, ex = -0.5f, ez = 11.9f, hy = c.ground.height(hx, hz), ey = c.ground.height(ex, ez);
        cam.yaw = (float) Math.toRadians(250) + (float) Math.PI;         // "posicionar 0.4 12.2 250" do roteiro
        cam.pitch = 0.30f;
        cam.wantDist = FollowCamera.DEFAULT_DIST;
        cam.reset();
        cam.update(H, hx, hy, hz, c);
        cam.dist = 1.0f;                                                  // vinha encostada da captura anterior
        float bad = 0;
        CameraFraming.Result worst = null, m = null;
        for (int i = 0; i < Math.round(1.5f / H); i++) {
            cam.combatFocus(ex, ey, ez, 1f);
            cam.update(H, hx, hy, hz, c);
            if (i % 6 == 0) {
                m = CameraFraming.measure(cam, hx, hy, hz, ex, ey, ez, c);
                if (worst == null || m.heroCover() > worst.heroCover()) worst = m;
                if (m.heroDominates() || m.enemyShown() < 0.5f) bad += 6 * H;
            }
        }
        return new Case(hx, hz, ex, ez, 0f, m, worst, bad, 0f, 0f);
    }
}
