# Experimento 01 — Proporção dos personagens

**Pergunta:** quantas "cabeças" de altura deve ter o herói para ler como *pequeno aventureiro*, e não
como pessoa de proporção realista feita de cubos?

## Hipóteses
Ponto de partida do proprietário: 2,5 / 3 / 3,5 / 4 cabeças. A pesquisa bibliográfica
([../research/proporcoes.md](../pesquisa/proporcoes.md)) apontava 2,75–3,25 como faixa segura.
Acrescentamos 2,2 para ver o extremo compacto.

## Procedimento
- Um único construtor (`tools/chars_a.py`, função `adventurer(leg_h, torso_h)`) gera o mesmo
  aventureiro — mesma cabeça (10×9×9 voxels, 12 de largura com cabelo), mesma roupa, espada, escudo e
  ombreira — variando **apenas** a altura das pernas e do tronco (os braços acompanham o tronco).
- Cena `mode=prop`: mesma câmera (FOV 20°, 13,5 u de distância, quase frontal), mesma luz neutra,
  mesmo piso, parede-régua com faixas de 1 cabeça (10 voxels).
- Duas variantes: **mesmo voxel** (cabeça de tamanho fixo; o personagem cresce) e **mesma altura
  total** (`norm=1`; o voxel encolhe e a cabeça diminui), mais a vista de perfil.

| Chave | Pernas | Tronco | Altura total (voxels) | Cabeças | Pernas ÷ altura |
|---|---|---|---|---|---|
| p22 | 4 | 7 | 23 | 2,2 | 17% |
| p25 | 5 | 9 | 26 | 2,5 | 19% |
| p30 | 8 | 11 | 31 | 3,0 | 26% |
| p35 | 11 | 13 | 36 | 3,5 | 31% |
| p40 | 14 | 15 | 41 | 4,0 | 34% |

("Cabeças" = (pernas + tronco + 9 de crânio + 2 de cabelo) ÷ 10; a altura em voxels inclui os tufos.)

Reproduzir:

```bash
tools/snap.sh "mode=prop,neutral=1,size=2400x1100,out=output/tmp/prop.png" \
              "mode=prop,neutral=1,norm=1,size=2400x1100,out=output/tmp/prop-norm.png" \
              "mode=prop,neutral=1,myaw=90,size=2400x1100,out=output/tmp/prop-perfil.png"
```

Capturas de entrega: `output/visual-review/comparacao/proporcoes-mesmo-voxel.png`,
`proporcoes-mesma-altura.png`, `proporcoes-perfil.png`.

## Resultado
- **2,2 cabeças:** o mais "mascote". Carismático, mas o tronco de 7 voxels quase não tem área para
  roupa e equipamento, e os braços ficam curtos demais para um golpe de espada convincente.
- **2,5 cabeças:** melhor equilíbrio. Lê como aventureiro pequeno e decidido; há espaço para cinto,
  alça, cachecol; a espada (≈ 60% da altura) parece grande sem esconder o corpo.
- **3,0 cabeças:** já parece uma criança de proporção comum. Perde o encanto de miniatura; as pernas
  começam a dominar a silhueta.
- **3,5 e 4,0 cabeças:** o "humano de cubos" que motivou o projeto. Na variante de mesma altura total
  a cabeça encolhe e o rosto deixa de ler a distância de jogo.

## Decisão
Heróis entre **2,3 e 2,6 cabeças**, pernas ≤ 30% da altura ([ADR 0004](../../decisions/0004-proporcao-dos-personagens.md)).
A direção A usa 2,5 (pernas 5, tronco 9); cada direção documenta as suas medidas no próprio guia.

## Ressalvas
- A conclusão vale para cabeças de ~10 voxels. Com voxel mais fino (direção D) uma proporção um pouco
  mais alta ainda pode funcionar, porque há resolução para afinar cintura e membros.
- O estudo usa um único personagem. Silhuetas com manto longo (conjurador) escondem as pernas e
  toleram tronco mais comprido.
- É um julgamento de direção de arte feito sobre as capturas, não uma medida objetiva: a escolha
  final é do proprietário.
