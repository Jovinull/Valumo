package vx.viewer;

import vx.engine.Screenshot;
import vx.engine.Window;

import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;

/**
 * Ponto de entrada.
 *
 * <pre>
 *   (sem argumentos)             abre o visualizador interativo
 *   --snap chave=valor,...       captura offscreen de um estado; pode repetir. Chaves:
 *                                style=a|b|c|d|e  mode=scene|char|side|prop|lineup  char=N  clip=idle|walk|run|attack|hit|emote
 *                                t=segundos  yaw= pitch= dist= fov= ty= (câmera, graus)  myaw= (giro do modelo, graus)
 *                                hx= hz= hyaw= (herói na cena)  lift= (altura do alvo da câmera na cena)
 *                                neutral=1 notex=1 noshadow=1 noao=1 nopost=1 nofog=1 nohud=1 norm=1
 *                                size=LxA  out=arquivo.png
 *                                rodada 02: preset=dia|tarde|neutra  outline=0..3  fog=0..3  sil=1  expr=neutral|joy|determined|surprise
 *                                set=<conjunto do modo lineup>  quality=baixa|padrao|alta  tt=1 (girar)  move=1 (herói anda no clipe)
 *                                frames=N fps=F  (sequência: out vira prefixo_000.png ...; ver tools/anim.sh)
 *   --review2                    gera o pacote da rodada 02 em output/visual-review/round-02/
 *   --review3 [seções]           gera o pacote da rodada 03 em output/visual-review/round-03/
 *   --check [csv]                valida as interseções de todos os golpes (sem janela; rodada 04)
 *   --demo N                     abre a janela, percorre modos/direções automaticamente por N quadros e sai
 *   --review                     gera o pacote completo de revisão em output/visual-review/
 *   --bench [LxA]                mede o tempo de quadro da cena nas 4 direções (offscreen, sem vsync)
 *   --occlusion [rig golpe]      rosto coberto e arma escondida em projeção de câmera (rodada 05.1)
 *   --grip rig golpe [passo]     torção/flexão/giro do item na mão ao longo do golpe (rodada 05.1)
 *   --info                       imprime dados do contexto OpenGL e sai
 *   --custom-demo PASTA          roteiro do modo PERSONALIZACAO (teclas reais) com capturas por passo (rodada 07)
 *   --custom-bench ARQUIVO.md    custo de compor/malhar/enviar, cache, memória e vazamento (rodada 07)
 *   --editor                     criador de personagens do jogador (rodada 07.2: interface clicável)
 *   --editor-debug               editor de depuração da rodada 07 (modo PERSONALIZACAO, só teclado)
 *   --editor-script ROTEIRO SAIDA [FPS]  roteiro de entrada no criador (capturas/vídeo; janela oculta)
 *   --layout NOME                abre a composição assets/scenes/NOME.json (ex.: vila_e, vitrine_e) no modo interativo
 * </pre>
 */
