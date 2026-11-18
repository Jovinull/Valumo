# 0015 — Áudio com OpenAL Soft e stb_vorbis (módulos do LWJGL) (rodada 09)

**Status:** aceita (rodada 09). Os sons e as músicas que passam por aqui são ESTUDOS: a aprovação artística é
do proprietário.

## Contexto

Até a rodada 08 o Valumo não tinha áudio nenhum (nem código, nem arquivos). A rodada 09 pediu música, efeitos
sonoros e uma integração mínima no jogo e no criador, sem game engine e sem biblioteca grande.

O que a integração precisa:
- tocar vários efeitos curtos ao mesmo tempo, com volume e tom por voz;
- tocar música longa sem carregar tudo descomprimido, com laço exato;
- funcionar sem dispositivo de áudio (o jogo segue mudo) e ser testável sem placa de som;
- distribuição em Linux sem instalar nada no sistema.

## Decisão

- **OpenAL Soft pelo `lwjgl-openal`** (mesma versão do BOM do LWJGL, 3.4.3). Os natives `natives-linux` trazem o
  OpenAL Soft embutido: nada de pacote do sistema. No Linux ele sai pelo PipeWire/PulseAudio/ALSA sozinho.
- **stb_vorbis pelo `lwjgl-stb`** para decodificar OGG Vorbis em fluxo (música). A ADR 0001 tinha descartado o
  `lwjgl-stb` *para imagens e fonte* (o JDK resolve); para Vorbis não há alternativa no JDK.
- **WAV PCM 16 bits** para efeitos, lido por um leitor próprio (`Pcm.readWav`, ~40 linhas).
- Separação (pacote `vx.engine.audio`):
  - `SoundCatalog` lê `assets/audio/sons.json` (escrito à mão, contrato `valumo.sons` v1): eventos semânticos →
    arquivos, ganho, variação, intervalo mínimo, limite de vozes, prioridade, barramento; músicas com laço.
  - `SoundSystem`: regras (variação sem repetir, limites, barramentos, transição de música), sem OpenAL.
  - `AudioOut`: `OpenAlOut` (dispositivo ou **loopback** `ALC_SOFT_loopback`) e `SilentOut`.
  - `Audio`: ponto único no processo; enquanto ninguém chama `start`, tudo é mudo e sem custo.
- O jogo (`game`) e o criador (`viewer`) só emitem eventos (`passo.grama.andar`, `ui.confirmar`); nenhum caminho
  de arquivo, buffer ou dispositivo fora do `engine`.

## Alternativas descartadas

- **`javax.sound.sampled`** (JDK): sem mixagem de muitas vozes com tom por voz, latência alta e sem modo de
  renderização em memória para testes.
- **Biblioteca de áudio completa** (FMOD, Wwise, miniaudio por JNI própria): dependência grande, licença ou
  binding próprio — contra a regra de dependências mínimas.
- **Só WAV também para música**: ~11 MB por minuto de música estéreo 24 bits; o OGG q5 fica em ~1,7 MB/min.

## Consequências

- Duas dependências novas (módulos do LWJGL já usado), sem código nativo próprio.
- Testes de áudio rodam sem dispositivo: regras com saída falsa; mixagem real pelo loopback do OpenAL Soft (se a
  máquina não tiver o OpenAL, o teste de loopback é pulado, os outros não).
- Roteiros e capturas continuam mudos; com `-Dvx.audio=loopback -Dvx.audio.gravar=x.wav` a mixagem do jogo é
  gravada em sincronia com os quadros (amostra a amostra com o tempo simulado do roteiro).
- O OpenAL Soft aplica *dither* na saída de 16 bits: o "silêncio" do loopback é ±1 LSB.
- Áudio posicional: a saída já usa buffers mono para efeitos (requisito do OpenAL para posição), mas toda voz
  hoje toca relativa ao ouvinte (herói e interface). Posição no mundo fica para quando houver fonte sonora
  fora do herói (NPC, criatura, ambiente).
