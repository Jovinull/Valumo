package vx.viewer;

import vx.engine.Screenshot;
import vx.engine.Window;
import vx.engine.Window.InputEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 07.2 — criador de personagens do jogador (o que {@code ./gradlew editor} abre). Junta a cena dedicada
 * ({@code assets/scenes/criador_e.json}), o herói composto de verdade, a interface clicável e as miniaturas
 * reais. O modo PERSONALIZACAO da rodada 07 continua como ferramenta de depuração ({@code --editor-debug}).
 *
 * <p>Fluxo de cada quadro: eventos da fila da janela → controlador (sobre o layout que está na tela) →
 * resultados da composição assíncrona (envio à GPU) → novo layout → cena + interface.
 */
public final class Creator implements AutoCloseable {
    private final App app;
    private final Window window;
    public final App.State st = new App.State();
    public final CreatorState cs = new CreatorState();
    public final CreatorCamera cam = new CreatorCamera();
    public final AsyncComposer async;
    public final CreatorController ctl;
    public final CreatorView view = new CreatorView();
    public final CreatorThumbs thumbs;
    public char variant = 'A';
    public CreatorLayout layout;
    private final Map<String, Appearance> bySig = new HashMap<>();
    private String shownSig;
    private final String prevLayout = World.layout;
    /** Ângulo da câmera definido no cenário ("camera": {"yaw": graus} em criador_e.json). */
    private final float sceneYaw;

    public Creator(App app, Window window, Path assets) {
        this.app = app;
        this.window = window;
        World.layout = "criador_e";
        st.mode = App.Mode.SCENE;
        st.style = 4;
        st.hud = false;
        st.quality = App.Quality.PADRAO;
        st.cam.fovDeg = 30f;
        st.camSet = true;
        float yaw = 0f;
        try {
            var doc = com.google.gson.JsonParser.parseString(Files.readString(assets.resolve("scenes/criador_e.json"))).getAsJsonObject();
            if (doc.has("camera")) yaw = (float) Math.toRadians(doc.getAsJsonObject("camera").get("yaw").getAsFloat());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("assets/scenes/criador_e.json ausente", e);
        }
        sceneYaw = yaw;
        async = new AsyncComposer(assets, 10);
        async.evicted = (sig, m) -> app.forget("custom:" + sig);
        thumbs = new CreatorThumbs(app, async);
        ctl = new CreatorController(cs, async.catalog(), cam, a -> {
            bySig.put(a.signature(), a.copy());
            async.request(a);
        });
        ctl.sound = vx.engine.audio.Audio::event;                        // rodada 09: mudo se ninguém abriu o áudio
        app.overlay = (w, h) -> { if (layout != null) view.draw(layout, ctl, thumbs, async, h); };
    }

    /** Rodada 08: abre já com uma aparência (vinda do jogo). */
    public void start(Appearance a, String name) {
        cs.appearance = a.copy();
        cs.name = name == null ? "" : name;
        bySig.put(a.signature(), a.copy());
        async.request(a);
    }

    /** Um quadro de lógica: eventos, resultados prontos, layout e câmera para o tamanho (w, h). */
    public void frame(List<InputEvent> events, float dt, int w, int h) {
        vx.engine.audio.Audio.update(dt);
        ctl.time += dt;
        st.time += dt;
        if (layout == null || layout.w != w || layout.h != h) layout = build(w, h);
        ctl.layout = layout;
        ctl.handle(events);
        async.poll();                                     // envio do personagem pronto (thread do GL)
        thumbs.pump(1);                                   // uma miniatura por quadro: sem picos somados
        String sig = async.shown;
        if (sig != null && !sig.equals(shownSig)) {
            CharacterModel m = async.model(sig);
            if (m != null) {
                app.injected.put("custom:" + sig, m);
                st.appearance = bySig.get(sig);
                shownSig = sig;
            }
        }
        layout = build(w, h);
        ctl.layout = layout;
        thumbs.update(layout, cs);
        // câmera: órbita suave do criador; o herói fica no centro da área livre
        if (cs.showcase) cam.tYaw -= dt * 0.3f;
        cam.update(dt);
        // o herói gira (arrasto); a câmera fica no ângulo composto da cena
        st.cam.yaw = sceneYaw;
        st.heroYaw = sceneYaw - cam.yaw;
        st.cam.pitch = cam.pitch;
        st.cam.distance = cam.dist;
        st.camLift = cam.height;
        float n = layout.scene == null ? 0 : (layout.scene.x() + layout.scene.w() / 2 - w / 2f) / (w / 2f);
        st.camSide = -n * cam.dist * (float) Math.tan(Math.toRadians(st.cam.fovDeg) / 2) * ((float) w / h);
        if (st.clip != cs.pose) {
            st.clip = cs.pose;
            st.clipStart = st.time;
        }
        st.expr = cs.expr;
    }

