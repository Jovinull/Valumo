# 0016 — Áudio: OpenAL Soft pelo LWJGL, eventos lidos da lógica e efeitos sintetizados por script (rodada 09.1)

**Status:** aceita (rodada 09.1). Complementa o [ADR 0001](0001-stack-java-lwjgl.md) (stack), o
[ADR 0002](0002-assets-por-script.md) (assets por script) e o [ADR 0015](0015-combate-por-trajetoria-e-passo-fixo.md)
(passo fixo). Detalhes: [audio.md](../architecture/audio.md).

## Contexto

O combate da rodada 09 funcionava sem som, e sem som não dava para avaliar o peso e a resposta dos golpes. Havia
quatro riscos:

1. **Dependência e engine:** o projeto proíbe game engine e exige ADR para dependência nova.
2. **Determinismo:** a lógica é idêntica a 30, 60, 120 e 240 FPS (ADR 0015). Um sistema de som que decidisse algo, ou
   que fosse chamado de dentro das regras, poderia mudar a luta.
3. **Máquinas sem som:** testes, integração contínua e computadores sem dispositivo não podem quebrar.
4. **Origem dos sons:** nada de terceiros sem licença verificada, nada de outros jogos, nada de IA geradora como atalho.

## Decisão

1. **Saída: OpenAL Soft pelo módulo `lwjgl-openal`.**
   - É o mesmo projeto e a mesma versão do LWJGL já adotado (3.4.3), não um middleware novo.
   - O jar de *natives* traz a biblioteca dinâmica do OpenAL Soft (LGPL 2.0+), carregada em tempo de execução.
   - O repositório não inclui o código do OpenAL Soft.
   - Uma camada pequena na engine (`vx.engine.audio`):
     - `AudioBackend` (interface);
     - `OpenAlBackend` (dispositivo real ou *loopback*);
     - `SilentBackend` (sem som);
     - `Wav`.
   - Não há streaming, música, DSP em tempo real nem reverb.
2. **O som só lê a lógica.**
   - Um diretor (`vx.game.CombatAudio`) observa o confronto **depois** de cada passo lógico e pede efeitos a `Sfx`.
   - Nada no som escreve em `Combat`, `Hero`, `Enemy` ou `Player`.
   - A variação (arquivo, tom) vem de um hash de (semente, efeito, passo): é determinística e fica só na apresentação.
   - **Teste:** a mesma luta com e sem som é idêntica, e os sons são os mesmos a 30, 60, 120 e 240 FPS.
3. **Falha segura.**
   - Qualquer erro ao abrir o OpenAL (sem dispositivo, biblioteca ausente, contexto recusado) vira `SilentBackend` com
     o motivo no console.
   - Os roteiros de captura usam o modo mudo por padrão.
   - Os testes usam um backend registrador.
4. **Sons sintetizados por script** (`tools/audio_e.py`), como os voxels (ADR 0002).
   - São determinísticos, editáveis e revisáveis por números (pico, RMS, centroide, cortes, DC) e por prancha de
     espectrograma.
   - Saem como WAV mono de 48 kHz, gerados (fora do git) em `assets/audio/`.
   - A mistura é escrita à mão em `assets/scenes/sons_e.json`.
5. **Gravação com o som da aplicação:**
   - Os roteiros podem usar o dispositivo *loopback* do OpenAL Soft (extensão `ALC_SOFT_loopback`, `-Dvx.som=loopback`).
   - O mesmo mixer da aplicação mixa, a cada quadro gravado, exatamente o tempo de jogo daquele quadro.
   - O vídeo leva o som que o jogo produziu, sincronizado com o relógio do jogo.

## Consequências

- **Determinismo preservado:** com teste.
- **Licença:** o jogo depende de uma biblioteca LGPL carregada dinamicamente. Isso é compatível com a distribuição como
  está; se um dia o OpenAL Soft for embutido de outra forma, a licença precisa ser revista.
- **Latência de pedido:** cada som custa ~0,2 ms na thread do jogo, medido em luta real, e ~3 µs em regime. A mixagem é
  na thread do OpenAL.
- **Dispositivo:** o dispositivo informa 12–20 ms de latência de saída (PipeWire, nesta máquina).
- **Fora do escopo:** música, ambiência e superfícies sonoras. A arquitetura aceita um canal de música/ambiente depois,
  mas não foi criado sem conteúdo.
- **Sensação:** um som feito por síntese pode soar "eletrônico". A revisão por números e espectrograma não substitui o
  ouvido do proprietário: a aprovação do timbre é dele, no teste físico.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| `javax.sound` (Java Sound) | sem posicionamento 3D, latência alta e inconsistente no Linux, sem mixer próprio confiável |
| Middleware (FMOD, Wwise) | licença comercial, binários fechados, contra a regra de não usar engine/middleware |
| Amostras de bancos livres | verificação de licença caso a caso, proveniência difícil de manter e controle fino menor que a síntese |
| Gerador de áudio por IA | proibido como atalho (direção do proprietário) e não reproduzível |
