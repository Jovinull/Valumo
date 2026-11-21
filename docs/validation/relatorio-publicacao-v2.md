# Publicação da v2 — fechamento das Rodadas 12, 12.1, 12.2 e 12.3

**Tag:** `procedural-world-v0.6`.

**O que foi publicado:** a primeira fundação procedural rica do Valumo, aprovada pelo proprietário.

| Peça | O que é |
|---|---|
| entrada | menu de entrada |
| personagens | personalizáveis |
| mundos | procedurais, com os geradores v1 e v2 versionados e congelados |
| ação | combate, esquiva, salto |
| vilas | NPCs com os cinco ofícios |
| exploração | quatro tipos de ruína, descobertas e recompensas |
| progresso | missões e persistência |
| câmera | contextual na torre caída |
| conteúdo | biblioteca de assets |

## 1. Aprovação do proprietário

O proprietário fez o **teste físico completo** e aprovou o resultado, sem pedido de correção. Ficaram satisfatórios:

- a jogabilidade, a movimentação e o combate;
- os NPCs e as ruínas;
- a câmera e as descobertas;
- a personalização;
- os fluxos de salvamento.

Com isso fecham:

- **P89, P94 e P99:** os testes físicos pendentes.
- **P95, P96 e P97:** as limitações de câmera, **aceitas no checkpoint** com a severidade real (baixa). Não houve
  reclamação no teste, e nenhuma rodada nova foi aberta.

## 2. Congelamento formal do gerador v2

| | Antes | Depois |
|---|---|---|
| `RegionGenerator.published(v)` | só a v1 | **v1 e v2** |
| `RegionGenerator.LATEST` | v2 | v2 (sem mudança) |
| `RegionGenerator.hadTestBuilds(v)` | — | só a v2 (as versões de testes das rodadas 12 a 12.3) |

- **Commit do congelamento:** `f475421e62665d1f4a88567936851617b62edbac`.
- **O que não mudou:**
  - nenhum algoritmo, peso, RNG, POI, morador, colisão ou geometria;
  - `regioes-v1.txt` e `regioes-v2.txt` (`RegionGoldenTest` verde, sem refazer referência).
- **A frase dos mundos de testes:**
  - **Mundos v2 das versões de testes** (como o "RUINAS" do proprietário): um save deles que não bate com a região de agora
    continua recebendo "Esta aventura é de uma versão de testes deste mundo…", sem `.invalido` e sem tocar no arquivo.
    Por isso existe `hadTestBuilds`: sem ele, publicar a v2 trocaria essa frase por "não corresponde a este mundo".
  - **A v1:** continua "não corresponde".
- **Testes novos ou atualizados:**
  - `GeneratorVersionTest.asVersoes1E2SaoPublicadasESuportadas` — as duas suportadas e publicadas, cada uma com a
    referência das 8 sementes; nenhuma versão desconhecida suportada; os mundos novos na v2;
  - `SaveCompatTest` — a frase das versões de testes com a v2 publicada; a v1 sem versões de testes.
- **Do que a v2 depende para continuar reproduzível** (código, perfil congelado, colisão, caixas dos modelos, dados de
  cena, referências): [mundo procedural](../architecture/mundo-procedural.md) §13. Mudar qualquer item é a **v3**
  ([ADR 0029](../decisions/0029-versoes-do-gerador.md) §Publicação da v2,
  [guia](../processo/como-evoluir-o-gerador.md)).
- **Um ajuste de texto, sem efeito de jogo:** a entrada de desenvolvimento sem menu (`-Dvx.menu=nao`, só dos roteiros)
  deixou de chamar o modo procedural de "experimental".

## 3. Compatibilidade

