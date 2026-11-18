# Relatório — Rodada 09.1: câmera de combate e primeiro som

**Pacote local** (fora do git):
- pasta `output/visual-review/round-09-1/` (`LEIA-ME.md`);
- zip `output/visual-review/rodada-09-1-imagens-e-documentos.zip`, sem MP4 nem WAV, que ficam na pasta;
- tudo é regenerável com `tools/review91.sh tudo`.

**Documentos:**
- câmera: [combate.md §9](../architecture/combate.md);
- som: [audio.md](../architecture/audio.md);
- decisão: [ADR 0016](../decisions/0016-audio-openal-e-sons-sintetizados.md);
- teste físico: [roteiro-de-teste-fisico-combate.md](roteiro-de-teste-fisico-combate.md).

**Sem commit nem push.** A tag `playable-foundation-v0.1` não foi tocada. Nenhum teste físico foi feito pelo assistente:
a sensação, o timbre dos sons e a diversão são do proprietário.

## Como executar

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo                     # som no dispositivo padrão; Esc = pausa (volume, mudo); M = mudo
./gradlew test                     # 176 testes
tools/review91.sh camera           # câmera: 9 casos, antes (binário da 09) × depois, mesmo instante lógico
tools/review91.sh videos           # vídeos com o som GRAVADO PELA APLICAÇÃO + relatório de sincronia
tools/review91.sh sons             # catálogo: 33 arquivos, faixa isolada, prancha
tools/review91.sh tudo             # + desempenho, testes, validadores, regressão, zip
```

## 1. Auditoria inicial

| Item | Estado |
|---|---|
| Git | `main` = `origin/main` = `24e83df`; tag `playable-foundation-v0.1` em `24e83df`; 56 alterações locais da rodada 09 preservadas |
| Testes | 156, 0 falhas |
| Validadores | `--check` TODOS OK; `interrupcao-fina` 0 inseguros; oclusão = linha de base (só `bow_shot`) |
| Câmera (captura citada) | `round-09/camera/06-carvalho-com-ajuste.png`: câmera a 0,96 u, a copa do carvalho cortava a vista a 25% da distância (`tree_oak3:copa`) |
| LWJGL | 3.4.3 (GLFW + OpenGL); `lwjgl-openal` do mesmo projeto disponível; OpenAL Soft 1.24 no sistema; PipeWire 1.6 |

Antes de mexer, o binário da 09 foi guardado em `output/tmp/game-09/`, para as capturas de antes.

## 2. Câmera de combate

### Diagnóstico

Um medidor novo, `CameraFraming`, projeta herói e fungo pela câmera lógica, sem OpenGL. Ele mede quanto da tela o herói
ocupa e quanto do fungo aparece, separando o que fica fora da tela, atrás do herói e atrás do cenário. A medição bate com
as capturas reais: 50% contra 72% visível, conferido lado a lado.

| Causa | Efeito |
|---|---|
| A busca de desvio avaliava a vista do jogador **sem** o giro e a inclinação de combate, somados depois | junto ao carvalho, a vista final entrava na copa e a câmera encostava a 0,9 u: a cabeça tomava a tela (o caso citado) |
| O giro de combate era um **cone com troca de lado** | dentro de 35° o mouse não mexia a vista (zona morta); ao cruzar o meio a câmera varria 70° com o fungo atrás do herói |
| O giro era calculado sobre o desvio **suavizado** | o lado trocava no meio do trajeto e a busca vaivinha |
| Fungo colado (0,5–0,9 u, onde a cabeçada acontece) | nenhum giro lateral o tira de trás da cabeça grande do herói |
| Piso de 0,9 u | com o herói encostado no tronco ou na pedra, o olho ficava **dentro** deles |

As capturas da 09 também tinham um artefato de método: `posicionar` não zerava a distância suavizada, e cada captura
herdava a câmera da anterior. Por isso o "sem ajuste" do carvalho parecia bom.

### Correção (a menor combinação que resolveu os casos)

1. **Busca que vê a vista final:**
   - cada candidato é avaliado como vai ficar (viés + inclinação + desvio);
   - há inclinações para baixo, para sair de debaixo de copas;
   - penalidade se o cenário esconde o fungo;
   - troca só por ganho ≥ 0,5 u e segura a escolha por 0,4 s.
2. **Viés contínuo de 35° de um lado só:**
   - o fungo aparece à direita do herói;
   - o mouse sempre move a vista (inclinação ≥ 1/2), sem zona morta e sem salto.
3. **Inclinação até 0,9 rad com o fungo colado:** suave, de 1,7 u a 0,6 u.
4. **Na luta:** piso de 0,55 u (o pivô fica fora da cabeça; o medidor confere que o olho não entra no herói) e
   desvio/volta mais rápidos (6/s e 5/s).

Não houve trava de mira, nem como experimento: as evidências não pediram.

**Parâmetros escolhidos por varredura** (tempo ruim médio nos 10 cenários em movimento):

| Viés | Inclinação com o fungo colado | Tempo ruim |
|---|---|---|
| 30° | 0,85 | 7,6% |
| 40° | 0,85 | 5,4% |
| 35° | 0,9 | 5,7% |

Ficou 35°, o mesmo ângulo medido na 09: mais que isso gira demais a vista em relação ao mouse.

### Evidência

**Varredura da arena inteira** (`CombatCameraTest`, 5.005 situações): herói em toda a grade da arena, fungo em 8 direções
e 2 distâncias, mouse em 5 desvios.

| Medida | Rodada 09 | 09.1 |
|---|---|---|
| Herói domina a tela (assentada) | 242 (4,8%) | 2 (0,0%) |
| Fungo escondido, menos de 50% visível | 91 (1,8%) | 4 (0,1%) |
| Câmera dentro de algo | 14 | 0 |
| Distância média | 4,34 u | 4,58 u |

**Dez cenários em movimento:** lógica completa, entrada de mouse e teclado, o fungo lutando de verdade. Tempo ruim = herói
dominando ou fungo com menos de 50% visível.

| Cenário | Rodada 09 | 09.1 |
|---|---|---|
| 1 campo aberto | 13,2% | 0,0% |
| 2 pedra grande | 33,1% | 3,5% |
| 3 carvalho | 44,0% | 20,9% |
| 4 toco | 66,7% | 3,2% |
| 5 encosta | 33,1% | 3,7% |
| 6 fungo muito próximo | 6,1% | 5,8% |
| 7 fungo atrás do herói | 0,0% | 0,0% |
| 8 giro rápido do mouse | 27,7% | 19,5% |
| 9 defesa junto à pedra | 1,2% | 0,0% |
| 10 dano junto à pedra | 18,7% | 0,0% |
| **média** | **24,4%** | **5,7%** |

Câmera dentro de algo nos cenários: rodada 09, 102 quadros (pedra 18, carvalho 8, toco 60, giro 16); 09.1, 4 quadros (só
no giro de 360° colado na pedra).

**Nove casos no jogo real**, no mesmo instante lógico nos dois binários. O modo `debug camera-09` do binário novo
reproduz o binário antigo pixel a pixel nos 9 casos:

| Caso | Antes (distância · herói na tela · fungo visível) | Depois |
|---|---|---|
| campo aberto | 4,6 u · 3% · 61% | 4,6 u · 3% · 83% |
| carvalho | 4,6 · 3% · 61% | 4,6 · 3% · 78% |
| pedra | 4,6 · 3% · 100% | 4,6 · 2% · 100% |
| toco | **0,9 · 38%** · 83% | 4,6 · 2% · 72% |
| encosta | **2,29 · 13%** · 61% | 4,6 · 3% · 67% |
| fungo colado | 4,6 · 3% · 61% | 4,6 · 3% · 61% |
| dano junto à pedra | 4,6 · 3% · 56% | 4,6 · 2% · 89% |
| pior caso a (varredura) | 4,6 · 3% · 100% | 4,6 · 3% · 100% |
| pior caso b (varredura) | 4,6 · 3% · 50% | 4,6 · 3% · **39%** |

O **roteiro exato da rodada 09** (`tools/combate/camera.txt`) foi rodado no binário novo. A captura citada,
`06-carvalho-com-ajuste`, tinha a cabeça tomando a tela; agora mostra herói de corpo inteiro e fungo inteiro
(`round-09-1/camera/roteiro-da-rodada-09/prancha-06-carvalho.png`).

**Preservado:**
- fora da luta, a câmera é idêntica passo a passo (teste) e 13 de 14 capturas de exploração da 08 saem idênticas (a 14ª
  é a pausa, que ganhou volume);
- câmera igual a 30, 60, 120 e 240 FPS (teste).

**Aberto:**
- carvalho em movimento e giro rápido, cerca de 20% de tempo ruim;
- fungo colado, cerca de 60% visível, com o chapéu à vista;
- pior caso b, 39%;
- giro grande em encurralamento (P37, P38).

## 3. Som

### Infraestrutura (ADR 0016)

- **Engine** (`vx.engine.audio`):
  - `AudioBackend`;
  - `OpenAlBackend`: OpenAL Soft pelo LWJGL; 24 vozes fixas; atenuação "inverse distance clamped" com referência de
    4,5 u; dispositivo real ou *loopback*;
  - `SilentBackend`;
  - `Wav`.
- **Jogo:**
  - `Sfx`: mistura, pré-carga, regras, volumes, mudo, pausa, aquecimento silencioso;
  - `CombatAudio`: diretor que **só lê** o confronto depois de cada passo;
  - `Footsteps`: passos no pouso do pé, mesma fase do `Animator`.
- **Eventos:** golpe no ar (h, d, v), acerto, bloqueio, alerta, preparação (para se interrompida), cabeçada, cabeçada que
  pegou, herói ferido, fungo ferido, fungo derrotado, vitória, derrota, passo.
- **Falha segura:** sem dispositivo, sem biblioteca ou com contexto recusado, o jogo fica mudo e avisa. Roteiros são
  mudos por padrão e testes usam um backend registrador.
- **Volume:** principal × efeitos e mudo, na pausa e na tecla M. Música e ambiente não foram criados (sem conteúdo).

### Efeitos

São 15 efeitos e 33 arquivos, todos sintetizados em `tools/audio_e.py`. Não há terceiros, gravações nem IA; a
proveniência por efeito está em `audio.md` §4.

**Revisão** por números (`analise.json`) e por prancha de espectrograma. Defeitos pegos e corrigidos:

| Defeito | Correção |
|---|---|
| "Fuu" chiado (centroide 6 kHz) | 1,8–2,7 kHz |
| Aliasing nas vozes (osciladores de pulso) | soma de harmônicos |
| Estalos no fim das notas dedilhadas e nos cortes do alerta e da derrota do fungo | janelas em cosseno |
| Mistura invertida: preparação acima do acerto; acerto espacial abaixo do "fuu" | ganhos por alvo de nível; acerto colado ao ouvinte, ~6 dB acima do corte no ar |

**Testes de limpeza:** todos os arquivos são mono, 48 kHz, −3 dBFS, sem DC e sem estalo de borda. A mistura gravada da
luta tem pico de −5,6 dBFS e 0 amostras cortadas.

### Sincronia e latência

| Medida | Resultado |
|---|---|
| Evento → pedido | mesmo passo lógico (teste: o som de acerto sai no passo do contato) |
| "Fuu" × lâmina | o pico cai dentro da janela de dano (teste) |
| Gravação (loopback), sons isolados | 1,0 ms de média e 1,3 ms no máximo do quadro do evento (`videos/*-sincronia.txt`). Sons sobrepostos ficam fora da conta, por imprecisão |
| Dispositivo real | coletor nulo do PipeWire, sem tocar nos alto-falantes, e captura do monitor: 4/4 sons; espaçamento dos pedidos preservado em +2–4 ms |
| Latência informada pelo dispositivo | 12–20 ms |
| Clique → ouvido, com hardware real | **não medido** (teste físico) |

**Defeito real que o teste de som pegou:** o diretor criado sem ver o confronto engolia o que acontecia no primeiro
passo. Agora ele nasce olhando.

### Vídeo principal (`videos/01-luta-com-som.mp4`, 20 s)

- **Conteúdo:**
  - da trilha à vitória, só com entrada de jogo;
  - 59 sons, com 2 bloqueios, 6 acertos, 2 danos no herói, 5 cabeçadas e a vitória.
- **Origem do som:** é o que o próprio jogo mixou (*loopback*), quadro a quadro; não é uma trilha montada depois.
- **Derrota:** `02-derrota-com-som.mp4`.
- **Catálogo:** `sons/catalogo-isolado.mp4` (prancha + faixa com os 33 arquivos, com a mistura aplicada).

## 4. Revisão da sensação (leitura técnica, não aprovação)

- **Balanceamento:** não mudou. Os parâmetros da 09 são a linha de base; nada exigiu comparação controlada.
- **O que o som acrescenta nos vídeos:**
  - errar e acertar se distinguem pelo nível e pelo timbre;
  - bloqueio ("tunc" de madeira) e dano ("bonc" + "uf") são diferentes;
  - a preparação tem um resmungo que cresce até a cabeçada.
- **O que a câmera muda:** com o fungo colado ela olha mais de cima (52°). É a forma de mostrar os dois, e muda a
  sensação da câmera na luta. Fica para o proprietário julgar.
- **O que o assistente não consegue avaliar:** se os sons sintetizados soam "eletrônicos" demais, se são divertidos e
  se o volume é confortável num alto-falante de verdade.

## 5. Desempenho (mesma sessão, mesma máquina; duas repetições)

**Combate (`tools/combate91/desempenho.txt`, 1920×1080, 786 quadros):**

| | Binário 09 | 09.1, som no dispositivo | 09.1 mudo | 09.1 com a câmera da 09 |
|---|---|---|---|---|
| Mediana do quadro | 10,14 / 10,93 ms | 10,13 / 9,99 ms | 10,31 / 10,06 ms | 10,00 / 10,46 ms |
| p95 | 11,37 / 12,59 | 12,04 / 11,79 | 12,30 / 11,98 | 11,51 / 12,22 |
| Lógica, p95 | 0,70 / 1,23 | 1,04 / 0,90 | 0,87 / 0,94 | 0,71 / 0,91 |
| Quadros acima de 20 ms | 0 / 1 | 0 / 0 | 1 / 0 | 0 / 1 |

**Exploração** (roteiro da 08):

| | Binário 09 | 09.1, som no dispositivo |
|---|---|---|
| Mediana do quadro | 11,77 / 11,41 ms | 11,39 / 10,79 ms |
| p95 | 14,47 / 13,75 | 13,48 / 12,63 |
| Lógica, p95 | 2,56 / 2,42 | 0,92 / 0,62 |

O ganho na lógica da exploração vem do filtro de raios.

- **Combate:** dentro do ruído entre execuções; o próprio binário da 09 varia 0,8 ms de mediana entre repetições.
- **Exploração:** mais rápida.
- **Som na thread do jogo:** ~0,2 ms por pedido em luta real (máximo 0,44 ms), 7 ms em 13 s; 3 µs por pedido em
  regime. A mixagem é na thread do OpenAL.
- **Carga:** 60–80 ms na abertura, com o aquecimento.
- **Primeiro passo:** antes do aquecimento custava 12 ms de lógica; o pico sumiu.
- **Triângulos:** 474 mil na luta (481 mil na 09); a diferença vem só da vista.

## 6. Testes e regressões

| Verificação | Resultado |
|---|---|
| Testes | **176, 0 falhas** (156 + 7 `CombatCameraTest` + 13 `CombatAudioTest`) |
| `CombatTest` (câmera) | contrato atualizado de "35° de qualquer lado" para "35° à direita"; acrescentada a visibilidade medida |
| `--check` | TODOS OK |
| `--check interrupcao-fina` | golpes jogáveis seguros |
| Oclusão | idêntica à da rodada 09 (só `bow_shot`) |
| Gate dos presets | 51 pares, 0 diferenças |
| Exploração da 08 × 09.1 | 13/14 capturas idênticas pixel a pixel; a pausa mudou de propósito (volume) |
| Referências, criador, protagonistas, criaturas | nenhum arquivo de renderer, viewer, gerador visual, animação, cena visual ou criador mudou (lista de arquivos alterados no pacote); geradores determinísticos |
| Câmera da 09 reproduzida | 9/9 capturas idênticas (binário da 09 × `debug camera-09` no novo) |

## 7. Problemas que continuam

Detalhes em [problemas-conhecidos.md](problemas-conhecidos.md):

| Nº | Problema |
|---|---|
| P37 | câmera junto ao carvalho e no giro rápido; fungo colado ~60% visível; um caso em 39% |
| P38 | giro grande em encurralamento muda a direção do WASD |
| P39 | timbre e mistura não aprovados por ouvido; latência do clique ao ouvido não medida |
| P40 | sem música, ambiência nem superfícies de passo |

Seguem da rodada 09: lâmina longa 18/47, fungo sem busca de caminho e câmera da exploração dentro de obstáculo por
instantes.

## 8. Gate de aprovação (16 itens)

| # | Item | Estado |
|---|---|---|
| 1 | Câmera não esconde gravemente o inimigo em situações comuns | sim nos casos comuns (varredura 0,1%); aberto no fungo colado (~60%) e num pior caso (39%) |
| 2 | Cabeça não domina perto de obstáculos | sim assentada (0,0%); em movimento junto ao carvalho ainda há trechos (P37) |
| 3 | Jogador controla a câmera confortavelmente | mouse sem zona morta (teste); conforto = teste físico |
| 4 | Sem novos atravessamentos | sim: câmera dentro 14 → 0 na varredura; 102 → 4 quadros nos cenários (o giro de 360° tinha 16) |
| 5 | Sons correspondem às ações reais | sim (testes) |
| 6 | Sincronizados com golpes, defesas e dano | sim: 1 ms na gravação; o pico do "fuu" fica na janela de dano |
| 7 | Sem reproduções duplicadas | sim (teste) |
| 8 | Pausa e recomeço | sim (testes) |
| 9 | Funciona sem dispositivo | sim (teste) |
| 10 | Lógica determinística preservada | sim (luta igual com e sem som; FPS) |
| 11 | Testes anteriores passando | sim (156 + 20) |
| 12 | Validadores sem regressão | sim |
| 13 | Desempenho aceitável | sim (combate dentro do ruído; exploração mais rápida) |
| 14 | Criador e exploração funcionando | sim |
| 15 | Vídeo principal com o combate audiovisual real | sim (som gravado pela aplicação) |
| 16 | Remanescentes documentados | sim |

**Recomendação:**
- **Câmera e som:** prontos para o teste físico, não para aprovação final.
- **O que o proprietário decide:**
  - se o olhar mais de cima e o viés de 35° ajudam;
  - se os 20% junto ao carvalho incomodam;
  - se os sons soam bem.

A rodada 10 não foi iniciada.
