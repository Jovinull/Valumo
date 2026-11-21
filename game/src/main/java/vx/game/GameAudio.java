package vx.game;

import vx.engine.audio.Audio;
import vx.game.quest.QuestSystem;
import vx.viewer.World;

/**
 * Rodada 09.4 — o que o jogo faz soar. Só traduz fatos do jogo em EVENTOS semânticos do catálogo
 * (assets/audio/sons.json); arquivos, variação, limites e volumes ficam no {@code vx.engine.audio}.
 * <ul>
 *   <li>pisada: cada vez que um pé começa o apoio ({@link Player#steps}), com a superfície sob o herói
 *       (grama/floresta → grama; caminho → terra; areia → areia; rocha → pedra) e o passo (andar/correr);</li>
 *   <li>interface: ações do {@link GameLogic} (começar, pausar, conversar, escolher, diário, salvar…);</li>
 *   <li>missão (rodada 10): aceitar, objetivo cumprido, marco aceso e recompensa ({@link #questEvent});</li>
 *   <li>música: a da vila desde a escolha de aparência, sem reiniciar; abafada na pausa e na conversa;</li>
 *   <li>ambiente: vento (cama) e água do lago (posicional, no centro do lago); ouvinte na CÂMERA, olhando para o herói —
 *       o mesmo dos efeitos do combate (um ouvinte só por contexto, ADR 0023).</li>
 * </ul>
 */
final class GameAudio {
    static final String DEFAULT_MUSIC = "vila_a";
    private final GameLogic logic;
    private int lastSteps;
    private final String music = Audio.musicChoice(DEFAULT_MUSIC);
    /**
     * Rodada 11.1 — música do menu principal: a outra faixa aprovada da vila ({@code vila_b}), para a entrada não repetir a
     * da aventura; {@code -Dvx.musica.menu=ID|nenhuma} troca. Mesmo dispositivo, mesmo catálogo (ADR 0023).
     */
    static final String MENU_MUSIC = "vila_b";
    private final String menuMusic = menuMusic();

    static String menuMusic() {
        String m = System.getProperty("vx.musica.menu", System.getProperty("vx.musica", MENU_MUSIC));
        return m == null || m.equals("nenhuma") ? null : m;
    }
    private final boolean log = Boolean.getBoolean("vx.audio.log");     // relatório: o que tocou, quando e onde
    String lastEvent;                                                    // testes
    private final org.joml.Vector3f eye = new org.joml.Vector3f();

    GameAudio(GameLogic logic) {
        this.logic = logic;
        lastSteps = logic.player.steps;
        logic.onAction = this::action;
        logic.onQuest = this::quest;
    }

    private QuestSystem.Notice lastNotice;

    /**
     * Rodada 10 — mudança de missão → som (null = mudo): aceitar, objetivo cumprido, marco aceso e recompensa. O objetivo
     * cumprido NO MESMO uso do marco não toca de novo (o som do marco já diz).
     */
    static String questEvent(QuestSystem.Notice n, QuestSystem.Notice before) {
        return switch (n.kind()) {
            case ACEITA -> "missao.aceita";
            case OBJETO -> "missao.sinal";
            case OBJETIVO -> before != null && before.kind() == QuestSystem.NoticeKind.OBJETO && n.objective().equals(before.objective())
                    ? null : "missao.objetivo";
            case RECOMPENSA -> "missao.recompensa";
            default -> null;
        };
    }

    private void quest(QuestSystem.Notice n) {
        String e = questEvent(n, lastNotice);
        lastNotice = n;
        if (e != null) emit(e);
    }

    /** Ação do jogador → som de interface (null = ação muda). */
    static String uiEvent(String action) {
        if (action.startsWith("espaco:")) return "ui.selecionar";        // rodada 13: um espaço da mochila
        return switch (action) {
            case "comecar", "criador", "continuar-jogo" -> "ui.confirmar";
            case "pausar", "trocar-aparencia", "fala-inicio", "diario", "diario-pausa" -> "ui.abrir";
            case "continuar", "fala-fim", "sair", "indisponivel", "fechar-diario", "fala-recusar", "objeto-usar" -> "ui.cancelar";
            case "escolha", "continuar-fala", "rastreador" -> "ui.selecionar";
            case "salvo" -> "ui.salvar";                                // o automático é mudo: sai junto com o som da missão
            case "descoberta" -> "missao.objetivo";                     // rodada 12: um lugar descoberto (o som aprovado de objetivo)
            case "inventario" -> "ui.abrir";                              // rodada 13: mochila (os sons aprovados da interface)
            case "fechar-inventario", "item-indisponivel" -> "ui.cancelar";
            case "item-usar" -> "ui.confirmar";
            case "item-coletado" -> "missao.recompensa";                  // um item recebido (o som aprovado de recompensa)
            default -> null;
        };
    }

    /** Superfície do relevo + passo → evento de pisada. */
    static String stepEvent(int surface, boolean run) {
        String s = switch (surface) {
            case World.SURF_GRASS, World.SURF_FOREST -> "grama";
            case World.SURF_SAND -> "areia";
            case World.SURF_ROCK -> "pedra";
            default -> "terra";                                           // caminho
        };
        return "passo." + s + (run ? ".correr" : ".andar");
    }

    private void action(String a) {
        String e = uiEvent(a);
        if (e != null) emit(e);
    }

    private void emit(String e) {
        lastEvent = e;
        Audio.event(e);
        if (log && Audio.get() != null)
            System.out.printf("som t=%.3f %s herói (%.2f, %.2f) superfície %d%n", Audio.get().time(), e, logic.player.x,
                    logic.player.z, World.surfaceAt(logic.scene.field, logic.player.x, logic.player.z));
    }

    void tick(float dt) {
        Player p = logic.player;
        if (p.steps != lastSteps && logic.state != GameLogic.State.MENU) {
            lastSteps = p.steps;
            emit(stepEvent(World.surfaceAt(logic.scene.field, p.x, p.z), p.stepRun));   // rodada 11: o relevo da cena
        }
        Audio.music(logic.state == GameLogic.State.MENU ? menuMusic : music);
        // ambiente: cama de vento + água do lago no lugar do lago; ouvinte na câmera, olhando para o pivô (o herói)
        Audio.ambient("vento", null);
        float[] pond = logic.scene.field.pondCenter();                     // rodada 11: região sem lago = sem som de lago
        if (pond != null) Audio.ambient("lago", pond);
        FollowCamera c = logic.cam;
        c.position(eye);
        float fx = c.pivot.x - eye.x, fy = c.pivot.y - eye.y, fz = c.pivot.z - eye.z, l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (l > 1e-5f) Audio.listener(eye.x, eye.y, eye.z, fx / l, fy / l, fz / l);
        Audio.duck(switch (logic.state) {
            case PAUSE, JOURNAL -> 0.45f;
            case DIALOG -> 0.7f;
            default -> 1f;
        });
        Audio.update(dt);
    }
}
