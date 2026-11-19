# Relatório da Rodada 10 — "As Luzes do Posto"

**Situação:** **aprovada pelo proprietário** depois do teste físico da aventura. Fechada no checkpoint
`adventure-foundation-v0.4`; veja "Fechamento" no fim. Parte do checkpoint `engineering-foundation-v0.3` (`main`
`07ec057`). O Titã não foi usado.

Pacote local: `output/visual-review/round-10/` (LEIA-ME, capturas, vídeos, marco antes × depois, casos, desempenho,
registros, ZIP). Documentos: [missoes.md](../architecture/missoes.md), [save.md](../architecture/save.md),
[missao-luzes-do-posto.md](../architecture/missao-luzes-do-posto.md),
[como-adicionar-uma-missao.md](../processo/como-adicionar-uma-missao.md), [ADR 0024](../decisions/0024-missoes-e-save.md),
[teste físico](roteiro-de-teste-fisico-aventura.md).

## O que mudou

| Área | Antes | Agora |
|---|---|---|
| Missões | não existiam | `vx.game.quest` (definição, progresso, estado derivado, eventos, sistema) + `missoes_e.json` |
| Kelyra | 4 falas fixas | 10 contextos + 2 reações (`dialogos_e.json`), oferta com Aceitar / Agora não |
| Bramor | 3 falas | + 1 fala por contexto (missão ativa / concluída), sem segunda missão |
| Arena | encontros `um`/`grupo`/`chefe` | + `trilha` (2 fungos com identidade estável). Os que contaram não voltam. Antes de aceitar, a arena é a de sempre |
| Mundo | — | o marco de luz do posto (`trail_beacon_off`/`_on`, `tools/env_e.py`), objeto com estado, corpo sólido, E |
| Interface | escolha, exploração, conversa, pausa | + Jogo novo / Continuar, escolhas na conversa, placa do objeto, rastreador, aviso, diário (J), Salvar jogo e Diário na pausa |
| Persistência | aparências e preferências | + jogo salvo v1 (`output/saves/aventura.json`, atômico, `-Dvx.savegame`) |
| Som | 43 arquivos | + 4 sons da missão (`fontes/audio/sfx/missao/`, proposta A) e 4 eventos `missao.*` |
| Testes | 290 | 324 (ver abaixo) |

## Defeitos encontrados e corrigidos durante a rodada

1. **Alcance do marco.** O E no marco não pegava no roteiro da aventura: o alcance era 1,5 u, e o `ir` para a ~0,4 u
   do ponto. Passou a 1,8 u, o alcance dos NPCs.
2. **"Trilha limpa" virava vitória.** Limpar a trilha mostrava "vitória — R recomeça", e o R traria os fungos de volta.
   A conta da vitória agora ignora quem já contou para a missão.
3. **Recompensa nunca aparecia na tela.** O aviso "+30 moedas" era trocado na hora pelo "Missão concluída", porque os
   dois saem no mesmo lote. Agora é um aviso só: "Missão concluída: … · +30 moedas" (teste
   `AdventureTest.recompensaApareceNaTela…`).
4. **Save inválido mostrava o texto bruto do Gson**, com o link de ajuda, e a mensagem passava da tela. Agora o motivo é
   curto (`SaveGame.brief`), e toda mensagem termina em "…" na largura da tela.
5. **Mensagem por cima do título da pausa** ("Não dá para salvar no meio da luta"). Nos menus, a mensagem foi para baixo.
6. **Rastreador coberto pela mensagem do topo** em 720p: o rastreador desceu para baixo da faixa das mensagens.
7. **`ui.salvar` junto com o som da missão** em cada salvamento automático. O automático ficou mudo; só "Salvar jogo"
   soa.
8. **Rótulos da pausa encolhiam** a fonte ("Trocar aparência" em meia largura): agora "Aparência" e "Criador".
9. **Sementes de luz quase invisíveis** (1/36 u): passaram a 1/24 u, ainda discretas (5 pontos, 2,4 s).
10. **Roteiros antigos que conversavam com a Kelyra até o fim** (`tools/jogo/capturas.txt`, `05-kelyra.txt`,
    `07-audio.txt`, `tools/qa/sessao_longa.py`): o último E agora aceitaria a missão. Encerram com Esc, que se comporta
    igual no binário de antes e no de agora. Assim a comparação de regressão continua valendo.
