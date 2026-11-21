# Relatório da Rodada 13 — Inventário, itens e recompensas

**Situação:** ramo LOCAL `feature/inventory-and-loot`, sobre a `main` publicada (`a6a18eb`, tag `procedural-world-v0.6`).

- **Não feito:** sem push, sem PR e sem tag. O checkpoint candidato `inventory-foundation-v0.7` não foi criado.
- **Para publicar:** depois da revisão do pacote e do teste físico.

**Pacote:** `output/visual-review/round-13/`, reproduzível com `tools/review13.sh`. Tem LEIA-ME, relatório, arquitetura,
catálogo, QA funcional e visual, matriz de saves, testes, desempenho, problemas, handoff do artista, roteiro de teste
físico, capturas, dois vídeos e o ZIP sem os vídeos.

## 1. Auditoria de partida

- **Testes:** 457, 1 pulado (o escritor da referência).
- **Vida do herói:** 6 (três corações), no `Combat`; não vai para o save. Ao continuar, volta cheia; R depois de um
  desfecho também a enche.
- **Eventos estáveis já existentes:**
  - o fim da conversa com um morador (`GameLogic.endDialog`);
  - a descoberta de um lugar (`QuestSystem.discover`, uma vez, por id estável `desc.rN.tipo.N`).
- **Onde há o quê:**
  - a Índira mora só nas regiões v2 com o conjunto da herbalista (88 de 200 sementes);
  - a vila fixa e as regiões v1 não têm descobertas.
- **Congelado** (ADR 0029): a v1 e a v2, `descobertas_e.json` (textos e moedas), as falas dos moradores, o perfil da v2 e
  as referências `regioes-v1.txt` e `regioes-v2.txt`.

## 2. A arquitetura

[ADR 0031](../decisions/0031-itens-e-recompensas.md).

- **Pacote:** `vx.game.item`, sem GL.
- **Separação:** definição (catálogo), pilha, mochila, efeito, tabela de recompensas, progresso (mochila + fontes
  coletadas) e regras.
- **Integração:** a `GameLogic` (estado `INVENTORY`, as fontes, o uso), o `SaveGame` (campos opcionais), o `Game` (a tela,
  o cartão do item, o botão da pausa) e o `GameAudio` (eventos aprovados).

## 3. Itens, mochila e fontes

- **Itens:** cinco, em quatro categorias ([itens](../architecture/itens.md)).
- **Mochila:** 24 espaços, tudo ou nada, ordem determinística ([inventário](../architecture/inventario.md)).
- **Fontes:** o presente da Índira e os achados por tipo de lugar ([recompensas](../architecture/recompensas-e-loot.md)).
- **No jogo real:** as cinco fontes de item foram conferidas.
  - **Semente 7:** Índira, casa e árvore.
  - **Semente 23:** sítio.
  - **Semente 54:** abóbada.
  - **Semente 2:** o mirante, que não dá item.
  - **Nenhuma duplica:** cada fonte entrega uma vez e não entrega de novo.

## 4. Interface

- **Estilo:** a mesma família do diário e da pausa (madeira, pergaminho, P1 só na seleção), com ícones de pixel 14 × 14
  no idioma do coração do HUD.
- **Resoluções oficiais:** 1280×720, 1920×1080, 1680×1050 e 2560×1080, todas legíveis.
- **Problemas achados nas capturas e corrigidos:**
  - o glifo "×" que a fonte não tem;
  - a linha de quantidade cortada;
  - a pausa que encolhia os rótulos em 720p;
  - a cruz da cataplasma.

## 5. Desempenho

Medido com `tools/review13.sh desempenho` (detalhes em `round-13/desempenho/resumo.md`):

- **Condições:** 1080p, janela oculta, mudo, `glFinish` por quadro e GPU por passo.
- **Binários:** a tag `procedural-world-v0.6` × a rodada 13, intercalados na mesma sessão, 3 repetições (mediana).

