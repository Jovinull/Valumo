# Criador de personagens (interface do jogador)

Rodada 07.2. É o que `./gradlew editor` abre: o jogador monta a aparência do herói clicando em miniaturas e
amostras, com o personagem real numa cena do mundo. A composição continua sendo a da rodada 07
([personalização](personalizacao.md)); aqui fica só a interface. O editor técnico da rodada 07 (texto, teclado,
métricas) continua existindo como modo de depuração: `./gradlew editorDebug` ou `viewer --editor-debug`.

Este documento também serve de guia para as próximas interfaces do jogo: as peças de engine (fila de eventos,
lote de UI, fonte própria) e o procedimento de layout testável e de captura por roteiro valem para qualquer tela.

## 1. Peças

```
engine  Window.InputEvent + events()/inject()   fila de eventos em pixels do framebuffer (HiDPI), injetável
        UiBatch                                  quads 2D coloridos/texturizados, VBO persistente
        PixelFont + FontAtlas                    fonte de pixel própria (acentos do português), escala inteira
viewer  CreatorState       estado sem OpenGL: categoria, aparência, nome, prévia, diálogo, rolagem
        CreatorLayout      geometria sem OpenGL: controles, hitboxes, escala, tamanho de texto (A e B)
        CreatorController  entrada → ações: clique, arrasto, roda, teclado, texto, salvar/carregar/restaurar
        CreatorCamera      giro do personagem, inclinação, zoom com limites, foco rosto/corpo, suavização
        CreatorView        desenho: madeira, pergaminho, ícones de pixel, estados, dica, cursor do roteiro
        CreatorThumbs      miniaturas reais (modelo composto renderizado num FBO), uma por quadro
        AsyncComposer      composição na thread de trabalho, envio na thread do GL, descarte, LRU
        Creator            junta tudo: cena criador_e, laço da janela, roteiros de captura/vídeo/medição
```

`App` ganhou só ganchos pequenos: `overlay` (desenho 2D por cima da cena), `renderFrame`, `renderThumb`,
`forget`, `injected` (modelos já enviados pelo criador) e `State.camSide` (pan lateral da câmera da cena).
Nada do criador mora dentro de `App`.

Fluxo de um quadro (`Creator.frame`):

1. Eventos da fila da janela → `CreatorController.handle`, sobre o layout **que está na tela**.
2. `AsyncComposer.poll()` envia à GPU o personagem que ficou pronto; `CreatorThumbs.pump(1)` renderiza no
   máximo uma miniatura.
3. Layout novo (`CreatorLayout`) para o tamanho atual da janela; pedidos de miniaturas que faltam.
4. Câmera suavizada; cena + interface (`App.renderFrame` → `overlay`).

## 2. Layout

### Propostas avaliadas

| | A — fantasia compacta (escolhida) | B — ateliê (faixa inferior) |
|---|---|---|
| Disposição | abas à esquerda, painel de opções à direita, ações sob o painel, poses e câmera sob o personagem | abas no topo, opções numa faixa inferior em duas colunas, ações à direita da faixa |
| Área do personagem | coluna central inteira (≈ 54% da largura em 1080p), altura total | largura total, mas só ≈ 45% da altura |
| Foco no rosto | cabeça inteira visível acima dos botões | cabeça cortada pela faixa e pelas poses |
| Leitura | lista vertical de categorias com ícone; miniaturas grandes (2–3 colunas) | miniaturas menores; abas mais estreitas |
| Cliques até uma cor | 2 (categoria, amostra) | 2 |
| 720p | cabe sem rolar | cabe, personagem pequeno |

A venceu pelo espaço vertical do personagem: é um criador de aparência, e o rosto e o cabelo precisam de
altura. B continua disponível nos roteiros (`variant B`) para comparação; capturas em
`output/visual-review/round-07-2/capturas/30-*` e `31-*`.

### Escala e resoluções

- Tudo em pixels do framebuffer, multiplicado por `s = min(L/1920, A/1080)`, limitado a 0,6–1,8.
  - O mesmo controle ocupa a mesma fração da tela em 720p e 1080p.
  - Em telas largas (2560×1080, 3440×1440), sobra espaço só para a cena.
- Texto: a fonte é de pixel, então a escala é sempre **inteira**. O layout escolhe por controle o maior passo
  que cabe, e uma fileira inteira usa o mesmo tamanho (`CreatorLayout.sizeTexts`). O desenho e os testes usam
  esse valor. Só depois disso um rótulo pode ganhar "…", e os testes proíbem "…" em nomes amigáveis a partir de 720p.