11. **Mensagem na tela inicial.** Descida para baixo, a mensagem cobria o botão "Continuar". Ali ela voltou ao topo;
    só a pausa e o diário a mostram embaixo.
12. **Avisos de compilação que eu tinha introduzido.** `javac -Xlint:all` e o import limpo da IDE pegaram um comentário
    de documentação duplicado (`CombatDef.Spawn`) e um import sem uso (`DialogueBook`). Os dois foram corrigidos e
    agora estão em 0.

## Portões (§25)

| # | Portão | Resultado | Evidência |
|---|---|---|---|
| 1–3 | aceitar; recusar e voltar; objetivos em ordem | sim | `AdventureTest`, `QuestTest`, `casos/a*` |
| 4–5 | fungos da missão derrotáveis; arena de teste não completa | sim (luta real no vídeo; `grupo`/`chefe`/`um` não contam) | vídeo 01, `QuestTest.morteAntesDeAceitar…`, `AdventureTest.durante…` |
| 6–7 | sinal restaurado; mudança persiste | sim (save + continuar) | `marco/`, `capturas/19–22` |
| 8 | Kelyra reconhece o progresso | sim (10 contextos, todos alcançáveis) | `QuestTest.falasDaKelyra…`, `casos/` |
| 9, 16 | recompensa uma vez; sem duplicação conhecida | sim | `depoisDeConcluir…`, `recompensaSaiUmaVez…` |
| 10 | diário correto | sim | `capturas/07`, `17`, `22`; `diarioParaALogica…` |
| 11–13 | salvar; continuar após reiniciar; aparência preservada | sim (duas execuções reais com o mesmo arquivo) | vídeo 01 (parte 2), `SaveGameTest.idaEVolta…` |
| 14 | corrupção | 19 formas + falha de escrita + aparência incompatível + posição inválida | `SaveGameTest`, `casos/c*` |
| 15 | sem softlock no percurso principal | nenhum conhecido (matriz §16) | `relatorio-gameplay.md` |
| 17–20 | combate, esquiva R2, salto, câmera | validadores ok; testes de combate/câmera/mobilidade verdes; nada mudou neles | `validadores.txt`, `testes.txt` |
| 21 | música e sons | catálogo 47 arquivos 0 alertas; 4 sons da missão tocando no instante certo (log de som) | vídeo 01 com som da aplicação |
| 22 | NPCs válidos | `GameplayTest` (vagas, falas), `OficiosNpcTest`, `VilaNpcTest` verdes | `testes.txt` |
| 23–24 | testes antigos e novos | **324, 0 falhas** (290 + 34) | `testes.txt` |
| 25 | validadores | sem regressão (iguais à linha de base) | `validadores.txt` × `linha-de-base/validadores.txt` |
| 26 | presets | **51 pares, 0 pixel** | gate |
| 27 | referências | **22/22, desvio 0** | `referencias.txt` |
| 28 | assets determinísticos | 1.318 arquivos, mesmo hash depois de apagar e regenerar (clone e checkout) | — |
| 29 | clone limpo | build, 324 testes, validadores, links: ok (clone do HEAD + as mudanças da rodada, `PATH` sem `.venv`, `.venv` nova) | — |
| 30 | vídeo do ciclo completo | sim: 20 pontos, duas execuções reais, som da aplicação | `videos/01-aventura-completa.mp4` |

## Desempenho (1080p, Iris Xe, mesma sessão, 3 repetições, mesmos roteiros do checkpoint)

