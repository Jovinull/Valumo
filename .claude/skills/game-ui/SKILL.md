---
name: game-ui
description: Construir ou alterar interfaces de jogo do Valumo na engine própria — lote 2D (UiBatch), fonte de pixel própria (PixelFont/FontAtlas), fila de eventos da janela em pixels do framebuffer, layout e estado testáveis sem OpenGL, estados de controle, escala por resolução, composição assíncrona e revisão por roteiro de entrada. Use ao criar uma tela (criador, inventário, diálogo, HUD de jogo) ou ao mexer no criador de personagens. Rodada 11.1: menus da porta de entrada (MenuFlow sem GL + MenuView), campos de texto, modais exclusivos, roteiros tools/menu.
---

# Interfaces de jogo (rodada 07.2 em diante)

Referência completa e exemplo vivo: `docs/architecture/criador-de-personagens.md` (criador de personagens).
Decisão: `docs/decisions/0012-interface-do-jogo.md`.

## Regras

- **Sem biblioteca de UI e sem fonte de terceiros.** Desenho com `UiBatch` (quads coloridos/texturizados),
  texto com `FontAtlas` sobre a `PixelFont` (letra nova = bitmap novo nela; as setas e o "·" entraram na 08).
  Ícones de pixel no código.
- **Peças compartilhadas:** `UiKit` (viewer) tem painel de madeira, chanfro, texto, semente P1, marca, cursor e
  recorte. O criador e o jogo usam as mesmas; mudou uma, confira as capturas dos dois (as do criador saíram
  idênticas pixel a pixel na extração).
- **Nada falso.** Todo controle visível funciona; opção que não existe não aparece; miniatura é o modelo real
  renderizado. Nada de métrica, ID técnico ou hex na tela do jogador (isso fica no modo de depuração).
- **Separar as peças.**
  - Estado (sem GL) e layout (só geometria, sem GL, testável).
  - Controlador: consome `Window.events()`.
  - Desenho e recursos (texturas/FBOs).
  - Dentro do `App`, só ganchos (`overlay`, `renderFrame`).
- **Escala.** Pixels do framebuffer × `s = min(L/1920, A/1080)` limitado; nunca coordenada fixa. Texto em
  escala inteira escolhida pelo layout (`textPx`), igual na fileira; só depois "…".
- **Entrada.**
  - Clique = apertar e soltar no mesmo controle.
  - Arrasto captura até soltar.
  - A roda depende da região (painel rola, cena dá zoom).
  - Modal bloqueia o resto.
  - Sempre há alternativa de teclado.
- **Estados visíveis:** hover, selecionado, pressionado, indisponível (com motivo na dica), carregando.
- **Trabalho pesado fora da thread do GL** (padrão `AsyncComposer`: gerações, descarte, LRU que libera). Na
  thread do GL, no máximo um trabalho caro por quadro (ex.: uma miniatura).
- **Recursos de GPU:** VBO persistente para a UI; nada criado e destruído por quadro; feche FBOs e texturas
  no `close()`.
- Paleta da E (madeira, pergaminho, dourado, musgo), P1 só como acento; nada copiado de outros jogos.

## Procedimento

1. Estado + layout + controlador sem GL; teste com eventos sintéticos (`Window.InputEvent`):
   - hitboxes no centro de cada controle;
   - nada fora da tela nem sobreposto em 720p, 1080p e ultrawide;
   - texto cabe;
   - clique, arrasto, roda e teclado;
   - diálogos e redimensionamento.
2. Desenho; capture com um roteiro (`viewer --editor-script ROTEIRO SAIDA`, comandos no doc). Os eventos entram
   na mesma fila do GLFW: é a entrada real, não chamada interna.
3. Olhe em tamanho real; corrija; repita. Então vídeo do fluxo (`record on`) e medição (`perf on`/`off`).
4. Teste de fumaça da janela real: `viewer --editor --demo 400` (abre, desenha, imprime FPS).
5. Documente no doc da tela; atualize o checklist "Interface do jogador".

## Rodada 09.4

- Fileira "rótulo … [−] valor [+]" (`Game.stepperRow`): rótulo com `textPx` (escala inteira que cabe à esquerda dos
  botões) e valor centrado; usada por sensibilidade e volumes. Em 720p o rótulo antigo era atravessado pelo "−".
- Miniaturas: enquadramentos `olhos` (close na linha dos olhos) e `pes` (botas) em `App.renderThumb`; escolha pelo
  prefixo do id em `CreatorThumbs.framing`. Miniatura que corta o que se escolhe é inútil — confira em tamanho real.
- Conteúdo cresceu → amplie só o necessário: 3 colunas de penteado com mais de 4; na variante B (pouca altura) o
  formato dos olhos vira botões de alternância sob um título único. O `CreatorTest` (todas as resoluções e variantes)
  pega o que sai da tela.
