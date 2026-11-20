# Como criar, registrar e liberar conteúdo do mundo

Guia da biblioteca de conteúdo (lotes 1 e 2, integrados depois da rodada 11.1).

- **Arte:** [art/conteudo-mundo.md](../art/conteudo-mundo.md).
- **Contrato do catálogo:** [architecture/catalogo-de-conteudo.md](../architecture/catalogo-de-conteudo.md).
- **Procedimento do agente:** skill `conteudo-mundo`.

Este guia é o caminho de ponta a ponta, do modelo até o gerador.

## 0. As três perguntas, separadas

Um asset tem três aprovações, e uma não implica a outra:

| Campo do catálogo | Pergunta | Quem decide |
|---|---|---|
| `aprovacao.artistica` | está bonito, na E, original? | o proprietário |
| `aprovacao.integracao` | o jogo consegue usar com segurança? | o teste (colisão, pontos, alcance) |
| `aprovacao.distribuicao_procedural` | o gerador pode espalhar por conta própria? | o agente principal, numa versão nova do gerador |

Estados de integração:

- **`liberado`:** cabe no jogo com o perfil de uma forma.
- **`depende_colisao_composta`:** precisa de formas por prop
  ([colisao-composta.md](../architecture/colisao-composta.md)).
- **`aguardando_refinamento`:** aprovado como direção, mas não como peça final.
- **`restrito_a_conjuntos`:** só faz sentido dentro de um conjunto de ofício.
- **`nao_distribuivel`:** rejeitado ou só de estudo.

**Um asset aprovado artisticamente não é, por isso, um asset pronto para o jogo.**

## 1. Criar e registrar um asset

1. Escreva o construtor no módulo da categoria:
   - `tools/conteudo_construcoes.py`, `conteudo_vegetacao.py`, `conteudo_poi.py`, `conteudo_props.py` ou
     `conteudo_assentamento.py`;
   - use o decorador `@asset(...)` de `tools/conteudo_lib.py` (campos na skill `conteudo-mundo`).
2. `python3 tools/build_assets.py conteudo_e`. O registro é automático:
   - o `.vox` vai para `assets/environment/e/`;
   - o prop entra em `props.json`;
   - a ficha entra em `conteudo_catalogo.json`.
   Os três são gerados e ficam fora do git.
3. Registrar **não custa memória no jogo**: o `Assets.props` só malha o que uma cena pede (ADR 0030).

## 2. Variante

- No `@asset`, declare `variacoes={param: [valores]}`. A variante muda a aparência, nunca a pegada nem os pontos (teste
  `variantsKeepTheFootprintAndThePoints`).
- Se o jogo precisar de uma variante como prop próprio (o pictograma de uma placa, o conteúdo de um cesto), use
  `publicar_variantes=True`. Ela sai como prop `<id>__<param>-<valor>`; hoje são 6.

## 3. Restrições

- **Frequência:** `frequencia="incomum"` (ou `rara`). É o gerador que respeita; o catálogo só informa.
- **Onde pode ficar:** `restricoes=["encostado em construção ou na borda da mata"]`, mais `terreno`, `apoio`,
  `inclinacao_max_u`, `area_livre` e `relacoes` (`acompanha`, `perto_de`).
- **Só em conjunto:** `integracao = restrito_a_conjuntos` (ex.: `basket_group`). O gerador nunca o planta solto.

## 4. Conjunto de ofício

`tools/conteudo_conjuntos.py`: uma âncora e itens com posição no referencial dela.

- **O build recusa:** item que invade a âncora, itens sobrepostos e entrada bloqueada (teste
  `craftSetsKeepAnchorEntrancesFreeAndDoNotOverlap`).
- **Estado:** o conjunto herda a dependência da âncora e dos itens. O `ferreiro` (âncora `workshop_forge`) e o
  `comerciante` (item `market_stall`) dependem da colisão composta.

## 5. Vitrine

- **Fonte:** `tools/conteudo/vitrines.py` gera `assets/scenes/vitrine_conteudo_*.json` (fonte à mão versionada, escrita
  pelo script) e `bench_conteudo_*.json`.
- **Assentamento:** a vitrine assenta pelo relevo medido (`GroundProbe`) e põe NPCs existentes nos spawns.
- **Captura:** `.venv/bin/python tools/conteudo/cenas.py SAIDA`.
- **Uso:** a vitrine é cena de revisão, não cena de jogo. Carrega só o que mostra.

## 6. Validar geometria e metadados

```bash
./gradlew :viewer:test --tests vx.viewer.ConteudoMundoTest     # 11 testes: medidas, vento, fragmentos, entradas, pontos, conjuntos, custo
python3 tools/asset_audit.py assets/environment/e
.venv/bin/python tools/conteudo/determinismo.py PASTA_TEMP      # dois processos, bytes iguais
java -cp "viewer/build/install/viewer/lib/*" tools/conteudo/MeshCost.java    # triângulos, MB, ms por prop
tools/conteudo/vistas.sh <id>                                   # 8 vistas com o herói
```

Nunca afrouxe teste nem tolerância: o defeito é do asset.

## 7. Acrescentar colisão (depois)

- **Asset `liberado`:** cole a ficha `colisao` do catálogo em `assets/scenes/colisao_e.json` (`props.<id>`). Rode
  `GameplayTest` e a vitrine percorrida.
- **Asset que depende de formas compostas:** espera a extensão do `Collision`
  ([colisao-composta.md](../architecture/colisao-composta.md)). Não use `nenhum` em parede nem a caixa inteira num galpão.
- **Props da v1:** nunca altere o perfil de um prop que o gerador v1 planta. A referência das sementes falharia, e com
  razão.

## 8. Liberar um asset para o gerador

Distribuir muda o resultado das sementes. Por isso:

1. **Versão:** a liberação acontece numa **versão nova do gerador** (ADR 0029, proposta), nunca na v1. A v1 é conferida
   por `RegionGoldenTest` contra `game/src/test/resources/regioes-v1.txt`.
2. **Gerador:** o asset entra no perfil da versão nova (lista, peso, vagas, raio de exclusão). Os pontos do catálogo
   viram POIs, spawns e interações com ids derivados de rótulo (`contrato-pontos-de-interesse.md`).
3. **Conferência:** o `Reachability` passa nas sementes de teste. Uma recusa nova é regressão, a menos que seja
   justificada.
4. **Referência:** a versão nova ganha `regioes-v2.txt` quando aprovada.

## 9. Não quebrar save

- **Quem lê o quê:**
  - o save v1 (vila) não lê nada do catálogo;
  - o save v2 confere gerador, versão e vínculos.
- **O que não quebra:** acrescentar asset à biblioteca **não** muda nenhum save, porque o gerador v1 não lê o
  `props.json`.
- **O que quebra hoje:** mudar o gerador sem versão nova faz o save v2 ser recusado (`.invalido`, com o motivo). É
  exatamente o que a ADR 0029 evita.
- **Antes de entregar:** os testes de save com pastas temporárias (`-Dvx.savegame=`, `-Dvx.mundos=`, `-Dvx.personagens=`),
  nunca com `output/`.

## 10. Reproduzir os testes da integração

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew test :viewer:installDist :game:installDist                 # inclui RegionGoldenTest, ConteudoMundoTest, AssetsLazyTest
game/build/install/game/bin/game --roteiro tools/menu/14-memoria.txt SAIDA 30   # recursos vivos e tempos (dados TEMPORÁRIOS, ver o cabeçalho)
viewer/build/install/viewer/bin/viewer --layout vitrine_conteudo_vila_a --bench "1920x1080,style=e,quality=padrao,runs=3"
```