- A câmera põe o herói no centro da área livre entre os painéis (`State.camSide`), não no centro da janela.

## 3. Componentes e estados

| Tipo | Uso | Estados |
|---|---|---|
| `TAB` | categorias (Modelo, Cabelo, Rosto, Pele, Chapéu, Roupa) | normal, hover (madeira clara, sobe 2 px), selecionado (pergaminho, borda dourada, semente P1) |
| `THUMB` | presets, penteados, rostos, coberturas | normal, hover (brilho quente, sobe 4 px), selecionado (borda dourada + marca), indisponível (cinza + cadeado + motivo na dica), carregando (pontos) |
| `SWATCH` | cores de cabelo, pele, íris | faixas luz/base/sombra da própria cor; hover; selecionado (marca); nome embaixo |
| `BUTTON` | Restaurar, Salvar, Carregar, Pronto; diálogos | normal, hover, pressionado (desce 2 px), principal (musgo), indisponível (cinza + motivo) |
| `TOGGLE` | poses, câmera, expressão | ligado (dourado), hover, pressionado |
| `FIELD` | nome do aventureiro | foco (borda dourada, cursor piscando) |
| `LIST_ITEM` | aparências salvas no diálogo Carregar | hover; rola com a roda |
| `DIALOG` | confirmar restauração, carregar, concluir | modal: escurece o resto e só ele recebe clique |

Mensagens (salvou, carregou, restaurou, erro) aparecem num balão sobre a cena por 4,5 s. Erro em coral, com
texto compreensível ("Não combina com o penteado atual", "Arquivo x.json não é válido: …").

Paleta da interface (`CreatorView`): madeira `#6b4630`/`#45291c`/`#8f6040`, pergaminho `#f3e6c8`/`#fbf3df`,
tinta `#3a2a26`, dourado `#ecb84c`, musgo `#5f8a5a`, coral `#c8643c`, e a semente P1 `#4fd6ae` só como acento
da escolha ativa. Cantos em degraus (chanfro de voxel), sombra projetada curta. Ícones de pixel desenhados no
código (`ICONS`). A fonte é própria (`PixelFont`): maiúsculas de 7 linhas, minúsculas de 5, acentos compostos,
negrito engrossado sem fechar vãos de 1 pixel.

## 4. Entrada

- **Fila da janela** (`Window.events()`): MOVE, DOWN, UP, SCROLL, KEY (com repetição) e CHAR, já em pixels do
  framebuffer (a posição do GLFW vem em coordenadas de janela e é multiplicada pela razão framebuffer/janela:
  HiDPI). `Window.inject` põe eventos na mesma fila — os roteiros usam isso.
- **Clique** = apertar e soltar sobre o mesmo controle. Soltar em outro, ou depois de arrastar, não aciona nada.
- **Arrastar** com o botão esquerdo na área do personagem gira o **personagem**. A câmera fica no ângulo
  composto da cena (`criador_e.json`, `camera.yaw`), então nunca atravessa o cenário. A captura dura até soltar,
  mesmo saindo da área. Botão direito arrastado sobe e desce o enquadramento.
- **Roda:** sobre o painel rola as opções (com fim); sobre o personagem dá zoom (1,6 a 6,2 u); no diálogo
  Carregar rola a lista.
- **Câmera:** valores-alvo com amortecimento exponencial (sem salto). As categorias Cabelo, Rosto e Chapéu
  aproximam a cabeça e viram o herói de frente pelo caminho curto; Modelo e Pele voltam ao corpo. Os botões
  Rosto, Corpo e Centralizar fazem o mesmo à mão.
- **Teclado:**

  | Tecla | Ação |
  |---|---|
  | `Tab` | próxima categoria |
  | `←` `→` | opção principal (miniaturas) |
  | `↑` `↓` | cor |
  | `Enter` | Pronto ou confirmar |
  | `Esc` | fechar diálogo ou vitrine |
  | `F2` | salvar |
  | `F3` | carregar |
  | `Backspace` | restaurar (com confirmação) |
  | `R` | recentralizar |
  | `F12` | captura em `output/visual-review/manual/` |

## 5. Ações

- Toda escolha passa por `Appearance.validate`. Combinação incompatível (regra `X` no catálogo) fica
  **indisponível com o motivo** e nunca é aplicada. O catálogo atual tem uma (cabelo crespo × capuz C3); o caminho também é coberto por
  teste com uma regra marcada só em memória.
