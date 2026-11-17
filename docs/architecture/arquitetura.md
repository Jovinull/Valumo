# Arquitetura

## Módulos

```
engine  (vx.engine)   biblioteca: não conhece estilos, personagens nem o jogo
  Window        janela GLFW, contexto OpenGL 3.3 core, estado de teclado/mouse por quadro
  Shader        programa GLSL do classpath, cache de uniforms
  Mesh          VAO/VBO de triângulos não indexados, atributos float intercalados, caixa delimitadora
  VoxelSource   interface de volume (voxel -> cor + material)
  VoxModel      leitor de .vox (MagicaVoxel), conversão de eixos, material por faixa de paleta
  VoxelMesher   volume -> faces visíveis com oclusão de ambiente por vértice
  Texture       textura RGBA8 (nearest para pixel art)
  Framebuffer   FBO com N anexos de cor + profundidade
  Camera        câmera orbital
  Hud           retângulos e texto em fonte bitmap 5x7 embutida
  Screenshot    leitura do framebuffer -> PNG

viewer  (vx.viewer)   aplicativo desta rodada
  Main          linha de comando: interativo, --snap, --review, --bench, --info
  App           estado (State), modos de avaliação, entrada, HUD, captura, benchmark
  StyleProfile  parâmetros de luz/céu/névoa/pós de cada direção (A, B, C, D, neutra)
  Renderer      sombra -> cena HDR -> bloom -> composição; descarte por frustum
  Assets        carga e cache de rigs e props
  CharacterModel / CuboidMesh   rig.json -> partes (malha voxel ou cuboides texturizados)
  Animator / Actor              clipes procedurais, pose, hierarquia, transição
  World         clareira: relevo analítico, terreno por estilo, layout JSON, criaturas, água, nuvens
  Review        pacote de capturas e pranchas de comparação
```

A dependência é de mão única: `viewer -> engine`. Bibliotecas externas: LWJGL 3.4.3 (GLFW, OpenGL),
JOML 1.10.8, Gson 2.13.2; JUnit 5 nos testes. Ver [ADR 0001](../decisions/0001-stack-java-lwjgl.md).

## Fluxo de dados

```
tools/*.py  --build_assets.py-->  assets/**/*.vox, rig.json, props.json, *.png
                                        |
                       Assets / CharacterModel / World   (carga única, cache)
                                        |
        State (estilo, modo, personagem, clipe, tempo, câmera, opções)
                                        |
                 App.buildFrame -> Renderer.Frame (lista de Draw)
                                        |
                              Renderer.render -> janela ou FBO de captura
```

`App.State` contém tudo o que define uma imagem. O modo interativo altera o `State` pela entrada; o
modo `--snap` o preenche a partir de `chave=valor`. Por isso **toda captura é reproduzível** e usa
exatamente o mesmo código do modo interativo.

## Sistema de transformações

- Mundo: Y para cima, mão direita, unidades `u` (≈ altura de um herói).
- Cada malha já é construída em unidades de mundo (voxel × `voxelSize`) e no espaço do modelo, com a
  origem no ponto de apoio (x,z centrados, y=0 no chão).
- Personagens: cada parte tem um pivô. A pose guarda, por parte, rotação Euler XYZ, deslocamento (em
  alturas do personagem) e escala. A matriz final é
  `M_parte = M_pai · T(pivô + desloc) · Rz·Ry·Rx · S · T(−pivô)`, com `M_raiz = M_mundo · T · R · S` da raiz.
  Achatar/esticar (squash & stretch) é uma escala na raiz, com os pés como ponto fixo.
- Props: `T(posição no terreno) · Ry(rotação)`. Construções usam múltiplos de 90° para manter a grade.

## Câmera

Orbital: alvo + yaw + pitch + distância, perspectiva. Na cena, o alvo segue o herói; nos modos de
inspeção, o enquadramento se ajusta ao tamanho do modelo (`camAuto`) até o usuário mexer no zoom. O
estudo de proporções usa FOV estreito (20°) e distância grande para reduzir a distorção de perspectiva.

## Carregamento de assets

Ver [assets.md](assets.md) e o [contrato](../art/contrato-de-assets.md). Resumo: `.vox` por parte
+ `rig.json` (personagens/criaturas), `.vox` + `props.json` (ambiente), cuboides + atlas PNG (direção
C), `scenes/clearing.json` (disposição). Tudo é lido uma vez e mantido em cache; árvores e plantas
são instâncias da mesma malha.

## Testes

`./gradlew test` roda sem contexto gráfico: geometria do mesher (`VoxelMesherTest`), leitor `.vox`
(`VoxModelTest`) e contrato dos assets gerados (`AssetContractTest`: estrutura dos rigs, partes
obrigatórias, heróis entre 2 e 3 cabeças, manifestos de props, determinismo do relevo). A validação
visual é feita por capturas — ver [../validation/](../validation/).

## Rodada 02 (acréscimos)

- `App.Mode.LINEUP`: comparação em linha de qualquer lista de rigs sob a mesma luz/câmera (`App.LINEUPS`).
- `App.State`: `preset`, `outlineLevel`, `fogLevel`, `expr`, `lineup`, `quality`, `heroDir`, `metrics`.
- `Review2`: pacote determinístico da rodada 02 (`--review2 [seções]`).
- `Animator.secondary`: modos de arma, manto em metades, capa com restrição, postura, trança.
- `CharacterModel.Part.alts` + `Actor.autoExpression`: expressões.
- `App.bench`: repetições com aquecimento, média, mediana, p95, 1% low, draws, triângulos, malhas,
  MB de vértices, heap; CSV opcional.
- Testes novos: fusão de faces (`VoxelMesherTest`), contrato da E (`AssetContractTest`).