- Roteiros sem `record on` não desenham: use `settle` antes de `click` em botão que acabou de aparecer.
## Rodada 11.1 — menus da porta de entrada (`docs/architecture/menus.md`)

- **Separação:**
  - regras em `vx.game.menu.MenuFlow` (sem GL, testável);
  - desenho e recursos de GL em `MenuView` (fecha em `close()`);
  - pedidos que exigem GL ou o laço da janela cumpridos pelo `Game` (`done`/`failed`).
- **Botão novo:** `g.button(id, …)` na tela + o `id` em `MenuFlow.action` (ou `Game.menuClick` para `cfg:*`) + o caso no
  `MenuFlowTest`.
- **Tela nova:**
  1. Valor em `Screen`.
  2. Chegada em `action`, volta em `back()`.
  3. `drawX` + `case` em `MenuView.draw`.
  4. Roteiro de captura em `tools/menu/`.
- **Modal:** limpe `g.buttons` antes de desenhar o modal; senão os botões de baixo recebem o clique (aconteceu: o clique
  no campo da semente caiu num cartão da lista).
- **Campo de texto:**
  - eventos `CHAR` para `MenuFlow.chr` e Backspace/Tab/Enter/Esc para `MenuFlow.key`;
  - enquanto `typing()`, nenhuma tecla de jogo vale, nem o M de mudo;
  - a semente só aceita algarismos;
  - o fim do texto fica visível e o cursor pisca.
- **Quebra de linha:** mesma fonte na medida e no desenho. `g.wrap` mede na regular: desenhar em negrito o que foi
  quebrado na regular invade a coluna vizinha (aconteceu com a mensagem de erro).
- **Avisos:** no topo, onde não cobrem plaqueta nem botões (embaixo cobriam o nome do personagem).
- **Dica de teclas:** não ponha `controlsHint` onde há painel até o rodapé: cobre os botões. Os botões já dizem a tecla.
- **Roteiros:** `click` desenha um quadro antes de procurar o botão. `digitar`, `arrastar`, `esperar-menu`,
  `menu-estado`, `menu-mundo`, `menu-tomada`, `menu-leiaute`, `menu-recursos`.
- **Resoluções:** capture 720p, 1080p, 16:10 e 21:9 (`tools/menu/07-resolucoes.txt`); o menu principal tem os leiautes
  A (oficial) e B (só comparação: `-Dvx.menu.layout=B`).

## Texto na tela (decisão do proprietário, 11.1)

Nenhuma dica de tecla em lugar nenhum: nada de linha "↑ ↓ escolher · Enter confirmar · mouse", "(Esc)"/"(Enter)"/"(E)"
em botão, tecla na placa de interação, "arraste para girar". Nada de texto de desenvolvimento (rodada, legado, versão do
gerador, nome de arquivo). Espera = indicador sem texto (`MenuView.waiting`).

## Fechamento da 11.1 — tipografia, carregamento, X

- **Grupos de botões:** escala comum (`g.groupPx = g.groupPxFrom(base, rótulo, largura, …)` antes do grupo; `0` depois).
  - Base 30 no menu principal, `MenuView.MENU_BUTTON` (27) nas telas de menu, 22 na pausa.
  - Se um par lado a lado encolheria o grupo, empilhe (personagens).
  - Nunca deixe um botão escolher sozinho a escala ao lado de outro do mesmo grupo.
- **Carregamento:** logotipo pequeno + passo real + sementes P1 (`MenuView.waiting`); sem porcentagem, sem cena 3D.
  - O passo pesado só roda depois de um quadro com o rótulo NA TELA (`labelShown`).
  - Roteiro com `wait` não prova isso; grave os quadros (`record on`) e procure o rótulo.
- **Modais e confirmações:** a confirmação de saída é uma só (pausa e X da janela).
- **Roteiros:** `fechar-janela` (mesmo caminho do callback do GLFW), `shot-agora` (captura sem esperar),
  `menu-personagem NOME`.

## Defeitos já vistos (não repita)

| Defeito | Causa | Correção |
|---|---|---|
| Marca solta sob letras | acento de maiúscula invadindo a célula de cima no atlas | célula com a altura máxima + folga entre linhas |
| Listras no degradê | faixas com 1 px de sobreposição dobrando o alfa | faixas inteiras, sem sobreposição |
| Texto saindo do botão em 720p | escala inteira saltando entre tamanhos vizinhos | layout escolhe o passo que cabe, igual na fileira |
| Botão da vitrine minúsculo | construtor retornava antes de dimensionar textos | dimensionar em todo caminho; teste da vitrine |
| Pico de 80 ms ao trocar cor | 4 miniaturas renderizadas no mesmo quadro | uma por quadro |
| Câmera atravessando o cenário | órbita da câmera | girar o personagem; câmera no ângulo composto da cena |
| Teste de rolagem que nunca rolava | `if (conteúdo > painel)` em caso que cabia | exigir a pré-condição com assert |
