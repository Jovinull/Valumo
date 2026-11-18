# Áudio — efeitos sonoros do combate (rodada 09.1; esquiva, salto, pouso e desfazer na 09.2)

Primeiro som do Valumo: efeitos que melhoram a leitura das ações da luta e passos discretos. Não há música nem
ambiência. Decisões estruturais: [ADR 0016](../decisions/0016-audio-openal-e-sons-sintetizados.md).

```bash
./gradlew jogo                                       # som no dispositivo padrão; M liga/desliga; volume na pausa (Esc)
python3 tools/audio_e.py                             # regenera os efeitos (o Gradle faz sozinho antes de run/test)
python3 tools/audio_board.py saida.png               # prancha de revisão: forma de onda + espectrograma + números
tools/review91.sh sons                               # catálogo: arquivos isolados, faixa com todos, prancha
tools/review91.sh videos                             # vídeos com o som GRAVADO PELA APLICAÇÃO + relatório de sincronia
JAVA_OPTS=-Dvx.som=loopback game/build/install/game/bin/game --roteiro R.txt SAIDA 30   # grava SAIDA/audio.wav
```

## 1. Peças

| Peça | Onde | Faz |
|---|---|---|
| `AudioBackend` | engine `vx.engine.audio` | interface da saída: carregar, tocar, parar, pausar, ouvinte, ganho |
| `OpenAlBackend` | engine | OpenAL Soft: 24 vozes fixas, atenuação pela distância; dispositivo real ou *loopback* (gravação) |
| `SilentBackend` | engine | sem dispositivo, falha ou modo mudo: aceita tudo, não toca nada |
| `Wav` | engine | lê PCM 16 bits, escreve a gravação do loopback |
| `Sfx` | game | mistura (`sons_e.json`), pré-carga, regras de reprodução, volumes, mudo, pausa, registro do que tocou |
| `CombatAudio` | game | diretor: depois de cada passo lógico, olha o que mudou no confronto e pede os efeitos |
| `Footsteps` | game | passos no pouso de cada pé (mesma fase de animação do desenho) |
| `tools/audio_e.py` | tools | síntese de todos os efeitos (a fonte editável) → `assets/audio/*.wav` + `variantes.json` + `analise.json` |
| `assets/scenes/sons_e.json` | escrito à mão | ganho, espacial, variação de tom, vozes e intervalo por efeito; volumes iniciais |

## 2. Fluxo (e por que o som não muda a luta)

```
passo lógico (1/120 s): combat.tick(...)  →  CombatAudio.afterTick(combat)  →  Sfx.cue(efeito, passo, posição)  →  backend.play
                         (regras)             (só LÊ estado e listas)          (regras de reprodução)              (OpenAL)
```

- **Só leitura:** o diretor guarda o que viu da última vez (estado do fungo, golpe do herói, quantos impactos, resultado) e
  pede som quando algo muda. Ele **não escreve** em nada da lógica.
- **Sem RNG da lógica:** a variação de arquivo e de tom vem de um hash (semente, efeito, passo).
- **Teste** (`CombatAudioTest.soundNeverChangesTheFightAndIsTheSameAtAnyFrameRate`):
  - a luta com som é idêntica à luta sem som;
  - a lista de sons é a mesma a 30, 60, 120 e 240 FPS.

| Efeito | Quando | Onde soa |
|---|---|---|
| `golpe_ar_h` / `_d` / `_v` | golpe em andamento: o **pico** do arquivo cai no **meio da janela de dano** (lâmina mais rápida). Golpe interrompido antes: sem "fuu" | colado ao ouvinte |
| `golpe_acerto` + `fungo_dano` | acerto confirmado pela lógica (um por golpe), no passo do contato | acerto colado (clareza); fungo no fungo |
| `escudo_bloqueio` | cabeçada bloqueada | colado |
| `cabecada_acerto` + `heroi_dano` | cabeçada que feriu o herói | colado |
| `fungo_alerta` | repouso/volta → alerta | no fungo |
| `fungo_preparo` | entrou na preparação; o som dura a preparação (0,70 s) e **para** se ela for interrompida (golpe, morte) | no fungo |
| `fungo_cabecada` | a cabeçada sai (movimento); o contato tem som próprio (bloqueio ≠ dano ≠ errou) | no fungo |
| `fungo_derrota` | morreu | no fungo |
| `vitoria` / `derrota` | resultado do confronto | colado |
| `passo_terra` | pé pousou: fase `frac(animT × cadência)` passa por 0 ou 0,5, a mesma do `Animator` | colado, bem baixo |
| `esquiva_rolar` / `esquiva_recuo` | rodada 09.2: começo da esquiva (rolamento com direção / recuo) | colado |
| `salto` / `pouso` | rodada 09.2: saiu do chão / pousou (contadores do `Player`; pouso só depois de 0,12 s no ar) | colado |
| `fungo_desfaz` | rodada 09.2: o corpo se desfaz em blocos (morte + 0,14 s de reação + a pausa de impacto) | no inimigo |

