# 0014 — Malha conforme: fusão de faces sem T-junctions e sem sobreposição (rodada 08.1)

**Status:** aceita (rodada 08.1). Substitui a margem anti-fresta do [ADR 0010](0010-fxaa-e-margem-anti-fresta.md); mantém a
fusão exata do [ADR 0007](0007-fusao-exata-de-faces.md).

## Contexto

- **Fusão gulosa (0007):** junta faces coplanares iguais em retângulos. Onde um retângulo grande encosta em
  vários pequenos, o vértice do pequeno fica no **meio da aresta** do grande: uma T-junction. As duas arestas não
  compartilham vértices, e a rasterização deixava pixels sem cobertura (pontos claros no gramado, rodada 03).
- **Margem (0010):** cada quad fundido passou a avançar 0,06 voxel sobre os vizinhos. As frestas sumiram, mas
  faces **coplanares de cores diferentes passaram a se sobrepor** numa faixa de 0,12 voxel. Ali a profundidade
  empata, e qualquer movimento mínimo da câmera troca a vencedora: z-fighting.
  - De longe (capturas do viewer), a faixa tem menos de um pixel.
  - De perto (câmera do jogo, 3–5 u), tem vários pixels e "treme": reboco × madeira na casa, olhos e boca nas
    placas faciais, bordas dos degraus do T1.
- **Recuo das faces maiores (experimento da 08):** tirou o tremor da casa, mas reabriu frestas no terreno
  (pontos claros na fachada: 5 → 93). Abandonado.

## Decisão

Malha **conforme** em `VoxelMesher.buildDataMerged`:

1. **Coleta:** primeiro coleta todos os quads fundidos e o conjunto dos seus vértices, em coordenadas inteiras
   da grade (comparação exata).
2. **Arestas:** em cada aresta de cada quad, insere os vértices da malha que caem no seu interior: são as
   T-junctions, venham elas de quads coplanares ou perpendiculares (quina de degrau).
3. **Triangulação** do polígono resultante (convexo, com pontos colineares nas arestas):
   - leque a partir de um canto cujas duas arestas não receberam vértice, sem triângulo degenerado;
   - na falta desse canto, leque a partir do centro do quad.
4. **Atributos:** quad fundido tem cor, material e AO uniformes, então os vértices novos herdam os mesmos
   valores e a imagem não muda.
5. **Quads sem T-junction** continuam com os 2 triângulos de sempre.
6. **Margem 0:** toda aresta compartilhada tem os mesmos vértices nos dois lados, o que é estanque pela regra
   de rasterização do OpenGL. Não sobra fresta nem sobreposição.

`-Dvx.mesh=legado` reproduz o comportamento antigo (sem conformidade + margem 0,06), para comparação e para o
teste de regressão.

## Invariantes do mesher (testados em `MeshSeamTest`)

1. **Cobertura exata:** todo ponto de toda face visível é coberto por exatamente um triângulo do mesmo plano e
   normal, inclusive a 0,02 voxel da borda.
2. **Estanque:** em superfície fechada, toda aresta dirigida tem a oposta na mesma quantidade (sem T-junction).
3. **Área:** a mesma da malha de referência (um quad por face).
4. **Contenção:** nenhum vértice sai da face que representa (sem margem nem sobreposição).
5. **Regressão:** o modo legado precisa falhar 1 e 2, o que prova que o teste pega o defeito.

## Consequências

- **Imagem:** a malha fundida passa a renderizar praticamente igual à malha sem fusão. Na referência 16, 0,039%
  dos pixels diferem da malha sem fusão, contra 7,6% no modo legado.
- **Geometria:** mais triângulos onde há T-junctions (números em
  `docs/validation/relatorio-rodada-08-1.md`); quads lisos não mudam.
- **Referências canônicas:** mudam nas bordas entre cores e nas silhuetas, porque a faixa sobreposta e as
  pontas de 0,06 voxel saíram. Atualizadas com auditoria (as anteriores ficam em
  `docs/art/reference/anteriores/r08/`).
- **O que não muda:** o serrilhado de detalhe menor que um pixel, como os degraus de morros distantes, continua
  igual ao da malha sem fusão. É aliasing da geometria, não defeito do mesher.
