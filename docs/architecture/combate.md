# Combate — primeira fatia jogável (rodada 09; câmera e som na 09.1; mobilidade, mira, grupos e morte voxel na 09.2)

Um herói de espada e broquel contra um inimigo (o fungo-cardo), numa arena na borda sul da vila. Não há inventário,
experiência, classes, mana nem missões. Decisões estruturais: [ADR 0015](../decisions/0015-combate-por-trajetoria-e-passo-fixo.md).
Números ajustáveis: [`assets/scenes/combate_e.json`](../../assets/scenes/combate_e.json) (escrito à mão).

```bash
./gradlew jogo                                   # entrar na vila e descer a trilha até a arena (ao sul)
./gradlew jogo -Ptecnico                         # modo técnico: F3 liga hitboxes, hurtboxes e estados
tools/review9.sh [capturas|videos|angulos|interrupcoes|validadores|desempenho|testes|zip|tudo]
tools/review91.sh [camera|videos|sons|desempenho|testes|validadores|regressao|zip|tudo]   # 09.1: câmera e som
viewer/build/install/viewer/bin/viewer --check interrupcao-fina   # cortes a cada 0,01 s nos golpes jogáveis
```

## 1. Peças

| Classe (`vx.game`) | Papel |
|---|---|
| `Combat` | o confronto: passo fixo, ordem do passo, pausa de impacto, eventos, resultado, recomeço |
| `Hero` | o herói em combate: estados, golpes e encadeamento, guarda, dano recebido, trajetória da lâmina |
| `ActionLayer` | camada de ação AO VIVO com a semântica do `Sequencer` (o validador aprova o que o jogo desenha) |
| `Enemy` | o fungo-cardo: máquina de estados, percepção, movimento com colisão, coleira, cabeçada |
| `CombatDef` | leitura do `combate_e.json` |
| `GameLogic` | passo fixo de 1/120 s na exploração, intenção por passo, câmera no passo, recomeço |
| `FollowCamera` | o mesmo da rodada 08 + ajuste de combate (seção 9) |
| `Game` | desenho (herói, inimigo, lampejo, acentos), interface de combate, roteiros |

No viewer e na engine, a rodada acrescentou só ganchos:
- **`Choreo.Move`:** janelas de interrupção (`cuts`, `cutRate`, `exitFor`).
- **`Sequencer`:** saída autorada e origem dupla.
- **`Animator`:**
  - o clipe `DEATH`;
  - a animação `brawler`;
  - `Motion.fromMove2`.
- **`Check`:** `interrupcao-fina`.
- **`Composer.kit`:** espada e broquel em qualquer aparência.
- **`AsyncComposer`:** a contagem de descartes.
- **Desenho:** `Renderer.Draw.flash` e `uFlash` no `scene.frag`.

## 2. Tempo e ordem (determinismo)

- **Passo fixo:** a exploração e o combate andam em passos de **1/120 s** (`GameLogic.TICK`), com acumulador em `double`.
  - O desenho interpola entre o passo anterior e o atual: posição, rumo, câmera e tempo da animação.
  - Nada da lógica depende da taxa de quadros nem do tamanho da janela.
- **Câmera no passo:** a câmera também anda no passo fixo, porque o rumo dela entra na intenção de movimento e na mira da
  guarda. O desenho interpola entre dois passos dela.
- **Ordem de um passo** (`Combat.tick`):
  1. pausa de impacto: se ativa, só ela corre;
  2. herói: intenção → estado → corpo → trajetória da lâmina → dano no inimigo;
  3. inimigo: percepção → estado → corpo → cabeçada → dano ou bloqueio no herói;
  4. separação dos corpos;
  5. resultado.
- **Entrada:**
  - o aperto do botão esquerdo vale para o próximo passo, mesmo num quadro sem passo, então não se perde a 240 FPS;
  - o botão direito é estado (segurado).
- **Prova** (`CombatTest.theSameInputGivesTheSameFightAtAnyFrameRate`):
  - a mesma luta roteirizada pela fila de eventos sai **idêntica** a 30, 60, 120 e 240 FPS;
  - a 17,3, 45 e 144 FPS, idêntica à luta a 120 FPS com os eventos chegando nos mesmos instantes.
  - Taxas que não dividem 120 entregam o clique até um quadro depois (a 17 FPS, até 58 ms). Isso muda o que o jogador
    fez, não a regra.
- **Pausa de impacto:** congela só os combatentes (estado, corpo, animação). O relógio do mundo, a câmera e o vento
  seguem. É contada em passos, então é determinística.

## 3. O herói

| Estado | Entrada | O que acontece |
|---|---|---|
| LIVRE | WASD, Shift, clique, botão direito | anda como na rodada 08 (mesmo `Player`) |
| GOLPE | clique | corte horizontal; um clique na janela `aceita` encadeia o diagonal e depois o descendente |
| GUARDA | botão direito segurado | ergue o broquel (0,12 s), anda a 45% e vira para onde a câmera olha |
| ATINGIDO | — | reação + atordoamento 0,42 s (0,60–0,70 s se a reação for uma saída autorada); invulnerável 0,75 s |
| CAÍDO | — | reação → derrota (ajoelha); painel "Você caiu" |

