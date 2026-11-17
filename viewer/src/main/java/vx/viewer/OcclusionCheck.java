package vx.viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rodada 05.1 — legibilidade em projeção de câmera ({@code viewer --occlusion [rig golpe]}). O validador espacial
 * prova que nada se ATRAVESSA; esta verificação mede o que se ESCONDE, projetando os mesmos volumes aproximados
 * ({@code meta.volumes}) no plano de câmeras fixas em torno do personagem (sem render, determinística):
 * <ul>
 *   <li><b>rosto coberto</b>: fração do disco do rosto (placa frontal da cabeça, só quando o rosto está voltado
 *       para a câmera) tapada por arma/item mais perto da câmera;</li>
 *   <li><b>arma visível</b>: fração dos pontos da arma (lâmina, cabo, cajado, arco) que nenhum volume do corpo
 *       (cabeça, tronco, costas, braços) esconde.</li>
 * </ul>
 * É uma triagem: volumes são cápsulas aproximadas e o contorno real do voxel não entra. Não substitui o QA
 * humano em vídeo (skill visual-qa). Critério de alerta da guarda: rosto coberto ≥ 25% por mais de 0,10 s.
 */
final class OcclusionCheck {
    static final float DT = 1f / 240f;
    /** Câmeras: guinada em graus (0 = de frente; + = pela esquerda do personagem), elevação 12°. */
    static final float[] YAWS = {0, 40, -40, 90, -90, 140, -140, 180};
    static final float PITCH = 12f;
    static final float FACE_ALERT = 0.25f, FACE_ALERT_S = 0.10f;

    record ViewStat(float yaw, float maxFace, float faceAt, float faceTime, float minVisible, float visibleAt, float legibleShare) { }

    record Result(String subject, List<ViewStat> views) {
        boolean faceOk() {
            for (ViewStat v : views) if (v.faceTime > FACE_ALERT_S) return false;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(subject + (faceOk() ? " OK" : " ROSTO COBERTO"));
            for (ViewStat v : views)
                sb.append(String.format(Locale.ROOT, "%n  câmera %+5.0f°  rosto coberto máx %4.0f%% em %.2f s (%.2f s ≥ %.0f%%)   arma visível mín %4.0f%% em %.2f s   arma ≥ 50%% visível em %3.0f%% do golpe",
                        v.yaw, v.maxFace * 100, v.faceAt, v.faceTime, FACE_ALERT * 100, v.minVisible * 100, v.visibleAt, v.legibleShare * 100));
            return sb.toString();
        }
    }

    private OcclusionCheck() { }

    static boolean weapon(String tag) {
        return switch (tag) {
            case "blade", "handle", "staff", "bow" -> true;
            default -> false;
        };
    }

    static boolean body(String tag) {
        return switch (tag) {
            case "head", "hat", "torso", "back", "arm_l", "arm_r" -> true;
            default -> false;
        };
    }

    static Result run(CharacterModel m, String what) {
        Animator an = new Animator(m, 1f);
        Animator.Pose pose = an.newPose();
        Matrix4f[] mats = new Matrix4f[m.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        Matrix4f id = new Matrix4f();
        List<WeaponCheck.Vol> bind = WeaponCheck.volumes(m);
        Sequencer seq = what.startsWith("seq:") ? new Sequencer(what.substring(4), an) : null;
        Animator.Clip clip = what.startsWith("clip:") ? Animator.Clip.valueOf(what.substring(5)) : null;
        float dur = seq != null ? seq.total - 1e-4f : clip != null ? an.duration(clip) : an.moveDuration(what);
        Animator.Motion mo = new Animator.Motion();
        int nv = YAWS.length;
        float[] legible = new float[nv], maxFace = new float[nv], faceAt = new float[nv], faceTime = new float[nv], minVis = new float[nv], visAt = new float[nv];
        java.util.Arrays.fill(minVis, 1f);
        WeaponCheck.Vol headBind = null;
        for (WeaponCheck.Vol v : bind) if (v.tag().equals("head")) headBind = v;
        int n = (int) Math.ceil(dur / DT);
        for (int s = 0; s <= n; s++) {
            float t = Math.min(dur, s * DT);
            if (seq != null) seq.eval(t, mo);
            else if (clip != null) mo.set(clip, t);
            else { mo.set(Animator.Clip.IDLE, t); mo.move = what; mo.moveT = t; mo.weight = 1f; }
            an.evaluate(mo, pose, id, null);
            an.compose(pose, id, mats);
            List<WeaponCheck.Vol> pv = WeaponCheck.posed(bind, mats);
            List<WeaponCheck.Vol> weps = new ArrayList<>(), bodies = new ArrayList<>();
            for (WeaponCheck.Vol v : pv) {
                if (weapon(v.tag())) weps.add(v);
                else if (body(v.tag())) bodies.add(v);
            }
            if (weps.isEmpty()) continue;
            // rosto: centro um pouco abaixo do centro da cabeça, na frente; normal = +Z da cabeça
            Vector3f faceC = null, faceN = null;
            float faceR = 0;
            if (headBind != null) {
                Matrix4f hm = mats[headBind.part()];
                Vector3f c = new Vector3f(headBind.b() != null ? new Vector3f(headBind.a()).add(headBind.b()).mul(0.5f) : headBind.a());
                faceC = hm.transformPosition(new Vector3f(c).add(0, -0.15f * headBind.r(), 0.8f * headBind.r()));
                faceN = hm.transformDirection(new Vector3f(0, 0, 1)).normalize();
                faceR = 0.55f * headBind.r();
            }
            for (int k = 0; k < nv; k++) {
                Vector3f d = camDir(YAWS[k]);
                Vector3f u = new Vector3f(0, 1, 0).cross(d).normalize(), w = new Vector3f(d).cross(u).normalize();
                if (faceC != null && faceN.dot(d) > 0.3f) {
                    float cov = faceCoverage(faceC, faceR, d, u, w, weps);
                    if (cov > maxFace[k]) { maxFace[k] = cov; faceAt[k] = t; }
                    if (cov >= FACE_ALERT) faceTime[k] += DT;
                }
                float vis = weaponVisibility(weps, bodies, d, u, w);
                if (vis < minVis[k]) { minVis[k] = vis; visAt[k] = t; }
                if (vis >= 0.5f) legible[k] += 1;
            }
        }
        List<ViewStat> out = new ArrayList<>();
        for (int k = 0; k < nv; k++) out.add(new ViewStat(YAWS[k], maxFace[k], faceAt[k], faceTime[k], minVis[k], visAt[k], legible[k] / (n + 1)));
        return new Result(m.name + " / " + what, out);
    }

    static Vector3f camDir(float yawDeg) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(PITCH);
        return new Vector3f((float) (Math.sin(y) * Math.cos(p)), (float) Math.sin(p), (float) (Math.cos(y) * Math.cos(p))).normalize();
    }

