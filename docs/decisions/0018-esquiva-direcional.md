# 0018 — Esquiva direcional: rolamento e recuo como gestos do perfil, invulnerabilidade no estado lógico (rodada 09.2)

**Status:** aceita (rodada 09.2). Detalhes: [combate.md](../architecture/combate.md) §15.

## Contexto

Sem esquiva, a única defesa era o escudo, e a única saída de perigo era andar. O pedido: esquiva com função física
(deslocamento real, colisão) e de combate (janela de invulnerabilidade), bonita no voxel, sem atravessar o corpo com a
espada e sem virar um giro de 360° do modelo inteiro.

## Decisão

1. **Entrada:** Alt esquerdo ou botão do meio do mouse.
   - Com direção (WASD, relativa à câmera; com a mira travada, relativa ao alvo pela própria câmera): **rolamento** para
     aquele lado. O corpo vira rápido (30 rad/s) para o rumo da esquiva e rola para a frente.
   - Sem direção: **recuo** de frente (salto curto para trás).
2. **Os gestos são golpes do perfil** (`roll`, `backstep` em `assets/anim/sword.json`), com poses de mão, direção da
   lâmina, tronco, quadril e pés. Assim o validador de armas os vê.
   - **Rolamento:** dois canais novos no Choreo — `spin` (giro do corpo inteiro em torno do centro da bola, interpolado
     pelo caminho mais curto) e `footY` (pés recolhidos sem erguer o quadril).
   - **A espada vai para o lado, alinhada ao eixo do giro.** Gira em torno de si mesma e nunca cruza o corpo nem o chão.
   - **Contato:** enquanto gira, o ponto mais baixo dos volumes do rig fica exatamente no chão, como uma bola; nem a
     cabeça grande afunda nem o corpo flutua.
3. **Deslocamento real:**
   - curva de velocidade (sobe, fica, desce) cuja área é a distância autorada (1,9 u no rolamento, 0,95 u no recuo);
   - colisão normal (desliza na parede, não atravessa).
4. **Invulnerabilidade na janela autorada** (0–0,32 s do rolamento, 0–0,20 s do recuo), em passos lógicos.
   - Um ataque que chega nela é "esquivado". A colisão física continua.
   - Começa em 0 também por um motivo técnico: cortar o próprio rolamento antes de 0,24 s não é seguro no validador, e
     a janela cobre esse trecho.
5. **Sem stamina.**
   - Há uma recarga de 0,22 s depois do fim e um buffer de 0,3 s para o aperto.
   - Medido: apertando sem parar, o herói fica invulnerável menos de 45% do tempo.
6. **Transições medidas, não chutadas** (`viewer --check esquiva`).
   - **Entrada do corte:** a esquiva entra rápida (`cutFade` 0,12 s, campo novo do gesto). Com os 0,30 s padrão, a
     mistura golpe × bola atravessava o punho no tronco.
   - **Janelas por golpe** (`esquiva.janelas`): cortar para rolar e para recuar passa no validador nos dois kits, a
     cada 0,01 s.
   - **Execução:** fica de fora por regra.
   - **Guarda:** pode esquivar a qualquer momento.
   - **Saídas:** golpe a partir de 0,44 s, reação ao fim da invulnerabilidade, guarda a partir de 0,46 s.

## Consequências

- O jogador sai de cercos e de cabeçadas. Bloquear e esquivar são escolhas diferentes.
- Novos golpes ganham esquiva automaticamente depois de medidas as janelas: rodar `viewer --check esquiva` e copiar as
  janelas. Ver a skill `combat`.
- Limite: há uma única animação de rolamento (para a frente, com o corpo virado para o rumo). Com a mira travada, rolar
  de lado vira o corpo de lado por meio segundo. Rolamentos laterais próprios ficam para depois.

## Adendo — rodada 09.3: esquiva R2 (decisão) e registro da comparação

### Decisão (vigente)

**A esquiva é a R2.** Para a frente (e para trás, ou na mira livre) sai `dodge_f`, um salto baixo que nunca inverte o
corpo. De lado com a mira travada saem `dodge_l`/`dodge_r`, de frente para o alvo.

