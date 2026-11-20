# Relatório da Rodada 12 — Mundos com Identidade

**Situação:** entregue para avaliação no ramo LOCAL `feature/world-generation-v2`, a partir da `main` `4cbde86`
(integração dos lotes 1 e 2). São 44 commits pequenos, todos locais.

- **Não feito:** push, PR ou tag. A `main` e as tags antigas não foram tocadas.
- **Fora da rodada:** nenhum asset novo de arte, nenhum NPC novo, o Titã e o `stone_circle`.

**Pacote local:** `output/visual-review/round-12/`, reproduzível com `tools/review12.sh`. Contém:

- capturas por semente e comparações v1 × v2;
- plantas da colisão composta;
- os dois vídeos (fora do ZIP);
- desempenho, saves antigos e registros.

**Contratos:**

- [ADR 0029](../decisions/0029-versoes-do-gerador.md), implementada;
- [colisão composta](../architecture/colisao-composta.md);
- [mundo procedural](../architecture/mundo-procedural.md) §9;
- [persistência](../architecture/persistencia-mundo-procedural.md);
- [save](../architecture/save.md);
- guia [como evoluir o gerador](../processo/como-evoluir-o-gerador.md).

## O que foi feito

1. **Portão A — versões do gerador convivendo.**
   - **Despacho:** `RegionGenerator.V1`/`V2`/`LATEST = V2`, `compose(semente, tentativa, versão)` e
     `ProceduralScene.create(assets, semente, versão)`.
   - **De onde vem a versão:** do mundo (`mundo.json → versaoGerador`, save v2 `mundo.versaoGerador`). Na linha de
     comando: `-Dvx.gerador`, senão a do save, senão `LATEST`.
   - **A v1:** o corpo da rodada 11, intocado (`composeV1`). A v2 é uma classe própria (`RegionV2`), com rótulos `"v2"`.
   - **Versão desconhecida:** não abre ("…criado por uma versão mais nova do Valumo… Nada foi alterado.") e não toca no
     arquivo.
   - **Sem** save v3, migração ou mudança de missão.
2. **Portão B — colisão composta.**
   - **Perfil:** `composta` em `colisao_e.json`, várias caixas e círculos no espaço do modelo, transformados pela
     matriz do prop (`Collision.addComposite`). Cada forma tem corpo, câmera e pisável.
   - **Medição:** formas medidas dos voxels por `tools/conteudo/formas_compostas.py`: faixa do corpo, peças baixas,
     partes altas só de câmera, piso elevado pisável e, desde esta rodada, a copa acima da cabeça só de câmera.
   - **Liberados:** oficina, ruína de casa e barraca. A torre arruinada NÃO (P74).
   - **P73 resolvido:** o spawn da ferreira saiu de cima da bigorna.
3. **Portão C — gerador v2 com a biblioteca.**
   - **Perfil:** o gerador lê o perfil congelado `regiao_v2_e.json` (54 assets, 5 conjuntos), nunca o catálogo gerado.
   - **Dois estilos:**
     - de caminho: praça com chafariz, comércio, oficina, torre;
     - rural: poço com sarilho, lavoura, paiol, caçador, herbalista.
   - **Construções:** a estalagem ao fundo e mais duas a quatro construções ou conjuntos de ofício, sorteados por peso e
     encaixados peça a peça, inteiros.
   - **A ruína:** fora da vila, num patamar com trilha própria. A trilha sai da principal ANTES da arena do posto.
   - **Moradores** nos lugares livres.
   - **Mata:** três misturas, com choupos, a árvore retorcida rara perto da ruína e a árvore seca rara.
   - **Conferência:** toda entrada, interação e descoberta é um `checkpoint` da conferência de alcance (colisão real).