- **Mira do golpe:**
  - vai para onde o jogador empurra o movimento, senão para onde o herói já olha;
  - com um inimigo a até 2,4 u e a menos de 55° dessa direção, gira para ele na preparação (assistência de largada; não
    é trava de mira).
- **Avanço:** na execução, o corpo avança `avanco` u pela colisão, com impulso e depois frenagem.
- **Encadeamento:**
  - um clique dentro de `aceita` fica guardado;
  - a troca acontece em `encadeiaEm`, não no clique;
  - cliques fora da janela não encadeiam, e um clique adiantado vale por `bufferAtaque` (0,25 s);
  - não há árvore de combos: é uma sequência fixa h → d → v.
- **Liberação:** a partir de `libera`, andar, guardar ou clicar de novo cortam o golpe. Antes disso, o golpe se
  compromete. Não há cancelamento irrestrito.
- **Instantes autorados:** encadear, liberar e voltar a agir depois de uma reação usam instantes medidos pelo validador
  de armas (seção 6). Nenhum foi escolhido a olho.

## 4. Golpe → dano: hitbox, hurtbox e colisão são coisas diferentes

| Volume | Do quê | Para quê |
|---|---|---|
| colisão de movimento | círculo do corpo (`Player.RADIUS` 0,26; inimigo 0,28) | não atravessar cenário nem um ao outro |
| hurtbox | cápsula vertical presa aos pés (`hurtbox` no JSON) | onde se pode ser atingido |
| hitbox do herói | cápsula ao longo do +Y da peça `weapon`, do fim do punho à ponta (voxels 5–27, raio 0,05 u) | o que fere, **só na janela de dano** |
| hitbox do inimigo | esfera presa à peça `cap` (ponto e raio no JSON) | a cabeçada, só na fase ativa |
| volumes do validador | cápsulas do `rig.json` (`*_volumes`) | interseção de animação (não são de jogo) |

- **Trajetória, não quadro:**
  - a cada passo, a pose do herói é avaliada pela **própria animação** em subamostras de **1/480 s**, a mesma resolução
    do validador;
  - entre os pontos, a raiz é interpolada da posição do passo anterior para a do atual.
- **Varredura:**
  - a cápsula da lâmina e o **rastro da ponta** entre subamostras são testados contra a hurtbox;
  - a ponta anda até cerca de 0,1 u por subamostra, menos que o raio da hurtbox, então um golpe rápido não atravessa
    entre dois quadros (teste com alvo de 2 cm).
- **Janela de dano** (`ativo`, por golpe):
  - fica dentro da execução do perfil e só enquanto a ponta varre a metade da frente (até ±82°; teste);
  - não fere na preparação nem na recuperação, nem quem está atrás ou ao lado.
- **Restrições:**
  - **uma vez por golpe por alvo:** cada golpe tem uma identidade, e o inimigo guarda a última que o atingiu;
  - **obstáculo:** só há dano se nada sólido estiver entre o peito do herói e o ponto de contato (`Collision.solidBetween`:
    relevo e formas de corpo; copas não);
  - **amigos:** as moradoras não são alvos; a lista de alvos é só o inimigo.

## 5. O inimigo (fungo-cardo)

- **Asset:** variante de combate do fungo canônico (`tools/creatures_e.py: fungo_cardo`). O fungo canônico continua
  idêntico (hash conferido).
  - **Muda:** chapéu carmim mais escuro, coroa de 5 espinhos, olhos em fenda inclinada e mãos de nó de espinhos.
  - **Fica igual:** proporção, corpo, pés e boca do canônico.

```
REPOUSO ─vê/ouve─▶ ALERTA (0,55 s) ─▶ PERSEGUE ─perto e de frente─▶ PREPARA (0,70 s) ─▶ GOLPE (0,26 s) ─▶ RECUPERA (0,85 s)
   ▲                                     │                                                                  │
   └──── VOLTA (recupera a vida em casa) ◀─ perdeu de vista 2,5 s / herói fora da coleira ◀─────────────────┘
```

- **Percepção:** precisa das três condições.
  - Distância até 5,2 u.
  - Campo de 140°, ou até 1,6 u em qualquer direção (ouve).
  - Linha de visão livre de sólidos: não vê nem ataca através da pedra.
- **Coleira:** 4,4 u em volta de casa. O fungo nunca sai dela, desiste se o herói sai e volta para casa. As moradoras
  ficam a mais de 8 u da coleira (teste).
- **Movimento:**
  - mesmo relevo, formas, degrau, água e limite do herói;
  - anda para onde olha, com giro limitado de 4,5 rad/s: não desliza de lado nem vira de estalo.
- **Obstáculo:**
  - sem progresso por 0,7 s, contorna para o lado mais livre por 0,8 s;
  - preso pela terceira vez, desiste e volta.
  - **Não há busca de caminho:** um obstáculo grande entre os dois faz o fungo contornar ou desistir, previsível e
    testado.
