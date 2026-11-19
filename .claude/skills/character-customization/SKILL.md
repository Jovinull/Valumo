---
name: character-customization
description: Personalização modular dos protagonistas em tempo de execução (Java) — catálogo de componentes gerado por tools/components_e.py, compositor (Composer) com grade fundida por peça, recoloração por canal semântico que preserva a rampa, variantes de cabelo sob chapéu/capuz com assento, presets idênticos ao canônico, Appearance versionada (salvar/carregar), criador do jogador (07.2) e modo PERSONALIZACAO técnico, gate pixel a pixel; rodada 09.4: formato dos olhos (olhos.*), botas por região (pes), Appearance v2 com migração, regra X real e arma × cabelo voxel a voxel (--check-cabelos). Use ao criar cabelo, chapéu, cor, rosto, olhos, roupa ou preset, ao mexer no compositor ou ao validar combinações. Rodada 11.1: o criador aberto pelo menu (Cancelar só nesse modo) grava perfis de personagem.
---

# Personalização de personagens (direção E)

Arquitetura, contratos e o que está [IMPLEMENTADO] ou [PLANEJADO]: `docs/architecture/personalizacao.md`.

## Regras que não mudam

- **Presets idênticos.** Aventureiro, exploradora e maga sem mudanças saem iguais aos rigs canônicos: mesmos
  voxels, cores, malha, pivôs e expressões, e 0 pixel diferente no gate. Se algo seu quebrar isso, corrija
  o componente; **nunca** mexa nos assets canônicos nem afrouxe o gate.
- **A cabeça é uma grade só.** Rosto, cabelo e capuz são fundidos antes do meshing, na ordem do gerador
  (rosto, depois cabelo, depois capuz). Malhas separadas criam costura e faces internas. Só têm parte própria
  as peças com pivô e movimento próprios (`hat`, `tail`).
- **Variante só remove.** Nunca estique, mova ou aumente o cabelo nem a cobertura para esconder clipping.
- **Canal semântico vem da origem.** Use a sonda no gerador (mudar a cor-base e marcar o que mudou), nunca
  adivinhe pelo RGB do resultado.
- O save guarda IDs e cores, nunca geometria. IDs são estáveis e nunca reaproveitados.

## Criar um estilo de cabelo

1. Escreva o estilo no gerador do esqueleto dos heróis (ex.: um ramo novo em `chars_e.adventurer_hair`)
   **sem mudar os ramos existentes**. Confira os heróis por hash contra a linha de base.
2. Em `tools/components_e.py`, gere-o como o `curto`: `_gen(..., hair_style=...)`, depois cabeça fundida e
   limpa, células do cabelo, sonda de cor (`hair_hex=PROBE_HAIR`) para a máscara, e `comps["cabelo.<id>"]`
   com `base` e `rampa`. Peças próprias entram em `partes`, com pivô e pai.
3. Meça a diferença de volume contra os outros estilos (Jaccard < 0,8; `atLeastFourHairStylesWithDifferentShapes`)
   e olhe a silhueta de frente, perfil e costas. Volume quase igual é variação, não estilo.
4. Rode as coberturas: o teste do chapéu cobre todos os estilos automaticamente. Olhe também as costas.

## Criar um chapéu ou capuz

- **Chapéu:** parte própria filha de `head` (`partes`), no crânio dos heróis. No campo `cabelo`, diga a
  variante por estilo (`nativo` quando foram desenhados juntos; `sob_aba` para os outros; `X` quando é
  incompatível). O assento é automático (`Composer.seat`). Se o chapéu tiver outra forma de base, confira se
  o contato pela frente ainda faz sentido.
- **Capuz ou peça que se funde à cabeça:** use `cabeca` (fundida depois do cabelo). Uma pintura sobre o cabelo
  vira regra, como a `faixa` do C3, e não vai gravada no cabelo.

## Adicionar cores

Acrescente em `COLORS` (`components_e.py`), escolhidas à mão e poucas. Confira cada cor em luz neutra, de dia e
de tarde, e em escala de jogo. Cabelo e pele parecidos precisam continuar separados pelo contorno e pela sombra.

## Montar e inspecionar

