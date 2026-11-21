# Colisão composta: construções com interior

**Status:** implementada na rodada 12 e refeita na 12.1. O teste físico do proprietário achou dois defeitos:

- **Afundar na laje:** ao chegar andando, o herói afundava na laje da ruína e depois subia.
- **A parede quebrada:** quem pulava sobre ela afundava.

Os dois vinham de a colisão não ter os topos da geometria.

## 1. O contrato

Um prop com perfil `"tipo": "composta"` em `assets/scenes/colisao_e.json` tem várias formas simples. Exemplo real
(trecho):

```json
"ruin_house": {"tipo": "composta", "_fonte": "medido dos voxels por tools/conteudo/formas_compostas.py (revisado)", "formas": [
 {"tipo": "caixa", "min": [-2.375, -1.875], "max": [2.375, 1.75], "altura": [0.0, 0.375], "pisavel": true, "camera": false},
 {"tipo": "caixa", "min": [-1.5, 2.25], "max": [-0.25, 2.375], "altura": [0.0, 0.125], "pisavel": true, "camera": false},
 {"tipo": "caixa", "min": [-0.5, -0.5], "max": [0.5, 0.5], "altura": [2.125, 3.125], "corpo": false}
]}
```

- **Espaço e transformação:**
  - **Espaço:** u, no espaço do MODELO; caixa (`min`/`max` em x, z) ou círculo (`centro`, `raio`).
  - **Transformação:** cada forma passa pela matriz do posicionamento (`Collision.addComposite`). O resultado é a
    `Collision.Shape` de sempre, com nome `<prop>#<i>`.
- **Altura:** `altura [a, b]` é a faixa vertical acima da origem do modelo. Na 12.1 ela é **exata**: o topo de uma
  forma é o topo dos voxels que ela cobre.
- **Bandeiras:** `corpo` (padrão sim), `camera` (padrão = a do perfil), `pisavel` (padrão não).
- **Recorte** (rodada 12.3, só a torre caída): `"recorte": {"centro_u": [x, z], "raio_u": r}` no perfil. Com o herói
  dentro dessa zona, as consultas da CÂMERA ignoram as formas desta construção e o desenho a recorta entre a câmera e o
  herói; o corpo não muda ([recorte da câmera](recorte-da-camera.md)).
- **Um contrato só:** o mesmo serve ao movimento, à câmera, à conferência de alcance, aos pontos de interação, à
  colocação do herói e aos testes.

**A regra de suporte** (12.1, `Collision.supports`, vale para TODA forma, simples ou composta):

> todo sólido (corpo) sustenta quem está acima dele, menos pessoa e objeto de missão (NPC parado, o marco); e o que o
> perfil marca `pisavel`.

**Por quê:**

- **O defeito:** um corpo deixa de empurrar quando o topo dele está a até um degrau (0,30 u) dos pés. Assim se sobe
  degrau e se passa por peça baixa. Antes só o `pisavel` virava chão.
- **O efeito:** quem pulava sobre uma cerca ou um muro baixo ficava acima de algo que já não empurrava e ainda não
  sustentava, e caía DENTRO dele.
- **Com o salto da 12.1:** o salto alcança topos até 1,30 u (`MobilityTest`), então o caso deixou de ser raro.

**O apoio do herói** (`Collision.support`, só o `Player` usa): o chão pelo centro (`floor`, como sempre: é assim que se
sobe degrau) ou um topo na altura dos pés ou abaixo que o corpo ainda toca. Quem sai andando de uma borda só cai quando o
corpo já saiu dela, e quem desce de um salto rente a uma borda pousa nela. Descendo ao lado de um sólido cujo topo ficou
acima dos pés, o sólido é parede no mesmo passo (`Player.step`). A conferência de alcance continua com `floor`.

**A colocação** (`Collision.standAt`) põe o herói (início, continuar o jogo, recomeço) na superfície **livre mais baixa**
entre o relevo e os topos ao alcance do salto, mais um degrau:

- **Pode:** o piso da oficina, o topo de uma pedra média.
- **Não pode:** o telhado da casa, que fica fora do alcance. Um ponto dentro da casa continua inválido.

**O índice espacial** (`Collision.index`, células de 1 u) faz `floor`, `resolve` e `bodyAt` olharem só as formas de
perto, na mesma ordem da lista:

- **Mesmo resultado:** sai bit a bit igual ao de percorrer tudo (`CollisionIndexTest`, mais de 4000 consultas).
- **Saída de emergência:** se os empurrões tirarem o corpo da vizinhança consultada, a conta é refeita com todas as
  formas.

## 2. De onde vêm as formas (medição, `tools/conteudo/formas_compostas.py`)