- **Ataque telegrafado:**
  - **PREPARA:** agacha, recua, chapéu para trás e tremor no último terço. Mira só nos primeiros 60%, depois trava, por
    isso dá para esquivar andando.
  - **GOLPE:** cabeçada com avanço de 3 u/s. A fase ativa vai de 20% a 85%, com a hitbox no chapéu.
  - **RECUPERA:** parado e cambaleando por 0,85 s. É a janela do jogador.
- **Atingido:**
  - recua e cambaleia por 0,45 s;
  - **compromete** no GOLPE (leva dano, não para);
  - fica **imune a cambalear** por 1,1 s depois de se recompor, para não ser travado em sequência.
- **Bloqueado:** ricochete, com recuperação de 1,25 s e recuo.
- **Morte:** cai de costas e some encolhendo. O resultado é "vitória", e R recomeça.
- **Animação `brawler`** (`Animator`): EMOTE é o alerta, ATTACK usa tempo normalizado [0,1) preparação, [1,2) golpe e
  [2,3) recuperação, HIT é o recuo e DEATH é a queda. Os tempos de verdade são da lógica.

## 6. Interrupção da espada (o débito das rodadas 05–08)

**Linha de base:** cortar o golpe e deixar a reação assumir atravessava o corpo.
- **Espada:** 7 de 21 cortes (passo 0,05 s) e 7 de 112 no passo 0,01 s.
- **Lâmina longa:** 18 de 47.

**Correção:** regra autorada por fase no perfil (`"interrupt": [{"until", "rate", "exits"}]`, `Choreo.Move`), usada igual pelo
`Sequencer` (validador e vídeos) e pelo jogo (`ActionLayer`):

| Fase cortada | O golpe cortado… | A reação |
|---|---|---|
| preparação e execução | rebobina pela própria trajetória (ritmo −2) | "hit" |
| logo após a execução (h até 0,60 s, d até 0,62 s) | segue em frente | **saída autorada** `hit_cross_h`/`hit_cross_d`: recua o corpo com a espada do lado onde o corte terminou e volta pela recuperação autorada do golpe |
| recuperação | segue em frente até a guarda | "hit" |

- **Resultado:** `--check interrupcao-fina` dá **0 cortes inseguros a cada 0,01 s**, em todos os casos.
  - **Golpes:** os três cortes, o `shield_block` e a guarda do jogo.
  - **Equipamentos:** os dois.
  - **Relatório histórico:** a espada passa de 21/21 cortes seguros.
- **Lâmina longa:** **não jogável** nesta rodada e continua 18/47 (`AnimationQaTest` fixa o número).
- **Transições do jogo:** o `CombatAnimationTest` valida, nos tempos do JSON, encadeamentos, dano no meio de um
  encadeamento (origem dupla), liberação, golpe ou guarda depois da reação, segundo dano, guarda baixando e derrota. Os três
  corpos (aventureiro, exploradora e maga) com o kit passam no validador.
- **Regras de interrupção** (explícitas):
  - dano sempre interrompe (o herói não tem armadura de golpe);
  - golpe só cancela golpe pelo encadeamento ou depois de `libera`;
  - guarda não cancela golpe;
  - durante a invulnerabilidade e a reação não há corte novo;
  - por isso nunca há três transições sobrepostas (`ActionLayer` recusa).
- **Reações:** cortadas por uma ação, seguem em frente. Depois de uma saída autorada, o controle só volta quando agir dali
  é seguro (0,60 e 0,70 s, medidos). É o castigo de levar dano logo depois do próprio corte.

## 7. Vida, dano, morte e recomeço

- **Números:** herói 6 de vida (3 corações), fungo 6. Corte horizontal e diagonal tiram 1, o descendente tira 2, e a
  cabeçada tira 1.
- **Dano recebido**, nesta ordem:
  1. vida;
  2. reação (com a saída da fase, se for o caso);
  3. lampejo;
  4. recuo;
  5. pausa de impacto;
  6. invulnerabilidade;
  7. o controle volta depois do atordoamento.
- **Morte:** para de atacar, de ferir e de ser alvo. O herói ajoelha e, 1,6 s depois, aparece o painel "Você caiu",
  com **Tentar de novo** (Enter) ou **Voltar à vila**. O recomeço põe o herói na entrada da arena, o inimigo em casa, e
  os dois com a vida cheia.
- **Vitória:** uma mensagem; R recomeça. Não há perda de nada.

## 8. Defesa

- **Bloqueio:** a guarda bloqueia só se as três condições valem.
  - A subida terminou (0,12 s; quem ergue no último instante leva).
  - O golpe vem da frente (±65° do rumo do herói).
  - O herói está em guarda.
- **Bloqueado:** sem dano, pequeno recuo, impacto no broquel (`guard_hold`: segura → impacto → segura) e o fungo ricocheteia
  e abre a guarda. Pelas costas, leva.
- **Sem extras:** não há aparar, vigor nem atributos.
- **Pulos de tempo da guarda:** só caem em platôs parados da curva, onde pose e movimento secundário são iguais (teste).