4. **Portão D — a primeira descoberta.**
   - **Onde:** a lareira da ruína (`desc.r0.ruina.0`): placa "Examinar a lareira" e E.
   - **Na primeira vez:** "Descoberta: Casa tomada pela mata · +5 moedas", uma frase e o som de objetivo.
   - **Persistência:** salvamento no primeiro passo seguro; uma vez por personagem × mundo
     (`progresso.descobertas`, opcional no save v2).
   - **Onde aparece depois:** no diário ("Descobertas") e na lista de mundos ("· 1 lugar descoberto").
   - **Sem** inventário nem raridade.
5. **Demais entregas:**
   - **Roteiros:** `luz`, `vista-lugar`, `ir-lugar`, `seguir-ramo`.
   - **Medição:** a medição de GPU por passo (sombra) no relatório `perf` do jogo.
   - **Ferramentas:** `tools/review12.sh`, `tools/qa/resumo_desempenho12.py`, `tools/conteudo/planta_colisao.py` e
     `tools/conteudo/perfil_regiao_v2.py`.
   - **Documentos e skills** atualizados.

## Defeitos achados e corrigidos na rodada

| Defeito | Como apareceu | Correção |
|---|---|---|
| Formas compostas da 1ª medição: o retângulo começava no chão e incluía o arco, fechando a porta da torre | `CompositeCollisionTest` | medição por coluna de voxel, com a faixa do corpo a partir da base real do voxel; o alto vira forma só de câmera |
| Herói afundado na laje de 0,375 u da oficina e da ruína | captura | piso elevado = forma pisável no degrau (0,30 u) |
| Ponto "escombros" da ruína 1,3 u dentro do telhado caído | teste de interação | o ponto marca a frente do monte |
| Spawn da ferreira a 0,125 u da bigorna (P73) | teste de 7 passos | spawn no lado direito do galpão (0,67 u da forja, 1,0 da bigorna) |
| Conjuntos nunca cabiam (caixa inteira assimétrica, peças do mesmo conjunto conferidas entre si, frente do catálogo inteira) | medição de 60 sementes | encaixe peça a peça, sem conferir o conjunto contra si mesmo, frente de 1,0 u |
| Chafariz e poço fora do ocupado: conjunto encostado neles | captura | entram no `taken` com margem |
| Kelyra virada para o chafariz (a fala caía dentro dele) | captura | ao lado da porta, de lado; a placa do outro lado |
| A trilha da ruína saía de DENTRO da arena do posto: quem ia à ruína entrava na luta | vídeo da demonstração (o herói parou no ponto 11, cercado) | a bifurcação sai do ponto mais perto FORA da arena (+ teste) |
| Vila rala: a semente 23 saía com uma construção só além da estalagem | `densidadeDentroDoOrcamento` depois da correção acima | segunda passada nos que não couberam, com mais folga |
| A copa da árvore de dentro da ruína não segurava a câmera: quem examinava a lareira sumia atrás das folhas | captura no jogo | a folhagem acima da cabeça vira forma só de câmera, como a copa das árvores soltas (+ teste que falha sem a correção) |
| `vista-lugar marco` e a linha `regiao` pegavam a primeira feature (na v2, a descoberta) | demonstração | procura pelo tipo `marco_de_luz` |
| A árvore retorcida "rara junto à ruína" nunca saía (0 de 60 sementes): a reserva da própria ruína cobria o patamar inteiro e a mata já tinha tomado o anel em volta. Na primeira correção, plantada fora do plano, ela reprovava a tentativa por desnível (60 → 59 sementes aceitas) | contagem dos modelos postos (Q10) | plantada antes da mata, no plano do patamar, com a MESMA conferência de desnível da `Reachability` (cantos girados da caixa do modelo); sai em 24 de 60, sem recusa; teste novo `asArvoresRarasSaemEARetorcidaFicaJuntoDaRuina` |
| Vídeo de 1080p estourava a cota do `/tmp` (tmpfs) | gravação | quadros em `output/tmp` (e a skill avisa) |

## Números

**Gerador v2, sementes 1–60 (sem GPU, binário final):**

