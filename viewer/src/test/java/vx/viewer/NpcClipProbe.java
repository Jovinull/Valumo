package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vx.engine.VoxModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Laboratório de conteúdo — interpenetração de um NPC nos clipes REAIS (o mesmo Animator e o mesmo perfil do jogo),
 * voxel a voxel, sem OpenGL. A cada 1/60 s de IDLE, WALK, RUN, EMOTE, de cada gesto do perfil e das transições
 * (andar → ofício → andar, andar → aceno → andar, ofício → aceno; só nos perfis com gesto de ofício), os voxels de cada
 * membro vão para o espaço de repouso de cada alvo e são comparados com as células reais do alvo:
 * <ul>
 *   <li><b>braço × tronco</b>: braço e antebraço (com luva ou mão) contra o tronco com tudo que está preso nele
 *       (avental, bolsa, manto, martelo). A junta do ombro é excluída: voxel do braço a até JOINT voxels do pivô do
 *       ombro sempre encosta no tronco, em qualquer NPC;</li>
 *   <li><b>braço × cabeça</b>: braço e antebraço contra a cabeça e o chapéu/coque (o aceno passa perto);</li>
 *   <li><b>perna × tronco</b>: coxa e canela contra a barra do casaco e o que pende do quadril;</li>
 *   <li><b>pé × chão</b>: o quanto o voxel mais baixo das canelas afunda abaixo de y = 0 (chão plano).</li>
 * </ul>
 * Medida: PROFUNDIDADE em u (distância do centro do voxel invasor à borda do alvo, campo de 6 vizinhos), que não
 * depende da escala do rig, e o número de voxels invasores no pior instante. Um NPC novo passa se nenhum grupo
 * passar da profundidade máxima medida nos NPCs aprovados (relatório em markdown; código de saída 1 se reprovar).
 *
 * Usado por {@link MundoNpcTest} (regra) e pelo relatório (tools/conteudo/npc_clip.sh NOVOS... -- APROVADOS...).
 */
final class NpcClipProbe {
    static final float DT = 1f / 60f;
    static final int JOINT = 3;                                   // raio (voxels de repouso) da junta do ombro
    static final Map<String, String[][]> GROUPS = new LinkedHashMap<>();

    static {
        String[] arms = {"arm_l", "farm_l", "arm_r", "farm_r"};
        GROUPS.put("braço × tronco", new String[][]{arms, {"torso"}});
        GROUPS.put("braço × cabeça", new String[][]{arms, {"head", "hat", "lock", "ear_l", "ear_r"}});
        GROUPS.put("perna × tronco", new String[][]{{"leg_l", "shin_l", "leg_r", "shin_r"}, {"torso", "skirt_l", "skirt_r"}});
    }

    record Cells(int part, Set<Long> set, List<int[]> list, Map<Long, Integer> depth) { }

