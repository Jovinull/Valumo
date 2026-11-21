---
name: mundo-procedural
description: Gerar, alterar e validar o mundo procedural do Valumo (rodada 11 em diante) — semente e RNG derivado por rótulo (vx.game.world.Rng), relevo como dado (TerrainField, World.LEGACY intocado), composição da região com componentes autorais (RegionGenerator), conferência espacial na colisão real com tentativas limitadas (Reachability), pontos de interesse com ids estáveis, missão como modelo + instância a partir de fatos (ProceduralScene), save v2 com vínculos, reprodução por semente, capturas reais e desempenho de geração (tools/mundo, tools/review11.sh). Use ao mexer em geração de região, POI, encontro gerado, missão vinculada ao mundo, save do mundo procedural ou ao validar sementes.
---

# Mundo procedural (rodada 11 em diante)

Fonte canônica: `docs/architecture/mundo-procedural.md` e os contratos ligados de lá (sementes, regiões, POIs,
instâncias de missão, persistência). Decisões: ADRs 0025 e 0026. Guias: `docs/processo/reproduzir-sementes.md`,
`docs/processo/como-adicionar-um-tipo-de-poi.md`.

## Regras

1. **Semente → mesmo mundo.**
   - Toda decisão usa `Rng.of(semente, regiao, categoria, …)`: nunca `java.util.Random`, relógio, ordem de
     `HashMap`/`HashSet` nem ordem de carga.
   - Uma categoria nova tem rótulo próprio, para não mover o resto.
   - Mudou o resultado de alguma semente → é uma versão NOVA do gerador (rodada 12, ADR 0029 implementada):
     - **Uma classe por versão:** como `RegionV2`, com rótulos próprios (`Rng.of(semente, "v2", …)`); o caminho de uma
       versão publicada nunca muda (a v1 é o código da rodada 11, intocado).
     - **Referência por versão:** `RegionGoldenTest` × `regioes-v1.txt` e `regioes-v2.txt`; **nunca** atualize para
       passar.
     - **A versão é do mundo** (`mundo.json`, save v2): `ProceduralScene.create(assets, semente, versão)`.
       - Mundos novos nascem na `RegionGenerator.LATEST`.
       - Versão desconhecida não abre e não toca no arquivo (sem `.invalido`).
   - A v2 lê o perfil CONGELADO `assets/scenes/regiao_v2_e.json` (`RegionProfile`), nunca o catálogo gerado. Guia
     completo: `docs/processo/como-evoluir-o-gerador.md`.
2. **O mundo fixo não muda.**
   - `World.LEGACY` são as funções de antes; refatorar o relevo exige as referências 22/22 com desvio 0 e o gate 51/0.
3. **A autoria está nos componentes.** Só props e NPCs aprovados da E. Asset novo só com aprovação e pelo pipeline
   (`tools/env_e.py`, ou a biblioteca `tools/conteudo_*.py` com `aprovacao.integracao = liberado`). Os assets com
   interior (`depende_colisao_composta`) esperam a extensão do `Collision` (`docs/architecture/colisao-composta.md`).
4. **Só vale o que passa na colisão real** (`Reachability.check`: a mesma `Collision` do jogo, o T1, o raio e o degrau do
   herói).
   - Reprovou: outra tentativa (máximo `MAX_ATTEMPTS`) ou recusa com motivo.
   - Nunca aumente tolerância nem aceite "caso raro".
5. **Ids estáveis derivados de ids, nunca de coordenadas:** `poi.r0.posto_trilha.0`, `obj.r0.posto_0.marco`,
   `r0.posto_0.fungo.1`, `quest.instance.s1024.r0.posto_trilha_0.1`.
6. **Missão = modelo + fatos.** Sem fatos, sem instância. O texto corresponde aos fatos: rumo real, ninguém afirma ver o
   que não vê, parâmetro sem valor é erro.
7. **Save v2** guarda o mundo e os vínculos, e recusa qualquer diferença. O v1 (mundo fixo) é intocável. QA sempre com
   `-Dvx.savegame=PASTA_TEMP`.
8. **Nada pesado no laço de desenho.** A geração é dado puro, feita antes do primeiro quadro; as malhas são montadas na
   thread do GL.

## Procedimento

```bash
./gradlew :game:test --tests 'vx.game.ProceduralWorldTest'          # reprodutibilidade, diferença, ids, alcance, missão, save v2
./gradlew jogo -Dvx.mundo=procedural -Dvx.semente=1024              # jogar
tools/review11.sh vistas        # 8 vistas reais × sementes + comparações lado a lado
tools/review11.sh videos        # aventura na região com entrada real + continuar (som da aplicação)
tools/review11.sh desempenho    # vila fixa × regiões, mesmo binário; medir-geracao
tools/review11.sh regressao     # referências 22/22 e a aventura da rodada 10 no binário novo
```

Olhe as capturas com olho crítico:
- repetição;
- relevo artificial;
- trilha serrilhada;
- props flutuando ou enterrados;
- árvore na trilha;
- NPC em lugar impróprio;
- objetivo escondido pelo herói;
- escala.

Se ficou abaixo da vila aprovada, diga e registre o que precisa melhorar.

## Defeitos já vistos (não repita)

