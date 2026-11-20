package vx.game;

import com.google.gson.JsonObject;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static vx.game.CompositeCollisionTest.*;

/**
 * Rodada 12.3 (P85) — a câmera dentro da torre caída, MEDIDA andando com a física real e a {@link FollowCamera} real, nos
 * mesmos trajetos, em dois modos: a câmera da rodada 12.2 (as paredes da torre seguram a câmera) e o recorte contextual
 * ({@link CameraCutaway}: a câmera ignora só as formas da torre enquanto o herói está nela; o desenho recorta, só na torre,
 * um cone câmera → herói). Torre em 5 rumos; cenários: entrada, escada, centro, mirante (com o salto), descida, saída, giro
 * rápido, mouse parado, perto da parede (de costas e de frente), diagonal e salto no lugar. Medidas sobre os quadros com o
 * herói DENTRO da torre: % de câmera colada (&lt; 1,2 u), o trecho colado mais longo, herói legível (3 de 5 pontos do corpo
 * vistos), interior visto (pontos do piso), desvio automático do rumo, oscilação (vai-e-volta e saltos da distância) e a
 * mudança da referência do WASD. A visibilidade no modo recorte conta o que o shader recorta: atrás de uma forma da torre,
 * um ponto se vê se está dentro do cone (k &lt; 0,75, o miolo sem pontilhado) e nada mais o tapa.
 * <p>
 * O relatório completo sai em {@code build/medidas/torre-camera.txt}, na raiz do repositório (entra no pacote da rodada).
 */
class TowerCameraTest {
    static final float TICK = 1f / 120f, FRAME = 1f / 60f, GLUED = 1.2f;
    static final float[] TOWER_YAWS = {0f, 37f, 90f, 180f, 270f};
    static JsonObject rec;

    @BeforeAll
    static void load() throws Exception { rec = catalog().getAsJsonObject("ruin_tower"); }

    /**
     * Um passo do trajeto: o ponto de mundo para onde o herói anda (NaN = parado), o mouse (null = parado; NaN = segue o rumo
     * do andar; número = vai até esse rumo) e se pula. {@code until}: repete o quadro até chegar ao ponto (os pontos de um
     * caminho da grade); senão é um quadro só.
     */
    record Step(float ix, float iz, Float mouseYaw, boolean jump, boolean until) {
        Step(float ix, float iz, Float mouseYaw, boolean jump) { this(ix, iz, mouseYaw, jump, false); }
    }

    /** Totais de um cenário num modo (somados nos rumos da torre). */
    static final class Stats {
        int frames, inside, glued, heroSeen, reversals, snaps, door, doorGlued;
        float longest, run, interior, devSum, devMax, wasdMax, travel, dropMax, doorLongest, doorRun;
        int interiorFrames;

        void add(Stats o) {
            frames += o.frames; inside += o.inside; glued += o.glued; heroSeen += o.heroSeen; reversals += o.reversals; snaps += o.snaps;
            longest = Math.max(longest, o.longest); interior += o.interior; interiorFrames += o.interiorFrames;
            devSum += o.devSum; devMax = Math.max(devMax, o.devMax); wasdMax = Math.max(wasdMax, o.wasdMax); travel += o.travel;
            dropMax = Math.max(dropMax, o.dropMax);
            door += o.door; doorGlued += o.doorGlued; doorLongest = Math.max(doorLongest, o.doorLongest);
        }

        float gluedPct() { return inside == 0 ? 0 : 100f * glued / inside; }
        float heroPct() { return inside == 0 ? 0 : 100f * heroSeen / inside; }
        float interiorPct() { return interiorFrames == 0 ? 0 : 100f * interior / interiorFrames; }
        float devMean() { return inside == 0 ? 0 : devSum / inside; }

        String line() {
            return String.format(Locale.ROOT, "dentro %4d q | colada %5.1f%% | maior trecho %.2f s | herói legível %5.1f%% | interior visto %5.1f%%"
                            + " | desvio médio %4.1f° máx %5.1f° | WASD máx %5.1f° | vai-e-volta %d | saltos %d (maior %.2f u) | percurso da distância %.1f u"
                            + " | na porta (até 2,5 u fora) %d q, colada %.1f%%, maior trecho %.2f s",
                    inside, gluedPct(), longest, heroPct(), interiorPct(), devMean(), devMax, wasdMax, reversals, snaps, dropMax, travel,
                    door, door == 0 ? 0f : 100f * doorGlued / door, doorLongest);
        }
    }

