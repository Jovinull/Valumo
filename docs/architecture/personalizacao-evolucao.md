# Evolução da personalização — proposta (rodada 09.4)

Estado: o primeiro passo do §1 (formato dos olhos) e a peça do §2 (botas, aprovadas pelo proprietário) foram **[IMPLEMENTADOS]** na
própria rodada 09.4 (ver [personalizacao.md](personalizacao.md)); o resto continua **[PLANEJADO]**. Base: [personalizacao.md](personalizacao.md) (o que existe)
e a skill `character-customization` (regras que não mudam: presets idênticos, cabeça como grade única, variante
só remove, canal semântico pela sonda, save só com IDs e cores).

## 1. Rosto por componentes

**Hoje:** `rosto.<herói>` é o rosto inteiro (olho, sobrancelha, boca, atuação), 4 expressões. Só a íris recolore.

**Fato que viabiliza:** o gerador já pinta o rosto por PARÂMETROS separados (`charlib_e.FACE_DEFAULT`: `eye`,
`brow`, `mouth`, `freckles`, `big_hi`, `lash`, `acting`) sobre uma placa plana (`skull`, z = `FACE_Z`). Os traços são
decalques 2D na placa, não volume — dá para exportar cada traço como CARIMBO por expressão, pela mesma técnica de
sonda que já separa cabelo e rosto.

**Proposta:**
1. `rosto.<id>` vira o crânio + pele + atuação (o jeito de expressar, `acting`), sem os traços.
2. Novos slots `olhos.*`, `sobrancelha.*`, `boca.*`, `detalhe.*` (sardas, cílio): cada componente tem um carimbo
   por expressão (`neutral`, `joy`, `determined`, `surprise`) e uma região da placa (máscara de região).
3. Composição na ordem do gerador: crânio → olhos → sobrancelha → boca → detalhes → cabelo → capuz. Mesma ordem
   = preset idêntico (gate pixel a pixel continua a prova).
4. **Atuação é dona da expressão.** O carimbo de olho `alegre` de um rosto `bold` não é o de um `soft` (a rodada 06
   proibiu o "mesmo carimbo nos três"). Então o carimbo é indexado por (`traço`, `atuação`, `expressão`) e o
   exportador gera só as combinações que o gerador sabe pintar; combinação sem carimbo fica indisponível com
   motivo (regra `X`, como nas coberturas).
5. Teste novo: cada combinação nova tem as 4 expressões distintas entre si e os traços dentro da região.

**Persistência (migração):** `valumo.aparencia` v2:
```json
"aparencia": {"rosto": {"base": "rosto.aventureiro", "olhos": "olhos.inclinado", "sobrancelha": "sobrancelha.grossa",
                        "boca": "boca.canto", "detalhes": []}}
```
`migrar_1_para_2` (função pura): `"rosto": "rosto.X"` → objeto com os traços do herói X (tabela gerada pelo
exportador). Saves v1 continuam abrindo; o jogo grava v2. IDs nunca reaproveitados (§6 de personalizacao.md).

**Primeiro passo [IMPLEMENTADO — rodada 09.4]:** `olhos.redondos|amendoados|inclinados` (as 3 formas do pintor), com a
atuação do rosto-base. Implementado de forma mais simples que os carimbos: o exportador regenera cada rosto com só o
`eye` trocado e grava as 4 expressões da variante dentro do rosto (`variantesOlhos`); o nativo são os arquivos
originais. Persistência v2 com `migrate1to2`. Observação: na atuação `cool` (exploradora) o "alegre" fecha os olhos —
o formato não aparece nessa expressão (por desenho da atuação).

## 2. Roupas e equipamentos por região

**Partes do rig que já existem (os três corpos):** `torso`, `arm_l/r` + `farm_l/r` (cotovelo), `leg_l/r` +
`shin_l/r` (joelho), `head`, e acessórios próprios (`pack`, `scarf`, `tail`, `hat`, `skirt_*`). Pivôs e hierarquia
são do esqueleto dos heróis (congelado).

| Slot | Ocupa (partes) | Como entra | Risco principal |
|---|---|---|---|
| luvas | ponta de `farm_l/r` | substitui as células da mão abaixo do punho | empunhadura (`viewer --grip`) |
| botas | ponta de `shin_l/r` | substitui o cano abaixo do joelho | IK do pé, pés no chão em degraus |
| mangas | `arm_*`, `farm_*` | substitui a casca do braço (variante por corpo) | dobra do cotovelo (tampas de dobra) |
| calças | `leg_*`, `shin_*` | idem pernas | dobra do joelho |
| tronco | `torso` | substitui a casca; acessórios do tronco (`pack`, `scarf`) declaram compatibilidade | arma às costas, mochila |
| chapéu / capuz / capacete | `head` / parte `hat` | já existe (variante de cabelo por estilo) | crânio exposto, chapéu flutuando |
| capa | parte nova `cape` filha de `torso` | parte própria com atraso secundário (como `tail`) | atravessar pernas na corrida; arma |
| armadura | sobreposição de tronco/ombro | oculta a peça de baixo na região | volume: `body_volumes` muda → `--check` |

Regras propostas (mesma filosofia do cabelo):
- Cada peça declara `ocupa` (regiões), `oculta` (regiões que somem por baixo), `prioridade` (quem vence numa
  região) e `compat` com acessórios e armas (`X` = indisponível com motivo).
- **Variante só remove** a peça de baixo; nunca estica a de cima para tapar.
- Peça é desenhada POR CORPO (os três heróis têm cascas diferentes): variante por preset, gerada no mesmo módulo.
- Aceite: `viewer --check`, `--occlusion`, `--grip` sem alerta nos três perfis de arma + vídeo em 4 ângulos de
  correr, golpe e HIT. Roupa bonita parada que atravessa o corpo correndo não está pronta.

**Peça [IMPLEMENTADA e aprovada — rodada 09.4]: botas "viajante"** (forro de lã creme virado em sino + tira com
fivela). Achado: a canela JÁ é a bota inteira (joelho em y = 7/8), então "cano alto" exigiria a coxa dobrando com o
joelho; a peça ficou na canela e se distingue pelo desenho (o forro claro lê na câmera de jogo). A recomendação era: Motivo: a canela já
é parte própria com pivô no joelho, o pé tem IK testado (`AnimationQaTest`), e a bota não interage com armas
nem com cabelo — é a região com menos risco para provar `ocupa`/`substitui` antes de tronco e capa.

## 3. Criador do jogador

Medido nesta rodada (capturas em `round-09-4/personalizacao/criador/`): seis miniaturas de penteado cabem na grade
em 1080p e em 720p sem "…" (`CreatorTest` verde). O criador aguenta o catálogo atual; precisará de mudança quando:
- um slot passar de ~12 opções → rolagem por categoria já existe; falta **filtro** (ex.: comprimento do cabelo);
- houver rosto por partes → subcategorias (Rosto ▸ Olhos / Sobrancelha / Boca);
- houver roupas → categoria "Roupa" com miniatura de corpo inteiro (a de cabeça não mostra botas).
Não redesenhar antes disso (regra da skill `game-ui`).