- O rolamento da 09.2 (`roll`) e o R1 (`roll_r1`) saíram do perfil. As poses estão em
  `docs/historico/esquivas-09-3/esquivas-removidas.json`.
- Saíram também os estilos (`-Dvx.esquiva`) e as janelas por gesto, que só o R1 precisava.
- Os canais `spin`, `spinPivot` e `spinAxis` continuam no `Choreo` (sem uso hoje) para uma esquiva futura.
- Nada de função mudou; `--check esquiva` continua "segura".

### Registro da comparação (não é o estado atual)

O limite acima foi resolvido sem mexer na função da esquiva. Distância (1,9 u), duração (0,56 s), invulnerabilidade
(0–0,32 s), recarga (0,22 s), recuo (0,95 u, 0–0,20 s) e janelas gerais continuam exatamente os da 09.2
(`DodgeJumpPolishTest.everyDodgeStyleKeepsTheSameDistanceInvulnerabilityAndDuration`).

1. **Diagnóstico do rolamento da 09.2.** No meio do giro o corpo virava uma coluna invertida ("bananeira"). Quatro
   causas se somavam:
   - o giro rígido no eixo sagital, por cima da inclinação do tronco;
   - a cabeça, que era o ponto mais baixo e por isso o contato com o chão;
   - o eixo da espada saliente;
   - a subida mecânica no fim.
2. **Duas alternativas, construídas e comparadas:**
   - **R1** (`roll_r1`): rolamento diagonal pelo ombro, que termina com um pé à frente. É mais baixo que o da 09.2 (0,68 ×
     0,78 u de altura média no meio do giro), mas a cabeça continua encostando por ~0,13 s sem afundar: com 2,4
     cabeças de altura, ela é a superfície da "bola" sempre que o corpo inverte. A coluna diminuiu, não sumiu.
   - **R2** (`dodge_f`, `dodge_l`, `dodge_r`): salto esportivo baixo. Para a frente é um mergulho curto. Para os lados é
     um salto inclinado que continua encarando o alvo.
3. **Estilos (removidos depois da decisão)** (`esquiva.estilos` em `combate_e.json`, `-Dvx.esquiva=09.2|R1|R2|R1+R2`):
   - cada estilo mapeia frente/esquerda/direita em gestos;
   - padrão técnico: **R1+R2**, ou seja, R1 para a frente (mira livre, corpo virado para o rumo) e R2 para os lados com
     a mira travada;
   - para trás com a mira travada vale o gesto da frente virado (corpo vira por meio segundo, como na 09.2).
4. **Janelas por gesto (removidas com o R1)** (`esquiva.janelasPorGesto`): o R1 tem janelas próprias mais estreitas, medidas pelo validador.
   No fim de `slash_h` e de `slash_d`, o giro pelo ombro passaria a lâmina pela cabeça. Fora dessas janelas, mas dentro
   das gerais, o jogo usa o **substituto** medido (`dodge_f`). A decisão de jogo não muda; só o gesto desenhado.
5. **Canais novos do perfil** (`Choreo`):
   - `rollZ` (inclinação lateral do corpo inteiro; o corpo sobe o quanto o pé do lado da inclinação desceria);
   - `feetX` (afastamento lateral dos pés);
   - `spinPivot` (centro do giro);
   - `spinAxis` (eixo do giro desviado do sagital, para o rolamento diagonal);
   - `fadeIn` por gesto (a esquiva entra em 0,08 s a partir da locomoção, para a ponta da espada não saltar).
6. **Validação:** `viewer --check esquiva` cobre os seis gestos, cada um com cortes a cada 0,01 s dentro das próprias
   janelas, saídas e entradas parado, andando e correndo. `DodgeJumpPolishTest` cobre:
   - as poses do jogo (não só as do perfil), com mira livre e travada, nos 5 rumos;
   - os presets e as aparências com chapéu e capuz;
   - o contato do R1 com o chão.

A escolha estética era do proprietário; R1+R2 era só a recomendação técnica na entrega.
