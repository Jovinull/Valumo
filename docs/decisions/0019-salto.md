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
