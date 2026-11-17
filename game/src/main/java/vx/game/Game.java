package vx.game;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import vx.engine.Camera;
import vx.engine.Framebuffer;
import vx.engine.Screenshot;
import vx.engine.Window;
import vx.engine.Window.InputEvent;
import vx.viewer.Actor;
import vx.viewer.Animator;
import vx.viewer.App;
import vx.viewer.Appearance;
import vx.viewer.Assets;
import vx.viewer.AsyncComposer;
import vx.viewer.CharacterModel;
import vx.viewer.Creator;
import vx.viewer.Renderer;
import vx.viewer.StyleProfile;
import vx.viewer.Terrain;
import vx.viewer.UiKit;
import vx.viewer.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Rodada 08 — o protótipo jogável: janela, cena, desenho, interface e roteiros. As regras ficam no
 * {@link GameLogic}; aqui só o que precisa de OpenGL. Reaproveita o runtime do módulo viewer (Renderer, World,
 * Actor, AsyncComposer, Creator, UiKit) sem duplicar.
 */
public final class Game implements AutoCloseable {
    public static final String SCENE = "jogo_vila_e";
    /** Pasta das aparências salvas pelo criador (-Dvx.saves=... muda; os roteiros usam uma pasta própria). */
    public static final Path SAVES = Path.of(System.getProperty("vx.saves", "output/aparencias"));

    private final Window window;
    private final Path assetsPath;
    private final Assets assets;
    private final StyleProfile style;
    private final World world;
    public final Terrain terrain;
    public final GameLogic logic;
    private final AsyncComposer async;
    private final Renderer renderer = new Renderer();
    private final UiKit kit = new UiKit();
    private final Camera cam = new Camera();
    private final Input input = new Input();
    private final Renderer.Toggles toggles = new Renderer.Toggles();
    private final Map<GameLogic.Npc, Actor> npcActors = new HashMap<>();
    private Actor playerActor;
    private CharacterModel playerModel;
    private String shownSig;
    private Framebuffer captureFbo;
    private final List<Button> buttons = new ArrayList<>();
    private String hover;
    public boolean drawCursor, scripted;
    private float mouseX = -1, mouseY = -1;
    private final Animator.Motion pMotion = new Animator.Motion(), nMotion = new Animator.Motion();
    // medição
    public double lastLogicMs, lastRenderMs;
    private float debugShadowRadius = -1;
    /** Tempo para montar a cena (relevo + props, inclui o meshing). */
    public double worldBuildMs;

    record Button(String id, float x, float y, float w, float h, String label, boolean primary) {
        boolean contains(float px, float py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }

    public Game(Window window, Path assetsPath) {
        this.window = window;
        this.assetsPath = assetsPath;
        assets = new Assets(assetsPath);
        String prev = World.layout;
        World.layout = SCENE;
        style = StyleProfile.of(4, 0);
        long w0 = System.nanoTime();
        world = new World(assets, style);
        worldBuildMs = (System.nanoTime() - w0) / 1e6;
        World.layout = prev;
        terrain = world.terrainGrid;
        SceneDef scene = SceneDef.load(assetsPath, SCENE);
        Collision col = Collision.build(terrain, Collision.profiles(assetsPath), world.placements(), world.bodies(), scene);
        logic = new GameLogic(scene, col);
        async = new AsyncComposer(assetsPath, 8);
        logic.onAppearance = async::request;
        logic.loadChoices(async.catalog(), SAVES);
        for (GameLogic.Npc n : logic.npcs) npcActors.put(n, new Actor(assets.rig(n.def.rig()), style.animExaggeration));
        toggles.fxaa = true;
        toggles.terrainFar = true;
    }

    // ------------------------------------------------------------------ quadro

