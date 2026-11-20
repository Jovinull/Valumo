# 0019 — Salto: física no passo fixo, coyote e buffer, props "pisáveis" por perfil, sem golpe aéreo nesta rodada (rodada 09.2)

**Status:** aceita (rodada 09.2). Detalhes: [jogo.md](../architecture/jogo.md) §salto.

## Contexto

Não havia salto: nem para a exploração (degraus, caixotes) nem para o combate (sair de uma cabeçada, mudar de altura).

## Decisão

1. **Espaço pula** (em exploração e em luta; não no meio de golpe, guarda ou esquiva).
   - Altura de 0,62 u.
   - Gravidade de 24 u/s² na subida e 34 na queda: cai mais rápido do que sobe, sem flutuar.
   - São cerca de 0,42 s no ar.
2. **Resposta:**
   - **Coyote:** 0,09 s depois de sair da borda ainda pula.
   - **Buffer:** um aperto até 0,12 s antes de pousar sai no pouso.
   - **No ar:** controle de 55% da aceleração (perfil C).
3. **Colisão:**
   - as formas existentes continuam bloqueando no ar;
   - um desnível maior que um degrau é parede também no ar, sem teleporte para cima de borda alta.
4. **Plataformas controladas:**
   - Só props marcados `"pisavel": true` em `colisao_e.json` têm topo pisável: caixote, tronco caído, toco, pedras pequena
     e média, barril.
   - Casa, poço, cerca, árvores e pedra grande continuam só paredes.
5. **Golpe no ar: não nesta rodada.** O clique no ar fica guardado e sai ao pousar. Não há animação aérea pronta com a
   qualidade das outras, e um corte aéreo ruim é pior que nenhum.
6. **Animação:** no ar os pés recolhem (o esquerdo mais) e o corpo inclina; ao pousar o quadril cede conforme a
   velocidade da queda (`Motion.air`, `Motion.land`). O tronco e a espada ficam no transporte.

## Consequências

- Sobe em caixote e toco. Não sobe em casa nem pula o poço.
- O som de salto e o de pouso saem dos contadores do `Player` (o som só lê).
- Corte aéreo, escalada e salto duplo ficam fora (pedido do proprietário: nada de voo nem salto duplo).

## Adendo — rodada 09.3: apresentação do salto (a física não mudou)

Altura (0,62 u), tempo no ar (~0,42 s), coyote (0,09 s) e buffer (0,12 s) continuam iguais. Só o desenho mudou.

1. **Impulso sem atraso:** a física sobe no mesmo passo do aperto
   (`DodgeJumpPolishTest.theJumpLeavesTheGroundOnTheFirstStepAndShowsTheTakeoff`). O agachamento aparece por cima dos
   primeiros 0,10 s do voo (`Player.takeoffPhase`, `Motion.takeoff`):
   - o quadril desce e volta em curva (1−u)²;
   - os pés empurram para trás.

   Na tela parece "agacha e salta", mas o comando não espera.
2. **Subida e ápice:** os pés recolhem e o tronco inclina, como na 09.2.
3. **Preparação da queda:** nos últimos 0,14 s antes do chão previsto (`Player.fallPrep`, `Motion.fall`), as pernas
   estendem e vão à frente, desfazendo o recolhimento.
4. **Pouso:** a absorção (`landDur`) varia com o impacto: 0,75× a 1,25× de 0,16 s, conforme a velocidade da queda (salto
   curto × queda de superfície alta). Correndo, é 35% mais curta e menos funda (`Motion.landRun`), e a velocidade
   horizontal não cai (`landingWhileRunningDoesNotStopTheRun`). Parado, os pés abrem.
5. **Prévia no visualizador:** `salto=parado|andando|correndo` reproduz a física do `Player` para pranchas de perfil.
   `salto=antigo-…` mostra a apresentação da 09.2 para comparação.

## Adendo — rodada 12.1: o salto que sobe nas coisas (a física mudou, a pedido do proprietário)

**Contexto:** no teste físico da rodada 12, o proprietário relatou:

- o salto parecia curto e pesado;
- não se subia em pedras pequenas onde daria para ficar em cima;
- quem pulava sobre uma parede quebrada da ruína afundava nela.

