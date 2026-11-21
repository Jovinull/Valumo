# Publicação da v0.7 — fechamento das Rodadas 13 e 13.1 (fundação do inventário)

**Tag:** `inventory-foundation-v0.7`.

**O que é:** a primeira fundação de inventário do Valumo, aprovada pelo proprietário. O jogador agora:
- encontra materiais e achados nos lugares que descobre;
- recebe itens de NPCs;
- administra uma mochila sem limite prático de espaços, organizada por categoria;
- vê os itens em modelos voxel;
- usa um consumível;
- continua se movendo com a mochila aberta;
- salva e recupera tudo, inclusive em mundos antigos.

Os geradores v1 e v2 continuam intactos.

## 1. Aprovação do proprietário

O proprietário avaliou a Rodada 13 e aprovou as fontes, a cataplasma e o save. Pediu mudanças na mochila, que viraram a
Rodada 13.1 ([relatório](relatorio-rodada-13-1.md)).

A mochila da 13.1 foi **aprovada**: "o resultado está satisfatório", sem pedido de alteração de gameplay ou de interface.
Com isso fecha o P107 (a avaliação pendente da 13.1). Este relatório registra só a aprovação relatada, sem afirmar testes
manuais específicos que não foram relatados.

## 2. O que a v0.7 publica

| Peça | Estado |
|---|---|
| Mochila | painel translúcido na metade direita, o mundo visível atrás, o jogo em tempo real (o herói anda, corre, pula e esquiva); o mouse é dela enquanto aberta (seletor que segue o mouse, clique que usa), teclado nas setas e no Enter, abas por categoria, grade que rola, prévia voxel 3D girando ([ADR 0032](../decisions/0032-mochila-viva.md), [inventário](../architecture/inventario.md)) |
| Inventário | sem limite de espaços no jogo; pilhas com o limite de cada item; ordem de chegada; a lista se compacta; teto técnico de 4096 pilhas só para a leitura segura do save |
| Itens | cinco itens em quatro categorias, definidos por dados (`itens_e.json`); modelos voxel gerados por código (`tools/itens_e.py`) ([itens](../architecture/itens.md)) |
| Cataplasma | devolve um coração com o herói ferido e fora da luta; fora disso não é gasta e a tela diz por quê |
| Fontes | o presente da Índira ao fim da conversa; os achados dos lugares que já existem (casa, abóbada, sítio, árvore; o mirante não dá item) ([recompensas](../architecture/recompensas-e-loot.md)) |
| Persistência | cada fonte entrega uma vez por aventura (personagem × mundo), tudo ou nada; as fontes usadas e a mochila vão no save, em campos OPCIONAIS do contrato existente ([ADR 0031](../decisions/0031-itens-e-recompensas.md), [save](../architecture/save.md)) |

**O catálogo não está congelado:**
- itens novos podem entrar depois, desde que o save continue compatível (id estável, campos opcionais);
- itens novos não podem mudar o resultado dos geradores publicados.

## 3. Geradores congelados (gate)

| Conferência | Resultado |
|---|---|
| 300 sementes v1 (arranjo completo por sha256) | **idênticas** às da v0.6 |
| 200 sementes v2 | **idênticas** às da v0.6 |
| sementes recusadas | 0 (v1) e 0 (v2) |
| `regioes-v1.txt`, `regioes-v2.txt` | iguais aos da tag `procedural-world-v0.6` |
| gerador (`vx.game.world`), `Collision`, `ProceduralScene`, `Reachability` | nenhuma diferença desde a v0.6 |
| `regiao_v2_e.json`, `descobertas_e.json`, `habitantes_e.json`, `colisao_e.json`, `modelos_missao_e.json` | nenhuma diferença desde a v0.6 |

O inventário é uma camada de jogo e de progresso. Não há v3.

## 4. Saves

**Com os binários de verdade.** Os binários da v0.6 (tag), da rodada 13 (`26806fa`) e da v0.7 rodaram com dados
TEMPORÁRIOS (cópias); os do proprietário foram conferidos por sha256 antes e depois.

**Duas matrizes:**
- `tools/qa/saves_antigos.sh`: vila v1 com a missão ativa e concluída, região v2 com o marco aceso e concluída, e as
  quatro aventuras do menu (mesmo/outro personagem × vila/região, com personagens, mundos e aparências).
- `tools/qa/saves_inventario.sh`, nova: os casos com a mochila. A v0.7 abre cada save pelo Continuar e o grava de novo
  sem mexer em nada. O arquivo regravado tem de sair igual, campo a campo: moedas, missões, descobertas, objetos, fontes,
  mochila na mesma ordem, aparência, mundo, vínculos e posição.

