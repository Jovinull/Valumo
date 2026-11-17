# ADR 0007 — Fusão exata de faces e vento afim

**Estado:** aceito (rodada 02) — substitui a parte "sem greedy meshing" do [ADR 0005](0005-tres-escalas-de-voxel-sem-greedy.md)

## Contexto
O custo da cena é dominado por triângulos (B: 435 mil na cena + 710 mil na sombra; D: 1,2 mi + 1,7 mi).
O ADR 0005 evitava greedy meshing para preservar cor e AO por voxel.

## Decisão
- `VoxelMesher.buildDataMerged`: greedy meshing **exato** — só funde faces coplanares vizinhas com a
  mesma cor, o mesmo material e AO idêntico nos quatro cantos. Todos os atributos do quad fundido são
  constantes, então a imagem é a mesma (diferenças subpixel só na borda do contorno). Ligado por
  padrão; `-Dvx.merge=false` desliga para comparar. Testes de área e de orientação em `VoxelMesherTest`.
- Folhagem só é fundida se o asset declara `"affineWind": true`; nesse caso o vertex shader usa vento
  **afim** (o objeto inteiro inclina proporcionalmente à altura, fase por objeto), que deforma um quad
  grande exatamente como os pequenos — sem frestas.
- Assets da E são desenhados para fundir: terreno sem jitter (manchas de cor contínuas), sombreamento
  em bandas largas.

## Resultado medido (cena E, 1080p nativo, 3 × 300 quadros, Iris Xe)
| | Triângulos (cena) | MB de vértices | FPS médio |
|---|---|---|---|
| sem fusão | 1 030 510 | 146,5 | 65,5 |
| com fusão | 369 370 | 48,6 | ~101 |

Nos assets antigos (jitter por voxel) o ganho é só ~10%.

## Riscos
T-junctions podem gerar frestas de 1 pixel em ângulos rasantes (não observadas nas capturas). O vento
afim perde a "ondulação" por folha da B/D (troca consciente).
