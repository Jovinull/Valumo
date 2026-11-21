package vx.viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import vx.engine.Camera;
import vx.engine.Framebuffer;
import vx.engine.Mesh;
import vx.engine.Shader;
import vx.engine.Texture;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_FUNC;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

/**
 * Pipeline: (1) mapa de sombra ortográfico do sol; (2) céu + cena em FBO HDR com supersampling,
 * gravando cor e normal; (3) bloom opcional em 1/4 de resolução; (4) composição final
 * (contorno, gradação, gama) no alvo. Ver docs/architecture/renderizacao.md.
 */
public final class Renderer implements AutoCloseable {
    /** Um item a desenhar neste quadro. */
    public static final class Draw {
        public final Mesh mesh;
        public final Matrix4f model;
        public final Texture texture;
        public final boolean transparent, castsShadow;
        /** Peso do contorno a tinta deste desenho (1 = cheio; terreno usa o peso do estilo). */
        public float outline = 1f;
        /** Desenho do relevo: recebe o tratamento de distância (uTerrainFar). */
        public boolean terrain;
        /** Sobreposição de depuração: desenhado por último, sem teste de profundidade ("raio X"). */
        public boolean overlay;
        /** Rodada 09: lampejo de impacto deste desenho (0 = nenhum; 1 = cor 35% mais clara e levemente quente). */
        public float flash;
        /** Rodada 09.3: pontilhado do desenho (0 = opaco; ex.: herói com a câmera colada nele). */
        public float fade;
        /**
         * Rodada 12.3 (P85): peso do RECORTE deste desenho (0 = nada; até 1): o que dele fica entre a câmera e o alvo
         * ({@link Frame#cutTarget}), num cone com vértice na câmera e raio {@link Frame#cutRadius} na altura do alvo, é descartado
         * (borda pontilhada). Só o passo de cor: a sombra continua a da construção inteira.
         */
        public float cut;

        public Draw overlay() {
            overlay = true;
            return this;
        }

        public Draw terrain() {
            terrain = true;
            return this;
        }

        public Draw outline(float w) {
            outline = w;
            return this;
        }

        public Draw(Mesh mesh, Matrix4f model, Texture texture, boolean transparent, boolean castsShadow) {
            this.mesh = mesh;
            this.model = model;
            this.texture = texture;
            this.transparent = transparent;
            this.castsShadow = castsShadow;
        }
    }

    /** Recursos visuais alternáveis na interface. */
    public static final class Toggles {
        public boolean shadows = true, ao = true, fog = true, post = true, textures = true, studio = false;
        /** Tratamento do relevo à distância (rodada 04); desligável para comparação. */
        public boolean terrainFar = true;
        /** Silhueta chapada: todo modelo em um tom escuro sobre fundo claro (teste de leitura de forma). */
        public boolean silhouette = false;
        /** FXAA no pós (só aplicado sem supersampling). */
        public boolean fxaa = false;
        /**
         * Rodada 13.1: fundo TRANSPARENTE (sem céu; o alfa sai da cobertura dos objetos e do contorno) — para os itens da
         * mochila desenhados sobre o painel translúcido. Desligado (o padrão) o resultado é o de sempre, pixel a pixel.
         */
        public boolean alpha = false;
    }

    public static final class Frame {
        public final List<Draw> draws = new ArrayList<>();
        public Camera camera;
        public StyleProfile style;
        public Toggles toggles;
        public float time;
        public final Vector3f shadowFocus = new Vector3f();
        public float shadowRadius = 12f;
        /** Rodada 12.3: alvo do recorte (o peito do herói) e o raio do cone na altura do alvo (u; vértice na câmera). */
        public final Vector3f cutTarget = new Vector3f();
        public float cutRadius = 1.4f;

        public void add(Mesh mesh, Matrix4f model) { draws.add(new Draw(mesh, model, null, false, true)); }
        public void add(Mesh mesh, Matrix4f model, Texture tex) { draws.add(new Draw(mesh, model, tex, false, true)); }
    }

    /** Diagnóstico (rodada 08.1): -Dvx.wind=0 desliga o vento para isolar defeitos de malha. */
    static final float WIND_SCALE = Float.parseFloat(System.getProperty("vx.wind", "1"));

