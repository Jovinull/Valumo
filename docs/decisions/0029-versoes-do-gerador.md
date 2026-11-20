# 0029 — Versões do gerador: a v1 congelada para os mundos v1, a v2 só para mundos novos (proposta)

**Status:** proposta (integração dos lotes 1 e 2 de conteúdo). **Nada disto está implementado.** O gerador continua
sendo um só (`RegionGenerator.VERSION = 1`) e os lotes entram só como biblioteca (ADR 0030).

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
