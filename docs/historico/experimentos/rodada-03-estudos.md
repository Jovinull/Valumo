# Rodada 03 — estudos controlados (personagens, terreno, vegetação, criaturas, luz)

Regra de todos os estudos: **uma variável por vez**, mesma luz (preset "dia" da E), mesma câmera tele
do modo LINHA (FOV 16°), mesmo chão e pose; ângulos girando os modelos (`myaw`). Código dos estudos:
`tools/chars_e_study.py` (personagens), `tools/env_e.py:oak_study/build_studies` (carvalho),
`tools/creatures_e.py:tortuga(shell_style)` (carapaça), `World.quantize/buildChamfer` (relevo).
Capturas em `output/visual-review/round-03/`. Escolhas = **propostas** para aprovação do proprietário.

## 1. Cabelo do aventureiro (rosto, roupa e equipamento iguais)

| Opção | Desenho | Frente | Costas | Silhueta | Veredito |
|---|---|---|---|---|---|
| R02 | topete em gancho + 2 pontas na coroa | boa | **as pontas leem como antenas** | recortada | reprovado |
| H1 | natural assimétrico: franja varrida, coroa macia, redemoinho | limpa | limpa | "capacete arredondado", sem assinatura | reprovado |
| **H2** | topete refinado: onda grossa que termina em cacho; coroa lisa | assinatura preservada | volume único e limpo | topete lê em preto | **escolhido** |
| H3 | vento: mechas para trás + rabinho amarrado | expressiva de perfil | ruidosa | assimétrica forte | reprovado (costas) |

Evidência: `personagens/estudo-cabelo-aventureiro.png`, `-silhueta.png`. Altura e proporção iguais (2,44).

## 2. Capuz da exploradora (rosto e corpo iguais)

| Opção | Desenho | Problema | Veredito |
|---|---|---|---|
| C0 | capuz verde pontudo (rodada 02) | arquétipo previsível ("capuz verde + arco") | referência |
| C1 | C0 refinado: menor, abertura maior | continua no mesmo arquétipo | reprovado |
| C2 | capuz de folha azul-petróleo, ponta enrolada **para a frente**, borda terracota | de frente a ponta some; forma muda pouco | reprovado |
| **C3** | capuz **abaixado** e assimétrico (rolo no ombro esquerdo), cabelo à mostra, faixa terracota amarrada com pontas soltas, pena, bússola de bronze | 1ª versão virou "capacete loiro" → casca de cabelo mais fina | **escolhido** |

Paleta nova: azul-petróleo `#2f6f73`/`#24575c` + terracota `#c8643c`; túnica creme mantida.
Evidência: `personagens/estudo-capuz-exploradora.png`, `-silhueta.png`.

## 3. Chapéu e cajado da maga

| Opção | Aba (raio x/z) | Copa | Separação cabelo × aba | Veredito |
|---|---|---|---|---|
| M0 (R02) | 12,2 / 11,8 | 7 níveis | nenhuma: aba pesada colada ao cabelo | referência |
| **M1** | 10,4 / 10,0, erguida 1 voxel | 6 níveis | avesso escuro da aba | **escolhido** |
| M2 | 11,2 / 10,8 virada à esquerda | 7 níveis, estreita | avesso escuro | chama atenção demais |

Cajado: o de meia-lua (R02) virou **galhas** — cabo nodoso que se abre em dois galhos curvos abraçando
um cristal grande, aro dourado e pingente (`charlib_e.staff_fork`): silhueta "Y com luz" que lê na
câmera de exploração. Em repouso o braço abre e o cajado compensa giro em X **e Z** (antes inclinava
para dentro e sumia atrás da cabeça). Manto-sino com raio menor (crescimento 2,6 → 2,2).
Evidência: `personagens/estudo-chapeu-maga.png`, `estudo-cajado-maga.png`.

## 4. Rostos

A gramática não muda (placa plana, olho de 3 de largura, brilho do lado da luz, sobrancelha 2 acima do
cílio, boca de 2, blush). Variam por personagem (`charlib_e.FACE_DEFAULT`):

| | Olho | Sobrancelha | Boca | Extras |
|---|---|---|---|---|
| Aventureiro | `tilt` (canto externo erguido) | `bold` | `smirk` (meio sorriso) | — |
| Exploradora | `almond` (3 × 4) | `thin` | `small` | sardas, cílio externo |
| Maga | `round` (3 × 5) | `flat` | `small` | brilho de 2 voxels, cílio externo |

