# Relatório — Integrações da 09.4: PR #1 (áudio e personalização) e NPCs de ofício

Duas integrações feitas em sequência sobre a `main` depois da publicação das rodadas 09–09.3 (tag
`combat-foundation-v0.2`, `3596f36`):

1. **PR #1**: trabalho feito em paralelo como "rodada 09" (direção sonora, música, efeitos, ambiente e
   personalização), integrado como **rodada 09.4** e publicado na `main` em `6da5a20`.
2. **NPCs de ofício**: cinco NPCs novos vindos de um estudo isolado, integrados ao pipeline de assets, à animação e
   à vila jogável com conversa.

O detalhe artístico e técnico da 09.4 (música, efeitos, cabelos, olhos, botas) está em
[relatorio-rodada-09-4.md](relatorio-rodada-09-4.md). Este documento registra **o que a integração fez**: conflitos,
decisões, correções, validação e publicação.

---

## Parte 1 — PR #1 → rodada 09.4

### 1.1 Ponto de partida

- O PR #1 (`rodada-09-audio-e-personalizacao`) foi desenvolvido sobre a `main` antiga (`24e83df`, rodada 08.1).
  Naquela base ainda não havia combate, câmera de luta, mobilidade, esquiva nem o som do combate.
- Enquanto isso, a `main` recebeu as rodadas 09, 09.1, 09.2 e 09.3 (21 commits). As duas linhas tocavam nos mesmos
  pontos: `Game`, `GameLogic`, a pausa, o ADR seguinte, os scripts de revisão e o som.

### 1.2 Conflitos resolvidos e renomeações

| No PR | Na integração | Por quê |
|---|---|---|
| "rodada 09" | **rodada 09.4** (relatório, pacote `output/visual-review/round-09-4/`) | 09–09.3 já eram do combate |
| ADR 0015 (áudio) | **ADR 0023** ([0023-audio-openal-stb-vorbis.md](../decisions/0023-audio-openal-stb-vorbis.md)) | 0015–0022 já existiam |
| `tools/review9.sh` | **`tools/review94.sh`** | `review9.sh` é o pacote do combate |
| efeitos sintetizados em `assets/audio/` | **`assets/audio/sintese/`** (gerado por `tools/audio_e.py`) | separar o gerado do versionado à mão |
| preferências de áudio no arquivo comum | propriedade própria **`vx.prefs.audio`** | não misturar com as preferências do combate |

A `main` foi mesclada no ramo do PR, os conflitos foram resolvidos arquivo a arquivo e o resultado foi validado
antes de qualquer decisão sobre o merge.

### 1.3 Um só dispositivo de som

As duas linhas tinham cada uma o seu som: o catálogo do PR (música, ambiente, passos, interface) e os efeitos do
combate da 09.1. Abrir dois dispositivos OpenAL no mesmo processo dá dois contextos que competem pela saída.

- O `Audio` (engine) abre **o** dispositivo; o `Sfx` do combate toca no mesmo contexto por `OpenAlBackend.shared`.
- O ouvinte fica na câmera, e o volume vai por voz.
- Os passos vêm do catálogo. O `Footsteps` sintetizado só toca quando não há catálogo.
- A regra "o som só lê a lógica depois de cada passo" vale para os dois: a luta é idêntica com e sem som (teste).
- O adendo do ADR 0023 registra a decisão.

### 1.4 Outras costuras

- **Pausa:** junta Música, Efeitos, mudo e as opções de combate (barras, sensação, arena), com rótulos que cabem em
  720p.
- **`viewer --check-cabelos`:** ganhou a tabela do equipamento de combate (espada e broquel em toda aparência), além
  da tabela dos presets.
- **Ferramentas:** LMMS 1.2.2 instalado por `flatpak --user` (sem privilégio de administrador); `soundfile` e
  `scipy` no `.venv`.

### 1.5 Decisões do proprietário e o que cada uma exigiu

1. **Síntese própria no lugar do banco de amostras.**
   - As duas músicas e os sons da interface foram refeitos nos sintetizadores do LMMS (`tools/audio/sintese.py`),
     com as mesmas notas, forma e mistura.
   - O equilíbrio entre faixas foi medido de novo (`tools/audio/nivelar.py`), e o banco saiu do projeto.
   - **Achado:** a sonda que calibra o ponto de laço usava o banco. Sem ele, saía muda e adiantava o laço em 2 400
     amostras (50 ms). Agora a sonda usa um clique próprio, e uma sonda muda é erro. Os pontos de laço do catálogo
     foram restaurados.
