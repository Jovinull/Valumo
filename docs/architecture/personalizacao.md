# Personalização modular de personagens

Objetivo: o jogador parte de um dos três protagonistas (preset) e muda cabelo, cor do cabelo, pele, rosto,
íris e cobertura de cabeça. Tudo é composto **em tempo de execução, em Java**, sem rodar Python a cada
escolha. Sem nenhuma mudança, o preset sai **idêntico** ao rig canônico.

| Marca | Significa |
|---|---|
| **[IMPLEMENTADO]** | existe no viewer e é coberto por testes |
| **[PLANEJADO]** | proposta; nada no código ainda |
| **[NÃO IMPLEMENTADO]** | fora do escopo até decisão contrária |

## 1. Visão geral **[IMPLEMENTADO — rodada 07]**

```
tools/chars_e.py ──(ganchos _parts / hair_hex / iris_hex)──► tools/components_e.py ──► assets/components/
                                                                                      catalog.json + .vox + máscaras
viewer: Appearance (JSON) ──► ComponentCatalog ──► Composer.compose (CPU)   ──► Composer.build (malha + GPU)
                                                     grade fundida por peça        cache LRU por assinatura
```

| Peça | Arquivo | Papel |
|---|---|---|
| Exportador de componentes | `tools/components_e.py` (módulo `components_e` do `build_assets`) | separa cada cabeça canônica em rosto, cabelo e capuz; grava máscaras de canal; escreve o catálogo |
| Catálogo | `assets/components/catalog.json` (gerado, contrato `valumo.componentes/1`) | componentes, presets, paletas, canais |
| Grade de voxels | `engine/.../VoxGrid.java` | voxels em memória no espaço do modelo; vira `VoxelSource` para o mesher |
| Aparência | `viewer/.../Appearance.java` | escolhas do jogador, validação, JSON versionado (`valumo.aparencia`, versão 1) |
| Catálogo (Java) | `viewer/.../ComponentCatalog.java` | leitura do catálogo, resolução de "do preset", variante da cobertura |
| Compositor | `viewer/.../Composer.java` | composição, recoloração, variantes, assento do chapéu, malha, cache, liberação de GPU |
| Criador do jogador (07.2) | `viewer/.../Creator*.java`, `AsyncComposer.java` (`./gradlew editor`) | interface clicável, miniaturas reais, composição fora da thread do GL — [criador de personagens](criador-de-personagens.md) |
| Editor técnico | `App` → modo **PERSONALIZACAO** (`./gradlew editorDebug` ou `Tab`) | painel de texto, métricas, troca ao vivo, salvar/carregar/restaurar |

## 2. Por que os presets saem idênticos

1. O exportador refaz a cabeça de cada herói **com o mesmo procedimento do gerador** (`head_with_expressions`
   e `prune_specks`). Uma asserção confere que o resultado é igual ao `head.vox` canônico; só então ele
   separa as células em rosto, cabelo e capuz.
2. O compositor faz a sobreposição na **mesma ordem do gerador**: primeiro o rosto, depois o cabelo, por
   último o capuz. Com isso, rosto, cabelo e capuz voltam a ser **uma única grade por expressão**.
3. A grade fundida passa pelo mesmo mesher (`buildDataMerged`) e com a mesma origem do `rig.json`. Resultado:
   - malha idêntica;
   - **sem costura** (a diferença de 0,28% da 06.1 vinha de malhas separadas para cabeça e cabelo);
   - **sem faces internas** (a separação tinha aumentado as faces em 27%).
4. Corpo, chapéu e rabo vêm dos arquivos canônicos, sem cópia nem alteração, e mantêm a mesma ordem de
   partes, os mesmos pivôs e a mesma hierarquia.
5. Cor igual à original (do preset ou escolhida) significa nenhuma recoloração.

Provas:
- `CustomizationTest`: voxels, cores, malha, pivôs, pais e as 4 expressões de cada preset;
- gate de captura `tools/review7.sh gate`: 51 pares, 0 pixels diferentes.

## 3. Componentes e slots **[IMPLEMENTADO]**

| Slot | IDs | Origem | Observação |
|---|---|---|---|
| `rosto` | `rosto.aventureiro`, `rosto.exploradora`, `rosto.maga` | rosto de cada herói (olho, sobrancelha, boca, atuação) | 4 expressões por rosto |
| `cabelo` | `cabelo.topete`, `cabelo.curto`, `cabelo.chanel`, `cabelo.rabo` | topete h2 do aventureiro; **curto** (novo, rodada 07); cabelo da maga; cabelo da exploradora com o rabo (parte `tail`) | o `natural` (h1) ficou fora: tinha 82% do mesmo volume do topete |
| `cabeca` | `chapeu.maga`, `capuz.c3` | `hat.vox` canônico da maga; nó, fitas e pena da exploradora, mais a regra da faixa | |
| corpo | dos rigs `characters/e/*` | arquivos canônicos | máscara de pele só onde há pele (mãos da maga) |