| Cenário | Quadro (mediana, ms): antes → depois | p95 | Lógica | Coletas |
|---|---|---|---|---|
| vila | 9,43 → 9,42 | 10,95 → 10,65 | 0,29 → 0,29 | 1 → 1 |
| combate um | 8,40 → 8,63 | 9,88 → 9,84 | 0,34 → 0,37 | 1 → 1 |
| combate grupo | 9,00 → 8,77 | 10,31 → 10,10 | 0,47 → 0,42 | 1 → 1 |
| combate chefe | 8,71 → 8,51 | 10,49 → 10,35 | 0,43 → 0,42 | 1 → 1 |
| combate trilha (novo) | — → 9,61 | — → 11,61 | — → 0,39 | — → 1 |
| vila com a missão ativa + 3 s de diário × sem missão (mesmo binário) | 9,27–9,43 → 9,43–9,56 | ~11,0 → ~11,1 | 0,28–0,29 → 0,26–0,28 | 1 → 1 |

Leitura:
- **Vila e combates:** dentro da variação da GPU integrada (±0,2 ms entre repetições).
- **Missão ativa:** custa ~0,1 ms de desenho (rastreador e diário), e a lógica fica igual.
- **Malhas vivas:** 212 nos dois. O marco usa as duas malhas de prop já carregadas: trocar de estado não cria recurso.
- **Memória Java em uso:** 26–81 MB nos dois binários, conforme o momento da coleta.
- **Cena montada:** 1,9–2,0 s nos dois.

**Save:** 915 bytes. Gravar com sincronização, reler e validar: mediana 2,0 ms, máximo 12–14 ms (a primeira, a frio).
Ler: 0,24 ms. Só acontece nos 4 marcos da missão e no "Salvar jogo", nunca por quadro.

A trilha mede mais que um fungo por ter dois inimigos e outro enquadramento. Não há linha de base para ela.

## Respostas (§26)

1. **Arquitetura.**
   - Missão = dados (`missoes_e.json`) + uma camada lógica pequena sem interface (`vx.game.quest`: `QuestDefinition`,
     `QuestProgress`, `QuestState`, `QuestEvent`, `QuestSystem`).
   - O `GameLogic` liga mundo, falas, combate e save. O `Game` e o `GameAudio` só leem.
   - ADR 0024.
2. **Objetivos.** Lista ordenada de `derrotar` (identidades estáveis), `interagir` (objeto) e `falar` (NPC). O último é
   sempre falar com quem deu a missão. O estado é **derivado** de aceita, concluída, índice do objetivo e alvos
   contados.
3. **Combate.**
   - Cada spawn tem identidade (`trilha.fungo.1`).
   - O `Combat` publica a morte uma vez no passo lógico (`Combat.defeated`), e o `GameLogic.questsAfterTick` entrega
     `EnemyDefeated`.
   - Só conta se a missão estiver aceita e o objetivo atual listar o alvo.
4. **Duplicações.**
   - Cada identidade conta uma vez (`LinkedHashSet`).
   - O que contou vai para `Combat.retired`: nasce e recomeça caído e invisível, e fica fora da vitória.
   - A recompensa e o "concluída" mudam juntos.
   - Missão concluída não tem objetivo atual.
   - O save que contradiz isso é recusado.
5. **Diálogos contextuais.**
   - Ficam em `dialogos_e.json`: 10 contextos obrigatórios para quem dá a missão, mais as reações às escolhas.
   - Uma função pura escolhe o contexto pelo estado e pelas lembranças (viu o marco, lutou, recusou, caiu).
   - A entrega acontece ao abrir a conversa de entrega.
6. **Bramor.** Uma fala a mais (`acrescenta`), durante a missão (dica: "os cardos andam em dupla") e depois (soube que
   o marco acendeu; no fechamento deixou de afirmar que vê a luz). Sem segunda missão.
7. **Objeto interativo.**
   - Fica em `jogo.objetos`: id, posição, rumo, alcance 1,8 u, dois modelos, corpo sólido e altura da placa.
   - O E pega o mais perto entre NPC e objeto (mesma regra de alcance e ângulo), nunca no meio da luta.
   - `QuestSystem.use` devolve `ATIVOU`, `JA_ATIVO`, `SEM_MISSAO`, `CEDO` ou `NADA`, e a frase vem do JSON.
