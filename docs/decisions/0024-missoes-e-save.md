# 0024 — Missões como dados + camada lógica, identidade estável de inimigo, jogo salvo v1 atômico (rodada 10)

**Status:** aceita (rodada 10). A missão "As Luzes do Posto", o marco e os sons são conteúdo novo: a aprovação artística
é do proprietário.

## Contexto

A rodada 10 transforma os sistemas existentes (vila, NPCs, combate, som, interface) na primeira aventura completa:
aceitar, cumprir objetivos, voltar, receber recompensa, salvar e continuar. Pedidos que decidem a forma:
- "uma camada pequena e reutilizável", sem depender da interface, testável;
- inimigos de missão por identidade estável (sem contar duas vezes, sem contar antes de aceitar, sem a arena de teste
  completar a missão);
- savegame versionado, com escrita atômica e leitura que sobrevive a qualquer arquivo ruim, sem migrações falsas;
- lógica determinística a 30 e a 144 quadros/s.

## Decisão

- **Missões são dados** escritos à mão (`assets/scenes/missoes_e.json`, `valumo.missoes` v1) e o mundo se refere a elas
  por id estável (`quest.trilha.luzes_do_posto`).
  - Objetivos em ordem, de três tipos: derrotar, interagir e falar. O último é sempre falar com quem deu a missão.
  - O pacote `vx.game.quest` não depende de OpenGL, de som nem da interface.
- **O estado é derivado, nunca guardado à parte:** sai de aceita, concluída, índice do objetivo e alvos contados. Assim
  combinações contraditórias não existem na memória, e o save que as tiver é recusado.
- **Identidade estável de spawn** em `encontros` (5º item, ou `<encontro>.<índice>`).
  - O combate publica cada morte uma vez, no passo lógico (`Combat.defeated`).
  - O que contou fica "aposentado" (`Combat.retired` → `Enemy.retire`): não volta ao recomeçar nem ao carregar, e não
    entra na conta da vitória.
  - A arena de teste (`um`, `grupo`, `chefe`) continua existindo para comparação e nunca conta.
- **Encontro da missão:** aceitar troca a arena para o encontro da missão. Antes disso a arena abre no encontro padrão,
  como nas rodadas 09.x, e roteiros e capturas antigos não mudam.
- **Falas por contexto** em `assets/scenes/dialogos_e.json`. Quem dá a missão tem 10 contextos obrigatórios, e a regra
  de escolha é uma função pura (`DialogueBook.giverContext`). A entrega acontece ao ABRIR a conversa de entrega: sair no
  meio da fala não perde a recompensa nem permite recebê-la de novo.
- **Jogo salvo v1:**
  - **Formato:** um arquivo (`output/saves/aventura.json`, `-Dvx.savegame`) com a aparência embutida no contrato do
    compositor.
  - **Escrita:** temporário na mesma pasta → `force` → fechar → reler e validar → movimento atômico.
  - **Leitura:** recusa com o motivo e guarda `.invalido`. Aparência incompatível não derruba o progresso. Posição
    inválida volta ao ponto seguro.
  - **Versões:** só a 1 existe, e outra versão é recusada (nada de "migrar" no escuro).
- **Salvamento automático** só nos marcos da missão, no primeiro passo lógico seguro (fora da pausa de impacto, da
  luta, no chão).

## Alternativas descartadas

- **Missão como código** (uma classe por missão): mais rápido para uma missão, mas mistura conteúdo e regra e não
  permite a próxima sem programar.
- **Contar inimigos pela ordem da lista (`Enemy.id`):** a ordem muda quando o encontro muda; a identidade estável não.
- **Respawnar o fungo contado ao recomeçar** e confiar no contador: deixa um inimigo que não serve para nada e deixa
  dúvida se ele conta. Aposentar é explícito.
- **Vários slots de save e migração v0 → v1:** fora do escopo; não existe v0. A cópia `aventura.anterior.json` cobre o
  "jogo novo por cima".
- **Salvar a cada N segundos:** escreveria no disco no meio da luta, e "a cada quadro" é o que o pedido proíbe.

## Consequências

- Uma missão nova é só dados (missão, falas, encontro, objeto), mais um teste que percorra os estados. Guia:
  `docs/processo/como-adicionar-uma-missao.md`.
- Inimigos de encontros antigos ganharam identidade (`um.0`, `grupo.1`, …). O comportamento deles não mudou.
- A pausa ganhou "Salvar jogo" e "Diário", com os botões de aparência e criador lado a lado. A dica da exploração ganhou
  "J diário". Os roteiros das 08/09.x que mostram a pausa ou a dica mudam só nisso.
