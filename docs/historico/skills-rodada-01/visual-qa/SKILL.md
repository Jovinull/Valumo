---
name: visual-qa
description: Capturar imagens reais do viewer, comparar as direções artísticas sob as mesmas condições, verificar proporções e animações, e registrar defeitos visuais.
---

# QA visual

Princípio: **nenhuma afirmação sobre aparência sem captura do viewer**. As capturas saem do mesmo
pipeline do modo interativo (FBO offscreen, SSAA 2×).

## Executar

```bash
./gradlew :viewer:run                      # interativo (controles no README)
tools/snap.sh "<spec>" ["<spec>" ...]      # capturas avulsas, sem janela
viewer/build/install/viewer/bin/viewer --review   # pacote completo em output/visual-review/
```

### Spec de captura (`chave=valor` separados por vírgula)

| Chave | Valores |
|---|---|
| `mode` | `scene`, `char`, `side` (4 direções lado a lado), `prop` (estudo de proporções) |
| `style` | `a` `b` `c` `d` |
| `char` | índice: 0 aventureiro, 1 aventureira, 2 conjurador, 3+ criaturas (ordem alfabética) |
| `clip`, `t` | `idle walk run attack hit emote`; tempo em segundos |
| `yaw pitch dist fov ty` | câmera orbital (graus; `ty` = altura do alvo) |
| `myaw` | giro do modelo (graus) |
| `hx hz hyaw lift` | posição/rumo do herói na cena; altura do alvo da câmera |
| `neutral notex noshadow noao nopost nofog nohud norm` | `=1` liga a opção |
| `size`, `out` | `LxA`; caminho do PNG |

Pontos da clareira: casa em (−6,5; −8) com porta para +Z; poço (−1,6; −6,6); santuário (6,8; −6,8);
lago (7,8; 4,6); árvore-marco (−11,5; 2,5); tortuga (6,4; 10,9).

## Roteiro de revisão de um personagem

1. Frente, perfil, costas, 3/4 de cima (`yaw=0,90,180` e `yaw=40,pitch=28`).
2. `neutral=1` — o modelo se sustenta sem a atmosfera do estilo?
3. Direção C: `notex=1` — a forma se sustenta sem a textura?
4. Poses: `walk t=0.14 e 0.43`, `run t=0.10 e 0.30`, `attack t=0.26, 0.44, 0.60`, `hit t=0.12`, `emote t=0.55`.
5. Na cena, ao lado da porta e de uma criatura — a escala relativa convence?
6. Compare com as outras direções: `mode=side,char=N`.

Perguntas a responder por escrito: a silhueta é reconhecível? as proporções estão entre 2,3 e 2,6
cabeças e *parecem* compactas? o rosto lê a 7 u de distância? o equipamento ajuda ou atrapalha a
silhueta? alguma parte atravessa outra em movimento? as costas são interessantes?

## Verificar proporções

`mode=prop` mostra o mesmo personagem de 2,2 a 4 cabeças contra uma régua de faixas de 1 cabeça
(`norm=1` normaliza a altura total; `myaw=90` mostra de perfil). As dimensões em voxels aparecem no
HUD e estão em `meta` de cada `rig.json`.

## Comparar direções

- Sempre a mesma spec para as quatro (`Review.sevenViews` faz isso: frente, perfil, costas,
  isométrica, no cenário, junto à estrutura, junto à criatura).
- Nunca use enquadramentos diferentes para favorecer uma direção.
- `comparacao/matriz-7-vistas.png`: linhas = direções A–D, colunas = as 7 vistas.

## Registrar defeitos

Em `docs/validation/problemas-conhecidos.md`, uma linha por defeito: direção, onde aparece (arquivo
da captura), descrição, severidade (bloqueia / incomoda / cosmético), causa provável. Corrigiu →
recapture a mesma spec e remova a linha. Captura que não vem do viewer deve ser marcada como tal
(nesta rodada não há nenhuma).

## Validar animações

Imagem parada não prova animação. Além das poses-chave, rode o interativo e observe ciclos inteiros
(`Q`/`E` trocam o clipe, `T` gira o modelo). Procure: estalo na emenda do laço, partes rígidas demais,
pés deslizando na caminhada da cena (`W A S D`), capa atravessando o corpo na corrida.
