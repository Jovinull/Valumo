# ADR 0004 — Proporção dos personagens: 2,3 a 2,6 cabeças

**Estado:** aceito para a rodada 01; a confirmar pelo proprietário

## Contexto
O problema que motivou o projeto: personagens que pareciam "pessoas grandes de proporção realista
feitas de cubos". A pesquisa bibliográfica ([../research/proporcoes.md](../historico/pesquisa/proporcoes.md))
sugeria 2,75–3,25 cabeças. O estudo no próprio viewer
([../experiments/proporcoes.md](../historico/experimentos/proporcoes.md)) comparou 2,2 / 2,5 / 3,0 / 3,5 / 4,0
com o mesmo personagem, câmera, luz e equipamento.

## Decisão
- Heróis com **2,3–2,6 cabeças** de altura total (cabeça medida com o cabelo).
- **Pernas ≤ 30%** da altura (na direção A: 5 de 26 voxels ≈ 19%).
- Cabeça mais larga que o tronco e mais estreita que o conjunto ombros+braços.
- Mãos e botas 1 voxel mais largas que o membro; olhos grandes na metade inferior do rosto, sem nariz.
- Equipamento ligeiramente exagerado: espada ≈ 60–65% da altura do corpo.
- `AssetContractTest.heroesAreCompact` trava a faixa em 2–3 cabeças.

## Por quê
No estudo, 2,2 e 2,5 leem como "pequeno aventureiro"; a 3,0 o personagem já parece uma criança de
proporção comum; a 3,5 e 4,0 volta o "humano de cubos". A faixa sugerida pela pesquisa ficou alta
demais para a nossa resolução de voxel (cabeça de 10): com tão poucos voxels, o tronco comprido
domina a leitura.

## Consequências
- Pernas curtas limitam a amplitude da passada: a corrida depende de inclinação do corpo, balanço e
  capa para parecer rápida.
- Armaduras pesadas precisam ser desenhadas para o corpo compacto (ombreiras e elmo carregam a
  silhueta; o tronco quase não tem área).
