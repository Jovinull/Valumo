package vx.viewer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validação espacial das animações com armas (rodada 04). NÃO é hitbox de combate: é ferramenta de QA.
 *
 * <p>Usa os volumes APROXIMADOS de {@code meta.volumes} (cápsulas e esferas por parte, ver
 * tools/charlib_e.body_volumes) levados pela pose, e mede a PENETRAÇÃO (soma dos raios − distância) entre
 * pares proibidos: item × cabeça/chapéu/tronco/costas/pernas/braço que não o segura, antebraço afundando no
 * tronco, mão dentro do rosto. Contatos legítimos ficam de fora: a mão com o cabo que ela segura, a flecha
 * com as duas mãos do arqueiro, o escudo com o braço que o segura.
 *
 * <p>A amostragem é densa (1/480 s) e o relatório guarda o maior deslocamento da ponta entre duas amostras:
 * se ele fica abaixo da soma dos raios, um cruzamento entre amostras não passa despercebido. Volumes
 * aproximados não provam ausência absoluta de clipping — a revisão visual multiângulo continua obrigatória.
 */
public final class WeaponCheck {
    public static final float DT = 1f / 480f;
    /** Penetração máxima aceita (voxels) por tipo de par. */
    public static final float TOL_HEAD = 0.5f, TOL_BODY = 1.0f, TOL_ARM = 1.2f, TOL_FOREARM_TORSO = 2.5f, TOL_HAND_FACE = 1.0f;
    /** Erro de alcance aceito (voxels): mão longe do alvo/empunhadura. */
    public static final float TOL_REACH = 0.5f;
    /** Maior passo aceito da ponta entre duas amostras (voxels por 1/480 s): acima disso é um salto. */
    public static final float TOL_TIP_JUMP = 6f;

    public record Vol(int part, String tag, Vector3f a, Vector3f b, float r) { }

    /** Resultado de um par (ou critério) ao longo de uma animação. */
    public static final class Item {
        public final String name;
        public final float tol;
        public float worst;      // maior penetração/erro (voxels)
        public float at = -1;    // instante do pior caso (s)
        /** Mão direita e direção da arma no pior caso (voxels, espaço da raiz) — para corrigir a pose. */
        public String where = "";

        Item(String name, float tol) {
            this.name = name;
            this.tol = tol;
        }

        void see(float v, float t) {
            if (v > worst) {
                worst = v;
                at = t;
                if (probe != null) where = probe.get();
            }
        }

        java.util.function.Supplier<String> probe;

        public boolean ok() { return worst <= tol; }
    }

    public static final class Report {
        public final String subject;
        public final Map<String, Item> items = new LinkedHashMap<>();
        public float maxTipStep;   // maior deslocamento da ponta entre amostras (voxels)
        public int samples;
        public long nanos;

        Report(String subject) { this.subject = subject; }

        java.util.function.Supplier<String> probe;

        Item item(String name, float tol) {
            Item i = items.computeIfAbsent(name, n -> new Item(n, tol));
            i.probe = probe;
            return i;
        }

        public boolean ok() {
            for (Item i : items.values()) if (!i.ok()) return false;
            return true;
        }

        public List<Item> failures() {
            List<Item> f = new ArrayList<>();
            for (Item i : items.values()) if (!i.ok()) f.add(i);
            return f;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(subject + (ok() ? " OK" : " FALHA") + String.format(" (%d amostras, passo máx. da ponta %.2f vox)", samples, maxTipStep));
            for (Item i : items.values())
                sb.append(String.format("%n  %-28s %6.2f vox (tol %.1f)%s", i.name, i.worst, i.tol, i.ok() ? "" : "  <-- em " + String.format("%.3f s  %s", i.at, i.where)));
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ volumes

    public static List<Vol> volumes(CharacterModel m) {
        List<Vol> out = new ArrayList<>();
        if (m.meta == null || !m.meta.has("volumes")) return out;
        float vs = m.voxelSize;
        for (JsonElement e : m.meta.getAsJsonArray("volumes")) {
            JsonObject o = e.getAsJsonObject();
            Integer pi = m.index.get(o.get("part").getAsString());
            if (pi == null) continue;
            var a = o.getAsJsonArray("a");
            Vector3f va = new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()).mul(vs);
            Vector3f vb = null;
            if (o.has("b")) {
                var b = o.getAsJsonArray("b");
                vb = new Vector3f(b.get(0).getAsFloat(), b.get(1).getAsFloat(), b.get(2).getAsFloat()).mul(vs);
            }
            out.add(new Vol(pi, o.get("tag").getAsString(), va, vb, o.get("r").getAsFloat() * vs));
        }
        return out;
    }

