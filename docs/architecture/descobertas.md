# Descobertas: achados e lugares (rodada 12 → 12.1)

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

## 2. Os três tipos

| Tipo | Categoria | Onde existe | Como se acha | Moedas |
|---|---|---|---|---|
| `casa`: Casa tomada pela mata | vestígio | toda região com a ruína de casa (119 de 200 sementes) | **E** no pote, nas cinzas da lareira ("Pegar o pote da lareira") | 5 (a comum) |
| `torre`: Torre caída | mirante | toda região com a torre caída (81 de 200) | **chegar**: ficar de pé em cima da muralha baixa (1 u, sobe-se com um salto); embaixo, a placa "Subir na muralha" | 8 (pede o salto) |
| `arvore`: Árvore retorcida | marco natural | só quando a árvore retorcida existe (77 de 200, sempre ao lado da ruína) | **E** na árvore ("Examinar a árvore") | 0 (o registro) |

- **Ids** derivados de rótulos, nunca de coordenada:
  - `desc.r0.ruina.0` (casa);
  - `desc.r0.torre.0` (torre);
  - `desc.r0.arvore.0` (árvore);
  - POIs `poi.r0.ruina.0`, `poi.r0.torre.0`, `poi.r0.arvore.0`.
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
- **Checklist:** nenhum mundo tem os três tipos garantidos (a árvore é rara, e a ruína é casa OU torre).
