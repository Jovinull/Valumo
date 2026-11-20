---
name: character-design
description: Criar ou alterar um herói, NPC ou criatura na identidade visual consolidada (direção E, "Fábula Voxel") — protagonistas congelados (2,35–2,60 cabeças, olho 3×5 — só deles), NPCs com faixa 2,0–3,0 cabeças e forma de olho livre, sempre rosto em placa plana, 4 expressões para NPC com diálogo, cabelo em massa+mechas, hierarquia de detalhe, equipamento e metadados de rig; NPCs em lote (um construtor, várias fichas, sem recoloração) e personagem inspirado em pessoa real (só o modelo entra no repositório: nada que o ligue à pessoa). Use antes de modelar qualquer personagem ou criatura.
---

# Design de personagem (direção E)

Referência obrigatória: `docs/art/visual-identity.md`, `docs/art/especificacao-de-personagem.md`,
`docs/art/sistema-de-proporcoes.md`. Exemplos funcionando: `tools/chars_e.py` (aventureiro,
exploradora, maga) e `tools/creatures_e.py` (pompom = criatura de referência).

## Regras que reprovam um personagem

1. Proporção fora da faixa do seu tipo. **Fonte única:** `docs/art/contrato-de-qualidade.md` —
   protagonistas: valores congelados de `sistema-de-proporcoes.md` (teste `consolidatedHeroesFollowTheIdentity`);
   NPC humanoide: faixa 2,0–3,0 cabeças com escolha autoral registrada. Medição: cabelo conta; chapéu/capuz/pontas não.
2. Voxel diferente de **1/40 u** (heróis/NPCs). Criaturas: nível 1 = 1/32, nível 2 = 1/40, nível 3 = 1/16.
3. Silhueta em preto (`sil=1`) que não se distingue dos heróis existentes.
4. Rosto pintado em superfície curva. **Sempre** placa plana (`charlib_e.skull()` faz; criaturas:
   achatar a frente como em `creatures_e.pompom`).
5. Cabelo-capacete ou mais de ~6 mechas finas (vira xadrez nas costas).
6. Arma que atravessa cabeça/chapéu em algum quadro de ação (`viewer --check`, com o rig em `Check.RIGS`); parte pendurada > 40% da altura.
7. Voxel solto ou fragmento < 4 voxels em qualquer parte (`tools/asset_audit.py`, `QualityTest`).

## Receita

```python
import charlib_e as L
from charlib_e import P, NECK, HIP, SHOULDER, SKIN
sk = L.skull(SKIN)
h = L.hair_base(sk, hair, bang, nape, thick=1.6)          # bang[x] >= 10 sobre a sobrancelha
L.hair_locks(h, hair, [((x0,y0,z0),(xm,ym,zm),(x1,y1,z1), r0, r1), ...])   # 3–6 mechas grossas
h = L.hair_finish(h, sk, hair, face_guard=...)            # 3 tons + brilho
L.hair_strands(h, hair)                                   # sulcos nas costas
heads = L.head_with_expressions(sk, h, iris, brow, style={..., "acting": "bold|cool|soft|<novo>"})  # atuação própria: nunca o carimbo comum
t = L.torso(cor, hem=debrum); L.belt(t, HIP + 1, L.LEATHER)
arm_l, arm_r = L.arm(manga, luva, cuff=debrum)
leg_l, leg_r = L.leg(calça, bota, B.light(bota), sola)
arma, grip = L.sword() | L.staff(...) | L.bow(...)
rig = L.finish(nome, parts, head_top=h.bounds()[4], meta_extra={"weaponMode": ...}, alts={"head": {...}})
```

Ordem de trabalho: **silhueta → volumes → cores → identidade → ornamentos**. Pare em cada etapa e
capture ao lado dos heróis: `tools/snap.sh "mode=lineup,style=e,set=herois,sil=1,out=output/tmp/s.png"`.
Para comparar o novo com os existentes, acrescente um conjunto em `App.LINEUPS`.

## Diferenciação (pelo menos 3, incluindo silhueta)

Silhueta · formato de cabelo · paleta secundária · arma/equipamento · postura (`meta.lean`) · cor da
íris · acessório assimétrico. Tabela dos três atuais em `especificacao-de-personagem.md §6`.

