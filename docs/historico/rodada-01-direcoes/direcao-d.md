# Direção D — Stylized Voxel RPG

Guia da direção D. Fontes: `tools/chars_d.py`, `tools/creatures_d.py`, `tools/env_d.py`
(regenerar com `python3 tools/build_assets.py chars_d creatures_d env_d`). Luz e pós:
`StyleProfile.d()`. Tudo aqui foi conferido em capturas de `tools/snap.sh`, salvo onde a seção
"Limitações conhecidas" diz o contrário.

## 1. Princípios

1. **Voxel fino, leitura grossa.** O personagem usa voxel de 1/44 u (quase o dobro de A), mas as
   formas continuam grandes: cabeça, mecha, bota, ombreira. O detalhe fino serve ao rosto e às
   ferragens, nunca à textura de ruído.
2. **Pequeno aventureiro, mundo grande.** 2,55 cabeças, pernas com 27% da altura, cabeça de
   18 voxels de largura (20–22 com cabelo). Nada de proporção humana.
3. **Cor pintada por voxel.** Toda rampa desvia a matiz: sombra fria (azul/violeta), luz quente
   (amarelo). `shade()` estima a normal de cada voxel de superfície e escolhe o degrau da rampa; o
   shader soma sol, AO, rim e especular por cima.
4. **Cada peça desenha silhueta.** Capa, ombreira assimétrica e espada (aventureiro); trança,
   aljava, pena e arco (patrulheira); chapéu de aba larga com ponta caída, manto em sino e cajado
   inclinado (maga).
5. **Materiais com papel definido.** Metal só em ferragens; emissivo só em pontos focais; folhagem
   em tudo que deve balançar.
6. **Riqueza sem ruído.** Detalhe concentrado (janelas, runas, lanternas, cogumelos), descanso
   entre os focos. Variação de cor em manchas de 2–4 voxels, nunca em xadrez de 1 voxel.

## 2. Paleta

| Uso | Hex |
|---|---|
| Pele / sombra / luz | `#f6c9a0` / `#d48a78` / `#ffe6c8` |
| Aventureiro: cabelo, túnica, barra, capa, forro, calça, íris | `#e2a23c` `#22828c` `#8fe0cf` `#cf3340` `#f0b848` `#39415f` `#2fae7c` |
| Patrulheira: cabelo, capuz, barra-folha, colete, meia, faixa, íris | `#c9502c` `#4f9a3e` `#b9e06a` `#7b4a2c` `#2f5a4a` `#35c0b0` `#3d9be8` |
| Maga: manto, chapéu, cabelo, debrum, íris | `#5a43b0` `#4a3698` `#dcd2f2` `#f2c14a` `#c24fd0` |
| Couro (escuro/base/claro), creme | `#5c3622` `#8a5632` `#b47a46` `#f1e4c6` |
| Metal: ouro, ouro escuro, aço, aço escuro, fio da lâmina | `#f2c14a` `#c28a2a` `#93a3bd` `#66758f` `#c9d6e8` |
| Emissivos: cristal/runa, runa clara, violeta, luz quente (escura/base/clara), poção | `#6fe8ff` `#d8fbff` `#b48aff` `#ff9a3a` `#ffc65a` `#ffe9a8` `#ff6fa0` |
| Folhagem carvalho (sombra → luz) | `#1d5a52` `#27774c` `#3b9645` `#5db342` `#8fcd49` `#c4e562` |
| Folhagem pinheiro | `#123f4a` `#18584e` `#22724f` `#34904f` `#5aae58` `#9ad27a` |
| Folhagem bétula | `#3f7f3a` `#5f9f38` `#86bd3a` `#aed545` `#d3e85e` `#eef58a` |
| Casca, pedra (escura/base/clara), musgo | `#6b4a36` `#6f7888` `#8d95a3` `#bcc3c9` `#5f9f3c` |
| Arquitetura: enxaimel, estuque, ardósia do telhado, veneziana | `#57382a` `#f1e2c0` `#3a6b8e` `#3f8f8a` |

Telhado azul-ardósia contra janelas âmbar é o contraste-assinatura da direção (frio/quente).

## 3. Personagens (rig `biped`, voxel 1/44 u)

Corpo comum: pernas 14, tronco 17, crânio 18 × 18 × 16 (L × A × P) + 2 de casca de cabelo.
`headsTall` = (31 + 20) / 20 = **2,55**; tufos, pena e chapéu ficam fora da razão (nota em `meta`).
Rosto: placa plana em z = 6; olhos 4 × 5 (cílio, pupila, íris em degradê, brilho do mesmo lado nos
dois olhos), sobrancelha, blush, boca de 2 voxels. Braço 5 × 5 na manga, 4 × 4 no antebraço, luva
com punho virado. Bota de cano com dobra e bico arredondado, mais larga que a perna.

| Personagem | Caixa (vox) | Altura | Faces expostas | Partes além do corpo |
|---|---|---|---|---|
| `adventurer` | 26 × 52 × 21 | 1,18 u | 8 272 | `weapon` (espada 2 de espessura, sulco, fio claro, gema emissiva), `cape` (2 camadas: vermelho/forro dourado, barra rasgada) |
| `ranger` | 28 × 54 × 24 | 1,23 u | 7 910 | `weapon` (arco recurvo), `tail` (trança em gomos com laço); aljava e capuz baixado no tronco |
| `mage` | 36 × 55 × 31 | 1,25 u | 10 408 | `hat`, `skirt` (sino com runas emissivas), `staff` (filho rígido de `arm_l`) |

Orçamento: ≤ 12 000 faces por personagem.

## 4. Criaturas