- **Aceitas:** 60 de 60, com 14 tentativas recusadas e refeitas e cerca de 137 ms por semente.
- **Por que recusa:** degrau de props em desnível (marco da trilha, choupo, pedra), falta de lugar para a ruína e um
  ponto da oficina fora do alcance. Em todos os casos a tentativa seguinte passa.
- **Conjuntos:** os 5 aparecem. Por número de regiões:

  | Conjunto | Regiões |
  |---|---|
  | ferreiro | 25 |
  | herbalista | 23 |
  | caçador | 17 |
  | comerciante | 16 |
  | guardião | 16 |

- **Densidade:** P81. De caminho, quase sempre a estalagem + 2. Rural, + 2 a + 4.
- **Árvores raras:** a retorcida em 24 de 60 e a seca em 22 de 60.

**Formas de colisão por região:**

| | v1 | v2 |
|---|---|---|
| sementes 1–30 | 200–211 | 272–365 |
| s1024 medida no jogo | 212 | 278 |
| s31337 medida no jogo | — | 353 |

**Desempenho** (1080p, janela oculta, mudo, glFinish por quadro, o mesmo binário e a mesma sessão, intercalado, 3
repetições; Iris Xe):

| Cenário | quadro mediana / p95 / p99 (ms) | lógica (ms) | GPU sombra (ms) | malhas (MB) | cena montada (ms) | gerar + conferir, 1ª vez no processo (ms) |
|---|---|---|---|---|---|---|
| vila fixa | 9,55 / 11,05 / 11,74 | 0,29 | 1,20 | 206 (106,1) | 2126 | — |
| região v1 s1024 | 9,32 / 10,55 / 11,20 | 0,39 | 1,15 | 96 (77,2) | 2089 | 144 |
| região v2 s1024 (rural) | 9,07 / 10,45 / 11,26 | 0,48 | 1,01 | 126 (87,3) | 2732 | 345 (uma tentativa recusada) |
| região v2 s31337 (caminho) | 9,70 / 11,19 / 11,75 | 0,57 | 1,16 | 127 (87,5) | 2671 | 229 |

- **Ruído entre sessões:** a mesma cena variou ~0,6 ms (a vila fixa deu 8,93 ms numa medição anterior, antes da
  correção da árvore). Por isso a comparação vale DENTRO da sessão.
- **Quadro:**
  - a v2 rural fica abaixo da v1;
  - a de caminho fica 0,38 ms acima da v1 e 0,15 acima da vila fixa (no nível do ruído).
- **Sombra:** não piora.
- **Lógica:** sobe de 0,39 para 0,48–0,57 ms, pelas formas a mais. Fica longe de 1 ms.
- **Montar a região:** +580 a +640 ms, porque há mais modelos distintos para malhar (+30 malhas, +10 MB).
- **Gerar:**
  - **A quente:** 23 ms (s31337) contra 7 ms (v1).
  - **Pior caso medido:** 125 ms a quente e 345 ms na primeira vez, na s1024, que recusa a primeira tentativa (uma
    conferência que falha percorre a grade toda).
- **Pelo menu:** `tools/menu/14-memoria.txt`, o mesmo roteiro; o mundo nasce v2 no novo binário e v1 no publicado.
  - **Região no menu:** 284 malhas e 176,1 MB, contra 269 e 169,0 MB.
  - **Gerar e conferir:** 312 contra 102 ms.
  - **Montar:** 2120 contra 1682 ms.
  - **Primeiro quadro do menu:** 3360 contra 3249 ms (ruído).
- **Bench do viewer** (as cenas não mudaram nesta rodada):

  | Cena | quadro (ms) | GPU sombra (ms) |
  |---|---|---|
  | vila do jogo | 9,40–9,63 | 1,33 |
  | vitrine A | 9,77 | 1,29 |
  | vitrine B | 9,07–9,14 | 1,25 |
  | bench denso | 12,6–12,7 | 2,36 |

  O bench denso é o pior caso demonstrativo, que nenhuma região gerada alcança.