Contrato de componente no catálogo:
- `versao`, `slot` e `origem` (fonte);
- `cabeca`: massa fundida à cabeça, com `file`, `origin` e `mascara`;
- `partes`: peças próprias, com `nome`, `pai`, `pivo`, `file`, `origin` e `mascara`;
- `base`: cor-base do canal;
- `ocupa`;
- `cabelo`: regras de variante por estilo (só nas coberturas);
- `faixa`: só no capuz.

Os IDs são estáveis. Um componente removido ou renomeado não tem o ID reaproveitado (§6).

## 4. Cor sem achatar o material **[IMPLEMENTADO]**

Os canais são definidos **na origem**: o exportador gera o herói de novo com a cor-base trocada por uma cor
de sonda e marca as células que mudaram. Pele, cabelo e íris têm sondas próprias; para a pele, troca a do
herói e a `SKIN` global usada por `paint_face`. Nada é inferido comparando RGB do resultado. Máscaras:
`<peça>.mask.vox`, com a cor indicando o canal: `#010000` pele, `#020000` cabelo, `#030000` sobrancelha
(segue o cabelo), `#040000` íris.

Recoloração (`Composer.recolor`), canal a canal do RGB, a partir da cor original `c`, da base `b` e da nova
base `n`:
- se `c ≤ b` (sombra): `n·c/b`;
- se `c > b` (luz): `n + (255−n)·(c−b)/(255−b)`.

As duas fórmulas são exatamente as relações de `shadow()` e `light()` do gerador. Por isso a rampa (sombra,
base, luz, sombra profunda) se mantém em qualquer cor, e o material (bits altos) não muda. Teste:
`hairColorKeepsTheRampAndTheOriginalColorChangesNothing`.

Paletas do catálogo:

| Canal | Cores |
|---|---|
| Cabelo | cobre, mel, lilás, castanho, preto, loiro, ruivo, cinza, musgo, anil |
| Pele | pêssego, bronzeada, clara, morena, escura |
| Íris | verde, azul, violeta, âmbar, castanho |

## 5. Coberturas de cabeça e variantes do cabelo **[IMPLEMENTADO]**

| Combinação | Variante | Regra |
|---|---|---|
| sem cobertura | — | cabelo completo; tirar o chapéu ou o capuz restaura o cabelo |
| `chapeu.maga` × `chanel` | `nativo` | foram desenhados juntos; nada muda (preserva a maga) |
| `chapeu.maga` × outros | `sob_aba` | ver passos abaixo |
| `capuz.c3` × qualquer cabelo | `faixa` | o nó, as fitas e a pena se fundem à cabeça; a faixa é pintada sobre o cabelo pela mesma regra do gerador |
| `chapeu.maga` × `capuz.c3` | — | o mesmo slot, então são exclusivos por construção |
| incompatível | `"X"` no catálogo | `InvalidAppearance` com mensagem; o viewer pula a opção e informa |

Passos da variante `sob_aba`:
1. **Assento.** O chapéu desce até a aba encostar na cabeça, com o contato medido na metade da frente, e
   nunca sobe. Isso corrigiu a fresta e o chapéu flutuando sobre cabelos baixos.
2. **Corte.** Some só o cabelo que **atravessa** o chapéu ou **sai por cima** dele. O cabelo de dentro fica,
   porque não aparece e fecha o furo da aba.
3. **Pontas soltas.** Pedaços pequenos que o corte deixou soltos da aba para cima saem.

Antes de chegar a essa regra houve dois erros, ambos registrados no relatório da rodada:
- um corte "da aba para cima" deixava o crânio exposto em cabelos de casca fina;
- um filtro de pedaços apagava mechas legítimas.

Teste: `hatSitsOnEveryHairStyleWithoutPokingThroughOrLeavingAGap`.

**[PLANEJADO]** Ainda faltam:
- capacete aberto (variante desenhada por estilo);
- capacete fechado (oculta o cabelo);
- capuz de tecido sobre a cabeça;
- orelhas de fantasia × coberturas.

## 6. Persistência **[IMPLEMENTADO]**