| Defeito | Causa | Correção |
|---|---|---|
| Árvores "dentro da água" em todas as sementes | o ruído natural afundava abaixo do nível da água longe do lago | vales rasos: `natural()` comprime o negativo |
| Moradores colados em poço, lanterna ou barril | vagas fixas sem olhar os props do arranjo | `clearOfProps` + `clearOfNpcs` na escolha da vaga |
| Estalagem em desnível | patamar curto para a pegada da casa | patamar pleno até `plateauR + 1` |
| Vista geral virou close | a câmera de enquadramento encurta por obstáculo | `FollowCamera.technicalView` (só nos roteiros) |
| Todo salvamento automático recusado | vínculo com coordenada `float` ≠ a mesma relida como `double` | `double` arredondado no JSON |
| Teste do muro "não bloqueou" | o muro caiu dentro do círculo do assentamento (o herói dá a volta de verdade) | ponha o obstáculo fora das áreas abertas; a validação estava certa |

## Rodada 12 — a v2 e a descoberta (`mundo-procedural.md` §9)

- **Composição:** estilo (caminho/rural) → patamar → estalagem ao fundo → chafariz ou poço (ocupam lugar) → 3–4
  construções ou conjuntos por peso, encaixados PEÇA A PEÇA → saída da trilha → caminhos às portas → moradores →
  ruína com patamar e trilha secundária → posto (`composePost`) → mata.
- **Conjuntos:** sempre inteiros (âncora + itens no referencial dela); restritos só dentro deles.
- **Checkpoints:** cada entrada, interior e descoberta vira um `checkpoint` da `Reachability`. A v1 não tem nenhum.
- **Dados de distribuição:** só as variáveis `RegionV2.POOL_*` e o perfil congelado.
- **Medir:** `tools/review12.sh vistas comparacoes` (capturas) e `GeneratorV2Test` (determinismo, restrições,
  densidade).
- **Defeitos já vistos (não repita):**
  - conferir o encaixe pela caixa INTEIRA de um conjunto (assimétrica) → nada cabia;
  - conferir as peças do mesmo conjunto entre si → a placa junto da porta reprovava tudo;
  - a área livre da frente do catálogo inteira numa vila compacta → nada cabia;
  - o chafariz e o poço fora do `taken` → conjunto encostado neles;
  - a Kelyra virada para o chafariz → a fala caía dentro dele.

## Rodada 12.1 — acabamento da v2 (`mundo-procedural.md` §10)

- **A v2 está PUBLICADA e congelada** (tag `procedural-world-v0.6`), como a v1.
  - **O que é a v3:** mudar o resultado de qualquer semente (peso, rótulo, asset, forma, ponto, vaga, texto de
    descoberta), com classe nova, rótulos novos, `regioes-v3.txt` e o escritor da referência. Guia:
    `docs/processo/como-evoluir-o-gerador.md`.
  - **Nunca refaça** `regioes-v1.txt` nem `regioes-v2.txt`.
  - **O que a v2 congela:** `mundo-procedural.md` §13.
- **A impressão digital inclui os textos** da cena (as descobertas com os fatos). Mudar um texto de
  `descobertas_e.json` muda a referência da v2.
- **Variedade sem trocar tudo:** POIs compatíveis sorteados com rótulo próprio (`"ruina-tipo"`: casa ou torre). Porte da
  vila de caminho com `"porte"`; moradores com `"oficios"`.
- **Relevo plano por projeto:** patamar, patamares das ruínas e clareira do posto (`ProceduralField.flat`). Sem isso, a
  primeira fiada de pedra fica no plano da grama e pisca.
- **Morador de ofício:** pelo dado (`habitantes_e.json → oficio` → perfil `oficios`), num spawn `npc` da âncora; corpo
  livre, sem bloquear porta nem ponto, `falar:<id>` na conferência.
- **Saves:** um save v2 de uma versão em desenvolvimento cuja região mudou pode ser recusado (id que não existe mais).
  Teste com o binário anterior (`tools/review121.sh` e o roteiro de compatibilidade no relatório), nunca com os saves
  do proprietário.
- **Defeitos já vistos (não repita):**
  - medir a média de construções com o computador gravando vídeo (os tempos saem ~20% maiores; as contagens não mudam);
  - perfil de região escrito à mão em vez de pelo script (`perfil_regiao_v2.py`).

## Rodada 12.2 — quatro ruínas, moradores e saves de versão de testes (`mundo-procedural.md` §11)

- **Ruína:** uma por região, entre quatro (`RuinKind`). Ordem sorteada com peso (`"ruina-tipos"`); cada tipo só entra onde
  o terreno faz sentido (abóbada: sobe ≥ `VAULT_RISE` para os fundos; sítio: varia ≤ `FARM_FLAT` na pegada). Quem não cabe
  cede a vez.
- **Morador de ofício:** o ponto vem do perfil (`conjuntos.*.trabalhador`), nunca do nome. `RegionGenerator.RESIDENTS` é
  da v1 e NUNCA muda: morador só de ofício entra pelo perfil.
- **Campo novo na cena:** só para o que a v1 não tem (`y`, `gesto`, `oficio` dos moradores de ofício). Senão a cena da v1
  muda e a referência dela quebra.
- **Saves:** o save que não bate com a região é recusado sem `.invalido` (`SaveGame.Incompatible`). Confira com CÓPIA
  dos dados do proprietário, nunca com os originais.
  - **Versão não publicada, ou com versões de testes** (`RegionGenerator.hadTestBuilds`, a v2): "de uma versão de
    testes".
  - **A v1:** "não corresponde a este mundo".
- **Ao publicar uma versão nova:**
  1. ponha a versão em `RegionGenerator.published`;
  2. faça `hadTestBuilds` dizer se ela teve builds de testes;
  3. liste o que ela congela (como o §13 da v2);
  4. atualize `GeneratorVersionTest.asVersoes1E2SaoPublicadasESuportadas`.
- **Defeito já visto (não repita):** a conferência compara alturas do MUNDO; ponto relativo ao chão precisa somar o relevo
  (o primeiro filtro da Brásia na laje ignorava o patamar).