## Metadados

`weaponMode` (`sword`/`staff`/`bow`), `weaponRest`, `footPlant: true`, `lean`, `tailGain`, `faces`
(preenchido por `finish`), `tier` (criaturas). Contrato: `docs/art/contrato-de-assets.md §7`.

## Antes de entregar

`python3 tools/build_assets.py chars_e` → `./gradlew test` → checklist em
`docs/validation/checklist-de-aceitacao.md` com capturas (frente/perfil/costas/3-4, silhueta, sem cor,
luz neutra, reduzido, close de rosto com as 4 expressões, no mundo, vídeo dos clipes).

## Rodada 03 (polimento)

- Heróis da E são **articulados**: gere braços/pernas com `charlib_e.limb_parts(...)` (antebraço
  `farm_*`, canela `shin_*`, tampas de dobra); arma filha de `farm_r`, arco de `farm_l`; `meta.joints`.
- Compare alternativas **uma variável por vez** (`tools/chars_e_study.py` → `assets/characters/r3/`,
  modo LINHA `r3-*`). Defeitos a evitar: pontas finas na coroa (antenas), capuz levantado verde +
  arco (arquétipo de outra obra), aba colada ao cabelo, cajado atrás da cabeça em repouso.
- Rosto (protagonistas e variantes de estudo): varie os parâmetros de `FACE_DEFAULT` (olho, sobrancelha,
  boca, sardas, cílio); nada de pálpebra escura permanente. NPCs podem ter forma de olho NOVA (ver rodada
  06), desde que em placa plana. Invariantes × flexíveis: `docs/art/visual-identity.md` §0.

## Rodada 05 — referências e aceite

- Padrão visual: `docs/art/reference/01–09` (proporção, cor, costas, perfil, silhuetas, rostos, kits);
  reprovado `r2` (antenas). Critérios por classe ([O]/[F]/[B]/[P]): `docs/art/contrato-de-qualidade.md`.
- (Corrigido na rodada 06.) As medidas exatas dos heróis — 2,35–2,60 cabeças, olho 3 × 5, 1,18 u — valem
  **só para os três protagonistas** e estão congeladas. NPCs humanoides: faixa 2,0–3,0 cabeças, forma do
  olho, cabelo, corpo e silhueta livres dentro da linguagem E (rosto em placa, sem nariz, brilho do lado da
  luz), voxel 1/40 u — fonte única: `contrato-de-qualidade.md`, seção "NPCs humanoides futuros". Criatura
  nova segue a tabela de criaturas do contrato (nível 1/2/3, rosto próprio, sem olho de herói).
- Equipamento novo: peça com três tons por seção, `*_volumes` correspondente e perfil em `assets/anim/`
  (skill `weapon-animation`).
- Comando de aceite: `python3 tools/build_assets.py chars_e chars_e_study chars_variants && python3
  tools/asset_audit.py assets/characters/e && ./gradlew test && viewer --check`, depois capturas
  comparáveis às referências (`tools/reference.sh output/tmp/ref` e compare lado a lado).
- Falhas já vistas na rodada 05: ponta solta na pena (corrigida com `connect_path`), voxel solto na aba,
  antebraços com fragmentos, variantes desatualizadas depois de mudar o `charlib_e` (regenere `chars_variants`).

## Rodada 05.1 — NPCs, partes secundárias e verificação de ação

- **NPC ≠ protagonista.** Não copie corpo e olho dos três heróis: escolha proporção e forma do olho dentro
  das faixas do contrato (seção "NPCs humanoides futuros") e registre a escolha em `meta`. O que não muda:
  rosto em placa plana, brilho do lado da luz, sem nariz, massas arredondadas, voxel 1/40 u, rig bípede.
- **Partes secundárias** (rodada 05.1): mochila, pontas de cachecol, trança e chapéu podem ser partes
  próprias (`pack`, `scarf`, `tail`, `hat`), recortadas do tronco/cabeça **depois** de `shade()` (mesmas
  cores) e filhas do tronco/cabeça, com o pivô no ponto de amarração. O `Animator` aplica atraso com limites
  unilaterais (nada entra no corpo). Exemplo: `chars_e.adventurer` (`pk`, `st`). Sem articulação adequada,
  NÃO simule: documente a limitação.
