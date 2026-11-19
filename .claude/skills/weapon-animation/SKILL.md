---
name: weapon-animation
description: Criar ou alterar animações com equipamento (espada, escudo, lâmina longa, arco, cajado ou arma nova) na direção E — perfis de arma em assets/anim/*.json, poses por trajetória (mão + direção da ponta + fio), IK de braço, fases do golpe, transições, validação espacial (viewer --check), revisão multiângulo e testes. Use antes de mexer em qualquer golpe, guarda, transporte de arma ou item segurado. Rodada 09.2: canais spin e footY (rolamento), cutFade por gesto, golpe validado com as pernas andando (--check golpe-andando), esquiva (--check esquiva) e pranchas com pernas no viewer (pernas=walk:fase:rumo:passada).
---

# Animação de armas (direção E, rodadas 04–05.1)

Referências: `docs/art/guia-de-animacao.md` (seções 05.1, 05 e 04 — fonte única dos números), `docs/decisions/0011-coreografia-por-trajetorias.md`,
`viewer/.../Choreo.java` (formato), `Animator.java` (`actionLayer`, `solveArm`, `bowRig`), `WeaponCheck.java`.

## Regra de ouro

Uma pose diz **onde está a mão e para onde aponta a arma**, não ângulos. O golpe é uma trajetória que
você desenha; o braço é consequência (IK). Nunca "conserte" um golpe somando rotações no `Animator`.

## Procedimento

1. **Item no rig** (`tools/charlib_e.py` + `chars_e*.py`): VERTICAL, +Y = ponta, pivô no centro da mão
   (`(-7,13,1)` dir., `(7,13,1)` esq.), filho de `farm_r`/`farm_l`; volumes (`sword_volumes`,
   `staff_volumes`, `bow_volumes`, `buckler_volumes`…); `meta.weaponProfile`. Regere: `python3 tools/build_assets.py chars_e chars_e_study`.
2. **Perfil** `assets/anim/<arma>.json` (escrito à mão; único lugar dos números): `rest`, `carry_walk`,
   `carry_run`, `guard`, depois cada golpe. Reaproveite com `extends`/`merge` e `base`.
3. **Fases**: repouso → preparação → execução (`snap`) → continuação (`stop` no fim) → recuperação → guarda.
4. **Alcance**: `python3 tools/anim_reach.py <arma>` — nenhuma pose com `!` (braço 10,6 voxels).
5. **Validação** (três ferramentas, cada uma vê uma coisa):
   - `viewer --check` → zero falhas (nada se ATRAVESSA; o relatório traz mão e direção do pior instante);
   - `viewer --occlusion [rig golpe]` → rosto livre (< 25% ou ≤ 0,10 s) nas 8 câmeras e arma ≥ 60% visível
     nas câmeras de frente/3/4 em guardas e repouso (nada se ESCONDE);
   - `viewer --grip rig golpe` → sem "ITEM PARADO NO MUNDO" (a mão SEGURA o item). Δpunho é informativo
     (a amarração é arbitrária); valor alto → close em vídeo da mão (guarda acima do punho, pomo abaixo).
     Escolha o FIO do repouso que minimize a troca de empunhadura até a guarda (lâmina longa: fio para fora).
   Corrija a pose, nunca a tolerância.
6. **Olhe**: `tools/review4.sh armas` (5 ângulos × 8 instantes + câmera de exploração), vídeo a 30 e 60
   FPS e a meia velocidade, `dbg=1` (volumes, empunhaduras, trajetória). Validação geométrica ≠ aprovação.
7. **Testes**: `./gradlew test` (o `ChoreoTest` roda todos os golpes; acrescente um teste de intenção
   da trajetória para golpes novos, como `horizontalSlashSweepsAcrossTheFrontFromRightToLeft`).

## Armadilhas já encontradas (não repita)

- **Pomo na barriga**: girar a lâmina de "para trás" a "para a frente" pelo lado aponta o pomo para o
  tronco. Passe pela lâmina EM PÉ, ou mantenha a mão a x ≤ −5 e z ≥ 9,5 quando a ponta aponta para a frente.
- **Guarda cobrindo o rosto**: lâmina para cima-dentro na guarda não intercepta (validador passa), mas
  cobre o rosto de frente. Guarda média: ponta à frente na altura do peito, mão à direita.
- **Direções quase opostas entre chaves** (repouso para trás × guarda para a frente): a interpolação
  passa perto do vetor nulo e a ponta "salta" (o validador acusa "salto da ponta"). Mantenha < ~120°
  entre chaves vizinhas ou ponha uma chave intermediária.
- **Lâmina erguida acima da cabeça**: impossível com braços chibi sem cruzar o crânio; erga AO LADO
  (mão em x ≈ −11,5, y ≈ 28).
- **Duas mãos**: cabem só numa família assimétrica (tronco girado +20..+40°, mãos no peito esquerdo,
  lâmina à frente-direita) — `longblade.json` `guard_two_hands`; busca em `tools/twohand_search.py`. É
  propriedade da proporção atual, **não** impossibilidade geral. Arco: arqueiro de lado (ver guia).
- **Arma sumindo atrás do braço (falha real da rodada 05)**: o repouso da lâmina longa apontava para cima
  e PARA TRÁS (sobre o ombro); a saída da guarda girava a lâmina por trás do braço e da cabeça. O validador
  passava (nada atravessava). Regra: repouso de arma longa **lateral, em pé, por fora do ombro**; guarda ↔
  repouso pela FRENTE e por fora, nunca girando por trás do corpo. Meça com `--occlusion` (arma visível).
- **Item parado no mundo (falha real da rodada 05, arco no HIT)**: pose de reação herdando `lDir`/`lEdge`
  (ou `rDir`/`rEdge`) do repouso deixa o item congelado enquanto o braço gira — lê como "torceu na mão".
  Em toda pose em que o antebraço gira, autore a direção do item: `viewer --grip` imprime, por instante, a
  orientação "se seguisse o antebraço"; use ~80% dela (deixa folga ao punho, não trava o item na mão).
- **Lateral = pomo no tronco**: lâmina apontando para FORA (lado) põe o pomo dentro do tronco. Para girar
  entre trás e frente, passe pela lâmina em pé (pomo para baixo), não pelo lado.
- **Rosto coberto na volta**: a recuperação que ergue a arma diante da face (cajado na conjuração circular)
  passa no validador. Ponha uma chave de retorno pela lateral (`circ_return`).
- **Transições**: só pela `Sequencer`/Motion (controle), nunca interpolando Euler.

## Critérios de qualidade

Antecipação legível; peso (tronco e pés participam); trajetória ampla à frente do corpo; continuação;
recuperação suave; nenhuma arma pelo rosto, cabeça, chapéu ou tronco em nenhum dos 5 ângulos; mão sempre
na empunhadura; legível na câmera de exploração; nada de rastro para esconder movimento ruim.

## Rodada 05 — procedimentos acrescentados

- **Gestos HIT/EMOTE** são moves `hit`/`emote` do perfil (o `Animator` os usa no lugar do clipe FK
  quando existem). Todo perfil novo precisa deles; o `viewer --check` valida `clip:HIT` e `clip:EMOTE`.
- **Peso**: `hip` (agachar 0,83–0,90), `rootZ` (avanço −0,6..+2,8), `feet` (abertura) e `torso` amplos na
  preparação e no corte; segure a antecipação com uma chave `stop` repetida (~0,05 s).
- **Pulo/elevação**: canal `lift` (voxels) sobe o corpo inteiro; use só em gestos (emote).
- **Segunda variação de um golpe** precisa diferir na trajetória, não só no tempo: o `QualityTest`
  `secondStaffCastIsADifferentGesture` mede a distância média do cristal (> 5 voxels).
- **Geometria mudou** (ex.: lâmina mais larga) → atualize `*_volumes` em `charlib_e.py` para seguir a peça
  real, regenere, rode `--check`; se falhar, mova a pose (mão mais à frente/fora). Nunca a tolerância.
- **Duas mãos**: `GRIP_W` (`lMode: "grip"` ou `lGripW` 0–1) e `lGrip` (voxels ao longo do cabo); entrada e
  saída pela lâmina em pé bem à frente; o teste `longBladeTwoHandedGuardKeepsBothHandsOnTheHandle` cobre.

### Referências e aceite

Imagens: `docs/art/reference/10–12` (espada, arco, cajado); reprovados `r1`, `r3`, `r7`. Aceite:
`--check` TODOS OK · `./gradlew test` · vídeo em velocidade normal + câmera de jogo · 5 ângulos sem
cruzar rosto/cabeça/tronco · registro no relatório da rodada (o que mudou, números do validador).

## Entrada e saída de guarda (receita, rodada 05.1)

1. Desenhe a guarda (ex.: `guard_two`) e o repouso como poses de leitura clara em 4 câmeras.
2. Entrada: repouso → **ergue à frente** (mão à frente do ombro, item ainda em pé) → **à frente-direita**
   (tronco JÁ girado para a guarda) → **aproximação** (segunda mão chegando, `lGripW` 0) → guarda (`lGripW` 1).
3. Saída: **solta a segunda mão** primeiro (`two_release`, mesma pose da guarda com `lGripW` 0) → o mesmo
   caminho ao contrário. Nunca suba a lâmina diante do rosto nem gire por trás do braço.
4. Valide: `--check`, `--occlusion rig guard_two_hands`, vídeo em frente/perfil/costas/3-4 (`tools/review51.sh`).

## Transições entre estados

- Locomoção ↔ golpe e golpe ↔ golpe: `Sequencer` (`seq=`), nada de Euler. Roteiros de checagem em `Check.SEQS`.
- Interrupção obrigatória (HIT): `golpe!segundos` no roteiro. O golpe cortado anda pela PRÓPRIA trajetória
  por 0,30 s (rebobina na preparação; `"interrupt": "forward"` no golpe faz seguir em frente) e o HIT assume.
  Varredura: `viewer --check interrupcao`. Garantido hoje: nenhum teleporte; preparação sempre segura; arco e
  cajado seguros em qualquer instante. A espada foi resolvida na rodada 09 (0 cortes inseguros a cada 0,01 s: `viewer --check
  interrupcao-fina`); só a lâmina longa (18/47, não jogável) está em problemas-conhecidos.
- Cancelamento, prioridades e pausa de impacto do jogo: skill `combat`.

## Acessórios secundários

Partes `tail`, `hat`, `pack`, `scarf` recebem atraso de 0,09 s do tronco/raiz durante ações
(`Animator.secondaryLag`, sem estado). Valide: vídeo de costas e de frente com `-Dvx.secondary=false` × padrão
(`tools/review51.sh`, vídeos 10–13) e o teste `secondaryPartsMoveDuringActionsDeterministically` (limites
unilaterais: mochila só se afasta, cachecol só vai para a frente). Acessório fundido ao tronco: não simule.

## Rodada 06 — perfis sem combate (NPC) e o que continua aberto

- Um perfil sem ataque (NPC) usa a mesma estrutura: `rightItem` = objeto na mão (`tool`), `attack` = o gesto,
  move `emote`. O `viewer --check` valida o gesto e as transições; roteiros com HIT são pulados quando o perfil
  não tem `hit`. `tools/anim_reach.py` mede com o braço dos HERÓIS — não use para outro esqueleto.
- **Continua aberto (não corrigido):** interrupção por HIT atravessa o corpo em 7/21 cortes da espada e 18/47
  da lâmina longa (`viewer --check interrupcao`). Não produza armas novas com animação de combate nem inimigos
  que interrompam golpes até a rodada de combate.

## Rodada 09 — golpes que o jogo interrompe

- Golpe jogável = janelas `"interrupt"` no perfil (ritmo por fase + saídas autoradas) e 0 cortes inseguros em
  `viewer --check interrupcao-fina` (passo 0,01 s). Nunca torne o golpe imune a dano para esconder recorte.
- **Saída autorada:** começa na pose da fase cortada e volta pela recuperação já validada.
  - Ajuste por busca, com as duas exigências: o golpe sozinho e todos os cortes da janela.
  - Prefira um ponto cujos vizinhos passam.
  - Inclinar o tronco para trás leva os ombros: recue cabeça, quadril e raiz.
- **Pulos de tempo dentro de um golpe** (guarda segurada, impacto): só em platôs parados, a ≥ 0,09 s de uma chave em
  movimento. Senão, o movimento secundário salta.
- **Corte durante outra transição:** `Motion.fromMove2` (origem dupla), no `Sequencer` e no jogo.
- **Procedimento completo** (janela de dano, instantes medidos, testes): skill `combat`.

## Rodada 09.2 — gestos de corpo inteiro e golpe andando

- **Canais novos do Choreo:**
  - `spin` (graus): giro do corpo inteiro em torno do centro da bola (`spinCenter` do perfil). É interpolado pelo
    caminho mais curto: termine o gesto em 360 para sair sem desenrolar.
  - `footY` (voxels, 2 valores): pés recolhidos sem erguer o quadril.
- **O giro visível é `spin` + inclinação do tronco:** desconte o tronco ao autorar o `spin`.
- **Contato:** enquanto gira, o `Animator` mantém o ponto mais baixo dos volumes do rig no chão (`rollContact`). Não
  compense a altura à mão.
- **Espada num rolamento:** alinhada ao eixo do giro (para o lado). Nunca apontando para fora no plano do giro (afunda
  no chão) nem cruzando o tronco.
- **Antes de o punho virar:** a mão sai para o lado (pose `roll_pre`), senão o cabo atravessa o tronco.
- **`cutFade` (s) num gesto:** encurta a transição quando ele entra cortando outro. Esquivas usam 0,12; a padrão de
  0,30 s misturava golpe × bola demais.
- **Golpe andando:** as pernas vêm da passada (`Motion.legW`, `legClip`, `legT`, `legDir`).
  - Valide com `viewer --check golpe-andando` (quatro rumos × quatro fases, andar e correr).
  - Revise com prancha: `tools/snap.sh "mode=char,...,seq=idle:0.1+slash_h+idle:0.4,pernas=walk:0:90:1.05,t=0.45,tt=0"`.
  - **Use `tt=0`:** no modo personagem a câmera gira com o tempo.
- **Rumo da passada (`locoDir`):** de lado a coxa abre (`rz`); com rumo 0 e passada 1 a pose é a de sempre (gate dos
  presets).
- **Arma nova com mobilidade:** basta o perfil ter fases e o golpe passar em `--check`, `--check golpe-andando` e (se
  jogável) nas janelas de `--check esquiva`. Nada de mobilidade é específico da espada.

## Rodada 09.3 — canais de esquiva e salto

- **Esquiva atual:** R2 (`dodge_f/l/r`, sem giro). Os rolamentos saíram; as lições abaixo valem para uma esquiva
  com giro no futuro.

- **Canais novos:** `rollZ`, `feetX`, `spinPivot`, `spinAxis` e o `fadeIn` do gesto. Ver o guia de animação, seção
  "Esquiva e salto".
- **Rolamento:** use giro diagonal (`spinAxis`) com o pivô baixo. Meça a coluna (altura no meio do giro) e a folga da cabeça
  (a sonda que mediu o R1 na 09.3); com 2,4 cabeças, a cabeça encosta quando o corpo inverte. A R2 atual é coberta por
  `theR2DodgeNeverPutsTheHeadOnTheGround`.
  A lâmina passa
  por cima, com o pomo longe das pernas.
- **Clipping:** a Maga (robe) e o kit `adventurer_shield` são os piores casos de pomo × perna. Valide os presets com
  `DodgeJumpPolishTest.appearancesWith…`; não basta o modelo canônico.
- **Pranchas:** gesto em perfil, frente, três quartos e costas
  (`snap mode=char,…,seq=dodge_f+idle:0.6,myaw=90|0|40|180`). Para o salto, `salto=` (o `antigo-` é a 09.2).

### Receitas (09.3)

1. **Esquiva autorada:**
   - Crie poses `xx_load/push/air/land` no perfil e um gesto com `phases` (prep/exec/follow/recover).
   - Use os mesmos `cutFade` 0,12 e `fadeIn` 0,08 das esquivas, e a mesma duração da esquiva lógica (0,56 s). O
     `Sequencer` estica o gesto, mas a duração igual evita surpresa.
   - Registre o gesto em `combate_e.json` → `esquiva` (`rolar.golpe`, `recuo.golpe` ou `lados`) e em `Check.DODGES`.
2. **Contato visual com o chão:**
   - No rolamento, o contato é o volume mais baixo do rig (`rollContact`). Prefira ombro, costas e quadril; com a cabeça grande ela
     encosta por um instante quando o corpo inverte (aceito se não afundar).
   - O contato é o mais baixo de TUDO, itens incluídos: se a espada ficar por baixo, o corpo flutua apoiado nela.
     Role pelo lado oposto à mão da arma só se o item não for o mais baixo (medir).
   - Inclinação lateral (`rollZ`) levanta o corpo inteiro.
   - Meça com o `PoseProbe` do `DodgeJumpPolishTest`: corpo encostado (folga mediana < 0,02 u), cabeça sem afundar e
     altura do corpo no meio do giro (a coluna). Antes de afirmar "a cabeça não é o contato", sonde a peça mais baixa
     a cada passo.
3. **Pivôs de rotação:**
   - `spinPivot` [altura, frente] em voxels: centro do giro baixo e à frente (perto do ombro de contato).
   - `spinAxis` desvia o eixo para a diagonal.
   - Pivô alto vira "roda gigante"; eixo puramente sagital vira "bananeira".
4. **Variantes direcionais:**
   - Um gesto por lado (`dodge_l`/`dodge_r`) com `rollZ` e `feetX` espelhados. O lado do escudo e o lado da espada
     podem pedir mãos diferentes (o `dodge_r` precisou da lâmina mais à frente).
   - O jogo escolhe o gesto pelo ângulo ao alvo (`Hero.startDodge`).
5. **Preservar a física:**
   - Animação nunca altera distância, invulnerabilidade, recarga, altura, tempo no ar, coyote ou buffer.
   - O desenho lê fases (`Motion.takeoff/fall/land/landRun`) calculadas da física. Os testes de paridade conferem.
6. **Antecipação e pouso:**
   - Antecipação por cima do movimento já começado (a física sobe no primeiro passo; o corpo agacha nos primeiros
     0,10 s).
   - O pouso escala com o impacto e encurta correndo. O mesmo vale para golpes: antecipação nunca atrasa o comando.
7. **Validar acessórios:** compose os presets e as aparências com chapéu e capuz pelo `Composer` (kit
   `characters/r4/adventurer_shield`) e rode o `WeaponCheck` nos gestos e nas sequências de entrada e saída. A Maga (robe
   largo) é o pior caso de pomo × perna.
8. **Comparar movimentos:**
   - Mesmo estado e mesma câmera, lado a lado (`review93.sh animacao`: visualizador em 1× e 0,25×).
   - O mesmo roteiro em dois binários (`review93.sh antes`).
   - Para o teste físico, deixe as variantes trocáveis por propriedade só enquanto o proprietário compara. Escolhida
     uma, retire as outras e arquive-as em `docs/historico/` (foi o que aconteceu com a esquiva na 09.3).
9. **Evitar clipping nas transições:**
   - Valide as entradas (idle, walk, run → gesto), os cortes (golpe → gesto a cada 0,01 s) e as saídas (gesto → golpe,
     reação, guarda), todos em `--check esquiva`.
   - Quando um gesto não cabe em todo corte, dê janelas próprias e um substituto. Nunca aumente tolerância.

## Rodada 09.4 — arma × cabelo personalizado

O `--check` mede a cabeça como uma cápsula (raio 8,6): cabelo além dela não é visto. `viewer --check-cabelos` mede a
arma contra a cabeça COMPOSTA (voxels reais) em todo golpe, HIT, EMOTE e locomoção, para cada preset × penteado.
Achado: o EMOTE do aventureiro põe 21 voxels da espada dentro do topete canônico (t = 0,77 s). Ao mexer em qualquer
golpe ou gesto, rode também `--check-cabelos` e não aumente a penetração de nenhuma combinação.

### Rodada 09.4 — EMOTE corrigido e o que ele escondia

- **EMOTE** (comemoração): a lâmina erguida (`emote_up`) e a do pouso (`emote_land`) inclinam para FORA (−x) e um pouco
  para trás; no cajado, o bastão também abre no agachamento (`emote_crouch` do `staff.json`). Inclinar demais leva a
  ponta de baixo à perna, e abrir a mão demais estoura o alcance: varra poucos pontos e fique com o mais perto do
  original que passe nos três (cabelo, corpo, alcance).
- **Um defeito grande esconde os pequenos:** com o EMOTE em 0, o `--check-cabelos` mostrou o roçar do `slash_v` no ápice
  (1–2 voxels em penteados altos). `v_drive` foi 1 voxel à frente; `combo` tem pose própria (`cv_drive`). Depois de
  mexer num golpe jogável, rode `--check`, `interrupcao-fina`, `golpe-andando` e `esquiva` (a 1ª tentativa estourou o
  alcance e quebrou os quatro).
- **Cajado da maga:** o culpado era a pose de DESCANSO (todo gesto volta a ela). A inclinação do cajado gira em torno da
  mão: topo para fora leva a ponta de baixo à perna, e abrir a mão estoura o alcance. A saída foi a terceira direção
  (topo um pouco à frente: a ponta de baixo passa atrás da perna) e a empunhadura um pouco mais alta (mão mais perto do
  ombro = mais alcance).
- **Medição por gesto**, não só o pior instante: um gesto grave esconde os outros. A ferramenta de depuração precisa
  provar que rodou: `Val` com `idle` (em vez de `clip:IDLE`) lança exceção, e "nenhuma linha de FALHA" NÃO é aprovação.
  Conte as linhas OK.

