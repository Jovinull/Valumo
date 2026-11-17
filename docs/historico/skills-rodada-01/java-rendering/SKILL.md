---
name: java-rendering
description: Alterar o renderer OpenGL do projeto (LWJGL 3) — inicialização, shaders GLSL, perfis de estilo, materiais, recursos de GPU, depuração e medição de desempenho.
---

# Renderização em Java (LWJGL 3 / OpenGL 3.3 core)

Leia antes: `docs/architecture/renderizacao.md`.

## Onde mexer

| Quero... | Arquivo |
|---|---|
| mudar a luz/atmosfera/pós de uma direção | `viewer/.../StyleProfile.java` (só números; sem recompilar shader) |
| mudar o modelo de iluminação ou um material | `engine/src/main/resources/shaders/scene.frag` (+ uniform em `Renderer`) |
| mudar contorno, bloom, gradação | `shaders/post.frag`, `shaders/blur.frag` |
| acrescentar um passo de render | `viewer/.../Renderer.java` |
| novo atributo de vértice | `Mesh.SCENE_LAYOUT` + `VoxelMesher` + `CuboidMesh` + `World` (malhas manuais) + `scene.vert` |

## Convenções OpenGL do projeto

- Contexto 3.3 core, `GL_CULL_FACE` ligado, faces **anti-horárias** vistas de fora, `GL_LEQUAL`.
- Cores de asset em sRGB; o shader lineariza (`pow 2.2`), ilumina em linear HDR e o `post.frag`
  aplica gama. Cores de luz em `StyleProfile` passam por `lin("#rrggbb", intensidade)`.
- Unidades de textura da cena: 0 = mapa de sombra (`sampler2DShadow`), 1 = atlas.
- Uniform inexistente é ignorado em silêncio (`Shader.set`): se um parâmetro "não faz nada",
  confira o nome no GLSL.
- `Mesh`, `Texture`, `Framebuffer`, `Shader` são `AutoCloseable`; quem cria fecha. Assets em cache
  vivem até o fim do processo.

## Inicialização

`Window` cria o contexto (GLFW), `GL.createCapabilities()`, vsync ligado. Janela invisível
(`visible=false`) serve às capturas offscreen. JVM: `--enable-native-access=ALL-UNNAMED` e
`-Djava.awt.headless=true` (já em `viewer/build.gradle.kts`).

## Ciclo de trabalho

```bash
REBUILD=1 tools/snap.sh "mode=scene,style=d,out=output/tmp/antes.png"
# ...edita shader/perfil...
REBUILD=1 tools/snap.sh "mode=scene,style=d,out=output/tmp/depois.png"
```

Sempre compare antes/depois na **mesma** spec, e confira as outras três direções: o shader é
compartilhado. Erro de compilação GLSL aborta com o log do driver no terminal.

## Depuração

- Isole efeitos: `noshadow=1`, `noao=1`, `nopost=1`, `nofog=1`, `notex=1`, `neutral=1` na spec (ou F5–F10).
- Normal invertida / buraco na malha: quase sempre ordem dos vértices; desligue o culling
  temporariamente para confirmar.
- Sombra com listras (acne) ou descolada (peter-panning): ajuste o viés em `shadowFactor` e o
  `glPolygonOffset` em `Renderer`; lembre que o raio do mapa muda com a distância da câmera.
- `viewer --info` imprime fornecedor, renderer e versão do OpenGL.

## Medição

```bash
viewer/build/install/viewer/bin/viewer --bench 1920x1080
```

Renderiza 240 quadros da cena de cada direção, offscreen, sem vsync, `glFinish` por quadro, SSAA
1,5×; imprime média, mediana e p95 em ms, além de draws e triângulos após o descarte por frustum.
Opções: `LxA`, `ssaa=1.0`, `noshadow`, `prepass` (separadas por vírgula). Registre a máquina junto com o número (`docs/validation/resultados.md`). Não cite FPS que não saiu
desse comando ou do contador do HUD.

## Limites atuais (não presuma que existem)

Sem greedy meshing, sem instancing por GPU, sem LOD, sem SSAO, sem cascatas de sombra, sem
transparência ordenada (só a água). Ver ADR 0005.