    /** Volumes levados pela pose: mats[i] leva o bind da parte i ao espaço de saída. */
    public static List<Vol> posed(List<Vol> bind, Matrix4f[] mats) {
        List<Vol> out = new ArrayList<>(bind.size());
        for (Vol v : bind) {
            Matrix4f mm = mats[v.part];
            out.add(new Vol(v.part, v.tag, mm.transformPosition(new Vector3f(v.a)), v.b == null ? null : mm.transformPosition(new Vector3f(v.b)), v.r));
        }
        return out;
    }

    public static boolean isItem(String tag) {
        return switch (tag) {
            case "blade", "handle", "staff", "bow", "arrow", "shield" -> true;
            default -> false;
        };
    }

    /** Tolerância do par (item, corpo) ou NaN se o par não é verificado (contato legítimo). */
    public static float pairTol(CharacterModel m, Vol item, Vol body) {
        String it = item.tag, bt = body.tag;
        // item × item: só a arma do braço direito contra o escudo do esquerdo (a lâmina não atravessa o broquel)
        if (isItem(bt)) return (bt.equals("shield") && (it.equals("blade") || it.equals("handle"))) ? TOL_ARM : Float.NaN;
        boolean holder = holds(m, item.part, bt);
        return switch (bt) {
            case "head", "hat" -> it.equals("handle") ? TOL_BODY : TOL_HEAD;
            case "torso", "back", "leg" -> TOL_BODY;
            case "arm_l", "arm_r" -> {
                if (holder) yield Float.NaN;                                   // a mão que segura o item
                if (it.equals("arrow")) yield Float.NaN;                      // flecha passa pelas duas mãos
                if (it.equals("handle") || it.equals("bow")) yield Float.NaN; // duas mãos no cabo; mão da corda junto do arco
                yield TOL_ARM;
            }
            default -> Float.NaN;
        };
    }

