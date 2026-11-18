package vx.viewer;

import vx.engine.Screenshot;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote de revisão da rodada 03 em {@code output/visual-review/round-03/}. Toda imagem é captura real
 * do viewer (FBO offscreen, SSAA 2× salvo indicação); pranchas são mosaicos dessas capturas. O "antes" vem
 * das capturas da rodada 02 (mesmas câmeras, lidas de {@code round-02/}) ou do snapshot congelado da E da
 * rodada 02 ({@code round-03/snapshot-r02/}, carregado pelo modo LINHA).
 *
 * <p>Seções: personagens, animacoes, criaturas, terreno, vegetacao, iluminacao, comparacoes, performance.
 */
public final class Review3 {
    private final App app;
    private final Path out;
    private final String only;
    private static final int E = 4;
    private static final String[] HEROES = {"adventurer", "ranger", "mage"};

    public Review3(App app, Path out, String only) {
        this.app = app;
        this.out = out;
        this.only = only;
    }

    private boolean want(String s) { return only.equals("all") || only.contains(s); }

    private App.State base(App.Mode mode) {
        App.State st = new App.State();
        st.style = E;
        st.mode = mode;
        st.hud = false;
        app.prepareCamera(st);
        return st;
    }

    private BufferedImage shot(App.State st, int w, int h, String file) {
        BufferedImage img = app.capture(st, w, h);
        if (file != null) save(img, file);
        return img;
    }

    private void save(BufferedImage img, String file) {
        Screenshot.save(img, out.resolve(file));
        System.out.println("review3: " + file);
    }

