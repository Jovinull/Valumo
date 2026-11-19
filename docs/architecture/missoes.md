# Missões (rodada 10)

Camada pequena e reutilizável que transforma os sistemas que já existiam (vila, NPCs, combate, som, interface) numa
aventura. Sem OpenGL, sem som e sem interface: tudo testável em `QuestTest`, `AdventureTest` e `SaveGameTest`.
Decisão: [ADR 0024](../decisions/0024-missoes-e-save.md). A primeira missão: [missao-luzes-do-posto.md](missao-luzes-do-posto.md).
Para criar outra: [../processo/como-adicionar-uma-missao.md](../processo/como-adicionar-uma-missao.md). Save:
[save.md](save.md).

## Peças

| Peça | Onde | O que faz |
|---|---|---|
| `QuestDefinition` | `vx.game.quest` | dados de uma missão, lidos de `assets/scenes/missoes_e.json` (à mão); leitor recusa o malformado com o caminho do campo |
| `QuestProgress` | `vx.game.quest` | progresso de UMA missão; o estado é **derivado** dos campos (`state()`), e `check()` diz se os campos fecham |
| `QuestState` | `vx.game.quest` | disponível · aceita · em andamento · pronta para entregar · concluída |
| `QuestEvent` | `vx.game.quest` | o que aconteceu no mundo: inimigo derrotado (identidade estável), herói derrotado |
| `QuestSystem` | `vx.game.quest` | o único que escreve no progresso; moedas; objetos do mundo acesos; avisos (`Notice`) em ordem; parte do save |
| `DialogueBook` | `vx.game` | falas por contexto, lidas de `assets/scenes/dialogos_e.json`; a regra que escolhe o contexto |
| `SceneDef.WorldObject` | `vx.game` | objeto do mundo com estado (o marco): id, posição, alcance do E, os dois modelos, corpo sólido |
| `SaveGame` | `vx.game` | jogo salvo v1 ([save.md](save.md)) |

O `GameLogic` liga tudo: entrega os eventos depois de cada passo do combate, escolhe as falas, usa os objetos com E,
troca o encontro da arena quando a missão é aceita e salva nos marcos. O `Game` só desenha (marco apagado/aceso, efeito,
placa do E, escolhas, rastreador, aviso, diário) e o `GameAudio` só traduz avisos em eventos de som.

## Estados

O estado nunca é guardado à parte: sai de `aceita`, `concluida`, `objetivo` e `derrotados`. Assim não existe "aceita e
disponível" nem "concluída sem recompensa".

| Estado | Quando |
|---|---|
| `DISPONIVEL` | não aceita (inclusive depois de "Agora não": a oferta continua de pé) |
| `ACEITA` | aceita, nenhum progresso contado |
| `EM_ANDAMENTO` | algum progresso contado; objetivos de campo pendentes |
| `PRONTA_PARA_ENTREGAR` | só falta o último objetivo (falar com quem deu a missão) |
| `CONCLUIDA` | entregue; a recompensa saiu uma vez; nunca reabre |

## Objetivos

Em ordem; o seguinte só vale depois do anterior. O último é sempre `falar` com quem deu a missão (validado no leitor).

| Tipo | Conta quando | Campo |
|---|---|---|
| `derrotar` | um inimigo cuja **identidade estável** está em `alvos` morre enquanto o objetivo é o atual | `alvos` |
| `interagir` | o jogador usa (E) o objeto enquanto o objetivo é o atual | `objeto` |
| `falar` | o jogador começa a conversa com o NPC (a entrega é na hora: sair no meio da fala não perde nada) | `npc` |

### Identidade estável dos inimigos

- Cada spawn de `combate_e.json → encontros` tem uma identidade: o 5º item (`trilha.fungo.1`) ou
  `<encontro>.<índice>`. Missões contam pela identidade, nunca pela ordem na lista.
- `Combat.defeated` recebe a identidade **uma vez por morte**, no passo em que a morte é vista.
- Identidade que já contou entra em `Combat.retired`: o inimigo nasce e recomeça caído e invisível (`Enemy.retire`, sem
  som nem desagregação) e não entra na conta da vitória. Recomeçar o confronto, cair ou carregar o jogo nunca trazem de
  volta um fungo contado e nunca apagam progresso.
- Inimigo que não é alvo (arena de teste `um`, `grupo`, `chefe`) nunca conta.

### Encontro da missão

- A definição pode dizer `encontro` (`trilha`).
- Ao **aceitar**, a arena passa para esse encontro, com inimigos inteiros (o que caiu antes de aceitar não contou e
  volta).
- Ao **continuar** um save com a missão aceita ou concluída, a arena volta ao encontro da missão, com os contados fora.
- A troca de encontro existe só no modo técnico (`-Ptecnico`) e nos roteiros (comparação). No jogo normal a pausa não a
  oferece, e a missão nunca perde os fungos.
- Antes de aceitar, a arena abre no encontro padrão (ou `-Dvx.encontro`), como nas rodadas 09.x: roteiros antigos não
  mudam.

## Objetos do mundo

`jogo.objetos` na cena: `id`, posição, rumo, `alcance`, `modelos.apagado`/`modelos.aceso` (props gerados), `raio` do
corpo sólido (entra na `Collision`) e `placa` (altura da placa "E").

- O E pega o mais perto entre NPC e objeto, com a mesma regra de alcance e ângulo.
- Nada se usa no meio da luta (`combat.engaged()`).
- `QuestSystem.use` devolve o resultado, e a frase vem do arquivo de falas:

| Resultado | Quando |
|---|---|
| `ATIVOU` | era o objetivo atual |
| `JA_ATIVO` | já estava aceso |
| `SEM_MISSAO` | ainda não aceita; vira lembrança para a fala da oferta |
| `CEDO` | falta um objetivo anterior |
| `NADA` | nenhuma missão se importa com o objeto |

## Falas por contexto

`dialogos_e.json → npcs.<id>` (id = pasta do rig, `kelyra`):
- Quem dá a missão (`missao` sem `acrescenta`) tem os **10 contextos obrigatórios** (`DialogueBook.GIVER_CONTEXTS`; o
  leitor recusa se faltar um).
- `escolhas` só na última fala (`aceitar`/`recusar`, `segue` = contexto de reação).
- Quem só comenta (`acrescenta: true`) ganha uma fala a mais depois das de sempre (`missao_ativa`, `missao_concluida`).

Prioridade (`DialogueBook.giverContext`):

| # | Contexto | Quando |
|---|---|---|
| 1 | `oferta` | disponível, nada aconteceu ainda |
| 2 | `oferta_ja_viu` | disponível, o jogador usou o objeto da missão antes de aceitar |
| 3 | `oferta_ja_lutou` | disponível, um alvo da missão caiu antes de aceitar (vale mais que 2) |
| 4 | `oferta_apos_recusa` | disponível, já recusou (vale mais que 2 e 3) |
| 5 | `aceita` | aceita, sem progresso |
| 6 | `em_andamento` | algum alvo contado |
| 7 | `caiu` | o herói caiu desde a última conversa (vale uma conversa; vale mais que 5, 6, 8) |
| 8 | `trilha_limpa` | o objetivo atual é o objeto |
| 9 | `entrega` | pronta: a entrega e a recompensa acontecem ao abrir esta conversa |
| 10 | `concluida` | depois |

Na escolha: ← → (ou ↑ ↓, A/D, W/S) escolhem, E/Enter confirma, Esc sai sem decidir (não conta como recusa).

## Recompensa

- `recompensa` por tipo, extensível. Hoje só `moedas`; tipo desconhecido é recusado no leitor.
- Sai uma vez: `recompensaEntregue` e `concluida` mudam juntos, e uma missão concluída não tem objetivo atual.
- Moedas ficam no `QuestSystem`, salvas, sem inventário nem loja.

## Avisos, som, salvamento automático

`QuestSystem.drain()` depois de cada mudança:

| Aviso | Tela | Som | Salva? |
|---|---|---|---|
| `ACEITA` | "Missão aceita: …" | `missao.aceita` | sim |
| `PROGRESSO` | "objetivo: 1/2" | — (o combate já soa) | não |
| `OBJETIVO` | "Objetivo cumprido: …" | `missao.objetivo` (não no mesmo uso do marco) | sim |
| `OBJETO` | frase do objeto | `missao.sinal` | (pelo `OBJETIVO` que vem junto) |
| `RECOMPENSA` | (entra no aviso seguinte) | `missao.recompensa` | (pelo `CONCLUIDA`) |
| `CONCLUIDA` | "Missão concluída: … · +30 moedas" (um aviso só: antes o da recompensa era trocado na hora) | — | sim |

O salvamento automático espera o primeiro **passo lógico** seguro (`GameLogic.saveBlocker`):
- fora da pausa de impacto e da luta;
- herói de pé e no chão;
- jogo começado.

Nunca a cada quadro. Como é medido em passos, o mesmo jogo salva no mesmo passo a 30 e a 144 quadros/s
(`AdventureTest.mesmaMissaoA30EA144Quadros`).

## Interface

- **Placa "E":** "Acender o marco" (apagado) ou "Examinar o marco" (aceso).
- **Escolhas:** na última fala da oferta.
- **Rastreador:** canto superior direito, abaixo da faixa das mensagens.
  - Mostra o nome, o objetivo atual e o progresso 1/2.
  - Some na luta, com chefe na arena e sem missão aceita.
  - T no diário alterna, e a escolha fica guardada nas preferências (`rastreador`).
- **Aviso:** abaixo do rastreador.
- **Diário (J, ou na pausa):** a lógica para enquanto está aberto. Mostra:
  - nome, estado e descrição;
  - os objetivos, com marca de feito e de atual;
  - a recompensa prometida ou recebida;
  - o registro (aceita/concluída aos N min de jogo);
  - as moedas.
- **Tela inicial:**
  - "Jogo novo" e "Continuar o jogo salvo".
  - Sem save válido, o Continuar aparece apagado com o motivo curto, e o clique mostra o completo.
  - Teclas: Enter = jogo novo; C = continuar.
- **Pausa:** "Salvar jogo" (recusa com o motivo na luta, no ar ou caído) e "Diário".

Tudo com `UiKit` + `PixelFont`.

## Testes

| Classe | O quê |
|---|---|
| `QuestTest` (13) | arquivo da missão; os estados; contagem única; antes de aceitar; arena de teste; ordem; recompensa única; recusar; cair; save da parte das missões em cada estado; inconsistências recusadas; arquivo de missão malformado; os 10 contextos alcançáveis; Bramor |
| `AdventureTest` (10) | a aventura inteira pela lógica do jogo, com save e continuar; a matriz §16 (antes, durante, depois); não salvar na luta; posição salva inválida; save inválido; determinismo 30 × 144 quadros/s |
| `SaveGameTest` (6) | ida e volta (presets idênticos, aparência personalizada); escrita atômica; ausente; 19 formas de arquivo corrompido; aparência incompatível; versão |
| `GameAudioTest` (+1) | aviso → evento de som; eventos no catálogo |
