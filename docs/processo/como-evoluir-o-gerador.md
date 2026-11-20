# Como evoluir o gerador sem quebrar os mundos de ninguém

Guia da rodada 12. Os contratos estão em [ADR 0029](../decisions/0029-versoes-do-gerador.md),
[colisão composta](../architecture/colisao-composta.md) e [mundo procedural](../architecture/mundo-procedural.md) §9.
Os assets da biblioteca seguem o [guia de conteúdo](como-integrar-conteudo-ao-mundo.md).

## 1. Acrescentar uma versão nova do gerador

1. **Classe nova:** escreva-a (como `RegionV2`), sem tocar no caminho das versões anteriores. Reaproveite as peças sem
   estado (`sampleCurve`, `deg`, `crowded`, `composePost`…) e use um prefixo próprio nos rótulos (`Rng.of(semente, "v3",
   …)`).
2. **Constantes e despacho:** `RegionGenerator.V3 = 3`, um `case` em `compose` e `supports`.
3. **Perfil:** se a versão usa dados novos, um perfil congelado próprio (§2).
4. **Testes:** os de determinismo e restrição, como `GeneratorV2Test`.
5. **Olhar:** capturas (§12) e revisão das regiões.
6. **Congelar:** §2 e §3.
7. **Padrão:** só então `LATEST = V3`. Os mundos que já existem continuam na versão deles.

## 2. Congelar um perfil aprovado

- **Dados:** os assets que a versão usa ficam num arquivo próprio, escrito por script a partir do catálogo e revisado
  (`tools/conteudo/perfil_regiao_v2.py` → `assets/scenes/regiao_v2_e.json`). O gerador lê o perfil, nunca o catálogo
  gerado.
- **Composição:** com a composição aprovada, fixe a referência das 8 sementes:
  `VX_REFERENCIA_ESCREVER=N ./gradlew --no-daemon :game:test --tests vx.game.RegionGoldenTest` grava
  `game/src/test/resources/regioes-vN.txt` (recusa a v1 e recusa sobrescrever), e acrescente o teste
  `asRegioesDoGeradorVNNaoMudam`.
- **Depois disso:** mudou um hash = um mundo do jogador mudou. A correção é outra versão, nunca reescrever a referência.

## 3. Preservar os mundos antigos

- **A versão é do mundo:** está no `mundo.json` e no save v2. Abrir gera a região por ela
  (`ProceduralScene.create(assets, semente, versão)`).
- **Código antigo:** nunca refatore o caminho de uma versão já publicada. Se precisar, copie a peça para a versão nova.
- **Dados antigos:** nunca mude a pegada nem o perfil de colisão de um prop que uma versão publicada planta. Pegada nova
  é prop novo.
- **Antes de entregar:** `tools/qa/saves_antigos.sh RAIZ_DO_BINARIO_PUBLICADO . output/tmp/saves` (iguais, sem
  `.invalido`).

## 4. Acrescentar uma estrutura a uma região

- **Pré-requisitos:** o asset precisa estar `liberado` no catálogo e no perfil da versão.
- **Na v2:** construção ou conjunto entra na lista de candidatos do estilo (`RegionV2.POOL_CAMINHO`/`POOL_RURAL`), com
  peso. O encaixe é conferido peça a peça:
  - dentro do patamar;
  - fora da praça e do corredor da saída;
  - sem sobrepor outra construção, o chafariz ou o poço.
- **Conferência:** cada entrada vira um `checkpoint` da conferência de alcance.

## 5. Distribuir um conjunto de ofício

- **Origem:** o conjunto vem do perfil (âncora + itens no referencial dela). `RegionV2.build` põe a âncora e cada item
  girados juntos.
- **Internamente:** não se confere sobreposição entre as peças do mesmo conjunto (a composição interna é do catálogo,
  `ConteudoMundoTest.craftSetsKeepAnchorEntrancesFreeAndDoNotOverlap`).
- **Teste:** `GeneratorV2Test.conjuntosSaoPostosInteirosComOsItensNoLugarExato` exige o item no lugar exato.

## 6. Metadados de posicionamento

No catálogo e no perfil, todos no espaço do modelo, com +z = frente:

| Campo | Uso |
|---|---|
| `pegada` | o encaixe |
| `livre` (frente, lados, fundos) | folgas; numa vila compacta, a frente pode ser a praça |
| `entradas` (com rumo) | o alcance de 0,6 u e o caminho de chão batido até a porta |
| `interacao` | o objeto; quem usa fica de pé a até 1 u |
| `spawns` | onde um morador ficaria |

Vire o modelo para a praça com `deg(centro − posição)`.

## 7. Criar colisão composta

Siga [colisão composta](../architecture/colisao-composta.md) §6:

1. `formas: "medidas"` no `@asset`;
2. `tools/conteudo/formas_compostas.py` e revisão do diff de `colisao_e.json`;
3. o id em `CompositeCollisionTest.ENTERABLE`;
4. o teste verde;
5. só então liberado.

## 8. Validar a entrada de uma construção

- **Isolado:** `CompositeCollisionTest` põe o prop em chão plano, em 5 rumos, e confere entrada, interior, piso,
  câmera e os NPCs nos spawns.
- **Na região:** o `checkpoint` `entrada:…` da conferência de alcance (colisão real, grade de 0,25 u, degrau 0,30 u).
- **Ponto de interação inalcançável:** o defeito é do ponto ou do modelo, nunca da conferência.

## 9. Criar um POI

1. Escolha o lugar (fora do assentamento, perto da trilha) e um patamar (`RegionLayout.pads`).
2. Faça a trilha secundária escavada (`RegionLayout.Branch`, `carve = true`).
3. Dê o id derivado de rótulo (`poi.r0.<tipo>.<n>`), nunca de coordenada.
4. Ponha as entradas e os pontos como `checkpoints`.
5. Acrescente o tipo novo ao contrato ([pontos de interesse](../architecture/contrato-pontos-de-interesse.md)).

## 10. Acrescentar uma descoberta

1. **Dados:** um tipo novo em `assets/scenes/descobertas_e.json` (nome, verbo da placa, texto, texto de "já visto",
   moedas pequenas). O texto só diz o que o lugar mostra.
2. **Gerador:** uma `Feature` de `kind` `"descoberta"` com id derivado do POI (`desc.r0.<tipo>.<n>`), na lista de
   features do POI, mais um `checkpoint` `descoberta:…`.
3. **O que já existe:** `ProceduralScene` monta `jogo.descobertas`; `GameLogic.useDiscovery` dá o aviso, as moedas uma
   vez, a frase e o salvamento seguinte; o diário lista.

## 11. Validar a persistência

- **Testes:** `DiscoveryTest` (uma vez só, salvar e reabrir, personagens e mundos independentes, save sem o campo,
  descoberta desconhecida recusada), `GeneratorVersionTest` (versão desconhecida intacta) e `SaveGameTest`.
- **Saves de verdade feitos pelo binário anterior:** `tools/qa/saves_antigos.sh`.
- **Nunca** use `output/` (os saves do proprietário). Use sempre pastas temporárias (`-Dvx.savegame`, `-Dvx.mundos`,
  `-Dvx.personagens`).

## 12. Comparar sementes entre versões

```bash
for s in 1024 2048 4096 77; do
  JAVA_OPTS="-Dvx.som=mudo -Dvx.mundo=procedural -Dvx.gerador=1 -Dvx.semente=$s -Dvx.savegame=-" game/build/install/game/bin/game --roteiro tools/mundo/01-vistas.txt output/tmp/v1-s$s 30
  JAVA_OPTS="-Dvx.som=mudo -Dvx.mundo=procedural -Dvx.gerador=2 -Dvx.semente=$s -Dvx.savegame=-" game/build/install/game/bin/game --roteiro tools/mundo/04-vistas-v2.txt output/tmp/v2-s$s 30
  .venv/bin/python tools/qa/prancha.py output/tmp/s$s-v1-v2.png 2 760 output/tmp/v1-s$s/1-geral.png output/tmp/v2-s$s/01-geral.png output/tmp/v1-s$s/2-assentamento.png output/tmp/v2-s$s/02-assentamento.png
done
```

Ou de uma vez: `tools/review12.sh comparacoes`.
