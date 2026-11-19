# Contrato de pontos de interesse (rodada 11)

`RegionLayout.PointOfInterest(id, type, region, x, z, radius, state, features, spawns)`, criado pelo gerador e
consultado pelos modelos de missão.

| Campo | Regra |
|---|---|
| `id` | `poi.<regiao>.<tipo_pt>.<n>`: `poi.r0.assentamento.0`, `poi.r0.posto_trilha.0`. Nunca coordenadas |
| `type` | inglês, estável: `settlement`, `trail_post` (os do protótipo). Futuros: `ruin`, `cave_entrance`, `enemy_camp`, `landmark` |
| `region` | id da região |
| `x, z, radius` | o lugar e o raio da área que ele ocupa (para o gerador afastar outras coisas) |
| `state` | estado **gerado** (inicial): `habitado`, `sinal_apagado`. O que o jogador muda (o marco aceso) fica no `QuestSystem` e no save, nunca aqui |
| `features` | ids dos objetos com estado do lugar (`obj.r0.posto_0.marco`) |
| `spawns` | identidades estáveis dos inimigos do lugar (`r0.posto_0.fungo.1`) |

**Funções narrativas:** um modelo de missão pede um lugar por tipo, estado e conteúdo. Por exemplo: "um
`trail_post` em `sinal_apagado`, com inimigos `fungo_cardo` e um objeto, alcançável a partir de um assentamento com um
morador de papel `caminhos` ou `trilhas`". Ele nunca pede "um marco em x = −1,15". Consulta: `RegionLayout.poi(tipo)`
(0,1 µs).

**Acesso:** o lugar só entra nos fatos se a conferência de alcance o aprovou (a região inteira é recusada se não, então
o que existe é alcançável).

Novo tipo: [guia](../processo/como-adicionar-um-tipo-de-poi.md).