2. **Música A como padrão e B como alternativa**, as duas aprovadas (`-Dvx.musica=vila_b`).
3. **Estudos aprovados:** crespo, coque alto, formatos de olho e botas viajante (`"aprovado": "rodada 09.4"`).
   - Continuam protegidos pela regra de componente novo (`HairWeaponCheck.isNew`).
4. **EMOTE corrigido** na espada e no cajado, com 0 voxel atravessando qualquer penteado.
   - Isso revelou o roçar do corte vertical no ápice, corrigido com 1 voxel na mão de `v_drive`.
   - Com autorização, também foram corrigidos:
     - a **vitrine `combo`**: pose `cv_drive` com a mão mais aberta;
     - o **cajado da maga**: a causa era a pose de descanso, e o cajado foi inclinado para fora e à frente, com a
       empunhadura mais alta.
   - Cada mudança passou por toda a bateria de combate: `--check`, `interrupcao-fina`, `golpe-andando`, `esquiva` e
     `--check-cabelos`.
5. **"Preso" (rabo) nos outros corpos:** antes a mecha amarrada encostava na cabeça, a ponta flutuava e o rabo ficava
   fora do centro.
   - Agora é um rabo de cavalo centrado e amarrado rente ao cabelo (`porCorpo` no `Composer` e em
     `tools/components_e.py`).
   - A exploradora mantém a trança canônica.

### 1.6 Achados no caminho

| Achado | Causa | Correção |
|---|---|---|
| Sonda do EMOTE do cajado dizia "0 voxel" | relatava só o pior instante | medição por gesto; a causa real era a pose de descanso |
| Ferramenta de validação "sem FAIL" após mudar o cajado | rodada com `idle` (inválido), que falhava antes de validar | rodar com `clip:IDLE` e contar as linhas OK; o ajuste foi refeito dentro do alcance |
| `AudioTest`: pico da interface 1 951 < 2 000 | interface sintetizada 1 dB mais baixa | render da interface com `--alvo-rms -24.5`; o teste não mudou |
| Áudio antigo, renderizado com o banco, sobrando em `assets/audio/` | mudança de pasta | movido para `output/quarentena/audio-local-antigo-2026-11-18/` com `LEIA-ME` |

### 1.7 Publicação

- **Squash local** na `main`: `6da5a20`, com mensagem só de título e e-mail noreply. São 155 arquivos e +8 699 / −176
  linhas.
  - Motivo do squash: tirar do histórico público os renders antigos feitos com o banco de terceiros e garantir o
    e-mail noreply. O squash pelo site usaria o e-mail da conta.
- **Push:** `3596f36..6da5a20` normal, sem reescrever histórico.
- **PR #1:** fechado com comentário (aparece como "closed", não "merged").
  - A árvore publicada é idêntica à do ramo validado (`ccf2a8e`).
  - Esse ramo foi conferido num clone do GitHub: 241 testes, 0 falhas.
- A tag `combat-foundation-v0.2` não foi movida. Não houve tag nova.

### 1.8 Pendente da 09.4

- A **escuta** das músicas e da interface sintetizadas é do proprietário. O agente só mediu: o QA cobre 43 arquivos,
  com 0 alertas.

---

## Parte 2 — NPCs de ofício

### 2.1 O que chegou

Um estudo isolado, feito fora do repositório, de cinco NPCs: um gerador próprio, prévias e uma cópia **mais antiga**
da `charlib_e`.

- Entrou só o **modelo**, reescrito no pipeline do projeto.
- A `charlib_e` antiga do estudo foi descartada em favor da do repositório.
- Nenhum outro material do estudo entrou no repositório.

### 2.2 Quem são

