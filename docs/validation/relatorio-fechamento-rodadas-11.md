# Relatório — fechamento das Rodadas 11 e 11.1

**Situação:**
- Commits locais no ramo `rodada-11/mundo-procedural`, sobre a `main` publicada (`8fd6756`, tag `adventure-foundation-v0.4`;
  a `origin/main` não recebeu commits novos).
- **Sem push, sem merge, sem tag, sem Release.**
- O Titã não foi usado. O laboratório de assets (`valumo-content-lab`) não foi tocado.

**Pacote local:** `output/visual-review/round-11-1-final/` (o da 11.1, `round-11-1/`, ficou como estava).

**Decisões definitivas do proprietário, registradas** (ADR 0027 adendo, ADR 0028, `menus.md`):
- menu lateral A, logotipo "VALUMO", cena 3D cinematográfica;
- personagem separado de mundo, saves por par personagem × mundo, semente dentro da interface;
- nenhuma instrução de tecla nem texto de desenvolvimento na tela;
- adoção automática dos saves antigos;
- criador com cache;
- "Menu principal" volta à tela inicial;
- o X da janela pede confirmação quando há o que perder.

## O que mudou no fechamento

| # | Item | Antes | Agora | Evidência |
|---|---|---|---|---|
| 1 | **X da janela (P0)** | fechava na hora, mesmo com progresso | o `Window` intercepta o pedido (`glfwSetWindowCloseCallback`, antes de destruir nada). Fora de uma aventura (menus, carregamento), fecha. Numa aventura, SEMPRE a mesma confirmação "Sair do jogo?" da pausa, de qualquer estado (explorando, luta, diálogo, diário, pausa); com tudo salvo ela diz isso e oferece Sair/Cancelar. Cancelar volta ao estado anterior, o diálogo na mesma fala. No criador com alterações: Salvar e sair / Sair sem salvar / Cancelar | `MenuAdventureTest` (2 testes novos + 1 reescrito), roteiros `13-navegacao-a…e`, `capturas/navegacao/` |
| 2 | **"Menu principal" (P0)** | voltava à tela de mundos | volta à TELA INICIAL. Personagem e mundo ficam guardados: Jogar reabre com eles escolhidos | roteiros 13-a e 13-b; capturas n04–n06 |
| 3 | **O que a pergunta oferece** | — | numa aventura sempre pergunta; `GameLogic.unsaved()` (progresso das missões ou posição de 0,25 u desde o último save, o Continuar ou o começo; o tempo não conta) decide só o CONTEÚDO: tudo salvo = Sair / Cancelar; progresso = Salvar e sair / Sair sem salvar / Cancelar | `MenuAdventureTest` |
| 4 | **Tipografia dos botões** | cada botão escolhia a sua escala; vizinhos diferentes (no 720p, "Novo personagem" minúsculo ao lado de "Voltar" grande) | mesma escala por grupo (`Game.groupPx`): base 30 no menu principal, 27 nas telas de menu, 22 na pausa. Em personagens, "Novo/Editar" empilham quando lado a lado encolheriam o grupo. Rótulos das configurações encurtados ("Barras", "Sensibilidade") | `tipografia/` (14 pares antes × depois, 720p e 1080p) |
| 5 | **Tela de carregamento** | preto quase total + uma linha | fundo escuro quente em faixas, o logotipo pequeno, o passo real e as sementes P1; sem porcentagem, sem cena 3D | `carregamento/` |
| 6 | **Câmera da prévia** | — | comparada com uma alternativa baixa nas sementes 1024, 2048, 4096 e 77; a alternativa perdeu em todas (telhado no primeiro plano; 2048 e 77 sem rumo livre na colisão real). **Mantida a atual**, `PREVIEW_VERSION` = 1 | `previa/` |
| 7 | Falha de gravação ao sair | — | não sai, mostra o motivo, a confirmação continua aberta, nada escrito | `MenuAdventureTest.falhaDeGravacao…` |
| 8 | Código morto e avisos | 3 avisos na IDE | 0 diagnósticos | `ide-diagnosticos.json` |

