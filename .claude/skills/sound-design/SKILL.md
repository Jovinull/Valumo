---
name: sound-design
description: Criar efeitos sonoros do Valumo (passos, interface, futuros combate/ambiente) por síntese e camadas deliberadas no LMMS 1.2.2 — sessão com uma faixa por camada e um som por compasso, Kicker (tom com queda + ruído) e sf2player, EQ por camada, fatiamento calibrado, consistência entre variações, duração desenhada, mono 48 kHz 16 bits, catálogo de eventos semânticos (assets/audio/sons.json) e integração por eventos no jogo; superfícies de passo (grama, terra, areia, pedra) e camadas de ambiente em laço (vento, lago posicional). Use ao criar, alterar ou integrar qualquer efeito sonoro ou ambiente.
---

# Sound design (rodada 09.4 em diante)

Arquitetura: `docs/architecture/audio.md`; decisão: ADR 0023. Música: skill `music-composition`. QA: `audio-qa`.

## Princípios

- **Um efeito tem função perceptiva**: dizer onde o pé pisou e em quê; confirmar que o clique foi aceito. Nada de
  "frequência com duração diferente" — camadas com papel (peso, material, brilho) e envelopes desenhados.
- **Variação sem perder identidade**: 3–4 variações escritas (altura da camada de peso, desenho dos grãos,
  intensidade) + variação moderada no jogo (tom ±0,5 semitom só em sons não tonais; ganho ±1,5 dB).
  Seleção determinística: nunca a mesma variação duas vezes seguidas.
- **Coerência com a música**: sons tonais da interface em Ré maior (sem variação de tom no catálogo).
- **Proibido:** geradores por IA, pacotes de terceiros, `sfxr`/aleatórios de "botão mágico".

## Construção que funcionou (rodada 09.4)

| Camada | Instrumento do LMMS | Notas |
|---|---|---|
| peso (pisada, toque) | **Kicker** (`L.kicker`, tom com queda de frequência) + EQ passa-baixa | `follow_note=True`: a nota escolhe a frequência inicial (variação de altura) |
| material (grama, terra, papel) | Kicker com `noise=1` (grão de ~13 ms) + EQ de banda | o desenho está nos TEMPOS e intensidades dos grãos, escritos à mão |
| tonal (interface) | sf2player: kalimba (108), bloco de madeira (percussão GM 76/77), sininho (112), celesta (8), caixa de música (10) | sala curta (ReverbSC) com envio baixo |

Sessão: um som por compasso a 240 bpm (1 tick ≈ 5,2 ms; compasso = 1 s), uma faixa por camada, e
`<sessao>.fatias.json` (id, grupo, tick, `max_ms`). Exemplo: `fontes/audio/sfx/passos/passos.py`.

## Procedimento

1. Entenda o evento no código (quem dispara, em que instante, com que frequência). Passos: `Player.steps` sobe
   quando um pé começa o apoio — a mesma fase que planta o pé na animação.
2. Escreva **duas propostas** com hipóteses diferentes (não só volume) e meça antes de escolher:
   `.venv/bin/python tools/audio/render_sfx.py <sessao>.mmp SAIDA --alvo-rms -26` e
   `.venv/bin/python tools/audio/medir_sfx.py SAIDA` (duração, sonoridade do corpo, centroide, energia grave,
   distância espectral entre variações).
3. Escolha por função: ex. grama e terra precisam de **contraste** (centroide 2,8 × 0,9 kHz), senão o jogador não
   distingue as superfícies (Grama A × Terra B tinham o mesmo centroide — rejeitado por isso).
4. Refine a escolhida (uma hipótese por vez), rode o QA (`audio-qa`) até 0 alertas.
5. Sequência de contexto (passos na cadência real: 227 ms andando, 178 correndo; sessão de cliques) para escuta.
6. Copie as escolhidas para `assets/audio/sfx/<familia>/` e descreva o evento no catálogo `assets/audio/sons.json`.
7. Integre por **evento semântico** (`Audio.event("passo.grama.andar")`) e grave o jogo em loopback
   (skill `audio-qa`, "contexto real").

## Fatiamento (`tools/audio/render_sfx.py`)

- Posição real de cada compasso calibrada por sonda (não por conta).
- Fim pelo pico do PRÓPRIO som (−60 dB) + 15 ms de folga; se ainda soa em `max_ms`, a duração é desenhada (descida
  cúbica nos últimos 35%), nunca um corte seco.
- Passa-alta de 20 Hz (DC), mono, 16 bits, 48 kHz.
- Consistência: RMS do corpo de cada variação a ±2 dB da mediana do grupo (corrige o acaso de fase dos grãos).
- `--alvo-rms`: sonoridade do corpo padronizada; a mixagem ENTRE eventos fica no catálogo (`ganho_db`, barramento).

## Defeitos já vistos (não repita)

| Defeito | Causa | Correção |
|---|---|---|
| Passos "baque" sem material (95% da energia < 300 Hz) | camada de ruído 25 dB abaixo | meça a energia por banda antes de ouvir; equilibre camadas |
| Clique no fim de cada passo | o **Kicker só soa enquanto a nota está ligada** (nota de 21 ms cortava o baque a −28 dB) | nota ≥ decaimento (pisada 16 ticks; grão 4) |
| Variação 15 dB mais fraca no mesmo grupo | grãos de ruído com fase ao acaso; normalização por pico | consistência por RMS do corpo |
| Cauda cortada (kalimba ainda soando) | corte em `max_ms` com 6 ms de descida | duração desenhada |
| Re-render muda o som | o ruído do Kicker é aleatório a cada render | cada render é uma TOMADA: a avaliada é a versionada em `assets/audio/`; não re-renderize depois do QA |

## Fontes e saídas

| O quê | Onde | Git |
|---|---|---|
| sessão + gerador + fatias | `fontes/audio/sfx/<familia>/` | sim |
| tomadas escolhidas (runtime) | `assets/audio/sfx/<familia>/*.wav` | sim (pequenas) |
| catálogo de eventos | `assets/audio/sons.json` | sim (à mão) |
| propostas, medidas, contexto | `output/visual-review/round-NN/efeitos-sonoros/` | não |

## Rodada 09.4 (2ª parte) — superfícies e ambiente

- **Areia e pedra** no mesmo `passos.py` (pisada abafada + "chff" longo; pisada dura com clique + estalo). Distinção
  medida por `tools/audio/superficies.py` (espectro em 1/3 de oitava, duração, fator de crista): todos os pares ≥ 3,8 dB;
  o par mais próximo (areia × terra) se separa pela duração (97 × 48 ms). Renderize a sessão inteira, mas copie só as
  tomadas NOVAS (as antigas já avaliadas ficam).
- **Ambiente**: `fontes/audio/ambiente/ambiente.py` (vento: cama estéreo 24 s com rajadas por automação e folhas nos
  picos; lago: mono posicional 16 s, ondas de período irregular, respingos, gotas) → `tools/audio/render_ambiente.py`
  (laço por crossfade de igual potência, −24 LUFS). Ruído sustentado = `L.noise_osc()` (TripleOscillator; L/R
  descorrelacionados). Posicional exige mono.