public final class Main {
    /**
     * Roda um validador de linha de comando: 0 só se ele conferiu e aprovou. Uma exceção (asset ausente, perfil que não
     * carrega, golpe inexistente) vira diagnóstico de uma linha e saída 1 — nunca "ok" e nunca só a pilha.
     */
    static int validator(String name, java.util.function.BooleanSupplier run) {
        try {
            return run.getAsBoolean() ? 0 : 1;
        } catch (RuntimeException e) {
            Throwable c = e;
            StringBuilder why = new StringBuilder(String.valueOf(e.getMessage()));
            while (c.getCause() != null && c.getCause() != c) { c = c.getCause(); why.append(" ← ").append(c.getMessage()); }
            System.out.println(name + ": ERRO, nada foi aprovado — " + e.getClass().getSimpleName() + ": " + why);
            return 1;
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--occlusion")) {     // rosto coberto / arma escondida (rodada 05.1)
            System.exit(validator("occlusion", () -> OcclusionCheck.run(args)));
            return;
        }
        if (args.length > 0 && args[0].equals("--grip")) {         // sonda de empunhadura (rodada 05.1)
            if (args.length < 3) { System.out.println("grip: uso: --grip rig golpe [passo] (nada conferido)"); System.exit(1); }
            System.exit(validator("grip", () -> GripProbe.run(args)));
            return;
        }
        if (args.length > 0 && args[0].equals("--check-cabelos")) { // rodada 09.4: arma × cabeça composta, voxel a voxel
            System.exit(validator("check-cabelos", () -> HairWeaponCheck.run(args.length > 1 ? args[1] : null)));
            return;
        }
        if (args.length > 0 && args[0].equals("--check")) {        // validação geométrica, sem janela
            System.exit(validator("check", () -> Check.run(args.length > 1 ? args[1] : null)));
            return;
        }
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--layout")) World.layout = args[i + 1];   // 06.1
        boolean interactive = true;
        for (String a : args)
            if (a.equals("--snap") || a.equals("--info") || a.equals("--review") || a.equals("--review2") || a.equals("--review3") || a.equals("--bench")
                    || a.equals("--custom-demo") || a.equals("--custom-bench") || a.equals("--editor-script"))
                interactive = false;

        try (Window window = new Window(hasArg(args, "--editor") ? "Valumo - criar aventureiro" : "Valumo - prototipo visual", 1600, 900, interactive)) {
            if (hasArg(args, "--info")) {
                System.out.println("GL_VENDOR   = " + glGetString(GL_VENDOR));
                System.out.println("GL_RENDERER = " + glGetString(GL_RENDERER));
                System.out.println("GL_VERSION  = " + glGetString(GL_VERSION));
                System.out.println("GLSL        = " + glGetString(org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION));
                return;
            }
            try (App app = new App(window, Path.of(System.getProperty("vx.assets", "assets")))) {
                if (interactive) {
                    int demo = 0;
                    for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--demo")) demo = Integer.parseInt(args[i + 1]);
                    App.State st = new App.State();
                    st.style = 4;                        // abre na direção consolidada E
                    st.quality = App.Quality.ALTA;       // inspeção; P alterna
                    if (hasArg(args, "--editor")) {      // rodada 07.2: criador do jogador
                        Path assets = Path.of(System.getProperty("vx.assets", "assets"));
                        // rodada 09.4: som da interface (e a música da vila) no criador do jogador; teste de fumaça mudo
                        vx.engine.audio.Audio.start(assets, demo == 0 ? vx.engine.audio.Audio.Mode.DEVICE : vx.engine.audio.Audio.Mode.OFF);
                        vx.engine.audio.Audio.music(vx.engine.audio.Audio.musicChoice("vila_a"));
                        try (Creator c = new Creator(app, window, assets)) {
                            c.run(demo);
                        } finally {
                            vx.engine.audio.Audio.stop();
                        }
                        return;
                    }
                    if (hasArg(args, "--editor-debug")) {   // rodada 07: editor de depuração (texto, teclado)
                        st.mode = App.Mode.CUSTOMIZE;
                        st.appearance = Appearance.preset("aventureiro");
                    }
                    app.runInteractive(st, demo);
                    return;
                }
                for (int i = 0; i < args.length; i++) {
                    if (args[i].equals("--snap") && i + 1 < args.length) snap(app, args[i + 1]);
                    if (args[i].equals("--review")) new Review(app, Path.of("output/visual-review")).run();
                    if (args[i].equals("--review2")) new Review2(app, Path.of("output/visual-review/round-02"),
                            i + 1 < args.length && !args[i + 1].startsWith("--") ? args[i + 1] : "all").run();
                    if (args[i].equals("--review3")) new Review3(app, Path.of("output/visual-review/round-03"),
                            i + 1 < args.length && !args[i + 1].startsWith("--") ? args[i + 1] : "all").run();
                    if (args[i].equals("--bench")) app.bench(i + 1 < args.length ? args[i + 1] : "");
                    if (args[i].equals("--editor-script")) {                                   // rodada 07.2
                        Path assets = Path.of(System.getProperty("vx.assets", "assets"));
                        // rodada 09.4: mudo por padrão; -Dvx.audio=loopback -Dvx.audio.gravar=x.wav grava a sessão
                        vx.engine.audio.Audio.start(assets, vx.engine.audio.Audio.Mode.OFF);
                        vx.engine.audio.Audio.music(vx.engine.audio.Audio.musicChoice("vila_a"));
                        try (Creator c = new Creator(app, window, assets)) {
                            c.script(Path.of(args[i + 1]), Path.of(args[i + 2]), i + 3 < args.length && !args[i + 3].startsWith("--")
                                    ? Integer.parseInt(args[i + 3]) : 30);
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        } finally {
                            vx.engine.audio.Audio.stop();
                        }
                    }
                    try {                                                                      // rodada 07
                        if (args[i].equals("--custom-demo")) app.customDemo(Path.of(args[i + 1]));
                        if (args[i].equals("--custom-bench")) app.customBench(Path.of(args[i + 1]));
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            }
        }
    }

    private static boolean hasArg(String[] args, String name) {
        for (String a : args) if (a.equals(name)) return true;
        return false;
    }

    static void snap(App app, String spec) {
        App.State st = new App.State();
        int w = 1280, h = 720;
        String out = "output/tmp/snap.png";
        Float yaw = null, pitch = null, dist = null, fov = null, ty = null;
        int frames = 1;
        float fps = 30f;
        boolean move = false;
        String kit = null;                       // kit=DIR: equipamento da aparência composta (o do jogo: characters/r4/adventurer_shield)
        for (String kv : spec.split(",")) {
            String[] p = kv.split("=", 2);
            if (p.length != 2) throw new IllegalArgumentException("esperado chave=valor: " + kv);
            String v = p[1];
            switch (p[0]) {
                case "style" -> st.style = StyleProfile.KEYS.indexOf(v);
                case "mode" -> st.mode = switch (v) {
                    case "scene" -> App.Mode.SCENE;
                    case "side" -> App.Mode.SIDE_BY_SIDE;
                    case "prop" -> App.Mode.PROPORTIONS;
                    case "lineup" -> App.Mode.LINEUP;
                    default -> App.Mode.CHARACTER;
                };
                case "preset" -> st.preset = java.util.Arrays.asList(StyleProfile.E_PRESETS).indexOf(v);
                case "outline" -> st.outlineLevel = Integer.parseInt(v);
                case "fog" -> st.fogLevel = Integer.parseInt(v);
                case "sil" -> st.toggles.silhouette = v.equals("1");
                case "expr" -> st.expr = v;
                case "set" -> st.lineup = v;
                case "hero" -> st.heroDir = v;
                case "terrain" -> st.terrain = Integer.parseInt(v);
                case "ssaa" -> st.captureSsaa = Float.parseFloat(v);
                case "party" -> st.party = v.equals("1");
                case "act" -> st.act = v;
                case "kit" -> kit = v;
                case "lab" -> st.lab = v;
                case "far" -> st.terrainFar = v.equals("1");
                case "seq" -> st.seq = v.replace('+', ',');
                case "pernas" -> st.legs = v;
                case "salto" -> st.jump = v;
                case "speed" -> st.timeScale = Float.parseFloat(v);
                case "hold" -> st.actHold = Float.parseFloat(v);
                case "dbg" -> Actor.debug = v.equals("1");
                case "legacy" -> Animator.legacy = v.equals("1");
                case "layout" -> World.layout = v;
                // rodada 07: personagem composto em tempo de execução (preset + escolhas; null = do preset)
                case "custom" -> { st.appearance = Appearance.preset(v); if (st.mode == App.Mode.CHARACTER) st.mode = App.Mode.CUSTOMIZE; }
                case "rosto" -> st.appearance.face = v;
                case "olhos" -> st.appearance.eyes = v;                  // rodada 09.4
                case "pes" -> st.appearance.feet = v;                    // rodada 09.4
                case "cabelo" -> st.appearance.hair = v;
                case "corCabelo" -> st.appearance.hairColor = "#" + v;
                case "pele" -> st.appearance.skin = "#" + v;
                case "iris" -> st.appearance.iris = "#" + v;
                case "cobertura" -> st.appearance.cover = v;
                case "aparencia" -> st.appearance = app.loadAppearance(v);           // rodada 06: composição alternativa (vitrine)
                case "quality" -> st.quality = App.Quality.valueOf(v.toUpperCase());
                case "tt" -> st.turntable = v.equals("1");
                case "move" -> move = v.equals("1");
                case "frames" -> frames = Integer.parseInt(v);
                case "fps" -> fps = Float.parseFloat(v);
                case "char" -> st.character = Integer.parseInt(v);
                case "clip" -> st.clip = Animator.Clip.valueOf(v.toUpperCase());
                case "t" -> st.time = Float.parseFloat(v);
                case "yaw" -> yaw = (float) Math.toRadians(Float.parseFloat(v));
                case "pitch" -> pitch = (float) Math.toRadians(Float.parseFloat(v));
                case "dist" -> dist = Float.parseFloat(v);
                case "fov" -> fov = Float.parseFloat(v);
                case "ty" -> ty = Float.parseFloat(v);
                case "hx" -> st.heroX = Float.parseFloat(v);
                case "hz" -> st.heroZ = Float.parseFloat(v);
                case "hyaw" -> st.heroYaw = (float) Math.toRadians(Float.parseFloat(v));
                case "lift" -> st.camLift = Float.parseFloat(v);
                case "myaw" -> st.modelYaw = (float) Math.toRadians(Float.parseFloat(v));
                case "neutral" -> st.neutral = v.equals("1");
                case "notex" -> st.toggles.textures = !v.equals("1");
                case "noshadow" -> st.toggles.shadows = !v.equals("1");
                case "noao" -> st.toggles.ao = !v.equals("1");
                case "nopost" -> st.toggles.post = !v.equals("1");
                case "nofog" -> st.toggles.fog = !v.equals("1");
                case "nohud" -> st.hud = !v.equals("1");
                case "norm" -> st.normalized = v.equals("1");
                case "size" -> {
                    String[] wh = v.split("x");
                    w = Integer.parseInt(wh[0]);
                    h = Integer.parseInt(wh[1]);
                }
                case "out" -> out = v;
                default -> throw new IllegalArgumentException("chave desconhecida: " + p[0]);
            }
        }
        if (!java.util.Objects.equals(app.composer().kit, kit)) {   // cada captura começa do seu próprio equipamento
            app.composer().clear();
            app.composer().kit = kit;
        }
        app.prepareCamera(st);
        if (yaw != null) st.cam.yaw = yaw;
        if (pitch != null) st.cam.pitch = pitch;
        if (dist != null || ty != null) st.camAuto = false;
        if (dist != null) st.cam.distance = dist;
        if (fov != null) st.cam.fovDeg = fov;
        if (ty != null) st.cam.target.y = ty;
        if (frames <= 1) {
            Screenshot.save(app.capture(st, w, h), Path.of(out));
            System.out.println("snap: " + out);
            return;
        }
        // Sequência para vídeo: o tempo avança 1/fps por quadro; com move=1 o herói anda na cena na
        // velocidade casada com o clipe (a câmera o segue).
        String base = out.endsWith(".png") ? out.substring(0, out.length() - 4) : out;
        float t0 = st.time;
        boolean jointed = app.heroJointed(st);
        float speed = st.clip == Animator.Clip.RUN ? (jointed ? App.WALK_RUN[1] : 3.2f)
                : st.clip == Animator.Clip.WALK ? (jointed ? App.WALK_RUN[0] : 1.35f) : 0f;
        for (int i = 0; i < frames; i++) {
            st.time = t0 + i / fps;
            if (move && i > 0 && !Float.isNaN(st.heroX)) {
                st.heroX += (float) Math.sin(st.heroYaw) * speed / fps;
                st.heroZ += (float) Math.cos(st.heroYaw) * speed / fps;
            }
            Screenshot.save(app.capture(st, w, h), Path.of(String.format("%s_%03d.png", base, i)));
        }
        System.out.println("snap: " + frames + " quadros em " + base + "_###.png");
    }
}