    /** A torre girada {@code yaw}° e os pontos dela: a porta de fora, o centro, o mirante, os pontos do piso de dentro. */
    static final class Tower {
        final Collision c;
        final float[] out, center, lookout;
        final Collision.Cutaway zone;
        final List<Vector3f> floor = new ArrayList<>();

        Tower(float yaw) {
            c = build("ruin_tower", yaw, 1f, false, rec);
            out = outside(rec, yaw);
            center = points(rec, "interacao", "examinar", yaw, 1f).getFirst();
            lookout = points(rec, "interacao", "olhar", yaw, 1f).getFirst();
            assertEquals(1, c.cutaways.size(), "a torre tem uma zona de recorte");
            zone = c.cutaways.getFirst();
            // o desenho acha a construção pela origem do modelo (a mesma matriz do posicionamento), não pelo centro da zona
            assertEquals(OX, zone.ox(), 1e-4f, "origem x do modelo na zona");
            assertEquals(OZ, zone.oz(), 1e-4f, "origem z do modelo na zona");
            Flood f = flood(c, out[0], out[1]);
            for (int iz = 0; iz < f.n(); iz += 2)
                for (int ix = 0; ix < f.n(); ix += 2) {
                    if (!f.seen()[ix + iz * f.n()]) continue;
                    float x = f.x0() + (ix + 0.5f) * CELL, z = f.z0() + (iz + 0.5f) * CELL;
                    if (Math.hypot(x - zone.x(), z - zone.z()) > zone.r() - 0.4f) continue;
                    floor.add(new Vector3f(x, c.standAt(x, z, 1.3f, Player.RADIUS, Player.HEIGHT) + 0.2f, z));
                }
            assertTrue(floor.size() >= 10, "pontos do piso de dentro: " + floor.size());
        }

        boolean inside(Player p) { return Math.hypot(p.x - zone.x(), p.z - zone.z()) <= zone.r(); }
    }

    /** Rumo da câmera atrás de quem anda em (dx, dz). */
    static float behind(float dx, float dz) { return (float) Math.atan2(-dx, -dz); }

    static float wrap(float a) { return (float) Math.atan2(Math.sin(a), Math.cos(a)); }

    /** Trajeto andando pela grade (a mesma busca dos outros testes) de {@code from} a {@code to}, com o mouse seguindo (ou não) o rumo. */
    static List<Step> walk(Collision c, float[] from, float[] to, boolean follow, int settleFrames) {
        List<Step> out = new ArrayList<>();
        for (float[] w : path(c, from[0], from[1], to[0], to[2])) out.add(new Step(w[0], w[1], follow ? Float.NaN : null, false, true));
        for (int i = 0; i < settleFrames; i++) out.add(new Step(Float.NaN, Float.NaN, null, false));
        return out;
    }