- **Arquivo:** tabela completa em `round-12/desempenho/resumo.md`.

**Saves antigos** (`tools/qa/saves_antigos.sh`, binário publicado `4cbde86` × binário novo):

- **8 casos** com as linhas iguais, 0 `.invalido` e 0 exceção:
  - vila com missão aceita e com missão concluída;
  - região v1 com o marco aceso e com a missão concluída;
  - o mesmo personagem na vila e na região;
  - outro personagem na região e na vila.
- **Ao contrário:** o binário publicado, diante de um mundo v2, recusa com o texto dele e não muda nenhum arquivo
  (sha256 iguais; P83).

**Testes e validadores** (binário final; `round-12/testes.txt`, `validadores.txt`):

| O quê | Resultado |
|---|---|
| testes | **406, 0 falha**: engine 45, viewer 128, game 233 (eram 377 na `main`) |
| novos nesta rodada | `GeneratorVersionTest` 6, `CompositeCollisionTest` 8, `GeneratorV2Test` 9, `DiscoveryTest` 4, `RegionGoldenTest` +2 |
| validadores | `viewer --check` (129 animações), `interrupcao-fina`, `golpe-andando`, `esquiva` e `--check-cabelos`: ok. `--occlusion`: só a exceção documentada do arco |
| `javac -Xlint:all` | 0 aviso (o argumento chega ao compilador, conferido) |
| presets | 51 pares, 0 com diferença |
| referências canônicas | 22/22 equivalentes, desvio 0 |
| links | 0 quebrado |
| áudio | 47 arquivos, 0 alerta |
| clone limpo de `63fdcf4` | build ok, 406/0 |

## Respostas

1. **O despacho por versão funciona?**
   - **Sim:**
     - `RegionGenerator.compose(…, versão)` e `ProceduralScene.create(assets, semente, versão)`;
     - a versão vem do `mundo.json` e do save v2;
     - testes em `GeneratorVersionTest` (6).
   - **Na prática:**
     - a demonstração cria um mundo v2 pela interface (`mundo.json` com `versaoGerador: 2`);
     - o vídeo 02 abre um mundo v1 antigo no mesmo executável.
2. **A v1 continua idêntica?**
   - **Sim.**
     - **Referência:** `regioes-v1.txt` tem 0 linha de diferença para a `main`, e as 8 sementes batem
       (`RegionGoldenTest`).
     - **Saves:** os saves de região v1 feitos pelo binário publicado abrem com as mesmas linhas `regiao`/`missao` e a
       mesma posição.
3. **Como a v1 foi preservada?**
   - **Código:** o caminho da v1 não foi tocado (`composeV1` é o corpo da rodada 11). A v2 é outra classe, com outros
     rótulos de RNG.
   - **Relevo:** as contas novas (patamares, trilhas escavadas) só valem com `versão >= 2`.
   - **Dados:** os perfis de colisão dos props que a v1 planta saem byte a byte iguais (o script só reescreve os da
     biblioteca).
   - **Cache:** a impressão digital e o id do relevo levam a versão.
   - **Guarda:** a referência de 8 sementes nunca é reescrita (o escritor recusa a v1).
4. **Os mundos novos usam a v2?**
   - **Sim:**
     - `WorldStore.create` usa `LATEST = V2`;
     - na linha de comando, `-Dvx.gerador`, senão a versão do save, senão a v2.
5. **Os saves antigos continuam válidos?** Sim, nos 8 casos, com 0 `.invalido`.
   - **Save sem o campo:** um save sem `progresso.descobertas` abre (`DiscoveryTest`).
   - **Mundos e personagens:** os de antes aparecem e abrem (vídeo 02).
6. **E as versões desconhecidas?**
   - **No executável novo, um mundo de versão maior:**
     - não abre, com "Este mundo foi criado por uma versão mais nova do Valumo e não pode ser aberto aqui. Nada foi
       alterado.";
     - sem prévia gerada, sem `.invalido`, arquivo intacto (`GeneratorVersionTest`).
   - **No binário publicado, um mundo v2:** recusa e não muda nada (medido, P83).
