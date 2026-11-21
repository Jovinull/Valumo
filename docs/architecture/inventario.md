# Inventário (rodadas 13 e 13.1)

A mochila da aventura: o contrato, a tela e as regras. Rodada 13.1 ([ADR 0032](../decisions/0032-mochila-viva.md)): ela
fica sobre o jogo correndo, não tem limite de espaços e mostra os itens pelos modelos voxel. Aprovada pelo proprietário e
publicada na tag `inventory-foundation-v0.7`. Os itens estão em
[itens](itens.md); as fontes, em [recompensas e loot](recompensas-e-loot.md); o save, em [save](save.md) §Rodada 13.

## O contrato (`vx.game.item.Inventory`)

- **Uma lista de pilhas** (`ItemStack`: o item e de 1 a `pilha` unidades), na ordem de chegada. **Sem limite de espaços**.
  O único teto é técnico: `MAX_STACKS` = 4096, só para o leitor de save nunca alocar sem limite.
- **Entregar** (`add`) é **tudo ou nada**:
  - confere numa cópia antes de mudar;
  - no teto técnico, um pedido de duas coisas em que só uma cabe não entrega nenhuma (`Inventory.limited` nos testes).
- **Onde entra** (determinístico):
  1. completa as pilhas do mesmo item, na ordem;
  2. o resto vira pilhas novas no fim, cada uma até a pilha do item.

  A mesma sequência de coletas dá sempre a mesma mochila.
- **Tirar** (`remove`): só o que existe. A pilha que chega a zero sai da lista, que se compacta sem deixar buraco.
- **Números inválidos** (zero, negativo, acima da pilha, mais que 9999 num pedido) são recusados.

## A tela (rodada 13.1)

**Como abrir:**

- `I`, na exploração;
- o botão **Inventário** da pausa, que fecha a pausa e abre a mochila com o jogo correndo (sem dica de tecla na tela;
  a pausa é por onde o jogador descobre a mochila).

**Como fechar:** `I` ou `Esc`. Com a mochila aberta, o Esc só a fecha; com ela fechada, pausa como sempre.

**Onde e como:**

- **Lugar:** na metade direita da tela, para o herói ficar livre no meio.
- **Painel:** de tinta quente translúcida (o mundo aparece atrás), com filete de pergaminho dourado e cantos em degrau de
  pixel. A fonte é a de sempre; nada de textura nova nem framework.
- **Cabeçalho:** o título "Mochila" e as moedas, com a moeda de pixel. A vida já está no HUD, que continua na tela.
- **Abas:** **Tudo** e as categorias do catálogo, na ordem do arquivo. A ativa é dourada.
- **Grade:** 7 colunas e 5 linhas visíveis, que rolam com a roda do mouse sobre o painel, com a barra à direita. Só as
  linhas visíveis são desenhadas e sempre sobra pelo menos uma linha vazia no fim.
  - Cada item aparece pelo **modelo voxel** sobre um disco de sombra, com a quantidade no canto (pilhas que empilham).
  - O espaço escolhido ganha a borda dourada com brilho.
  - Os espaços vazios só marcam o lugar.
- **Detalhe** (a faixa de baixo):
  - o modelo grande girando devagar (o modelo gira; a luz de estúdio fica);
  - o nome, a categoria e a quantidade ("N unidades" ou "peça única");
  - a descrição;
  - para o que se usa, a etiqueta de uso: verde quando dá, ou o motivo de não dar agora.
- **Vazios:** com a mochila vazia, "A mochila está vazia"; numa aba vazia, "Nada nesta aba".
- **Reabrir:** a mochila volta ao topo, rolada até a seleção.

**Como se escolhe e usa:**

| Entrada | Efeito |
|---|---|
| Passar o mouse num espaço | seleciona (`pairar:i`, sem som) |
| Clique esquerdo | usa o item que se usa; nos outros só seleciona (`clique:i`) |
| Clique direito | usa (`usar:i`; o que não se usa diz por quê) |
| Etiqueta de uso no detalhe | usa o selecionado (`usar-item`) |
| Setas | andam pela grade da aba, sem dar a volta |
| Enter | usa o selecionado |
| Clique numa aba | filtra (`aba:<categoria>`) |

