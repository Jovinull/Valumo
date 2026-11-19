# 0010 — FXAA na qualidade padrão e margem anti-fresta (rodada 03)

**Estado:** aceito pelo agente, sujeito à revisão do proprietário. Complementa o 0007. **A margem anti-fresta foi
substituída pelo [0014](0014-malha-conforme-sem-t-junctions.md)** (malha conforme, margem 0); o FXAA continua valendo.

## Contexto

1. A qualidade PADRÃO não tinha antisserrilhado (só a ALTA, com SSAA 1,5×, ~2,2× os pixels). A missão
   pediu investigar AA mais leve sem descaracterizar a arte.
2. Com a fusão exata de faces (0007), apareciam **pontos claros isolados** no gramado: frestas de
   T-junction entre quads fundidos de tamanhos diferentes (rasterização de arestas que não
   compartilham vértices). Só existiam com a fusão ligada.

## Decisão

- **FXAA** compacto de passe único em `post.frag` (`uFxaa`), ligado na qualidade PADRÃO.
- Cada quad fundido (w > 1 ou h > 1) avança **0,06 voxel** sobre os vizinhos
  (`VoxelMesher.crackMargin`). 0,01 voxel foi testado e não bastava (~¼ de pixel a 7 u); 0,06 cobre
  ~1 pixel e não altera a silhueta de forma perceptível.

## Consequências

- A área total de uma malha fundida cresce um pouco (sobreposição invisível); o teste de área compara
  sem margem e um teste novo garante a sobreposição.
- O custo do FXAA está medido em `output/visual-review/round-03/performance/`.
- MSAA não foi adotado: o pipeline usa FBOs com pós-processamento (contorno por normal/profundidade),
  que exigiria resolver múltiplas amostras em vários alvos.
