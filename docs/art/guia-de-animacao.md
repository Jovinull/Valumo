# Guia de animação (direção E)

Animação procedural por partes rígidas (ADR 0003), em `viewer/.../Animator.java`. Clipes: `IDLE`,
`WALK`, `RUN`, `ATTACK`, `HIT`, `EMOTE`; exagero da E = 1,15. Evidências reais (MP4 + GIF) em
`output/visual-review/round-02/videos/` e `round-03/videos/`; quadros em `round-03/animacoes/`.

## Rodada 06: animação dos assets novos

| Asset | Como anima | Validação |
|---|---|---|
| Cartógrafa (NPC) | locomoção procedural dos heróis (`biped2`, pivôs do próprio rig: corpo mais alto funciona sem código novo); abas do casaco = `skirt_l/r` (78% da perna); tubo de mapas = `pack` (atraso secundário); gesto característico = move `emote` do perfil `cartografa.json` (consulta a bússola com as duas mãos, cabeça inclinada; depois aponta para o lado esquerdo com o braço estendido e o olhar). Sem ataque, sem HIT de combate. | `viewer --check` (o NPC entra em `Check.RIGS`); roteiros com HIT são pulados para perfis sem `hit`; `ExpansionTest.npcAnimationsPassTheSpatialCheckAndHaveNoCombat` |
| Mariposa | tipo novo `flutter` (`Animator.flutter`): asas modeladas planas, erguidas em V (0,72 rad) e com a corda inclinada (0,45 rad) para o desenho aparecer de frente; batida 1,1 Hz pairando, 1,75 Hz voando (deslocamento em oito, sem IA); susto (HIT) = asas PARAM e sobem num gesto único; volta curiosa (EMOTE) = giro completo | `ExpansionTest.mothWingsBeatSlowlyEnoughNotToFlicker`: a 30 fps, ≥ 5 quadros entre inversões e ≤ 2,6 batidas/s, medidos em relação ao corpo. A 1ª versão do susto somava estalo + batida e cintilava (inversões a 1 quadro) |

Limites registrados: `tools/anim_reach.py` usa o braço dos HERÓIS (10,6 voxels) e não vale para o NPC (braço
~11,5); para o NPC, o alcance é verificado pelo `viewer --check`. Alvos de mão no limite exato do braço
(11,4 de 11,55) falham com qualquer flexão de quadril: deixe o braço levemente dobrado.

## Rodada 05.1: o que o validador não via

O validador espacial prova que nada se **atravessa**; três falhas da rodada 05 passavam nele. Agora há
ferramentas para cada uma, e os critérios estão na skill `weapon-animation`.

| Falha (rodada 05) | Causa medida | Correção | Ferramenta |
|---|---|---|---|
| Lâmina longa "entra na silhueta" no fim da guarda de duas mãos | o repouso segurava a lâmina para cima e **para trás** (sobre o ombro): a saída girava ~120° por trás do braço e da cabeça; de 3/4 a lâmina ficava 59% escondida no próprio repouso; de frente-direita cobria 37% do rosto por 0,52 s | repouso **lateral em pé**, por fora do ombro, inclinado para fora e com o fio para fora (menor troca de empunhadura até a guarda) (`rest`, `carry_run`); entrada e saída **por fora e pela frente**: repouso → `two_front` (ergue à frente) → `two_low` (frente-direita, tronco já girado) → `two_reach` (mãos se aproximam) → `guard_two`; saída: `two_release` (solta a esquerda) → o mesmo caminho ao contrário | `viewer --occlusion` (rosto ≤ 22%, lâmina ≥ 77% visível nas câmeras de frente e 3/4) |
| Arco "vira de lado na mão" no HIT | `hit_recoil`/`hit_hold` herdavam `lDir`/`lEdge` do repouso: o arco ficava **parado no mundo** (giro 0°) enquanto o antebraço girava 50° em volta dele | o arco acompanha 80% do giro do antebraço (punho varia 13,5° em vez de 50°) | `viewer --grip rig golpe` |
| Cajado cobrindo o rosto na concentração e na volta da conjuração circular (câmera −40°: 41% por 0,41 s) | cristal à frente do rosto; retorno subia o cajado diante da face | cajado inclinado para fora (`align`, `focus`); volta pela lateral (`circ_return`) | `viewer --occlusion` |

