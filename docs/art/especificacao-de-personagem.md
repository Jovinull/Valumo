# Especificação de personagem (direção E)

Como construir um herói ou NPC humanoide na linguagem consolidada. Os números exatos deste guia são os
dos **três protagonistas**; um NPC herda a linguagem com a liberdade descrita em
[contrato-de-qualidade.md](contrato-de-qualidade.md) ("NPCs humanoides futuros"). Proporções:
[sistema-de-proporcoes.md](sistema-de-proporcoes.md). Paleta: [paleta-e-materiais.md](paleta-e-materiais.md).
Código: `tools/charlib_e.py` (biblioteca) e `tools/chars_e.py` (os três arquétipos como exemplo).

## 1. Ordem de construção (não pule etapas)

1. **Silhueta** — esboce em preto: cabelo/chapéu, arma, um volume nas costas ou nos ombros. Capture
   com `sil=1` de frente e de perfil, ao lado dos heróis existentes (`mode=lineup`). Precisa ser
   distinguível dos três atuais.
2. **Volumes** — `torso()`, `arm()`, `leg()`, `skull()` com as cores-base.
3. **Cores e materiais** — no máximo 5 matizes; 1 destaque.
4. **Identidade** — 1 ou 2 acessórios (cachecol, broche, aljava).
5. **Ornamentos** — só se a captura reduzida (`tamanho-reduzido`) continuar limpa.

## 2. Rosto

| Elemento | Regra |
|---|---|
| Placa | plana em z = 6 (`skull()` corta e preenche); **todo** decalque de rosto vai nela |
| Olho | 3 × 5: cílio / brilho+pupila / íris+pupila / íris / íris clara; brilho do lado da luz nos dois olhos |
| Íris | cor própria do personagem (verde, azul, violeta…), 3 tons (`B.light`/`B.shadow`) |
| Sobrancelha | 3 voxels, tom do cabelo escurecido 1,6–2,6×, 2 acima do cílio |
| Boca | 2 voxels `#a9524a`, cantos em pele escura (sorriso) |
| Blush | L de 3 voxels `#f2988a` ao lado do canto externo |
| Nariz | não usar |
| Cílio externo | opcional (`lash=True`) para dar traço próprio (exploradora) |

Expressões obrigatórias para heróis: `neutral`, `joy`, `determined`, `surprise`
(`head_with_expressions`). Elas só repintam a placa: mesmas posições de voxel, mesma origem.

**Atuação por personagem (rodada 06, pedido do proprietário):** até a 05.1 as três expressões não neutras eram
o MESMO carimbo nos três heróis (só a cor mudava) — o proprietário achou "cópia uma da outra". Agora o estilo
de rosto tem `acting` (`charlib_e.act_face`), e cada temperamento reage do seu jeito; o rosto neutro não passa
por aí (identidade aprovada, idêntico byte a byte):

| `acting` | Herói | Alegre | Determinada | Surpresa |
|---|---|---|---|---|
| `bold` | aventureiro | piscadela + sorrisão aberto | sobrancelhas em V grossas + dentes cerrados | olhos arregalados com íris + boca aberta larga |
| `cool` | exploradora | olhos em meia-lua + sorriso torto | mira: um olho apertado, testa franzida de um lado | cética: uma sobrancelha alta, boca de lado |
| `soft` | maga | olhos ^ ^ + sorriso de gato + blush forte | serena: pálpebra bem baixa, boca firme | encantada: brilho duplo, sobrancelha em telhadinho, "o" pequeno |

Regras que continuam: mesmas colunas de olho, brilho do lado da luz, blush, sem nariz, boca dentro de
x −3…2 e y ey−2…ey−1. Personagem novo escolhe um `acting` existente ou cria outro — **não** use o desenho
comum antigo. Teste: `QualityTest.expressionsActDifferentlyPerHeroInsteadOfOneTemplate` (cada expressão muda
≥ 10 voxels e dois heróis não podem pintar o mesmo desenho: Jaccard < 0,6). A cartógrafa tem rosto próprio
(`npcs_e.face`): na determinada ela *avalia* (uma sobrancelha erguida, boca puxada de lado).

## 3. Cabelo

1. `hair_base(sk, cor, bang, nape, thick)` — casca; `bang[x]` define a franja (altura relativa a
   NECK) e deve ficar ≥ NECK + 10 sobre as sobrancelhas.
