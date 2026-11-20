# Catálogo de conteúdo do mundo — `valumo.conteudo` v1 (provisório, isolado)

**Base:** `main` @ `8fd6756` (rodada 10). O mundo procedural (rodada 11/11.1) não estava publicado quando o formato foi
feito; os contratos dele foram lidos, não usados.

O laboratório de conteúdo ([../art/conteudo-mundo.md](../art/conteudo-mundo.md)) entrega assets **com contexto**: cada
um vem com os metadados que um gerador procedural precisa para pô-lo no mundo sem chutar. O formato é **provisório** e
fica fora do módulo `game`. Ele foi desenhado para caber nos contratos da rodada 11 (região, POI, colisão por perfil,
conferência de alcance), que ainda não estão na `main`. O agente principal adapta os metadados sem redesenhar os modelos.

- **Arquivo:** `assets/environment/e/conteudo_catalogo.json`, gerado por `tools/conteudo_e.py` (fora do git, como todo
  asset gerado).
- **Testes:** `viewer/src/test/java/vx/viewer/ConteudoMundoTest.java` (10 testes, leitor e mesher reais).

## 1. Como os números nascem (nada é número solto)

| Campo | Origem |
|---|---|
| `medidas.*` (caixa, pegada, área, voxels, faces, emissivo, tronco, formas medidas) | **medido** dos voxels por `conteudo_lib.measure` e `collision_boxes`, e **medido de novo** no teste pelo `VoxModel` do jogo |
| `pontos.*` (entradas, interação, spawns, observação, área segura, formas autoradas) | **autorado** no construtor do asset, em voxels do modelo, e **conferido** pelo teste contra a geometria |
| `colisao`, `apoio`, `inclinacao_max_u`, `area_livre`, `frequencia`, `tags`, `terreno`, `restricoes` | **autorados** no registro `@asset`. `area_livre` e `inclinacao_max_u` são **usados** pelas vitrines (`tools/conteudo/vitrines.py` recusa lugar inclinado e área invadida) |
| `custo` | medido (faces expostas). Os triângulos reais estão em `tools/conteudo/MeshCost.java` e o orçamento em `ConteudoMundoTest` |

## 2. Campos

| Campo | Significado |
|---|---|
| `id` | id estável, igual ao nome do prop no `props.json` (minúsculas e `_`, em inglês, como os aprovados). Nunca colide com um aprovado (o build recusa) |
| `nome`, `categoria`, `subcategoria`, `lote`, `versao`, `fonte` | identificação. `fonte` = `arquivo.py:função` |
| `estado` | `aprovado` (no `props.json`) ou `rejeitado` (só em `assets/studies/conteudo/`, com `motivo_rejeicao`; nunca distribuível) |
| `aprovacao.artistica` | decisão do proprietário: `aprovado`, `aprovado_conceito` (aceito, com refinamento pendente) ou `rejeitado` |
| `aprovacao.integracao` | o que o gerador pode usar: `liberado`, `depende_colisao_composta`, `aguardando_refinamento` ou `nao_distribuivel` |
| `aprovacao.distribuicao_procedural` | `true` só quando `liberado`; `aprovacao.dependencias` explica o resto (teste: nunca vazio) |
| `origem`, `orientacao` | x = z = 0 no centro da pegada; y = 0 é o chão (soleira); +z é a frente (entrada principal) |
| `funcao`, `tags`, `frequencia`, `terreno`, `restricoes` | para as regras de composição: onde o asset cabe, quantos, com o quê |
| `apoio` | `patamar` (o relevo precisa ser aplainado sob a pegada + `margem_u`), `ponto` (árvore: só o tronco toca o chão) ou `terreno` (as peças se enterram e aceitam desnível) |
| `inclinacao_max_u` | maior desnível sob a pegada que a fundação de pedra cobre sem mostrar vão |
| `area_livre` | `frente_u`, `lados_u`, `fundos_u` ou `raio_u`: o que nenhum outro sólido pode ocupar em volta (entrada, circulação, câmera) |
| `colisao` | perfil compatível com `assets/scenes/colisao_e.json` (`caixa`/`escala`, `circulo`/`raio`, `tronco`/`raio`/`copa`/`copaDe`, `nenhum`). Com `"formas": ...`, o asset **precisa** de formas compostas (§4) |
| `affineWind` | **medido**: `true` só se o modelo ainda tem material folhagem. A flag do prop NÃO liga vento: o vento afim é do estilo E e desloca só a folhagem; a flag autoriza o mesher a fundir essas faces. Estruturas rígidas sem folhagem declaram `false` |
| `medidas.caixa_u`, `dimensoes_u` | caixa dos voxels, em u, no espaço do modelo |
| `medidas.pegada_u` | colunas com voxel em y 0..3 (o que toca o chão): retângulo e área real |
| `medidas.tronco_u` | árvores: centro e raio do tronco medidos na faixa `faixa_y_u` (casca, sem folhagem) |
| `medidas.formas_medidas_u` | retângulos `[x0, z0, x1, z1]` (u, modelo) das colunas sólidas acima do que se sobe a pé (y ≥ 0,375 u), numa grade de 0,25 u (a da conferência de alcance), fundidos de forma gulosa |
| `pontos.entradas[]` | `pos_u` (onde o herói fica, fora da parede) e `rumo` (graus; 0 = olha para +z) |
| `pontos.interacao[]`, `spawns[]` | `pos_u` + `tipo` (`usar`, `examinar`, `comerciar`, `tocar`, `npc`, `criatura`…) |
| `pontos.observacao[]` | mirante: `pos_u` + `rumo` da vista |
| `pontos.area_segura_u[]` | retângulos `[x0, z0, x1, z1]` caminháveis (interior de ruína, pátio): espaço de luta ou de exploração |
| `pontos.formas_u[]` | formas autoradas (círculos das pedras do círculo), quando as medidas não descrevem bem |
| `relacoes[]` | lote 2: `{tipo, alvo, distancia_u}`. `tipo`: `acompanha` (pertence a uma construção), `perto_de`, `dentro_de`, `centro_de`. `alvo`: id de asset (inclusive aprovado) ou `tag:<nome>` (ex.: `tag:praca`). Dados para o gerador; não são impostos aqui |
| `variacoes.parametros` | parâmetros **seguros** e os valores possíveis (o primeiro é o padrão) |
| `variacoes.extremos[]` | cada extremo gerado, com a pegada, as dimensões, as faces e `pontos_iguais` (o teste exige `true`). Com `prop` preenchido, a variante é **publicada** como prop próprio no `props.json` (`<id>__<param>-<valor>`, `variante_de`): a placa de ofício por ofício, os cestos por conteúdo |