    /**
     * Roda um cenário. {@code script} produz os quadros dado o herói e a câmera (alvos de caminho: ix/iz = ponto de mundo a
     * alcançar; NaN = parado; mouseYaw NaN = segue o rumo do andar; null = parado; número = gira até lá).
     */
    static Stats run(Tower t, boolean cut, float[] start, float startYaw, List<Step> steps) {
        Collision c = t.c;
        Player p = new Player();
        p.place(start[0], start[1], startYaw, c);
        FollowCamera cam = new FollowCamera();
        cam.yaw = startYaw;
        CameraCutaway cc = new CameraCutaway();
        Stats s = new Stats();
        float prevDist = -1f, prevDir = 0f, swing = 0f;
        Vector3f eye = new Vector3f(), target = new Vector3f();
        for (Step st : steps)
            for (int g = 0; g < (st.until() ? 90 : 1); g++) {
            if (st.until() && Math.hypot(st.ix() - p.x, st.iz() - p.z) <= 0.12f) break;
            // o andador: vai ao ponto do passo
            float ix = 0, iz = 0;
            if (!Float.isNaN(st.ix())) {
                float ex = st.ix() - p.x, ez = st.iz() - p.z, el = (float) Math.hypot(ex, ez);
                if (el > 0.06f) { ix = ex / el; iz = ez / el; }
            }
            if (st.mouseYaw() != null) {
                float want = Float.isNaN(st.mouseYaw()) ? (ix != 0 || iz != 0 ? behind(ix, iz) : cam.yaw) : st.mouseYaw();
                cam.yaw += wrap(want - cam.yaw) * (Float.isNaN(st.mouseYaw()) ? 0.08f : 1f);
            }
            if (st.jump()) p.requestJump();
            if (cut) {
                cc.update(FRAME, c, p.x, p.z);
                c.cameraSkip = cc.skip();
            }
            for (int k = 0; k < 2; k++) {
                p.update(TICK, ix, iz, false, c);
                cam.update(TICK, p.x, p.visualY, p.z, c);
            }
            c.cameraSkip = -1;
            s.frames++;
            if (prevDist >= 0f) {                                         // a distância: em todo o trajeto (entrar e sair contam)
                float d = cam.dist - prevDist;
                s.travel += Math.abs(d);
                if (d < -0.8f) s.snaps++;
                s.dropMax = Math.max(s.dropMax, -d);
                if (Math.abs(d) > 1e-3f) {
                    float dir = Math.signum(d);
                    if (prevDir != 0f && dir != prevDir) { if (swing > 0.25f) s.reversals++; swing = 0f; }
                    swing += Math.abs(d);
                    prevDir = dir;
                }
            }
            prevDist = cam.dist;
            if (!t.inside(p)) {
                s.run = 0f;
                if (Math.hypot(p.x - t.zone.x(), p.z - t.zone.z()) <= t.zone.r() + 2.5f) {
                    s.door++;
                    if (cam.dist < GLUED) { s.doorGlued++; s.doorRun += FRAME; s.doorLongest = Math.max(s.doorLongest, s.doorRun); } else s.doorRun = 0f;
                } else s.doorRun = 0f;
                continue;
            }
            s.doorRun = 0f;
            s.inside++;
            cam.position(eye);
            target.set(p.x, p.visualY + 0.75f, p.z);
            int skip = cut ? cc.skip() : -1;
            float w = cut ? cc.weight : 0f;
            if (cam.dist < GLUED) { s.glued++; s.run += FRAME; s.longest = Math.max(s.longest, s.run); } else s.run = 0f;
            int seen = 0;
            for (float[] q : new float[][]{{0, 1.1f, 0}, {0.15f, 1.1f, 0}, {-0.15f, 1.1f, 0}, {0.15f, 0.75f, 0}, {-0.15f, 0.75f, 0}})
                if (seen(c, eye, cam.pivot, target, new Vector3f(p.x + q[0], p.visualY + q[1], p.z + q[2]), skip, w)) seen++;
            if (seen >= 3) s.heroSeen++;
            int fl = 0;
            for (Vector3f q : t.floor) if (seen(c, eye, cam.pivot, target, q, skip, w)) fl++;
            s.interior += (float) fl / t.floor.size();
            s.interiorFrames++;
            float dev = (float) Math.toDegrees(Math.abs(wrap(cam.curYaw - cam.yaw)));
            s.devSum += dev;
            s.devMax = Math.max(s.devMax, dev);
            s.wasdMax = Math.max(s.wasdMax, (float) Math.toDegrees(Math.abs(wrap(cam.inputYaw - cam.yaw))));
            }
        assertTrue(s.frames > 0);
        return s;
    }

    /**
     * Um ponto se vê do olho: nada o tapa; ou (modo recorte) só a torre o tapa, e ele está no miolo do cone câmera → alvo
     * (k &lt; 0,75: o que o shader descarta sem pontilhado).
     */
    static boolean seen(Collision c, Vector3f eye, Vector3f look, Vector3f target, Vector3f q, int skip, float w) {
        if (!inView(eye, look, q)) return false;
        if (c.rayClear(eye, q, 0f) > 0.99f) return true;
        if (skip < 0 || w <= 0f) return false;
        c.cameraSkip = skip;
        boolean clearOthers = c.rayClear(eye, q, 0f) > 0.99f;
        c.cameraSkip = -1;
        if (!clearOthers) return false;
        Vector3f ab = new Vector3f(target).sub(eye);
        float len = ab.length(), tt = new Vector3f(q).sub(eye).dot(ab) / (len * len);
        if (tt <= 0f) return false;
        float d = new Vector3f(eye).fma(tt, ab).distance(q);
        return d / (CameraCutaway.RADIUS * w * tt) < CameraCutaway.CORE;
    }