## 9. Câmera durante a luta (refeita na 09.1)

> **Rodada 09.2:** o ajuste automático descrito nesta seção (viés de 35°, subida até 52°) deixou de ser o padrão e
> ficou em `-Dvx.camera=091`, só para comparar. A câmera de luta padrão é a mira livre ou travada da §16. A busca de
> vista, as distâncias e as taxas daqui continuam valendo com a mira travada e na composição da mira livre.

- **O que se mantém:** a câmera da rodada 08, com distância padrão de 4,6 u, órbita, linha de visão e desvio junto a
  paredes. Fora da luta ela é idêntica, passo a passo (`CombatCameraTest.explorationCameraIsUnchanged`, 13/14 capturas
  de exploração idênticas; só a pausa mudou, pelo volume).
- **Entrada do ajuste de combate:** suave, com um inimigo **engajado a menos de 4,5 u**. Continua sendo ajuste, não modo
  nem trava de mira:
  - o alvo anda 35% até o inimigo e 0,30 u para o lado do ombro.
- **Viés de 35° (09.1):**
  - com o mouse apontando o inimigo, a vista fica 35° ao lado, e o inimigo aparece à **direita** do herói;
  - é contínuo e crescente: todo movimento do mouse move a vista, com inclinação mínima de 1/2 e sem zona morta;
  - some quando o próprio jogador já deixou o inimigo de lado (−70° ou +60°).
- **O que o viés substituiu (rodada 09):** um giro em cone, em que, a menos de 35°, a câmera girava o que faltava, do
  lado mais perto. Dentro do cone o mouse não mexia a vista, e ao cruzar o meio o lado trocava, com a câmera varrendo 70°
  e passando com o fungo atrás do herói.
- **Inclinação:**
  - pelo menos 0,62 rad;
  - **sobe até 0,9 rad com o fungo colado** (de 1,7 u a 0,6 u, suave). A cabeçada acontece a 0,5–0,9 u, e aí nenhum
    giro lateral tira o fungo de trás da cabeça grande do herói: só olhar mais de cima.
- **Busca de desvio na luta (09.1):** avalia cada candidato **já como vai ficar** (viés + inclinação de combate +
  desvio):
  - **inclinações:** para cima (pedras) e **para baixo** (sob copas);
  - **pontuação:** distância livre, menor desvio e penalidade se o cenário esconde o inimigo;
  - **estabilidade:** só troca por ganho ≥ 0,5 u e segura a escolha por 0,4 s.
  - Na 09, a busca olhava a vista do jogador **sem** o giro e a inclinação de combate, somados depois: junto ao
    carvalho, a vista final entrava na copa e a câmera encostava a 0,96 u, com a cabeça tomando a tela
    (`round-09/camera/06`).
- **Na luta:**
  - desvio e volta mais rápidos (6/s e 5/s; exploração 3,5/s e 3/s);
  - distância mínima de 0,55 u em vez de 0,9 u, com o pivô fora da cabeça. O piso de 0,9 u punha o olho **dentro** do
    tronco do carvalho ou da pedra quando o herói encostava neles. O medidor confere que o olho nunca entra no herói.
- **Medição** (`CameraFraming`, sem OpenGL):
  - projeta herói e fungo pela câmera lógica;
  - mede quanto da tela o herói ocupa (domina acima de 20%) e quanto do fungo aparece (fora da tela, atrás do herói,
    atrás do cenário);
  - confere com capturas reais.
- **Evidência** (`CombatCameraTest`, `round-09-1/camera/`):

  | Medida | Rodada 09 | 09.1 |
  |---|---|---|
  | Varredura da arena (5.005 situações): herói dominando | 4,8% | 0,0% |
  | Varredura: fungo escondido | 1,8% | 0,1% |
  | Varredura: câmera dentro de algo | 14 casos | 0 |
  | 10 cenários em movimento: tempo ruim médio | 24,4% | 5,7% |
  | Os 9 casos reais (mesmo instante lógico): distância | 0,9 u no toco, 2,29 u na encosta | 4,6 u em todos |

- **O que ainda falha:** está nos problemas conhecidos (P37). Contornar o carvalho em movimento ainda tem cerca de 20%
  de tempo ruim, e o giro rápido de 360° do mouse junto à pedra, cerca de 20%.

## 10. Retorno visual e sonoro

| Recurso | Valor | Por quê |
|---|---|---|
| reação corporal + recuo | empurrão de 2,2 u/s (herói) e 1,8 u/s (fungo) | o golpe "empurra" |
| pausa de impacto | 0,07 s (acerto), 0,05 s (dano e defesa) | contato perceptível, sem travar |
| lampejo | 0,14 s, cor até 35% mais clara e levemente quente (`uFlash`) | quem foi atingido; sem flash agressivo |
| acento de impacto | 8 raios de pixel abrindo por 0,2 s no ponto de contato (quente no acerto, alaranjado no dano, aço na defesa) | onde |
| vinheta | bordas quentes por 0,35 s ao levar dano | o herói foi ferido |
| **som (09.1)** | corte no ar, acerto, bloqueio, alerta, preparação, cabeçada, dano, derrota, vitória/derrota, passos | ler as ações sem olhar; ver [audio.md](audio.md) |