| Criatura | Anim | Voxel | Altura | Faces | Ideia |
|---|---|---|---|---|---|
| `pompom` | hopper | 1/40 | 0,78 u | 3 312 | bolinha felpuda creme/caramelo, orelhas-folha de ponta turquesa, broto na cabeça |
| `lumi` | floater | 1/40 | corpo ~0,75 u, flutuando | 3 456 | lanterna viva: miolo emissivo com rosto, telhadinho, contas orbitando, cauda de chama |
| `fungo` | hopper | 1/36 | 0,92 u | 4 590 | cogumelo rabugento: chapéu magenta com espinhos, olhos emissivos, braços de cipó |
| `tortuga` | quadruped | 1/22 | 2,27 u | 13 678 | tartaruga mansa com jardim no casco (árvore, flores, cogumelos luminosos, lanterna de pedra) |

## 5. Regras de forma — vegetação

- Copa = 5–12 lóbulos achatados (superelipsoide p ≈ 2,3) + tufos pequenos na superfície.
  Cada lóbulo tem gradiente próprio (barriga escura, topo claro) somado ao gradiente global.
- Tronco afilado, curvo, com base alargada e raízes que mergulham; casca em estrias verticais.
- Pinheiro: 7 saias sobrepostas de borda recortada, pontas caídas e mais claras.
- `tree_oak2`: copas em andares ("guarda-sol"), verde mais azulado, flores claras esparsas.
- `tree_big`: contrafortes, nicho com cristais, cogumelos-prateleira, lanternas e cipós pendentes.
- Plantas pequenas: lâminas de 1 voxel com curvatura; cada flor tem silhueta própria (papoula,
  margarida, espiga, tufo branco).

## 6. Regras de forma — arquitetura e pedra

- Casa: fundação de alvenaria, estuque com enxaimel de 2 voxels e mãos-francesas, telhado íngreme
  e côncavo em fiadas de 2 voxels com junta deslocada, cumeeira, água-furtada, chaminé, alpendre,
  janelas emissivas com pinázios, venezianas e floreiras, hera num canto.
- Pedra: superelipsoide cortado por planos, rampa por normal, musgo em manchas só no topo.
- Santuário: estrado circular, monólito com glifo, pilares em ruína, cristal flutuante.

## 7. Materiais

| Material | Onde | Regra |
|---|---|---|
| fosco | quase tudo | rampa de 5–8 tons por cor-base |
| metal | lâmina, ombreira, fivelas, ferragens | bases de valor médio — aço claro estoura no bloom |
| emissivo | gema, poção, runas, cristais, janelas, lanternas, fogo, olhos do fungo | área pequena; cores saturadas e não brancas quando houver detalhe por cima |
| folhagem | copas, arbustos, grama, hera, cipós, broto do pompom | tudo que deve balançar e deixar passar luz |

## 8. Escalas e orçamento

1/12 u em árvores, casa, poço, santuário, píer, `rock_l`; 1/14 u nos arbustos; 1/16 u em objetos
médios; 1/20–1/24 u nas plantas pequenas; 1/4 u na nuvem. O build imprime as faces expostas de cada
prop e falha se estourar o limite. Mais pesados: `tree_big` 32 482 (limite 45 mil), `house` 20 706
(40 mil), `tree_oak2` 7 360, `tree_oak` 6 988, `tree_pine` 6 352 (9 mil), `shrine` 4 292,
`rock_l` 3 168, arbustos ~830 (2 mil), flores e tufos 82–142 (150).

**Volumes são maciços.** O mesher não elimina faces voltadas para voxels ausentes, então esvaziar
o miolo expõe a face interna da casca e quase dobra a contagem (medido: aventureiro 12,7 mil oco
contra 8,3 mil maciço). `hollow()` ficou como no-op documentado.

## 9. Restrições

- Sem proporção realista; `headsTall` entre 2,4 e 2,7.
- Sem ruído de 1 voxel em áreas grandes; sem emissivo decorativo fora de pontos focais.
- Máximo de 255 cores por `.vox`, respeitando as faixas de material (metal 24, emissivo 20,
  folhagem 30).
- Tudo original e gerado por script determinístico (dois builds seguidos dão arquivos idênticos).

## 10. Limitações conhecidas

- **Costas escuras.** Com o sol de frente, cabelo e capa vistos por trás caem para oliva/vinho
  escuro; o cabelo loiro perde saturação na sombra.
- **Ombreira e lâmina estouram.** Em alguns ângulos o especular + bloom transforma o aço numa
  mancha branca, mesmo com as bases escurecidas.
- **Arco fraco.** O animador inclina toda `weapon` ~60° para a frente; o arco fica em escorço e lê
  como "vara com corda". É o elemento mais fraco dos três personagens.
- **Cajado rígido.** Para ficar em pé, o cajado é filho de `arm_l` e balança como pêndulo no
  walk/run; a maga ataca com a mão direita vazia. Falta um modo "cajado" no animador.
- **Padrão xadrez no mantelete da maga** numa pose de ataque (provável acne de sombra em degraus
  de 1 voxel); não investigado.
- **Curvas em degraus.** Copas, chapéus e o casco mostram curvas de nível; é inerente ao voxel sem
  suavização.
- **Silhuetas em preto chapado não foram testadas** — a distinção foi julgada nas capturas
  coloridas (frente, costas, 3/4 e poses de walk/run/attack).
- **Escalas mistas.** Arbustos e árvores (1/12–1/14 u) ficam visivelmente mais grossos que o
  personagem (1/44 u) em close.
- Altura da patrulheira (1,23 u) e da maga (1,25 u) encosta no teto do contrato por causa da pena
  e do chapéu. A copa da `tree_big` tem ~6,5 u de diâmetro (o contrato sugere ~5).
- `gradle test` não foi rodado nesta rodada (instrução de não compilar).