Defeitos encontrados e corrigidos: pálpebra escura do `almond` dava cara de sono (removida);
sobrancelha `thin` ficava sob a franja (descida 1 voxel); `arch` virava 3 pontos soltos (trocada).
As 4 expressões continuam funcionando nos três. Evidência: `rostos-expressoes-close.png`,
`rostos-expressoes-escala-exploracao.png`.

## 5. Relevo

| | Técnica | Naturalidade | Custo | Chunks |
|---|---|---|---|---|
| T0 (R02) | degrau a cada 1/8 u seguindo o relevo | curvas de nível regulares | base | trivial |
| **T1** | limiar do degrau com ruído suave de baixa frequência (±0,45 voxel) | bordas irregulares; morro distante ainda listrado | = T0 | trivial (função de posição) |
| T2 | patamares de 0,4 u + rupturas de pedra | intenção geológica; faixas de pedra repetem em encosta uniforme | = T0 | trivial |
| T3 | T1 + **chanfros**: rampa de 0,6 voxel sobre cada degrau de 1 voxel em trechos retos de ≥ 3 colunas, com tampas nas pontas | rampas suavizam as listras ao longe; perto ≈ T1 | + malha de chanfro | local (vizinhos 4) |
| T4 | T2 + chanfros | bom de perto; listras de pedra ao longe | idem | idem |

**Escolha: T1** (`World.E_TERRAIN = 1`); T3 continua disponível (`terrain=3`) como opção.

Histórico honesto da decisão: a primeira escolha foi o T3, mas as capturas em que ela se baseou **não
mostravam os chanfros** (ordem de vértices invertida → todas as rampas eram descartadas pelo
*backface culling*; T3 e T1 diferiam em 0,1% dos pixels). Corrigido o bug, o T3 com rampas em todo
degrau mostrou um defeito novo: **dentes** triangulares em toda borda de degrau em zigue-zague
(pontas de rampas curtas), bem visíveis na encosta da casa na câmera de apresentação. Tampas nas
pontas e triângulos nas quinas externas não resolveram; exigir trecho reto de ≥ 3 colunas resolve
quase tudo, mas aí o ganho sobre o T1 fica pequeno (só as listras dos morros distantes) e não paga a
malha extra. Evidência: `terreno/comparacao-*.png` e `terreno/chanfro-dentes.png`.## 6. Carvalho de referência

| | Copa | Leitura |
|---|---|---|
| O1 | novelos lisos (B) | nuvem; sem folhagem |
| O2 | novelos + tufos (E rodada 02) | encaroçada |
| **O3** | 3–4 **massas** de base achatada com avesso escuro, leve variação de valor entre massas, **franja de cachos** pendentes em ritmo regular | guarda-chuva com tronco e galhos visíveis; lê como folhagem sem ruído |

Aplicado a `tree_oak`, `tree_oak3`, `tree_oak4`, `tree_oak2` (`env_e.crown_masses`); pinheiro, bétula e
árvore-marco mantêm forma própria. Compatível com a fusão de faces (vento afim). Evidência:
`vegetacao/estudo-carvalho-*.png`, `floresta-antes-depois.png`, `panorama-antes-depois.png`.

## 7. Carapaça da tortuga

| | Ecossistema | Risco de semelhança | Veredito |
|---|---|---|---|
| S1 | gramado + árvore + cogumelos (R02 refinado) | alto (tartaruga com árvore nas costas) | referência |
| S2 | placas de ardósia, musgo em manchas, **geodos** de cristal | baixo; mas "criatura com cristais" é genérico | alternativa regional |
| **S3** | **sem árvore**: prateleiras de fungo-coral em camadas, frondes em espiral, cápsulas luminosas em hastes, marco de pedra | baixo; silhueta própria | **escolhido** |

Evidência: `criaturas/estudo-carapaca-tortuga.png`, `-silhueta.png`.

## 8. Fim de tarde

Medido no gramado e nas copas do mesmo enquadramento (`iluminacao/tarde-antes-depois.png`):
matiz mediano das copas R02 **69,8°** (faixa oliva 60–75°) → R03 **86,5°**; gramado 81° → 88°; a
saturação **não** subiu (0,50 → 0,45). A correção foi de luz (céu ambiente azul, matiz de sombra 0,085 →
0,045, tint sem corte de azul, rim quente 0,30), não de saturação.