    /** Fração de uma grade de 61 pontos do disco do rosto tapada por algum item mais perto da câmera. */
    static float faceCoverage(Vector3f c, float r, Vector3f d, Vector3f u, Vector3f w, List<WeaponCheck.Vol> weps) {
        int tot = 0, hit = 0;
        for (int i = -4; i <= 4; i++)
            for (int j = -4; j <= 4; j++) {
                if (i * i + j * j > 16) continue;
                tot++;
                Vector3f p = new Vector3f(c).fma(r * i / 4f, u).fma(r * j / 4f, w);
                for (WeaponCheck.Vol v : weps)
                    if (covers(v, p, d, u, w)) { hit++; break; }
            }
        return tot == 0 ? 0 : hit / (float) tot;
    }

    /** O volume v tapa o ponto p visto na direção d (câmera em +d)? Projeção ortográfica. */
    static boolean covers(WeaponCheck.Vol v, Vector3f p, Vector3f d, Vector3f u, Vector3f w) {
        Vector3f a = v.a(), b = v.b() != null ? v.b() : v.a();
        float px = p.dot(u), py = p.dot(w), pd = p.dot(d);
        float ax = a.dot(u), ay = a.dot(w), bx = b.dot(u), by = b.dot(w);
        float ex = bx - ax, ey = by - ay, l2 = ex * ex + ey * ey;
        float s = l2 < 1e-12f ? 0f : Math.clamp(((px - ax) * ex + (py - ay) * ey) / l2, 0f, 1f);
        float qx = ax + s * ex - px, qy = ay + s * ey - py;
        if (qx * qx + qy * qy > v.r() * v.r()) return false;
        float depth = a.dot(d) + s * (b.dot(d) - a.dot(d));
        return depth > pd;                                  // mais perto da câmera que o ponto
    }

    /** Fração de 13 pontos ao longo de cada volume da arma que nenhum volume do corpo esconde. */
    static float weaponVisibility(List<WeaponCheck.Vol> weps, List<WeaponCheck.Vol> bodies, Vector3f d, Vector3f u, Vector3f w) {
        int tot = 0, vis = 0;
        for (WeaponCheck.Vol v : weps) {
            Vector3f a = v.a(), b = v.b() != null ? v.b() : v.a();
            for (int i = 0; i <= 12; i++) {
                Vector3f p = new Vector3f(a).lerp(b, i / 12f);
                tot++;
                boolean hidden = false;
                for (WeaponCheck.Vol o : bodies)
                    if (covers(o, p, d, u, w)) { hidden = true; break; }
                if (!hidden) vis++;
            }
        }
        return tot == 0 ? 1f : vis / (float) tot;
    }

    /** {@code viewer --occlusion} (todos os golpes canônicos) ou {@code viewer --occlusion rig golpe}. */
    static boolean run(String[] args) {
        boolean all = true;
        if (args.length > 2) {
            Result r = run(CharacterModel.loadSkeleton(Path.of("assets").resolve(args[1])), args[2]);
            System.out.println(r);
            return r.faceOk();
        }
        for (String rig : Check.RIGS) {
            CharacterModel m = CharacterModel.loadSkeleton(Path.of("assets").resolve(rig));
            Animator an = new Animator(m, 1f);
            if (an.prof == null) continue;
            List<String> what = new ArrayList<>(an.prof.moves.keySet());
            what.add("clip:IDLE");
            what.add("clip:WALK");
            what.add("clip:RUN");
            for (String wv : what) {
                Result r = run(m, wv);
                System.out.println(r);
                all &= r.faceOk();
            }
        }
        System.out.println(all ? "occlusion: rosto livre em todos os golpes" : "occlusion: HÁ ROSTO COBERTO");
        return all;
    }
}
