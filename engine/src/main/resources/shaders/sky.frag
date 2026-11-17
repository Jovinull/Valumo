#version 330 core
// Céu em gradiente + halo do sol. Com uFlat, fundo liso de estúdio (modos de inspeção).
in vec2 vUv;
layout(location = 0) out vec4 oColor;
layout(location = 1) out vec4 oNormal;

uniform mat4 uInvViewProj;
uniform vec3 uZenith;
uniform vec3 uHorizon;
uniform vec3 uSunDir;
uniform vec3 uSunGlow;
uniform bool uFlat;

void main() {
    if (uFlat) {
        float g = smoothstep(0.0, 1.0, vUv.y);
        vec2 d = vUv - 0.5;
        vec3 c = mix(uHorizon, uZenith, g) * (1.0 - dot(d, d) * 0.5);
        oColor = vec4(c, 1.0);
    } else {
        vec4 p = uInvViewProj * vec4(vUv * 2.0 - 1.0, 1.0, 1.0);
        vec3 dir = normalize(p.xyz / p.w);
        float h = clamp(dir.y, -0.2, 1.0);
        vec3 c = mix(uHorizon, uZenith, pow(smoothstep(-0.05, 0.75, h), 0.7));
        float s = max(dot(dir, uSunDir), 0.0);
        c += uSunGlow * (pow(s, 6.0) * 0.25 + pow(s, 90.0) * 0.9 + smoothstep(0.9993, 0.9996, s) * 4.0);
        oColor = vec4(c, 1.0);
    }
    oNormal = vec4(0.5, 0.5, 1.0, 0.0);
}
