# Conteúdo do mundo — construções, vegetação e pontos de interesse novos (laboratório)

**Estado:** frente paralela de produção artística, em ramo próprio (`feature/world-content-library`), base `8fd6756`
(rodada 10, anterior ao mundo procedural da rodada 11, ainda não publicado). O **lote 1 foi aprovado artisticamente pelo
proprietário** (2026-11-19): 12 assets aceitos, 1 rejeitado. Aprovação artística **não** é liberação para o gerador: o
catálogo separa as duas (§9). Nada substitui um asset aprovado, e nenhuma cena do jogo muda. Catálogo e contrato técnico: [../architecture/catalogo-de-conteudo.md](../architecture/catalogo-de-conteudo.md).
Skill: `conteudo-mundo`.

Este guia complementa (não repete) [visual-identity.md](visual-identity.md), [estilo-arquitetonico.md](estilo-arquitetonico.md),
[regras-de-vegetacao.md](regras-de-vegetacao.md), [paleta-e-materiais.md](paleta-e-materiais.md) e
[contrato-de-qualidade.md](contrato-de-qualidade.md). Quando um número aparece lá e aqui, **lá manda**.

## 1. Matriz de referência visual (aprovados, medidos)

Medida com o mesmo leitor e o mesmo mesher do jogo (`tools/conteudo/MeshCost.java`, fusão exata, folhagem fundida só com
`affineWind`). É a régua de escala, de densidade e de custo dos novos.

| Prop | Voxel | Caixa (u) L × A × P | Voxels | Triângulos | Vento |
|---|---|---|---|---|---|
| `house` | 1/8 | 5,50 × 6,00 × 4,75 | 30 476 | 17 776 | sim |
| `trail_post` | 1/8 | 4,25 × 3,75 × 2,75 | 5 261 | 9 492 | não |
| `well` | 1/8 | 2,25 × 3,12 × 2,00 | 1 978 | 3 490 | sim |
| `shrine` | 1/8 | 3,75 × 3,62 × 3,00 | 4 233 | 5 204 | sim |
| `tree_oak` (O3) | 1/8 | 3,75 × 5,25 × 2,75 | 5 980 | 6 946 | sim |
| `tree_oak3` | 1/8 | 2,62 × 6,00 × 2,00 | 3 610 | 4 798 | sim |
| `tree_oak4` | 1/8 | 2,50 × 4,00 × 1,88 | 1 817 | 2 956 | sim |
| `tree_oak2` | 1/8 | 3,62 × 5,00 × 2,50 | 5 258 | 6 260 | sim |
| `tree_pine` | 1/8 | 2,75 × 6,38 × 2,75 | 6 491 | 6 686 | sim |
| `tree_birch` | 1/8 | 2,25 × 4,88 × 1,50 | 1 788 | 2 746 | sim |
| `tree_willow` | 1/8 | 3,62 × 4,12 × 3,75 | 5 722 | 11 904 | sim |
| `tree_big` (marco) | 1/8 | 6,62 × 9,50 × 5,75 | 46 991 | 26 538 | sim |
| `rock_l` | 1/8 | 2,88 × 2,00 × 2,12 | 3 735 | 2 688 | sim |
| `fence` | 1/8 | 2,25 × 1,50 × 0,25 | 153 | 580 | não |
| `lantern_post` | 1/16 | 0,75 × 1,81 × 0,38 | 361 | 724 | sim |
| `barrel` / `crate` | 1/16 | 0,62 × 0,81 × 0,62 | ~1 000 | 1 070 / 486 | sim |

O que a matriz ensina, por traço:

| Traço | Nos aprovados | Regra para os novos |
|---|---|---|
| Paleta | pedra lilás-quente em 3 tons; estuque `#f4e4c4` liso; enxaimel `#7a4a3a`; telha coral em fiadas; teal nas portas e venezianas; palha mostarda no poço | as mesmas cores; cor nova só com papel (§4) |
| Proporção | porta 1,5 u ≈ 1,25 × herói; beiral acima da cabeça; casa 4,4 u de cumeeira | portas 1,4–1,6 u; nenhum vão menor que 1,3 u onde o herói "entra" |
| Voxel | 1/8 para tudo que é grande; 1/16 para o que se segura; 1/24 para flor e grama | igual; nada de detalhe de 1/16 colado em peça de 1/8 |
| Silhueta | casa larga e baixa com cumeeira em X; posto aberto; poço com cúpula; árvores de massas | cada novo muda planta, volume, telhado ou entrada, não só a cor |
| Sombra | pintada em bandas pela normal (`shade`), copa em 4 tons com manchas largas | igual; proibido ruído de 1 voxel e mancha aleatória em telhado/estuque |
| Densidade de detalhe | focos (porta, janela, lanterna, floreira) e descanso (paredes lisas) | 1 luz quente + 1 toque vegetal por construção; o resto descansa |
| Fora de prumo | cisalhamento 0,06–0,08 voxel/voxel; janelas desenhadas antes do cisalhamento | igual; peça solta precisa de 2 voxels de largura em X (senão parte) |
| Todas as faces | fundos da casa com janela, enxaimel, floreira (rodada 05) | frente, lados, fundos e telhado com acabamento — conferido nas 8 vistas |
| Custo | casa 17,8 mil triângulos; árvore comum 3–7 mil | orçamento relativo (§6) |

