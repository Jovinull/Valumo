---
name: character-customization
description: Personalização modular de personagens jogáveis na direção E — componentes por slot (rosto, cabelo, cabeça, roupas, mãos), variantes de cabelo sob chapéu/capuz/capacete, regras de ocultação e substituição, presets como configuração visual, formato versionado de aparência e a prova técnica tools/custom_e.py. Use ao acrescentar estilo de cabelo, chapéu, peça de roupa ou preset, ou ao mexer no compositor.
---

# Personalização de personagens (direção E)

Estado real (rodada 06.1): **prova técnica em tempo de geração** (`tools/custom_e.py` →
`assets/characters/custom/`). Não há criador de personagens nem composição em tempo de execução.
Arquitetura, matriz de compatibilidade e o que é [IMPLEMENTADO]/[VALIDADO]/[PLANEJADO]:
`docs/architecture/personalizacao.md`. Não descreva planejado como existente.

## Regras que não mudam

- Os três protagonistas são **presets** e continuam idênticos às referências. Preset = documento de
  aparência (`origem.preset`), não classe; arma/estilo de combate é escolha separada.
- Componente encaixa num **esqueleto** declarado (heróis: constantes de `charlib_e`; NPCs: `npcs_e`). Nunca
  monte componente de herói em rig de NPC.
- Cobertura de cabeça **oculta** o cabelo por variante; variante só **remove** voxels (nunca estica, move ou
  aumenta peça para esconder clipping). Sem variante = incompatível.
- Save guarda ids e parâmetros (`valumo.aparencia/1`), nunca geometria; migração por versão.

## Acrescentar um estilo de cabelo

1. Escreva o estilo no gerador do esqueleto (ex.: `chars_e.adventurer_hair`) e dê um id estável em
   `custom_e.HAIR_STYLES`.
2. Gere a combinação na `PROOF` (ou no compositor futuro) e confira: `hair` é filha de `head` com o mesmo
   pivô; rosto e expressões não mudam.
3. Para cada cobertura de cabeça existente, decida: variante por corte (`sob_chapeu`), variante desenhada
   (`sob_capuz`, `sob_capacete_aberto`) ou incompatível. Registre na matriz do documento.

## Acrescentar um chapéu / capuz / capacete

1. Parte própria filha de `head` (`hat`/`hood`/`helm`), no crânio do esqueleto-alvo.
2. Declare o que oculta e o **assento** (altura da aba sobre o crânio): o corte de colunas deixa uma fresta
   nas costas quando o chapéu foi desenhado para outro cabelo (achado da prova).
3. Fora da medida de cabeças (`headsTall` do corpo base).

## Testes e QA obrigatórios

- `ModularTest`: preset recomposto idêntico voxel a voxel ao canônico (4 expressões) e demais partes
  idênticas byte a byte; cor troca só cor; estilo troca só o cabelo; chapéu sem voxel compartilhado e
  variante só removendo; validador de golpe/locomoção na combinação mais carregada.
- 22 referências canônicas pixel a pixel (`tools/reference.sh`).
- Capturas frente/costas/rosto de cada combinação (`output/visual-review/round-06-1/modularidade/`) e vídeo
  de caminhada + golpe.

## Limites conhecidos (resolver antes do criador)

- Separar cabelo da cabeça expõe faces internas (+27% no aventureiro) e muda 0,28% dos pixels: o jogo deve
  **fundir as partes rígidas do mesmo pai** depois de compor.
- Cor por regeração do `.vox`; falta paleta com canais recoloríveis.
- `SKIN` fixo em `paint_face`/sombras: tom de pele ainda não é parâmetro.
- Capuz da exploradora fundido na cabeça; precisa virar componente.
