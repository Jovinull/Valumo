# 0023 — Áudio com OpenAL Soft e stb_vorbis (módulos do LWJGL) (rodada 09.4)

**Status:** aceita (rodada 09.4). Os sons e as músicas que passam por aqui são ESTUDOS: a aprovação artística é
do proprietário.

## Contexto

Até a rodada 08 o Valumo não tinha áudio nenhum (nem código, nem arquivos). A rodada 09.4 pediu música, efeitos
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

## Adendo — integração com a linha do combate (09.1–09.3)

Esta rodada foi desenvolvida em paralelo ("rodada 09") enquanto a linha do combate criava o próprio som
([ADR 0016](0016-audio-openal-e-sons-sintetizados.md)). Na integração:

1. **Um dispositivo por processo.** Só o `Audio` abre o OpenAL (dispositivo, loopback ou mudo).
   - Os efeitos do combate (`vx.game.Sfx`) criam as vozes deles no MESMO contexto: `OpenAlBackend.shared(Audio.output())`.
   - Sem catálogo (`assets/audio/sons.json` ausente), o `Sfx` abre o seu, como na 09.1.
   - Motivo: dois dispositivos são duas misturas, duas gravações e dois relógios. No loopback, misturar duas vezes por
     quadro faria a gravação correr no dobro do tempo.
2. **Volume principal do combate por voz**, não no ouvinte: o `AL_GAIN` do ouvinte é de todo o contexto e baixaria a
   música junto.
3. **Um ouvinte:** a câmera, olhando para o pivô (o herói), como na 09.1. O `GameAudio` usa o mesmo, então não há dois
   ouvintes disputando o contexto.
4. **Passos:** os do catálogo (quatro superfícies × andar/correr, `Player.steps`). O passo único da 09.1 (`Footsteps`,
   `passo_terra`) só age sem catálogo.
5. **Modo:** `-Dvx.som` (linha do combate) e `-Dvx.audio` (esta rodada) valem igual: `dispositivo`, `loopback` ou
   `mudo`. Roteiro com loopback grava `SAIDA/audio.wav` (ou `-Dvx.audio.gravar`), uma mistura por quadro, em sincronia
   exata com os quadros (medido: 6,900 s para 207 quadros; 14,40 s para 432).
6. **Arquivos:**
   - os do catálogo ficam versionados em `assets/audio/`;
   - os efeitos do combate gerados por `tools/audio_e.py` vão para `assets/audio/sintese/`, fora do git.
7. **Preferências:** volumes em `-Dvx.prefs.audio` (padrão `output/preferencias/audio.json`); `-Dvx.prefs=-` desliga.
   Antes, a mesma propriedade `vx.prefs` era o caminho do arquivo aqui e "nenhum" na linha do combate.
8. **Pausa:** "Música" e "Efeitos" (efeitos = combate, passos, interface e ambiente) e o mudo (M), que desliga tudo.

**Origem dos sons:** o ADR 0016 diz "só síntese, nada de terceiros". Os efeitos de passo e o ambiente desta rodada são
síntese (Kicker do LMMS). A **música e parte da interface usam amostras do banco FluidR3 GM** (licença MIT, de terceiros).
Isso é uma **decisão pendente do proprietário**: aceitar o banco (e registrar a exceção) ou trocar por síntese própria.
Ver `problemas-conhecidos.md`.
