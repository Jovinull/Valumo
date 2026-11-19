# 0027 — Porta de entrada: menu com a vila como cenário, sessões trocáveis e transições em passos (rodada 11.1)

**Status:** aceita (rodada 11.1). A aprovação artística do menu é do proprietário.

## Contexto

Até a rodada 11 o jogo abria direto na escolha de aparência da vila, e o mundo gerado só existia por linha de comando
(`-Dvx.mundo=procedural -Dvx.semente=N`). O pedido da 11.1 é uma porta de entrada de RPG: menu principal com o mundo 3D
ao fundo, seleção de personagem, seleção e criação de mundos (com a semente digitada na interface) e a aventura
escolhida, tudo na mesma aplicação, com mouse e teclado, sem perder o que já foi aprovado.

## Decisão

- **O fundo do menu é a vila fixa da rodada 10, numa sessão própria em estado `MENU`.**
  - Usa o mesmo `World`, a mesma colisão e os mesmos atores.
  - Não roda nada de jogo: sem IA, sem combate, sem missão, sem save (`saveDir = null`).
  - O marco aparece aceso, os inimigos não aparecem e não existem protótipos.
  - A câmera é uma apresentação escrita à mão (`assets/scenes/apresentacao_e.json`): tomadas com alvo, rumo, inclinação
    e distância, suavizadas nas pontas, com escurecimento curto na troca.
  - O `ApresentacaoTest` confere cada olho de câmera contra a colisão real.
- **A sessão (mundo, relevo, lógica, som da sessão, atores) troca em tempo de execução.**
  - `Game.openSession`/`closeSession` fecham malhas, a grade do relevo procedural e o som.
  - A vila fica montada entre o menu e uma aventura na vila; a mesma cena serve aos dois.
  - Um mundo gerado é montado e fechado a cada entrada.
- **Transições em passos com rótulo real** (`Step`).
  - A tela escurece e cada passo roda num quadro, depois de um quadro desenhado com o rótulo: "Gerando a região da
    semente N", "Montando o mundo", "Carregando a aventura de X".
  - Sem porcentagem inventada e sem thread nova: os recursos de GL ficam na thread do contexto, como sempre.
  - Falha antes de fechar o menu: a mensagem aparece e o menu fica.
  - Falha depois: o menu volta, com a mensagem.
- **Regras sem OpenGL** (`vx.game.menu.MenuFlow`).
  - Telas, seleções, campos de texto, teclas, Esc e pedidos.
  - O `Game` cumpre os pedidos que precisam de GL ou do laço da janela (criador, prévia, gerar, entrar) e devolve
    `done`/`failed`.
  - O desenho fica na `MenuView`, que é dona dos recursos de GL do menu e fecha tudo.
- **O criador aprovado abre do menu na mesma janela.**
  - Concluir grava o perfil.
  - O novo "Cancelar" (botão e Esc, só quando aberto pelo menu) fecha sem gravar.
  - O criador de sempre não muda (gate 51/0).
- **Roteiros antigos sem menu.** `GameMain` põe `-Dvx.menu=nao` nos roteiros: as comparações históricas das rodadas
  08–11 continuam iguais. Os roteiros do menu pedem `-Dvx.menu=sim`.
- **Leiaute A (lateral) oficial.** O B (central) fica só para comparação (`-Dvx.menu.layout=B`), como as outras
  comparações do projeto.

## Alternativas descartadas

- **Gerar uma região só para o fundo.**
  - Mostraria um mundo diferente a cada abertura, com custo de geração e malha.
  - A vila é o cenário aprovado e já está na memória quando o jogador escolhe a vila.
- **Thread de carregamento.**
  - A geração pura leva ~0,1 s; o que custa (~1,7 s) é montar malhas, que só pode acontecer na thread do GL.
  - Concorrência sem ganho real.
- **Vídeo pré-gravado no fundo.** Não é o motor; não acompanha mudanças do mundo; é asset novo.
- **Dois menus oficiais.** O pedido proíbe; o B só existe atrás da propriedade de comparação.

## Adendo — fechamento das rodadas 11 e 11.1 (decisões definitivas do proprietário)

- **Menu e cena:** menu lateral (A), logotipo "VALUMO" na fonte própria, cena 3D cinematográfica da vila.
- **Texto na tela:** nenhuma instrução de tecla nem texto de desenvolvimento.
- **"Menu principal" na pausa volta à TELA INICIAL** (Jogar, Configurações, Sair), não à de mundos. As escolhas ficam
  guardadas para o próximo Jogar.
- **X da janela:** interceptado (`glfwSetWindowCloseCallback` → `Window.takeCloseRequest`). Pergunta só quando há
  progresso fora do disco (`GameLogic.unsaved`: missões ou posição, não o tempo). Usa a MESMA confirmação da pausa, de
  qualquer estado; cancelar volta ao estado de antes. No criador com alterações, o diálogo dele. Encerramento forçado do
  processo não é interceptável e não é prometido.
- **Criador com cache:** o `App` do criador vive enquanto o jogo vive; a primeira montagem da cena vira um passo de
  carregamento.
- **Botões equivalentes, mesma escala da fonte** (`Game.groupPx`). A tela de carregamento ganhou o logotipo pequeno e as
  sementes P1, sem porcentagem.
- **Câmera da prévia:** a atual, comparada com uma alternativa baixa nas quatro sementes de referência e mantida.