| # | Save | Feito por | Resultado na v0.7 |
|---|---|---|---|
| 1 | Vila do Posto (save v1), missão ativa e concluída | v0.6 | iguais nos dois binários, 0 `.invalido` |
| 2 | região do gerador **v1** (semente 1024), missão pronta para entregar, marco aceso | v0.6 | igual campo a campo; mochila vazia, sem campos novos |
| 3 | região do gerador **v2** (semente 1024), idem | v0.6 | igual campo a campo; mochila vazia |
| 3b | aventuras do menu (personagens, mundos, aparência) | v0.6 | iguais nos dois binários |
| 4 | rodada 13 com os **24 espaços** cheios (presente da Índira + caderno da casa + montagem) | rodada 13 | 24 pilhas e 54 unidades na mesma ordem, as 2 fontes, 5 moedas, 1 descoberta: igual |
| 5 | rodada 13 com **buracos** (espaços 0, 2, 3) e consumível usado | rodada 13 | abre e regrava compactado (0, 1, 2), mesmas pilhas e fontes |
| 6 | v0.7 com itens, fontes, uma cataplasma usada e **dezenas de pilhas** (45) | v0.7 | igual campo a campo |
| 7 | v0.7 **sem item** | v0.7 | sem os campos novos; igual |
| 8 | itens e fontes coletadas | (casos 4, 5 e 6) | as fontes não entregam de novo; sem duplicação |
| 9 | consumível usado | (casos 5 e 6) | a quantidade usada continua usada |
| 10 | **item desconhecido** (`item.espada_de_cristal`) | cópia alterada | recusado como "jogo mais novo"; arquivo intacto, **sem** `.invalido` |
| 11 | **quantidade inválida** (999 > pilha) | cópia alterada | recusado como inválido; original intacto + cópia em `.invalido` |
| 12 | **formato futuro** (versão 3 do save) | cópia alterada | recusado ("salva por uma versão mais nova"); arquivo intacto, sem `.invalido` |

**Resultados:**
- **Matrizes:** 0 falhas em todas.
- **Testes do jogo** que cobrem o mesmo:
  - `InventoryFlowTest`: salvar e continuar, save sem itens, recusas, aventuras isoladas.
  - `InventoryTest`: o save da rodada 13 com buracos, o teto do leitor.
  - `SaveCompatTest`.
- **Os mundos experimentais RUINAS e 31337 e os `.invalido` históricos do proprietário:** não foram abertos nem tocados.

**Garantia desta publicação:** das versões antigas para a v0.7 (e para as futuras compatíveis).

**Limitação no sentido contrário (P103):** um save da v0.7 aberto no executável da v0.6 abre sem os itens. Esse executável
ignora os campos que não conhece. Se o jogador salvar lá, a mochila e as fontes coletadas se perdem. Como as fontes somem
junto, de volta à v0.7 elas podem ser recolhidas de novo: a perda é recuperável e não duplica nada.

Não há migração reversa nem segundo formato de save; voltar de versão não é caminho suportado.

## 5. Testes e validadores

Revalidados no commit `802483c`, que já tem todo o código final; depois dele só entram documentos:

| Conferência | Resultado |
|---|---|
| Suíte completa (engine 45, viewer 156, game 297) | **498** passam, 0 falhas, 1 pulado (o escritor da referência, intencional) |
| Testes da mochila, itens, recompensas, coleta única, consumo, controles e persistência | `InventoryFlowTest` 24, `InventoryTest` 12, `ItemCatalogTest` 5, `SaveCompatTest`, `DiscoveryTest`: verdes |
| Clone limpo (sem a `.venv` do checkout, `clean build test` + distribuições) | saída 0, assets gerados no clone (inclusive `assets/items/e/`) |
| `javac -Xlint:all` (engine, viewer, game; principal e testes) | **0 avisos** |
| Armas e animações (`--check`) | 129 animações, todas ok |
| Interrupção fina, golpe andando, esquiva, cabelos | ok |
| Oclusão | só a exceção documentada do arco |
| Presets (`tools/review7.sh gate`) | **51 pares, 0 pixel** de diferença |
| Referências canônicas | **22**, equivalentes (< 0,5% dos pixels acima de 8/255); nada regenerado |
| Áudio | 47 arquivos, 0 alerta |
| Links da documentação | 0 quebrados |
| Sementes e saves | §3 e §4 |

Nenhum teste foi removido e nenhum limite de validação foi afrouxado.

## 6. Desempenho

Medido de novo para a publicação, com a máquina mais calma que na 13.1 (carga 3,5–7, navegadores abertos). Os números
estão em `output/visual-review/publicacao-v0.7/desempenho/` e, os da 13.1, em `round-13-1/desempenho/`.