    /** Dentro do quadro (16:9, 45° na vertical) da câmera em {@code eye} olhando para {@code look}. */
    static boolean inView(Vector3f eye, Vector3f look, Vector3f q) {
        Vector3f f = new Vector3f(look).sub(eye).normalize(), up = new Vector3f(0, 1, 0);
        Vector3f r = new Vector3f(f).cross(up).normalize(), u = new Vector3f(r).cross(f);
        Vector3f d = new Vector3f(q).sub(eye);
        float z = d.dot(f);
        if (z < 0.1f) return false;
        double tv = Math.tan(Math.toRadians(FollowCamera.FOV / 2));
        return Math.abs(d.dot(u) / z) <= tv && Math.abs(d.dot(r) / z) <= tv * 16 / 9;
    }

    /** Os cenários, todos nos mesmos pontos da torre. */
    static Map<String, Stats[]> measure() {
        Map<String, Stats[]> all = new LinkedHashMap<>();
        for (float yaw : TOWER_YAWS) {
            Tower t = new Tower(yaw);
            float[] o = t.out, ce = {t.center[0], t.center[2]}, lk = {t.lookout[0], t.lookout[2]};
            float inYaw = behind(ce[0] - o[0], ce[1] - o[1]);
            float dx = lk[0] - ce[0], dz = lk[1] - ce[1], dl = (float) Math.hypot(dx, dz);
            float[] base = {lk[0] - dx / dl * 0.9f, lk[1] - dz / dl * 0.9f};
            Map<String, Object[]> sc = new LinkedHashMap<>();
            // entrada: de fora até o centro, o mouse seguindo
            sc.put("entrada", new Object[]{o, inYaw, steps(walk(t.c, o, t.center, true, 60))});
            // escada: do centro ao pé do mirante
            sc.put("escada", new Object[]{ce, behind(dx, dz), steps(walk(t.c, ce, new float[]{base[0], 0, base[1]}, true, 60))});
            // centro: parado, girando a câmera devagar (uma volta em 6 s)
            sc.put("centro", new Object[]{ce, inYaw, spin(360, 6f, inYaw)});
            // mirante: do pé, pula andando para cima da muralha, olha em volta (uma volta em 4 s)
            List<Step> m = new ArrayList<>();
            for (int i = 0; i < 70; i++) m.add(new Step(lk[0], lk[1], Float.NaN, i == 6));
            m.addAll(spin(360, 4f, behind(dx, dz)));
            sc.put("mirante", new Object[]{base, behind(dx, dz), m});
            // descida: de cima do mirante, desce andando para o centro
            List<Step> dsc = new ArrayList<>();
            for (int i = 0; i < 70; i++) dsc.add(new Step(lk[0], lk[1], null, i == 6));
            for (int i = 0; i < 150; i++) dsc.add(new Step(ce[0], ce[1], Float.NaN, false));
            sc.put("descida", new Object[]{base, behind(dx, dz), dsc});
            // saída: do centro para fora, o mouse seguindo
            sc.put("saida", new Object[]{ce, behind(o[0] - ce[0], o[1] - ce[1]), steps(walk(t.c, ce, new float[]{o[0], 0, o[1]}, true, 30))});
            // giro rápido: uma volta em 0,8 s no centro
            sc.put("giro rapido", new Object[]{ce, inYaw, spin(360, 0.8f, inYaw)});
            // mouse parado: o percurso inteiro com a câmera onde estava na porta
            List<Step> still = new ArrayList<>();
            for (Step st : walk(t.c, o, t.center, false, 20)) still.add(st);
            for (Step st : walk(t.c, ce, new float[]{base[0], 0, base[1]}, false, 20)) still.add(st);
            for (Step st : walk(t.c, base, new float[]{o[0], 0, o[1]}, false, 20)) still.add(st);
            sc.put("mouse parado", new Object[]{o, inYaw, still});
            // perto da parede: o ponto livre do piso mais longe do centro (encostado na parede), de costas e de frente
            Vector3f wall = t.floor.stream().max((a, b) -> Float.compare((float) Math.hypot(a.x - ce[0], a.z - ce[1]),
                    (float) Math.hypot(b.x - ce[0], b.z - ce[1]))).orElseThrow();
            float toWall = behind(wall.x - ce[0], wall.z - ce[1]);
            List<Step> wb = new ArrayList<>(), wf = new ArrayList<>();
            for (int i = 0; i < 120; i++) { wb.add(new Step(Float.NaN, Float.NaN, wrap(toWall + (float) Math.PI), false)); wf.add(new Step(Float.NaN, Float.NaN, toWall, false)); }
            sc.put("parede de costas", new Object[]{new float[]{wall.x, wall.z}, wrap(toWall + (float) Math.PI), wb});
            sc.put("parede de frente", new Object[]{new float[]{wall.x, wall.z}, toWall, wf});
            // diagonal: atravessa o piso entre os dois pontos mais afastados (fora do rumo da porta)
            Vector3f a = wall, b = t.floor.stream().max((u, v) -> Float.compare(u.distance(a), v.distance(a))).orElseThrow();
            List<Step> dg = new ArrayList<>(walk(t.c, new float[]{a.x, a.z}, new float[]{b.x, 0, b.z}, true, 20));
            dg.addAll(walk(t.c, new float[]{b.x, b.z}, new float[]{a.x, 0, a.z}, true, 20));
            sc.put("diagonal", new Object[]{new float[]{a.x, a.z}, behind(b.x - a.x, b.z - a.z), dg});
            // salto no lugar, no centro, com a câmera de lado
            List<Step> jp = new ArrayList<>();
            for (int i = 0; i < 180; i++) jp.add(new Step(Float.NaN, Float.NaN, null, i % 60 == 5));
            sc.put("salto", new Object[]{ce, wrap(inYaw + 1.2f), jp});
            for (var e : sc.entrySet()) {
                Object[] v = e.getValue();
                @SuppressWarnings("unchecked") List<Step> st = (List<Step>) v[2];
                Stats[] pair = all.computeIfAbsent(e.getKey(), k -> new Stats[]{new Stats(), new Stats()});
                Stats b0 = run(t, false, (float[]) v[0], (float) v[1], st), b1 = run(t, true, (float[]) v[0], (float) v[1], st);
                pair[0].add(b0);
                pair[1].add(b1);
            }
        }
        return all;
    }

