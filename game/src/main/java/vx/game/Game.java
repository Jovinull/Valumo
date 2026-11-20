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
    /**
     * Rodada 11 — modo experimental: {@code -Dvx.mundo=procedural -Dvx.semente=1024} gera a região pela semente (o modo
     * padrão continua a vila fixa da rodada 10). Save à parte: {@code output/saves-procedural/s<semente>/}.
     */
    static final boolean PROCEDURAL = "procedural".equals(System.getProperty("vx.mundo"));
    static final long SEED = Long.parseUnsignedLong(System.getProperty("vx.semente", "1024"));
    /** A região gerada (null no modo padrão) e o tempo de gerar + conferir (ms). */
    ProceduralScene procedural;
    double generateMs;
    /** Pasta das aparências salvas pelo criador (-Dvx.saves=... muda; os roteiros usam uma pasta própria). */
    public static final Path SAVES = Path.of(System.getProperty("vx.saves", "output/aparencias"));

    private final Window window;
    private final Path assetsPath;
    private final Assets assets;
    private final StyleProfile style;
    /** Rodada 11.1 — a SESSÃO (mundo, relevo, lógica) troca em tempo de execução: menu ⇄ aventura, mundo A ⇄ mundo B. */
    private World world;
    public Terrain terrain;
    public GameLogic logic;
    final AsyncComposer async;
    private final Renderer renderer = new Renderer();
    final UiKit kit = new UiKit();
    private final Camera cam = new Camera();
    private final Input input = new Input();
    private final Renderer.Toggles toggles = new Renderer.Toggles();
    private final Map<GameLogic.Npc, Actor> npcActors = new HashMap<>();
    private Actor playerActor;
    private String shownSig;
    private Framebuffer captureFbo;
    final List<Button> buttons = new ArrayList<>();
    String hover;
    public boolean drawCursor, scripted;
    GameAudio gameAudio;
    final AudioPrefs audioPrefs = AudioPrefs.load(AudioPrefs.FILE);
    float mouseX = -1, mouseY = -1;
    private final Animator.Motion pMotion = new Animator.Motion(), nMotion = new Animator.Motion(), eMotion = new Animator.Motion();
    /** Rodada 09: ator do inimigo (fungo-cardo) e modo técnico (caixas de golpe e de dano; F3 com -Dvx.tecnico=1). */
    private Actor enemyActor;
    /** Rodada 09.2: um ator por inimigo do encontro (o mesmo rig; pose própria) e os efeitos de morte em andamento. */
    private final Map<Enemy, Actor> enemyActors = new HashMap<>();
    private final Map<Enemy, DeathFx> deaths = new HashMap<>();
    private final java.util.Set<Enemy> broken = new java.util.HashSet<>();
    private DeathFxMesh deathMesh;
    /** Rodada 10: os modelos dos objetos com estado (props da E) e a semente de luz do efeito do marco. */
    private final Map<String, Assets.Prop> props;
    /**
     * Rodada 11.1 — o jogo abre no menu principal ({@code -Dvx.menu=nao}: direto na escolha de aparência da vila, como os
     * roteiros das rodadas 08–11, que o {@code GameMain} mantém assim para as comparações históricas).
     */
    static final boolean MENU = !"nao".equals(System.getProperty("vx.menu", "sim"));
    private vx.engine.Mesh mote;
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
        style = StyleProfile.of(4, 0);
        props = assets.props("e");
        async = new AsyncComposer(assetsPath, 8);
        initSfx();
        long w0 = System.nanoTime();
        if (PROCEDURAL) {                                                 // rodada 11: região gerada pela semente (modo técnico)
            long g0 = System.nanoTime();
            ProceduralScene ps = ProceduralScene.create(assetsPath, SEED);
            generateMs = (System.nanoTime() - g0) / 1e6;
            System.out.printf(java.util.Locale.ROOT, "mundo: procedural, semente %s, %s v%d, tentativa %d, gerado e conferido em %.0f ms%s%n",
                    Long.toUnsignedString(SEED), vx.game.world.RegionGenerator.ID, vx.game.world.RegionGenerator.VERSION, ps.layout.attempt,
                    generateMs, ps.log.isEmpty() ? "" : " (" + ps.log.size() + " tentativa(s) recusada(s))");
            w0 = System.nanoTime();
            openSession(new World(assets, style, ps.field, ps.worldDoc), ps.scene(), ps, SaveGame.procDir(ps.worldId()));
        } else openSession(villageWorld(), SceneDef.load(assetsPath, SCENE), null, MENU ? null : SaveGame.dir());
        worldBuildMs = (System.nanoTime() - w0) / 1e6;
        toggles.fxaa = true;
        toggles.terrainFar = true;
        if (MENU && !PROCEDURAL) enterMenu();                             // rodada 11.1: o jogo abre no menu principal
    }

    /** A vila fixa (rodada 10): o mundo legado e o cenário da apresentação do menu. */
    private World villageWorld() {
        String prev = World.layout;
        World.layout = SCENE;
        try {
            return new World(assets, style);
        } finally {
            World.layout = prev;
        }
    }

    /**
     * Rodada 11.1 — monta a sessão: o mundo já construído (thread do GL), a colisão, a lógica, o som da sessão, os atores.
     * A anterior precisa ter sido fechada ({@link #closeSession}).
     */
    private void openSession(World w, SceneDef scene, ProceduralScene ps, Path saveDir) {
        world = w;
        terrain = w.terrainGrid;
        procedural = ps;
        Collision col = Collision.build(terrain, Collision.profiles(assetsPath), world.placements(), world.bodies(), scene);
        logic = new GameLogic(scene, col);
        logic.technical = technical;
        logic.saveDir = saveDir;                                          // rodada 11.1: a pasta da aventura (menu = nenhuma)
        gameAudio = new GameAudio(logic);
        audioPrefs.apply();
        if (logic.combat != null) {                                       // rodada 09: espada e broquel em qualquer aparência
            async.kit(logic.combat.def.heroKit);
            enemyActor = new Actor(assets.rig(logic.combat.def.enemyRig), style.animExaggeration);
            // rodada 09.2: blocos da morte e malha dinâmica prontos antes da luta (sem ler arquivo no meio do combate)
            VoxelCloud.of(assetsPath.resolve(logic.combat.def.enemyRig), 2);
            if (deathMesh == null) deathMesh = new DeathFxMesh();
        }
        if (logic.combat != null) logic.audio = new CombatAudio(sfx, logic.combat);
        if (!vx.engine.audio.Audio.has("passo.grama.andar")) logic.steps = new Footsteps(sfx);
        applyVolumes();
        logic.onAppearance = async::request;
        logic.loadChoices(async.catalog(), SAVES);
        logic.cam.sensitivity = logic.prefs.sensitivity;                  // rodada 11.1: câmera guardada nas preferências
        logic.cam.invertY = logic.prefs.invertY;
        npcActors.clear();
        for (GameLogic.Npc n : logic.npcs) npcActors.put(n, new Actor(assets.rig(n.def.rig()), style.animExaggeration));
        sessions++;
    }

    /** Rodada 11.1 — fecha a sessão: malhas do mundo, a grade do relevo procedural, atores e efeitos (nada fica vivo). */
    private void closeSession() { closeSession(false); }

    /**
     * {@code keepWorld}: a vila fixa continua montada para a próxima sessão (menu ⇄ aventura na vila: o mesmo cenário, sem
     * refazer 1,4 s de malhas); a lógica, os atores e o som da sessão fecham do mesmo jeito. Quem chama reabre com o
     * mundo devolvido por {@link #takeWorld}.
     */
    private void closeSession(boolean keepWorld) {
        if (world == null) return;
        if (sfx != null) sfx.stopAll();
        if (keepWorld) kept = world; else world.close();
        if (procedural != null) Terrain.release(procedural.field);
        enemyActors.clear();
        deaths.clear();
        broken.clear();
        npcActors.clear();
        enemyActor = null;
        world = null;
        procedural = null;
    }

    /** Sessões abertas desde o início (QA: troca de mundo). */
    int sessions;
    /** A vila guardada entre duas sessões ({@link #closeSession(boolean)}). */
    private World kept;

    /** A vila guardada (ou uma nova, se não houver). */
    private World takeWorld() {
        World w = kept != null ? kept : villageWorld();
        kept = null;
        return w;
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
    private void initSfx() {
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
        // por sessão (openSession): o som do combate e, sem catálogo, o passo único da 09.1
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
        if (sfx != null) sfx.setVolumes(sfxMaster * audioPrefs.effects * audioPrefs.master, sfx.effects, sfx.muted);
    }

    /** Mudo (M ou botão da pausa): tudo — combate, música, ambiente e interface. */
    private void setMuted(boolean m) {
        sfx.setVolumes(sfx.master, sfx.effects, m);
        vx.engine.audio.SoundSystem ss = vx.engine.audio.Audio.get();
        if (ss != null) ss.volume("geral", m ? 0f : audioPrefs.master);   // rodada 11.1: volta ao volume geral escolhido
    }

    private final org.joml.Vector3f earEye = new org.joml.Vector3f();

    /** Pausa, mudo (M) e ouvinte na câmera, depois da lógica do quadro. */
    private void audioFrame() {
        if (input.hit(GLFW_KEY_M) && !(menuActive() && menuFlow.typing())) setMuted(!sfx.muted);   // digitando: M é letra
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
        lastW = w;
        lastH = h;
        if (window.takeCloseRequest()) closeRequest();                    // rodada 11.1: o X da janela
        input.feed(events);
        for (InputEvent e : events) if (e.kind() == InputEvent.Kind.MOVE) { mouseX = e.x(); mouseY = e.y(); }
        hover = null;
        for (Button b : buttons) if (b.contains(mouseX, mouseY)) hover = b.id;
        boolean busy = transitionTick(dt);                                // rodada 11.1: troca de sessão em passos
        boolean menuOn = menuActive();
        boolean defeat = logic.combat != null && logic.combat.outcome == Combat.Outcome.DEFEAT;
        boolean uiState = logic.state != GameLogic.State.EXPLORE || defeat;
        if (uiState && !menuOn && !busy)
            for (float[] c : input.clicks) for (Button b : buttons) if (b.contains(c[0], c[1])) { logic.action(b.id); break; }
        if (menuOn && !busy) menuInput(events);
        if (logic.state == GameLogic.State.EXPLORE && !defeat && !scripted) {
            if (!window.cursorCaptured() && (window.focused() || !input.clicks.isEmpty())) { window.captureCursor(true); input.resetMouse(); input.leftPressed = 0; }
        } else if (window.cursorCaptured()) { window.captureCursor(false); input.resetMouse(); }
        if (logic.state == GameLogic.State.EXPLORE && !window.cursorCaptured() && !scripted) { input.mdx = 0; input.mdy = 0; input.leftPressed = 0; }
        if (defeat && input.hit(GLFW_KEY_ENTER)) logic.action("recomecar");
        if (technical && input.hit(GLFW_KEY_F3)) debugCombat = !debugCombat;
        if (!busy) logic.update(dt, input);
        if (logic.wantMenu) { logic.wantMenu = false; backToMenu(); }
        if (menuActive()) menuTick(dt);
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
        renderedSinceStep = true;
        if (world == null) {                                              // rodada 11.1: entre sessões só a tela de carregamento
            if (target != null) target.bind(); else Framebuffer.bindDefault(w, h);
            org.lwjgl.opengl.GL11.glClearColor(0.055f, 0.04f, 0.03f, 1f);
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
            drawUi(w, h);
            lastRenderMs = (System.nanoTime() - t0) / 1e6;
            return;
        }
        boolean menuOn = menuActive();
        if (menuOn && menuReadyMs < 0) {
            menuReadyMs = ProcessHandle.current().info().startInstant().map(i -> (double) (System.currentTimeMillis() - i.toEpochMilli())).orElse(0.0);
            System.out.printf(java.util.Locale.ROOT, "menu: primeiro quadro %.0f ms depois do início do processo (cena da vila montada em %.0f ms)%n", menuReadyMs, worldBuildMs);
        }
        if (menuOn) menu.pump();                                          // retratos prontos (FBO próprio) antes da cena
        Renderer.Frame f = new Renderer.Frame();
        if (menuOn) menuCamera();
        else logic.cam.apply(cam, logic.alpha);
        cam.update((float) w / h);
        f.camera = cam;
        f.style = style;
        f.time = logic.time;
        f.toggles = toggles;
        world.submit(f, logic.time);
        float blend = Math.min(1f, dt * 12f), a = logic.alpha;
        Player p = logic.player;
        Combat cb = logic.combat;
        boolean showHero = !menuOn || menuFlow.screen == vx.game.menu.MenuFlow.Screen.CHARACTERS && menuFlow.profile() != null;
        if (playerActor != null && showHero) {
            Matrix4f pw = new Matrix4f().translate(p.rx(a), p.rVisualY(a), p.rz(a)).rotateY(p.rYaw(a) + (menuOn ? menu.previewYaw : 0f));
            if (cb != null) {
                // pose EXATA (blend 1): as trajetórias e transições já são as validadas; suavizar por cima as deformaria
                playerActor.flash = flash(cb.hero.hurtT);
                playerActor.fade = nearFade(p, a);
                playerActor.submit(f, pw, cb.hero.motion(pMotion, p, a), 1f, heroExpression(cb.hero), terrain);
            } else playerActor.submit(f, pw, p.motion(pMotion), blend, null, terrain);
        }
        if (cb != null && enemyActor != null && !menuOn) {                // o fundo do menu não mostra inimigos
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
        drawObjects(f);
        boolean showcase = menuOn && menuFlow.screen == vx.game.menu.MenuFlow.Screen.CHARACTERS;
        for (GameLogic.Npc n : logic.npcs) {
            // vitrine dos personagens: morador colado no herói confunde a silhueta (só desenho; a lógica não muda)
            if (showcase && behindHero(n.def.x(), n.def.z(), p)) continue;
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

    // ------------------------------------------------------------------ rodada 10: objetos com estado (o marco)

    /** Duração (s) e quantidade das sementes de luz que sobem da lanterna quando o marco acende (P1: ≤ 5 na tela). */
    static final float MOTE_LIFE = 2.4f;
    static final int MOTES = 5;

    /** O marco no estado da missão (apagado/aceso) e, logo depois de acender, o efeito discreto. */
    private void drawObjects(Renderer.Frame f) {
        for (SceneDef.WorldObject o : logic.scene.objects) {
            boolean on = menuActive() || logic.quests != null && logic.quests.objectOn(o.id());   // menu: o marco aceso
            Assets.Prop pr = props.get(on ? o.modelOn() : o.modelOff());
            if (pr == null) continue;
            Matrix4f m = new Matrix4f().translate(o.x(), terrain.groundAt(o.x(), o.z()), o.z()).rotateY((float) Math.toRadians(o.yawDeg()));
            f.draws.add(new Renderer.Draw(pr.mesh(), m, pr.texture(), false, true));
            Float t0 = logic.litAt.get(o.id());
            if (t0 != null && logic.time - t0 < MOTE_LIFE) motes(f, m, logic.time - t0);
        }
    }

    /**
     * Sementes de luz (P1, verde-água emissivo) subindo em espiral da lanterna e encolhendo até sumir: 5 pontos de
     * 1/24 u por 2,4 s. Só desenho; nada disso volta para a lógica.
     */
    private void motes(Renderer.Frame f, Matrix4f model, float t) {
        if (mote == null) mote = vx.engine.VoxelMesher.build(new vx.engine.VoxelSource() {
            public int sizeX() { return 1; }
            public int sizeY() { return 1; }
            public int sizeZ() { return 1; }
            public int get(int x, int y, int z) { return x == 0 && y == 0 && z == 0 ? ((vx.engine.VoxModel.MAT_EMISSIVE + 1) << 24) | 0x9ff0c8 : 0; }
        }, 1f, -0.5f, -0.5f, -0.5f);
        org.joml.Vector3f c = model.transformPosition(new org.joml.Vector3f(0f, 1.06f, 0.44f));   // centro da lanterna
        for (int i = 0; i < MOTES; i++) {
            float u = Math.clamp((t - i * 0.12f) / (MOTE_LIFE - 0.5f), 0f, 1f);
            if (u <= 0f || u >= 1f) continue;
            float a = i * 1.2566f + u * 4.2f, r = 0.08f + 0.20f * u;
            float k = 0.042f * (float) Math.sqrt(1f - u);
            f.draws.add(new Renderer.Draw(mote, new Matrix4f().translate(c.x + r * (float) Math.cos(a), c.y + 0.70f * u, c.z + r * (float) Math.sin(a))
                    .rotateY(a).scale(k), null, false, false));
        }
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

    float s;

    private void drawUi(int w, int h) {
        s = Math.clamp(Math.min(w / 1920f, h / 1080f), 0.6f, 1.8f);
        buttons.clear();
        kit.begin(w, h);
        switch (logic.state) {
            case SELECT -> drawSelect(w, h);
            case EXPLORE -> drawExplore(w, h);
            case DIALOG -> drawDialog(w, h);
            case PAUSE -> drawPause(w, h);
            case JOURNAL -> drawJournal(w, h);
            case MENU -> { if (menu != null) { menu.drawFade(w, h); menu.draw(w, h); } }
        }
        if (logic.state == GameLogic.State.EXPLORE || logic.state == GameLogic.State.DIALOG) drawToast(w, h);
        if (async.busy() && async.waitingMs() > 150) {
            float bw = 260 * s;
            kit.chamfer((w - bw) / 2, h - 120 * s, bw, 46 * s, 5 * s, 0x2a1e18c0);
            kit.text((w - bw) / 2 + 24 * s, h - 111 * s, "Preparando" + ".".repeat(((int) (logic.time * 3)) % 4), px(20), true, UiKit.PARCH_L);
        }
        if (logic.message != null && logic.time < logic.messageUntil) {
            int p = px(21);
            String msg = fit(logic.message, p, w - 120 * s);              // rodada 10: nunca passa da tela
            float tw = kit.width(msg, p, true), bw = tw + 60 * s;
            // rodada 10: na pausa e no diário a mensagem vai para baixo — no topo cobria o título do painel (na tela inicial
            // continua no topo: embaixo cobriria os botões de jogo novo e continuar)
            boolean menu = logic.state == GameLogic.State.PAUSE || logic.state == GameLogic.State.JOURNAL;
            float my0 = menu ? h - 62 * s : 40 * s;
            kit.chamfer((w - bw) / 2, my0, bw, 52 * s, 6 * s, UiKit.WOOD_D);
            kit.chamfer((w - bw) / 2 + 3 * s, my0 + 3 * s, bw - 6 * s, 46 * s, 5 * s, UiKit.PARCH_L);
            kit.text((w - bw) / 2 + 30 * s, my0 + 26 * s - p * 0.66f, msg, p, true, UiKit.INK);
        }
        boolean defeatUi = logic.combat != null && logic.combat.outcome == Combat.Outcome.DEFEAT && logic.state == GameLogic.State.EXPLORE;
        drawOverlay(w, h);
        if (drawCursor && (logic.state != GameLogic.State.EXPLORE || defeatUi) && mouseX >= 0 && overlay < 1f) kit.cursor(mouseX, mouseY, s, false);
        kit.end();
    }

    int px(float base) { return Math.max(9, Math.round(base * s)); }

    void button(String id, float x, float y, float w, float h, String label, boolean primary) {
        Button b = new Button(id, x, y, w, h, label, primary);
        buttons.add(b);
        boolean hv = id.equals(hover);
        kit.rect(x + 3 * s, y + 5 * s, w, h, UiKit.SHADOW);
        kit.chamfer(x, y, w, h, 5 * s, primary ? UiKit.MOSS_D : UiKit.WOOD_D);
        kit.chamfer(x + 3 * s, y + 3 * s, w - 6 * s, h - 7 * s, 4 * s,
                primary ? (hv ? 0x74a26cff : UiKit.MOSS) : hv ? UiKit.WOOD_L : UiKit.WOOD);
        kit.rect(x + 9 * s, y + 4 * s, w - 18 * s, Math.max(1, 2 * s), 0xffffff38);
        int p = groupPx > 0 && kit.width(label, groupPx, true) <= w - 20 * s ? groupPx : textPx(label, w - 20 * s, 22);
        kit.text(x + (w - kit.width(label, p, true)) / 2, y + h / 2 - p * 0.66f, label, p, true, UiKit.PARCH_L);
    }

    /**
     * Rodada 11.1 — escala comum de um grupo de botões equivalentes (Jogar/Configurações/Sair; Voltar/Criar mundo…): a
     * maior escala inteira que serve a TODOS os rótulos, cada um na largura do seu botão. Antes cada botão escolhia a sua
     * e vizinhos saíam com tamanhos diferentes. 0 = sem grupo.
     */
    int groupPx;

    /** Pares (rótulo, largura do botão) → a escala comum ({@link #groupPx}), a partir do tamanho base 22. */
    int groupPx(Object... labelAndWidth) { return groupPxFrom(22, labelAndWidth); }

    /** Idem a partir de {@code base} (telas de menu: botões maiores que os da pausa, mesma regra de caber). */
    int groupPxFrom(float base, Object... labelAndWidth) {
        int p = Integer.MAX_VALUE;
        for (int i = 0; i < labelAndWidth.length; i += 2)
            p = Math.min(p, textPx((String) labelAndWidth[i], ((Number) labelAndWidth[i + 1]).floatValue() - 20 * s, base));
        return p == Integer.MAX_VALUE ? 0 : p;
    }

    /** Maior passo inteiro da fonte de pixel que cabe na largura. */
    int textPx(String t, float room, float base) {
        int p = px(base);
        while (kit.width(t, p, true) > room && vx.engine.PixelFont.scale(p) > 1) p = 9 * (vx.engine.PixelFont.scale(p) - 1);
        return p;
    }

    void title(String t, String sub) {
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
        boolean fresh = !logic.started && logic.quests != null;           // rodada 10: Jogo novo / Continuar
        if (procedural != null) title("Valumo", "Mundo procedural (experimental) · semente " + Long.toUnsignedString(procedural.seed)
                + " · " + (procedural.quest != null ? procedural.quest.name() : "sem missão nesta região"));
        else title("Valumo", fresh ? "As Luzes do Posto — escolha a aparência e comece, ou continue o jogo salvo"
                : "Escolha a aparência e entre na vila");
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
        int maxRows = Math.max(1, (int) ((py + ph - 24 * s - 70 * s - 66 * s * (fresh ? 2 : 1) - 40 * s - y) / (bh + gap)));
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
            kit.text(x, y, "+" + below, px(18), false, UiKit.INK_SOFT);
        float by = py + ph - 24 * s - 70 * s;
        if (fresh) {
            String why = logic.continueBlocker();
            boolean can = why == null;
            button("comecar", x, by, iw, 70 * s, "Jogo novo", !can);
            if (can) button("continuar-jogo", x, by - 66 * s, iw, 56 * s, "Continuar o jogo salvo", true);
            else disabledButton("continuar-jogo", x, by - 66 * s, iw, 56 * s, "Continuar — " + shortReason(why));
            button("criador", x, by - 132 * s, iw, 56 * s, "Criar no editor", false);
            return;
        }
        button("comecar", x, by, iw, 70 * s, logic.started ? "Voltar ao jogo" : "Entrar na vila", true);
        button("criador", x, by - 66 * s, iw, 56 * s, "Criar no editor", false);
    }

    /** Botão desabilitado: desenhado apagado; o clique ainda chega à lógica, que explica o motivo. */
    void disabledButton(String id, float x, float y, float w, float h, String label) {
        buttons.add(new Button(id, x, y, w, h, label, false));
        kit.chamfer(x, y, w, h, 5 * s, 0x6b544480);
        kit.chamfer(x + 3 * s, y + 3 * s, w - 6 * s, h - 7 * s, 4 * s, 0xcfc2b0ff);
        int p = groupPx > 0 && kit.width(label, groupPx, true) <= w - 24 * s ? groupPx : textPx(label, w - 20 * s, 20);
        String t = fit(label, p, w - 24 * s);
        kit.text(x + (w - kit.width(t, p, true)) / 2, y + h / 2 - p * 0.66f, t, p, true, UiKit.INK_SOFT);
    }

    /** Motivo curto para o botão (o completo sai na mensagem ao clicar). */
    static String shortReason(String why) {
        if (why.startsWith("Jogo salvo inválido")) return "jogo salvo inválido";
        if (why.startsWith("Nenhum")) return "nenhum jogo salvo";
        return why.toLowerCase(java.util.Locale.ROOT);
    }

    /** O texto inteiro se couber em {@code room}; senão o maior prefixo que cabe seguido de "…". */
    String fit(String t, int p, float room) {
        if (kit.width(t, p, true) <= room) return t;
        int n = t.length();
        while (n > 1 && kit.width(t.substring(0, n).stripTrailing() + "…", p, true) > room) n--;
        return t.substring(0, n).stripTrailing() + "…";
    }

    /** Quebra o texto em linhas que cabem em {@code room} (px), por palavra; palavra maior que a linha fica sozinha. */
    List<String> wrap(String t, int p, float room) {
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

    private void drawExplore(int w, int h) {
        Combat cb = logic.combat;
        boolean arena = cb != null && cb.inArena(logic.player);
        if (cb != null) drawCombat(cb, w, h, arena);
        if (cb != null && cb.outcome == Combat.Outcome.DEFEAT) return;
        GameLogic.Npc n = logic.cam.technicalView ? null : logic.candidate;   // rodada 11: vista técnica sem placas
        if (n != null) {
            float gy = terrain.groundAt(n.def.x(), n.def.z());
            // a placa fica acima da cabeça de QUALQUER NPC (1,55 u era fixo: no Bramor, de 1,73 u, caía sobre o cabelo)
            float[] sc = project(n.def.x(), gy + Math.max(1.55f, n.def.height() + 0.30f), n.def.z(), w, h);
            if (sc != null) interactPlate(sc, "Conversar com " + n.def.name());
        }
        SceneDef.WorldObject o = logic.objectCandidate;                   // rodada 10: o marco
        if (o != null && logic.dialogues != null && logic.dialogues.object(o.id()) != null) {
            DialogueBook.ObjectText t = logic.dialogues.object(o.id());
            float[] sc = project(o.x(), terrain.groundAt(o.x(), o.z()) + o.plateY(), o.z(), w, h);
            if (sc != null) interactPlate(sc, logic.quests.objectOn(o.id()) ? t.verbActive() : t.verb());
        }
        drawTracker(w, h);
    }

    /** Placa "E ação" acima de quem/que se pode usar (sc = ponto projetado na tela). */
    private void interactPlate(float[] sc, String b) {
        int p = px(21);
        float tw = kit.width(b, p, true), bw = tw + 44 * s, bh = 46 * s;
        float x = sc[0] - bw / 2, y = sc[1] - bh;
        kit.chamfer(x, y, bw, bh, 5 * s, UiKit.WOOD_D);
        kit.chamfer(x + 3 * s, y + 3 * s, bw - 6 * s, bh - 6 * s, 4 * s, UiKit.PARCH_L);
        kit.text(x + 22 * s, y + bh / 2 - p * 0.66f, b, p, true, UiKit.INK);
        kit.rect(sc[0] - 4 * s, y + bh, 8 * s, 6 * s, UiKit.WOOD_D);
    }

    // ------------------------------------------------------------------ rodada 10: rastreador, aviso e diário

    /** Topo do rastreador (px a 1080p): a mensagem central ocupa de 40 a 92 e pode ser larga. */
    static final float TRACKER_Y = 112f;

    /** Largura do canto direito (rastreador). */
    private float trackerW(int w) { return Math.min(470 * s, w - 56 * s); }

    /**
     * Rastreador discreto (canto superior direito): nome da missão, objetivo atual e progresso. Some com T no diário,
     * na luta (o canto é do inimigo chefe e da mira) e quando não há missão aceita.
     */
    private void drawTracker(int w, int h) {
        if (logic.quests == null || !logic.prefs.tracker) return;
        vx.game.quest.QuestProgress q = logic.quests.tracked();
        if (q == null) return;
        Combat cb = logic.combat;
        if (cb != null && (cb.engaged() || cb.boss() != null && cb.inArena(logic.player))) return;
        var o = q.current();
        if (o == null) return;
        int i = q.objectiveIndex(), pt = px(19), pn = px(21);
        float tw = trackerW(w), x = w - 28 * s - tw, y = TRACKER_Y * s;     // abaixo da faixa das mensagens do topo
        String prog = o.total() > 1 ? "  " + q.done(i) + "/" + o.total() : "";
        List<String> lines = wrap(o.text() + prog, pt, tw - 64 * s);
        float th = 52 * s + lines.size() * pt * 1.3f + 14 * s;
        kit.chamfer(x, y, tw, th, 5 * s, 0x2a1e18b0);
        kit.seed(x + 18 * s, y + 20 * s, s * 0.8f);
        kit.text(x + 40 * s, y + 14 * s, fit(q.def.name(), pn, tw - 56 * s), pn, true, UiKit.PARCH_L);
        float ly = y + 50 * s;
        for (String l : lines) {
            kit.text(x + 40 * s, ly, l, pt, false, 0xf3e6c8ff);
            ly += pt * 1.3f;
        }
    }

    /** Aviso de missão (aceita, progresso, objetivo, recompensa): abaixo do rastreador, some sozinho. */
    private void drawToast(int w, int h) {
        if (logic.toast == null || logic.time >= logic.toastUntil) return;
        int p = px(20);
        float left = logic.toastUntil - logic.time, tw = Math.min(kit.width(logic.toast, p, true) + 64 * s, w - 56 * s);
        float x = w - 28 * s - tw, y = (logic.state == GameLogic.State.EXPLORE && logic.prefs.tracker && logic.quests != null
                && logic.quests.tracked() != null ? TRACKER_Y + 140 : TRACKER_Y) * s;
        int a = (int) (255 * Math.clamp(left / 0.4f, 0f, 1f));
        kit.chamfer(x, y, tw, 48 * s, 5 * s, (UiKit.WOOD_D & 0xffffff00) | a);
        kit.chamfer(x + 3 * s, y + 3 * s, tw - 6 * s, 42 * s, 4 * s, (UiKit.PARCH_L & 0xffffff00) | a);
        kit.text(x + 32 * s, y + 24 * s - p * 0.66f, fit(logic.toast, p, tw - 44 * s), p, true, (UiKit.INK & 0xffffff00) | a);
    }

    /** Diário (J): cada missão aceita, com descrição, objetivos, estado, recompensa e registro; moedas no rodapé. */
    private void drawJournal(int w, int h) {
        kit.rect(0, 0, w, h, 0x1a1210a0);
        float pw = Math.min(980 * s, w - 56 * s), ph = Math.min(860 * s, h - 40 * s), x = (w - pw) / 2, y = (h - ph) / 2;
        kit.panel(x, y, pw, ph, s, true);
        kit.text(x + 40 * s, y + 30 * s, "Diário", px(40), true, UiKit.INK);
        String coins = "Moedas: " + logic.quests.coins();
        kit.text(x + pw - 40 * s - kit.width(coins, px(22), true), y + 44 * s, coins, px(22), true, UiKit.GOLD_D);
        float cx = x + 44 * s, cw = pw - 88 * s, cy = y + 104 * s, bottom = y + ph - 110 * s;
        int pt = px(20), ph2 = px(26);
        boolean any = false;
        for (vx.game.quest.QuestProgress q : logic.quests.all()) {
            if (!q.accepted() || cy > bottom) continue;
            any = true;
            var d = q.def;
            kit.text(cx, cy, d.name(), ph2, true, UiKit.INK);
            String st = q.state().label;
            kit.chamfer(cx + cw - kit.width(st, px(18), true) - 28 * s, cy - 2 * s, kit.width(st, px(18), true) + 28 * s, 34 * s, 4 * s,
                    q.completed() ? UiKit.MOSS : UiKit.GOLD);
            kit.text(cx + cw - kit.width(st, px(18), true) - 14 * s, cy + 15 * s - px(18) * 0.66f, st, px(18), true,
                    q.completed() ? UiKit.PARCH_L : UiKit.INK);
            cy += 44 * s;
            for (String l : wrap(d.description(), pt, cw)) { kit.text(cx, cy, l, pt, false, UiKit.INK_SOFT); cy += pt * 1.35f; }
            cy += 10 * s;
            for (int i = 0; i < d.objectives().size(); i++) {
                var o = d.objectives().get(i);
                boolean done = q.done(i) >= o.total() && (i < q.objectiveIndex() || q.completed()), cur = !q.completed() && i == q.objectiveIndex();
                if (done) kit.check(cx, cy + 2 * s, s);
                else {
                    kit.rect(cx, cy + 2 * s, 18 * s, 18 * s, cur ? UiKit.GOLD_D : 0x6b463060);
                    kit.rect(cx + 3 * s, cy + 5 * s, 12 * s, 12 * s, UiKit.PARCH_L);
                }
                String t = o.text() + (o.total() > 1 ? "  " + q.done(i) + "/" + o.total() : "");
                kit.text(cx + 32 * s, cy, fit(t, pt, cw - 32 * s), pt, cur, done ? UiKit.INK_SOFT : cur ? UiKit.INK : 0x6b544490);
                cy += pt * 1.6f;
            }
            cy += 8 * s;
            int r = d.reward("moedas");
            String rw = q.rewardGiven() ? "Recompensa recebida: " + r + " moedas" : "Recompensa prometida: " + r + " moedas";
            kit.text(cx, cy, rw, pt, true, UiKit.GOLD_D);
            cy += pt * 1.5f;
            String rec = "Aceita aos " + clock(q.acceptedAt()) + (q.completed() ? " · concluída aos " + clock(q.completedAt()) : "");
            kit.text(cx, cy, rec, px(18), false, UiKit.INK_SOFT);
            cy += 60 * s;
        }
        if (!any) {
            for (String l : wrap("Nenhuma missão ainda. Converse com os moradores da vila.", pt, cw)) { kit.text(cx, cy, l, pt, false, UiKit.INK_SOFT); cy += pt * 1.4f; }
        }
        float bw = (pw - 100 * s) / 2, bh = 58 * s, by = y + ph - 30 * s - bh;
        button("rastreador", x + 40 * s, by, bw, bh, logic.prefs.tracker ? "Rastreador: visível" : "Rastreador: oculto", false);
        button("fechar-diario", x + 60 * s + bw, by, bw, bh, "Fechar", true);
    }

    /** Tempo de jogo como "12 min" (ou "40 s" no primeiro minuto). */
    static String clock(float secs) {
        if (secs < 0) return "—";
        return secs < 60 ? Math.round(secs) + " s de jogo" : (int) (secs / 60) + " min de jogo";
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
            button("recomecar", x + 40 * s, y + 150 * s, bw2, bh2, "Tentar de novo", true);
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
        int n = logic.dialogLines.size();
        for (int i = 0; i < n; i++)
            kit.rect(dx + 44 * s + i * 22 * s, dy + dh - 42 * s, 12 * s, 12 * s, i <= logic.line ? UiKit.GOLD_D : 0x6b463050);
        boolean last = logic.lastLine();
        float bw = 260 * s, bh = 58 * s;
        if (last && !logic.dialogChoices.isEmpty()) {                    // rodada 10: escolha (← → escolher, E confirmar)
            float cx = dx + dw - 36 * s;
            for (int i = logic.dialogChoices.size() - 1; i >= 0; i--) {
                String t = logic.dialogChoices.get(i).text();
                float cw = Math.max(200 * s, kit.width(t, px(22), true) + 48 * s);
                cx -= cw;
                button("fala-escolha:" + i, cx, dy + dh - 30 * s - bh, cw, bh, t, i == logic.choiceSelected);
                cx -= 16 * s;
            }
            button("encerrar-fala", cx - 190 * s, dy + dh - 30 * s - bh, 190 * s, bh, "Sair", false);
            return;
        }
        button("continuar-fala", dx + dw - 36 * s - bw, dy + dh - 30 * s - bh, bw, bh, last ? "Até logo" : "Continuar", true);
        button("encerrar-fala", dx + dw - 36 * s - bw - 16 * s - 190 * s, dy + dh - 30 * s - bh, 190 * s, bh, "Sair", false);
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
    void stepperRow(String id, String label, String value, float bx, float bw, float y) {
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
        float pw = 620 * s, ph = (cmb ? (logic.technical ? 990 : 866) : 800) * s, x = (w - pw) / 2, y = Math.max(8 * s, (h - ph) / 2);
        kit.panel(x, y, pw, ph, s, true);
        kit.text(x + 40 * s, y + 34 * s, "Pausa", px(40), true, UiKit.INK);
        float hw0 = (pw - 80 * s - 14 * s) / 2, bw0 = pw - 80 * s;
        groupPx = groupPx("Continuar", bw0, "Menu principal", bw0, "Aparência", hw0, "Criador", hw0, "Salvar jogo", hw0, "Diário", hw0,
                "Inverter vertical: não", bw0, "Som: ligado", bw0, "Barras: com dano ou alvo", bw0, "Sair do jogo", bw0);
        if (procedural != null) {                                          // rodada 11: a semente sempre à vista
            String sd = "semente " + Long.toUnsignedString(procedural.seed);
            kit.text(x + pw - 40 * s - kit.width(sd, px(20), false), y + 50 * s, sd, px(20), false, UiKit.INK_SOFT);
        }
        float bx = x + 40 * s, bw = pw - 80 * s, bh = 62 * s, by = y + 110 * s;
        button("continuar", bx, by, bw, bh, "Continuar", true);
        float hw = (bw - 14 * s) / 2;                                     // rodada 10: dois por linha
        if (logic.menuAvailable) button("menu-principal", bx, by + 76 * s, bw, bh, "Menu principal", false);   // rodada 11.1
        else {
            button("trocar-aparencia", bx, by + 76 * s, hw, bh, "Aparência", false);
            button("criador", bx + hw + 14 * s, by + 76 * s, hw, bh, "Criador", false);
        }
        if (logic.quests != null) {
            button("salvar", bx, by + 152 * s, hw, bh, "Salvar jogo", false);
            button("diario-pausa", bx + hw + 14 * s, by + 152 * s, hw, bh, "Diário", false);
        }
        // sensibilidade do mouse
        float sy = by + 240 * s;
        stepperRow("sens", "Sensibilidade", String.format("%.1f", logic.cam.sensitivity / 0.0032f), bx, bw, sy);
        String inv = logic.cam.invertY ? "Inverter vertical: sim" : "Inverter vertical: não";
        button("inverter", bx, sy + 66 * s, bw, 50 * s, inv, false);
        // som: volumes do jogador (música; efeitos = combate, passos, interface e ambiente), gravados nas preferências, e mudo
        stepperRow("mus", "Música", AudioPrefs.percent(audioPrefs.music), bx, bw, sy + 132 * s);
        stepperRow("efe", "Efeitos", AudioPrefs.percent(audioPrefs.effects), bx, bw, sy + 198 * s);
        button("mudo", bx, sy + 264 * s, bw, 50 * s, sfx.muted ? "Som: mudo" : "Som: ligado", false);
        if (cmb) {                                                         // rodada 09.2: combate
            float cy = sy + 334 * s;
            String bars = switch (logic.prefs.bars) { case "nunca" -> "nunca"; case "sempre" -> "sempre"; default -> "com dano ou alvo"; };
            button("barras", bx, cy, bw, 50 * s, "Barras: " + bars, false);
            if (logic.technical) {                                         // fechamento da 10: comparações só no modo técnico
                button("sensacao", bx, cy + 62 * s, bw, 50 * s, "Sensação: " + logic.prefs.feel, false);
                String enc = switch (logic.combat.encounter) {
                    case "grupo" -> "três fungos"; case "chefe" -> "chefe + 2"; case "trilha" -> "trilha (missão)"; default -> "um fungo";
                };
                button("encontro", bx, cy + 124 * s, bw, 50 * s, "Arena: " + enc, false);
            }
        }
        button("sair", bx, y + ph - 40 * s - bh, bw, bh, "Sair do jogo", false);
        groupPx = 0;
        if (logic.leaving != null) drawLeaving(w, h);
    }

    /**
     * Rodada 11.1 — confirmação antes de sair da aventura (menu principal ou fechar o jogo): nada se perde em silêncio.
     * Os botões da pausa por baixo deixam de valer (só os do painel).
     */
    private void drawLeaving(int w, int h) {
        buttons.clear();
        kit.rect(0, 0, w, h, 0x1a1210b0);
        boolean quit = "sair".equals(logic.leaving), dirty = logic.unsaved();
        String why = dirty ? logic.saveBlocker() : null;
        int n = dirty ? 3 : 2;
        float pw = Math.min(760 * s, w - 56 * s), bh = 58 * s, ph = 260 * s + n * (bh + 12 * s), x = (w - pw) / 2, y = (h - ph) / 2;
        kit.panel(x, y, pw, ph, s, true);
        kit.text(x + 40 * s, y + 32 * s, quit ? "Sair do jogo?" : "Voltar ao menu principal?", px(36), true, UiKit.INK);
        String kept = " O que já foi salvo, inclusive nos marcos da missão, continua salvo.";
        String t = !dirty ? "Tudo desta aventura já está salvo."
                : why == null ? "Sair sem salvar perde só o que aconteceu desde o último salvamento." + kept
                : "Agora não dá para salvar (" + why.toLowerCase(java.util.Locale.ROOT) + "). Sair sem salvar perde só o que aconteceu desde o último salvamento." + kept;
        float ty = y + 96 * s;
        for (String l : wrap(t, px(20), pw - 80 * s)) { kit.text(x + 40 * s, ty, l, px(20), false, UiKit.INK_SOFT); ty += px(20) * 1.35f; }
        float bw = pw - 80 * s, by = y + ph - 34 * s - n * bh - (n - 1) * 12 * s;
        String go = quit ? "Sair do jogo" : "Voltar ao menu";
        groupPx = groupPx(quit ? "Salvar e sair" : "Salvar e voltar ao menu", bw, quit ? "Sair sem salvar" : "Voltar sem salvar", bw, go, bw, "Cancelar", bw);
        if (dirty) {
            if (why == null) button("menu-salvar", x + 40 * s, by, bw, bh, quit ? "Salvar e sair" : "Salvar e voltar ao menu", true);
            else disabledButton("menu-salvar", x + 40 * s, by, bw, bh, quit ? "Salvar e sair" : "Salvar e voltar ao menu");
            by += bh + 12 * s;
            button("menu-sem-salvar", x + 40 * s, by, bw, bh, quit ? "Sair sem salvar" : "Voltar sem salvar", false);
        } else button("menu-sem-salvar", x + 40 * s, by, bw, bh, go, true);   // nada a perder: só sair (ou cancelar)
        button("menu-cancelar", x + 40 * s, by + bh + 12 * s, bw, bh, "Cancelar", false);
        groupPx = 0;
    }

    // ------------------------------------------------------------------ rodada 11.1: menu principal e transições

    /** Estado e regras da porta de entrada (sem GL) e o desenho dela (com os recursos de GL do menu). */
    vx.game.menu.MenuFlow menuFlow;
    MenuView menu;
    /** Pedido de abrir o criador (o laço da janela ou o roteiro o cumpre: o criador tem o laço dele). */
    private vx.game.menu.MenuFlow.Request creatorReq;
    /** Prévia de mundo a fazer depois de um quadro com o "preparando a prévia..." na tela. */
    private vx.game.menu.WorldStore.World previewJob;

    /** Um passo da transição: rótulo real do que vai acontecer + o trabalho (thread do GL). */
    private record Step(String label, Runnable run) { }

    private final java.util.ArrayDeque<Step> steps = new java.util.ArrayDeque<>();
    private String stepLabel;
    /** Cobertura da transição (0–1): escurece antes do primeiro passo e clareia depois do último (sem clarão). */
    private float overlay;
    private boolean renderedSinceStep;
    /** O rótulo do passo atual já foi desenhado num quadro (com a cobertura inteira). */
    private boolean labelShown;
    private Renderer previewRenderer;
    private final Camera previewCam = new Camera();
    /** Transições concluídas e prévias feitas nesta execução (QA: dezenas de idas e voltas sem crescer recursos). */
    int transitions, previewsMade;

    boolean menuActive() { return menu != null && logic.state == GameLogic.State.MENU; }

    /**
     * Rodada 11.1 — o X da janela (pedido interceptado pelo {@code Window}). Menu, carregamento ou aventura sem nada
     * por salvar: fecha. Aventura com progresso fora do disco: a confirmação "Sair do jogo?" da pausa
     * ({@link GameLogic#requestQuit}). O criador atende o seu próprio X (ele tem o laço dele).
     */
    private void closeRequest() {
        if (!steps.isEmpty() || menuActive() || world == null) { logic.quit = true; return; }
        logic.requestQuit();
        if (window.cursorCaptured()) { window.captureCursor(false); input.resetMouse(); }
        System.out.println("janela: pedido de fechar → " + (logic.quit ? "fecha" : "confirmação (" + logic.state + ")"));
    }

    /**
     * Vitrine: o morador está perto do herói ou atrás dele, visto do olho (até 16° do eixo olho → herói)? Esconder só no
     * desenho, para a silhueta do personagem ficar limpa.
     */
    private boolean behindHero(float nx, float nz, Player p) {
        org.joml.Vector3f e = cam.position();
        if (Math.hypot(nx - p.x, nz - p.z) < 3.2f) return true;
        double a = Math.atan2(nx - e.x, nz - e.z), b = Math.atan2(p.x - e.x, p.z - e.z);
        double d = Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
        return d < Math.toRadians(16) && Math.hypot(nx - e.x, nz - e.z) > Math.hypot(p.x - e.x, p.z - e.z);
    }

    /** Tempo do início do processo até o primeiro quadro do menu (ms; -1 = ainda não). */
    double menuReadyMs = -1;

    private void enterMenu() { enterMenu(vx.game.menu.MenuFlow.Screen.MAIN); }

    /** Põe a sessão atual (a vila da apresentação) no estado MENU, na tela pedida, com as listas relidas do disco. */
    private void enterMenu(vx.game.menu.MenuFlow.Screen screen) {
        if (menuFlow == null) {
            var rnd = new java.security.SecureRandom();
            menuFlow = new vx.game.menu.MenuFlow(new vx.game.menu.CharacterStore(vx.game.menu.CharacterStore.defaultDir(), async.catalog(), rnd),
                    new vx.game.menu.WorldStore(vx.game.menu.WorldStore.defaultRoot(), rnd));
            // os jogos salvos de antes (vila e sementes) viram aventuras uma vez, por cópia validada; os originais ficam
            if (!menuFlow.worlds.oldSaves(SaveGame.dir(), procSaveRoot()).isEmpty())
                transition(new Step("Preparando seus jogos salvos", () -> {
                    adoptOldSaves(assetsPath, async.catalog(), menuFlow.characters, menuFlow.worlds, SaveGame.dir(), procSaveRoot(),
                            vx.game.menu.CharacterStore.now()).forEach(m -> System.out.println("adoção: " + m));
                    menuFlow.refreshCharacters();
                    menuFlow.refreshWorlds();
                }));
        }
        if (menu == null) menu = new MenuView(this, menuFlow, vx.game.menu.Cinematic.load(assetsPath));
        menu.cine.restart();
        menu.pose = menu.cine.at(0f);
        float[] sh = menu.cine.showcase;                                   // o herói na vitrine (tela de personagens)
        logic.player.place(sh[0], sh[1], sh[2], logic.collision);
        logic.player.snapshot();
        menu.invalidate();
        logic.state = GameLogic.State.MENU;
        logic.saveDir = null;                                             // o cenário do menu nunca lê nem grava save
        logic.menuAvailable = false;
        menuFlow.refreshCharacters();
        menuFlow.refreshWorlds();
        menuFlow.screen = screen;
        menuFlow.loading = null;
        shownProfileSig = null;
    }

    /** Raiz dos saves por semente da rodada 11 (para oferecer a importação): a mesma regra de {@link SaveGame#procDir}. */
    static Path procSaveRoot() {
        String d = System.getProperty("vx.savegame");
        if ("-".equals(d)) return null;
        return d != null ? Path.of(d) : Path.of("output/saves-procedural");
    }

    /** Câmera do menu: a tomada da apresentação, ou (personagens) o herói enquadrado à direita da lista. */
    private void menuCamera() {
        cam.fovDeg = FollowCamera.FOV;
        cam.near = 0.1f;
        if (menuFlow.screen == vx.game.menu.MenuFlow.Screen.CHARACTERS) {
            Player p = logic.player;
            float[] v = vx.game.menu.Cinematic.showcaseCamera(p.x, terrain.groundAt(p.x, p.z), p.z, p.yaw);
            cam.target.set(v[0], v[1], v[2]);
            cam.yaw = v[3];
            cam.pitch = v[4];
            cam.distance = v[5];
            return;
        }
        vx.game.menu.Cinematic.Pose q = menu.pose;
        cam.target.set(q.tx(), q.ty(), q.tz());
        cam.yaw = q.yaw();
        cam.pitch = q.pitch();
        cam.distance = q.dist();
    }

    /** Enfileira uma transição (a tela escurece, os passos rodam um por quadro com o rótulo real, a tela clareia). */
    private void transition(Step... s) {
        steps.addAll(List.of(s));
        stepLabel = steps.peek().label();
        renderedSinceStep = false;
        labelShown = false;
    }

    /** Roda o próximo passo quando a tela já está coberta e um quadro com o rótulo foi desenhado. true = ocupado. */
    private boolean transitionTick(float dt) {
        float step = Math.max(dt, 1f / 60f);
        if (steps.isEmpty()) {
            overlay = Math.max(0f, overlay - step / 0.35f);
            return false;
        }
        overlay = Math.min(1f, overlay + step / 0.2f);
        // o passo pesado só começa depois de um quadro com o RÓTULO dele na tela (antes valia qualquer quadro desenhado:
        // o da cobertura ainda subindo, sem rótulo, e a janela congelava escura sem dizer o quê — "Abrindo o criador")
        if (overlay < 1f || !labelShown) return true;
        Step st = steps.poll();
        labelShown = false;
        renderedSinceStep = false;
        long t0 = System.nanoTime();
        try {
            st.run().run();
        } catch (RuntimeException e) {
            e.printStackTrace();                                          // detalhes no console; na tela, só a frase
            steps.clear();
            recoverToMenu("Algo deu errado ao " + st.label().toLowerCase(java.util.Locale.ROOT) + ": " + brief(e));
        }
        System.out.printf(java.util.Locale.ROOT, "transição: %s em %.0f ms%n", st.label(), (System.nanoTime() - t0) / 1e6);
        stepLabel = steps.isEmpty() ? null : steps.peek().label();
        if (steps.isEmpty()) transitions++;
        return true;
    }

    static String brief(Throwable e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m.length() > 160 ? m.substring(0, 157) + "..." : m;
    }

    /** A cobertura da transição: escuro e o passo atual (texto real); nada de porcentagem inventada. */
    private void drawOverlay(int w, int h) {
        if (overlay <= 0f) return;
        kit.rect(0, 0, w, h, 0x0e0a0800 | Math.round(255 * overlay));
        if (stepLabel == null || overlay < 1f) return;
        labelShown = true;
        // rodada 11.1 (fechamento): o logotipo pequeno, o passo REAL e as sementes P1 em sequência sobre um fundo quente
        // (faixas de cor, sem textura nem cena 3D: nada a carregar para mostrar o carregamento)
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            float k = Math.abs(i - bands / 2f) / (bands / 2f);
            kit.rect(0, i * h / (float) bands, w, h / (float) bands + 1, (0x2a1d14 << 8) | Math.round(255 * (1f - 0.55f * k * k)) & 0xff);
        }
        int lp = px(64);
        MenuView.logo(this, w / 2f, h * 0.36f, lp, true);
        int p = px(26);
        float tw = kit.width(stepLabel, p, true), ty = h * 0.36f + lp * 1.9f;
        kit.text((w - tw) / 2, ty, stepLabel, p, true, UiKit.PARCH_L);
        MenuView.waiting(this, logic.time, 0, ty + p * 1.6f, w, 24 * s, 1.6f);
    }

    private String shownProfileSig;
    private float dragX;

    /** Entrada do menu: cliques, teclas, texto digitado, arrastar (girar o herói) e a roda (andar na lista). */
    private void menuInput(List<InputEvent> events) {
        vx.game.menu.MenuFlow f = menuFlow;
        String[] mains = vx.game.menu.MenuFlow.mainItems();
        for (int i = 0; i < mains.length; i++) if (mains[i].equals(hover) && f.screen == vx.game.menu.MenuFlow.Screen.MAIN) f.mainFocus = i;
        for (InputEvent e : events) {
            switch (e.kind()) {
                case KEY -> {
                    var before = f.screen;
                    f.key(e.code());
                    uiSound(f.screen != before ? (e.code() == GLFW_KEY_ESCAPE ? "ui.cancelar" : "ui.confirmar")
                            : e.code() == GLFW_KEY_UP || e.code() == GLFW_KEY_DOWN ? "ui.selecionar" : null);
                }
                case CHAR -> f.chr(e.code());
                case SCROLL -> { if (!f.typing()) f.key(e.scroll() > 0 ? GLFW_KEY_UP : GLFW_KEY_DOWN); }
                case DOWN -> {
                    if (e.button() != 0) break;
                    Button hit = null;
                    for (Button b : buttons) if (b.contains(e.x(), e.y())) hit = b;
                    if (hit == null && f.screen == vx.game.menu.MenuFlow.Screen.CHARACTERS) { menu.dragging = true; dragX = e.x(); }
                }
                case MOVE -> {
                    if (menu.dragging) { menu.previewYaw += (e.x() - dragX) * 0.012f; dragX = e.x(); }
                }
                case UP -> {
                    if (e.button() != 0) break;
                    if (menu.dragging) { menu.dragging = false; break; }
                    for (Button b : buttons) if (b.contains(e.x(), e.y())) { menuClick(b.id); break; }
                }
                default -> { }
            }
        }
    }

    private static void uiSound(String ev) { if (ev != null) vx.engine.audio.Audio.event(ev); }

    /** Clique num controle do menu: ajustes (cfg:*) aqui; o resto é do MenuFlow. */
    private void menuClick(String id) {
        if (id.startsWith("cfg:")) {
            Prefs pr = logic.prefs;
            switch (id) {
                case "cfg:geral-", "cfg:geral+", "cfg:musica-", "cfg:musica+", "cfg:efeitos-", "cfg:efeitos+" -> {
                    String key = id.substring(4, id.length() - 1);
                    if (audioPrefs.nudge(key, id.endsWith("+") ? 1 : -1)) {
                        applyVolumes();
                        audioPrefs.save(AudioPrefs.FILE);
                    }
                }
                case "cfg:sens-", "cfg:sens+" -> {
                    pr.sensitivity = Math.clamp(pr.sensitivity + (id.endsWith("+") ? 0.0008f : -0.0008f), 0.0008f, 0.012f);
                    logic.cam.sensitivity = pr.sensitivity;
                    pr.save();
                }
                case "cfg:inverter" -> { pr.invertY = !pr.invertY; logic.cam.invertY = pr.invertY; pr.save(); }
                case "cfg:barras" -> { pr.cycleBars(); pr.save(); }
                default -> { }
            }
            uiSound("ui.selecionar");
            return;
        }
        var before = menuFlow.screen;
        menuFlow.action(id);
        uiSound(id.equals("voltar") || id.equals("cancelar-mundo") ? "ui.cancelar"
                : id.startsWith("personagem:") || id.startsWith("mundo:") || id.startsWith("campo:") || id.equals("gerar-semente") ? "ui.selecionar"
                : menuFlow.screen != before || menuFlow.pending != null ? "ui.confirmar" : "ui.selecionar");
    }

    /** Depois da lógica: câmera da apresentação, aparência do personagem escolhido, pedidos e prévias atrasadas. */
    private void menuTick(float dt) {
        vx.game.menu.MenuFlow f = menuFlow;
        f.tick(dt);
        menu.pose = menu.cine.advance(dt);
        var prof = f.profile();
        if (f.screen == vx.game.menu.MenuFlow.Screen.CHARACTERS && prof != null && !prof.appearance().signature().equals(shownProfileSig)) {
            shownProfileSig = prof.appearance().signature();
            async.request(prof.appearance());                              // o modelo de verdade, pelo cache do compositor
        }
        if (!steps.isEmpty()) return;
        if (previewJob != null && renderedSinceStep) {                     // um quadro com "preparando a prévia..." já saiu
            var wd = previewJob;
            previewJob = null;
            try {
                long t0 = System.nanoTime();
                worldPreview(wd, null);
                System.out.printf(java.util.Locale.ROOT, "prévia: %s (semente %s) em %.0f ms%n", wd.name(), wd.seedText(), (System.nanoTime() - t0) / 1e6);
            } catch (RuntimeException e) {
                e.printStackTrace();
                menu.previewFailed.add(wd.id());
            }
        }
        vx.game.menu.MenuFlow.Request r = f.take();
        if (r != null) request(r);
        if (f.screen == vx.game.menu.MenuFlow.Screen.WORLDS && menu.legacyPic == null && procedural == null) legacyPreview();   // barata: o cenário já está montado
        if (f.screen == vx.game.menu.MenuFlow.Screen.WORLDS && previewJob == null && steps.isEmpty()) {
            var wd = f.world();
            if (wd != null && menu.needsPreview(wd) && !menu.previewFailed.contains(wd.id())) {
                if (wd.legacy()) legacyPreview();
                else { previewJob = wd; renderedSinceStep = false; }
            }
        }
    }

    /** Cumpre um pedido do menu. */
    private void request(vx.game.menu.MenuFlow.Request r) {
        var f = menuFlow;
        switch (r.kind()) {
            case QUIT -> logic.quit = true;
            case NEW_CHARACTER, EDIT_CHARACTER -> transition(new Step("Abrindo o criador", () -> prepareCreator(r)));
            case PREVIEW -> {
                ProceduralScene[] ps = new ProceduralScene[1];
                transition(new Step("Gerando a região da semente " + Long.toUnsignedString(r.seed()), () -> {
                    try {
                        ps[0] = generate(r.seed());
                    } catch (ProceduralScene.Rejected e) {
                        steps.clear();
                        f.failed(r, rejected(r.seed()));
                    }
                }), new Step("Desenhando a prévia", () -> {
                    World w = new World(assets, style, ps[0].field, ps[0].worldDoc);
                    try {
                        menu.formPic = renderOverview(w, w.terrainGrid, overviewOf(ps[0], w.terrainGrid), menu.formPic);
                        menu.formSeed = r.seed();
                        previewsMade++;
                    } finally {
                        w.close();
                        Terrain.release(ps[0].field);
                    }
                    f.done(r, null);
                }));
            }
            case CREATE_WORLD -> {
                ProceduralScene[] ps = new ProceduralScene[1];
                vx.game.menu.WorldStore.World[] made = new vx.game.menu.WorldStore.World[1];
                transition(new Step("Gerando e conferindo a região", () -> {
                    try {
                        made[0] = f.worlds.create(r.name(), r.seed(), vx.game.menu.CharacterStore.now(), seed -> {
                            try {
                                ps[0] = generate(seed);
                            } catch (ProceduralScene.Rejected e) {
                                throw new vx.game.menu.WorldStore.CreateFailed(rejected(seed));
                            }
                        });
                    } catch (vx.game.menu.WorldStore.CreateFailed e) {
                        steps.clear();
                        if (ps[0] != null) Terrain.release(ps[0].field);
                        f.failed(r, e.getMessage());
                    }
                }), new Step("Desenhando a prévia", () -> {
                    try {
                        worldPreview(made[0], ps[0]);
                    } catch (RuntimeException e) {                         // o mundo existe; a prévia sai depois
                        e.printStackTrace();
                    }
                    f.done(r, made[0].id());
                }));
            }
            case ENTER -> enterAdventure(r);
        }
    }

    /**
     * Gera a região da semente pelo caminho do jogo. QA: {@code -Dvx.qa.recusar=SEMENTE} simula a recusa do gerador para
     * essa semente (nenhuma semente conhecida é recusada; o caminho de falha da interface precisa ser exercitado).
     */
    ProceduralScene generate(long seed) {
        if (Long.toUnsignedString(seed).equals(System.getProperty("vx.qa.recusar")))
            throw new ProceduralScene.Rejected("semente " + Long.toUnsignedString(seed) + " recusada por -Dvx.qa.recusar (simulação de QA)");
        return ProceduralScene.create(assetsPath, seed);
    }

    /** Mensagem para o jogador quando a semente não gera região jogável (o motivo técnico vai para o console). */
    static String rejected(long seed) {
        return "A semente " + Long.toUnsignedString(seed) + " não gerou uma região jogável. Tente outra (o nome e a semente ficaram aqui).";
    }

    /**
     * Rodada 11.1 (fechamento) — vista ALTERNATIVA da prévia (comparação): mais baixa e inclinada, o olho atrás do
     * assentamento olhando a trilha que sai dele (a estalagem em primeiro plano, o caminho para o posto ao fundo). O rumo
     * desvia até ±60° se a linha de visão (colisão real da região) estiver cortada. {alvo x, y, z, rumo, inclinação,
     * distância, fração livre}.
     */
    static float[] closeViewOf(ProceduralScene ps, Terrain t, Collision col) {
        var L = ps.layout;
        float dx = L.px - L.sx, dz = L.pz - L.sz, len = (float) Math.hypot(dx, dz);
        float tx = L.sx + dx * 0.32f, tz = L.sz + dz * 0.32f, ty = t.groundAt(tx, tz) + 0.6f;
        float base = (float) Math.atan2(-dx, -dz), pitch = (float) Math.toRadians(30), dist = 0.42f * len + 9f;
        float bestYaw = base, best = -1f;
        org.joml.Vector3f tg = new org.joml.Vector3f(tx, ty, tz);
        for (float off : new float[]{0, 20, -20, 40, -40, 60, -60}) {
            float yaw = base + (float) Math.toRadians(off);
            org.joml.Vector3f eye = new org.joml.Vector3f(tx + dist * (float) (Math.cos(pitch) * Math.sin(yaw)), ty + dist * (float) Math.sin(pitch),
                    tz + dist * (float) (Math.cos(pitch) * Math.cos(yaw)));
            float clear = col.cameraInside(eye) ? 0f : col.rayClear(tg, eye, 0.3f);
            if (clear > best + 0.02f) { best = clear; bestYaw = yaw; }
            if (clear >= 0.999f) break;
        }
        return new float[]{tx, ty, tz, bestYaw, pitch, dist, best};
    }

    /** A vista geral de uma região gerada: alvo entre o assentamento e o posto, inclinada, com tudo no quadro. */
    static float[] overviewOf(ProceduralScene ps, Terrain t) {
        var L = ps.layout;
        float cx = (L.sx + L.px) / 2, cz = (L.sz + L.pz) / 2, dx = L.px - L.sx, dz = L.pz - L.sz, len = (float) Math.hypot(dx, dz);
        return new float[]{cx, t.groundAt(cx, cz) + 0.6f, cz, (float) Math.atan2(-dz, dx), (float) Math.toRadians(50), 0.95f * len + 13f};
    }

    /** Fração livre da linha de visão alvo → olho da vista {@code v} (colisão real). */
    static float clearOf(Collision col, float[] v) {
        org.joml.Vector3f tg = new org.joml.Vector3f(v[0], v[1], v[2]);
        org.joml.Vector3f eye = new org.joml.Vector3f(v[0] + v[5] * (float) (Math.cos(v[4]) * Math.sin(v[3])), v[1] + v[5] * (float) Math.sin(v[4]),
                v[2] + v[5] * (float) (Math.cos(v[4]) * Math.cos(v[3])));
        return col.cameraInside(eye) ? 0f : col.rayClear(tg, eye, 0.3f);
    }

    /** Desenha a vista {@code v} (alvo x y z, rumo, inclinação, distância) do mundo {@code w} num FBO 640×360. */
    private Framebuffer renderOverview(World w, Terrain t, float[] v, Framebuffer into) {
        if (previewRenderer == null) previewRenderer = new Renderer();
        if (into == null) into = new Framebuffer(MenuView.PREVIEW_W, MenuView.PREVIEW_H, false, GL_RGBA8);
        Camera c = previewCam;
        c.target.set(v[0], v[1], v[2]);
        c.yaw = v[3];
        c.pitch = v[4];
        c.distance = v[5];
        c.fovDeg = FollowCamera.FOV;
        c.near = 0.1f;
        c.update((float) into.width / into.height);
        Renderer.Frame f = new Renderer.Frame();
        f.camera = c;
        f.style = style;
        f.time = 0f;
        f.toggles = toggles;
        w.submit(f, 0f);
        f.shadowFocus.set(v[0], v[1], v[2]);
        f.shadowRadius = 26f;
        previewRenderer.render(f, into, into.width, into.height, 1.5f);
        return into;
    }

    /** A prévia da vila fixa: a primeira tomada da apresentação, desenhada do cenário do menu (já carregado). */
    private void legacyPreview() {
        var q = menu.cine.at(0f);
        menu.legacyPic = renderOverview(world, terrain, new float[]{q.tx(), q.ty(), q.tz(), q.yaw(), q.pitch(), q.dist()}, menu.legacyPic);
    }

    /**
     * Gera (ou reaproveita {@code ps}) a região do mundo, desenha a vista geral, grava {@code previa.png} (troca atômica) e
     * a chave da prévia no {@code mundo.json}. Mundo, relevo e FBO temporários fecham aqui.
     */
    private void worldPreview(vx.game.menu.WorldStore.World wd, ProceduralScene ps) {
        if (ps == null) ps = generate(wd.seed());
        World w = new World(assets, style, ps.field, ps.worldDoc);
        Framebuffer fb = null;
        try {
            fb = renderOverview(w, w.terrainGrid, overviewOf(ps, w.terrainGrid), null);
            fb.bind();
            glFinish();
            java.awt.image.BufferedImage img = Screenshot.read(fb.width, fb.height);
            Path file = menuFlow.worlds.previewFile(wd.id());
            Path tmp = file.resolveSibling("previa-" + Long.toHexString(System.nanoTime()) + ".tmp");
            try {
                javax.imageio.ImageIO.write(img, "png", tmp.toFile());
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                menuFlow.worlds.save(wd.withPreview(wd.expectedPreviewKey(), ps.summary()));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
            menu.forget(wd.id());
            menu.previewFailed.remove(wd.id());
            menuFlow.refreshWorlds();
            previewsMade++;
        } finally {
            if (fb != null) fb.close();
            w.close();
            Terrain.release(ps.field);
        }
    }

    /**
     * Entra na aventura (personagem × mundo) em três passos com rótulo real: gerar a região (procedural), montar o mundo,
     * preparar a aventura (save da pasta da aventura: continuar ou começar). Falha antes de fechar o menu = mensagem e o
     * menu fica; falha depois = volta ao menu com a mensagem (nada gravado).
     */
    private void enterAdventure(vx.game.menu.MenuFlow.Request r) {
        var f = menuFlow;
        var prof = r.profile();
        var wd = r.world();
        Path dir = f.worlds.adventureDir(wd.id(), prof.id());
        boolean resume = vx.game.menu.MenuFlow.hasSave(dir);
        if (!wd.legacy() && wd.generatorVersion() != vx.game.world.RegionGenerator.VERSION) {
            f.failed(r, "Este mundo foi criado pelo gerador v" + wd.generatorVersion() + "; esta versão do jogo gera v"
                    + vx.game.world.RegionGenerator.VERSION + ". A mesma semente daria outra região.");
            return;
        }
        ProceduralScene[] ps = new ProceduralScene[1];
        World[] built = new World[1];
        List<Step> l = new ArrayList<>();
        if (!wd.legacy()) l.add(new Step("Gerando a região da semente " + wd.seedText(), () -> {
            try {
                ps[0] = generate(wd.seed());
            } catch (ProceduralScene.Rejected e) {
                steps.clear();
                f.failed(r, rejected(wd.seed()));
            }
        }));
        l.add(new Step(wd.legacy() ? "Preparando a vila" : "Montando o mundo", () -> {
            closeSession(wd.legacy());                                    // a vila do menu É a vila da aventura: fica montada
            menu.close();
            menu = null;
            long w0 = System.nanoTime();
            built[0] = ps[0] != null ? new World(assets, style, ps[0].field, ps[0].worldDoc) : takeWorld();
            worldBuildMs = (System.nanoTime() - w0) / 1e6;
        }));
        l.add(new Step(resume ? "Carregando a aventura de " + prof.name() : "Preparando a aventura de " + prof.name(), () -> {
            SceneDef scene = ps[0] != null ? ps[0].scene() : SceneDef.load(assetsPath, SCENE);
            openSession(built[0], scene, ps[0], dir);
            String why = logic.beginAdventure(prof.appearance(), prof.name(), resume);
            if (why != null) {
                recoverToMenu("Não consegui continuar a aventura: " + why + ". O arquivo ficou como estava.");
                return;
            }
            long now = vx.game.menu.CharacterStore.now();
            try {
                f.characters.touch(prof, now);
                f.worlds.save(wd.used(now));
            } catch (IOException e) {
                System.err.println("menu: não consegui anotar o último uso: " + e.getMessage());
            }
        }));
        transition(l.toArray(Step[]::new));
    }

    /** O save do arquivo vale para a cena (vila fixa, ou a região de {@code ps})? null = vale; senão o motivo. Só lê. */
    static String validateSave(Path assetsPath, vx.viewer.ComponentCatalog catalog, Path file, ProceduralScene ps) {
        try {
            readSave(assetsPath, catalog, file, ps);
            return null;
        } catch (IOException | RuntimeException e) {
            return brief(e);
        }
    }

    /** Lê um save com o mesmo leitor do Continuar, contra a cena da vila fixa ou da região {@code ps}. Inválido = exceção. */
    static SaveGame.Data readSave(Path assetsPath, vx.viewer.ComponentCatalog catalog, Path file, ProceduralScene ps) throws IOException {
        if (Files.size(file) > SaveGame.MAX_BYTES) throw new IOException("arquivo grande demais");
        SceneDef sc = ps != null ? ps.scene() : SceneDef.load(assetsPath, SCENE);
        java.util.Collection<vx.game.quest.QuestDefinition> quests = ps != null
                ? (ps.quest != null ? List.of(ps.quest) : List.of())
                : vx.game.quest.QuestDefinition.load(sc.assets.resolve(sc.questsFile)).values();
        java.util.Set<String> objs = new java.util.HashSet<>();
        for (SceneDef.WorldObject o : sc.objects) objs.add(o.id());
        return SaveGame.parse(Files.readString(file), catalog, sc.layout, quests, objs, ps);
    }

    /**
     * Rodada 11.1 — os jogos salvos de antes do menu (o da vila, {@code output/saves/aventura.json}; os por semente,
     * {@code output/saves-procedural/s<N>/}) viram aventuras do catálogo UMA vez, sem pedir nada ao jogador:
     * <ol>
     *   <li>o save é lido com o leitor do Continuar (v1 na vila, v2 na região da semente); inválido = registrado como
     *       recusado, nada criado, o arquivo fica onde está;</li>
     *   <li>o personagem é o da aparência gravada no save (um que já exista com a mesma aparência e sem aventura nesse mundo,
     *       ou um novo com o nome da aparência ou do modelo);</li>
     *   <li>o mundo é a Vila do Posto, ou o mundo da mesma semente (um novo, "Mundo N", se não houver);</li>
     *   <li>a cópia entra pela importação de sempre ({@link vx.game.menu.WorldStore#importSave}): atômica, registrada por
     *       sha256, nunca por cima de uma aventura, nunca duas vezes.</li>
     * </ol>
     * Devolve uma linha por save (para o console e os testes).
     */
    static List<String> adoptOldSaves(Path assetsPath, vx.viewer.ComponentCatalog catalog, vx.game.menu.CharacterStore cs,
                                      vx.game.menu.WorldStore ws, Path legacyDir, Path procRoot, long now) {
        List<String> out = new ArrayList<>();
        for (vx.game.menu.WorldStore.OldSave o : ws.oldSaves(legacyDir, procRoot)) {
            boolean proc = o.kind() == vx.game.menu.WorldStore.Kind.PROCEDURAL;
            ProceduralScene ps = null;
            try {
                if (proc) ps = ProceduralScene.create(assetsPath, o.seed());
                ProceduralScene psf = ps;
                vx.viewer.Appearance a = readSave(assetsPath, catalog, o.file(), ps).appearance();
                vx.game.menu.WorldStore.World w = proc
                        ? ws.list(null).stream().filter(x -> !x.legacy() && x.seed() == o.seed()).findFirst().orElse(null)
                        : vx.game.menu.WorldStore.legacy();
                vx.game.menu.WorldStore.World wf = w;
                var p = cs.list(null).stream().filter(x -> x.appearance().signature().equals(a.signature())
                        && (wf == null || !ws.hasAdventure(wf.id(), x.id()))).findFirst().orElse(null);
                if (p == null) {
                    String n = vx.game.menu.CharacterStore.cleanName(a.name);
                    p = cs.create(n != null ? n : GameLogic.presetName(a.preset), a, now);
                }
                if (w == null) w = ws.create("", o.seed(), now, s -> { });
                ws.importSave(o, w.id(), p.id(), now, f -> validateSave(assetsPath, catalog, f, psf));
                cs.touch(p, Files.getLastModifiedTime(o.file()).toMillis());   // a última aventura = quando o save foi gravado
                out.add(o.file() + " → " + w.name() + " / " + p.name());
            } catch (IOException | RuntimeException | vx.game.menu.WorldStore.CreateFailed e) {
                out.add(o.file() + " recusado: " + brief(e));
                try {
                    ws.recordRejected(o, brief(e), now);
                } catch (IOException ex) {
                    out.add("registro: " + brief(ex));
                }
            } finally {
                if (ps != null) Terrain.release(ps.field);
            }
        }
        return out;
    }

    /**
     * Pausa → "Menu principal" (confirmado, ou sem nada a perder): a aventura fecha (malhas, relevo, som da sessão, combate,
     * missão, diálogo) e a vila do menu volta na TELA INICIAL (Jogar, Configurações, Sair). O personagem e o mundo
     * escolhidos ficam guardados no {@code MenuFlow}: Jogar abre a lista com eles selecionados.
     */
    private void backToMenu() {
        transition(new Step("Voltando ao menu", () -> {
            closeSession(procedural == null);                             // da vila fixa: o mesmo cenário serve ao menu
            openSession(takeWorld(), SceneDef.load(assetsPath, SCENE), null, null);
            enterMenu(vx.game.menu.MenuFlow.Screen.MAIN);                 // o menu principal de verdade (escolhas guardadas)
        }));
    }

    /** Algo falhou no meio de uma transição: o menu volta (sessão nova se a anterior já tinha fechado) com a mensagem. */
    private void recoverToMenu(String msg) {
        steps.clear();
        if (world == null || !menuActive()) {
            closeSession();
            if (menu != null && logic.state != GameLogic.State.MENU) { menu.close(); menu = null; }
            openSession(takeWorld(), SceneDef.load(assetsPath, SCENE), null, null);
            enterMenu(vx.game.menu.MenuFlow.Screen.WORLDS);
        }
        menuFlow.loading = null;
        menuFlow.say(msg, 8f);
        System.err.println("menu: " + msg);
    }

    /*
     * Criar/editar personagem: o criador aprovado (rodada 07.2) na mesma janela. Concluir grava o perfil (criar = id novo;
     * editar = o mesmo id, só a aparência e o nome); Cancelar (botão ou Esc) não grava nada.
     */
    /** O {@code App} do criador: montado uma vez (a cena dele fica no cache) e fechado com o jogo. */
    private App creatorApp;
    /** O criador já preparado pelo passo "Abrindo o criador" (o laço da janela ou o roteiro o abre). */
    private Creator preparedCreator;
    private int lastW = 1920, lastH = 1080;

    /**
     * Passo de carregamento: o criador com a aparência certa e a cena dele montada (a primeira montagem da clareira leva
     * ~1 s; antes ela acontecia no primeiro quadro do criador e congelava a janela sem aviso).
     */
    private void prepareCreator(vx.game.menu.MenuFlow.Request r) {
        long o0 = System.nanoTime();
        if (creatorApp == null) creatorApp = new App(window, assetsPath);
        Creator cr = new Creator(creatorApp, window, assetsPath);
        boolean edit = r.kind() == vx.game.menu.MenuFlow.RequestKind.EDIT_CHARACTER;
        cr.cs.finishLabel = edit ? "Salvar personagem" : "Criar personagem";
        cr.cs.cancelLabel = "Cancelar";
        if (edit) cr.start(r.profile().appearance(), r.profile().name());
        cr.warm(lastW, lastH);
        preparedCreator = cr;
        creatorReq = r;
        System.out.printf(java.util.Locale.ROOT, "criador: preparado em %.0f ms%n", (System.nanoTime() - o0) / 1e6);
    }

    private void openMenuCreator(Path script, Path out, int fps) {
        var r = creatorReq;
        creatorReq = null;
        var f = menuFlow;
        boolean edit = r.kind() == vx.game.menu.MenuFlow.RequestKind.EDIT_CHARACTER;
        if (window.cursorCaptured()) window.captureCursor(false);
        try (Creator cr = preparedCreator) {
            preparedCreator = null;
            if (script != null) {
                cr.recorded = recorded;
                cr.script(script, out, fps);
                recorded = cr.recorded;
            } else cr.run(0);
            if (cr.cs.finished) {
                String name = vx.game.menu.CharacterStore.cleanName(cr.cs.name);
                if (name == null) name = edit ? r.profile().name() : "Personagem " + (f.profiles.size() + 1);
                var p = edit ? f.characters.update(r.profile(), cr.cs.appearance, name)
                        : f.characters.create(name, cr.cs.appearance, vx.game.menu.CharacterStore.now());
                f.done(r, p.id());
                f.say(edit ? "Personagem atualizado" : "Personagem criado: " + p.name(), 3f);
            } else f.say(edit ? "Edição cancelada: nada mudou" : "Criação cancelada", 3f);
            if (cr.cs.quitRequested) logic.quit = true;                    // o X da janela dentro do criador
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            f.failed(r, "Não consegui gravar o personagem: " + brief(e));
        }
        input.releaseAll();
        shownProfileSig = null;
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
            if (creatorReq != null) { openMenuCreator(null, null, 30); continue; }
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
                    case "sens-", "sens+" -> {                             // rodada 11.1: gravada nas preferências
                        logic.cam.sensitivity = Math.clamp(logic.cam.sensitivity + (b.id.endsWith("+") ? 0.0008f : -0.0008f), 0.0008f, 0.012f);
                        logic.prefs.sensitivity = logic.cam.sensitivity;
                        logic.prefs.save();
                    }
                    case "inverter" -> {
                        logic.cam.invertY = !logic.cam.invertY;
                        logic.prefs.invertY = logic.cam.invertY;
                        logic.prefs.save();
                    }
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
            if (logic.quit) { System.out.println("jogo: fechado durante o roteiro (o resto não roda)"); break; }
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
                    for (int i = 0; i < 600 && (!steps.isEmpty() || menuActive() && menuFlow.pending != null); i++) {
                        tickFrame(List.of(), dt, w, h, record, out);       // "Abrindo o criador": o passo roda depois de um quadro
                        render(null, w, h, 1f, dt);
                    }
                    if (creatorReq != null) {                              // rodada 11.1: pedido do menu (novo/editar personagem)
                        openMenuCreator(Path.of(t[1]), out, fps);
                        vx.engine.audio.Audio.recording(record);
                        settle(w, h, dt, out);
                        break;
                    }
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
                // ---- rodada 11.1: menu principal
                case "digitar" -> {                                        // texto (sublinhado = espaço), um caractere por quadro
                    String txt = line.substring(line.indexOf(' ') + 1).replace('_', ' ');
                    for (int i = 0; i < txt.length(); ) {
                        int cp = txt.codePointAt(i);
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.CHAR, mx, my, 0, 0, cp)), dt, w, h, record, out);
                        i += Character.charCount(cp);
                    }
                }
                case "arrastar" -> {                                       // arrastar dx px em s segundos (girar o herói)
                    float dx = Float.parseFloat(t[1]);
                    int n = Math.max(1, Math.round(Float.parseFloat(t[2]) * fps));
                    float ax = w * 0.74f, ay = h * 0.5f;
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, ax, ay, 0, 0, 0)), dt, w, h, record, out);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.DOWN, ax, ay, 0, 0, 0)), dt, w, h, record, out);
                    for (int i = 1; i <= n; i++) tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, ax + dx * i / n, ay, 0, 0, 0)), dt, w, h, record, out);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.UP, ax + dx, ay, 0, 0, 0)), dt, w, h, record, out);
                    mx = ax + dx;
                    my = ay;
                }
                case "esperar-menu" -> {                                   // até a transição, a prévia e os retratos terminarem
                    long end = System.currentTimeMillis() + Math.round(Float.parseFloat(t.length > 1 ? t[1] : "60") * 1000);
                    render(null, w, h, 1f, dt);                            // a tela atual pede retratos e prévias ao ser desenhada
                    int n = 0;
                    while (System.currentTimeMillis() < end && (!steps.isEmpty() || overlay > 0f || previewJob != null || async.busy()
                            || menuActive() && (!menu.portraitsIdle() || menuFlow.pending != null))) {
                        tickFrame(List.of(), dt, w, h, record, out);
                        render(null, w, h, 1f, dt);
                        n++;
                    }
                    System.out.printf("esperar-menu: %d quadros; %s%n", n, menuState());
                }
                case "menu-estado" -> System.out.println("menu-estado: " + menuState());
                case "previa-comparar" -> {                                // rodada 11.1: vista atual × alternativa da prévia (semente, pasta)
                    long seed = Long.parseUnsignedLong(t[1]);
                    ProceduralScene ps = ProceduralScene.create(assetsPath, seed);
                    World pw = new World(assets, style, ps.field, ps.worldDoc);
                    try {
                        Collision col = Collision.build(pw.terrainGrid, Collision.profiles(assetsPath), pw.placements(), pw.bodies(), ps.scene());
                        float[] a0 = overviewOf(ps, pw.terrainGrid), b0 = closeViewOf(ps, pw.terrainGrid, col);
                        float ca = clearOf(col, a0);
                        for (Object[] v : new Object[][]{{"A", a0}, {"B", b0}}) {
                            Framebuffer fb = renderOverview(pw, pw.terrainGrid, (float[]) v[1], null);
                            fb.bind();
                            glFinish();
                            Screenshot.save(Screenshot.read(fb.width, fb.height), out.resolve("previa-s" + t[1] + "-" + v[0] + ".png"));
                            fb.close();
                        }
                        System.out.printf(java.util.Locale.ROOT, "previa-comparar s%s: atual livre %.2f; alternativa rumo %.0f° livre %.2f%n", t[1], ca,
                                Math.toDegrees(b0[3]), b0[6]);
                    } finally {
                        pw.close();
                        Terrain.release(ps.field);
                    }
                }
                case "shot-agora" -> {                                     // captura SEM esperar (a tela de carregamento no meio)
                    Screenshot.save(capture(w, h, 1f), out.resolve(t[1] + ".png"));
                    System.out.println("jogo: " + out.resolve(t[1] + ".png"));
                }
                case "fechar-janela" -> {                                  // o X da janela: o mesmo caminho do callback do GLFW
                    window.simulateCloseRequest();
                    tickFrame(List.of(), dt, w, h, record, out);
                    render(null, w, h, 1f, dt);
                    System.out.println("fechar-janela: " + (logic.quit ? "o jogo fecha" : "confirmação aberta: " + logic.leaving + " (" + logic.state + ")"));
                }
                case "vitrine" -> {                                        // QA: moradores e o que a vitrine esconde
                    render(null, w, h, 1f, dt);
                    Player pl = logic.player;
                    org.joml.Vector3f e = cam.position();
                    StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "vitrine: herói (%.2f, %.2f) olho (%.2f, %.2f)", pl.x, pl.z, e.x, e.z));
                    for (GameLogic.Npc n : logic.npcs)
                        sb.append(String.format(java.util.Locale.ROOT, " | %s (%.1f, %.1f) %s", n.def.name(), n.def.x(), n.def.z(), behindHero(n.def.x(), n.def.z(), pl) ? "escondido" : "visível"));
                    System.out.println(sb);
                }
                case "menu-personagem" -> {                                // escolhe o personagem pelo nome (sublinhado = espaço)
                    String want = line.substring(line.indexOf(' ') + 1).replace('_', ' ');
                    int i = 0;
                    for (; i < menuFlow.profiles.size(); i++) if (menuFlow.profiles.get(i).name().equals(want)) break;
                    if (i == menuFlow.profiles.size()) throw new IllegalArgumentException("personagem não está na lista: " + want);
                    menuFlow.action("personagem:" + i);
                    tickFrame(List.of(), dt, w, h, record, out);
                }
                case "menu-mundo" -> {                                     // escolhe o mundo pelo nome (sublinhado = espaço) ou id
                    String want = line.substring(line.indexOf(' ') + 1).replace('_', ' ');
                    int i = 0;
                    for (; i < menuFlow.worldList.size(); i++) if (menuFlow.worldList.get(i).name().equals(want) || menuFlow.worldList.get(i).id().equals(want)) break;
                    if (i == menuFlow.worldList.size()) throw new IllegalArgumentException("mundo não está na lista: " + want);
                    menuFlow.action("mundo:" + i);
                    tickFrame(List.of(), dt, w, h, record, out);
                }
                case "menu-tomada" -> { if (menu != null) { menu.cine.seek(Float.parseFloat(t[1])); menu.pose = menu.cine.advance(0f); } }
                case "menu-leiaute" -> MenuView.layout = t[1];
                case "menu-recursos" -> System.out.printf(java.util.Locale.ROOT, "menu-recursos %s: malhas %d (%.1f MB), texturas %d, fbos %d, grades de relevo %d, sessões %d, transições %d, prévias %d, heap %.0f MB%n",
                        t.length > 1 ? t[1] : "", vx.engine.Mesh.liveMeshes, vx.engine.Mesh.liveBytes / 1048576.0, vx.engine.Texture.live, Framebuffer.live,
                        Terrain.cached(), sessions, transitions, previewsMade, (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576.0);
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
                case "derrubar" -> {                                       // rodada 10: montagem técnica — derruba o inimigo #i
                    Enemy e = logic.combat.enemies.get(Integer.parseInt(t[1].substring(1)));   // pelo caminho do dano (receive)
                    e.receive(9999, logic.player.x, logic.player.z);
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
                case "medir-save" -> {                                     // rodada 10: N gravações + N leituras do save (ms)
                    int n = Integer.parseInt(t[1]);
                    double[] wr = new double[n], rd = new double[n];
                    for (int i = 0; i < n; i++) {
                        long a0 = System.nanoTime();
                        if (!logic.save(false)) { System.out.println("medir-save: falhou: " + logic.saveError); break; }
                        long a1 = System.nanoTime();
                        logic.refreshSaved();
                        long a2 = System.nanoTime();
                        wr[i] = (a1 - a0) / 1e6;                           // save() já relê para o Continuar: grava + relê
                        rd[i] = (a2 - a1) / 1e6;
                    }
                    java.util.Arrays.sort(wr);
                    java.util.Arrays.sort(rd);
                    long bytes = logic.saveDir != null && Files.exists(logic.saveDir.resolve(SaveGame.FILE)) ? Files.size(logic.saveDir.resolve(SaveGame.FILE)) : -1;
                    System.out.printf(java.util.Locale.ROOT, "medir-save: %d vezes, gravar+validar mediana %.2f ms (máx %.2f), ler mediana %.2f ms (máx %.2f), %d bytes%n",
                            n, wr[n / 2], wr[n - 1], rd[n / 2], rd[n - 1], bytes);
                }
                case "vista" -> {                                          // rodada 11: câmera técnica — alvo x z, rumo°, inclinação°, distância
                    float vx = Float.parseFloat(t[1]), vz = Float.parseFloat(t[2]);
                    logic.cam.frame(true, new org.joml.Vector3f(vx, terrain.groundAt(vx, vz) + 0.6f, vz), (float) Math.toRadians(Float.parseFloat(t[3])),
                            (float) Math.toRadians(Float.parseFloat(t[4])), Float.parseFloat(t[5]));
                    logic.cam.reset();
                    logic.cam.snapshot();
                }
                case "vista-lugar" -> {                                    // rodada 11: vistas calculadas da região gerada
                    var L = procedural.layout;
                    float[] v = switch (t[1]) {                               // alvo x, z, rumo do olho (graus), inclinação, distância
                        case "geral" -> {
                            float cx = (L.sx + L.px) / 2, cz = (L.sz + L.pz) / 2, dx = L.px - L.sx, dz = L.pz - L.sz;
                            float len = (float) Math.hypot(dx, dz);
                            yield new float[]{cx, cz, (float) Math.toDegrees(Math.atan2(-dz, dx)), 50f, 0.95f * len + 13f};
                        }
                        case "assentamento" -> new float[]{L.sx, L.sz, (float) Math.toDegrees(Math.atan2(L.fx, L.fz)) + 25f, 32f, 13f};
                        case "posto" -> new float[]{L.px + L.gx, L.pz + L.gz, (float) Math.toDegrees(Math.atan2(-L.gx, -L.gz)) + 20f, 26f, 8.5f};
                        case "trilha" -> {
                            float[] m = L.path.get(L.path.size() / 2), m2 = L.path.get(L.path.size() / 2 + 2);
                            yield new float[]{m[0], m[1], (float) Math.toDegrees(Math.atan2(-(m2[1] - m[1]), m2[0] - m[0])), 34f, 9.5f};
                        }
                        default -> throw new IllegalArgumentException("vista desconhecida: " + t[1]);
                    };
                    logic.cam.technicalView = true;
                    // rumo com a vista livre (copas e troncos são formas de câmera): o desvio menor que não atravessa nada
                    org.joml.Vector3f tg = new org.joml.Vector3f(v[0], terrain.groundAt(v[0], v[1]) + 0.6f, v[1]);
                    float bestYaw = v[2], bestClear = -1f;
                    for (float off : new float[]{0, 25, -25, 50, -50, 75, -75, 105, -105, 135, -135, 180}) {
                        double y = Math.toRadians(v[2] + off), p = Math.toRadians(v[3]);
                        org.joml.Vector3f eye = new org.joml.Vector3f(tg.x + v[4] * (float) (Math.cos(p) * Math.sin(y)), tg.y + v[4] * (float) Math.sin(p),
                                tg.z + v[4] * (float) (Math.cos(p) * Math.cos(y)));
                        float clear = logic.collision.rayClear(tg, eye, 0.3f);
                        if (clear > bestClear + 0.02f) { bestClear = clear; bestYaw = v[2] + off; }
                        if (clear >= 0.999f) break;
                    }
                    System.out.printf(java.util.Locale.ROOT, "vista-lugar %s: rumo %.0f° (livre %.2f)%n", t[1], bestYaw, bestClear);
                    logic.cam.frame(true, tg, (float) Math.toRadians(bestYaw), (float) Math.toRadians(v[3]), v[4]);
                    logic.cam.reset();
                    logic.cam.snapshot();
                }
                case "ir-lugar" -> {                                       // rodada 11: montagem técnica — herói num lugar da região
                    var L = procedural.layout;
                    float[] q = switch (t[1]) {                               // x, z, rumo (graus)
                        case "assentamento" -> new float[]{L.startX, L.startZ, L.startYawDeg};
                        case "posto" -> new float[]{L.px - L.gx * 2.6f, L.pz - L.gz * 2.6f, (float) Math.toDegrees(Math.atan2(L.gx, L.gz))};
                        case "marco" -> {
                            var f = L.features.getFirst();
                            float a = (float) Math.toRadians(f.yawDeg());
                            float x = f.x() + (float) Math.sin(a) * 1.15f, z = f.z() + (float) Math.cos(a) * 1.15f;
                            yield new float[]{x, z, (float) Math.toDegrees(Math.atan2(f.x() - x, f.z() - z))};
                        }
                        case "doador" -> {
                            SceneDef.Interactive n = logic.scene.interactives.stream().filter(i -> i.id().equals(procedural.binding.giver())).findFirst().orElseThrow();
                            float a = (float) Math.toRadians(n.yawDeg());
                            float x = n.x() + (float) Math.sin(a) * 1.1f, z = n.z() + (float) Math.cos(a) * 1.1f;
                            yield new float[]{x, z, (float) Math.toDegrees(Math.atan2(n.x() - x, n.z() - z))};
                        }
                        case "trilha" -> {
                            float[] m = L.path.get(L.path.size() / 2), m2 = L.path.get(L.path.size() / 2 + 2);
                            yield new float[]{m[0], m[1], (float) Math.toDegrees(Math.atan2(m2[0] - m[0], m2[1] - m[1]))};
                        }
                        case "casa" -> {                                        // diante da estalagem, de frente para a parede
                            var house = L.props.stream().filter(p -> p.name().equals("house")).findFirst().orElseThrow();
                            float a = (float) Math.toRadians(house.rotDeg());
                            float x = house.x() + (float) Math.sin(a + 1.2f) * 4.6f, z = house.z() + (float) Math.cos(a + 1.2f) * 4.6f;
                            yield new float[]{x, z, (float) Math.toDegrees(Math.atan2(house.x() - x, house.z() - z))};
                        }
                        default -> throw new IllegalArgumentException("lugar desconhecido: " + t[1]);
                    };
                    if (t.length > 2 && t[2].equals("andando")) {             // entrada real: W + mouse até o ponto
                        List<InputEvent> downs = new ArrayList<>(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_W)));
                        tickFrame(downs, dt, w, h, record, out);
                        for (int i = 0; i < Math.round(25f * fps); i++) {
                            Player pl = logic.player;
                            if (Math.hypot(q[0] - pl.x, q[1] - pl.z) < 0.35f) break;
                            float want = (float) Math.atan2(pl.x - q[0], pl.z - q[1]);
                            float d = (float) Math.atan2(Math.sin(want - logic.cam.curYaw), Math.cos(want - logic.cam.curYaw));
                            mx += Math.clamp(-d / logic.cam.sensitivity * 0.25f, -40f, 40f);
                            tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, mx, my, 0, 0, 0)), dt, w, h, record, out);
                        }
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_W)), dt, w, h, record, out);
                        float want = (float) Math.toRadians(q[2]) + (float) Math.PI;   // vira para o alvo com o mouse
                        float d = (float) Math.atan2(Math.sin(want - logic.cam.curYaw), Math.cos(want - logic.cam.curYaw));
                        int n = Math.round(0.4f * fps);
                        for (int i = 0; i < n; i++) { mx += -d / logic.cam.sensitivity / n; tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, mx, my, 0, 0, 0)), dt, w, h, record, out); }
                        List<InputEvent> nudge = List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_W));   // um passo curto para o corpo virar
                        tickFrame(nudge, dt, w, h, record, out);
                        for (int i = 0; i < Math.round(0.12f * fps); i++) tickFrame(List.of(), dt, w, h, record, out);
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_W)), dt, w, h, record, out);
                        System.out.printf(java.util.Locale.ROOT, "ir-lugar %s andando: (%.2f, %.2f)%n", t[1], logic.player.x, logic.player.z);
                        break;
                    }
                    logic.player.place(q[0], q[1], (float) Math.toRadians(q[2]), logic.collision);
                    logic.player.snapshot();
                    logic.cam.yaw = logic.player.yaw + (float) Math.PI;
                    logic.cam.curYaw = logic.cam.yaw;
                    logic.cam.reset();
                    logic.cam.snapshot();
                }
                case "seguir-trilha" -> {                                  // rodada 11: entrada real — W + mouse pelo eixo da trilha
                    var L = procedural.layout;
                    boolean back = t[1].equals("volta"), run = t.length > 2 && t[2].equals("correndo");
                    List<float[]> pts = new ArrayList<>();
                    for (int i = 0; i < L.path.size(); i += 3) pts.add(L.path.get(i));
                    pts.add(L.path.getLast());
                    if (back) { java.util.Collections.reverse(pts); pts.add(new float[]{L.startX, L.startZ}); }
                    else pts.add(new float[]{L.px - L.gx * 1.2f, L.pz - L.gz * 1.2f});
                    int wp = 0;                                               // começa do ponto mais perto (na direção pedida)
                    float best = Float.MAX_VALUE;
                    for (int i = 0; i < pts.size(); i++) {
                        float d = (float) Math.hypot(pts.get(i)[0] - logic.player.x, pts.get(i)[1] - logic.player.z);
                        if (d < best) { best = d; wp = i; }
                    }
                    List<InputEvent> downs = new ArrayList<>(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_W)));
                    if (run) downs.add(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_LEFT_SHIFT));
                    tickFrame(downs, dt, w, h, record, out);
                    int limit = Math.round(60f * fps);
                    for (int i = 0; i < limit && wp < pts.size(); i++) {
                        Player pl = logic.player;
                        float[] g = pts.get(wp);
                        if (Math.hypot(g[0] - pl.x, g[1] - pl.z) < (wp == pts.size() - 1 ? 0.45f : 1.0f)) { wp++; continue; }
                        float want = (float) Math.atan2(pl.x - g[0], pl.z - g[1]);
                        float d = (float) Math.atan2(Math.sin(want - logic.cam.curYaw), Math.cos(want - logic.cam.curYaw));
                        mx += Math.clamp(-d / logic.cam.sensitivity * 0.25f, -40f, 40f);
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.MOVE, mx, my, 0, 0, 0)), dt, w, h, record, out);
                    }
                    List<InputEvent> ups = new ArrayList<>(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_W)));
                    if (run) ups.add(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_LEFT_SHIFT));
                    tickFrame(ups, dt, w, h, record, out);
                    System.out.printf(java.util.Locale.ROOT, "seguir-trilha %s: %s em (%.2f, %.2f)%n", t[1], wp >= pts.size() ? "chegou" : "parou no ponto " + wp,
                            logic.player.x, logic.player.z);
                }
                case "conversar" -> {                                      // rodada 11: entrada real — E até o fim; aceitar | recusar
                    boolean accept = t.length < 2 || !t[1].equals("recusar");
                    float pause = t.length > 2 ? Float.parseFloat(t[2]) : 1.2f;
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_E)), dt, w, h, record, out);
                    tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_E)), dt, w, h, record, out);
                    for (int k = 0; k < 30 && logic.state == GameLogic.State.DIALOG; k++) {
                        for (int i = 0; i < Math.round(pause * fps); i++) tickFrame(List.of(), dt, w, h, record, out);
                        if (logic.lastLine() && !logic.dialogChoices.isEmpty() && (accept ? logic.choiceSelected != 0 : logic.choiceSelected == 0)) {
                            tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_RIGHT)), dt, w, h, record, out);
                            tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_RIGHT)), dt, w, h, record, out);
                            for (int i = 0; i < Math.round(0.4f * fps); i++) tickFrame(List.of(), dt, w, h, record, out);
                        }
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_E)), dt, w, h, record, out);
                        tickFrame(List.of(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_E)), dt, w, h, record, out);
                    }
                    System.out.println("conversar: " + logic.state + (logic.dialogContext != null ? " (contexto " + logic.dialogContext + ")" : ""));
                }
                case "medir-geracao" -> {                                  // rodada 11: N gerações completas da semente (ms)
                    int n = Integer.parseInt(t[1]);
                    double[] comp = new double[n], val = new double[n], tot = new double[n];
                    long mem0 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    for (int i = 0; i < n; i++) {
                        long a0 = System.nanoTime();
                        ProceduralScene ps = ProceduralScene.create(assetsPath, procedural.seed);
                        tot[i] = (System.nanoTime() - a0) / 1e6;
                        comp[i] = ps.composeNanos / 1e6;
                        val[i] = ps.validateNanos / 1e6;
                    }
                    java.util.Arrays.sort(comp); java.util.Arrays.sort(val); java.util.Arrays.sort(tot);
                    // consulta de POI e escolha da missão (o que um modelo faz com os fatos): 10 000 vezes
                    long q0 = System.nanoTime();
                    int hits = 0;
                    for (int i = 0; i < 10000; i++) if (procedural.layout.poi("trail_post") != null) hits++;
                    double poiUs = (System.nanoTime() - q0) / 1e3 / 10000;
                    var tpl = ProceduralScene.read(assetsPath, "modelos_missao_e.json").getAsJsonArray("modelos").get(0).getAsJsonObject();
                    var res = ProceduralScene.read(assetsPath, "habitantes_e.json").getAsJsonObject("habitantes");
                    long i0 = System.nanoTime();
                    for (int i = 0; i < 200; i++) ProceduralScene.instantiate(assetsPath, procedural.layout, tpl, res, procedural.binding.rumo());
                    double instMs = (System.nanoTime() - i0) / 1e6 / 200;
                    long mem1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    System.out.printf(java.util.Locale.ROOT, "medir-geracao: %d vezes; total mediana %.1f ms (máx %.1f), compor %.2f ms, conferir na colisão real %.1f ms; consulta de POI %.2f µs (%d); instanciar a missão %.3f ms; memória Java +%d MB durante a medição%n",
                            n, tot[n / 2], tot[n - 1], comp[n / 2], val[n / 2], poiUs, hits, instMs, (mem1 - mem0) >> 20);
                }
                case "vista-fim" -> { logic.cam.technicalView = false; logic.cam.frame(false, null, 0, 0, 0); logic.cam.reset(); logic.cam.snapshot(); }
                case "regiao" -> {                                         // rodada 11: o que a semente gerou, no log
                    if (procedural == null) { System.out.println("regiao: mundo fixo"); break; }
                    var L = procedural.layout;
                    System.out.printf(java.util.Locale.ROOT, "regiao: semente %s tentativa %d arranjo %d lago %s assentamento (%.2f, %.2f) posto (%.2f, %.2f) marco %s doador %s moradores %s props %d%n",
                            Long.toUnsignedString(procedural.seed), L.attempt, L.arrangement, L.pond != null ? String.format(java.util.Locale.ROOT, "(%.1f, %.1f)", L.pond[0], L.pond[1]) : "não",
                            L.sx, L.sz, L.px, L.pz, L.features.isEmpty() ? "-" : L.features.getFirst().id(), procedural.binding != null ? procedural.binding.giver() : "-",
                            L.npcs.stream().map(vx.game.world.RegionLayout.Npc::id).toList(), L.props.size());
                }
                case "missao" -> {                                         // rodada 10: estado das missões no log
                    var q = logic.quests;
                    StringBuilder sb = new StringBuilder("missao:");
                    if (q != null) for (var p : q.all()) {
                        sb.append(' ').append(p.def.id()).append(' ').append(p.state().key()).append(" objetivo ").append(p.objectiveIndex())
                                .append(" derrotados ").append(p.defeated());
                    }
                    if (q != null) sb.append(" | moedas ").append(q.coins()).append(" | acesos ").append(q.objectsOn());
                    sb.append(" | saves ").append(logic.saves).append(logic.saveError != null ? " (erro: " + logic.saveError + ")" : "");
                    if (logic.combat != null) sb.append(" | arena ").append(logic.combat.encounter).append(" vivos ").append(logic.combat.alive());
                    System.out.println(sb);
                }
                case "lutar" -> {                                          // rodada 10: entrada real — trava a mira (Q), aproxima (W)
                    int n = Math.round(Float.parseFloat(t[1]) * fps);      // e clica a cada 0,38 s até não sobrar fungo vivo
                    boolean wDown = false;
                    float sinceQ = 9f, sinceClick = 9f;
                    for (int i = 0; i < n && logic.combat.alive() > 0 && logic.combat.hero.alive(); i++) {
                        List<InputEvent> ev = new ArrayList<>();
                        sinceQ += dt;
                        sinceClick += dt;
                        if (!logic.targeting.locked() && sinceQ > 0.5f) {
                            ev.add(new InputEvent(InputEvent.Kind.KEY, mx, my, 0, 0, GLFW_KEY_Q));
                            ev.add(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_Q));
                            sinceQ = 0f;
                        }
                        Enemy tg = logic.targeting.target;
                        boolean far = tg != null && Math.hypot(tg.x - logic.player.x, tg.z - logic.player.z) > 1.15f;
                        if (far != wDown) { ev.add(new InputEvent(far ? InputEvent.Kind.KEY : InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_W)); wDown = far; }
                        if (tg != null && !far && sinceClick > 0.38f) { ev.add(new InputEvent(InputEvent.Kind.DOWN, mx, my, 0, 0, 0)); sinceClick = 0f; }
                        else if (sinceClick > 0.05f && sinceClick < 0.05f + dt) ev.add(new InputEvent(InputEvent.Kind.UP, mx, my, 0, 0, 0));
                        tickFrame(ev, dt, w, h, record, out);
                    }
                    List<InputEvent> up = new ArrayList<>(List.of(new InputEvent(InputEvent.Kind.UP, mx, my, 0, 0, 0)));
                    if (wDown) up.add(new InputEvent(InputEvent.Kind.KEY_UP, mx, my, 0, 0, GLFW_KEY_W));
                    tickFrame(up, dt, w, h, record, out);
                    System.out.printf("lutar: vivos %d, herói %s%n", logic.combat.alive(), logic.combat.hero.alive() ? "de pé" : "caído");
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
                    render(null, w, h, 1f, dt);                            // rodada 11.1: botões da tela ATUAL (o desenho só lê a lógica)
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

    /** Uma linha do estado do menu (roteiros). */
    String menuState() {
        if (!menuActive()) return "fora do menu: " + logic.state + (logic.saveDir != null ? " save " + logic.saveDir : "") + (logic.started ? " começado" : "");
        var f = menuFlow;
        return String.format("tela %s, %d personagem(ns) (escolhido %s), %d mundo(s) (escolhido %s), aventura %s, erro %s, mensagem %s",
                f.screen, f.profiles.size(), f.profile() == null ? "-" : f.profile().name() + "/" + f.profile().id(), f.worldList.size(),
                f.world() == null ? "-" : f.world().name() + "/" + f.world().id() + "/" + f.world().seedText(), f.hasAdventure() ? "sim" : "não",
                f.error, f.message != null && f.time < f.messageUntil ? f.message : "-");
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
            case "J" -> GLFW_KEY_J; case "T" -> GLFW_KEY_T; case "C" -> GLFW_KEY_C;   // rodada 10: diário, rastreador, continuar
            case "N" -> GLFW_KEY_N; case "BACKSPACE" -> GLFW_KEY_BACKSPACE;          // rodada 11.1: menu
            default -> throw new IllegalArgumentException("tecla desconhecida: " + name);
        };
    }

    @Override
    public void close() {
        if (sfx != null) sfx.close();
        if (captureFbo != null) captureFbo.close();
        if (deathMesh != null) deathMesh.mesh.close();
        if (mote != null) mote.close();
        if (menu != null) menu.close();
        if (previewRenderer != null) previewRenderer.close();
        if (preparedCreator != null) preparedCreator.close();
        if (creatorApp != null) creatorApp.close();
        async.close();
        renderer.close();
        kit.close();
        if (world != null) world.close();
        if (kept != null) kept.close();
        assets.close();
    }
}
