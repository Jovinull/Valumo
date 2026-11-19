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
   - Mudou o resultado de alguma semente → suba `RegionGenerator.VERSION`.
2. **O mundo fixo não muda.**
   - `World.LEGACY` são as funções de antes; refatorar o relevo exige as referências 22/22 com desvio 0 e o gate 51/0.
3. **A autoria está nos componentes.** Só props e NPCs aprovados da E. Asset novo só com aprovação e pelo pipeline
   (`tools/env_e.py`).
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
