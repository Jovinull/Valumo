# Descobertas: achados e lugares (rodada 12 → 12.1 → 12.2)

**Status:** implementado no gerador v2. A rodada 12 tinha uma descoberta só: E na lareira da ruína dava 5 moedas e o
aviso "Descoberta: …". A rodada 12.1 a refez a partir de duas perguntas do proprietário:

1. Faz sentido moedas saírem de uma lareira abandonada sem nada que as represente?
2. Faz sentido chamar de "descoberta" cada ruína, se o mundo procedural vai ter um número enorme delas?

## 1. As duas respostas

**Moedas vêm de algo que se vê.**

- **Na casa:** nas cinzas da lareira há um **pote de barro com moedas** (`pote_achado`). Ele está cheio antes de pegar e
  vazio depois (`pote_achado__estado-vazio`), conforme o progresso salvo.
- **Na torre:** o mesmo pote vira o **jarro esquecido no parapeito**.
- **Na árvore:** não há objeto, e ela não dá moedas: nem todo lugar tem tesouro.

**"Descoberta" é de um TIPO de lugar, não de cada lugar.** São dois conceitos separados:

- **Achado:** o objeto daquele lugar. Dá as moedas **uma vez** e fica marcado no progresso
  (`progresso.descobertas`, o mesmo campo opcional da rodada 12).
- **Lugar:** na **primeira** vez que se acha um tipo de lugar nesta aventura (personagem × mundo), o aviso diz
  "Lugar novo: Casa tomada pela mata" e o diário ganha o tipo, em "Lugares", com uma frase sobre esse tipo de lugar.
  Os seguintes do mesmo tipo são só o achado ("Pote de barro · +5 moedas").

O registro por tipo é **derivado** dos achados salvos. Não há campo novo no save nem save v3.

Hoje cada mundo tem uma região, então cada tipo aparece uma vez por mundo. O desenho já é o de um mundo com muitas
regiões: o diário guarda 3 tipos, não 300 lugares.

## 2. Os tipos (rodada 12.2: quatro ruínas e a árvore)

**Quatro coisas diferentes, que não se confundem:**

| O quê | Exemplo | Onde mora | Muda? |
|---|---|---|---|
| **tipo de lugar** | "Abóbada soterrada" | `descobertas_e.json → tipos.abobada` (dado) | não; é o que o diário lista em "Lugares", uma linha por tipo |
| **instância do POI** | `poi.r0.abobada.0` | a região gerada (`RegionLayout.pois`), id por rótulo | só se a região muda (versão do gerador) |
| **descoberta daquele POI** | `desc.r0.abobada.0` | o id derivado do POI; ela fica em `progresso.descobertas` quando achada | uma vez por aventura (personagem × mundo) |
| **recompensa** | +5 moedas | o tipo (`moedas`); paga no passo da descoberta (`QuestSystem.discover`) | uma vez: salvar e reabrir NÃO devolve o pote cheio (`DiscoveryTest`) |

**Por que quatro tipos de ruína e não "ruína" quatro vezes:** cada uma é um lugar diferente, com objeto e verbo próprios
(o pote na lareira, o jarro no mirante, o pote entre os barris, a pedra do marco). O diário só cresce quando aparece um
tipo novo.