    private static BufferedImage enlarge(BufferedImage small, int f) {
        BufferedImage big = new BufferedImage(small.getWidth() * f, small.getHeight() * f, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics();
        g.drawImage(small, 0, 0, big.getWidth(), big.getHeight(), null);
        g.dispose();
        return big;
    }

    private static BufferedImage fit(BufferedImage src, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return o;
    }

    private BufferedImage roundTwo(String rel) {
        try {
            Path p = out.getParent().resolve("round-02").resolve(rel);
            return Files.exists(p) ? ImageIO.read(p.toFile()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private App.State lineup(String set, float myawDeg, float pitchDeg) {
        App.State st = base(App.Mode.LINEUP);
        st.lineup = set;
        st.hud = true;
        st.modelYaw = (float) Math.toRadians(myawDeg);
        st.cam.pitch = (float) Math.toRadians(pitchDeg);
        return st;
    }

    private App.State character(String dir, float yawDeg, float pitchDeg) {
        App.State st = base(App.Mode.CHARACTER);
        st.heroDir = dir;
        st.cam.yaw = (float) Math.toRadians(yawDeg);
        st.cam.pitch = (float) Math.toRadians(pitchDeg);
        return st;
    }

    private App.State scene(String hero, float hx, float hz, float hyawDeg, float yawDeg, float pitchDeg, float dist, float lift) {
        App.State st = base(App.Mode.SCENE);
        st.heroDir = hero;
        st.heroX = hx;
        st.heroZ = hz;
        st.heroYaw = (float) Math.toRadians(hyawDeg);
        st.cam.yaw = (float) Math.toRadians(yawDeg);
        st.cam.pitch = (float) Math.toRadians(pitchDeg);
        st.cam.distance = dist;
        st.camLift = lift;
        st.time = 1.3f;
        return st;
    }

    /** Câmeras de cena: as 6 da rodada 01/02 + encosta, casa e floresta (rodada 03). */
    private static final Object[][] SCENES = {
            {"no-cenario", Float.NaN, Float.NaN, 25f, 28f, 16f, 5.0f, 0.75f},
            {"ao-lado-da-estrutura", -5.4f, -3.2f, 30f, 22f, 9f, 6.0f, 0.75f},
            {"ao-lado-da-criatura", 4.2f, 9.6f, 95f, -18f, 10f, 6.2f, 0.75f},
            {"cena-apresentacao", Float.NaN, Float.NaN, 25f, 28f, 17f, 7.5f, 0.75f},
            {"cena-panorama", Float.NaN, Float.NaN, 25f, -40f, 28f, 26f, 0.75f},
            {"cena-rumo-a-casa", -1.5f, 7.5f, -160f, 165f, 6f, 5.5f, 0.75f},
            {"encosta-norte", 3f, -9.5f, 180f, 10f, 16f, 9f, 1.2f},
            {"casa", -5.4f, -3.0f, 200f, 15f, 12f, 7f, 0.75f},
            {"floresta", 11.0f, -1.0f, 90f, -90f, 18f, 8.0f, 0.75f},
    };

    private App.State sceneNamed(String hero, Object[] c) {
        return scene(hero, (Float) c[1], (Float) c[2], (Float) c[3], (Float) c[4], (Float) c[5], (Float) c[6], (Float) c[7]);
    }

    public void run() {
        if (want("personagens")) personagens();
        if (want("animacoes")) animacoes();
        if (want("criaturas")) criaturas();
        if (want("terreno")) terreno();
        if (want("vegetacao")) vegetacao();
        if (want("iluminacao")) iluminacao();
        if (want("comparacoes")) comparacoes();
        if (want("performance")) performance();
    }

    // ------------------------------------------------------------------ personagens
    private void silhouettes(String set, String file, int w, int h) {
        List<BufferedImage> cells = new ArrayList<>();
        for (float y : new float[]{0, 90, 180}) {
            App.State st = lineup(set, y, 4);
            st.toggles.silhouette = true;
            st.hud = false;
            cells.add(shot(st, w, h, null));
        }
        save(Review.sheet(cells, 1), file);
    }

    private void personagens() {
        String p = "personagens/";
        int w = 1800, h = 760;
        shot(lineup("herois", 0, 6), w, h, p + "herois-frente.png");
        shot(lineup("herois", 90, 6), w, h, p + "herois-perfil.png");
        shot(lineup("herois", 180, 6), w, h, p + "herois-costas.png");
        shot(lineup("herois", 35, 22), w, h, p + "herois-isometrica.png");
        App.State clay = lineup("herois", 35, 22);
        clay.toggles.textures = false;
        shot(clay, w, h, p + "herois-sem-textura.png");
        App.State neu = lineup("herois", 35, 12);
        neu.preset = 2;
        shot(neu, w, h, p + "herois-luz-neutra.png");
        silhouettes("herois", p + "herois-silhueta-frente-perfil-costas.png", 1500, 620);
        // tamanho de exploração: render REAL em baixa resolução (herói com ~60 px de altura), ampliado sem filtro
        App.State small = lineup("herois", 35, 22);
        small.hud = false;
        save(enlarge(app.capture(small, 480, 200), 4), p + "herois-tamanho-exploracao.png");
        App.State smallSil = lineup("herois", 0, 4);
        smallSil.hud = false;
        smallSil.toggles.silhouette = true;
        save(enlarge(app.capture(smallSil, 480, 200), 4), p + "herois-silhueta-tamanho-exploracao.png");
        // antes (rodada 02, snapshot) × depois
        shot(lineup("herois-r02-r03", 0, 6), 2600, 760, p + "antes-depois-frente.png");
        shot(lineup("herois-r02-r03", 35, 20), 2600, 760, p + "antes-depois-isometrica.png");
        shot(lineup("herois-r02-r03", 180, 6), 2600, 760, p + "antes-depois-costas.png");
        // estudos controlados (uma variável por vez)
        for (String[] s : new String[][]{{"r3-cabelo", "estudo-cabelo-aventureiro"}, {"r3-capuz", "estudo-capuz-exploradora"},
                {"r3-chapeu", "estudo-chapeu-maga"}}) {
            List<BufferedImage> v = new ArrayList<>();
            for (float[] a : new float[][]{{0, 6}, {35, 20}, {90, 6}, {180, 6}}) v.add(shot(lineup(s[0], a[0], a[1]), 1600, 640, null));
            save(Review.sheet(v, 2), p + s[1] + ".png");
            silhouettes(s[0], p + s[1] + "-silhueta.png", 1600, 560);
        }
        shot(lineup("r3-cajado", 15, 8), 1300, 640, p + "estudo-cajado-maga.png");
        shot(lineup("r3-rosto", 10, 6), 1200, 640, p + "estudo-rosto-aventureiro.png");
        // rostos: 4 expressões × 3 heróis, em close e em escala de exploração
        List<BufferedImage> ex = new ArrayList<>(), exSmall = new ArrayList<>();
        for (String hero : HEROES)
            for (String e : new String[]{"neutral", "joy", "determined", "surprise"}) {
                App.State st = character("characters/e/" + hero, 12, 4);
                st.camAuto = false;
                st.cam.distance = 1.25f;
                st.cam.target.y = 0.95f;
                st.expr = e;
                ex.add(shot(st, 480, 480, null));
                App.State far = character("characters/e/" + hero, 12, 14);
                far.camAuto = false;
                far.cam.distance = 7.0f;
                far.cam.fovDeg = 45f;
                far.cam.target.y = 0.75f;
                far.expr = e;
                BufferedImage fi = app.capture(far, 480, 480);
                exSmall.add(fi.getSubimage(160, 120, 160, 160));
            }
        save(Review.sheet(ex, 4), p + "rostos-expressoes-close.png");
        List<BufferedImage> big = new ArrayList<>();
        for (BufferedImage b : exSmall) big.add(enlarge(b, 3));
        save(Review.sheet(big, 4), p + "rostos-expressoes-escala-exploracao.png");
        // equipamentos: espada, arco com flecha (puxada máxima) e cajado
        List<BufferedImage> eq = new ArrayList<>();
        App.State sw = character("characters/e/adventurer", 30, 8);
        eq.add(shot(sw, 640, 640, null));
        App.State bw = character("characters/e/ranger", 90, 8);
        bw.clip = Animator.Clip.ATTACK;
        bw.time = 1.0f;
        eq.add(shot(bw, 640, 640, null));
        App.State bw2 = character("characters/e/ranger", 35, 14);
        bw2.clip = Animator.Clip.ATTACK;
        bw2.time = 1.0f;
        eq.add(shot(bw2, 640, 640, null));
        App.State stf = character("characters/e/mage", 25, 8);
        eq.add(shot(stf, 640, 640, null));
        save(Review.sheet(eq, 4), p + "equipamentos.png");
        // personagens no mundo: junto da casa e da criatura, em escala de exploração
        List<BufferedImage> world = new ArrayList<>();
        for (String hero : HEROES) {
            world.add(shot(sceneNamed("characters/e/" + hero, SCENES[1]), 720, 720, null));
            world.add(shot(sceneNamed("characters/e/" + hero, SCENES[2]), 720, 720, null));
        }
        save(Review.sheet(world, 2), p + "herois-no-mundo.png");
    }

    // ------------------------------------------------------------------ animações (quadros; vídeos em videos/)
    private void animacoes() {
        String p = "animacoes/";
        Animator.Clip[] clips = Animator.Clip.BASIC;
        for (String hero : HEROES)
            for (Animator.Clip c : clips) {
                float dur = switch (c) {
                    case IDLE -> 2.9f; case WALK -> 1f / Animator.WALK_CADENCE; case RUN -> 1f / Animator.RUN_CADENCE;
                    case ATTACK -> hero.equals("ranger") ? 1.6f : hero.equals("mage") ? 1.3f : 1.1f; case HIT -> 0.9f; case EMOTE -> 1.6f; case DEATH -> 1.2f;
                };
                List<BufferedImage> fr = new ArrayList<>();
                int n = c == Animator.Clip.ATTACK ? 10 : 8;
                for (int i = 0; i < n; i++) {
                    App.State st = character("characters/e/" + hero, c == Animator.Clip.WALK || c == Animator.Clip.RUN ? 0 : 35, 8);
                    if (c == Animator.Clip.WALK || c == Animator.Clip.RUN) st.modelYaw = (float) Math.toRadians(90);
                    st.camAuto = false;
                    st.cam.distance = 2.8f;
                    st.cam.target.y = 0.55f;
                    st.clip = c;
                    st.time = dur * i / n;
                    fr.add(shot(st, 360, 400, null));
                }
                save(Review.sheet(fr, n == 10 ? 5 : 8), p + hero + "-" + c.name().toLowerCase() + ".png");
            }
        // pés no terreno irregular: caminhando de perfil numa encosta (IK de pé)
        List<BufferedImage> slope = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            App.State st = scene("characters/e/adventurer", -7.6f, -3.6f, 90f, 10f, 8f, 3.2f, 0.5f);
            st.clip = Animator.Clip.WALK;
            st.time = 1.3f + i / (6 * Animator.WALK_CADENCE);
            slope.add(shot(st, 480, 400, null));
        }
        save(Review.sheet(slope, 6), p + "pes-em-terreno-irregular.png");
    }

    // ------------------------------------------------------------------ criaturas
    private void criaturas() {
        String p = "criaturas/";
        shot(lineup("criaturas-r02-r03", 25, 10), 2400, 760, p + "antes-depois.png");
        shot(lineup("criaturas", 30, 12), 2400, 820, p + "escala.png");
        List<BufferedImage> sh = new ArrayList<>();
        for (float[] a : new float[][]{{30, 16}, {150, 20}}) sh.add(shot(lineup("r3-carapaca", a[0], a[1]), 2000, 760, null));
        save(Review.sheet(sh, 1), p + "estudo-carapaca-tortuga.png");
        silhouettes("r3-carapaca", p + "estudo-carapaca-silhueta.png", 2000, 560);
        List<BufferedImage> ind = new ArrayList<>();
        for (String c : new String[]{"pompom", "lumi", "fungo", "tortuga"})
            for (float y : new float[]{25, 205}) ind.add(shot(character("creatures/e/" + c, y, 14), 560, 560, null));
        save(Review.sheet(ind, 4), p + "individuais.png");
        shot(scene(null, -1.8f, 4.6f, -60f, 70f, 12f, 3.6f, 0.75f), 1280, 720, p + "no-cenario-pompom.png");
        shot(scene(null, 5.0f, -2.4f, 120f, -150f, 10f, 4.0f, 0.75f), 1280, 720, p + "no-cenario-fungo-lumi.png");
        shot(sceneNamed(null, SCENES[2]), 1280, 720, p + "no-cenario-tortuga.png");
    }

    // ------------------------------------------------------------------ terreno
    private void terreno() {
        String p = "terreno/";
        String[] names = {"T0-rodada-02", "T1-voxel-refinado", "T2-organico", "T3-hibrido", "T4-combinacao"};
        for (int ci : new int[]{6, 4, 7, 5}) {
            List<BufferedImage> v = new ArrayList<>();
            for (int t = 0; t <= 4; t++) {
                App.State st = sceneNamed(null, SCENES[ci]);
                st.terrain = t;
                v.add(shot(st, 1280, 720, p + SCENES[ci][0] + "-" + names[t] + ".png"));
            }
            save(Review.sheet(v.subList(0, 4), 2), p + "comparacao-" + SCENES[ci][0] + ".png");
        }
    }

    // ------------------------------------------------------------------ vegetação
    private void vegetacao() {
        String p = "vegetacao/";
        shot(lineup("r3-carvalho", 0, 8), 1800, 800, p + "estudo-carvalho-frente.png");
        shot(lineup("r3-carvalho", 60, 30), 1800, 800, p + "estudo-carvalho-isometrica.png");
        App.State sil = lineup("r3-carvalho", 0, 8);
        sil.toggles.silhouette = true;
        shot(sil, 1800, 800, p + "estudo-carvalho-silhueta.png");
        BufferedImage before = roundTwo("mundo/floresta.png");
        BufferedImage after = shot(sceneNamed(null, SCENES[8]), 1600, 1000, p + "floresta.png");
        if (before != null) save(Review.sheet(List.of(fit(before, 1600, 1000), after), 2), p + "floresta-antes-depois.png");
        BufferedImage pb = roundTwo("mundo/cena-panorama.png");
        BufferedImage pa = shot(sceneNamed(null, SCENES[4]), 1920, 1080, p + "panorama.png");
        if (pb != null) save(Review.sheet(List.of(fit(pb, 1920, 1080), pa), 2), p + "panorama-antes-depois.png");
        shot(scene(null, -3.2f, 5.0f, 40f, 40f, 12f, 2.6f, 0.45f), 1600, 1000, p + "vegetacao-close.png");
    }

    // ------------------------------------------------------------------ iluminação
    private void iluminacao() {
        String p = "iluminacao/";
        List<BufferedImage> now = new ArrayList<>();
        for (int pr = 0; pr < 3; pr++) {
            App.State st = sceneNamed(null, SCENES[3]);
            st.preset = pr;
            now.add(shot(st, 1280, 720, p + "luz-" + StyleProfile.E_PRESETS[pr] + ".png"));
        }
        App.State tp = sceneNamed(null, SCENES[4]);
        tp.preset = 1;
        now.add(shot(tp, 1280, 720, p + "luz-tarde-panorama.png"));
        save(Review.sheet(now, 2), p + "presets.png");
        BufferedImage tb = roundTwo("mundo/luz-tarde.png"), tpb = roundTwo("mundo/luz-tarde-panorama.png");
        if (tb != null && tpb != null)
            save(Review.sheet(List.of(fit(tb, 1280, 720), now.get(1), fit(tpb, 1280, 720), now.get(3)), 2), p + "tarde-antes-depois.png");
        // antisserrilhado: sem AA, FXAA (padrão), SSAA 1,5× — capturas na resolução do jogo, recorte ampliado
        List<BufferedImage> aa = new ArrayList<>();
        for (Object[] q : new Object[][]{{App.Quality.BAIXA, 1f}, {App.Quality.PADRAO, 1f}, {App.Quality.ALTA, 1.5f}}) {
            App.State st = sceneNamed(null, SCENES[3]);
            st.quality = (App.Quality) q[0];
            st.captureSsaa = (Float) q[1];
            BufferedImage full = app.capture(st, 1920, 1080);
            aa.add(enlarge(full.getSubimage(820, 330, 300, 230), 3));
        }
        save(Review.sheet(aa, 3), p + "antisserrilhado-nenhum-fxaa-ssaa.png");
    }

    // ------------------------------------------------------------------ comparações rodada 02 × rodada 03
    private void comparacoes() {
        String p = "comparacoes/";
        for (int i = 0; i < 6; i++) {
            String name = (String) SCENES[i][0];
            boolean wide = name.startsWith("cena-");
            int w = wide ? 1920 : 720, h = wide ? 1080 : 720;
            BufferedImage after = shot(sceneNamed(null, SCENES[i]), w, h, p + name + ".png");
            BufferedImage before = roundTwo("mundo/" + name + ".png");
            if (before != null) save(Review.sheet(List.of(fit(before, w, h), after), 2), p + "antes-depois-" + name + ".png");
        }
        // a cena-objetivo da missão: os três aventureiros juntos, criaturas e a floresta pequena
        App.State party = scene("characters/e/adventurer", -0.6f, 3.6f, 20f, 18f, 13f, 6.4f, 0.75f);
        party.party = true;
        shot(party, 1920, 1080, p + "cena-equipe.png");
        App.State party2 = scene("characters/e/adventurer", 2.6f, 7.0f, 150f, 175f, 12f, 7.5f, 0.75f);
        party2.party = true;
        shot(party2, 1920, 1080, p + "cena-equipe-tortuga.png");
        App.State hero = scene("characters/e/adventurer", -0.6f, 3.6f, 20f, 18f, 14f, 6.2f, 0.75f);
        shot(hero, 1920, 1080, p + "cena-aventureiro.png");
        App.State r = scene("characters/e/ranger", 1.2f, 6.2f, 200f, 200f, 10f, 4.6f, 0.75f);
        shot(r, 1920, 1080, p + "cena-exploradora.png");
        App.State m = scene("characters/e/mage", 4.0f, -3.0f, -60f, -30f, 12f, 4.8f, 0.75f);
        shot(m, 1920, 1080, p + "cena-maga.png");
    }

    // ------------------------------------------------------------------ performance (capturas das configurações)
    private void performance() {
        String p = "performance/";
        for (App.Quality q : App.Quality.values()) {
            App.State st = sceneNamed(null, SCENES[3]);
            st.quality = q;
            st.captureSsaa = q.ssaa;
            shot(st, 1920, 1080, p + "cena-referencia-" + q.name().toLowerCase() + ".png");
        }
    }
}