8. **Aparência do sinal.**
   - Dois props gerados (`trail_beacon_off`/`_on`), com a mesma forma: estela, braço de ferro e lanterna.
   - Apagado: vidro fosco e semente cinza-esverdeada.
   - Aceso: vidro de luz quente (o emissivo das lanternas da vila) e 1 semente P1 verde-água de 2 voxels.
   - Ao acender, 5 sementes de luz sobem em 2,4 s (só desenho) e toca o som `missao.sinal`.
   - O estado fica salvo em `objetosAtivos`.
9. **Recompensa.**
   - `recompensa` por tipo (`moedas: 30`), extensível; tipo desconhecido é recusado.
   - Sai uma vez, ao abrir a conversa de entrega.
   - As moedas são salvas e aparecem no diário e no aviso "Missão concluída · +30 moedas".
10. **Formato do save.** `valumo.save` v1 em `output/saves/aventura.json`, com:
    - versão, id e cena;
    - a aparência embutida (contrato do compositor);
    - posição e direção, e o tempo de jogo;
    - o progresso: moedas, objetos acesos e, por missão, aceita/recusada/objetivo/concluída/recompensa, derrotados,
      lembranças e tempos.
    - Detalhes em `save.md`.
11. **Salvamento seguro.**
    - Temporário na mesma pasta → `force` → fechar → reler e validar com o mesmo leitor → movimento atômico.
    - Falha em qualquer passo: o antigo fica intacto e o temporário some.
    - Jogo novo por cima de outro guarda `aventura.anterior.json`.
    - Nunca na luta, na pausa de impacto, caído ou no ar. Automático só nos marcos, no primeiro passo seguro.
12. **Recuperação.**
    - Arquivo ausente = sem save.
    - Qualquer outro problema = recusado com motivo curto, original em `.invalido`, "Continuar" apagado com a
      explicação e "Jogo novo" livre.
    - Aparência incompatível: progresso mantido, com o Aventureiro.
    - Posição inválida: início da vila.
    - Sem migração falsa.
13. **Começar e continuar.**
    - Tela inicial com "Jogo novo" (Enter) e "Continuar o jogo salvo" (C).
    - Continuar carrega aparência, posição (validada), missões, moedas, objetos e a arena da missão.
14. **Softlocks.**
    - **Na lógica:** o estado é derivado; nada se perde ao cair ou recomeçar; o que contou não volta e o que não contou
      volta inteiro; aceitar repõe os fungos da missão.
    - **No marco:** o E usa a mesma regra dos NPCs, com alcance medido.
    - **No save:** posição segura e save inválido não bloqueia o jogo novo.
    - A matriz §16 está toda em teste.
15. **Testes:** 324, 0 falhas, 0 pulados (engine 45, viewer 116, game 163).
16. **Testes novos:** 34.
    - `QuestTest` (13);
    - `AdventureTest` (13): fluxo completo, matriz §16, salvar na luta, posição e save inválidos, diário, recompensa
      exibida, mover depois de carregar, tela inicial, 30 × 144 quadros/s;
    - `SaveGameTest` (7): ida e volta, escrita atômica, falha de escrita, ausente, 19 corrupções, aparência, versão;
    - `GameAudioTest` (+1).
    - `GameplayTest`: a expectativa da conversa da Kelyra foi atualizada (agora é a oferta).
17. **Regressões verificadas.**
    - Testes, validadores (`--check` ×4, `--check-cabelos`, `--occlusion`), gate 51/0, referências 22/22, links e QA
      do áudio.
    - Capturas do checkpoint × de agora nos roteiros da 08 e da 09.2: só a interface nova e o marco no fundo da
      clareira.
    - Clone limpo, determinismo, `javac -Xlint:all` 0, IDE 0.
    - Sessão longa curta (2 ciclos) sem erro.
18. **Desempenho:** igual ao checkpoint dentro do ruído; a missão ativa custa ~0,1 ms de desenho (tabela acima).
19. **Problemas restantes:** P49–P51 em `problemas-conhecidos.md`:
    - escuta dos sons da missão;
    - teste físico;
    - conteúdo candidato (marco, falas, Kelyra com missão);
    - o marco não aparece nas capturas feitas da estalagem, embora a Kelyra dissesse "daqui eu vejo" (**corrigido no
      fechamento:** as falas não afirmam mais ver o marco);
    - a troca de arena na pausa tirava os fungos da missão (**corrigido no fechamento:** só no modo técnico);
    - limites de escopo: uma missão por NPC, um save.
