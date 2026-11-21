# Jogo — protótipo de exploração (rodada 08), combate (rodada 09) e mobilidade (rodada 09.2)

> **Rodada 11.1:**
> - **Abertura:** o jogo abre no **menu principal** ([menus.md](menus.md)): personagem → mundo → aventura. A escolha de
>   aparência da vila (abaixo) continua sendo a entrada dos roteiros (`-Dvx.menu=nao`, padrão dos roteiros) e do modo
>   `-Dvx.mundo=procedural`.
> - **Estado `MENU`:** a vila como cenário, sem jogo rodando.
> - **Sessão trocável:** `Game.openSession`/`closeSession`.
> - **Pausa:** na aventura do menu, "Menu principal" e "Sair do jogo" pedem confirmação (salvar e sair / sair sem salvar /
>   cancelar).

> **Rodada 09:**
> - **Combate:** a cena ganhou uma arena ao sul e o primeiro combate. Sistema completo em [combate.md](combate.md).
> - **Passo fixo:** a exploração passou a andar em passos lógicos fixos de 1/120 s, com o desenho interpolado e a câmera
>   no mesmo passo. O movimento e a câmera da rodada 08 não mudaram; os 21 testes continuam.
>
> **Rodada 09.1:**
> - **Câmera de luta:** refeita (viés contínuo, inclinação com o fungo colado, busca de desvio que vê a vista final);
>   detalhes em [combate.md §9](combate.md#9-câmera-durante-a-luta-refeita-na-091). Fora da luta, a câmera é idêntica
>   (teste passo a passo).
> - **Linha de visão:** só testa as formas perto do raio. O resultado é o mesmo (teste com 4.000 raios); a lógica da
>   exploração ficou ~3× mais barata no p95.
> - **Som:** efeitos e passos ([audio.md](audio.md)); volume e mudo na pausa, tecla M.

Primeira experiência jogável:
1. escolher a aparência (preset, salva pelo criador ou feita na hora);
2. entrar na vila;
3. andar e correr com câmera em terceira pessoa, colidindo com o cenário e subindo pequenos degraus;
4. conversar com a Kelyra.

Não há inventário, missões nem progressão (o combate veio na rodada 09: [combate.md](combate.md)).

```bash
./gradlew jogo                                       # janela do jogo (rodada 11.1: abre no menu principal)
game/build/install/game/bin/game --demo 400          # fumaça: abre, 400 quadros, FPS médio
game/build/install/game/bin/game --roteiro ROTEIRO SAIDA [FPS]   # entrada por roteiro (janela oculta)
tools/review8.sh [capturas|videos|desempenho|testes|zip|tudo]   # pacote em output/visual-review/round-08/
```

## 1. Módulos

```
engine   infraestrutura: janela + fila de eventos, shaders, malhas, texturas, FBO, câmera, lote 2D, fonte própria
viewer   runtime compartilhado (Renderer, World, Terrain, Assets, Actor/Animator, Composer/AsyncComposer, UiKit,
         Creator) + o aplicativo de inspeção (App, Main, bench, capturas, QA)
game     regras do jogo: jogador, colisão, câmera de exploração, NPCs interativos, diálogo, estados, interface do jogo
```

Dependências: `game → viewer → engine` (e `game → engine`). O jogo **não duplica** renderer, mundo, carregamento
de assets, animação nem compositor; usa os do módulo viewer.

O nome "viewer" ficou maior que o papel: hoje ele é runtime e ferramenta ao mesmo tempo. Separar um módulo
`runtime` seria mexer em quase todo o código. A decisão está no [ADR 0013](../decisions/0013-modulo-game.md),
com o critério para fazer essa separação depois.

| Classe (`vx.game`) | Papel | OpenGL? |
|---|---|---|
| `GameMain` | linha de comando | — |
| `Game` | janela, laço, desenho da cena e da interface, roteiros, ida e volta ao criador | sim |
| `GameLogic` | estados (escolha → exploração → conversa → pausa), ações, NPCs, aparências | não |
| `Player` | corpo do herói: velocidade, colisão, chão, degraus, gravidade, rumo, animação pela velocidade | não |
| `FollowCamera` | câmera de terceira pessoa: órbita, zoom, suavização, linha de visão, desvio, enquadramento | não |
| `Collision` | relevo + formas (círculos e caixas) por perfil de prop, água, limite, linha de visão | não |
| `SceneDef` | seção `jogo` da cena (início, limite, interativos, falas) e props à mão para testes | não |
| `Input` | entrada de um quadro a partir da fila de eventos | não |

No viewer e na engine, a rodada só acrescentou ganchos:
- `Terrain` (colunas do relevo sem GL, usadas também pelo `World`);
- `World.placements()` e `World.bodies()`;
- `UiKit` (extraído do criador, desenho idêntico);
- `Creator.start()` e o botão "Levar para a vila";
- os eventos `KEY_UP` e `Window.captureCursor`;
- setas na `PixelFont`.

## 2. Inicialização e estados

`Game` monta, nesta ordem:
1. o `World` com a cena `jogo_vila_e`;
2. o `Terrain`;
3. a `Collision` (perfis + props colocados + NPCs);
4. o `GameLogic`;
5. o `AsyncComposer` (aparência do herói);
6. os atores das NPCs interativas.

| Estado | Entrada | Câmera | Cursor |
|---|---|---|---|
| `SELECT` | clique ou ↑↓ escolhe a aparência; Enter ou **Jogo novo** começa; **Continuar o jogo salvo** (C) carrega o save (apagado, com o motivo, sem save válido — rodada 10); **Criar no editor** abre o criador | herói de frente (3/4), à esquerda do painel | livre |
| `EXPLORE` | `WASD`/setas, `Shift`, mouse, roda, `E`, `Esc` | terceira pessoa | preso (clique recaptura se a janela perder o foco) |
| `DIALOG` | `E`/`Enter`/`Espaço` ou **Continuar**; `Esc` ou **Sair** | enquadra herói e NPC de lado | livre |
| `PAUSE` | **Continuar**, **Aparência**, **Criador**, **Salvar jogo**, **Diário** (rodada 10), sensibilidade, inverter vertical, som, combate, **Sair do jogo** | parada | livre |
| `JOURNAL` | diário (rodada 10): `J`/`Esc` fecha, `T` mostra/esconde o rastreador; a lógica para como na pausa | parada | livre |

Pausar e conversar preservam posição, rumo e câmera; o tempo do herói não corre na pausa (testes).

## 3. Controle do herói (`Player`)

| Parâmetro | Valor | Motivo |
|---|---|---|
| Andar / correr | 1,14 / 2,33 u/s | as velocidades da passada do `Animator` (pé de apoio parado) |
| Aceleração / frenagem | 9 / 11 u/s² | anda em 0,13 s, corre em 0,26 s e para em 0,2 s: responde sem ser instantâneo |
| Giro | até 11 rad/s | vira suave para a direção do movimento |
| Raio / altura | 0,26 / 1,2 u | herói de 1,2 u |
| Degrau | 0,30 u | sobe até 2 voxels do T1 (0,25); 3 ou mais (0,375) bloqueiam |
| Grudar no chão ao descer | até 0,40 u | mais que isso cai com gravidade (18 u/s²) |

- **Independência de FPS:** subpassos fixos de no máximo 1/120 s, então o percurso é o mesmo a 17, 30, 60, 144 e
  240 FPS (teste).
- **Movimento relativo à câmera:** `W` anda para onde a câmera olha.
- **Animação pela velocidade real:**
  - o clipe (parado/andar/correr, com histerese) sai da velocidade medida;
  - a **fase avança pela distância percorrida** (`fase += dt · v / v_clipe`): a cadência escala com a velocidade
    e a passada fica igual, então o pé de apoio não patina nem na aceleração (teste);
  - a troca de clipe faz mistura de 0,2 s e mantém a fase do passo.
- **Chão visual × lógico:**
  - o herói lógico pisa no topo do voxel (`Terrain.groundAt`);
  - a altura desenhada suaviza o degrau de 1 voxel (taxa 22/s);
  - o IK dos pés (visual) recebe o mesmo `Terrain` como chão. A colisão é do jogo, o IK é do desenho, e os dois
    usam o mesmo relevo.

## 4. Colisão (`Collision`)

Não é um motor físico. São três coisas simples e testáveis:

1. **Relevo:**
   - as colunas de voxel do T1 (`Terrain`, a mesma grade que o `World` desenha);
   - a altura vem do topo do voxel;
   - a água não é pisável, nem a margem rente a ela (`chão ≥ água + 0,06`);
   - há um limite circular por cena.
2. **Formas em XZ com faixa de altura:**
   - círculos para troncos, pedras e pessoas; caixas orientadas para casa, cerca e caixotes;
   - o corpo é um círculo empurrado para fora delas (4 iterações) e perde só a componente da velocidade contra a
     parede, ou seja, desliza;
   - forma cujo topo fica até 0,30 u acima dos pés é pisável e não bloqueia.
3. **Perfis por tipo de prop**, escritos à mão em [`assets/scenes/colisao_e.json`](../../assets/scenes/colisao_e.json):
   - `caixa` usa a pegada da malha × escala, girada com o prop;
   - `circulo` usa um raio fixo;
   - `tronco` é um círculo do tronco para o corpo mais a **copa só para a câmera**, porque o herói passa por
     baixo dela;
   - `nenhum` vale para flores, capim, samambaia e juncos;
   - `camera: false` deixa a câmera atravessar postes finos e arbustos.

   A malha visual nunca é usada para colidir. As caixas saem da matriz e da caixa da malha que o `World` colocou;
   nos testes, da origem e do tamanho do `props.json`, que dão o mesmo resultado.
4. **Desnível:** o passo é bloqueado se o chão novo subir mais que o degrau ou não for pisável. Antes de parar,
   tenta deslizar por um eixo.

Para acrescentar um prop novo à colisão, dê a ele uma linha em `colisao_e.json` (ou deixe o `padrao`: caixa
× 0,9) e confira com `GameplayTest` e um vídeo de obstáculos.

## 5. Câmera (`FollowCamera`)

- **Órbita:**
  - gira em volta de um ponto 0,95 u acima dos pés;
  - o mouse gira (0,0032 rad/px, ajustável na pausa, vertical invertível);
  - a roda vai de 2,2 a 8 u;
  - a inclinação vai de −0,10 a 1,05 rad.
- **Distância padrão 4,6 u**, comparada com 3,1 e 5,9 u (capturas `08–10`):
  - a 3,1 u o herói esconde a vila;
  - a 5,9 u a câmera passa colada em placas e pedras do primeiro plano;
  - a 4,6 u o herói fica no terço inferior com a vila, as NPCs e o posto à vista.
- **Suavização:** o ponto segue rápido no plano (16/s) e devagar na altura (6/s). A câmera sobe a escada de
  degraus sem quicar (teste: nunca desce subindo).
- **Linha de visão** contra o relevo e as formas de câmera (paredes, troncos, copas):
  - quando bloqueia, a câmera **encosta na hora**: suavizar a entrada fazia ela atravessar a parede por alguns
    quadros (pego no teste);
  - quando libera, volta devagar (3/s).
- **Desvio junto a paredes:**
  - se a vista livre fica menor que 1,7 u, procura o menor desvio de rumo (até 80°) e de inclinação que dê vista
    livre;
  - aplica o desvio suavemente e volta a 0 quando libera;
  - o rumo escolhido pelo jogador não muda;
  - sem desvio possível (corredor fechado), encosta, com mínimo de 0,9 u.
- **Enquadramento de conversa:** alvo entre os dois, de lado e um pouco atrás do herói, do lado em que a câmera
  já estava; transição suave.
- **Na luta (rodada 09, refeita na 09.1):** ajuste de combate por cima desta câmera. Ver [combate.md §9](combate.md).
- **Dentro da torre caída (rodada 12.3, P85):** a câmera ignora só as formas daquela torre e o desenho a recorta num
  cone entre a câmera e o herói (`CameraCutaway`). Fora da zona da torre, esta câmera é idêntica passo a passo. Ver
  [recorte da câmera](recorte-da-camera.md).

## 6. Cena jogável

[`assets/scenes/jogo_vila_e.json`](../../assets/scenes/jogo_vila_e.json) é uma cópia controlada da vila; `vila_e`
e `clearing_e` não mudaram.

- **Mesmo de antes:** relevo, luz, floresta, casa, posto de trilha, salgueiro, cartógrafa, Índira, Súvia,
  mariposa e pompom.
- **Acréscimos:**
  - poço;
  - cerca;
  - caixotes e barril da estalagem;
  - dois postes de lanterna;
  - tronco, toco, pedras, placa e moitas;
  - juncos e nenúfar no lago.
- **A Kelyra** sai das NPCs decorativas e vira interativa.
- **Seção `jogo`:**
  - início `[x, z, rumo°]`;
  - limite (círculo de raio 11,5);
  - `interativos` (rig, nome, título, posição, rumo, alcance e falas com expressão).

Percursos usados nos vídeos e testes:

| Percurso | Trajeto |
|---|---|
| Plano | o caminho entre o início e o posto |
| Encosta | pelo leste do posto, entre o poço e a casa, até o patamar da estalagem (0,88 u, degraus de 1 voxel) |
| Obstáculos | pedras, caixotes e poço |
| Água | a margem do lago (bloqueia) |

## 7. Interação e diálogo

- **Quem pode conversar:** a NPC mais perto dentro do alcance (1,8 u) e a até 80° da frente do herói (testes:
  de costas não, longe não).
- **Indicação:** uma placa discreta sobre a cabeça, **E Conversar com Kelyra**, projetada na tela.
- **Ao conversar:**
  - o herói freia e vira para ela;
  - a Kelyra vira para o herói, acena no começo (o gesto `EMOTE` existente) e muda de expressão por fala;
  - a câmera enquadra os dois.
- **Painel:**
  - mostra a plaqueta "Kelyra · Estalajadeira", a fala com quebra de linha e o progresso;
  - tem os botões **Continuar (E)**, que vira **Até logo** na última fala, e **Sair (Esc)**.
- **Falas:** as de sempre ficam na seção `jogo` da cena. Desde a rodada 10, quem tem missão (Kelyra) ou comenta uma
  (Bramor) fala por contexto (`assets/scenes/dialogos_e.json`, `DialogueBook`). A última fala da oferta tem escolhas
  (Aceitar / Agora não). Ver [missoes.md](missoes.md).
- **Objetos do mundo (rodada 10):** o E também usa objetos com estado (`jogo.objetos`: o marco do posto), pela mesma
  regra de alcance e ângulo (pega o mais perto entre NPC e objeto). A placa diz a ação ("Acender o marco").
- **NPCs de ofício (integração da 09.4):** Bramor (guardião de trilhas), Teodor (estudioso), Pluma (cronista),
  Ondina (guia costeira) e Lunara (cantora noturna) entram em `jogo.interativos` com três falas cada, pelo mesmo
  sistema. Têm altura narrativa (Bramor 1,73 u); a câmera de conversa sobe o alvo e se afasta na proporção da
  altura do rig (`SceneDef.Interactive.height`, de `meta.worldUnits`; até 1,25 u nada muda).
- **Lado da câmera na conversa:** entre os dois lados (e, se ambos fecharem, por cima do ombro), o mais perto de
  onde a câmera já estava **entre os que têm a vista livre** — antes, uma pedra do lado escolhido empurrava a câmera
  para dentro do rosto.
- **Vagas:** cada NPC de ofício fica em chão caminhável, sem nenhuma forma de corpo ou câmera a menos de 1,6 u, sem
  copa por cima e longe dos outros (`GameplayTest.everyResidentStandsOnFreeGroundWithRoomToTalk`); roteiro de
  captura `tools/jogo/10-npcs-oficio.txt`.
- **Repetição automática do teclado** não reabre a conversa (teste).

## 8. Aparência personalizada no mundo

- **Escolhas:** os três presets e as aparências salvas em `output/aparencias/` (formato `valumo.aparencia` v2 — a v1 ainda é lida;
  arquivo inválido aparece com o motivo e não é escolhível).
- **Mesmo pipeline do criador:** o herói é composto pelo `AsyncComposer`/`Composer` da rodada 07, com o mesmo
  rig e as mesmas animações.
- **Criador dentro do jogo:**
  - **Criar no editor** (ou **Abrir o criador** na pausa) abre o criador da 07.2 na mesma janela, com a aparência
    atual;
  - **Pronto → Levar para a vila** volta com a aparência nova (em memória; **Salvar** no criador grava em
    arquivo).
- **A aparência não define classe** nem equipamento (dito na tela de escolha).

## 9. Interface do jogo

- **Componentes:** `UiKit` (as mesmas peças do criador: madeira, pergaminho, fonte de pixel própria, P1 só na
  escolha ativa).
- **HUD mínima:**
  - indicação de interação;
  - balão de mensagem;
  - "Preparando…" se a composição demorar.
- **Sem barras** de vida nem de mana na exploração (na luta: [combate.md](combate.md) §19).
- **Saída limpa:**
  - **Sair do jogo** ou o X da janela fecham;
  - `close()` libera o compositor, o renderer, o FBO de captura, o `UiKit`, a cena e os assets;
  - abrir e fechar o criador libera o `App` e o `Creator` dele, com a cena e os assets que carregaram (conferido por
    contagem de malhas vivas: `output/qa/engineering-audit/`, roteiro `vazamento-criador.txt`).

## 9.1 Áudio e volume (rodada 09.4)

> Integração com a 09.3: um dispositivo só (ADR 0023). Na pausa ficam "Música", "Efeitos" (combate, passos,
> interface e ambiente) e "Som: ligado/mudo (M)", e as opções de combate em fileiras inteiras ("Barras", "Sensação",
> "Arena"). O rótulo da sensibilidade do mouse virou "Sensibilidade", para caber em 720p. Detalhes em
> [audio.md](audio.md).

- `GameAudio` traduz fatos do jogo em eventos do catálogo de som: pisada (`Player.steps`, na fase de contato do pé)
  com a superfície (`World.surfaceAt`: grama, terra, areia, pedra), ações do `GameLogic` (`onAction`), música da vila
  (abafada na pausa e na conversa), vento e lago (posicional) e o ouvinte no herói. Arquitetura: [audio.md](audio.md).
- Pausa: fileiras Sensibilidade, Música e Efeitos com o mesmo componente (rótulo que cabe na escala inteira, valor
  centrado entre − e +); preferências em `output/preferencias/audio.json` (`AudioPrefs`; `-Dvx.prefs.audio=` muda o
  arquivo, `-Dvx.prefs=ARQUIVO` leva o áudio para um arquivo irmão e `-Dvx.prefs=-`, o padrão dos roteiros, nem lê nem grava).
- `./gradlew jogo` toca no dispositivo; roteiros e `--demo` são mudos. Gravação sincronizada:
  `JAVA_OPTS="-Dvx.audio=loopback -Dvx.audio.gravar=x.wav"`. Roteiros: `tools/jogo/07-audio.txt`, `08-pausa-volume.txt`,
  `09-ambiente.txt`.

## 10. Testes

`GameplayTest` (sem OpenGL, 22 testes):

| Assunto | O que verifica |
|---|---|
| FPS | percurso igual de 17 a 240 FPS (tempo simulado exato) |
| Aceleração e frenagem | curtas, mas não instantâneas |
| Giro | limitado por quadro |
| Animação | clipe pela velocidade e fase pela distância (pé plantado) |
| Obstáculos e paredes | parada no obstáculo e deslize ao longo da parede |
| Degraus | o baixo sobe e o alto bloqueia, com os pés no chão |
| Desnível | queda com gravidade, sem salto |
| Câmera | estável parada e na escada; não atravessa parede; desvia; volta devagar; corredor; morro; limites; sensibilidade |
| Cena | carrega com colisores e Kelyra; vila e clareira não têm a seção de jogo |
| Relevo | o chão do jogo é o voxel desenhado |
| Passeio | percurso pela vila sem entrar em prop, sem sair do chão e sem ficar preso de verdade (fuga em 8 direções) |
| Água | não é pisável |
| Kelyra | alcançável desde o início |
| Interação | distância e ângulo |
| Diálogo | entrar, avançar, sair, Esc, sem repetição do teclado, posição preservada |
| Pausa | preserva tudo |
| Mouse | gira a câmera e o movimento é relativo a ela |
| Aparência | presets, salvas, inválida, criada; vai para o compositor |

Regressão das peças compartilhadas:
- as 33 capturas do criador saem idênticas às da 07.2 pixel a pixel (depois do `UiKit` e do `Terrain`);
- as referências canônicas e o gate dos presets continuam a 0 de diferença (`tools/review7.sh gate`,
  `tools/reference.sh`).

## 11. Validação e limites

- **Roteiros:** `tools/jogo/*.txt` (comandos documentados em `Game.script`). A entrada passa pela fila da janela,
  não pelo sistema operacional. O roteiro de teste com teclado e mouse físicos para o proprietário está em
  [roteiro-de-teste-fisico.md](../validation/roteiro-de-teste-fisico.md).
- **Problemas abertos:** [problemas-conhecidos.md](../validation/problemas-conhecidos.md).
  - Rodada 09: a interrupção da espada foi resolvida.
  - A lâmina longa continua aberta e fora do jogo.
  - O combate está em [combate.md](combate.md).

## 12. Rodada 09.2 — velocidade, salto, controles e preferências

### Velocidade (perfil de sensação)

`Player` deixou de usar constantes fixas: andar, correr, aceleração, frenagem, giro, passada e controle no ar vêm do
perfil (`combate_e.json → sensacao`; ver [combate.md](combate.md) §14).

- **Perfil padrão C:** andar 1,75 u/s, correr 3,50 u/s, aceleração 22, frenagem 24, giro 15 rad/s.
- **Perfil A:** os números da rodada 08/09.1.
- **Pé plantado:** a fase da passada avança por `distância / (velocidade de desenho × passada)`. Mais rápido = mais
  cadência e o pé continua plantado.
- **Passos (som):** acompanham a mesma fase.

### Salto ([ADR 0019](../decisions/0019-salto.md))

| | |
|---|---|
| tecla | Espaço (exploração e luta; não no meio de golpe, guarda ou esquiva) |
| altura / tempo no ar | 0,62 u / ~0,42 s (gravidade 24 u/s² subindo, 34 caindo) |
| coyote / buffer | 0,09 s depois de sair da borda / aperto até 0,12 s antes de pousar |
| controle no ar | 55% da aceleração (perfil C) |
| chão | `Collision.floor`: relevo ou topo de forma `"pisavel"` (caixote, tronco caído, toco, pedras pequena e média, barril) |
| parede no ar | desnível maior que um degrau bloqueia também no ar (sem teleporte para cima de borda) |
| golpe no ar | não existe nesta rodada: o clique no ar sai ao pousar |
| animação | pés recolhidos e corpo inclinado no ar (`Motion.air`); quadril cede ao pousar (`Motion.land`) |

### Controles (todos do módulo game; o criador e o viewer não mudaram)

| Entrada | Ação |
|---|---|
| WASD | mover (relativo à câmera; travado, relativo ao alvo pela própria câmera) |
| Shift | correr |
| Espaço | pular |
| Alt esquerdo / botão do meio | esquivar (com direção: salto baixo R2; sem: recuo) |
| Clique esquerdo | atacar (de novo, encadeia) |
| Clique direito segurado | defender |
| Q | travar / soltar a mira |
| Tab / Shift+Tab | próximo alvo à direita / à esquerda na tela |
| Mouse | câmera (travado: ajuste de ±0,40 rad e inclinação) |
| E | conversar |
| I | mochila (rodada 13; também pelo botão Inventário da pausa) |
| Esc | pausa (volume, mudo, barras de inimigos; sensação e arena só no modo técnico, desde o fechamento da 10) |
| M | mudo |

As teclas ficam em um só lugar: `GameLogic.explore` (entrada → intenção) e `Input` (botão do meio).

### Preferências

`Prefs` grava barras de inimigos, perfil de sensação e encontro da arena em `output/preferencias.json`
(`-Dvx.prefs=ARQUIVO`; `-` = nenhum). É separado da aparência do personagem. Arquivo ausente ou ilegível = padrões.
Roteiros e testes usam sempre os padrões e não gravam.

### Referência do WASD

`FollowCamera.inputYaw`: o rumo do mouse na hora, mais o desvio automático da câmera (parede, mira) seguido no máximo a
2,5 rad/s. A câmera pode desviar de uma parede sem virar o controle de uma vez.


## 13. Rodada 09.3 — salto apresentado e herói translúcido perto da câmera

- **Salto:** a física é a da 09.2. O desenho ganhou impulso nos primeiros 0,10 s, preparação de queda nos últimos 0,14 s
  e pouso proporcional ao impacto, mais curto correndo. Ver o adendo do [ADR 0019](../decisions/0019-salto.md).
  `Player.takeoffPhase()`, `fallPrep()` e `landRunning()` alimentam `Motion.takeoff`, `fall` e `landRun`.
- **Herói perto da câmera** (`Game.nearFade`): mede a distância do olho ao eixo do corpo, do quadril ao alto da cabeça.
  - Abaixo de 0,9 u, o herói se dissolve em pontilhado ordenado Bayer 4×4 (`uFade` no `scene.frag`, `discard`), até 75%
    a 0,45 u.
  - A posição da câmera não muda: os números do `CameraModesTest` são os da 09.2.
  - O pontilhado não usa transparência real. Com isso não há ordenação nem custo de mistura, e as referências e o gate
    não mudam (só o herói do jogo recebe `fade`).

## 14. Rodada 10 — a primeira aventura, missões e jogo salvo

- **Missões:** [missoes.md](missoes.md) (sistema), [missao-luzes-do-posto.md](missao-luzes-do-posto.md) (a primeira),
  [../processo/como-adicionar-uma-missao.md](../processo/como-adicionar-uma-missao.md) e o
  [ADR 0024](../decisions/0024-missoes-e-save.md).
- **Jogo salvo:** [save.md](save.md). `-Dvx.savegame` (padrão `output/saves`; `-` desliga), escrita atômica, salvamento
  automático nos marcos da missão no primeiro passo lógico seguro, "Salvar jogo" na pausa e "Continuar" na tela
  inicial.
- **Arena:** antes de aceitar a missão, a arena abre no encontro padrão (como na 09.x). Ao aceitar, passa para o
  encontro `trilha` (dois fungos com identidade estável). Fungos que já
  contaram nunca voltam (`Combat.retired`).
- **Interface nova:**
  - placa do objeto;
  - escolhas no diálogo;
  - rastreador (canto superior direito, abaixo das mensagens);
  - aviso de missão;
  - diário (J);
  - na pausa, as mensagens descem para baixo (no topo cobriam o título);
  - mensagens longas terminam em "…" na largura da tela.
- **Roteiros:** `tools/aventura/` (aventura inteira, continuar, marco antes × depois, recusar, cair na trilha) e
  `tools/review10.sh`. Comandos novos do interpretador:
  - `missao`: estado no log;
  - `lutar S`: entrada real, trava a mira, aproxima e clica;
  - `derrubar #i`: montagem técnica pelo caminho do dano;
  - as teclas `J`, `T` e `C`.
- **Testes:** `QuestTest`, `AdventureTest`, `SaveGameTest` e o caso de missão do `GameAudioTest`. Mais o ajuste do
  `GameplayTest`: a conversa da Kelyra é a oferta.

### Fechamento da rodada 10 — ferramentas técnicas fora do jogo normal

- **Jogo normal:** a pausa não oferece a arena (um / três / chefe / trilha) nem a sensação A/B/C. As ações
  `encontro`/`sensacao` da interface são recusadas. A arena e a sensação gravadas antes nas preferências não valem
  (nem são apagadas do arquivo): vale o padrão aprovado (sensação C, encontro padrão até aceitar a missão). A missão
  nunca perde os fungos porque o jogador abriu a pausa (`AdventureTest.jogoNormalNaoTrocaArena…`).
- **Modo técnico** (`./gradlew jogo -Ptecnico`, `-Dvx.tecnico=true`): tudo como na 09.2, com F3 e as comparações na
  pausa.
- **Roteiros e testes:** mantêm o acesso direto (comando `encontro X`, `setEncounter`, `-Dvx.encontro`, `-Dvx.sensacao`).
  O `tools/combate92/capturas.txt`, que mostra essas opções na pausa, roda com `-Dvx.tecnico=true` nos pacotes 09.2 e
  10, e as comparações históricas não mudam.
- **Falas:** nenhuma afirma o que o NPC não vê. O marco não aparece da estalagem nem do lugar do Bramor, então a
  Kelyra deduz pelo retorno do herói e o Bramor "soube".

## 15. Rodada 11 — modo procedural (experimental)

- **Como abrir:** `./gradlew jogo -Dvx.mundo=procedural -Dvx.semente=1024` gera a região da semente ([mundo
  procedural](mundo-procedural.md)). O modo padrão continua a vila fixa da rodada 10.
- **O que muda:** o `Game` cria o `World` com o campo e o documento gerados (`ProceduralScene`), e o `GameLogic` recebe
  a cena gerada (`SceneDef.fromDoc`, com `scene.procedural`).
- **O que é igual:** controles, câmera, combate, missão, diário, save e som. O som de passo e o do lago leem o relevo da
  cena (`scene.field`); região sem lago não toca o lago.
- **Na tela:** a semente aparece no subtítulo da tela inicial e no canto da pausa.
- **Roteiros:** `tools/mundo/` e `tools/review11.sh`. Comandos novos:
  - `regiao`, `vista-lugar`, `vista-fim`;
  - `ir-lugar [andando]`, `seguir-trilha`, `conversar`;
  - `medir-geracao`.
  - A vista técnica (`FollowCamera.technicalView`) não encurta por obstáculo e esconde as placas. Ela só existe nos
    roteiros.

## 16. Rodada 11.1 — porta de entrada

Detalhes em [menus.md](menus.md), [perfis-de-personagem.md](perfis-de-personagem.md) e
[catalogo-de-mundos.md](catalogo-de-mundos.md); decisões nas ADRs [0027](../decisions/0027-porta-de-entrada-e-sessoes.md)
e [0028](../decisions/0028-personagens-mundos-e-aventuras.md).

- **Estados:** `MENU` entra na lista (`SELECT, EXPLORE, DIALOG, PAUSE, JOURNAL, MENU`). Em `MENU`, a lógica só assenta o
  herói e a câmera; nada de combate, IA, missão ou save.
- **Começar:** `GameLogic.beginAdventure(aparência, nome, continuar)` começa ou continua a aventura da pasta `saveDir`
  (a pasta do par personagem × mundo). A aparência é sempre a do personagem.
- **Sair:** `GameLogic.leaving` ("menu" | "sair") é a confirmação aberta. `menu-salvar` só sai se o save der certo;
  `wantMenu` pede ao `Game` a troca de sessão.
- **Preferências:** `Prefs` ganhou `sensibilidade` e `inverterVertical` (antes viviam só na câmera da sessão);
  `AudioPrefs` ganhou `geral` (volume principal).
- **Roteiros:** `click` desenha um quadro antes de procurar o botão (os botões da tela atual). Os comandos novos estão em
  [menus.md](menus.md#como-acrescentar).

## 17. Rodada 12 — interiores e descoberta

- **Colisão composta:** oficina, ruína de casa e barraca têm interior. Paredes, peças baixas e telhado estão em formas
  medidas dos voxels, e o piso elevado é chão pisável no degrau. O herói entra pela porta e não atravessa parede, e a
  câmera não atravessa parede nem telhado. Contrato: [colisão composta](colisao-composta.md).
- **Descoberta** (mundos v2):
  - **Usar:** a lareira da ruína é um ponto de descoberta. Perto e de frente aparece a placa "Examinar a lareira"
    (só o verbo, sem tecla na tela).
  - **Primeira vez:** E dá o aviso "Descoberta: Casa tomada pela mata · +5 moedas", a frase do lugar, o som de objetivo
    e o salvamento no primeiro passo seguro.
  - **Depois:** só a frase de "já visto".
  - **Diário:** ganha a seção "Descobertas", e a lista de mundos mostra "· 1 lugar descoberto".
  - **Regra:** `GameLogic.useDiscovery`, `QuestSystem.discover` (uma vez), `progresso.descobertas` no save (opcional).
- **Candidato:** NPC, objeto e descoberta disputam o E; vence o mais perto, mais ou menos à frente do herói.

## 18. Rodada 13 — mochila, itens e recompensas

- **A mochila** (rodada 13.1, [ADR 0032](../decisions/0032-mochila-viva.md)): uma camada sobre a exploração
  (`GameLogic.bagOpen`), com o jogo correndo.
  - **Abrir e fechar:** abre com I ou pela pausa e fecha com I ou Esc.
  - **Mouse:** é dela. O cursor fica solto, a câmera não gira, o seletor segue o mouse e o clique usa o item; nunca ataca
    nem levanta o escudo.
  - **Teclado:** continua no herói (WASD, Shift, Espaço, Alt, E, Q e Tab); as setas e o Enter são da grade.
  - **O que a fecha:** a conversa, o diário, a pausa e a derrota.
  - **Usar:** é uma ação da lógica.
  - **Sem limite de espaços.**

  Na rodada 13 era um estado (`INVENTORY`) que parava a lógica, com 24 espaços.
- **Fontes:** o presente da Índira ao terminar a conversa e o achado de cada descoberta (por tipo de lugar), uma vez por
  aventura, tudo ou nada.
- **Uso:** a cataplasma cura 2 de vida fora da luta engajada.
- **Vida:** continua não indo para o save (ao continuar, volta cheia); R depois de um desfecho também a enche. A cura vale
  entre lutas: um herói ferido que venceu, ou que saiu da coleira no meio de um grupo.
- **Contratos:** [inventário](inventario.md), [itens](itens.md), [recompensas e loot](recompensas-e-loot.md),
  [ADR 0031](../decisions/0031-itens-e-recompensas.md).
