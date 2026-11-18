---
name: combat
description: Criar ou ajustar o combate do Valumo (módulo game, rodada 09 em diante) — novo golpe com janela de dano autorada, hitbox pela trajetória da arma (subamostras de 1/480 s) separada da hurtbox e da colisão, janelas de interrupção e saídas autoradas no perfil, instantes de encadear/liberar medidos pelo validador, reação a dano, defesa frontal, inimigo por máquina de estados (percepção, coleira, telegrafia), passo fixo de 1/120 s, testes de propriedade e evidências (tools/combate, tools/review9.sh). Use ao mexer em ataque, dano, defesa, inimigo, câmera de luta ou ao validar combate. Rodada 09.2: golpe móvel (teto por fase, pernas na passada), esquiva com janelas medidas, mira opcional (Q/Tab), vários inimigos (encontros, vez no grupo, um acerto por alvo por golpe), ensaio de chefe, morte por desagregação voxel e barras comum × chefe (tools/combate92, tools/review92.sh).
---

# Combate (rodada 09 em diante)

Arquitetura e números: `docs/architecture/combate.md`, ADR 0015 e `assets/scenes/combate_e.json` (escrito à mão).
Animação e validação de armas: skill `weapon-animation`. Movimento e câmera: skill `gameplay`.

## Regras que não se negociam

- **Dano só pela trajetória.** A pose da arma vem da animação, em subamostras ≤ 1/480 s, na janela `ativo`. Nunca:
  - colisão de movimento como acerto;
  - cone em frente ao herói;
  - posição da câmera.
- **Três volumes separados:** colisão (círculo), hurtbox (cápsula), hitbox (cápsula ou esfera presa a uma peça). Os
  `*_volumes` do rig são do validador, não de jogo.
- **Tudo em passos fixos** de `GameLogic.TICK`. Nada de lógica no desenho, nem `dt` de quadro na regra.
- **Transições:** toda transição que o jogo produz passa no validador de armas.
  - **Proibido:** aumentar tolerância, tornar golpe imune a dano para esconder recorte e suavizar a pose do herói no
    desenho.
- **Ao vivo = validado:** a camada ao vivo (`ActionLayer`) tem a mesma semântica do `Sequencer`. Mudou um, muda o outro
  e o `CombatAnimationTest.theLiveLayerMatchesTheSequencerFrameByFrame`.

## Criar um golpe novo

1. **Animação:** coreografia no perfil (`assets/anim/*.json`), com fases `prep`, `exec`, `follow` e `recover`. Siga a
   skill `weapon-animation`.
2. **Janela de dano:** meça a ponta na execução (ângulo em relação à frente), copiando o laço de
   `damageWindowsSitInsideTheExecutionAndOnlyWhileTheTipSweepsTheFront`. Escreva `ativo` em `combate_e.json`.
   - Fica dentro da execução.
   - Só com a ponta a até cerca de 80° da frente.
3. **Interrupção** (`"interrupt": [...]` no golpe). Rode `viewer --check interrupcao-fina`.
   - Preparação e execução costumam rebobinar (ritmo −2); a recuperação segue em frente (ritmo 1).
   - Onde os dois atravessam o corpo, crie uma **saída autorada** que comece na pose da fase e volte pela recuperação já
     validada (ex.: `hit_cross_h`). Ligue-a em `exits`.
   - Ajuste a saída com busca nos parâmetros e duas exigências ao mesmo tempo: o golpe sozinho e todos os cortes da
     janela. Prefira um ponto cujos vizinhos também passam.
4. **Instantes de jogo:** meça `encadeiaEm`, `libera` e o atordoamento depois da saída **com o validador**. Use
   sequências reais com o prefixo verdadeiro: `golpe!corte,hit!x,ação`, nunca a reação vinda do repouso se o jogo não faz
   isso.
   - Escolha com folga de 0,02 s.
5. **Teste:** acrescente o golpe ao `Check.PLAYABLE` e confira que `CombatAnimationTest` cobre as transições dele.

## Reação, defesa e morte

- **Reação** = `layer.start("hit")`: a saída autorada da fase entra sozinha.
  - **Atordoamento:** nunca menor que o instante seguro de agir depois daquela reação (`atordoadoSaida`).
  - **Invulnerabilidade:** ≥ todo atordoamento.
- **Defesa** (`guard_hold`):
  - esperas e pulos de tempo só em **platôs parados** da curva, a ≥ 0,09 s (atraso do movimento secundário) de uma chave
    em movimento;
  - bloqueia só de frente (`angulo`) e depois da subida (`subida`).
- **Morte:** a reação termina no repouso, onde a derrota começa. A troca acontece `DEFEAT_LEAD` antes do fim, e o corte
  é validado.

## Criar um inimigo

1. **Asset:** variante do gerador, com mudanças pequenas e justificadas, como o `fungo_cardo` em `creatures_e.py`.
   - Confira por hash que o canônico não mudou.
   - Rode `asset_audit`.
2. **Animação:** de combate no `Animator`, com estados por clipe. ATTACK em tempo normalizado (preparação, golpe,
   recuperação). Os tempos reais são da lógica.
