# Contrato de assets entre arte e engine

Este documento define o que o viewer espera encontrar em `assets/`. Qualquer direção artística
(A, B, C, D) que siga este contrato aparece automaticamente em todos os modos do viewer e na cena
de validação, **sem alterar código Java**. É o documento de referência para quem modela.

## 1. Unidades, eixos e escala

- Unidade de mundo `u` ≈ altura de um aventureiro (os heróis têm entre 1,0 e 1,25 u).
- **Y para cima, +Z é a frente do modelo**, X é a direita de quem olha o modelo de frente. O braço
  direito do personagem está em `x < 0`.
- Modelos são centrados em `x = 0, z = 0`; `y = 0` é o chão. Partes abaixo de `y = 0` ficam
  enterradas (útil para fundações e raízes).
- Cada modelo declara o tamanho do seu voxel (`voxelSize`, em u). Três escalas convivem na cena:
  voxel de terreno, voxel de ambiente e voxel de personagem (o mais fino).

| Direção | Terreno | Ambiente grande | Ambiente fino | Personagem |
|---|---|---|---|---|
| A | 1/4 u | 1/8 u | 1/16 u | 1/24 u |
| B | 1/4 u | 1/8 u | 1/16 u | livre (sugestão 1/26–1/30 u) |
| C | bloco de 1/2 u com textura de 16 px | texel de 1/32 u | texel de 1/32 u | texel de 1/32 u |
| D | 1/8 u | 1/12–1/16 u | 1/24–1/32 u | livre (sugestão 1/40–1/48 u) |
| E (rodada 02) | 1/8 u | 1/8 u | 1/16 u (objetos), 1/24 u (flores, grama) | **1/40 u** (criaturas 1/32–1/40; grandes 1/16) |

### Tamanhos de referência (em u) — a escala relativa é obrigatória

| Elemento | Tamanho |
|---|---|
| Herói | 1,0–1,25 de altura |
| Porta | 1,5 de altura, ~0,9 de largura |
| Casa (`house`) | base ~4,5 × 3,5; cumeeira a ~4,4; porta na face +Z |
| Carvalho (`tree_oak`, `tree_oak2`) | 4,5–5 de altura, copa ~2,5 de diâmetro |
| Pinheiro (`tree_pine`) | 5,5–6,5 |
| Bétula (`tree_birch`) | 4–4,5, tronco fino |
| Árvore-marco (`tree_big`) | 8–9 de altura, copa ~5 de diâmetro |
| Arbusto | 0,6–1,0 |
| Pedras `rock_s` / `rock_m` / `rock_l` | 0,5 / 1,0 / 2,0 de altura |
| Flores, tufos de grama | 0,25–0,5 |
| Cogumelo gigante (`mushroom_big`) | ~2 |
| Poço, santuário | ~2,2 e ~2,8 de altura |
| Cerca (`fence`) | 2 de comprimento ao longo de X, ~1 de altura |
| Píer (`dock`) | ~3,5 de comprimento ao longo de +X, tábuas em y ≈ 0,45 acima da origem |
| Nuvem (`cloud`) | ~8 × 2,5 × 4 |

## 2. Personagens e criaturas (`rig.json`)

Diretórios: `assets/characters/<estilo>/<nome>/` e `assets/creatures/<estilo>/<nome>/`.

Personagens esperados (nomes fixos): `adventurer` (aventureiro com espada), `ranger`
(aventureira com equipamento leve), `mage` (conjurador). Criaturas esperadas: `pompom` (pequeno
animal selvagem), `lumi` (criatura fantástica amigável, flutua), `fungo` (inimigo simples),
`tortuga` (criatura grande, referência de escala). Os nomes dos diretórios são fixos; o desenho
é livre e deve ser original.

```json
{
  "name": "Aventureiro (A)", "type": "vox", "voxelSize": 0.0416667, "anim": "biped",
  "sizeVoxels": [22, 29, 18],
  "meta": {"headsTall": 2.7, "legH": 7, "torsoH": 10, "headH": 10},
  "parts": [
    {"name": "torso", "file": "torso.vox", "parent": null, "origin": [-5, 7, -3], "pivot": [0, 7, 0.5]}
  ]
}
```

- `origin` = canto mínimo da parte, em voxels do espaço do modelo; `pivot` = articulação, na mesma
  unidade (coordenadas contínuas: o voxel `x` ocupa `[x, x+1]`).
- Uma parte filha deve vir **depois** do pai na lista.
- `meta.weaponRest` (opcional, radianos): inclinação de repouso da arma para a frente; padrão 1,05
  (espada). Use ~0,1 para cajados e armas seguradas na vertical.
