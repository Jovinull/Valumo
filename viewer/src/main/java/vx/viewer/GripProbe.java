package vx.viewer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Rodada 05.1 — sonda de empunhadura: {@code viewer --grip <rig> <golpe|clip:HIT|seq:...> [passo_s]}.
 *
 * <p>O validador ({@link WeaponCheck}) prova que nada se atravessa; esta sonda mede o que ele não vê: a
 * ORIENTAÇÃO do item em relação ao antebraço que o segura. Por amostra imprime, para cada item segurado:
 * <ul>
 *   <li><b>torção</b> do punho: rotação do item em torno do eixo do antebraço (pronação/supinação vistas
 *       no item), em graus a partir do repouso de amarração;</li>
 *   <li><b>flexão</b>: quanto o item se inclina para fora do eixo do antebraço;</li>
 *   <li><b>giro</b>: velocidade angular do item no mundo (°/s) — "teleporte de orientação" aparece aqui;</li>
 *   <li>eixos do item no mundo: Y (ao longo da arma/arco) e X (fio / ventre do arco).</li>
 * </ul>
 * No fim resume os máximos. Critério (ver skill weapon-animation): torção ≤ 90°, flexão ≤ 75°, giro sem
 * picos isolados (pico ≤ 3 × a média dos 10 maiores vizinhos e ≤ 1500 °/s).</p>
 */
final class GripProbe {
    static final float DT = 1f / 480f;

    private GripProbe() { }

    /** maxWrist = maior mudança da relação item/antebraço desde o início do golpe (o punho "torceu" quanto);
     *  maxForearm = quanto o antebraço girou no mundo nesse período; frozen = o item não girou (< 1°) embora o
     *  antebraço tenha girado mais de 15° — sintoma do item "parado no ar" enquanto o braço gira em volta. */
    record Stats(float maxTwist, float maxBend, float maxSpin, float spinAt, float maxWrist, float maxForearm, float itemTurn, boolean frozen) { }