3. **Lógica:** máquina de estados em `vx.game` (modelo: `Enemy`).
   - **Percepção:** distância + campo + linha de visão.
   - **Coleira** em volta de casa.
   - **Ataque telegrafado:** mira trava antes do golpe.
   - **Recuperação** = janela do jogador.
4. **Hitbox:** presa a uma peça, avaliada pela animação em subamostras. Nunca ataca através de sólido.
5. **Contrato:** novo tipo de `anim` → `contrato-de-assets.md` + `AssetContractTest`.

## Câmera de luta (rodada 09.1)

- **Peças:**
  - **viés contínuo de 35°**, de um lado só (`FollowCamera.biasedAngle`): o mouse sempre move a vista, sem zona morta
    e sem troca de lado;
  - **inclinação até 0,9 rad com o fungo colado** (1,7 → 0,6 u);
  - **busca de desvio que avalia a VISTA FINAL** (viés + inclinação + desvio, com inclinação para baixo sob copas),
    com histerese de 0,5 u e 0,4 s;
  - **piso de 0,55 u na luta.**
- **Mudou um parâmetro?** Rode `CombatCameraTest` (varredura da arena + 10 cenários em movimento contra `legacy09`) e
  `tools/review91.sh camera` (9 casos reais, antes no binário da 09 e depois). Nada de posições fixas favoráveis.
- **Diagnóstico:** `VX_TRACE="3 carvalho" ./gradlew :game:test --rerun --tests '*CombatCameraTest.moving*' -i` imprime
  o cenário quadro a quadro (ângulo do mouse, viés, desvio, distância, o que bloqueia).
- **Armadilhas:**
  - giro de combate calculado sobre o desvio suavizado (a caminho): o lado trocava no meio e a busca vaivinha. Use o
    desvio-alvo;
  - busca com todos os candidatos ruins (encurralado): sem tempo mínimo de escolha, ela pula de lado a cada passo.

## Som (rodada 09.1)

Skill `audio`. No combate: o diretor `CombatAudio` só LÊ o confronto depois de cada passo. Golpe novo = som de "fuu"
com o pico no meio da janela `ativo` (automático, pelo pico do arquivo) e acerto pelo `Combat.impacts`. Inimigo novo =
sons próprios no diretor + teste de correspondência.

## Validar

```bash
./gradlew test                                              # CombatTest, CombatAnimationTest, AnimationQaTest, CombatCameraTest, CombatAudioTest
viewer/build/install/viewer/bin/viewer --check              # todos os golpes (inclui saídas e derrota)
viewer/build/install/viewer/bin/viewer --check interrupcao-fina
tools/review9.sh videos angulos                             # vídeos reais (roteiros em tools/combate/) e 4 ângulos
```

Comandos de roteiro do combate:
- `atacar`, `defesa on|off`;
- `esperar-inimigo ESTADO máx [extra]`, `encarar`;
- `posicionar x z rumo` e `inimigo x z rumo [ESTADO]` (montagem técnica de cena);
- `debug golpes on`, `debug camera-combate off`;
- `eventos on`, `combate`.

## Armadilhas já encontradas (não repita)

- **Janela de dano = fase de execução inteira:** o corte horizontal começa a execução com a lâmina ainda ATRÁS do herói
  e feria quem estava atrás e ao lado.
- **Encadear cedo demais** (h → d aos 0,60 s): a lâmina cruza a cabeça para ir ao início do diagonal. Medir, não supor.
- **Validar a saída autorada vinda do repouso:** acusa recortes que o jogo nunca produz. Use o prefixo real.
- **Pular de tempo dentro de um golpe** onde a curva anterior se movia: o movimento secundário (mochila, cachecol)
  salta.
- **Fim natural numa avaliação adiantada:** o tempo que passou do fim tem de contar na saída (`ActionLayer.update`).
- **Câmera atrás do herói** com inimigo baixo a menos de 1 u: o herói o cobre inteiro. O ajuste de combate gira pela
  linha de vista; deslocar o alvo não basta, porque não cria paralaxe.
- **Rumo suavizado da câmera** usado na intenção sem a câmera no passo fixo: a luta mudava com o FPS.

## Rodada 09.2 — mobilidade, mira, grupos e morte (ADRs 0017–0022; `combate.md` §14–19)

**Fonte de verdade dos números:** `assets/scenes/combate_e.json` (`sensacao`, `esquiva`, `salto`, `mira`, `grupo`,
`variantes`, `encontros`, `interface`). Nenhum número desses no código.

### Golpe móvel (ADR 0017)
- O teto de velocidade e o giro de cada fase vêm do perfil de sensação (`golpe`, `giroGolpe`). Um golpe novo só precisa
  de `phases` corretas no perfil (`prep`/`exec`/`follow`/`recover`).
- As pernas seguem a passada sozinhas (`Hero.updateLegs` → `Motion.legW`). Nunca autore pé andando dentro do golpe.
- **Validar:**
  - `viewer --check golpe-andando` (acrescente o golpe em `Check.movingAttacks` se for jogável);
  - `MobilityTest` (deslocamento por fase, pé plantado, FPS).