| Medida | Resultado |
|---|---|
| Mochila aberta × fechada em PARES (mesmo processo, mesmo lugar, 18 pares) | **+0,50 ms** de mediana (de −0,26 a +1,80) |
| Andando 7 s (melhor de 2) | fechada 10,26 ms → aberta com a prévia 11,34 ms |
| 283 pilhas, parada | fechada 10,60 → aberta 11,41 → aberta e rolada até o fim 11,42 ms |
| Chamadas de desenho da interface com a mochila aberta | **6** por quadro (eram 88 antes das camadas da 13.1; mesma imagem) |
| Recursos depois de abrir e fechar 20 vezes | 10 FBOs e 146 malhas antes e depois: **nada cresce** |
| Coletas de lixo nas janelas de medição | 0 |
| Mochila fechada (13 × 13.1, na 13.1) | dentro do ruído; mesmos draws e triângulos |
| Entrar no jogo (menu → vila → região), v0.6 × v0.7 | primeiro quadro do menu 3,9 × 3,8 s; "Montando o mundo" 2,2 × 2,2–2,4 s |
| Memória de malhas | +5 malhas (+0,8 MB) depois de entrar numa aventura: os modelos dos itens, que não crescem ao voltar ao menu |
| Atlas dos itens | uma vez por processo, no primeiro quadro da aventura (75–230 ms medidos, sob o esmaecer da entrada) |
| Save com 283 pilhas (28,8 KB) | gravar e validar 9,5 ms de mediana, ler 1,7 ms |

**Observações (sem defeito grave e sem crescimento de recursos):**
- **O caso extremo da 13.1** (283 pilhas roladas até o fim, +8 ms) **não se repetiu**: rolada custa o mesmo que no topo.
  Era a carga da máquina naquela medida.
- **Memória Java:** em uso, sobe com a mochila aberta (45 → 85 → 108 MB em janelas de 8 s, com heap de 248 MB). É lixo de
  vida curta do desenho por quadro: textos, listas e o quadro da prévia. Não houve coleta nas janelas e nada fica
  retido; reduzir isso (reaproveitar o quadro e o estilo da prévia) fica para uma rodada futura, sem urgência.

## 7. Arte

- **Modelos:** os cinco modelos voxel da 13.1 (ervas, cataplasma, pedra, madeira, caderno) estão **aprovados para o
  checkpoint**.
- **Refinamentos futuros:** seguem o [contrato](../art/contrato-icones-de-itens.md); nada foi encomendado agora.
- **Restrições:** nada de IA geradora de imagem e nada de asset novo. O laboratório do artista não foi tocado.

## 8. Limitações aceitas no checkpoint

Ficam registradas, sem implementação nova:

| Problema | Situação na v0.7 |
|---|---|
| P100 — a Vila do Posto e os mundos v1 sem fonte de item | aceita; candidata da Rodada 14 (o drop de fungo), se autorizada |
| P101 — a vida do herói não vai para o save | aceita, baixa |
| P102 — os modelos são a primeira versão artística | aprovados para o checkpoint; refinamento futuro pelo contrato |
| P103 — um executável antigo não preserva os campos novos | aceita e documentada (§4) |
| P104 — materiais sem uso | escopo (crafting e ferraria futuros) |
| P106 — texto longo de descoberta cortado em 720p (anterior à 13) | aceita, baixa |
| P108 — a mochila cobre o lado direito durante a luta | aceita, baixa (por desenho) |
| P109 — a rolagem pode mudar o item sob o cursor | aceita, baixa (o seletor segue o mouse) |
| P110 — os modelos ainda não aparecem no chão nem na mão | escopo (loot no chão: Rodada 14, se autorizada) |

## 9. Git e publicação

**Ponto de partida:** a `main` publicada `a6a18eb` (tag `procedural-world-v0.6`).

**O ramo `feature/inventory-and-loot`:**
- **Rodadas:** os 57 commits das rodadas 13 (36) e 13.1 (21).
- **Publicação:** os commits desta publicação (matriz de saves com a mochila e documentos).
- **Autoria:** todos com autor e committer noreply e mensagens só com título.
- **O que não entra:** trailer, linha de atribuição, arquivo de `output/`, asset gerado (os modelos de `assets/items/` são
  gerados), arquivo acima de 32 KB acrescentado ou dado pessoal.

**PR:** [#10](https://github.com/Jovinull/Valumo/pull/10), "feat: fundação de inventário e mochila viva — rodadas 13 e
13.1".

**CI** (`ci`: build, testes, validadores sem GPU, links; `regras`: autoria e texto do PR):
- verde na ponta final do PR antes do merge;
- verde no push da `main`.

**Merge:** por **fast-forward**. A ponta do ramo é enviada para a `main`; a `main` passa a ter os mesmos SHAs do ramo,
sem merge commit, sem squash, sem force e sem reescrita.

**Tag:** `inventory-foundation-v0.7`, anotada, aponta para o commit publicado: a ponta da `main`, que é o commit que
traz este relatório. As tags anteriores não se mexem.

**Release:** nenhuma.

**Provas:** os SHAs exatos (o da `main` publicada e o da tag) e os links das execuções do CI estão no relatório final
local, `output/visual-review/publicacao-v0.7/relatorio-final.md`. Um commit não pode conter o próprio SHA.
