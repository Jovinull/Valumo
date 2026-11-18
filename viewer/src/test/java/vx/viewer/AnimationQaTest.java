package vx.viewer;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 05.1 — o que o validador espacial NÃO via: orientação do item na mão, rosto coberto em projeção de
 * câmera, interrupção de golpe por HIT e movimento secundário. Cada teste nasce de um defeito real da rodada 05
 * (guarda de duas mãos escondendo a lâmina e passando diante do rosto; arco "parado no ar" durante o HIT).
 */
class AnimationQaTest {
    static final String[] RIGS = {"characters/e/adventurer", "characters/e/ranger", "characters/e/mage",
            "characters/r4/adventurer_shield", "characters/r4/adventurer_longblade"};

    /** Golpes com rosto coberto ACEITO, com justificativa em docs/validation/problemas-conhecidos.md. */
    static final Set<String> FACE_EXCEPTIONS = Set.of(
            "bow_shot"   // a linha de mira passa diante do rosto vista do lado do alvo (limitação da proporção)
    );

    static CharacterModel rig(String r) { return CharacterModel.loadSkeleton(Path.of("assets").resolve(r)); }

    @Test
    void bowFollowsTheForearmDuringHit() {
        // rodada 05: o arco ficava parado no mundo enquanto o antebraço girava 50° em volta dele
        GripProbe.Stats s = GripProbe.probe(rig("characters/e/ranger"), "clip:HIT", "bow", "farm_l", 1f, false);
        assertFalse(s.frozen(), "arco parado no mundo durante o HIT");
        assertTrue(s.maxWrist() < 25f, "punho torce " + s.maxWrist() + "° em relação à empunhadura inicial do HIT");
    }

    @Test
    void noHeldItemStaysFrozenWhileTheArmTurnsInHit() {
        for (String r : RIGS) {
            CharacterModel m = rig(r);
            Animator an = new Animator(m, 1f);
            if (an.prof.rightItem != null && m.index.containsKey(an.prof.rightItem))
                assertFalse(GripProbe.probe(m, "clip:HIT", an.prof.rightItem, "farm_r", 1f, false).frozen(), r + " direita");
            if (an.prof.leftItem != null && m.index.containsKey(an.prof.leftItem))
                assertFalse(GripProbe.probe(m, "clip:HIT", an.prof.leftItem, "farm_l", 1f, false).frozen(), r + " esquerda");
        }
    }

