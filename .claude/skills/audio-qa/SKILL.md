---
name: audio-qa
description: Validar áudio do Valumo tecnicamente e em contexto — medição de arquivos (tools/audio/audio_qa.py - taxa, pico real, saturação, LUFS, ataque, cauda, DC, emenda do laço comparada à passagem natural), testes sem dispositivo (AudioTest - laço exato, regras, catálogo, mixagem real por loopback do OpenAL Soft), gravação da mixagem do jogo em sincronia com os quadros de um roteiro, medição do atraso evento → som, ambiente posicional (atenuação medida por loopback) e distinção entre superfícies de passo. Use antes de declarar pronto qualquer som, música ou mudança no subsistema de áudio.
---

# QA de áudio (rodada 09.4 em diante)

**Análise de forma de onda não é escuta.** O agente mede e registra; quem aprova é o proprietário, ouvindo. Se o
agente não ouviu (não tem saída de som acessível), o relatório diz isso com essas palavras.

## 1. Arquivos

```bash
.venv/bin/python tools/audio/audio_qa.py --tipo efeito --md SAIDA/qa.md arquivos.wav          # efeitos
.venv/bin/python tools/audio/audio_qa.py --tipo musica --laco INICIO,FIM --imagens SAIDA/img jogo.wav
.venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json                     # tudo do jogo
```

| Regra | Limite | Por quê |
|---|---|---|
| taxa | 48 000 Hz | o OpenAL reamostra o resto; padroniza |
| saturação | 0 amostras ≥ 0,999 | — |
| pico real (4×) | ≤ −1 dBFS | reconstrução e codificação OGG |
| DC | < 0,002 | estalo ao iniciar/parar |
| música | −20 a −16 LUFS integrados | arquivo de música; a MISTURA do jogo fica em ~−24 LUFS (alvo de console), não confunda |
| efeito: ataque | ≤ 10 ms de silêncio inicial | latência percebida |
| efeito: fim | últimos 5 ms < −50 dB do pico | cauda cortada = clique |
| efeito: duração | dentro de `duracao` do catálogo | coerência com o evento |
| laço | salto na emenda ≤ 3× a variação normal **ou** ≤ 1,2× o salto da passagem natural | ataque de tempo forte é música, não emenda |
| laço | fim da 2ª passada ≈ fim da 1ª: nível ≤ 1 dB e envelope por oitava ≤ 1,5 dB | prova de que voltar ao início do laço equivale a seguir tocando |

A correlação de forma de onda é informativa: o ReverbSC do LMMS modula as linhas de atraso (sala grande e eco:
~0,89; seco: ~1,0). Nunca troque a métrica nem o limite para passar; mude o som ou prove que a métrica estava
errada e diga por quê (como aqui).

Espectrograma e forma de onda (`--imagens`) + energia por banda + stems (`lmms rendertracks`) para equilíbrio.

## 2. Testes sem dispositivo (`./gradlew test`)

`engine/src/test/java/vx/engine/audio/AudioTest.java`:
- laço exato na amostra (fonte sintética, pedaços que não dividem o laço);
- regras: variação sem repetir e determinística, tom/ganho na faixa, intervalo mínimo, limite de vozes por evento
  e global por prioridade, barramentos, música sem reiniciar, transição e abafamento, arquivo ausente sem queda;
- catálogo real: todo arquivo decodifica, 48 kHz, efeitos mono, duração na faixa, laço dentro do OGG, busca do
  stb_vorbis exata no início do laço;
- mixagem real: **OpenAL Soft em loopback** (silêncio = ±1 LSB de dither; o evento soa sem saturar; vozes e fontes
  devolvidas). Sem OpenAL na máquina o teste de loopback é pulado.

`game/src/test/java/vx/game/GameAudioTest.java`: pisada na fase de contato, intervalo = 1/(2 × cadência), mesma
contagem a 30 e 144 FPS, parado não pisa, mapeamento de superfície e de ações.

## 3. Contexto real (o jogo misturando)

