package vx.viewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Roteiro determinístico de animação (rodada 04): "idle:0.6,run:1.2,slash_h,idle:0.8". Cada item é um clipe
 * de locomoção (idle, walk, run) com duração em segundos ou um golpe do perfil (duração própria, ou
 * {@code nome:segundos} para segurar a pose final; {@code nome!segundos} INTERROMPE o golpe nesse instante —
 * rodada 05.1, para testar que um estado obrigatório como o HIT assume sem teleporte). Tudo é função do tempo T: a mesma chamada dá a mesma
 * pose em qualquer taxa de quadros, então capturas e vídeos são reproduzíveis.
 *
 * <p>Transições: locomoção → golpe mistura a camada de ação em {@link #FADE_IN} s (as pernas freiam da
 * passada para a base do golpe); golpe → locomoção, em {@link #FADE_OUT} s; golpe → golpe encadeia pelo
 * controle (mão, direção da arma), em {@link #XFADE} s. Nada é interpolado em ângulos de Euler.
 */
public final class Sequencer {
    public static final float FADE_IN = 0.16f, FADE_OUT = 0.28f, XFADE = 0.12f, LOCO_FADE = 0.25f;
    /** Interrupção (rodada 05.1): durante INT_FADE s o golpe cortado continua andando pela PRÓPRIA trajetória
     *  autorada enquanto o novo golpe assume — para trás (REWIND×) se foi cortado na preparação, para a frente
     *  (embalo) se já estava na execução. A mistura nunca liga em linha reta duas poses distantes. */
    public static final float INT_FADE = 0.30f, REWIND = 2.0f;

    /** Rodada 09.2 — duração da transição de corte quando {@code move} entra (o {@code cutFade} do gesto ou INT_FADE). */
    public static float intFade(Choreo prof, String move) {
        if (prof == null || move == null) return INT_FADE;
        Choreo.Move m = prof.moves.get(move);
        return m == null || Float.isNaN(m.cutFade) ? INT_FADE : m.cutFade;
    }

    private Choreo prof;

    /** Rodada 09.3 — entrada a partir da locomoção quando {@code move} começa (o {@code fadeIn} do gesto ou FADE_IN). */
    public static float fadeIn(Choreo prof, String move) {
        if (prof == null || move == null) return FADE_IN;
        Choreo.Move m = prof.moves.get(move);
        return m == null || Float.isNaN(m.fadeIn) ? FADE_IN : m.fadeIn;
    }

    record Seg(Animator.Clip clip, String move, float start, float dur, boolean cut, float rate) {
        boolean action() { return move != null; }
    }

    private final List<Seg> segs = new ArrayList<>();
    public final float total;

    private static final Map<String, Sequencer> CACHE = new HashMap<>();

    public static Sequencer of(String script, Animator a) {
        return CACHE.computeIfAbsent(script + "@" + a.hashCode(), k -> new Sequencer(script, a));
    }

    public Sequencer(String script, Animator a) {
        prof = a.prof;
        float t = 0;
        for (String tok : script.split("[,>;]")) {
            tok = tok.trim();
            if (tok.isEmpty()) continue;
            float cut = Float.NaN;                                   // "golpe!0.35": interrompido em 0,35 s
            if (tok.contains("!")) {
                String[] ck = tok.split("!");
                tok = ck[0];
                cut = Float.parseFloat(ck[1]);
            }
            String[] kv = tok.split(":");
            String n = kv[0];
            Animator.Clip clip = switch (n) {
                case "idle" -> Animator.Clip.IDLE;
                case "walk" -> Animator.Clip.WALK;
                case "run" -> Animator.Clip.RUN;
                default -> null;
            };
            float d;
            if (clip != null) {
                d = kv.length > 1 ? Float.parseFloat(kv[1]) : 1f;
                segs.add(new Seg(clip, null, t, d, false, 0f));
            } else {
                Seg before = segs.isEmpty() ? null : segs.get(segs.size() - 1);
                if (before != null && before.cut() && a.prof != null) {             // rodada 09: saída autorada da fase cortada
                    String exit = a.prof.moves.get(before.move()).exitFor(n, before.dur());
                    if (exit != null) n = exit;
                }
                float md = a.moveDuration(n);
                fullDur.put(n, md);
                if (md <= 0) throw new IllegalArgumentException("golpe inexistente no perfil: " + n);
                d = kv.length > 1 ? Math.max(md, Float.parseFloat(kv[1])) : md;
                if (!Float.isNaN(cut)) d = Math.min(md, Math.max(0.01f, cut));
                Choreo.Move mv = a.prof != null ? a.prof.moves.get(n) : null;
                float rate = !Float.isNaN(cut) && mv != null ? mv.cutRate(cut) : -REWIND;   // janela autorada (05.1/09)
                segs.add(new Seg(Animator.Clip.IDLE, n, t, d, !Float.isNaN(cut), rate));
            }
            t += d;
        }
        total = t;
    }

    /** Preenche a Motion no tempo T (s, a partir do início do roteiro; repete em laço). */
    public Animator.Motion eval(float T, Animator.Motion out) {
        float tt = total > 0 ? T % total : 0;
        int k = 0;
        while (k < segs.size() - 1 && tt >= segs.get(k + 1).start) k++;
        Seg s = segs.get(k), prev = k > 0 ? segs.get(k - 1) : null;
        float local = tt - s.start;
        out.set(Animator.Clip.IDLE, T);
        if (!s.action()) {
            out.clip = s.clip;
            if (prev != null && !prev.action() && prev.clip != s.clip && local < LOCO_FADE) {
                out.fromClip = prev.clip;                        // parado ↔ andar ↔ correr sem corte
                out.locoW = local / LOCO_FADE;
            }
            if (prev != null && prev.action() && local < FADE_OUT) {
                out.move = prev.move;
                out.moveT = prev.dur;
                out.weight = 1f - local / FADE_OUT;
            }
        } else {
            out.move = s.move;
            out.moveT = local;
            if (prev == null || !prev.action()) {
                float fi = fadeIn(prof, s.move);
                out.clip = prev != null && local < fi ? prev.clip : Animator.Clip.IDLE;
                out.weight = prev == null ? 1f : Math.min(1f, local / fi);
            } else {
                out.weight = 1f;
                float fade = intFade(prof, s.move);
                if (prev.cut && local < fade) {
                    out.fromMove = prev.move;
                    out.fromT = Math.clamp(prev.dur + prev.rate * local, 0f, prevFull(prev));
                    out.xfade = local / fade;
                    // cortado ainda entrando (peso < 1 sobre a locomoção): o peso continua de onde estava
                    Seg pp = k > 1 ? segs.get(k - 2) : null;
                    // rodada 09: cortado enquanto ainda misturava com o golpe anterior (encadeamento + dano): a origem
                    // é a própria mistura, que continua andando — contínua no instante do segundo corte
                    if (pp != null && pp.action()) {
                        float since = prev.dur + local;
                        float pf = intFade(prof, prev.move);
                        if (pp.cut && prev.dur < pf) {
                            out.fromMove2 = pp.move;
                            out.fromT2 = Math.clamp(pp.dur + pp.rate * since, 0f, prevFull(pp));
                            out.xfade2 = Math.min(1f, since / pf);
                        } else if (!pp.cut && prev.dur < XFADE) {
                            out.fromMove2 = pp.move;
                            out.fromT2 = pp.dur;
                            out.xfade2 = Math.min(1f, since / XFADE);
                        }
                    }
                    if (pp != null && !pp.action() && prev.dur < fadeIn(prof, prev.move)) {
                        float w0 = prev.dur / fadeIn(prof, prev.move);
                        out.clip = pp.clip;
                        out.weight = w0 + (1f - w0) * out.xfade;
                    }
                } else if (!prev.cut && local < XFADE) {
                    out.fromMove = prev.move;
                    out.fromT = prev.dur;
                    out.xfade = local / XFADE;
                }
            }
        }
        return out;
    }

    private final Map<String, Float> fullDur = new HashMap<>();

    private float prevFull(Seg s) { return fullDur.getOrDefault(s.move, s.dur); }

    /** Velocidade de avanço (u/s) no tempo T: a da passada nos trechos de locomoção, freando nos golpes. */
    public float speed(float T) {
        float tt = total > 0 ? T % total : 0;
        int k = 0;
        while (k < segs.size() - 1 && tt >= segs.get(k + 1).start) k++;
        Seg s = segs.get(k), prev = k > 0 ? segs.get(k - 1) : null;
        float local = tt - s.start;
        if (!s.action()) {
            float v = clipSpeed(s.clip);
            if (prev != null && prev.action() && local < FADE_OUT) v *= local / FADE_OUT;
            if (prev != null && !prev.action() && local < LOCO_FADE) v = clipSpeed(prev.clip) + (v - clipSpeed(prev.clip)) * local / LOCO_FADE;
            return v;
        }
        float fi = fadeIn(prof, s.move);
        if (prev != null && !prev.action() && local < fi) return clipSpeed(prev.clip) * (1f - local / fi);
        return 0f;
    }

    private static float clipSpeed(Animator.Clip c) {
        return c == Animator.Clip.WALK ? Animator.WALK_SPEED : c == Animator.Clip.RUN ? Animator.RUN_SPEED : 0f;
    }

    /** Distância percorrida até T (integração com passo fixo, determinística). */
    public float distance(float T) {
        float d = 0, dt = 1f / 240f;
        for (float x = 0; x < T; x += dt) d += speed(x) * Math.min(dt, T - x);
        return d;
    }
}