Coluna a coluna de voxels (sem folhagem), as corridas contínuas (vão de até 2 voxels = a mesma peça), subindo do chão:

| Peça | Regra |
|---|---|
| **sólida** | começa abaixo da cabeça de quem está no topo sólido de baixo (topo + 1,2 u), OU o herói alcança o topo dela (a busca abaixo). Corpo **e** pisável, com a base e o topo **exatos** (laje, degrau, parede, bigorna, muro quebrado). Câmera só se o topo passa de 1,2 u |
| **topo inalcançável** | parede com topo acima de 2,0 u (laje + salto + degrau): o topo sobe para a grade de 0,25 u (o corpo é o mesmo; menos formas) |
| **só câmera** | o que começa a uma altura de herói acima do topo sólido E ninguém alcança (arco, lintel, telhado, toldo), numa grade de 0,25 u |
| **copa** | folhagem não empurra o corpo; a que fica acima da cabeça (≥ 1,2 u) é só de câmera |

- **Fusão:** os retângulos gulosos só juntam colunas de **mesma base e mesmo topo**. Na rodada 12 a parede em degraus
  virava um bloco com o topo do degrau mais alto, e os degraus visíveis não existiam na colisão.
- **Busca de alcance** (`reachable_tops`): sobre lugares de pé onde o corpo cabe (nenhuma coluna a até 0,26 u com sólido
  entre um degrau acima dos pés e a cabeça), a partir do chão em volta, subindo até o salto + degrau (lido de
  `combate_e.json`). Sem a exigência do corpo, a busca subia por saliências de 1 voxel até o telhado.
- **O piso elevado** é só uma peça sólida: a laje de 0,375 u é corpo + pisável com o topo em 0,375. Sobe-se por
  **degraus reais** de 0,125 u nas entradas (`conteudo_lib.stone_steps`, quebrados nas ruínas); sem degrau, a borda da
  laje é um rodapé que se sobe com um salto.
- **Gravação:** o script reescreve só os perfis da biblioteca; os da v1 saem byte a byte iguais. Revise o diff.

## 3. Quem tem e o que está liberado (rodada 12.1)

