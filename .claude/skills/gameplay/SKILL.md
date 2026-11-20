---
name: gameplay
description: Desenvolver a experiência jogável do Valumo (módulo game) — controle do herói por velocidade com subpassos fixos, colisão simples por perfis de prop (colisao_e.json) separada da malha, relevo T1 compartilhado com o desenho (Terrain), câmera de terceira pessoa com linha de visão e desvio, NPCs interativos e diálogo, estados do jogo, aparência do compositor no mundo, testes de propriedades de gameplay e roteiros de entrada (tools/jogo, tools/review8.sh). Use ao mexer em movimento, colisão, câmera de exploração, cena jogável, interação ou ao validar jogabilidade. Rodada 09.2: perfis de sensação (velocidade/passada/aceleração), salto com coyote e buffer, props pisáveis (colisao_e.json "pisavel"), referência do WASD com giro limitado e preferências do jogador (Prefs). Rodada 10: missões, objetos do mundo com estado (E), falas por contexto, jogo salvo v1 (atômico), Continuar, diário e rastreador (tools/aventura, tools/review10.sh). Rodada 11.1: o jogo abre no menu (estado MENU, sessão trocável, transições em passos), aventura = personagem × mundo (beginAdventure, pasta do par), confirmação ao sair (tools/menu, tools/review111.sh).
---

# Gameplay (rodada 08 em diante)

Arquitetura, parâmetros e decisões: `docs/architecture/jogo.md` e `docs/decisions/0013-modulo-game.md`.
Teste físico do proprietário: `docs/validation/roteiro-de-teste-fisico.md`.

## Regras

- **Regras de jogo ficam em `game/` e sem OpenGL** quando possível: `GameLogic`, `Player`, `Collision`,
  `FollowCamera`, `SceneDef`, `Input`. No `viewer` entra só gancho genérico (nada de regra de jogo); no `engine`,
  só infraestrutura.
- **Nunca duplique** renderer, mundo, assets, animação ou compositor: use os do viewer.
- **Colisão ≠ malha:**
  - forma por perfil em `assets/scenes/colisao_e.json`;
  - IK dos pés é visual, colisão é do jogo, e os dois pisam no mesmo `Terrain`.
- **Velocidade, não posição:**
  - movimento em subpassos fixos (≤ 1/120 s), sem depender do FPS;
  - a animação sai da velocidade real, com a fase pela distância (pé plantado). Nada de clipe de andar com o
    herói parado.
- **Câmera:**
  - encosta na hora quando algo bloqueia e volta devagar;
  - desvia junto a paredes;
  - nunca muda o rumo escolhido pelo jogador;
  - **meça o que o jogador vê, não só a geometria** (rodada 09.1): `CameraFraming` projeta herói e alvo e diz quanto da
    tela o herói ocupa e quanto do alvo aparece. Uma câmera pode estar fora da parede e ainda assim inútil. A câmera
    de luta e a sua validação estão na skill `combat`.
- **Linha de visão (`Collision.rayClear`):** testa só as formas perto do raio (filtro exato, com teste de 4.000 raios).
  Se mexer em `Shape.contains`, mantenha o raio envolvente (`bound`) coerente, ou o filtro passa a errar.
- **Cenas jogáveis são cópias controladas** (`jogo_*.json`, com a seção `jogo`); a clareira canônica e a vila não
  mudam para acomodar gameplay.
- **Fora do escopo** até ser pedido: inventário, missões, progressão. Combate, dano e IA hostil são da skill `combat`.
  A interrupção por HIT da espada foi resolvida na rodada 09; só a da lâmina longa (não jogável) continua aberta.

## Procedimentos

**Prop novo na cena jogável**
1. Ponha o prop no `jogo_*.json`.
2. Dê a ele um perfil em `colisao_e.json`:
   - `caixa` (escala da pegada) para construções;
   - `circulo` (raio do pé) para pedras e postes;
   - `tronco` (tronco + copa só para a câmera) para árvores;
   - `nenhum` para plantas baixas;
   - `camera: false` para coisas finas que a câmera pode atravessar.
3. Rode `GameplayTest` (passeio sem entrar em prop e sem ficar preso).
4. Grave o vídeo de obstáculos.

**NPC interativa:** entrada em `jogo.interativos` (rig, nome, título, x, z, r, alcance, falas com `expressao`
opcional entre as do personagem). Ela vira corpo de colisão sozinha. Falas originais, curtas; sem árvore de
diálogo até ser pedido.

**Mexer em movimento ou câmera:**
1. Escreva primeiro o teste da propriedade contra terreno sintético (`Collision` com chão lambda) e contra a vila
   real (`GameplayTest.village()`).
