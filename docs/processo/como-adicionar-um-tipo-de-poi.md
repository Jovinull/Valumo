# Como adicionar um tipo de ponto de interesse

Exemplo hipotético: `ruin` (uma ruína com algo a examinar). Contratos: [POIs](../architecture/contrato-pontos-de-interesse.md),
[regiões](../architecture/contrato-regioes.md).

1. **Componentes primeiro (arte):** a ruína é um prop (ou alguns) da direção E, gerado em `tools/env_e.py`, com fonte
   editável. Só com aprovação do proprietário (assets novos).
   - Defina a pegada, as entradas, a área livre de circulação e o ponto de interação.
   - Defina o perfil de colisão em `colisao_e.json`.
2. **Onde ele pode existir:** em `RegionGenerator`, uma função `composeRuin(L)` com um rótulo de RNG próprio
   (`Rng.of(semente, regiao, "ruina")`). Assim os outros sorteios não mudam.
   - Escolha o lugar por regras: distância de outros lugares e da água, inclinação, alcance da trilha. Se precisar, abra
     um ramal da trilha.
   - Registre `PointOfInterest("poi.<regiao>.ruina.<n>", "ruin", …, estado, features, spawns)`, com ids estáveis
     derivados do id do POI.
   - Ponha o lugar nos `limits` (área jogável) e em `nearPlaces`, para a vegetação se afastar.
3. **Conferência:** o que o lugar exige entra em `Reachability.check`: alcance de frente do objeto, espaço de luta se
   houver inimigos. Composição que reprova pede outra tentativa; nunca aumente tolerância para passar.
4. **Fatos e missões:** um modelo em `modelos_missao_e.json` que peça `"poi": "ruin"` e o estado; as falas em vozes
   autorais com parâmetros; e, se for preciso, um tipo de objetivo novo (evento novo em `QuestEvent`).
5. **Suba a versão do gerador:** isso muda o resultado de sementes existentes. Saves de outra versão são recusados.
6. **Testes:** em `ProceduralWorldTest`:
   - o tipo aparece quando deve e não aparece quando não pode;
   - os ids são únicos e estáveis;
   - o lugar é alcançável nas sementes de teste;
   - mudar a vegetação não move o lugar.
7. **Evidência:** `tools/review11.sh vistas` (uma vista nova em `tools/mundo/01-vistas.txt`), comparações entre sementes
   e desempenho de geração.
