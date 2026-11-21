---
name: rendering
description: Mexer no renderer (Java/GLSL), perfis de luz e presets da direção E, névoa, contorno, sombras, materiais, mesher com fusão exata de faces e benchmarks reproduzíveis (média, mediana, p95, 1% low, draws, triângulos, memória). Use ao alterar StyleProfile, shaders, Renderer, VoxelMesher ou ao medir desempenho.
---

# Renderização e desempenho

Referências: `docs/architecture/renderizacao.md`, `docs/art/presets-graficos.md`,
`docs/decisions/0007-fusao-exata-de-faces.md`. Código: `viewer/.../StyleProfile.java` (presets),
`Renderer.java`, `engine/src/main/resources/shaders/{scene,post,sky}.*`, `engine/.../VoxelMesher.java`.

## Princípios

- Cada efeito justifica sua presença com captura antes/depois **em luz neutra também**; nenhum efeito
  esconde defeito de modelo.
- A E tem três presets (`dia`, `tarde`, `neutra`) e níveis comparáveis de névoa (0–3) e contorno (0–3).
  Mudou um parâmetro → atualize a tabela de `presets-graficos.md`.
- Névoa começa depois do foco (`fogStart + 0,8 × distância`), com teto; contorno discreto por padrão
  com peso por desenho/material; luz de preenchimento por trás sempre ligada.
- Direções A–D são referência histórica: não mude o visual delas sem necessidade (os parâmetros novos
  têm padrões neutros para elas).
- **Props sob demanda (ADR 0030):** `Assets.props(estilo)` registra o `props.json` inteiro, mas só malha o que a cena
  pede pelo nome (`PropSet.get`).
  - Não itere `values()`/`entrySet()` do catálogo: isso carregaria a biblioteca inteira.
  - Para medir, use `Assets.loadedProps` e as malhas vivas do roteiro `tools/menu/14-memoria.txt`.

## Benchmark (sempre assim)

```bash
B=viewer/build/install/viewer/bin/viewer
$B --bench "1920x1080,style=bde,runs=3,frames=300,out=output/tmp/bench.csv"      # nativo
$B --bench "1920x1080,style=e,ssaa=1.5,runs=3,frames=300"                         # SSAA
$B --bench "1920x1080,style=e,quality=baixa|padrao|alta,outline=0..3,fog=0..3,preset=tarde"
JAVA_OPTS="-Dvx.merge=false" $B --bench "1920x1080,style=e"                       # sem fusão
```

Uma execução de aquecimento (**400 quadros** desde a rodada 06.1; `warm=N` muda) é descartada. Com 90, a
Iris Xe ainda estava subindo de frequência (~550 → ~1 100 MHz em ~3 s de carga) e a 1ª execução medida
inflava o p95 — mais em cenas LEVES, que demoram mais a acordar o regulador (era o "p95 alto da vitrine";
evidência em `output/visual-review/round-06-1/performance/`). Para investigar picos: `dump=arquivo.csv`
grava tempo por quadro, CPU até o envio e GC por quadro; leia `/sys/class/drm/card1/gt_cur_freq_mhz` durante
o bench. Pico que só aparece na 1ª execução e não se repete no mesmo ângulo = aquecimento, não conteúdo.
Reporte média, mediana, p95 e 1% low **do total**,
mais draws, triângulos (cena e sombra), malhas e MB de vértices. Não rode outra coisa na GPU durante a
medição. Compare só números medidos na mesma máquina e na mesma sessão. Alvo: ≥ 60 FPS em 1080p nativo
na Iris Xe (E: ~100 FPS medidos na rodada 02).

## Onde está o custo (rodada 02)

Triângulos (resolvido com fusão exata + vento afim nos assets da E), PCF de sombra em SSAA (Poisson 12
em vez de 5 × 5), contorno ~0,7 ms. Antes de otimizar outra coisa, meça com `--bench` isolando o fator.

## Depuração

`noshadow=1 noao=1 nopost=1 fog=0 outline=0 sil=1 notex=1` nas capturas; `F5`–`F10`, `O`, `L`, `K`, `M`
(métricas: draws, triângulos, malhas, MB, resolução) no interativo.

## Rodada 03

- Qualidade PADRÃO = FXAA (`post.frag`, `uFxaa`); ALTA = SSAA 1,5×; bench com `fxaa=0` desliga.
- Fusão de faces: **margem 0** desde o ADR 0014 (malha conforme sem T-junctions; `MeshSeamTest`). A margem anti-fresta
  de 0,06 voxel (`VoxelMesher.crackMargin`) só existe com `-Dvx.mesh=legado`, para comparar.
- Relevo da E: `World.E_TERRAIN` = 1 (T1); `terrain=0..4` para estudo. Malha nova gerada à mão
  (ex.: chanfro): confira a **ordem dos vértices** (normal = produto vetorial) — a ordem invertida
  some por backface culling sem erro nenhum.
- Bench reproduzindo a rodada 02 no mesmo binário: `JAVA_OPTS=-Dvx.assets=output/visual-review/round-03/snapshot-r02`
  + `terrain=0,fxaa=0`. Rode antes/depois **intercalados** e registre a carga da máquina.

## Convenções OpenGL (transferidas da skill `java-rendering` da rodada 01)

- Contexto 3.3 core, `GL_CULL_FACE`, faces **anti-horárias** vistas de fora, `GL_LEQUAL`.
- Cores de asset em sRGB; o shader lineariza, ilumina em HDR linear e o `post.frag` aplica gama. Cores de
  luz em `StyleProfile` via `lin("#rrggbb", intensidade)`.