- **Câmera:** sem tremor de câmera.
- **Som:** só lê a lógica depois de cada passo; a luta é idêntica com e sem som (teste).

## 11. Interface de combate

> **Rodada 09.2:** a barra grande do topo agora é só de chefe. Inimigo comum tem a barra pequena configurável (§19).

- **Canto superior esquerdo:** corações (meio coração = 1 de vida) e o broquel, aceso com a guarda erguida.
- **Alto:** nome e vida do inimigo só em luta ou com ele ferido.
- **Dica de controles:** perto da arena.
- **Painéis:** de derrota e a mensagem de vitória.
- **Não mostra:** métricas, mana nem inventário.
- **Pausa e conversa:**
  - a pausa congela o combate;
  - sem conversa durante a luta;
  - sem combate durante a conversa (clique não vira golpe; teste).
- **Modo técnico** (`./gradlew jogo -Ptecnico` + F3, ou `debug golpes on` nos roteiros):
  - hurtboxes em verde;
  - lâmina na janela de dano em vermelho;
  - cabeçada ativa em laranja;
  - estados e tempos.

## 12. Testes

> **Rodada 09.2:** mais 32 testes, somando 208 ao todo:
>
> | Classe | Testes | O que cobrem |
> |---|---|---|
> | `MobilityTest` | 15 | golpe andando, de lado e recuando; teto por fase; pé plantado; FPS; rolamento e recuo; invulnerabilidade; parede; spam; janelas; salto; coyote e buffer; props pisáveis |
> | `GroupCombatTest` | 16 | grupo independente; corte em dois; vez e espaçamento; morto inerte e recomeço; chefe; barras; preferências; mira (escolha, linha de visão, Tab pela tela, soltura, câmera travada sem giro brusco, WASD estável); câmera livre sem giro automático; herói travado andando de lado; morte em fragmentos; várias mortes |
> | `CameraModesTest` | 1 | as três câmeras nos 10 cenários em movimento da 09.1 |
>
> Os testes de câmera da 09.1 (`CombatCameraTest` e o de câmera do `CombatTest`) rodam agora explicitamente no modo de
> comparação 09.1 (`combatAssist`, perfil A).

- **`CombatTest` (20):**
  - janela de dano e acerto único;
  - fora de alcance, atrás e ao lado;
  - golpe rápido com alvo fino;
  - pedra barra o golpe;
  - encadeamento só com clique na janela; buffer;
  - percepção (frente, costas, audição, pedra);
  - preparação, dano e esquiva andando;
  - coleira e volta curado;
  - obstáculo (contorna ou desiste);
  - escudo (frente, costas, tarde demais);
  - reação, atordoamento e invulnerabilidade;
  - saída autorada;
  - morte e recomeço do inimigo e do herói;
  - FPS;
  - conversa × luta;
  - moradoras;
  - câmera de combate (separa, solta, obstáculos).
- **`CombatAnimationTest` (8):**
  - encadeamentos e dano no encadeamento;
  - liberações;
  - depois da reação, segundo dano e derrota;
  - platôs da guarda;
  - janelas só na frente;
  - kit nos três corpos;
  - camada ao vivo idêntica ao `Sequencer`.
- **`AnimationQaTest` (+3):**
  - jogáveis seguros a cada 0,01 s;
  - escolha da saída;
  - lâmina longa histórica.
- **`CreatorTest`:** invariante de descartes (seção 13).
- **`CombatCameraTest` (7, rodada 09.1):**
  - varredura da arena inteira;
  - 10 cenários em movimento contra a câmera da 09;
  - mouse sem zona morta;
  - exploração idêntica;
  - câmera igual em qualquer FPS;
  - filtro de raios exato;
  - o caso do carvalho da 09.
- **`CombatAudioTest` (13, rodada 09.1):** golpe errado sem som de acerto; um som por acerto, no passo do contato, e o
  "fuu" no meio da janela de dano; bloqueio ≠ dano; luta idêntica com e sem som e sons iguais em qualquer FPS; fungo morto
  calado e preparação cortada; pausa sem avalanche; recomeço sem som preso; cliques rápidos; passos; volume e mudo; sons
  gerados limpos; sem dispositivo, o jogo segue mudo.

## 13. Teste intermitente da 08.1 (`CreatorTest.rapidRequestsEndOnTheLastChoice`)

- **Defeito real de contagem:**
  - cada pedido novo tira da fila os pedidos antigos ainda não começados, mas esses descartes não entravam em `discarded`;
  - sob carga, a thread de composição não pegava nenhum pedido intermediário, `discarded` ficava 0 e a asserção falhava
    com o comportamento certo.
- **Correção:** contar só o que `queue.remove` **confirma** ter tirado da fila (é atômico).
- **Invariante sem tempo:** todo pedido termina exatamente uma vez, mostrado ou descartado. Ela falharia sempre sem a
  correção.