| Recurso novo | Onde | Uso |
|---|---|---|
| Sonda de empunhadura | `GripProbe` (`viewer --grip rig golpe [passo]`) | torção/flexão do punho, Δpunho desde o início do golpe, giro do item (°/s), alerta "ITEM PARADO NO MUNDO" e a orientação que acompanharia o antebraço. Critério: só o alerta reprova; Δpunho alto pede close da mão (golpes aprovados da espada chegam a 113°, porque a amarração item/antebraço é arbitrária) |
| Oclusão em projeção | `OcclusionCheck` (`viewer --occlusion [rig golpe]`) | 8 câmeras: rosto coberto (alerta ≥ 25% por > 0,10 s) e % visível da arma |
| Movimento secundário | `Animator.secondaryLag` (`-Dvx.secondary=false` desliga) | atraso de 0,09 s do tronco/raiz, sem estado: trança (`tail`), chapéu (`hat`), mochila (`pack`, só se afasta das costas) e pontas do cachecol (`scarf`, só para a frente) |
| Interrupção por HIT | `Sequencer` (`golpe!segundos`), `"interrupt": "forward"` no perfil | o golpe cortado anda pela PRÓPRIA trajetória por 0,30 s (rebobina na preparação; segue em frente onde o perfil pede) enquanto o HIT assume; varredura: `viewer --check interrupcao` |
| Perfis alternativos | `-Dvx.anim=pasta` | comparação antes/depois com o mesmo binário |

**Acessórios sem articulação** (cabelo do aventureiro, aljava, mochila e cachecol das outras heroínas,
bolsas): fazem parte do tronco ou da cabeça e não balançam. Separá-los exige remodelar o rig; o cabelo é uma
massa única (H2) e não deve virar mola.

## Rodada 05: polimento da coreografia

| Recurso | Onde | Uso |
|---|---|---|
| Gestos HIT/EMOTE coreografados | moves `hit`/`emote` em cada perfil; `Animator` (gesture → move) | reação a dano e comemoração com a arma sem cruzar a cabeça; validados por `--check` (`clip:HIT`, `clip:EMOTE`) |
| Canal `lift` | `Choreo.LIFT` | sobe o corpo (voxels) — pulo do emote |
| Canal `spin` (09.2) | `Choreo.SPIN` | giro do corpo inteiro (graus) em torno do centro da bola (`spinCenter`) — rolamento; caminho mais curto (termine em 360) |
| Canal `footY` (09.2) | `Choreo.FOOTY` | pés recolhidos (voxels, esq./dir.) sem erguer o quadril — rolamento, recuo |
| Campo `cutFade` do gesto (09.2) | `Choreo.Move.cutFade` | transição mais curta quando o gesto entra cortando outro (esquiva: 0,12 s) |
| Pernas sob a ação (09.2) | `Motion.legW/legClip/legT/legDir` | golpe andando: pernas na passada real, tronco/braços/arma do golpe (validar com `--check golpe-andando`) |
| Rumo da passada (09.2) | `Motion.locoDir` | andar de lado/de costas (coxa abre); rumo 0 + passada 1 = idêntico ao anterior |
| Salto (09.2) | `Motion.air/land` | pés recolhidos no ar; quadril cede ao pousar |
| Peso no golpe | `hip`, `rootZ`, `feet`, `torso` mais amplos; chave `stop` repetida segura a antecipação | espada: agacha (0,83), avança até 2,6 e gira o tronco −46° na preparação |
| Lâmina longa L1→L2→L3 | `longblade.json` | transferência de peso contínua (rootZ −0,4 → +2,2 → +2,8) |
| Guarda de duas mãos | `guard_two_hands` (`lMode: "grip"`, `lGrip`, `lGripW`) | **substituída na rodada 05.1** (a passagem pela lâmina em pé diante do rosto e o repouso sobre o ombro escondiam a lâmina) |
| Arco | `drawn_full`, `recoil` mais forte; repouso com a curva visível | preparação → tensão → soltura legíveis; flecha alinhada |
| Segunda conjuração | `staff_cast_wide` circular (`circ_low → back → top → gather → release`) | trajetória diferente da primeira (teste de distância) |
| Crossfade de locomoção | `Motion.fromClip`/`locoW`, `Pose.lerp` pelo menor ângulo | entrada/saída de golpes a partir de andar/correr sem estalo |