| Conferência | Resultado |
|---|---|
| v1, sementes 1–300 (arranjo, props, moradores, POIs, inimigos, vínculo) | **iguais** à base |
| v2, sementes 1–200 | **iguais em dois processos** e **iguais às da rodada 12.3** (com os moradores); 0 recusa |
| tentativas da v2 | 172 / 23 / 4 / 1 |
| referências das 8 sementes de cada versão | intactas |
| 8 saves da `main` publicada (`4cbde86`): v1 ativa e concluída, v2 ativa (marco aceso) e concluída, 4 combinações do menu | **8 de 8 iguais**, 0 `.invalido`, 0 exceção |
| os mesmos 8, feitos pelo binário da 12.3 (regiões **v2**: `versaoGerador 2`) | **8 de 8 iguais**, 0 `.invalido`, 0 exceção |
| descobertas que persistem, recompensa única, missões, personagens e mundos independentes | `DiscoveryTest`, `QuestTest`, `SaveGameTest`, `SaveCompatTest`, `MenuStoresTest`, `MenuAdventureTest` verdes |
| mundos experimentais "RUINAS" e "31337" | **não abertos nem migrados**; arquivos conferidos por sha256 antes e depois; a cópia `.invalido` histórica continua lá |

## 4. QA

| Conferência | Resultado |
|---|---|
| suíte | **457 testes**, 0 falhas, 1 pulado (o escritor da referência, de propósito) — a base da 12.3 (456) mais o teste do congelamento |
| colisão composta, gerador, saves, câmera | `CompositeCollisionTest` 11, `GeneratorV2Test` 10, `GeneratorVersionTest` 7, `SaveCompatTest` 7, `SaveGameTest` 7, `TowerCameraTest` 6, `CameraModesTest`, `RegionGoldenTest` |
| animações e catálogo | `AnimationQaTest` 9, `ConteudoMundoTest` 15, `AcapelaTest` 11 |
| `javac -Xlint:all` | **0 avisos** |
| armas (`--check`) | 129 animações, todas ok |
| interrupção fina, golpe andando, esquiva R2 | seguros |
| arma × cabelo | nenhum estudo pior que a referência |
| oclusão | só a exceção documentada do arco |
| gate dos presets | **51 pares, 0 pixel** |
| referências canônicas | **22, equivalentes** |
| áudio, links | 47 arquivos sem alerta; 0 link quebrado |
| clone limpo | §6 do relatório final do pacote |

**Achado e corrigido nesta publicação:** um aviso `dangling-doc-comments` do `-Xlint`, que tinha entrado na 12.3 (dois
comentários de documentação seguidos na zona de recorte). Juntá-los não muda nenhum comportamento.

## 5. Git

- **Histórico:** de `4cbde86` (a `main` de referência) até o fim desta publicação, todos os commits têm autor e
  committer noreply e mensagens só com título.
  - **O que não há:** trailer, arquivo de `output/`, asset gerado, arquivo acima de 2 MB ou dado pessoal.
  - **Os saves do proprietário** não estão no git.
- **Os 34 commits do artista** (PR #8) entraram pelos dois merges (`b7e0347`, `d2a7134`), com os SHAs originais.
- **A publicação:**
  - **Merge:** o PR #9 entra na `main` por **fast-forward**, sem squash, sem force e sem reescrita. A `main` passa a
    conter os mesmos SHAs do ramo.
  - **Tag:** `procedural-world-v0.6`, anotada, aponta para o commit publicado.
  - **Release:** nenhuma criada.
- **O PR #8:** é fechado com um comentário que aponta o PR #9, sem um segundo merge. O ramo do artista não é tocado.
- **As provas da publicação** (SHA final da `main`, CI da `main`, tag, PRs): relatório final em
  `output/visual-review/publicacao-v0.6/`.

## 6. Problemas conhecidos no checkpoint

| Problema | Situação |
|---|---|
| P95 — a câmera encosta no fim da saída de costas da torre | aceito, baixa |
| P96 — anel pontilhado do recorte, visível parado | aceito, baixa |
| P97 — câmera curta dentro da abóbada | aceito, baixa; recorte com limite de altura recomendado para o futuro |
| P98 — o Acapela sem papel no mundo | escopo; decisão futura do proprietário |
| P91, P92 | decisão registrada; miniaturas antigas não regeneradas |

A lista completa e os históricos estão em [problemas conhecidos](problemas-conhecidos.md).