- **A invariante pegou duas corridas** das primeiras versões da correção; cada uma falhou 1 vez sob carga total:
  - medir o tamanho da fila antes e depois;
  - contar dentro do filtro do `removeIf`, que no `LinkedBlockingDeque` roda fora da trava.

  Nas duas, um pedido pego pela thread de composição no meio era contado em dobro.
- **Prova sob carga:** `round-09/testes/creator-sob-carga.txt`.
- **Sem afrouxar:** nenhuma espera nem limite de tempo mudou.


## 14. Mobilidade durante o golpe (rodada 09.2, ADR 0017)

O jogador continua dirigindo o corpo no golpe; a ação não prende mais o personagem.

| Peça | O que faz |
|---|---|
| teto por fase (`sensacao.perfis.X.golpe`) | velocidade desejada = entrada limitada a `golpe[fase] × andar`; o avanço autorado da execução soma por cima |
| giro por fase (`giroGolpe`) | preparação gira para a mira; execução pouco (a trajetória fica); continuação e recuperação viram para onde se anda (travado: para o alvo) |
| pernas na passada (`Motion.legW`) | com o corpo andando, as pernas seguem a passada real (fase pela distância → pé plantado); tronco, braços, quadril e espada são os do golpe validado |
| rumo da passada (`Motion.locoDir`) | de lado e de costas o pé anda ao longo do deslocamento (coxa abre; passada lateral encolhe; pés não cruzam) |
| lâmina | `Hero.bladeAt` avalia com as mesmas pernas do desenho |

**Perfis de sensação** (`combate_e.json → sensacao`; pausa "Sensação", `-Dvx.sensacao=`, preferência gravada):

| Perfil | andar / correr (u/s) | golpe: teto por fase (× andar) | giro por fase (rad/s) | o que é |
|---|---|---|---|---|
| A | 1,14 / 2,33 | 0 / 0 / 0 / 0 | 12 / 0 / 0 / 0 | rodada 09.1 (golpe parado; só o avanço autorado) |
| B | 1,45 / 2,90 | 0,50 / 0,25 / 0,40 / 0,70 | 12 / 2 / 4 / 8 | responsivo |
| C (padrão) | 1,75 / 3,50 | 0,65 / 0,35 / 0,55 / 0,85 | 14 / 3 / 6 / 10 | ágil |

- A velocidade nova vem sobretudo da cadência: as pernas do herói têm 0,24 u e a passada já estava no limite do alcance;
  a passada cresceu só 5%.
- **Medido** (`MobilityTest`, empurrando a entrada o tempo todo durante um corte de 1,12 s):

  | Perfil | Deslocamento |
  |---|---|
  | A | 0,54 u |
  | B | 1,23 u |
  | C | 1,63 u |

  - O mesmo vale de lado e recuando: com a mira travada, de costas, a 1,63 u.
  - **Pé de apoio** no golpe andando, perfil C:
    - mediana de **0,001 u/s** com a passada;
    - **1,34 u/s** com a base do golpe deslizando (o defeito que a passada evita).
- **Validação:** `viewer --check golpe-andando`. Cada golpe jogável, a guarda e o bloqueio por cima das pernas andando
  e correndo, em quatro rumos e quatro fases: 0 problemas.

## 15. Esquiva (rodada 09.2, ADR 0018)

> **Atual (09.3):** os números abaixo valem; o gesto com direção deixou de ser o rolamento `roll` e passou a ser o salto
> baixo R2 (`dodge_f`, lados `dodge_l`/`dodge_r`). Ver §20.

| | Rolamento (com direção) | Recuo (sem direção) |
|---|---|---|
| gesto | `roll` (0,56 s) | `backstep` (0,40 s) |
| deslocamento real | 1,9 u em 0,03–0,46 s | 0,95 u em 0,04–0,28 s |
| invulnerável | 0–0,32 s | 0–0,20 s |
| golpe a partir de | 0,44 s | 0,26 s |
| andar/guarda a partir de | 0,46 s | 0,30 s |
| corpo | vira para o rumo (30 rad/s) e rola para a frente | de frente, salta para trás |

- **Entrada:** Alt esquerdo ou botão do meio. Buffer de 0,3 s; recarga de 0,22 s depois do fim; sem stamina.
  Apertando sem parar, o herói fica invulnerável menos de 45% do tempo (teste).
- **Janelas medidas** (`esquiva.janelas`, `viewer --check esquiva`): de cada corte só se esquiva nos instantes em que
  cortar para rolar E para recuar passa no validador.

  | Golpe | Janelas (s) |
  |---|---|
  | slash_h | 0,01–0,27 e 0,68–1,09 |
  | slash_d | 0,01–0,30, 0,64–0,76 e 0,92–1,12 |
  | slash_v | 0,01–0,36 e 0,53–1,19 |

  - A execução fica sempre de fora.
  - Da guarda, a qualquer momento.
