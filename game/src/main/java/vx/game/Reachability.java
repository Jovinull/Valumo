package vx.game;

import vx.game.world.RegionLayout;
import vx.viewer.Terrain;
import vx.viewer.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 11 — confere se uma região gerada é jogável com a colisão REAL ({@link Collision}, a mesma do jogo) e o
 * relevo T1 ({@link Terrain}), não com linhas retas entre pontos. Grade de 0,25 u na área jogável:
 * uma célula é caminhável se está no limite, fora da água e se o herói (raio {@link Player#RADIUS}) cabe nela sem
 * encostar em sólido; vizinhas se ligam se o degrau é ≤ {@link Player#STEP}. A busca parte do início do jogo, e cada
 * alvo (falar com cada morador, usar o marco, chegar a cada fungo, o ponto de recomeço) precisa de uma célula
 * alcançável perto dele. Como os passos valem nos dois sentidos, chegar implica voltar.
 */
public final class Reachability {
    public static final float CELL = 0.25f;

    /** {@code problems} vazio = jogável. {@code targets}: alvo → alcançado. {@code arenaFree}: fração livre da arena. */
    public record Report(List<String> problems, int cells, int walkable, int reachable, float arenaFree, Map<String, Boolean> targets,
                         long nanos) { }

    private Reachability() { }

    public static Report check(RegionLayout L, SceneDef scene, Collision c, Terrain t) {
        long t0 = System.nanoTime();
        List<String> problems = new ArrayList<>();
        float x0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (float[] l : allLimits(c)) {
            x0 = Math.min(x0, l[0] - l[2]); x1 = Math.max(x1, l[0] + l[2]);
            z0 = Math.min(z0, l[1] - l[2]); z1 = Math.max(z1, l[1] + l[2]);
        }
        int nx = (int) Math.ceil((x1 - x0) / CELL), nz = (int) Math.ceil((z1 - z0) / CELL);
        boolean[] walk = new boolean[nx * nz];
        float[] h = new float[nx * nz];
        int walkable = 0;
        for (int iz = 0; iz < nz; iz++)
            for (int ix = 0; ix < nx; ix++) {
                float x = x0 + (ix + 0.5f) * CELL, z = z0 + (iz + 0.5f) * CELL;
                int k = ix + iz * nx;
                h[k] = c.floor(x, z, 99f, Player.RADIUS);
                walk[k] = c.walkable(x, z) && free(c, x, z, h[k]);
                if (walk[k]) walkable++;
            }
        // busca a partir do início
        boolean[] seen = new boolean[nx * nz];
        int start = nearestWalkable(walk, nx, nz, x0, z0, scene.startX, scene.startZ, 0.6f);
        int reached = 0;
        if (start < 0) problems.add("o início do jogo não é caminhável");
        else {
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(start);
            seen[start] = true;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!q.isEmpty()) {
                int k = q.poll();
                reached++;
                int ix = k % nx, iz = k / nx;
                for (int[] d : dirs) {
                    int jx = ix + d[0], jz = iz + d[1];
                    if (jx < 0 || jz < 0 || jx >= nx || jz >= nz) continue;
                    int j = jx + jz * nx;
                    if (seen[j] || !walk[j] || Math.abs(h[j] - h[k]) > Player.STEP) continue;
                    seen[j] = true;
                    q.add(j);
                }
            }
        }
        // alvos
        Map<String, Boolean> targets = new LinkedHashMap<>();
        for (SceneDef.Interactive n : scene.interactives) {
            float a = (float) Math.toRadians(n.yawDeg());
            float tx = n.x() + (float) Math.sin(a) * 1.2f, tz = n.z() + (float) Math.cos(a) * 1.2f;
            boolean ok = reachedNear(seen, nx, nz, x0, z0, tx, tz, 0.45f);
            targets.put("falar:" + n.id(), ok);
            if (!ok) problems.add("ninguém chega para falar com " + n.name());
            for (Collision.Shape s : c.shapes) {
                if (!s.body || s.name.equals(n.rig())) continue;
                if (s.push(n.x(), n.z(), 0.55f) != null) { problems.add(n.name() + " colado em " + s.name); break; }
            }
        }
        for (SceneDef.WorldObject o : scene.objects) {
            float a = (float) Math.toRadians(o.yawDeg());
            float tx = o.x() + (float) Math.sin(a) * 1.15f, tz = o.z() + (float) Math.cos(a) * 1.15f;
            boolean ok = reachedNear(seen, nx, nz, x0, z0, tx, tz, 0.5f);
            targets.put("usar:" + o.id(), ok);
            if (!ok) problems.add("o objeto " + o.id() + " não é alcançável de frente");
        }
        for (RegionLayout.Spawn s : L.spawns) {
            boolean ok = reachedNear(seen, nx, nz, x0, z0, s.x(), s.z(), 0.5f);
            targets.put("inimigo:" + s.key(), ok);
            if (!ok) problems.add("o inimigo " + s.key() + " nasce fora do alcance");
        }
        boolean rs = reachedNear(seen, nx, nz, x0, z0, L.restartX, L.restartZ, 0.5f);
        targets.put("recomeco", rs);
        if (!rs) problems.add("o ponto de recomeço não é alcançável");
        // rodada 12 (v2): entradas das construções, pontos de interação dos interiores, a descoberta (a v1 não tem nenhum)
        for (RegionLayout.Checkpoint cp : L.checkpoints) {
            boolean ok = reachedNear(seen, nx, nz, x0, z0, cp.x(), cp.z(), cp.within());
            targets.put(cp.name(), ok);
            if (!ok) problems.add(cp.name() + " fora do alcance em (" + r1(cp.x()) + ", " + r1(cp.z()) + ")");
        }
        // arena: espaço livre para lutar
        int in = 0, ok = 0;
        for (int iz = 0; iz < nz; iz++)
            for (int ix = 0; ix < nx; ix++) {
                float x = x0 + (ix + 0.5f) * CELL, z = z0 + (iz + 0.5f) * CELL;
                if (Math.hypot(x - L.px, z - L.pz) > L.arenaR - 0.6f) continue;
                in++;
                if (seen[ix + iz * nx]) ok++;
            }
        float arenaFree = in == 0 ? 0 : (float) ok / in;
        if (arenaFree < 0.75f) problems.add(String.format(java.util.Locale.ROOT, "arena com só %.0f%% livre", arenaFree * 100));
        // props: nada sólido afundado na água nem em desnível grande dentro da área jogável
        for (World.Placement p : scene.handPlaced) {
            float[] b = p.bounds();
            org.joml.Vector3f cc = p.model().transformPosition(new org.joml.Vector3f((b[0] + b[3]) / 2, b[1], (b[2] + b[5]) / 2));
            if (p.name().equals("reeds") || p.name().equals("lily") || p.name().startsWith("flower") || p.name().startsWith("grass")) continue;
            if (t.groundAt(cc.x, cc.z) < World.WATER_LEVEL + 0.06f) problems.add(p.name() + " dentro da água em (" + r1(cc.x) + ", " + r1(cc.z) + ")");
            if (!c.insideLimit(cc.x, cc.z) && !p.name().equals("house")) continue;
            float hw = (b[3] - b[0]) / 2 * 0.8f, hd = (b[5] - b[2]) / 2 * 0.8f, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (float[] q : new float[][]{{-hw, -hd}, {hw, -hd}, {-hw, hd}, {hw, hd}, {0, 0}}) {
                org.joml.Vector3f w = p.model().transformPosition(new org.joml.Vector3f((b[0] + b[3]) / 2 + q[0], b[1], (b[2] + b[5]) / 2 + q[1]));
                float g = t.groundAt(w.x, w.z);
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
            }
            float tol = p.name().equals("house") ? 0.3f : 0.55f;
            if (hi - lo > tol) problems.add(p.name() + " em desnível de " + r1(hi - lo) + " u em (" + r1(cc.x) + ", " + r1(cc.z) + ")");
        }
        return new Report(List.copyOf(problems), nx * nz, walkable, reached, arenaFree, targets, System.nanoTime() - t0);
    }

    static String r1(float v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    static List<float[]> allLimits(Collision c) {
        List<float[]> l = new ArrayList<>(c.extraLimits);
        l.add(new float[]{c.limitX, c.limitZ, c.limitR});
        return l;
    }

    /** O herói cabe ali: nenhum sólido (acima de um degrau) encosta no círculo dele. */
    static boolean free(Collision c, float x, float z, float y) {
        return !c.bodyAt(x, z, y, Player.RADIUS, Player.HEIGHT);           // rodada 12.1: pelo índice (o mesmo resultado)
    }

    static int nearestWalkable(boolean[] walk, int nx, int nz, float x0, float z0, float x, float z, float within) {
        int best = -1;
        float bd = within;
        for (int iz = Math.max(0, (int) ((z - z0 - within) / CELL)); iz < Math.min(nz, (int) ((z - z0 + within) / CELL) + 1); iz++)
            for (int ix = Math.max(0, (int) ((x - x0 - within) / CELL)); ix < Math.min(nx, (int) ((x - x0 + within) / CELL) + 1); ix++) {
                if (!walk[ix + iz * nx]) continue;
                float d = (float) Math.hypot(x0 + (ix + 0.5f) * CELL - x, z0 + (iz + 0.5f) * CELL - z);
                if (d <= bd) { bd = d; best = ix + iz * nx; }
            }
        return best;
    }

    static boolean reachedNear(boolean[] seen, int nx, int nz, float x0, float z0, float x, float z, float within) {
        return nearestWalkable(seen, nx, nz, x0, z0, x, z, within) >= 0;
    }
}