    static List<Step> steps(List<Step> s) { return s; }

    /** Parado, girando o mouse {@code deg}° em {@code secs} s a partir de {@code from}, e meio segundo parado no fim. */
    static List<Step> spin(float deg, float secs, float from) {
        List<Step> out = new ArrayList<>();
        int n = Math.round(secs / FRAME);
        for (int i = 1; i <= n; i++) out.add(new Step(Float.NaN, Float.NaN, wrap(from + (float) Math.toRadians(deg) * i / n), false));
        for (int i = 0; i < 30; i++) out.add(new Step(Float.NaN, Float.NaN, null, false));
        return out;
    }

    @Test
    void oRecorteTiraACameraDaNucaDentroDaTorreSemPerderOHeroi() throws Exception {
        Map<String, Stats[]> all = measure();
        Stats t0 = new Stats(), t1 = new Stats();
        StringBuilder r = new StringBuilder("# Câmera dentro da torre caída — 12.2 × recorte contextual (TowerCameraTest)\n");
        r.append("torre a ").append(java.util.Arrays.toString(TOWER_YAWS)).append("°; quadros de 1/60 s com dois passos de 1/120 s; só quadros com o herói dentro\n\n");
        for (var e : all.entrySet()) {
            r.append(String.format(Locale.ROOT, "%-17s 12.2    %s%n", e.getKey(), e.getValue()[0].line()));
            r.append(String.format(Locale.ROOT, "%-17s recorte %s%n", "", e.getValue()[1].line()));
            t0.add(e.getValue()[0]);
            t1.add(e.getValue()[1]);
        }
        r.append(String.format(Locale.ROOT, "%n%-17s 12.2    %s%n%-17s recorte %s%n", "TOTAL", t0.line(), "", t1.line()));
        Path out = Path.of("build/medidas/torre-camera.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, r.toString());
        System.out.println(r);
        // a meta da rodada: sem trechos longos de câmera colada, muito menos casos ruins, o herói sempre legível
        assertTrue(t1.longest <= 0.5f, "maior trecho colado com o recorte: " + t1.longest + " s\n" + r);
        assertTrue(t1.gluedPct() <= 10f, "câmera colada com o recorte: " + t1.gluedPct() + "%\n" + r);
        assertTrue(t1.gluedPct() <= t0.gluedPct() / 3f, "o recorte reduz a câmera colada a um terço ou menos\n" + r);
        assertTrue(t1.heroPct() >= 95f, "herói legível com o recorte: " + t1.heroPct() + "%\n" + r);
        assertTrue(t1.interiorPct() >= t0.interiorPct(), "o interior se vê pelo menos tanto quanto antes\n" + r);
        assertTrue(t1.devMax <= t0.devMax + 1f, "o recorte não aumenta o desvio automático do rumo\n" + r);
        assertTrue(t1.wasdMax <= t0.wasdMax + 1f, "nem a mudança da referência do WASD\n" + r);
    }

    /** Fora da zona, o recorte não existe: a câmera é idêntica, passo a passo, à de sem recorte (rodeando a torre por fora). */
    @Test
    void foraDaTorreACameraEIdenticaPassoAPasso() {
        for (float yaw : TOWER_YAWS) {
            Tower t = new Tower(yaw);
            List<Step> around = new ArrayList<>();
            float r = t.zone.r() + 1.6f;
            for (int i = 0; i <= 240; i++) {
                double a = Math.PI * 2 * i / 240;
                around.add(new Step(t.zone.x() + (float) Math.sin(a) * r, t.zone.z() + (float) Math.cos(a) * r, Float.NaN, false, true));
            }
            float[] st = {t.zone.x() + 0f, t.zone.z() + r};
            Player p0 = new Player(), p1 = new Player();
            FollowCamera c0 = new FollowCamera(), c1 = new FollowCamera();
            CameraCutaway cc = new CameraCutaway();
            p0.place(st[0], st[1], 0f, t.c);
            p1.place(st[0], st[1], 0f, t.c);
            for (Step s : around)
                for (int g = 0; g < 90 && Math.hypot(s.ix() - p0.x, s.iz() - p0.z) > 0.12f; g++) {
                float ex = s.ix() - p0.x, ez = s.iz() - p0.z, el = (float) Math.hypot(ex, ez), ix = el > 0.06f ? ex / el : 0, iz = el > 0.06f ? ez / el : 0;
                c0.yaw += wrap(behind(ix, iz) - c0.yaw) * 0.08f;
                c1.yaw = c0.yaw;
                cc.update(FRAME, t.c, p1.x, p1.z);
                int skip = cc.skip();
                assertEquals(-1, skip, "torre a " + yaw + "°: fora da zona a câmera não ignora nada");
                assertEquals(0f, cc.weight, "torre a " + yaw + "°: fora da zona nada é recortado");
                for (int k = 0; k < 2; k++) {
                    p0.update(TICK, ix, iz, false, t.c);
                    c0.update(TICK, p0.x, p0.visualY, p0.z, t.c);
                    t.c.cameraSkip = skip;
                    p1.update(TICK, ix, iz, false, t.c);
                    c1.update(TICK, p1.x, p1.visualY, p1.z, t.c);
                    t.c.cameraSkip = -1;
                }
                assertEquals(c0.dist, c1.dist, 0f);
                assertEquals(c0.curYaw, c1.curYaw, 0f);
                assertEquals(c0.curPitch, c1.curPitch, 0f);
                }
        }
    }

    /** O recorte é dado do perfil: só a torre caída tem zona; nenhum outro composto (nem a v1, sem compostos) ganha recorte. */
    @Test
    void soATorreCaidaTemRecorte() throws Exception {
        JsonObject prof = Collision.profiles(A);
        List<String> with = new ArrayList<>();
        for (var e : prof.getAsJsonObject("props").entrySet())
            if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("recorte")) with.add(e.getKey());
        assertEquals(List.of("ruin_tower"), with, "perfis com recorte");
        for (String name : COMPOSITE) {
            if (name.equals("ruin_tower")) continue;
            Collision c = build(name, 0f, 1f, false, catalog().getAsJsonObject(name));
            assertTrue(c.cutaways.isEmpty(), name + " sem zona de recorte");
        }
    }

