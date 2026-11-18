package vx.game;

import vx.viewer.Animator;
import vx.viewer.Choreo;
import vx.viewer.Sequencer;

/**
 * Rodada 09 — camada de ação AO VIVO do herói: a mesma semântica do {@link Sequencer} (que gera os vídeos e alimenta o
 * validador de armas), mas com os segmentos decididos pelo jogo em vez de um roteiro. Por isso tudo o que o validador
 * aprova num roteiro "golpe!corte,reação" vale para o jogo — {@code CombatAnimationTest} compara as duas saídas.
 *
 * <p>Regras (as do Sequencer):
 * <ul>
 *   <li>locomoção → golpe: a ação entra em {@link Sequencer#FADE_IN} s;</li>
 *   <li>golpe cortado → outro golpe/reação: o cortado anda pela PRÓPRIA trajetória no ritmo da janela autorada do
 *       perfil ({@link Choreo.Move#cutRate}) durante {@link Sequencer#INT_FADE} s, e a reação pode virar a saída
 *       autorada da fase ({@link Choreo.Move#exitFor});</li>
 *   <li>corte durante uma transição de corte: a origem é a própria mistura (dois níveis no máximo — o jogo não deixa
 *       haver três: ver {@link Hero});</li>
 *   <li>golpe → locomoção (fim natural ou liberação): a pose congelada no instante sai em {@link Sequencer#FADE_OUT} s.</li>
 * </ul>
 * A camada é um valor: {@link #copyFrom} faz o retrato do passo anterior para a interpolação do desenho.
 */
final class ActionLayer {
    /** Segmento: locomoção (move == null) ou golpe. dur = quanto tocou (corte) ou a duração inteira. */
    static final class Seg {
        String move;
        float dur, rate, full;
        boolean cut;

        Seg set(Seg o) {
            if (o == null) return null;
            move = o.move; dur = o.dur; rate = o.rate; full = o.full; cut = o.cut;
            return this;
        }

        boolean action() { return move != null; }
    }

    private final Choreo prof;
    Seg cur = new Seg(), prev, pprev;
    /** Tempo desde o início do segmento atual. */
    float local;
    /** Golpe parado neste instante (guarda segurada). NaN = corre. */
    float holdAt = Float.NaN;

    ActionLayer(Choreo prof) { this.prof = prof; }

    void copyFrom(ActionLayer o) {
        cur.set(o.cur);
        prev = o.prev == null ? null : (prev == null ? new Seg() : prev).set(o.prev);
        pprev = o.pprev == null ? null : (pprev == null ? new Seg() : pprev).set(o.pprev);
        local = o.local;
        holdAt = o.holdAt;
    }

    String move() { return cur.move; }

    /** Tempo do golpe atual (com a guarda segurada). */
    float moveT() { return Float.isNaN(holdAt) ? local : Math.min(local, holdAt); }

    boolean busy() { return cur.action(); }

    /** Pose de um golpe ainda saindo sobre a locomoção (FADE_OUT): uma ação nova aqui daria salto, então espera. */
    boolean fadingOut() { return !cur.action() && prev != null && prev.action() && local < Sequencer.FADE_OUT; }

    /** Golpe em transição de corte agora (a origem ainda pesa na pose). */
    boolean crossfading() { return cur.action() && prev != null && prev.action() && prev.cut && local < fade(cur.move); }

    /** Rodada 09.2 — duração da transição de corte quando {@code move} entra ({@link Sequencer#intFade}). */
    float fade(String move) { return Sequencer.intFade(prof, move); }

    /**
     * Rodada 09.2 — um corte agora seria o terceiro dentro de uma transição dupla (a camada só mistura dois níveis)?
     * Esquiva e golpe esperam (ficam no buffer) em vez de cortar aqui.
     */
    boolean canCut() {
        return !(crossfading() && pprev != null && pprev.action() && pprev.cut && prev.dur < fade(prev.move));
    }

    /** Começa um golpe ou reação; se havia golpe tocando, ele é CORTADO aqui (regra autorada do perfil). Retorna o nome usado. */
    String start(String move) {
        if (crossfading() && pprev != null && pprev.action() && pprev.cut && prev.dur < fade(prev.move))
            throw new IllegalStateException("terceiro corte dentro de uma transição dupla (o jogo não deveria permitir)");
        float playing = moveT();
        Seg ended = new Seg().set(cur);
        if (ended.action()) {
            ended.dur = Math.min(playing, ended.full);
            ended.cut = ended.dur < ended.full - 1e-5f;
            if (ended.cut) {
                Choreo.Move mv = prof.moves.get(ended.move);
                ended.rate = mv.cutRate(ended.dur);
                String exit = mv.exitFor(move, ended.dur);
                if (exit != null) move = exit;
            }
        } else ended.dur = local;
        if (!prof.moves.containsKey(move)) throw new IllegalArgumentException("golpe inexistente no perfil: " + move);
        pprev = prev;
        prev = ended;
        cur = new Seg();
        cur.move = move;
        cur.full = prof.moves.get(move).duration();
        cur.dur = cur.full;
        local = 0;
        holdAt = Float.NaN;
        return move;
    }

