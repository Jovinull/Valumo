# Sistema de proporções (direção E)

Fonte de verdade dos números de proporção **dos três protagonistas** (congelados). NPCs futuros usam
a faixa 2,0–3,0 cabeças com escolha autoral ([contrato-de-qualidade.md](contrato-de-qualidade.md)). Código: `tools/charlib_e.py` (constantes `HIP`, `NECK`,
`SHOULDER`, `EYE_Y`, `FACE_Z`). Teste: `AssetContractTest.consolidatedHeroesFollowTheIdentity`.

## Como medir

`headsTall = (topo do cabelo + 1) / (topo do cabelo − NECK + 1)`, em voxels, **sem** chapéu, capuz,
pena e pontas de mecha que saem do contorno (a ponta do topete do aventureiro e a copa do chapéu da
maga ficam fora). É a mesma convenção para os três heróis — a rodada 01 media a exploradora com o
capuz e ela parecia ter 2,30 cabeças sem ter mudado de forma.

## Grade do bípede (voxel 1/40 u)

```
y = 43..44  topo do cabelo (casca)          cabeça com cabelo = 18 voxels
y = 26..41  crânio 16 × 16 × 15 (NECK = 26 = pivô da cabeça)
              rosto: placa plana em z = 6; olhos y 29..33; sobrancelha y 35; boca y 27
y = 23.5    ombro (pivô dos braços, x = ±6,5)
y = 17      cotovelo (pivô do antebraço `farm_*`, x = ±7,45) — rodada 03
y = 9..25   tronco em pera (a barra cobre o topo das coxas, y 9..10)
y = 11      quadril (pivô das pernas, x = ±3,5)
y = 0..10   pernas (11) — bota de 0 a 6, mais larga que a coxa; joelho (pivô de `shin_*`) a ~45% da perna,
            tornozelo a 1,5 voxel (meta.ankle) — rodada 03
```

## Números

| Medida | Alvo | Faixa aceita | Aventureiro | Exploradora | Maga |
|---|---|---|---|---|---|
| Cabeças | 2,45 | 2,35–2,60 | 2,44 | 2,53 | 2,53 |
| Altura total (com chapéu) | — | ≤ 1,25 u | 1,18 u | 1,25 u | 1,25 u |
| Pernas / altura | 25% | 22–28% | 25% | 26% | 26% |
| Largura do ombro / largura do quadril | 0,75 | 0,7–0,85 | 0,75 | 0,75 | 0,8 |
| Mão (diâmetro) / braço | ≥ 1,1 | — | 1,15 | 1,15 | 1,0 (manga-sino) |

A exploradora e a maga medem 2,53 porque, sob capuz e chapéu, a casca de cabelo é mais fina
(cabeça de 17 voxels em vez de 18): o corpo é o mesmo, a medida muda 0,09. Ainda dentro da faixa.

## Por que 2,35–2,60

- Estudo da rodada 01 ([../experiments/proporcoes.md](../historico/experimentos/proporcoes.md)): acima de ~3
  cabeças o personagem volta a ler como "pessoa de cubos"; abaixo de 2,2 vira bebê/mascote.
- Comparação desta rodada (`round-02/personagens/variantes-*.png`): V1 (2,31) lê infantil demais
  de perto; V3 (2,38) e V2 (2,44) leem como "pequeno aventureiro"; D original (2,55) começa a
  alongar o tronco.

## Regras que não mudam com o personagem

1. Pernas curtas e grossas; bota mais larga que a coxa; bico da bota à frente.
2. Corpo em pera: quadril ≥ ombro.
3. Braço termina na altura do quadril; mão maior que o antebraço.
4. Cabeça centrada sobre o tronco (não à frente): lê "compacto", não "corcunda".
5. Variação permitida por personagem: largura do tronco (±0,5 voxel), postura (`meta.lean`),
   volume de cabelo/chapéu. **Não** se varia comprimento de perna — o rig e as animações dependem dela.