    /** Lado do mapa de sombra em texels; -Dvx.shadow=N permite experimentar. */
    public static final int SHADOW_SIZE = Integer.getInteger("vx.shadow", 2048);

    private final Shader scene = new Shader("scene.vert", "scene.frag");
    /** Rodada 12.3: a mesma cena com o recorte contextual (VX_CUT), só para os desenhos com {@link Draw#cut} > 0. */
    private final Shader sceneCut = new Shader("scene.vert", "scene.frag", "VX_CUT");
    private final Shader depth = new Shader("depth.vert", "depth.frag");
    /** Pré-passo de profundidade: mesmo vertex shader da cena (inclui o vento), fragment shader vazio. */
    private final Shader prepass = new Shader("scene.vert", "depth.frag");
    /** Liga/desliga o pré-passo de profundidade (para medir o ganho). */
    public boolean depthPrepass = false;
    private final Shader sky = new Shader("fullscreen.vert", "sky.frag");
    private final Shader blur = new Shader("fullscreen.vert", "blur.frag");
    private final Shader post = new Shader("fullscreen.vert", "post.frag");
    private final Framebuffer shadowMap;
    private Framebuffer sceneFbo, bloomA, bloomB;
    private final int emptyVao = glGenVertexArrays();
    private final Matrix4f lightVP = new Matrix4f();
    private final org.joml.FrustumIntersection frustum = new org.joml.FrustumIntersection();

    /**
     * QA de desempenho: com {@code -Dvx.gpu=true} cada passo é cercado por uma consulta {@code GL_TIME_ELAPSED} e o
     * tempo de GPU (ns) se acumula em {@link #gpuNanos}, na ordem de {@link #GPU_PASSES}. O resultado de um quadro é
     * lido no começo do seguinte (ou em {@link #gpuCollect()}); desligado, não custa nada. Só para medição
     * ({@code viewer --bench}): ler a consulta espera a GPU.
     */
    public static final boolean GPU_TIMERS = Boolean.getBoolean("vx.gpu");
    public static final String[] GPU_PASSES = {"sombra", "cena", "bloom", "composicao"};
    public final long[] gpuNanos = new long[GPU_PASSES.length];
    public int gpuFrames;
    private final int[] gpuQuery = new int[GPU_PASSES.length];
    private final boolean[] gpuUsed = new boolean[GPU_PASSES.length];
    private boolean gpuPending;

    private void gpuBegin(int pass) {
        if (!GPU_TIMERS) return;
        if (gpuQuery[pass] == 0) gpuQuery[pass] = glGenQueries();
        glBeginQuery(GL_TIME_ELAPSED, gpuQuery[pass]);
        gpuUsed[pass] = true;
    }

    private void gpuEnd() {
        if (GPU_TIMERS) glEndQuery(GL_TIME_ELAPSED);
    }

    /** Lê as consultas do último quadro desenhado (espera a GPU terminar). */
    public void gpuCollect() {
        if (!gpuPending) return;
        for (int i = 0; i < gpuQuery.length; i++) {
            if (gpuUsed[i]) gpuNanos[i] += glGetQueryObjectui64(gpuQuery[i], GL_QUERY_RESULT);
            gpuUsed[i] = false;
        }
        gpuFrames++;
        gpuPending = false;
    }
    private final Vector3f tmpCenter = new Vector3f(), tmpScale = new Vector3f();
    /** Estatísticas do último quadro (após descarte por frustum, só o passo de cena). */
    public int lastDrawCount, lastTriangles, lastShadowTriangles;

    /** Teste esfera × frustum com a caixa da malha levada ao mundo; margem cobre vento e animação. */
    private boolean visible(Draw d) {
        float[] b = d.mesh.bounds;
        tmpCenter.set((b[0] + b[3]) * 0.5f, (b[1] + b[4]) * 0.5f, (b[2] + b[5]) * 0.5f);
        float r = 0.5f * (float) Math.sqrt((b[3] - b[0]) * (b[3] - b[0]) + (b[4] - b[1]) * (b[4] - b[1]) + (b[5] - b[2]) * (b[5] - b[2]));
        d.model.transformPosition(tmpCenter);
        d.model.getScale(tmpScale);
        r = r * Math.max(tmpScale.x, Math.max(tmpScale.y, tmpScale.z)) + 0.15f;
        return frustum.testSphere(tmpCenter, r);
    }

