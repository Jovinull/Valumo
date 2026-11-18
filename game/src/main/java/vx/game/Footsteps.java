package vx.game;

import vx.viewer.Animator;

/**
 * Rodada 09.1 (escopo secundário) — passos: um som discreto quando um pé POUSA, pela mesma fase de animação que o
 * desenho usa (Animator: ciclo frac(animT × cadência); o pé esquerdo pousa em 0, o direito em 0,5). Uma superfície só
 * (terra/grama, o mesmo som). Só lê o {@link Player}.
 */
public final class Footsteps {
    private final Sfx sfx;
    private float lastPhase = -1f;
    private long ticks;

    public Footsteps(Sfx sfx) { this.sfx = sfx; }

    public void afterTick(Player p) {
        ticks++;
        boolean moving = (p.clip == Animator.Clip.WALK || p.clip == Animator.Clip.RUN) && p.grounded && p.speed() > 0.3f;
        if (!moving) { lastPhase = -1f; return; }
        float cad = p.clip == Animator.Clip.RUN ? Animator.RUN_CADENCE : Animator.WALK_CADENCE;
        float ph = (p.animT * cad) % 1f;
        if (lastPhase >= 0f && (ph < lastPhase || (lastPhase < 0.5f && ph >= 0.5f)))   // passou por 0 (esquerdo) ou 0,5 (direito)
            sfx.cue("passo_terra", ticks, p.x, p.y, p.z);
        lastPhase = ph;
    }
}