**Achado de desenho no meio do caminho:** a primeira regra de "progresso não salvo" (tempo de jogo) fazia o "Menu
principal" perguntar a quem tinha acabado de entrar e ficado parado. O roteiro 13-b achou o problema; a regra passou a ser
o que o save de fato guarda.

## Respostas

1. **O X da janela agora respeita o salvamento?** Sim.
   - Numa aventura, sempre abre a confirmação. Com progresso fora do disco, "Salvar e sair" grava (escrita atômica) e
     fecha; com tudo salvo, ela diz isso e oferece Sair/Cancelar.
   - Provado em execução real: a parte A salvou pelo X na posição (−6,95; 2,54), e a parte B reabriu e continuou em
     (−6,95; 2,55).
   - Fora de uma aventura (menu, personagens, mundos, carregamento, depois de uma falha de geração), fecha direto.
   - O encerramento forçado do processo não é interceptável e não é prometido.
2. **O cancelamento do fechamento funciona?** Sim: volta ao estado de antes, inclusive ao diálogo na mesma fala (teste),
   e o jogo continua (roteiro 13-a, captura n08).
3. **O botão Menu principal retorna à tela certa?** Sim, à tela inicial (Jogar, Configurações, Sair). A sessão de jogo é
   fechada (combate, missão e diálogo não ficam no fundo) e a cena cinematográfica volta.
4. **A navegação preserva personagem e mundo?** Sim (roteiro 13-a, n05/n06). Os três trajetos pedidos passaram (13-b),
   com saves separados:
   - mesmo personagem e mesmo mundo → Continuar;
   - outro personagem no mesmo mundo → aventura nova;
   - mesmo personagem em outro mundo → aventura nova.
5. **Os textos dos botões ficaram consistentes?** Sim, por grupo, em 720p e 1080p (`tipografia/`). Não houve redução
   geral: o menu ficou maior em 1080p (escala 3) e nenhum grupo caiu para a escala mínima.
6. **A tela de carregamento melhorou?** Sim (antes × depois em `carregamento/`). Ela não esconde o congelamento: a montagem
   de malhas continua na thread do GL e aparece como o passo nomeado.
7. **A câmera da prévia foi alterada ou mantida?** Mantida (item 6 acima).
8. **O menu continua bonito em 720p e 1080p?** Sim, pelas capturas (`tipografia/depois-telas/`, `capturas/listas/`). A
   aprovação artística é do proprietário.
9. **O criador continua rápido?** Sim.
   - **Primeira abertura:** ~2,8 s, sob "Abrindo o criador".
   - **Seguintes:** 54–107 ms (6 ciclos).
   - **Recursos:** nenhuma malha cresce depois da primeira (300 constantes, com as 88 da cena do criador mantidas de
     propósito).
   - **Cancelar** não grava; **editar** mantém o id; nada duplica.
10. **A importação dos saves permanece segura?** Sim, inalterada: cópia validada, original intacto (hash), idempotente,
    sem duplicar personagem, mundo ou recompensa, nunca por cima de uma aventura (`MenuAdventureTest`, `MenuStoresTest`).
11. **O desempenho mudou?** Não de forma mensurável. Mesma sessão, binários antes × depois intercalados, 2 execuções:
    - **Menu principal:** mediana 13,0–13,2 → 13,3–13,5 ms (+0,2, ruído).
    - **Personagens e mundos:** iguais.
    - **Ressalva:** os números absolutos desta sessão estão acima dos da 11.1 nos DOIS binários, porque havia Firefox e
      VS Code abertos (load ~2,3, P65).
12. **Houve vazamentos?** Não.
    - **Medição:** 18 voltas (criador → cancelar → aventura → menu; prévia → cancelar; semente recusada → outra) com som
      por loopback.
    - **Constantes:** malhas, texturas, FBOs, grades de relevo, heap pós-GC (10,8 MB) e buffers/fluxos do OpenAL.
    - **RSS:** oscila sem crescer.
13. **Quantos testes passam?** **364 de 364** (eram 362; +2: o X da janela em qualquer estado e a falha de gravação; o
    teste da confirmação na pausa foi reescrito para a regra nova).
