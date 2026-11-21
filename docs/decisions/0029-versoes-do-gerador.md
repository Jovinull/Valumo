# 0029 — Versões do gerador: a v1 congelada para os mundos v1, a v2 só para mundos novos

**Status:** aceita, **implementada na rodada 12** e com as **duas versões publicadas** no fechamento da v2 (tag
`procedural-world-v0.6`; seção "Publicação da v2", no fim). A seção "Implementação (rodada 12)" diz o que foi feito e onde
difere da proposta. A proposta original (integração dos lotes 1 e 2) fica abaixo como registro.

## Contexto

- **Gravação:** o mundo procedural guarda a versão do gerador em dois lugares.
  - O `mundo.json` (`versaoGerador`) e o save v2 (`mundo.gerador`, `mundo.versaoGerador`, `vinculos`).
  - Quem abre gera a região de novo e compara.
- **Hoje, subir `RegionGenerator.VERSION` quebra todos os mundos existentes:**
  - **Entrar (`Game.enterAdventure`):** recusa com "Este mundo foi criado pelo gerador v1; esta versão do jogo gera v2".
  - **Ler o save (`SaveGame.parse`):** recusa com "mundo salvo diferente deste" e guarda uma **cópia** em `.invalido`. É o
    mesmo caminho de um arquivo corrompido, embora o save seja válido e só de outra versão.
- **O que vem pela frente:** a biblioteca de conteúdo (lotes 1 e 2: 35 assets, 6 variantes, 5 conjuntos) é para ser
  distribuída nas regiões. Distribuir muda o resultado das sementes, logo exige uma versão nova do gerador. Os mundos que
  o jogador já criou não podem deixar de abrir por isso.

## Decisão (proposta)

**Várias versões do gerador convivem.** Cada mundo usa, para sempre, a versão com que foi criado. Não há migração nem
reposicionamento.

### 1. Despacho por versão

`RegionGenerator.compose(seed, attempt, version)`:

- **Despacho:** escolhe um **perfil de geração** (`PerfilV1`, `PerfilV2`, …).
  - O perfil é um objeto imutável com o que muda entre versões: as listas de props e pesos, os arranjos do assentamento,
    os componentes de mata, os modelos de missão aceitos e o subconjunto da biblioteca liberado.
  - As **peças** são compartilhadas: `Rng` por rótulo, `ProceduralField`, `Reachability`, a trilha, os patamares e as
    tentativas. Isso evita duplicar código entre versões.
- **v1:** é o código de hoje com as constantes extraídas para o `PerfilV1`, sem mudar uma conta.
- **v2:** reaproveita as peças e acrescenta componentes novos (os conjuntos de ofício, as ruínas). Nunca altera o
  caminho do v1.
- **Rótulos do RNG:** as versões novas usam rótulos novos para os sorteios novos. Um rótulo a mais na v2 não pode
  deslocar os sorteios da v1, porque os rótulos são independentes por construção (`Rng.derive`).

### 2. O que a v1 precisa preservar (as entradas congeladas)

A região v1 depende de:

- **Código:**
  - `RegionGenerator` (arranjos, mata, lago, colina, trilha, posto);
  - `ProceduralField` (relevo);
  - `Reachability`, porque a tentativa aceita depende dela;
  - `ProceduralScene` (documentos, instância de missão).
- **Dados:** `habitantes_e.json`, `modelos_missao_e.json` (o modelo `posto_trilha` v1), `combate_e.json → encontros`.
- **Colisão:** os perfis em `colisao_e.json` **dos props que a v1 planta**: `house`, `well`, `barrel`, `crate`, `fence`,
  `log`, `stump`, `trail_post`, o marco, as árvores, as pedras e a vegetação baixa.
- **Assets:** as dimensões desses mesmos props. A caixa de colisão sai da caixa da malha (`Collision.addProp`): mudar a
  pegada de um prop da v1 muda a colisão, a conferência de alcance, talvez a tentativa aceita, e com isso a região.
  - **Regra:** melhoria visual num prop da v1 mantém a pegada; uma pegada nova é um **prop novo** (nome novo), usado só
    pela v2.

Tudo isso é conferido pela referência `game/src/test/resources/regioes-v1.txt` (`RegionGoldenTest`). Ela foi fixada na
`main` publicada antes da integração e guarda o sha256 da região inteira de 8 sementes. Mudou um hash = um mundo v1
mudou; o teste falha. **Nunca se atualiza a referência para passar.**

### 2b. Como a v2 usa a biblioteca de conteúdo

- **Lista própria e congelada:** o `PerfilV2` lista, POR NOME, os assets que distribui, com peso, lugar e vagas. Ele
  não lê o catálogo gerado em tempo de execução: um asset melhorado depois não pode mover uma região v2 já criada.
