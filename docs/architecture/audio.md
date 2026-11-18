# Áudio

Rodada 09. Decisão: [ADR 0015](../decisions/0015-audio-openal-stb-vorbis.md). Procedimentos: skills
`music-composition`, `sound-design`, `audio-qa`. Todos os sons e músicas atuais são **estudos** (aprovação do
proprietário pendente).

| Marca | Significa |
|---|---|
| **[IMPLEMENTADO]** | existe e é coberto por testes |
| **[PLANEJADO]** | proposta; nada no código ainda |

## 1. Visão geral **[IMPLEMENTADO]**

```
fontes/audio/  (partitura .py → sessão LMMS .mmp + .mid)  ──render (LMMS CLI)──►  output/ (masters, propostas, QA)
                                                                           └──►  assets/audio/ (runtime, versionado)
assets/audio/sons.json (catálogo, à mão) ──► SoundCatalog ──► SoundSystem (regras) ──► AudioOut
                                                                                      ├─ OpenAlOut (dispositivo | loopback)
jogo (GameAudio) / criador (CreatorController.sound) ──► Audio.event("…")              └─ SilentOut (mudo)
```

| Peça | Onde | Papel |
|---|---|---|
| `Audio` | `engine/.../audio/Audio.java` | ponto único; mudo até alguém chamar `start`; propriedades `vx.audio`, `vx.vol.*`, `vx.musica`, `vx.audio.gravar` |
| `SoundCatalog` | idem | lê `assets/audio/sons.json` (`valumo.sons` v1) |
| `SoundSystem` | idem | variação, intervalo, limites, prioridade, barramentos, música com transição e abafamento |
| `OpenAlOut` / `SilentOut` | idem | OpenAL Soft (fontes em pool, fila de 4 × 0,25 s por música, loopback para gravar/testar) |
| `Pcm`, `VorbisSource`, `LoopingReader` | idem | WAV 16 bits próprio; OGG por stb_vorbis; laço exato na amostra |
| `GameAudio` | `game/.../GameAudio.java` | pisadas (`Player.steps` + `World.surfaceAt`), ações do `GameLogic`, música da vila, ambiente e ouvinte |
| `AudioPrefs` | `game/.../AudioPrefs.java` | volumes do jogador (música, efeitos) na pausa; `output/preferencias/audio.json` (`valumo.preferencias` v1) |
| Criador | `CreatorController.sound` → `Audio::event` | sons da interface; `Creator.frame` avança o áudio |

## 2. Catálogo e eventos **[IMPLEMENTADO]**

`assets/audio/sons.json` (escrito à mão): `barramentos` (música 0,6 · efeitos 0,85 · interface 0,75 · ambiente 0,7), `eventos`
(arquivos = variações, `ganho_db`, `variacao_tom` em semitons, `variacao_ganho_db`, `intervalo_min`, `max_vozes`,
`prioridade`, `duracao`), `musicas` (`arquivo`, `laco_inicio`/`laco_fim` em quadros, `ganho_db`) e `ambientes`
(`arquivo`, `barramento`, `ganho_db`, `posicional`, `distancia_ref`, `distancia_max`).

| Evento | Quem dispara | Arquivos |
|---|---|---|
| `passo.grama.andar` / `.correr` | pé começa o apoio sobre grama/floresta | 4 + 4 |
| `passo.terra.andar` / `.correr` | idem sobre o caminho | 4 + 4 |
| `passo.areia.andar` / `.correr` | idem sobre a areia (margem do lago) | 4 + 4 |
| `passo.pedra.andar` / `.correr` | idem sobre rocha (encostas) | 4 + 4 |
| `ui.selecionar` | trocar opção/cor/categoria/pose no criador; escolher aparência; avançar fala | 3 |
| `ui.confirmar` | começar, concluir, confirmar restauração, carregar arquivo, vitrine, abrir o criador | 1 |
| `ui.cancelar` | Esc/fechar diálogo, opção indisponível, sair da pausa, fim da conversa | 1 |
| `ui.abrir` | pausa, diálogo de restaurar/carregar/concluir, começo da conversa, trocar aparência | 1 |
| `ui.salvar` | aparência gravada em disco (só no sucesso) | 1 |
| música `vila_a` / `vila_b` | jogo (padrão `vila_a`) e criador; `-Dvx.musica=vila_b|nenhuma` | OGG com laço |
| ambiente `vento` | jogo, sempre (cama, sem posição, estéreo) | OGG em laço de 24 s |
| ambiente `lago` | jogo, no centro do lago (`World.pondCenter`), posicional, mono: pleno até 2,5 u, some até 14 u | OGG em laço de 16 s |

