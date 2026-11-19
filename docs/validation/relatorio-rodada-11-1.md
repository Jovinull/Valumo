# Relatório da Rodada 11.1 — a porta de entrada do RPG

**Situação:** entregue para revisão no ramo local `rodada-11/mundo-procedural`, por cima da Rodada 11 (também sem
commit; todo o trabalho dela preservado). **Sem commit e sem push.** `main` (`8fd6756`) e as tags não foram tocadas;
nenhuma Release; o Titã não foi usado; nenhum asset novo (o logotipo é texto na fonte própria; a música é uma faixa já
aprovada).

**Pacote local:** `output/visual-review/round-11-1/` (capturas, resoluções, vídeo, desempenho, registros, ZIP; os MP4
ficam fora do ZIP). Reproduzir: `tools/review111.sh`.

**Arquitetura e decisões:**
- [menus.md](../architecture/menus.md), [perfis-de-personagem.md](../architecture/perfis-de-personagem.md),
  [catalogo-de-mundos.md](../architecture/catalogo-de-mundos.md);
- ADRs [0027](../decisions/0027-porta-de-entrada-e-sessoes.md) e [0028](../decisions/0028-personagens-mundos-e-aventuras.md);
- teste físico: [roteiro-de-teste-fisico-menu.md](roteiro-de-teste-fisico-menu.md).

## O que foi feito

1. **Menu principal**, aberto direto ao iniciar:
   - logotipo "VALUMO" na fonte de pixel própria;
   - fundo 3D da vila fixa, com câmera cinematográfica em 5 tomadas validadas na colisão real;
   - botões Jogar, Configurações e Sair; mouse e teclado; música `vila_b`.
2. **Personagens** (`valumo.personagem`):
   - lista com retratos reais (cache do compositor);
   - o herói de verdade na vila, girado pelo mouse;
   - **Novo personagem** e **Editar aparência** pelo criador aprovado, na mesma janela, com **Cancelar**;
   - as aparências soltas de antes ficam no **Carregar** do criador.
3. **Mundos** (`valumo.mundo`):
   - lista com prévia, semente, último uso e progresso do personagem escolhido; um resumo real de cada região;
   - **Criar mundo** com nome e semente digitados no jogo (vazia = sorteada e mostrada), "Gerar outra seed" e "Ver
     prévia" real;
   - falha amigável, sem nada parcial no disco.
4. **Aventura = personagem × mundo.**
   - Uma pasta por par, nos formatos que já existiam: v1 na vila, v2 no mundo gerado. **Sem save v3.**
   - **Continuar aventura** ou **Iniciar aventura**.
   - Os jogos salvos de antes (rodadas 10 e 11) adotados sozinhos, uma vez, por cópia validada e registrada.
5. **Sessões trocáveis**, com transições em passos (rótulo real, sem porcentagem).
   - A vila do menu continua montada quando a aventura é na vila.
   - Na pausa, **Menu principal** e **Sair do jogo** pedem confirmação: salvar e sair, sair sem salvar ou cancelar.
6. **Configurações:** volume geral (novo), música, efeitos, sensibilidade, inverter vertical, barras dos inimigos;
   gravadas nas preferências de sempre.
7. **Testes e QA:**
   - 4 classes novas (24 testes): `MenuStoresTest`, `MenuFlowTest`, `MenuAdventureTest`, `ApresentacaoTest`; um teste
     antigo saiu com o recurso que testava (a dica de controles com F1);
   - 8 roteiros em `tools/menu/` e `tools/review111.sh`, com dados temporários e conferência por hash dos dados do
     proprietário.

## Defeitos achados e corrigidos na rodada

