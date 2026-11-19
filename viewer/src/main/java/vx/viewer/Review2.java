package vx.viewer;

import vx.engine.Screenshot;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote de revisão da rodada 02 em {@code output/visual-review/round-02/}. Toda imagem é uma captura
 * real do viewer (FBO offscreen, SSAA 2x); as pranchas são mosaicos dessas capturas. Câmeras fixas e
 * nomeadas: rodar de novo reproduz as mesmas imagens. As câmeras de cena repetem as da rodada 01
 * ({@link Review}) para permitir comparação direta B × E.
 *
 * <p>Seções (argumento opcional de {@code --review2}): personagens, arquetipos, mundo, criaturas,
 * performance, all.
 */
public final class Review2 {
    private final App app;
    private final Path out;
    private final String only;
    private static final int E = 4, B = 1;

    public Review2(App app, Path out, String only) {
        this.app = app;
        this.out = out;
        this.only = only;
    }

    private boolean want(String s) { return only.equals("all") || only.contains(s); }

    private App.State base(int style, App.Mode mode) {
        App.State st = new App.State();
        st.style = style;
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
        System.out.println("review2: " + file);
    }

    private static BufferedImage enlarge(BufferedImage small, int f) {
        BufferedImage big = new BufferedImage(small.getWidth() * f, small.getHeight() * f, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = big.createGraphics();
        g.drawImage(small, 0, 0, big.getWidth(), big.getHeight(), null);   // vizinho mais próximo
        g.dispose();
        return big;
    }

    private App.State lineup(String set, float myawDeg, float pitchDeg) {
        App.State st = base(E, App.Mode.LINEUP);
        st.lineup = set;
        st.hud = true;
        st.modelYaw = (float) Math.toRadians(myawDeg);
        st.cam.pitch = (float) Math.toRadians(pitchDeg);
        return st;
    }

    private App.State character(int style, String dir, float yawDeg, float pitchDeg) {
        App.State st = base(style, App.Mode.CHARACTER);
        st.heroDir = dir;
        st.cam.yaw = (float) Math.toRadians(yawDeg);
        st.cam.pitch = (float) Math.toRadians(pitchDeg);
        return st;
    }

    private App.State closeFace(String dir, float yawDeg) {
        App.State st = character(E, dir, yawDeg, 4);
        st.camAuto = false;
        st.cam.distance = 1.25f;
        st.cam.target.y = 0.92f;
        return st;
    }

    private App.State scene(int style, String hero, float hx, float hz, float hyawDeg, float yawDeg, float pitchDeg, float dist) {
        App.State st = base(style, App.Mode.SCENE);
        st.heroDir = hero;
        st.heroX = hx;
        st.heroZ = hz;
        st.heroYaw = (float) Math.toRadians(hyawDeg);
        st.cam.yaw = (float) Math.toRadians(yawDeg);
        st.cam.pitch = (float) Math.toRadians(pitchDeg);
        st.cam.distance = dist;
        st.time = 1.3f;
        return st;
    }

    /** Câmeras de cena da rodada 01 (mesmos valores de {@link Review}) + novas da rodada 02. */
    private static final Object[][] SCENES = {
            {"no-cenario", Float.NaN, Float.NaN, 25f, 28f, 16f, 5.0f, 0.75f},
            {"ao-lado-da-estrutura", -5.4f, -3.2f, 30f, 22f, 9f, 6.0f, 0.75f},
            {"ao-lado-da-criatura", 4.2f, 9.6f, 95f, -18f, 10f, 6.2f, 0.75f},
            {"cena-apresentacao", Float.NaN, Float.NaN, 25f, 28f, 17f, 7.5f, 0.75f},
            {"cena-panorama", Float.NaN, Float.NaN, 25f, -40f, 28f, 26f, 0.75f},
            {"cena-rumo-a-casa", -1.5f, 7.5f, -160f, 165f, 6f, 5.5f, 0.75f},
    };

    private App.State sceneNamed(int style, String hero, Object[] c) {
        App.State st = scene(style, hero, (Float) c[1], (Float) c[2], (Float) c[3], (Float) c[4], (Float) c[5], (Float) c[6]);
        st.camLift = (Float) c[7];
        return st;
    }

    public void run() {
        if (want("personagens")) personagens();
        if (want("arquetipos")) arquetipos();
        if (want("mundo")) mundo();
        if (want("criaturas")) criaturas();
        if (want("performance")) performance();
    }

    // ------------------------------------------------------------------ 1. variantes do aventureiro
    private void personagens() {
        String p = "personagens/";
        int w = 2400, h = 820;
        shot(lineup("variantes", 0, 6), w, h, p + "variantes-frente.png");
        shot(lineup("variantes", 90, 6), w, h, p + "variantes-perfil.png");
        shot(lineup("variantes", 180, 6), w, h, p + "variantes-costas.png");
        shot(lineup("variantes", 35, 22), w, h, p + "variantes-isometrica.png");
        App.State sil = lineup("variantes", 0, 6);
        sil.toggles.silhouette = true;
        shot(sil, w, h, p + "variantes-silhueta-frente.png");
        App.State silp = lineup("variantes", 90, 6);
        silp.toggles.silhouette = true;
        shot(silp, w, h, p + "variantes-silhueta-perfil.png");
        App.State clay = lineup("variantes", 35, 22);
        clay.toggles.textures = false;
        shot(clay, w, h, p + "variantes-sem-textura.png");
        App.State neu = lineup("variantes", 35, 12);
        neu.preset = 2;
        shot(neu, w, h, p + "variantes-luz-neutra.png");
        // tamanho reduzido: render REAL em baixa resolução (≈ altura do herói na câmera de exploração), ampliado sem filtro
        App.State small = lineup("variantes", 35, 22);
        small.hud = false;
        save(enlarge(app.capture(small, 600, 205), 4), p + "variantes-tamanho-reduzido.png");
        List<BufferedImage> faces = new ArrayList<>();
        for (String v : new String[]{"v1", "v2", "v3"}) faces.add(shot(closeFace("characters/variants/" + v, 15), 700, 700, null));
        save(Review.sheet(faces, 3), p + "variantes-rostos.png");
        // escala de exploração, junto à estrutura e à criatura (V1, V2, V3 na mesma câmera da rodada 01)
        for (String v : new String[]{"v1", "v2", "v3"}) {
            List<BufferedImage> row = new ArrayList<>();
            for (int i = 0; i < 3; i++) row.add(shot(sceneNamed(E, "characters/variants/" + v, SCENES[i]), 720, 720, null));
            save(Review.sheet(row, 3), p + "variante-" + v + "-no-cenario.png");
        }
        // movimento: caminhada de perfil e golpe, 8 quadros de cada, mesmas variantes lado a lado
        for (String clip : new String[]{"walk", "attack"}) {
            List<BufferedImage> fr = new ArrayList<>();
            Animator.Clip c = Animator.Clip.valueOf(clip.toUpperCase());
            float dur = clip.equals("walk") ? 1f / 1.75f : 1.1f;
            for (int i = 0; i < 8; i++) {
                App.State st = lineup("v1-v2-v3", clip.equals("walk") ? 90 : 40, 10);
                st.hud = false;
                st.clip = c;
                st.time = dur * i / 8f;
                fr.add(shot(st, 900, 380, null));
            }
            save(Review.sheet(fr, 4), p + "variantes-" + clip + "-8-quadros.png");
        }
    }

    // ------------------------------------------------------------------ 2. arquétipos consolidados
    private void arquetipos() {
        String p = "arquetipos/";
        int w = 1800, h = 820;
        shot(lineup("herois", 0, 6), w, h, p + "herois-frente.png");
        shot(lineup("herois", 90, 6), w, h, p + "herois-perfil.png");
        shot(lineup("herois", 180, 6), w, h, p + "herois-costas.png");
        shot(lineup("herois", 35, 22), w, h, p + "herois-isometrica.png");
        App.State sil = lineup("herois", 25, 6);
        sil.toggles.silhouette = true;
        shot(sil, w, h, p + "herois-silhueta.png");
        App.State clay = lineup("herois", 35, 22);
        clay.toggles.textures = false;
        shot(clay, w, h, p + "herois-sem-textura.png");
        App.State neu = lineup("herois", 35, 12);
        neu.preset = 2;
        shot(neu, w, h, p + "herois-luz-neutra.png");
        App.State small = lineup("herois", 35, 22);
        small.hud = false;
        save(enlarge(app.capture(small, 450, 205), 4), p + "herois-tamanho-reduzido.png");
        shot(lineup("herois-b-e-d", 30, 14), 2600, 760, p + "herois-b-e-d.png");
        // rostos e expressões
        String[] heroes = {"adventurer", "ranger", "mage"};
        List<BufferedImage> ex = new ArrayList<>();
        for (String hero : heroes)
            for (String e : new String[]{"neutral", "joy", "determined", "surprise"}) {
                App.State st = closeFace("characters/e/" + hero, 12);
                st.expr = e;
                ex.add(shot(st, 520, 520, null));
            }
        save(Review.sheet(ex, 4), p + "expressoes.png");
        // equipamento em close (mão com a arma) e de costas
        List<BufferedImage> eq = new ArrayList<>();
        for (String hero : heroes)
            for (float[] v : new float[][]{{35, 10}, {200, 10}}) {
                App.State st = character(E, "characters/e/" + hero, v[0], v[1]);
                st.camAuto = false;
                st.cam.distance = 2.2f;
                st.cam.target.y = 0.55f;
                eq.add(shot(st, 600, 600, null));
            }
        save(Review.sheet(eq, 2), p + "equipamentos.png");
        // animações: 6 clipes × 6 quadros, os três heróis lado a lado
        for (Animator.Clip c : Animator.Clip.BASIC) {
            float dur = switch (c) {
                case IDLE -> 2.9f; case WALK -> 1f / 1.75f; case RUN -> 1f / 2.5f; case ATTACK -> 1.1f; case HIT -> 0.9f; case EMOTE -> 1.6f; case DEATH -> 1.2f;
            };
            List<BufferedImage> fr = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                App.State st = lineup("herois", c == Animator.Clip.WALK || c == Animator.Clip.RUN ? 70 : 35, 12);
                st.hud = false;
                st.clip = c;
                st.time = dur * i / 6f;
                fr.add(shot(st, 900, 420, null));
            }
            save(Review.sheet(fr, 3), p + "anim-" + c.name().toLowerCase() + ".png");
        }
        // heróis no mundo
        String[] dirs = {"characters/e/adventurer", "characters/e/ranger", "characters/e/mage"};
        List<BufferedImage> row = new ArrayList<>();
        for (String d : dirs) row.add(shot(sceneNamed(E, d, SCENES[0]), 720, 720, null));
        save(Review.sheet(row, 3), p + "herois-no-cenario.png");
    }