```json
{
  "contrato": "valumo.aparencia",
  "versao": 1,
  "identidade": {"nome": "Lia"},
  "origem": {"preset": "aventureiro"},
  "aparencia": {"rosto": "rosto.maga", "cabelo": {"estilo": "cabelo.curto", "cor": "#6b4630"},
                "pele": "#c98a5e", "iris": "#c08a2a"},
  "equipamento_visual": {"cabeca": "chapeu.maga"}
}
```

- Só se salva o que difere do preset (campo ausente significa "do preset"). `"cabeca": "nenhum"` tira a
  cobertura do preset.
- Identidade, aparência e equipamento visual ficam separados, e não há atributos de combate nem classe.
- A validação (`Appearance.validate`) recusa, com mensagem clara:
  - contrato ou versão diferente;
  - preset desconhecido;
  - ID desconhecido ou de slot errado;
  - cor fora de `#rrggbb`;
  - combinação incompatível.
- `save(arquivo, overwrite=false)` nunca sobrescreve. O viewer grava `output/aparencias/<preset>-NNN.json` com
  número novo.
- **Migração [PLANEJADO]:** a cada versão nova do contrato, uma função pura `migrar_N_para_N+1`. IDs
  removidos ganham um `substituto` no catálogo, e IDs nunca são reaproveitados.

## 7. Rig, animação e desempenho

- O compositor preserva as partes, a ordem, os pivôs e os pais do rig base. O rabo e o chapéu entram como
  partes com pivô próprio, e o `Animator` dá a eles movimento secundário (`tail`, `hat`). A meta `tailGain`
  vem do cabelo `rabo`.
- **[IMPLEMENTADO]** Animação válida (locomoção, golpe do perfil, HIT e EMOTE) nos três presets e em
  combinações com outro cabelo, pele e chapéu (`composedPresetsAndCustomCharactersKeepValidAnimation`).
  Nenhuma interrupção nova: as conhecidas de espada e lâmina longa continuam abertas.
- **Custo medido** (`viewer --custom-bench`, Iris Xe):

  | Etapa | Média | Máximo |
  |---|---|---|
  | Compor | 2,8 ms | 8,7 ms |
  | Malha | 8,1 ms | 23 ms |
  | Envio à GPU | 1,4 ms | 3,5 ms |

  - Memória: cerca de 2,3 a 3 MB de vértices por aparência (com as 4 cabeças de expressão).
  - Draws: o mesmo número do canônico (13 partes).
  - Restaurar o preset a partir do cache: 0,005 ms.
  - Trocas: 400 trocas com cache LRU de 12; limpar o cache deixa **0 malhas e 0 bytes** a mais.
  - Nada é recomposto a cada quadro.
- **[IMPLEMENTADO — 07.2]** No criador do jogador, a composição e os vértices rodam numa thread de trabalho
  (`AsyncComposer`, `Composer.prepare`). Só o envio fica na thread do GL (`Composer.upload`, ≈ 3 ms), com
  descarte de pedidos obsoletos.
  - Nos cliques rápidos o pior quadro foi de 23 ms (era ≈ 35 ms bloqueando).
  - O editor técnico e as capturas continuam síncronos (`Composer.build` = `upload(prepare(...))`, mesmo resultado).
- **[PLANEJADO]** Pré-compor o grupo da cena.

## 8. Limites conhecidos

- Os rostos são os três dos heróis; formatos de olho, sobrancelha e boca avulsos ainda não são componentes.
  Hoje troca-se o rosto inteiro e a cor da íris.
- As orelhas de fantasia e os NPCs não entram no sistema (o esqueleto é outro).
- Roupas e armaduras por região do corpo: **[PLANEJADO]** (ocupa, oculta, substitui; variantes de manga e
  de cano).
- Criador de personagens do jogador: **[IMPLEMENTADO — 07.2]** ([criador de personagens](criador-de-personagens.md)).
  Inventário, atributos e classes: **[NÃO IMPLEMENTADO]**.

## 9. QA de componentes

1. O preset recomposto é idêntico ao canônico (testes e gate de captura).
2. Variantes só removem voxels; nenhum voxel é compartilhado entre componentes; nada sai por cima do chapéu
   e a aba encosta na cabeça.
3. Estilos de cabelo distintos (Jaccard de volume < 0,8 entre pares) e silhuetas de frente, perfil e costas.
4. A animação passa no validador para os presets e para uma combinação carregada.
5. O pacote visual inclui cada cabelo, cor, pele, rosto e cobertura, as expressões com cobertura, a matriz de
   combinações, vídeos e o roteiro da interface (`tools/review7.sh`).
