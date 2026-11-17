# Pipeline de renderização

OpenGL 3.3 core. Um único caminho de código serve às quatro direções; o que muda é o
[`StyleProfile`](../../viewer/src/main/java/vx/viewer/StyleProfile.java) e os assets.

## Passos por quadro (`Renderer.render`)

1. **Sombra** — mapa de profundidade 2048², projeção ortográfica do sol centrada no foco (herói ou
   modelo). O raio coberto acompanha a distância da câmera (1,5 × distância, entre 6 e 26 u na cena;
   2,2 u ou mais na inspeção), então personagens em close têm sombra nítida; perto da borda do mapa a
   sombra desvanece em vez de ser cortada. O centro é preso à grade de texels do mapa para a sombra não
   tremer com o movimento. `glPolygonOffset` + viés dependente do ângulo evitam acne.
2. **Céu + cena** em FBO HDR (`RGBA16F` cor, `RGBA8` normal em espaço de câmera, profundidade 24 bits),
   renderizado com supersampling (1,5× no interativo, 2× nas capturas). O céu é um triângulo de tela
   cheia; depois vêm os opacos e, por último, os transparentes (água) com mistura.
3. **Bloom** (se o estilo pedir) — extração de brilho acima do limiar, 1/4 da resolução, dois pares
   de desfoque gaussiano separável.
4. **Composição** (`post.frag`) no alvo final: contorno por descontinuidade de profundidade e de
   normal (direção B), soma do bloom, exposição, ombro suave só nas altas luzes (preserva as cores
   de paleta nas médias), matiz, saturação, contraste, vinheta, gama 2,2. O downsample do SSAA
   acontece aqui.
5. **HUD** por cima, em pixels.

## Formato de vértice

Um layout para tudo (`Mesh.SCENE_LAYOUT`, 13 floats): posição (3), normal (3), cor sRGB (3),
AO (1), material (1), UV (2). Voxels usam cor por vértice; cuboides texturizados usam UV e guardam
na cor a média da face, que aparece no modo "sem texturas".

## Iluminação (`scene.frag`)

Tudo em espaço linear. Por fragmento:

- **Difusa do sol** com *wrap* configurável (0 = Lambert) × sombra PCF (3×3 ou 5×5 amostras com
  comparação por hardware; raio em texels por estilo).
- **Ambiente hemisférico**: mistura céu/chão pela normal, multiplicado pelo AO de vértice.
- **Sombreamento fixo por face** (`uFaceShade`): escurece um pouco as faces ±X, ±Z e −Y. É o que dá a
  leitura "voxel clássica" de cubos mesmo dentro da sombra.
- **Matiz de sombra** (`uShadowTint`): cor somada onde o sol não chega (sombras arroxeadas em B).
- **Materiais**: metal (especular + reflexo do céu por Fresnel), emissivo (ignora sombra, alimenta o
  bloom), folhagem (luz que atravessa, balanço no vertex shader), água (ondas em grade de 1/8 u para
  manter a leitura voxel, Fresnel, brilho).
- **Rim light** opcional e **névoa** exponencial com atenuação por altura.

### Oclusão de ambiente

Calculada no mesher (`VoxelMesher`), técnica clássica de 3 vizinhos por vértice, com inversão da
diagonal do quad para a interpolação ficar simétrica. Custo zero em tempo de execução. Não há SSAO:
o contato entre modelos diferentes (personagem × chão) é resolvido pelo mapa de sombra.

## O que cada direção liga

| | A | B | C | D |
|---|---|---|---|---|
| Sombra PCF | 3×3, dura | 5×5, larga | 3×3, dura | 5×5, macia |
| Wrap difuso | 0 | 0,35 | 0 | 0,12 |
| Sombreamento por face | forte | leve | forte | leve |
| Matiz de sombra | — | roxo | — | azul |
| Rim light | — | quente, leve | — | azulado |
| Especular em metal | médio | baixo | — | alto |
| Bloom | — | leve | — | sim |
| Contorno | — | sim | — | — |
| Vento na folhagem | — | sim | — | sim |
| Translucidez de folhagem | — | — | — | sim |
| Texturas | — | — | nearest | — |