    /** Lógica de um quadro com os eventos dados (fila da janela). */
    public void tick(List<InputEvent> events, float dt, int w, int h) {
        long t0 = System.nanoTime();
        input.feed(events);
        for (InputEvent e : events) if (e.kind() == InputEvent.Kind.MOVE) { mouseX = e.x(); mouseY = e.y(); }
        hover = null;
        for (Button b : buttons) if (b.contains(mouseX, mouseY)) hover = b.id;
        boolean uiState = logic.state != GameLogic.State.EXPLORE;
        if (uiState) for (float[] c : input.clicks) for (Button b : buttons) if (b.contains(c[0], c[1])) { logic.action(b.id); break; }
        if (logic.state == GameLogic.State.EXPLORE && !scripted) {
            if (!window.cursorCaptured() && (window.focused() || !input.clicks.isEmpty())) { window.captureCursor(true); input.resetMouse(); }
        } else if (window.cursorCaptured()) { window.captureCursor(false); input.resetMouse(); }
        if (logic.state == GameLogic.State.EXPLORE && !window.cursorCaptured() && !scripted) { input.mdx = 0; input.mdy = 0; }
        logic.update(dt, input);
        async.poll();
        String sig = async.shown;
        if (sig != null && !sig.equals(shownSig)) {
            CharacterModel m = async.model(sig);
            if (m != null) {
                playerModel = m;
                playerActor = new Actor(m, style.animExaggeration);
                shownSig = sig;
            }
        }
        lastLogicMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Desenha a cena e a interface no alvo (null = janela). */
    public void render(Framebuffer target, int w, int h, float ssaa, float dt) {
        long t0 = System.nanoTime();
        Renderer.Frame f = new Renderer.Frame();
        logic.cam.apply(cam);
        cam.update((float) w / h);
        f.camera = cam;
        f.style = style;
        f.time = logic.time;
        f.toggles = toggles;
        world.submit(f, logic.time);
        float blend = Math.min(1f, dt * 12f);
        Player p = logic.player;
        if (playerActor != null)
            playerActor.submit(f, new Matrix4f().translate(p.x, p.visualY, p.z).rotateY(p.yaw), p.motion(pMotion), blend, null, terrain);
        for (GameLogic.Npc n : logic.npcs) {
            SceneDef.Line l = n == logic.talking ? logic.currentLine() : null;
            String expr = l != null ? l.expression() : null;
            npcActors.get(n).submit(f, new Matrix4f().translate(n.def.x(), terrain.groundAt(n.def.x(), n.def.z()), n.def.z()).rotateY(n.yaw),
                    logic.npcMotion(n, nMotion), blend, expr, terrain);
        }
        f.shadowFocus.set(p.x, p.y, p.z);
        f.shadowRadius = debugShadowRadius > 0 ? debugShadowRadius : Math.clamp(cam.distance * 1.5f, 6f, 26f);
        renderer.render(f, target, w, h, ssaa);
        if (target != null) target.bind(); else Framebuffer.bindDefault(w, h);
        drawUi(w, h);
        lastRenderMs = (System.nanoTime() - t0) / 1e6;
    }

    public java.awt.image.BufferedImage capture(int w, int h, float ssaa) {
        if (captureFbo == null || captureFbo.width != w || captureFbo.height != h) {
            if (captureFbo != null) captureFbo.close();
            captureFbo = new Framebuffer(w, h, false, GL_RGBA8);
        }
        render(captureFbo, w, h, ssaa, 1f / 30f);
        captureFbo.bind();
        glFinish();
        return Screenshot.read(w, h);
    }

    // ------------------------------------------------------------------ interface

    private float s;

    private void drawUi(int w, int h) {
        s = Math.clamp(Math.min(w / 1920f, h / 1080f), 0.6f, 1.8f);
        buttons.clear();
        kit.begin(w, h);
        switch (logic.state) {
            case SELECT -> drawSelect(w, h);
            case EXPLORE -> drawExplore(w, h);
            case DIALOG -> drawDialog(w, h);
            case PAUSE -> drawPause(w, h);
        }
        if (async.busy() && async.waitingMs() > 150) {
            float bw = 260 * s;
            kit.chamfer((w - bw) / 2, h - 120 * s, bw, 46 * s, 5 * s, 0x2a1e18c0);
            kit.text((w - bw) / 2 + 24 * s, h - 111 * s, "Preparando" + ".".repeat(((int) (logic.time * 3)) % 4), px(20), true, UiKit.PARCH_L);
        }
        if (logic.message != null && logic.time < logic.messageUntil) {
            int p = px(21);
            float tw = kit.width(logic.message, p, true), bw = tw + 60 * s;
            kit.chamfer((w - bw) / 2, 40 * s, bw, 52 * s, 6 * s, UiKit.WOOD_D);
            kit.chamfer((w - bw) / 2 + 3 * s, 43 * s, bw - 6 * s, 46 * s, 5 * s, UiKit.PARCH_L);
            kit.text((w - bw) / 2 + 30 * s, 66 * s - p * 0.66f, logic.message, p, true, UiKit.INK);
        }
        if (drawCursor && logic.state != GameLogic.State.EXPLORE && mouseX >= 0) kit.cursor(mouseX, mouseY, s, false);
        kit.end();
    }

    private int px(float base) { return Math.max(9, Math.round(base * s)); }

    private void button(String id, float x, float y, float w, float h, String label, boolean primary) {
        Button b = new Button(id, x, y, w, h, label, primary);
        buttons.add(b);
        boolean hv = id.equals(hover);
        kit.rect(x + 3 * s, y + 5 * s, w, h, UiKit.SHADOW);
        kit.chamfer(x, y, w, h, 5 * s, primary ? UiKit.MOSS_D : UiKit.WOOD_D);
        kit.chamfer(x + 3 * s, y + 3 * s, w - 6 * s, h - 7 * s, 4 * s,
                primary ? (hv ? 0x74a26cff : UiKit.MOSS) : hv ? UiKit.WOOD_L : UiKit.WOOD);
        kit.rect(x + 9 * s, y + 4 * s, w - 18 * s, Math.max(1, 2 * s), 0xffffff38);
        int p = textPx(label, w - 20 * s, 22);
        kit.text(x + (w - kit.width(label, p, true)) / 2, y + h / 2 - p * 0.66f, label, p, true, UiKit.PARCH_L);
    }

    /** Maior passo inteiro da fonte de pixel que cabe na largura. */
    private int textPx(String t, float room, float base) {
        int p = px(base);
        while (kit.width(t, p, true) > room && vx.engine.PixelFont.scale(p) > 1) p = 9 * (vx.engine.PixelFont.scale(p) - 1);
        return p;
    }

    private void title(String t, String sub) {
        float m = 28 * s;
        for (int i = 0; i < 24; i++) {
            int y0 = Math.round(i * 7.5f * s), y1 = Math.round((i + 1) * 7.5f * s);
            kit.rect(0, y0, 99999, y1 - y0, 0x2a1a1200 | Math.round(150 * (1 - i / 24f) * (1 - i / 24f)));
        }
        kit.text(m + 2 * s, m + 3 * s, t, px(48), true, 0x20140eb0);
        kit.text(m, m, t, px(48), true, 0xfff8eaff);
        if (sub != null) kit.text(m, m + 52 * s, sub, px(20), false, 0xfff8eaff);
    }

    private void drawSelect(int w, int h) {
        title("Valumo", "Protótipo de exploração — escolha a aparência e entre na vila");
        float pw = 560 * s, px0 = w - 28 * s - pw, py = 132 * s, ph = h - py - 28 * s;
        kit.panel(px0, py, pw, ph, s, false);
        float x = px0 + 24 * s, y = py + 24 * s, iw = pw - 48 * s;
        kit.text(x, y, "Aparência", px(26), true, UiKit.INK);
        y += 38 * s;
        kit.text(x, y, "A classe não é definida pela aparência.", px(19), false, UiKit.INK_SOFT);
        y += 40 * s;
        float bh = 54 * s, gap = 8 * s;
        int maxRows = (int) ((ph - 310 * s) / (bh + gap));
        for (int i = 0; i < Math.min(logic.choices.size(), maxRows); i++) {
            GameLogic.Choice c = logic.choices.get(i);
            boolean sel = i == logic.selected, hv = ("escolha:" + i).equals(hover);
            buttons.add(new Button("escolha:" + i, x, y, iw, bh, c.label(), false));
            kit.chamfer(x, y, iw, bh, 4 * s, sel ? UiKit.GOLD : hv ? UiKit.WOOD_L : UiKit.WOOD);
            kit.chamfer(x + 3 * s, y + 3 * s, iw - 6 * s, bh - 6 * s, 3 * s, sel ? UiKit.PARCH_L : c.valid() ? UiKit.PARCH_D : 0xcfc2b0ff);
            String tag = c.id().startsWith("preset:") ? "modelo" : c.id().startsWith("salva:") ? "salva" : "criada agora";
            int p = px(22);
            kit.text(x + 18 * s, y + bh / 2 - p * 0.66f, c.label(), p, true, c.valid() ? UiKit.INK : UiKit.INK_SOFT);
            String right = c.valid() ? tag : c.reason();
            kit.text(x + iw - 18 * s - kit.width(right, px(18), false), y + bh / 2 - px(18) * 0.66f, right, px(18), false, UiKit.INK_SOFT);
            if (sel) kit.seed(x + iw - 40 * s - kit.width(right, px(18), false), y + bh / 2 - 4 * s, s);
            y += bh + gap;
        }
        if (logic.choices.size() > maxRows)
            kit.text(x, y, "+" + (logic.choices.size() - maxRows) + " (use ↑ ↓)", px(18), false, UiKit.INK_SOFT);
        float by = py + ph - 24 * s - 70 * s;
        button("comecar", x, by, iw, 70 * s, "Entrar na vila", true);
        button("criador", x, by - 66 * s, iw, 56 * s, "Criar no editor", false);
        controlsHint(w, h, "↑ ↓ escolher · Enter entrar · Esc sair");
    }

    private void controlsHint(int w, int h, String t) {
        int p = px(19);
        float tw = kit.width(t, p, false) + 40 * s;
        kit.chamfer(28 * s, h - 28 * s - 44 * s, tw, 44 * s, 5 * s, 0x2a1e18b0);
        kit.text(48 * s, h - 28 * s - 22 * s - p * 0.66f, t, p, false, UiKit.PARCH_L);
    }

    private void drawExplore(int w, int h) {
        if (logic.showHelp && logic.time < 40f)
            controlsHint(w, h, "WASD andar · Shift correr · mouse olhar · roda aproxima · E conversar · Esc pausa · F1 ajuda");
        GameLogic.Npc n = logic.candidate;
        if (n != null) {
            float gy = terrain.groundAt(n.def.x(), n.def.z());
            float[] sc = project(n.def.x(), gy + 1.55f, n.def.z(), w, h);
            if (sc != null) {
                String a = "E", b = "Conversar com " + n.def.name();
                int p = px(21);
                float tw = kit.width(b, p, true), kw = 34 * s, bw = tw + kw + 46 * s, bh = 46 * s;
                float x = sc[0] - bw / 2, y = sc[1] - bh;
                kit.chamfer(x, y, bw, bh, 5 * s, UiKit.WOOD_D);
                kit.chamfer(x + 3 * s, y + 3 * s, bw - 6 * s, bh - 6 * s, 4 * s, UiKit.PARCH_L);
                kit.chamfer(x + 10 * s, y + 8 * s, kw, bh - 16 * s, 3 * s, UiKit.GOLD);
                kit.text(x + 10 * s + (kw - kit.width(a, p, true)) / 2, y + bh / 2 - p * 0.66f, a, p, true, UiKit.INK);
                kit.text(x + kw + 26 * s, y + bh / 2 - p * 0.66f, b, p, true, UiKit.INK);
                kit.rect(sc[0] - 4 * s, y + bh, 8 * s, 6 * s, UiKit.WOOD_D);
            }
        }
    }

    private float[] project(float x, float y, float z, int w, int h) {
        Vector4f v = cam.viewProj().transform(new Vector4f(x, y, z, 1));
        if (v.w <= 0.01f) return null;
        return new float[]{(v.x / v.w * 0.5f + 0.5f) * w, (1 - (v.y / v.w * 0.5f + 0.5f)) * h};
    }

    private void drawDialog(int w, int h) {
        SceneDef.Line l = logic.currentLine();
        if (l == null) return;
        float dw = Math.min(1100 * s, w - 56 * s), dh = 236 * s, dx = (w - dw) / 2, dy = h - 28 * s - dh;
        kit.panel(dx, dy, dw, dh, s, true);
        // plaqueta do nome
        String name = logic.talking.def.name(), role = logic.talking.def.title();
        int pn = px(26);
        float nw = kit.width(name, pn, true) + 56 * s + (role != null ? kit.width(role, px(19), false) + 24 * s : 0);
        kit.chamfer(dx + 30 * s, dy - 26 * s, nw, 52 * s, 5 * s, UiKit.GOLD_D);
        kit.chamfer(dx + 33 * s, dy - 23 * s, nw - 6 * s, 46 * s, 4 * s, UiKit.GOLD);
        kit.text(dx + 58 * s, dy - 23 * s + 23 * s - pn * 0.66f, name, pn, true, UiKit.INK);
        if (role != null) kit.text(dx + 58 * s + kit.width(name, pn, true) + 20 * s, dy - 23 * s + 23 * s - px(19) * 0.66f, role, px(19), false, UiKit.INK_SOFT);
        // fala com quebra de linha
        int pt = px(24);
        float ty = dy + 48 * s;
        for (String line : wrap(l.text(), dw - 90 * s, pt)) {
            kit.text(dx + 44 * s, ty, line, pt, false, UiKit.INK);
            ty += pt * 1.45f;
        }
        // progresso e botões
        int n = logic.talking.def.lines().size();
        for (int i = 0; i < n; i++)
            kit.rect(dx + 44 * s + i * 22 * s, dy + dh - 42 * s, 12 * s, 12 * s, i <= logic.line ? UiKit.GOLD_D : 0x6b463050);
        boolean last = logic.lastLine();
        float bw = 260 * s, bh = 58 * s;
        button("continuar-fala", dx + dw - 36 * s - bw, dy + dh - 30 * s - bh, bw, bh, last ? "Até logo  (E)" : "Continuar  (E)", true);
        button("encerrar-fala", dx + dw - 36 * s - bw - 16 * s - 190 * s, dy + dh - 30 * s - bh, 190 * s, bh, "Sair  (Esc)", false);
    }

    private List<String> wrap(String t, float maxW, int p) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : t.split(" ")) {
            String trial = cur.isEmpty() ? word : cur + " " + word;
            if (kit.width(trial, p, false) > maxW && !cur.isEmpty()) { out.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(trial);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private void drawPause(int w, int h) {
        kit.rect(0, 0, w, h, 0x1a1210a0);
        float pw = 620 * s, ph = 640 * s, x = (w - pw) / 2, y = (h - ph) / 2;
        kit.panel(x, y, pw, ph, s, true);
        kit.text(x + 40 * s, y + 34 * s, "Pausa", px(40), true, UiKit.INK);
        float bx = x + 40 * s, bw = pw - 80 * s, bh = 62 * s, by = y + 110 * s;
        button("continuar", bx, by, bw, bh, "Continuar", true);
        button("trocar-aparencia", bx, by + 76 * s, bw, bh, "Trocar aparência", false);
        button("criador", bx, by + 152 * s, bw, bh, "Abrir o criador", false);
        // sensibilidade do mouse
        float sy = by + 240 * s;
        kit.text(bx, sy + 14 * s, "Sensibilidade do mouse", px(21), true, UiKit.INK);
        String val = String.format("%.1f", logic.cam.sensitivity / 0.0032f);
        button("sens-", bx + bw - 200 * s, sy, 56 * s, 50 * s, "-", false);
        kit.text(bx + bw - 130 * s, sy + 25 * s - px(22) * 0.66f, val, px(22), true, UiKit.INK);
        button("sens+", bx + bw - 56 * s, sy, 56 * s, 50 * s, "+", false);
        String inv = logic.cam.invertY ? "Inverter vertical: sim" : "Inverter vertical: não";
        button("inverter", bx, sy + 66 * s, bw, 50 * s, inv, false);
        button("sair", bx, y + ph - 40 * s - bh, bw, bh, "Sair do jogo", false);
    }

    // ------------------------------------------------------------------ janela interativa

    public void run(int maxFrames) {
        double last = window.time(), start = last;
        int frames = 0;
        double sumMs = 0;
        while (!window.shouldClose() && !logic.quit && (maxFrames <= 0 || frames < maxFrames)) {
            window.poll();
            double now = window.time();
            float dt = (float) Math.min(0.1, now - last);
            last = now;
            int w = window.width(), h = window.height();
            if (w <= 0 || h <= 0) { window.swap(); continue; }
            for (InputEvent e : window.events())
                if (e.kind() == InputEvent.Kind.KEY && e.code() == GLFW_KEY_F12) shot(Path.of("output/visual-review/manual"), w, h);
            uiAction(window.events());
            tick(window.events(), dt, w, h);
            if (logic.openCreator) { openCreator(); continue; }
            render(null, w, h, 1f, dt);
            window.swap();
            frames++;
            sumMs += dt * 1000;
        }
        if (window.cursorCaptured()) window.captureCursor(false);
        if (maxFrames > 0)
            System.out.printf("jogo: janela %dx%d, %d quadros, %.1f FPS medio%n", window.width(), window.height(), frames,
                    frames / Math.max(1e-6, window.time() - start));
    }

    /** Ações que não passam pelo GameLogic (ajustes da câmera no menu). */
    private void uiAction(List<InputEvent> evs) {
        for (InputEvent e : evs) {
            if (e.kind() != InputEvent.Kind.UP || e.button() != 0 || logic.state != GameLogic.State.PAUSE) continue;
            for (Button b : buttons) {
                if (!b.contains(e.x(), e.y())) continue;
                switch (b.id) {
                    case "sens-" -> logic.cam.sensitivity = Math.max(0.0008f, logic.cam.sensitivity - 0.0008f);
                    case "sens+" -> logic.cam.sensitivity = Math.min(0.012f, logic.cam.sensitivity + 0.0008f);
                    case "inverter" -> logic.cam.invertY = !logic.cam.invertY;
                    default -> { }
                }
            }
        }
    }

    private int shots;

    private void shot(Path dir, int w, int h) {
        Path out = dir.resolve(String.format("jogo-%03d.png", ++shots));
        Screenshot.save(capture(w, h, 1f), out);
        System.out.println("captura: " + out);
    }

    /** Abre o criador da rodada 07.2 na mesma janela; "Levar para a vila" volta com a aparência. */
    private void openCreator() {
        logic.openCreator = false;
        if (window.cursorCaptured()) window.captureCursor(false);
        try (App app = new App(window, assetsPath); Creator cr = new Creator(app, window, assetsPath)) {
            cr.cs.finishLabel = "Levar para a vila";
            if (logic.appearance != null) cr.start(logic.appearance, logic.choices.get(logic.selected).label());
            cr.run(0);
            if (cr.cs.finished) {
                logic.loadChoices(async.catalog(), SAVES);
                logic.useCreated(cr.cs.appearance, cr.cs.name.isBlank() ? "Criada agora" : cr.cs.name.trim());
            }
        }
        logic.action("trocar-aparencia");
    }

    // ------------------------------------------------------------------ roteiro (entrada injetada na fila da janela)

    /**
     * Roteiro de jogo: os eventos entram na MESMA fila da janela ({@link Window#inject}) e passam pelo mesmo
     * caminho do jogo interativo. Não é automação do sistema (sessão Wayland); o cursor dos menus é desenhado
     * pela interface. Comandos (um por linha):
     * {@code size L A}, {@code wait s}, {@code key NOME}, {@code hold TECLAS s} (ex.: W, W+SHIFT, A+W),
     * {@code look dx dy s}, {@code mirar x z s}, {@code ir x z [correndo]} (segura W e corrige a mira com o mouse até
     * chegar), {@code wheel n}, {@code click id}, {@code criador ROTEIRO}, {@code estado}, {@code settle},
     * {@code shot nome}, {@code record on|off}, {@code perf on|off}.
     */
    public void script(Path file, Path out, int fps) throws IOException {
        scripted = true;
        drawCursor = true;
        List<String> lines = Files.readAllLines(file);
        Files.createDirectories(out);
        int w = 1920, h = 1080;
        float dt = 1f / fps, mx = w / 2f, my = h / 2f;
        boolean record = false;
        tickFrame(List.of(), dt, w, h, false, out);
        settle(w, h, dt, out);
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "size" -> { w = Integer.parseInt(t[1]); h = Integer.parseInt(t[2]); mx = w / 2f; my = h / 2f; }
                case "record" -> record = t[1].equals("on");
                case "criador" -> {                                        // abre o criador com um roteiro dele (mesma janela)
                    logic.openCreator = false;
                    if (window.cursorCaptured()) window.captureCursor(false);
                    try (App app = new App(window, assetsPath); Creator cr = new Creator(app, window, assetsPath)) {
                        cr.cs.finishLabel = "Levar para a vila";
                        if (logic.appearance != null) cr.start(logic.appearance, logic.choices.get(logic.selected).label());
                        cr.recorded = recorded;
                        cr.script(Path.of(t[1]), out, fps);
                        recorded = cr.recorded;
                        if (cr.cs.finished) logic.useCreated(cr.cs.appearance, cr.cs.name.isBlank() ? "Criada agora" : cr.cs.name.trim());
                    }
                    logic.action("trocar-aparencia");
                    settle(w, h, dt, out);
                }
                case "camera" -> System.out.printf("camera: pivô %.6f %.6f %.6f rumo %.6f incl %.6f dist %.6f desvio %.6f/%.6f herói visualY %.6f%n",
                        logic.cam.pivot.x, logic.cam.pivot.y, logic.cam.pivot.z, logic.cam.curYaw, logic.cam.curPitch, logic.cam.dist,
                        logic.cam.avoidYaw, logic.cam.avoidPitch, logic.player.visualY);
                case "estado" -> {
                    Player pl = logic.player;
                    StringBuilder sb = new StringBuilder(String.format("estado: %s herói (%.2f, %.2f) y=%.3f rumo %.0f° vel %.2f clipe %s",
                            logic.state, pl.x, pl.z, pl.y, Math.toDegrees(pl.yaw), pl.speed(), pl.clip));
                    for (GameLogic.Npc n : logic.npcs)
                        sb.append(String.format(" | %s a %.2f u", n.def.name(), Math.hypot(n.def.x() - pl.x, n.def.z() - pl.z)));
                    sb.append(logic.findCandidate() != null ? " | pode conversar" : "");
                    sb.append(String.format(" | câmera: quer %.2f u, está %.2f u, livre %.2f, enquadrando %s; aparência %s mostrada %s",
                            logic.cam.wantDist, logic.cam.dist, logic.cam.lastClearFraction(), logic.cam.framing(),
                            logic.appearance == null ? "-" : logic.appearance.signature(), shownSig));
                    org.joml.Vector3f want = logic.cam.position(logic.cam.wantDist, new org.joml.Vector3f());
                    String blk = logic.collision.blocker(logic.cam.pivot, want, 0.18f);
                    if (blk != null) sb.append(" | linha de visão cortada por ").append(blk);
                    System.out.println(sb);
                }
                case "settle" -> settle(w, h, dt, out);
                case "debug" -> {                                          // diagnóstico: sombra on|off, raio N|auto
                    if (t[1].equals("sombra")) toggles.shadows = t[2].equals("on");
                    if (t[1].equals("raio")) debugShadowRadius = t[2].equals("auto") ? -1 : Float.parseFloat(t[2]);
                }
                case "aquecer" -> {                                       // desenha sem medir (a GPU integrada leva ~3 s para subir de frequência)
                    int n = Math.round(Float.parseFloat(t[1]) * fps);
                    for (int i = 0; i < n; i++) {
                        tickFrame(List.of(), dt, w, h, false, out);
                        render(null, w, h, 1f, dt);
                        glFinish();
                    }
                }
                case "perf" -> {
                    if (t[1].equals("on")) { perf = new ArrayList<>(); perfLogic = new ArrayList<>(); perfRender = new ArrayList<>(); }
                    else { report(out.resolve(t.length > 2 ? t[2] : "desempenho.txt")); perf = null; }
                }
                case "shot" -> {
                    settle(w, h, dt, out);                                   // composição pronta antes de capturar
                    Screenshot.save(capture(w, h, 2f), out.resolve(t[1] + ".png"));
                    System.out.println("jogo: " + out.resolve(t[1] + ".png"));
                }
                case "wait" -> {
                    int n = Math.round(Float.parseFloat(t[1]) * fps);
                    for (int i = 0; i < n; i++) tickFrame(List.of(), dt, w, h, record, out);
                }
                case "key" -> {
                    int k = keyCode(t[1]);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, k)), dt, w, h, record, out);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, k)), dt, w, h, record, out);
                }
                case "hold" -> {
                    String[] ks = t[1].split("\\+");
                    int n = Math.round(Float.parseFloat(t[2]) * fps);
                    List<InputEvent> downs = new ArrayList<>(), ups = new ArrayList<>();
                    for (String k : ks) {
                        downs.add(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, keyCode(k)));
                        ups.add(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, keyCode(k)));
                    }
                    tickFrame(downs, dt, w, h, record, out);
                    for (int i = 1; i < n; i++) tickFrame(List.of(), dt, w, h, record, out);
                    tickFrame(ups, dt, w, h, record, out);
                }
                case "look" -> {
                    float dx = Float.parseFloat(t[1]), dy = Float.parseFloat(t[2]);
                    int n = Math.max(1, Math.round(Float.parseFloat(t[3]) * fps));
                    for (int i = 0; i < n; i++) {
                        mx += dx / n;
                        my += dy / n;
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, mx, my, 0, 0, 0)), dt, w, h, record, out);
                    }
                }
                case "mirar" -> {                                          // gira a câmera (pelo mouse) até olhar para (x, z)
                    float x = Float.parseFloat(t[1]), z = Float.parseFloat(t[2]);
                    int n = Math.max(1, Math.round(Float.parseFloat(t[3]) * fps));
                    float want = (float) Math.atan2(logic.player.x - x, logic.player.z - z);   // câmera atrás do herói olhando para o alvo
                    float d = (float) Math.atan2(Math.sin(want - logic.cam.yaw), Math.cos(want - logic.cam.yaw));
                    float pixels = -d / logic.cam.sensitivity;
                    for (int i = 0; i < n; i++) {
                        mx += pixels / n;
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, mx, my, 0, 0, 0)), dt, w, h, record, out);
                    }
                }
                case "ir" -> {                                             // segura W (e Shift) e corrige a mira com o mouse até chegar
                    float x = Float.parseFloat(t[1]), z = Float.parseFloat(t[2]);
                    boolean run = t.length > 3 && t[3].equals("correndo");
                    List<InputEvent> downs = new ArrayList<>(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_W)));
                    if (run) downs.add(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_LEFT_SHIFT));
                    tickFrame(downs, dt, w, h, record, out);
                    int limit = Math.round(25f * fps);
                    for (int i = 0; i < limit; i++) {
                        Player pl = logic.player;
                        if (Math.hypot(x - pl.x, z - pl.z) < 0.45f) break;
                        float want = (float) Math.atan2(pl.x - x, pl.z - z);
                        float d = (float) Math.atan2(Math.sin(want - logic.cam.curYaw), Math.cos(want - logic.cam.curYaw));
                        float px = Math.clamp(-d / logic.cam.sensitivity * 0.25f, -40f, 40f);   // mão: corrige aos poucos
                        mx += px;
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, mx, my, 0, 0, 0)), dt, w, h, record, out);
                    }
                    List<InputEvent> ups = new ArrayList<>(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_W)));
                    if (run) ups.add(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_LEFT_SHIFT));
                    tickFrame(ups, dt, w, h, record, out);
                }
                case "wheel" -> {
                    int n = Integer.parseInt(t[1]);
                    for (int i = 0; i < Math.abs(n); i++) {
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.SCROLL, mx, my, 0, Math.signum(n), 0)), dt, w, h, record, out);
                        tickFrame(List.of(), dt, w, h, record, out);
                    }
                }
                case "click" -> {
                    Button b = null;
                    for (Button x : buttons) if (x.id.equals(t[1])) b = x;
                    if (b == null) throw new IllegalArgumentException("botão não está na tela: " + t[1]);
                    float tx = b.x + b.w / 2, ty = b.y + b.h / 2;
                    int n = Math.round(0.4f * fps);
                    for (int i = 1; i <= n; i++) {
                        float k = (float) i / n, e = k * k * (3 - 2 * k);
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, mx + (tx - mx) * e, my + (ty - my) * e, 0, 0, 0)), dt, w, h, record, out);
                    }
                    mx = tx;
                    my = ty;
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.DOWN, mx, my, 0, 0, 0)), dt, w, h, record, out);
                    tickFrame(List.of(), dt, w, h, record, out);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.UP, mx, my, 0, 0, 0)), dt, w, h, record, out);
                }
                default -> throw new IllegalArgumentException("comando de roteiro desconhecido: " + line);
            }
        }
        if (recorded > 0) System.out.println("jogo: " + recorded + " quadros gravados");
    }

    private int recorded;
    private List<Double> perf, perfLogic, perfRender;

    private void tickFrame(List<InputEvent> evs, float dt, int w, int h, boolean record, Path out) {
        long t0 = System.nanoTime();
        window.poll();
        for (InputEvent e : evs) window.inject(e);
        uiAction(window.events());
        tick(window.events(), dt, w, h);
        if (perf != null) {
            long t1 = System.nanoTime();
            render(null, w, h, 1f, dt);
            glFinish();
            long t2 = System.nanoTime();
            perf.add((t2 - t0) / 1e6);
            perfLogic.add((t1 - t0) / 1e6);
            perfRender.add((t2 - t1) / 1e6);
            return;
        }
        if (record) {
            Path f = out.resolve("quadros").resolve(String.format("q_%05d.png", recorded++));
            Screenshot.save(capture(w, h, 1f), f);
        }
    }

    private void settle(int w, int h, float dt, Path out) {
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end && (async.busy() || playerActor == null)) {
            tickFrame(List.of(), 0f, w, h, false, out);
            try { Thread.sleep(4); } catch (InterruptedException e) { return; }
        }
        render(null, w, h, 1f, dt);
        glFinish();
    }

    private void report(Path f) throws IOException {
        StringBuilder sb = new StringBuilder("# Jogo (rodada 08): tempo por quadro (lógica + desenho com glFinish), janela oculta\n");
        sb.append(stats("quadro (ms)", perf)).append(stats("lógica: entrada, corpo, colisão, câmera, NPCs (ms)", perfLogic))
                .append(stats("desenho: cena + personagens + interface (ms)", perfRender));
        sb.append(String.format("draws %d, triângulos %d; formas de colisão %d; cena montada em %.0f ms (malha %s); malhas vivas %d (%.1f MB)%n",
                renderer.lastDrawCount, renderer.lastTriangles, logic.collision.shapes.size(), worldBuildMs,
                vx.engine.VoxelMesher.conforming ? "conforme" : "legado", vx.engine.Mesh.liveMeshes, vx.engine.Mesh.liveBytes / 1048576.0));
        StringBuilder slow = new StringBuilder();
        int nslow = 0;
        for (int i = 0; i < perf.size(); i++)
            if (perf.get(i) > 20) { nslow++; if (slow.length() < 600) slow.append(String.format(" #%d=%.1f(lógica %.1f)", i, perf.get(i), perfLogic.get(i))); }
        sb.append(String.format("quadros acima de 20 ms: %d de %d:%s%n", nslow, perf.size(), slow));
        Files.writeString(f, sb.toString());
        System.out.print(sb);
    }

    static String stats(String name, List<Double> v) {
        if (v == null || v.isEmpty()) return name + ": sem amostras\n";
        List<Double> s = new ArrayList<>(v);
        s.sort(null);
        double sum = 0;
        for (double x : s) sum += x;
        int n = s.size();
        double low1 = 0;
        int k = Math.max(1, n / 100);
        for (int i = n - k; i < n; i++) low1 += s.get(i);
        return String.format("%s: n=%d média %.2f mediana %.2f p95 %.2f p99 %.2f máx %.2f (1%% pior: %.2f)%n", name, n, sum / n, s.get(n / 2),
                s.get(Math.min(n - 1, (int) Math.ceil(n * 0.95) - 1)), s.get(Math.min(n - 1, (int) Math.ceil(n * 0.99) - 1)), s.getLast(), low1 / k);
    }

    static int keyCode(String name) {
        return switch (name) {
            case "W" -> GLFW_KEY_W; case "A" -> GLFW_KEY_A; case "S" -> GLFW_KEY_S; case "D" -> GLFW_KEY_D;
            case "E" -> GLFW_KEY_E; case "SHIFT" -> GLFW_KEY_LEFT_SHIFT; case "ESC" -> GLFW_KEY_ESCAPE;
            case "ENTER" -> GLFW_KEY_ENTER; case "SPACE" -> GLFW_KEY_SPACE; case "F1" -> GLFW_KEY_F1;
            case "UP" -> GLFW_KEY_UP; case "DOWN" -> GLFW_KEY_DOWN; case "LEFT" -> GLFW_KEY_LEFT; case "RIGHT" -> GLFW_KEY_RIGHT;
            default -> throw new IllegalArgumentException("tecla desconhecida: " + name);
        };
    }

    @Override
    public void close() {
        if (captureFbo != null) captureFbo.close();
        async.close();
        renderer.close();
        kit.close();
    }
}
