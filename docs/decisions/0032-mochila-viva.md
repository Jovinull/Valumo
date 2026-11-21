# 0032 — A mochila viva: sobre o jogo correndo, sem limite de espaços, itens pelos modelos voxel

**Status:** aceita e implementada na rodada 13.1 (pedido do proprietário depois do teste da rodada 13). Substitui três
pontos da [ADR 0031](0031-itens-e-recompensas.md): a mochila de 24 espaços, a tela que pausa a lógica e os ícones de
pixel. O resto da 0031 continua: itens são dados, efeitos são código, fontes como camada sobre os mundos congelados, uma
vez por aventura, tudo ou nada, campos opcionais no save.

## Contexto

O proprietário testou a rodada 13 e aprovou as fontes, a cataplasma e o save. Reprovou a mochila:

- o painel de pergaminho opaco, que esconde o mundo;
- a lógica parada com ela aberta;
- os ícones de pixel 14 × 14, pouco reconhecíveis;
- o limite de 24 espaços.

O pedido: um painel translúcido sobre o jogo que continua correndo, sem limite de espaços, com o seletor seguindo o
mouse e os itens mais reconhecíveis.

## Decisão

1. **A mochila é uma CAMADA sobre a exploração** (`GameLogic.bagOpen`), não um estado do jogo. Tudo o que roda na
   exploração continua rodando com ela aberta:
   - os passos fixos, o herói, os inimigos;
   - o salvamento automático, as descobertas, o som.

   O mouse passa a ser dela:
   - o cursor fica solto e a câmera não gira;
   - os botões não atacam nem defendem.

   O teclado continua no herói:
   - WASD, Shift, Espaço e Alt andam, correm, pulam e esquivam;
   - E conversa e usa o mundo.

   Só as setas e o Enter ficam com a grade. A conversa, o diário, a pausa e a derrota fecham a mochila.
2. **O seletor segue o mouse** (`pairar:i`). Clicar no item (`clique:i`) usa o que se usa e só seleciona o resto. O clique
   direito (`usar:i`), o Enter e a etiqueta de uso no detalhe também usam. Usar continua sendo uma ação da lógica.
3. **Sem limite de espaços.** A mochila é uma lista de pilhas na ordem de chegada:
   - cada pilha vai até a pilha do item;
   - a lista se compacta quando uma pilha acaba;
   - a grade rola.

   O único teto é técnico (`Inventory.MAX_STACKS` = 4096), para o leitor de save nunca alocar sem limite. O "mochila
   cheia" da 0031 só existe nele.
4. **Os itens são modelos voxel** (`tools/itens_e.py` → `assets/items/e/`, voxel 1/40 u, até 0,4 u). Eles são desenhados
   pelo mesmo renderizador da cena, com fundo transparente (`Renderer.Toggles.alpha`):
   - **na grade e no cartão:** um atlas feito uma vez;
   - **no detalhe:** uma prévia girando (o modelo gira; a luz fica).

   O modo transparente desligado dá o resultado de sempre, pixel a pixel.
5. **O save não muda de formato.**
   - **Mesmos campos:** os mesmos opcionais da 0031; `espaco` passa a ser a posição na lista.
   - **Saves da rodada 13:** os 24 espaços, com buracos, abrem compactados na mesma ordem.
   - **Saves sem itens:** continuam iguais aos da v0.6.

## Consequências

- **O jogo não para:** abrir a mochila no meio da luta é uma escolha do jogador, e o fungo continua. A cataplasma continua
  só fora da luta, a mesma regra da 0031.
- **O painel fica na metade direita** (o herói fica no meio da tela). O rastreador de missão some enquanto ela está
  aberta, e uma placa do mundo que cairia sob o painel não aparece.
- **Custo:** o atlas custa ~70 ms uma vez. A prévia é um passo do renderizador num FBO de 384 px por quadro, só com a
  mochila aberta e um item selecionado, medida em `round-13-1/desempenho/`.
- **Arte:** os modelos são a primeira versão, escritos em código como os outros assets. A arte final segue o
  [contrato do artista](../art/contrato-icones-de-itens.md), agora de modelos voxel.
- **Pilhas:** maiores, porque o limite de espaços não existe mais: 99 para os materiais e o achado, 20 para a cataplasma
  e 1 para o caderno.