    private CreatorLayout build(int w, int h) {
        return new CreatorLayout(variant, w, h, cs, async.catalog(), CreatorView.measure());
    }

    public boolean idle() { return !async.busy() && thumbs.idle() && shownSig != null && shownSig.equals(async.wanted); }

    // ------------------------------------------------------------------ janela interativa

    public void run() { run(0); }

    /** Laço da janela; maxFrames > 0 fecha depois de N quadros (teste de fumaça) e imprime o FPS médio. */
    public void run(int maxFrames) {
        double last = window.time(), start = last;
        int frames = 0;
        while (!window.shouldClose() && !cs.finished && (maxFrames <= 0 || frames < maxFrames)) {
            frames++;
            window.poll();
            double now = window.time();
            float dt = (float) Math.min(0.1, now - last);
            last = now;
            int w = window.width(), h = window.height();
            if (w <= 0 || h <= 0) { window.swap(); continue; }
            for (InputEvent e : window.events())
                if (e.kind() == InputEvent.Kind.KEY && e.code() == GLFW_KEY_F12) shot(Path.of("output/visual-review/manual"), w, h);
            frame(window.events(), dt, w, h);
            app.renderFrame(st, null, w, h, st.quality.ssaa, Math.min(1f, dt * 12f));
            window.swap();
        }
        if (maxFrames > 0)
            System.out.printf("criador: janela %dx%d, %d quadros, %.1f FPS medio, composicoes %d (descartadas %d), miniaturas %d%n",
                    window.width(), window.height(), frames, frames / Math.max(1e-6, window.time() - start), async.completed,
                    async.discarded, thumbs.rendered);
    }

    private int shots;