2. Meça com `estado` nos roteiros antes de mudar constantes.
3. Nunca afrouxe um teste para passar. Se o teste estava errado, corrija o teste e diga o porquê. Exemplos:
   tempo simulado diferente entre FPS; rota empurrando contra uma parede.

**Roteiros** (`tools/jogo/*.txt`, executados por `game --roteiro ROTEIRO SAIDA [FPS]`):

| Comando | Para que serve |
|---|---|
| `ir x z [correndo]` | segura W e corrige a mira com o mouse até chegar |
| `mirar x z s` | aponta a câmera para um ponto |
| `hold W+SHIFT s` | segura teclas pelo tempo dado |
| `look dx dy s` | move o mouse |
| `wheel n` | gira a roda |
| `key E` | aperta uma tecla |
| `click id` | clica num botão da interface |
| `criador ROTEIRO` | abre o criador com um sub-roteiro dele |
| `estado` | imprime posição, câmera e o que bloqueia a vista |
| `shot nome` | captura (espera a composição) |
| `record on\|off` | grava quadros para vídeo |
| `perf on\|off` | mede o tempo por quadro |

- Rode primeiro a seco (sem `record`) com `estado`.
- O alvo de `ir` não pode estar dentro de um prop.
- Sem gravar, os quadros passam quase sem tempo real: espere a composição com `settle` (o `shot` já espera).

**Revisão:** `tools/review8.sh tudo` gera capturas, 6 vídeos com pranchas, medição do jogo e do viewer na mesma
cena, testes e zip. Olhe os quadros em tamanho real: pés no chão em degraus, herói na água, câmera dentro de
parede ou da cabeça, indicação de interação sobre a NPC certa.

## Rodada 09.4 — som no jogo

- Fatos do jogo → eventos do catálogo em `GameAudio` (pisada pela fase de contato do pé + superfície; `GameLogic.onAction`
  para a interface; música, vento, lago, ouvinte). Nunca caminho de arquivo nem `Audio` dentro das regras.
- Pisada: `Player.steps` muda quando `floor(2·animT·cadência)` muda, com o herói no chão e acima de 0,12 u/s
  (`GameAudioTest`: intervalo 1/(2·cadência), mesma contagem a 30 e 144 FPS, parado não pisa).
- Superfície nova no relevo → caso novo em `GameAudio.stepEvent` + evento no catálogo + teste.
- Volume do jogador: `AudioPrefs` (pausa), `output/preferencias/audio.json`; roteiros com `-Dvx.prefs=-` (padrão: nem lê nem grava) ou `-Dvx.prefs=` + `-Dvx.prefs.audio=` temporários.
## Defeitos já vistos (não repita)

| Defeito | Causa | Correção |
|---|---|---|
| Câmera dentro da parede por alguns quadros | entrada suavizada | encostar na hora; suavizar só a saída |
| Câmera dentro da cabeça junto à estalagem | só encurtava a distância | desvio de rumo/inclinação (até 80°) antes de encostar; mínimo 0,9 u |
| Herói "dentro" do lago | margem a −0,375 contava como chão (água a −0,42) | pisável só com chão ≥ água + 0,06 |
| Câmera nunca voltava à distância pedida | folga descontada mesmo com vista livre | folga só quando bloqueia |
| Escolha mostrava as costas do herói | primeiro quadro não nascia no enquadramento | `first` usa o enquadramento pedido |
| Poste de lanterna tapando a escolha / a rota | prop na linha de visão do enquadramento fixo | conferir capturas da escolha e das rotas ao mexer na cena |
| (09.1) Cabeça tomando a tela junto ao carvalho na luta | a busca de desvio avaliava a vista SEM os ajustes somados depois (giro e inclinação de combate) | avaliar cada candidato como a vista FINAL vai ficar |
| (09.1) Comparação antes/depois enganosa nas capturas | `posicionar` não zera a distância suavizada: a captura herdava a câmera encostada da anterior | `camera-recompor` na montagem, ou esperar assentar; e comparar no MESMO instante lógico nos dois binários |

## Rodada 09 — combate e passo fixo

- **Passo fixo:** a exploração anda em passos fixos de `GameLogic.TICK` (1/120 s).
  - **Câmera:** anda no mesmo passo, porque o rumo dela entra na intenção.
  - **Desenho:** interpola (`Player.rx/rz/rVisualY/rYaw`, `FollowCamera.apply(cam, alpha)`).
  - **Regra:** nunca ponha regra no desenho nem use `dt` de quadro na lógica.
- **Ajuste de combate da câmera** (`combatFocus`): não é trava de mira e some fora da luta. Detalhes e números em
  `docs/architecture/combate.md` §9.
- **Combate:** ataque, dano, defesa e inimigo seguem a skill `combat`.