7. **A colisão composta foi implementada?**
   - **Sim:**
     - perfil `composta`, `Collision.addComposite`;
     - formas medidas dos voxels e revisadas;
     - um contrato só para movimento, câmera, conferência de alcance, pontos e testes;
     - `CompositeCollisionTest` (8 testes × 5 rumos: 0°, 90°, 180°, 270° e 37°).
   - **Plantas:** `round-12/colisao/`.
8. **Os interiores são percorríveis?**
   - **Sim, em 5 rumos:**
     - a oficina: porta, galpão, forja, bigorna, bancada e piso;
     - a ruína de casa: arco, brecha, lareira e a frente dos escombros;
     - a barraca: o feirante atrás do balcão.
   - **No jogo:** o vídeo 01 entra na ruína pela colisão real.
   - **A torre arruinada NÃO:** sobram 0,5 u sob o arco, e o herói tem 0,52 u. Não foi liberada (P74).
9. **O P73 foi resolvido?**
   - **Sim:**
     - o spawn da ferreira foi para (1,94; 0,44), a 0,67 u da forja, 1,0 da bigorna e 1,27 da bancada;
     - o teste de 7 passos (entrar, forja, bigorna, bancada, falar com a ferreira, sair) passa em todos os rumos.
   - **Planta:** `colisao/planta-workshop_forge.png`.
10. **Quantos assets novos a v2 usa?**
    - **No perfil congelado** (54 entradas):
      - 38 entradas da biblioteca dos lotes 1 e 2, que somam 32 modelos-base;
      - 16 props aprovados da v1.
    - **Postos de fato** nas sementes 1–60: 36 entradas da biblioteca (31 modelos-base), mais os props da v1 de sempre.
    - **No perfil e nunca postos:**
      - `basket_group` puro (só as variantes saem nos conjuntos);
      - `table_bench` (nenhuma vaga o usa; está no perfil porque o perfil leva todo liberado).
11. **Quantos conjuntos de ofício?**
    - **Todos os 5**, sempre inteiros: ferreiro, herbalista, comerciante, caçador e guardião.
    - **Nas sementes 1–60** (número de regiões em que cada um aparece):

      | Conjunto | Regiões |
      |---|---|
      | ferreiro | 25 |
      | herbalista | 23 |
      | caçador | 17 |
      | comerciante | 16 |
      | guardião | 16 |
12. **O que continua restrito?**
    - **Só dentro de conjunto:** `basket_group` e as variantes dele.
    - **Não distribuídos:**
      - `ruin_tower` (P74) e `stone_circle` (refino artístico), em `aguardando_refinamento`;
      - `tree_tiered`, rejeitado.
    - **Teste:** `soAssetsLiberadosERestritosSoDentroDosConjuntos`.
13. **Os assentamentos ficaram visualmente variados?**
    - **Entre estilos:** a vila de caminho e a rural se leem como lugares diferentes (pranchas das 8 sementes;
      comparações v1 × v2).
    - **Limites:** P81 (as de caminho são compactas) e P78 (as 4 sementes de revisão saíram rurais).
    - **O julgamento artístico** é do proprietário, pelas capturas.
14. **Os caminhos estão acessíveis?**
    - **Sim:** toda região aceita passou na conferência de alcance da colisão real (grade de 0,25 u, degrau de
      0,30 u), com os checkpoints:
      - entradas a 0,6 u;
      - interiores a 1 u;
      - a descoberta.
    - **Sementes 1–60:** 60 aceitas, com 14 tentativas recusadas e refeitas.
15. **A identidade dos POIs é estável?** Sim: `poi.r0.ruina.0` e `desc.r0.ruina.0` vêm de rótulos, nunca de coordenada
    (teste `aRuinaEOLugarAExplorarComIdEstavel`).
