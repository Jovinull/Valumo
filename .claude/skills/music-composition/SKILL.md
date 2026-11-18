---
name: music-composition
description: Compor e produzir música do Valumo (direção E, "Fábula Voxel") de forma deliberada — partitura de origem em Python (nota a nota, com as decisões escritas), sessão editável do LMMS 1.2.2 (.mmp, piano roll, mixer, automação), instrumentos do sf2player (FluidR3 GM, MIT) e do próprio LMMS, render por linha de comando, laço sem emenda para o jogo (2ª passada), masterização por ganho único e OGG de runtime. Use ao criar, alterar ou revisar qualquer música ou estudo musical.
---

# Composição musical (rodada 09 em diante)

Arquitetura e decisão: `docs/decisions/0015-audio-openal-stb-vorbis.md`, `docs/architecture/audio.md`.
QA técnico: skill `audio-qa`. Efeitos: skill `sound-design`.

## O que é permitido

- Composição deliberada: cada nota, ritmo, instrumento e forma é decisão escrita. Código só transcreve a partitura
  (`tools/audio/lmms.py` não compõe nada).
- Instrumentos virtuais tradicionais: sf2player com o banco **FluidR3 GM** (`/usr/share/sounds/sf2/FluidR3_GM.sf2`,
  pacote `fluid-soundfont-gm`, licença MIT) e sintetizadores do LMMS. Registre o instrumento na faixa.
- **Proibido:** geradores de música por IA, algoritmo que "inventa" notas, PCM escrito por código como música,
  cópia de temas de outros jogos.

## Identidade (estudo da rodada 09 — não aprovada)

- Sensação: *um pequeno aventureiro num mundo grande e convidativo*. Nada de épico genérico nem demo de sintetizador.
- **Motivo do Valumo**: 5̣ → 3 (sexta maior ascendente), 2, 1, 2 — "olhar para longe e dar um passo". Comum às
  peças, tratado de jeito diferente em cada uma (cabeça da melodia na A; aumentado e espaçado na B).