| Cenário | v0.6 | 13 |
|---|---|---|
| Vila fixa: quadro mediana / p95 | 9,78 / 12,48 ms | 9,87 / 11,64 ms |
| Vila fixa: lógica, GPU soma | 0,27 / 7,20 ms | 0,27 / 7,12 ms |
| Região s7, mochila fechada: quadro mediana / p95 | 9,06 / 9,92 ms | 9,23 / 10,19 ms |
| Região s7: lógica, GPU soma | 0,35 / 6,30 ms | 0,36 / 6,53 ms |
| Draws, triângulos, malhas (vila; região) | 114, 492 840, 206 (106,1 MB); 96, 367 210, 141 (81,4 MB) | iguais |
| Coletas de lixo na medição | 0 | 0 |
| Menu: malhas e MB nos 6 pontos do roteiro de memória | 206 → 300 (106,1 → 180,0 MB) | iguais |
| Menu: primeiro quadro; gerar a região 1024; montar o mundo | 3169; 297; 2133 ms | 3207; 292; 2136 ms |

**Mochila fechada:** não tem custo relevante.

- **O que mede:** as diferenças ficam na casa de 0,1–0,2 ms de mediana, para os dois lados, dentro da variação entre
  repetições.
- **O que não muda:** a mesma cena, os mesmos draws e as mesmas malhas.
- **Por que vale:** estas linhas foram medidas antes das duas últimas mudanças do binário (ícones em faixas e o relatório
  de desempenho). Nenhuma das duas toca o desenho com a mochila fechada.

**Mochila aberta e cheia**, no mesmo lugar, parado, pelo mesmo tempo da fechada:

- **Quadro:** 8,39 ms fechada → 11,04 ms aberta.
- **Lógica:** parada (0,16 ms).
- **Coletas:** 0. A memória em uso sobe um pouco mais que fechada (~30 MB × ~24 MB em ~10 s), sem coleta.
- **Ícones em faixas:** juntar os pixels vizinhos num retângulo só baixou a mochila aberta de 12,32 para 11,06 ms (A/B
  intercalado), com a captura idêntica pixel a pixel em 1080p e 720p.

**Save com a mochila cheia:** 3838 bytes.

- **Gravar e validar:** mediana ~2,5 ms; o pior caso fica em ~31–35 ms.
- **Ler:** mediana 0,35 ms.

## 6. As 30 respostas

1. **Arquitetura:** uma camada de jogo SOBRE os mundos, em `vx.game.item`, sem GL.
   - **Dados:** itens e recompensas (`itens_e.json`, `recompensas_e.json`).
   - **Código:** os efeitos, escolhidos por nome (`ItemEffects`).
   - **Progresso:** a mochila e as fontes coletadas, num objeto só (`ItemProgress`), salvo no mesmo arquivo atômico da
     aventura.
   - **Fontes:** reagem só a eventos estáveis que já existiam (fim de conversa, descoberta). O gerador não mudou
     ([ADR 0031](../decisions/0031-itens-e-recompensas.md)).
2. **Classes novas (10):** `ItemDefinition`, `ItemUse`, `ItemCatalog`, `ItemStack`, `Inventory`, `ItemEffects`,
   `ItemIcons`, `LootTable`, `ItemProgress`, `ItemRules`. Testes novos: `ItemCatalogTest`, `InventoryTest`,
   `InventoryFlowTest`.
3. **Arquivos modificados:**
   - **Código (4):** `GameLogic.java` (estado `INVENTORY`, fontes, uso, retrato do `unsaved`), `SaveGame.java` (campos
     opcionais, `readItems`), `Game.java` (tela, cartão do item, botão da pausa, comandos de roteiro) e `GameAudio.java`
     (eventos).
   - **Documentação:** `save.md`, `jogo.md`, os índices, o `README.md`, o `CLAUDE.md`, os problemas conhecidos e cinco
     skills.
   - **Intactos:** nenhum arquivo do gerador, das regiões, das descobertas, dos moradores, da colisão ou do combate mudou
     (`git diff procedural-world-v0.6` vazio nesses caminhos).
4. **Itens:** cinco. Ervas da trilha, cataplasma de ervas, fragmento de pedra antiga, madeira envelhecida e registro de
   viagem.