14. **Quais validadores foram executados?**
    - `--check` (129), `interrupcao-fina`, `golpe-andando`, `esquiva`, `--check-cabelos`, `--occlusion` (só a exceção
      conhecida);
    - gate de personalização, referências canônicas, links, QA de áudio;
    - diagnóstico da IDE num import limpo (0).
15. **Os 51 presets continuam intactos?** Sim: 51 pares, 0 diferença.
16. **As 22 referências continuam intactas?** Sim: 22 capturas equivalentes (< 0,5% acima de 8/255).
17. **O clone limpo funciona?** Sim: 364 testes e `--check` num clone novo (seção "Clone limpo").
18. **O CI poderá executar os novos testes?** Sim. O CI roda `./gradlew build` (todos os testes) e nenhum teste novo
    precisa de GPU ou janela. As capturas, o X por roteiro e o desempenho são QA local, como antes.
19. **Quantos commits locais foram preparados?** 25 (lista abaixo).
20. **Quais limitações permanecem?**
    - **P57/P64:** entrada física e o X real do gerenciador de janelas não automatizáveis no Wayland.
    - **P59:** ~1,7–3 s de malha ao entrar num mundo gerado, ao preparar uma prévia que falta e na primeira abertura do
      criador; sempre sob o passo nomeado.
    - **P60:** sem opção de qualidade gráfica nem de resolução no jogo.
    - **P61:** sem tema musical próprio do menu.
    - **P62:** sem renomear nem excluir mundos.
    - **P63:** a vitrine esconde moradores colados ou atrás do herói.
    - **P65:** números absolutos de desempenho medidos com a máquina ocupada.
    - **P66:** se um dia for preciso ensinar controles, a proposta é um tutorial ou uma tela de ajuda própria (não
      implementado).
21. **O que ainda precisa de teste físico?** O roteiro `roteiro-de-teste-fisico-menu.md`, em especial o §5 novo:
    - o X real da janela (andando, em diálogo, parado, no criador);
    - Menu principal → tela inicial;
    - mouse e roda;
    - o tamanho das letras;
    - a tela de carregamento;
    - o ouvido.
22. **O projeto está pronto para publicar as Rodadas 11 e 11.1?** Tecnicamente sim: o clone limpo passou (abaixo).
    - **Portões:** todos verdes.
    - **Commits:** locais, organizados, com a identidade noreply e só o título.
    - **Antes do push:** o teste físico do §5 do roteiro e a sua autorização.

## Commits

**25 commits locais** sobre `8fd6756`:
- Conventional Commits em português, só o título.
- Identidade `Jovinull <79649732+Jovinull@users.noreply.github.com>`.
- Sem trailers.
- `Game.java` e `GameLogic.java` juntam rodada 11 e 11.1 num commit só, porque as mudanças dependem umas das outras.

- `94217b1` feat(engine): intercepta o pedido de fechar a janela
- `e9e98e6` feat(viewer): separa o relevo como dado e mantém a vila legada intocada
- `800bd57` refactor(viewer): expõe o retrato do criador para outras telas
- `fa7d1fa` feat(viewer): permite cancelar e perguntar ao fechar no criador aberto pelo jogo
- `8f2e9a0` feat(game): adiciona o gerador de regiões por semente com conferência na colisão
- `273581d` feat(game): instancia missões a partir de modelos e fatos da região
- `67cd418` feat(game): adiciona o save v2 do mundo gerado e a escrita atômica reutilizável
- `73702ae` feat(game): adiciona perfis de personagem e catálogo de mundos
- `769c0b8` feat(game): adiciona o fluxo de menus e a apresentação cinematográfica
- `97b226b` feat(game): adiciona volume geral e preferências de câmera gravadas
- `10c839c` feat(game): integra o menu, as sessões trocáveis e a saída sem perder progresso
- `1129072` test(game): adiciona os testes do mundo procedural e das missões instanciadas
- `0edd171` test(game): adiciona os testes do menu, dos catálogos, da apresentação e da saída
- `4e6d450` feat(scenes): adiciona habitantes e modelos de missão do mundo gerado
- `a77a09c` feat(scenes): adiciona as tomadas da apresentação do menu
- `5dfd695` feat(tools): adiciona os roteiros e o pacote de revisão do mundo procedural
- `485824b` feat(tools): adiciona os roteiros e o pacote de revisão do menu
- `cadb428` docs: adiciona a arquitetura e os contratos do mundo procedural
- `c3c151f` docs: adiciona a arquitetura dos menus, dos perfis e do catálogo de mundos
- `21f4fcb` docs: adiciona as decisões do mundo procedural e da porta de entrada
- `eb63c31` docs: adiciona o handoff para integrar os assets do laboratório
- `16fd303` docs: atualiza jogo, save e missões para as rodadas 11 e 11.1
- `aa4a09e` docs(skills): adiciona a skill do mundo procedural e atualiza as de menu, jogo e QA
- `docs: adiciona os relatórios e o teste físico das rodadas 11 e 11.1`
- `docs: atualiza o estado do projeto e os problemas conhecidos`