```bash
JAVA_OPTS="-Dvx.audio=loopback -Dvx.audio.log=true -Dvx.audio.gravar=$PWD/SAIDA/mix.wav" \
  game/build/install/game/bin/game --roteiro tools/jogo/07-audio.txt TMP 30 | grep '^som' > SAIDA/eventos.txt
ffmpeg -framerate 30 -i TMP/quadros/q_%05d.png -i SAIDA/mix.wav -c:v libopenh264 -b:v 8M -c:a aac -shortest SAIDA/jogo.mp4   # libx264 -crf 18 onde existir
```

- O loopback mistura exatamente `dt` por quadro do roteiro e grava só entre `record on` e `record off`:
  N quadros ↔ N/30 s de áudio (confira as duas contagens impressas).
- `-Dvx.audio.log` imprime tempo, evento, posição e superfície: confira que cada passo bate com a superfície.
- Atraso evento → som: mesma rodada com `-Dvx.musica=nenhuma`; ataques medidos a −32,3 ± 0,8 ms do tempo registrado
  = deslocamento de 1 quadro do `t0` do roteiro (o `size` avança um quadro), latência real ≈ 1 ms.
- Roteiros e capturas são mudos por padrão: nunca toque som no alto-falante de quem roda a revisão.

## Defeitos de processo já vistos

| Defeito | Correção |
|---|---|
| Áudio 0,5 s mais longo que o vídeo | o binário do `installDist` era anterior à mudança: recompile antes de medir |
| "Laço com salto" em falso (ataque do tempo forte) | comparar com a passagem natural |
| Correlação baixa na emenda com reverb grande | métrica espectral (o conteúdo é igual; a fase da sala não) |
| Pico ok, pico real +0,6 dBFS em ruído | teto de amostra −2,5 dBFS nos efeitos com ruído |

## Entrega

Pacote `output/visual-review/round-NN/` com `musica/`, `efeitos-sonoros/`, `integracao/` (vídeo com som, mistura,
eventos), tabelas de QA e o que foi **ouvido por quem**.

## Rodada 09.4 (2ª parte)

- `audio_qa.py` tipo `ambiente` (−28 a −20 LUFS; emenda fim → começo ≤ 3×) e `--catalogo` inclui `ambientes`.
- `tools/audio/superficies.py assets/audio/sfx/passos grama terra areia pedra`: distinção entre superfícies de passo (só
  imprime as medidas; não reprova sozinho).
- `AudioTest` prova a posição pelo loopback (lago a 2 u × 12 u: longe < metade do RMS) e as camadas de ambiente
  (entram em rampa, não reiniciam, só o lago tem posição, barramento próprio).
- Jogo indo ao lago e se afastando: `tools/review94.sh ambiente` (marulho ~8 dB mais baixo longe; passos de areia
  registrados na margem). Preferências de volume: `GameAudioTest.volumePreferences…`; os roteiros usam
  `-Dvx.prefs=-` (nem lê nem grava). Quem precisa de preferências num roteiro passa `-Dvx.prefs=ARQUIVO` **e**
  `-Dvx.prefs.audio=ARQUIVO` temporários — o `review94.sh` gravava nas do proprietário antes da auditoria de engenharia.
  Atenção: `tools/review94.sh audio|ambiente` REGENERA as sessões e sobrescreve os OGG versionados; para só medir, use
  `audio_qa.py --catalogo`.

## Fechamento do QA — tempo real

- O loopback só mistura quando o jogo pede: não mede falta de dados. Para isso: `tools/qa/audio_tempo_real.sh ROTEIRO
  SAIDA` (backend `wave` do OpenAL Soft, nada nos alto-falantes) + `tools/qa/audio_buracos.py`.
- Fila dos fluxos: 1 s no começo, cresce um pedaço de 0,25 s por quadro até 4 s (`OpenAlOut.STREAM_*`); abrir o criador
  (~2 s sem quadro) deixava 1,9 s de silêncio com a fila fixa de 1 s. Teste: `AudioTest.musicSurvivesTheGameThreadStalling…`.
