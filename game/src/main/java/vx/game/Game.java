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
    private String shownSig;
    private Framebuffer captureFbo;
    private final List<Button> buttons = new ArrayList<>();
    private String hover;
    public boolean drawCursor, scripted;
    final GameAudio gameAudio;
    final AudioPrefs audioPrefs = AudioPrefs.load(AudioPrefs.FILE);
    private float mouseX = -1, mouseY = -1;
    private final Animator.Motion pMotion = new Animator.Motion(), nMotion = new Animator.Motion(), eMotion = new Animator.Motion();
    /** Rodada 09: ator do inimigo (fungo-cardo) e modo técnico (caixas de golpe e de dano; F3 com -Dvx.tecnico=1). */
    private Actor enemyActor;
    /** Rodada 09.2: um ator por inimigo do encontro (o mesmo rig; pose própria) e os efeitos de morte em andamento. */
    private final Map<Enemy, Actor> enemyActors = new HashMap<>();
    private final Map<Enemy, DeathFx> deaths = new HashMap<>();
    private final java.util.Set<Enemy> broken = new java.util.HashSet<>();
    private DeathFxMesh deathMesh;
    /** Estilo da morte: D2 (padrão), D1 ou "antiga" (rodada 09: cai e encolhe — só para comparar). */
    public static String deathStyle = System.getProperty("vx.morte", "D2");

    /** Troca o estilo da morte (roteiros de comparação): D1, D2 ou antiga. */
    public static void setDeathStyle(String st) {
        deathStyle = st;
        Enemy.oldDeath = st.equals("antiga");
    }
    public boolean technical = Boolean.getBoolean("vx.tecnico"), debugCombat;
    /** Rastro curto das cápsulas avaliadas (depuração): {relógio, tipo 0 lâmina / 1 cabeçada, pontos...}. */
    private final java.util.ArrayDeque<float[]> dbgTrail = new java.util.ArrayDeque<>();
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
        gameAudio = new GameAudio(logic);
        audioPrefs.apply();
        async = new AsyncComposer(assetsPath, 8);
        if (logic.combat != null) {                                       // rodada 09: espada e broquel em qualquer aparência
            async.kit(logic.combat.def.heroKit);
            enemyActor = new Actor(assets.rig(logic.combat.def.enemyRig), style.animExaggeration);
            // rodada 09.2: blocos da morte e malha dinâmica prontos antes da luta (sem ler arquivo no meio do combate)
            VoxelCloud.of(assetsPath.resolve(logic.combat.def.enemyRig), 2);
            deathMesh = new DeathFxMesh();
        }
        initAudio();
        logic.onAppearance = async::request;
        logic.loadChoices(async.catalog(), SAVES);
        for (GameLogic.Npc n : logic.npcs) npcActors.put(n, new Actor(assets.rig(n.def.rig()), style.animExaggeration));
        toggles.fxaa = true;
        toggles.terrainFar = true;
    }

    // ------------------------------------------------------------------ som (rodada 09.1, ADR 0016)

    /** Efeitos sonoros; nunca null (sem dispositivo = backend mudo). */
    public Sfx sfx;
    /** Gravação: dispositivo loopback (o som da aplicação mixado no relógio do jogo) e o que já foi mixado. */
    private vx.engine.audio.OpenAlBackend loopback;
    private short[] loopChunk = new short[0];
    private java.io.ByteArrayOutputStream loopRecorded;
    private double loopFrac;

    /**
     * -Dvx.som = dispositivo (padrão do jogo), mudo (padrão dos roteiros) ou loopback (roteiros com som gravado em
     * audio.wav). Qualquer falha ao abrir o OpenAL: o jogo segue sem som, avisando no console.
     */
    private void initAudio() {
        // um só dispositivo (ADR 0023): se o Audio abriu o OpenAL (catálogo presente), os efeitos do combate tocam no MESMO
        // contexto; senão (sem catálogo), o Sfx abre o seu, como na rodada 09.1.
        String mode = System.getProperty("vx.som", "dispositivo");
        vx.engine.audio.AudioBackend b = combatBackend(mode, vx.engine.audio.Audio.output(), vx.engine.audio.Audio.get() != null);
        vx.engine.audio.OpenAlOut shared = vx.engine.audio.Audio.output();
        loopback = shared == null && b instanceof vx.engine.audio.OpenAlBackend o && !o.audible() ? o : null;
        long t0 = System.nanoTime();
        sfx = new Sfx(b, assetsPath);
        sfxMaster = sfx.master;
        System.out.printf("som: %s (efeitos carregados em %.1f ms)%n", b.describe(), (System.nanoTime() - t0) / 1e6);
        if (logic.combat != null) logic.audio = new CombatAudio(sfx, logic.combat);
        // passos: os do catálogo (quatro superfícies, GameAudio) quando houver; senão o passo único da 09.1
        if (!vx.engine.audio.Audio.has("passo.grama.andar")) logic.steps = new Footsteps(sfx);
        applyVolumes();
    }

    /**
     * Saída dos efeitos do combate — um só dispositivo por processo (ADR 0023):
     * <ul>
     *   <li>o {@code Audio} abriu o OpenAL → as vozes do combate entram no MESMO contexto (falha ao criá-las = mudo);</li>
     *   <li>o {@code Audio} existe mas está mudo (pedido, ou sem dispositivo) → o combate fica mudo também: nunca abre um
     *       segundo dispositivo por conta própria (antes, {@code --demo} e {@code -Dvx.audio=mudo} abriam o real);</li>
     *   <li>sem catálogo (sem {@code Audio}) → o combate abre a saída dele pelo modo, como na rodada 09.1.</li>
     * </ul>
     */
    static vx.engine.audio.AudioBackend combatBackend(String mode, vx.engine.audio.OpenAlOut shared, boolean catalogOpen) {
        if (shared != null) return Sfx.backendFor("dispositivo", () -> vx.engine.audio.OpenAlBackend.shared(shared), null);
        if (catalogOpen) return new vx.engine.audio.SilentBackend("o áudio do jogo está mudo");
        return Sfx.backendFor(mode, vx.engine.audio.OpenAlBackend::open, () -> vx.engine.audio.OpenAlBackend.loopback(48000));
    }

    /** Volume principal da mistura do combate (sons_e.json), antes da preferência do jogador. */
    private float sfxMaster = 1f;

    /** Preferências do jogador → catálogo (música, efeitos, interface, ambiente) e efeitos do combate. */
    private void applyVolumes() {
        audioPrefs.apply();
        if (sfx != null) sfx.setVolumes(sfxMaster * audioPrefs.effects, sfx.effects, sfx.muted);
    }

    /** Mudo (M ou botão da pausa): tudo — combate, música, ambiente e interface. */
    private void setMuted(boolean m) {
        sfx.setVolumes(sfx.master, sfx.effects, m);
        vx.engine.audio.SoundSystem ss = vx.engine.audio.Audio.get();
        if (ss != null) ss.volume("geral", m ? 0f : 1f);
    }

    private final org.joml.Vector3f earEye = new org.joml.Vector3f();

    /** Pausa, mudo (M) e ouvinte na câmera, depois da lógica do quadro. */
    private void audioFrame() {
        if (input.hit(GLFW_KEY_M)) setMuted(!sfx.muted);
        sfx.setPaused(logic.state == GameLogic.State.PAUSE);
        FollowCamera c = logic.cam;
        c.position(earEye);
        float fx = c.pivot.x - earEye.x, fy = c.pivot.y - earEye.y, fz = c.pivot.z - earEye.z;
        float l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (l > 1e-5f) sfx.backend.listener(earEye.x, earEye.y, earEye.z, fx / l, fy / l, fz / l, 0f, 1f, 0f);
    }

    /** Loopback: mixa o som deste quadro (dt de jogo) e guarda se estiver gravando. */
    private void audioRender(float dt, boolean record) {
        if (loopback == null) return;
        loopFrac += dt * loopback.rate();
        int frames = (int) loopFrac;
        loopFrac -= frames;
        if (frames <= 0) return;
        if (loopChunk.length < frames * 2) loopChunk = new short[frames * 2];
        loopback.render(loopChunk, frames);
        if (!record) return;
        if (loopRecorded == null) loopRecorded = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < frames * 2; i++) { loopRecorded.write(loopChunk[i] & 0xff); loopRecorded.write((loopChunk[i] >> 8) & 0xff); }
    }

    private void saveRecordedAudio(Path out) throws IOException {
        if (loopRecorded == null) return;
        byte[] b = loopRecorded.toByteArray();
        short[] pcm = new short[b.length / 2];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) ((b[2 * i] & 0xff) | (b[2 * i + 1] << 8));
        vx.engine.audio.Wav.write(out.resolve("audio.wav"), pcm, pcm.length, loopback.rate(), 2);
        System.out.printf("som: %.2f s gravados do loopback em %s%n", pcm.length / 2.0 / loopback.rate(), out.resolve("audio.wav"));
    }

    // ------------------------------------------------------------------ quadro

    /** Lógica de um quadro com os eventos dados (fila da janela). */
    public void tick(List<InputEvent> events, float dt, int w, int h) {
        long t0 = System.nanoTime();
        input.feed(events);
        for (InputEvent e : events) if (e.kind() == InputEvent.Kind.MOVE) { mouseX = e.x(); mouseY = e.y(); }
        hover = null;
        for (Button b : buttons) if (b.contains(mouseX, mouseY)) hover = b.id;
        boolean defeat = logic.combat != null && logic.combat.outcome == Combat.Outcome.DEFEAT;
        boolean uiState = logic.state != GameLogic.State.EXPLORE || defeat;
        if (uiState) for (float[] c : input.clicks) for (Button b : buttons) if (b.contains(c[0], c[1])) { logic.action(b.id); break; }
        if (logic.state == GameLogic.State.EXPLORE && !defeat && !scripted) {
            if (!window.cursorCaptured() && (window.focused() || !input.clicks.isEmpty())) { window.captureCursor(true); input.resetMouse(); input.leftPressed = 0; }
        } else if (window.cursorCaptured()) { window.captureCursor(false); input.resetMouse(); }
        if (logic.state == GameLogic.State.EXPLORE && !window.cursorCaptured() && !scripted) { input.mdx = 0; input.mdy = 0; input.leftPressed = 0; }
        if (defeat && input.hit(GLFW_KEY_ENTER)) logic.action("recomecar");
        if (technical && input.hit(GLFW_KEY_F3)) debugCombat = !debugCombat;
        logic.update(dt, input);
        if (logic.state == GameLogic.State.EXPLORE) updateDeaths(dt);
        audioFrame();                                                     // efeitos do combate: pausa, mudo
        gameAudio.tick(dt);                                               // passos, interface, música, ambiente, ouvinte, mistura
        async.poll();
        String sig = async.shown;
        if (sig != null && !sig.equals(shownSig)) {
            CharacterModel m = async.model(sig);
            if (m != null) {
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
        logic.cam.apply(cam, logic.alpha);
        cam.update((float) w / h);
        f.camera = cam;
        f.style = style;
        f.time = logic.time;
        f.toggles = toggles;
        world.submit(f, logic.time);
        float blend = Math.min(1f, dt * 12f), a = logic.alpha;
        Player p = logic.player;
        Combat cb = logic.combat;
        if (playerActor != null) {
            Matrix4f pw = new Matrix4f().translate(p.rx(a), p.rVisualY(a), p.rz(a)).rotateY(p.rYaw(a));
            if (cb != null) {
                // pose EXATA (blend 1): as trajetórias e transições já são as validadas; suavizar por cima as deformaria
                playerActor.flash = flash(cb.hero.hurtT);
                playerActor.fade = nearFade(p, a);
                playerActor.submit(f, pw, cb.hero.motion(pMotion, p, a), 1f, heroExpression(cb.hero), terrain);
            } else playerActor.submit(f, pw, p.motion(pMotion), blend, null, terrain);
        }
        if (cb != null && enemyActor != null) {
            boolean old = deathStyle.equals("antiga");
            for (Enemy e : cb.enemies) {
                boolean show = e.alive() || (old ? e.stateT < Animator.BRAWLER_DEATH + 0.05f : !broken.contains(e));
                if (!show) continue;
                Actor ac = enemyActors.computeIfAbsent(e, k -> new Actor(enemyActor.model, style.animExaggeration));
                ac.flash = flash(e.hurtT);
                ac.submit(f, new Matrix4f().translate(e.rx(a), e.rY(a), e.rz(a)).rotateY(e.rYaw(a)).scale(e.scale), e.motion(eMotion, a),
                        Math.min(1f, dt * 14f), null, terrain);
            }
            if (!old) drawDeaths(f, cb);
        }
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

    // ------------------------------------------------------------------ rodada 09.2: morte por desagregação voxel

    /** Depois do passo lógico: inimigo morto há {@link DeathFx#BREAK} s vira fragmentos da pose do último quadro desenhado. */
    private void updateDeaths(float dt) {
        Combat cb = logic.combat;
        if (cb == null || deathStyle.equals("antiga")) return;
        enemyActors.keySet().removeIf(e -> !cb.enemies.contains(e));
        deaths.keySet().removeIf(e -> !cb.enemies.contains(e));
        broken.removeIf(e -> !cb.enemies.contains(e) || e.alive());
        for (Enemy e : cb.enemies) {
            if (e.alive() || broken.contains(e) || e.deadT < DeathFx.BREAK) continue;
            Actor ac = enemyActors.get(e);
            broken.add(e);
            if (ac == null) continue;                                        // nunca foi desenhado (fora da cena): só some
            VoxelCloud cloud = VoxelCloud.of(assetsPath.resolve(cb.def.enemyRig), 2);
            Player p = logic.player;
            deaths.put(e, new DeathFx(cloud, ac.lastMatrices(), e.scale, e.x, e.y + 0.35f * e.scale, e.z, p.x, p.z,
                    e.id * 7919L + cb.ticks, deathStyle.equals("D1") ? DeathFx.Style.D1 : DeathFx.Style.D2, terrain));
        }
        long f0 = System.nanoTime();
        int live = 0;
        for (DeathFx fx : deaths.values()) { fx.update(dt); live += fx.visible(); }
        deaths.values().removeIf(DeathFx::done);
        if (live > 0) { long d = System.nanoTime() - f0; fxNanos += d; fxMax = Math.max(fxMax, d); fxFrames++; fxPeak = Math.max(fxPeak, live); }
    }

    private void drawDeaths(Renderer.Frame f, Combat cb) {
        if (deaths.isEmpty()) return;
        if (deathMesh == null) deathMesh = new DeathFxMesh();
        long m0 = System.nanoTime();
        deathMesh.build(deaths.values());
        meshNanos += System.nanoTime() - m0;
        if (deathMesh.mesh.vertexCount > 0) f.draws.add(new Renderer.Draw(deathMesh.mesh, new Matrix4f(), null, false, true));
    }

    /** Medição das mortes (rodada 09.2): ns de lógica, máximo, quadros com efeito, pico de fragmentos, ns de montagem da malha. */
    private long fxNanos, fxMax, fxFrames, meshNanos;
    private int fxPeak;

    /** Efeitos de morte vivos (relatório, testes de roteiro). */
    public int deathCount() { return deaths.size(); }

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
        boolean defeatUi = logic.combat != null && logic.combat.outcome == Combat.Outcome.DEFEAT && logic.state == GameLogic.State.EXPLORE;
        if (drawCursor && (logic.state != GameLogic.State.EXPLORE || defeatUi) && mouseX >= 0) kit.cursor(mouseX, mouseY, s, false);
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
        // a frase quebra em linhas na largura do painel (em 1366×768 o passo inteiro da fonte de pixel passava da borda)
        for (String line : wrap("A classe não é definida pela aparência.", px(19), iw)) {
            kit.text(x, y, line, px(19), false, UiKit.INK_SOFT);
            y += px(19) * 1.25f;
        }
        y += 40 * s - px(19) * 1.25f;
        float bh = 54 * s, gap = 8 * s;
        int maxRows = Math.max(1, (int) ((py + ph - 24 * s - 70 * s - 66 * s - 40 * s - y) / (bh + gap)));
        // janela que segue a seleção: antes só os primeiros maxRows eram desenhados e a 10ª aparência (inclusive a recém-
        // criada, que entra selecionada no fim da lista) não aparecia
        int first = Math.clamp(logic.selected - maxRows + 1, 0, Math.max(0, logic.choices.size() - maxRows));
        if (first > 0) {
            kit.text(x + iw - kit.width("↑ " + first, px(18), false), y - px(18) * 1.4f, "↑ " + first, px(18), false, UiKit.INK_SOFT);
        }
        for (int i = first; i < Math.min(logic.choices.size(), first + maxRows); i++) {
            GameLogic.Choice c = logic.choices.get(i);
            boolean sel = i == logic.selected, hv = ("escolha:" + i).equals(hover);
            buttons.add(new Button("escolha:" + i, x, y, iw, bh, c.label(), false));
            kit.chamfer(x, y, iw, bh, 4 * s, sel ? UiKit.GOLD : hv ? UiKit.WOOD_L : UiKit.WOOD);
            kit.chamfer(x + 3 * s, y + 3 * s, iw - 6 * s, bh - 6 * s, 3 * s, sel ? UiKit.PARCH_L : c.valid() ? UiKit.PARCH_D : 0xcfc2b0ff);
            String tag = c.id().startsWith("preset:") ? "modelo" : c.id().startsWith("salva:") ? "salva" : "criada agora";
            String right = c.valid() ? tag : c.reason();
            // o nome usa só o espaço à esquerda da etiqueta e do marcador (nome longo de arquivo editado à mão passava
            // por cima da etiqueta ou da borda): primeiro um passo menor da fonte; se nem o menor couber, termina em "…"
            float room = iw - 36 * s - kit.width(right, px(18), false) - 34 * s;
            int p = textPx(c.label(), room, 22);
            String label = fit(c.label(), p, room);
            kit.text(x + 18 * s, y + bh / 2 - p * 0.66f, label, p, true, c.valid() ? UiKit.INK : UiKit.INK_SOFT);
            kit.text(x + iw - 18 * s - kit.width(right, px(18), false), y + bh / 2 - px(18) * 0.66f, right, px(18), false, UiKit.INK_SOFT);
            if (sel) kit.seed(x + iw - 40 * s - kit.width(right, px(18), false), y + bh / 2 - 4 * s, s);
            y += bh + gap;
        }
        int below = logic.choices.size() - (first + maxRows);
        if (below > 0)
            kit.text(x, y, "+" + below + " (use ↑ ↓)", px(18), false, UiKit.INK_SOFT);
        float by = py + ph - 24 * s - 70 * s;
        button("comecar", x, by, iw, 70 * s, "Entrar na vila", true);
        button("criador", x, by - 66 * s, iw, 56 * s, "Criar no editor", false);
        controlsHint(w, h, "↑ ↓ escolher · Enter entrar · Esc sair");
    }

    /** O texto inteiro se couber em {@code room}; senão o maior prefixo que cabe seguido de "…". */
    private String fit(String t, int p, float room) {
        if (kit.width(t, p, true) <= room) return t;
        int n = t.length();
        while (n > 1 && kit.width(t.substring(0, n).stripTrailing() + "…", p, true) > room) n--;
        return t.substring(0, n).stripTrailing() + "…";
    }

    /** Quebra o texto em linhas que cabem em {@code room} (px), por palavra; palavra maior que a linha fica sozinha. */
    private List<String> wrap(String t, int p, float room) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : t.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && kit.width(next, p, false) > room) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private void controlsHint(int w, int h, String t) {
        int p = px(19);
        float tw = kit.width(t, p, false) + 40 * s;
        kit.chamfer(28 * s, h - 28 * s - 44 * s, tw, 44 * s, 5 * s, 0x2a1e18b0);
        kit.text(48 * s, h - 28 * s - 22 * s - p * 0.66f, t, p, false, UiKit.PARCH_L);
    }

    private void drawExplore(int w, int h) {
        Combat cb = logic.combat;
        boolean arena = cb != null && cb.inArena(logic.player);
        if (cb != null) drawCombat(cb, w, h, arena);
        if (cb != null && cb.outcome == Combat.Outcome.DEFEAT) return;
        if (logic.helpVisible())
            controlsHint(w, h, arena ? "Clique atacar · direito defender · Alt (ou botão do meio) esquivar · Espaço pular · Q mira · Tab trocar alvo · F1"
                    : "WASD andar · Shift correr · mouse olhar · roda aproxima · E conversar · Esc pausa · F1 ajuda");
        GameLogic.Npc n = logic.candidate;
        if (n != null) {
            float gy = terrain.groundAt(n.def.x(), n.def.z());
            // a placa fica acima da cabeça de QUALQUER NPC (1,55 u era fixo: no Bramor, de 1,73 u, caía sobre o cabelo)
            float[] sc = project(n.def.x(), gy + Math.max(1.55f, n.def.height() + 0.30f), n.def.z(), w, h);
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

    // ------------------------------------------------------------------ rodada 09: combate na tela

    static float flash(float since) { return since < 0.14f ? 1f - since / 0.14f : 0f; }

    /**
     * Rodada 09.3 (P42) — câmera encurralada colada no herói: ele vira pontilhado (até 75% dos pixels somem) em vez de
     * encher a tela, entre 0,9 u e 0,45 u do olho ao peito. Só desenho; a câmera e o controle não mudam.
     */
    private float nearFade(Player p, float a) {
        // distância do olho ao EIXO do corpo (do quadril ao alto da cabeça): o cabelo colado na lente também conta
        float base = p.rVisualY(a), cy = cam.position().y;
        float ey = cy - Math.clamp(cy, base + 0.35f, base + 1.20f);
        float ex = cam.position().x - p.rx(a), ez = cam.position().z - p.rz(a);
        float d = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
        return Math.clamp((NEAR_FADE_FROM - d) / (NEAR_FADE_FROM - NEAR_FADE_TO), 0f, 1f) * 0.75f;
    }

    static final float NEAR_FADE_FROM = 0.9f, NEAR_FADE_TO = 0.45f;

    static String heroExpression(Hero h) {
        return switch (h.state) {
            case ATTACK, BLOCK -> "determined";
            case HIT, DEAD -> "surprise";
            default -> null;
        };
    }

    /** Coração de pixel (7 × 6) cheio, metade ou vazio. */
    private void heart(float x, float y, float u, int fill) {
        String[] rows = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < 7; c++) {
                if (rows[r].charAt(c) != '#') continue;
                int col = fill == 0 ? 0x5a3a30ff : (fill == 1 && c > 3) ? 0x5a3a30ff : (r == 1 && (c == 1 || c == 4) ? 0xff9c8aff : 0xd8443cff);
                kit.rect(x + c * u, y + r * u, u, u, col);
            }
    }

    /** Broquel de pixel (aceso = guarda erguida). */
    private void shieldIcon(float x, float y, float u, boolean lit) {
        String[] rows = {".#####.", "#######", "##.#.##", "#######", ".#####.", "..###.."};
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < 7; c++)
                if (rows[r].charAt(c) == '#') kit.rect(x + c * u, y + r * u, u, u, lit ? (rows[r].charAt(c) == '#' && r == 2 ? UiKit.GOLD : 0x3a958aff) : 0x6b5444a0);
    }

    private void drawCombat(Combat cb, int w, int h, boolean arena) {
        Hero hero = cb.hero;
        // dano recebido: vinheta quente curta nas bordas
        if (hero.hurtT < 0.35f) {
            int al = Math.round(110 * (1f - hero.hurtT / 0.35f));
            for (int i = 0; i < 10; i++) {
                int c = 0x9a2a2000 | Math.max(0, al - i * 11);
                float e = i * 6 * s;
                kit.rect(0, e, w, 6 * s, c); kit.rect(0, h - e - 6 * s, w, 6 * s, c);
                kit.rect(e, 0, 6 * s, h, c); kit.rect(w - e - 6 * s, 0, 6 * s, h, c);
            }
        }
        // acentos de impacto (pixel, curtos): raios que abrem a partir do ponto de contato
        for (Combat.Impact im : cb.impacts) {                            // só lê: a lista é da lógica (Combat.impacts)
            float[] sc = project(im.at().x, im.at().y, im.at().z, w, h);
            if (sc == null) continue;
            float k = Math.min(1f, (cb.clock - im.time()) / Combat.IMPACT_SHOW), u = Math.max(2f, 4 * s);
            int col = switch (im.kind()) { case "dano" -> 0xff7a52ff; case "defesa" -> 0xcfe8ffff; default -> 0xfff1c8ff; };
            int a8 = Math.round(255 * (1f - k));
            for (int r = 0; r < 8; r++) {
                double ang = r * Math.PI / 4 + 0.39;
                for (int j = 0; j < 3; j++) {
                    float d = (10 + 26 * k + j * 7) * s;
                    kit.rect(sc[0] + (float) Math.cos(ang) * d - u / 2, sc[1] + (float) Math.sin(ang) * d - u / 2, u, u, (col & 0xffffff00) | Math.max(0, a8 - j * 60));
                }
            }
        }
        // vida e guarda (canto superior esquerdo), só perto da arena ou em luta
        if (arena || cb.engaged() || hero.hp < cb.def.heroHp) {
            float u = Math.max(2f, 4.5f * s), x0 = 28 * s, y0 = 28 * s;
            int hearts = (cb.def.heroHp + 1) / 2;
            float pw = hearts * 9 * u + 9 * u + 34 * s;
            kit.chamfer(x0, y0, pw, 8 * u + 20 * s, 5 * s, 0x2a1e18c0);
            for (int i = 0; i < hearts; i++) heart(x0 + 14 * s + i * 9 * u, y0 + 10 * s + u, u, Math.clamp(hero.hp - i * 2, 0, 2));
            shieldIcon(x0 + 18 * s + hearts * 9 * u, y0 + 10 * s + u, u, hero.guardUp());
        }
        // rodada 09.2: inimigos comuns = barra pequena sobre eles (preferência); chefe = barra grande no topo; alvo travado
        enemyBars(cb, w, h);
        Enemy boss = cb.boss();
        if (GameLogic.bossBar(boss)) bossBar(boss, w);
        if (logic.targeting.target != null && logic.targeting.target.alive()) lockMarker(logic.targeting.target, w, h);
        if (debugCombat) drawCombatDebug(cb, w, h);
        if (cb.outcome == Combat.Outcome.DEFEAT) {
            kit.rect(0, 0, w, h, 0x1a1210a0);
            float pw = 620 * s, ph = 330 * s, x = (w - pw) / 2, y = (h - ph) / 2;
            kit.panel(x, y, pw, ph, s, true);
            kit.text(x + 40 * s, y + 34 * s, "Você caiu", px(40), true, UiKit.INK);
            kit.text(x + 40 * s, y + 96 * s, cb.enemies.size() == 1 ? "O " + cb.def.enemyName.toLowerCase() + " voltou para a toca."
                    : "Os inimigos voltaram para a toca.", px(21), false, UiKit.INK_SOFT);
            float bw2 = pw - 80 * s, bh2 = 62 * s;
            button("recomecar", x + 40 * s, y + 150 * s, bw2, bh2, "Tentar de novo  (Enter)", true);
            button("voltar-vila", x + 40 * s, y + 226 * s, bw2, bh2, "Voltar à vila", false);
        }
    }

    // ------------------------------------------------------------------ rodada 09.2: barras e marcador de alvo

    private final org.joml.Vector3f eyeTmp = new org.joml.Vector3f(), topTmp = new org.joml.Vector3f();

    /** Inimigo visível agora: na frente da câmera e sem sólido entre o olho e o alto do corpo. */
    private boolean seen(Enemy e) {
        logic.cam.position(eyeTmp);
        topTmp.set(e.x, e.y + 0.6f * e.scale, e.z);
        return logic.collision.solidBetween(eyeTmp, topTmp) == null;
    }

    /**
     * Barra pequena sobre cada inimigo comum vivo, conforme a preferência: "nunca"; "dano" (padrão: levou dano há menos de
     * interface.mostra s ou está travado); "sempre" (engajado e visível). Nunca para o chefe (ele tem a barra grande).
     * Escondida se o inimigo está atrás de sólido ou fora da tela. Sem nome: discreta.
     */
    private void enemyBars(Combat cb, int w, int h) {
        String mode = logic.prefs.bars;
        if (mode.equals("nunca")) return;
        for (Enemy e : cb.enemies) {
            if (!GameLogic.smallBar(mode, e, logic.targeting.target, cb.def.barShow) || !seen(e)) continue;
            float[] sc = project(e.rx(logic.alpha), e.rY(logic.alpha) + (0.98f) * e.scale, e.rz(logic.alpha), w, h);
            if (sc == null || sc[0] < -40 || sc[0] > w + 40 || sc[1] < -20 || sc[1] > h + 20) continue;
            float bw = 78 * s, bh = Math.max(4f, 7 * s), x = sc[0] - bw / 2, y = sc[1] - bh;
            float k = (float) e.hp / e.def.enemyHp;
            kit.rect(x - 2 * s, y - 2 * s, bw + 4 * s, bh + 4 * s, 0x2a1e18d0);
            kit.rect(x, y, bw, bh, 0x4a3430ff);
            kit.rect(x, y, bw * k, bh, 0xc8443cff);
            kit.rect(x, y, bw * k, Math.max(1, 2 * s), 0xff9c8a90);
            for (int i = 1; i < e.def.enemyHp; i++) kit.rect(x + bw * i / e.def.enemyHp, y, Math.max(1, s), bh, 0x2a1e1880);
        }
    }

    /** Barra de chefe: no topo, larga, com nome, moldura dourada e a marca de protótipo quando for recurso de teste. */
    private void bossBar(Enemy b, int w) {
        float bw = 720 * s, bh = 22 * s, x = (w - bw) / 2, y = 30 * s;
        int p = px(24);
        String name = b.name().replace(" (protótipo)", "");
        kit.text(x + 2 * s, y + 2 * s, name, p, true, 0x20140eb0);
        kit.text(x, y, name, p, true, 0xfff1c8ff);
        if (b.def.enemyPrototype) {                                        // recurso temporário: sempre legível (sombra)
            String tag = "protótipo de teste";
            float tx = x + bw - kit.width(tag, px(18), true);
            kit.text(tx + 2 * s, y + 4 * s, tag, px(18), true, 0x20140ec0);
            kit.text(tx, y + 2 * s, tag, px(18), true, 0xffd27aff);
        }
        float by = y + 36 * s;
        kit.chamfer(x - 6 * s, by - 6 * s, bw + 12 * s, bh + 12 * s, 5 * s, UiKit.GOLD);
        kit.chamfer(x - 3 * s, by - 3 * s, bw + 6 * s, bh + 6 * s, 4 * s, UiKit.WOOD_D);
        kit.rect(x, by, bw, bh, 0x3a2a26ff);
        float k = (float) b.hp / b.def.enemyHp;
        kit.rect(x, by, bw * k, bh, 0xa8323aff);
        kit.rect(x, by, bw * k, Math.max(1, 4 * s), 0xe0707a90);
        for (float t = 0.25f; t < 1f; t += 0.25f) kit.rect(x + bw * t, by, Math.max(1, 2 * s), bh, 0x1a120e90);
        // ponteiras em losango (identidade própria da barra de chefe)
        for (float ex : new float[]{x - 16 * s, x + bw + 16 * s}) {
            float c = by + bh / 2, r = 9 * s;
            for (int i = 0; i < 9; i++) {
                float hw = r * (1f - Math.abs(i - 4) / 4.5f);
                kit.rect(ex - hw, c - r + i * 2 * s, hw * 2, 2 * s, UiKit.GOLD);
            }
        }
    }

    /**
     * Marcador do alvo travado: quatro cantoneiras douradas em volta do corpo (não cobrem a cabeça nem a preparação: ficam
     * FORA do contorno do inimigo) e um losango pequeno no chão sob ele. Pulsa devagar.
     */
    private void lockMarker(Enemy e, int w, int h) {
        float a = logic.alpha, ex = e.rx(a), ey = e.rY(a), ez = e.rz(a);
        float r = (e.def.enemyHurt.r() + 0.1f * e.scale), top = ey + e.def.enemyHurt.to() + 0.12f * e.scale, bot = ey + 0.02f;
        float rx = (float) Math.cos(logic.cam.curYaw) * r, rz = -(float) Math.sin(logic.cam.curYaw) * r;
        float x0 = 1e9f, x1 = -1e9f, y0 = 1e9f, y1 = -1e9f;
        for (float[] q : new float[][]{{ex - rx, top, ez - rz}, {ex + rx, top, ez + rz}, {ex - rx, bot, ez - rz}, {ex + rx, bot, ez + rz}}) {
            float[] sc = project(q[0], q[1], q[2], w, h);
            if (sc == null) return;
            x0 = Math.min(x0, sc[0]); x1 = Math.max(x1, sc[0]); y0 = Math.min(y0, sc[1]); y1 = Math.max(y1, sc[1]);
        }
        float pulse = 1f + 0.06f * (float) Math.sin(logic.time * 5f), cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        float hw = (x1 - x0) / 2 * pulse + 8 * s, hh = (y1 - y0) / 2 * pulse + 8 * s, len = Math.max(10 * s, Math.min(hw, hh) * 0.35f), t = Math.max(2f, 3 * s);
        int col = UiKit.GOLD, sh = 0x20140ea0;
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sy = -1; sy <= 1; sy += 2) {
                float px = cx + sx * hw, py = cy + sy * hh;
                for (int k = 0; k < 2; k++) {                              // sombra e traço
                    int c = k == 0 ? sh : col;
                    float o = k == 0 ? 2 * s : 0f;
                    kit.rect(Math.min(px, px - sx * len) + o, py - (sy > 0 ? t : 0) + o, len, t, c);
                    kit.rect(px - (sx > 0 ? t : 0) + o, Math.min(py, py - sy * len) + o, t, len, c);
                }
            }
        float[] g = project(ex, ey + 0.02f, ez, w, h);
        if (g != null) {
            float d = 7 * s;
            for (int i = 0; i < 7; i++) {
                float hw2 = d * (1f - Math.abs(i - 3) / 3.5f);
                kit.rect(g[0] - hw2, g[1] + 12 * s + i * 2 * s, hw2 * 2, 2 * s, col);
            }
        }
    }

    /** Modo técnico: hurtboxes (verde), lâmina na janela de dano (vermelho), cabeçada ativa (laranja), estados. */
    private void drawCombatDebug(Combat cb, int w, int h) {
        Player p = logic.player;
        Hero hero = cb.hero;
        Enemy e = logic.targeting.target != null ? logic.targeting.target : cb.enemy;
        for (float[] sgm : hero.debugBlade) dbgTrail.add(new float[]{cb.clock, 0, sgm[0], sgm[1], sgm[2], sgm[3], sgm[4], sgm[5]});
        for (Enemy o : cb.enemies) for (float[] q : o.debugHit) dbgTrail.add(new float[]{cb.clock, 1, q[0], q[1], q[2], o.def.hitR});
        while (!dbgTrail.isEmpty() && cb.clock - dbgTrail.peekFirst()[0] > 0.25f) dbgTrail.pollFirst();
        capsule(p.x, p.y + cb.def.heroHurt.from(), p.z, p.y + cb.def.heroHurt.to(), cb.def.heroHurt.r(), hero.evading() ? 0x6ab8ffff : 0x5fe07aff, w, h);
        for (Enemy o : cb.enemies)
            if (o.alive()) capsule(o.x, o.y + o.def.enemyHurt.from(), o.z, o.y + o.def.enemyHurt.to(), o.def.enemyHurt.r(), 0x5fe07aff, w, h);
        for (float[] t : dbgTrail) {
            if (t[1] == 0) seg(t[2], t[3], t[4], t[5], t[6], t[7], 0xff4040ff, w, h);
            else ring(t[2], t[3], t[4], t.length > 5 ? t[5] : cb.def.hitR, 0xffa030ff, w, h);
        }
        String info = String.format("herói %s %s t=%.2f vida %d pernas %.2f%s  |  %s#%d %s t=%.2f vida %d  |  pausa %.2f  |  sensação %s", hero.state,
                hero.layer.move() == null ? "-" : hero.layer.move(), hero.layer.moveT(), hero.hp, hero.legW, hero.evading() ? " INVULNERÁVEL" : "",
                e.name(), e.id, e.state, e.stateT, e.hp, cb.hitstop, hero.feel.name());
        kit.chamfer(24 * s, h - 150 * s, kit.width(info, px(18), false) + 40 * s, 40 * s, 4 * s, 0x000000b0);
        kit.text(44 * s, h - 140 * s, info, px(18), false, 0xffffffff);
    }

    private void seg(float ax, float ay, float az, float bx, float by, float bz, int col, int w, int h) {
        float[] a = project(ax, ay, az, w, h), b = project(bx, by, bz, w, h);
        if (a == null || b == null) return;
        int n = Math.max(2, (int) (Math.hypot(b[0] - a[0], b[1] - a[1]) / (2.5f * s)));
        float u = Math.max(3f, 4 * s);                                    // legível também em prancha reduzida
        for (int i = 0; i <= n; i++) kit.rect(a[0] + (b[0] - a[0]) * i / n - u / 2, a[1] + (b[1] - a[1]) * i / n - u / 2, u, u, col);
    }

    private void ring(float x, float y, float z, float r, int col, int w, int h) {
        for (int i = 0; i < 16; i++) {
            double a0 = i * Math.PI / 8, a1 = (i + 1) * Math.PI / 8;
            seg(x + r * (float) Math.cos(a0), y, z + r * (float) Math.sin(a0), x + r * (float) Math.cos(a1), y, z + r * (float) Math.sin(a1), col, w, h);
        }
    }

    private void capsule(float x, float y0, float z, float y1, float r, int col, int w, int h) {
        ring(x, y0, z, r, col, w, h);
        ring(x, y1, z, r, col, w, h);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            seg(x + r * (float) Math.cos(a), y0, z + r * (float) Math.sin(a), x + r * (float) Math.cos(a), y1, z + r * (float) Math.sin(a), col, w, h);
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

    /**
     * Fileira "rótulo … [−] valor [+]" da pausa (sensibilidade e volumes, rodada 09.4). O rótulo cabe no espaço à
     * esquerda dos botões (escala inteira que cabe, como nos botões: em 720p ele era atravessado pelo "−") e o valor
     * fica centrado entre − e +.
     */
    private void stepperRow(String id, String label, String value, float bx, float bw, float y) {
        float minusX = bx + bw - 236 * s, plusX = bx + bw - 56 * s, room = minusX - bx - 12 * s;
        int lp = textPx(label, room, 21);
        kit.text(bx, y + 25 * s - lp * 0.66f, label, lp, true, UiKit.INK);
        button(id + "-", minusX, y, 56 * s, 50 * s, "-", false);
        int vp = textPx("100%", plusX - minusX - 56 * s - 8 * s, 22);
        float cx = (minusX + 56 * s + plusX) / 2;
        kit.text(cx - kit.width(value, vp, true) / 2, y + 25 * s - vp * 0.66f, value, vp, true, UiKit.INK);
        button(id + "+", plusX, y, 56 * s, 50 * s, "+", false);
    }

    private void drawPause(int w, int h) {
        kit.rect(0, 0, w, h, 0x1a1210a0);
        boolean cmb = logic.combat != null;
        float pw = 620 * s, ph = (cmb ? 990 : 800) * s, x = (w - pw) / 2, y = Math.max(8 * s, (h - ph) / 2);
        kit.panel(x, y, pw, ph, s, true);
        kit.text(x + 40 * s, y + 34 * s, "Pausa", px(40), true, UiKit.INK);
        float bx = x + 40 * s, bw = pw - 80 * s, bh = 62 * s, by = y + 110 * s;
        button("continuar", bx, by, bw, bh, "Continuar", true);
        button("trocar-aparencia", bx, by + 76 * s, bw, bh, "Trocar aparência", false);
        button("criador", bx, by + 152 * s, bw, bh, "Abrir o criador", false);
        // sensibilidade do mouse
        float sy = by + 240 * s;
        stepperRow("sens", "Sensibilidade", String.format("%.1f", logic.cam.sensitivity / 0.0032f), bx, bw, sy);
        String inv = logic.cam.invertY ? "Inverter vertical: sim" : "Inverter vertical: não";
        button("inverter", bx, sy + 66 * s, bw, 50 * s, inv, false);
        // som: volumes do jogador (música; efeitos = combate, passos, interface e ambiente), gravados nas preferências, e mudo
        stepperRow("mus", "Música", AudioPrefs.percent(audioPrefs.music), bx, bw, sy + 132 * s);
        stepperRow("efe", "Efeitos", AudioPrefs.percent(audioPrefs.effects), bx, bw, sy + 198 * s);
        button("mudo", bx, sy + 264 * s, bw, 50 * s, sfx.muted ? "Som: mudo (M)" : "Som: ligado (M)", false);
        if (cmb) {                                                         // rodada 09.2: combate
            float cy = sy + 334 * s;
            String bars = switch (logic.prefs.bars) { case "nunca" -> "nunca"; case "sempre" -> "sempre"; default -> "com dano ou alvo"; };
            button("barras", bx, cy, bw, 50 * s, "Barras: " + bars, false);
            button("sensacao", bx, cy + 62 * s, bw, 50 * s, "Sensação: " + logic.prefs.feel, false);
            String enc = switch (logic.combat.encounter) { case "grupo" -> "três fungos"; case "chefe" -> "chefe + 2"; default -> "um fungo"; };
            button("encontro", bx, cy + 124 * s, bw, 50 * s, "Arena: " + enc, false);
        }
        button("sair", bx, y + ph - 40 * s - bh, bw, bh, "Sair do jogo", false);
    }

    // ------------------------------------------------------------------ janela interativa

    public void run(int maxFrames) {
        double last = window.time(), start = last;
        int frames = 0;
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
                    case "mus-", "mus+", "efe-", "efe+" -> {
                        if (audioPrefs.nudge(b.id.startsWith("mus") ? "musica" : "efeitos", b.id.endsWith("+") ? 1 : -1)) {
                            applyVolumes();
                            audioPrefs.save(AudioPrefs.FILE);
                        }
                        vx.engine.audio.Audio.event("ui.selecionar");             // referência do novo nível
                    }
                    case "mudo" -> setMuted(!sfx.muted);
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
        input.releaseAll();                     // teclas soltas com o criador aberto não ficam "apertadas" na volta
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
        vx.engine.audio.Audio.recording(false);                          // rodada 09.4: áudio gravado = quadros gravados
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
                case "record" -> {
                    record = t[1].equals("on");
                    vx.engine.audio.Audio.recording(record);                  // áudio gravado = quadros gravados
                    if (record && logic.combat != null) {                  // rodada 09.1: referência para alinhar som e quadros
                        recordTick = logic.combat.ticks;
                        recordFrame = recorded;
                        System.out.printf("gravação: começa no passo %d, quadro %d%n", recordTick, recordFrame);
                    }
                }
                case "som-tocar" -> {                                      // rodada 09.1: toca um efeito (teste do dispositivo real)
                    long tk = logic.combat != null ? logic.combat.ticks + (++testCue) * 1000 : ++testCue;
                    int v = sfx.cue(t[1], tk, 0f, 0f, 0f);
                    System.out.printf("som-tocar: %s voz %d em %d ms (relógio do sistema)%n", t[1], v, System.currentTimeMillis());
                }
                case "som-medir" -> {                                      // rodada 09.1: custo de um pedido de som em regime (N pedidos)
                    int n = Integer.parseInt(t[1]);
                    float master = sfx.master;
                    sfx.setVolumes(0f, sfx.effects, sfx.muted);              // mede sem fazer barulho
                    long[] d = new long[n];
                    for (int i = 0; i < n; i++) {
                        long a = System.nanoTime();
                        sfx.cue("passo_terra", 1_000_000L + i * 100L, 0f, 0f, 0f);
                        d[i] = System.nanoTime() - a;
                    }
                    sfx.stopAll();
                    sfx.setVolumes(master, sfx.effects, sfx.muted);
                    long[] first = java.util.Arrays.copyOf(d, Math.min(50, n));
                    long[] last = java.util.Arrays.copyOfRange(d, n / 2, n);
                    java.util.Arrays.sort(first); java.util.Arrays.sort(last);
                    System.out.printf("som-medir: %s; primeiros 50: mediana %.1f µs; em regime (%d últimos): mediana %.1f µs, p95 %.1f µs, máx %.1f µs%n",
                            sfx.backend.describe(), first[first.length / 2] / 1e3, last.length, last[last.length / 2] / 1e3,
                            last[(int) (last.length * 0.95)] / 1e3, last[last.length - 1] / 1e3);
                }
                case "esperar-real" -> {                                   // espera em tempo de parede (o som real toca)
                    try { Thread.sleep(Math.round(Float.parseFloat(t[1]) * 1000)); } catch (InterruptedException ignored) { }
                    System.out.printf("som: dispositivo informa latência de saída %.1f ms; vozes tocando %d%n",
                            sfx.backend.reportedLatency() * 1000, sfx.backend.activeVoices());
                }
                case "sons" -> {                                           // rodada 09.1: o que o Sfx tocou (passo, efeito, arquivo)
                    for (Sfx.Played pl : sfx.played)
                        System.out.printf("som: passo %d (%.3f s) %s %s ganho %.2f tom %.3f%s%n", pl.tick(), (pl.tick() - recordTick) * GameLogic.TICK,
                                pl.cue(), pl.file(), pl.gain(), pl.pitch(), pl.spatial() ? String.format(" em (%.2f, %.2f, %.2f)", pl.x(), pl.y(), pl.z()) : "");
                }
                case "criador" -> {                                        // abre o criador com um roteiro dele (mesma janela)
                    logic.openCreator = false;
                    if (window.cursorCaptured()) window.captureCursor(false);
                    try (App app = new App(window, assetsPath); Creator cr = new Creator(app, window, assetsPath)) {
                        cr.cs.finishLabel = "Levar para a vila";
                        if (logic.appearance != null) cr.start(logic.appearance, logic.choices.get(logic.selected).label());
                        cr.recorded = recorded;
                        cr.script(Path.of(t[1]), out, fps);
                        recorded = cr.recorded;
                        vx.engine.audio.Audio.recording(record);
                        if (cr.cs.finished) logic.useCreated(cr.cs.appearance, cr.cs.name.isBlank() ? "Criada agora" : cr.cs.name.trim());
                    }
                    input.releaseAll();
                    logic.action("trocar-aparencia");
                    settle(w, h, dt, out);
                }
                case "camera" -> System.out.printf("camera: pivô %.6f %.6f %.6f rumo %.6f incl %.6f dist %.6f desvio %.6f/%.6f herói visualY %.6f%n",
                        logic.cam.pivot.x, logic.cam.pivot.y, logic.cam.pivot.z, logic.cam.curYaw, logic.cam.curPitch, logic.cam.dist,
                        logic.cam.avoidYaw, logic.cam.avoidPitch, logic.player.visualY);
                case "enquadramento" -> {                                  // rodada 09.1: o que a câmera mostra da luta (CameraFraming)
                    Enemy e = logic.combat.enemy;
                    Player pl = logic.player;
                    System.out.println("enquadramento" + (t.length > 1 ? " " + t[1] : "") + ": " + CameraFraming.measure(logic.cam, pl.x, pl.visualY, pl.z,
                            e.x, e.y, e.z, logic.collision));
                }
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
                // ---- rodada 09: combate
                case "atacar" -> {                                         // clique esquerdo (aperta e solta)
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.DOWN, mx, my, 0, 0, 0)), dt, w, h, record, out);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.UP, mx, my, 0, 0, 0)), dt, w, h, record, out);
                }
                case "defesa" -> tickFrame(List.of(new InputEvent(t[1].equals("on") ? InputEvent.Kind.DOWN : InputEvent.Kind.UP, mx, my, 1, 0, 0)),
                        dt, w, h, record, out);
                case "combate" -> {
                    Combat cb = logic.combat;
                    Player pl = logic.player;
                    System.out.printf("combate: herói %s (%s t=%.2f) vida %d em (%.2f, %.2f) | inimigo %s t=%.2f vida %d em (%.2f, %.2f) a %.2f u | %s%n",
                            cb.hero.state, cb.hero.layer.move(), cb.hero.layer.moveT(), cb.hero.hp, pl.x, pl.z, cb.enemy.state, cb.enemy.stateT,
                            cb.enemy.hp, cb.enemy.x, cb.enemy.z, Math.hypot(cb.enemy.x - pl.x, cb.enemy.z - pl.z), cb.outcome);
                    String wall = logic.collision.solidBetween(new org.joml.Vector3f(cb.enemy.x, cb.enemy.y + 0.5f, cb.enemy.z),
                            new org.joml.Vector3f(pl.x, pl.y + 0.8f, pl.z));
                    System.out.printf("combate: o fungo %s o herói (visão %s)%n", cb.enemy.sees(pl, logic.collision) ? "vê" : "não vê",
                            wall == null ? "livre" : "cortada por " + wall);
                }
                case "eventos" -> logic.combat.echo = t[1].equals("on");
                case "esperar-resultado" -> {                              // espera vitória/derrota (painel na tela) + extra
                    int n = Math.round(Float.parseFloat(t[1]) * fps);
                    for (int i = 0; i < n && logic.combat.outcome == Combat.Outcome.NONE; i++) tickFrame(List.of(), dt, w, h, record, out);
                    tickFrame(List.of(), dt, w, h, record, out);
                    render(null, w, h, 1f, dt);                            // monta os botões do painel
                }
                case "vida-heroi" -> logic.combat.hero.hp = Integer.parseInt(t[1]);   // montagem técnica (vídeos curtos)
                case "vida-inimigo" -> {                                   // rodada 09.2: montagem técnica — vida N [#i]
                    int idx = t.length > 2 ? Integer.parseInt(t[2].substring(1)) : 0;
                    if (idx < logic.combat.enemies.size()) logic.combat.enemies.get(idx).hp = Integer.parseInt(t[1]);
                }
                case "posicionar" -> {                                     // montagem técnica de cena (QA): herói em x z rumo°
                    logic.player.place(Float.parseFloat(t[1]), Float.parseFloat(t[2]), (float) Math.toRadians(Float.parseFloat(t[3])), logic.collision);
                    logic.player.snapshot();
                    logic.cam.yaw = logic.player.yaw + (float) Math.PI;
                    logic.cam.curYaw = logic.cam.yaw;
                    logic.cam.snapshot();
                }
                case "camera-recompor" -> { logic.cam.reset(); logic.cam.snapshot(); }   // rodada 09.1: esquece a suavização (montagem)
                case "inimigo" -> {                                        // montagem técnica: inimigo [#N] em x z rumo° [ESTADO]
                    int o = t[1].startsWith("#") ? 1 : 0;
                    Enemy e = o == 1 ? logic.combat.enemies.get(Integer.parseInt(t[1].substring(1))) : logic.combat.enemy;
                    e.x = Float.parseFloat(t[1 + o]); e.z = Float.parseFloat(t[2 + o]); e.y = logic.collision.ground.height(e.x, e.z);
                    e.yaw = (float) Math.toRadians(Float.parseFloat(t[3 + o]));
                    if (t.length > 4 + o) { e.state = Enemy.State.valueOf(t[4 + o]); e.stateT = 0f; }
                    e.snapshot();
                }
                // ---- rodada 09.2
                case "segurar", "soltar" -> {                              // teclas seguradas até "soltar" (andar enquanto ataca)
                    List<InputEvent> evs = new ArrayList<>();
                    for (String k : t[1].split("\\+"))
                        evs.add(new InputEvent(t[0].equals("segurar") ? InputEvent.Kind.KEY : InputEvent.Kind.KEY_UP, mx, my, 0, 0, keyCode(k)));
                    tickFrame(evs, dt, w, h, record, out);
                }
                case "esquiva" -> {                                        // Alt (com as teclas já seguradas: direção) ou botão do meio
                    if (t.length > 1 && t[1].equals("meio")) {
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.DOWN, mx, my, 2, 0, 0)), dt, w, h, record, out);
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.UP, mx, my, 2, 0, 0)), dt, w, h, record, out);
                    } else {
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_LEFT_ALT)), dt, w, h, record, out);
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_LEFT_ALT)), dt, w, h, record, out);
                    }
                }
                case "pular" -> {
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_SPACE)), dt, w, h, record, out);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_SPACE)), dt, w, h, record, out);
                }
                case "mira" -> {                                           // Q
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_Q)), dt, w, h, record, out);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_Q)), dt, w, h, record, out);
                    System.out.println("mira: " + (logic.targeting.locked() ? "travada em " + logic.targetName() : "livre"));
                }
                case "trocar" -> {                                         // Tab (ou Shift+Tab com "anterior")
                    boolean back = t.length > 1 && t[1].equals("anterior");
                    List<InputEvent> d0 = new ArrayList<>(), u0 = new ArrayList<>();
                    if (back) { d0.add(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_LEFT_SHIFT)); }
                    d0.add(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_TAB));
                    u0.add(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_TAB));
                    if (back) u0.add(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_LEFT_SHIFT));
                    tickFrame(d0, dt, w, h, record, out);
                    tickFrame(u0, dt, w, h, record, out);
                    System.out.println("mira: alvo " + logic.targetName());
                }
                case "encontro" -> logic.setEncounter(t[1]);
                case "sensacao" -> logic.applyFeel(t[1]);
                case "barras" -> logic.prefs.bars = Prefs.valid(t[1], logic.prefs.bars);
                case "morte" -> setDeathStyle(t[1]);
                case "grupo" -> {
                    Player pl = logic.player;
                    StringBuilder sb = new StringBuilder("grupo:");
                    for (Enemy e : logic.combat.enemies)
                        sb.append(String.format(" | #%d %s %s vida %d a %.2f u", e.id, e.name(), e.state, e.hp, Math.hypot(e.x - pl.x, e.z - pl.z)));
                    sb.append(String.format(" | mortes em andamento %d", deathCount()));
                    System.out.println(sb);
                }
                case "esperar-inimigo" -> {                                // espera o inimigo chegar a um estado (reação no momento certo)
                    Enemy.State want = Enemy.State.valueOf(t[1]);
                    int n = Math.round(Float.parseFloat(t[2]) * fps);
                    Enemy we = t.length > 4 ? logic.combat.enemies.get(Integer.parseInt(t[4].substring(1))) : null;
                    for (int i = 0; i < n && (we != null ? we.state != want : logic.combat.enemies.stream().noneMatch(x -> x.state == want)); i++)
                        tickFrame(List.of(), dt, w, h, record, out);
                    if (t.length > 3) {                                    // e mais um pouco dentro do estado
                        int m = Math.round(Float.parseFloat(t[3]) * fps);
                        for (int i = 0; i < m; i++) tickFrame(List.of(), dt, w, h, record, out);
                    }
                }
                case "esquivar" -> {                                       // escolhe as teclas (WASD, relativas à câmera) mais perto da
                    Enemy e = logic.combat.enemy;                          // direção pedida em relação ao fungo e segura com Shift
                    Player pl = logic.player;
                    float toE = (float) Math.atan2(e.x - pl.x, e.z - pl.z);
                    float want = switch (t[1]) { case "direita" -> toE - (float) Math.PI / 2; case "esquerda" -> toE + (float) Math.PI / 2; default -> toE + (float) Math.PI; };
                    float fx = logic.cam.forwardX(), fz = logic.cam.forwardZ();
                    String best = "W";
                    float bestD = 9;
                    for (String[] kv : new String[][]{{"W", "0", "1"}, {"S", "0", "-1"}, {"D", "1", "0"}, {"A", "-1", "0"}, {"W+D", "1", "1"}, {"W+A", "-1", "1"}, {"S+D", "1", "-1"}, {"S+A", "-1", "-1"}}) {
                        float r = Float.parseFloat(kv[1]), f = Float.parseFloat(kv[2]);
                        float ix = fx * f - fz * r, iz = fz * f + fx * r;          // mesma conta do GameLogic
                        float d = Math.abs(Hero.angle((float) Math.atan2(ix, iz) - want));
                        if (d < bestD) { bestD = d; best = kv[0]; }
                    }
                    System.out.printf("esquiva: %s → teclas %s+SHIFT (desvio %.0f°)%n", t[1], best, Math.toDegrees(bestD));
                    String[] ks = (best + "+SHIFT").split("\\+");
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
                case "encarar" -> {                                        // gira a câmera (pelo mouse) até o inimigo ficar à frente
                    Enemy e = logic.combat.enemy;
                    float want = (float) Math.atan2(logic.player.x - e.x, logic.player.z - e.z);
                    float d = (float) Math.atan2(Math.sin(want - logic.cam.yaw), Math.cos(want - logic.cam.yaw));
                    int n = Math.max(1, Math.round((t.length > 1 ? Float.parseFloat(t[1]) : 0.4f) * fps));
                    float pixels = -d / logic.cam.sensitivity;
                    for (int i = 0; i < n; i++) {
                        mx += pixels / n;
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, mx, my, 0, 0, 0)), dt, w, h, record, out);
                    }
                }
                case "debug" -> {                                          // diagnóstico: sombra on|off, raio N|auto, golpes on|off
                    if (t[1].equals("golpes")) debugCombat = t[2].equals("on");
                    if (t[1].equals("camera-combate")) FollowCamera.combatAssist = t[2].equals("on");
                    if (t[1].equals("camera-09")) FollowCamera.legacy09 = t[2].equals("on");   // rodada 09.1: câmera de luta da 09 (antes × depois)
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
                    if (t[1].equals("on")) {
                        perf = new ArrayList<>(); perfLogic = new ArrayList<>(); perfRender = new ArrayList<>();
                        if (logic.combat != null) { logic.combat.heroNanos = logic.combat.enemyNanos = logic.combat.hitNanos = logic.combat.measuredTicks = 0; }
                        logic.camNanos = logic.camTicks = 0;
                        fxNanos = fxMax = fxFrames = meshNanos = 0;
                        fxPeak = 0;
                        Actor.animNanos = 0; Actor.animCount = 0;
                        sfx.cueNanos = sfx.cueCount = sfx.cueMax = 0;
                        gc0 = gcStats();
                    }
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
                case "amostra" -> sample(out.resolve(t[1]));               // sessão longa: uma linha de recursos por chamada
                case "tempo-real" -> {                                     // quadros no ritmo do relógio (o áudio real reabastece)
                    int n = Math.round(Float.parseFloat(t[1]) * fps);
                    long t0 = System.nanoTime();
                    for (int i = 0; i < n; i++) {
                        tickFrame(List.of(), dt, w, h, record, out);
                        long due = t0 + Math.round((i + 1) * dt * 1e9), now = System.nanoTime();
                        if (due > now) try { Thread.sleep((due - now) / 1_000_000, (int) ((due - now) % 1_000_000)); } catch (InterruptedException ignored) { }
                    }
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
        saveRecordedAudio(out);
    }

    private int recorded, recordFrame;
    private long recordTick;
    private int testCue;
    private List<Double> perf, perfLogic, perfRender;

    /** Sessão longa: tempo de cada quadro (ms, com glFinish) desde a última {@code amostra}. */
    private final List<Double> sampleFrames = new ArrayList<>();
    private long sampleT0;

    private void tickFrame(List<InputEvent> evs, float dt, int w, int h, boolean record, Path out) {
        long t0 = System.nanoTime();
        window.poll();
        for (InputEvent e : evs) window.inject(e);
        uiAction(window.events());
        tick(window.events(), dt, w, h);
        audioRender(dt, record);
        if (perf != null) {
            long t1 = System.nanoTime();
            render(null, w, h, 1f, dt);
            glFinish();
            long t2 = System.nanoTime();
            perf.add((t2 - t0) / 1e6);
            perfLogic.add((t1 - t0) / 1e6);
            perfRender.add((t2 - t1) / 1e6);
            sampleFrames.add((t2 - t0) / 1e6);
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

    /**
     * Uma linha CSV de recursos (sessão longa): relógio, tempo do jogo, heap antes e depois de um GC pedido, memória
     * não-heap, buffers diretos, RSS do processo, malhas/texturas/FBOs vivos, buffers/fluxos/vozes do OpenAL, threads,
     * coletas e o tempo dos quadros desde a amostra anterior (com {@code perf on}).
     */
    private void sample(Path f) throws IOException {
        if (sampleT0 == 0) sampleT0 = System.nanoTime();
        Runtime rt = Runtime.getRuntime();
        long heap = rt.totalMemory() - rt.freeMemory();
        System.gc();
        long heapGc = rt.totalMemory() - rt.freeMemory();
        long direct = 0;
        for (var p : java.lang.management.ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class))
            if (p.getName().equals("direct")) direct = p.getMemoryUsed();
        long nonHeap = java.lang.management.ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
        long rss = 0;
        for (String l : Files.readAllLines(Path.of("/proc/self/status")))
            if (l.startsWith("VmRSS:")) rss = Long.parseLong(l.replaceAll("[^0-9]", "")) * 1024;
        long gcN = 0, gcMs = 0;
        for (var g : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) { gcN += g.getCollectionCount(); gcMs += g.getCollectionTime(); }
        vx.engine.audio.OpenAlOut al = vx.engine.audio.Audio.output();
        List<Double> fr = new ArrayList<>(sampleFrames);
        sampleFrames.clear();
        java.util.Collections.sort(fr);
        java.util.function.DoubleUnaryOperator q = p -> fr.isEmpty() ? 0 : fr.get(Math.min(fr.size() - 1, (int) Math.floor(p * fr.size())));
        boolean head = !Files.exists(f);
        String line = String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%.1f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%d,%d,%d%n",
                (System.nanoTime() - sampleT0) / 1e9, logic.time, heap / 1048576.0, heapGc / 1048576.0, nonHeap / 1048576.0,
                direct / 1048576.0, rss / 1048576.0, vx.engine.Mesh.liveMeshes, vx.engine.Mesh.liveBytes / 1048576.0,
                vx.engine.Texture.live, vx.engine.Framebuffer.live, al == null ? -1 : al.loadedBuffers(), al == null ? -1 : al.liveStreams(),
                al == null ? -1 : al.liveVoices(), java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount(), gcN, gcMs,
                fr.size(), q.applyAsDouble(0.5), q.applyAsDouble(0.95), q.applyAsDouble(0.99), fr.isEmpty() ? 0 : fr.getLast(),
                perf == null ? 0 : perf.size(), logic.combat == null ? 0 : logic.combat.events.size(), logic.choices.size());
        Files.writeString(f, (head ? "relogio_s,jogo_s,heap_mb,heap_pos_gc_mb,nao_heap_mb,diretos_mb,rss_mb,malhas,malhas_mb,texturas,fbos,"
                + "al_buffers,al_fluxos,al_vozes,threads,gc_coletas,gc_ms,quadros,quadro_mediana_ms,quadro_p95_ms,quadro_p99_ms,quadro_max_ms,"
                + "perf_quadros_guardados,eventos_combate,escolhas\n" : "")
                + line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private void report(Path f) throws IOException {
        StringBuilder sb = new StringBuilder("# Jogo (rodada 08): tempo por quadro (lógica + desenho com glFinish), janela oculta\n");
        sb.append(stats("quadro (ms)", perf)).append(stats("lógica: entrada, corpo, colisão, câmera, NPCs (ms)", perfLogic))
                .append(stats("desenho: cena + personagens + interface (ms)", perfRender));
        sb.append(String.format("draws %d, triângulos %d; formas de colisão %d; cena montada em %.0f ms (malha %s); malhas vivas %d (%.1f MB)%n",
                renderer.lastDrawCount, renderer.lastTriangles, logic.collision.shapes.size(), worldBuildMs,
                vx.engine.VoxelMesher.conforming ? "conforme" : "legado", vx.engine.Mesh.liveMeshes, vx.engine.Mesh.liveBytes / 1048576.0));
        sb.append(String.format("som (rodada 09.1): %s; %d pedidos na medição, %.1f µs em média e %.1f µs no máximo cada, %.3f ms no total (thread do jogo; a mixagem é na thread do OpenAL)%n",
                sfx.backend.describe(), sfx.cueCount, sfx.cueCount == 0 ? 0 : sfx.cueNanos / 1e3 / sfx.cueCount, sfx.cueMax / 1e3, sfx.cueNanos / 1e6));
        StringBuilder slow = new StringBuilder();
        int nslow = 0;
        for (int i = 0; i < perf.size(); i++)
            if (perf.get(i) > 20) { nslow++; if (slow.length() < 600) slow.append(String.format(" #%d=%.1f(lógica %.1f)", i, perf.get(i), perfLogic.get(i))); }
        sb.append(String.format("quadros acima de 20 ms: %d de %d:%s%n", nslow, perf.size(), slow));
        Combat cb = logic.combat;
        if (cb != null && cb.measuredTicks > 0) {                          // rodada 09: custo do combate por passo lógico
            double n = cb.measuredTicks;
            sb.append(String.format("combate (por passo de 1/120 s, %d passos): herói %.3f ms (estado + corpo + lâmina), inimigo %.3f ms (IA + corpo + cabeçada), "
                    + "só detecção de golpe %.3f ms; animação no desenho %.3f ms/avaliação (%d avaliações)%n", cb.measuredTicks, cb.heroNanos / n / 1e6,
                    cb.enemyNanos / n / 1e6, cb.hitNanos / n / 1e6, Actor.animCount > 0 ? Actor.animNanos / (double) Actor.animCount / 1e6 : 0, Actor.animCount));
            int ne = cb.enemies.size();
            sb.append(String.format("rodada 09.2: encontro %s (%d inimigos, %d vivos no fim); IA + corpo + cabeçada por inimigo %.4f ms/passo; câmera %.4f ms/passo (%s); "
                    + "morte em fragmentos: %.4f ms de lógica por quadro em média, %.3f ms no máximo, até %d fragmentos vivos, montagem da malha %.4f ms/quadro%n",
                    cb.encounter, ne, cb.alive(), cb.enemyNanos / n / 1e6 / Math.max(1, ne), logic.camTicks == 0 ? 0 : logic.camNanos / (double) logic.camTicks / 1e6,
                    logic.targeting.locked() ? "travada" : "livre", fxFrames == 0 ? 0 : fxNanos / (double) fxFrames / 1e6, fxMax / 1e6, fxPeak,
                    fxFrames == 0 ? 0 : meshNanos / (double) fxFrames / 1e6));
            long[] g1 = gcStats();
            sb.append(String.format("coletas de lixo durante a medição: %d (%d ms no total)%n", g1[0] - gc0[0], g1[1] - gc0[1]));
            Runtime rt = Runtime.getRuntime();
            sb.append(String.format("memória Java em uso %.0f MB (heap %.0f MB)%n", (rt.totalMemory() - rt.freeMemory()) / 1048576.0, rt.totalMemory() / 1048576.0));
        }
        Files.writeString(f, sb.toString());
        System.out.print(sb);
    }

    private long[] gc0 = {0, 0};

    /** Coletas de lixo da JVM até agora: {quantidade, ms}. */
    static long[] gcStats() {
        long n = 0, ms = 0;
        for (var b : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, b.getCollectionCount());
            ms += Math.max(0, b.getCollectionTime());
        }
        return new long[]{n, ms};
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
            case "R" -> GLFW_KEY_R; case "F3" -> GLFW_KEY_F3; case "M" -> GLFW_KEY_M;
            case "Q" -> GLFW_KEY_Q; case "TAB" -> GLFW_KEY_TAB; case "ALT" -> GLFW_KEY_LEFT_ALT;
            default -> throw new IllegalArgumentException("tecla desconhecida: " + name);
        };
    }

    @Override
    public void close() {
        if (sfx != null) sfx.close();
        if (captureFbo != null) captureFbo.close();
        if (deathMesh != null) deathMesh.mesh.close();
        async.close();
        renderer.close();
        kit.close();
        world.close();
        assets.close();
    }
}
