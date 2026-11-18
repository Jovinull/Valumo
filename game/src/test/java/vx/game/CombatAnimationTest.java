package vx.game;

import org.junit.jupiter.api.Test;
import vx.viewer.Animator;
import vx.viewer.CharacterModel;
import vx.viewer.Sequencer;
import vx.viewer.WeaponCheck;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09 — a animação do combate passa no validador de armas em TODAS as transições que o jogo pode produzir, nos
 * tempos reais de {@code combate_e.json}: encadeamentos, dano no meio de um encadeamento, liberação para andar, golpe ou
 * guarda logo depois da reação, guarda baixando, derrota. E a camada ao vivo ({@link ActionLayer}) dá a MESMA Motion do
 * {@link Sequencer} — então o que o validador aprova num roteiro é o que o jogo desenha.
 */
class CombatAnimationTest {
    static final Path A = GameplayTest.A;
    static final float STEP = 0.02f;

    static CombatDef def() { return CombatDef.load(A, "combate_e"); }

    static CharacterModel kit(CombatDef d) { return CharacterModel.loadSkeleton(A.resolve(d.heroKit)); }

    static List<String> bad(CharacterModel m, List<String> seqs) {
        List<String> out = new ArrayList<>();
        for (String s : seqs) {
            WeaponCheck.Report r = WeaponCheck.validate(m, "seq:" + s);
            if (!r.ok()) out.add(r.toString());
        }
        return out;
    }

    static String f(float x) { return String.format(Locale.ROOT, "%.3f", x); }

    @Test
    void chainsAndHitsDuringAChainAreSafe() {
        CombatDef d = def();
        CharacterModel m = kit(d);
        CombatDef.Attack h = d.attacks.get("slash_h"), dd = d.attacks.get("slash_d");
        List<String> seqs = new ArrayList<>();
        seqs.add("idle:0.3,slash_h!" + f(h.chainAt()) + ",slash_d,idle:0.5");
        seqs.add("idle:0.3,slash_h!" + f(h.chainAt()) + ",slash_d!" + f(dd.chainAt()) + ",slash_v,idle:0.5");
        for (float x = 0.01f; x < Sequencer.INT_FADE; x += 0.01f) {        // dano enquanto o encadeamento ainda mistura
            seqs.add("idle:0.3,slash_h!" + f(h.chainAt()) + ",slash_d!" + f(x) + ",hit,idle:0.5");
            seqs.add("idle:0.3,slash_h!" + f(h.chainAt()) + ",slash_d!" + f(dd.chainAt()) + ",slash_v!" + f(x) + ",hit,idle:0.5");
        }
        List<String> b = bad(m, seqs);
        assertTrue(b.isEmpty(), b.size() + " transições inseguras:\n" + String.join("\n", b));
    }

    @Test
    void releasingRestartingAndGuardingAfterAnAttackAreSafe() {
        CombatDef d = def();
        CharacterModel m = kit(d);
        Animator an = new Animator(m, 1f);
        List<String> seqs = new ArrayList<>();
        for (CombatDef.Attack a : d.attacks.values()) {
            float full = an.moveDuration(a.move());
            for (float x = a.release(); x < full; x += STEP) {
                seqs.add("idle:0.3," + a.move() + "!" + f(x) + ",walk:0.8");              // andar libera
                seqs.add("idle:0.3," + a.move() + "!" + f(x) + "," + d.firstAttack + ",idle:0.5");   // recomeça a sequência
                seqs.add("idle:0.3," + a.move() + "!" + f(x) + "," + Hero.GUARD + "!0.6,idle:0.6");   // ergue a guarda
            }
        }
        List<String> b = bad(m, seqs);
        assertTrue(b.isEmpty(), b.size() + " transições inseguras:\n" + String.join("\n", b));
    }

    /** Como o jogo chega a cada reação: parado, no meio do corte (rebobina), logo após o corte (saídas) e na recuperação. */
    static List<String[]> reactions(CombatDef d) {
        return List.of(new String[]{"idle:0.3,hit", "hit"}, new String[]{"idle:0.3,slash_h!0.40,hit", "hit"},
                new String[]{"idle:0.3,slash_h!0.55,hit", "hit_cross_h"}, new String[]{"idle:0.3,slash_d!0.57,hit", "hit_cross_d"},
                new String[]{"idle:0.3,slash_h!0.80,hit", "hit"}, new String[]{"idle:0.3," + Hero.GUARD + "!0.6,hit", "hit"});
    }