Sem hitstop e sem partículas extras: o impacto vem da antecipação segurada, do `snap` no corte e da
continuação com `stop`.

## Rodada 04: coreografia de armas (ADR 0011)

**Por que mudou.** Os ataques antigos eram ângulos somados num plano só: a arma ia e voltava passando
pela cabeça (lâmina × cabeça 7,9 voxels, cajado × cabeça 6,9; `round-04/antes/`). Agora um golpe é uma
**trajetória autorada** e os braços são resolvidos por IK.

### Estrutura de todo golpe

**Repouso → Preparação → Execução → Continuação → Recuperação → Guarda.** As fases ficam no perfil
(`"phases"`), com durações próprias de cada arma (espada ~1,1 s; arco 2,0 s; cajado 1,8 s). Regras:

- **Preparação**: a arma sai da linha da cabeça ANTES de qualquer giro grande — passa pela lâmina em pé
  (pomo para baixo) e nunca gira "de lado" com o pomo apontando para a barriga.
- **Execução**: `snap` nas chaves do impacto; a ponta descreve um arco amplo À FRENTE do corpo; o tronco
  gira junto (o ombro leva o braço) e o pé da frente avança (`feet`, `rootZ`).
- **Continuação**: a lâmina passa do alvo e desacelera (`stop` na chave final da continuação).
- **Recuperação → Guarda**: volta passando pela lâmina em pé; guarda ≠ repouso (guarda é pronta para agir).
- Combinação: o fim de um golpe É a preparação do seguinte (sem voltar à guarda); se a lâmina precisa
  trocar de lado, passa baixa à frente (oito) ou alta ao lado da cabeça, nunca por cima do crânio.

### Perfil de arma (`assets/anim/<perfil>.json`, escrito à mão)

| Campo da pose | Significado (espaço da raiz, voxels de 1/40 u; +Z frente, +X esquerda do personagem) |
|---|---|
| `r`, `rDir`, `rEdge`, `rElbow` | mão direita (ponto de empunhadura), direção da ponta (+Y do item), fio (+X do item), polo do cotovelo |
| `l`, `lDir`, `lEdge`, `lElbow` | o mesmo para a mão esquerda (escudo, arco, mão livre) |
| `lMode` / `lGripW`, `lGrip` | `free` (balanço FK), `ik`, `grip` (no cabo, `lGrip` voxels abaixo da direita; peso contínuo) |
| `torso`, `head` | [pitch, yaw, roll] em graus; a cabeça compensa 80% do yaw do tronco (rosto para a frente) |
| `yaw`, `tilt`, `hip`, `feet`, `rootZ` | giro do corpo inteiro (arqueiro de lado), inclinação, altura do quadril (fração da perna), avanço dos pés, avanço do corpo |
| `draw`, `arrow`, `glow` | corda presa à mão (0–1); flecha `hidden`/`hand`/`string`; brilho do cristal |

Chaves: `{"t": s, "pose": nome, "stop": bool, "snap": k}` mais campos locais. Golpe: `keys`, `phases`,
`release`/`emit` (eventos). Perfil: `rightItem`, `leftItem`, `tipLength`, `emit`, `arrowRest`, `attack`
(golpe do Clip ATTACK), `carry` (poses de repouso/caminhada/corrida, mão livre, balanço) e `extends`.

### Como criar uma arma nova (passo a passo)

1. **Item no rig** (`tools/charlib_e.py`): modelado VERTICAL (+Y = ponta), pivô no centro da mão
   (`(-7, 13, 1)` direita / `(7, 13, 1)` esquerda); volumes com `*_volumes()` (tag `blade`, `handle`,
   `staff`, `bow`, `shield`…); `meta.weaponProfile`.