- **Criador do jogador** (07.2): `./gradlew editor`. É o que o jogador vê. Componente novo aparece sozinho
  como miniatura real; cor nova, como amostra.
  - Dê um nome amigável em `CreatorState.NAMES`: nunca mostre ID técnico ou hex.
  - Se for penteado, defina a ordem em `CreatorLayout.HAIR_ORDER`.
  - Regra `X` no catálogo deixa a opção indisponível com motivo.
  - Arquitetura e testes: `docs/architecture/criador-de-personagens.md`; revisão: `tools/review72.sh`.
- **Editor técnico** (depuração, com métricas): `./gradlew editorDebug` ou `./gradlew :viewer:run` e `Tab` até
  **PERSONALIZACAO**.
  - Setas: linha e valor.
  - `BACKSPACE`: preset original.
  - `F2`: salvar em `output/aparencias/` (nunca sobrescreve).
  - `F3`: carregar o mais recente.
  - `X` e `Q`/`E`: expressão e clipe; o painel também tem essas linhas.
  - Combinação incompatível é pulada e informada.
- Captura: `tools/snap.sh "mode=char,style=e,custom=aventureiro,cabelo=cabelo.curto,corCabelo=6b4630,pele=c98a5e,iris=c08a2a,rosto=rosto.maga,cobertura=chapeu.maga,expr=joy,out=..."`.
  Chaves: `custom`, `rosto`, `cabelo`, `corCabelo`, `pele`, `iris` (hex sem `#`), `cobertura`
  (`nenhum`/ID) e `aparencia=<arquivo.json>`. Funciona em `mode=char` e `mode=scene`.
- Roteiro da interface: `viewer --custom-demo <pasta>`. Custo: `viewer --custom-bench <arquivo.md>`.

## Validar (obrigatório)

1. `python3 tools/build_assets.py` (canônicos idênticos por hash) e `./gradlew test` (`CustomizationTest`).
2. `tools/review7.sh gate`: tem de dar **0 pares com diferença**. Se der diferença, investigue a causa (ordem de
   sobreposição, limpeza, origem, recoloração indevida) e mantenha o problema aberto até corrigir.
3. `for s in cabelos pele coberturas matriz videos; do tools/review7.sh $s; done` (o script lê um bloco por chamada; cabelos
   novos, olhos e botas saem do `tools/review94.sh`) e **olhe**: costas com chapéu, mechas
   atravessando, cor achatada, crânio exposto, chapéu flutuando.
4. `viewer --check` continua TODOS OK, e a mesma lista de exceções em `--occlusion` (os dois percorrem só os rigs de
   `Check.RIGS`: um rig novo precisa entrar nessa lista para ser conferido).
5. Se o componente aparece no criador: `./gradlew test` (`CreatorTest`: rótulo cabe sem "…", hitboxes) e
   `tools/review72.sh capturas`; olhe a miniatura nova em tamanho real.
6. Gate de rodadas seguintes sem sobrescrever a 07: `R=output/visual-review/<rodada>/regressao tools/review7.sh gate`.

## Rodada 09.4 — estudos de cabelo, olhos, botas e arma × cabelo

- **Cabelo novo**: função própria em `chars_e.py` (ex.: `hair_crespo`, `hair_coque`) chamada por `HAIR_STUDIES_R09`, sem
  mexer nos ramos existentes; exporte como o `curto` com `"experimental": true` (aprovado pelo proprietário → `"aprovado": "rodada NN"`; os dois continuam
  protegidos por `HairWeaponCheck.isNew`). Partes que não são cabelo (amarração
  de couro) entram DEPOIS do `hair_finish` (ele descarta tudo que não é a cor do cabelo) e ficam fora da máscara.
- **Medidas**: `python3 tools/hair_metrics.py` (caixa e Jaccard no espaço do modelo, mesmo mapeamento do Java). Os
  estudos novos ficaram com Jaccard máx. 0,41/0,50 (os aprovados chegam a 0,63).
- **Arma × cabelo, voxel a voxel**: `viewer --check-cabelos [saida.md]` e `HairWeaponTest`. Estudo pior que o penteado
  do preset reprova; aprovado acima da referência aparece como PRÉ-EXISTENTE (achado: EMOTE do aventureiro atravessa
  o topete canônico — problemas conhecidos).
- **Regra `X`** quando a cobertura some no volume (crespo × capuz C3): indisponível com motivo; o `CustomizationTest`
  exige que exatamente as combinações `X` sejam recusadas.
- **Olhos** (`olhos.*`): o exportador regenera cada rosto com só o `eye` trocado (`NATIVE_FACE`, `EYES` em
  `components_e.py`) e grava `rosto.*.variantesOlhos`; o nativo são os arquivos originais. Teste: muda só a placa do
  rosto, 4 expressões distintas, nativo explícito = preset.