## Rodada 09.2 — velocidade, salto e preferências (`jogo.md` §12)

- **Velocidade:** é do perfil de sensação (`Player.applyFeel`). Mais rápido = mais cadência; a passada fica perto de 1:
  as pernas do herói têm 0,24 u.
  - **Pé plantado:** a fase anda por `distância / (velocidade de desenho × passada)`.
  - **Teste:** `CombatAudioTest.footstepsLand...` calcula o ritmo esperado pelo perfil.
- **Salto (ADR 0019):** `Player.requestJump` (buffer) + coyote no passo fixo.
  - **Gravidade:** diferente na subida e na queda.
  - **Chão:** `Collision.floor` (relevo ou topo de forma `pisavel`). Para um prop novo virar plataforma:
    `"pisavel": true` no perfil em `colisao_e.json`, nunca no código.
  - **Validar:** `MobilityTest` (altura, tempo no ar, coyote/buffer, pisáveis × casa) e o vídeo 11 do pacote 09.2.
- **WASD:** é relativo a `FollowCamera.inputYaw`, não ao rumo exato da câmera. Desvios automáticos entram a no máximo
  2,5 rad/s.
- **Preferências:** `Prefs` grava em `output/preferencias.json`, separado da aparência.
  - Roteiros e testes usam `-Dvx.prefs=-`: nem leem nem gravam.
  - Use `-Dvx.sensacao`, `-Dvx.encontro` e `-Dvx.barras` para forçar. `./gradlew jogo -Dvx.X=...` repassa.

## Rodada 09.3 — salto apresentado, herói translúcido perto

- **Física congelada:** altura 0,62 u, ~0,42 s no ar, coyote 0,09 s, buffer 0,12 s. Mude o desenho, não a física.
  - Impulso, queda e pouso são fases visuais (`Player.takeoffPhase`, `fallPrep`, `landRunning`, `landDur`).
  - A subida começa no passo do aperto (teste).
- **Pouso correndo** não freia (`landingWhileRunningDoesNotStopTheRun`): só a absorção encurta.
- **Câmera:** não imponha giro nem subida automáticos. Contra a câmera colada no herói, use o pontilhado
  (`Game.nearFade`, `uFade`) em vez de mover a câmera. O ajuste de rumo "aperto" foi testado e piorou o
  `CameraModesTest` (relatório 09.3).

## Fechamento do QA — interface do jogo e preferências

- Dica de controles na tela: **removida na 11.1** a pedido do proprietário (nenhuma linha de teclas, nenhum "(Esc)" ou
  "(E)" em botão ou placa, em lugar nenhum). Não reintroduza.
- Voltar de outra tela que tomou a janela (o criador): `Input.releaseAll()` (as solturas de tecla foram para ela).
- `-Dvx.sensacao`/`-Dvx.encontro` são comparação de sessão: `Prefs.save()` grava o valor do arquivo até o jogador escolher
  na pausa. Preferência ilegível: padrões + original em `ARQUIVO.invalido`.
- Placa "E Conversar" acima da cabeça de qualquer NPC (`max(1,55, altura + 0,30)`).
- Conversa só sem luta engajada (`combat.engaged()`): roteiro que conversa logo depois de lutar precisa reiniciar o encontro.

## Rodada 10 — missões, objetos com estado, jogo salvo (`jogo.md` §14)

- Missão = dados (`missoes_e.json`) + falas por contexto (`dialogos_e.json`) + encontro com identidade estável
  (`combate_e.json`, 5º item) + objeto (`jogo.objetos`). A camada `vx.game.quest` não sabe de interface nem de som. Guia:
  `docs/processo/como-adicionar-uma-missao.md`; skill `quest-design`.
- **Contagem:** evento de missão só no passo lógico (`Combat.defeated` → `GameLogic.questsAfterTick`). O que contou vai
  para `Combat.retired` e nunca volta.
- **Salvamento automático:** só no primeiro passo seguro (`saveBlocker`: sem pausa de impacto, sem luta, no chão).
  Nunca por quadro.
- **QA sempre com save temporário:**
  - `-Dvx.savegame=PASTA -Dvx.prefs=- -Dvx.prefs.audio=- -Dvx.saves=PASTA_VAZIA`;
  - os testes já recebem `vx.savegame=-` do Gradle;
  - nunca use `output/saves` do proprietário.
- **Roteiro que clica num botão logo depois de abrir um menu:** desenhe a tela antes (`shot`) — os botões são os do
  último quadro desenhado.