- **Coerência com o catálogo:** um teste confere a lista contra o catálogo (`conteudo_catalogo.json`):
  - só entra asset com `aprovacao.integracao = liberado` e `distribuicao_procedural = true`;
  - `restrito_a_conjuntos` só aparece dentro de um conjunto;
  - `depende_colisao_composta` só entra depois da extensão do `Collision`
    ([colisão composta](../architecture/colisao-composta.md));
  - `frequencia` e `restricoes` viram peso e regra de lugar (ex.: `woodshed_open` incomum, encostado em construção ou
    na borda da mata).
- **Conjuntos de ofício:** são plantados como unidade, com a âncora e os itens nas posições relativas do catálogo,
  girados juntos. O `Reachability` confere o conjunto inteiro.
- **POIs a partir dos pontos do catálogo:**
  - `entradas`, `interacao` e `spawns` viram ids estáveis derivados de rótulo (`obj.r0.<poi>.<id>`, `r0.<poi>.<id>`),
    como na v1 (`contrato-pontos-de-interesse.md`);
  - a conferência de alcance usa as entradas e os pontos de interação com o rumo.
- **Pegadas:** a pegada de cada asset usado pela v2 entra na referência `regioes-v2.txt`. Mudar a pegada depois é um
  prop novo ou uma v3, nunca uma mudança silenciosa.

### 3. Criar mundo e abrir mundo

- **Criar:** o catálogo grava `versaoGerador` = a versão PADRÃO do jogo (a mais nova estável).
  - Quando existir a v2, os mundos novos nascem v2.
  - O formulário pode, opcionalmente, oferecer "estilo antigo" (v1). Não é obrigatório.
