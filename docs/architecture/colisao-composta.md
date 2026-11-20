# Colisão composta: construções com interior

**Status:** implementada na rodada 12. A proposta da integração dos lotes 1 e 2 virou o contrato abaixo, com dois
ajustes medidos: a altura de cada forma sai dos voxels, e o piso elevado é chão pisável.

## 1. O contrato

Um prop com perfil `"tipo": "composta"` em `assets/scenes/colisao_e.json` tem várias formas simples. Exemplo real
(trecho da oficina):

```json
"workshop_forge": {"tipo": "composta", "_fonte": "medido dos voxels por tools/conteudo/formas_compostas.py (revisado)", "formas": [
 {"tipo": "caixa", "min": [0.0, 0.0], "max": [0.25, 0.375], "altura": [0.375, 2.625]},
 {"tipo": "caixa", "min": [0.5, -0.25], "max": [1.0, 0.0], "altura": [0.5, 1.0], "camera": false},
 {"tipo": "caixa", "min": [-0.25, -1.25], "max": [2.75, 1.0], "altura": [1.5, 5.875], "corpo": false},
 {"tipo": "caixa", "min": [1.0, 0.0], "max": [2.25, 1.0], "altura": [0.0, 0.3], "corpo": false, "camera": false, "pisavel": true}
]}
```

- **Espaço:** u, no espaço do MODELO (relativo à origem do modelo, não ao centro da caixa).
  - **caixa:** `min`/`max` (x, z);
  - **círculo:** `centro`, `raio`.
- **Transformação:** cada forma passa pela matriz do posicionamento (`Collision.addComposite`).
  - O centro vai por `transformPosition`, o rumo é o do prop e o raio e as meias-extensões multiplicam pela escala.
  - O resultado é a mesma `Collision.Shape` de sempre, com nome `<prop>#<i>`.
- **Altura:** `altura [a, b]` é a faixa vertical acima da origem do modelo.
- **Bandeiras:**
  - **`corpo`** (padrão sim): empurra o herói (`Collision.resolve`). A forma é ignorada quando o topo fica a até um
    degrau dos pés.
  - **`camera`** (padrão = a do perfil): encurta o braço da câmera (`rayClear`).
  - **`pisavel`** (padrão não): o topo vira chão (`Collision.floor`) se estiver a até um degrau
    (`Player.STEP` = 0,30 u).
- **Um contrato só:** o mesmo serve ao movimento (`Player`), à câmera (`FollowCamera`), à conferência de alcance
  (`Reachability`, que decide se uma região gerada é aceita), aos pontos de interação e aos testes. Não existe uma
  colisão "de vitrine" à parte.

## 2. De onde vêm as formas

`python3 tools/conteudo/formas_compostas.py` mede cada asset do catálogo com `colisao.formas` direto dos voxels do
`.vox` gerado.

| Forma | Regra |
|---|---|
| **corpo** | toda coluna de voxels com algo na faixa **[0,375; 1,2) u** (acima do degrau que se sobe a pé, abaixo da cabeça do herói), da base desse voxel ao topo da parede contínua (vão de até 2 voxels conta como parede: janela, frincha), fundidas em retângulos gulosos na resolução do voxel |
| **peça baixa** | topo < 1,2 u (bigorna, tina, balcão): corpo sem câmera; topo ≤ 0,8 u: pisável também (como o caixote) |
| **só câmera** | o que fica acima da parede contínua ou só existe acima da cabeça (arco, lintel, telhado, toldo), numa grade de 0,25 u, da base ao topo do que está lá |
| **copa** | folhagem não empurra o corpo; a que fica ACIMA da cabeça (≥ 1,2 u) vira forma só de câmera, como a copa das árvores soltas (perfil `tronco`). A árvore jovem de dentro da ruína: sem isso, quem examinava a lareira sumia atrás das folhas |
| **piso elevado** | coluna só com a laje de piso (abaixo da faixa do corpo, topo ≥ 0,25 u): pisável, sem corpo nem câmera, com o topo em **0,30 u** (o degrau). O herói entra sem pular; o pé fica no máximo 0,075 u abaixo da laje de 0,375 u |

- **O que é gravado:** o script reescreve só os perfis da biblioteca em `colisao_e.json`. O resto sai byte a byte igual,
  e os perfis dos props que o gerador v1 planta nunca são tocados.
- **Revisão:** o diff é revisado antes do commit.
- **Os outros assets da biblioteca** recebem o perfil simples que o catálogo entrega. As variantes publicadas usam o do
  original por prefixo (`"sign_trade*"`).

## 3. Quem tem e o que foi liberado