- **Alcance de objeto ≥ 1,8 u**, como os NPCs: o `ir` do roteiro para a ~0,4 u do ponto, e com 1,5 u o E não pegava.
- **Captura de objeto antes × depois:** o mesmo `posicionar` + `camera-recompor` nos dois. Com o herói de costas para o
  objeto, a câmera atrás dele o esconde: vire o herói ~30–40° para o objeto ficar de lado no quadro.

## Rodada 11 — modo procedural (`jogo.md` §15, skill `mundo-procedural`)

- `-Dvx.mundo=procedural -Dvx.semente=N`: o mesmo `Game` e `GameLogic` com a cena gerada (`ProceduralScene` →
  `SceneDef.fromDoc`, `scene.procedural`). O modo padrão é a vila fixa e não pode mudar.
- Tudo que antes lia o relevo estático (`World.surfaceAt`, `World.pondCenter`) lê o da cena (`scene.field`).
- Roteiro de região não escreve coordenadas: `ir-lugar`, `seguir-trilha`, `vista-lugar`, `conversar` acham os lugares
  da região gerada.

## Rodada 11.1 — porta de entrada (`jogo.md` §16, `menus.md`)

- **O jogo abre no menu.** `Game` abre a sessão da vila com `saveDir = null` e põe a lógica em `MENU` (só assenta herói e
  câmera). Os roteiros antigos rodam sem menu (`GameMain`: `-Dvx.menu=nao`); os do menu pedem `-Dvx.menu=sim`.
- **Sessão trocável:** `openSession(world, cena, ps, pastaDoSave)` / `closeSession(manterVila)`.
  - Tudo da sessão fecha: malhas, `Terrain.release` do relevo gerado, atores, mortes, som.
  - Nunca deixe combate ou diálogo vivo atrás do menu.
- **Transições:** `transition(Step...)`, um passo por quadro, depois de um quadro com o rótulo real. Trabalho de GL só
  na thread do contexto. Falha = `recoverToMenu(mensagem)`.
- **Começar ou continuar:** `GameLogic.beginAdventure(aparência do perfil, nome, continuar)` com `saveDir` = pasta do par
  (`WorldStore.adventureDir`). A aparência do perfil vale também ao continuar.
- **Sair da aventura:** `leaving` ("menu" | "sair"). Salvar e sair só se `saveBlocker()` deixar. Fora do menu, "sair"
  continua direto.
- **Preferências:** sensibilidade e inverter vertical estão em `Prefs` (gravadas). O volume geral está em `AudioPrefs`.
- **Testes:** `MenuAdventureTest` (começar, continuar com a aparência do perfil, confirmação, v1/v2 no mundo certo) e
  `ApresentacaoTest` (tomadas e vitrine na colisão real).

## Fechamento da 11.1 — sair sem perder

- **Pedido de sair** (pausa "Menu principal", "Sair do jogo", X da janela):
  - numa aventura, SEMPRE pergunta (`inAdventure()`); `GameLogic.unsaved()` decide só o CONTEÚDO (tudo salvo = Sair /
    Cancelar; senão Salvar e sair / Sair sem salvar / Cancelar): o progresso das missões ou a posição (0,25 u) desde o
    último save/Continuar/começo; o tempo de jogo não conta;
  - `requestQuit()` abre a mesma confirmação de qualquer estado e lembra o estado anterior para o Cancelar.
- **Menu principal:** volta à tela inicial (`enterMenu(MAIN)`); as escolhas ficam no `MenuFlow`.
- **Testes:** `MenuAdventureTest` (X em diálogo, salvamento automático intocado, falha de gravação não sai).

## Rodada 12 — colisão composta, piso elevado e descoberta

- **Colisão composta:** perfil `composta` em `colisao_e.json`, com formas do espaço do modelo transformadas pela matriz
  do prop (`Collision.addComposite`).
  - O mesmo contrato serve ao movimento, à câmera e ao alcance.
  - Contrato: `docs/architecture/colisao-composta.md`. Teste: `CompositeCollisionTest`, em 5 rumos.
- **Piso elevado** (laje de 0,375 u): forma pisável com o topo no degrau (0,30 u). Sem ela o herói anda afundado na
  laje (o defeito que a captura mostrou).
- **Descoberta:** `SceneDef.Discovery` → `GameLogic.findDiscovery`/`useDiscovery`, o mais perto entre morador, objeto e
  descoberta.
  - Placa com o verbo, sem texto de tecla.
  - `QuestSystem.discover` paga uma vez; o diário lista; som `missao.objetivo`.
- **Roteiros:**
  - `luz dia|tarde`;
  - `vista-lugar ruina|construcao N`;
  - `ir-lugar ruina|ruina-dentro|descoberta|construcao N [andando]`;
  - `seguir-ramo ida|volta [correndo]`: a trilha principal até a bifurcação e depois a da ruína.