- **Entrar:** usa `ProceduralScene.create(assets, seed, world.generatorVersion())`.
  - **Versão suportada:** abre; o save v2 é conferido contra a região DESSA versão, então bate.
  - **Versão desconhecida** (um mundo criado por um jogo mais novo): recusa com a explicação ("criado por uma versão mais
    nova do Valumo") e **não** toca no arquivo. Sem cópia `.invalido`: o arquivo não está corrompido.
- **Save:** o formato não muda (o v2 já guarda gerador, versão e vínculos). O `SaveGame.parse` passa a receber a cena
  gerada pela versão salva.
  - O ramo "mundo salvo diferente deste" continua existindo para o que ele protege de verdade: o mesmo gerador e a mesma
    versão produzindo outra coisa, ou seja, um defeito.
  - Esse ramo é que deve continuar guardando `.invalido`.

### 4. Prévias

- **Chave:** a chave da prévia já inclui a versão (`<semente>.g<versão>.v<prévia>`). Um mundo v1 e um mundo v2 com a
  mesma semente têm prévias diferentes e não se confundem.
- **Na lista de mundos:** a versão não aparece como texto técnico; no máximo um rótulo de estilo para o jogador, se o
  proprietário quiser.

### 5. Vínculos de missão

- **Formato:** o vínculo (`Binding`) guarda o modelo e a versão do modelo.
- **v1:** continua instanciando o `posto_trilha` v1.
- **v2:** pode trazer modelos novos (com outra versão), mas nunca altera o v1.
- **Missão em andamento:** como a região é a da versão do mundo, ela nunca muda de lugar.

### 6. Validação de uma versão nova

1. `RegionGoldenTest` da v1 continua verde: a versão nova não mexeu no que a v1 lê.
2. A versão nova ganha a referência dela (`regioes-v2.txt`), fixada quando for aprovada.
3. `ProceduralWorldTest` roda por versão: determinismo, alcance, ids estáveis.
4. Roteiro: abrir um mundo v1 salvo (com missão em andamento e concluída) num binário com v1 e v2.

## Alternativas descartadas

- **Subir a versão e recusar os mundos antigos:** é o comportamento de hoje, e o proprietário o recusou.
- **Migrar os mundos v1 para v2:** reposicionaria estalagem, POI e missão em andamento; é migração silenciosa.
- **Guardar a região inteira no save:** arquivo grande e uma segunda fonte da verdade; a semente e a versão bastam,
  desde que a versão antiga continue existindo.
- **Copiar o gerador inteiro por versão:** duplicação que apodrece. O perfil + as peças compartilhadas + a referência
  por versão dão a mesma garantia com muito menos código.

## Implementação (rodada 12)

**O que existe:**

- **Versões:** `RegionGenerator.V1 = 1`, `V2 = 2`, `LATEST = V2` (os mundos novos nascem na v2) e `supports(v)` (1 e 2).
- **Despacho:** `RegionGenerator.compose(semente, tentativa, versão, perfil, vegetação)` escolhe o caminho.
  - **v1:** é `composeV1`, o código da rodada 11 **literalmente igual**, sem extrair constantes para um objeto de perfil.
    Não refatorar foi a forma mais segura de não mover nenhum sorteio.
  - **v2:** é a classe `RegionV2`. Reaproveita as peças sem estado da v1 (`sampleCurve`, `deg`, `minPathDist`,
    `playableEdge`, `crowded`, `nearPlaces`, `composePost`, `RESIDENTS`); todo sorteio novo leva o prefixo `"v2"` no
    rótulo do `Rng`.
- **Perfil da v2:** é o arquivo **congelado** `assets/scenes/regiao_v2_e.json` (`RegionProfile`).
  - Guarda pegada, altura, área livre, entradas, interação, spawns e os conjuntos.
  - Foi escrito uma vez por `tools/conteudo/perfil_regiao_v2.py` a partir do catálogo e revisado.
  - O gerador não lê o catálogo gerado. `GeneratorV2Test.oPerfilCongeladoAindaBateComOCatalogo` confere que os dois
    ainda batem.
- **A versão é do mundo, não do jogo:**
  - `RegionLayout.version` e `RegionLayout.key()` (`s1024.g2.r0`);
  - o id do relevo (`proc.<semente>.v<versão>.<tentativa>`, chave do cache de `Terrain`);
  - o `_doc` da cena (a v1 continua escrevendo `v1`, e por isso a impressão digital dela não mudou);
  - o save v2 (`mundo.versaoGerador` = a versão da região).
- **Criar e abrir:**
  - o menu cria na `LATEST`;
  - `Game.enterAdventure`, as prévias e o resumo geram na versão do `mundo.json`;
  - a adoção de saves antigos lê a versão gravada no save (`SaveGame.peekGenerator`) e cria o mundo nela;
  - o modo técnico usa `-Dvx.gerador=N`, ou a versão do save que já existe na pasta da semente, ou a `LATEST`.
- **Versão desconhecida** (mundo de um jogo mais novo):
  - `WorldStore.World.supported()` = falso: o mundo continua na lista, sem prévia;
  - entrar mostra "Este mundo foi criado por uma versão mais nova do Valumo e não pode ser aberto aqui. Nada foi
    alterado.";
  - **nenhum arquivo é tocado** e não há `.invalido`;
  - `ProceduralScene.create` recusa a versão com `Rejected`.
- **`.invalido` só para o que é defeito de verdade:**
  - o save diz uma versão e o mundo diz outra;
  - vínculos diferentes da região gerada;
  - descoberta desconhecida;
  - JSON ilegível.
- **Referências:**
  - `game/src/test/resources/regioes-v1.txt` (inalterada, 8/8);
  - `regioes-v2.txt` (fixada na aprovação da composição desta rodada);
  - `RegionGoldenTest` confere as duas;
  - o escritor (`VX_REFERENCIA_ESCREVER=N ./gradlew --no-daemon :game:test --tests vx.game.RegionGoldenTest`) recusa a
    v1 e recusa sobrescrever.
- **Testes:**
  - `GeneratorVersionTest`: v1 continua v1, mundo novo na mais nova, versão desconhecida intacta, save v1 válido nunca
    `.invalido`, adoção na versão do save;
  - `GeneratorV2Test`;
  - `RegionGoldenTest`;
  - a matriz `tools/qa/saves_antigos.sh` (saves feitos pelo binário publicado anterior, abertos pelo novo).

**Diferenças em relação à proposta:**

- não há `PerfilV1` (a v1 é o código congelado);
- o formulário de mundo não oferece "estilo antigo" (não foi pedido);
- a v2 tem a mesma região controlada da v1 (±16,5 u), sem aumentar o mapa.

## Publicação da v2 (fechamento das rodadas 12 a 12.3, tag `procedural-world-v0.6`)

**Aprovação:** o proprietário fez o teste físico completo (movimento, combate, NPCs, ruínas, câmera, descobertas,
personalização e saves), aprovou e autorizou a publicação.

**O que mudou no código:**

- `RegionGenerator.published(v)` = v1 e v2.
- Nenhum algoritmo, peso, RNG, POI, morador, colisão ou geometria mudou.

**A garantia** (as 8 sementes de cada versão estão fixadas em `regioes-v1.txt` e `regioes-v2.txt`, `RegionGoldenTest`):

| Versão | Situação | Quem a usa |
|---|---|---|
| v1 | publicada e congelada desde `worlds-and-menus-v0.5` | os mundos v1, para sempre |
| v2 | publicada e congelada nesta publicação | os mundos v2, para sempre; é a dos mundos novos (`LATEST`) |
| v3 | não existe | o que mudar o resultado de uma semente daqui em diante |

**Mundos das versões de testes da v2** (rodadas 12 a 12.3, como o "RUINAS" do proprietário):

- **Não são migrados.** A região daquela semente pode ter mudado antes da publicação.
- **Save que não bate:** é recusado sem `.invalido` e sem tocar no arquivo, com a frase "de uma versão de testes"
  (`RegionGenerator.hadTestBuilds(2)`). Depois da publicação a região de uma semente v2 não muda mais, então só esse
  período produz a diferença.
- **A v1 não teve versões de testes:** um save dela que não bate "não corresponde a este mundo".

**Do que a v2 depende** (mudar qualquer item muda regiões e exige a v3): a lista está em
[mundo procedural](../architecture/mundo-procedural.md) §13.

**Testes:**

- `GeneratorVersionTest.asVersoes1E2SaoPublicadasESuportadas`;
- `SaveCompatTest` (a frase das versões de testes com a v2 publicada);
- `RegionGoldenTest`.