    private void shot(Path dir, int w, int h) {
        try {
            Path out = dir.resolve(String.format("criador-%03d.png", ++shots));
            Screenshot.save(app.capture(st, w, h), out);
            System.out.println("captura: " + out);
        } catch (RuntimeException e) {
            System.err.println("captura falhou: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ roteiro (capturas e vídeo com entrada real)

    /**
     * Executa um roteiro de entrada: os eventos são postos na MESMA fila da janela ({@link Window#inject}) e
     * passam pelo mesmo controlador do uso interativo. Não é automação do sistema operacional (sessão Wayland,
     * sem xdotool/ydotool): o cursor do vídeo é desenhado pela própria interface na posição dos eventos.
     *
     * <p>Comandos (um por linha; '#' comenta): {@code size L A}, {@code variant A|B}, {@code wait s},
     * {@code move alvo [s]}, {@code click alvo [s]}, {@code drag dx dy [s]}, {@code wheel n [alvo]},
     * {@code key NOME}, {@code type texto}, {@code settle}, {@code shot nome}, {@code record on|off},
     * {@code saves limpar}. Alvo = id de controle ("hair:cabelo.curto"), "scene", "options" ou "x,y" (0–1).
     */
    public void script(Path file, Path out, int fps) throws IOException {
        vx.engine.audio.Audio.recording(false);                          // rodada 09: grava áudio só com record on
        List<String> lines = Files.readAllLines(file);
        Files.createDirectories(out);
        int w = 1920, h = 1080;
        float dt = 1f / fps, cx = w * 0.5f, cy = h * 0.55f;
        boolean record = false;
        int frameNo = 0;
        view.drawCursor = true;
        ctl.saveDir = out.resolve("aparencias");
        List<InputEvent> evs = new ArrayList<>();
        frame(evs, dt, w, h);
        settle(w, h, dt);
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+", 3);
            switch (t[0]) {
                case "size" -> {
                    w = Integer.parseInt(t[1]);
                    h = Integer.parseInt(t[2]);
                    layout = null;
                    frame(List.of(), 0f, w, h);
                }
                case "variant" -> { variant = t[1].charAt(0); layout = null; frame(List.of(), 0f, w, h); }
                case "saves" -> {
                    if (Files.exists(ctl.saveDir)) try (var fs = Files.list(ctl.saveDir)) {
                        for (Path p : fs.toList()) Files.delete(p);   // só a pasta do roteiro (output/…/aparencias)
                    }
                    ctl.refreshSaves();
                }
                case "perf" -> {
                    if (t[1].equals("on")) { perf = new ArrayList<>(); latencies = new ArrayList<>(); }
                    else { report(out.resolve("desempenho.txt")); perf = null; }
                }
                case "record" -> {
                    record = t[1].equals("on");
                    vx.engine.audio.Audio.recording(record);                // rodada 09: áudio em sincronia com os quadros
                    st.captureSsaa = record ? 1f : 2f;              // vídeo: como o jogo (FXAA, sem SSAA)
                }
                case "settle" -> settle(w, h, dt);
                case "shot" -> {
                    settle(w, h, dt);
                    Screenshot.save(app.capture(st, w, h), out.resolve(t[1] + ".png"));
                    System.out.println("criador: " + out.resolve(t[1] + ".png"));
                }
                default -> {
                    float dur = 0;
                    List<float[]> path = new ArrayList<>();          // {x, y} por quadro
                    List<InputEvent> at = new ArrayList<>();          // eventos extras no último quadro
                    switch (t[0]) {
                        case "wait" -> dur = Float.parseFloat(t[1]);
                        case "move", "click" -> {
                            String[] a2 = (t.length > 2 ? t[1] + " " + t[2] : t[1]).split("\\s+");
                            float[] p = target(a2[0], w, h);
                            dur = a2.length > 1 ? Float.parseFloat(a2[1]) : 0.45f;
                            path = glide(cx, cy, p[0], p[1], Math.max(1, Math.round(dur * fps)));
                            cx = p[0]; cy = p[1];
                            if (t[0].equals("click")) {
                                at.add(new InputEvent(InputEvent.Kind.DOWN, cx, cy, 0, 0, 0));
                                at.add(new InputEvent(InputEvent.Kind.UP, cx, cy, 0, 0, 0));
                            }
                        }
                        case "drag" -> {
                            String[] a2 = (t[1] + " " + (t.length > 2 ? t[2] : "")).split("\\s+");
                            float dx = Float.parseFloat(a2[0]) * layout.s, dy = Float.parseFloat(a2[1]) * layout.s;
                            dur = a2.length > 2 ? Float.parseFloat(a2[2]) : 0.8f;
                            int n = Math.max(2, Math.round(dur * fps));
                            injectFrame(List.of(new InputEvent(InputEvent.Kind.DOWN, cx, cy, 0, 0, 0)), dt, w, h, record, out, frameNo++);
                            path = glide(cx, cy, cx + dx, cy + dy, n);
                            cx += dx; cy += dy;
                            at.add(new InputEvent(InputEvent.Kind.UP, cx, cy, 0, 0, 0));
                        }
                        case "wheel" -> {
                            String[] a2 = (t[1] + " " + (t.length > 2 ? t[2] : "")).split("\\s+");
                            if (a2.length > 1 && !a2[1].isEmpty()) {
                                float[] p = target(a2[1], w, h);
                                path = glide(cx, cy, p[0], p[1], Math.round(0.3f * fps));
                                cx = p[0]; cy = p[1];
                            }
                            int n = Integer.parseInt(a2[0]);
                            for (int i = 0; i < Math.abs(n); i++) {
                                path.add(new float[]{cx, cy});
                                path.add(new float[]{cx, cy});
                            }
                            for (int i = 0; i < Math.abs(n); i++) at.add(new InputEvent(InputEvent.Kind.SCROLL, cx, cy, 0, Math.signum(n), 0));
                            // a roda é espaçada no tempo (um passo a cada 2 quadros) — ver loop abaixo
                        }
                        case "key" -> at.add(new InputEvent(InputEvent.Kind.KEY, cx, cy, 0, 0, keyCode(t[1])));
                        case "type" -> {
                            String text = line.substring(5);
                            for (int i = 0; i < text.length(); i++) {
                                injectFrame(List.of(new InputEvent(InputEvent.Kind.CHAR, cx, cy, 0, 0, text.charAt(i))), dt, w, h, record, out, frameNo++);
                                injectFrame(List.of(), dt, w, h, record, out, frameNo++);
                            }
                        }
                        default -> throw new IllegalArgumentException("comando de roteiro desconhecido: " + line);
                    }
                    if (t[0].equals("wheel")) {
                        int k = 0;
                        for (int i = 0; i < path.size(); i++) {
                            float[] p = path.get(i);
                            List<InputEvent> e = new ArrayList<>(List.of(new InputEvent(InputEvent.Kind.MOVE, p[0], p[1], 0, 0, 0)));
                            if (i >= path.size() - 2 * at.size() && (i - (path.size() - 2 * at.size())) % 2 == 0 && k < at.size()) e.add(at.get(k++));
                            injectFrame(e, dt, w, h, record, out, frameNo++);
                        }
                        break;
                    }
                    for (float[] p : path) injectFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, p[0], p[1], 0, 0, 0)), dt, w, h, record, out, frameNo++);
                    if (!at.isEmpty()) {
                        if (at.size() == 2 && at.get(0).kind() == InputEvent.Kind.DOWN) {   // clique: aperta, segura 3 quadros, solta
                            injectFrame(List.of(at.get(0)), dt, w, h, record, out, frameNo++);
                            for (int i = 0; i < 3; i++) injectFrame(List.of(), dt, w, h, record, out, frameNo++);
                            injectFrame(List.of(at.get(1)), dt, w, h, record, out, frameNo++);
                        } else injectFrame(at, dt, w, h, record, out, frameNo++);
                    }
                    int rest = Math.round(dur * fps) - path.size();
                    if (t[0].equals("wait")) for (int i = 0; i < rest; i++) injectFrame(List.of(), dt, w, h, record, out, frameNo++);
                }
            }
        }
        if (frameNo > 0 && record) System.out.println("criador: " + frameNo + " quadros");
    }

    /** Quadros gravados (público: o roteiro do jogo continua a mesma numeração ao abrir o criador). */
    public int recorded;
    private List<Double> perf, latencies;
    private final List<String> slow = new ArrayList<>();
    private long pendingSince;
    private String pendingSig;

    private void report(Path f) throws IOException {
        StringBuilder sb = new StringBuilder("# Criador (rodada 07.2): tempo por quadro durante cliques rápidos (lógica + envio + render, glFinish)\n");
        sb.append(stats("quadro (ms)", perf)).append(stats("clique → personagem novo (ms)", latencies));
        sb.append(String.format("composições concluídas %d, descartadas %d, miniaturas %d, modelos no cache %d%n",
                async.completed, async.discarded, thumbs.rendered, async.cachedModels()));
        sb.append(String.format("última composição: compor %.1f ms, malha %.1f ms (thread de trabalho); envio %.1f ms (thread do GL)%n",
                async.lastComposeMs, async.lastMeshMs, async.lastUploadMs));
        sb.append("quadros acima de 25 ms:\n");
        for (String x : slow) sb.append(x).append('\n');
        sb.append(String.format("malhas vivas %d (%.1f MB)%n", vx.engine.Mesh.liveMeshes, vx.engine.Mesh.liveBytes / 1048576.0));
        Files.writeString(f, sb.toString());
        System.out.print(sb);
    }

    private static String stats(String name, List<Double> v) {
        if (v.isEmpty()) return name + ": sem amostras\n";
        List<Double> s = new ArrayList<>(v);
        s.sort(null);
        double sum = 0;
        for (double x : s) sum += x;
        return String.format("%s: n=%d média %.1f mediana %.1f p95 %.1f máx %.1f%n", name, s.size(), sum / s.size(),
                s.get(s.size() / 2), s.get(Math.min(s.size() - 1, (int) Math.ceil(s.size() * 0.95) - 1)), s.getLast());
    }

    private void injectFrame(List<InputEvent> evs, float dt, int w, int h, boolean record, Path out, int n) {
        long t0 = System.nanoTime();
        int th0 = thumbs.rendered, up0 = async.completed;
        window.poll();
        for (InputEvent e : evs) window.inject(e);
        frame(window.events(), dt, w, h);
        long t1 = System.nanoTime();
        if (perf != null) {
            if (async.wanted != null && !async.wanted.equals(pendingSig) && !async.wanted.equals(shownSig)) { pendingSig = async.wanted; pendingSince = t0; }
            if (pendingSig != null && pendingSig.equals(shownSig)) { latencies.add((System.nanoTime() - pendingSince) / 1e6); pendingSig = null; }
            app.renderFrame(st, null, w, h, 1f, 1f);
            org.lwjgl.opengl.GL11.glFinish();
            double ms = (System.nanoTime() - t0) / 1e6;
            perf.add(ms);
            if (ms > 25) slow.add(String.format("  quadro %d: %.1f ms (lógica+envio %.1f ms; miniaturas %d; personagem enviado %d)",
                    perf.size(), ms, (t1 - t0) / 1e6, thumbs.rendered - th0, async.completed - up0));
            return;
        }
        if (record) {
            Path f = out.resolve("quadros").resolve(String.format("q_%05d.png", recorded++));
            Screenshot.save(app.capture(st, w, h), f);
        } else {
            app.renderFrame(st, null, Math.max(1, window.width()), Math.max(1, window.height()), 1f, 1f);  // mantém o GL vivo
        }
    }

    /** Espera a composição e as miniaturas (até 6 s), avançando o tempo de lógica sem gravar. */
    private void settle(int w, int h, float dt) {
        long end = System.currentTimeMillis() + 6000;
        int guard = 0;
        while (System.currentTimeMillis() < end) {
            window.poll();
            frame(window.events(), 0f, w, h);
            if (idle() && ++guard > 3) break;
            try { Thread.sleep(4); } catch (InterruptedException e) { return; }
        }
        cam.snap();
        frame(List.of(), 0f, w, h);
        app.renderFrame(st, null, w, h, 1f, 1f);            // primeiro render (cena montada) fora das medições
        org.lwjgl.opengl.GL11.glFinish();                  // o trabalho de GPU da espera não cai no quadro seguinte
    }

    private float[] target(String id, int w, int h) {
        if (id.contains(",") && !id.contains(":")) {
            String[] p = id.split(",");
            return new float[]{Float.parseFloat(p[0]) * w, Float.parseFloat(p[1]) * h};
        }
        if (id.equals("scene")) return new float[]{layout.scene.x() + layout.scene.w() / 2, h * 0.5f};
        if (id.equals("options")) return new float[]{layout.options.x() + layout.options.w() / 2, layout.options.y() + layout.options.h() * 0.6f};
        CreatorLayout.Widget wd = layout.get(id);
        if (wd == null) throw new IllegalArgumentException("controle não está na tela: " + id);
        return new float[]{wd.r.x() + wd.r.w() * 0.5f, wd.r.y() + wd.r.h() * 0.45f};
    }

    private static List<float[]> glide(float x0, float y0, float x1, float y1, int n) {
        List<float[]> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            float t = (float) i / n, e = t * t * (3 - 2 * t);              // suave, como a mão
            float arc = (float) Math.sin(t * Math.PI) * 0.06f * (float) Math.hypot(x1 - x0, y1 - y0);
            out.add(new float[]{x0 + (x1 - x0) * e, y0 + (y1 - y0) * e - arc});
        }
        return out;
    }

    static int keyCode(String name) {
        return switch (name) {
            case "TAB" -> GLFW_KEY_TAB; case "LEFT" -> GLFW_KEY_LEFT; case "RIGHT" -> GLFW_KEY_RIGHT;
            case "UP" -> GLFW_KEY_UP; case "DOWN" -> GLFW_KEY_DOWN; case "ENTER" -> GLFW_KEY_ENTER;
            case "ESC" -> GLFW_KEY_ESCAPE; case "F2" -> GLFW_KEY_F2; case "F3" -> GLFW_KEY_F3;
            case "BACKSPACE" -> GLFW_KEY_BACKSPACE; case "R" -> GLFW_KEY_R;
            default -> throw new IllegalArgumentException("tecla desconhecida: " + name);
        };
    }

    @Override
    public void close() {
        app.overlay = null;
        thumbs.close();
        async.close();
        view.close();
        if (shownSig != null) app.forget("custom:" + shownSig);
        World.layout = prevLayout;
    }
}
