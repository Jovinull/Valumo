# Guia geral de direção de arte

Vale para qualquer direção. Os guias específicos ([A](direcao-a.md), [B](direcao-b.md),
[C](direcao-c.md), [D](direcao-d.md)) detalham paleta, formas e medidas.

## Princípios visuais

1. **Pequeno aventureiro, mundo grande.** O herói tem ~1 u; a porta 1,5 u; a casa 4,4 u; um carvalho
   4,5–5 u; a árvore-marco 8–9 u. Se o personagem parecer "uma pessoa num jardim de brinquedo", a
   escala do cenário está pequena demais.
2. **Silhueta antes de detalhe.** Cada personagem deve ser reconhecível em preto chapado: cabelo,
   chapéu, arma e capa carregam a identidade. Assimetria é bem-vinda.
3. **Compacto, não infantil.** 2,3–2,6 cabeças; pernas curtas; mãos e botas grandes; rosto simples
   (olhos grandes, sem nariz, boca mínima). Postura e equipamento dão a atitude de aventureiro.
4. **Voxel evidente.** Nada de malha suavizada. Formas arredondadas são construídas em degraus;
   rotações livres só em partes animadas e armas.
5. **Cor com intenção.** Poucos matizes por modelo (3–5), sem preto puro; sombras esfriam, luzes
   esquentam (rampas com desvio de matiz). Uma cor de destaque por personagem.
6. **Detalhe em focos.** Rosto, mãos, arma e um ou dois acessórios recebem detalhe; o resto descansa.
   No cenário, pontos de interesse (casa, santuário, árvore-marco, lago, fogueira) separados por
   áreas calmas.
7. **Luz conta a história.** Sombras ancoram os objetos; emissivos (janelas, runas, cristais,
   lanternas) marcam o que é mágico ou habitado.

## Regras de escala

Tabela completa no [contrato de assets](../../art/contrato-de-assets.md). O que **não** muda entre direções:
alturas de referência em `u`, o relevo e a disposição da clareira. O que muda: o tamanho do voxel
(ou texel) de cada categoria.

## Personagens

| | A | B | C | D |
|---|---|---|---|---|
| Linguagem | cúbica, chanfrada | redonda, caricatural | cuboides + pixel art | voxel fino, silhueta elaborada |
| Resolução | voxel 1/24 u | ver guia | texel 1/32 u | ver guia |
| Proporção alvo | 2,5 cabeças | 2,3–2,6 | 2,3–2,6 | 2,4–2,7 |

Arquétipos comuns: aventureiro com espada, aventureira leve, conjurador. As partes de rig e os
clipes são os mesmos; o desenho é próprio de cada direção.

## Vegetação

- Copa = massa com volume (lóbulos), nunca um plano. Tronco visível e proporcional.
- Rampa "iluminada de cima": topo claro e quente, base escura e fria.
- Três famílias de silhueta (copa larga, cônica, esguia) + uma árvore de cor especial como acento.
- Flores e tufos pequenos em grupos, nunca em grade regular.

## Arquitetura

- Construções alinhadas à grade (rotações de 90°), com base de pedra, parede clara, estrutura de
  madeira escura e telhado saturado: lê a 30 u de distância.
- Porta de 1,5 u e janelas iluminadas dão a escala humana. Beiral e chaminé quebram a caixa.
- Ruínas: pedra com variação de tom, musgo no topo, um elemento emissivo como foco.

## Convenções de textura (direção C)

Pixel art desenhada, filtragem nearest, sem mipmaps, 1 texel = 1/32 u em tudo. Detalhes em
[direcao-c.md](direcao-c.md) e na skill `pixel-art`.

## Restrições visuais

- Sem proporção humana realista; sem rostos detalhados; sem roupas realistas.
- Sem assets, formas ou criaturas reconhecíveis de outros jogos.
- Sem preto ou branco puros em grandes áreas; sem gradiente liso gerado por interpolação (o gradiente
  é pintado voxel a voxel).
- Sem ruído aleatório como textura: variação de cor tem estrutura (manchas, estrias, placas).