- `meta.headsTall` aparece no HUD; preencha com a altura total ÷ altura da cabeça (com cabelo).
- Rigs de cuboides (direção C) usam `"type": "cuboid"`, `"atlas": "atlas.png"` e, em cada parte,
  `"cuboids": [...]` no lugar de `file`/`origin` (formato na seção 4).

### Partes reconhecidas pelo animador (`viewer/.../Animator.java`)

Partes com outros nomes são desenhadas, mas ficam rígidas em relação ao pai.

| `anim` | Partes animadas |
|---|---|
| `biped` | `torso` (raiz; pivô no quadril), `head`, `arm_l`, `arm_r` (pivô no ombro), `farm_l`, `farm_r` (antebraço; filhos do braço, pivô no cotovelo), `leg_l`, `leg_r` (pivô no quadril, **sem pai**), `shin_l`, `shin_r` (canela; filhas da perna, pivô no joelho), `arrow` (filha de `bow`), `weapon` (filha de `arm_r` ou, com articulação, de `farm_r`, modelada na VERTICAL com o punho no pivô — o animador a inclina), `cape`, `tail` (rabo de cavalo/trança; filha de `head`), `skirt`, `hat` |
| `quadruped` | `body`, `head`, `leg_fl`, `leg_fr`, `leg_bl`, `leg_br`, `tail`, `ear_l`, `ear_r` |
| `hopper` | `body` (ou qualquer raiz), `head`, `ear_l`, `ear_r`, `tail`, `arm_l`, `arm_r`, `leg_l`, `leg_r`, `cap` |
| `floater` | `core` (pulsa), `ring` (gira em Y), `wing_l`, `wing_r`, `tail`, `leaf_l`, `leaf_r` |
| `flutter` (rodada 06) | `body` (raiz, tórax), `head`, `abdomen`, `wing_fl`, `wing_fr` (asas da frente), `wing_bl`, `wing_br` (de trás, atrasadas 0,7 rad); asas modeladas PLANAS na horizontal com a articulação no pivô — o animador as ergue em V e inclina a corda. Batida ≤ 2,6 Hz (sem cintilação) |

Convenção de rotação: X negativo leva um membro pendente para a frente; Z positivo abre o braço
esquerdo (`x > 0`).

### NPCs e vitrine (rodada 06)

- NPCs ficam em `assets/characters/npc/<nome>/` (fora de `characters/<estilo>/`, para não entrarem no
  ciclo de heróis nem deslocarem os índices `char=` das referências). `meta.npc = true`; mesmas partes do
  bípede articulado + opcionais `skirt_l/r`, `pack`, `tool`, `ear_l/r` (acessório filho de `head`, fora da
  medida de cabeças), `lock` (mecha/trança longa presa ao **tronco**, rígida; rodada 06.1). NPCs atuais:
  `cartografa` (`npcs_e.py`), `kelyra`, `indira`, `suvia` (`npcs_trio.py`).
- Metadados de NPC (rodada 06.1): `role` (papel na vila), `actingStyle` (desenho das expressões:
  `confident`/`sweet`/`playful`), `acting` (objeto de atuação de animação — `guia-de-animacao.md`).
- Estudos de alternativa: `assets/studies/<nome>/` (ex.: `kelyra_estalajadeira`, `kelyra_tecela`), gerados
  pelo mesmo módulo; não entram em cenas.
- Prova de personalização (rodada 06.1): `assets/characters/custom/<id>/` (`tools/custom_e.py`), com a parte
  `hair` (filha de `head`, mesmo pivô) separada do rosto e `meta.appearance` (contrato
  `valumo.aparencia/1`). Ver `docs/architecture/personalizacao.md`.
- Perfil compartilhado de NPC sem item: `assets/anim/npc_vila.json` (`meta.weaponProfile = "npc_vila"`,
  move `emote` = acenar). Cena da vila: `assets/scenes/vila_e.json` (`layout=vila_e`); lineup `set=vila`.
- Composições alternativas: `assets/scenes/<nome>.json` (escritas à mão), abertas com a chave de captura
  `layout=<nome>`; aceitam `npcs: [{"rig", "x", "z", "r", "clip", "lift"}]`. A clareira canônica
  (`clearing_e.json`) não recebe assets novos sem aprovação.

## 3. Ambiente (`props.json`)

Diretório: `assets/environment/<estilo>/`, com um `props.json` (mapa nome → entrada). Nomes usados
pela cena `assets/scenes/clearing.json` (os ausentes são simplesmente ignorados):

