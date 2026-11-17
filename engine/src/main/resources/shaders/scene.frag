#version 330 core
// Iluminação direta estilizada. Saída em espaço linear HDR; o pós-processo faz tone mapping.
in vec3 vWorldPos;
in vec3 vNormal;
in vec3 vViewNormal;
in vec3 vColor;
in float vAo;
in float vMat;
in vec2 vUv;
in vec4 vShadowPos;

layout(location = 0) out vec4 oColor;
layout(location = 1) out vec4 oNormal;

uniform vec3 uSunDir;        // direção PARA o sol
uniform vec3 uSunColor;
uniform vec3 uSkyAmb;
uniform vec3 uGroundAmb;
uniform vec3 uCamPos;
uniform vec3 uFogColor;
uniform vec3 uRimColor;
uniform vec3 uShadowTint;    // cor somada às áreas em sombra (sombras coloridas)
uniform float uAoStrength;
uniform float uShadowStrength;
uniform float uShadowSoft;   // raio do PCF em texels
uniform int uShadowTaps;     // 1 = 3x3, 2 = 5x5, 4 = Poisson 12
uniform float uWrap;         // difusa "enrolada" (0 = Lambert)
uniform float uFaceShade;    // escurecimento fixo por orientação de face (look voxel clássico)
uniform float uRim;
uniform float uSpec;
uniform float uEmissive;
uniform float uBacklight;    // translucidez de folhagem
uniform float uFogDensity;
uniform float uFogHeight;
uniform float uFogStart;     // u sem névoa a partir da câmera
uniform float uFogMax;       // opacidade máxima da névoa (preserva a cor dos planos distantes)
uniform vec3 uFogSunColor;   // dispersão quente na direção do sol
uniform vec3 uFillDir;       // luz de preenchimento (sem sombra)
uniform vec3 uFillColor;
uniform float uOutlineWeight;   // peso do contorno deste desenho (gravado no alfa do buffer de normais)
uniform float uOutlineFoliage;  // multiplicador do contorno na folhagem
uniform bool uSilhouette;       // silhueta chapada (teste de leitura)
uniform bool uClay;             // "sem texturas": albedo neutro (argila) para julgar só a forma
uniform float uTime;
uniform bool uShadows;
uniform bool uUseTex;
// Rodada 04: tratamento do relevo à distância. Nos degraus do terreno, as faces verticais (risers) ficam
// escuras pela orientação e pelo AO e, repetidas numa encosta, viram listras. Entre start e end (u da
// câmera) a normal das faces verticais inclina para cima e o AO alivia: o degrau deixa de contrastar ao
// longe sem mudar a geometria nem o primeiro plano. end <= 0 desliga (outros desenhos e direções).
uniform float uTerrainFarStart;
uniform float uTerrainFarEnd;
uniform float uAlpha;
uniform sampler2DShadow uShadowMap;
uniform sampler2D uAtlas;

vec3 toLinear(vec3 c) { return pow(c, vec3(2.2)); }

float shadowFactor(float ndl) {
    if (!uShadows) return 1.0;
    vec3 p = vShadowPos.xyz / vShadowPos.w * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
    float bias = mix(0.0022, 0.0006, clamp(ndl, 0.0, 1.0));
    vec2 texel = 1.0 / vec2(textureSize(uShadowMap, 0));
    float sum = 0.0;
    float n = 0.0;
    if (uShadowTaps == 4) {
        // 12 amostras em disco de Poisson (rodada 02): maciez do 5x5 com metade do custo.
        const vec2 pd[12] = vec2[](vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
                                   vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
                                   vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598));
        for (int i = 0; i < 12; i++)
            sum += texture(uShadowMap, vec3(p.xy + pd[i] * uShadowSoft * 1.25 * texel, p.z - bias));
        vec2 e4 = abs(p.xy * 2.0 - 1.0);
        return mix(sum / 12.0, 1.0, smoothstep(0.86, 1.0, max(e4.x, e4.y)));
    }
    int r = uShadowTaps;
    for (int x = -r; x <= r; x++)
        for (int y = -r; y <= r; y++) {
            vec2 o = vec2(x, y) / float(r) * uShadowSoft * texel;
            sum += texture(uShadowMap, vec3(p.xy + o, p.z - bias));
            n += 1.0;
        }
    // Desvanece a sombra perto da borda do mapa: o que sai da área coberta some aos poucos, sem corte.
    vec2 e = abs(p.xy * 2.0 - 1.0);
    float edge = smoothstep(0.86, 1.0, max(e.x, e.y));
    return mix(sum / n, 1.0, edge);
}

