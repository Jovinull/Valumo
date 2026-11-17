package vx.viewer;

import org.joml.Vector3f;

/**
 * Perfil de renderização de uma direção artística: luz, céu, névoa e pós-processo.
 * É aqui (e nos assets) que as quatro direções divergem; o código do renderer é o mesmo.
 * Cores de luz são dadas em sRGB e convertidas para linear.
 */
public final class StyleProfile {
    public String key, title, tagline;

    // luz
    public Vector3f sunDir = new Vector3f(-0.45f, 0.78f, 0.52f).normalize();
    public Vector3f sunColor, skyAmb, groundAmb, shadowTint = new Vector3f();
    public float aoStrength = 0.7f, shadowStrength = 0.8f, shadowSoft = 1.5f, wrap = 0f, faceShade = 0f;
    public int shadowTaps = 1;
    public Vector3f rimColor = new Vector3f(1, 1, 1);
    public float rim = 0f, spec = 0.3f, emissive = 1.6f, backlight = 0f, wind = 0f;
    /** Luz de preenchimento sem sombra (vinda de trás/lado oposto ao sol). Zero = desligada. */
    public Vector3f fillDir = new Vector3f(0.55f, 0.30f, -0.78f).normalize(), fillColor = new Vector3f();
    /** Vento "afim": a folhagem inteira de um objeto se inclina proporcionalmente à altura (sem frestas
     *  entre quads fundidos). Falso = ondulação por vértice da rodada 01. */
    public boolean affineWind = false;

    // céu e névoa
    public Vector3f zenith, horizon, sunGlow, fogColor;
    public float fogDensity = 0.012f, fogHeight = 2f;
    /** Distância (u) antes da qual não há névoa; opacidade máxima; cor somada na direção do sol. */
    public float fogStart = 0f, fogMax = 1f;
    public Vector3f fogSunColor = new Vector3f();

    // pós
    public float outline = 0f, outlineWidth = 1.5f, bloom = 0f, bloomThreshold = 1.0f;
    /** Peso do contorno por descontinuidade de normal; multiplicador na folhagem; distância de esmaecimento. */
    public float outlineNormal = 0.28f, outlineFoliage = 1f, outlineFadeStart = 9f, outlineFadeEnd = 38f;
    /** Peso do contorno no terreno (degraus de relevo). */
    public float outlineTerrain = 1f;
    /** Rodada 04: tratamento do relevo à distância (u da câmera); end = 0 desliga. */
    public float terrainFarStart = 0f, terrainFarEnd = 0f;
    /** Nome do preset de luz (só a direção E tem mais de um). */
    public String preset = "";
    public Vector3f outlineColor = new Vector3f(0.1f, 0.06f, 0.05f);
    public float exposure = 1f, saturation = 1f, contrast = 1f, vignette = 0.25f;
    public Vector3f tint = new Vector3f(1, 1, 1);

    // animação
    public float animExaggeration = 1f;