2. **Perfil** (`assets/anim/<arma>.json`): comece por `rest`, `carry_*`, `guard`; depois a preparação
   e o impacto; `extends` reaproveita golpes (ex.: `sword_shield` só muda a mão esquerda com `merge`).
3. **Alcance** (`python3 tools/anim_reach.py <arma>`): nenhuma pose com `!` (braço = 10,6 voxels).
4. **Validação** (`viewer --check`): zero falhas; o relatório diz a pose do pior caso.
5. **Olhar** em 5 ângulos + câmera de exploração (`tools/review4.sh armas`), em vídeo e a meia
   velocidade; passar no validador NÃO basta (a guarda da espada cobria o rosto de frente sem interseção).
6. **Testes**: `./gradlew test` (o `ChoreoTest` roda todos os golpes de todos os perfis).

### Limites das proporções da E (medidos)

- **Duas mãos no mesmo cabo** (revisto na rodada 05): com braço de 10,6 voxels, a guarda simétrica à
  frente põe o pomo no tronco. Existe, porém, uma família assimétrica viável (tronco +20..+40°, mãos no
  peito esquerdo, lâmina à frente-direita), encontrada por busca geométrica (`tools/twohand_search.py`) e
  usada em `guard_two_hands`. Os cortes amplos da lâmina longa seguem de uma mão; isso é propriedade da
  implementação atual, não impossibilidade.
- **Arco**: flecha alinhada à frente só com o corpo de lado (`yaw` −63°), puxada curta à frente do peito,
  arco inclinado e flecha de 15 voxels. De frente o arco fica diante do rosto porque aponta para a câmera.
- **Cajado**: nunca acima do chapéu; erguido de lado (x ≈ −11) e projetado à frente; o orbe nasce no
  cristal.

### Pés (rodada 04)

O chão do IK é o **topo real dos voxels** (`World.groundAt`), com **pegada** (calcanhar, meio, ponta —
o pé apoia no ponto mais alto, sem ficar pendurado na quina); o pé em balanço já mira a altura do pouso
e ganha folga sobre a quina. Quatro grandezas DIFERENTES (perna dos heróis da E = coxa 4 + canela 5,5 =
9,5 voxels = 0,2375 u; rodada 05.1):

| Grandeza | Valor | O que significa | Onde |
|---|---|---|---|
| Alcance geométrico de um pé | ±0,147 u (0,62 × perna) | quanto um pé pode subir/descer em relação ao nível de referência do quadril antes de o IK esticar no limite | `Animator.GROUND_REACH` |
| Diferença de altura entre os pés | 0,131 u (0,55 × perna) | o maior desnível que os dois pés apoiam ao mesmo tempo; acima disso o pé alto fica no limite, no ar | `Animator.STEP_LIMIT` |
| Obstáculo superável andando | degrau ≤ 0,131 u | o degrau do relevo T1 (1/8 u = 0,125 u) cabe com folga de 0,006 u | bancada `lab=steps` |
| Degrau que exige animação de subida | > 0,131 u | ex.: a bancada `lab=big` usa degraus de 0,2 u **para mostrar o limite**; o pé alto fica ~0,07 u no ar. Subir/escalar está fora do escopo | `lab=big`, `round-04/videos/14c-*` |

O "0,2 u" que aparecia em versões anteriores é a altura do degrau de TESTE da bancada, não um limite.
Prova de perfil: bancada `lab=` (plano, rampa, degraus de 1/8 u, degraus de 0,2 u),
`round-04/animacoes/pes-perfil-*.png` e vídeos 14*.

## Rodada 03: articulações, pés plantados e ataques com objeto