void main() {
    vec3 base = vColor;
    if (uUseTex) {
        vec4 t = texture(uAtlas, vUv);
        if (t.a < 0.5) discard;
        base = t.rgb;
    }
    vec3 albedo = toLinear(base);
    if (uClay && int(vMat + 0.5) != 2) albedo = vec3(0.42, 0.40, 0.38);
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos - vWorldPos);
    int mat = int(vMat + 0.5);
    float farTerrain = 0.0;
    if (uTerrainFarEnd > 0.0) {
        farTerrain = smoothstep(uTerrainFarStart, uTerrainFarEnd, length(uCamPos - vWorldPos)) * (1.0 - abs(N.y));
        N = normalize(mix(N, vec3(0.0, 1.0, 0.0), farTerrain * 0.85));
    }

    float ndl = dot(N, uSunDir);
    float diff = clamp((ndl + uWrap) / (1.0 + uWrap), 0.0, 1.0);
    float sh = shadowFactor(ndl);
    float direct = diff * mix(1.0, sh, uShadowStrength);

    float ao = 1.0 - (1.0 - vAo) * uAoStrength * (1.0 - 0.7 * farTerrain);
    albedo *= 1.0 - uFaceShade * (abs(N.x) * 0.16 + abs(N.z) * 0.07 + max(-N.y, 0.0) * 0.25);

    vec3 amb = mix(uGroundAmb, uSkyAmb, N.y * 0.5 + 0.5);
    vec3 light = amb * ao + uSunColor * direct * mix(1.0, ao, 0.6);
    light += uShadowTint * (1.0 - direct) * ao;
    light += uFillColor * max(dot(N, uFillDir), 0.0) * ao;
    vec3 col = albedo * light;

    if (mat == 3 && uBacklight > 0.0) {
        col += albedo * uSunColor * max(-ndl, 0.0) * uBacklight * mix(1.0, sh, 0.7);
    }
    if (mat == 1) {
        vec3 H = normalize(uSunDir + V);
        float spec = pow(max(dot(N, H), 0.0), 24.0) * uSpec * sh;
        float fres = pow(1.0 - max(dot(N, V), 0.0), 3.0);
        col = mix(col, uSkyAmb * 1.6, fres * 0.35 * uSpec) + uSunColor * spec;
    }
    if (mat == 2) {
        col = albedo * uEmissive + col * 0.3;
    }
    float alpha = uAlpha;
    if (mat == 4) {
        // Água estilizada: ondulação em grade (mantém leitura "voxel"), brilho do sol e Fresnel simples.
        vec2 g = floor(vWorldPos.xz * 8.0) / 8.0;
        float w = sin(g.x * 3.1 + uTime * 1.2) * sin(g.y * 2.7 - uTime * 0.9);
        float w2 = sin((g.x + g.y) * 5.0 + uTime * 1.7);
        float crest = smoothstep(0.55, 0.9, w * 0.6 + w2 * 0.4);
        float fres = pow(1.0 - max(dot(vec3(0, 1, 0), V), 0.0), 2.5);
        vec3 deep = albedo * (amb + uSunColor * 0.6 * mix(1.0, sh, uShadowStrength));
        col = mix(deep, uSkyAmb * 1.5, fres * 0.55) + vec3(crest) * 0.22 * uSunColor * sh;
        alpha = mix(0.72, 0.95, fres);
    }
    if (uRim > 0.0) {
        float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0) * max(N.y * 0.5 + 0.7, 0.0);
        col += uRimColor * rim * uRim * (0.4 + 0.6 * sh);
    }

    float dist = length(uCamPos - vWorldPos);
    float hf = exp(-max(vWorldPos.y - uFogHeight, 0.0) * 0.12);
    float fog = 1.0 - exp(-max(dist - uFogStart, 0.0) * uFogDensity * mix(0.35, 1.0, hf));
    vec3 fogCol = uFogColor + uFogSunColor * pow(max(dot(-V, uSunDir), 0.0), 6.0);
    col = mix(col, fogCol, clamp(min(fog, uFogMax), 0.0, 1.0));

    float ow = uOutlineWeight * (mat == 3 ? uOutlineFoliage : 1.0);
    if (uSilhouette) {
        col = vec3(0.018, 0.014, 0.022);
        alpha = 1.0;
        ow = 0.0;
    }
    oColor = vec4(col, alpha);
    oNormal = vec4(normalize(vViewNormal) * 0.5 + 0.5, ow);
}
