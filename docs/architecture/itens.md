# Itens (rodada 13)

O catálogo de itens: o que cada item É. A mochila está em [inventário](inventario.md) e de onde os itens vêm, em
[recompensas e loot](recompensas-e-loot.md). Decisão: [ADR 0031](../decisions/0031-itens-e-recompensas.md).

## O catálogo (`assets/scenes/itens_e.json`, escrito à mão)

```json
{"formato": "valumo.itens", "versao": 1,
 "categorias": {"material": "Material", "achado": "Achado", "consumivel": "Consumível", "curiosidade": "Curiosidade"},
 "itens": [
  {"id": "item.cataplasma", "nome": "Cataplasma de ervas", "categoria": "consumivel", "pilha": 5, "icone": "cataplasma",
   "descricao": "…", "uso": {"efeito": "curar", "vida": 2, "rotulo": "Aplicar"}}
 ]}
```

| Campo | Regra |
|---|---|
| `id` | estável, `item.[a-z0-9_]+`; é a identidade no save (nunca o nome) |
| `nome`, `descricao` | o que a tela mostra (até 32 e 200 caracteres); mudar não mexe em save nenhum |
| `categoria` | uma das `categorias` declaradas no arquivo |
| `pilha` | quantas unidades cabem num espaço da mochila, de 1 (não empilha) a 99 |
| `icone` | um dos ícones de pixel de `vx.game.item.ItemIcons` |
| `uso` (opcional) | `efeito` = um dos efeitos do CÓDIGO (`ItemEffects.KNOWN`), com o número dentro do limite dele; `rotulo` = o botão |

**Leitura** (`ItemCatalog.load`, uma vez por sessão):

- **Uma recusa só:** qualquer campo errado recusa o catálogo INTEIRO, com o motivo; nunca um item pela metade.
- **Ordem:** a do arquivo é a ordem de exibição.

## A primeira coleção (5 itens)

| Id | Nome | Categoria | Pilha | Uso | De onde vem |
|---|---|---|---|---|---|
| `item.ervas_trilha` | Ervas da trilha | material | 20 | — | presente da Índira, árvore retorcida |
| `item.cataplasma` | Cataplasma de ervas | consumível | 5 | cura 2 de vida (um coração) | presente da Índira |
| `item.fragmento_pedra` | Fragmento de pedra antiga | achado | 10 | — | sítio abandonado |
| `item.madeira_velha` | Madeira envelhecida | material | 20 | — | abóbada soterrada |
| `item.registro_viagem` | Registro de viagem | curiosidade | 1 | — | ruína de casa |

O que fica de fora nesta rodada:

- **Raridade, equipamento, peso, durabilidade e qualidade aleatória.**
- **Materiais sem uso:** existem para o crafting futuro, e a descrição diz isso sem prometer.

## Os efeitos (`ItemEffects`)

| Efeito | Número | Regra |
|---|---|---|
| `curar` | `vida` 1–12 | devolve a vida sem passar do máximo; só com o herói de pé, ferido e FORA da luta engajada |

Sem efeito possível, o item **não é gasto**, e a tela diz por quê antes do clique (`ItemEffects.blocker`): "Você não
está ferido", "Não dá para aplicar no meio da luta".

**Efeito novo:**

1. um caso em `ItemEffects.apply` e `blocker`;
2. o nome em `KNOWN`;
3. o limite em `validAmount`;
4. um teste em `InventoryTest`.

## Como fazer

- **Criar um item:** acrescente um objeto em `itens`, com id novo e categoria e ícone existentes, e rode
  `ItemCatalogTest`. Só aparece no jogo se alguma fonte o entregar ([recompensas](recompensas-e-loot.md)).
- **Acrescentar uma categoria:** uma chave em `categorias` (`[a-z_]`) com o rótulo da tela. Nenhuma categoria tem regra de
  código.
- **Configurar o empilhamento:** `pilha`; 1 = cada unidade ocupa um espaço.
- **Criar um ícone:**
  1. um mapa 14 × 14 em `ItemIcons.ICONS`, só com as cores de `ItemIcons.PALETTE` (o teste confere);
  2. o nome no item.
  - **Arte autoral:** o agente artista substitui o mapa sem mudar o contrato
    ([contrato dos ícones](../art/contrato-icones-de-itens.md)).
- **Testar:** `ItemCatalogTest` (o catálogo oficial e as recusas), `InventoryTest` (efeitos).
