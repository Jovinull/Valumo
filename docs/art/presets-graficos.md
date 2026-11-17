# Presets gráficos (direção E)

Fonte: `StyleProfile.e(preset)`, `withOutline`, `withFog`; qualidade em `App.Quality`. Todos
reproduzíveis por captura (`preset=`, `outline=`, `fog=`, `quality=`) e por tecla (`L`, `O`, `F7`, `P`).

## Presets de luz

| Parâmetro | dia (padrão) | tarde | neutra |
|---|---|---|---|
| Sol (dir. para o sol) | (−0,50, 0,70, 0,52) | (−0,64, 0,42, 0,64) | (−0,4, 0,8, 0,6) |
| Sol (cor × força) | `#fff0d4` × 1,26 | `#ffd9a6` × 1,40 | branco × 0,95 |
| Céu / chão (ambiente) | `#b6cbef` × 0,56 / `#c8b08c` × 0,40 | `#9fb6ee` × 0,62 / `#c8a07c` × 0,34 | branco × 0,60 / 0,45 |
| Preenchimento (por trás) | `#a9b9e6` × 0,14 | `#8fa8e8` × 0,18 | branco × 0,22 |
| Matiz de sombra | `#5a50a0` × 0,06 | `#5a5aa8` × 0,045 | — |
| Rim | 0,16 | 0,30 (`#ffcf98`) | — |
| Névoa: cor / densidade / início / teto | `#a9c1da` / 0,024 / 10 + 0,8·dist / 0,42 | `#c9c3d8` / 0,024 / 10 + 0,8·dist / 0,40 | — |
| Céu: zênite / horizonte | `#5f93dc` / `#d3e2ec` | `#6f8fd0` / `#ffd3a6` | cinza de estúdio |
| Pós: saturação / contraste / tint | 1,07 / 1,06 / neutro | 1,06 / 1,06 / (1,025; 1; 0,985) | 1 / 1 / — |

**Tarde (rodada 03).** A versão da rodada 02 deixava a vegetação **oliva**: luz laranja + ambiente e
sombra roxos + tint que cortava o azul empurravam o verde para o cinza-amarelado. A correção foi de
luz, não de saturação: céu ambiente azul (sombras verde-azuladas), matiz de sombra 0,085 → 0,045,
tint quase neutro e rim quente mais forte. Medido no mesmo enquadramento: matiz mediano das copas
69,8° → 86,5°, gramado 81° → 88°, saturação 0,50 → 0,45 (não subiu). Evidência:
`round-03/iluminacao/tarde-antes-depois.png`.

**Neutra** existe para julgar modelo e cor: sem névoa, sem matiz de sombra, sem pós — se um asset só
fica bonito no "dia" ou na "tarde", ele tem defeito.

## Névoa

`fog = 1 − exp(−max(d − início, 0) · densidade · altura)`, limitada ao **teto** e somada a um
brilho quente na direção do sol. O início é **relativo ao foco** (10 u + 0,8 × distância da câmera):
na câmera de exploração (7 u) começa a ~15,6 u; no panorama (26 u), a ~31 u. Assim o plano do herói
nunca fica velado e só o fundo ganha profundidade — o defeito da B era névoa desde a câmera.

| Nível (`F7`) | Efeito | Uso |
|---|---|---|
| 0 | desligada | depuração, inspeção de cor |
| 1 | densidade × 0,5, início × 1,4 | cenas pequenas |
| **2** | padrão | **escolhido** |
| 3 | densidade × 1,9, início × 0,55, teto + 0,25 | clima (dia nublado) |

## Contorno a tinta

| Nível (`O`) | Peso | Largura | Normal | Folhagem | Terreno | Comentário |
|---|---|---|---|---|---|---|
| 0 | — | — | — | — | — | |
| **1 (padrão)** | 0,42 | 1,1 | 0,10 | × 0,18 | × 0,30 | separa personagem e objetos sem riscar tufos |
| 2 | 0,62 | 1,3 | 0,18 | × 0,40 | × 0,55 | começa a riscar degraus |
| 3 | 0,75 | 1,4 | 0,28 | × 1 | × 1 | contorno original da B |

O peso é por desenho (gravado no alfa do buffer de normais) e por material: plantas < 0,7 u recebem
× 0,25. A borda pertence ao objeto mais próximo da câmera. Custo medido (1080p nativo, 3 × 300
quadros): sem contorno 9,10 ms; discreto 9,77 ms; forte 9,72 ms → **~0,7 ms**, independente do nível
(`round-02/performance/bench-e-contorno-*.txt`).

## Qualidade (`P`)

| Qualidade | SSAA | AA de pós | Sombra | Bloom |
|---|---|---|---|---|
| BAIXA | 1,0× | — | PCF 3 × 3 | desligado |
| **PADRÃO** | 1,0× | **FXAA** (rodada 03) | Poisson 12 amostras | 0,07 |
| ALTA | 1,5× | — | PCF 5 × 5 | 0,07 |

FXAA (`post.frag`, `uFxaa`): variante compacta de passe único sobre a imagem final (contraste de
luma com limiar max(0,04; 0,125 × luma máx.), direção pelo gradiente dos 4 cantos, 2 + 2 amostras). Alisa os degraus diagonais de voxel sem amolecer as faces
planas; custo medido no relatório da rodada 03. É a alternativa leve ao SSAA 1,5× (que custa ~2,2×
os pixels). Prancha: `round-03/iluminacao/antisserrilhado-nenhum-fxaa-ssaa.png`.

## Relevo (rodada 03)

`World.E_TERRAIN` (e `terrain=0..4` na captura/bench): T0 degraus seguindo curvas de nível (rodada 02);
**T1 limiar com ruído suave (padrão)**; T2 patamares + rupturas de pedra; T3 = T1 + chanfros; T4 =
T2 + chanfros. O chanfro é uma malha complementar: rampa de 0,6 voxel sobre cada degrau de
**exatamente 1 voxel** em trechos retos de ≥ 3 colunas (`-Dvx.chamferMinRun`), com tampas nas pontas;
degraus maiores ficam verticais. Não é o padrão: com trechos curtos gera "dentes" e, sem eles, quase
não difere do T1 (ver `docs/historico/experimentos/rodada-03-estudos.md` §5). Margem anti-fresta dos quads fundidos:
`VoxelMesher.crackMargin` = 0,06 voxel (cobre os pontos claros de T-junction).

Medições em [../validation/relatorio-rodada-02.md](../historico/relatorios/relatorio-rodada-02.md#6-performance).
As capturas de revisão usam sempre SSAA 2× (independente da qualidade).

## Revisão dos parâmetros gráficos

| Item | Situação |
|---|---|
| Gama / espaço de cor | conversão sRGB→linear no shader (pow 2,2) e de volta no pós; consistente |
| Exposição / tone map | ombro suave só nas altas luzes; preservado |
| Bloom | limiar 1,3 e força 0,07: só emissivos; metal não estoura (era o problema da D) |
| Sombras | Poisson 12 (padrão); mapa 2048² |
| AO | por vértice no mesher, 0,62 |
| Contorno | por desenho/material/distância (acima) |
| Filtragem | voxels sem textura; cuboides nearest |
| Aliasing | SSAA 1,5× na ALTA; **FXAA na PADRÃO** (rodada 03); nenhum na BAIXA |
| Frestas de T-junction | margem de 0,06 voxel nos quads fundidos (rodada 03) |
| Costas escuras | luz de preenchimento por trás em todos os presets |
| Tonalidade de sombra | roxo leve (dia 0,06; tarde 0,045) |