    public static Vector3f lin(String hex, float intensity) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new Vector3f(
                (float) Math.pow(((v >> 16) & 0xFF) / 255.0, 2.2) * intensity,
                (float) Math.pow(((v >> 8) & 0xFF) / 255.0, 2.2) * intensity,
                (float) Math.pow((v & 0xFF) / 255.0, 2.2) * intensity);
    }

    /** A — Miniature Fantasy: voxel clássico, luz dura e limpa, cores vivas. */
    public static StyleProfile a() {
        StyleProfile s = new StyleProfile();
        s.key = "a";
        s.title = "A - MINIATURE FANTASY";
        s.tagline = "voxel classico, cores vivas, luz limpa";
        s.sunColor = lin("#fff4d6", 1.25f);
        s.skyAmb = lin("#b4d8ff", 0.80f);
        s.groundAmb = lin("#a8c090", 0.55f);
        s.aoStrength = 0.75f;
        s.shadowStrength = 0.74f;
        s.shadowSoft = 1.2f;
        s.faceShade = 0.8f;
        s.spec = 0.35f;
        s.zenith = lin("#3f8fe8", 1f);
        s.horizon = lin("#bfe6ff", 1f);
        s.sunGlow = lin("#fff1c4", 1f);
        s.fogColor = lin("#bfe2fb", 1f);
        s.fogDensity = 0.0055f;
        s.saturation = 1.08f;
        s.contrast = 1.04f;
        s.vignette = 0.18f;
        return s;
    }

    /** B — Storybook Voxel: fim de tarde acolhedor, sombras suaves e coloridas, contorno de ilustração. */
    public static StyleProfile b() {
        StyleProfile s = new StyleProfile();
        s.key = "b";
        s.title = "B - STORYBOOK VOXEL";
        s.tagline = "ilustrado, acolhedor, contorno a tinta";
        s.sunDir = new Vector3f(-0.62f, 0.52f, 0.58f).normalize();
        s.sunColor = lin("#ffe2b4", 1.28f);
        s.skyAmb = lin("#b9c4f0", 0.62f);
        s.groundAmb = lin("#c9b090", 0.40f);
        s.shadowTint = lin("#5a4a9a", 0.10f);
        s.aoStrength = 0.5f;
        s.shadowStrength = 0.62f;
        s.shadowSoft = 3.2f;
        s.shadowTaps = 2;
        s.wrap = 0.35f;
        s.faceShade = 0.35f;
        s.rim = 0.25f;
        s.rimColor = lin("#ffe2b0", 1f);
        s.spec = 0.15f;
        s.wind = 0.6f;
        s.zenith = lin("#7fa6d9", 1f);
        s.horizon = lin("#ffe3c2", 1f);
        s.sunGlow = lin("#ffd9a0", 1f);
        s.fogColor = lin("#f3e2d2", 1f);
        s.fogDensity = 0.0065f;
        s.outline = 0.75f;
        s.outlineWidth = 1.4f;
        s.outlineColor = lin("#4a2f2a", 1f);
        s.bloom = 0.10f;
        s.saturation = 1.06f;
        s.contrast = 1.04f;
        s.tint = new Vector3f(1.015f, 1.0f, 0.975f);
        s.vignette = 0.38f;
        s.animExaggeration = 1.2f;
        return s;
    }

    /** C — Pixel Voxel Adventure: pixel art nítida, luz simples que não briga com a textura. */
    public static StyleProfile c() {
        StyleProfile s = new StyleProfile();
        s.key = "c";
        s.title = "C - PIXEL VOXEL ADVENTURE";
        s.tagline = "cuboides + pixel art, filtragem nearest";
        s.sunColor = lin("#fff0d0", 1.10f);
        s.skyAmb = lin("#b4d0f0", 0.70f);
        s.groundAmb = lin("#9aa68a", 0.42f);
        s.aoStrength = 0.6f;
        s.shadowStrength = 0.70f;
        s.shadowSoft = 1.0f;
        s.faceShade = 0.8f;
        s.spec = 0.0f;
        s.zenith = lin("#4a86d8", 1f);
        s.horizon = lin("#c8e4f8", 1f);
        s.sunGlow = lin("#fff1c4", 0.8f);
        s.fogColor = lin("#c4e0f4", 1f);
        s.fogDensity = 0.0050f;
        s.saturation = 1.04f;
        s.vignette = 0.15f;
        return s;
    }

    /** D — Stylized Voxel RPG: luz mais sofisticada (sombras macias, rim, bloom, névoa em altura). */
    public static StyleProfile d() {
        StyleProfile s = new StyleProfile();
        s.key = "d";
        s.title = "D - STYLIZED VOXEL RPG";
        s.tagline = "voxel fino, materiais, bloom, nevoa";
        s.sunDir = new Vector3f(-0.55f, 0.62f, 0.56f).normalize();
        s.sunColor = lin("#fff0cf", 1.45f);
        s.skyAmb = lin("#9fc4f5", 0.52f);
        s.groundAmb = lin("#8fa888", 0.42f);
        s.shadowTint = lin("#3a4f9a", 0.07f);
        s.aoStrength = 0.85f;
        s.shadowStrength = 0.85f;
        s.shadowSoft = 2.4f;
        s.shadowTaps = 2;
        s.wrap = 0.12f;
        s.faceShade = 0.3f;
        s.rim = 0.5f;
        s.rimColor = lin("#cfe6ff", 1f);
        s.spec = 0.55f;
        s.emissive = 3.0f;
        s.backlight = 0.35f;
        s.wind = 1.0f;
        s.zenith = lin("#2f78d8", 1f);
        s.horizon = lin("#cfe9f7", 1f);
        s.sunGlow = lin("#fff1c4", 1.3f);
        s.fogColor = lin("#d6e6f0", 1f);
        s.fogDensity = 0.0040f;
        s.fogHeight = 1.5f;
        s.bloom = 0.28f;
        s.bloomThreshold = 1.25f;
        s.saturation = 1.06f;
        s.contrast = 1.08f;
        s.vignette = 0.35f;
        return s;
    }

    /** Presets de luz da direção E, na ordem do atalho L. */
    public static final String[] E_PRESETS = {"dia", "tarde", "neutra"};

    /**
     * E — Fábula Voxel (direção consolidada da rodada 02): forma e paleta da B, acabamento de
     * personagem da D. Três presets reproduzíveis: "dia" (padrão), "tarde" (fim de tarde) e "neutra".
     * Valores justificados em docs/art/presets-graficos.md.
     */
    public static StyleProfile e(String preset) {
        StyleProfile s = new StyleProfile();
        s.key = "e";
        s.title = "E - FABULA VOXEL";
        s.preset = preset;
        s.tagline = "direcao consolidada: forma da B, acabamento da D (" + preset + ")";
        if (preset.equals("neutra")) {
            StyleProfile n = neutral(s);
            n.title = s.title;
            n.tagline = "direcao consolidada (luz neutra)";
            n.preset = preset;
            n.affineWind = true;
            n.animExaggeration = 1.15f;
            return n;
        }
        // comum aos presets atmosféricos
        s.aoStrength = 0.62f;
        s.shadowTaps = 4;          // Poisson 12 (qualidade ALTA usa 5x5)
        s.wrap = 0.28f;
        s.faceShade = 0.30f;
        s.spec = 0.30f;
        s.emissive = 2.2f;
        s.backlight = 0.22f;
        s.wind = 0.8f;
        s.affineWind = true;
        s.fogHeight = 2.5f;
        s.outline = 0.42f;
        s.outlineWidth = 1.1f;
        s.outlineColor = lin("#3e2a2c", 1f);
        s.outlineNormal = 0.10f;
        s.outlineFoliage = 0.18f;
        s.outlineTerrain = 0.30f;
        s.terrainFarStart = 12f;
        s.terrainFarEnd = 32f;
        s.outlineFadeStart = 8f;
        s.outlineFadeEnd = 30f;
        s.bloom = 0.07f;
        s.bloomThreshold = 1.3f;
        s.vignette = 0.20f;
        s.animExaggeration = 1.15f;
        if (preset.equals("tarde")) {
            // Rodada 03: a vegetação ficava oliva porque luz laranja + ambiente/sombra roxos + tint que cortava
            // azul empurravam o verde para o cinza-amarelado. Agora o céu ambiente é azul (sombras
            // verde-azuladas), a sombra quase não tinge, o tint não corta azul e o rim quente separa volumes.
            s.sunDir = new Vector3f(-0.64f, 0.42f, 0.64f).normalize();
            s.sunColor = lin("#ffd9a6", 1.40f);
            s.skyAmb = lin("#9fb6ee", 0.62f);
            s.groundAmb = lin("#c8a07c", 0.34f);
            s.shadowTint = lin("#5a5aa8", 0.045f);
            s.shadowStrength = 0.66f;
            s.shadowSoft = 2.8f;
            s.rim = 0.30f;
            s.rimColor = lin("#ffcf98", 1f);
            s.fillColor = lin("#8fa8e8", 0.18f);
            s.zenith = lin("#6f8fd0", 1f);
            s.horizon = lin("#ffd3a6", 1f);
            s.sunGlow = lin("#ffc27a", 1.1f);
            s.fogColor = lin("#c9c3d8", 1f);
            s.fogSunColor = lin("#ffb878", 0.30f);
            s.fogDensity = 0.024f;
            s.fogStart = 10f;
            s.fogMax = 0.40f;
            s.saturation = 1.06f;
            s.contrast = 1.06f;
            s.tint = new Vector3f(1.025f, 1.0f, 0.985f);
        } else {
            s.sunDir = new Vector3f(-0.50f, 0.70f, 0.52f).normalize();
            s.sunColor = lin("#fff0d4", 1.26f);
            s.skyAmb = lin("#b6cbef", 0.56f);
            s.groundAmb = lin("#c8b08c", 0.40f);
            s.shadowTint = lin("#5a50a0", 0.06f);
            s.shadowStrength = 0.70f;
            s.shadowSoft = 2.4f;
            s.rim = 0.16f;
            s.rimColor = lin("#fff0d0", 1f);
            s.fillColor = lin("#a9b9e6", 0.14f);
            s.zenith = lin("#5f93dc", 1f);
            s.horizon = lin("#d3e2ec", 1f);
            s.sunGlow = lin("#ffe6b4", 1f);
            s.fogColor = lin("#a9c1da", 1f);
            s.fogSunColor = lin("#ffe0b0", 0.22f);
            s.fogDensity = 0.024f;
            s.fogStart = 10f;          // somado a 0,8 × distância da câmera: a névoa começa depois do foco
            s.fogMax = 0.42f;
            s.saturation = 1.07f;
            s.contrast = 1.06f;
            s.tint = new Vector3f(1.01f, 1.0f, 0.985f);
        }
        return s;
    }

    /** Níveis de contorno comparáveis (atalho O): 0 desligado, 1 discreto, 2 intermediário, 3 forte (B original). */
    public StyleProfile withOutline(int level) {
        switch (level) {
            case 0 -> outline = 0f;
            case 1 -> { outline = 0.42f; outlineWidth = 1.1f; outlineNormal = 0.10f; outlineFoliage = 0.18f; outlineTerrain = 0.30f; }
            case 2 -> { outline = 0.62f; outlineWidth = 1.3f; outlineNormal = 0.18f; outlineFoliage = 0.40f; outlineTerrain = 0.55f; }
            case 3 -> { outline = 0.75f; outlineWidth = 1.4f; outlineNormal = 0.28f; outlineFoliage = 1f; outlineTerrain = 1f;
                        outlineFadeStart = 9f; outlineFadeEnd = 38f; }
            default -> { }
        }
        if (outline > 0 && outlineColor == null) outlineColor = lin("#3e2a2c", 1f);
        return this;
    }

    /** Níveis de névoa comparáveis (atalho F7): 0 desligada, 1 leve, 2 padrão do estilo, 3 densa. */
    public StyleProfile withFog(int level) {
        switch (level) {
            case 0 -> fogDensity = 0f;
            case 1 -> { fogDensity *= 0.5f; fogStart *= 1.4f; }
            case 3 -> { fogDensity *= 1.9f; fogStart *= 0.55f; fogMax = Math.min(1f, fogMax + 0.25f); }
            default -> { }
        }
        return this;
    }

    /** Luz neutra de estúdio: usada no modo "iluminação neutra" para julgar modelo e cor sem atmosfera. */
    public static StyleProfile neutral(StyleProfile base) {
        StyleProfile s = new StyleProfile();
        s.key = base.key;
        s.title = base.title;
        s.tagline = base.tagline;
        s.animExaggeration = base.animExaggeration;
        s.sunDir = new Vector3f(-0.4f, 0.8f, 0.6f).normalize();
        s.sunColor = lin("#ffffff", 0.95f);
        s.skyAmb = lin("#ffffff", 0.60f);
        s.groundAmb = lin("#ffffff", 0.45f);
        s.aoStrength = 0.6f;
        s.shadowStrength = 0.5f;
        s.shadowSoft = 1.5f;
        s.spec = 0.2f;
        s.emissive = 1.2f;
        s.zenith = lin("#8a8f98", 1f);
        s.horizon = lin("#b4b8be", 1f);
        s.sunGlow = new Vector3f();
        s.fogColor = lin("#b4b8be", 1f);
        s.fogDensity = 0f;
        s.vignette = 0f;
        // preenchimento por trás: sem ele as costas caem no ambiente puro e escurecem (rodada 01)
        s.fillColor = lin("#ffffff", 0.22f);
        return s;
    }

    /** Número de direções navegáveis (A–D da rodada 01 + E consolidada). */
    public static final int COUNT = 5;
    public static final String KEYS = "abcde";

    public static StyleProfile of(int i) {
        return of(i, 0);
    }

    /** @param preset índice em {@link #E_PRESETS} (ignorado pelas direções A–D) */
    public static StyleProfile of(int i, int preset) {
        return switch (i) {
            case 0 -> a();
            case 1 -> b();
            case 2 -> c();
            case 3 -> d();
            default -> e(E_PRESETS[Math.floorMod(preset, E_PRESETS.length)]);
        };
    }
}