`tree_oak`, `tree_oak2`, `tree_pine`, `tree_birch`, `tree_big`, `sapling`, `bush`, `bush_berry`,
`fern`, `grass_tuft`, `grass_tall`, `flower_red`, `flower_yellow`, `flower_blue`, `flower_white`,
`mushroom_red`, `mushroom_big`, `rock_s`, `rock_m`, `rock_l`, `house`, `well`, `fence`, `signpost`,
`lantern_post`, `crate`, `barrel`, `shrine`, `campfire`, `log`, `stump`, `dock`, `reeds`, `lily`,
`cloud`.

Entrada voxel: `{"file": "x.vox", "voxelSize": 0.125, "origin": [x, y, z], "sizeVoxels": [w, h, d]}`.
Entrada de cuboides (C): `{"voxelSize": 0.03125, "sizeVoxels": [w, h, d], "cuboids": [...]}`, com um
único `atlas.png` no mesmo diretório.

## 4. Formato `.vox` e materiais

- MagicaVoxel `.vox` versão 150, um modelo por arquivo, com chunk `RGBA` obrigatório.
- O material é codificado pela **faixa do índice de paleta**:

| Índices | Material | Efeito no shader |
|---|---|---|
| 1–175 | fosco | difuso |
| 176–199 | metal | especular + reflexo do céu (força depende do estilo) |
| 200–219 | emissivo | ignora sombra; alimenta o bloom |
| 220–249 | folhagem | balanço de vento e translucidez (se o estilo ligar) |
| 250–255 | água/vidro | reservado |

`tools/voxlib.py` cuida disso: use `C("#rrggbb", "metal")` etc.

## 5. Cuboides texturizados (direção C)

```json
{"from": [x, y, z], "size": [w, h, d], "flat": [r, g, b],
 "faces": {"px": [u, v, w, h], "nx": [...], "py": [...], "ny": [...], "pz": [...], "nz": [...]},
 "rot": [rx, ry, rz], "origin": [x, y, z]}
```

- `from`/`size` em texels do espaço do modelo; `faces` são retângulos em pixels do atlas (faces
  omitidas não são desenhadas); `flat` é a cor usada no modo "sem texturas" (`flatFaces` permite
  uma cor por face). `rot`/`origin` são opcionais (graus).
- Orientação das texturas: nas faces laterais o topo da imagem é +Y; na face +Y o topo da imagem
  é −Z (costas); pixels com alfa < 0,5 são descartados.
- Filtragem sempre *nearest*, sem mipmaps. Densidade única: **1 texel = 1/32 u** em tudo.

### Terreno da direção C

`assets/textures/c/terrain.png` é uma faixa horizontal de tiles quadrados, e `terrain.json` diz
quais tiles servem a cada superfície (listas = variações sorteadas):

```json
{"tile": 16,
 "tiles": {"grass_top": [0, 1], "forest_top": [2], "path": [3, 4], "sand": [5], "rock": [6, 7],
           "grass_side": [8], "forest_side": [9], "dirt": [10, 11]}}
```

## 6. Ferramentas

- `tools/voxlib.py` — volume esparso, primitivas, escritor `.vox`, `Rig`, `PropSet`, escritor PNG.
- `tools/envlib.py` — superelipsoides, rampa "iluminada de cima", troncos, pedras, telhados.
- `python3 tools/build_assets.py [módulos]` — regenera assets (determinístico).
- `tools/snap.sh "<spec>" ...` — captura offscreen do viewer real (ver cabeçalho do script).

## 7. Extensões da rodada 02

Tudo opcional e retrocompatível: rigs e manifestos da rodada 01 continuam válidos.

| Onde | Campo | Significado |
|---|---|---|
| `rig.json` (raiz) | `"affineWind": true` | folhagem do rig usa vento afim; o mesher pode fundir as faces de folhagem |
| parte | `"alts": {"joy": {"file", "origin"}, ...}` | malhas alternativas da parte (expressões da cabeça: `joy`, `determined`, `surprise`); mesma pivô |
| `meta` | `weaponMode` | `sword` (padrão), `staff` (cajado vertical na mão direita), `bow` (parte `bow` na mão esquerda) |
| `meta` | `footPlant` | `true`: quadril desce com o arco da perna (pés no chão) |
| `meta` | `lean`, `tailGain` | postura (rad, + = à frente); ganho do balanço de `tail` |
| `meta` | `faces`, `tier` | faces expostas; nível de complexidade da criatura (1–3) |
| partes `biped` | `skirt_l`, `skirt_r` | metades do manto, seguem 78% da perna do mesmo lado (filhas do tronco) |
| partes `biped` | `cape_lo` | segmento inferior da capa (filho de `cape`), com atraso; `cape` nunca gira para dentro |
| partes `biped` | `bow` | arco na mão esquerda (filho de `arm_l`), vertical, ventre em +Z |
| `props.json` (entrada) | `"affineWind": true` | idem, por prop |
| `scenes/` | `clearing_<estilo>.json` | composição própria de uma direção (senão usa `clearing.json`) |
| `scatter` | `"cluster": n`, `"clusterR": r` | cada sorteio planta n itens a até r u (touceiras) |