16. **Quais as diferenças entre a v1 e a v2?**
    - **Na v2:**
      - assentamento com estilo, praça e conjuntos virados para ela;
      - a ruína com patamar e trilha;
      - a descoberta;
      - mata em misturas;
      - mais formas de colisão.
    - **Igual:**
      - o relevo-base;
      - a trilha principal;
      - o posto com a missão `posto_trilha` (o mesmo `composePost`);
      - o lago.
    - **Imagens:** `comparacoes/`.
17. **As descobertas foram implementadas?** Sim, uma, de um tipo só: a lareira da ruína, em toda região v2 (P77).
18. **A recompensa persiste?**
    - **Sim:** uma vez por personagem × mundo (`DiscoveryTest`).
    - **Na demonstração:** salvar, voltar ao menu, continuar → 5 moedas, a descoberta no diário e a lista com
      "1 lugar descoberto". Está no save: `progresso.descobertas: ["desc.r0.ruina.0"]`.
19. **E o desempenho?**
    - **Quadro:** a v2 rural fica abaixo da v1 e a de caminho 0,4 ms acima (ruído). A sombra não piora.
    - **Custos:**
      - **Lógica:** +0,1 a +0,2 ms.
      - **Montagem da região:** +0,6 s.
      - **Geração:** até 345 ms na primeira vez, contra 144 da v1, e 23–125 ms a quente.
      - **Memória da região:** +15 malhas e +7 MB.
    - **Detalhe:** em "Números".
20. **Os testes passaram?**
    - **Sim:** 406, com 0 falha e 0 erro.
    - **Validadores:** todos ok; `-Xlint:all` com 0 aviso.
    - **Testes que pegaram defeitos de verdade nesta rodada:** a copa da ruína e a árvore retorcida que nunca saía.
21. **As referências foram preservadas?** Sim: 22/22 equivalentes, com desvio 0 em todas.
22. **Os presets foram preservados?** Sim: o gate deu 51 pares, 0 com diferença.
23. **E o clone limpo?**
    - **Resultado:** o clone de `63fdcf4` (o último commit de código) gera os assets, compila e passa 406/0 em 1 min 44 s.
    - **Ambiente:** PATH sem a `.venv`, Python com numpy por `-Pvx.python`.
24. **Quais problemas restam?** P74–P84 (`problemas-conhecidos.md`). Os de maior peso:
    - **P74:** a porta da torre;
    - **P76:** ofícios sem morador;
    - **P81:** vilas de caminho compactas;
    - **P80:** o teste físico.
25. **Está pronto para a avaliação física?**
    - **Sim**, pelo `round-12/roteiro-de-teste-fisico.md` (cerca de 20 min).
    - **Validado só por entrada injetada:** roteiros, testes e capturas. Nada substitui o teste físico.
26. **O que depende do proprietário?**
    - **Aprovar a composição da v2.** Enquanto não for publicada, ela ainda pode mudar. Publicada, congela, e mudança
      vira v3.
    - **Decisões de arte:**
      - alargar o arco da torre (P74);
      - a laje dos interiores (P75);
      - subir a copa da árvore jovem da ruína, se a câmera incomodar (P82).
    - **NPCs de ofício** para os conjuntos (P76).
    - **O tamanho da recompensa** (5 moedas).
    - **A densidade das vilas de caminho** (P81).
    - **Publicar:** push, PR e tag. Só com pedido.
27. **O que se recomenda para a Rodada 13?** "Gente nos ofícios", **antes** de publicar a v2. Assim tudo entra na mesma
    versão, sem gastar uma v3:
    1. os NPCs de ofício (ferreira, feirante, herbalista, caçador), nos spawns que já são alcançáveis;
    2. a torre com o arco alargado, liberada pelo teste;
    3. de 2 a 3 tipos novos de descoberta (mirante, árvore retorcida, marco antigo);
    4. o patamar maior nas vilas de caminho.

    Depois, publicar a v2 congelada.