- **Antes de entregar um personagem armado:** `viewer --check`, `viewer --occlusion` (rosto livre),
  `viewer --grip <rig> clip:HIT` (sem "ITEM PARADO NO MUNDO") e vídeo em 4 ângulos (skill `visual-qa`).

## Rodada 06 — procedimento de NPC (provado com a cartógrafa)

1. Escolha a proporção dentro da faixa de NPC (contrato) e escreva o esqueleto como constantes do módulo
   (`HIP`, `NECK`, `SHOULDER`, `ELBOW`, `KNEE`, `FACE_Z`, `EYE_Y`) — ver `tools/npcs_e.py`. Não altere as
   constantes do `charlib_e` (são dos protagonistas).
2. Mantenha as partes do bípede (`torso`, `head`, `arm/farm`, `leg/shin`) com pivôs reais: o `Animator` anima
   qualquer proporção sem código novo. Acessórios articuláveis: `skirt_l/r`, `pack`, `tool`.
3. Silhueta primeiro: `set=r6-npc` com `sil=1` de frente, perfil e costas AO LADO dos heróis. Ela precisa de
   um elemento próprio no contorno (cartógrafa: coque lateral + tubo de mapas ao lado da cabeça).
4. Rosto: gramática da E com forma de olho própria; nada sobre os olhos que domine o rosto (óculos no cabelo).
5. Cabelo: casca fina + mechas grossas + franja com desenho; casca grossa e franja reta = capacete.
6. Gesto característico em `assets/anim/<npc>.json` (move `emote`), validado no `viewer --check`; sem ataques.
7. Testes: acrescente o NPC ao `ExpansionTest` (proporção na faixa e fora da dos heróis, partes, expressões,
   cabeça estruturalmente diferente, sem fragmentos, animações no validador).

## Personagem inspirado em pessoa real (regra do proprietário)

Permitido, com a autorização da pessoa (o proprietário cuida disso **fora** do repositório). O repositório
guarda só o modelo.

**Nunca entra no repositório** (código, assets, docs, nomes de arquivo, metadados `rig.json`, comentários,
`output/`, mensagens de commit):
- fotos, capturas de rede social, pranchas ou recortes de referência;
- nome real, apelido, @, link, ou nome de personagem que "soe" como a pessoa de propósito;
- texto descrevendo a referência ("como nas fotos", "homenagem a…", "baseada em…");
- previews lado a lado com a referência.

**Procedimento:**
1. O proprietário trabalha as referências numa pasta FORA do repositório. Nada é copiado de lá além de
   scripts de modelagem e técnicas — e os scripts são revisados antes (comentários e `note` limpos).
2. Dê ao personagem um nome próprio de fantasia e descreva-o só pelo que é no jogo: papel, forma, paleta,
   roupa, acessório, atuação (ver `docs/art/especificacao-de-personagem.md` §10).
3. Corpo na linguagem da E: tronco em massa única, sem volume de busto nem decote profundo
   (`VilaNpcTest.torsoHasNoExtraFrontVolume`: nada projeta além de z = 5 no tronco do NPC).
4. Siga o procedimento de NPC (seções "Rodada 06" e "NPCs em lote") (proporção na faixa, silhueta ao lado dos heróis, expressões com
   atuação própria, gesto validado, testes, captura, vídeo).
5. Antes de entregar, varra o repositório por qualquer vínculo:
   `grep -rniE "<nome real>|<apelido>|homenagem|foto|refer" tools assets docs viewer .claude output`
   (rode com os termos reais **no seu terminal**, sem gravá-los em arquivo).
6. Autorização retirada → o personagem sai do jogo (mova para `output/quarentena/` só o que não tem vínculo e
   regenere).

## NPCs em lote — um construtor, várias fichas (provado com `tools/npcs_vila_e.py`)

1. Um `npc(key)` + `SPECS` com paleta e escolhas estruturais (`collar`, `belt`, `charm`, `tails`,
   `hair_bottom`, `crown`, `flare`, `fringe`, `front_bottom`, `ears`, `acting`). Esqueleto importado de
   `npcs_e` — não duplique o arquivo por personagem (o estudo de origem tinha três cópias de 300 linhas).