Diretórios novos: `characters/e/`, `creatures/e/`, `environment/e/` (direção E) e
`characters/variants/{v1,v2,v3}` (experimento das variantes). Props novos da E: `tree_oak3`, `tree_oak4`.
Testes: `AssetContractTest.consolidatedHeroesFollowTheIdentity`, `adventurerVariantsExistForComparison`,
`consolidatedEnvironmentHasTheTreeLibrary`.

## 8. Extensões da rodada 03

Retrocompatíveis: rigs sem `farm_*`/`shin_*` continuam animados pelo bípede da rodada 02.

| Onde | Campo | Significado |
|---|---|---|
| partes `biped` | `farm_l`, `farm_r` | antebraço (filho de `arm_*`, pivô no cotovelo); `weapon` filha de `farm_r`, `bow` de `farm_l` |
| partes `biped` | `shin_l`, `shin_r` | canela + bota (filha de `leg_*`, pivô no joelho); resolvida por IK de 2 ossos |
| partes `biped` | `arrow` | flecha (filha de `bow`), oculta fora do ataque |
| `meta` | `joints` | `true` liga o animador articulado (ciclo de pé, IK, sonda de chão) |
| `meta` | `ankle` | altura do tornozelo em voxels (apoio da sola no IK) |
| diretórios | `characters/r3/`, `studies/` | estudos da rodada 03 (cabelo, capuz, chapéu, cajado, rosto; carvalho O1–O3; carapaça S1–S3) — não usados no cenário |
| captura/bench | `terrain=0..4` | variante de relevo da E (padrão `World.E_TERRAIN` = 1) |
| captura/bench | `fxaa=0`, `-Dvx.assets=<dir>`, `-Dvx.chamferMinRun=n` | desliga FXAA; raiz de assets alternativa (ex.: snapshot da rodada 02); trecho mínimo do chanfro |
| captura | `party=1`, `ssaa=`, `hero=` | os três heróis juntos na cena; SSAA da captura; herói da cena |

Testes novos: `AssetContractTest.consolidatedHeroesHaveElbowsAndKnees`,
`creaturesDoNotShareTheHeroFace` (orçamento de faces por nível), `VoxelMesherTest.mergedQuadsOverlapNeighboursToHideTJunctionCracks`.

## 9. Extensões da rodada 04 (coreografia de armas)

| Onde | Campo | Significado |
|---|---|---|
| `meta` | `weaponProfile` | nome do perfil em `assets/anim/<nome>.json` (sword, sword_shield, longblade, bow, staff) |
| `meta` | `hands` | `{"r": [x,y,z], "l": [x,y,z]}` centro de cada mão (voxels, bind) quando não há item |
| `meta` | `volumes` | volumes APROXIMADOS para a validação: `{"part","tag","a",["b"],"r"}` (cápsula a–b ou esfera); tags `head hat torso back leg arm_l arm_r blade handle staff bow arrow shield` |
| partes | `string_u`, `string_d` | metades da corda do arco (filhas de `bow`, pivô na ponta); o animador gira e estica até a mão |
| partes | `shield` | broquel (filho de `farm_l`, pivô na mão, face +Z) — estudo `characters/r4/adventurer_shield` |
| itens | eixo | todo item segurado é modelado VERTICAL: +Y = ponta/topo, +X = fio; pivô = centro da mão |
| `assets/anim/` | perfis | **escritos à mão** (exceção às pastas geradas, como `assets/scenes/`): poses, golpes, fases, eventos |
| captura | `act=`, `seq=`, `speed=`, `hold=`, `dbg=1`, `legacy=1`, `lab=`, `far=` | golpe em laço, roteiro (`+` separa), velocidade, pausa entre laços, depuração, ataques antigos, bancada de terreno, tratamento de relevo à distância |
| CLI | `viewer --check [csv\|legacy]` | valida todos os golpes e transições; `legacy` mede os ataques da rodada 03 |

Estudos novos: `characters/r4/adventurer_shield`, `characters/r4/adventurer_longblade` (`tools/chars_e_study.py`).
A flecha da exploradora passou a 15 voxels (proporção chibi da puxada). Testes: `ChoreoTest` (14 casos).