    @Test
    void weaponsDoNotCoverTheFaceInCanonicalMoves() {
        List<String> bad = new ArrayList<>();
        for (String r : RIGS) {
            CharacterModel m = rig(r);
            Animator an = new Animator(m, 1f);
            for (String mv : an.prof.moves.keySet()) {
                if (FACE_EXCEPTIONS.contains(mv)) continue;
                OcclusionCheck.Result res = OcclusionCheck.run(m, mv);
                if (!res.faceOk()) bad.add(res.toString());
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void longBladeTwoHandGuardKeepsTheBladeVisibleAndOffTheFace() {
        OcclusionCheck.Result r = OcclusionCheck.run(rig("characters/r4/adventurer_longblade"), "guard_two_hands");
        assertTrue(r.faceOk(), r.toString());
        for (OcclusionCheck.ViewStat v : r.views())
            if (Math.abs(v.yaw()) <= 40f)        // câmeras de frente e 3/4: a lâmina nunca some atrás do corpo
                assertTrue(v.minVisible() >= 0.6f, "lâmina escondida na câmera " + v.yaw() + "°: " + r);
    }

    @Test
    void interruptionByHitNeverTeleportsAndIsSafeWhereGuaranteed() {
        // Sem sistema de combate (fora do escopo). Garantias atuais, medidas por viewer --check interrupcao:
        //  1. NENHUM corte, em nenhum instante de nenhum golpe, faz a ponta da arma teleportar;
        //  2. cortar na PREPARAÇÃO é sempre seguro (o golpe rebobina pela própria trajetória);
        //  3. arco e cajado são seguros em qualquer instante.
        // As janelas inseguras da continuação da espada e do meio do combo da lâmina longa estão em
        // docs/validation/problemas-conhecidos.md (exigem saídas de interrupção autoradas: rodada de combate).
        List<String> bad = new ArrayList<>();
        for (String r : RIGS) {
            CharacterModel m = rig(r);
            Animator an = new Animator(m, 1f);
            for (String atk : new java.util.LinkedHashSet<>(List.of(an.prof.attack, "long_combo"))) {
                if (!an.prof.moves.containsKey(atk)) continue;
                float d = an.moveDuration(atk), prepEnd = an.prof.moves.get(atk).phases.get("prep")[1];
                boolean ranged = r.endsWith("ranger") || r.endsWith("mage");
                for (float c = 0.05f; c < d - 0.05f; c += 0.05f) {
                    String seq = String.format(java.util.Locale.ROOT, "seq:idle:0.3,%s!%.2f,hit,idle:0.5", atk, c);
                    WeaponCheck.Report rep = WeaponCheck.validate(m, seq);
                    if (!rep.items.get("salto da ponta (continuidade)").ok()) bad.add("TELEPORTE " + rep);
                    else if ((ranged || c <= prepEnd) && !rep.ok()) bad.add(rep.toString());
                }
            }
        }
        assertTrue(bad.isEmpty(), bad.size() + " cortes fora da garantia:\n" + String.join("\n", bad));
    }

    @Test
    void playableSwordAndShieldMovesAreSafeWhenInterruptedAtAnyInstant() {
        // Rodada 09: o combate interrompe golpes de verdade. Cada golpe jogável é cortado a cada 0,01 s e a reação a
        // dano assume pela regra autorada do perfil (rebobina na preparação/execução, embalo depois, saída autorada
        // logo após a execução do horizontal e do diagonal). Nenhum corte pode atravessar o corpo nem teleportar.
        CharacterModel m = rig("characters/r4/adventurer_shield");
        List<String> bad = new ArrayList<>();
        for (String mv : Check.PLAYABLE) bad.addAll(Check.unsafeCuts(m, mv, 0.01f, null));
        assertTrue(bad.isEmpty(), bad.size() + " cortes inseguros:\n" + String.join("\n", bad));
    }

    @Test
    void interruptionWindowsPickTheAuthoredExitOnlyRightAfterTheCut() {
        Animator an = new Animator(rig("characters/r4/adventurer_shield"), 1f);
        Choreo.Move h = an.prof.moves.get("slash_h");
        assertTrue(h.cutRate(0.20f) < 0, "na preparação o golpe rebobina");
        assertTrue(h.cutRate(0.40f) < 0, "na execução o golpe rebobina (embalo atravessaria o corpo)");
        assertTrue(h.cutRate(0.80f) > 0, "na recuperação o golpe segue em frente até a guarda");
        assertNull(h.exitFor("hit", 0.40f));
        assertEquals("hit_cross_h", h.exitFor("hit", 0.55f), "logo após o corte: saída autorada do lado da espada");
        assertNull(h.exitFor("hit", 0.80f));
        assertEquals("hit_cross_d", an.prof.moves.get("slash_d").exitFor("hit", 0.56f));
        // o Sequencer (validador e vídeos) usa a mesma regra que o jogo
        Sequencer seq = new Sequencer("idle:0.3,slash_h!0.55,hit,idle:0.5", an);
        Animator.Motion mo = seq.eval(0.3f + 0.55f + 0.20f, new Animator.Motion());
        assertEquals("hit_cross_h", mo.move);
    }

    @Test
    void longBladeKeepsItsHistoricalInterruptionRecordUntilItBecomesPlayable() {
        // Rodada 09: a lâmina longa NÃO é arma jogável; o problema histórico continua medido e documentado
        // (problemas-conhecidos.md). Se alguém mexer no combo, este número muda e o documento precisa mudar junto.
        CharacterModel m = rig("characters/r4/adventurer_longblade");
        Animator an = new Animator(m, 1f);
        float d = an.moveDuration("long_combo");
        int unsafe = 0;
        for (float c = 0.05f; c < d - 0.05f; c += 0.05f)
            if (!WeaponCheck.validate(m, String.format(java.util.Locale.ROOT, "seq:idle:0.3,long_combo!%.2f,hit,idle:0.5", c)).ok()) unsafe++;
        assertEquals(18, unsafe, "cortes inseguros da lâmina longa (histórico: 18/47)");
    }

    @Test
    void secondaryPartsMoveDuringActionsDeterministically() {
        CharacterModel m = rig("characters/e/adventurer");
        Animator an = new Animator(m, 1f);
        Animator.Pose p1 = an.newPose(), p2 = an.newPose();
        Animator.Motion mo = new Animator.Motion();
        Matrix4f id = new Matrix4f();
        int pack = m.index.get("pack"), scarf = m.index.get("scarf");
        float maxPack = 0, maxScarf = 0;
        for (float t = 0; t < an.moveDuration("combo"); t += 1f / 60f) {
            mo.set(Animator.Clip.IDLE, t); mo.move = "combo"; mo.moveT = t; mo.weight = 1f;
            an.evaluate(mo, p1, id, null);
            an.evaluate(mo, p2, id, null);
            for (int k = 0; k < p1.v.length; k++) assertEquals(p1.v[k], p2.v[k], 1e-6f, "pose não determinística em t=" + t);
            float px = p1.v[pack * 9], sx = p1.v[scarf * 9];
            assertTrue(px >= -1e-4f && px <= 0.16f + 1e-4f, "mochila entraria nas costas: rx=" + px);
            assertTrue(sx <= 1e-4f && sx >= -0.45f - 1e-4f, "cachecol entraria no peito: rx=" + sx);
            maxPack = Math.max(maxPack, Math.abs(px));
            maxScarf = Math.max(maxScarf, Math.abs(sx) + Math.abs(p1.v[scarf * 9 + 2]));
        }
        assertTrue(maxPack > 0.02f, "mochila sem reação ao golpe");
        assertTrue(maxScarf > 0.02f, "cachecol sem reação ao golpe");
    }
}
