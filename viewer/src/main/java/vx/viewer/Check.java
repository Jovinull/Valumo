package vx.viewer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code viewer --check [saida.csv]}: roda {@link WeaponCheck} em todos os golpes de todos os heróis com
 * perfil de arma (E e estudos r4) e nos roteiros de transição, imprime o relatório e grava CSV.
 * Sai com código 1 se algum item passar da tolerância.
 */
final class Check {
    static final String[] RIGS = {"characters/e/adventurer", "characters/r4/adventurer_shield", "characters/r4/adventurer_longblade",
            "characters/e/ranger", "characters/e/mage", "characters/npc/cartografa",
            "characters/npc/kelyra", "characters/npc/indira", "characters/npc/suvia"};

    /** Roteiros de transição verificados (o golpe "attack" vira o padrão de cada perfil). */
    static final String[] SEQS = {"idle:0.4,walk:1.0,attack,idle:0.6", "run:1.0,attack,run:1.0", "walk:0.8,attack,walk:0.8",
            // rodada 05.1: golpe interrompido por HIT na preparação e no meio da execução (sem teleporte de arma)
            "idle:0.3,attack!0.25,hit,idle:0.5"};

    private Check() { }

    /** Auditoria: os ataques da rodada 03 (Animator.legacy) medidos com os mesmos volumes. */
    static boolean legacy() {
        Animator.legacy = true;
        for (String r : new String[]{"characters/e/adventurer", "characters/e/ranger", "characters/e/mage"}) {
            CharacterModel m = CharacterModel.loadSkeleton(Path.of("assets").resolve(r));
            System.out.println(WeaponCheck.validate(m, "clip:ATTACK"));
            System.out.println(WeaponCheck.validate(m, "clip:IDLE"));
        }
        Animator.legacy = false;
        return true;
    }

    /**
     * Rodada 05.1 — {@code viewer --check interrupcao}: corta o golpe padrão (e o combo da lâmina longa) a cada
     * 0,05 s e deixa o HIT assumir. Classifica cada corte: OK, ATRAVESSA (interseção na transição) ou SALTO
     * (teleporte da ponta). Relatório para a rodada de combate; não faz parte do portão canônico.
     */
    static boolean interrupt() {
        boolean noJump = true;
        for (String r : RIGS) {
            CharacterModel m = CharacterModel.loadSkeleton(Path.of("assets").resolve(r));
            Animator an = new Animator(m, 1f);
            if (an.prof == null) continue;
            if (!an.prof.moves.containsKey("hit")) continue;          // só personagens com reação a golpe
            for (String atk : new java.util.LinkedHashSet<>(List.of(an.prof.attack, "long_combo"))) {
                if (!an.prof.moves.containsKey(atk)) continue;
                Choreo.Move mv = an.prof.moves.get(atk);
                float d = an.moveDuration(atk), prepEnd = mv.phases.containsKey("prep") ? mv.phases.get("prep")[1] : 0f;
                StringBuilder line = new StringBuilder();
                int ok = 0, n = 0;
                for (float c = 0.05f; c < d - 0.05f; c += 0.05f) {
                    WeaponCheck.Report rep = WeaponCheck.validate(m, String.format(Locale.ROOT, "seq:idle:0.3,%s!%.2f,hit,idle:0.5", atk, c));
                    boolean jump = !rep.items.get("salto da ponta (continuidade)").ok();
                    noJump &= !jump;
                    n++;
                    if (rep.ok()) ok++;
                    line.append(String.format(Locale.ROOT, " %.2f%s", c, rep.ok() ? "·" : jump ? "S" : "X"));
                }
                System.out.printf(Locale.ROOT, "%s / %s (preparação até %.2f s): %d/%d cortes seguros%n  %s%n", m.name, atk, prepEnd, ok, n, line);
            }
        }
        System.out.println("legenda: · seguro, X atravessa o corpo na transição, S teleporte da ponta");
        System.out.println(noJump ? "interrupcao: nenhum teleporte" : "interrupcao: HÁ TELEPORTE");
        return noJump;
    }

    static boolean run(String csv) {
        if ("legacy".equals(csv)) return legacy();
        if ("interrupcao".equals(csv)) return interrupt();
        boolean all = true;
        List<String> rows = new ArrayList<>();
        rows.add("rig,animacao,item,pior_vox,tolerancia,instante_s,ok,amostras,passo_ponta_vox,ms");
        for (String r : RIGS) {
            Path dir = Path.of("assets").resolve(r);
            if (!Files.exists(dir.resolve("rig.json"))) continue;
            CharacterModel m = CharacterModel.loadSkeleton(dir);
            Animator an = new Animator(m, 1f);
            if (an.prof == null) continue;
            List<String> what = new ArrayList<>(an.prof.moves.keySet());
            for (String sq : SEQS) {
                if (sq.contains("hit") && !an.prof.moves.containsKey("hit")) continue;   // NPC sem gesto de HIT (rodada 06)
                what.add("seq:" + sq.replace("attack", an.prof.attack));
            }
            what.add("clip:HIT");                      // rodada 05: golpe recebido e comemoração também são validados
            what.add("clip:EMOTE");
            for (String w : what) {
                WeaponCheck.Report rep = WeaponCheck.validate(m, w);
                System.out.println(rep);
                all &= rep.ok();
                for (WeaponCheck.Item i : rep.items.values())
                    rows.add(String.format(Locale.ROOT, "%s,%s,%s,%.3f,%.2f,%.3f,%s,%d,%.2f,%.1f", r, w.replace(',', ' '), i.name, i.worst, i.tol,
                            i.at, i.ok(), rep.samples, rep.maxTipStep, rep.nanos / 1e6));
            }
        }
        if (csv != null) {
            try {
                Files.createDirectories(Path.of(csv).toAbsolutePath().getParent());
                Files.write(Path.of(csv), rows);
                System.out.println("check: csv " + csv);
            } catch (IOException e) {
                System.err.println("check: falha ao gravar " + csv + ": " + e.getMessage());
            }
        }
        System.out.println(all ? "check: TODOS OK" : "check: HÁ FALHAS");
        return all;
    }
}