| Tipo | Categoria | Onde existe | Como se acha | Moedas |
|---|---|---|---|---|
| `casa`: Casa tomada pela mata | vestígio | toda região com a ruína de casa (94 de 200 sementes na 12.2) | **E** no pote, nas cinzas da lareira ("Pegar o pote da lareira") | 5 (a comum) |
| `torre`: Torre caída | mirante | toda região com a torre caída (71 de 200) | **chegar**: ficar de pé em cima da muralha baixa (1 u, sobe-se com um salto); embaixo, a placa "Subir na muralha" | 8 (pede o salto) |
| `abobada`: Abóbada soterrada (12.2, PR #8) | abrigo soterrado | toda região com a abóbada (14 de 200; só num declive) | **E** no pote entre os barris do fundo ("Pegar o pote entre os barris"; depois, "Olhar o pote vazio") | 5 |
| `sitio`: Sítio abandonado (12.2, PR #8) | vestígio de lavoura | toda região com o sítio (21 de 200; só em chão aberto) | **E** na pedra do marco ("Examinar a pedra do marco") | 0 (o registro) |
| `arvore`: Árvore retorcida | marco natural | só quando a árvore retorcida existe (75 de 200, sempre ao lado da ruína) | **E** na árvore ("Examinar a árvore") | 0 (o registro) |

- **Ids** derivados de rótulos, nunca de coordenada:
  - `desc.r0.ruina.0` (casa);
  - `desc.r0.torre.0` (torre);
  - `desc.r0.abobada.0` (abóbada) e `desc.r0.sitio.0` (sítio), na 12.2;
  - `desc.r0.arvore.0` (árvore);
  - POIs `poi.r0.ruina.0`, `poi.r0.torre.0`, `poi.r0.abobada.0`, `poi.r0.sitio.0`, `poi.r0.arvore.0`.
- **Teto conservador:** uma região rende no máximo 8 moedas de achados; a missão do posto rende 30.

## 3. Textos

Os textos estão em `assets/scenes/descobertas_e.json` (escrito à mão; versão 2):

- `lugar`, `categoria`, `modo`, `verbo` (a placa) e `verboVisto` (a placa depois do achado: "Olhar o pote vazio";
  padrão = o verbo);
- `achado` e `objeto` (cheio e vazio);
- `moedas`;
- `texto` (a primeira vez), `jaVisto` (as seguintes) e `diario` (o tipo de lugar).

Só dizem o que a região tem. Os fatos vêm do gerador (`ProceduralScene.discovery`):

| Chave | Fato |
|---|---|
| `{rumo_vila}` | rumo da vila visto do lugar |
| `{rumo_posto}` | rumo do posto visto do lugar |
| `{lago}` | uma frase com o lago e o rumo dele, só se existe |
| `{ruina}` | ao lado de que ruína a árvore cresceu |

Exemplo real (semente 555): "Da muralha caída se vê a vila a sudeste e o posto da trilha a sudoeste. Mais longe, brilha o
lago a sul. No parapeito, um jarro esquecido com moedas."

## 4. Regras e onde estão

| Regra | Onde |
|---|---|
| o ponto, o tipo e a altura vêm do gerador | `RegionV2.ruin` (a lareira ou o mirante, pelo catálogo), `RegionV2.vegetation` (a árvore) → `RegionLayout.DiscoverySpot` |
| alcançável, ou a tentativa é recusada | `checkpoint` `descoberta:<id>` na conferência de alcance |
| E (usar) × chegar | `GameLogic.findDiscovery` (só `usar`); o mirante registra no passo em que o herói está de pé no ponto (`discoveryFloor`: o chão de colisão, não o meio voxel do catálogo) |
| paga uma vez; outro personagem e outro mundo não enxergam | `QuestSystem.discover` (rodada 12) |
| "Lugar novo" × achado | `GameLogic.useDiscovery` |
| diário por tipo | `GameLogic.knownPlaces` → "Lugares" |
| o objeto cheio/vazio | `Game.drawDiscoveryObjects` (apoiado no chão de colisão do ponto) |
| testes | `DiscoveryTest` (os três tipos, o pote, o mirante, a árvore, o diário, o save, os textos com fatos conferidos à parte); `GeneratorV2Test` (casa ou torre, a árvore ⇔ a descoberta dela); `CompositeCollisionTest.p74…` (a subida na muralha com a física real) |

## 5. O que não é (escopo)

- **Inventário:** não há; o pote não vai para lugar nenhum, as moedas vão para a carteira de sempre.
- **Sorteio:** não há loot aleatório nem raridade; o que cada tipo dá está escrito no arquivo.
- **Checklist:** nenhum mundo tem todos os tipos garantidos (a árvore é rara, e a ruína é UMA das quatro).
- **Troca de ruína num mundo já salvo:** se a região de uma semente muda de ruína antes da publicação da v2, a descoberta
  salva da ruína antiga não existe mais. O save é recusado como "de uma versão de testes", sem `.invalido` e sem tocar no
  arquivo ([save](save.md)). Nunca se inventa a equivalência (a lareira não vira o mirante).

## Publicação da v2 (tag `procedural-world-v0.6`)

- **Congelado:** os quatro tipos de ruína, a árvore e os textos e moedas de `descobertas_e.json`. Eles entram na cena
  gerada, e `regioes-v2.txt` os inclui.
- **Texto novo ou moedas diferentes num tipo existente** mudam a cena de mundos já criados, então são a v3.
- **Uma descoberta de um tipo NOVO** também é a v3 (o gerador precisa pôr o lugar).
- **O que vale para sempre:** o pote vazio, as moedas uma vez e o "Lugar novo" na primeira vez de um tipo. Vêm do save
  (`progresso.descobertas`), que não muda de formato.