| # | Defeito | Como apareceu | Correção |
|---|---|---|---|
| 1 | Tentativas recusadas da geração deixavam a grade de relevo no cache estático (cresceria a cada mundo criado ou prévia) | leitura do `Terrain` ao planejar a medição de transições | `Terrain.release` da tentativa recusada; resultado das sementes igual (`ProceduralWorldTest`, roteiros da 11) |
| 2 | Tomadas iniciais atravessavam o posto da trilha, a copa do salgueiro e um poste | `ApresentacaoTest` (183 falhas na 1ª versão) | alvos e rumos refeitos com sonda na colisão real; 0 falhas |
| 3 | Vitrine dos personagens no ponto de início da vila: um bloco de relevo enorme cobria a frente | captura | ponto próprio da vitrine (`apresentacao_e.json → vitrine`) com teste de vista livre |
| 4 | Morador colado atrás do herói na vitrine | captura | moradores colados ou atrás do herói (vistos do olho) não são desenhados nessa tela |
| 4b | Mesmo assim, uma moradora DECORATIVA da cena (Índira, desenhada pelo `World`, não pela lógica) ficava a 1 u do herói | comando de roteiro `vitrine` (lista o que se esconde) | vitrine no ponto de início da vila (0,6; 4,4), rumo 345° (330° testado e pior); só a Súvia aparece, ao longe |
| 5 | Aviso do menu cobria a plaqueta do nome | captura | avisos no topo |
| 6 | O clique no campo da semente caía num cartão da lista por baixo do formulário | roteiro C (o nome virou "Recusado4242") | o modal limpa os botões de baixo |
| 7 | Dica de teclas do rodapé cobria os botões do painel de mundos | captura | dica removida onde os botões já dizem a tecla |
| 8 | Mensagem de erro em negrito quebrada pela medida da fonte regular invadia a prévia | captura | mesma fonte na medida e no desenho |
| 9 | O criador não tinha como desistir (só concluir ou fechar a janela) | leitura (pedido: "Cancelar descarta") | `cancelLabel`/`cancelled` só quando aberto pelo menu; o criador de sempre não muda (gate 51/0) |
| 10 | "Sair do jogo" na pausa saía sem salvar | leitura | confirmação na aventura do menu |
| 11 | Roteiros: `click` procurava botões do último quadro desenhado (o `wait` não desenha) | roteiro C | `click` desenha um quadro antes (o desenho só lê a lógica; roteiros antigos iguais) |
| 12 | Prévia da vila sumia da lista depois de voltar de um mundo gerado | roteiro D | refeita do cenário do menu sempre que falta |
| 13 | Nome importado "aventureiro-001 (importado)" | captura | a importação de aparências saiu (14) |
| 14 | **Primeira revisão do proprietário:** dicas de tecla na tela ("↑ ↓ escolher · Enter confirmar · mouse", "(Esc)", "(Enter)", "(E)"…), botão "Trazer aparências", textos de desenvolvimento ("Trazer o jogo salvo da vila (rodada 10)", "legado", "gerador v1", "Protótipo jogável · rodada 11.1"), explicação da semente | o proprietário | tudo isso saiu de todas as telas (menu e jogo); a dica de controles do jogo e o F1 saíram junto; as teclas continuam funcionando |
| 15 | **Trava ao abrir o criador** (Novo personagem / Editar) | o proprietário; medido: **3,1 s** com a janela congelada, a cada abertura | o `App` do criador montava a cena dele (`App.world`, preguiçoso) no PRIMEIRO QUADRO, e o `App` era recriado a cada abertura. Agora o `App` vive enquanto o jogo vive e a cena é montada num passo de carregamento ("Abrindo o criador"): 3,1 s cobertos na primeira vez, **50–55 ms** depois. O `Creator.close` passou a esquecer todos os modelos que injetou no `App` (antes só o último) |
| 16 | Descrição do mundo era texto meu ("A vila fixa da rodada 10 (legado)…"; "Semente · gerador valumo.regiao v1") | o proprietário | resumo tirado dos FATOS da região (`ProceduralScene.summary`, gravado no `mundo.json`): moradores, rumo da trilha, lago ou colina; a vila tem a descrição do lugar |
| 17 | O jogo salvo de antes dependia de um botão "Trazer…" | o proprietário | adotado sozinho, uma vez, ao abrir o menu (`Game.adoptOldSaves`): vira a aventura de um personagem com a aparência gravada nele; cópia validada; nunca duas vezes; recusado fica registrado |

## Respostas

1. **Como ficou o menu principal?**
   - **Leiaute:** coluna à esquerda sobre um véu escuro de leitura. Em cima, o logotipo **VALUMO** e a linha "Um RPG de
     aventura em voxel"; abaixo, três botões grandes (Jogar em verde, Configurações e Sair em madeira).
   - **Foco:** a semente verde P1 marca o botão focado (mouse ou ↑ ↓).
   - **Sem texto de teclas nem rodapé** (decisão do proprietário).
   - **Fundo:** a vila em movimento.
   - **Abertura:** o jogo abre nele (`./gradlew jogo`).
   - **Capturas:** `capturas/01-menu-a.png` a `07-*`.
