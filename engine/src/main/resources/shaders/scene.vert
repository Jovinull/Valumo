#version 330 core
// Geometria da cena (voxels e cuboides texturizados). Layout: ver Mesh.SCENE_LAYOUT.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in float aAo;
layout(location = 4) in float aMat;
layout(location = 5) in vec2 aUv;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uViewProj;
uniform mat4 uLightVP;
uniform float uTime;
uniform float uWind;      // amplitude do balanço de folhagem (0 desliga)
uniform bool uAffineWind; // true: inclinação do objeto inteiro proporcional à altura (afim, sem frestas)

// O pré-passo de profundidade usa este mesmo vertex shader; 'invariant' garante profundidades idênticas.
invariant gl_Position;

out vec3 vWorldPos;
out vec3 vNormal;
out vec3 vViewNormal;
out vec3 vColor;
out float vAo;
out float vMat;
out vec2 vUv;
out vec4 vShadowPos;

void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    if (uWind > 0.0 && abs(aMat - 3.0) < 0.5 && uAffineWind) {
        // Deslocamento afim na posição (fase por objeto, amplitude cresce com a altura acima da base):
        // quads fundidos se deformam exatamente como os voxels separados — nenhuma fresta.
        vec3 o = uModel[3].xyz;
        float ph = o.x * 0.83 + o.z * 0.61;
        float k = (wp.y - o.y) * 0.022 * uWind;
        wp.x += (sin(uTime * 1.25 + ph) * 0.8 + sin(uTime * 2.3 + ph * 1.7) * 0.25) * k;
        wp.z += cos(uTime * 1.05 + ph * 1.3) * 0.6 * k;
    } else if (uWind > 0.0 && abs(aMat - 3.0) < 0.5) {
        float ph = wp.x * 0.9 + wp.z * 0.7 + wp.y * 0.4;
        wp.x += sin(uTime * 1.6 + ph) * 0.035 * uWind;
        wp.z += cos(uTime * 1.3 + ph * 1.3) * 0.025 * uWind;
        wp.y += sin(uTime * 2.1 + ph * 0.7) * 0.012 * uWind;
    }
    mat3 nm = mat3(uModel);
    vec3 n = normalize(nm * aNormal);
    vWorldPos = wp.xyz;
    vNormal = n;
    vViewNormal = mat3(uView) * n;
    vColor = aColor;
    vAo = aAo;
    vMat = aMat;
    vUv = aUv;
    vShadowPos = uLightVP * vec4(wp.xyz + n * 0.02, 1.0);
    gl_Position = uViewProj * wp;
}