| Defeito (rodada 02) | Correção | Onde |
|---|---|---|
| Braço e perna inteiriços (golpe dependia só do tronco) | **cotovelo e joelho**: `farm_*` (antebraço, filho de `arm_*`, pivô em y = 17) e `shin_*` (canela, filha de `leg_*`, pivô no joelho); tampas de dobra (`cap`) que escondem a fresta | `charlib_e.limb_parts`, `split_limb` |
| Pé deslizando ~10% | ciclo de pé por fase (apoio/balanço): no apoio o pé fica **fixo no mundo** e a perna é resolvida por **IK analítico de 2 ossos** (coxa + canela); a velocidade de deslocamento é derivada da passada | `footCycle`, `legIK`, `WALK_SPEED`/`RUN_SPEED` |
| Pé atravessando o chão em encosta | sonda do chão (`Animator.Ground`) sob cada pé; o quadril desce para o pé mais baixo e o IK dobra o joelho do pé mais alto (limite atual: ver a tabela "Pés", 0,131 u entre os pés) | `groundOffset`, `Actor.submit(..., ground)` |
| Exploradora disparava sem flecha | parte `arrow` (filha do `bow`), visível só no ataque; ciclo preparar → puxar (cotovelo dobra até o queixo) → soltar (flecha some, arco recua) → recuperar | `bowShot2` |
| Cajado da maga sumia atrás da cabeça | braço aberto em repouso (rz −0,45) e compensação de giro X **e Z** no cajado; ataque = erguer, girar o cristal para a frente e apontar | `staffCast2`, `secondary()` |
| Espada cobrindo o rosto em repouso | cotovelo dobrado leva a lâmina para o lado e para baixo | IDLE |

Números: caminhada 2,2 passo-pares/s, apoio 56% do ciclo, meia passada 0,145 u → **1,14 u/s**;
corrida 2,8/s, apoio 36%, meia passada 0,15 u → **2,33 u/s**. O modo `move=1` e a cena usam essas
velocidades. Por construção o pé de apoio fica parado no mundo enquanto o corpo avança; a
transição apoio → balanço é onde ainda pode haver escorregão residual (ver vídeos `round-03/videos/`).
Os heróis da A–D e as variantes V1–V3 continuam no `biped()` antigo (sem articulação): não foram
alterados (restrição da rodada).

## O que mudou nesta rodada (e por quê)

| Defeito (rodada 01) | Correção | Onde |
|---|---|---|
| Pés flutuam/afundam na caminhada | `meta.footPlant`: o quadril desce `L·(1 − cos θ)` (L = comprimento da perna) e a perna que avança sobe um pouco no meio do passo | `biped()`, WALK/RUN |
| Cajado como pêndulo; maga ataca de mão vazia | `weaponMode = staff`: cajado na mão direita compensa 92% do giro do braço; ataque = erguer (braço aberto) e apontar | `secondary()`, `staffCast()` |
| Arco "vara com corda"; ataque de espada | `weaponMode = bow`: arco na mão esquerda, girado 35° no repouso; ataque = virar o tronco, estender o arco, puxar e soltar | `bowShot()` |
| Pernas atravessam o manto | manto em **duas metades** (`skirt_l`/`skirt_r`) filhas do tronco que seguem 78% do giro da perna do mesmo lado; abertura frontal | `secondary()` |
| Capa rígida | capa com restrição: rotação X nunca < 0,03 (não entra no corpo); `cape_lo` (segmento de baixo) atrasa e balança mais na corrida | `secondary()` (testado na V3) |
| Lâmina atravessa a cabeça na antecipação | antecipação com o braço aberto para fora (rz −0,8) e a espada girada | ATTACK |
| Trança horizontal na corrida | `meta.tailGain` (0,45 para tranças longas) | `secondary()` |
| Todos com a mesma postura | `meta.lean` | `secondary()` |
| Rosto fixo | expressão automática por clipe: ATTACK → determinada, HIT → surpresa, EMOTE → alegre | `Actor.autoExpression` |

## Regras para novos personagens

- Partes penduradas longas (capa, trança, cauda) **≤ 40% da altura** do personagem. Capas longas até
  o chão não entram nesta fase: rígidas atravessam, articuladas pedem mais segmentos/física.