    /** Mede um item segurado ao longo de um golpe; {@code print} imprime a tabela a cada {@code step} s. */
    static Stats probe(CharacterModel m, String what, String item, String farm, float step, boolean print) {
        Animator an = new Animator(m, 1f);
        Animator.Pose pose = an.newPose();
        Matrix4f[] mats = new Matrix4f[m.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        Matrix4f id = new Matrix4f();
        Sequencer seq = what.startsWith("seq:") ? new Sequencer(what.substring(4), an) : null;
        Animator.Clip clip = what.startsWith("clip:") ? Animator.Clip.valueOf(what.substring(5)) : null;
        float dur = seq != null ? seq.total - 1e-4f : clip != null ? an.duration(clip) : an.moveDuration(what);
        if (dur <= 0) throw new IllegalArgumentException("golpe inexistente: " + what);   // antes: uma amostra em repouso, "ok"
        Animator.Motion mo = new Animator.Motion();
        int ii = m.index.get(item), fi = m.index.get(farm);
        Quaternionf prev = null, rel0 = null, qf0 = null, qi0 = null;
        float maxWrist = 0, maxForearm = 0, itemTurn = 0;
        float maxTwist = 0, maxBend = 0, maxSpin = 0, spinAt = 0, nextPrint = 0;
        if (print)
            System.out.printf(Locale.ROOT, "%s / %s — item %s no %s%n   t(s)  torção°  flexão°  giro°/s  Δpunho°   eixoY(arma)           eixoX(fio/ventre)     | se seguisse o antebraço: Y, X%n",
                    m.name, what, item, farm);
        int n = (int) Math.ceil(dur / DT);
        for (int s = 0; s <= n; s++) {
            float t = Math.min(dur, s * DT);
            if (seq != null) seq.eval(t, mo);
            else if (clip != null) mo.set(clip, t);
            else { mo.set(Animator.Clip.IDLE, t); mo.move = what; mo.moveT = t; mo.weight = 1f; }
            an.evaluate(mo, pose, id, null);
            an.compose(pose, id, mats);
            Quaternionf qi = mats[ii].getNormalizedRotation(new Quaternionf());
            Quaternionf qf = mats[fi].getNormalizedRotation(new Quaternionf());
            Quaternionf rel = new Quaternionf(qf).conjugate().mul(qi);           // item no espaço do antebraço
            // torção = componente da rotação relativa em torno do eixo do antebraço (Y local do antebraço)
            Vector3f ax = new Vector3f(rel.x, rel.y, rel.z);
            Quaternionf twist = new Quaternionf(0, ax.y, 0, rel.w).normalize();
            float tw = angle(twist);
            Vector3f y = new Vector3f(0, 1, 0).rotate(rel);
            float bend = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, y.y))));
            float spin = 0;
            if (prev != null) {
                Quaternionf d = new Quaternionf(prev).conjugate().mul(qi);
                spin = angle(d) / DT;
            }
            prev = qi;
            if (rel0 == null) { rel0 = new Quaternionf(rel); qf0 = new Quaternionf(qf); qi0 = new Quaternionf(qi); }
            float dWrist = angle(new Quaternionf(rel0).conjugate().mul(rel));
            maxWrist = Math.max(maxWrist, dWrist);
            maxForearm = Math.max(maxForearm, angle(new Quaternionf(qf0).conjugate().mul(qf)));
            itemTurn = Math.max(itemTurn, angle(new Quaternionf(qi0).conjugate().mul(qi)));
            // orientação que o item teria se o punho mantivesse a empunhadura inicial (segue o antebraço)
            Quaternionf follow = new Quaternionf(qf).mul(rel0);
            Vector3f fy = new Vector3f(0, 1, 0).rotate(follow), fx = new Vector3f(1, 0, 0).rotate(follow);
            if (tw > maxTwist) maxTwist = tw;
            if (bend > maxBend) maxBend = bend;
            if (spin > maxSpin) { maxSpin = spin; spinAt = t; }
            if (print && t + 1e-6f >= nextPrint) {
                Matrix3f r = mats[ii].get3x3(new Matrix3f()).normal();
                Vector3f wy = r.transform(new Vector3f(0, 1, 0)).normalize(), wx = r.transform(new Vector3f(1, 0, 0)).normalize();
                System.out.printf(Locale.ROOT, "  %5.2f   %6.1f   %6.1f   %7.0f   %5.1f   (%5.2f %5.2f %5.2f)   (%5.2f %5.2f %5.2f)   | (%5.2f %5.2f %5.2f) (%5.2f %5.2f %5.2f)%n",
                        t, tw, bend, spin, dWrist, wy.x, wy.y, wy.z, wx.x, wx.y, wx.z, fy.x, fy.y, fy.z, fx.x, fx.y, fx.z);
                nextPrint += step;
            }
        }
        boolean frozen = itemTurn < 1f && maxForearm > 15f;
        if (print)
            System.out.printf(Locale.ROOT, "  máximos: torção %.1f°, flexão %.1f°, giro %.0f °/s em %.3f s | Δpunho %.1f°, antebraço girou %.1f°, item girou %.1f°%s%n",
                    maxTwist, maxBend, maxSpin, spinAt, maxWrist, maxForearm, itemTurn, frozen ? "  ← ITEM PARADO NO MUNDO" : "");
        return new Stats(maxTwist, maxBend, maxSpin, spinAt, maxWrist, maxForearm, itemTurn, frozen);
    }

    static float angle(Quaternionf q) {
        // atan2 é estável para ângulos pequenos (acos de w ≈ 1 perde tudo em float)
        double v = Math.sqrt((double) q.x * q.x + (double) q.y * q.y + (double) q.z * q.z);
        return (float) Math.toDegrees(2 * Math.atan2(v, Math.abs((double) q.w)));
    }

    /**
     * {@code viewer --grip rig golpe [passo]}. Reprova (saída 1): rig ausente ou sem perfil, golpe inexistente, nenhum
     * item segurado (nada conferido) e ITEM PARADO NO MUNDO — o critério da skill weapon-animation. Torção, flexão e giro
     * são informativos: a amarração do item é arbitrária (o repouso de vários itens já passa de 90°).
     */
    static boolean run(String[] args) {
        Path dir = Path.of("assets").resolve(args[1]);
        if (!java.nio.file.Files.isRegularFile(dir.resolve("rig.json"))) {
            System.out.println("grip: rig inexistente: " + dir + " (nada conferido)");
            return false;
        }
        CharacterModel m = CharacterModel.loadSkeleton(dir);
        float step = args.length > 3 ? Float.parseFloat(args[3]) : 0.05f;
        Animator an = new Animator(m, 1f);
        if (an.prof == null) {
            System.out.println("grip: " + args[1] + " não tem perfil de arma (nada conferido)");
            return false;
        }
        int checked = 0, frozen = 0;
        if (an.prof.rightItem != null && m.index.containsKey(an.prof.rightItem)) {
            if (probe(m, args[2], an.prof.rightItem, "farm_r", step, true).frozen()) frozen++;
            checked++;
        }
        if (an.prof.leftItem != null && m.index.containsKey(an.prof.leftItem)) {
            if (probe(m, args[2], an.prof.leftItem, "farm_l", step, true).frozen()) frozen++;
            checked++;
        }
        if (checked == 0) {
            System.out.println("grip: " + args[1] + " não segura item no perfil " + an.prof.name + " (nada conferido)");
            return false;
        }
        System.out.println(frozen == 0 ? "grip: OK (" + checked + " item(ns) conferido(s); torção, flexão e giro são informativos)"
                : "grip: ITEM PARADO NO MUNDO em " + frozen + " de " + checked + " item(ns)");
        return frozen == 0;
    }
}