    @Test
    void actingAfterAHitBeingHitAgainDyingAndLoweringTheGuardAreSafe() {
        CombatDef d = def();
        CharacterModel m = kit(d);
        Animator an = new Animator(m, 1f);
        List<String> seqs = new ArrayList<>();
        for (String[] r : reactions(d)) {
            float full = an.moveDuration(r[1]), stun = Math.max(d.heroStun, d.exitStun.getOrDefault(r[1], 0f));
            for (float x = stun; x < full; x += STEP) {                     // o controle volta: golpe, guarda ou andar
                seqs.add(r[0] + "!" + f(x) + "," + d.firstAttack + ",idle:0.5");
                seqs.add(r[0] + "!" + f(x) + "," + Hero.GUARD + "!0.6,idle:0.6");
                seqs.add(r[0] + "!" + f(x) + ",walk:0.8");
            }
            for (float x = d.heroInvuln; x < full; x += STEP)               // outro dano quando a proteção acaba
                seqs.add(r[0] + "!" + f(x) + ",hit,idle:0.5");
            seqs.add(r[0] + "!" + f(full - Hero.DEFEAT_LEAD) + ",defeat,idle:0.2");   // derrota
        }
        for (float x = Hero.GUARD_DOWN; x < an.moveDuration(Hero.GUARD); x += STEP) {   // guarda baixando
            seqs.add("idle:0.3," + Hero.GUARD + "!" + f(x) + "," + d.firstAttack + ",idle:0.5");
            seqs.add("idle:0.3," + Hero.GUARD + "!" + f(x) + ",walk:0.8");
        }
        List<String> b = bad(m, seqs);
        assertTrue(b.isEmpty(), b.size() + " transições inseguras:\n" + String.join("\n", b));
    }

    @Test
    void exitReactionsStunLongEnoughAndProtectionOutlastsEveryStun() {
        CombatDef d = def();
        assertTrue(d.exitStun.containsKey("hit_cross_h") && d.exitStun.containsKey("hit_cross_d"));
        for (float s : d.exitStun.values()) assertTrue(d.heroInvuln >= s, "proteção acaba antes do atordoamento");
        assertTrue(d.heroInvuln >= d.heroStun);
    }

    @Test
    void theGuardKeysUsedForHoldingAndImpactShareTheSamePose() {
        CombatDef d = def();
        Animator an = new Animator(kit(d), 1f);
        Animator.Pose a = an.newPose(), b = an.newPose();
        Animator.Motion mo = new Animator.Motion();
        float[] keys = {Hero.GUARD_UP, Hero.GUARD_IMPACT, Hero.GUARD_IMPACT_END, Hero.GUARD_DOWN};
        int n = an.model().parts.size();
        org.joml.Matrix4f[] ma = new org.joml.Matrix4f[n], mb = new org.joml.Matrix4f[n];
        for (int i = 0; i < n; i++) { ma[i] = new org.joml.Matrix4f(); mb[i] = new org.joml.Matrix4f(); }
        org.joml.Matrix4f id = new org.joml.Matrix4f();
        for (float k : keys) {
            mo.set(Animator.Clip.IDLE, 0); mo.move = Hero.GUARD; mo.moveT = Hero.GUARD_UP; mo.weight = 1;
            an.evaluate(mo, a, id, null);
            an.compose(a, id, ma);
            mo.set(Animator.Clip.IDLE, 0); mo.move = Hero.GUARD; mo.moveT = k; mo.weight = 1;
            an.evaluate(mo, b, id, null);
            an.compose(b, id, mb);
            for (int i = 0; i < n; i++) assertTrue(ma[i].equals(mb[i], 1e-3f), "salto ao pular para " + k + " s da guarda (peça " + i + ")");
        }
    }

    @Test
    void damageWindowsSitInsideTheExecutionAndOnlyWhileTheTipSweepsTheFront() {
        GameLogic g = CombatTest.arena();
        Hero h = g.combat.hero;
        for (CombatDef.Attack a : g.combat.def.attacks.values()) {
            float[] ex = h.prof.moves.get(a.move()).phases.get("exec");
            assertTrue(a.activeFrom() >= ex[0] - 1e-4f && a.activeTo() <= ex[1] + 0.03f, a.move() + ": janela de dano fora da execução");
            h.layer.copyFrom(new ActionLayer(h.prof));
            h.layer.start(a.move());
            for (float t = a.activeFrom(); t <= a.activeTo() + 1e-4f; t += 0.005f) {
                float[] s = h.bladeAt(t, 0, 0, 0, 0, g.collision);
                double ang = Math.toDegrees(Math.atan2(s[3], s[5]));
                assertTrue(Math.abs(ang) <= 82, a.move() + " fere com a ponta a " + Math.round(ang) + "° em t=" + t + " (atrás/ao lado)");
            }
        }
    }