    /** A colisão do CORPO não muda com o recorte: o herói não atravessa a parede da torre por ter a câmera dentro da zona. */
    @Test
    void oRecorteNaoMudaAColisaoDoCorpo() {
        for (float yaw : TOWER_YAWS) {
            Tower t = new Tower(yaw);
            for (Collision.Shape s : t.c.shapes) {
                if (!s.body) continue;
                boolean before = t.c.bodyAt(s.cx, s.cz, 0f, Player.RADIUS, Player.HEIGHT);
                t.c.cameraSkip = t.zone.id();
                boolean during = t.c.bodyAt(s.cx, s.cz, 0f, Player.RADIUS, Player.HEIGHT);
                t.c.cameraSkip = -1;
                assertEquals(before, during, "torre a " + yaw + "°: corpo em " + s.name);
            }
        }
    }

    /**
     * As transições do recorte, passo a passo: quem chega de fora (mesmo encostado na parede) não ganha nada; dentro da zona
     * o peso sobe e a câmera só atravessa a torre com o recorte aberto; parado no vão (histerese) nada pisca; ao sair, a saída
     * dura no máximo {@link CameraCutaway#EXIT} u ou {@link CameraCutaway#EXIT_T} s; depois o peso desce até zero.
     */
    @Test
    void asTransicoesDoRecorteSaoSuavesELimitadas() {
        Tower t = new Tower(37f);
        Collision.Cutaway z = t.zone;
        CameraCutaway cc = new CameraCutaway();
        float ax = (float) Math.sin(0.7), az = (float) Math.cos(0.7);
        // de fora, encostado na borda de fora da histerese: nada
        for (int i = 0; i < 120; i++) cc.update(FRAME, t.c, z.x() + ax * (z.r() + 0.2f), z.z() + az * (z.r() + 0.2f));
        assertNull(cc.zone, "de fora não há recorte");
        assertEquals(-1, cc.skip());
        // dentro: o peso sobe; a câmera só ignora a torre com o recorte aberto
        int firstSkip = -1;
        for (int i = 0; i < 60; i++) {
            cc.update(FRAME, t.c, z.x(), z.z());
            if (cc.skip() >= 0 && firstSkip < 0) firstSkip = i;
            if (cc.skip() >= 0) assertTrue(cc.weight >= CameraCutaway.OPEN);
        }
        assertTrue(firstSkip > 0 && firstSkip * FRAME < 0.25f, "abre em menos de 0,25 s: " + firstSkip);
        assertTrue(cc.weight > 0.99f);
        // parado no vão (dentro da histerese) por 3 s: continua igual
        for (int i = 0; i < 180; i++) {
            cc.update(FRAME, t.c, z.x() + ax * (z.r() + 0.25f), z.z() + az * (z.r() + 0.25f));
            assertEquals(z.id(), cc.skip(), "no vão");
        }
        // saída parada logo além da histerese: dura EXIT_T e solta
        float held = 0f;
        for (int i = 0; i < 240; i++) {
            cc.update(FRAME, t.c, z.x() + ax * (z.r() + 1.0f), z.z() + az * (z.r() + 1.0f));
            if (cc.skip() >= 0) held += FRAME;
        }
        assertEquals(CameraCutaway.EXIT_T, held, 2 * FRAME, "a saída dura no máximo EXIT_T parada");
        assertEquals(-1, cc.skip());
        // e o peso desceu até zero (sem zona)
        assertNull(cc.zone, "o recorte se desfaz depois da saída");
        assertEquals(0f, cc.weight);
        // de novo dentro e saindo para longe de uma vez: a saída acaba na distância
        for (int i = 0; i < 60; i++) cc.update(FRAME, t.c, z.x(), z.z());
        cc.update(FRAME, t.c, z.x() + ax * (z.r() + CameraCutaway.EXIT + 0.1f), z.z() + az * (z.r() + CameraCutaway.EXIT + 0.1f));
        assertEquals(-1, cc.skip(), "além da saída, nada");
        // a v1 e a vila não têm zonas: o recorte nunca existe
        Collision none = new Collision((x, zz) -> 0f);
        CameraCutaway c2 = new CameraCutaway();
        for (int i = 0; i < 10; i++) c2.update(FRAME, none, 0f, 0f);
        assertNull(c2.zone);
        assertEquals(-1, c2.skip());
    }

