---
name: character-customization
description: Personalização modular dos protagonistas em tempo de execução (Java) — catálogo de componentes gerado por tools/components_e.py, compositor (Composer) com grade fundida por peça, recoloração por canal semântico que preserva a rampa, variantes de cabelo sob chapéu/capuz com assento, presets idênticos ao canônico, Appearance versionada (salvar/carregar), criador do jogador (07.2) e modo PERSONALIZACAO técnico, gate pixel a pixel. Use ao criar cabelo, chapéu, cor, rosto ou preset, ao mexer no compositor ou ao validar combinações.
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
3. `tools/review7.sh cabelos pele coberturas matriz videos` e **olhe**: costas com chapéu, mechas
   atravessando, cor achatada, crânio exposto, chapéu flutuando.
4. `viewer --check` continua TODOS OK, e a mesma lista de exceções em `--occlusion`.
5. Se o componente aparece no criador: `./gradlew test` (`CreatorTest`: rótulo cabe sem "…", hitboxes) e
   `tools/review72.sh capturas`; olhe a miniatura nova em tamanho real.
6. Gate de rodadas seguintes sem sobrescrever a 07: `R=output/visual-review/<rodada>/regressao tools/review7.sh gate`.

## Defeitos já vistos (não repita)

| Defeito | Causa | Correção |
|---|---|---|
| Costura de 0,28% e +27% de faces (06.1) | malhas separadas para cabeça e cabelo | grade fundida por peça |
| Chapéu flutuando sobre cabelo baixo | aba na altura do cabelo da maga | assento pela frente |
| Crânio exposto atrás com chapéu | corte "da aba para cima" com casca fina, mais filtro de componente | cortar só o que atravessa ou sai por cima; filtro só para pontas soltas da aba para cima |
| Ponta do topete flutuando fora da aba | mecha fora das colunas da aba | também cai o que sai acima da aba mais baixa |
| Estilo "natural" ≈ topete | mesma casca | medir Jaccard antes de entrar no catálogo |
| Lado da cabeça careca no `curto` v1 | laterais e nuca altas com casca fina | laterais 6, nuca 3, casca 1,5 |
