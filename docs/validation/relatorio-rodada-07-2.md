# Relatório — Rodada 07.2: criador de personagens do jogador

Pacote local (fora do git): `output/visual-review/round-07-2/` (`LEIA-ME.md`). Regenerável com
`tools/review72.sh tudo`. **Aprovação artística: pendente com o proprietário.** Nada aqui é declarado
aprovado.

## Como executar

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew editor          # criador de personagens (gera os assets antes, se preciso)
./gradlew editorDebug     # editor técnico da rodada 07 (depuração, métricas)
./gradlew test            # 98 testes
tools/review72.sh tudo    # capturas, vídeo do fluxo, desempenho e testes → output/visual-review/round-07-2/
```

## 1. Auditoria do estado inicial

- **Uncommitted:** 27 entradas da rodada 07 e da ligação do Gradle; preservadas.
- **Janela:** só estado por quadro (teclas, botões, delta do mouse), sem eventos de clique, sem conversão para
  pixels do framebuffer e sem `char`.
- **Texto e desenho:** HUD com fonte 5×7 só em maiúsculas, sem acentos, só retângulos.
- **Compositor:** síncrono na thread do GL (≈ 12 ms por troca, até ≈ 35 ms).
- **Editor:** um painel de texto do modo PERSONALIZACAO.
- **Testes:** 83 testes; `--check` TODOS OK; `--occlusion` com a exceção conhecida do arco.

## 2. Deficiências do editor antigo

- **Interação:**
  - só setas (linha e valor);
  - nenhuma prévia antes de escolher;
  - IDs técnicos (`cabelo.curto`) e códigos de cor;
  - métricas de GPU e malha na tela.
- **Apresentação:** platô de grama sobre céu em degradê.
- **Salvar e carregar:**
  - carregar pegava só o mais recente;
  - restaurar sem confirmação;
  - nome da aparência não editável.

## 3. Propostas visuais avaliadas

Duas, implementadas e capturadas com a mesma identidade:
- **A, "fantasia compacta":** abas à esquerda, opções à direita, ações sob o painel.
- **B, "ateliê":** abas no topo, opções numa faixa inferior em duas colunas.

Comparação lado a lado: `comparacao/layout-a-vs-b.png`.

## 4. Layout escolhido

A. O personagem fica com a coluna central inteira e a altura total. Em B, a faixa inferior e as poses cortam a
cabeça no foco do rosto, e a área do personagem cai para ≈ 45% da altura. Cliques até qualquer escolha: 2 nos
dois. Detalhes em `docs/architecture/criador-de-personagens.md` §2.

## 5. Cenário de fundo

`assets/scenes/criador_e.json`, com os assets, a luz e o relevo oficiais:
- **Herói:** gramado plano a oeste do lago, sem pedestal.
- **Fundo:** lago com píer, nenúfares, juncos e o salgueiro.
- **Esquerda:** acampamento (fogueira, tronco, caixote, barril, lanterna).
- **Direita:** flores e pedras em touceiras.
- **Criaturas:** pompom e lumi.

A câmera fica no ângulo composto (`camera.yaw`) e o arrasto gira o personagem, porque a primeira versão, com
órbita, atravessava o terreno. A clareira canônica não mudou.

## 6. Componentes de interface

| Tipo | Uso |
|---|---|
| Abas | categorias, com ícone de pixel próprio |
| Miniaturas | presets, penteados, rostos, coberturas |
| Amostras | cores em faixas de luz, base e sombra |
| Botões | ações principais |
| Alternadores | poses, câmera, expressão |
| Campo de texto | nome |
| Lista | aparências salvas |
| Diálogos modais | restaurar, carregar, concluir |
| Avisos | balões sobre a cena |
| Dicas | motivo de indisponível |
| Barra de rolagem | painel de opções |
| Indicador "Preparando…" | composição demorada |

Estados: hover, selecionado, pressionado, indisponível e carregando.

Visual: madeira e pergaminho com cantos em degraus, dourado de seleção e a semente P1 só na escolha ativa.

Fonte de pixel própria, desenhada no código: maiúsculas de 7 linhas, minúsculas de 5 e acentos do português
compostos. Nenhuma fonte de terceiros.

## 7. Funcionamento do mouse

- **Fila de eventos:** a `Window` ganhou fila de eventos (mover, apertar, soltar, roda, tecla, caractere) em
  pixels do framebuffer, com conversão HiDPI.
- **Clique:** apertar e soltar no mesmo controle.
- **Arrasto:** com o esquerdo, gira o personagem, com captura até soltar. Com o direito, sobe e desce o
  enquadramento.
- **Roda:** rola o painel ou dá zoom (1,6–6,2 u), conforme a região.
- **Modais:** bloqueiam o resto.
- **Redimensionamento:** o layout é refeito a cada quadro para o tamanho real.
- **Teclado:** o fluxo inteiro funciona sem mouse, e não só pelas setas.

**Validação:** por testes com eventos sintéticos e por roteiros que injetam eventos na mesma fila do GLFW
(capturas e vídeo). Também houve teste de fumaça da janela real (`--editor --demo 400`, e `./gradlew editor`
aberto por 60 s).

**Limite honesto:** a sessão é Wayland, sem ferramenta de automação do sistema. Por isso não houve clique
físico, e nenhuma tela HiDPI real foi exercitada. Ver §19.

## 8. Miniaturas reais

- Cada miniatura é o modelo composto da opção sobre a aparência atual. Exemplo: os penteados aparecem na cor
  e no rosto escolhidos.
- O renderizador é o da cena, num FBO 320², com fundo de pergaminho.
- Enquadramento por tipo:
  - corpo para presets;
  - cabeça inteira para penteados;
  - close para rostos;
  - 3/4 lateral para coberturas, onde o laço e a pena do C3 aparecem.
- Renderização: uma miniatura por quadro.
- Dados: o modelo-miniatura é liberado logo depois, e fica só a textura.

## 9. Paletas clicáveis

| Paleta | Cores |
|---|---|
| Cabelo | 10 |
| Pele | 5 |
| Íris | 5 |

As cores são as curadas do catálogo, com nome amigável. A recoloração preserva a rampa do material
(`Composer.recolor`, rodada 07).

## 10. Seleção dos presets

Três cartões com o corpo inteiro real: Aventureiro, Exploradora e Maga. Rotulados como "Modelo-base —
aparência inicial (não define classe)". Escolher um preset reinicia as escolhas e o nome.

O gate pixel a pixel da rodada 07 continua com 51 pares e 0 diferenças (`regressao/presets/gate.md`).

## 11. Compatibilidade de chapéus

Opções de cobertura:
- Sem chapéu;
- Laço e pena (C3);
- Chapéu de aba.

As regras do catálogo continuam valendo (`sob_aba`, `nativo`, `faixa`). Regra `X` deixa a opção indisponível
com o motivo e nunca é aplicada, nos dois sentidos (chapéu × penteado).

O catálogo atual não tem nenhuma combinação proibida. Por isso esse caminho só aparece nos testes, com uma
regra marcada em memória. A captura de "opção indisponível" mostra o Restaurar sem nada a restaurar, com a
dica.

## 12. Salvamento e carregamento

- **Formato:** `valumo.aparencia` v1, inalterado.
- **Salvar:** grava `output/aparencias/<nome>-NNN.json` e nunca sobrescreve.
- **Carregar:** abre uma lista dentro do editor.
- **Arquivo inválido:** mensagem compreensível, sem mudança na aparência.
- **Restaurar:** pede confirmação.
- **Nome:** editável, com acentos.
- **Atalhos:** `F2`/`F3` continuam.
- **Pronto:** abre a vitrine (herói andando e girando, "Voltar ao criador"). Não finge iniciar um jogo.

## 13. Responsividade

- **Escala:** proporcional (`s = min(L/1920, A/1080)`, limitada) e texto em escala inteira escolhida pelo
  layout.
- **Resoluções capturadas:** 1280×720, 1920×1080, 2560×1080 e 3440×1440.
- **Testes de layout:** 6 resoluções × 5 categorias × A/B. Nada fora da tela nem sobreposto, nenhum texto
  cortado, e o painel nunca fica sobre o personagem.
- **Redimensionar:** os cliques seguem o layout novo.

## 14. Desempenho

Medido em `output/visual-review/round-07-2/desempenho/desempenho.txt` e `vazamento/desempenho.txt`. Máquina:
Iris Xe, 1920×1080, janela oculta. Cada quadro mede lógica + envio + render + `glFinish`.

| Medida | Resultado |
|---|---|
| Cliques rápidos (20 em ≈ 3 s + rajada pelo teclado, uma troca por quadro): quadro | mediana 10,5 ms, p95 17,3 ms, **máximo 23,4 ms**, nenhum quadro > 25 ms |
| Clique → personagem novo na tela | mediana 28 ms, p95 41 ms (na rajada: mediana 13 ms); o personagem anterior continua na tela até lá |
| Composição (thread de trabalho) | compor ≈ 2–4 ms + vértices ≈ 12–21 ms |
| Envio à GPU (thread do GL) | ≈ 2–7 ms |
| 360 trocas (vazamento) | 154 composições, 150 descartadas por obsoletas, 607 miniaturas; **217 malhas vivas antes e depois** (estável); p95 13,5 ms; só o 1º quadro > 25 ms (76 ms, GPU enfileirada antes da medição) |
| Janela real 1600×900 com vsync | ≈ 40 FPS (a cena do criador custa o mesmo que a clareira; ver §19) |

Corrigido durante a rodada:
- miniaturas acumuladas num quadro (pico de 80 ms);
- contador de bytes das malhas (`Mesh.close` descontava como layout de cena);
- lote de UI criando e destruindo VBO a cada descarga, hoje persistente.

O primeiro quadro de uma medição pode incluir trabalho de GPU enfileirado antes dela; o relatório de
medição lista cada quadro > 25 ms com a causa.

## 15. Testes

**98 testes, 0 falhas:** os 83 anteriores e 15 do `CreatorTest`, listados em
`docs/architecture/criador-de-personagens.md` §9.

| Verificação | Resultado |
|---|---|
| `viewer --check` | TODOS OK |
| `viewer --occlusion` | só a exceção conhecida (`bow_shot`) |
| Gate de presets | 51 pares, 0 diferenças |
| Referências canônicas refeitas | 22/22 a 0/255 |

## 16. Capturas reais

`output/visual-review/round-07-2/capturas/` traz 33 capturas do viewer, todas por roteiro de entrada:
- de 01 a 20, os itens pedidos: tela inicial, presets, cabelo, penteado, paletas, rosto, íris, coberturas,
  com chapéu, com capuz, sem cobertura, close, corpo inteiro, salvamento, carregamento, restauração,
  indisponível, 720p e 1080p;
- os extras: nome, prévia de expressão, hover, confirmar, concluir, vitrine, ultrawide e layout B.

Comparações em `comparacao/`:
- antes e depois com o editor da 07;
- A × B.

## 17. Vídeo do fluxo completo

`video/fluxo-completo.mp4` (e `.gif`): 1920×1080, 30 fps, ≈ 66 s. O fluxo:
1. abrir;
2. preset;
3. penteado;
4. cor;
5. pele;
6. rosto, íris e expressão;
7. chapéu e capuz;
8. girar (arrasto) e aproximar (roda);
9. pose;
10. nome;
11. salvar;
12. restaurar (confirmar);
13. carregar (lista);
14. Pronto, e vitrine.

Os eventos do roteiro (`tools/criador/video.txt`) entram na fila da janela e passam pelo mesmo controlador do
uso real. O cursor é desenhado pela interface na posição dos eventos. Não é gravação de tela de um mouse
físico.

## 18. Documentação e skills

- **Novos:**
  - `docs/architecture/criador-de-personagens.md`, que também é o guia das próximas interfaces;
  - ADR `0012-interface-do-jogo.md`;
  - este relatório.
- **Atualizados:**
  - `README.md`, `CLAUDE.md`, `docs/README.md`;
  - `personalizacao.md`, `arquitetura.md`;
  - contrato de qualidade (seção "Interface do jogador");
  - checklist de aceitação (seção "Interface do jogador");
  - processo de revisão visual e problemas conhecidos.
- **Skills:**
  - `character-customization` e `visual-qa` atualizadas;
  - `game-ui` nova, porque o procedimento (layout testável, roteiro de entrada, estados, escala, trabalho
    fora da thread do GL) vale para inventário, diálogo e HUD.

## 19. Defeitos conhecidos

- **Validação manual pendente:** clique físico e tela HiDPI real não foram exercitados. Peço um teste à mão
  do proprietário (`./gradlew editor`).
- **Indisponível:** nenhuma combinação proibida no catálogo atual; o estado só aparece nos testes.
- **C3 discreto de frente:** a miniatura usa 3/4 lateral. O asset aprovado não foi alterado.
- **Poses:** Andar e Correr no lugar.
- **720p:** o texto fica na escala 1× da fonte de pixel (pequeno, nítido).
- **Abertura e FPS:** abrir leva ≈ 2 s. Na janela real são ≈ 40 FPS: a cena do criador tem o custo da
  clareira, e não foi otimizada nesta rodada.
- **Campo de nome:** aceita letras, números e espaço; sem cursor no meio nem seleção.
- **Layout B:** existe só nos roteiros.
- **Diálogo Carregar:** lista o que está em `output/aparencias/`, sem apagar nem renomear.

## 20. Recomendação para a próxima rodada

Primeiro, o proprietário testa o criador à mão e aprova ou rejeita a direção da interface (layout A, cena,
fonte de pixel). Com isso definido, a próxima rodada pode tratar de:
- um segundo tamanho de fonte para 720p, se o 1× incomodar;
- uma cena do criador mais leve (sem a floresta distante fora do enquadramento), para 60 FPS;
- novos componentes, que o criador já mostra sem trabalho de interface.

Equipamento e roupas por região ficam para a rodada 08, quando o proprietário pedir.