2. `hair_locks(...)` — 3 a 6 mechas grossas (raio 2–3 → 0,7) que **saem** do contorno: topo, nuca,
   laterais. Franja: 2–3 mechas que emolduram o rosto.
3. `hair_finish(...)` — limpa o que invadiu o rosto (`face_guard`), fecha buracos, pinta 3 tons +
   brilho.
4. `hair_strands(...)` — sulcos pintados nas costas (período 0,85–1,0 rad).

Capuz e chapéu são partes de cabeça (capuz) ou parte `hat`; com capuz, casca de cabelo fina (1,1) e
forro **escuro** na borda da abertura.

## 4. Corpo, roupa e equipamento

| Peça | Função | Detalhe |
|---|---|---|
| `torso(col, hem)` | túnica em pera; barra abre sobre as coxas | `belt()` com fivela dourada |
| `arm(sleeve, glove, cuff)` | pose A leve, ombro arredondado, mão grande | `bell=True` para manga-sino |
| `leg(pants, boot, cuff, sole)` | coxa curta, bota de cano com dobra | `tall=True` para bota alta |
| `sword()` | lâmina em folha, guarda curva, punho no pivô | metal só na lâmina/guarda |
| `staff()` | cabo nodoso, meia-lua, cristal emissivo | topo **abaixo** da aba de chapéu largo |
| `bow()` | recurvo, 3 voxels de largura, corda creme | parte `bow` filha de `arm_l` |

Orçamento: ≤ 9 mil faces por herói (medido: 5,9 / 6,2 / 8,0 mil). Mochilas, aljavas e bolsas são
parte do tronco (rígidas). **Capas longas não** (ver [guia-de-animacao.md](guia-de-animacao.md)).

## 5. Rig e metadados

Partes do `biped` (contrato completo em [contrato-de-assets.md](contrato-de-assets.md)):
`torso, head, arm_l, arm_r, leg_l, leg_r` e, nos heróis da E desde a rodada 03, **`farm_l`/`farm_r`**
(antebraço, filho do braço, pivô no cotovelo y = 17) e **`shin_l`/`shin_r`** (canela, filha da perna,
pivô no joelho) — gerados por `charlib_e.limb_parts`. Opcionais: `weapon` (filha de `farm_r`), `bow`
(filha de `farm_l`), `arrow` (filha de `bow`, só aparece no ataque), `tail` (filha da cabeça), `hat`,
`skirt_l`/`skirt_r` (metades do manto), `cape`/`cape_lo`.

| `meta` | Uso |
|---|---|
| `weaponMode` | `sword` (padrão), `staff` (mantém vertical; ataque = conjurar), `bow` (ataque = puxar e soltar) |
| `weaponRest` | inclinação de repouso da arma (rad); cajado 0 |
| `footPlant` | `true`: o quadril desce com o arco da perna (pés no chão) |
| `lean` | postura: + para frente (exploradora 0,045), − para trás (maga −0,03) |
| `tailGain` | ganho do balanço da trança/rabo (tranças longas 0,45) |
| `faces` | contagem de faces expostas (HUD do modo LINHA) |
| `joints` | `true`: rig com cotovelo/joelho → animador articulado com IK de pé (`Animator.jointed`) |
| `ankle` | altura do tornozelo (voxels) usada pelo IK para apoiar a sola |

## 6. Diferenciar arquétipos (o que fizemos e o que um novo personagem deve fazer)

| | Aventureiro | Exploradora | Maga |
|---|---|---|---|
| Silhueta | topete-onda (H2) + mochila com saco de dormir | capuz abaixado no ombro esquerdo + faixa com pontas soltas + pena (C3) | chapéu de aba média (M1) + copa torta + cajado em Y |
| Cabelo | ferrugem, topete que termina em cacho, coroa lisa | mel, à mostra, trança | lavanda, chanel com tufos, separado da aba pelo avesso escuro |
| Paleta | verde-petróleo + mostarda | azul-petróleo `#2f6f73` + terracota `#c8643c` + creme | ameixa + ouro |
| Equipamento | espada, ombreira de couro | arco recurvo, aljava, flecha, bússola de bronze | cajado de galhas com cristal e pingente |
| Rosto | olho `tilt`, sobrancelha grossa, meio sorriso | olho amendoado 3 × 4, sobrancelha fina, sardas | olho redondo, brilho de 2 voxels |
| Postura | neutra | inclinada à frente | levemente para trás |
| Íris | verde | azul | violeta |

Um personagem novo precisa diferir dos existentes em **pelo menos três** linhas desta tabela, sendo
uma delas a silhueta.

