# Direção A — Miniature Fantasy

Voxel clássico: pequenos aventureiros num mundo de fantasia de cubos bem definidos e cores vivas.
É a direção mais próxima da tradição do gênero; a originalidade vem dos desenhos, não da técnica.

Fontes: `tools/chars_a.py`, `tools/creatures_a.py`, `tools/env_a.py`. Luz: `StyleProfile.a()`.

## Princípios

- **Forma cúbica chanfrada.** Massas são caixas com os cantos comidos (superelipsoide com expoente
  5); copas em blocos empilhados; telhados retos e íngremes.
- **Cor viva e limpa**, luz de meio-dia, sombras definidas, sombreamento fixo por face para os cubos
  lerem mesmo na sombra. Sem contorno, sem bloom.
- **Três escalas de voxel:** terreno 1/4 u, ambiente 1/8 u (objetos pequenos 1/16 u), personagem 1/24 u.

## Paleta

| Uso | Cores |
|---|---|
| Pele | `#f6caa0` · sombra `#e2a77c` · bochecha `#f29c8c` |
| Olhos / boca | `#2b2238` (aventureira `#1f4a3a`, conjurador `#3b2a78`) · `#b9614d` |
| Aventureiro | túnica `#2f78de` / `#2459ad` / `#62a4f5` · cachecol `#e63b30` · cabelo `#8c4a22` |
| Aventureira | túnica `#3fa24a` · capa `#2f6f52` · colete `#c98d4e` · cabelo `#e2662b` |
| Conjurador | manto `#7a46c8` / `#5a2f9c` / `#a878ee` · chapéu `#6a3fc0` · cabelo `#e9e4f5` · cristal `#58e0ff` (emissivo) |
| Metais | ouro `#f8c93a` · aço `#eef3fa` / `#b3c1d6` |
| Couro / madeira | `#8a5530` / `#5f381d` · `#a9703a` / `#7d4f26` |
| Folhagem | carvalho `#46b040` · pinheiro `#2f8f5a` · bétula `#9fd13e` · flor de cerejeira `#f48fb6` |
| Terreno | grama `#66b84a` `#58a842` `#7cc658` · floresta `#469a44` · caminho `#cfa368` · areia `#eedc9c` |
| Construção | estuque `#f3e6c4` · telha `#d8432f` · viga `#5d3a22` · pedra `#9aa3ad` · veneziana `#3b7dd8` |
| Emissivos | janela `#ffe9a6` · lanterna `#ffd66b` · runa `#6fe6ff` · fogo `#ff8a2a` / `#ffe07a` |

Tons intermediários vêm de `tone()`/`ramp()` (desvio de matiz embutido).

## Personagens

Base comum (`Body`): cabeça 10×9×9 (12 de largura com cabelo), tronco 8×9×5 com barra em sino,
pernas 3×5×3 com botas 4×2×6, braços 3×7×3 (aventureira: 2 de largura). Voxel 1/24 u.

| Personagem | Altura (voxels) | Altura (u) | Cabeças | Silhueta |
|---|---|---|---|---|
| Aventureiro | 26 | 1,08 | 2,5 | tufos de cabelo, cachecol com pontas nas costas, ombreira à direita, escudo redondo à esquerda, espada de 16 voxels |
| Aventureira | 24 | 1,00 | 2,4 | rabo de cavalo com laço (parte animada), capuz caído, capa curta, aljava e arco nas costas, adaga |
| Conjurador | 33 (com chapéu) | 1,38 | 2,4 (sem chapéu) | chapéu de aba larga com ponta caída, cabelo longo prateado, manto em sino, mangas largas, cajado com cristal |

Rosto: olhos 2×3 inteiros escuros com 1 voxel de brilho, bochechas, boca de 1–2 voxels. Íris
colorida separada foi testada e descartada (vira ruído nessa resolução).

## Criaturas

| Criatura | Papel | Tamanho | Ideia |
|---|---|---|---|
| Pompom | animal pequeno (saltador) | 0,67 u | bola de pelo com orelhas longas, cauda de pompom e broto de folha na testa |
| Lumi | fantástica amigável (flutua) | 0,71 u | espírito-lanterna emissivo com chapéu de bolota e fagulhas em órbita |
| Fungo | inimigo simples (saltador) | 0,89 u | cogumelo-espinheiro: chapéu ameixa com espinhos e manchas em brasa, olhos amarelos sob sobrancelha |
| Tortuga | grande e mansa (quadrúpede) | ~3 u de altura com a árvore do casco (voxel 1/10 u) | tartaruga-colina com jardim e árvore no casco |

## Vegetação e arquitetura

- Carvalho: tronco com raízes + 5 lóbulos cúbicos; variante rosa (`tree_oak2`) como acento.
- Pinheiro: cinco saias quadradas escalonadas. Bétula: tronco claro com marcas, copa amarelada.
- Árvore-marco com porta, janela iluminada e cogumelos-prateleira.
- Casa enxaimel: base de pedra, estuque, vigas, porta em arco de 1,5 u, janelas emissivas com
  venezianas azuis e floreiras, telhado vermelho íngreme, chaminé, trepadeira.
- Santuário: degraus, dois pilares (um quebrado), monólito com runa emissiva, cristais, musgo.

## Animação
Clipes comuns do `Animator` com exagero 1,0. O cachecol (`cape`) e o rabo de cavalo (`tail`) seguem
com atraso; o chapéu do conjurador balança (`hat`); o cajado usa `meta.weaponRest = 0,12`.

## Limitações conhecidas

- Resolução baixa: sem expressão facial variável; mãos são blocos.
- O arco da aventureira (redesenhado como "D" num plano só) ainda lê fraco de costas, porque o rabo
  de cavalo e a aljava disputam o mesmo espaço.
- O cajado do conjurador tem 1 voxel de espessura e some a distância; o cristal foi aumentado, mas
  fica parcialmente atrás do cabelo em vista frontal.
- Sem bloom, os emissivos (runas, lanternas) brilham menos do que nas direções B e D.
- A grama é uma cor por voxel de 1/4 u: de perto o chão é liso; a riqueza vem dos props espalhados.
- É a direção com menor distância visual em relação às referências do gênero — o risco de "parecer
  mais um" é o maior das quatro.