    public Renderer() {
        shadowMap = new Framebuffer(SHADOW_SIZE, SHADOW_SIZE, true);
        glBindTexture(GL_TEXTURE_2D, shadowMap.depth);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    }

    private void ensureTargets(int w, int h) {
        if (sceneFbo != null && sceneFbo.width == w && sceneFbo.height == h) return;
        if (sceneFbo != null) {
            sceneFbo.close();
            bloomA.close();
            bloomB.close();
        }
        sceneFbo = new Framebuffer(w, h, true, GL_RGBA16F, GL_RGBA8);
        bloomA = new Framebuffer(Math.max(1, w / 4), Math.max(1, h / 4), false, GL_RGBA16F);
        bloomB = new Framebuffer(Math.max(1, w / 4), Math.max(1, h / 4), false, GL_RGBA16F);
        for (Framebuffer fb : new Framebuffer[]{bloomA, bloomB}) {   // memória de GPU nova vem com lixo
            fb.bind();
            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT);
        }
    }

    private void computeLight(Frame f) {
        Vector3f l = f.style.sunDir;
        float r = f.shadowRadius, dist = r * 3f + 20f;
        Vector3f up = Math.abs(l.y) > 0.98f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(up).cross(l).normalize();
        Vector3f lup = new Vector3f(l).cross(right).normalize();
        // Fixa o centro na grade de texels do mapa para a sombra não "nadar" quando a câmera anda.
        float texel = 2f * r / SHADOW_SIZE;
        Vector3f c = f.shadowFocus;
        float cr = (float) Math.floor(c.dot(right) / texel) * texel;
        float cu = (float) Math.floor(c.dot(lup) / texel) * texel;
        float cl = c.dot(l);
        Vector3f center = new Vector3f(right).mul(cr).add(new Vector3f(lup).mul(cu)).add(new Vector3f(l).mul(cl));
        Vector3f eye = new Vector3f(l).mul(dist).add(center);
        Matrix4f view = new Matrix4f().setLookAt(eye, center, lup);
        lightVP.setOrtho(-r, r, -r, r, 0.1f, dist * 2f).mul(view);
    }

    /**
     * Renderiza o quadro. {@code target == null} desenha no framebuffer padrão (janela).
     *
     * @param ssaa fator de supersampling da cena (1 = nativo)
     */
    public void render(Frame f, Framebuffer target, int outW, int outH, float ssaa) {
        render(f, target, 0, 0, outW, outH, ssaa);
    }

    /** Os uniforms do passo de cena que valem para o quadro inteiro (os dois programas: a cena e a variante do recorte). */
    private Shader sceneUniforms(Shader sh, Frame f, Toggles tg) {
        StyleProfile s = f.style;
        return sh
                .set("uView", f.camera.view()).set("uViewProj", f.camera.viewProj()).set("uLightVP", lightVP)
                .set("uTime", f.time).set("uWind", s.wind * WIND_SCALE).set("uAffineWind", s.affineWind)
                .set("uFogStart", s.fogStart > 0 ? s.fogStart + 0.8f * f.camera.distance : 0f).set("uFogMax", s.fogMax).set("uFogSunColor", s.fogSunColor)
                .set("uFillDir", s.fillDir).set("uFillColor", s.fillColor)
                .set("uOutlineFoliage", s.outlineFoliage).set("uSilhouette", tg.silhouette)
                .set("uClay", !tg.textures)
                .set("uSunDir", s.sunDir).set("uSunColor", s.sunColor)
                .set("uSkyAmb", s.skyAmb).set("uGroundAmb", s.groundAmb)
                .set("uCamPos", f.camera.position())
                .set("uFogColor", s.fogColor).set("uRimColor", s.rimColor).set("uShadowTint", s.shadowTint)
                .set("uAoStrength", tg.ao ? s.aoStrength : 0f)
                .set("uShadowStrength", s.shadowStrength).set("uShadowSoft", s.shadowSoft).set("uShadowTaps", s.shadowTaps)
                .set("uWrap", s.wrap).set("uFaceShade", s.faceShade)
                .set("uRim", s.rim).set("uSpec", s.spec).set("uEmissive", s.emissive).set("uBacklight", s.backlight)
                .set("uFogDensity", tg.fog && !tg.studio ? s.fogDensity : 0f).set("uFogHeight", s.fogHeight)
                .set("uShadows", tg.shadows)
                .set("uShadowMap", 0).set("uAtlas", 1).set("uAlpha", 1f);
    }

    /** Variante que compõe o resultado no retângulo (vx, vy, outW, outH) do alvo. */
    public void render(Frame f, Framebuffer target, int vx, int vy, int outW, int outH, float ssaa) {
        StyleProfile s = f.style;
        Toggles tg = f.toggles;
        int w = Math.max(1, Math.round(outW * ssaa)), h = Math.max(1, Math.round(outH * ssaa));
        ensureTargets(w, h);
        computeLight(f);
        lastDrawCount = 0;
        lastTriangles = 0;
        lastShadowTriangles = 0;

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);

        gpuCollect();

        // 1) sombra
        if (tg.shadows) {
            gpuBegin(0);
            shadowMap.bind();
            glClear(GL_DEPTH_BUFFER_BIT);
            glEnable(GL_POLYGON_OFFSET_FILL);
            glPolygonOffset(1.5f, 2.0f);
            depth.use().set("uLightVP", lightVP);
            frustum.set(lightVP);
            for (Draw d : f.draws) {
                if (!d.castsShadow || d.transparent || !visible(d)) continue;
                lastShadowTriangles += d.mesh.vertexCount / 3;
                depth.set("uModel", d.model);
                d.mesh.drawDepth();
            }
            glDisable(GL_POLYGON_OFFSET_FILL);
            gpuEnd();
        }

        // 2) céu + cena
        gpuBegin(1);
        sceneFbo.bind();
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glDepthMask(false);
        glDisable(GL_DEPTH_TEST);
        if (!tg.alpha) {                                       // rodada 13.1: sem céu no fundo transparente
            sky.use()
                    .set("uInvViewProj", new Matrix4f(f.camera.viewProj()).invert())
                    .set("uZenith", s.zenith).set("uHorizon", s.horizon)
                    .set("uSunDir", s.sunDir).set("uSunGlow", s.sunGlow)
                    .set("uFlat", tg.studio);
            glBindVertexArray(emptyVao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);

        // Pré-passo: grava só profundidade dos opacos sem textura; o passo de cor então sombreia cada
        // pixel uma única vez (o custo por pixel é alto por causa do PCF). Malhas texturizadas ficam de
        // fora porque descartam pixels por alfa.
        frustum.set(f.camera.viewProj());
        if (depthPrepass) {
            glColorMask(false, false, false, false);
            prepass.use().set("uView", f.camera.view()).set("uViewProj", f.camera.viewProj()).set("uLightVP", lightVP)
                    .set("uTime", f.time).set("uWind", s.wind * WIND_SCALE).set("uAffineWind", s.affineWind);
            for (Draw d : f.draws) {
                if (d.transparent || d.overlay || (d.texture != null && tg.textures) || d.cut > 0f || !visible(d)) continue;
                prepass.set("uModel", d.model);
                d.mesh.draw();
            }
            glColorMask(true, true, true, true);
        }

        boolean anyCut = false;
        for (Draw d : f.draws) anyCut |= d.cut > 0f;
        if (anyCut) sceneUniforms(sceneCut.use(), f, tg).set("uCutTarget", f.cutTarget).set("uCutRadius", f.cutRadius);
        sceneUniforms(scene.use(), f, tg);
        Texture.bind(0, shadowMap.depth);
        frustum.set(f.camera.viewProj());
        Shader current = scene;
        for (int pass = 0; pass < 3; pass++) {
            boolean transparent = pass == 1;
            if (pass == 2) {                                   // depuração por cima de tudo
                glDisable(GL_BLEND);
                glDepthMask(true);
                glDisable(GL_DEPTH_TEST);
            }
            if (transparent) {
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                glDepthMask(false);
                // o anexo de normais não deve receber mistura
                glDisablei(GL_BLEND, 1);
            }
            for (Draw d : f.draws) {
                if (d.overlay != (pass == 2) || (pass < 2 && d.transparent != transparent) || !visible(d)) continue;
                lastDrawCount++;
                lastTriangles += d.mesh.vertexCount / 3;
                boolean tex = d.texture != null && tg.textures;
                if (tex) d.texture.bind(1);
                Shader sh = d.cut > 0f ? sceneCut : scene;                    // rodada 12.3: a variante só para o recorte
                if (sh != current) current = sh.use();
                sh.set("uUseTex", tex).set("uModel", d.model).set("uOutlineWeight", d.outline).set("uFlash", d.flash).set("uFade", d.fade);
                if (sh == sceneCut) sh.set("uCut", d.cut);
                boolean far = d.terrain && f.style.terrainFarEnd > 0 && f.toggles.terrainFar;
                sh.set("uTerrainFarStart", f.style.terrainFarStart).set("uTerrainFarEnd", far ? f.style.terrainFarEnd : 0f);
                d.mesh.draw();
            }
        }
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glBindVertexArray(emptyVao);
        gpuEnd();

        // 3) bloom
        boolean bloomOn = tg.post && s.bloom > 0f && !tg.silhouette;
        if (bloomOn) {
            gpuBegin(2);
            bloomA.bind();
            blur.use().set("uTex", 0).set("uThreshold", s.bloomThreshold).set("uDir", 0f, 0f);
            Texture.bind(0, sceneFbo.color[0]);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            blur.set("uThreshold", -1f);
            for (int i = 0; i < 2; i++) {
                bloomB.bind();
                Texture.bind(0, bloomA.color[0]);
                blur.set("uDir", (1.3f + i) / bloomA.width, 0f);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                bloomA.bind();
                Texture.bind(0, bloomB.color[0]);
                blur.set("uDir", 0f, (1.3f + i) / bloomA.height);
                glDrawArrays(GL_TRIANGLES, 0, 3);
            }
            gpuEnd();
        }

        // 4) composição
        gpuBegin(3);
        glBindFramebuffer(GL_FRAMEBUFFER, target != null ? target.id : 0);
        glViewport(vx, vy, outW, outH);
        post.use()
                .set("uColor", 0).set("uNormal", 1).set("uDepth", 2).set("uBloom", 3)
                .set("uTexel", 1f / w, 1f / h)
                .set("uNear", f.camera.near).set("uFar", f.camera.far)
                .set("uOutline", tg.post && !tg.silhouette ? s.outline : 0f).set("uOutlineWidth", s.outlineWidth * ssaa)
                .set("uOutlineColor", s.outlineColor).set("uOutlineNormal", s.outlineNormal)
                .set("uOutlineFade", s.outlineFadeStart, s.outlineFadeEnd)
                .set("uBloomStrength", bloomOn ? s.bloom : 0f)
                .set("uExposure", s.exposure)
                .set("uSaturation", tg.post && !tg.silhouette ? s.saturation : 1f)
                .set("uContrast", tg.post && !tg.silhouette ? s.contrast : 1f)
                .set("uTint", tg.post && !tg.silhouette ? s.tint : new Vector3f(1, 1, 1))
                .set("uVignette", tg.post && !tg.silhouette ? s.vignette : 0f)
                .set("uFxaa", tg.fxaa && ssaa < 1.2f)
                .set("uAlphaOut", tg.alpha);
        Texture.bind(0, sceneFbo.color[0]);
        Texture.bind(1, sceneFbo.color[1]);
        Texture.bind(2, sceneFbo.depth);
        Texture.bind(3, bloomA.color[0]);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        gpuEnd();
        gpuPending = GPU_TIMERS;
        Texture.bind(0, 0);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        glDeleteVertexArrays(emptyVao);
        for (int q : gpuQuery) if (q != 0) glDeleteQueries(q);
        scene.close();
        sceneCut.close();
        depth.close();
        prepass.close();
        sky.close();
        blur.close();
        post.close();
        shadowMap.close();
        if (sceneFbo != null) {
            sceneFbo.close();
            bloomA.close();
            bloomB.close();
        }
    }
}