- Tonalidade de referência: Ré (a interface também usa Ré maior; sons e música não brigam).
- Cor "mágica": o 4º grau aumentado (Sol#, lídio) — como acorde de passagem (A) ou permanente sobre pedal (B).
- Textura cresce por seção e volta ao mínimo no começo do laço: o ponto mais denso fica no fim do laço.

## Ferramentas instaladas e verificadas (rodada 09)

| Ferramenta | Versão | Uso | Verificação |
|---|---|---|---|
| LMMS | 1.2.2 (apt) | DAW: sessão `.mmp`, render | abre o projeto escrito à mão (captura em `round-09/ferramentas/`); render por CLI determinístico (amostras idênticas em dois renders) |
| FluidR3 GM | 3.1 (apt) | instrumentos do sf2player | afinação conferida (A4 = 440 Hz) |
| ffmpeg / ffprobe | 6.1 (apt) e build em `~/bin` | medida, limitador, OGG | `ebur128`, `alimiter`, `libvorbis` |
| sox | 14.4.2 (apt) | utilidades | — |
| Audacity | 3.4.2 (apt) | edição manual pelo proprietário | instalado; **sem** `mod-script-pipe` no pacote do Ubuntu (não dá para automatizar); a abertura com interface não foi verificada nesta rodada |

Sem interface: `QT_QPA_PLATFORM=offscreen lmms render …`. Com interface (inspeção): `xvfb-run` + `xdotool` +
`import -window root`. O primeiro uso pede o diretório de trabalho (`~/Documents/lmms`) e a tela de configuração;
o `~/.lmmsrc.xml` precisa de `<app configured="1" …/>`.

## Procedimento

1. **Partitura de origem** em `fontes/audio/musica/<lugar>/<peca>.py`: docstring com hipótese artística e
   decisões (compasso, andamento, harmonia, forma, textura, instrumentos); compassos na notação de
   `tools/audio/lmms.py` (`"F#5:3 E5:1 D5 E5"`, `r` pausa, `+` acorde, `@` intensidade). O leitor confere que cada
   compasso soma o tamanho certo.
2. Humanização **determinística** (`L.humanize`: acento métrico, desvio de intensidade por tabela, atraso de até
   2 ticks no ataque). Nunca `random`.
3. **Andamento alinhado**: 60000/bpm deve dar quadros por tick inteiros e o corpo do laço um múltiplo de 256
   quadros (ex.: 100 bpm em 6/8, 75 bpm em 4/4). O render avisa se não der.
4. `python3 fontes/audio/musica/<lugar>/<peca>.py` → `<peca>/<peca>.mmp` + `.mid`.
   **Depois disso a `.mmp` é a fonte**: quem editar no LMMS salva por cima e para de regenerar pelo `.py`
   (o `.py` vira histórico das decisões).
5. Render + laço + master: `.venv/bin/python tools/audio/render_musica.py <peca>.mmp SAIDA --runtime assets/audio/musica/<id>`.
   Escreve `SAIDA/<peca>-escuta.wav|ogg` (introdução + corpo + cauda), `SAIDA/<peca>-jogo.wav` (introdução + 2
   passadas), `<peca>-render.json` (ganho, LUFS, laço em amostras) e o OGG de runtime.
6. Copie `laco_inicio_amostra`/`laco_fim_amostra` para `assets/audio/sons.json`.
7. QA (skill `audio-qa`) + stems (`lmms rendertracks`) para conferir equilíbrio por seção + espectrograma.
8. Relatório: hipótese, o que mudou entre versões e por quê, medidas, e **"não ouvido pelo agente"** se for o caso.

## Mixagem (regras que funcionaram)

- Um canal de mixer por faixa (EQ do LMMS) + **um retorno de reverberação** (ReverbSC) compartilhado; o master só
  com EQ suave. Hierarquia medida por stems: melodia ≥ acompanhamento > baixo/tapete > cor (glock, chocalho).
- Masterização fora da DAW: ganho único para −18 LUFS integrados + limitador a −1 dBFS com latência compensada
  (`alimiter …:latency=true` — sem isso o arquivo inteiro atrasa 5 ms e o laço desloca).

## Defeitos já vistos (não repita)

| Defeito | Causa | Correção |
|---|---|---|
| Violão sumia na 2ª passada do laço | LMMS 1.2 para de tocar o padrão quando as notas não estão em ordem de posição | `render_musica.looped_session` reordena; ao editar XML, mantenha as notas em ordem |
| 2ª passada diferente da 1ª | sf2player dispara notas na grade do buffer (256 quadros); corpo fora da grade | andamento alinhado (passo 3) |
| Eco do Delay não soa | efeito Delay do LMMS 1.2.2 não funciona escrito em XML (testado: 4 nomes de nó, autoquit 0/1) | eco ESCRITO na partitura (`L.written_echo`) |
| Mallets (STK) desafinado; travou o render | preset com altura errada (C5 → 5,5 kHz), "madeira" vira clique, tigela derrubou o LMMS | não use; sf2player (vibrafone, FX cristal) |
| Glockenspiel e chocalho inaudíveis | FluidR3: alguns timbres saem 25–35 dB abaixo | meça stems; `gain` do sf2player (até 5) + volume da faixa |
| Música abafada (acima de 4 kHz −8 dB de uma curva acústica) | timbres do FluidR3 escuros | EQ do master (prateleira +2,5 dB em 6 kHz), escuta humana decide |
| `rendertracks` com NaN/estalo nos primeiros 3 ms de algumas faixas | artefato do modo por faixa | ignore os 10 ms iniciais dos stems; a mistura (`render`) é limpa |

## Aceite

Técnico (skill `audio-qa`) **e** escuta do proprietário. O agente não declara música aprovada, e não diz que
ouviu se só mediu.

## Ambiente (rodada 09)

Camadas de ambiente seguem o mesmo princípio (partitura/desenho explícito → sessão LMMS → render): ver skill
`sound-design` (vento e lago) e `tools/audio/render_ambiente.py`. Música e ambiente têm barramentos próprios.
