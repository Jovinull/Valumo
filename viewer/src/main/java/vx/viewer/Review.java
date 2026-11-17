package vx.viewer;

import vx.engine.Screenshot;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera o pacote de revisão visual em {@code output/visual-review/}: capturas reais do viewer para as
 * quatro direções sob as MESMAS câmeras, mais pranchas compostas (matrizes). As pranchas são apenas
 * mosaicos das capturas; nenhum pixel é desenhado fora do renderer.
 */
public final class Review {
    private final App app;
    private final Path out;

    public Review(App app, Path out) {
        this.app = app;
        this.out = out;
    }

    private static final String[] KEYS = {"a", "b", "c", "d"};
    private static final String[] CHAR_NAMES = {"adventurer", "ranger", "mage"};

    private App.State base(int style, App.Mode mode) {
        App.State st = new App.State();
        st.style = style;
        st.mode = mode;
        app.prepareCamera(st);
        return st;
    }

    private BufferedImage shot(App.State st, int w, int h, String file) {
        BufferedImage img = app.capture(st, w, h);
        if (file != null) {
            Screenshot.save(img, out.resolve(file));
            System.out.println("review: " + file);
        }
        return img;
    }

    static BufferedImage sheet(List<BufferedImage> cells, int cols) {
        int cw = cells.get(0).getWidth(), ch = cells.get(0).getHeight();
        int rows = (cells.size() + cols - 1) / cols;
        BufferedImage s = new BufferedImage(cw * cols, ch * rows, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = s.createGraphics();
        for (int i = 0; i < cells.size(); i++) g.drawImage(cells.get(i), (i % cols) * cw, (i / cols) * ch, null);
        g.dispose();
        return s;
    }

    private App.State character(int style, int ch, float yawDeg, float pitchDeg) {
        App.State st = base(style, App.Mode.CHARACTER);
        st.character = ch;
        st.cam.yaw = (float) Math.toRadians(yawDeg);
        st.cam.pitch = (float) Math.toRadians(pitchDeg);
        return st;
    }

    private App.State scene(int style, float hx, float hz, float hyawDeg, float yawDeg, float pitchDeg, float dist) {
        App.State st = base(style, App.Mode.SCENE);
        st.heroX = hx;
        st.heroZ = hz;
        st.heroYaw = (float) Math.toRadians(hyawDeg);
        st.cam.yaw = (float) Math.toRadians(yawDeg);
        st.cam.pitch = (float) Math.toRadians(pitchDeg);
        st.cam.distance = dist;
        st.time = 1.3f;
        return st;
    }

    /** As 7 vistas exigidas pela matriz de comparação, na mesma ordem para todas as direções. */
    private List<BufferedImage> sevenViews(int style, int cell) {
        List<BufferedImage> row = new ArrayList<>();
        String k = KEYS[style];
        row.add(shot(character(style, 0, 0, 8), cell, cell, k + "/vista-frontal.png"));
        row.add(shot(character(style, 0, 90, 8), cell, cell, k + "/vista-lateral.png"));
        row.add(shot(character(style, 0, 180, 8), cell, cell, k + "/vista-traseira.png"));
        row.add(shot(character(style, 0, 40, 28), cell, cell, k + "/vista-isometrica.png"));
        row.add(shot(scene(style, Float.NaN, Float.NaN, 25, 28, 16, 5.0f), cell, cell, k + "/no-cenario.png"));
        row.add(shot(scene(style, -5.4f, -3.2f, 30, 22, 9, 6.0f), cell, cell, k + "/ao-lado-da-estrutura.png"));
        row.add(shot(scene(style, 4.2f, 9.6f, 95, -18, 10, 6.2f), cell, cell, k + "/ao-lado-da-criatura.png"));
        return row;
    }

    public void run() {
        int cell = 720;
        List<BufferedImage> matrix = new ArrayList<>();
        for (int s = 0; s < 4; s++) {
            String k = KEYS[s];
            matrix.addAll(sevenViews(s, cell));

            // personagens × 4 ângulos
            List<BufferedImage> chars = new ArrayList<>();
            for (int c = 0; c < CHAR_NAMES.length; c++)
                for (float[] v : new float[][]{{0, 8}, {90, 8}, {180, 8}, {40, 28}})
                    chars.add(shot(character(s, c, v[0], v[1]), 600, 600, null));
            Screenshot.save(sheet(chars, 4), out.resolve(k + "/personagens-4-angulos.png"));

            // criaturas
            List<BufferedImage> creatures = new ArrayList<>();
            for (int c = 3; c < 7; c++) creatures.add(shot(character(s, c, 30, 14), 600, 600, null));
            Screenshot.save(sheet(creatures, 4), out.resolve(k + "/criaturas.png"));

            // animações do protagonista: poses-chave de cada clipe
            Object[][] poses = {
                    {Animator.Clip.IDLE, 0.7f}, {Animator.Clip.WALK, 0.14f}, {Animator.Clip.WALK, 0.43f},
                    {Animator.Clip.RUN, 0.10f}, {Animator.Clip.RUN, 0.30f}, {Animator.Clip.ATTACK, 0.26f},
                    {Animator.Clip.ATTACK, 0.44f}, {Animator.Clip.ATTACK, 0.60f}, {Animator.Clip.HIT, 0.12f},
                    {Animator.Clip.EMOTE, 0.55f}};
            List<BufferedImage> anim = new ArrayList<>();
            for (Object[] p : poses) {
                App.State st = character(s, 0, 50, 12);
                st.clip = (Animator.Clip) p[0];
                st.time = (Float) p[1];
                st.camAuto = false;
                st.cam.target.y = 0.7f;
                st.cam.distance = 4.4f;
                anim.add(shot(st, 480, 480, null));
            }
            Screenshot.save(sheet(anim, 5), out.resolve(k + "/animacoes.png"));

            // cena: apresentação e panorama
            shot(scene(s, Float.NaN, Float.NaN, 25, 28, 17, 7.5f), 1920, 1080, k + "/cena-apresentacao.png");
            shot(scene(s, Float.NaN, Float.NaN, 25, -40, 28, 26f), 1920, 1080, k + "/cena-panorama.png");
            App.State low = scene(s, -1.5f, 7.5f, -160, 165, 6, 5.5f);
            shot(low, 1920, 1080, k + "/cena-rumo-a-casa.png");

            // modos de avaliação
            App.State nt = character(s, 0, 30, 12);
            nt.toggles.textures = false;
            shot(nt, 900, 900, k + "/sem-texturas.png");
            App.State nl = character(s, 0, 30, 12);
            nl.neutral = true;
            shot(nl, 900, 900, k + "/luz-neutra.png");
            App.State raw = scene(s, Float.NaN, Float.NaN, 25, 28, 17, 7.5f);
            raw.toggles.shadows = false;
            raw.toggles.ao = false;
            raw.toggles.post = false;
            raw.toggles.fog = false;
            shot(raw, 1280, 720, k + "/cena-sem-efeitos.png");
        }
        Screenshot.save(sheet(matrix, 7), out.resolve("comparacao/matriz-7-vistas.png"));
        System.out.println("review: comparacao/matriz-7-vistas.png");

        for (int c = 0; c < 3; c++) {
            App.State st = base(0, App.Mode.SIDE_BY_SIDE);
            st.character = c;
            shot(st, 2400, 900, "comparacao/lado-a-lado-" + CHAR_NAMES[c] + ".png");
        }
        App.State neutral = base(0, App.Mode.SIDE_BY_SIDE);
        neutral.neutral = true;
        shot(neutral, 2400, 900, "comparacao/lado-a-lado-luz-neutra.png");
        App.State walk = base(0, App.Mode.SIDE_BY_SIDE);
        walk.clip = Animator.Clip.WALK;
        walk.time = 0.14f;
        walk.cam.yaw = (float) Math.toRadians(60);
        shot(walk, 2400, 900, "comparacao/lado-a-lado-caminhada.png");

        App.State prop = base(0, App.Mode.PROPORTIONS);
        prop.neutral = true;
        shot(prop, 2400, 1100, "comparacao/proporcoes-mesmo-voxel.png");
        App.State propN = base(0, App.Mode.PROPORTIONS);
        propN.neutral = true;
        propN.normalized = true;
        shot(propN, 2400, 1100, "comparacao/proporcoes-mesma-altura.png");
        App.State propSide = base(0, App.Mode.PROPORTIONS);
        propSide.neutral = true;
        propSide.modelYaw = (float) Math.toRadians(90);
        shot(propSide, 2400, 1100, "comparacao/proporcoes-perfil.png");
    }
}
