---
name: game-ui
description: Construir ou alterar interfaces de jogo do Valumo na engine própria — lote 2D (UiBatch), fonte de pixel própria (PixelFont/FontAtlas), fila de eventos da janela em pixels do framebuffer, layout e estado testáveis sem OpenGL, estados de controle, escala por resolução, composição assíncrona e revisão por roteiro de entrada. Use ao criar uma tela (criador, inventário, diálogo, HUD de jogo) ou ao mexer no criador de personagens.
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

## Rodada 09

- Fileira "rótulo … [−] valor [+]" (`Game.stepperRow`): rótulo com `textPx` (escala inteira que cabe à esquerda dos
  botões) e valor centrado; usada por sensibilidade e volumes. Em 720p o rótulo antigo era atravessado pelo "−".
- Miniaturas: enquadramentos `olhos` (close na linha dos olhos) e `pes` (botas) em `App.renderThumb`; escolha pelo
  prefixo do id em `CreatorThumbs.framing`. Miniatura que corta o que se escolhe é inútil — confira em tamanho real.
- Conteúdo cresceu → amplie só o necessário: 3 colunas de penteado com mais de 4; na variante B (pouca altura) o
  formato dos olhos vira botões de alternância sob um título único. O `CreatorTest` (todas as resoluções e variantes)
  pega o que sai da tela.
- Roteiros sem `record on` não desenham: use `settle` antes de `click` em botão que acabou de aparecer.
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
