package vx.game;

/**
 * Rodada 09.1 — diretor de som do combate: depois de CADA passo lógico olha o que mudou no confronto e pede os efeitos
 * ao {@link Sfx}. Só lê: nada aqui escreve em {@link Combat}, {@link Hero}, {@link Enemy} ou {@link Player}, então o
 * som não altera dano, estados, tempos nem o resultado (CombatAudioTest compara a luta com e sem som).
 *
 * <table>
 *   <caption>Quando cada efeito toca</caption>
 *   <tr><td>golpe_ar_h/d/v</td><td>golpe em andamento: o pico do som cai no MEIO da janela de dano (lâmina mais rápida).
 *       Golpe interrompido antes disso: sem "fuu".</td></tr>
 *   <tr><td>golpe_acerto + fungo_dano</td><td>acerto confirmado pela lógica (um por golpe), no ponto de contato</td></tr>
 *   <tr><td>escudo_bloqueio</td><td>cabeçada bloqueada</td></tr>
 *   <tr><td>cabecada_acerto + heroi_dano</td><td>cabeçada que feriu o herói</td></tr>
 *   <tr><td>fungo_alerta</td><td>repouso/volta → alerta (percebeu o herói)</td></tr>
 *   <tr><td>fungo_preparo</td><td>entrou na preparação (o som dura a preparação); interrompida → o som para</td></tr>
 *   <tr><td>fungo_cabecada</td><td>a cabeçada sai (movimento; contato tem som próprio)</td></tr>
 *   <tr><td>fungo_derrota</td><td>morreu</td></tr>
 *   <tr><td>vitoria / derrota</td><td>resultado do confronto</td></tr>
 *   <tr><td>esquiva_recuo</td><td>começo da esquiva (09.3: todos os gestos, R2 e recuo; esquiva_rolar ficou sem uso)</td></tr>
 *   <tr><td>salto / pouso</td><td>rodada 09.2: saiu do chão / pousou depois de um salto ou queda</td></tr>
 *   <tr><td>fungo_desfaz</td><td>rodada 09.2: o corpo se desfaz em fragmentos ({@link DeathFx#BREAK} s depois da morte)</td></tr>
 * </table>
 * Rodada 09.2: um estado guardado POR INIMIGO (grupo); cada som do fungo sai na posição de quem o produziu.
 */
public final class CombatAudio {
    private final Sfx sfx;
    private Enemy.State[] lastState = new Enemy.State[0];
    private int[] windupVoice = new int[0];
    private boolean[] broke = new boolean[0];
    private int lastSwing, impactsSeen, lastDodges, lastJumps, lastLandings;
    private Combat.Outcome lastOutcome = Combat.Outcome.NONE;
    private boolean whooshPending;
    private String whooshCue;

    /** Já olhando o confronto como ele está: o que aconteceu antes não toca; o próximo passo já conta. */
    public CombatAudio(Sfx sfx, Combat c) {
        this.sfx = sfx;
        reset(c);
    }

    /** Esquece o passado (início, recomeço): o que já aconteceu não toca de novo. */
    public void reset(Combat c) {
        sfx.stopAll();
        int n = c.enemies.size();
        lastState = new Enemy.State[n];
        windupVoice = new int[n];
        broke = new boolean[n];
        for (int i = 0; i < n; i++) {
            lastState[i] = c.enemies.get(i).state;
            windupVoice[i] = -1;
            broke[i] = !c.enemies.get(i).alive();
        }
        lastSwing = c.hero.swing;
        lastDodges = c.hero.dodges;
        if (c.player != null) { lastJumps = c.player.jumps; lastLandings = c.player.landings; }
        impactsSeen = c.impacts.size();
        lastOutcome = c.outcome;
        whooshPending = false;
    }

