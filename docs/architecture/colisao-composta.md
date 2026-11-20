# Colisão composta: contrato e proposta (não implementada)

**Status:** proposta da integração dos lotes 1 e 2. O `Collision` continua com **uma forma por prop**. Os assets que
dependem desta extensão estão na biblioteca, mas não podem entrar no mundo procedural. Eles estão marcados no catálogo
com `aprovacao.integracao = depende_colisao_composta`.

## 1. Como é hoje

O `Collision.addProp(perfil, posicionamento)` cria uma forma por prop, a partir do perfil do nome em
`assets/scenes/colisao_e.json`.

- **Tipos de forma:**
  - `caixa`: caixa orientada com o tamanho da caixa da malha, vezes `escala`;
  - `circulo`: com `raio`;
  - `tronco`: um círculo para o corpo, mais uma copa só para a câmera;
  - `nenhum`.
- **Centro e altura:** o centro é o centro da caixa da malha, transformado pela matriz do prop. A altura vai de
  `y0` = base até `y0 + altura × h`.
- **Bandeiras:**
  - `camera`: a forma bloqueia a linha de visão; padrão sim;
  - `pisavel`: o topo vira chão para quem pula; padrão não.
- **Corpo × câmera:** a `Shape` separa `body` (empurra o herói) de `camera` (encurta o braço da câmera). A copa da árvore
  já é `body = false, camera = true`, a partir de `copaDe × h`.
- **Quem usa:**
  - **`Player`:** empurra o círculo do herói (`RADIUS` 0,26 u) para fora das formas `body` e sobe em formas `pisavel` de
    até `STEP` 0,30 u;
  - **`FollowCamera`:** testa a linha de visão contra as formas `camera`;
  - **`Reachability` (rodada 11):** usa ESTA colisão, numa grade de 0,25 u, para conferir se a região é percorrível.

Com uma forma só, um galpão aberto vira um bloco: a forja, a bigorna e a ferreira ficam dentro do sólido. Uma ruína com
interior vira um bloco, ou fica atravessável (`nenhum`).

## 2. Quem precisa

| Asset | Estado | Por quê | Fonte das formas |
|---|---|---|---|
| `workshop_forge` | depende_colisao_composta | galpão aberto: forja, bigorna, tina e ferreira dentro | `medidas.formas_medidas_u` (14 retângulos) |
| `ruin_tower` | depende_colisao_composta | anel de paredes com porta; o interior é caminhável | `formas_medidas_u` (17) |
| `ruin_house` | depende_colisao_composta | dois cantos de parede + chaminé; o interior é caminhável | `formas_medidas_u` (12) |
| `market_stall` | depende_colisao_composta | o feirante fica atrás do balcão, sob o toldo | `formas_medidas_u` (5) |
| `stone_circle` | aguardando_refinamento | 7 pedras = 7 círculos; o centro é livre | `pontos.formas_u` (7 círculos) |
| conjunto `ferreiro` | depende (da âncora) | a âncora é a `workshop_forge` | — |
| conjunto `comerciante` | depende (de um item) | a âncora é a `house_tall` (liberada); o item `market_stall` depende | — |

Os outros 28 liberados servem com o perfil de uma forma (a ficha `colisao` do catálogo é o perfil pronto para colar).

## 3. Contrato do perfil composto

Exemplo de formato (os números são ilustrativos; os de verdade saem das medidas):

```json
"workshop_forge": {
  "tipo": "composta",
  "camera": true,
  "formas": [
    {"tipo": "caixa",   "min": [-2.50, -1.75], "max": [-2.25, 2.25], "altura": [0.0, 3.0]},
    {"tipo": "caixa",   "min": [ 0.50, -0.25], "max": [ 1.10, 0.35], "altura": [0.0, 0.75], "camera": false, "pisavel": true},
    {"tipo": "circulo", "centro": [2.6, 1.4], "raio": 0.35, "altura": [0.0, 0.9], "camera": false},
    {"tipo": "caixa",   "min": [-2.50, -1.75], "max": [3.375, 2.25], "altura": [3.0, 5.9], "corpo": false}
  ]
}
```

- **Espaço:** as coordenadas estão em u, no espaço do MODELO, relativas à origem do modelo (a mesma dos `pontos` do
  catálogo), e não ao centro da caixa.
  - Cada forma passa pela matriz do posicionamento: o centro por `transformPosition` e o rumo pela matriz.
  - Se a matriz tiver escala, as meias-extensões e o raio multiplicam pelo fator.
