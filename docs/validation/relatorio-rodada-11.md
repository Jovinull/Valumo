# Relatório da Rodada 11 — fundação do mundo procedural

**Situação:** a Etapa A (fechamento da Rodada 10) foi publicada: `main` `8fd6756` e a tag `adventure-foundation-v0.4`.
A Etapa B (este relatório) é experimental e está **sem commit e sem push**, no ramo local
`rodada-11/mundo-procedural`. O Titã não foi usado.

Pacote local: `output/visual-review/round-11/` (LEIA-ME, vistas por semente, comparações, vídeo, registros,
desempenho, ZIP). Arquitetura: [mundo-procedural.md](../architecture/mundo-procedural.md). ADRs
[0025](../decisions/0025-mundo-procedural-por-composicao.md) e [0026](../decisions/0026-save-v2-mundo-procedural.md).

## Etapa A — fechamento da Rodada 10

| Item | Feito |
|---|---|
| Inconsistência narrativa | nenhuma fala afirma ver o marco (Kelyra deduz pelo retorno; Bramor "soube"); o marco não mudou de lugar |
| Ferramentas técnicas | arena e sensação A/B/C na pausa só no modo técnico (`-Ptecnico`); o jogo normal recusa e não oferece; roteiros e testes com acesso direto; o `combate92/capturas.txt` roda em modo técnico (comparações históricas iguais) |
| Aprovação | missão, marco, falas e consequência registrados como APROVADOS (referência de gameplay); escuta individual dos 4 sons segue pendente, sem resultado específico |
| Portões | 325 testes, validadores, gate 51/0, referências 22/22, aventura completa, clone limpo, CI verde, varredura de arquivos pessoais |
| Git | 17 commits por assunto (só título, noreply) `07ec057..8fd6756` por avanço rápido; tag anotada `adventure-foundation-v0.4` → `8fd6756` publicada depois do CI; nenhuma tag antiga movida; sem Release |

## Etapa B — o que foi feito

1. **O relevo virou dado** (`TerrainField`). `World.LEGACY` faz as mesmas contas de antes: **referências 22/22 com
   desvio 0, gate 51/0**.
2. **Gerador de região** (`vx.game.world`):
   - RNG próprio derivado por rótulo;
   - relevo com os parâmetros da semente, colina e lago opcionais;
   - assentamento em 3 arranjos autorais girados para a trilha e espelhados;
   - trilha curva, posto de trilha (quadro, marco, dois fungos-cardo) e 3 composições de mata.
3. **Conferência na colisão real** (`Reachability`), com tentativas limitadas e recusa com motivo.
4. **A região vira os documentos de cena de sempre** (`ProceduralScene`): o mesmo `World`, `SceneDef`, `Collision`,
   `CombatDef`, `GameLogic`.
5. **"As Luzes do Posto" como modelo + instância:**
   - **Fatos:** um posto apagado, alcançável, com fungos-cardo, e um morador com papel de caminhos ou trilhas.
   - **Doador:** escolhido por papel, com duas vozes autorais (estalajadeira, guardião).
   - **Falas:** com o rumo real.
   - **Ids:** estáveis.
   - **Sistema:** o mesmo `QuestSystem` da Rodada 10.
6. **A fundação deixou de impor "derrotar → interagir → falar":**
   - `conclusao: "ultimo_objetivo"` (testado);
   - um NPC pode ter várias missões em sequência (testado).
7. **Save v2 só para o mundo gerado:** guarda o mundo e os vínculos, e recusa qualquer diferença. O v1 da Rodada 10 fica
   intacto e os dois não se confundem.
8. **Modo** `-Dvx.mundo=procedural -Dvx.semente=N`, com a semente na tela inicial e na pausa, e saves em pasta própria.

## Defeitos achados e corrigidos na rodada

| # | Defeito | Como apareceu | Correção |
|---|---|---|---|
| 1 | Árvores, pedras e o posto "dentro da água" em todas as sementes | conferência (a primeira sonda reprovou as 8 sementes) | vales rasos no relevo natural; água só na bacia do lago |
| 2 | Moradores colados em poço, lanterna, barril ou toco; um sem acesso de frente | conferência | vagas escolhidas longe dos props do arranjo e dos outros moradores, com o lugar de quem fala livre |
| 3 | Estalagem em desnível | conferência | patamar pleno até `plateauR + 1` |
| 4 | Nenhuma das sementes do pedido com lago | capturas | regra do lago mais larga (a 77 entra como semente com lago; ver P54) |
| 5 | Vista geral virou close; vista do posto dentro de copas | capturas | vista técnica que não encurta e escolhe o rumo com a vista livre (só nos roteiros) |
| 6 | **Todo salvamento automático do mundo gerado era recusado** | teste de save v2 | o vínculo guardava coordenada `float` e a releitura comparava `double`; agora é `double` arredondado |
| 7 | Fala "Dá para ver na sua bota" pegava o filtro de "ninguém afirma ver" | teste de texto | reescrita ("Sua bota diz que sim") |

## Respostas (§28)

1. **O que passou a ser procedural?**
   - o relevo: parâmetros, colina e lago;
   - a posição do assentamento e do posto, e o traçado da trilha;
   - o arranjo do assentamento, quem mora nele e onde;
   - o encontro e o marco no lugar gerado;
   - a vegetação inteira;
   - a área jogável;
   - a missão (instância), o doador e as falas paramétricas.

   Os componentes (props, NPCs, a estalagem) são os autorais aprovados.
2. **O terreno varia?** Sim, mas pouco à distância de jogo. A diferença média de altura entre sementes passa de 0,25 u
   (teste), e há encostas de 0 a 4,5 u, colina e lago. De cima, o que mais distingue as regiões é o traçado, os lugares e
   a mata. O relevo ainda é suave (P53).