- **Por que `cutFade` 0,12 s:** com a transição de corte padrão (0,30 s), a mistura golpe × bola atravessava o punho no
  tronco em quase todos os instantes. Com a entrada rápida, só sobram os trechos acima.
- **Animação:**
  - canal `spin` (giro em torno do centro da bola, pelo caminho mais curto: 360° = 0°, sair não desenrola) e canal
    `footY` (pés recolhidos);
  - espada ao lado, alinhada ao eixo do giro;
  - contato com o chão pelos volumes do rig (o ponto mais baixo fica no chão; a cabeça grande não afunda).
- **Colisão:** a física continua. A invulnerabilidade protege de dano, não atravessa parede (teste).

## 16. Mira livre e travada (rodada 09.2, ADR 0020)

| | Livre (padrão) | Travada (Q) |
|---|---|---|
| rumo da câmera | só o mouse | gira para trás do herói em relação ao alvo (≤ 4 rad/s); mouse desloca ±0,40 rad |
| alvo na tela | onde o jogador puser | 35° de lado colado → 22° longe |
| inclinação | a do mouse | 0,50 rad (≈ 29°), até 0,70 (≈ 40°) com o alvo colado; nunca 52° (09.1) |
| pivô | +0,65 u de ombro na luta | entre herói e alvo + ombro (busca de vista da luta) |
| herói | vira para onde anda | de frente para o alvo (anda de lado/de costas); correndo, vira para onde corre |
| golpe | rumo da entrada + assistência 40° / 2,4 u | para o alvo |

- **Troca:** Tab (direita na tela) / Shift+Tab (esquerda), pela ordem angular em relação à vista.
- **Soltura:** Q; morte (passa ao engajado mais perto); além de 11 u; escondido por mais de 1,2 s.
- **Referência do WASD:** `FollowCamera.inputYaw` (mouse na hora + desvio automático a no máximo 2,5 rad/s). Soltar a
  mira ou um desvio grande da câmera não vira o controle de uma vez.
- **Ajuste automático da 09.1:** viés de 35° e subida a 52°, hoje só em `-Dvx.camera=091`, para comparar.

**Medido** nos mesmos 10 cenários em movimento da 09.1 (`CameraModesTest`, perfil C). "Ruim" = herói em mais de 20% da
tela ou fungo menos de 50% visível.

| Câmera | Tempo ruim | Câmera dentro de algo |
|---|---|---|
| 09.1 ajuste automático (até 52°) | 11,2% | 2 quadros |
| travada | 19,3% | 4 quadros |
| livre, jogador centralizando o fungo na tela | 37,2% | 56 quadros (P42) |
| livre, herói e fungo alinhados (o pior caso: fungo atrás da cabeça) | 58,6% | 89 quadros |

## 17. Vários inimigos e ensaio de chefe (rodada 09.2, ADR 0021)

- **Arquitetura:**
  - `Combat.enemies` é uma classe só para todos (`Enemy`), com números próprios por instância (`CombatDef.forSpawn`:
    variante + lugar);
  - **Encontros:** `um`, `grupo` (três fungos) e `chefe` (`fungo_anciao` + dois fungos), trocados pela pausa ("Arena")
    ou por `-Dvx.encontro=`.
- **Golpe:** a varredura da lâmina testa a hurtbox de cada inimigo vivo, no máximo uma vez por golpe. Dois na trajetória
  = dois acertos legítimos (teste com dois à frente e um atrás: 2 acertos, o de trás intacto).
- **Grupo:**
  - **Vez:** só os 2 mais perto do herói têm a vez de chegar ao alcance (ordem por distância).
  - **Ataques:** no máximo 2 preparando/atacando ao mesmo tempo, com 0,5 s entre começos.
  - **Espera:** quem espera fica no anel de 1,75 u.
  - **Corpos:** separação de 0,95 u entre corpos e colisão inimigo × inimigo.
- **Chefe:** `fungo_anciao` é o mesmo modelo × 1,6 (colisão, hurtbox, hitbox e alcance escalados), vida 16, um pouco
  mais lento e com preparação mais longa.
  - **Marcação:** chefe e protótipo, recurso TEMPORÁRIO de teste.
  - **O que fica de fora:** sem ataques novos nem fases.
- **Morto:** não pensa, não se move e não ataca. A vitória exige todos mortos.

## 18. Morte por desagregação voxel (rodada 09.2, ADR 0022)

1. **0–0,14 s:** reação ao golpe (pose de reação).
2. **Desagregação:**
   - o corpo some e vira os próprios blocos (`VoxelCloud`, grupos 2³, cor do modelo), cada um onde estava na pose;
   - a onda solta primeiro o lado do golpe (até 0,22 s);
   - impulso pequeno para fora e para cima, gravidade, quique baixo, giro;
   - cada bloco encolhe no seu tempo até 1,35 s.
3. **Limites:** no máximo 150 fragmentos por morte.
4. **Desenho:** uma malha dinâmica para todas as mortes (um desenho por quadro). Nada colide; nada fica no fim.
5. **D2 (padrão):** os blocos clareiam e até 7 sementes de luz emissivas sobem.
6. **Outros modos:** D1 só os blocos; `-Dvx.morte=antiga` traz a morte da 09 para comparar.
7. **Som:** `fungo_desfaz` no instante da desagregação.