2. **Quais leiautes foram comparados?**
   - A (lateral) e B (central: logotipo no centro, botões no centro).
   - Mesmas tomadas, em 1280×720, 1920×1080, 1680×1050 (16:10) e 2560×1080 (21:9):
     `resolucoes/comparacao-leiautes-a-b.png`.
3. **Qual foi escolhido e por quê?** **A.**
   - **Leitura:** o véu lateral garante leitura sobre qualquer tomada.
   - **Composição:** a paisagem fica livre do centro para a direita, de modo que a vila é vista e não coberta.
   - **Defeitos do B:** o logotipo briga com as copas e os botões tapam o centro da vila (a estalagem e o caminho).
   - **B fora do jogo:** só existe para comparar (`-Dvx.menu.layout=B`); não há dois menus oficiais.
4. **Como funciona o fundo 3D?** É a vila fixa numa sessão própria em estado `MENU` (ADR 0027).
   - **O que não roda:** nada de jogo. Sem IA, combate, missão ou save (`saveDir = null`).
   - **O que aparece:**
     - moradores em idle;
     - o marco aceso;
     - nenhum inimigo nem protótipo.
   - **Câmera:** uma apresentação em laço (`Cinematic`) com tomadas de `apresentacao_e.json`.
     - **Movimento:** suavizado nas pontas.
     - **Troca:** escurece 0,6 s; a volta do laço acontece no escuro.
     - **Validação:** `ApresentacaoTest` confere 61 instantes por tomada contra a colisão real: olho fora de tudo, a mais
       de 0,5 u de qualquer forma e a mais de 1,6 u de um morador, e linha de visão livre.
     - **Sem abertura forçada:** os botões respondem desde o primeiro quadro.
5. **Que cenas são apresentadas?** Cinco tomadas, 45 s no laço:
   - panorama da vila;
   - estalagem e moradores;
   - margem do lago;
   - a trilha do sul;
   - o marco do posto.

   Guia para acrescentar: [como-adicionar-uma-tomada.md](../processo/como-adicionar-uma-tomada.md).
6. **Como funciona a seleção de personagens?**
   - **Lista à esquerda:** cartões com retrato real, nome, modelo de origem e data ("Aventureiro · criado em…") e
     "última aventura em…" (o personagem do jogo salvo de antes: a data do save).
     O escolhido fica em dourado.
   - **Direita:** o herói de verdade na vila, em idle. Arrastar com o mouse gira o desenho.
   - **Navegação:** ↑ ↓ e a roda andam na lista; Enter escolhe; Esc volta. A escolha é guardada por id.
7. **Como funciona a criação e edição de personagens?**
   - **Abrir:** **Novo personagem** e **Editar aparência** abrem o criador aprovado na mesma janela, com a aparência
     certa.
   - **Concluir** (Pronto → Criar/Salvar personagem): grava o perfil e volta à lista com ele escolhido.
     - **Criar:** id novo.
     - **Editar:** o mesmo arquivo e o mesmo id (nome e aparência); nenhum outro perfil muda um byte (teste).
   - **Cancelar** (botão no canto ou Esc): não grava nada. Esse botão só existe quando o criador é aberto pelo menu.
8. **Como os perfis são armazenados?** `output/personagens/p-<12 hex>.json`.
   - **Conteúdo:** contrato `valumo.personagem` v1 com id, nome, origem, datas e a aparência no contrato do compositor.
   - **Gravação:** escrita atômica relida e validada.
   - **Arquivo ruim:** fica de fora com o motivo.
   - **Nome:** nunca é caminho.
   - **Aparências soltas de antes** (`output/aparencias/`): não viram personagens; ficam no **Carregar** do criador.