    /** Volta à locomoção (fim natural ou liberação): a pose do golpe sai em FADE_OUT. */
    void release() {
        if (!cur.action()) return;
        Seg ended = new Seg().set(cur);
        ended.dur = Math.min(moveT(), ended.full);
        ended.cut = ended.dur < ended.full - 1e-5f;
        if (ended.cut) ended.rate = prof.moves.get(ended.move).cutRate(ended.dur);
        pprev = prev;
        prev = ended;
        cur = new Seg();
        local = 0;
        holdAt = Float.NaN;
    }

    /** Pula para outro instante do MESMO golpe (só entre chaves com a mesma pose parada: guarda → impacto → guarda). */
    void seek(float t) {
        local = t;
        holdAt = Float.NaN;
    }

    void update(float dt) {
        local += dt;
        if (cur.action() && Float.isNaN(holdAt) && local >= cur.full) {   // fim natural: o que passou do fim já conta na saída
            float over = local - cur.full;
            release();
            local = over;
        }
    }

    /**
     * Preenche a Motion sobre a locomoção já posta em {@code out} (clipe, tempo e troca do {@link Player}), {@code ahead}
     * segundos depois do estado atual (subamostras e interpolação do desenho), sem mudar o estado.
     */
    Animator.Motion eval(Animator.Motion out, float ahead) {
        float l = local + ahead;
        if (cur.action() && Float.isNaN(holdAt) && l >= cur.full) {      // o golpe termina dentro do adiantamento
            if (l - cur.full < Sequencer.FADE_OUT) {
                out.move = cur.move;
                out.moveT = cur.full;
                out.weight = 1f - (l - cur.full) / Sequencer.FADE_OUT;
            }
            return out;
        }
        if (!cur.action()) {
            if (prev != null && prev.action() && l < Sequencer.FADE_OUT) {
                out.move = prev.move;
                out.moveT = prev.dur;
                out.weight = 1f - l / Sequencer.FADE_OUT;
            }
            return out;
        }
        out.move = cur.move;
        out.moveT = Float.isNaN(holdAt) ? Math.min(l, cur.full) : Math.min(l, holdAt);
        if (prev == null || !prev.action()) {
            float fi = Sequencer.fadeIn(prof, cur.move);
            if (prev == null || l >= fi) { out.clip = Animator.Clip.IDLE; out.fromClip = null; out.locoW = 1f; }
            out.weight = prev == null ? 1f : Math.min(1f, l / fi);
            return out;
        }
        out.weight = 1f;
        float cf = fade(cur.move);
        if (prev.cut && l < cf) {
            out.fromMove = prev.move;
            out.fromT = Math.clamp(prev.dur + prev.rate * l, 0f, prev.full);
            out.xfade = l / cf;
            if (pprev != null && !pprev.action() && prev.dur < Sequencer.fadeIn(prof, prev.move)) {
                float w0 = prev.dur / Sequencer.fadeIn(prof, prev.move);  // cortado ainda entrando sobre a locomoção
                out.weight = w0 + (1f - w0) * out.xfade;
            } else {
                out.clip = Animator.Clip.IDLE; out.fromClip = null; out.locoW = 1f;
            }
            if (pprev != null && pprev.action()) {
                float since = prev.dur + l;
                float pf = fade(prev.move);
                if (pprev.cut && prev.dur < pf) {
                    out.fromMove2 = pprev.move;
                    out.fromT2 = Math.clamp(pprev.dur + pprev.rate * since, 0f, pprev.full);
                    out.xfade2 = Math.min(1f, since / pf);
                } else if (!pprev.cut && prev.dur < Sequencer.XFADE) {
                    out.fromMove2 = pprev.move;
                    out.fromT2 = pprev.dur;
                    out.xfade2 = Math.min(1f, since / Sequencer.XFADE);
                }
            }
        } else {
            out.clip = Animator.Clip.IDLE; out.fromClip = null; out.locoW = 1f;
            if (!prev.cut && l < Sequencer.XFADE) {
                out.fromMove = prev.move;
                out.fromT = prev.dur;
                out.xfade = l / Sequencer.XFADE;
            }
        }
        return out;
    }
}