- **Altura:** `altura: [a, b]` em u, acima da base do prop. O padrão é a altura inteira da malha.
- **Corpo e câmera:**
  - `corpo` (padrão sim): a forma empurra o herói;
  - `camera` (padrão = a do perfil): a forma bloqueia a linha de visão.
  - Um telhado é `corpo: false` a partir da altura das paredes, como a copa. Uma peça baixa (bigorna, tina) é
    `camera: false`, para a câmera não pular dentro do galpão.
- **Pisável:** `pisavel` (padrão não) vale por forma: o topo de uma mureta baixa da ruína pode ser pisável, a parede não.
- **Fonte da verdade:** o perfil composto é escrito em `colisao_e.json`, à mão e revisado, como os outros perfis. As
  formas partem das medidas do catálogo.
  - **Teste:** um teste confere que o perfil cobre as colunas sólidas medidas. Toda célula de `formas_medidas_u` acima do
    degrau precisa estar dentro de alguma forma `corpo`, sem tolerância nova.
  - **Por que não ler o catálogo direto:** o `Collision` não lê o catálogo (gerado). Assim, melhorar um modelo nunca
    muda a colisão em silêncio.

## 4. O que precisa ser verdade (testes da implementação futura)

Para cada asset composto, prop sozinho na origem, com rumos 0°, 90°, 180°, 270° e um ângulo qualquer (37°):

1. **Paredes fecham:** nenhuma célula sólida medida (§3) fica fora das formas `corpo`.
2. **Interior alcançável:** pelo `Reachability` (a colisão real), toda entrada de `pontos.entradas` leva às células
   livres do interior, e a área segura `area_segura_u` é percorrível.
3. **Passagens com folga:** a menor passagem (porta da torre, vão do galpão) tem pelo menos 2 × `RADIUS` + 0,25 u
   (uma célula da grade), medida, não suposta.
4. **Interação de frente:** cada `pontos.interacao[]` fica fora das formas `corpo` (com o raio do herói), e o herói chega
   até ele virado para o `rumo` (a mesma regra das falas na rodada 11).
   - **Com os NPCs dos `spawns` no lugar** (corpo 0,3 u, `colisao_e.json → corpos`).
   - **Achado na integração:** na oficina, `bigorna` fica a 0,125 u do spawn `ferreira` e `bancada` a 0,28 u. Com a
     ferreira ali, o herói (0,26 u) não chega a nenhum dos dois. É preciso afastar os pontos ou o spawn ANTES de liberar
     a oficina.
5. **Câmera:** o roteiro de vistas (`tools/mundo/01-vistas.txt`), adaptado à vitrine, não atravessa parede e não pula
   para dentro do galpão a cada passo.
6. **Pisável:** subir só onde a forma é `pisavel` e o degrau ≤ `STEP`.
7. **Nada antigo muda:** `RegionGoldenTest` (as 8 sementes da v1), `GameplayTest`, `CombatTest` e as referências 22/22.

## 5. Implementação (para a rodada que fizer)

- **`Collision.addProp`:** um ramo novo, `tipo == "composta"`, percorre `formas` e cria `Shape.box`/`Shape.circle` com o
  nome `prop:i`. É a mesma `Shape`, a mesma `push` e a mesma `contains`. Fica em ~30 linhas, sem estrutura nova.
- **Custo:** as consultas percorrem todas as formas (lista linear com filtro pelo raio envolvente).
  - A vila tem 185 formas (relatório `perf`). Quatro POIs compostos acrescentariam ~60, sem custo perceptível (lógica
    medida em ~0,4 ms por quadro).
  - Se a v2 passar de ~500 formas, aí sim vale uma grade uniforme.
- **Ordem:**
  1. extensão + teste sintético;
  2. `workshop_forge` + vitrine percorrida em 4 rumos;
  3. as ruínas e a barraca;
  4. o `stone_circle` depois do refinamento artístico;
  5. só então o gerador **v2** pode distribuí-los (ADR 0029).
- **Nunca:** mudar o perfil de um prop que a v1 planta. A colisão dele entra na conta da tentativa aceita, e a
  referência da v1 falharia (ADR 0029 §2).
