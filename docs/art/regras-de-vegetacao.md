# Regras de vegetação (direção E)

Código: `tools/env_e.py` (`crown`, `canopy_paint`, árvores) sobre as primitivas de `tools/env_b.py`
(`trunk_b`, `branch`). Evidências: `round-02/mundo/floresta.png`, `vegetacao-close.png`,
`cena-panorama.png`.

## Biblioteca de árvores (poucas, distintas)

| Nome no cenário | Silhueta | Papel na composição |
|---|---|---|
| `tree_oak` | copa larga em 3–4 massas com franja de cachos (O3, rodada 03) | massa padrão da floresta |
| `tree_oak3` | oval em dois andares, tronco alto | vertical: separa planos |
| `tree_oak4` | jovem, torta, copa puxada para um lado | quebra a repetição de alturas |
| `tree_oak2` | guarda-chuva âmbar | acento quente (1 em ~9) |
| `tree_pine` | saias redondas empilhadas, ponta dobrada | contraste de forma e de verde |
| `tree_birch` | tronco creme fino e sinuoso | acento claro, pontual |
| `tree_big` | árvore-marco: porta, lanternas, cogumelos-prateleira | ponto de interesse |
| `tree_willow` (rodada 06, candidato) | salgueiro: tronco baixo inclinado, guarda-chuva achatado e CORTINAS pendentes com vãos | silhueta de sino, beira d'água e clareiras; verde-amarelado `#9cbf55` (pontas `#c6d873`) |

Uma espécie nova só entra se tiver silhueta distinta das sete em preto chapado.

## Copa

**Rodada 03 — carvalhos (O3, `env_e.crown_masses`):** 3–4 **massas** superelipsoides achatadas na
base (p ≈ 2,2), cada uma com leve variação de valor; **avesso escuro** (2 tons abaixo) na base de cada
massa; **franja** de 6–8 cachos pendentes em ritmo regular na borda inferior; tronco e 2–3 galhos
visíveis entre as massas. Estudo O1 (novelos lisos) e O2 (novelos + tufos) em
`assets/studies/oak_o*`; prancha `round-03/vegetacao/estudo-carvalho-*.png`. As regras abaixo
continuam valendo para pinheiro, bétula, árvore-marco e arbustos.

1. **Novelos**: 3–7 superelipsoides (p 2,15) que se sobrepõem; um dominante.
2. **Tufos de borda**: até 7 tufos de raio 2,3–3,4 voxels nos pontos da superfície que olham para
   o lado/cima. Mais tufos (14, raio 1,6) foi testado e virou "couve-flor".
3. **Pintura**: 4 tons pela normal suavizada; manchas de bloco de 4 voxels com amplitude ±0,14
   (a B usava ±0,30 em blocos de 3 → camuflagem em copas grandes). Luz do topo moderada
   (`light(base, 0,9)`), senão a copa fica plástica.
4. **Material folhagem** com `affineWind` no manifesto: o viewer funde as faces da copa e o vento
   inclina o objeto inteiro (sem frestas).

## Tronco

Pera (`trunk_b`): base larga com raízes bojudas, eixo em S, 2–3 galhos grossos visíveis entre os
novelos. Casca com estrias verticais largas (16%), nunca pontilhado.

## Plantas pequenas

| Item | Voxel | Regra |
|---|---|---|
| Flores | 1/24 u | 2 por touceira, corola em cruz + miolo; touceiras de 4 (`cluster`) |
| Grama | 1/24 u | 5–7 lâminas curvas; touceiras de 4 |
| Arbustos | 1/8 u | grupos de 2 |
| Samambaia, cogumelos | 1/16 u | grupos de 3 na borda da floresta |

## Composição (`assets/scenes/clearing_e.json`)

- Hierarquia: caminho (circulação) → casa, poço, santuário, lago, árvore-marco (interesse) →
  arbustos e pedras (secundário) → flores e grama (decoração).
- **Descanso visual**: nada de decoração a menos de 1 u do caminho; `minDist` maior que na B;
  plantas pequenas em touceiras com vazios entre elas.
- Contagens (B → E): árvores 95 → 84; arbustos 70 → 34 grupos de 2; pedras 22 → 14; samambaias/
  cogumelos 36 → 14 grupos de 3; flores 56 → 16 grupos de 4; grama 130 → 34 grupos de 4.

## Não fazer

Folhagem em caixas (C); copa lisa sem recorte; dezenas de tufos pequenos; flores "gordas" a 1/16 u;
espalhar decoração uniformemente em toda superfície.

## Rodada 06 — como foi feito o salgueiro (receita reutilizável para copas pendentes)

`env_e.tree_willow`: tronco `trunk_b(h=15)` + 4 galhos que TERMINAM dentro do domo; domo = `sblob`
achatado com base reta (y ≥ 21); cortinas = fitas de ~4,5 voxels de largura × 2,6 de espessura em duas rodas
(11 externas com 2 aberturas, 7 internas mais escuras e curtas), comprimentos por `hash3`, ponta arredondada
e mais clara, abertura para fora embaixo. Pintura `canopy_paint` + avesso escuro + `prune_specks`.
Defeitos da 1ª versão (não repetir): galhos furando a folhagem (pintas de casca — a folhagem tem prioridade),
bola solta no topo, cortinas encostadas como saia de abajur (precisam de VÃOS). Prova: `ExpansionTest`
(`willowHangsItsFoliageAndStaysOneSolidPiece`) e `round-06/arvore/`.