20. **Executar e reproduzir.**
    - `./gradlew jogo`, ou `./gradlew jogo -Dvx.savegame=/tmp/x` para um save de teste.
    - O pacote: `tools/review10.sh [capturas|marco|casos|desempenho|aventura-desempenho|testes|validadores|regressao|zip]`,
      sempre com saves e preferências temporários.
21. **Teste físico:** `roteiro-de-teste-fisico-aventura.md`, uns 20 minutos. Cobre:
    - teclado, mouse e cursor preso;
    - ler o marco aceso de longe;
    - a escuta dos 4 sons;
    - fechar o jogo e continuar;
    - o "Jogo novo" por cima.
22. **Segunda missão sem copiar a implementação?** Sim, para missões com os três tipos de objetivo, recompensa em moedas
    e um NPC por missão: é só dados mais um teste (guia `como-adicionar-uma-missao.md`). Duas missões do mesmo NPC pedem
    estender `dialogos_e.json` (contexto por missão).
23. **Começo, meio e fim reais?** Sim.
    - **Começo:** a oferta, com escolha.
    - **Meio:** a trilha e a luta, que guarda o objetivo.
    - **Fim:** o marco aceso, que fica no mundo, a volta a pé e a entrega, com consequência persistente (moedas e
      registro) que sobrevive a fechar e abrir o jogo.
24. **Avaliação crítica.**
    - **Pontos fortes:**
      - a missão é pequena e coesa, porque usa o que já existia (trilha, clareira, fungos, Kelyra);
      - os 10 contextos fazem a Kelyra parecer atenta;
      - o marco lê como "o mesmo objeto, aceso";
      - nenhuma duplicação nem travamento conhecido.
    - **Pontos fracos:**
      - é curta (~2 min para quem conhece o caminho);
      - as moedas ainda não servem para nada;
      - o marco não se vê da vila, e a fala da Kelyra prometia que se via (corrigido no fechamento);
      - o efeito de sementes é discreto a ponto de só se notar de perto;
      - os sons não foram ouvidos;
      - a validação é por entrada injetada.
    - **Para aprovar,** falta o teste físico e a escuta do proprietário.

## Fechamento (aprovação do proprietário)

- **Aprovado:** o proprietário jogou a aventura com teclado, mouse e som reais e aprovou a missão, o diário, o marco, a
  recompensa e o save. A missão, o marco, as falas e a consequência visual passam de candidatos a **conteúdo aprovado**:
  a referência de qualidade de gameplay.
- **Correção narrativa:** nenhuma fala afirma o que o NPC não vê. O marco não aparece da estalagem nem do lugar do
  Bramor, e a localização foi aprovada como está.
  - A Kelyra deduz pelo retorno: "Voltou inteiro — e com esse sorriso, o marco está aceso, não está?". Em
    `trilha_limpa`, diz "Depois volte e me conte".
  - Em `em_andamento`, ela não "ouve a confusão daqui"; pela cara do herói, ainda há fungo.
  - Em `concluida`: "Desde que você acendeu o marco, a trilha do sul ficou outra".
  - O Bramor "soube" do marco e sobe a trilha "sem lanterna na mão".
  - O marco, a recompensa e o combate não mudaram.
- **Ferramentas técnicas fora do jogo normal:**
  - **Jogo normal:** a pausa só oferece a arena e a sensação A/B/C no modo técnico (`-Ptecnico`). A interface recusa
    essas ações, e as gravadas antes nas preferências não valem.
  - **Roteiros e testes:** mantêm o acesso direto. O `combate92/capturas.txt` roda em modo técnico nos pacotes, e as
    comparações históricas não mudam.
  - Teste: `AdventureTest.jogoNormalNaoTrocaArenaNemSensacaoPelaPausa`.
- **Escuta:** o proprietário não deu um resultado específico sobre os quatro sons da missão. P49 continua documentado
  como escuta individual opcional. O agente não os ouviu.

