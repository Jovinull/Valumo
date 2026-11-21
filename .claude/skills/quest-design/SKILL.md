---
name: quest-design
description: Criar ou alterar missões do Valumo (rodada 10 em diante) — missão como dados (assets/scenes/missoes_e.json, ids estáveis quest.*), objetivos em ordem (derrotar por identidade estável de spawn, interagir com objeto do mundo, falar), falas por contexto (dialogos_e.json, 10 contextos de quem dá a missão), recompensa única, encontro da missão, objeto com estado (marco), matriz de travamentos e trapaças (§16), save e determinismo. Use ao criar uma missão nova, mexer numa existente, num objeto interativo ou nas falas por contexto.
---

# Missões (rodada 10 em diante)

Sistema: `docs/architecture/missoes.md`. Primeira missão: `docs/architecture/missao-luzes-do-posto.md`. Passo a passo:
`docs/processo/como-adicionar-uma-missao.md`. Save: `docs/architecture/save.md`. Decisão: ADR 0024.

## Regras

- **Conteúdo é dado; regra é código.**
  - Missão, falas, encontro e objeto são JSON escritos à mão.
  - Modelos de objeto saem de `tools/env_e.py`, nunca de `.vox` à mão.
  - Nada de classe por missão.
- **Identidade estável em tudo que conta:**
  - missão: `quest.<lugar>.<nome>`;
  - inimigo: 5º item do spawn, por exemplo `trilha.fungo.1`;
  - objeto: `obj.<lugar>.<nome>`;
  - NPC: a pasta do rig.
  - Nunca reaproveite identidade de outra missão nem dos encontros de teste.
- **O estado é derivado** (`QuestProgress.state()`). Não guarde "estado" à parte nem crie campo que possa contradizer os
  outros.
- **Evento de missão só no passo lógico** (nunca no desenho nem por quadro). A mesma identidade conta uma vez, e o que
  contou fica aposentado (`Combat.retired`).
- **A recompensa sai ao abrir a conversa de entrega**, uma vez. Missão concluída nunca reabre.
- **Falas que correspondem ao mundo:** nenhum NPC afirma ver, ouvir ou saber o que a posição dele não permite (a Kelyra
  da estalagem não vê o marco: deduz pelo retorno do herói). Confira com uma captura do ponto do NPC antes de escrever
  "daqui eu vejo".
- **Falas:** quem dá a missão tem os 10 contextos (`DialogueBook.GIVER_CONTEXTS`). Expressões só `joy`, `determined`,
  `surprise` ou nenhuma. Texto original, em português, curto (cabe em 2 linhas do painel a 720p).
- **Objeto:**
  - alcance ≥ 1,8 u;
  - corpo sólido (`raio`);
  - nada se usa no meio da luta;
  - antes/depois com a MESMA forma (só a luz/estado muda);
  - P1 com moderação (≤ 3 voxels, 1 por objeto, efeito ≤ 5 sementes na tela).
- **Som:** reaproveite `missao.*`. Som novo pela skill `sound-design`, com fonte editável. Medir não é ouvir: diga
  quando não ouviu.
- **QA com save temporário** (`-Dvx.savegame=PASTA`), nunca o do proprietário. Som só por loopback.

## Matriz obrigatória (teste em `AdventureTest` ou num teste da missão)

| Momento | Casos |
|---|---|
| antes | derrotar alvos antes de falar; usar o objeto antes de aceitar; recusar; aceitar depois de recusar |
| durante | derrotar um; cair; recomeçar o confronto; salvar; sair; carregar; derrotar o resto; objeto cedo; ativar; sair antes de voltar |
| depois | entregar; falar de novo; recomeçar; cair (numa arena de teste); voltar à trilha; reativar o objeto; pedir a recompensa de novo |

Nenhum caso pode:
- duplicar a recompensa;
- contar uma morte duas vezes;
- reabrir a missão;
- apagar progresso válido;
- bloquear um objetivo para sempre;
- deixar inimigo inalcançável;
- produzir estado contraditório.

Mais: o mesmo resultado a 30 e a 144 quadros/s (entradas no começo de quadros de 1/30 s).

## Evidência

- Roteiro em `tools/aventura/` com entrada real (`lutar`, `ir`, `key E`).
- Capturas do objeto antes × depois do mesmo ponto.
- Vídeo com o som da aplicação (`tools/review10.sh videos`).
- Desempenho com os mesmos roteiros da rodada anterior.

## Defeitos já vistos (não repita)

| Defeito | Causa | Correção |
|---|---|---|
| E no marco não pegava no roteiro | alcance 1,5 u e o `ir` para a ~0,4 u do ponto | alcance 1,8 u (o dos NPCs) |
| "Trilha limpa" virava "vitória — R recomeça" | a conta da vitória incluía os fungos da missão | quem contou fica fora da conta (`retired`) |
| Captura do marco sem o marco | câmera atrás do herói, e o herói de costas para o marco | herói girado 30–40°, mesmo ponto nos dois |
| Mensagem de save inválido com o texto bruto do Gson e um link, passando da tela | motivo sem tratamento | `SaveGame.brief` + `fit` na largura |
| Kelyra dizia "daqui eu vejo quando brilhar" e o marco não aparece da estalagem | fala escrita sem conferir a vista do NPC | falas por dedução (retorno do herói); regra acima |
| A pausa deixava o jogador trocar a arena e sumir com os fungos da missão | ferramenta de comparação exposta no jogo normal | só no modo técnico (`technical`) |
| Clique de roteiro "botão não está na tela" logo depois de abrir a pausa | os botões são os do último quadro desenhado | `shot` (ou um quadro desenhado) antes do `click` |

## Rodada 12.2 — referência desconhecida no save

`QuestSystem.UnknownReference` (missão, objeto, descoberta ou inimigo que a cena não tem) separa "a CENA é outra" de "o
dado está quebrado". Numa região gerada vira `SaveGame.Incompatible` (sem `.invalido`); na vila fixa continua inválido.
Missão nova nunca reaproveita o id de outra.

## Rodada 13 — recompensas de item fora da missão

- **O presente de um morador** (`recompensas_e.json → presentes`) sai ao terminar a conversa e NÃO usa as falas nem os
  contextos da missão: as falas dos moradores da v2 estão congeladas.
- **Não é missão:** nada de missão inteira para dar duas ervas.
- **Moedas da missão:** continuam as publicadas (Kelyra 30); itens não substituem nem duplicam a recompensa.
- **Fonte de item ligada a missão** (drop de inimigo da trilha): use a identidade estável do spawn e a regra de
  `Combat.retired`. Nunca entregue de novo ao recomeçar.