### Conjuntos de ofício (`conjuntos[]`, lote 2)

`{id, nome, oficio, ancora, leitura, referencial, itens: [{id, pos_u: [x, z], rumo}], caixa_u, estado}`. As posições ficam no
referencial da âncora (+z = frente da âncora) e giram com ela (mesma conta do §3). O build (`conteudo_e._conjuntos`) e o
teste `craftSetsKeepAnchorEntrancesFreeAndDoNotOverlap` conferem:
- cada item existe no `props.json`;
- nenhum item invade a pegada da âncora nem a de outro item;
- nenhuma entrada da âncora fica a menos de 0,3 u de um item.

`caixa_u` é a área do conjunto inteiro: é a pegada que o gerador reserva.

### Estados (lotes 1 e 2 avaliados)

Os lotes 1 e 2 foram avaliados pelo proprietário. Um lote ainda não avaliado (3 em diante) nasce `estado: candidato`,
com `aprovacao.artistica: candidato` e `integracao: aguardando_avaliacao` (teste: nunca nasce aprovado).

- **`restrito_a_conjuntos`** (lote 2, `basket_group`): aprovado, mas `distribuicao_procedural: false`. Só entra como
  item de um conjunto ou junto de uma construção. As variantes publicadas (`variante_de`) herdam a restrição. O teste
  exige que o asset esteja em pelo menos um conjunto.
- **Conjuntos:** `aprovacao.artistica: aprovado_como_proposta`. A `integracao` deles é a do membro mais restrito:
  `depende_colisao_composta` se a âncora ou um item depender de formas compostas (teste).
- **Rodada 12:** a colisão composta existe no jogo ([colisao-composta.md](colisao-composta.md)).
  - **Liberados:** `workshop_forge`, `ruin_house` e `market_stall`, percorridos pelo `CompositeCollisionTest`. Os
    conjuntos `ferreiro` e `comerciante` seguem a âncora e o item.
  - **Torre arruinada:** passou a `aguardando_refinamento`, porque a porta é estreita demais para o herói (P74).
  - **Regra do teste:** composto liberado ⇔ perfil `composta` em `colisao_e.json` ⇔ percorrido.
  - **Dados corrigidos:** o spawn da ferreira (P73) e o ponto dos escombros da ruína.

## 3. Do modelo para o mundo

O viewer põe o prop com `translate(x, chão + dy, z) · rotateY(r)` (graus). Um ponto `(px, pz)` do catálogo vai para:

```
X = x + px·cos r + pz·sin r
Z = z − px·sin r + pz·cos r          rumo no mundo = (r + rumo) mod 360
```

É a mesma conta de `tools/conteudo/vitrines.py` (`rot`), que põe os NPCs nos spawns. As vitrines mostram que ela bate:
Índira na frente da cabana redonda, Kelyra no balcão do sobrado, Bramor na porta da torre, Teodor nos escombros.

## 4. Colisão: o que serve hoje e o que precisa de adaptação

Hoje o jogo (`Collision.addProp`) usa **uma** forma por prop, centrada no **centro da caixa da malha**:

- **Serve como está:**
  - construções fechadas (`house_tall` caixa 0,82; `cabin_hunter` 0,80; `tower_watch` 0,62);
  - a cabana redonda (círculo de 1,55 u);
  - as árvores (`tronco`), com o tronco a ≤ 0,6 × raio do centro da caixa (teste).
- **Precisa de formas compostas** (marcado `"formas"`):
  - **oficina:** galpão aberto; com a caixa única, a forja, a bigorna e a ferreira ficam dentro do sólido;
  - **torre arruinada:** anel com porta e interior caminhável;
  - **círculo de pedras:** 7 pedras = 7 círculos; o centro é livre;
  - **ruína de casa:** cantos de parede e chaminé; o interior é caminhável.

  Enquanto o `Collision` não aceitar formas por prop, esses quatro **não podem** entrar no mundo procedural com a
  promessa de interior: ou ficam com `nenhum` (atravessáveis, inaceitável para paredes), ou com a caixa (interior
  inalcançável, e a conferência de alcance da rodada 11 recusaria o lugar).
- **Proposta (para o agente principal decidir):** perfil `"formas": [{"tipo": "caixa", "min": [x0, z0], "max": [x1, z1]},
  {"tipo": "circulo", "centro": [x, z], "raio": r}]` em u, no espaço do modelo, giradas com o prop. Os dados já saem no
  catálogo (`formas_medidas_u` / `formas_u`). É uma mudança pequena e localizada no `Collision.addProp`.

## 5. Mapeamento para a rodada 11 (lida no workspace principal, não modificada)

| Rodada 11 | Daqui |
|---|---|
| `RegionLayout.props` (nome, x, z, rumo) | `id` = nome do prop; `r` = rumo |
| patamares do `ProceduralField` onde há construção | `apoio.tipo == "patamar"`: raio ≈ meia-diagonal da `pegada_u` + `margem_u`; aplainar à altura do centro ou à mais alta (as vitrines usam a mais alta e deixam a fundação cobrir) |
| prop na área jogável em desnível ≤ 0,55 u | `inclinacao_max_u` por asset (0,35–0,7) — mais preciso que um limite único |
| `nearPlaces` / `limits` (vegetação se afasta) | `area_livre` |
| `PointOfInterest.type` futuro (`ruin`, `landmark`…) | `categoria` = `poi`, `subcategoria` (`ruina`, `santuario`) e `tags`. Sugestão: `ruin_tower`/`ruin_house` → `ruin`; `stone_circle` → `landmark` |
| `PointOfInterest.features` (`obj.<regiao>.<poi>.<n>`) | `pontos.interacao[].id` → `obj.r0.<poi>.<id>` |
| `PointOfInterest.spawns` (identidades estáveis) | `pontos.spawns[].id` → `r0.<poi>.<id>` |
| `Reachability.check` (alcance de frente) | `pontos.entradas[]`, `pontos.interacao[]` com `rumo`; `area_segura_u` para a arena livre ≥ 75% |
| `colisao_e.json` | `colisao` (perfil pronto para colar); `formas` só depois da extensão do §4 |

O que **não** depende da rodada 11: os modelos, o `props.json`, as vitrines (relevo legado), os testes e o catálogo.
O que **depende**: usar os pontos para gerar POIs e missões (`RegionGenerator`), os patamares (`ProceduralField`) e a
conferência de alcance com as formas compostas.

## 6. Carga e memória (decisão de integração)

O `Assets.props` lê e malha **todo** o `props.json` na primeira carga, mesmo os props que a cena não usa. Os 12
assets do lote 1 somam (duas medidas da MESMA malha, com definições diferentes):

- **+16,55 MB de dados de malha de cena** (`MeshCost`: 13 floats por vértice; +86% sobre os 19,14 MB dos 41 props
  aprovados);
- **+20,37 MB na GPU** (`Mesh.liveBytes`, o "MB vertices" do `--bench`): os mesmos dados mais a cópia só de posições
  para sombra e profundidade (3 floats por vértice), ou seja, 16,55 × 16/13. Medido no bench antes × depois: 82,8 →
  103,2 MB;
- **~120 ms** de leitura e malha de CPU (os aprovados: ~150 ms);
- números de `tools/conteudo/MeshCost.java`, no relatório técnico do pacote de revisão
  (`output/visual-review/world-content-lab/`, fora do git).

**Resolvido na integração:** o `Assets.props` passou a malhar sob demanda
([ADR 0030](../decisions/0030-catalogo-de-props-sob-demanda.md)).

- **Registro:** a biblioteca registrada não custa memória enquanto nenhuma cena a usa.
- **Menu:** fica com menos memória de vértices que a `main` antes da biblioteca (106,1 × 112,7 MB), porque nem os props
  aprovados que a cena não usa são carregados.
- **Vitrines e `bench_conteudo_*`:** pagam o que mostram.
