---
name: weapon-animation
description: Criar ou alterar animações com equipamento (espada, escudo, lâmina longa, arco, cajado ou arma nova) na direção E — perfis de arma em assets/anim/*.json, poses por trajetória (mão + direção da ponta + fio), IK de braço, fases do golpe, transições, validação espacial (viewer --check), revisão multiângulo e testes. Use antes de mexer em qualquer golpe, guarda, transporte de arma ou item segurado.
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
  cajado seguros em qualquer instante. Janelas inseguras da espada/lâmina longa estão em problemas-conhecidos.
- Sem cancelamento de gameplay, prioridades, stamina ou hitstop (fora do escopo até existir combate).

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

## Rodada 09 — arma × cabelo personalizado

O `--check` mede a cabeça como uma cápsula (raio 8,6): cabelo além dela não é visto. `viewer --check-cabelos` mede a
arma contra a cabeça COMPOSTA (voxels reais) em todo golpe, HIT, EMOTE e locomoção, para cada preset × penteado.
Achado: o EMOTE do aventureiro põe 21 voxels da espada dentro do topete canônico (t = 0,77 s). Ao mexer em qualquer
golpe ou gesto, rode também `--check-cabelos` e não aumente a penetração de nenhuma combinação.
