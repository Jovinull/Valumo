# Recompensas e loot (rodada 13)

De onde vêm os itens e como nada se duplica. Decisão: [ADR 0031](../decisions/0031-itens-e-recompensas.md).

## Uma camada de jogo sobre os mundos

Os geradores v1 e v2 estão congelados ([ADR 0029](../decisions/0029-versoes-do-gerador.md)). As recompensas **não mudam
a região**. Não acrescentam baú, ponto de interação, POI nem texto de descoberta. Só reagem a dois eventos estáveis que
já existem:

| Fonte | Evento | Id da fonte (no save) | Quando |
|---|---|---|---|
| presente | terminar a conversa com o morador | `presente:<morador>` | uma vez por aventura; se a mochila não tem espaço, o morador espera |
| achado | descobrir um lugar (pelo TIPO do lugar) | `achado:<id da descoberta>` | junto com a descoberta; o que não coube fica no lugar e sai ao usar o lugar de novo (E) |

**A tabela** (`assets/scenes/recompensas_e.json`, escrita à mão, `valumo.recompensas` versão 1):

| Fonte | O que dá | Coerência |
|---|---|---|
| presente da Índira (herbalista) | 2 ervas da trilha + 2 cataplasmas | é o ofício dela |
| ruína de casa | 1 registro de viagem | ao lado do pote de moedas |
| abóbada soterrada | 2 madeiras envelhecidas | aduelas soltas dos barris |
| sítio abandonado | 1 fragmento de pedra antiga | uma lasca ao pé do marco |
| árvore retorcida | 3 ervas da trilha | ervas entre as raízes |
| torre caída (mirante) | nada | é um lugar para ver, não um depósito |

**As moedas publicadas não mudam** (`descobertas_e.json`): casa e abóbada 5, mirante da torre 8, sítio e árvore 0; a missão
da Kelyra 30. Itens não têm preço, e nada se vende.

**Onde há fontes:**

- **Regiões v2:** os achados das ruínas e da árvore (cada região tem a sua ruína) e o presente onde a Índira mora
  (88 de 200 regiões).
- **Vila fixa e regiões v1:** nenhuma fonte (não têm descobertas nem a Índira). Isso é limitação registrada, e o drop de
  fungo é o candidato da Rodada 14.

## Contra duplicação e perda

- **Uma vez por fonte:** `ItemProgress.grant` marca a fonte coletada **se e somente se** tudo entrou na mochila. Nunca
  marca sem entregar; nunca entrega sem marcar.
- **Repetir não entrega de novo:** apertar E várias vezes, conversar de novo, recomeçar o confronto (R) ou carregar o save
  não entregam outra vez (`InventoryFlowTest`).
- **Mochila cheia:** nada entra e a fonte continua disponível; o cartão diz "Mochila cheia" e o que fazer.
- **Saves de antes da rodada 13:**
  - **Regra explícita:** uma descoberta feita antes NÃO entrega nada ao carregar. O achado fica no lugar: quando o
    jogador volta e usa o lugar, recolhe uma vez.
  - **Moedas:** não pagam de novo.
  - **Teste:** `descobertaDeSaveAntigoRecolheQuandoOJogadorVolta`.
- **Cada aventura tem as suas fontes:** outro personagem no mesmo mundo recebe o próprio presente; o mesmo personagem em
  outro mundo começa do zero.

## Determinismo e versões

- **Recompensa fixa por fonte:** sem sorteio nesta versão.
- **Se houver sorteio:** a semente sai de `(id da fonte, mundo, versão da tabela)`, com o `Rng` por rótulo; nunca o
  relógio, a ordem dos NPCs, `Math.random` nem o RNG da geração.
- **O passado não muda:** o que já foi entregue está no save (os itens e a fonte), então uma tabela nova (`versao` 2) só
  vale para fontes ainda não coletadas.

## Como fazer

- **Definir uma fonte nova:**
  - presente de outro morador: uma chave em `presentes` com o id do rig;
  - achado de outro tipo de lugar: uma chave em `achados` com o tipo de `descobertas_e.json`.

  `ItemCatalogTest` confere que o tipo e o morador existem e que as quantidades cabem na pilha.
- **Fonte que exige mudar a região** (baú posto pelo gerador): isso é a v3 do gerador
  ([guia](../processo/como-evoluir-o-gerador.md)), não esta camada.
- **Validar saves:** `InventoryFlowTest`, `SaveCompatTest`, `tools/qa/saves_antigos.sh`.