2. Meça a diferença de FORMA, não de cor: Jaccard de voxels do rig e da cabeça entre pares
   (`VilaNpcTest.notRecolorsOfEachOther`: corpo < 0,88, cabeça < 0,85; a cartógrafa fica em 0,71–0,75).
3. Atuação própria por personagem (`acting`) com teste de que duas personagens não pintam o mesmo desenho
   na mesma expressão (Jaccard das células alteradas < 0,6). Se falhar, mude a ATUAÇÃO (ex.: rir de olhos
   abertos em vez de fechados), nunca o limite.
4. Gesto compartilhado de NPC sem item: `assets/anim/npc_vila.json` (`carry.free = "r"` + `lMode: free` no
   repouso → as duas mãos balançam; o `emote` usa a mão direita por IK). Mão do aceno fora do contorno da
   cabeça (perto da bochecha lê como "mão no rosto").
5. Cena própria (`assets/scenes/vila_e.json`, `layout=vila_e`); clareira canônica e vitrine não mudam.

## NPC com altura narrativa (provado com `tools/npcs_oficios_e.py`)

1. Construa no tamanho de NPC (`npcs_vila_e.npc(base, **ficha)`) e **reamostre** o rig pronto (`scale_vox`) para a
   altura da história: 0,75 u por metro narrativo. Grave `meta.escala`, `metersNarrative`, `worldUnits`; mãos e
   `*_volumes` escalados junto. Proporção em cabeças e voxel 1/40 continuam os do contrato.
2. O que depende do tamanho escala com ele (faces ≤ 9 000 × escala², tronco z ≤ ⌈5 × escala⌉); o resto do contrato
   é o da vila. Nunca afrouxe o limite para um NPC de tamanho normal.
3. O gesto compartilhado (`npc_vila.json`) está em voxels do esqueleto de origem: o `Animator` o converte com
   `voxelSize × meta.escala` (sem `escala`, idêntico). `OficiosNpcTest.sharedGesturePassesTheSpatialValidator`.
4. Detalhe de rosto não pode esconder a atuação: barba deixa a boca à vista (bigode fino, nada em cima da boca);
   óculos finos ficam abaixo da sobrancelha; cabelo volumoso (afro) não cobre a testa — `expressionsActDifferently`
   pede ≥ 6 voxels por expressão.
5. Na vila: `jogo.interativos` com falas próprias; vaga sem nenhuma forma (corpo ou câmera) a menos de 1,6 u e sem
   copa por cima (`GameplayTest.everyResidentStandsOnFreeGroundWithRoomToTalk`); confira a conversa capturada
   (`tools/jogo/10-npcs-oficio.txt`) — passar no teste não garante que um poste fino não cubra o rosto.

## NPC de ofício com acessórios e gesto próprio (provado com `tools/npcs_mundo_e.py`)

1. **Chapéu, boina e coque são casca colada à cabeça** (`npcs_mundo_e.shell`: camadas de 6 vizinhos crescendo do
   cabelo), em parte `hat` filha da `head`. Elipsoide solto flutua com faixa de ar
   (`MundoNpcTest.hatSitsOnTheHeadWithoutAGap`).
2. **Acessório fora do caminho dos membros:** braço pendurado ocupa x de 5 a 9 (voxels do esqueleto NPC); coxa gira
   abaixo do quadril (y 14). Avental, bolso, bolsa de moedas e corda na FRENTE, dentro de |x| ≤ 4; bolsa grande nas
   COSTAS, acima do quadril. Aba de chapéu do lado da mão que acena entra no aceno: caia a aba para o outro lado.
3. **Meça a interpenetração nos clipes reais:** `tools/conteudo/npc_clip.sh NOVOS... -- APROVADOS...`
   (`NpcClipProbe`, a cada 1/60 s de IDLE, WALK, RUN, EMOTE e cada gesto do perfil; profundidade em u e o instante).
   Regra do `MundoNpcTest`: braço × tronco ≤ 1 voxel na escala do rig; os outros grupos ≤ o pior NPC aprovado.