A referência pedida foi a dos jogos de exploração em voxel em que o salto é a ferramenta de andar pelo mundo, não só de
desviar na luta.

**Decisão** (números só em `combate_e.json → salto` e `sensacao.*.ar`):

| | 09.2–12 | 12.1 |
|---|---|---|
| altura | 0,62 u | **1,0 u**: com o degrau de 0,30 u no ar, alcança topos até 1,30 u |
| gravidade subida / queda | 24 / 34 u/s² | **18 / 26**: sobe em 0,33 s, desce em 0,28 s; continua caindo mais rápido do que sobe |
| tempo no ar | ~0,42 s | ~0,61 s |
| coyote / buffer | 0,09 / 0,12 s | **0,11 / 0,15 s** |
| controle no ar (perfil C) | 55% | **85%**: corrige o rumo no ar, dá para mirar o topo de uma pedra |

**A regra de suporte é a mesma para tudo** (`Collision.supports`):

- **Antes:** só o pisável segurava.
- **Agora:** todo sólido segura quem está acima dele, menos pessoa e objeto de missão.
- **Por quê:** um corpo para de empurrar quando o topo está a até um degrau dos pés. Quem pousava em cima de uma cerca ou
  de um muro baixo ficava num sólido que não empurrava nem segurava, e afundava.

**Apoio e borda, a física do corpo** (`Collision.support`, `Player.step`):

- **Apoio pelo corpo ao descer:** o herói fica de pé num topo NA ALTURA DOS PÉS OU ABAIXO enquanto o corpo (o círculo de
  0,26 u) ainda toca nele. Antes era só o centro: quem saía andando da borda de uma laje descia 0,375 u com o corpo
  ainda 0,26 u sobre ela e era jogado de lado no passo seguinte. Subir degrau continua pelo centro, como sempre.
- **A borda é parede na descida:** descendo ao lado de um sólido cujo topo ficou acima dos pés, sem o centro sobre ele,
  o sólido empurra no mesmo passo. O "degrau no ar" (o alcance de altura do salto + 0,30) vale subindo; descendo, ele
  deixava o corpo escorregar por dentro da lateral.
- **A conferência de alcance não muda:** continua com o apoio pelo centro, então o herói alcança pelo menos o que ela
  conta, e nenhuma região muda (v1: 300 sementes iguais; v2: 200 arranjos iguais).

**Medido:**

- `CompositeCollisionTest.quemPulaEmCimaDeUmSolidoBaixoNuncaFicaDentroDele`: em todo composto liberado, para cada um
  dos 377 sólidos com o topo ao alcance, quem pula para cima nunca fica mais de 2 mm dentro de um corpo. Com a física
  anterior, o teste acha 0,113 u dentro da laje da oficina.
- `CompositeCollisionTest.oPisoDosInteriores…`: por cada entrada, seguindo o caminho da grade com a física real, sobe-se
  pelos degraus até o meio do interior. Com a física anterior, a ruína a 90° não chega.
- `MobilityTest`: todo sólido segura o salto; pessoa nunca é plataforma; a casa fica fora do alcance.
- `CompositeCollisionTest`: a muralha de 1 u da torre se sobe com um salto andando.

**O que não muda:**

- golpe no ar, salto duplo e escalada continuam fora;
- a animação continua a da 09.3, sobre a física nova;
- a esquiva não muda;
- a câmera de exploração continua a da rodada 08 (a suavização de altura `visualY` é a mesma);
- desnível maior que o alcance continua parede.

**Consequências:**

- Sobe-se em pedra média, toco, tronco caído, caixote, cerca, mureta e no muro baixo das ruínas.
- Não se sobe em casa (o telhado fica fora do alcance) nem em árvore (o corpo do tronco tem a altura da árvore inteira).
- Os validadores de salto leem a janela do JSON (`DodgeJumpPolishTest`), nunca um número copiado.
- A referência das regiões (`RegionGoldenTest`) usa uma cópia congelada do combate da referência, porque o salto muda o
  JSON de combate e não muda nenhuma região.