    static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    static Cells cells(Path dir, JsonObject p, int idx) throws Exception {
        VoxModel v = VoxModel.load(dir.resolve(p.get("file").getAsString()));
        JsonArray o = p.getAsJsonArray("origin");
        int ox = Math.round(o.get(0).getAsFloat()), oy = Math.round(o.get(1).getAsFloat()), oz = Math.round(o.get(2).getAsFloat());
        Set<Long> set = new HashSet<>();
        List<int[]> list = new ArrayList<>();
        for (int x = 0; x < v.sizeX(); x++)
            for (int y = 0; y < v.sizeY(); y++)
                for (int z = 0; z < v.sizeZ(); z++)
                    if (v.get(x, y, z) != 0) {
                        set.add(key(x + ox, y + oy, z + oz));
                        list.add(new int[]{x + ox, y + oy, z + oz});
                    }
        // profundidade: camadas a partir da borda (célula com vizinho vazio = 1)
        Map<Long, Integer> depth = new HashMap<>();
        List<int[]> frontier = new ArrayList<>();
        int[][] n6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] c : list)
            for (int[] d : n6)
                if (!set.contains(key(c[0] + d[0], c[1] + d[1], c[2] + d[2]))) {
                    depth.put(key(c[0], c[1], c[2]), 1);
                    frontier.add(c);
                    break;
                }
        for (int layer = 2; !frontier.isEmpty() && layer < 64; layer++) {
            List<int[]> next = new ArrayList<>();
            for (int[] c : frontier)
                for (int[] d : n6) {
                    long k = key(c[0] + d[0], c[1] + d[1], c[2] + d[2]);
                    if (set.contains(k) && !depth.containsKey(k)) {
                        depth.put(k, layer);
                        next.add(new int[]{c[0] + d[0], c[1] + d[1], c[2] + d[2]});
                    }
                }
            frontier = next;
        }
        return new Cells(idx, set, list, depth);
    }

    /** Pior resultado por grupo: profundidade (u), voxels invasores no pior instante, onde. */
    record Worst(float depthU, int count, String where) { }

    static Map<String, Worst> measure(Path dir) throws Exception {
        JsonObject doc = JsonParser.parseString(Files.readString(dir.resolve("rig.json"))).getAsJsonObject();
        CharacterModel m = CharacterModel.loadSkeleton(dir);
        float vs = m.voxelSize;
        Map<String, Cells> parts = new HashMap<>();
        for (var e : doc.getAsJsonArray("parts")) {
            JsonObject p = e.getAsJsonObject();
            String n = p.get("name").getAsString();
            if (p.has("file") && m.index.containsKey(n)) parts.put(n, cells(dir, p, m.index.get(n)));
        }
        Animator an = new Animator(m, 1f);
        Animator.Pose pose = an.newPose();
        Matrix4f[] mats = new Matrix4f[m.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        Matrix4f id = new Matrix4f();
        Matrix4f inv = new Matrix4f();
        Animator.Motion mo = new Animator.Motion();
        List<String> what = new ArrayList<>(List.of("clip:IDLE", "clip:WALK", "clip:RUN", "clip:EMOTE"));
        if (an.prof != null) for (String mv : an.prof.moves.keySet()) if (!mv.equals("emote")) what.add(mv);
        if (an.prof != null && an.prof.moves.containsKey("oficio"))       // transições: andar ↔ gesto, gesto → aceno
            what.addAll(List.of("seq:walk:0.8,oficio,walk:0.8", "seq:walk:0.8,emote,walk:0.8", "seq:oficio,emote"));
        Map<String, Worst> out = new LinkedHashMap<>();
        for (String g : GROUPS.keySet()) out.put(g, new Worst(0, 0, "—"));
        out.put("pé × chão", new Worst(0, 0, "—"));
        Vector3f p = new Vector3f();
        int samples = 0;
        for (String w : what) {
            Animator.Clip clip = w.startsWith("clip:") ? Animator.Clip.valueOf(w.substring(5)) : null;
            Sequencer seq = w.startsWith("seq:") ? Sequencer.of(w.substring(4), an) : null;
            mo = new Animator.Motion();                                  // estado limpo a cada clipe/gesto/sequência
            float dur = seq != null ? seq.total : clip != null ? an.duration(clip) : an.moveDuration(w);
            if (dur <= 0) throw new IllegalStateException(dir + ": clipe/gesto sem duração: " + w);
            for (float t = 0; t <= dur; t += DT) {
                if (seq != null) mo = seq.eval(t, new Animator.Motion());
                else if (clip != null) mo.set(clip, t);
                else {
                    mo.set(Animator.Clip.IDLE, t);
                    mo.move = w;
                    mo.moveT = t;
                    mo.weight = 1f;
                }
                an.evaluate(mo, pose, id, null);
                an.compose(pose, id, mats);
                samples++;
                String at = String.format(Locale.ROOT, "%s t=%.2f", w.replace("clip:", ""), t);
                for (var ge : GROUPS.entrySet()) {
                    int count = 0;
                    float deep = 0;
                    String cell = "";
                    for (String tn : ge.getValue()[1]) {
                        Cells tc = parts.get(tn);
                        if (tc == null) continue;
                        mats[tc.part].invert(inv);
                        for (String ln : ge.getValue()[0]) {
                            Cells lc = parts.get(ln);
                            if (lc == null) continue;
                            boolean upperArm = ln.startsWith("arm_") && tn.equals("torso");
                            CharacterModel.Part lp = m.parts.get(lc.part);
                            for (int[] c : lc.list) {
                                if (upperArm) {
                                    float dx = c[0] + 0.5f - lp.px / vs, dy = c[1] + 0.5f - lp.py / vs, dz = c[2] + 0.5f - lp.pz / vs;
                                    if (dx * dx + dy * dy + dz * dz <= JOINT * JOINT * 4) continue;    // junta: 2·JOINT
                                }
                                p.set((c[0] + 0.5f) * vs, (c[1] + 0.5f) * vs, (c[2] + 0.5f) * vs);
                                mats[lc.part].transformPosition(p);
                                inv.transformPosition(p);
                                long k = key((int) Math.floor(p.x / vs), (int) Math.floor(p.y / vs), (int) Math.floor(p.z / vs));
                                Integer d = tc.depth.get(k);
                                if (d == null) continue;
                                count++;
                                if (d * vs > deep) cell = String.format(Locale.ROOT, " (%s em %s %d,%d,%d)", ln, tn,
                                        (int) Math.floor(p.x / vs), (int) Math.floor(p.y / vs), (int) Math.floor(p.z / vs));
                                deep = Math.max(deep, d * vs);
                            }
                        }
                    }
                    Worst cur = out.get(ge.getKey());
                    if (deep > cur.depthU || (deep == cur.depthU && count > cur.count))
                        out.put(ge.getKey(), new Worst(deep, count, at + cell));
                }
                float low = 0;
                int sunk = 0;
                for (String sn : List.of("shin_l", "shin_r")) {
                    Cells sc = parts.get(sn);
                    if (sc == null) continue;
                    for (int[] c : sc.list) {
                        p.set((c[0] + 0.5f) * vs, (c[1]) * vs, (c[2] + 0.5f) * vs);   // base do voxel
                        mats[sc.part].transformPosition(p);
                        if (p.y < -1e-4f) {
                            sunk++;
                            low = Math.min(low, p.y);
                        }
                    }
                }
                Worst cur = out.get("pé × chão");
                if (-low > cur.depthU) out.put("pé × chão", new Worst(-low, sunk, at));
            }
        }
        if (samples == 0) throw new IllegalStateException(dir + ": nenhuma amostra");
        return out;
    }

    public static void main(String[] a) throws Exception {
        List<Path> novos = new ArrayList<>(), aprovados = new ArrayList<>();
        boolean ref = false;
        for (String s : a) {
            if (s.equals("--")) { ref = true; continue; }
            (ref ? aprovados : novos).add(Path.of(s));
        }
        if (novos.isEmpty() || aprovados.isEmpty()) throw new IllegalArgumentException("uso: NOVOS... -- APROVADOS...");
        Map<String, Float> limit = new LinkedHashMap<>();
        Map<String, String> limitWho = new HashMap<>();
        StringBuilder sb = new StringBuilder("| NPC | grupo | profundidade (u) | voxels no pior instante | onde |\n|---|---|---|---|---|\n");
        for (Path d : aprovados) {
            for (var e : measure(d).entrySet()) {
                sb.append(String.format(Locale.ROOT, "| %s (aprovado) | %s | %.3f | %d | %s |%n", d.getFileName(), e.getKey(),
                        e.getValue().depthU(), e.getValue().count(), e.getValue().where()));
                if (e.getValue().depthU() >= limit.getOrDefault(e.getKey(), 0f)) {
                    limit.put(e.getKey(), e.getValue().depthU());
                    limitWho.put(e.getKey(), d.getFileName().toString());
                }
            }
        }
        boolean ok = true;
        StringBuilder verdict = new StringBuilder();
        for (Path d : novos) {
            for (var e : measure(d).entrySet()) {
                float lim = limit.getOrDefault(e.getKey(), 0f);
                boolean pass = e.getValue().depthU() <= lim + 1e-4f;
                ok &= pass;
                sb.append(String.format(Locale.ROOT, "| **%s** | %s | %.3f | %d | %s |%n", d.getFileName(), e.getKey(),
                        e.getValue().depthU(), e.getValue().count(), e.getValue().where()));
                verdict.append(String.format(Locale.ROOT, "- %s / %s: %.3f u (limite %.3f u, de %s) → %s%n", d.getFileName(),
                        e.getKey(), e.getValue().depthU(), lim, limitWho.getOrDefault(e.getKey(), "—"), pass ? "ok" : "REPROVA"));
            }
        }
        System.out.println(sb);
        System.out.println(verdict);
        System.out.println(ok ? "RESULTADO: nenhum NPC novo atravessa mais que os aprovados" : "RESULTADO: REPROVADO");
        System.exit(ok ? 0 : 1);
    }
}
