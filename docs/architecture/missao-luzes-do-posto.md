# Contrato da primeira missão — "As Luzes do Posto" (rodada 10, APROVADA)

**Aprovada pelo proprietário após teste físico** (fechamento da 10, checkpoint `adventure-foundation-v0.4`): é a
referência de qualidade de gameplay. Não redesenhe o marco nem mude a recompensa sem pedido. Regra das falas: nenhum NPC
afirma ver o marco (ele não aparece da estalagem nem do lugar do Bramor).

Id estável: **`quest.trilha.luzes_do_posto`**. Dados: `assets/scenes/missoes_e.json`; falas:
`assets/scenes/dialogos_e.json`; encontro: `assets/scenes/combate_e.json → encontros.trilha`; objeto:
`assets/scenes/jogo_vila_e.json → jogo.objetos`; modelos: `tools/env_e.py → trail_beacon(lit)`. Sistema:
[missoes.md](missoes.md).

## História

Os fungos-cardo tomaram a trilha do sul e o marco de luz do posto apagou. Sem ele, quem chega à noite se perde antes de
ver a vila. Kelyra, a estalajadeira, pede para limpar a trilha e reacender o sinal; o herói volta a pé e recebe 30
moedas. Bramor comenta (uma fala a mais: soube que o marco acendeu), sem segunda missão.

## Passos e o que vale

| # | Objetivo (`id`) | Tipo | Conta |
|---|---|---|---|
| 1 | `limpar_trilha` — "Afaste os fungos-cardo da trilha do sul" | derrotar | `trilha.fungo.1` e `trilha.fungo.2`, uma vez cada, só depois de aceitar |
| 2 | `acender_marco` — "Reacenda o marco de luz do posto" | interagir | E no `obj.posto.marco` com a trilha limpa |
| 3 | `voltar_kelyra` — "Volte à vila e conte à Kelyra" | falar | abrir a conversa com a Kelyra: 30 moedas, uma vez |

Não contam: qualquer fungo antes de aceitar; o fungo de teste (`um.0`); `grupo.*`; `chefe.*`; o mesmo fungo duas vezes;
o marco antes da trilha limpa; falar com a Kelyra antes do marco aceso.

## Lugares

| O quê | Onde (u) | Nota |
|---|---|---|
| Kelyra | (−3,6; 0,4), rumo 40° | junto à estalagem, alcance 1,8 |
| Entrada da trilha | placa em (0,6; 8,2) | a trilha de terra já existente |
| Fungos da missão | (−2,5; 12,3) e (−0,5; 12,9) | na clareira da arena (centro (−1,6; 11,2), raio 3,6) |
| Marco | (−1,15; 14,35), rumo 180° (a lanterna aponta para a vila) | no fim da clareira, atrás dos fungos; alcance 1,8; corpo de raio 0,26 |
| Ponto seguro de retorno | o início da vila (0,6; 4,4) | posição salva inválida volta aqui |

O caminho de volta é a pé (sem teletransporte), ~13 u pela trilha: ~6 s correndo.

## Marco de luz

- **Prop:** `trail_beacon_off` e `trail_beacon_on`, voxel FINE (1/16 u), 8 × 24 × 14 voxels. Gerados por
  `tools/env_e.py`: nunca à mão.
- **Forma:** estela de pedra assentada sobre um degrau, fuste que afina, capitel, braço de ferro e uma lanterna de quatro
  vidros (a linguagem das lanternas da vila).
- **Apagado:** vidro fosco e semente sem brilho (nada emissivo).
- **Aceso:** luz quente das lanternas da vila (`GLOW`/`GLOW_HI`) e **uma** semente de luz P1 verde-água `#7ff0dc`
  emissiva de 2 voxels no alto da gaiola. Respeita a assinatura visual: P1 ≤ 3 voxels e 1 por objeto.
- **Forma igual nos dois estados:** o antes/depois se lê como "o mesmo marco, aceso".
- **Efeito ao acender:** 5 sementes de luz P1 sobem em espiral da lanterna e encolhem até sumir em 2,4 s. Só desenho,
  e ≤ 5 na tela, como pede a P1.
- **Som:** `missao.sinal`.
- **Estado persistente:** `objetosAtivos` no save. O marco aceso continua aceso depois de carregar.

## Os dez contextos da Kelyra

`oferta`, `oferta_ja_viu`, `oferta_ja_lutou`, `oferta_apos_recusa`, `aceita`, `em_andamento`, `caiu`, `trilha_limpa`,
`entrega`, `concluida`, mais as reações `aceitou_agora` e `recusou_agora`. A prioridade está em
[missoes.md](missoes.md#falas-por-contexto).

## Salvamentos automáticos

Ao aceitar, ao limpar a trilha, ao acender o marco e ao concluir. São quatro numa aventura sem pausa, cada um no
primeiro passo seguro depois do marco. Salvar na pausa também vale.

## Sons (proposta A "madeira e luz", estudo)

Fonte: `fontes/audio/sfx/missao/missao.py` → sessão LMMS `missao.mmp`, com as fatias. Saem
`assets/audio/sfx/missao/{aceita,objetivo,sinal,recompensa}.wav` e os eventos `missao.*` em `assets/audio/sons.json`.
A família é a mesma da interface aprovada (bloco de madeira, kalimba e sininho, em Ré maior).

O arquivo do marco mede ~14 LU acima dos outros, porque o corpo começa no fôlego baixo e o ganho do fatiamento mira o
corpo. O catálogo dá −12 dB a ele (−1 a −2 dB aos outros), e ele fica ~3 LU acima dos outros (−27,5 × −30,1 a −30,6
LUFS antes do barramento) por ser o momento da aventura. **Medido, não ouvido:** aguarda a escuta do proprietário.