    /**
     * A abóbada soterrada por dentro, com a câmera de sempre (sem recorte: ela não tem zona). Da boca até o pote (os barris,
     * a descoberta), uma volta da câmera diante dele e a saída, nos 5 rumos: câmera colada, herói legível, o pote visto
     * (dentro do quadro e sem nada na frente) e a saída. Decide se a abóbada precisa do recorte da torre (rodada 12.3).
     */
    @Test
    void aAbobadaPorDentroComACameraDeSempre() throws Exception {
        JsonObject vault = catalog().getAsJsonObject("ruin_vault");
        Stats tot = new Stats();
        int potFrames = 0, potSeen = 0;
        StringBuilder r = new StringBuilder("# Abóbada soterrada por dentro — câmera de sempre (TowerCameraTest)\n");
        for (float yaw : TOWER_YAWS) {
            Collision c = build("ruin_vault", yaw, 1f, false, vault);
            assertTrue(c.cutaways.isEmpty(), "a abóbada não tem recorte");
            float[] o = outside(vault, yaw);
            float[] pot = points(vault, "interacao", "procurar", yaw, 1f).getFirst();
            float[] boca = points(vault, "entradas", null, yaw, 1f).getFirst();
            float dx = pot[0] - boca[0], dz = pot[2] - boca[2], dl = (float) Math.hypot(dx, dz);
            float[] front = {pot[0] - dx / dl * 0.9f, 0, pot[2] - dz / dl * 0.9f};
            List<Step> st = new ArrayList<>(walk(c, o, front, true, 30));
            st.addAll(spin(360, 6f, behind(dx, dz)));
            st.addAll(walk(c, new float[]{front[0], front[2]}, new float[]{o[0], 0, o[1]}, true, 30));
            // o mesmo andador, contando "dentro" pela pegada da abóbada (a 1,6 u do centro do pote)
            Player p = new Player();
            p.place(o[0], o[1], behind(dx, dz), c);
            FollowCamera cam = new FollowCamera();
            cam.yaw = behind(dx, dz);
            Stats s = new Stats();
            Vector3f eye = new Vector3f(), potP = new Vector3f(pot[0], pot[1] + 0.25f, pot[2]);
            for (Step q : st)
                for (int g = 0; g < (q.until() ? 90 : 1); g++) {
                    if (q.until() && Math.hypot(q.ix() - p.x, q.iz() - p.z) <= 0.12f) break;
                    float ix = 0, iz = 0;
                    if (!Float.isNaN(q.ix())) {
                        float ex = q.ix() - p.x, ez = q.iz() - p.z, el = (float) Math.hypot(ex, ez);
                        if (el > 0.06f) { ix = ex / el; iz = ez / el; }
                    }
                    if (q.mouseYaw() != null) {
                        float want = Float.isNaN(q.mouseYaw()) ? (ix != 0 || iz != 0 ? behind(ix, iz) : cam.yaw) : q.mouseYaw();
                        cam.yaw += wrap(want - cam.yaw) * (Float.isNaN(q.mouseYaw()) ? 0.08f : 1f);
                    }
                    for (int k = 0; k < 2; k++) { p.update(TICK, ix, iz, false, c); cam.update(TICK, p.x, p.visualY, p.z, c); }
                    s.frames++;
                    if (Math.hypot(p.x - pot[0], p.z - pot[2]) > 1.6f) { s.run = 0f; continue; }
                    s.inside++;
                    cam.position(eye);
                    if (cam.dist < GLUED) { s.glued++; s.run += FRAME; s.longest = Math.max(s.longest, s.run); } else s.run = 0f;
                    Vector3f tg = new Vector3f(p.x, p.visualY + 0.75f, p.z);
                    int seen = 0;
                    for (float[] b : new float[][]{{0, 1.1f, 0}, {0.15f, 1.1f, 0}, {-0.15f, 1.1f, 0}, {0.15f, 0.75f, 0}, {-0.15f, 0.75f, 0}})
                        if (seen(c, eye, cam.pivot, tg, new Vector3f(p.x + b[0], p.visualY + b[1], p.z + b[2]), -1, 0f)) seen++;
                    if (seen >= 3) s.heroSeen++;
                    // o pote: quando a câmera olha para ele (o rumo do mouse a menos de 60° de quem olha o pote)
                    float toPot = behind(pot[0] - p.x, pot[2] - p.z);
                    if (Math.abs(wrap(cam.curYaw - toPot)) < Math.toRadians(60)) {
                        potFrames++;
                        if (seen(c, eye, cam.pivot, tg, potP, -1, 0f)) potSeen++;
                    }
                }
            tot.add(s);
        }
        r.append(String.format(Locale.ROOT, "dentro %d q | colada %.1f%% | maior trecho %.2f s | herói legível %.1f%%%n"
                        + "pote visto quando a câmera olha para ele: %d de %d quadros (%.1f%%)%n",
                tot.inside, tot.gluedPct(), tot.longest, tot.heroPct(), potSeen, potFrames, 100f * potSeen / Math.max(1, potFrames)));
        Path out = Path.of("build/medidas/abobada-camera.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, r.toString());
        System.out.println(r);
        assertTrue(tot.heroPct() >= 95f, "herói legível na abóbada\n" + r);
        assertTrue(100f * potSeen / Math.max(1, potFrames) >= 90f, "o pote se vê quando se olha para ele\n" + r);
    }
}