| Asset | Formas | Estado (rodada 12) | Por quê |
|---|---|---|---|
| `workshop_forge` | 71 (32 de corpo, 10 de câmera, 29 de piso) | **liberado** | percorrido em 5 rumos; P73 resolvido |
| `ruin_house` | 78 (3 da copa) | **liberado** | entra pelo arco e pela brecha; chega à lareira e à frente dos escombros; a copa segura a câmera |
| `market_stall` | 12 | **liberado** | o feirante fica atrás do balcão, sob o toldo |
| `ruin_tower` | 59 | **aguardando_refinamento** | sob o arco da porta sobram **~0,5 u** abaixo da cabeça; o herói tem 0,52 u. O interior é inalcançável em qualquer rumo (P74, decisão de arte: alargar o arco) |
| `stone_circle` | 58 | aguardando_refinamento | refino artístico pendente (antes desta rodada) |

- **Conjuntos:** `ferreiro` (âncora oficina) e `comerciante` (item barraca) passaram a liberados. O catálogo os deriva
  dos membros.

**Dois pontos corrigidos nos dados, sem mexer em modelo:**

- **Oficina, P73:** o spawn da ferreira passou para o lado direito do galpão, a (1,94; 0,44). Antes ficava a 0,125 u
  da bigorna e a 0,28 u da bancada. Folgas agora:
  - 0,32 u da peça mais perto;
  - 0,67 u do ponto da forja;
  - 1,0 u da bigorna;
  - 1,27 u da bancada.
- **Ruína de casa:** o ponto "escombros" marca a FRENTE do monte. O pedaço de telhado caído é sólido do chão até
  1,1 u; antes o ponto ficava 1,3 u para dentro dele.

## 4. Os testes (`CompositeCollisionTest`)

Cada asset composto, sozinho num chão plano, deslocado da origem e girado 0°, 90°, 180°, 270° e 37°:

1. **Contra os voxels de verdade** (`VoxModel`, não o script): todo voxel sólido com a base na faixa do corpo fica
   dentro de uma forma de corpo.
2. **Entradas e interior:**
   - toda entrada é alcançável de fora;
   - todo ponto de interação de chão tem lugar de pé a até 1 u (o contrato do catálogo);
   - a área segura declarada é caminhável;
   - dá para falar com o NPC do spawn, com os NPCs no lugar (corpo 0,3 u);
   - a busca confere o degrau entre células, como a `Reachability`.
3. **Câmera:** nenhuma parede alta é atravessada; peça baixa não segura a câmera; o telhado da oficina, o toldo da
   barraca e a copa da árvore de dentro da ruína seguram a câmera de quem está dentro.
4. **Piso elevado:** dentro da oficina e da ruína, o chão é a laje.
5. **Escala e translação:** acompanham o prop (`rotateY` de 90°: z local vira +x).
6. **Torre:** a porta é estreita demais, e ela não é liberada. Se um dia passar, o teste avisa para reavaliar.
7. **P73**, passo a passo, em todos os rumos: entrar, forja, bigorna, bancada, falar com a ferreira, sair.
8. **Liberação:** todo composto liberado passou por aqui (`ENTERABLE`).

No catálogo, `ConteudoMundoTest` exige o perfil `composta` em `colisao_e.json` para liberar asset com `colisao.formas`.

## 5. Custo (medido na rodada 12, sementes 1–30, sem GPU)

| | v1 | v2 |
|---|---|---|
| formas de colisão por região | 200–211 (mediana 207) | 272–365 (mediana 287) |
| conferência de alcance | mediana 4 ms (máx. 22) | mediana 18 ms (máx. 25) |
| gerar + conferir a região inteira | mediana 55 ms (máx. 191) | mediana 110 ms (máx. 246) |

- **Busca por forma:** as consultas percorrem a lista com o filtro do raio envolvente. Uma oficina sozinha soma 71
  formas.
- **Grade espacial:** com menos de 400 formas, não foi preciso. Fica para quando uma região passar de ~500
  (`GeneratorV2Test` limita a 520).

## 6. Como acrescentar um asset composto

1. No `@asset`, declare `colisao={"tipo": ..., "formas": "medidas"}`. Ele nasce `depende_colisao_composta`.
2. `python3 tools/build_assets.py conteudo_e`, depois `python3 tools/conteudo/formas_compostas.py`, e revise o diff de
   `colisao_e.json`.
3. Ponha o id em `CompositeCollisionTest.ENTERABLE` e rode o teste. Se não entrar, o defeito é do asset (porta, piso,
   ponto) ou do ponto do catálogo, nunca da conferência. Não afrouxe nada.
4. Só então mude o estado para liberado no `@asset`, com o comentário do porquê.
