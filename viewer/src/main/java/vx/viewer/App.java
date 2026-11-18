package vx.viewer;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import vx.engine.Camera;
import vx.engine.Framebuffer;
import vx.engine.Hud;
import vx.engine.Mesh;
import vx.engine.Screenshot;
import vx.engine.Window;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.glFinish;

/** Aplicativo de visualização: modos de avaliação, entrada, HUD, captura e benchmark. */
public final class App implements AutoCloseable {
    public enum Mode {
        SCENE("CENA COMPLETA"), CHARACTER("PERSONAGEM ISOLADO"), SIDE_BY_SIDE("LADO A LADO (5 DIRECOES)"),
        PROPORTIONS("ESTUDO DE PROPORCOES"), LINEUP("COMPARACAO EM LINHA"), CUSTOMIZE("PERSONALIZACAO");
        final String label;
        Mode(String label) { this.label = label; }
    }

    /** Qualidade gráfica: SSAA da cena, amostras de sombra, bloom e contorno. */
    public enum Quality {
        BAIXA(1.0f), PADRAO(1.0f), ALTA(1.5f);
        /** PADRÃO usa FXAA no pós (rodada 03); BAIXA sem antisserrilhado; ALTA usa SSAA 1,5×. */
        public boolean fxaa() { return this == PADRAO; }
        public final float ssaa;
        Quality(float ssaa) { this.ssaa = ssaa; }
    }

    /**
     * Conjuntos do modo LINHA: todos os modelos sob a MESMA luz, câmera, chão e pose. Diretórios
     * inexistentes são ignorados. A ordem é a da apresentação, da esquerda para a direita.
     */
    public static final Map<String, List<String>> LINEUPS = new LinkedHashMap<>();
    static {
        LINEUPS.put("variantes", List.of("characters/b/adventurer", "characters/variants/v1", "characters/variants/v2",
                "characters/variants/v3", "characters/d/adventurer"));
        LINEUPS.put("v1-v2-v3", List.of("characters/variants/v1", "characters/variants/v2", "characters/variants/v3"));
        // rodada 04: todos os equipamentos lado a lado (locomoção com arma, guarda)
        LINEUPS.put("r4-armas", List.of("characters/e/adventurer", "characters/r4/adventurer_shield", "characters/r4/adventurer_longblade",
                "characters/e/ranger", "characters/e/mage"));
        LINEUPS.put("herois", List.of("characters/e/adventurer", "characters/e/ranger", "characters/e/mage"));
        // rodada 06: primeira expansão controlada (NPC e criatura novos ao lado do conjunto aprovado)
        LINEUPS.put("trio", List.of("characters/npc/kelyra", "characters/npc/indira", "characters/npc/suvia"));
        LINEUPS.put("elenco", List.of("characters/e/adventurer", "characters/e/ranger", "characters/e/mage", "characters/npc/cartografa",
                "characters/npc/kelyra", "characters/npc/indira", "characters/npc/suvia"));
        LINEUPS.put("vila", List.of("characters/e/adventurer", "characters/npc/cartografa", "characters/npc/kelyra", "characters/npc/indira", "characters/npc/suvia"));
        LINEUPS.put("r6-npc", List.of("characters/e/adventurer", "characters/e/ranger", "characters/e/mage", "characters/npc/cartografa"));
        LINEUPS.put("r6-criaturas", List.of("characters/e/adventurer", "creatures/e/pompom", "creatures/e/lumi",
                "creatures/e/mariposa", "creatures/e/fungo", "creatures/e/tortuga"));
        LINEUPS.put("herois-b-e-d", List.of("characters/b/adventurer", "characters/e/adventurer", "characters/d/adventurer",
                "characters/b/ranger", "characters/e/ranger", "characters/d/ranger",
                "characters/b/mage", "characters/e/mage", "characters/d/mage"));
        LINEUPS.put("criaturas", List.of("characters/e/adventurer", "creatures/e/pompom", "creatures/e/lumi",
                "creatures/e/fungo", "creatures/e/tortuga"));
        // rodada 03: estudos (R02 = snapshot congelado da rodada 02, fora de assets/)
        String r02 = "../output/visual-review/round-03/snapshot-r02/";
        LINEUPS.put("r3-carapaca", List.of("studies/tortuga_s1", "studies/tortuga_s2", "studies/tortuga_s3"));
        LINEUPS.put("r3-carvalho", List.of("studies/oak_o1", "studies/oak_o2", "studies/oak_o3"));
        LINEUPS.put("r3-cabelo", List.of("characters/r3/adv_r02", "characters/r3/adv_h1", "characters/r3/adv_h2", "characters/r3/adv_h3"));
        LINEUPS.put("r3-capuz", List.of("characters/r3/rng_c0", "characters/r3/rng_c1", "characters/r3/rng_c2", "characters/r3/rng_c3"));
        LINEUPS.put("r3-chapeu", List.of("characters/r3/mage_m0", "characters/r3/mage_m1", "characters/r3/mage_m2"));
        LINEUPS.put("r3-cajado", List.of("characters/r3/mage_staff_r02", "characters/r3/mage_staff_r3"));
        LINEUPS.put("r3-rosto", List.of("characters/r3/adv_face0", "characters/r3/adv_face1"));
        LINEUPS.put("herois-r02-r03", List.of(r02 + "characters/e/adventurer", "characters/e/adventurer",
                r02 + "characters/e/ranger", "characters/e/ranger", r02 + "characters/e/mage", "characters/e/mage"));
        LINEUPS.put("criaturas-r02-r03", List.of(r02 + "creatures/e/pompom", "creatures/e/pompom", r02 + "creatures/e/lumi",
                "creatures/e/lumi", r02 + "creatures/e/fungo", "creatures/e/fungo"));
        LINEUPS.put("criaturas-b-e", List.of("creatures/b/pompom", "creatures/e/pompom", "creatures/b/fungo", "creatures/e/fungo",
                "creatures/b/lumi", "creatures/e/lumi"));
    }

    /** Tudo o que define uma imagem; capturas são reproduzíveis a partir deste estado. */
    public static final class State {
        public int style = 0;
        public Mode mode = Mode.CHARACTER;
        public int character = 0;
        public Animator.Clip clip = Animator.Clip.IDLE;
        public float time = 0f;
        public boolean turntable = false, neutral = false, hud = true, normalized = false;
        public float modelYaw = 0f;
        public final Renderer.Toggles toggles = new Renderer.Toggles();
        public final Camera cam = new Camera();
        public boolean camSet = false;
        /** Enquadra automaticamente o modelo nos modos de inspeção (desliga quando o usuário mexe na câmera). */
        public boolean camAuto = true;
        /** Posição/rumo do herói na cena; NaN = usar o ponto inicial do layout. */
        public float heroX = Float.NaN, heroZ = Float.NaN, heroYaw = Float.NaN;
        public float camLift = 0.75f;
        /** Instante em que o clipe atual começou (para clipes de disparo único). */
        public float clipStart = 0f;
        // --- rodada 02
        /** Preset de luz da direção E (índice em StyleProfile.E_PRESETS). */
        public int preset = 0;
        /** Contorno: -1 = padrão do estilo; 0 desligado, 1 discreto, 2 intermediário, 3 forte. */
        public int outlineLevel = -1;
        /** Névoa: 0 desligada, 1 leve, 2 padrão do estilo, 3 densa. */
        public int fogLevel = 2;
        /** Expressão forçada (null = automática pelo clipe; "neutral" força a neutra). */
        public String expr = null;
        /** Conjunto do modo LINHA. */
        public String lineup = "variantes";
        public Quality quality = Quality.PADRAO;
        public boolean metrics = false;
        /** Rig específico (ex.: "characters/variants/v2") no lugar do personagem do estilo. */
        public String heroDir = null;
        /** Rodada 07: aparência composta em tempo de execução (modo PERSONALIZACAO, cena e capturas). */
        public Appearance appearance = null;
        public int customRow = 0;
        public String customMsg = null;
        public float customMsgUntil = 0f;
        /** Tratamento de relevo da E (0–3; ver World.quantize). */
        public int terrain = World.E_TERRAIN;
        /** SSAA das capturas offscreen (2 = padrão de revisão; 1 = como o jogo em qualidade PADRÃO). */
        public float captureSsaa = 2f;
        /** Desliga o FXAA mesmo na qualidade PADRÃO (reproduz a configuração da rodada 02 no bench). */
        public boolean noFxaa = false;
        /** Cena com os três heróis da E juntos (o herói atual + os outros dois ao lado). */
        public boolean party = false;
        /** Rodada 04: golpe do perfil em laço ("attack" = o padrão de cada perfil), roteiro e velocidade. */
        public String act, seq;
        /** Bancada de terreno no modo personagem (flat, ramp, steps, big); null = pedestal normal. */
        public String lab;
        public boolean terrainFar = true;
        public float timeScale = 1f, actHold = 0.45f;
        /** Rodada 07.2: deslocamento lateral do alvo da câmera na CENA (u, no eixo direito da câmera) — põe o
         *  herói no centro da área livre entre os painéis do criador. 0 = como sempre. */
        public float camSide = 0f;
    }

    private static final String[] STUDY = {"p22", "p25", "p30", "p35", "p40"};