Os valores exatos estão em `StyleProfile`; a tabela descreve o código no fechamento da rodada.

## Decisões de desempenho

- **Sem greedy meshing**: cada face de voxel é um quad. Preserva variação de cor por voxel e AO
  correto, e o volume desta rodada cabe com folga (medições em
  [../validation/resultados.md](../historico/rodada-01-direcoes/resultados.md)). É a primeira otimização a fazer quando
  houver mundo em chunks — ver [ADR 0005](../decisions/0005-tres-escalas-de-voxel-sem-greedy.md).
- **Instâncias por malha compartilhada**: as ~95 árvores usam 5–6 malhas; não há instancing por GPU
  (um draw por objeto), o que é suficiente para algumas centenas de objetos.
- **Descarte por frustum** (esfera envolvente) nos passos de sombra e de cena.
- **Pré-passo de profundidade**: implementado e medido; não trouxe ganho nesta GPU (o custo é de
  vértices, não de overdraw) e fica **desligado** por padrão (`--bench ...,prepass` para comparar).
- **Mapa de sombra 2048²**: 4096² custava ~4,5 ms a mais por quadro sem diferença visível.
- **Terreno** é uma malha única por estilo, gerada na carga a partir do relevo analítico.
- Não há LOD, oclusão, nem streaming: fora do escopo de uma cena fechada de 48 × 48 u.

## Malha: fusão conforme (rodada 08.1)

`VoxelMesher.buildDataMerged` funde faces iguais (ADR 0007) e, desde a 08.1, gera **malha conforme** (ADR 0014):
- **Arestas:** cada quad fundido recebe nas arestas os vértices da malha que caem nelas (fim das T-junctions).
- **Margem:** zero, então nenhuma face sobrepõe a vizinha. Fim do z-fighting e das frestas.
- **Invariantes** (cobertura exata, estanque, área, contenção) em `MeshSeamTest`.

Diagnóstico:
- **Modo antigo:** `-Dvx.mesh=legado`.
- **Controle sem fusão:** `-Dvx.merge=false`, malha exata de referência.
- **Sem vento:** `-Dvx.wind=0`, para isolar defeito de malha de movimento de folhagem.
- **Métrica temporal:** `tools/temporal/alternancia.py` conta pixels que vão e voltam (A→B→A) em câmera lenta. É
  o sintoma de z-fighting e separa tremor de deslocamento normal.

Para investigar um defeito parecido, compare as três malhas na mesma câmera:
- se a **conforme** se comporta como a **sem fusão**, o defeito não é do mesher (é modelo, aliasing ou shader);
- se ela se comporta como a **legada**, é.

## Depuração

- `F5`–`F10` isolam sombra, AO, névoa, pós, texturas e luz.
- `--snap ...,noshadow=1,noao=1,nopost=1,nofog=1` reproduz o mesmo offscreen.
- `--info` imprime fornecedor/renderer/versão do OpenGL.
- Erros de compilação/link de shader abortam com o log do driver.

## Rodada 02

- **Fusão exata de faces** no mesher (`buildDataMerged`) — [ADR 0007](../decisions/0007-fusao-exata-de-faces.md).
- **Vento afim** (`uAffineWind`): inclinação do objeto inteiro proporcional à altura; permite fundir folhagem.
- **Luz de preenchimento** sem sombra (`uFillDir`, `uFillColor`): corrige costas escuras.
- **Névoa** com início relativo ao foco (`fogStart + 0,8 × distância da câmera`), teto (`uFogMax`) e
  dispersão quente na direção do sol (`uFogSunColor`).
- **Contorno com peso por desenho**: `Draw.outline` vai para o alfa do buffer de normais; o pós usa o peso
  da amostra mais próxima da câmera; pesos por material (folhagem), categoria (terreno, plantas) e
  distância (`uOutlineFade`), e peso de normal configurável (`uOutlineNormal`).
- **Sombra Poisson** de 12 amostras (`uShadowTaps = 4`), metade do custo do PCF 5 × 5.
- **Silhueta** (`uSilhouette`) e **argila** (`uClay`) para QA.
- Valores por preset em [../art/presets-graficos.md](../art/presets-graficos.md).