    /** O item está preso (pela cadeia de pais) ao braço desta tag? Ou é o cabo de duas mãos (braço esquerdo)? */
    static boolean holds(CharacterModel m, int itemPart, String armTag) {
        String side = armTag.endsWith("_l") ? "l" : armTag.endsWith("_r") ? "r" : null;
        if (side == null) return false;
        for (int j = itemPart; j >= 0; j = m.parts.get(j).parent) {
            String n = m.parts.get(j).name;
            if (n.equals("farm_" + side) || n.equals("arm_" + side)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ geometria

    /** Distância entre dois segmentos (cápsulas); b == null vira ponto (esfera). */
    public static float segDist(Vector3f p1, Vector3f q1, Vector3f p2, Vector3f q2) {
        if (q1 == null) q1 = p1;
        if (q2 == null) q2 = p2;
        Vector3f d1 = new Vector3f(q1).sub(p1), d2 = new Vector3f(q2).sub(p2), r = new Vector3f(p1).sub(p2);
        float a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        float s, t;
        if (a <= 1e-12f && e <= 1e-12f) return p1.distance(p2);
        if (a <= 1e-12f) {
            s = 0;
            t = Math.clamp(f / e, 0f, 1f);
        } else {
            float c = d1.dot(r);
            if (e <= 1e-12f) {
                t = 0;
                s = Math.clamp(-c / a, 0f, 1f);
            } else {
                float b = d1.dot(d2), den = a * e - b * b;
                s = den != 0 ? Math.clamp((b * f - c * e) / den, 0f, 1f) : 0f;
                t = (b * s + f) / e;
                if (t < 0) {
                    t = 0;
                    s = Math.clamp(-c / a, 0f, 1f);
                } else if (t > 1) {
                    t = 1;
                    s = Math.clamp((b - c) / a, 0f, 1f);
                }
            }
        }
        Vector3f c1 = new Vector3f(p1).add(d1.mul(s)), c2 = new Vector3f(p2).add(d2.mul(t));
        return c1.distance(c2);
    }

    public static float penetration(Vol x, Vol y) {
        return x.r + y.r - segDist(x.a, x.b, y.a, y.b);
    }

    /**
     * Penetração por volume numa pose (para colorir o modo de depuração e para o relatório). Retorna, para
     * cada volume posado, a pior penetração além da tolerância do par (≤ 0 = ok).
     */
    public static float[] excess(CharacterModel m, List<Vol> posed) {
        float[] ex = new float[posed.size()];
        java.util.Arrays.fill(ex, -1f);
        float vs = m.voxelSize;
        for (int i = 0; i < posed.size(); i++) {
            Vol a = posed.get(i);
            if (!isItem(a.tag)) continue;
            for (int j = 0; j < posed.size(); j++) {
                Vol b = posed.get(j);
                float tol = pairTol(m, a, b);
                if (Float.isNaN(tol)) continue;
                float e = penetration(a, b) / vs - tol;
                ex[i] = Math.max(ex[i], e);
                ex[j] = Math.max(ex[j], e);
            }
        }
        return ex;
    }

    // ------------------------------------------------------------------ validação de uma animação

    /** Avalia um golpe do perfil (sozinho, a partir do repouso) ou um roteiro do Sequencer ("seq:..."). */
    public static Report validate(CharacterModel m, String what) {
        Animator an = new Animator(m, 1f);
        Animator.Pose pose = an.newPose();
        Matrix4f[] mats = new Matrix4f[m.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        Matrix4f id = new Matrix4f();
        List<Vol> bind = volumes(m);
        Report rep = new Report(m.name + " / " + what);
        // rodada 09.2: "...|pernas=walk:fase:rumo°:passada" avalia com as PERNAS andando por baixo da ação (golpe andando)
        Animator.Clip legClip = null;
        float legPh = 0f, legDir = 0f, legStride = 1f;
        if (what.contains("|pernas=")) {
            String[] lg = what.substring(what.indexOf("|pernas=") + 8).split(":");
            what = what.substring(0, what.indexOf("|pernas="));
            legClip = Animator.Clip.valueOf(lg[0].toUpperCase());
            legPh = lg.length > 1 ? Float.parseFloat(lg[1]) : 0f;
            legDir = lg.length > 2 ? (float) Math.toRadians(Float.parseFloat(lg[2])) : 0f;
            legStride = lg.length > 3 ? Float.parseFloat(lg[3]) : 1f;
        }
        Sequencer seq = what.startsWith("seq:") ? new Sequencer(what.substring(4), an) : null;
        Animator.Clip clip = what.startsWith("clip:") ? Animator.Clip.valueOf(what.substring(5)) : null;
        float dur = seq != null ? seq.total : clip != null ? an.duration(clip) : an.moveDuration(what);
        if (dur <= 0) throw new IllegalArgumentException("golpe inexistente: " + what);
        float vs = m.voxelSize;
        Animator.Motion mo = new Animator.Motion();
        Vector3f lastTip = null;
        long t0 = System.nanoTime();
        int n = (int) Math.ceil(dur / DT);
        Integer head = m.index.get("head");
        rep.probe = () -> {
            Vector3f h = new Vector3f(an.dbgHandR).div(vs), d = new Vector3f(an.dbgTip).sub(an.dbgHandR).normalize();
            return String.format(java.util.Locale.ROOT, "mão (%.1f, %.1f, %.1f) dir (%.2f, %.2f, %.2f)", h.x, h.y, h.z, d.x, d.y, d.z);
        };
        for (int s = 0; s <= n; s++) {
            // roteiros dão a volta no laço em T = total: a última amostra fica logo antes (senão compara fim × início)
            float t = Math.min(seq != null ? dur - 1e-4f : dur, s * DT);
            if (seq != null) seq.eval(t, mo);
            else if (clip != null) mo.set(clip, t);
            else {
                mo.set(Animator.Clip.IDLE, t);
                mo.move = what;
                mo.moveT = t;
                mo.weight = 1f;
            }
            if (legClip != null) {                                  // passada contínua (fase pela distância na velocidade de desenho)
                float cad = legClip == Animator.Clip.RUN ? Animator.RUN_CADENCE : Animator.WALK_CADENCE;
                mo.stride = legStride;
                mo.legW = 1f;
                mo.legClip = legClip;
                mo.legT = legPh / cad + t;
                mo.legDir = legDir;
                mo.locoDir = legDir;
            }
            an.evaluate(mo, pose, id, null);
            an.compose(pose, id, mats);
            List<Vol> pv = posed(bind, mats);
            for (Vol a : pv) {
                if (!isItem(a.tag)) continue;
                for (Vol b : pv) {
                    float tol = pairTol(m, a, b);
                    if (Float.isNaN(tol)) continue;
                    rep.item(a.tag + " × " + b.tag, tol).see(Math.max(0, penetration(a, b) / vs), t);
                }
            }
            // antebraço afundando no tronco (cotovelo para dentro)
            for (Vol a : pv) {
                if (!(a.tag.startsWith("arm_") && m.parts.get(a.part).name.startsWith("farm"))) continue;
                for (Vol b : pv)
                    if (b.tag.equals("torso"))
                        rep.item("antebraço × tronco", TOL_FOREARM_TORSO).see(Math.max(0, penetration(a, b) / vs), t);
            }
            // mão dentro do rosto
            if (head != null) {
                for (Vol b : pv) {
                    if (!b.tag.equals("head")) continue;
                    for (Vector3f hnd : new Vector3f[]{handWorld(an, mats, "r"), handWorld(an, mats, "l")})
                        rep.item("mão × rosto", TOL_HAND_FACE).see(Math.max(0, (b.r - segDist(hnd, null, b.a, b.b)) / vs - 0.0f), t);
                }
            }
            rep.item("alcance mão direita", TOL_REACH).see(an.reachErrR, t);
            rep.item("alcance/empunhadura mão esquerda", TOL_REACH).see(an.reachErrL, t);
            Vector3f tip = tipWorld(an, mats);
            if (lastTip != null) {
                float step = tip.distance(lastTip) / vs;
                rep.maxTipStep = Math.max(rep.maxTipStep, step);
                // continuidade: a ponta não pode "teleportar" (mais de 6 voxels em 1/480 s ≈ 115 u/s)
                rep.item("salto da ponta (continuidade)", TOL_TIP_JUMP).see(step, t);
            }
            lastTip = tip;
            rep.samples++;
        }
        rep.nanos = System.nanoTime() - t0;
        return rep;
    }

    /** Ponta de referência medida na pose FINAL (misturada): ponta da arma da direita ou ponta de cima do arco. */
    static Vector3f tipWorld(Animator an, Matrix4f[] mats) {
        CharacterModel m = an.model();
        float vs = m.voxelSize;
        if (an.prof != null && an.prof.rightItem != null && an.prof.tipLength > 0 && m.index.containsKey(an.prof.rightItem)) {
            CharacterModel.Part w = m.parts.get(m.index.get(an.prof.rightItem));
            return mats[m.index.get(an.prof.rightItem)].transformPosition(new Vector3f(w.px, w.py + an.prof.tipLength * vs, w.pz));
        }
        if (m.index.containsKey("bow")) return mats[m.index.get("bow")].transformPosition(new Vector3f(8.5f, 30f, 1f).mul(vs));
        return mats[m.index.get("farm_r")].transformPosition(new Vector3f(-7f, 11f, 1f).mul(vs));
    }

    /** Ponto da mão no espaço de saída das matrizes (pivô do item segurado, senão meta.hands). */
    static Vector3f handWorld(Animator an, Matrix4f[] mats, String side) {
        CharacterModel m = an.model();
        String item = side.equals("r") ? (an.prof != null ? an.prof.rightItem : "weapon") : (an.prof != null ? an.prof.leftItem : null);
        Integer fi = m.index.get("farm_" + side);
        if (item != null && m.index.containsKey(item)) {
            CharacterModel.Part p = m.parts.get(m.index.get(item));
            return mats[m.index.get(item)].transformPosition(new Vector3f(p.px, p.py, p.pz));
        }
        var a = m.meta.getAsJsonObject("hands").getAsJsonArray(side);
        Vector3f b = new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()).mul(m.voxelSize);
        return mats[fi].transformPosition(b);
    }
}