## 2. Construções novas — o que cada uma muda

A gramática é a da casa. O que muda é o **papel no assentamento**, e ele muda a forma:

| Asset | Planta | Volume | Telhado | Entrada | Papel |
|---|---|---|---|---|---|
| `house_tall` sobrado | retangular, empena para a rua | térreo de PEDRA mais estreito + pavimento de enxaimel em BALANÇO (barrotes e mãos-francesas à mostra) | duas águas, cumeeira em Z (o contrário da casa), óculo na empena | porta em arco + balcão de venda com toldo | comércio no centro da vila |
| `hut_herbalist` cabana redonda | CIRCULAR | tambor baixo de estuque com 6 prumos radiais | CONE côncavo de palha, beiral pendente, pote de barro no topo | portal reto saliente | ofício na borda da mata |
| `workshop_forge` oficina | em L: sala fechada + GALPÃO ABERTO | comprido e baixo | duas águas longas; chaminé alta da forja passa da cumeeira | porta + vão do galpão | ofício com fogo |
| `cabin_hunter` caçador | retangular pequena | TRONCOS empilhados, cantos cruzados | SALTBOX (água de trás longa sobre a lenha), teal | porta de tábuas + alpendre | isolada na mata |
| `tower_watch` torre | quadrada estreita | fuste de pedra com talude + piso de vigia em balanço | quatro águas com cata-vento | porta em arco; corda do sino no chão | orientação, guarda |

**Arquitetura rústica isolada (decisão do proprietário, lote 1):** a cabana do caçador foi **aprovada com paredes de
troncos**. É o exemplo aprovado de variação cultural e funcional para florestas, trilhas, regiões afastadas, abrigos de
caçador e pequenos acampamentos. Ela preserva o telhado assimétrico e os oitões de estuque com enxaimel. Isso **não**
autoriza casas da vila a abandonar pedra, estuque e enxaimel: no assentamento, a gramática continua a da casa.

Lições que valem para qualquer construção nova:
- **Diagonal de enxaimel é traço 6-conexo** (`conteudo_lib.face_line`), nunca linha rasterizada: rasterizada vira pontilhado
  (`reprovados`: X do sobrado v1).
- **Chaminé externa**: base com ombro e fuste de 3 voxels. Com 5 voxels até o topo, ela vira uma torre ao lado da casa
  (sobrado v1).
- **Mãos-francesas, balaústres e esteios soltos**: ligue com `line6` e dê 2 voxels de largura em X. Os testes acharam 27
  fragmentos na torre v1 e 4 na oficina v1.
- **Galpão aberto precisa de vão ≥ 0,9 u livre** entre obstáculos (o corpo tem 0,3 u de raio). A pedra de amolar da
  oficina v1 fechava a frente: o teste de entrada achou.
- **Palha não é telha**: fiada a cada 4 voxels, com aba pendente na borda; a cada 3, o cone vira pirâmide de degraus.
- **Planta redonda**: mais de 6 prumos radiais lê como barril (cabana v1).

## 3. Árvores novas — a regra é a silhueta

| Asset | Silhueta em preto | Por que não é nenhuma das 8 |
|---|---|---|
| `tree_forked` bifurcada | Y largo: o tronco abre em V e cada líder carrega a própria copa, com **vão de céu** no meio | nenhuma aprovada tem copa partida |
| `tree_poplar` álamo | fuso alto e estreito (1,4 × 6 u), afunilado embaixo, sulcos verticais rasos em feixes | o pinheiro é cone em saias horizontais; o oak3 é oval em dois andares redondos |
| `tree_gnarled` antiga | tronco torcido e inclinado; **pratos** achatados nas pontas de galhos longos, em alturas diferentes | é mais larga que alta, com vazios entre massas; nenhuma tem copa em nuvens soltas |
| `tree_dead` seca | só galhos, forquilhas e gravetos; oco e orelhas-de-pau | a única sem folhagem (custo baixo, sem vento) |
| `tree_tiered` em andares | **REJEITADA para florestas naturais**: pratos simétricos num tronco reto | lê como topiaria; pode voltar um dia como estudo ornamental de jardim, se houver motivo artístico |