    private final Window window;
    private final Renderer renderer = new Renderer();
    private final Hud hud = new Hud();
    private final Assets assets;
    private final Map<String, Actor> actors = new HashMap<>();
    private final Map<String, Mesh> pedestals = new HashMap<>();
    private final Map<String, World> worlds = new HashMap<>();
    private Mesh ruler;
    private Framebuffer captureFbo;
    private double fpsSmoothed = 0, msSmoothed = 0;
    private Animator.Clip oneShot;
    private float oneShotStart, oneShotEnd;

    public App(Window window, Path assetRoot) {
        this.window = window;
        this.assets = new Assets(assetRoot);
    }

    // ------------------------------------------------------------------ conteúdo
    private Composer composer;
    private final Map<String, Appearance> customs = new HashMap<>();

    /** Compositor de personagens (rodada 07), criado na primeira aparência pedida. */
    public Composer composer() {
        if (composer == null) composer = new Composer(assets.root(), 12);
        return composer;
    }

    // ------------------------------------------------------------------ personalização (rodada 07)

    static final String[] CUSTOM_ROWS = {"PRESET", "ROSTO", "CABELO", "COR DO CABELO", "PELE", "IRIS", "COBERTURA", "EXPRESSAO", "CLIPE"};

    /** Valores possíveis de uma linha do painel (null = "do preset"). */
    private List<String> customValues(int row) {
        ComponentCatalog c = composer().catalog;
        List<String> v = new ArrayList<>();
        switch (row) {
            case 0 -> v.addAll(c.presets.keySet());
            case 1 -> { v.add(null); v.addAll(c.ids("rosto")); }
            case 2 -> { v.add(null); v.addAll(c.ids("cabelo")); }
            case 3, 4, 5 -> {
                v.add(null);
                for (String[] col : c.colors.get(row == 3 ? "cabelo" : row == 4 ? "pele" : "iris")) v.add(col[1]);
            }
            case 6 -> { v.add(null); v.add(Appearance.NO_COVER); v.addAll(c.ids("cabeca")); }
            default -> { }
        }
        return v;
    }

    private String customGet(Appearance a, int row) {
        return switch (row) {
            case 0 -> a.preset;
            case 1 -> a.face;
            case 2 -> a.hair;
            case 3 -> a.hairColor;
            case 4 -> a.skin;
            case 5 -> a.iris;
            case 6 -> a.cover;
            default -> null;
        };
    }

    private void customSet(Appearance a, int row, String v) {
        switch (row) {
            case 0 -> { a.preset = v; a.reset(); }
            case 1 -> a.face = v;
            case 2 -> a.hair = v;
            case 3 -> a.hairColor = v;
            case 4 -> a.skin = v;
            case 5 -> a.iris = v;
            case 6 -> a.cover = v;
            default -> { }
        }
    }

    /** Muda o valor da linha; combinações incompatíveis são PULADAS e informadas (nunca silenciadas). */
    void customStep(State st, int delta) {
        Appearance a = st.appearance;
        if (st.customRow == 7) {
            String[] ex = {null, "neutral", "joy", "determined", "surprise"};
            int i = java.util.Arrays.asList(ex).indexOf(st.expr);
            st.expr = ex[Math.floorMod(i + delta, ex.length)];
            return;
        }
        if (st.customRow == 8) {
            Animator.Clip[] cl = {Animator.Clip.IDLE, Animator.Clip.WALK, Animator.Clip.RUN, Animator.Clip.ATTACK, Animator.Clip.EMOTE, Animator.Clip.HIT};
            int i = java.util.Arrays.asList(cl).indexOf(st.clip);
            st.clip = cl[Math.floorMod(i + delta, cl.length)];
            st.clipStart = st.time;
            return;
        }
        List<String> vals = customValues(st.customRow);
        int i = vals.indexOf(customGet(a, st.customRow));
        StringBuilder skipped = new StringBuilder();
        for (int n = 1; n <= vals.size(); n++) {
            Appearance t = a.copy();
            String v = vals.get(Math.floorMod(i + delta * n, vals.size()));
            customSet(t, st.customRow, v);
            try {
                t.validate(composer().catalog);
                st.appearance = t;
                customMsg(st, skipped.length() > 0 ? "PULEI (INCOMPATIVEL): " + skipped : null);
                return;
            } catch (Appearance.InvalidAppearance e) {
                skipped.append(ascii(e.getMessage())).append("  ");
            }
        }
        customMsg(st, "NENHUMA OPCAO COMPATIVEL: " + skipped);
    }

    /**
     * Rodada 07: roteiro do modo PERSONALIZACAO pelas MESMAS funções das teclas (setas, BACKSPACE, F2, F3),
     * capturando a tela com o painel a cada passo — prova de interface sem simular imagem.
     */
    public void customDemo(Path out) throws IOException {
        Files.createDirectories(out);
        State st = new State();
        st.style = 4;
        st.mode = Mode.CUSTOMIZE;
        st.appearance = Appearance.preset("aventureiro");
        st.quality = Quality.PADRAO;
        st.time = 1.3f;
        defaultCamera(st);
        st.cam.target.y = 0.75f;
        st.cam.distance = 2.6f;
        st.cam.yaw = 0.35f;
        st.cam.update(16f / 9f);
        st.camSet = true;
        Path bad = Path.of("output/aparencias/zz-arquivo-invalido.json");
        Object[][] steps = {
                {"preset-aventureiro", null},
                {"cabelo-curto", (Runnable) () -> { st.customRow = 2; customStep(st, 2); }},
                {"cor-castanho", (Runnable) () -> { st.customRow = 3; customStep(st, 4); }},
                {"pele-morena", (Runnable) () -> { st.customRow = 4; customStep(st, 4); }},
                {"iris-ambar", (Runnable) () -> { st.customRow = 5; customStep(st, 4); }},
                {"rosto-da-maga", (Runnable) () -> { st.customRow = 1; customStep(st, 3); }},
                {"cobertura-capuz-c3", (Runnable) () -> { st.customRow = 6; customStep(st, 2); }},
                {"expressao-determinada", (Runnable) () -> { st.customRow = 7; customStep(st, 3); }},
                {"salvar-F2", (Runnable) () -> customSave(st)},
                {"restaurar-BACKSPACE", (Runnable) () -> { st.appearance.reset(); st.expr = null; customMsg(st, "PRESET ORIGINAL RESTAURADO: " + st.appearance.preset); }},
                {"carregar-F3", (Runnable) () -> customLoad(st)},
                {"cobertura-chapeu-maga", (Runnable) () -> { st.customRow = 6; customStep(st, 1); }},
                {"preset-exploradora", (Runnable) () -> { st.customRow = 0; customStep(st, 1); }},
                {"arquivo-invalido-F3", (Runnable) () -> {
                    try {
                        Files.writeString(bad, Appearance.preset("maga").toJson().replace("\"maga\"", "\"paladino\""));
                        bad.toFile().setLastModified(System.currentTimeMillis() + 60_000);
                    } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                    customLoad(st);
                }},
        };
        int i = 1;
        for (Object[] step : steps) {
            if (step[1] != null) ((Runnable) step[1]).run();
            Screenshot.save(capture(st, 1280, 720), out.resolve(String.format("%02d-%s.png", i++, step[0])));
            System.out.println("custom-demo: " + step[0] + (st.customMsg != null ? "  [" + st.customMsg + "]" : ""));
            st.customMsg = null;
        }
        Files.deleteIfExists(bad);
    }

