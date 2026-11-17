# 0012 — Interface do jogo na engine própria (rodada 07.2)

**Status:** aceita (rodada 07.2; aprovação artística pendente com o proprietário).

## Contexto

O viewer só tinha o HUD de depuração (fonte bitmap 5×7 só com maiúsculas, retângulos) e uma leitura de
teclado e mouse por quadro, sem eventos de clique nem conversão para pixels do framebuffer. O criador de
personagens do jogador precisa de controles clicáveis, texto em português com acentos, miniaturas e
composição que não trave o quadro.

## Decisão

- **Sem biblioteca de UI.** Um lote de quads 2D (`UiBatch`, VBO persistente) e o layout/estado em classes do
  viewer, testáveis sem OpenGL. Nada de framework genérico: só os controles que o criador usa.
- **Fonte própria de pixel** (`PixelFont`), desenhada no código como os ícones, com os acentos do português
  compostos. Sem fonte de terceiros no repositório. Escala sempre inteira (traço nítido); o layout escolhe o
  tamanho que cabe.
- **Fila de eventos na janela** (`Window.InputEvent`), em pixels do framebuffer (HiDPI), com `inject` para
  roteiros: capturas e vídeos usam o mesmo caminho da entrada real.
- **Composição fora da thread do GL** (`AsyncComposer`): CPU numa thread de trabalho, envio na thread do GL,
  gerações para descartar pedidos obsoletos, LRU com liberação das malhas.

## Consequências

- Nenhuma dependência nova (só LWJGL, JOML, Gson).
- Telas futuras (inventário, diálogos, HUD de jogo) reaproveitam `UiBatch`, `PixelFont`, a fila de eventos e o
  procedimento de layout testável + roteiro de captura (skill `game-ui`).
- A fonte de pixel limita a tipografia a um estilo; tamanhos intermediários não existem (720p usa 1×).
- O editor técnico da rodada 07 (HUD 5×7) continua como modo de depuração.