## 3. Regras de reprodução (`Sfx`)

- **Pré-carga:** tudo é carregado na criação, cerca de 60–80 ms com o aquecimento. Depois, tocar não lê disco, não
  decodifica e não aloca buffer de som.
- **Aquecimento silencioso:**
  - na criação, cada som toca e para com a saída a 0;
  - o caminho de um pedido roda 3.000 vezes, até o JIT compilá-lo;
  - antes disso, o primeiro passo custava 12 ms de lógica e cada pedido ~0,3 ms.
- **Rodada 09.2 — vários inimigos:** o diretor guarda o estado de CADA inimigo, então cada um soa onde está. O som de
  acerto no fungo sai do inimigo atingido (`Impact.who`). Os efeitos aprovados da 09.1 não mudaram (arquivos e mistura
  iguais).
- **Repetição no mesmo passo:** o mesmo efeito duas vezes no mesmo passo vira um só.
- **Por efeito:** intervalo mínimo e número máximo de vozes, contados no relógio do jogo e não no de parede.
- **Pausa:** congela o que toca (`alSourcePause`) e recusa pedidos. Como a lógica não anda na pausa, nada se acumula:
  ao retomar não sai avalanche (teste).
- **Recomeço e volta à vila:** `stopAll` e o diretor esquece o passado. A vitória anterior não toca de novo (teste).
- **Volumes:**
  - **Valor:** ganho final = principal × efeitos, ou 0 no mudo.
  - **Controle:** na pausa ("Volume − / +", "Som: ligado/mudo") e na tecla M.
  - **Estrutura:** há um canal só ("efeitos"); música e ambiente entram como outro fator quando existirem.
- **Espacial:**
  - os sons do fungo saem na posição dele;
  - atenuação "inverse distance clamped" com referência de 4,5 u, a distância da câmera: na luta soam no nível da
    mistura, de longe (o alerta) mais baixo;
  - os sons do herói saem colados ao ouvinte.
- **Picos:** cada arquivo sai a −3 dBFS e os ganhos da mistura ficam ≤ 1. O OpenAL Soft tem limitador de saída ligado
  por padrão. Na luta gravada, o pico da mistura fica em −5,6 dBFS, sem nenhuma amostra cortada.

## 4. Os sons: síntese, revisão e mistura

Todos os sons foram sintetizados em `tools/audio_e.py`: nenhum arquivo de terceiros, nenhuma amostra gravada, nenhuma IA
geradora. A tabela é a proveniência completa: cada som vem da função indicada, com a semente fixa do arquivo.

| Efeito | Variações | Técnica | Material/ideia |
|---|---|---|---|
| golpe no ar (h, d, v) | 3 cada | ruído em dois passa-faixas cujo centro segue a velocidade da lâmina (sobe e desce), fio de assobio no auge; d e v mais graves e com corpo | lâmina cortando o ar; o descendente é mais pesado |
| acerto | 3 | estalo curto (contato) + baque grave com queda de tom + "chap" úmido com formante oscilando + anel leve do aço | aço em carne macia de cogumelo |
| bloqueio | 2 | síntese modal: 5 modos de madeira (196–2.890 Hz) + aro metálico + baque + "boing" curto do chapéu | broquel de madeira com aro, cogumelo quicando |
| alerta | 2 | "voz" aditiva (harmônicos sem aliasing) em formantes de "o", dois chirps subindo | "hup-hup!" curioso, nunca monstro |
| preparação | 1 | voz em "m" subindo de 150 a 290 Hz com tremor de 13 Hz (o chapéu treme) + raspar de pé | resmungo que cresce até a cabeçada |
| cabeçada | 2 | "fuu" grave e curto com corpo | o corpo todo do cogumelo se lançando |
| cabeçada que pegou | 2 | "bonc" de desenho animado (tom caindo) + baque | macio, caricato, sem violência |
| herói ferido | 2 | voz em "u" descendo | "uf!" curto, discreto |
| fungo ferido | 3 | voz em "i" aguda com vibrato | "iip!" |
| fungo derrotado | 1 | voz em "o" murchando com vibrato + "puf" de esporos | sem música |
| vitória / derrota | 1 cada | Karplus-Strong (corda dedilhada): arpejo maior subindo / quatro notas descendo | sinais curtos, de alaúde |
| passo | 4 | baque macio + raspar de terra/grama | escopo secundário |
| rolamento (09.2) | 2 | sopro grave do corpo girando + baque do ombro no chão + baque dos pés + raspar | o corpo rolando na terra |
| recuo (09.2) | 1 | sopro curto + pouso firme + raspar | salto curto para trás |
| salto (09.2) | 2 | raspar do pé saindo do chão + sopro leve de roupa (sem voz) | impulso |
| pouso (09.2) | 2 | baque com mais corpo que o passo + raspar | o passo mais pesado |
| desfazer (09.2) | 2 | ~34 estalinhos secos filtrados, cada vez mais esparsos e graves, alguns batendo no chão + sopro de esporos | blocos do fungo caindo |