- Unidades de textura da cena: 0 = sombra (`sampler2DShadow`), 1 = atlas.
- **Uniform inexistente é ignorado em silêncio** (`Shader.set`): parâmetro que "não faz nada" → confira o nome.
- `Mesh`, `Texture`, `Framebuffer`, `Shader` são `AutoCloseable`. Erro de GLSL aborta com o log do driver.
- Limites atuais (não presuma que existem): sem instancing, sem LOD, sem cascatas de sombra, água plana.

## Rodada 05

- Passe de **overlay** (`Renderer.Draw.overlay()`, sem teste de profundidade) para depuração (`dbg=1`).
- Bench comparável à rodada anterior: mesmo binário, mesma spec, execuções **intercaladas**; tokens
  `act=`, `party`, `legacy`, `far=0`, `dbg`, `fxaa=0`; a coluna de animação (µs/quadro) mede o custo do
  `Animator`. Resultados da rodada 05: `output/visual-review/round-05/performance/`.
- Referências de luz e terreno: `docs/art/reference/17–21`; o `tools/reference.sh` deve sair idêntico
  pixel a pixel na mesma máquina — se não sair, algo no renderer mudou.

## Rodada 05.1

- Presets, qualidade e procedimento de benchmark **inalterados** (fonte: `docs/art/presets-graficos.md`).
  Comparação com a rodada anterior: `tools/bench5.sh` (mesmas specs; controle `r03-equivalente`).
- Duas sondas sem render que complementam as capturas: `viewer --occlusion` (projeção ortográfica dos
  volumes em 8 câmeras) e `viewer --grip` (orientação do item na mão). Não substituem o vídeo.
- Partes novas no rig do aventureiro (`pack`, `scarf`) somam 2 malhas por herói; medir com `--bench`.

## Rodada 06

- Tipo de animação novo `flutter` (mariposa) e chave `layout=` (composições alternativas) — renderer, luz e
  presets inalterados. Benchmark da vitrine e custo por categoria: `output/visual-review/round-06/performance/`.

## Rodada 08.1 — malha conforme (fim do z-fighting e das frestas)

- **Nunca sobreponha faces coplanares** para tapar fresta: a margem da rodada 03 causava tremor de perto.
- **Nunca recue faces** para desempatar: a tentativa da 08 reabriu frestas.
- O mesher funde e insere nas arestas os vértices da malha que caem nelas, o que elimina as T-junctions
  (ADR 0014). Invariantes em `engine/src/test/java/vx/engine/MeshSeamTest.java`: cobertura exata, estanque, área
  e contenção. O modo legado precisa falhar.
- **Defeito de cintilação ou fresta:** compare as três malhas na mesma câmera.

  | Malha | Opção | Quando ela é o "certo" |
  |---|---|---|
  | conforme | padrão | — |
  | legado | `-Dvx.mesh=legado` | — |
  | sem fusão | `-Dvx.merge=false` | é a verdade geométrica |

  - Conforme ≈ sem fusão: o problema é do modelo, do shader ou serrilhado, não do mesher.
  - Use `-Dvx.wind=0` para tirar o vento.
- **Medição temporal**, não captura estática:
  - roteiros em `tools/temporal/*.txt`, câmera lenta;
  - `tools/temporal/alternancia.py` conta pixels A→B→A por quadro e gera mapa de calor;
  - em câmera rápida a métrica conta o próprio movimento: use vídeo lado a lado;
  - pacote completo: `tools/review81.sh`.
- **Mudou o mesher?** As referências canônicas mudam nas bordas. Siga o procedimento da skill `visual-qa`
  (preservar, comparar, inspecionar, justificar). Nunca atualize em massa para "ficar verde".

## Rodada 12.3 — recorte contextual (`uCut`, `scene.frag`)

- **O que marca:** `Renderer.Draw.cut` (peso 0–1) marca só os desenhos de uma construção (`World.cutaway`: nome do prop +
  posição); `Frame.cutTarget` / `cutRadius` dão o alvo (peito do herói) e o raio.
- **O que o shader faz:** descarta o fragmento ANTES do alvo (até 0,3 u da frente dele) dentro de um **cone com vértice
  na câmera** (`k = d / (R · uCut · t)`), com miolo `k < 0,75` e borda pontilhada Bayer 4×4.
  - **Cone, não cilindro:** na tela o cone é um disco de tamanho constante. O cilindro abria um buraco enorme no que
    estava perto da câmera.
- **O que não muda:**
  - **Sombra:** é outro passo; a construção continua fazendo sombra inteira.
  - **Pré-passo de profundidade:** pula os desenhos recortados.
  - **Os outros desenhos:** não pagam nada. O recorte só existe na variante `VX_CUT` (`new Shader(vert, frag, "VX_CUT")`,
    `Renderer.sceneCut`); com um `if` uniforme no shader de todos, a vila pagava +0,17 ms de GPU no passo de cena.
- **Regra geral:** recurso de shader que vale para poucos desenhos vai numa variante por `#define`, nunca num `if` no
  shader de todos. Meça o passo de cena antes e depois, intercalado.
- **Revisar:** em vídeo, não em captura parada (a borda pontilhada com o contorno forma um anel fino: P96). Custo
  medido com os dois binários intercalados (`tools/review123.sh desempenho`, roteiro `tools/mundo/desempenho-torre.txt`).