| NPC | Ofício | Altura narrativa → mundo | Cabeças | Faces | Traços |
|---|---|---|---|---|---|
| **Bramor** | guardião de trilhas | 2,30 m → 1,725 u | 2,76 | 11 970 | barba cheia, óculos retangulares, cabelo longo |
| **Teodor** | estudioso | 1,80 m → 1,35 u | 2,84 | 5 360 | barba aparada, óculos redondos, paletó escuro |
| **Pluma** | cronista | 1,60 m → 1,20 u | 2,67 | 4 248 | cavanhaque, óculos retangulares, asinhas na cabeça |
| **Ondina** | guia costeira | 1,70 m → 1,275 u | 2,76 | 6 118 | chapéu de palha, top azul, short |
| **Lunara** | cantora noturna | 1,66 m → 1,25 u | 2,73 | 4 498 | halo de cachos, óculos finos, vestido creme com faixa vinho |

- **Gerador:** `tools/npcs_oficios_e.py`, saída em `assets/characters/npc/<chave>/` (gerada, fora do git).
- **Base:** cada um é construído com o construtor dos NPCs da vila (`npcs_vila_e.npc`), com roupa, detalhes de rosto
  e atuação próprios.

### 2.3 Altura narrativa sem quebrar o contrato

- A altura vem de **reamostrar o rig pronto** (`scale_vox`), à razão de 0,75 u por metro narrativo, e não de alongar
  o corpo.
  - A proporção em cabeças (faixa de NPC 2,0–3,0, acima de 2,65 para não cair na faixa dos protagonistas) e o voxel
    1/40 não mudam.
  - `meta` grava `escala`, `metersNarrative`, `worldUnits`, mãos e `*_volumes` escalados.
- **Limites que dependem do tamanho escalam com o rig, e só eles:**
  - faces ≤ 9 000 × escala² (o Bramor, com 11 970, fica dentro do orçamento dele, de 21 150);
  - tronco z ≤ ⌈5 × escala⌉.
- Num NPC de tamanho normal os limites são os da vila. Nenhum limite foi afrouxado para o tamanho normal.

### 2.4 Correções de desenho na integração

| Problema no estudo | Correção |
|---|---|
| Vestido da Lunara com volume de busto e decote fundo e assimétrico | vestido limpo que envolve o tronco em massa única (conferido de frente, de lado e de costas) |
| Decote do top da Ondina | top na altura certa, alças e sem recorte |
| Barba do Bramor cobria a boca | barba cheia com bigode fino que deixa a boca à vista |
| Bigode do Teodor lia como "boca aberta" | barba aparada sem bigode |
| Óculos e halo da Lunara escondiam a sobrancelha: a "determinada" mudava só 5 voxels | armação fina abaixo da sobrancelha |
| Comentários e notas com descrição fora do jogo | notas dizem só o que cada um é no jogo |

### 2.5 Gesto em escala (`Animator`)

- O gesto compartilhado de NPC (`assets/anim/npc_vila.json`) guarda posições de mão em voxels do esqueleto de
  origem. Num rig reamostrado, as mãos do Bramor miravam pontos 1,5× mais perto do que deviam: antebraço × tronco 3,13
  e alcance 7,72 voxels no validador.
- **Correção:** o `Animator` lê a coreografia em `cvs = voxelSize × meta.escala`. A troca vale na camada de ação, no
  `applyControl`, no `fkHand` e no giro.
- Sem `escala` (heróis, cartógrafa, NPCs da vila), `cvs` = `voxelSize`: os rigs antigos ficam idênticos. O gate dos
  presets continua com 51 pares e 0 pixel.

### 2.6 Na vila jogável

**Falas:** três por NPC, originais e ligadas ao ofício, com expressões, em `jogo.interativos` de
`assets/scenes/jogo_vila_e.json`.

**Câmera da conversa** (`GameLogic.startDialog`):
- **Altura:** o alvo sobe e a distância cresce na proporção da altura do rig. `SceneDef.Interactive.height` vem de
  `meta.worldUnits`, e até 1,25 u nada muda (Kelyra igual).
- **Lado:** entre os dois lados, e por cima do ombro se os dois fecharem, vence o mais perto de onde a câmera já
  estava **entre os que têm a vista livre**. Antes, uma pedra no lado escolhido empurrava a câmera para dentro do rosto.

**Vagas:** escolhidas por sonda de colisão e confirmadas por captura. Cada tentativa errada mudou o critério:

| Tentativa | O que a captura mostrou | Critério acrescentado |
|---|---|---|
| anel de corpo livre | Pluma sob a copa do salgueiro (a copa só bloqueia a câmera) | nenhuma copa por cima da cabeça |
| perto das pedras a oeste | pedra entre a câmera e o rosto | lado da câmera com vista livre (correção da câmera) |
| junto à praça | poste de lanterna fino na frente do rosto | **nenhuma forma, de corpo ou de câmera, a menos de 1,6 u** |

Vagas finais:

| NPC | Posição (x, z) | Onde fica |
|---|---|---|
| Bramor | (−10, −2) | saída oeste das trilhas |
| Teodor | (2, −4) | a leste da praça |
| Ondina | (8, −1) | perto do lago |
| Lunara | (1, −8) | perto da casa ao norte |
| Pluma | (−1,5, −10) | borda norte, a única vaga livre que restou |

Todos ficam fora do alcance da coleira da arena.

**Roteiro de captura:** `tools/jogo/10-npcs-oficio.txt` (chegada e três falas de cada um).

### 2.7 Organização

- **`tools/npcs_trio.py` → `tools/npcs_vila_e.py`:** convenção `<assunto>_e.py`. São 12 arquivos de referência
  atualizados (código, testes, documentação, skill). O histórico em `docs/historico/` ficou como estava.
- **`build_assets.py`:** ganhou `npcs_vila_e` e `npcs_oficios_e`.
- **Documentação:**
  - mapa do `CLAUDE.md` e README (NPCs, tecla `E`);
  - contrato de qualidade: regra de altura narrativa;
  - `jogo.md`: conversa, câmera e vagas;
  - skill `character-design`: NPC com altura narrativa;
  - problemas conhecidos.

### 2.8 Testes novos

- **`OficiosNpcTest`** (9 testes):
  - contrato de NPC na escala de cada um;
  - razão das alturas (2,30 : 1,80 : 1,60);
  - sem fragmentos;
  - nenhum é recoloração de outro nem dos NPCs da vila (Jaccard de corpo < 0,88, de cabeça < 0,85);
  - expressões com atuação própria (≥ 6 voxels; Jaccard < 0,6 entre pares);
  - tronco sem volume extra à frente;
  - gesto compartilhado no validador espacial (`emote`, caminhada, corrida e sequência);
  - atuação sutil e ofício próprio;
  - metadados sem palavras de vínculo externo.
- **`GameplayTest.everyResidentStandsOnFreeGroundWithRoomToTalk`:**
  - chão caminhável dentro do limite;
  - NPC ocupando espaço;
  - pelo menos três falas;
  - distância mínima entre NPCs;
  - nenhuma forma a menos de 1,6 u;
  - céu livre acima da cabeça.
- **`GameplayTest.playableSceneLoadsWithCollidersAndKelyra`:** agora espera seis interlocutores.

---

## 3. Validação final (as duas integrações juntas)

| Verificação | Resultado |
|---|---|
| `./gradlew test` | **251 testes, 0 falhas** (engine 29, viewer 107, game 115) |
| `viewer --check` | TODOS OK |
| `viewer --check interrupcao-fina` / `golpe-andando` / `esquiva` | seguros |
| `viewer --check-cabelos` | nenhum estudo pior que a referência |
| `tools/review7.sh gate` | 51 pares, 0 pixel |
| `tools/check_links.py` | 0 links quebrados |
| Revisão visual | escala ao lado dos heróis; chegada e três falas de cada NPC na vila |

Pacote local, fora do git: `output/visual-review/round-09-4-npcs-oficio/` (`01-escala-ao-lado-dos-herois.png`,
`02-conversas-na-vila.png`, `conversas/`).

## 4. Limitações e próximos passos

- Os NPCs de ofício compartilham o gesto `acenar`. Não têm reação a golpe nem rotina; ficam parados no lugar.
- A vila está apertada: um sexto NPC de ofício já não teria vaga sem mexer em props.
- Postes de lanterna continuam sem volume de câmera. A conversa agora escolhe o lado com vista livre de pedras e
  paredes, mas um poste fino ainda pode cruzar o quadro se o NPC ficar colado a ele (as vagas evitam isso).
- A escuta do áudio da 09.4 continua com o proprietário.
