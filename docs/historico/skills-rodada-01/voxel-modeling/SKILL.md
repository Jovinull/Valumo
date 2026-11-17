---
name: voxel-modeling
description: Criar ou alterar personagens, criaturas e props voxel por script (tools/voxlib.py, tools/envlib.py), seguindo eixos, escalas, proporções e o ciclo de revisão visual do projeto.
---

# Modelagem voxel por script

## Convenções (não negociáveis)

- **Eixos:** Y para cima, +Z = frente do modelo, X = direita de quem olha de frente. Braço direito
  do personagem em `x < 0`.
- **Origem:** x e z centrados, `y = 0` no chão. Com largura par, os voxels ocupam `[-w/2, w/2-1]` e o
  espelho é `x -> -1 - x` (`Vox.mirror_x`, `Vox.flipped_x`).
- **Coordenadas contínuas:** o voxel `x` ocupa `[x, x+1]`. Pivôs são dados nesse espaço (o centro de
  um braço que ocupa `x = 4..6` é `5.5`).
- **Escalas** (`voxelSize`, em u): tabela em `docs/art/contrato-de-assets.md`. Não invente uma
  quarta escala dentro de uma direção.
- **Cores:** `C("#rrggbb", material)`; materiais `matte`, `metal`, `emissive`, `foliage`, `water`.
  Sem preto puro. Rampas com desvio de matiz: `tone(cor, k)` (k>0 clareia e esquenta; k<0 escurece e
  esfria) ou `envlib.ramp(cor)` (4 tons). Limite de 255 cores por arquivo, por faixa de material.
- **Tamanho:** no máximo 256 voxels por eixo por arquivo.

## Personagem (rig)

1. Parta de um módulo existente (`tools/chars_a.py` é o mais simples). Defina as medidas num objeto
   como `Body(leg_h, torso_h, ...)` e derive todos os marcos dele (quadril, pescoço, ombro, mão):
   mudar a proporção deve ser mudar dois números.
2. Proporção: **2,3–2,6 cabeças**, pernas ≤ 30% da altura, cabeça mais larga que o tronco, mãos e
   botas maiores que o membro, olhos grandes na metade inferior do rosto, sem nariz
   (`docs/decisions/0004-proporcao-dos-personagens.md`).
3. Modele o personagem inteiro num único espaço de coordenadas e separe em partes `Vox`:
   `torso` (raiz), `head`, `arm_l`, `arm_r`, `leg_l`, `leg_r`, e acessórios (`weapon`, `cape`, `tail`,
   `skirt`, `hat`, escudo...). Os nomes animados estão no contrato.
4. Pivôs: quadril para `torso`/pernas, base da cabeça para `head`, topo do ombro para braços, punho
   para `weapon`, ponto de fixação para capa/rabo. Filhos depois dos pais.
5. A arma é modelada **na vertical** com o punho na mão; `meta.weaponRest` define a inclinação de
   repouso (padrão 1,05 rad; ~0,1 para cajado).
6. Preencha `meta.headsTall` = altura total ÷ altura da cabeça com cabelo.
7. Salve com `Rig(...).save(f"{out}/characters/<estilo>/<nome>")`.

Truques que funcionaram:
- Chanfre as arestas verticais e o queixo da cabeça (tirar 1 voxel) — some a leitura de "caixa".
- Cabelo como casca 1 voxel maior que o crânio, com franja irregular e tufos no topo.
- Assimetria deliberada: ombreira de um lado, escudo do outro, nó do cachecol fora do centro.
- Em resolução baixa (cabeça de 10), íris colorida vira ruído: use o olho inteiro escuro + 1 voxel de brilho.
- Tons escuros gerados por `tone()` em cores quentes puxam para o vermelho; perto do rosto, misture
  com um marrom-arroxeado (`mix`) em vez de usar `tone`.

## Prop de ambiente

1. Escreva uma função que devolve um `Vox`. Use `envlib`: `lobe` (superelipsoide; `p` alto = cúbico),
   `toplight` (rampa iluminada de cima), `trunk`, `rock`, `gable_roof`, `gable_fill`, `frame_box`.
2. Respeite os tamanhos de referência do contrato (porta 1,5 u, carvalho 4,5–5 u, etc.).
3. Fundações e raízes podem descer abaixo de `y = 0` para assentar no relevo.
4. Registre no `build()` com `PropSet.add(nome, vox, voxel_size)`; o nome precisa constar no
   contrato para a cena usá-lo.

## Ciclo de revisão (obrigatório)

```bash
MODULES="chars_a" tools/snap.sh \
  "mode=char,style=a,char=0,out=output/tmp/f.png" \
  "mode=char,style=a,char=0,yaw=90,out=output/tmp/s.png" \
  "mode=char,style=a,char=0,yaw=180,out=output/tmp/b.png" \
  "mode=char,style=a,char=0,clip=walk,t=0.14,yaw=40,out=output/tmp/w.png"
```

Abra as imagens e confira, nesta ordem: silhueta (reconhecível em preto?), proporção, rosto, cor,
costas (o lado que o jogador mais vê), poses de caminhada/corrida/ataque (partes atravessando o
corpo? pivô errado?), e o personagem **dentro da cena** (`mode=scene`). Só então dê o modelo por pronto.

## Erros comuns

- Parte declarada antes do pai → `CharacterModel` recusa o rig.
- Pivô em coordenada inteira quando o centro é `x.5` → a parte "anda" ao girar.
- Voxels soltos invisíveis dentro do corpo inflando o arquivo: remova o interior de volumes grandes.
- Mais de 176 cores foscas num arquivo (rampas com jitter demais) → erro do escritor `.vox`.