    /** Rodada 07: custo de compor, malhar e enviar; cache; memória; vazamento em trocas repetidas. */
    public void customBench(Path out) throws IOException {
        Composer c = new Composer(assets.root(), 12);
        StringBuilder md = new StringBuilder("# Benchmark da personalização (rodada 07)\n\n");
        md.append("| Aparência | compor ms | malha ms | GPU ms | faces | MB de vértices |\n|---|---|---|---|---|---|\n");
        List<Appearance> all = new ArrayList<>();
        for (String p : c.catalog.presets.keySet()) all.add(Appearance.preset(p));
        String[] covers = {Appearance.NO_COVER, "chapeu.maga", "capuz.c3"};
        int n = 0;
        for (String p : c.catalog.presets.keySet())
            for (String h : c.catalog.ids("cabelo"))
                for (String cv : covers) {
                    Appearance a = Appearance.preset(p);
                    a.hair = h;
                    a.cover = cv;
                    a.hairColor = c.catalog.colors.get("cabelo").get(n % 10)[1];
                    a.skin = c.catalog.colors.get("pele").get(n % 5)[1];
                    n++;
                    all.add(a);
                }
        // aquecimento (JIT) fora da medida
        for (int k = 0; k < 3; k++) Composer.release(c.build(c.compose(all.get(k)), true));
        double[] tc = new double[all.size()], tm = new double[all.size()], tu = new double[all.size()];
        for (int k = 0; k < all.size(); k++) {
            long b0 = Mesh.liveBytes;
            CharacterModel m = c.build(c.compose(all.get(k)), true);
            tc[k] = c.lastComposeMs; tm[k] = c.lastMeshMs; tu[k] = c.lastUploadMs;
            if (k < 6 || k % 9 == 0)
                md.append(String.format(java.util.Locale.ROOT, "| %s | %.2f | %.2f | %.2f | %d | %.2f |%n", all.get(k).signature(), tc[k], tm[k], tu[k],
                        c.lastFaces, (Mesh.liveBytes - b0) / 1048576.0));
            Composer.release(m);
        }
        md.append(String.format(java.util.Locale.ROOT, "%n%d aparências: compor média %.2f ms (máx %.2f), malha média %.2f ms (máx %.2f), GPU média %.2f ms (máx %.2f)%n",
                all.size(), avg(tc), max(tc), avg(tm), max(tm), avg(tu), max(tu)));
        // trocas repetidas com cache LRU de 12: memória de GPU limitada, sem vazamento
        long meshes0 = Mesh.liveMeshes, bytes0 = Mesh.liveBytes;
        java.util.Random rnd = new java.util.Random(7);
        long t0 = System.nanoTime();
        int swaps = 400;
        for (int k = 0; k < swaps; k++) c.model(all.get(rnd.nextInt(all.size())));
        double swapMs = (System.nanoTime() - t0) / 1e6 / swaps;
        long meshesPeak = Mesh.liveMeshes - meshes0, bytesPeak = Mesh.liveBytes - bytes0;
        c.clear();
        md.append(String.format(java.util.Locale.ROOT, "%n%d trocas aleatórias entre %d aparências (cache 12): %.2f ms por troca em média (%d acertos, %d composições); "
                        + "malhas vivas no fim: %d (%.1f MB); depois de limpar o cache: %d malhas e %d bytes a mais que no início%n",
                swaps, all.size(), swapMs, c.cacheHits, c.cacheMisses, meshesPeak, bytesPeak / 1048576.0, Mesh.liveMeshes - meshes0, Mesh.liveBytes - bytes0));
        // restaurar preset: composição nova × cache
        Appearance pre = Appearance.preset("aventureiro");
        long r0 = System.nanoTime();
        c.model(pre);
        double cold = (System.nanoTime() - r0) / 1e6;
        r0 = System.nanoTime();
        c.model(pre);
        double warm = (System.nanoTime() - r0) / 1e6;
        CharacterModel canon = assets.rig("characters/e/adventurer");
        md.append(String.format(java.util.Locale.ROOT, "%nRestaurar o preset: %.2f ms compondo do zero, %.4f ms do cache. Partes (= draws por personagem): composto %d × canônico %d.%n",
                cold, warm, c.model(pre).parts.size(), canon.parts.size()));
        c.clear();
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString());
        System.out.print(md);
    }

    private static double avg(double[] a) { double s = 0; for (double v : a) s += v; return s / a.length; }
    private static double max(double[] a) { double m = 0; for (double v : a) m = Math.max(m, v); return m; }

    /** Carrega uma aparência salva (capturas: chave aparencia=arquivo). */
    public Appearance loadAppearance(String file) {
        try {
            return Appearance.load(Path.of(file), composer().catalog);
        } catch (IOException e) {
            throw new IllegalArgumentException("aparência ilegível: " + file, e);
        }
    }

    void customMsg(State st, String m) {
        st.customMsg = m;
        st.customMsgUntil = st.time + 4f;
    }

    /** Grava em output/aparencias/ sem sobrescrever (número novo a cada gravação). */
    void customSave(State st) {
        try {
            Path dir = Path.of("output/aparencias");
            int n = 1;
            Path f;
            do f = dir.resolve(String.format("%s-%03d.json", st.appearance.preset, n++)); while (Files.exists(f));
            st.appearance.save(f, false);
            customMsg(st, "SALVO EM " + f);
        } catch (IOException e) {
            customMsg(st, "ERRO AO SALVAR: " + ascii(e.getMessage()));
        }
    }

    /** Carrega o arquivo mais recente de output/aparencias/ (validado contra o catálogo). */
    void customLoad(State st) {
        try (var fs = Files.list(Path.of("output/aparencias"))) {
            Path f = fs.filter(p -> p.toString().endsWith(".json")).max(java.util.Comparator.comparing(p -> p.toFile().lastModified())).orElse(null);
            if (f == null) { customMsg(st, "NENHUMA APARENCIA SALVA"); return; }
            st.appearance = Appearance.load(f, composer().catalog);
            customMsg(st, "CARREGADO " + f);
        } catch (IOException | Appearance.InvalidAppearance e) {
            customMsg(st, "ERRO AO CARREGAR: " + ascii(String.valueOf(e.getMessage())));
        }
    }

    private String customLabel(int row, String v) {
        if (row == 0) return v;
        if (v == null) return "(DO PRESET)";
        if (v.startsWith("#")) {
            for (var e : composer().catalog.colors.entrySet())
                for (String[] c : e.getValue()) if (c[1].equals(v)) return c[0] + " " + v;
            return v;
        }
        return v;
    }

    private void drawCustomPanel(State st, int w, int h, float s, float pad, float line, int white, int soft, int accent) {
        Appearance a = st.appearance;
        if (a == null) return;
        float y = pad + line * 2.2f, cs = s * 0.7f;
        hud.text(pad, y, cs, "SETAS: LINHA / VALOR   BACKSPACE: PRESET ORIGINAL   F2 SALVAR   F3 CARREGAR", soft);
        y += line * 1.3f;
        for (int r = 0; r < CUSTOM_ROWS.length; r++) {
            String v = r == 7 ? (st.expr == null ? "(AUTO)" : st.expr) : r == 8 ? String.valueOf(st.clip) : customLabel(r, customGet(a, r));
            String txt = (r == st.customRow ? "> " : "  ") + CUSTOM_ROWS[r] + ": " + ascii(String.valueOf(v));
            hud.text(pad, y, cs, txt, r == st.customRow ? accent : white);
            y += line * 0.95f;
        }
        CharacterModel m = rig(currentDir(st, "e"));
        Composer c = composer();
        y += line * 0.4f;
        hud.text(pad, y, s * 0.55f, String.format("COMPOR %.1f MS  MALHA %.1f MS  GPU %.1f MS  CACHE %d (ACERTOS %d)",
                c.lastComposeMs, c.lastMeshMs, c.lastUploadMs, c.cachedModels(), c.cacheHits), soft);
        y += line * 0.8f;
        hud.text(pad, y, s * 0.55f, (m.meta.has("headsTall") ? m.meta.get("headsTall").getAsString() + " CABECAS  " : "") +
                String.format("%.2f U", m.height), soft);
        if (st.customMsg != null && st.time < st.customMsgUntil)
            hud.text(pad, h - pad - line * 4.2f, s * 0.6f, ascii(st.customMsg), accent);
    }

    /** Rig de um diretório de assets ou de uma aparência composta ("custom:<assinatura>"). */
    /** Rodada 07.2: modelos já compostos e enviados pelo editor (a cena nunca compõe de forma síncrona). */
    final Map<String, CharacterModel> injected = new HashMap<>();

    CharacterModel rig(String dir) {
        CharacterModel inj = injected.get(dir);
        if (inj != null) return inj;
        if (dir.startsWith("custom:")) return composer().model(customs.get(dir));
        return assets.rig(dir);
    }

    private Actor actor(String dir, StyleProfile style) {
        String key = dir + "@" + style.key;
        Actor a = actors.get(key);
        CharacterModel m = rig(dir);
        if (a == null || a.animator().model() != m) {                // aparência recomposta: ator novo
            a = new Actor(m, style.animExaggeration);
            actors.put(key, a);
        }
        return a;
    }

    /** Personagens + criaturas inspecionáveis do estilo. */
    private List<String> roster(String styleKey) {
        List<String> out = new ArrayList<>();
        for (String c : assets.characters(styleKey)) out.add("characters/" + styleKey + "/" + c);
        for (String c : assets.subdirs("creatures/" + styleKey)) out.add("creatures/" + styleKey + "/" + c);
        return out;
    }

    private String currentDir(State st, String styleKey) {
        if (st.appearance != null && (st.mode == Mode.CUSTOMIZE || st.mode == Mode.SCENE || st.mode == Mode.CHARACTER)) {
            String key = "custom:" + st.appearance.signature();
            customs.putIfAbsent(key, st.appearance.copy());
            return key;
        }
        if (st.heroDir != null && assets.exists(st.heroDir + "/rig.json")) return st.heroDir;
        List<String> r = roster(styleKey);
        if (r.isEmpty()) return null;
        return r.get(Math.floorMod(st.character, r.size()));
    }

    private List<String> lineup(State st) {
        List<String> out = new ArrayList<>();
        for (String d : LINEUPS.getOrDefault(st.lineup, List.of())) if (assets.exists(d + "/rig.json")) out.add(d);
        return out;
    }

    StyleProfile profile(State st, int styleIdx) {
        StyleProfile p = StyleProfile.of(styleIdx, st.preset);
        if (st.neutral) p = StyleProfile.neutral(p);
        if (st.outlineLevel >= 0) p.withOutline(st.outlineLevel);
        p.withFog(st.fogLevel);
        switch (st.quality) {
            case BAIXA -> { p.shadowTaps = 1; p.bloom = 0f; }
            case ALTA -> p.shadowTaps = 2;          // 5x5
            default -> { }
        }
        if (st.toggles.silhouette) {
            p.zenith = StyleProfile.lin("#efece6", 1f);
            p.horizon = StyleProfile.lin("#efece6", 1f);
            p.sunGlow = new org.joml.Vector3f();
            p.fogDensity = 0f;
        }
        return p;
    }

    private Mesh pedestal(String key, float radius) {
        float r = Math.round(radius * 4f) / 4f;
        return pedestals.computeIfAbsent(key + "@" + r, k -> World.pedestal(key, r));
    }

    private int curTerrain = World.E_TERRAIN;

    private World world(StyleProfile style) {
        int tm = curTerrain;
        return worlds.computeIfAbsent(style.key + "#" + tm + "#" + World.layout, k -> new World(assets, style, tm));
    }

    /** Motion do herói no tempo atual: roteiro (seq), golpe em laço (act) ou só o clipe. */
    static Animator.Motion motionFor(State st, Actor a) {
        Animator.Motion mo = new Animator.Motion();
        float tt = (st.time - st.clipStart) * st.timeScale;
        Animator an = a.animator();
        if (st.seq != null && an.prof != null) {
            try {
                return Sequencer.of(st.seq, an).eval(tt, mo);
            } catch (IllegalArgumentException e) {
                return mo.set(st.clip, tt);                     // este herói não tem o golpe: só o clipe
            }
        }
        if (st.act != null && an.prof != null) {
            String mv = st.act.equals("attack") ? an.prof.attack : st.act;
            float d = an.moveDuration(mv);
            if (d > 0) {
                mo.set(st.clip == Animator.Clip.ATTACK ? Animator.Clip.IDLE : st.clip, tt);
                mo.move = mv;
                mo.moveT = Math.min(d, tt % (d + st.actHold));
                mo.weight = 1f;
                return mo;
            }
        }
        return mo.set(st.clip, tt);
    }

    private void initHero(State st, World w) {
        if (Float.isNaN(st.heroX)) st.heroX = w.heroX;
        if (Float.isNaN(st.heroZ)) st.heroZ = w.heroZ;
        if (Float.isNaN(st.heroYaw)) st.heroYaw = w.heroYaw;
    }

    public void prepareCamera(State st) { defaultCamera(st); }

    private void defaultCamera(State st) {
        Camera c = st.cam;
        switch (st.mode) {
            case CHARACTER, SIDE_BY_SIDE, CUSTOMIZE -> {
                c.target.set(0, 0.62f, 0);
                c.yaw = 0.45f;
                c.pitch = 0.22f;
                c.distance = st.mode == Mode.SIDE_BY_SIDE ? 4.6f : 3.4f;
                c.fovDeg = 32f;
            }
            case PROPORTIONS -> {
                c.target.set(0, 0.85f, 0);
                c.yaw = 0f;
                c.pitch = 0.06f;
                c.distance = 13.5f;
                c.fovDeg = 20f;
            }
            case LINEUP -> {
                c.target.set(0, 0.6f, 0);
                c.yaw = 0f;
                c.pitch = 0.10f;
                c.distance = 8f;
                c.fovDeg = 16f;          // tele: pouca perspectiva, mesma escala aparente para todos
            }
            case SCENE -> {
                c.target.set(0, 0.7f, 0);
                c.yaw = 0.5f;
                c.pitch = 0.3f;
                c.distance = 7f;
                c.fovDeg = 45f;
            }
        }
        st.camSet = true;
    }

    /** Posições X dos modelos do modo LINHA (centradas em 0) e largura total. */
    private float[] lineupLayout(List<String> dirs) {
        float gap = 0.38f;
        float[] xs = new float[dirs.size() + 1];
        float acc = 0;
        for (int i = 0; i < dirs.size(); i++) {
            CharacterModel m = rig(dirs.get(i));
            float w = Math.max(0.62f, Math.min(m.width, 2.6f));
            xs[i] = acc + w / 2;
            acc += w + gap;
        }
        float total = acc - gap;
        for (int i = 0; i < dirs.size(); i++) xs[i] -= total / 2;
        xs[dirs.size()] = total;
        return xs;
    }

    private Renderer.Frame buildFrame(State st, int styleIdx, float blend) {
        StyleProfile style = profile(st, styleIdx);
        curTerrain = st.terrain;
        Renderer.Frame f = new Renderer.Frame();
        f.camera = st.cam;
        f.style = style;
        f.time = st.time;
        f.toggles = new Renderer.Toggles();
        f.toggles.shadows = st.toggles.shadows;
        f.toggles.ao = st.toggles.ao;
        f.toggles.fog = st.toggles.fog;
        f.toggles.post = st.toggles.post;
        f.toggles.textures = st.toggles.textures;
        f.toggles.silhouette = st.toggles.silhouette;
        f.toggles.fxaa = st.quality.fxaa() && !st.noFxaa;
        f.toggles.terrainFar = st.terrainFar;
        if (st.toggles.silhouette && st.mode != Mode.SCENE) f.toggles.shadows = false;
        f.toggles.studio = st.mode != Mode.SCENE;

        float yaw = st.modelYaw + (st.turntable ? st.time * 0.8f : 0f);
        switch (st.mode) {
            case SCENE -> {
                World w = world(style);
                initHero(st, w);
                w.submit(f, st.time);
                float gy = w.groundSmooth(st.heroX, st.heroZ);
                String dir = currentDir(st, style.key);
                if (dir != null) {
                    Actor a = actor(dir, style);
                    float hx = st.heroX, hz = st.heroZ;
                    if (st.seq != null) {                         // o roteiro anda: avanço pela passada
                        float dd = Sequencer.of(st.seq, a.animator()).distance((st.time - st.clipStart) * st.timeScale);
                        hx += (float) Math.sin(st.heroYaw) * dd;
                        hz += (float) Math.cos(st.heroYaw) * dd;
                        gy = w.groundSmooth(hx, hz);
                    }
                    a.submit(f, new Matrix4f().translate(hx, gy, hz).rotateY(st.heroYaw), motionFor(st, a), blend, st.expr,
                            w::groundAt);
                }
                if (st.party && style.key.equals("e")) {
                    // grupo: os outros dois heróis ao lado, cada um no seu clipe e com os pés no terreno
                    String[][] mates = {{"characters/e/ranger", "-0.95", "-0.55", "IDLE"}, {"characters/e/mage", "0.95", "-0.45", "IDLE"},
                            {"characters/e/adventurer", "-0.95", "-0.55", "IDLE"}};
                    int k = 0;
                    for (String[] mt : mates) {
                        if (k == 2 || mt[0].equals(dir)) continue;
                        float ox = Float.parseFloat(mt[1]), oz = Float.parseFloat(mt[2]);
                        float cs = (float) Math.cos(st.heroYaw), sn = (float) Math.sin(st.heroYaw);
                        float mx = st.heroX + ox * cs + oz * sn, mz = st.heroZ - ox * sn + oz * cs;
                        Actor ma = actor(mt[0], style);
                        ma.submit(f, new Matrix4f().translate(mx, w.groundSmooth(mx, mz), mz).rotateY(st.heroYaw + (k == 0 ? 0.35f : -0.35f)),
                                st.clip == Animator.Clip.WALK || st.clip == Animator.Clip.RUN ? st.clip : Animator.Clip.IDLE,
                                st.time + 0.7f * (k + 1), blend, null, w::groundSmooth);
                        k++;
                    }
                }
                float cx = st.heroX, cz = st.heroZ;
                if (st.seq != null && dir != null) {          // câmera acompanha o herói que anda pelo roteiro
                    float dd = Sequencer.of(st.seq, actor(dir, style).animator()).distance((st.time - st.clipStart) * st.timeScale);
                    cx += (float) Math.sin(st.heroYaw) * dd;
                    cz += (float) Math.cos(st.heroYaw) * dd;
                    gy = w.groundSmooth(cx, cz);
                }
                st.cam.target.set(cx, gy + st.camLift, cz);
                if (st.camSide != 0f)                              // rodada 07.2: pan lateral (eixo direito da câmera)
                    st.cam.target.add((float) Math.cos(st.cam.yaw) * st.camSide, 0, -(float) Math.sin(st.cam.yaw) * st.camSide);
                st.cam.update(st.cam.aspect);
                f.shadowFocus.set(cx, gy, cz);
                f.shadowRadius = Math.clamp(st.cam.distance * 1.5f, 6f, 26f);
            }
            case CHARACTER, SIDE_BY_SIDE, CUSTOMIZE -> {
                String dir = currentDir(st, style.key);
                float extent = 1f;
                if (dir != null) {
                    CharacterModel cm = rig(dir);
                    extent = Math.max(cm.height, Math.max(cm.width, cm.depth));
                }
                // silhueta: sem chão (fundo uniforme, pés visíveis) — a rodada 02 pintava o chão de preto
                GroundLab lab = st.lab != null ? new GroundLab(st.lab) : null;
                if (lab == null && !st.toggles.silhouette) f.add(pedestal(style.key, Math.max(1.25f, extent * 0.8f)), new Matrix4f());
                if (dir != null) {
                    if (st.camAuto && (st.mode == Mode.CHARACTER || st.mode == Mode.CUSTOMIZE)) {
                        st.cam.target.y = rig(dir).height * 0.55f;
                        st.cam.distance = 2.0f + extent * 1.5f;
                        st.cam.update(st.cam.aspect);
                    }
                    Actor a = actor(dir, style);
                    if (lab != null) {
                        // o herói anda no lugar; o chão desliza para trás na velocidade da passada
                        float tt = (st.time - st.clipStart) * st.timeScale;
                        Animator.Clip c = st.clip;
                        lab.scroll = st.seq != null ? Sequencer.of(st.seq, a.animator()).distance(tt)
                                : c == Animator.Clip.WALK ? Animator.WALK_SPEED * tt : c == Animator.Clip.RUN ? Animator.RUN_SPEED * tt : 0f;
                        lab.base = lab.smooth(0, 0);
                        lab.submit(f);
                        a.submit(f, new Matrix4f(), motionFor(st, a), blend, st.expr, lab::height);
                    } else
                    a.submit(f, new Matrix4f().rotateY(yaw), motionFor(st, a), blend, st.expr, null);
                }
                f.shadowFocus.set(0, 0.5f, 0);
                f.shadowRadius = Math.max(2.2f, extent * 1.6f);
            }
            case LINEUP -> {
                List<String> dirs = lineup(st);
                float[] xs = lineupLayout(dirs);
                float total = xs[dirs.size()], maxH = 0.5f;
                for (String d : dirs) maxH = Math.max(maxH, rig(d).height);
                if (!st.toggles.silhouette) f.add(pedestal(style.key, Math.max(1.6f, total / 2 + 0.9f)), new Matrix4f());
                if (st.camAuto) {
                    float tv = (float) Math.tan(Math.toRadians(st.cam.fovDeg) / 2);
                    float th = tv * st.cam.aspect;
                    st.cam.target.set(0, maxH * 0.5f, 0);
                    st.cam.distance = Math.max((total / 2 + 0.45f) / th, (maxH / 2 + 0.25f) / tv) + 0.6f;
                    st.cam.update(st.cam.aspect);
                }
                for (int i = 0; i < dirs.size(); i++) {
                    Actor a = actor(dirs.get(i), style);
                    a.submit(f, new Matrix4f().translate(xs[i], 0, 0).rotateY(yaw), motionFor(st, a), blend, st.expr, null);
                }
                f.shadowFocus.set(0, 0.5f, 0);
                f.shadowRadius = Math.max(2.5f, total * 0.62f);
            }
            case PROPORTIONS -> {
                if (ruler == null) ruler = buildRuler();
                f.add(ruler, new Matrix4f());
                for (int i = 0; i < STUDY.length; i++) {
                    String dir = "characters/study/" + STUDY[i] + (st.normalized ? "n" : "");
                    if (!assets.exists(dir)) continue;
                    Actor a = actor(dir, style);
                    a.submit(f, new Matrix4f().translate((i - 2) * 1.55f, 0, 0).rotateY(yaw), st.clip, st.time - st.clipStart, blend);
                }
                f.shadowFocus.set(0, 0.8f, 0);
                f.shadowRadius = 5f;
            }
        }
        return f;
    }

    /** Parede-régua: faixas horizontais de 1 "cabeça" (10 voxels de 1/24 u) + piso neutro. */
    private Mesh buildRuler() {
        final float vs = 1f / 24f;
        final int head = 10, w = 216, hgt = 46, d = 60;
        return vx.engine.VoxelMesher.build(new vx.engine.VoxelSource() {
            public int sizeX() { return w; }
            public int sizeY() { return hgt + 1; }
            public int sizeZ() { return d; }
            public int get(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= w || y > hgt || z >= d) return 0;
                if (y == 0) return (1 << 24) | (((x / 12 + z / 12) & 1) == 0 ? 0xb9bcc2 : 0xb1b4ba);
                if (z == 0) {
                    int band = (y - 1) / head;
                    boolean line = (y - 1) % head == 0;
                    return (1 << 24) | (line ? 0x6f7480 : ((band & 1) == 0 ? 0xc9ccd2 : 0xbcbfc6));
                }
                return 0;
            }
        }, vs, -w / 2f, -1, -22);
    }

    // ------------------------------------------------------------------ renderização
    private void renderState(State st, Framebuffer target, int w, int h, float ssaa, float blend) {
        if (!st.camSet) defaultCamera(st);
        if (st.mode == Mode.SIDE_BY_SIDE) {
            int n = StyleProfile.COUNT, cw = w / n;
            st.cam.update((float) cw / h);
            for (int i = 0; i < n; i++)
                renderer.render(buildFrame(st, i, blend), target, i * cw, 0, cw, h, ssaa);
        } else {
            st.cam.update((float) w / h);
            renderer.render(buildFrame(st, st.style, blend), target, w, h, ssaa);
        }
        if (target != null) target.bind();
        else Framebuffer.bindDefault(w, h);
        if (st.hud) drawHud(st, w, h, target == null);
        hud.flush(w, h);
        if (overlay != null) overlay.draw(w, h);
    }

    // ------------------------------------------------------------------ rodada 07.2: apoio ao criador

    /** Desenho 2D por cima da cena (interface do criador), no alvo já ligado, em pixels do alvo. */
    public interface Overlay { void draw(int w, int h); }
    public Overlay overlay;
    private Renderer thumbRenderer;

    public void renderFrame(State st, Framebuffer target, int w, int h, float ssaa, float blend) {
        renderState(st, target, w, h, ssaa, blend);
    }

    /** Tira da cena um modelo composto que saiu do cache (as malhas já foram fechadas). */
    public void forget(String dir) {
        injected.remove(dir);
        customs.remove(dir);
        actors.keySet().removeIf(k -> k.startsWith(dir + "@"));
    }

    /**
     * Miniatura REAL: o modelo composto, parado, em estúdio (sem pedestal), num FBO próprio — renderizador
     * separado para não realocar os alvos da cena principal a cada miniatura.
     */
    /** Enquadramento: "corpo" (inteiro, 3/4), "cabeca" (close, 3/4), "cabelo" (cabeça inteira com o alto do
     *  penteado) ou "cobertura" (cabeça com folga em cima, lateral). */
    public void renderThumb(CharacterModel m, Framebuffer target, String framing) {
        boolean head = !framing.equals("corpo"), cover = framing.equals("cobertura"), hair = framing.equals("cabelo");
        boolean eyes = framing.equals("olhos");                         // rodada 09: close na linha dos olhos
        boolean feetF = framing.equals("pes");                          // rodada 09: botas
        float yaw = cover ? 1.05f : 0.38f;
        if (thumbRenderer == null) thumbRenderer = new Renderer();
        StyleProfile style = StyleProfile.of(4, 0);
        style.zenith = StyleProfile.lin("#e9d7b4", 1f);            // fundo de pergaminho (combina com o painel)
        style.horizon = StyleProfile.lin("#f7ecd6", 1f);
        Renderer.Frame f = new Renderer.Frame();
        Camera c = new Camera();
        c.fovDeg = 22f;
        c.yaw = eyes ? 0.12f : yaw;
        c.pitch = head ? 0.06f : 0.10f;
        if (feetF) {
            c.target.set(0, m.height * 0.13f, 0);
            c.distance = 1.35f;
            c.pitch = 0.18f;
        } else if (eyes) {
            c.target.set(0, m.height * 0.67f, 0);
            c.distance = 1.15f;
        } else if (head) {
            c.target.set(0, m.height * (cover ? 0.86f : hair ? 0.82f : 0.80f), 0);
            c.distance = cover ? 1.9f : hair ? 1.8f : 1.55f;
        } else {
            c.target.set(0, m.height * 0.5f, 0);
            c.distance = m.height * 2.55f + 0.7f;
        }
        c.update((float) target.width / target.height);
        f.camera = c;
        f.style = style;
        f.time = 0f;
        f.toggles = new Renderer.Toggles();
        f.toggles.fxaa = true;
        f.toggles.studio = true;
        Actor a = new Actor(m, style.animExaggeration);
        a.submit(f, new Matrix4f(), Animator.Clip.IDLE, 0f, 1f, "neutral");
        f.shadowFocus.set(0, 0.5f, 0);
        f.shadowRadius = 2.2f;
        thumbRenderer.render(f, target, target.width, target.height, 2f);
    }

    private String charLabel(CharacterModel m) {
        String heads = m.meta.has("headsTall") ? "  " + m.meta.get("headsTall").getAsString() + " CABECAS" : "";
        return m.name + heads + "  " + m.heightVoxels + " VOX  " + String.format("%.2f", m.height) + " U";
    }

    private String charLabel(State st, String styleKey) {
        String dir = currentDir(st, styleKey);
        return dir == null ? "(SEM MODELO)" : charLabel(rig(dir));
    }

    private static String ascii(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
    }

    private String flags(State st) {
        StyleProfile p = profile(st, st.style);
        return "CLIPE: " + st.clip + (st.expr != null ? "  EXPR " + st.expr.toUpperCase() : "")
                + (st.neutral ? "  LUZ NEUTRA" : "") + (p.key.equals("e") ? "  LUZ " + ascii(p.preset) : "")
                + "  CONTORNO " + (st.outlineLevel < 0 ? "PADRAO" : new String[]{"OFF", "DISCRETO", "MEDIO", "FORTE"}[st.outlineLevel])
                + "  NEVOA " + new String[]{"OFF", "LEVE", "PADRAO", "DENSA"}[st.fogLevel]
                + "  QUALIDADE " + st.quality
                + (st.toggles.textures ? "" : "  SEM TEXTURA") + (st.toggles.shadows ? "" : "  SEM SOMBRA")
                + (st.toggles.ao ? "" : "  SEM AO") + (st.toggles.post ? "" : "  SEM POS")
                + (st.toggles.silhouette ? "  SILHUETA" : "");
    }

    private void drawHud(State st, int w, int h, boolean interactive) {
        float s = Math.max(2f, Math.round(h / 360f));
        int white = 0xFFFFFFFF, soft = 0xE8E8F0E0, accent = 0xFFD86AFF;
        if (st.toggles.silhouette) {
            white = 0x202020FF;
            soft = 0x303030E0;
            accent = 0x404040FF;
        }
        float pad = 6 * s, line = 10 * s;
        if (st.mode == Mode.SIDE_BY_SIDE) {
            int n = StyleProfile.COUNT, cw = w / n;
            for (int i = 0; i < n; i++) {
                StyleProfile p = StyleProfile.of(i, st.preset);
                hud.textCentered(i * cw + cw / 2f, pad, s * 0.8f, p.title, accent);
                hud.textCentered(i * cw + cw / 2f, pad + line, s * 0.6f, charLabel(st, p.key), soft);
                if (i > 0) hud.rect(i * cw - 1, 0, 2, h, 0x00000060);
            }
            hud.textCentered(w / 2f, h - pad - 7 * s, s * 0.75f, "CLIPE: " + st.clip + (st.neutral ? "   LUZ NEUTRA" : ""), soft);
        } else {
            StyleProfile p = profile(st, st.style);
            String title = st.mode == Mode.PROPORTIONS ? "ESTUDO DE PROPORCOES"
                    : st.mode == Mode.LINEUP ? "COMPARACAO EM LINHA: " + ascii(st.lineup) + "  (LUZ " + ascii(p.title) + ")" : p.title;
            hud.text(pad, pad, s, title, accent);
            float y = pad + line;
            if (st.mode == Mode.PROPORTIONS) {
                hud.text(pad, y, s * 0.75f, st.normalized ? "MESMA ALTURA TOTAL (CABECA ENCOLHE)" : "MESMO VOXEL (CABECA FIXA DE 10 VOX)   FAIXAS = 1 CABECA", soft);
                for (int i = 0; i < STUDY.length; i++) {
                    String dir = "characters/study/" + STUDY[i] + (st.normalized ? "n" : "");
                    if (!assets.exists(dir)) continue;
                    CharacterModel m = rig(dir);
                    Vector4f v = new Vector4f((i - 2) * 1.55f, -0.08f, 0.9f, 1).mul(st.cam.viewProj());
                    float sx = (v.x / v.w * 0.5f + 0.5f) * w, sy = (1 - (v.y / v.w * 0.5f + 0.5f)) * h;
                    hud.textCentered(sx, sy, s, m.meta.get("headsTall").getAsString() + " CABECAS", white);
                    hud.textCentered(sx, sy + line, s * 0.75f,
                            "PERNA " + m.meta.get("legH").getAsInt() + "  TRONCO " + m.meta.get("torsoH").getAsInt(), soft);
                    hud.textCentered(sx, sy + line * 1.8f, s * 0.75f,
                            m.heightVoxels + " VOX  " + String.format("%.2f", m.height) + " U", soft);
                }
            } else if (st.mode == Mode.LINEUP) {
                hud.text(pad, y, s * 0.6f, "MESMA LUZ, CAMERA, CHAO E POSE PARA TODOS", soft);
                List<String> dirs = lineup(st);
                float[] xs = lineupLayout(dirs);
                for (int i = 0; i < dirs.size(); i++) {
                    CharacterModel m = rig(dirs.get(i));
                    Vector4f v = new Vector4f(xs[i], 0f, 0.55f, 1).mul(st.cam.viewProj());
                    float sx = (v.x / v.w * 0.5f + 0.5f) * w, sy = (1 - (v.y / v.w * 0.5f + 0.5f)) * h + 4 * s;
                    hud.textCentered(sx, sy, s * 0.62f, ascii(m.name), white);
                    String heads = m.meta.has("headsTall") ? m.meta.get("headsTall").getAsString() + " CAB  " : "";
                    String faces = m.meta.has("faces") ? "  " + m.meta.get("faces").getAsInt() + " FACES" : "";
                    hud.textCentered(sx, sy + line * 0.8f, s * 0.5f, heads + String.format("%.2f U", m.height) + faces, soft);
                }
            } else if (st.mode == Mode.CUSTOMIZE) {
                hud.text(pad, y, s * 0.75f, "PERSONALIZACAO (COMPOSICAO EM TEMPO DE EXECUCAO)", soft);
                drawCustomPanel(st, w, h, s, pad, line, white, soft, accent);
            } else {
                hud.text(pad, y, s * 0.75f, ascii(p.tagline), soft);
                y += line;
                hud.text(pad, y, s * 0.75f, ascii(charLabel(st, p.key)), white);
            }
            hud.text(pad, h - pad - 7 * s * 0.75f - (interactive ? 2 * line : 0), s * 0.6f, flags(st), soft);
        }
        if (interactive) {
            float hs = s * 0.55f;
            hud.text(pad, h - pad - 7 * hs - 9 * hs, hs,
                    "1-5 DIRECAO  TAB MODO  C PERSONAGEM  V CONJUNTO  Q/E CLIPE  X EXPRESSAO  T GIRAR  WASD ANDAR  L LUZ  O CONTORNO  K SILHUETA", soft);
            hud.text(pad, h - pad - 7 * hs, hs,
                    "F5 SOMBRA  F6 AO  F7 NEVOA  F8 POS  F9 TEXTURA  F10 NEUTRA  P QUALIDADE  M METRICAS  N NORMALIZAR  H HUD  F12 CAPTURA", soft);
            String fps = String.format("%.0f FPS  %.1f MS", fpsSmoothed, msSmoothed);
            hud.text(w - pad - Hud.textWidth(fps, s * 0.75f), pad, s * 0.75f, fps, white);
            if (st.metrics) {
                String[] lines = {
                        String.format("%d DRAWS  %d TRI CENA  %d TRI SOMBRA", renderer.lastDrawCount, renderer.lastTriangles, renderer.lastShadowTriangles),
                        String.format("%d MALHAS  %.1f MB VERTICES", Mesh.liveMeshes, Mesh.liveBytes / 1048576.0),
                        String.format("RESOLUCAO %dx%d  SSAA %.2f", w, h, st.quality.ssaa)};
                for (int i = 0; i < lines.length; i++)
                    hud.text(w - pad - Hud.textWidth(lines[i], s * 0.6f), pad + line * (i + 1), s * 0.6f, lines[i], soft);
            }
        }
    }

    /** Renderiza o estado em um FBO offscreen (SSAA 2x) e devolve a imagem. */
    public BufferedImage capture(State st, int w, int h) {
        if (captureFbo == null || captureFbo.width != w || captureFbo.height != h) {
            if (captureFbo != null) captureFbo.close();
            captureFbo = new Framebuffer(w, h, false, GL_RGBA8);
        }
        renderState(st, captureFbo, w, h, st.captureSsaa, 1f);
        captureFbo.bind();
        glFinish();
        return Screenshot.read(w, h);
    }

    /**
     * Benchmark reproduzível da cena completa (offscreen, glFinish por quadro, sem vsync): câmera de
     * exploração orbitando o herói que caminha. Opções separadas por vírgula:
     * {@code LxA}, {@code ssaa=}, {@code style=a..e|all}, {@code quality=baixa|padrao|alta},
     * {@code runs=} (repetições após aquecimento), {@code frames=}, {@code out=arquivo.csv}, {@code prepass},
     * {@code noshadow}. Imprime média, mediana, p95, 1% low, draws, triângulos e memória.
     */
    public void bench(String spec) {
        int w = 1920, h = 1080, runs = 3, frames = 300;
        Float ssaaOverride = null;
        boolean shadows = true;
        String styles = "abcde", out = null;
        Quality q = Quality.PADRAO;
        int preset = 0, outline = -1, fog = 2, terrain = World.E_TERRAIN;
        boolean noFxaa = false;
        String benchAct = null;
        boolean benchFar = true;
        String benchDump = null;
        int benchWarm = 400;                                  // aquecimento (06.1: 90 → 400; a GPU integrada só sobe de frequência depois de ~3 s de carga — ver performance da rodada 06.1)
        boolean benchParty = false;
        for (String tok : spec.split(",")) {
            if (tok.isBlank()) continue;
            if (tok.matches("\\d+x\\d+")) {
                w = Integer.parseInt(tok.split("x")[0]);
                h = Integer.parseInt(tok.split("x")[1]);
            } else if (tok.startsWith("ssaa=")) ssaaOverride = Float.parseFloat(tok.substring(5));
            else if (tok.startsWith("style=")) styles = tok.substring(6).equals("all") ? "abcde" : tok.substring(6);
            else if (tok.startsWith("quality=")) q = Quality.valueOf(tok.substring(8).toUpperCase());
            else if (tok.startsWith("runs=")) runs = Integer.parseInt(tok.substring(5));
            else if (tok.startsWith("frames=")) frames = Integer.parseInt(tok.substring(7));
            else if (tok.startsWith("preset=")) preset = java.util.Arrays.asList(StyleProfile.E_PRESETS).indexOf(tok.substring(7));
            else if (tok.startsWith("out=")) out = tok.substring(4);
            else if (tok.startsWith("outline=")) outline = Integer.parseInt(tok.substring(8));
            else if (tok.startsWith("fog=")) fog = Integer.parseInt(tok.substring(4));
            else if (tok.startsWith("terrain=")) terrain = Integer.parseInt(tok.substring(8));
            else if (tok.equals("fxaa=0")) noFxaa = true;
            else if (tok.startsWith("act=")) benchAct = tok.substring(4);
            else if (tok.equals("legacy")) Animator.legacy = true;
            else if (tok.startsWith("layout=")) World.layout = tok.substring(7);   // rodada 06: composição alternativa
            else if (tok.equals("far=0")) benchFar = false;
            else if (tok.equals("dbg")) Actor.debug = true;
            else if (tok.equals("party")) benchParty = true;
            else if (tok.equals("prepass")) renderer.depthPrepass = true;
            else if (tok.equals("noshadow")) shadows = false;
            else if (tok.startsWith("dump=")) benchDump = tok.substring(5);
            else if (tok.startsWith("warm=")) benchWarm = Integer.parseInt(tok.substring(5));   // rodada 06.1: tempo por quadro + GC (CSV)
        }
        float ssaa = ssaaOverride != null ? ssaaOverride : q.ssaa;
        if (captureFbo == null || captureFbo.width != w || captureFbo.height != h) {
            if (captureFbo != null) captureFbo.close();
            captureFbo = new Framebuffer(w, h, false, GL_RGBA8);
        }
        System.out.printf("bench: %dx%d, SSAA %.2fx (cena interna %dx%d), qualidade %s%s, relevo T%d, assets %s, sombra %s, pre-passo %s, %d execucoes x %d quadros, fusao de faces %s%n",
                w, h, ssaa, Math.round(w * ssaa), Math.round(h * ssaa), q, q.fxaa() && !noFxaa ? " (FXAA)" : "", terrain,
                assets.root(), shadows ? Renderer.SHADOW_SIZE + "^2" : "desligada",
                renderer.depthPrepass ? "ligado" : "desligado", runs, frames, vx.engine.VoxelMesher.merge ? "ligada" : "desligada");
        StringBuilder dump = new StringBuilder("estilo,execucao,quadro,ms,cpu_ms,gc_eventos,gc_ms\n");
        var gcs = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
        StringBuilder csv = new StringBuilder("estilo,execucao,media_ms,fps_medio,mediana_ms,p95_ms,fps_1pct_low,draws,tri_cena,tri_sombra,malhas,mb_vertices,heap_mb,anim_us_quadro,avaliacoes_quadro\n");
        for (char sk : styles.toCharArray()) {
            int s = StyleProfile.KEYS.indexOf(sk);
            if (s < 0) continue;
            double[] all = new double[runs * frames];
            for (int run = -1; run < runs; run++) {          // run -1 = aquecimento (carga de assets, JIT, drivers)
                State st = new State();
                st.style = s;
                st.mode = Mode.SCENE;
                st.hud = false;
                st.quality = q;
                st.preset = preset;
                st.outlineLevel = outline;
                st.fogLevel = fog;
                st.terrain = terrain;
                st.noFxaa = noFxaa;
                st.act = benchAct;
                st.terrainFar = benchFar;
                st.party = benchParty;
                st.toggles.shadows = shadows;
                defaultCamera(st);
                double[] ms = new double[frames];
                int n = run < 0 ? benchWarm : frames;
                Actor.animNanos = 0;
                Actor.animCount = 0;
                for (int i = 0; i < n; i++) {
                    st.time += 1f / 60f;
                    st.cam.yaw += 0.012f;
                    st.clip = Animator.Clip.WALK;
                    long gcN0 = 0, gcT0 = 0;
                    for (var g : gcs) { gcN0 += g.getCollectionCount(); gcT0 += g.getCollectionTime(); }
                    long t0 = System.nanoTime();
                    renderState(st, captureFbo, w, h, ssaa, 1f);
                    long tc = System.nanoTime();                // fim do envio pela CPU (antes de esperar a GPU)
                    glFinish();
                    if (run >= 0) ms[i] = (System.nanoTime() - t0) / 1e6;
                    if (run >= 0 && benchDump != null) {
                        long gcN1 = 0, gcT1 = 0;
                        for (var g : gcs) { gcN1 += g.getCollectionCount(); gcT1 += g.getCollectionTime(); }
                        dump.append(String.format(java.util.Locale.ROOT, "%c,%d,%d,%.3f,%.3f,%d,%d%n", sk, run + 1, i, ms[i], (tc - t0) / 1e6, gcN1 - gcN0, gcT1 - gcT0));
                    }
                }
                if (run < 0) continue;
                System.arraycopy(ms, 0, all, run * frames, frames);
                double[] stats = stats(ms);
                Runtime rt = Runtime.getRuntime();
                double heap = (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
                double animUs = Actor.animNanos / 1000.0 / n, evals = (double) Actor.animCount / n;
                System.out.printf("bench: estilo %s exec %d  media %.2f ms (%.0f FPS)  mediana %.2f ms  p95 %.2f ms  1%%low %.0f FPS | %d draws, %d tri cena, %d tri sombra, %d malhas, %.1f MB vertices, heap %.0f MB | animacao %.0f us/quadro (%.0f atores)%n",
                        Character.toUpperCase(sk), run + 1, stats[0], 1000 / stats[0], stats[1], stats[2], stats[3],
                        renderer.lastDrawCount, renderer.lastTriangles, renderer.lastShadowTriangles, Mesh.liveMeshes, Mesh.liveBytes / 1048576.0, heap, animUs, evals);
                csv.append(String.format(java.util.Locale.ROOT, "%c,%d,%.3f,%.1f,%.3f,%.3f,%.1f,%d,%d,%d,%d,%.1f,%.0f,%.1f,%.1f%n", sk, run + 1, stats[0], 1000 / stats[0],
                        stats[1], stats[2], stats[3], renderer.lastDrawCount, renderer.lastTriangles, renderer.lastShadowTriangles,
                        Mesh.liveMeshes, Mesh.liveBytes / 1048576.0, heap, animUs, evals));
            }
            if (benchDump != null) {
                try { java.nio.file.Files.writeString(java.nio.file.Path.of(benchDump), dump.toString()); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
            double[] st = stats(all);
            System.out.printf("bench: ESTILO %s TOTAL (%d quadros)  media %.2f ms (%.1f FPS)  mediana %.2f ms (%.1f FPS)  p95 %.2f ms  1%%low %.1f FPS%n",
                    Character.toUpperCase(sk), all.length, st[0], 1000 / st[0], st[1], 1000 / st[1], st[2], st[3]);
        }
        if (out != null) {
            try {
                Path p = Path.of(out);
                if (p.getParent() != null) Files.createDirectories(p.getParent());
                Files.writeString(p, csv.toString());
                System.out.println("bench: csv " + out);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** média, mediana, p95 (ms) e "1% low" (FPS médio do 1% de quadros mais lentos). */
    private static double[] stats(double[] ms) {
        double[] s = ms.clone();
        java.util.Arrays.sort(s);
        double avg = java.util.Arrays.stream(s).average().orElse(0);
        int k = Math.max(1, s.length / 100);
        double worst = 0;
        for (int i = s.length - k; i < s.length; i++) worst += s[i];
        return new double[]{avg, s[s.length / 2], s[(int) (s.length * 0.95)], 1000.0 / (worst / k)};
    }

    // ------------------------------------------------------------------ laço interativo
    public void runInteractive(State st) { runInteractive(st, 0); }

    /**
     * @param demoFrames se maior que zero, roda uma demonstração automática na janela real: percorre
     *                   modos, direções, personagens e clipes, fecha após esse número de quadros e
     *                   imprime o FPS médio por modo (com vsync). Serve de teste de fumaça do caminho interativo.
     */
    public void runInteractive(State st, int demoFrames) {
        double last = window.time();
        int shot = 0, frame = 0;
        Animator.Clip[] clips = Animator.Clip.values();
        String[] exprs = {null, "neutral", "joy", "determined", "surprise"};
        List<String> sets = new ArrayList<>(LINEUPS.keySet());
        double[] modeTime = new double[Mode.values().length];
        int[] modeFrames = new int[Mode.values().length];
        while (!window.shouldClose()) {
            window.poll();
            if (demoFrames > 0) {
                int per = Math.max(8, demoFrames / (Mode.values().length * StyleProfile.COUNT));
                if (frame >= demoFrames) break;
                if (frame % per == 0) {
                    int step = frame / per;
                    st.style = step % StyleProfile.COUNT;
                    Mode m = Mode.values()[(step / StyleProfile.COUNT) % Mode.values().length];
                    if (m != st.mode) {
                        st.mode = m;
                        st.camSet = false;
                        st.camAuto = true;
                    }
                    st.character = step % 7;
                    st.clip = clips[step % clips.length];
                    st.clipStart = 0;
                    st.turntable = true;
                    st.preset = step % StyleProfile.E_PRESETS.length;
                    st.lineup = sets.get(step % sets.size());
                }
            }
            frame++;
            double now = window.time();
            float dt = (float) Math.min(0.1, now - last);
            last = now;
            fpsSmoothed = fpsSmoothed == 0 ? 1 / Math.max(dt, 1e-4) : fpsSmoothed * 0.95 + 0.05 / Math.max(dt, 1e-4);
            msSmoothed = msSmoothed == 0 ? dt * 1000 : msSmoothed * 0.95 + 0.05 * dt * 1000;
            st.time += dt;

            for (int k : window.pressedKeys()) {
                switch (k) {
                    case GLFW_KEY_ESCAPE -> window.requestClose();
                    case GLFW_KEY_1, GLFW_KEY_2, GLFW_KEY_3, GLFW_KEY_4, GLFW_KEY_5 -> st.style = k - GLFW_KEY_1;
                    case GLFW_KEY_TAB -> {
                        st.mode = Mode.values()[(st.mode.ordinal() + 1) % Mode.values().length];
                        st.camSet = false;
                        st.camAuto = true;
                        if (st.mode == Mode.CUSTOMIZE && st.appearance == null) st.appearance = Appearance.preset("aventureiro");
                    }
                    case GLFW_KEY_UP -> { if (st.mode == Mode.CUSTOMIZE) st.customRow = Math.floorMod(st.customRow - 1, CUSTOM_ROWS.length); }
                    case GLFW_KEY_DOWN -> { if (st.mode == Mode.CUSTOMIZE) st.customRow = Math.floorMod(st.customRow + 1, CUSTOM_ROWS.length); }
                    case GLFW_KEY_RIGHT -> { if (st.mode == Mode.CUSTOMIZE) customStep(st, 1); }
                    case GLFW_KEY_LEFT -> { if (st.mode == Mode.CUSTOMIZE) customStep(st, -1); }
                    case GLFW_KEY_BACKSPACE -> {
                        if (st.mode == Mode.CUSTOMIZE) { st.appearance.reset(); customMsg(st, "PRESET ORIGINAL RESTAURADO: " + st.appearance.preset); }
                    }
                    case GLFW_KEY_F2 -> { if (st.mode == Mode.CUSTOMIZE) customSave(st); }
                    case GLFW_KEY_F3 -> { if (st.mode == Mode.CUSTOMIZE) customLoad(st); }
                    case GLFW_KEY_C -> {
                        if (st.mode == Mode.CHARACTER) st.appearance = null;     // volta ao catálogo de rigs
                        st.character++;
                        st.camAuto = true;
                    }
                    case GLFW_KEY_V -> {
                        st.lineup = sets.get((sets.indexOf(st.lineup) + 1) % sets.size());
                        st.camAuto = true;
                    }
                    case GLFW_KEY_E -> st.clip = clips[(st.clip.ordinal() + 1) % clips.length];
                    case GLFW_KEY_Q -> st.clip = clips[(st.clip.ordinal() + clips.length - 1) % clips.length];
                    case GLFW_KEY_X -> {
                        int i = java.util.Arrays.asList(exprs).indexOf(st.expr);
                        st.expr = exprs[(i + 1) % exprs.length];
                    }
                    case GLFW_KEY_L -> st.preset = (st.preset + 1) % StyleProfile.E_PRESETS.length;
                    case GLFW_KEY_O -> st.outlineLevel = st.outlineLevel >= 3 ? -1 : st.outlineLevel + 1;
                    case GLFW_KEY_K -> st.toggles.silhouette = !st.toggles.silhouette;
                    case GLFW_KEY_J -> Actor.debug = !Actor.debug;
                    case GLFW_KEY_P -> st.quality = Quality.values()[(st.quality.ordinal() + 1) % Quality.values().length];
                    case GLFW_KEY_M -> st.metrics = !st.metrics;
                    case GLFW_KEY_T -> st.turntable = !st.turntable;
                    case GLFW_KEY_N -> st.normalized = !st.normalized;
                    case GLFW_KEY_H -> st.hud = !st.hud;
                    case GLFW_KEY_F5 -> st.toggles.shadows = !st.toggles.shadows;
                    case GLFW_KEY_F6 -> st.toggles.ao = !st.toggles.ao;
                    case GLFW_KEY_F7 -> st.fogLevel = (st.fogLevel + 1) % 4;
                    case GLFW_KEY_F8 -> st.toggles.post = !st.toggles.post;
                    case GLFW_KEY_F9 -> st.toggles.textures = !st.toggles.textures;
                    case GLFW_KEY_F10 -> st.neutral = !st.neutral;
                    case GLFW_KEY_F12 -> {
                        Path out = Path.of("output/visual-review/manual", String.format("captura-%03d.png", ++shot));
                        Screenshot.save(capture(st, window.width(), window.height()), out);
                        System.out.println("captura: " + out);
                    }
                    default -> { }
                }
            }
            if (!st.camSet) defaultCamera(st);
            if (window.button(GLFW_MOUSE_BUTTON_LEFT)) {
                st.cam.yaw -= (float) window.mouseDx() * 0.006f;
                st.cam.pitch += (float) window.mouseDy() * 0.006f;
            }
            if (window.scroll() != 0 || window.button(GLFW_MOUSE_BUTTON_RIGHT)) st.camAuto = false;
            if (window.button(GLFW_MOUSE_BUTTON_RIGHT)) {
                st.cam.target.y += (float) window.mouseDy() * 0.002f * st.cam.distance;
            }
            if (window.scroll() != 0) st.cam.distance *= (float) Math.pow(0.9, window.scroll());

            if (st.mode == Mode.SCENE) {
                // Andar: WASD relativo à câmera; SHIFT corre; ESPAÇO ataca; R comemora; G reage a dano.
                st.cam.pitch = Math.max(st.cam.pitch, 0.04f);
                float fx = -(float) Math.sin(st.cam.yaw), fz = -(float) Math.cos(st.cam.yaw);
                float mx = 0, mz = 0;
                if (window.key(GLFW_KEY_W)) { mx += fx; mz += fz; }
                if (window.key(GLFW_KEY_S)) { mx -= fx; mz -= fz; }
                if (window.key(GLFW_KEY_D)) { mx -= fz; mz += fx; }
                if (window.key(GLFW_KEY_A)) { mx += fz; mz -= fx; }
                for (int k : window.pressedKeys()) {
                    Animator.Clip one = k == GLFW_KEY_SPACE ? Animator.Clip.ATTACK : k == GLFW_KEY_R ? Animator.Clip.EMOTE
                            : k == GLFW_KEY_G ? Animator.Clip.HIT : null;
                    if (one != null) {
                        oneShot = one;
                        oneShotStart = st.time;
                        oneShotEnd = st.time + (one == Animator.Clip.ATTACK ? 1.05f : one == Animator.Clip.HIT ? 0.85f : 1.55f);
                    }
                }
                boolean moving = mx != 0 || mz != 0;
                boolean run = window.key(GLFW_KEY_LEFT_SHIFT);
                if (demoFrames > 0) {
                    // na demonstração o clipe é escolhido pelo roteiro
                } else if (oneShot != null && st.time < oneShotEnd) {
                    st.clip = oneShot;
                    st.clipStart = oneShotStart;
                } else {
                    oneShot = null;
                    st.clipStart = 0;
                    st.clip = moving ? (run ? Animator.Clip.RUN : Animator.Clip.WALK) : Animator.Clip.IDLE;
                    if (moving && !Float.isNaN(st.heroX)) {
                        // rigs articulados: velocidade casada com a passada; A–D mantêm a da rodada 01
                        boolean jointed = heroJointed(st);
                        float len = (float) Math.sqrt(mx * mx + mz * mz),
                                speed = jointed ? (run ? WALK_RUN[1] : WALK_RUN[0]) : (run ? 3.2f : 1.35f);
                        st.heroX = Math.clamp(st.heroX + mx / len * speed * dt, -World.SIZE / 2 + 2, World.SIZE / 2 - 2);
                        st.heroZ = Math.clamp(st.heroZ + mz / len * speed * dt, -World.SIZE / 2 + 2, World.SIZE / 2 - 2);
                        float want = (float) Math.atan2(mx, mz);
                        float d = (float) Math.atan2(Math.sin(want - st.heroYaw), Math.cos(want - st.heroYaw));
                        st.heroYaw += d * Math.min(1f, dt * 12f);
                    }
                }
            }

            if (window.width() > 0 && window.height() > 0)
                renderState(st, null, window.width(), window.height(), st.quality.ssaa, Math.min(1f, dt * 12f));
            window.swap();
            modeTime[st.mode.ordinal()] += dt;
            modeFrames[st.mode.ordinal()]++;
        }
        if (demoFrames > 0) {
            System.out.printf("demo: janela %dx%d, vsync ligado, SSAA %.2fx, %d quadros%n", window.width(), window.height(), st.quality.ssaa, frame);
            for (Mode m : Mode.values())
                if (modeFrames[m.ordinal()] > 1)
                    System.out.printf("demo: %-26s %6.1f FPS medio%n", m.label, modeFrames[m.ordinal()] / modeTime[m.ordinal()]);
        }
    }

    boolean heroJointed(State st) {
        String dir = currentDir(st, StyleProfile.KEYS.substring(st.style, st.style + 1));
        return dir != null && rig(dir).index.containsKey("shin_l");
    }

    /** Velocidade de deslocamento do herói (u/s) ao andar e ao correr; casada com a passada do clipe. */
    public static final float[] WALK_RUN = {Animator.WALK_SPEED, Animator.RUN_SPEED};

    @Override
    public void close() {
        if (thumbRenderer != null) thumbRenderer.close();
        renderer.close();
        hud.close();
        if (captureFbo != null) captureFbo.close();
    }
}