    // ------------------------------------------------------------------ 3. mundo
    private void mundo() {
        String p = "mundo/";
        // mesmas câmeras da rodada 01: B original × E
        for (Object[] c : SCENES) {
            boolean wide = ((String) c[0]).startsWith("cena-");
            int w = wide ? 1920 : 720, h = wide ? 1080 : 720;
            BufferedImage e = shot(sceneNamed(E, null, c), w, h, p + c[0] + ".png");
            BufferedImage b = shot(sceneNamed(B, null, c), w, h, null);
            save(Review.sheet(List.of(b, e), 2), p + "comparacao-b-e-" + c[0] + ".png");
        }
        // vistas novas
        shot(scene(E, null, -5.4f, -3.0f, 200f, 15f, 12f, 7f), 1600, 1000, p + "casa.png");
        App.State casaPerto = scene(E, null, -4.0f, -2.2f, 200f, 30f, 6f, 3.6f);
        casaPerto.camLift = 1.2f;
        shot(casaPerto, 1600, 1000, p + "casa-detalhe.png");
        shot(scene(E, null, 11.0f, -1.0f, 90f, -90f, 18f, 8.0f), 1600, 1000, p + "floresta.png");
        shot(scene(E, null, 0.8f, 1.2f, 200f, 200f, 14f, 6.5f), 1600, 1000, p + "caminho.png");
        App.State veg = scene(E, null, -3.2f, 5.0f, 40f, 40f, 12f, 2.6f);
        veg.camLift = 0.45f;
        shot(veg, 1600, 1000, p + "vegetacao-close.png");
        App.State est = scene(E, null, 6.2f, -3.4f, 190f, 8f, 10f, 4.6f);
        est.camLift = 1.1f;
        shot(est, 1600, 1000, p + "estrutura-santuario.png");
        // luz: dia, tarde, neutra (mesma câmera)
        List<BufferedImage> luz = new ArrayList<>();
        for (int pr = 0; pr < 3; pr++) {
            App.State st = sceneNamed(E, null, SCENES[3]);
            st.preset = pr;
            luz.add(shot(st, 1280, 720, "mundo/luz-" + StyleProfile.E_PRESETS[pr] + ".png"));
        }
        App.State tp = sceneNamed(E, null, SCENES[4]);
        tp.preset = 1;
        luz.add(shot(tp, 1280, 720, "mundo/luz-tarde-panorama.png"));
        save(Review.sheet(luz, 2), p + "luz-presets.png");
        // névoa 0..3 e contorno 0..3
        List<BufferedImage> fog = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            App.State st = sceneNamed(E, null, SCENES[4]);
            st.fogLevel = i;
            fog.add(shot(st, 1280, 720, null));
        }
        save(Review.sheet(fog, 2), p + "nevoa-0-1-2-3.png");
        List<BufferedImage> ol = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            App.State st = sceneNamed(E, null, SCENES[3]);
            st.outlineLevel = i;
            ol.add(shot(st, 1280, 720, null));
        }
        save(Review.sheet(ol, 2), p + "contorno-0-1-2-3.png");
        App.State raw = sceneNamed(E, null, SCENES[3]);
        raw.toggles.shadows = false;
        raw.toggles.ao = false;
        raw.toggles.post = false;
        raw.fogLevel = 0;
        shot(raw, 1280, 720, p + "cena-sem-efeitos.png");
    }

    // ------------------------------------------------------------------ 4. criaturas
    private void criaturas() {
        String p = "criaturas/";
        shot(lineup("criaturas", 30, 12), 2400, 820, p + "escala.png");
        shot(lineup("criaturas-b-e", 30, 12), 2400, 820, p + "comparacao-b-e.png");
        List<BufferedImage> ind = new ArrayList<>();
        for (String c : new String[]{"pompom", "lumi", "fungo", "tortuga"})
            for (float y : new float[]{25, 205})
                ind.add(shot(character(E, "creatures/e/" + c, y, 14), 600, 600, null));
        save(Review.sheet(ind, 4), p + "individuais.png");
        App.State ref = character(E, "creatures/e/pompom", 20, 10);
        ref.camAuto = false;
        ref.cam.distance = 1.6f;
        ref.cam.target.y = 0.35f;
        shot(ref, 1000, 1000, p + "referencia-pompom.png");
        shot(sceneNamed(E, null, SCENES[2]), 1280, 720, p + "no-cenario-tortuga.png");
        shot(scene(E, null, -1.8f, 4.6f, -60f, 70f, 12f, 3.6f), 1280, 720, p + "no-cenario-pompom.png");
        shot(scene(E, null, 5.0f, -2.4f, 120f, -150f, 10f, 4.0f), 1280, 720, p + "no-cenario-fungo-lumi.png");
    }

    // ------------------------------------------------------------------ 5. performance (capturas das configurações medidas)
    private void performance() {
        String p = "performance/";
        for (App.Quality q : App.Quality.values()) {
            App.State st = sceneNamed(E, null, SCENES[3]);
            st.quality = q;
            st.metrics = true;
            shot(st, 1920, 1080, p + "cena-referencia-" + q.name().toLowerCase() + ".png");
        }
    }
}
