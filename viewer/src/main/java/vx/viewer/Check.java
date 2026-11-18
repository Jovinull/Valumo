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
    static boolean interrupt() { return interrupt(0.05f, false); }

    /** Golpes que o combate da rodada 09 usa (a espada e broquel do herói): todos validados no passo fino. */
    public static final List<String> PLAYABLE = List.of("slash_h", "slash_d", "slash_v", "shield_block", "guard_hold");

    /**
     * Rodada 09 — {@code viewer --check interrupcao-fina}: corta cada golpe JOGÁVEL a cada {@code step} s (0,01) e
     * deixa a reação a dano assumir pela regra autorada do perfil. Retorna false se algum corte de golpe jogável
     * atravessar o corpo ou teleportar. A lâmina longa (não jogável) continua só no relatório histórico.
     */
    public static List<String> unsafeCuts(CharacterModel m, String move, float step, StringBuilder line) {
        Animator an = new Animator(m, 1f);
        List<String> bad = new ArrayList<>();
        float d = an.moveDuration(move);
        int n = Math.round((d - 0.02f) / step);
        for (int i = 1; i < n; i++) {
            float c = i * step;
            WeaponCheck.Report rep = WeaponCheck.validate(m, String.format(Locale.ROOT, "seq:idle:0.3,%s!%.3f,hit,idle:0.5", move, c));
            if (!rep.ok()) bad.add(String.format(Locale.ROOT, "%.2f", c) + " " + rep);
            if (line != null) line.append(rep.ok() ? "·" : !rep.items.get("salto da ponta (continuidade)").ok() ? "S" : "X");
        }
        return bad;
    }

    static boolean interruptFine() {
        boolean all = true;
        for (String r : List.of("characters/r4/adventurer_shield", "characters/e/adventurer")) {
            CharacterModel m = CharacterModel.loadSkeleton(Path.of("assets").resolve(r));
            Animator an = new Animator(m, 1f);
            for (String mv : PLAYABLE) {
                if (!an.prof.moves.containsKey(mv)) continue;
                StringBuilder line = new StringBuilder();
                List<String> bad = unsafeCuts(m, mv, 0.01f, line);
                all &= bad.isEmpty();
                System.out.printf(Locale.ROOT, "%s / %s: %d cortes inseguros (passo 0,01 s)%n  %s%n", m.name, mv, bad.size(), line);
                for (String b : bad) System.out.println("  " + b.replace("\n", "\n    "));
            }
        }
        System.out.println(all ? "interrupcao-fina: golpes jogáveis seguros" : "interrupcao-fina: HÁ CORTE INSEGURO");
        return all;
    }

    static boolean interrupt(float step, boolean unused) {
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

    /**
     * Rodada 09.2 — {@code viewer --check golpe-andando}: cada golpe jogável (e a guarda, o rolamento e o recuo) com as
     * PERNAS andando e correndo por baixo, para a frente, de lado (esquerda e direita) e de costas, em quatro fases do
     * passo, com a passada do perfil ágil. O tronco, os braços e a arma são os do golpe validado; o que muda são as pernas
     * — então o que se procura é lâmina/escudo atravessando perna em movimento.
     */
    static boolean movingAttacks() {
        boolean all = true;
        List<String> moves = List.of("slash_h", "slash_d", "slash_v", "guard_hold", "shield_block");
        for (String r : List.of("characters/r4/adventurer_shield", "characters/e/adventurer")) {
            CharacterModel m = CharacterModel.loadSkeleton(Path.of("assets").resolve(r));
            Animator an = new Animator(m, 1f);
            for (String mv : moves) {
                if (!an.prof.moves.containsKey(mv)) continue;
                int bad = 0, n = 0;
                StringBuilder line = new StringBuilder();
                for (String clip : List.of("walk", "run"))
                    for (int dir : new int[]{0, 90, -90, 180})
                        for (float ph : new float[]{0f, 0.25f, 0.5f, 0.75f}) {
                            WeaponCheck.Report rep = WeaponCheck.validate(m, String.format(Locale.ROOT, "seq:idle:0.2,%s,idle:0.4|pernas=%s:%.2f:%d:1.05", mv, clip, ph, dir));
                            n++;
                            if (!rep.ok()) { bad++; System.out.println("  " + rep.toString().replace("\n", "\n    ")); }
                            line.append(rep.ok() ? "·" : "X");
                        }
                all &= bad == 0;
                System.out.printf(Locale.ROOT, "%s / %s andando: %d/%d combinações com problema%n  %s%n", m.name, mv, bad, n, line);
            }
        }
        System.out.println(all ? "golpe-andando: tudo seguro" : "golpe-andando: HÁ PROBLEMA");
        return all;
    }

    /**
     * Rodada 09.2 — {@code viewer --check esquiva}: cortes golpe → esquiva/recuo dentro das janelas medidas e as saídas da
     * esquiva (golpe, reação, guarda) nos instantes em que o jogo as permite, nos dois kits, a cada 0,01 s.
     */
    /** Rodada 09.3 — gestos de esquiva do jogo: recuo e R2 (frente, esquerda, direita; decisão do proprietário). */
    static final List<String> DODGES = List.of("backstep", "dodge_f", "dodge_l", "dodge_r");

    static boolean dodges() {
        boolean all = true;
        java.util.Map<String, float[][]> win = new java.util.LinkedHashMap<>();
        win.put("slash_h", new float[][]{{0.01f, 0.27f}, {0.68f, 1.09f}});
        win.put("slash_d", new float[][]{{0.01f, 0.30f}, {0.64f, 0.76f}, {0.92f, 1.12f}});
        win.put("slash_v", new float[][]{{0.01f, 0.36f}, {0.53f, 1.19f}});
        win.put("guard_hold", new float[][]{{0.01f, 1.52f}});
        for (String r : List.of("characters/r4/adventurer_shield", "characters/e/adventurer")) {
            CharacterModel m = CharacterModel.loadSkeleton(Path.of("assets").resolve(r));
            Animator an = new Animator(m, 1f);
            for (var e : win.entrySet()) {
                if (!an.prof.moves.containsKey(e.getKey())) continue;
                int bad = 0, n = 0;
                for (float[] w : e.getValue())
                    for (float c = w[0]; c <= w[1] + 1e-4f; c += 0.01f)
                        for (String tg : DODGES) {                       // rodada 09.3: todos os gestos que o jogo usa
                            n++;
                            if (!WeaponCheck.validate(m, String.format(Locale.ROOT, "seq:idle:0.3,%s!%.3f,%s,idle:0.5", e.getKey(), c, tg)).ok()) bad++;
                        }
                all &= bad == 0;
                System.out.printf(Locale.ROOT, "%s / %s → esquiva (janelas medidas): %d inseguros em %d cortes%n", m.name, e.getKey(), bad, n);
            }
            // saídas da esquiva nos instantes que o jogo permite (combate_e.json: atacarDe, fim da invulnerabilidade, liberaDe)
            java.util.Map<String, float[]> from = java.util.Map.of("backstep", new float[]{0.26f, 0.21f, 0.30f},
                    "dodge_f", new float[]{0.44f, 0.33f, 0.46f}, "dodge_l", new float[]{0.44f, 0.33f, 0.46f}, "dodge_r", new float[]{0.44f, 0.33f, 0.46f});
            for (String src : DODGES)
                for (int ti = 0; ti < 3; ti++) {
                    String tg = List.of("slash_h", "hit", "guard_hold").get(ti);
                    int bad = 0, n = 0;
                    for (float c = from.get(src)[ti]; c < an.moveDuration(src) - 0.015f; c += 0.01f) {
                        n++;
                        if (!WeaponCheck.validate(m, String.format(Locale.ROOT, "seq:idle:0.3,%s!%.3f,%s,idle:0.5", src, c, tg)).ok()) bad++;
                    }
                    all &= bad == 0;
                    System.out.printf(Locale.ROOT, "%s / %s → %s (de %.2f s): %d inseguros em %d cortes%n", m.name, src, tg, from.get(src)[ti], bad, n);
                }
            List<String> entries = new ArrayList<>();
            for (String g : DODGES) for (String lc : List.of("idle:0.3", "walk:0.4", "run:0.4")) entries.add("seq:" + lc + "," + g + "," + lc.split(":")[0] + ":0.5");
            for (String s2 : entries) {
                WeaponCheck.Report rep = WeaponCheck.validate(m, s2);
                all &= rep.ok();
                System.out.println("  " + (rep.ok() ? "ok " : "FALHA ") + s2);
            }
        }
        System.out.println(all ? "esquiva: segura" : "esquiva: HÁ CORTE INSEGURO");
        return all;
    }

    static boolean run(String csv) {
        if ("golpe-andando".equals(csv)) return movingAttacks();
        if ("esquiva".equals(csv)) return dodges();
        if ("legacy".equals(csv)) return legacy();
        if ("interrupcao".equals(csv)) return interrupt();
        if ("interrupcao-fina".equals(csv)) return interruptFine();
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