- Nada pendurado à frente do corpo (o balanço vai sempre para trás).
- Arma: `sword` cabe no braço direito; `staff` ≤ altura do ombro + 0,25 u e topo fora da aba de
  chapéu; `bow` na parte `bow` da mão esquerda.
- Saias e mantos até o joelho: duas metades. Abaixo do joelho: duas metades **e** raio da barra ≥
  bota + 2 voxels.

## Cadência e deslocamento

Heróis da E: ver a tabela da rodada 03 acima. Demais direções (rodada 02): caminhada 1,75
passo-pares/s a 1,35 u/s; corrida 2,5/s a 3,2 u/s; deslizamento residual ~10% (sem joelho).

## Como inspecionar movimento (não use só quadros soltos)

```bash
tools/anim.sh output/tmp/walk "mode=lineup,style=e,set=herois,clip=walk,myaw=70" 60 30   # MP4 + GIF
tools/snap.sh "mode=scene,style=e,clip=walk,move=1,frames=90,fps=30,out=output/tmp/f.png"  # herói andando
.venv/bin/python tools/sheet.py seq output/tmp/walk.png 6 "output/tmp/f_###.png"           # prancha
```

No viewer: `E`/`Q` troca o clipe, `T` gira, `X` força uma expressão, WASD anda na cena.

## Atuação por personagem (rodada 06.1)

Personagens que dividem um perfil (ex.: `npc_vila`) atuam diferente por `meta.acting` no `rig.json`, sem copiar
animação. Todos os campos são opcionais; ausentes = comportamento padrão (heróis não usam):

| Campo | Unidade | Efeito | Faixa usada |
|---|---|---|---|
| `headPitch` | graus | cabeça baixa (+) ou erguida (−) no repouso | −3 a 4 |
| `headRoll` | graus | cabeça inclinada de lado | −5 a 6 |
| `torsoPitch` | graus | tronco para a frente (+) ou peito aberto (−) | −2 a 2 |
| `hipRoll` | graus | contraposto fixo (peso num quadril); o tronco compensa metade | −4 a 3 |
| `sway` | fator | amplitude da troca de apoio lenta (1 = padrão) | 0,7 a 1,4 |
| `emoteTempo` | fator | andamento do gesto `emote` (>1 = mais lento), via `Choreo.withTempo` | 0,8 a 1,25 |

Regras: sutil (|ângulos| ≤ 8°, andamento 0,75–1,3 — `VilaNpcTest.actingIsSubtleAndPersonal`); o perfil em
cache nunca muda (cópia por personagem); rode `viewer --check` depois (andamento mais rápido aumenta o passo
da ponta nas transições, ainda dentro da tolerância). Vídeos: `output/visual-review/round-06-1/videos/`.

## Combate (rodada 09): interrupções, guarda e derrota

Detalhes em [combate.md](../architecture/combate.md) e na skill `combat`.

### Janelas de interrupção

Cada golpe jogável tem, no perfil, a regra de corte por fase:

```json
"interrupt": [
  {"until": 0.50, "rate": -2},
  {"until": 0.60, "rate": 1, "exits": {"hit": "hit_cross_h"}},
  {"until": 9, "rate": 1}
]
```

- **Instante:** cortado em t, vale a primeira janela com t ≤ `until`.
- **Ritmo:** o golpe anda pela própria trajetória no ritmo `rate` (negativo = rebobina) enquanto a reação assume.
- **Saída:** em `exits`, a reação vira a saída autorada daquela fase.
- **Sem janelas:** vale a regra da 05.1 (rebobina na preparação; `"interrupt": "forward"` faz o embalo depois).
- **Ritmo das reações:** as reações (`hit`, saídas e `guard_hold`) seguem em frente quando cortadas.

### Saídas autoradas

`hit_cross_h` e `hit_cross_d` são usadas logo depois da execução, quando a espada está do outro lado do corpo:
1. começam na pose de continuação do corte (`h_follow`, `d_follow`);
2. recuam pelo corpo (cabeça −14°, quadril e raiz). O tronco quase não inclina, porque inclinar levava os ombros e as
   mãos não alcançavam;