    /** Depois de um passo lógico de {@code c}. */
    public void afterTick(Combat c) {
        long tick = c.ticks;
        Hero h = c.hero;
        if (lastState.length != c.enemies.size()) reset(c);                  // encontro trocado
        // rodada 09.2: esquiva, salto e pouso
        if (h.dodges != lastDodges) {
            lastDodges = h.dodges;
            // rodada 09.3: rolamento (R1/09.2) = som de rolar; impulso sem rolar (recuo, esquiva R2) = som do recuo
            String cue = "esquiva_recuo";                                  // rodada 09.3: a esquiva R2 é salto, não rolamento
            if (sfx.has(cue)) sfx.cue(cue, tick, 0f, 0f, 0f);
        }
        Player p = c.player;
        if (p != null) {
            if (p.jumps != lastJumps) { lastJumps = p.jumps; if (sfx.has("salto")) sfx.cue("salto", tick, 0f, 0f, 0f); }
            if (p.landings != lastLandings) { lastLandings = p.landings; if (sfx.has("pouso")) sfx.cue("pouso", tick, 0f, 0f, 0f); }
        }
        // golpe do herói: "fuu" no auge da lâmina
        if (h.swing != lastSwing) {
            lastSwing = h.swing;
            whooshPending = h.attack != null;
            whooshCue = h.attack == null ? null : "golpe_ar_" + h.attack.move().substring(h.attack.move().length() - 1);
            if (whooshCue != null && !sfx.has(whooshCue)) whooshCue = "golpe_ar_h";
        }
        if (whooshPending) {
            if (h.state != Hero.State.ATTACK || h.attack == null) whooshPending = false;          // interrompido antes do corte
            else {
                float mid = (h.attack.activeFrom() + h.attack.activeTo()) / 2f;
                if (h.layer.moveT() >= mid - sfx.peakTime(whooshCue)) {
                    sfx.cue(whooshCue, tick, 0f, 0f, 0f);
                    whooshPending = false;
                }
            }
        }
        // contatos confirmados pela lógica
        if (c.impacts.size() < impactsSeen) impactsSeen = 0;                 // recomeço limpou a lista
        for (int i = impactsSeen; i < c.impacts.size(); i++) {
            Combat.Impact im = c.impacts.get(i);
            switch (im.kind()) {
                case "acerto" -> {
                    sfx.cue("golpe_acerto", tick, im.at().x, im.at().y, im.at().z);
                    Enemy e = im.who() != null ? im.who() : c.enemy;
                    if (e.alive()) sfx.cue("fungo_dano", tick, e.x, e.y + 0.5f, e.z);
                }
                case "defesa" -> sfx.cue("escudo_bloqueio", tick, im.at().x, im.at().y, im.at().z);
                case "dano" -> {
                    sfx.cue("cabecada_acerto", tick, im.at().x, im.at().y, im.at().z);
                    sfx.cue("heroi_dano", tick, 0f, 0f, 0f);
                }
                default -> { }
            }
        }
        impactsSeen = c.impacts.size();
        // estados de cada fungo
        for (int i = 0; i < c.enemies.size(); i++) {
            Enemy e = c.enemies.get(i);
            if (e.state != lastState[i]) {
                if (lastState[i] == Enemy.State.WINDUP && e.state != Enemy.State.ATTACK && windupVoice[i] >= 0) {
                    sfx.stop(windupVoice[i]);                                // preparação interrompida (golpe, morte)
                }
                switch (e.state) {
                    case ALERT -> { if (lastState[i] == Enemy.State.IDLE || lastState[i] == Enemy.State.RETURN) sfx.cue("fungo_alerta", tick, e.x, e.y + 0.5f, e.z); }
                    case WINDUP -> windupVoice[i] = sfx.cue("fungo_preparo", tick, e.x, e.y + 0.5f, e.z);
                    case ATTACK -> sfx.cue("fungo_cabecada", tick, e.x, e.y + 0.5f, e.z);
                    case DEAD -> { sfx.cue("fungo_derrota", tick, e.x, e.y + 0.4f, e.z); broke[i] = false; }
                    default -> { }
                }
                if (e.state != Enemy.State.WINDUP) windupVoice[i] = -1;
                lastState[i] = e.state;
            }
            if (!e.alive() && !broke[i] && e.deadT >= DeathFx.BREAK) {         // rodada 09.2: o corpo se desfaz
                broke[i] = true;
                if (sfx.has("fungo_desfaz")) sfx.cue("fungo_desfaz", tick, e.x, e.y + 0.4f * e.scale, e.z);
            }
        }
        // resultado
        if (c.outcome != lastOutcome) {
            if (c.outcome == Combat.Outcome.VICTORY) sfx.cue("vitoria", tick, 0f, 0f, 0f);
            if (c.outcome == Combat.Outcome.DEFEAT) sfx.cue("derrota", tick, 0f, 0f, 0f);
            lastOutcome = c.outcome;
        }
    }
}
