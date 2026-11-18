package vx.game;

import vx.engine.Window;
import vx.engine.audio.Audio;

import java.nio.file.Path;

/**
 * Rodada 08 — entrada do protótipo jogável.
 * <pre>
 *   (sem argumentos)               escolha de aparência → vila
 *   --demo N                       fecha depois de N quadros e imprime o FPS (teste de fumaça)
 *   --roteiro ROTEIRO SAIDA [FPS]  entrada por roteiro na fila da janela (janela oculta): capturas, vídeo, medição
 * </pre>
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
        // rodada 09: som no jogo interativo; roteiros e o teste de fumaça ficam mudos (nada sai no alto-falante de
        // quem roda a revisão) — -Dvx.audio=loopback -Dvx.audio.gravar=x.wav grava a mixagem em sincronia
        Audio.start(assets, script == null && demo == 0 ? Audio.Mode.DEVICE : Audio.Mode.OFF);
        try (Window window = new Window("Valumo", 1600, 900, script == null); Game game = new Game(window, assets)) {
            if (script != null) game.script(Path.of(script), Path.of(out), fps);
            else game.run(demo);
        } finally {
            Audio.stop();
        }
    }

    private GameMain() { }

}
