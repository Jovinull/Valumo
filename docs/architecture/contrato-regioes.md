# Contrato de regiões (rodada 11)

Uma região é o que `RegionGenerator.compose(semente, tentativa)` devolve: um `RegionLayout`, dado puro em u (mundo) e
graus. Hoje há uma região por mundo, com id `r0`.

| Campo | Significado | Limites |
|---|---|---|
| `regionId` | id estável da região | `r0` (prefixo de todos os ids gerados) |
| `attempt` | tentativa que passou na conferência | 0–3 |
| relevo: `noiseX/Z`, `amplitude`, `rimN/S/E/W`, `hill` | parâmetros do `ProceduralField` | amplitude 0,6–1,6; encostas 0–4,5 u; colina 1,4–2,6 u de altura |
| `sx, sz`, `fx, fz`, `plateauR`, `plateauH` | assentamento: centro, eixo da saída da trilha, raio e altura do patamar | centro a 1,5–6 u do meio; raio 7 |
| `px, pz`, `gx, gz`, `clearingR`, `clearingH` | clareira do posto: centro, eixo de chegada, raio, altura | 13–16 u do assentamento, dentro de ±16,5 u; raio 3,6 |
| `path` | eixo da trilha, um ponto a cada ~0,5 u | do assentamento à clareira |
| `pond` | lago (x, z) ou null | longe do assentamento, do posto e da trilha |
| `arrangement`, `forest` | arranjo do assentamento (0–2) e composição da mata (0 carvalhal, 1 pinhal, 2 bétulas) | |
| `props`, `npcs`, `spawns`, `features` | o que foi posto (nome, x, z, rumo) | dentro de ±24 u |
| `pois` | lugares ([contrato](contrato-pontos-de-interesse.md)) | |
| `limits` | círculos jogáveis | dentro de ±24 u |
| `start*`, `restart*`, `arenaR` | início do jogo, recomeço do confronto, raio da arena | |

**Invariantes**, conferidos por `Reachability` (com a colisão real) e por `ProceduralWorldTest`:
- o início é caminhável;
- de lá se chega (com degraus de no máximo 0,30 u e o raio do herói) a cada morador pela frente, ao marco pela frente,
  a cada fungo e ao ponto de recomeço; a volta vem pelo mesmo caminho;
- a arena tem ≥ 75% de células livres;
- nenhum morador encosta em sólido;
- nenhum prop sólido está na água;
- nenhum prop na área jogável está em desnível maior que 0,55 u (estalagem: 0,3 u);
- ids únicos;
- a Kelyra sempre mora (a estalagem); sem lago, não há Ondina;
- tudo fica dentro do mundo, com alturas finitas.

Composição reprovada: outra tentativa, até 4. Se nenhuma passa, a semente é recusada com os motivos
(`ProceduralScene.Rejected`).