Regras: nunca a mesma variação duas vezes seguidas (gerador com semente → roteiro reproduzível); interface sem
variação de tom (fica em Ré maior); limite por evento rouba a voz mais antiga dele; limite global (24 vozes)
rouba a mais antiga de prioridade ≤, senão descarta; música pedida de novo não reinicia; troca de música sai em
1,2 s e entra em 1,5 s; pausa abafa a música a 45%, conversa a 70%.

## 3. Pisada sincronizada **[IMPLEMENTADO]**

`Player.animate` conta uma pisada quando `floor(2 · animT · cadência)` muda — exatamente quando `frac(t·cad)` ou
`frac(t·cad + ½)` volta a 0, a fase em que o `Animator` começa o apoio de um pé. Só com o herói no chão e acima de
0,12 u/s. O som coincide com o pé por construção, e a contagem não depende do FPS (testado a 30 × 144 FPS).

## 4. Laço da música **[IMPLEMENTADO]**

O OGG do jogo tem introdução + duas passadas do corpo; o laço é a 2ª passada (que já começa com a cauda da 1ª).
`LoopingReader` volta de `laco_fim` a `laco_inicio` sem pular nem repetir quadro; `stb_vorbis_seek` medido exato no
início do laço. Pontos calibrados no próprio render do LMMS (`tools/audio/render_musica.py`).

## 5. Falhas e modos **[IMPLEMENTADO]**

- Sem dispositivo, sem natives ou sem catálogo → aviso no console e jogo mudo (`SilentOut`), nunca exceção.
- Arquivo ausente → a variação não toca; o problema fica em `SoundSystem.problems()` (teste exige lista vazia).
- `./gradlew jogo` e `./gradlew editor` abrem o dispositivo. Roteiros, capturas, `--demo` e testes são mudos.
- `-Dvx.audio=loopback -Dvx.audio.gravar=x.wav` grava a mistura em sincronia com os quadros do roteiro.

## 6. Ambiente, posição e volume do jogador **[IMPLEMENTADO]**

- Ambientes são fluxos em laço (`SoundSystem.ambient`), entram e saem em 2 s, não reiniciam se pedidos de novo.
  Posicionais exigem OGG mono; o OpenAL atenua por distância (modelo inverso com limite: `distancia_ref`,
  `distancia_max`). O ouvinte (`SoundSystem.listener`) fica no herói, olhando para onde a câmera olha.
- Fontes: `fontes/audio/ambiente/ambiente.py` → `vento.mmp`/`lago.mmp` → `tools/audio/render_ambiente.py` (laço por
  crossfade de igual potência da sobra de 2 s; −24 LUFS).
- Prova de posição: `AudioTest` mede pelo loopback o mesmo lago a 2 u e a 12 u (longe < metade do RMS); no jogo,
  `tools/jogo/09-ambiente.txt` mostra o marulho ~8 dB mais baixo longe do lago.
- Volume do jogador: pausa → Música e Efeitos (−/+ de 10%, 0–100%). Multiplicam o equilíbrio do catálogo
  (`SoundSystem.userVolume`); "Efeitos" vale para efeitos, interface e ambiente. Arquivo ausente ou inválido = 100%.

## 7. Custo **[medido na rodada 09]**

Buffers dos efeitos carregados no início (39 arquivos); música e ambientes decodificados em fluxo (4 × 0,25 s cada,
até 3 fluxos simultâneos: música, vento, lago); 32 fontes reservadas. Nada alocado por quadro além do bloco de decodificação da música.

## 8. Limites e próximos passos **[PLANEJADO]**

- Efeitos curtos posicionais no mundo (NPC, criaturas): os buffers já são mono; falta a API de evento com posição.
- Pássaros e outras camadas de ambiente (fora por risco de soar caricato por síntese); música de exploração e de
  combate; transição por região do mundo.
- Aterrissagem quando houver pulo; passos de NPC.
