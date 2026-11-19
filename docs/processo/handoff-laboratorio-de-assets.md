# Handoff: integrar os assets do laboratório (`valumo-content-lab`) ao mundo procedural

Para a etapa depois do fechamento das rodadas 11 e 11.1. O laboratório é um clone independente com outro agente.
**Nada dele entra por merge cego.** Este documento diz o que pode vir direto, o que o agente principal adapta e como
validar.

## 1. O que existe hoje (rodada 11)

| Camada | Onde | Quem altera |
|---|---|---|
| Modelos voxel (props, árvores, casas) | `tools/env_e.py` (+ `tools/envlib.py`, `tools/voxlib.py`) → `assets/environment/e/*.vox` (GERADO, fora do git) | laboratório propõe; o principal revisa |
| Colisão por tipo de prop | `assets/scenes/colisao_e.json` (`props.<nome>`: caixa / círculo / tronco / nenhum, `camera`, `altura`, `pisavel`) | o principal (afeta jogabilidade) |
| Composição da região | `game/.../world/RegionGenerator.java`: arranjos do assentamento (`Item("house", …)`), posto, mata (`case 0 -> {"tree_oak", …}`) | **só o principal** |
| Conferência espacial | `Reachability.java` (colisão REAL, até 4 tentativas, recusa com motivo) | só o principal |
| Moradores e missões | `assets/scenes/habitantes_e.json`, `modelos_missao_e.json` | o principal |
| Vila fixa (legado) | `assets/scenes/jogo_vila_e.json`, `World.LEGACY` | **não muda** (referências 22/22) |

## 2. O que pode entrar direto, e o que não

- **Direto, depois da revisão:**
  - funções novas de modelo em `tools/env_e.py` (ou um módulo novo de primeiro nível em `tools/`, declarado no
    `build.gradle.kts` se a geração passar a lê-lo);
  - paletas na faixa da E;
  - prévias (`tools/vox_preview.py`).
- **Adaptado pelo principal:** a entrada no gerador (pesos, vagas, raios de exclusão), o perfil de colisão, os pontos de
  interesse novos e qualquer coisa com id persistido.
- **Nunca do laboratório:**
  - alteração de `World.LEGACY`, de `jogo_vila_e.json` ou das referências canônicas;
  - mudança de `RegionGenerator.VERSION`;
  - mudança de assets gerados à mão;
  - arquivos de `output/`;
  - a pasta do laboratório dentro deste repositório.

## 3. Metadados do laboratório → o que a rodada 11 precisa

Para cada asset novo, o laboratório entrega a ficha:

| Campo | Para quê | Vira |
|---|---|---|
| `nome` estável (`ruin_arch`, `tree_ash`) | id do prop | chave em `colisao_e.json` e nos `Item`/listas do gerador |
| pegada (x × z, em u) e altura | exclusão e colisão | raio em `taken` (como `house` 4,2 / outros 1,4) e o perfil `caixa`/`circulo`/`tronco` |
| tipo de lugar (assentamento, borda de mata, clareira, beira d'água, encosta) | onde pode nascer | o ramo do gerador que o planta |
| frequência sugerida | variedade | peso na lista (repetição = peso, como `tree_oak` duas vezes) |
| pisável? bloqueia a câmera? | jogabilidade | `pisavel`, `camera` |
| POI? (ruína com marco, santuário) | missão | `RegionLayout.PointOfInterest` com id estável + fatos para `modelos_missao_e.json` |

## 4. Como validar (antes de qualquer merge)

1. **Asset:**
   - `python3 tools/asset_audit.py assets/environment/e` sem voxel solto nem fragmento;
   - prévia na clareira canônica (`tools/snap.sh`) ao lado dos props aprovados (escala e paleta).
2. **Colisão:** perfil em `colisao_e.json`. `GameplayTest` e `ProceduralWorldTest` verdes. O herói não entra no prop e a
   câmera não o atravessa (roteiro `tools/mundo/01-vistas.txt`).
3. **Alcance:** a semente passa no `Reachability` com o asset novo (início → doador → posto → volta, falas de frente). Uma
   semente que passou a ser recusada é regressão, a menos que a recusa seja justificada e registrada.
4. **Distribuição por semente:**
   - `tools/review11.sh vistas comparacoes` nas sementes 1024, 2048, 4096 e 77, mais as de teste (1, 2, 3, 555, 9001,
     31337);
   - o asset aparece onde deve, sem amontoar, e a variedade sobe (P53).
5. **Determinismo:** a mesma semente dá a mesma região duas vezes (impressões digitais do `ProceduralWorldTest`).
6. **Desempenho:** `tools/review11.sh desempenho` antes × depois no mesmo binário (vila fixa × regiões). Olhe as malhas
   vivas e o tempo de montagem (~1,7 s hoje). Asset que dobra as faces exige justificativa.

## 5. Saves e ids

- **Mudou o resultado de uma semente** (asset novo no sorteio, peso novo): suba `RegionGenerator.VERSION`.
  - Os saves v2 antigos daquela semente passam a ser recusados com o motivo (vínculos conferidos). Os mundos do catálogo
    com outra versão do gerador são recusados ao entrar, com a explicação. É o comportamento certo: não reposicionar
    missão em silêncio.
  - Planeje a troca com o proprietário: os mundos atuais deixam de abrir. Se isso não for aceitável, os assets novos
    entram só em sementes/regiões novas, com outro rótulo de RNG.
- **Ids de POI e de spawn:** estáveis e derivados do rótulo, nunca da ordem de inserção. Asset novo **não** muda os ids
  dos existentes.
- **Prévias:** mudou a região → a chave da prévia muda junto (versão do gerador); `PREVIEW_VERSION` só sobe se o
  ENQUADRAMENTO mudar.

## 6. Revisão de Pull Request do laboratório

1. Ler o diff inteiro. Recusar arquivos fora de `tools/` (e da documentação do próprio asset) sem conversa.
2. Gerar num clone limpo: `python3 tools/build_assets.py` e `./gradlew test :viewer:installDist`.
3. Rodar o §4 e anexar as capturas ao PR.
4. Varredura de originalidade (nada de terceiros, nada de IA geradora de imagem) e de vínculos com pessoas reais.
5. Integrar em commits pequenos, por assunto (modelo; colisão; gerador; documentação). Nunca squash cego do ramo do
   laboratório.
