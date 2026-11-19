# Menus: porta de entrada, navegação e ciclo de vida das telas (rodada 11.1)

O jogo abre no **menu principal**. O caminho até a aventura é:

> menu principal → **personagens** → **mundos** (→ criar mundo) → **aventura** = personagem × mundo

Decisões: [ADR 0027](../decisions/0027-porta-de-entrada-e-sessoes.md) (menu, sessões, transições) e
[ADR 0028](../decisions/0028-personagens-mundos-e-aventuras.md) (personagens, mundos, saves). Dados:
[perfis de personagem](perfis-de-personagem.md), [catálogo de mundos](catalogo-de-mundos.md).

## Peças

| Peça | Onde | O que faz | GL? |
|---|---|---|---|
| `MenuFlow` | `vx.game.menu` | telas, listas, seleções, campos de texto, teclas, Esc, **pedidos** | não (testável: `MenuFlowTest`) |
| `CharacterStore` / `WorldStore` | `vx.game.menu` | perfis, mundos, adoção dos saves de antes, pastas das aventuras | não (`MenuStoresTest`) |
| `Cinematic` | `vx.game.menu` | tomadas do fundo, pose no tempo, vitrine dos personagens | não (`ApresentacaoTest`) |
| `MenuView` | `vx.game` | desenho das telas, retratos, prévias (FBO/texturas); fecha tudo | sim |
| `Game` | `vx.game` | cumpre os pedidos, transições em passos, sessão (mundo/lógica/som), câmera do menu | sim |
| `GameLogic` | `vx.game` | estado `MENU` (nada de jogo roda), `beginAdventure`, confirmação de saída (`leaving`) | não (`MenuAdventureTest`) |

## Telas e navegação

| Tela | Chega por | Esc | Enter | Outras teclas | Mouse |
|---|---|---|---|---|---|
| `MAIN` | abrir o jogo | — (não sai) | botão focado | ↑ ↓ foco | passar o mouse foca; clique |
| `CHARACTERS` | Jogar | → `MAIN` | Escolher | ↑ ↓ (e roda) escolhem, N novo | clique no cartão; **arrastar** fora dos botões gira o herói |
| `WORLDS` | Escolher personagem | → `CHARACTERS` (personagem preservado) | Entrar (Continuar/Iniciar) | ↑ ↓ (e roda), N criar mundo | clique no cartão |
| `CREATE_WORLD` (modal) | Criar mundo | cancela → `WORLDS` | Criar | Tab troca o campo, Backspace, digitação | clique no campo dá foco |
| `SETTINGS` | Configurações | → tela anterior | — | — | − / + e alternâncias |
| (criador) | Novo personagem / Editar | **Cancelar** (não grava) | — | as do criador | as do criador |

- **Seleção preservada:** `chosenProfile`/`chosenWorld` são ids. Recarregar a lista ou voltar com Esc mantém a
  escolha.
- **Digitando** (`MenuFlow.typing()`): as teclas de jogo não valem; nem o M de mudo, que vira letra.
- **Modal:** o formulário de criar mundo limpa os botões de baixo antes de desenhar os dele; só os controles dele recebem
  clique. O mesmo vale para a confirmação de saída na pausa.

## Pedidos (o que só o `Game` cumpre)

`MenuFlow.action(id)` muda de tela ou deixa um `Request` em `pending`. Depois da lógica do quadro, `Game.menuTick`
consome o pedido (`take()`), cumpre e responde com `done(req, idNovo)` ou `failed(req, mensagem)`.

| Pedido | Cumprimento | Falha |
|---|---|---|
| `QUIT` | fecha o jogo | — |
| `NEW_CHARACTER` / `EDIT_CHARACTER` | passo "Abrindo o criador" (`prepareCreator`: o `App` do criador é criado uma vez e fica vivo; `Creator.warm` monta a cena dele, ~3 s na primeira vez, ~50 ms depois) → o criador aprovado na mesma janela; concluir = `CharacterStore.create`/`update` | gravar falhou: mensagem |
| `PREVIEW` | passos: gerar a região → montar e desenhar a vista geral num FBO (não grava) | semente recusada: o erro fica no formulário |
| `CREATE_WORLD` | passos: gerar e conferir (antes de qualquer arquivo) → gravar `mundo.json` → desenhar e gravar `previa.png` | nada criado; nome e semente ficam |
| `ENTER` | passos: gerar (mundo gerado) → montar o mundo → abrir a sessão na pasta do par, continuar ou começar | antes de fechar o menu: mensagem; depois: volta ao menu com a mensagem |