- **Botas** (`pes`): `charlib_e.leg(boot_style=...)` + `porCorpo` no catálogo; só `shin_l`/`shin_r`, mesmo pivô, sola na
  mesma altura (IK do pé). A canela é a bota inteira (joelho em 7/8): cano alto exigiria a coxa. Peça precisa LER na
  câmera de jogo (v1 com o tom claro da própria bota não lia; v2 com forro creme lê).
- **Aparência v2** (`aparencia.olhos`, `equipamento_visual.pes`); v1 abre por `Appearance.migrate1to2`. Campo novo →
  copy, reset, assinatura, JSON, validate, `Main` (chave de captura), criador (layout, miniatura, controlador, nome).
- Captura: chaves `olhos=` e `pes=` além das antigas. Pacote: `tools/review94.sh cabelos|olhos|botas|armas`.
## Rodada 11.1 — criador aberto pelo menu (perfis de personagem)

- **Abrir:** o menu abre o MESMO criador (`Creator` na mesma janela) para **Novo personagem** e **Editar aparência**.
  - `cs.finishLabel` ("Criar personagem"/"Salvar personagem") + `cs.cancelLabel = "Cancelar"`.
  - O botão Cancelar (canto superior direito) e o Esc fecham sem gravar (`cs.cancelled`).
  - Sem `cancelLabel` o criador é o de sempre: o gate 51/0 não muda.
- **Concluir:** grava o PERFIL (`CharacterStore.create`/`update`, id estável, nome só exibição), não um arquivo solto
  de `output/aparencias/`.
- **Aparências soltas** (`output/aparencias/`): não viram personagens; ficam no **Carregar** do criador (sem botão de importar no menu, decisão do proprietário).
- **Retratos da lista:** o caminho das miniaturas do criador (`AsyncComposer.requestThumb` → `App.renderPortrait`,
  enquadramento "cabeça"), uma vez por assinatura de aparência. Nada de malha por quadro.
- **Testes:** `MenuStoresTest` (editar não duplica nem toca outro perfil, Unicode, importação repetida, arquivo ruim).

## Defeitos já vistos (não repita)

| Defeito | Causa | Correção |
|---|---|---|
| Costura de 0,28% e +27% de faces (06.1) | malhas separadas para cabeça e cabelo | grade fundida por peça |
| Chapéu flutuando sobre cabelo baixo | aba na altura do cabelo da maga | assento pela frente |
| Crânio exposto atrás com chapéu | corte "da aba para cima" com casca fina, mais filtro de componente | cortar só o que atravessa ou sai por cima; filtro só para pontas soltas da aba para cima |
| Ponta do topete flutuando fora da aba | mecha fora das colunas da aba | também cai o que sai acima da aba mais baixa |
| Estilo "natural" ≈ topete | mesma casca | medir Jaccard antes de entrar no catálogo |
| Lado da cabeça careca no `curto` v1 | laterais e nuca altas com casca fina | laterais 6, nuca 3, casca 1,5 |
| Têmpora careca no crespo/coque v1 (09) | franja lateral alta (bang 10–11) | `TEMPLE = 6` nas laterais da frente |
| Pontos escuros soltos nas costas do crespo v1 | emenda de cachos pintada em voxels 3D | entalhe geométrico na superfície (`_lobe_notches`) |
| Coque "bolo de andares", nuca chapada (v1) | superelipsoide p 2,3; sem sulcos | coque p 2,0 + `hair_strands` |
| "Cor do cabelo" fora do painel com 6 penteados | 2 colunas → 3 fileiras | 3 colunas quando houver mais de 4 |
| Nó/fitas do capuz enterrados no crespo | volume grande cobre a peça | regra `X` |

### Integração da 09.4 — peça de cabelo por corpo

- Uma parte própria do cabelo (o rabo) vem do corpo de origem e fica no pivô DAQUELE corpo. Em outro corpo ela pode
  ficar fora do centro e flutuar: veja SEMPRE de costas e de lado nos três corpos.
- Use `porCorpo.<preset>.partes` (o compositor lê como nas botas) para a versão de cada corpo, sem tocar a canônica
  (o gate exige o preset idêntico). Encoste a amarração na massa do cabelo: meça o z mais atrás da massa no centro
  (o "Preso" está em z = −8 entre y 30 e 36).