Regras que os novos confirmaram:
- Copa com vão (bifurcada, antiga) dá variedade de silhueta sem custo e sem cor nova.
- **Graveto é forquilha** que continua o rumo do galho (`line6`). Feito com o `connect_path` (y primeiro), vira barra em
  "T" e lê como antena (seca v1).
- Prato de folhagem com menos de ~3 voxels de altura lê como disco (antiga v1).
- Copa em fuso sem lóbulos e sem afunilar embaixo lê como cápsula num poste (álamo v1).
- O tronco precisa ficar perto do **centro da caixa** da malha: a colisão do jogo (`Collision.addProp`, perfil `tronco`)
  põe o círculo ali (teste `treeTrunksSitWhereTheGameCollisionPutsThem`).

## 4. Paleta: papéis novos

| Papel | Valor | Onde | Por quê |
|---|---|---|---|
| Folhagem — álamo | `#8aa83f` (folhagem) | `tree_poplar` | oliva-amarelado entre o carvalho `#76ad47` e a bétula `#b8cb55`: separa planos sem virar acento |
| Folhagem — árvore antiga | `#5c8c46` (folhagem) | `tree_gnarled` | verde-musgo escuro abaixo do arbusto `#6a9f47`: lê como velha e densa |
| Madeira seca | `#9a7c68` | `tree_dead` | casca sem seiva, família da casca `#8f5d40` puxada para o cinza quente |
| Orelha-de-pau | `#e8cfa0` | `tree_dead` | fungo de prateleira BEGE; nunca o vermelho do chapéu do fungo inimigo |
| Terracota | `#c8643c` | potes, pote-respiro da cabana, balcão | a da faixa da exploradora (já na paleta de personagens) |
| Couro esticado | `#9a6238` / `#6e4130` | varal do caçador | o couro dos personagens |
| Telhado mostarda / teal | `#eeaa3a` / `#3f8a8c` | variação de telhado | "telhado sempre saturado (coral, teal ou mostarda)" do estilo arquitetônico |

## 5. Pontos de interesse — a ruína é da mesma cultura

As ruínas usam a pedra, o estuque, o enxaimel e a telha da vila: são **mais velhas**, não de outra civilização. Assim o
jogador lê "aqui morou gente como a da vila". Nada de templo genérico nem de motivo de outra obra. O círculo de pedras
não tem lintel (não é nenhum monumento conhecido) e usa faixas entalhadas horizontais (o P2, espiral, não está aprovado).
A semente de luz P1 aparece **uma vez**, no altar, onde significa o que a assinatura significa: vida mágica do lugar.

| Asset | Função no mundo procedural | O que o jogador vê |
|---|---|---|
| `ruin_tower` | exploração, orientação (alta), abrigo, covil (encontro), missão no centro, ponto de vista na escada | anel de muralha partido em degraus largos, porta em arco intacta, seteira, lajes, escada quebrada, hera |
| `stone_circle` | descoberta e mistério, missão de ativar/proteger, encontro ao entardecer | 7 pedras ovais desiguais, uma caída, laje com a semente de luz |
| `ruin_house` | narrativa ambiental, recompensa (lareira, escombros), encontro pequeno | dois cantos de parede com estuque descascado, arco de pé, chaminé, placa do telhado caída, árvore jovem dentro |

**Círculo de pedras (decisão do proprietário):** conceito aprovado, refinamento pendente. As pedras agradam, mas o
conjunto ainda lembra monólitos genéricos, e a semente P1 quase some na câmera de jogo. No lote 4 vem um estudo
controlado, sem refazer do zero:
- pedras com proporção própria e composição assimétrica;
- centro reconhecível;
- P1 discreta, mas legível;
- marcas de uma cultura do Valumo;
- leitura a distância.

Nada de portal mágico genérico, excesso de partículas ou espirais P2. Até lá, o círculo fica no catálogo sem liberação
para distribuição procedural.

Lições: o topo quebrado se faz em degraus largos (blocos de arco de ~6 voxels, altura em múltiplos de 4); jitter por bloco
pequeno vira "tubos de órgão" (torre v1). Pedra redonda grande pede bloco de pintura maior (4, 3, 4) e musgo só no capuz
(círculo v1). Placa de telhado caída quase deitada (1 para baixo a cada 4); a 45° vira escada com tapete (ruína v1).

## 6. Custo e frequência

Orçamento relativo aos aprovados, conferido por `ConteudoMundoTest.meshCostStaysWithinTheCategoryBudget`:

| Categoria | Orçamento (triângulos) | Referência |
|---|---|---|
| construção | ≤ 20 500 | 1,15 × casa (17 776) |
| ponto de interesse | ≤ 17 800 | casa |
| árvore comum | ≤ 8 400 | 1,2 × carvalho O3 (6 946) |
| árvore incomum/rara | ≤ 11 900 | salgueiro |

Frequência sugerida:
- **comum** (pode repetir muito): bifurcada, álamo;
- **incomum** (1–2 por região): sobrado, cabana redonda, oficina, caçador, ruína de casa;
- **rara** (≤ 1 por região): torre de vigia, torre arruinada, círculo, antiga, seca.

A frequência e o custo andam juntos: o que é caro aparece pouco.

## 7. Variações controladas

Cada asset declara no catálogo os parâmetros **seguros** (`variacoes.parametros`), e o build gera os extremos:

- cada valor fora do padrão, um parâmetro por vez;
- todos no último valor ao mesmo tempo.

`ConteudoMundoTest.variantsKeepTheFootprintAndThePoints` confere que nenhum extremo muda a pegada (±0,5 u), a altura
(±16%) nem os pontos de entrada e interação. Seguros:

- **telhado:** coral, teal, mostarda, palha;
- **acessórios:** toldo, placa, couro, ervas, hera ou trepadeira;
- **desgaste:** pedra caída, telhado caído;
- **semente:** a franja da copa, o lado dos líderes, os blocos de quebra;
- **altura:** só no álamo, ±12%.

**Não** são seguros: proporção, planta, posição de porta e escala. Se uma seed deixa a leitura quebrada, o parâmetro sai da
lista.

## 8. Vento: o que balança e o que não

O vento afim da E (`scene.vert`) desloca **só o material folhagem**, proporcional à altura acima da base do prop
(0,022 × vento 0,8 × altura). Paredes, telhados e pedras nunca se movem. A flag `affineWind` do prop **não** liga vento:
ela só autoriza o mesher a fundir faces de folhagem.

Por isso, folhagem **encostada em sólido** desliza sobre ele e abre fresta onde o mesher descartou a face do vizinho. A
4 u de altura, o deslocamento chega a ~0,07 u (meio voxel). Reproduzido no laboratório com vento 0 × 1: o musgo do
telhado da **casa aprovada** abre frestas (defeito anterior a esta frente, registrado para o agente principal).

Regra dos assets do laboratório:
- vegetação **presa** (musgo em telhado, pedra ou galho; hera de parede; folhas de floreira) é **fosca**: `MOSS_FIX`,
  `IVY`;
- folhagem é só o que balança **solto**: copas, arbustos, ervas penduradas;
- o catálogo mede `affineWind` (só quem ainda tem folhagem declara);
- `ConteudoMundoTest.attachedFoliageDoesNotSlideOnRigidStructures` exige 0 contato folhagem × sólido acima de 1,7 u
  (≥ 1/4 de voxel) em construções e POIs.

## 9. Estados no catálogo

| Campo | Valores | Lote 1 |
|---|---|---|
| `aprovacao.artistica` | `aprovado`, `aprovado_conceito`, `rejeitado` | 11 aprovados, 1 conceito (círculo), 1 rejeitado (em andares) |
| `aprovacao.integracao` | `liberado`, `depende_colisao_composta`, `aguardando_refinamento`, `nao_distribuivel` | liberados: sobrado, cabana redonda, caçador, torre de vigia, bifurcada, álamo, antiga, seca. Dependem de formas compostas: oficina, torre arruinada, ruína de casa. Aguardando: círculo (que também depende das formas) |
| `aprovacao.distribuicao_procedural` | `true` só para `liberado` | 8 |
| `aprovacao.dependencias` | texto, nunca vazio quando não liberado | |

O teste exige coerência: quem declara `colisao.formas` não pode estar `liberado`, e quem não está liberado diz por quê.

## 10. Como revisar (resumo; procedimento na skill)

1. `python3 tools/build_assets.py conteudo_e`.
2. `tools/conteudo/vistas.sh <id>`: 8 vistas no modo LINHA com o herói (3/4, frente, os dois lados, fundos, 3/4 dos
   fundos, silhueta, tarde).
3. `set=conteudo-<categoria>-comparacao`: ao lado dos aprovados, em cor e em preto.
4. `tools/conteudo/vitrines.py` + `tools/conteudo/cenas.py`: no mundo, com câmera de jogo, 720p e 1080p, dia e tarde.
5. `./gradlew :viewer:test --tests vx.viewer.ConteudoMundoTest`.
6. Crítica escrita **com o problema concreto** (o que se vê, em que vista) e correção no script; nunca afrouxar teste.