4. **Gesto de ofício** num perfil próprio (`assets/anim/npc_<papel>.json`) com o aceno IDÊNTICO ao `npc_vila`
   (`emote`, poses e `carry` iguais, testado) e um `oficio` de 2 a 4 s (`"attack": "oficio"`). A testa fica fora do
   alcance (~11,5 voxels de braço): gesto à frente do peito, na cintura ou apontando. Mão que sai do repouso em linha
   reta corta a quina do tronco: ponha uma pose de passagem à frente. Valide `oficio`, `emote` e as sequências com
   `WeaponCheck.validate` (inclusive `seq:walk:0.8,oficio,walk:0.8`: o andar muda o alcance).
5. Pele avermelhada: a sombra pintada do queixo emenda na costeleta e lê como barba. Sombra mais leve
   (`P(cor, ks=0.6)`) e costeleta curta; nunca pintura à mão.

## NPC criatura com rabo e cabeça própria (provado com o Acapela, `tools/npc_acapela_e.py`)

1. **Esqueleto:** o da vila (`npcs_vila_e.torso/arms/legs`, quadril 14, pescoço 29), com cabeça e rabo PRÓPRIOS. A
   locomoção e o validador valem sem mudança.
2. **Rabo:** parte `tail`, filha do tronco. O `Animator` já a balança (parado, andar, corrida, golpe, salto).
   - Nasce DENTRO da base das costas, acima do topo das coxas (`AcapelaTest.tailIsAttachedToTheBody`).
   - A sonda mede `membro × rabo` (≤ 1 voxel) e `rabo × chão` (zero).
3. **Contorno de cabeça franjada:** triângulos de lado reto (`tri`), não cadeia de círculos, que serrilha. Ficam
   atrás da placa do rosto (z ≤ 4), para não cobrir os olhos.
4. **Aceno:** cabeça larga e franjada não aceita o aceno compartilhado (a mão atravessa a bochecha). Use um perfil
   próprio, igual ao `npc_vila` em repouso e balanço:
   - cotovelo para a frente;
   - mão à frente das franjas;
   - pose de passagem (`wave_out`) na subida.
5. **Reamostragem abaixo de 0,9:** use `scale_keep`. Traço do rosto (pupila, brilho, boca, presa) vence o voto. Com
   "o último vence", a linha da boca e as presas sumiam.
6. **Proporção:** chibi entre 2,0 e 2,35 cabeças (abaixo da faixa congelada dos heróis).

## Rodada 06.1 — refinar NPCs sem virar recoloração

1. Pergunta de aceite: **pintadas de preto, ainda se reconhecem?** Capture `set=trio`/`set=elenco` com
   `sil=1` de frente, perfil e costas e uma versão em cinza (valores). Diferença tem de ser de **forma**:
   comprimento/pontas do cabelo, volume da coroa, franja, mangas, saia, acessório que sai do contorno.
2. Não persiga a sobreposição de silhueta inteira (IoU) — corpo e braços iguais a mantêm alta. Teste as
   assinaturas do desenho (`VilaNpcTest.silhouetteSignaturesStayStructural`).
3. Dê a cada NPC um **papel na vila** e um objeto que faça sentido nele (estalajadeira: avental + chaves;
   herbalista: avental de sálvia, bolsa de ervas, raminho atrás da orelha; mensageira: bolsa de cartas).
   Produza duas alternativas pequenas quando houver dúvida (`npc(key, role=...)` → `assets/studies/`) e
   escolha pela captura.
4. Mecha ou trança longa à frente do peito: parte `lock` presa ao **tronco**.
5. Personagem escura: separe valor de roupa e cabelo (Δ luminância ≥ 25), pontos de destaque quentes,
   sem emissivo; confira na sombra real da cena (`hero=<rig>` em `mode=scene`, sob o salgueiro).
6. Atuação por personagem em `meta.acting` (postura e andamento), nunca cópia de perfil.
7. Antes/depois: `tools/review61.sh antes|depois` (mesmas câmeras) — modelo para próximas rodadas.

Personalização de jogador (cabelo/chapéu/presets): skill `character-customization`.
