package vx.game;

import vx.engine.audio.Audio;
import vx.viewer.World;

/**
 * Rodada 09.4 — o que o jogo faz soar. Só traduz fatos do jogo em EVENTOS semânticos do catálogo
 * (assets/audio/sons.json); arquivos, variação, limites e volumes ficam no {@code vx.engine.audio}.
 * <ul>
 *   <li>pisada: cada vez que um pé começa o apoio ({@link Player#steps}), com a superfície sob o herói
 *       (grama/floresta → grama; caminho → terra; areia → areia; rocha → pedra) e o passo (andar/correr);</li>
 *   <li>interface: ações do {@link GameLogic} (começar, pausar, conversar, escolher…);</li>
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
    private final boolean log = Boolean.getBoolean("vx.audio.log");     // relatório: o que tocou, quando e onde
    String lastEvent;                                                    // testes
    private final org.joml.Vector3f eye = new org.joml.Vector3f();

    GameAudio(GameLogic logic) {
        this.logic = logic;
        lastSteps = logic.player.steps;
        logic.onAction = this::action;
    }

    /** Ação do jogador → som de interface (null = ação muda). */
    static String uiEvent(String action) {
        return switch (action) {
            case "comecar", "criador" -> "ui.confirmar";
            case "pausar", "trocar-aparencia", "fala-inicio" -> "ui.abrir";
            case "continuar", "fala-fim", "sair", "indisponivel" -> "ui.cancelar";
            case "escolha", "continuar-fala" -> "ui.selecionar";
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
                    logic.player.z, World.surfaceAt(logic.player.x, logic.player.z));
    }

    void tick(float dt) {
        Player p = logic.player;
        if (p.steps != lastSteps) {
            lastSteps = p.steps;
            emit(stepEvent(World.surfaceAt(p.x, p.z), p.stepRun));
        }
        Audio.music(music);
        // ambiente: cama de vento + água do lago no lugar do lago; ouvinte na câmera, olhando para o pivô (o herói)
        Audio.ambient("vento", null);
        Audio.ambient("lago", World.pondCenter());
        FollowCamera c = logic.cam;
        c.position(eye);
        float fx = c.pivot.x - eye.x, fy = c.pivot.y - eye.y, fz = c.pivot.z - eye.z, l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (l > 1e-5f) Audio.listener(eye.x, eye.y, eye.z, fx / l, fy / l, fz / l);
        Audio.duck(switch (logic.state) {
            case PAUSE -> 0.45f;
            case DIALOG -> 0.7f;
            default -> 1f;
        });
        Audio.update(dt);
    }
}