| Asset | Formas | Estado | Prova |
|---|---|---|---|
| `workshop_forge` | 293 | **liberado** | 5 rumos: entradas (a porta com degraus, o galpão com os degraus novos), interior na laje, câmera, P73 em 7 passos |
| `ruin_house` | ~160 | **liberado** | entra pelo arco e pela brecha (degraus quebrados), lareira e escombros; a copa (fosca, subida a ~2,2 u) segura a câmera sem colar nela (P82) |
| `ruin_tower` | ~143 | **liberado (12.1)** | P74: escada interna recuada e arco de 8 voxels (1,0 u, nascendo acima da cabeça): passa com um corpo 0,12 u mais largo de cada lado; sobe-se na muralha baixa com um salto (o mirante) |
| `market_stall` | 60 | **liberado** | o feirante atrás do balcão; o toldo segura a câmera |
| `ruin_vault` (12.2, PR #8) | 356 (6 só de câmera) | **liberado (12.2)** | entra pela boca; interior coberto: as formas só de câmera ficam sobre a cobertura medida pelo PR (`medidas.cobertura_u`) e seguram a câmera, e o teto não é corpo (dá para entrar e andar); o rasgo de luz fica aberto; o morro é pisável |
| `ruin_farmstead` (12.2, PR #8) | 88 (2 só de câmera) | **liberado (12.2)** | portão em arco na empena, porteira do curral, cocho e pedra do marco alcançáveis em 5 rumos |
| `stone_circle` | 190 | aguardando refinamento | refino artístico pendente |

**P74, o diagnóstico corrigido:**

- **O erro da rodada 12:** ela atribuiu o vão de ~0,5 u "às pedras do arco".
- **A causa real:** a medida voxel a voxel mostrou o arco com 0,75 u livres até acima da cabeça; o que estreitava eram
  os degraus 3 e 4 da escada interna, logo atrás da boca da porta.
- **A correção:** escada recuada mais arco alargado, com o arco ainda velho e quebrado (comparação no pacote 12.1).

**Variações das ruínas novas:** o PR mede cada variação (`formas_medidas_u`, `formas_iguais`, `pegada_reservada_u`). Nenhuma
variação é publicada como prop (`"prop": null`), então só a base vai ao jogo, com a colisão medida da base. A abóbada tem
`formas_iguais: false`: uma variação publicada no futuro vai precisar do perfil próprio, nunca o da base.

**Morador de ofício e colisão** (12.2): o corpo do morador num ponto elevado vai do chão à cabeça lá em cima
(`Collision.build`). A conferência de alcance ignora o que fica abaixo dos pés dele e, para o ponto autorado, usa o raio do
corpo ([moradores](moradores-dos-oficios.md) §1).

## 4. Os testes (`CompositeCollisionTest`, 11)

Cada composto, sozinho num chão plano, deslocado e girado 0°, 90°, 180°, 270° e 37°:

1. **Corpo:** todo voxel sólido na faixa do corpo fica dentro de forma de corpo (contra o `.vox` de verdade).
2. **Entradas e interior:** entradas alcançáveis, interação a até 1 u, área segura, conversa com o NPC do spawn.
3. **Câmera:** paredes cortam o raio; peça baixa não; telhado, toldo e a copa da ruína seguram quem está dentro.
4. **Piso exato (P75):** por dentro o chão é o topo da laje (0,375); por CADA entrada, a pé e sem pular, com a física real
   do herói seguindo o caminho que a busca na grade acha, sobe-se pelos degraus e chega-se ao meio, de pé na laje.
5. **Escala e translação.**
6. **P74:** a torre, com folga (corpo +0,12 u) e o salto para a muralha do mirante.
7. **P73:** os 7 passos na oficina.
8. **P82:** a câmera do jogador dentro da ruína em 3 posições × 8 rumos. Rodada 12: colada em 9 de 24 (todos ao lado da
   árvore); 12.1: 0 de 24.
9. **Liberação:** composto liberado ⇔ percorrido aqui (`ENTERABLE`).
10. **Nunca dentro:** para cada sólido com o topo ao alcance do salto (nos seis compostos liberados, em 2 rumos), quem pula
    para cima dele nunca fica mais de 2 mm dentro de um corpo, e termina de pé num apoio de verdade.
11. **O feirante na barraca (12.2):** o Quelmo na vaga lateral, em 5 rumos, não encosta e se alcança; a câmera de jogo
    o vê (pela rua 5 de 5 pontos do corpo; na conversa 3 de 5; o spawn antigo, 2 de 5).

**Testes que dependem disto:**

- `MobilityTest` (todo sólido segura o salto; pessoa nunca é plataforma; a casa fora do alcance);
- `CollisionIndexTest` (o índice);
- `ConteudoMundoTest` (perfil `composta` para liberar; folhagem presa acima de 1,7 u é fosca).

## 5. Custo (rodada 12.1, sementes 1–30, sem GPU, o mesmo binário × o da rodada 12)

| | rodada 12 | 12.1 |
|---|---|---|
| formas por região v2 | 272–365 | 340–718 (mediana 369) |
| conferência de alcance (mediana) | 17 ms | **1 ms** (índice espacial) |
| criar a região inteira (mediana) | 112 ms | 96 ms |
| v1: formas / conferência | 207 / 4 ms | 207 / 1 ms (regiões idênticas) |

O orçamento do `GeneratorV2Test` passou de 520 para 900 formas, pela medida acima. O limite antigo existia porque cada
consulta percorria a lista inteira.

## 6. Como acrescentar um asset composto

1. **Declarar:** no `@asset`, `colisao={"tipo": ..., "formas": "medidas"}`; ele nasce `depende_colisao_composta`.
2. **Entradas:** toda entrada precisa subir de 0 até o piso em degraus ≤ 0,30 u (de preferência 0,125, com
   `conteudo_lib.stone_steps`). Vão livre ≥ 0,6 u + folga até 1,2 u acima do piso, com o arco nascendo acima da cabeça.
   A folhagem presa ao modelo acima de 1,7 u é fosca (`LEAF_FIX`, `MOSS_FIX`).
3. **Gerar:** `python3 tools/build_assets.py conteudo_e`, depois `python3 tools/conteudo/formas_compostas.py`, e revise
   o diff.
4. **Conferir:** ponha o id em `CompositeCollisionTest.ENTERABLE` e rode o teste. Se não entrar, o defeito é do asset
   (porta, piso, ponto), nunca da conferência.
5. **Liberar:** só então, com o comentário do porquê. Gere `tools/conteudo/planta_colisao.py` para a revisão.

## Publicação da v2 (tag `procedural-world-v0.6`)

- **As formas compostas dos assets que a v2 planta fazem parte dela.** Elas decidem a conferência de alcance e a
  tentativa aceita, então não mudam mais.
- **Uma medição nova** (outra resolução, outra regra em `formas_compostas.py`) que mude as formas de um asset liberado
  na v2 só entra com a v3, ou depois de provar que as 200 sementes e `regioes-v2.txt` não mudam.
- **A zona `recorte`** (só a torre caída) é da câmera e do desenho, não da geração. Também foi aprovada no teste físico
  como está.
