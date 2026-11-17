---
name: pixel-art
description: Desenhar pixel art e modelos de cuboides texturizados da direção C (tools/cuboidlib.py) — paleta mestra, densidade de texel, desenho por grade ASCII e por regras, atlas, aplicação nos modelos e testes de leitura.
---

# Pixel art e cuboides (direção C)

Leia antes: `docs/art/direcao-c.md` (paleta mestra e regras) e a seção 5 de
`docs/art/contrato-de-assets.md` (formato de cuboides e terreno).

## Regras fixas

- **1 texel = 1/32 u em tudo**: personagens, props e terreno (tile de 16 px por bloco de 1/2 u).
  Nunca escale uma textura para "caber"; mude a geometria.
- **Filtragem nearest, sem mipmaps.** Alfa é binário (< 0,5 descarta).
- **Paleta mestra** em `tools/cuboidlib.py`; cores novas entram lá, com rampa de 3–4 tons e desvio
  de matiz. Sem preto puro. `tone(nome, n)` anda na rampa.
- **Nada de imagem reduzida, filtro automático ou ruído aleatório.** Variação tem estrutura.
- **A textura não substitui a forma.** Silhueta vem dos cuboides (franja, chapéu, ombreira, beiral).
  Teste sempre com `notex=1`.

## Ferramentas (`tools/cuboidlib.py`)

| Função | Uso |
|---|---|
| `spr(rows, legend)` | sprite desenhado pixel a pixel em grade ASCII (rosto, frente da túnica, porta, janela) |
| `Canvas` (`rect`, `hline`, `vline`, `edges`, `checker`, `tone_rect`, `flip_h`, `tile`...) | desenho por regras |
| `fill`, `planks`, `bricks`, `bark`, `leaves`, `shingles` | materiais gerados por regra explícita |
| `M(base, top, bottom, left, right, ...)` | material de caixa: cor base + contorno interno/luz por borda |
| `box(x, y, z, w, h, d, mat, ...)` | cuboide com faces geradas pelo material; `rot`/`origin` opcionais |
| `prism(sprite, depth, at, axis)` | extruda um sprite (silhueta recortada com espessura) |
| `mirror_x`, `moved`, `turned` | espelhar, deslocar, girar 90° conjuntos de cuboides |
| `Atlas`, `CuboidRig`, `CuboidPropSet` | empacotam o atlas (com sangria) e gravam `rig.json` / `props.json` |

## Fluxo

1. Desenhe as faces importantes em grade ASCII; gere as secundárias por regra.
2. Monte o modelo com `box`/`prism`, separando as partes do rig como nos modelos voxel
   (`skills/voxel-modeling`): mesmos nomes de partes, mesmos pivôs, 2,3–2,6 cabeças.
3. Registre no `build(out)` de `chars_c.py`, `creatures_c.py` ou `env_c.py`.
4. Capture e revise:

```bash
MODULES="chars_c" tools/snap.sh \
  "mode=char,style=c,char=0,out=output/tmp/c_f.png" \
  "mode=char,style=c,char=0,yaw=180,out=output/tmp/c_b.png" \
  "mode=char,style=c,char=0,notex=1,out=output/tmp/c_notex.png" \
  "mode=scene,style=c,out=output/tmp/c_scene.png,size=1600x900"
```

Use o mesmo `size` em todas as specs de uma chamada.

## Testes de leitura

- A 7 u de distância (cena), o rosto ainda lê? Olhos de 2×3 ou 3×3 px com brilho resistem; detalhes
  de 1 px isolado somem.
- Emendas: bordas de faces vizinhas devem continuar o padrão ou assumir o contorno de propósito.
- Espelhamento: textos, fivelas e assimetrias não podem aparecer invertidos no lado oposto.
- Tiles de terreno: olhe um plano aberto (`yaw=-40,pitch=28,dist=26`) à procura de repetição.

## Limites atuais da engine para cuboides

Sem AO, sem materiais (emissivo/metal/vento: `CuboidMesh` grava material 0), uma rotação por
cuboide, e o passo de sombra ignora alfa (planos de flor projetam sombra retangular). Ver
`docs/validation/problemas-conhecidos.md`.
