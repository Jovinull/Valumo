#version 330 core
// Composição final: contorno (profundidade+normal), bloom, exposição, gradação, vinheta, gama.
in vec2 vUv;
out vec4 oColor;

uniform sampler2D uColor;
uniform sampler2D uNormal;
uniform sampler2D uDepth;
uniform sampler2D uBloom;
uniform vec2 uTexel;        // 1 / resolução da cena
uniform float uNear, uFar;
uniform float uOutline;     // 0 desliga
uniform float uOutlineWidth;
uniform vec3 uOutlineColor;
uniform float uOutlineNormal;   // peso das arestas por normal
uniform vec2 uOutlineFade;      // distância de início e fim do esmaecimento do contorno
uniform float uBloomStrength;
uniform float uExposure;
uniform float uSaturation;
uniform float uContrast;
uniform vec3 uTint;
uniform float uVignette;
uniform bool uFxaa;         // antisserrilhado por pós (rodada 03): alternativa leve ao SSAA

// FXAA compacto (variante "console" de Lottes): detecta borda pelo contraste de luma (raiz ~ gama) entre
// 4 diagonais e mistura ao longo da direção da borda. Só para SSAA 1x.
vec3 fxaa(vec2 uv) {
    vec2 px = uTexel;
    vec3 rM = texture(uColor, uv).rgb;
    vec3 rNW = texture(uColor, uv + vec2(-1.0, -1.0) * px).rgb, rNE = texture(uColor, uv + vec2(1.0, -1.0) * px).rgb;
    vec3 rSW = texture(uColor, uv + vec2(-1.0, 1.0) * px).rgb, rSE = texture(uColor, uv + vec2(1.0, 1.0) * px).rgb;
    const vec3 W = vec3(0.299, 0.587, 0.114);
    float lM = sqrt(dot(rM, W)), lNW = sqrt(dot(rNW, W)), lNE = sqrt(dot(rNE, W)), lSW = sqrt(dot(rSW, W)), lSE = sqrt(dot(rSE, W));
    float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE))), lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
    if (lMax - lMin < max(0.04, lMax * 0.125)) return rM;
    vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), (lNW + lSW) - (lNE + lSE));
    float red = max((lNW + lNE + lSW + lSE) * 0.03125, 1.0 / 128.0);
    float rcp = 1.0 / (min(abs(dir.x), abs(dir.y)) + red);
    dir = clamp(dir * rcp, -8.0, 8.0) * px;
    vec3 a = 0.5 * (texture(uColor, uv + dir * (1.0 / 3.0 - 0.5)).rgb + texture(uColor, uv + dir * (2.0 / 3.0 - 0.5)).rgb);
    vec3 b = a * 0.5 + 0.25 * (texture(uColor, uv - dir * 0.5).rgb + texture(uColor, uv + dir * 0.5).rgb);
    float lB = sqrt(dot(b, W));
    return (lB < lMin || lB > lMax) ? a : b;
}

float linDepth(vec2 uv) {
    float d = texture(uDepth, uv).r * 2.0 - 1.0;
    return 2.0 * uNear * uFar / (uFar + uNear - d * (uFar - uNear));
}

void main() {
    vec3 col = uFxaa ? fxaa(vUv) : texture(uColor, vUv).rgb;

    if (uOutline > 0.0) {
        vec2 o = uTexel * uOutlineWidth;
        float dc = linDepth(vUv);
        float d1 = linDepth(vUv + vec2(o.x, 0.0)), d2 = linDepth(vUv - vec2(o.x, 0.0));
        float d3 = linDepth(vUv + vec2(0.0, o.y)), d4 = linDepth(vUv - vec2(0.0, o.y));
        // Peso do contorno: o da amostra mais próxima da câmera (a borda pertence ao objeto da frente).
        float w = texture(uNormal, vUv).a, dm = dc;
        if (d1 < dm) { dm = d1; w = texture(uNormal, vUv + vec2(o.x, 0.0)).a; }
        if (d2 < dm) { dm = d2; w = texture(uNormal, vUv - vec2(o.x, 0.0)).a; }
        if (d3 < dm) { dm = d3; w = texture(uNormal, vUv + vec2(0.0, o.y)).a; }
        if (d4 < dm) { dm = d4; w = texture(uNormal, vUv - vec2(0.0, o.y)).a; }
        // Laplaciano relativo à distância: pega silhuetas sem riscar planos inclinados.
        float de = abs(d1 + d2 + d3 + d4 - 4.0 * dc) / dc;
        float edgeD = smoothstep(0.02, 0.07, de);
        vec4 nc = texture(uNormal, vUv);
        vec3 n0 = nc.xyz;
        float ne = 0.0;
        ne += length(n0 - texture(uNormal, vUv + vec2(o.x, 0.0)).xyz);
        ne += length(n0 - texture(uNormal, vUv - vec2(o.x, 0.0)).xyz);
        ne += length(n0 - texture(uNormal, vUv + vec2(0.0, o.y)).xyz);
        ne += length(n0 - texture(uNormal, vUv - vec2(0.0, o.y)).xyz);
        float edgeN = smoothstep(0.6, 1.2, ne) * uOutlineNormal;
        float fade = 1.0 - smoothstep(uOutlineFade.x, uOutlineFade.y, dc);   // some na distância (não polui o fundo)
        float e = clamp(max(edgeD, edgeN), 0.0, 1.0) * uOutline * fade * w;
        col = mix(col, uOutlineColor * col * 0.6 + uOutlineColor * 0.04, e);
    }

    // Só amostra o bloom quando ligado: a textura não é renderizada nos estilos sem bloom e
    // lixo (NaN/Inf) multiplicado por zero continuaria sendo NaN.
    if (uBloomStrength > 0.0) col += texture(uBloom, vUv).rgb * uBloomStrength;
    col *= uExposure;
    // Ombro suave apenas nas altas luzes: preserva a cor de paleta nas médias.
    col = mix(col, 1.0 - exp(-col), smoothstep(0.6, 1.4, col));
    col *= uTint;
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(l), col, uSaturation);
    col = (col - 0.18) * uContrast + 0.18;
    col = max(col, 0.0);
    vec2 d = vUv - 0.5;
    col *= 1.0 - dot(d, d) * uVignette;
    oColor = vec4(pow(col, vec3(1.0 / 2.2)), 1.0);
}