5. **Categorias:** quatro. Material, achado, consumível e curiosidade (declaradas no catálogo; nenhuma tem regra de
   código).
6. **Espaços:** 24 (grade 6 × 4).
7. **Empilhamento:** cada item tem a sua `pilha` (1 a 99; hoje 20, 5, 10, 20 e 1).
   - **Ordem de entrada:** primeiro completa as pilhas do mesmo item, na ordem dos espaços; o resto vai para os espaços
     vazios, em ordem. A mesma sequência de coletas dá sempre a mesma mochila.
   - **Tirar:** esvazia o espaço sem mover os outros.
8. **Consumível:** a cataplasma devolve 2 de vida (um coração), sem passar do máximo.
   - **Quando vale:** só com o herói de pé, ferido e FORA da luta engajada.
   - **Quando não vale:** o botão mostra o motivo antes e o item não é gasto.
   - **Pausa:** não usa item.
   - **Combate intacto:** a vida máxima e o dano dos inimigos não mudaram.
9. **Fontes reais:**
   - o presente da Índira (2 ervas + 2 cataplasmas) ao terminar a conversa;
   - os achados por tipo de lugar, junto com a descoberta: casa → registro, abóbada → 2 madeiras, sítio → fragmento,
     árvore → 3 ervas. O mirante da torre não dá item.
10. **Validação da coleta:**
    - **A fonte existe:** a tabela, o morador e o tipo de lugar são conferidos pelo `ItemCatalogTest`, e a região tem a
      descoberta.
    - **Entrega:** só no passo lógico, depois do evento.
    - **Leitura do save:** confere cada fonte contra a tabela e a região (`ItemRules.fit`).
11. **Duplicação:** cada fonte tem um id estável (`presente:indira`, `achado:<id da descoberta>`), que vai para o save
    junto com a mochila.
    - **Marcação:** `ItemProgress.grant` marca a fonte se e somente se tudo entrou.
    - **Repetições:** apertar E, conversar de novo, recomeçar o confronto ou carregar o save não entregam de novo
      (testes e vídeo).
12. **Mochila cheia:** nada entra, o cartão diz "Mochila cheia" e a fonte continua disponível. Com espaço, conversar ou
    usar o lugar de novo recolhe tudo de uma vez. A descoberta (texto e moedas) acontece normalmente.
13. **Onde se salva:** no `aventura.json` da aventura, a pasta do par `mundos/<mundo>/aventuras/<personagem>/` (ou o save
    da vila/região), em `progresso.inventario` (espaço, id e quantidade de cada espaço ocupado) e
    `progresso.fontesColetadas`.
14. **O formato mudou?** Não. Os dois campos são OPCIONAIS no bloco `progresso`, e as versões continuam v1 (vila) e v2
    (procedural).
    - **Sem itens:** os campos não são escritos. Medido: o arquivo sai igual ao da v0.6, só com outro id aleatório da
      aventura.
15. **Saves antigos:** abrem.
    - **Matriz:** os 8 saves criados pela v0.6 abrem iguais nos dois binários, sem `.invalido` e sem exceção.
    - **Vídeo 02:** um mundo e a vila criados pela v0.6 abrem com a mochila vazia e o resto preservado.
    - **Descobertas antigas:** não entregam nada ao carregar; o achado sai ao usar o lugar de novo, uma vez (regra
      explícita e testada).
16. **Outro personagem:** sim, a aventura continua isolada. Outro personagem no mesmo mundo tem mochila e presente
    próprios (`cadaAventuraTemAMochilaDela`).
17. **Sementes v1:** sim. As 300 sementes saem iguais à base.
18. **Sementes v2:** sim. As 200 sementes saem iguais às da publicação da v0.6, e iguais entre dois processos.
19. **Referências das regiões:** sim. `regioes-v1.txt` e `regioes-v2.txt` não mudaram desde a tag (`RegionGoldenTest`
    verde).