## Clone limpo

**Passou.** Clone novo do ramo em `aa4a09e`, sem `output/`, sem assets gerados e sem `build/`:
- `./gradlew test :viewer:installDist :game:installDist` gerou os assets e compilou tudo;
- **364 testes, 0 falhas**;
- `viewer --check` 129/129.

O Python da geração foi o da `.venv` do ambiente (`VX_PYTHON`), porque a geração precisa de numpy (o mesmo requisito do
README). Os commits seguintes são só de documentação.

## Procedimento recomendado para integrar na `main` (depois da sua autorização)

1. **Atualizar e conferir:**
   - `git fetch origin`;
   - conferir que a `origin/main` continua em `8fd6756` (senão, `git rebase origin/main` no ramo LOCAL, que nunca foi
     publicado, e repetir o portão).
2. **Publicar o ramo para o CI:** `git push origin rodada-11/mundo-procedural`. Abrir um PR para a `main` e esperar o CI
   verde.
3. **Integrar por avanço rápido:** `git checkout main && git merge --ff-only rodada-11/mundo-procedural && git push
   origin main` (o histórico fica com os commits por assunto, sem squash). Ou "Rebase and merge" no PR.
4. **Tag:** só se você quiser um checkpoint, anotada e nova (sugestão: `entry-foundation-v0.5`). Nenhuma tag antiga se
   move. Sem Release.

## Primeiro teste físico do proprietário — duas correções

| O que o proprietário viu | Causa | Correção | Prova |
|---|---|---|---|
| "Menu principal → volta com personagem e mundo selecionados": perfeito | — | — | — |
| **"O X às vezes pergunta, às vezes não"** | a regra perguntava só com progresso fora do disco. Logo depois de salvar, de continuar ou de um salvamento automático da missão, o X fechava direto. Era de propósito, mas parecia aleatório | numa aventura, sair SEMPRE pergunta. Com tudo salvo: "Tudo desta aventura já está salvo." (Sair / Cancelar); com progresso: Salvar e sair / Sair sem salvar / Cancelar | `MenuAdventureTest`; roteiros `13-navegacao-a…e` refeitos (todos ok) |
| **"No criador a tela escurece e trava, sem o aviso"** (na segunda vez, rápido) | o passo pesado começava no quadro em que a cobertura ficava escura, valendo o quadro ANTERIOR, que não tinha o rótulo. "Abrindo o criador" nunca chegava à tela, e isso valia para TODAS as telas de carregamento. Os roteiros não pegaram porque a captura desenhava o rótulo antes | o passo só começa depois de um quadro com o rótulo dele desenhado (`labelShown`) | quadros gravados durante "Novo personagem": **antes 0** com o rótulo, **depois 1**, antes da montagem |
| Tela e letras agradáveis | — | — | — |

### Estado final (depois das duas correções)

- 364 testes, 0 falhas; `--check` 129/129; gate de personalização 51/0.
- Roteiros de navegação 13-a…e ok, com o X perguntando sempre na aventura e a reabertura continuando do ponto salvo.
- Mais 4 commits locais (29 no total), sem push.
