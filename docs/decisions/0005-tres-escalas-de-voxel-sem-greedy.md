# ADR 0005 — Três escalas de voxel e malha sem greedy meshing

**Estado:** aceito para a rodada 01

## Contexto
Um único tamanho de voxel para tudo força uma escolha ruim: ou o personagem fica grosseiro (poucos
voxels), ou o mundo fica caro e sem leitura (voxels minúsculos).

## Decisão
- Três escalas declaradas por modelo (`voxelSize`): **terreno** (maior), **ambiente** (média) e
  **personagem/objeto de mão** (fina). Na direção A: 1/4 u, 1/8 u (1/16 u para objetos pequenos) e 1/24 u.
- O mesher emite **um quad por face exposta**, com cor e AO por vértice; sem fusão de faces.

## Por quê
- Voxels de terreno grandes ao lado de um personagem fino reforçam a leitura de "aventureiro pequeno
  num mundo grande" — o objetivo central da direção de arte.
- Sem greedy meshing, cada voxel pode ter cor própria (salpicos de terreno, rampas pintadas) e o AO
  fica correto. O volume da cena de validação cabe na GPU integrada usada nos testes.

## Riscos
- Misturar escalas pode parecer incoerente se a diferença for arbitrária: as razões são fixadas no
  contrato de assets e cada direção documenta as suas.
- O custo cresce linearmente com a área exposta. Um mundo em chunks vai precisar de greedy meshing
  (ao menos para faces de cor uniforme), LOD e instancing por GPU. Os números medidos estão em
  [../validation/resultados.md](../historico/rodada-01-direcoes/resultados.md).