    @Test
    void everyBodyCarriesTheCombatKitWithoutClipping() {
        // a aparência não define classe: espada e broquel vão em qualquer corpo (mesmos pivôs de braço nos três)
        CombatDef d = def();
        vx.viewer.Composer comp = new vx.viewer.Composer(A, 1);
        comp.kit = d.heroKit;
        List<String> moves = List.of("slash_h", "slash_d", "slash_v", Hero.GUARD, "hit", "hit_cross_h", "hit_cross_d", "defeat");
        List<String> bad = new ArrayList<>();
        for (String preset : List.of("aventureiro", "exploradora", "maga")) {
            CharacterModel m = comp.build(comp.compose(vx.viewer.Appearance.preset(preset)), false);
            assertTrue(m.index.containsKey("weapon") && m.index.containsKey("shield") && !m.index.containsKey("bow"), preset + ": peças do kit");
            for (String mv : moves) {
                WeaponCheck.Report r = WeaponCheck.validate(m, mv);
                if (!r.ok()) bad.add(preset + ": " + r);
            }
            for (String seq : List.of("idle:0.3,slash_h!0.55,hit,idle:0.5", "idle:0.3,slash_h!" + f(d.attacks.get("slash_h").chainAt()) + ",slash_d!"
                    + f(d.attacks.get("slash_d").chainAt()) + ",slash_v,idle:0.5")) {
                WeaponCheck.Report r = WeaponCheck.validate(m, "seq:" + seq);
                if (!r.ok()) bad.add(preset + ": " + r);
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void theLiveLayerMatchesTheSequencerFrameByFrame() {
        CombatDef d = def();
        CharacterModel m = kit(d);
        Animator an = new Animator(m, 1f);
        CombatDef.Attack h = d.attacks.get("slash_h");
        float hitAt = 0.12f;
        String script = "idle:0.3,slash_h!" + f(h.chainAt()) + ",slash_d!" + f(hitAt) + ",hit,idle:1.0";
        Sequencer seq = new Sequencer(script, an);
        ActionLayer live = new ActionLayer(an.prof);
        // passos inteiros de 1/400 s: os instantes de troca caem exatamente num passo (sem arredondamento)
        int per = 400, kH = Math.round(0.3f * per), kD = kH + Math.round(h.chainAt() * per), kHit = kD + Math.round(hitAt * per);
        float dt = 1f / per;
        boolean sHit = false;
        Animator.Motion a = new Animator.Motion(), b = new Animator.Motion();
        for (int k = 0; k * dt < seq.total - 0.01f; k++) {
            float T = (float) k / per;
            if (k == kH) live.start("slash_h");
            if (k == kD) live.start("slash_d");
            if (k == kHit) { live.start("hit"); sHit = true; }
            seq.eval(T + 0.5f * dt, a);                                   // meio do passo: longe das fronteiras de segmento
            b.set(a.clip, T);
            live.eval(b, 0.5f * dt);
            String w = " em T=" + T;
            assertEquals(a.move, b.move, "golpe" + w);
            if (a.move != null) {
                assertEquals(a.moveT, b.moveT, 2e-3f, "tempo do golpe" + w);
                assertEquals(a.weight, b.weight, 2e-3f, "peso" + w);
                assertEquals(a.fromMove, b.fromMove, "origem" + w);
                assertEquals(a.fromMove2, b.fromMove2, "origem dupla" + w);
                if (a.fromMove != null) { assertEquals(a.fromT, b.fromT, 3e-3f, "tempo da origem" + w); assertEquals(a.xfade, b.xfade, 3e-3f, "mistura" + w); }
                if (a.fromMove2 != null) { assertEquals(a.fromT2, b.fromT2, 3e-3f, "tempo da origem dupla" + w); assertEquals(a.xfade2, b.xfade2, 3e-3f); }
            }
            live.update(dt);
        }
        assertTrue(sHit, "o roteiro não chegou ao dano");
    }
}