- **Revisão por números:**
  - `analise.json` registra, para cada arquivo, duração, pico, RMS, fator de crista, DC, amostras cortadas e centroide.
  - `CombatAudioTest` exige: mono, 48 kHz, pico em −3 dBFS (±0,1), sem DC e sem estalo nas bordas.
- **Revisão por prancha:** `tools/audio_board.py` mostra forma de onda e espectrograma.
- **Defeitos que a revisão pegou e corrigiu:**
  - chiado: o "fuu" tinha centroide em 6 kHz, agora 1,8–2,7 kHz;
  - aliasing: os osciladores de pulso e dente-de-serra foram trocados por soma de harmônicos;
  - estalos no fim de cada nota dedilhada e nos cortes do alerta e da derrota do fungo: agora há janelas em cosseno.
- **Mistura por alvo de nível no jogo** (RMS com ponderação aproximada × ganho):

  | Faixa | Efeitos |
  |---|---|
  | ≈ −18,5 / −19,5 dB | impactos (acerto, bloqueio, cabeçada que pegou) |
  | ≈ −22 / −23 dB | vozes e sinais |
  | ≈ −25 dB | "fuu" e preparação |
  | ≈ −34 dB | passos |

  - O acerto fica ~6 dB acima do corte no ar: errar e acertar se distinguem pelo nível, não só pelo timbre.
  - A primeira mistura punha a preparação acima do acerto e o acerto, espacial, abaixo do "fuu". Foi corrigido.

## 5. Sincronia e latência (o que foi medido, e como)

| O quê | Como | Resultado |
|---|---|---|
| Evento lógico → pedido de som | por construção: o diretor roda no mesmo passo lógico | 0 passos (mesmo passo; testes conferem o passo do acerto) |
| Pedido → amostra na mixagem (gravação) | `tools/audio_sync.py`: envelope de cada arquivo (no tom tocado) correlacionado com o WAV que o jogo gravou | sons isolados: 1,0 ms de média, máx 1,3 ms do começo do quadro do evento |
| "Fuu" × lâmina | teste: instante do pedido + pico do arquivo dentro da janela de dano do golpe | dentro (h, d, v) |
| Dispositivo real | PipeWire, coletor nulo temporário (nada nos alto-falantes) + `parec` no monitor | 4/4 sons capturados; espaçamento dos pedidos preservado em +2–4 ms |
| Latência de saída | `ALC_SOFT_device_clock` (o que o OpenAL Soft informa) | 12–20 ms nesta máquina |
| Custo na thread do jogo | contador no `Sfx` durante a luta medida | ~0,2 ms por pedido (máx 0,44 ms), ~7 ms em 13 s; em regime 3 µs |

**Limites:**

- **Precisão:** a sincronia na gravação é por quadro: o som entra no começo do quadro de 33 ms que processou o evento,
  o mesmo quadro que mostra o evento.
- **Latência absoluta:** do clique ao ouvido, com mouse físico e alto-falante, não foi medida. Precisaria de microfone e
  ferramenta externa, e fica no teste físico.

## 6. Como acrescentar um som

Ver a skill `audio` (`.claude/skills/audio/SKILL.md`). Em resumo:

1. Função de síntese em `tools/audio_e.py` e entrada em `catalog()`.
2. Linha em `sons_e.json` (ganho por alvo de nível).
3. Evento no diretor, só lendo estado.
4. Teste de correspondência (um som por evento real, nada no evento errado).
5. Prancha, `audio_sync.py` num vídeo gravado por loopback e o ouvido do proprietário.