## 19. Barras de vida e marcador de alvo (rodada 09.2)

| Quem | Barra | Quando |
|---|---|---|
| inimigo comum | pequena (78 px na escala 1080p), sem nome, sobre a cabeça | preferência: nunca / **ao levar dano ou estar travado** (padrão, some 3 s depois do último dano) / sempre (engajado) |
| chefe | grande no topo: nome, moldura dourada, ponteiras em losango, marcas de quarto, "protótipo de teste" | engajado ou ferido |

- **Esconde** a barra pequena quando o inimigo está atrás de sólido ou fora da tela. O fungo sozinho nunca vira chefe.
- **Preferência:** em `output/preferencias.json` (`-Dvx.prefs`), separada da aparência; pausa → "Barras de inimigos".
  Os roteiros e os testes não leem nem gravam.
- **Marcador do alvo travado:** quatro cantoneiras douradas fora do contorno do inimigo (não cobrem cabeça nem
  preparação) e um losango no chão.

## 20. Esquiva R2 (rodada 09.3, adendo ao ADR 0018)

A função não mudou: distância, invulnerabilidade, recarga e janelas são as da 09.2. Mudou o gesto desenhado. Depois de
comparar o rolamento da 09.2, o rolamento diagonal R1 e o salto baixo R2, **o proprietário escolheu o R2**. Os outros
saíram do perfil e estão em `docs/historico/esquivas-09-3/`.

A esquiva frontal é um **salto baixo** (o corpo se lança rente ao chão e pousa), **não um rolamento completo**: o corpo
nunca inverte, e a cabeça fica a mais de 0,15 u do chão (teste).

| Situação | Gesto | Corpo |
|---|---|---|
| com direção (mira livre, ou à frente/atrás do alvo travado) | `dodge_f` (salto baixo para a frente) | vira para o rumo |
| de lado com a mira travada (45°–135° do alvo) | `dodge_l` / `dodge_r` (salto lateral inclinado) | continua de frente para o alvo |
| sem direção | `backstep` (recuo, intocado) | de frente |

- **Configuração** (`combate_e.json` → `esquiva`):
  - `rolar.golpe` = `dodge_f` (o nome `rolar` ficou por compatibilidade);
  - `lados` = gestos laterais.
- **Escolha** (`Hero.startDodge`): o ângulo entre o rumo pedido e o alvo decide.
- **Som:** `esquiva_recuo` em todos os gestos (o R2 é salto, não rolamento). `esquiva_rolar` ficou sem uso.
- **Testes** (`DodgeJumpPolishTest`):
  - distância, invulnerabilidade e duração da 09.2;
  - lados encarando o alvo;
  - a cabeça nunca chega perto do chão (o defeito da 09.2);
  - poses do jogo seguras;
  - acessórios.
- **Validador:** `viewer --check esquiva` (recuo e os três gestos do R2).
- **Evidência:** `tools/review93.sh`, roteiros `tools/combate93/`.

## 21. Roteiros de reprodução (`tools/combate*/`)

Roteiros de entrada do jogo, versionados: teclas, cliques, tempos e posições, lidos por `game --roteiro ROTEIRO SAÍDA
QPS`. Não dependem da máquina (caminhos relativos à raiz) e rodam no binário atual. Os pacotes dos scripts `review*`
saem em `output/` (fora do git).

| Pasta | Script | Rodada | Hoje |
|---|---|---|---|
| `tools/combate/` | `tools/review9.sh` | 09: primeiro combate (aproximação, golpes, defesa, derrota, câmera, hitboxes) | histórico: roda no binário atual, então a esquiva gravada é a R2 |
| `tools/combate91/` | `tools/review91.sh` | 09.1: câmera de luta e som (`gerar-camera.sh` monta os casos) | histórico: a câmera 09.1 só com `-Dvx.camera=091` |
| `tools/combate92/` | `tools/review92.sh` | 09.2: mobilidade, mira, grupos, chefe, morte, barras | histórico: o `07-esquivas` mostra hoje a R2; a prancha "rolamento" usa o `dodge_f` |
| `tools/combate93/` | `tools/review93.sh` | 09.3: esquiva R2, saltos, câmera perto, demonstração | **atual** |

- **Comparações "antes":** os blocos `antes` do `review92.sh` e do `review93.sh` usam binários guardados em `output/tmp/`
  (`G091`, `G092`). Sem eles, o script avisa e pula a comparação.
- **Som:** os vídeos usam `-Dvx.som=loopback` (o som da própria aplicação, sem tocar nos alto-falantes). Os roteiros
  avulsos tocam mudo.
- **Exemplos:**
  - `tools/review93.sh videos`: os 9 vídeos da R2 com som;
  - `tools/review93.sh videos 09`: só a demonstração;
  - `game/build/install/game/bin/game --roteiro tools/combate93/06-saltos.txt output/tmp/x 30`.