- **Restaurar** pede confirmação e volta ao preset canônico (idêntico, gate da rodada 07). Fica indisponível
  quando a aparência já é a original.
- **Salvar** grava `output/aparencias/<nome>-NNN.json` no formato `valumo.aparencia` v2 (lê v1), nunca sobrescreve.
- **Carregar** abre a lista dentro do editor; arquivo inválido mostra o erro e não muda nada.
- **Expressão** e **pose** são prévia: não entram na aparência salva.
- **Pronto** confirma a aparência e abre a vitrine: herói andando e girando devagar, sem painéis, com "Voltar
  ao criador". Não finge iniciar um jogo.

## 6. Miniaturas reais e composição assíncrona

- Cada miniatura é o modelo **composto** da aparência candidata: a atual com só aquela escolha trocada.
  Exemplo: os penteados aparecem na cor e no rosto atuais.
- Renderização: o renderizador da cena, num FBO de 320×320, fundo de pergaminho e um `Renderer` só para elas,
  para não realocar os alvos da cena.
- Enquadramento por tipo:
  - `corpo` para presets;
  - `cabelo` para penteados, com o alto da cabeça;
  - `cabeca` para rostos;
  - `cobertura` em 3/4 lateral, porque o laço e a pena do C3 ficam atrás da cabeça.
- Composição e vértices na thread de trabalho. Envio, render e liberação das malhas do modelo-miniatura na
  thread do GL; fica só a textura.
- **No máximo uma miniatura por quadro:** a troca de cor re-renderiza quatro de uma vez, e antes isso somava
  um pico de 80 ms.
- **`AsyncComposer`:**
  - uma thread de trabalho com o seu próprio `Composer`;
  - cada pedido do personagem tem uma geração, e resultado de geração antiga é descartado antes e depois de
    compor;
  - pedidos do personagem passam na frente das miniaturas;
  - LRU de 10 modelos enviados: voltar a uma escolha recente é instantâneo, e quem sai do cache tem as malhas
    fechadas e sai da cena (`App.forget`).
- **Durante a troca**, o personagem anterior continua na tela. Só depois de 120 ms aparece "Preparando…".

## 7. Cena

`assets/scenes/criador_e.json` (escrita à mão; não substitui a clareira):
- **Herói e câmera:** o herói fica num gramado plano a oeste do lago; a câmera olha para leste.
- **Ao fundo:** o lago com o píer, os nenúfares, os juncos e o salgueiro.
- **Laterais:** à esquerda, um pequeno acampamento (fogueira, tronco, toco, caixote, barril, poste de lanterna);
  à direita, flores e pedras em touceiras soltas.
- **Criaturas:** o pompom e a lumi.
- **Mundo:** relevo, luz, céu e floresta são os oficiais; nada de pedestal.

O poste de lanterna foi tirado de trás da cabeça porque a pena do C3 sumia contra ele.

## 8. Comandos

```bash
./gradlew editor                                   # criador do jogador
./gradlew editorDebug                              # editor técnico da rodada 07
viewer/build/install/viewer/bin/viewer --editor --demo 400          # fumaça: abre, 400 quadros, FPS
viewer/build/install/viewer/bin/viewer --editor-script ROTEIRO SAIDA [FPS]   # roteiro (janela oculta)
tools/review72.sh [capturas|video|desempenho|testes|tudo]            # pacote em output/visual-review/round-07-2/
```

Roteiros em `tools/criador/` (`capturas.txt`, `video.txt`, `desempenho.txt`, `vazamento.txt`).

Comandos de roteiro:

| Comando | Efeito |
|---|---|
| `size L A` | tamanho da tela virtual |
| `variant A\|B` | troca o layout |
| `wait s` | espera |
| `move alvo [s]` | leva o cursor ao alvo |
| `click alvo [s]` | move, aperta, segura 3 quadros e solta |
| `drag dx dy [s]` | arrasta a partir da posição atual |
| `wheel n [alvo]` | gira a roda |
| `key NOME` | aperta uma tecla |
| `type texto` | digita |
| `settle` | espera a composição e as miniaturas |
| `shot nome` | captura |
| `record on\|off` | grava quadros para vídeo |
| `perf on\|off` | mede quadros e latência |
| `saves limpar` | esvazia a pasta de aparências do roteiro |