9. **Como funciona a seleção de mundos?**
   - **Lista à esquerda:** prévia 16:9, nome, "Semente N" (a vila: "Feito à mão") e o progresso do personagem escolhido
     ("12 min de jogo · missão concluída · 30 moedas"), ou criado/visitado em.
   - **Painel à direita:** prévia grande, o **resumo da região** (dos fatos da geração: "Uma estalagem com 3 moradores. A
     trilha segue para o norte até um posto. Uma colina se ergue ao lado."; a vila: "A vila da Kelyra: a estalagem e
     seus moradores, um lago a leste e a trilha do posto ao sul."), semente e datas, a situação da aventura do
     personagem e **Continuar aventura** / **Iniciar aventura**.
   - **Teclas** (não escritas na tela): N cria mundo; Esc volta aos personagens.
10. **Como o usuário informa uma seed?** Em **Criar mundo**.
    - **Campo:** clique (ou Tab), digite; só algarismos, até 20; Backspace apaga.
    - **Faixa:** 0 a 18446744073709551615 (64 bits sem sinal). Fora disso: mensagem no formulário.
    - **Teclas de jogo:** não valem enquanto se digita, nem o M de mudo.
11. **Como funciona a geração de seed aleatória?**
    - **Quando:** campo vazio ao criar (ou ao pedir prévia), ou o botão "Gerar outra seed".
    - **Como:** sorteia **uma vez** (1 a 999 999 999, `SecureRandom`) e escreve no campo antes de criar.
    - **Depois:** a semente final fica visível e gravada no `mundo.json`.
12. **Como funciona a prévia real?** É a mesma região que a aventura usa, desenhada pelo motor:
    1. `ProceduralScene.create`, com as mesmas tentativas e recusas.
    2. `World` temporário.
    3. Vista geral entre o assentamento e o posto, num FBO de 640×360.
    4. `previa.png` gravado com troca atômica.
    5. Mundo, relevo e FBO fechados.

    - **Chave de cache** (`<semente>.g<gerador>.v<prévia>`):
      - fica no `mundo.json`;
      - arquivo sumido, chave diferente ou sem resumo → três sementes acendendo no lugar (sem texto), refeita quando o
        mundo é selecionado.
    - **Formulário:** "Ver prévia" desenha sem gravar.
    - **Vila:** a prévia vem do próprio cenário do menu.
13. **Como os mundos são identificados?**
    - **Id:** `m-<12 hex>`, independente da semente. A pasta `output/mundos/<id>/` tem o `mundo.json` e a prévia.
    - **Mesma semente:** pode estar em dois mundos (testado e capturado: "Vale das Brumas" e "Gemeo", ambos 1024).
    - **Vila fixa:** o mundo `vila`.
14. **Como o progresso é separado por personagem e mundo?**
    - **Pasta do par:** a aventura é `mundos/<mundo>/aventuras/<personagem>/aventura.json`.
    - **Isolamento:** outro personagem, ou outro mundo (mesmo com a mesma semente), é outra pasta. Nada sobrescreve nada
      (`MenuStoresTest`, `MenuAdventureTest`, roteiro D).
    - **Aparência:** ao continuar, vale a do personagem.
15. **Como os saves v1 e v2 foram preservados?**
    - **Formatos e leitores:** os mesmos.
    - **Arquivos antigos** (`output/saves/aventura.json`, `output/saves-procedural/s<N>/`):
      - nunca são movidos nem regravados;
      - são **adotados sozinhos, uma vez**, ao abrir o menu: cada um vira a aventura de um personagem com a aparência
        gravada nele (na vila, ou no mundo da semente). A cópia é validada com o mesmo leitor do Continuar (v1 só na
        vila, v2 só na região da mesma semente), movida de forma atômica, e o sha256 vai para `importacoes.json`;
      - o mesmo arquivo nunca entra duas vezes nem é tentado de novo (a recompensa não duplica); o inválido fica
        registrado como recusado, com o motivo;
      - nunca entram por cima de uma aventura existente.
    - **Linha de comando antiga:** `-Dvx.mundo=procedural -Dvx.semente=N` continua.
    - **Conferência:** o hash dos seus arquivos foi conferido antes e depois de todo o QA: intactos.
16. **Houve necessidade de novo formato de save?** **Não.** A identidade do par é a pasta; o v2 já confere semente,
    gerador e vínculos. Formatos novos só para o catálogo (`valumo.personagem` e `valumo.mundo`), que não são saves de
    jogo (ADR 0028).
17. **Como funcionam as configurações?**
    - **Som:** Volume geral (novo, `AudioPrefs.geral`), Música, Efeitos.
    - **Controles:** Sensibilidade do mouse (agora gravada em `Prefs`), Inverter vertical (gravada) e Barras dos
      inimigos.
    - **Gravação:** imediata, nas preferências de sempre. Valem no menu e na aventura; a pausa usa os mesmos valores.
    - **Fora:** qualidade gráfica e resolução/modo de janela não existem no jogo hoje como opções seguras. Nada foi
      inventado (P60).
18. **Quais problemas visuais foram encontrados?** Os itens 2–8, 12–14 e 16 da tabela acima.

    Em aberto e aceitos para a sua avaliação:
    - **Botões:** o tamanho do texto pode variar entre botões vizinhos (o maior passo inteiro da fonte que cabe; o mesmo
      comportamento da pausa da rodada 10).
    - **Estalagem:** um morador perto da borda inferior por uma fração da tomada.
    - **Prévia gerada:** é uma vista de cima, com muita mata; reconhece-se o mundo pelo traçado e pela estalagem.
19. **Quais bugs foram corrigidos?**
    - **Vazamento:** da grade de relevo nas tentativas recusadas (1).
    - **Criador:** sem cancelar (9).
    - **Saída sem salvar:** "Sair do jogo" sem confirmação (10).
    - **Interface:** clique atravessando o modal (6).
    - **Roteiros:** `click` com botões velhos (11).
    - **Prévia da vila** sumindo (12).
    - **Trava de 3,1 s ao abrir o criador**, em toda abertura (15).
    - **Persistência:** a sensibilidade e o inverter vertical da pausa nunca eram gravados; agora vão para `Prefs`.
20. **Quantos testes passam?**
    - **Total:** **362 de 362** (eram 339): `MenuStoresTest` 11, `MenuFlowTest` 4, `MenuAdventureTest` 5,
      `ApresentacaoTest` 4 (+24). Saiu 1 (`GameplayTest`: a dica de controles com F1, recurso removido a pedido).
    - **Validadores:**
      - `--check` 129/129, `interrupcao-fina`, `golpe-andando`, `esquiva` e `--check-cabelos` sem problema;
      - gate de personalização 51/0;
      - referências 22/22 com desvio 0;
      - links 0 quebrados;
      - áudio 47 arquivos sem alerta.
21. **Como ficou o desempenho?** 1080p, Iris Xe, com glFinish; três execuções no mesmo binário:

    | Medida | Valor |
    |---|---|
    | Menu principal (mediana) | 10,6–11,0 ms (p99 13,6–14,5 ms) |
    | Personagens (mediana) | 9,0–10,4 ms |
    | Mundos (mediana) | 9,6–9,8 ms |
    | Vila sem menu (referência, mediana) | 9,65 ms |
    | Quadros acima de 20 ms | 1 de 2 520 (20,8 ms, na tela de personagens) |
    | Primeiro quadro do menu | 3,45–3,53 s depois do início do processo (2,3 s são a montagem da vila, como antes) |

    - **Custo do menu:** ~1 ms acima da vila sem menu. As tomadas abertas põem a vila inteira no quadro, mais a interface.
      Não é regressão do jogo: a aventura é a mesma sessão de antes.
    - **Gerar e conferir uma região:** ~0,1 s.
    - **Montar o mundo ou a prévia:** ~1,6–1,9 s, na tela de carregamento com o passo real (P59).
    - **Entrar na vila e voltar:** sem remontar (o cenário é o mesmo).
    - **Voltar de um mundo gerado:** ~1,45 s.
    - **24 transições alternando vila e mundo gerado:** malhas (229), texturas (6), FBOs (9) e grades de relevo (1)
      **constantes**, e heap pós-GC entre 11,3 e 11,6 MB. Com som por loopback, os buffers (43) e fluxos (4) do OpenAL
      também ficam constantes (`desempenho/transicoes-recursos*.csv`).

22. **Quais regressões foram verificadas?**
    - **Testes:** os 339 antigos.
    - **Validadores, gate e referências:** todos os acima.
    - **Roteiros antigos** sem menu:
      - a aventura completa e o continuar da rodada 10;
      - aparência + criador da rodada 08;
      - a aventura procedural da rodada 11 na semente 1024.
    - **Criador de sempre:** sem o botão Cancelar.
    - **Determinismo:** as sementes da 11 continuam reproduzíveis (`ProceduralWorldTest`, mesmas impressões digitais).
    - **Dados do proprietário:** intactos (hash).
23. **O que ainda precisa de teste físico?** Tudo que é mão e ouvido ([roteiro de 15 min](roteiro-de-teste-fisico-menu.md)):
    - **Mouse:** hover, clique, roda e arrastar na vitrine.
    - **Foco:** do campo de texto com teclado real; acentos e teclas mortas na digitação do nome.
    - **Ritmo e conforto:** das tomadas e das transições.
    - **Música:** `vila_b` no menu e a passagem para `vila_a`.
    - **Volume geral:** com alto-falante real.
    - **Fechar pelo X da janela** durante a aventura (sai sem perguntar, P58).
    - **Uma tela HiDPI.**
    - **O gosto:** logotipo, leiaute e tomadas. A aprovação artística é sua.
24. **Quais melhorias artísticas são recomendadas para o menu?**
    - **Logotipo:** mais caráter, com letras de tamanhos diferentes ou uma letra ornamentada desenhada à mão em voxel.
      Hoje é a fonte de pixel com profundidade.
    - **Música de abertura:** um tema próprio, curto (asset novo: só com a sua aprovação).
    - **Palco da vitrine:** próprio (praça com luz de fim de tarde), em vez da vila de jogo com moradores escondidos.
    - **Hora do dia:** variar entre as tomadas, se o renderer ganhar presets por tomada.
    - **Botões:** tamanho de texto fixo por linha.
    - **Prévia dos mundos gerados:** mais baixa e inclinada, com a estalagem em primeiro plano.
25. **Como executar o jogo sem parâmetros Gradle?**
    - **Gradle:** `./gradlew jogo`, sem nenhum `-D`, abre o menu.
    - **Binário:** depois de `./gradlew :game:installDist`, `game/build/install/game/bin/game` também abre o menu, sem
      Gradle.
    - **Dados:** os seus ficam em `output/personagens`, `output/mundos`, `output/preferencias*.json`.
26. **Como reproduzir o menu e suas telas para QA?**
    - **Pacote completo:** `tools/review111.sh` (ou uma parte: `capturas`, `resolucoes`, `video`, `desempenho`,
      `transicoes`, `testes`, `validadores`, `regressao`, `zip`).
      - **Dados:** cria pastas temporárias com cópias dos seus dados antigos e roda os roteiros de `tools/menu/` em
        sequência: 01 menu, 02 personagens, 03 mundos, 04 aventura, 05 desempenho, 06 transições, 07 resoluções, 08
        vídeo.
      - **Verificação:** confere os hashes dos seus arquivos.
    - **Um roteiro sozinho:** `JAVA_OPTS="-Dvx.menu=sim -Dvx.personagens=T/p -Dvx.mundos=T/m -Dvx.savegame=T/s
      -Dvx.saves=T/a -Dvx.prefs=T/prefs.json -Dvx.prefs.audio=T/audio.json" game/build/install/game/bin/game --roteiro
      tools/menu/02-personagens.txt SAIDA 30`.
    - **Falha de geração simulada:** `-Dvx.qa.recusar=SEMENTE`.
    - **Leiaute B:** `-Dvx.menu.layout=B`.

## Portões (§25)

| # | Portão | Situação |
|---|---|---|
| 1–3 | abre no menu, logotipo, fundo 3D | feito (capturas 01–07) |
| 4 | câmera não atravessa geometria | `ApresentacaoTest` (61 instantes × 5 tomadas + vitrine) |
| 5 | fundo não altera saves | `saveDir = null` na sessão do menu; hash dos dados do proprietário |
| 6–10 | personagens: abrir, listar, criar, editar sem duplicar, seguir para mundos | roteiro B, `MenuStoresTest`, `MenuFlowTest` |
| 11–17 | mundos: listar, nome, semente, vazia sorteada, visível e gravada, prévia da região, falha sem save parcial | roteiro C, `MenuStoresTest`, `MenuFlowTest` |
| 18–20 | vários personagens e mundos, saves isolados, v1/v2 acessíveis | roteiro D, `MenuAdventureTest`, `MenuStoresTest` |
| 21–23 | entrar, salvar e continuar, voltar sem perder recursos nem progresso | roteiro D; 24 transições sem crescer recursos |
| 24–26 | configurações, mouse e teclado, resoluções | roteiros A, E3; teste físico pendente (P57) |
| 27–31 | criador, aventura da 10, sementes da 11, testes, presets e referências | gate 51/0, roteiros antigos, `ProceduralWorldTest`, 362/362, 22/22 |
| 32 | desempenho sem regressão grave | menu 10,6–11,0 ms × vila sem menu 9,65 ms no mesmo binário; 1 quadro > 20 ms em 2 520 |
| 33 | vídeo do fluxo real | `videos/01-porta-de-entrada.mp4` (som da aplicação por loopback) |
| 34 | pendências para avaliação física | resposta 23 e P57–P63 |

## Fechamento

O X da janela, o retorno ao menu principal, a tipografia dos botões, a tela de carregamento, a câmera da prévia e a
preparação dos commits: [relatório do fechamento das rodadas 11 e 11.1](relatorio-fechamento-rodadas-11.md).
