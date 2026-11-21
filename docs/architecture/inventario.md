# Inventário (rodada 13)

A mochila da aventura: 24 espaços, a tela e as regras. Os itens estão em [itens](itens.md); as fontes, em
[recompensas e loot](recompensas-e-loot.md); o save, em [save](save.md) §Rodada 13.

## O contrato (`vx.game.item.Inventory`)

- **24 espaços** (`SLOTS`), cada um vazio ou com uma `ItemStack` (o item e de 1 a `pilha` unidades).
- **Entregar** (`add`) é **tudo ou nada**:
  - confere numa cópia antes de mudar;
  - um pedido de duas coisas em que só uma cabe não entrega nenhuma.
- **Onde entra** (determinístico):
  1. completa as pilhas do mesmo item, na ordem dos espaços;
  2. o resto vai para os espaços vazios, em ordem, cada um até a pilha.

  A mesma sequência de coletas dá sempre a mesma mochila.
- **Tirar** (`remove`): só o que existe; o espaço vazio fica vazio, e os outros não andam.
- **Números inválidos** (zero, negativo, acima da pilha, mais que 9999 num pedido) são recusados.

## A tela

**Como abrir:**

- `I`, na exploração;
- o botão **Inventário** da pausa (sem dica de tecla na tela; a pausa é por onde o jogador descobre a mochila).

**Como fechar:** `I`, `Esc` ou **Fechar**.

**O que tem:**

- **No alto:** o título, a vida (corações) e as moedas.
- **À esquerda:** a grade 6 × 4, com o ícone e a quantidade de cada espaço; o espaço escolhido em latão, com o acento P1.
- **À direita**, o item escolhido:
  - ícone grande, nome e categoria;
  - quantidade e pilha, ou "Peça única";
  - descrição;
  - o botão de uso, com o motivo quando não dá agora.
- **Espaço vazio:** "Espaço vazio" e uma frase curta.

**Como se escolhe:** setas ou WASD, clique num espaço; usar = Enter, E, Espaço ou o botão.

**Desenho:**

- **Ferramentas:** o `UiKit` e a fonte de pixel de sempre, com os ícones de pixel de `ItemIcons`; nada de textura
  nova nem framework.
- **Família:** a mesma do diário e da pausa (capturas em `round-13/capturas/`).

**Estado do jogo** (`GameLogic.State.INVENTORY`): como o diário, **a lógica para**.

- **Nada se move:** o corpo não anda, a luta não corre escondida e a câmera não gira com o mouse.
- **Clique não é golpe:** o clique seleciona espaço, nunca ataca, e ao fechar o cursor volta a ser capturado sem disparar
  golpe.
- **Usar é uma ação da LÓGICA:** `logic.action("usar-item")`, uma vez por pedido; o desenho só lê o estado.
- **Não abre** antes de começar, na pausa (só pelo botão) nem fora de uma cena com jogo.
- **Som:** os eventos aprovados da interface (abrir, cancelar, selecionar, confirmar) e o de recompensa para o item
  recebido.

**O item recebido** aparece num cartão sob o aviso do canto (o que entrou, com os ícones, ou "Mochila cheia").

## Testes

- `InventoryTest`: empilhar, separar, encher, tudo ou nada, tirar, ordem estável, JSON e recusas.
- `InventoryFlowTest`:
  - abrir e fechar sem andar, atacar nem deixar tecla presa;
  - a seleção e as fontes;
  - a cura e a luta;
  - o save, o continuar e os saves antigos;
  - as aventuras isoladas.
- Roteiro `tools/itens/03-resolucoes.txt` (as quatro resoluções oficiais, a mochila cheia, o diário e a pausa).
