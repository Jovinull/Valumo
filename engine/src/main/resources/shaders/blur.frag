#version 330 core
// Bloom: extração de brilho (uThreshold >= 0) e desfoque gaussiano separável de 9 amostras.
in vec2 vUv;
out vec4 oColor;
uniform sampler2D uTex;
uniform vec2 uDir;         // passo em UV (0 = só extração)
uniform float uThreshold;  // < 0 desliga a extração

void main() {
    if (uThreshold >= 0.0) {
        vec3 c = texture(uTex, vUv).rgb;
        float l = max(c.r, max(c.g, c.b));
        oColor = vec4(c * smoothstep(uThreshold, uThreshold + 0.6, l), 1.0);
        return;
    }
    float w[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    vec3 c = texture(uTex, vUv).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        c += texture(uTex, vUv + uDir * float(i)).rgb * w[i];
        c += texture(uTex, vUv - uDir * float(i)).rgb * w[i];
    }
    oColor = vec4(c, 1.0);
}