**O jogo continua correndo** (`GameLogic.bagOpen`, uma camada sobre `State.EXPLORE`; não há mais `State.INVENTORY`):

- **Teclado:** passos fixos, herói, inimigos, salvamento automático e descobertas seguem. WASD, Shift, Espaço e Alt andam,
  correm, pulam e esquivam; as setas não andam com ela aberta, porque são da grade. Q e Tab miram.
- **Mouse:** fica com a mochila. O cursor fica solto, a câmera não gira, os botões não atacam nem levantam o escudo e a
  roda sobre o painel rola a grade (fora dele, aproxima a câmera).
- **E:** continua usando o mundo. Conversar fecha a mochila.
- **O que a fecha:** a conversa, o diário (J), a pausa e a derrota. Caído, ela não abre.
- **Na tela:** o rastreador de missão some enquanto ela está aberta (ocuparia o mesmo canto), e a placa de um objeto do
  mundo que cairia sob o painel não aparece.
- **A cataplasma:** continua só fora da luta. Aberta no meio da luta, o fungo continua e o detalhe diz o motivo.
- **Usar é uma ação da LÓGICA,** uma vez por pedido; o desenho só lê o estado.
- **Não abre** antes de começar, no meio de uma conversa nem fora de uma cena com jogo.
- **Som:** os eventos aprovados da interface (abrir, cancelar, selecionar, confirmar) e o de recompensa para o item
  recebido. Passar o mouse é mudo.

**O item recebido** aparece num cartão sob o aviso do canto (o que entrou, com os modelos, ou "Mochila cheia" no teto
técnico).

## Os itens desenhados em 3D (`vx.game.ItemRenders`)

- **Modelos:** gerados por `tools/itens_e.py` (`assets/items/e/*.vox` + `itens.json`); veja [itens](itens.md).
- **Mesmo renderizador da cena, com fundo transparente** (`Renderer.Toggles.alpha`): sem céu, e o alfa vem da cobertura e
  do contorno.
  - Desligado, o renderizador dá o resultado de sempre: as referências e o gate dos presets conferem.
  - O `Renderer` é próprio dos itens, para nunca redimensionar os alvos do mundo.
- **Atlas:** uma célula de 192 px por modelo, em 3/4 de frente, enquadrada pela caixa do modelo (`fit`). É feito uma vez,
  quando a mochila ou o cartão aparece pela primeira vez (~70 ms). A grade e o cartão são quads desse atlas, num lote só.
- **Prévia:** um FBO de 384 px, refeito só com a mochila aberta e um item selecionado, enquadrado para girar sem cortar.
- **Ordem do quadro:** atlas e prévia são renderizados antes da interface (`Game.prepareItems`), nunca no meio do lote
  2D. Quem cria fecha (`ItemRenders.close`); `itens-recursos` nos roteiros mostra os FBOs e as malhas vivos.

## Testes

- `InventoryTest`:
  - empilhar, separar, tirar, ordem estável e compactação;
  - **sem limite** (mil pilhas) e o teto técnico com tudo ou nada;
  - JSON, recusas e o save da rodada 13 com buracos.
- `InventoryFlowTest`:
  - o jogo correndo com ela aberta (anda, o tempo passa, a câmera não gira, nada de golpe nem escudo, o pulo funciona);
  - setas × WASD e o Enter;
  - o seletor pelo mouse, o clique que usa e o clique direito;
  - as abas e o que a fecha;
  - o fungo continua na luta e a cataplasma não se aplica;
  - o salvamento automático com ela aberta;
  - as fontes, a cura, o save, o continuar, os saves antigos e as aventuras isoladas.
- `ItemCatalogTest`:
  - todo item tem modelo gerado;
  - os modelos são objetos de mão (voxel 1/40 u, até 0,4 u, apoiados no chão, custo pequeno) com silhuetas distintas.
- Roteiros:
  - `tools/itens/03-resolucoes.txt`: as resoluções oficiais, as abas, a grade rolada, o cartão, a luta, o diário e a
    pausa;
  - `desempenho-andando-*.txt` e `desempenho-mochila.txt`: o custo;
  - `tools/review131.sh`.
