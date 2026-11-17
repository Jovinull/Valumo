#version 330 core
in vec2 vUv;
in vec4 vColor;
uniform sampler2D uTex;
out vec4 oColor;
// A textura multiplica a cor: branco 1x1 para áreas chapadas, atlas de fonte (alfa) e miniaturas (RGBA).
void main() { oColor = texture(uTex, vUv) * vColor; }