20. **Presets:** sim. Os 51 pares saem com 0 pixel de diferença (`tools/review7.sh gate`).
21. **Referências:** sim. As 22 referências saem equivalentes (menos de 0,5% dos pixels acima de 8/255), e os validadores
    de armas, interrupções, golpe andando, esquiva e cabelos passam.
22. **Testes:** 489 passaram, 0 falhas, 1 pulado (o de sempre). Antes eram 457; são 32 novos: `ItemCatalogTest` 5,
    `InventoryTest` 10, `InventoryFlowTest` 17.
23. **Clone limpo:** funciona. `git clone`, `.venv` nova a partir de `tools/requirements.txt`, PATH sem a `.venv` do
    checkout, testes e instalação: tudo passa. `-Xlint:all` sem nenhum aviso nos três módulos.
24. **Desempenho:** com a mochila fechada, sem custo relevante. A vila sai em 9,78 → 9,87 ms, a região em 9,06 → 9,23 ms,
    com a mesma cena, os mesmos draws, as mesmas malhas e 0 coletas. O menu tem as mesmas malhas e a mesma memória.
    - **Aberta e cheia:** +2,7 ms de desenho (11,0 ms no total) com a lógica parada, depois de juntar os ícones em faixas
      (antes, +3,6 ms).
    - **Save cheio:** gravar e validar ~2,5 ms; ler 0,35 ms (§5).
25. **Resoluções:** sim, nas quatro oficiais (1280×720, 1920×1080, 1680×1050, 2560×1080). Em 720p o texto pequeno é o
    mínimo legível. A pausa mantém a escala de rótulos da v0.6 (`capturas/pausa/`).
26. **O que depende do Claude artista:** a arte final dos cinco ícones (e, se o proprietário pedir, modelos voxel dos
    itens), pelo [contrato](../art/contrato-icones-de-itens.md).
    - **Hoje:** os ícones são a primeira versão em pixel, funcional e não aprovada como arte final.
    - **O clone do artista:** não foi tocado.
27. **Limitações:**
    - P100: a vila fixa e as regiões v1 não têm fonte de item;
    - P101: a vida não vai para o save;
    - P102: ícones provisórios;
    - P103: voltar para a v0.6 perde os itens se salvar lá (perda recuperável, sem duplicação);
    - P104: materiais ainda sem uso;
    - P106: anterior à rodada, o texto longo de descoberta corta em 720p;
    - os sons da mochila foram medidos, não ouvidos.
28. **Teste físico:** o roteiro está em `output/visual-review/round-13/roteiro-de-teste-fisico.md`.
    - **Mundo novo com a semente 7:** a mochila com mouse e teclado, o presente, os achados, uma luta ferida e a
      cataplasma, salvar, voltar e continuar.
    - **Outras sementes:** 23 e 54 (sítio e abóbada).
    - **Saves de antes:** abrir para ver a mochila vazia.
    - **Opinião pedida:** sobre o visual e sobre o equilíbrio da cura.
29. **Pronto para equipamentos?** A base sim; o equipamento em si não. O que já serve:
    - item por dados com id estável e categoria livre;
    - efeitos por nome;
    - pilha 1 (peça única);
    - a mochila transacional e os campos opcionais no save.

    O que falta, de propósito:
    - **Espaços equipados:** um campo opcional novo, como `progresso.equipado`.
    - **Ligação com o personagem:** a arma visível já vem da aparência e do rig, e trocar a espada exige validar o golpe
      com a arma nova (skill `weapon-animation`).
    - **Números de atributo:** não existem no combate de hoje.
30. **Recomendação para a Rodada 14:** o drop do fungo-cardo derrotado.
    - **Como:** pela identidade estável do spawn (a mesma da contagem da missão), uma vez por inimigo, sem entregar de
      novo ao recomeçar o confronto e recuperável até ser recolhido.
    - **Por quê:**
      - cumpre o "derrotar uma criatura e encontrar algo";
      - dá fonte à vila fixa e às regiões v1 (P100) sem mudar a geração;
      - usa a mesma camada (`recompensas_e.json → inimigos`).
    - **Junto, se couber:** decidir se a vida vai para o save (P101) e encomendar os ícones ao artista (P102).