## 7. Rodada 03: alternativas estudadas (uma variável por vez)

Os estudos ficam em `assets/characters/r3/` (gerados por `tools/chars_e_study.py`) e no modo LINHA
(`r3-cabelo`, `r3-capuz`, `r3-chapeu`, `r3-cajado`, `r3-rosto`). Parâmetros de construção:
`chars_e.adventurer(hair_style="r02|h1|h2|h3")`, `ranger(hood_style="c0|c1|c2|c3")`,
`mage(hat_style="m0|m1|m2", staff_style=...)`, `face=` em todos. Tabelas de decisão em
[../experiments/rodada-03-estudos.md](../historico/experimentos/rodada-03-estudos.md).

Regras aprendidas:
- Duas pontas finas e paralelas no alto da cabeça leem como antenas; uma massa única com cacho não.
- Capuz levantado em arqueiro com arco remete a arquétipos conhecidos; capuz abaixado + faixa não.
- Aba de chapéu: o avesso escuro (1 voxel) separa aba e cabelo melhor que afastar a aba.
- Cajado: o topo precisa de silhueta própria (Y, gancho, anel) e não pode passar atrás da cabeça
  em repouso — braço aberto ~0,45 rad.
- Cotovelo/joelho: o corte fica na manga/barra da calça e a tampa de dobra (`cap`) tem a cor da
  roupa; corte no meio de área lisa vira fresta visível.

## 8. Rodada 04: itens segurados, mãos e volumes

- Todo item segurado (espada, lâmina longa, cajado, arco, broquel) é modelado **vertical** (+Y = ponta
  ou topo, +X = fio/lado) com o **pivô no centro da mão**; é filho do antebraço da mão que o segura.
- `meta.hands` e `meta.volumes` acompanham todo herói (gerados por `charlib_e.body_volumes` e
  `*_volumes`): sem eles o validador de armas não roda. Ajuste volumes à mão só quando o corpo mudar
  (chapéu novo, mochila etc.).
- `meta.weaponProfile` liga o rig ao perfil de animação. Kits de estudo: `adventurer(kit="shield"|"longblade")`.
- Limites de proporção que afetam equipamento (medidos): braço 10,6 voxels do ombro à empunhadura;
  tronco ~6 voxels de raio; duas mãos no mesmo cabo só numa guarda assimétrica (ver guia de animação,
  rodada 05); flecha ≤ 15 voxels.

## 9. Rodada 06: primeiro NPC (cartógrafa, `tools/npcs_e.py`, candidato)

Exemplo de NPC humanoide com proporção PRÓPRIA dentro da faixa do contrato (2,0–3,0 cabeças):

| Medida | Protagonistas | Cartógrafa |
|---|---|---|
| Cabeças | 2,35–2,60 | 2,71 |
| Pescoço / quadril / ombro (y) | 26 / 11 / 23,5 | 29 / 14 / 26,5 |
| Crânio | 16 × 16 × 15, rosto em z = 6 | ~14,4 × 14,6 × 13, rosto em z = 5 |
| Olho | 3 × 5 com íris | 2 × 3 âmbar + traço de pálpebra |
| Altura | 1,18–1,25 u | 1,15 u |

Como foi feito: esqueleto parametrizado no próprio módulo (constantes `HIP`, `NECK`, `SHOULDER`…), usando só
as primitivas da biblioteca (`sblob`, `stack`, `lock`, `shade`, `split_limb`, `connect_path`, `prune_specks`);
rig com as mesmas partes dos heróis (o `Animator` lê os pivôs do `rig.json`), mais `skirt_l/r` (abas do
casaco), `pack` (tubo de mapas, com atraso secundário) e `tool` (bússola na mão direita, perfil
`assets/anim/cartografa.json`). `meta.npc = true`.

Iterações e defeitos encontrados (não repetir):
1. Cabelo escuro com franja reta e casca grossa → **capacete** (proibido). Solução: tom mais claro, casca
   fina (1,5), volume em mechas e franja diagonal partida.
2. Óculos de aro inteiro de latão sobre os olhos → dominavam o rosto. Solução: óculos **empurrados para o
   cabelo** (acessório que entra na silhueta sem cobrir o olhar).
3. Crânio com `p_lo` alto → cabeça-cubo. Solução: `p = 2,4`, `p_lo = 2,3`.
4. Silhueta frontal retangular → coque lateral saindo do contorno e tubo de mapas aparecendo ao lado da
   cabeça. Verifique SEMPRE em `sil=1` ao lado dos heróis.