3. param em `stop`;
4. voltam pela recuperação do próprio golpe (`h_settle` ou `d_settle`, depois a guarda).

Os parâmetros saíram de busca com as duas exigências do validador (o golpe sozinho e todos os cortes da janela), num
ponto cujos vizinhos também passam.

### Origem dupla

Um corte durante outra transição de corte (por exemplo, dano enquanto o encadeamento ainda mistura) usa
`Motion.fromMove2`: a origem é a própria mistura, então não há salto no segundo corte. O `Sequencer` e a camada do jogo
fazem o mesmo.

### Golpes do combate

- **`guard_hold`:** guarda do jogo (ergue, segura, impacto, segura, baixa). Os pontos de espera e de pulo estão em platôs
  parados da curva.
- **`defeat`:** ajoelha com a espada fincada ao lado, e segue a reação.
- **Equipamento:** os dois só existem nos perfis de espada.

### Criatura de combate

O `anim` `brawler` (fungo-cardo) usa:
- EMOTE como alerta;
- ATTACK em tempo normalizado (preparação, golpe e recuperação);
- HIT como recuo;
- DEATH como queda.

A preparação termina num tremor curto: é o aviso da cabeçada.

## Esquiva e salto (rodada 09.3)

- **Canais de perfil novos:**
  - `rollZ` (graus): inclinação lateral do corpo inteiro; + = esquerda. O corpo sobe junto, para a canela não afundar.
  - `feetX` [esq, dir] (voxels): afastamento lateral dos pés.
  - `spinPivot` [altura, frente] (voxels): centro do `spin`.
  - `spinAxis` (graus): desvia o eixo do `spin` do sagital, para um rolamento diagonal pelo ombro.
  - `fadeIn` (s, no gesto): entrada a partir da locomoção (esquivas 0,08).
- **Esquiva atual: R2** (`dodge_f/l/r`, sem `spin`). Os canais de giro ficam para uma esquiva futura; as poses dos
  rolamentos rejeitados estão em `docs/historico/esquivas-09-3/`.
- **Rolamento com cabeça grande (2,4 cabeças), lição da 09.3:** a cabeça vira a superfície da "bola" sempre que o corpo inverte; o
  que se controla é a COLUNA (altura do corpo no meio do giro) e a cabeça não afundar. Medido na 09.3: eixo lateral
  apoia o corpo na espada; rolar pelo lado do escudo deixa a cabeça mais tempo embaixo; recolher as pernas rende pouco
  (IK). Gire em diagonal (`spinAxis` −25 a −40),
  com o tronco dobrado e o pivô baixo. A lâmina vai por cima, com o pomo para trás e longe das pernas.
  `DodgeJumpPolishTest.theR1RollTouchesTheGroundWithTheBodyInsteadOfFloating` exige o corpo no chão no meio do giro.
- **Salto:**
  - `Motion.takeoff` (0→1 nos primeiros 0,10 s do voo) agacha e empurra.
  - `Motion.fall` (0→1 nos últimos 0,14 s) estende as pernas à frente.
  - `Motion.land` × `landRun` decide a profundidade do pouso.
  - Prévia sem o jogo: `snap mode=char,…,salto=parado|andando|correndo|antigo-parado…,myaw=90`.

## Limitações conhecidas

- Heróis da E (e rigs de estudo r4): braços por IK na camada de ação, **inclusive HIT e EMOTE**, que são
  gestos coreografados do perfil (moves `hit`/`emote`, rodada 05). Direções A–D e variantes V1–V3 não têm
  perfil de arma: nelas HIT e EMOTE continuam nos clipes FK antigos (`Animator.biped`), só para comparação.
- Desnível entre os pés acima de 0,131 u: o pé alto fica no ar (sem animação de subir; tabela "Pés").
- **Combate (rodada 09):** só espada e broquel. A flecha e o orbe continuam efeitos, sem colisão nem dano. A lâmina longa
  não é jogável (interrupção 18/47 aberta).
- A capa articulada só existe na variante V3; nenhuma física de pano (por decisão).
- Clipes de transição (parar, virar) não existem; a troca é por mistura simples.