## Transições (ciclo de vida da sessão)

Uma transição é uma lista de `Step(rótulo, trabalho)`:

1. A cobertura escurece (0,2 s).
2. Cada passo roda num quadro **depois** de um quadro desenhado com o rótulo dele.
3. A cobertura clareia (0,35 s).

O rótulo é o que vai acontecer; nunca porcentagem. Entre o fechamento de uma sessão e a abertura da
próxima, `render` desenha só a interface (`world == null`).

```text
abrir o jogo  → openSession(vila, cena da vila, saveDir = null) → enterMenu(MAIN)
              → [saves de antes ainda não adotados: "Preparando seus jogos salvos" (adoptOldSaves, uma vez)]
Entrar        → [gerar] → closeSession(vila fica montada se o destino é a vila) + menu.close()
              → openSession(mundo, cena, saveDir = pasta do par) → beginAdventure(aparência do perfil, continuar?)
Menu principal (pausa) → closeSession → openSession(vila, …, null) → enterMenu(MAIN)   (escolhas guardadas: Jogar volta a elas)
```

- **`closeSession`** fecha as malhas do mundo, a grade do relevo procedural (`Terrain.release`), atores, mortes e o som
  da sessão.
- **A `MenuView`** (retratos, prévias, renderers próprios) fecha ao entrar na aventura e é refeita na volta.
- **Combate, diálogo e missão** pertencem à lógica da sessão: nada fica ativo por trás do menu.
- **Medido:** 24 transições sem crescer malhas, texturas, FBOs, grades de relevo, heap pós-GC nem fluxos do OpenAL
  (`tools/review111.sh transicoes`).

## Sair da aventura

Na aventura aberta pelo menu, a pausa troca "Aparência/Criador" por **Menu principal**. Esse botão volta à **tela
inicial** (Jogar, Configurações, Sair), não à de mundos. O personagem e o mundo escolhidos continuam guardados: Jogar
reabre a lista com eles selecionados.

**Só pergunta quando há o que perder** (`GameLogic.unsaved()`): o progresso das missões ou a posição do herói (0,25 u)
mudou desde o último salvamento, o Continuar ou o começo. Ficar parado não conta. Sem nada a perder, Menu principal e
Sair agem direto. Com algo a perder, os dois abrem a confirmação (`GameLogic.leaving`):

- **Salvar e voltar** (ou **Salvar e sair**): só quando `saveBlocker()` deixa; senão o botão aparece apagado e diz por
  quê.
- **Voltar sem salvar** (ou **Sair sem salvar**).
- **Cancelar** (também com Esc).

Nada se perde em silêncio. "Sair sem salvar" perde só o que ainda não está no disco: o que já foi salvo, inclusive nos
marcos da missão, fica. Salvar falhou: a mensagem aparece e a confirmação continua aberta.

## O X da janela

O `Window` intercepta o pedido de fechar (`glfwSetWindowCloseCallback`). O callback roda antes de qualquer destruição e
desfaz o `shouldClose`; `GameMain` liga com `interceptClose(true)`. O pedido é atendido no `Game.tick`:

| Onde | O X faz |
|---|---|
| menu principal, personagens, mundos, criar mundo, configurações, carregamento, depois de uma falha de geração | fecha (nada em risco: personagens e mundos já estão no disco) |
| aventura sem nada fora do disco | fecha |
| aventura com progresso fora do disco: explorando, em luta, em diálogo, no diário, na pausa | a MESMA confirmação "Sair do jogo?" da pausa. Cancelar volta ao estado de antes (o diálogo na mesma fala) |
| criador sem alterações | fecha |
| criador com alterações não confirmadas | "Sair do jogo?" do criador: Salvar e sair (grava o perfil) / Sair sem salvar / Cancelar |

- **Ao sair:** o laço termina e `Game.close` libera mundo, malhas, criador e áudio; `Audio.stop` fecha o OpenAL e o
  `Window` fecha o GLFW.
