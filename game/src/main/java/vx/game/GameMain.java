package vx.game;

import vx.engine.Window;

import java.nio.file.Path;

/**
 * Rodada 08 — entrada do protótipo jogável.
 * <pre>
 *   (sem argumentos)               escolha de aparência → vila
 *   --demo N                       fecha depois de N quadros e imprime o FPS (teste de fumaça)
 *   --roteiro ROTEIRO SAIDA [FPS]  entrada por roteiro na fila da janela (janela oculta): capturas, vídeo, medição
 * </pre>
 * Som (rodada 09.1): -Dvx.som=dispositivo (padrão do jogo), mudo (padrão dos roteiros) ou loopback (roteiro grava o som
 * da própria aplicação em SAIDA/audio.wav, no relógio do jogo).
 */
public final class GameMain {
    public static void main(String[] args) throws Exception {
        Path assets = Path.of(System.getProperty("vx.assets", "assets"));
        int demo = 0;
        String script = null, out = null;
        int fps = 30;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--demo")) demo = Integer.parseInt(args[++i]);
            else if (args[i].equals("--roteiro")) {
                script = args[++i];
                out = args[++i];
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) fps = Integer.parseInt(args[++i]);
            }
        }
        if (script != null && System.getProperty("vx.som") == null) System.setProperty("vx.som", "mudo");   // roteiro não faz barulho
        if (script != null && System.getProperty("vx.prefs") == null) System.setProperty("vx.prefs", "-");  // nem lê nem grava preferências
        try (Window window = new Window("Valumo", 1600, 900, script == null); Game game = new Game(window, assets)) {
            if (script != null) game.script(Path.of(script), Path.of(out), fps);
            else game.run(demo);
        }
    }

    private GameMain() { }

}
