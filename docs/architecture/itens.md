# Itens (rodadas 13 e 13.1)

O catálogo de itens: o que cada item É. A mochila está em [inventário](inventario.md) e de onde os itens vêm, em
[recompensas e loot](recompensas-e-loot.md). Decisões: [ADR 0031](../decisions/0031-itens-e-recompensas.md) e, para os
modelos voxel e as pilhas maiores, [ADR 0032](../decisions/0032-mochila-viva.md). Publicado na tag
`inventory-foundation-v0.7`.

**O catálogo não está congelado.** Itens novos podem entrar depois, desde que:
- **o save continue compatível:** id estável `item.*` nunca reaproveitado, campos opcionais, e um item que o jogo não
  conhece é tratado como jogo mais novo;
- **os geradores publicados não mudem:** as fontes reagem a eventos que já existem.

Congelados ficam só os geradores v1 e v2.

## O catálogo (`assets/scenes/itens_e.json`, escrito à mão)

```json
{"formato": "valumo.itens", "versao": 1,
 "categorias": {"material": "Material", "achado": "Achado", "consumivel": "Consumível", "curiosidade": "Curiosidade"},
 "itens": [
  {"id": "item.cataplasma", "nome": "Cataplasma de ervas", "categoria": "consumivel", "pilha": 20, "modelo": "cataplasma",
   "descricao": "…", "uso": {"efeito": "curar", "vida": 2, "rotulo": "Aplicar"}}
 ]}
```

| Campo | Regra |
|---|---|
| `id` | estável, `item.[a-z0-9_]+`; é a identidade no save (nunca o nome) |
| `nome`, `descricao` | o que a tela mostra (até 32 e 200 caracteres); mudar não mexe em save nenhum |
| `categoria` | uma das `categorias` declaradas no arquivo |
| `pilha` | quantas unidades cabem numa pilha da mochila, de 1 (não empilha) a 99; a mochila não tem limite de pilhas (13.1) |
| `modelo` | o modelo voxel do item (`[a-z0-9_]`), gerado por `tools/itens_e.py` em `assets/items/e/`; ao ler dos assets, modelo não gerado recusa o catálogo |
| `uso` (opcional) | `efeito` = um dos efeitos do CÓDIGO (`ItemEffects.KNOWN`), com o número dentro do limite dele; `rotulo` = o botão |

**Leitura** (`ItemCatalog.load`, uma vez por sessão):

- **Uma recusa só:** qualquer campo errado recusa o catálogo INTEIRO, com o motivo; nunca um item pela metade.
- **Ordem:** a do arquivo é a ordem de exibição.

## A primeira coleção (5 itens)

| Id | Nome | Categoria | Pilha | Uso | De onde vem |
|---|---|---|---|---|---|
| `item.ervas_trilha` | Ervas da trilha | material | 99 | — | presente da Índira, árvore retorcida |
| `item.cataplasma` | Cataplasma de ervas | consumível | 20 | cura 2 de vida (um coração) | presente da Índira |
| `item.fragmento_pedra` | Fragmento de pedra antiga | achado | 99 | — | sítio abandonado |
| `item.madeira_velha` | Madeira envelhecida | material | 99 | — | abóbada soterrada |
| `item.registro_viagem` | Registro de viagem | curiosidade | 1 | — | ruína de casa |

As pilhas cresceram na 13.1 (eram 20, 5, 10 e 20): sem limite de espaços, a pilha só organiza a grade.

## Os modelos (rodada 13.1, `tools/itens_e.py`)

Cada item é um objeto de mão em voxel 1/40 u (a densidade do herói: é o que ele carrega), até 0,4 u, apoiado no chão,
desenhado pelo jogo na grade, no cartão e na prévia que gira ([inventário](inventario.md) §Os itens desenhados em 3D).
A silhueta e a cor dominante separam os cinco mesmo pequenos:

| Modelo | Silhueta | Cor dominante |
|---|---|---|
| `ervas` | maço em leque, de pé, amarrado embaixo por um cordão de palha, uma flor coral | verde-folha |
| `cataplasma` | rolo DEITADO de pano, duas voltas de cordão, a espiral do pano com as ervas no miolo, uma folha saindo | creme |
| `pedra` | lasca de lápide de pé, quebrada em diagonal, com meio entalhe e musgo no pé | cinza |
| `madeira` | duas aduelas curvas cruzadas em X, com um pedaço da cinta de ferro | castanho |
| `registro` | caderno de pé, capa de couro com filete, tira com fivela de latão, páginas claras, fita coral | vinho |

Regras da E: sombra pintada em bandas (`shade`), 3–5 matizes por modelo, sem preto nem branco puros. A folha de item é
FOSCA (não é folhagem: não balança com o vento). Prancha de revisão: o comando de roteiro `itens-prancha` (sobre o
painel, o pergaminho, em silhueta e pequenos).

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

- **Criar um item:** acrescente um objeto em `itens`, com id novo, categoria existente e modelo gerado, e rode
  `ItemCatalogTest`. Só aparece no jogo se alguma fonte o entregar ([recompensas](recompensas-e-loot.md)).
- **Acrescentar uma categoria:** uma chave em `categorias` (`[a-z_]`) com o rótulo da tela. Nenhuma categoria tem regra de
  código.
- **Configurar o empilhamento:** `pilha`; 1 = cada unidade é uma pilha.
- **Criar um modelo:**
  1. uma função em `tools/itens_e.py` (voxel 1/40 u, até 0,4 u; o gerador recusa maior) e o nome em `MODELS`;
  2. o nome no campo `modelo` do item;
  3. `python3 tools/build_assets.py itens_e` e a prancha (`itens-prancha`) para olhar.
  - **Arte autoral:** o agente artista entrega o modelo pelo contrato, sem mudar o catálogo
    ([contrato dos modelos](../art/contrato-icones-de-itens.md)).
- **Testar:** `ItemCatalogTest` (o catálogo oficial, as recusas e os modelos), `InventoryTest` (efeitos).