3. **As regiões são reproduzíveis?** Sim. Mesma semente e mesma versão dão os mesmos documentos, vínculos e alturas, em
   qualquer ordem de geração (teste), e a mesma grade T1 (cache pela identidade do campo).
4. **Os caminhos são navegáveis?** Sim, conferidos na colisão real com o raio e o degrau do herói. O vídeo percorre ida e
   volta com entrada real (`seguir-trilha`).
5. **Os POIs são identificados?** Sim: `poi.r0.assentamento.0` e `poi.r0.posto_trilha.0`, com tipo, estado gerado,
   objetos e inimigos.
6. **NPCs sem coordenadas fixas?** Sim. Vagas autorais no espaço local do assentamento, escolhidas pela semente e
   conferidas (livres de props, com acesso de frente). A Kelyra mora sempre (a estalagem); sem lago não há Ondina.
7. **Como os encontros são posicionados?** No posto: dois fungos-cardo entre a chegada da trilha e o marco, com ids
   `r0.posto_0.fungo.1` e `.2`. A arena é a clareira gerada, e o ponto de recomeço fica na trilha antes dela.
8. **Como a missão encontra um lugar?** Por fatos. O modelo pede um `trail_post` em `sinal_apagado`, com inimigos
   `fungo_cardo` e um objeto, e um morador com papel `caminhos` ou `trilhas`. `RegionLayout.poi(tipo)` leva 0,1 µs.
9. **IDs estáveis?** Derivados de ids (região, tipo, ordem), nunca de coordenadas. O RNG é derivado por rótulo, e a
   vegetação não move nada da missão (teste).
10. **Vínculo com o POI?** A instância `quest.instance.s1024.r0.posto_trilha_0.1` tem alvos = os inimigos do POI,
    objeto = o marco do POI, npc = o doador escolhido. O encontro `posto` do combate usa as mesmas identidades.
11. **Salvar os vínculos?** No save v2 (`vinculos`): modelo e versão, POI, doador, inimigos, objeto e posição, rumo. Ao
    abrir, a região é regenerada e o save só é aceito se tudo bater.
12. **Objetivos impossíveis?** A região só é aceita se o marco for alcançável de frente, cada fungo alcançável, a arena
    ≥ 75% livre, o recomeço alcançável e cada morador acessível. Senão vem outra tentativa (máximo 4) ou a recusa com
    motivo. A instância só existe com os fatos.
13. **Missões incoerentes?** Os textos são autorais, com parâmetros que correspondem aos fatos (rumo real, artigo do
    doador); parâmetro sem valor é erro; ninguém afirma ver o marco (teste). Depois de concluída, o marco fica aceso e o
    modelo não se reaplica ao lugar.
14. **Preservar o save v1?** Intacto: o mundo fixo continua escrevendo e lendo v1. v1 no procedural e v2 no fixo são
    recusados com a explicação (teste). Não há migração.
15. **As sementes parecem mundos diferentes?** Em parte. Mudam o lado da trilha (norte, oeste, leste), a forma dela, o
    lugar do posto, a orientação e o arranjo da estalagem, quem mora, a mata (carvalhal, pinhal, bétulas) e o lago.
    Repetem a estalagem, o poço, o quadro do posto e o relevo suave. **Não está no nível da vila aprovada** para dizer
    "outro lugar" com força (P53).
16. **Custo de geração:** ~140 ms na primeira do processo (carga de arquivos e JIT), 6–15 ms quente:
    - compor: ~2 ms;
    - conferir na colisão real: ~5 ms;
    - instanciar a missão: ~0,3 ms.

    É feita uma vez, antes do primeiro quadro.
17. **Desempenho:** as regiões geradas ficam em 8,9–9,4 ms de quadro (mediana), contra 9,5 ms da vila fixa, no mesmo
    binário e sessão. Lógica 0,39–0,42 contra 0,30 ms, 73–86 draws contra 114, 109–122 malhas contra 212, 0–1 coleta.
    Tabela: `round-11/desempenho/resumo.md`.
18. **Testes:** **339, 0 falhas** (engine 45, viewer 116, game 178). São 14 novos sobre os 325 do fechamento:
    `ProceduralWorldTest` 12 e `QuestTest` +2.
19. **Problemas:** os corrigidos estão acima. Os restantes estão em `problemas-conhecidos.md` (P52–P56): uma região
    só; variedade modesta; lago em ~2/3 das sementes; duas vozes de doador; teste físico pendente.
20. **O que falta para um mundo maior:**
    - **Regiões vizinhas:** bordas compartilhadas e trilhas que atravessam.
    - **Geração fora da thread do GL:** produzir layout e documentos à parte e montar as malhas na fila do GL.
    - **Mais componentes autorais** (casas, ruínas, entradas de caverna), com aprovação.
    - **Mais tipos de POI e de objetivo:** descobrir, defender, escoltar.
    - **Mais vozes e modelos de missão.**
    - **Save com várias regiões:** o formato já prefixa os ids pela região.
    - **Relevo com mais personagem** onde não há trilha.

## O que o proprietário precisa ver

- **Se este é o caminho:** `round-11/comparacoes/` e o vídeo da região 1024, com entrada real e o som da aplicação.
- **Teste físico do modo procedural:** `./gradlew jogo -Dvx.mundo=procedural -Dvx.semente=1024` (e 2048, 4096, 77).
- **Commit da Rodada 11:** só com nova autorização.

## Fechamento

Publicação preparada junto com a 11.1: [relatório do fechamento das rodadas 11 e 11.1](relatorio-fechamento-rodadas-11.md).