5. Traços finos em diagonal (lápis) perdem pedaços na limpeza → `connect_path`.

## 10. NPCs da vila — Kelyra, Índira e Súvia (`tools/npcs_trio.py`, candidatas; refeitas na rodada 06.1)

Um construtor só (`npc(key, **override)`) e três fichas em `SPECS`; esqueleto do NPC da cartógrafa (importado
de `npcs_e`). Gesto compartilhado `acenar` (`assets/anim/npc_vila.json`) com **atuação por personagem**
(`meta.acting`, ver `guia-de-animacao.md`).

| | Kelyra — estalajadeira | Índira — herbalista | Súvia — mensageira |
|---|---|---|---|
| Silhueta em preto | bloco longo de cabelo até a cintura + saia do casaco abrindo em sino | cabeça redonda com nuca curta, ombros bufantes, pernas retas | orelhas de raposa, coroa volumosa, pontas abertas nos ombros |
| Cabelo | liso, escuro, franja reta; **mecha varrida à frente do ombro esquerdo** (parte `lock`), lado direito atrás do ombro; ponta em **V** nas costas | claro, rente, franja em cortina, mechas curtas emoldurando o rosto, **trança de gomos** sobre o ombro direito (parte `lock`) com fita | escuro com pontas acobreadas, franja varrida, coroa volumosa (`crown` 2,0) |
| Roupa | corpete ameixa, gola em V rasa sobre blusa, broche de gema, casaco de abas longas, **avental claro** | blusa creme de **mangas bufantes**, jardineira índigo com alças, **avental de sálvia** com peitilho, sem abas | **corpete vinho**, mangas ardósia, gola redonda creme, cinto e punhos de **cobre**, abas curtas |
| Objeto do papel | molho de chaves no quadril esquerdo | bolsa de ervas com folhas no quadril direito + raminho de lavanda atrás da orelha | bolsa de cartas com aba de cobre e carta à mostra |
| Atuação | confiante: queixo erguido, peso num quadril, aceno rápido (0,9×) | doce: cabeça inclinada e baixa, aceno lento (1,25×) | travessa: quadril para o outro lado, troca de apoio inquieta, aceno ligeiro (0,8×) |

Alternativas de papel da Kelyra (estudos em `assets/studies/kelyra_*`): **estalajadeira** (avental + chaves,
escolhida) × tecelã (fita métrica no pescoço + tesoura). A fita lia como escada listrada no peito; o avental
dá uma forma clara na cintura legível à distância.

Lições (rodadas 06 e 06.1):
1. Três fichas no mesmo construtor tendem a virar recoloração. O que separa é **forma**: comprimento e
   desenho das pontas, volume da coroa, franja, mangas, saia. Teste estrutural: `VilaNpcTest`
   (`notRecolorsOfEachOther`, `silhouetteSignaturesStayStructural`).
2. A métrica de sobreposição de silhueta inteira (IoU) engana: Kelyra × Índira de frente ainda dão 0,92
   porque corpo e braços são iguais. Teste as **assinaturas** do desenho (comprimento do cabelo de trás,
   largura do ombro, acessório fora do contorno), não persiga um número com ruído.
3. Mecha longa à frente do peito vai para uma parte **`lock` presa ao tronco**: na cabeça, ela atravessa o
   peito quando a cabeça gira; o topo some sob o cabelo da cabeça.
4. Personagem escura some na sombra se roupa e cabelo têm o mesmo valor. Separe **valor** (mangas ardósia ×
   cabelo quase preto, Δ de luminância ≥ 25) e espalhe pontos de destaque (cobre, creme) — sem emissivo.
   Teste: `suviaClothingReadsAgainstHerDarkHair`.
5. Queixo: a placa do rosto preenchida como retângulo deixava a cabeça quadrada de frente (cartógrafa).
   `jaw_half(y)` afina as duas linhas do queixo (meia-largura 3,6 / 4,6 / 5,6).
6. Franja baixa em cabelo escuro engole a sobrancelha (deixe uma linha de testa); mechas que descem à testa
   deixam furo de pele (apague-as abaixo da linha da franja e preencha coluna a coluna).
7. Adorno: "+" de 5 voxels lê como cruz; coração precisa de 5 de largura. Gomos de trança precisam de
   valores alternados para ler contra roupa clara.

Prévia rápida sem GPU: `tools/vox_preview.py <rig ou .vox> saida.png` (frente, perfil, costas).