O alvo pode ser o id de um controle (`hair:cabelo.curto`, `hairColor:castanho`, `save`), `scene`,
`options` ou uma fração `x,y`.

Os eventos do roteiro entram na **mesma fila** que o GLFW preenche e passam pelo mesmo controlador do uso
interativo. Não é automação do sistema operacional (a sessão é Wayland, sem ferramenta de injeção): o cursor
dos vídeos é desenhado pela interface na posição dos eventos.

## 9. Testes

`CreatorTest` (sem OpenGL, 15 testes):

| Assunto | O que verifica |
|---|---|
| Layout | cabe na tela e no painel em 6 resoluções × 5 categorias × A/B; sem sobreposição; texto cabe sem "…"; painel nunca sobre o personagem |
| Diálogos e vitrine | dentro da tela, com texto dimensionado |
| Escala | proporcional e com limites |
| Hitboxes | cada controle responde no próprio centro |
| Redimensionamento | o clique segue o layout novo |
| Clique | cada categoria recompõe a aparência certa; a expressão é só prévia |
| Apertar e soltar | precisa ser no mesmo controle; o botão direito não clica |
| Arrasto, roda e rolagem | arrasto com captura; roda dá zoom ou rola conforme a região; limites; rolagem com fim |
| Câmera | suave, com limites, foco na cabeça |
| Restaurar | confirmação, cancelar, preset canônico |
| Salvar e carregar | v2 (lê v1), nome com acento, nunca sobrescreve, lista, arquivo inválido |
| Teclado | o fluxo inteiro sem mouse |
| Indisponível | com motivo, nunca aplicado, nos dois sentidos |
| Cliques rápidos | 108 escolhas terminam na última; obsoletos descartados; cache limitado |
| Cache | acerto instantâneo; sai o menos usado |

Os testes da rodada 07 (identidade dos presets) continuam iguais. O gate pixel a pixel continua em
`tools/review7.sh gate`; para não sobrescrever o pacote da 07, use
`R=output/visual-review/round-07-2/regressao tools/review7.sh gate`.

## 9.1 Rodada 09.4

- **Categoria Roupa** (ícone de bota em pixel): seção "Botas" com miniaturas reais enquadradas nos pés
  (enquadramento `pes` do `App.renderThumb`); "Do modelo" é o componente nativo `botas.originais`.
- **Rosto**: "Formato dos olhos" (miniaturas com enquadramento `olhos`, close na linha dos olhos) e "Cor dos olhos"
  (antes "Olhos"). No ateliê (variante B), que tem pouca altura, o formato vira botões de alternância sob um único
  título "Olhos" — com miniaturas a fileira saía da tela em 2560×1080 e 1280×720 (`CreatorTest` pegou).
- **Cabelo**: com mais de 4 penteados a grade usa 3 colunas (com 2, a "Cor do cabelo" saía do painel e o clique em
  "loiro" caía num controle recortado).
- **Opção indisponível**: penteado incompatível com a cobertura mostra "Não combina com o chapéu atual"; cobertura
  incompatível com o penteado, "Não combina com o penteado atual". Primeira regra real: crespo × capuz C3.
- **Sons**: `CreatorController.sound` emite eventos semânticos (`ui.selecionar`, `ui.confirmar`, `ui.cancelar`,
  `ui.abrir`, `ui.salvar` só ao gravar de fato); `./gradlew editor` abre o áudio; roteiros ficam mudos (loopback
  opcional para gravar).
- **Composição assíncrona**: pedidos tirados da fila por um pedido mais novo contam como descartados (antes não
  contavam e o `CreatorTest.rapidRequestsEndOnTheLastChoice` falhava com a CPU cheia).
- Roteiros: `tools/criador/cabelos-r09.txt`, `olhos-r09.txt`, `roupa-r09.txt`.

## 10. Revisão visual

`tools/review72.sh tudo` gera:
- capturas determinísticas de cada estado: categorias, seleção, paletas, coberturas, close, corpo, nome,
  salvar, restaurar, opção indisponível, carregar, concluir, vitrine, 720p, 1080p, ultrawide e layout B;
- o vídeo do fluxo completo com cursor;
- a medição de desempenho;
- os testes.

Olhe sempre:
- o texto em 720p;
- a miniatura igual ao que o personagem mostra depois do clique;
- o rosto inteiro no foco de rosto;
- a cobertura reconhecível na miniatura;
- o painel sem esconder o herói;
- nenhuma métrica técnica na tela do jogador.