- **Não interceptável:** encerramento forçado do processo (SIGKILL, queda de energia). Valem os saves já gravados; a
  escrita atômica não deixa arquivo pela metade.
- **Roteiros:** `fechar-janela` chama o MESMO caminho do callback (`Window.simulateCloseRequest`). O X de verdade, pelo
  gerenciador de janelas, fica no roteiro de teste físico (Wayland sem automação).

## Tipografia dos botões

Botões equivalentes usam a MESMA escala da fonte de pixel (`Game.groupPx`/`groupPxFrom`): a maior que serve a todos os
rótulos do grupo, cada um na largura do seu botão. Antes, cada botão escolhia a sua e vizinhos saíam com tamanhos
diferentes.

- **Tamanho base:** menu principal 30; telas de menu 27 (`MenuView.MENU_BUTTON`: escala 3 em 1080p, 2 em 720p); pausa 22.
- **Personagens:** se "Novo personagem / Editar aparência" lado a lado forçariam o grupo a encolher, cada um ganha uma
  linha inteira.

## Tela de carregamento

Fundo escuro quente em faixas (sem textura nem cena 3D), o logotipo pequeno, o passo REAL e as três sementes P1 em
sequência. Nenhuma porcentagem. O congelamento que existe (montar malhas na thread do GL) aparece como o passo nomeado,
nunca escondido.

## Fundo 3D

O fundo é a vila fixa em estado `MENU` (ADR 0027).

- **Câmera:** a pose do `Cinematic`. Na tela de personagens, a câmera da vitrine.
- **Herói:** desenhado só na tela de personagens, na vitrine (`apresentacao_e.json → vitrine`), com o giro do mouse
  aplicado só ao desenho.
- **Moradores:** na vitrine, os que ficam colados ou atrás do herói (vistos do olho) não são desenhados.
- **Inimigos:** não aparecem no fundo.
- **Marco:** aceso.
- **Música:** `vila_b` no menu (`-Dvx.musica.menu=ID|nenhuma`) e `vila_a` na aventura, no mesmo dispositivo (ADR 0023).

Tomadas novas: [como adicionar uma tomada](../processo/como-adicionar-uma-tomada.md).

## Como acrescentar…

- **Um botão numa tela existente:**
  1. Desenhe com `g.button(id, …)` na tela da `MenuView`.
  2. Trate o `id` em `MenuFlow.action`, ou em `Game.menuClick` se for um ajuste (`cfg:*`).
  3. Ponha o caso no `MenuFlowTest`.
- **Uma tela:**
  1. Acrescente um valor a `MenuFlow.Screen`.
  2. Defina a chegada em `action` e a volta em `back()` (Esc).
  3. Escreva `drawX` na `MenuView` e o `case` em `draw`.
  4. Teste a navegação no `MenuFlowTest` e capture num roteiro de `tools/menu/`.
- **Uma etapa que precisa de GL ou do mundo:**
  1. Acrescente um `RequestKind`.
  2. Faça o caso em `Game.request` com `transition(new Step(rótulo real, trabalho), …)`.
  3. Responda com `done`/`failed`.
  4. Falha: nada parcial no disco (confira antes, grave por último, com escrita atômica).
- **Comandos de roteiro do menu:** `digitar TEXTO`, `arrastar DX S`, `esperar-menu [S]`, `menu-estado`,
  `menu-mundo NOME|id`, `menu-personagem NOME`, `menu-tomada T`, `menu-leiaute A|B`, `menu-recursos ROTULO`, `vitrine` (lista os
  moradores e o que a vitrine esconde), `fechar-janela` (o X), `shot-agora NOME` (captura sem esperar: o carregamento),
  `previa-comparar SEMENTE` (vista atual × alternativa da prévia). O `click` desenha um quadro antes de
  procurar o botão (botões da tela atual).

## Texto na tela

O que **não** existe em tela nenhuma, por decisão do proprietário (11.1):
- dica de teclas: nenhuma linha "↑ ↓ escolher · Enter…", nenhum "(Esc)", "(Enter)", "(N)" ou "(E)" em botão ou placa;
- texto de desenvolvimento: "rodada", "legado", "gerador vN", nome de arquivo, versão;
- explicação técnica da semente.

As teclas continuam funcionando; só não são escritas. Espera de retrato ou prévia = três sementes acendendo, sem texto.