### Esquiva (ADR 0018)
- Golpe novo jogável: meça as janelas de onde se pode esquivar.
  - Rode o laço de `Check.dodges` (ou `output/tmp/r92/dbg/Windows.java` como modelo): cortar para `roll` e para
    `backstep` a cada 0,01 s, nos dois kits.
  - Copie as janelas seguras (fora da execução) para `esquiva.janelas.<golpe>`.
  - Nunca chute janela. Nunca aumente tolerância.
- `viewer --check esquiva` precisa terminar "segura".
- A invulnerabilidade é janela do ESTADO lógico (`Hero.evading`), não de quadros; a colisão continua.

### Mira opcional (ADR 0020)
- Inimigo travável = vivo, no alcance e com linha de visão (`Targeting.valid`). Para outro tipo de inimigo travável,
  basta ser `Enemy` (a mesma classe).
- **Ordem do Tab:** ângulo na tela. Não ordene por lista.
- **Câmera travada:** `FollowCamera.lockOn` + `combatFocus`. Meça com `CameraModesTest` (os 10 cenários em movimento) e
  com `CameraFraming`. Nunca valide câmera só parada.

### Vários inimigos (ADR 0021)
- **Encontro novo:** em `encontros` (variante, x, z, rumo). **Variante nova:** em `variantes`, com campos de `inimigo`
  trocados; `escala` multiplica modelo, colisão, hurtbox, hitbox e alcance.
- A lâmina testa CADA inimigo vivo uma vez por golpe (`Enemy.struckBy`). Não crie dano em área.
- **Grupo:** `Combat.hasTurn` (os N mais perto), `mayAttack` (vagas + intervalo) e `separation` (espaço). Teste em
  `GroupCombatTest.theGroupTakesTurnsSpreadsOutAndNeverStacks`.

### Chefe × comum
- `chefe: true` na variante dá a barra grande (`GameLogic.bossBar`). Comum tem a barra pequena pela preferência
  (`GameLogic.smallBar`).
- Protótipo de teste: `prototipo: true` e "(protótipo)" no nome. Nunca apresente um inimigo ampliado como chefe
  artístico.

### Morte (ADR 0022)
- `DeathFx` (lógica) + `VoxelCloud` (blocos do rig) + `DeathFxMesh` (uma malha dinâmica).
- Para outra criatura não há nada a fazer: ela usa o próprio rig.
- **Ajustes:** em `DeathFx` (impulso, onda, duração, `MAX`). **Teste:** em `GroupCombatTest` (blocos do modelo, chão,
  fim, determinismo, várias mortes).
- **Comparar:** `-Dvx.morte=antiga|D1|D2`.

### Reproduzir
```bash
tools/review92.sh [videos [NN]|comparacoes|animacao|capturas|desempenho|testes|validadores|regressao|zip]
game/build/install/game/bin/game --roteiro tools/combate92/16-tres-inimigos.txt output/tmp/x 30
```
- **Comandos de roteiro novos:**
  - `segurar`/`soltar TECLAS`, `esquiva [meio]`, `pular`, `mira`, `trocar [anterior]`;
  - `encontro`, `sensacao`, `barras`, `morte`;
  - `inimigo #N x z rumo [ESTADO]`, `grupo`.

## Rodada 09.3 — esquiva com identidade (adendo ao ADR 0018; `combate.md` §20)

- **Gesto ≠ função.** Distância, invulnerabilidade, recarga e janelas ficam em `esquiva`. O gesto desenhado é o
  `golpe` de `rolar`/`recuo` e os `lados`. Nunca mexa nos números para acomodar uma animação.
- **A esquiva é a R2** (decisão do proprietário): `dodge_f`, `dodge_l`/`dodge_r` e o `backstep`. O rolamento da 09.2 e o
  R1 estão em `docs/historico/esquivas-09-3/`.
- **Gesto novo de esquiva:**
  1. Adicione o gesto em `Check.DODGES`.
  2. Rode `viewer --check esquiva` (cortes a cada 0,01 s, saídas, entradas parado, andando e correndo).
  3. Se ele não couber em todas as janelas, ajuste a pose. Na 09.3 o R1 precisou de janelas próprias e de um substituto,
     e saiu: um gesto que não cabe nas janelas do jogo é um gesto pior.
  4. Teste as variantes lado a lado com o proprietário antes de escolher.
- **Lados com a mira travada:** o gesto lateral mantém o rosto no alvo (`dodgeTurns = false`). Para isso existe
  `lockedSideDodgesStayFacingTheTarget…`.
- **Prova no jogo:** `DodgeJumpPolishTest` avalia a pose DESENHADA no jogo a cada passo:
  - arma e escudo × corpo, pelas tolerâncias do `WeaponCheck`, sem afrouxar;
  - o chão, comparado com os pés andando;
  - os saltos da ponta da espada;
  - os presets e as aparências com chapéu e capuz.
- **Evidência:** `tools/review93.sh` e roteiros `tools/combate93/`.
