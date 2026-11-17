# Relatório — Rodada 08: primeira exploração jogável

Pacote local (fora do git): `output/visual-review/round-08/` (`LEIA-ME.md`) e
`output/visual-review/rodada-08-imagens-e-documentos.zip`. Regenerável com `tools/review8.sh tudo`.
Nada aqui é declarado aprovado: a aprovação é do proprietário, e o teste com teclado e mouse físicos também
([roteiro](roteiro-de-teste-fisico.md)).

## Como executar

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo            # escolha de aparência → vila (gera os assets antes, se preciso)
./gradlew editor          # criador de personagens (07.2)
./gradlew editorDebug     # editor técnico (07)
./gradlew test            # 119 testes
tools/review8.sh tudo     # capturas, vídeos, medição, testes, zip → output/visual-review/round-08/
```

## 1. Auditoria inicial

- **Git:** limpo no commit `2cf88c3`.
- **Testes:** 98 passando (o número da 07.2 confirmou).
- **Teste físico da 07.2:** não havia registro dele. Os dois arquivos em `output/aparencias/` são de 13:31 e
  13:35, do editor técnico da rodada 07. O `results.zip` na pasta da 07.2 é o próprio pacote compactado. Por isso
  o editor não teve correção funcional nesta rodada, e o roteiro de teste físico agora cobre criador e jogo.
- **O que já existia:**
  - herói andando com WASD na cena do viewer, em velocidade fixa, sem colisão, com chão suavizado;
  - IK de pé;
  - compositor assíncrono;
  - `UiBatch` e fonte própria;
  - vila com quatro NPCs.

## 2. Arquitetura

- **Novo módulo `game`**, que depende de `engine` e `viewer`. As regras do jogo ficam nele, e as testáveis não
  usam OpenGL.
- **No viewer**, só ganchos sem regra de jogo:
  - `Terrain`: colunas do relevo, a mesma grade que o `World` desenha;
  - `World.placements()` e `World.bodies()`;
  - `UiKit`: peças do criador, extraídas sem mudar um pixel;
  - `Creator.start()` e o botão "Levar para a vila".
- **Na engine:** o evento `KEY_UP` (com a marca de repetição no `KEY`), `captureCursor` e as setas na
  `PixelFont`.
- **Decisão sobre o módulo `runtime`:** ficou para depois (ADR 0013, com o critério de quando separar).
  Documentação: [jogo.md](../architecture/jogo.md).

## 3. Personagem controlável

- **Andar, correr e frear:**
  - andar e correr nas velocidades da passada do `Animator` (1,14 e 2,33 u/s);
  - aceleração de 9 u/s² e frenagem de 11 u/s²;
  - giro limitado a 11 rad/s.
- **Independência de FPS:** subpassos fixos de no máximo 1/120 s, então o percurso é o mesmo de 17 a 240 FPS
  (teste).
- **Animação pela velocidade real:** a fase avança pela distância percorrida, então o pé de apoio não patina
  nem na aceleração (teste). As trocas entre parado, andar e correr fazem mistura de 0,2 s e mantêm a fase.
- **Altura:** a lógica pisa no topo do voxel; o desenho suaviza o degrau de 1 voxel; o IK dos pés usa o mesmo
  relevo.

## 4. Câmera em terceira pessoa

- **Controles:**
  - o mouse gira, com sensibilidade ajustável na pausa e eixo vertical invertível;
  - a roda vai de 2,2 a 8 u;
  - a inclinação vai de −0,10 a 1,05 rad.
- **Distância padrão 4,6 u**, comparada com 3,1 e 5,9 u nas capturas 08 a 10.
- **Suavização:** o ponto seguido acompanha rápido no plano e devagar na altura, então a câmera não quica nos
  degraus (teste).
- **Linha de visão** contra o relevo, as paredes, os troncos e as copas:
  - encosta na hora e volta devagar;
  - junto a paredes, desvia o rumo e a inclinação o mínimo necessário, sem mudar o rumo escolhido pelo jogador.
- **Enquadramentos próprios** para a escolha de aparência (herói de frente) e para a conversa (os dois de lado).

## 5. Colisões e terreno

- **Relevo T1:** a altura vem das mesmas colunas de voxel desenhadas.
- **Degraus:** até 0,30 u sobe (2 voxels); acima disso bloqueia.
- **Descida:** até 0,40 u acompanha o chão; mais que isso cai com gravidade.
- **Água e limite:** a água e a margem rente a ela não são pisáveis; há limite circular.
- **Formas por tipo de prop** em `assets/scenes/colisao_e.json`, separadas da malha:
  - caixas orientadas;
  - círculos;
  - troncos com a copa só para a câmera;
  - plantas atravessáveis.
- **NPCs e criaturas** são círculos.
- **Deslize** ao longo de paredes.
- Não é motor físico e não tem dependência nova.

## 6. Cena jogável

- **Arquivo:** `assets/scenes/jogo_vila_e.json`, uma cópia controlada da vila. `vila_e` e `clearing_e` não
  mudaram, e os testes conferem que as duas continuam sem a seção de jogo.
- **Acréscimos:**
  - poço;
  - cerca;
  - caixotes e barril da estalagem;
  - postes de lanterna;
  - tronco, toco, pedras, placa e moitas;
  - juncos e nenúfar.
- **Percursos testados:** o caminho plano, a encosta até o patamar da estalagem (0,88 u), os obstáculos e a
  margem do lago.

## 7. Interação com a Kelyra

- **Quando aparece:** a 1,8 u ou menos e com até 80° de ângulo, surge sobre ela a indicação "E Conversar com
  Kelyra".
- **Durante a conversa:**
  - o herói para e vira para ela;
  - ela vira para ele, acena (o `EMOTE` existente) e muda de expressão por fala.
- **Painel:** plaqueta "Kelyra · Estalajadeira", quatro falas originais, **Continuar (E)** / **Até logo** e
  **Sair (Esc)**, com clique ou teclado.
- **Saída:** volta a explorar no mesmo lugar.
- **Sem** árvore de diálogo, missões ou serviço externo.

## 8. Aparência personalizada

- **Escolhas:** os três presets e as aparências salvas (`valumo.aparencia` v1; arquivo inválido aparece com o
  motivo).
- **Criador dentro do jogo:** **Criar no editor** (ou **Abrir o criador** na pausa) abre o criador da 07.2 na
  mesma janela. **Pronto → Levar para a vila** traz a aparência nova.
- **Mesmo pipeline:** compositor, rig e animações (`AsyncComposer`/`Composer`).
- **Sem classe:** a aparência não define classe.
- **Vídeo 06:** a "Brisa" salva pelo criador, editada (topete ruivo, chapéu de aba, nome "Brisa da vila") e
  andando na vila.

## 9. Entrada e interface

- **HUD mínima:**
  - linha de controles (some em 40 s; `F1`);
  - indicação de interação;
  - mensagens;
  - "Preparando…".
- **Pausa** com sensibilidade e inversão vertical.
- **Peças visuais:** `UiKit` e a fonte própria.
- **Cursor:** preso na exploração e solto nos menus e na conversa.
- **Saída:** pelo botão ou pelo X da janela, com `close()` liberando compositor, renderer, FBO e interface.
- **Controles:** README e [jogo.md](../architecture/jogo.md).

## 10. Combate

Não implementado: dano, IA hostil, loot, experiência, missões, inventário e equipamento com atributos. As
interrupções por HIT da espada (7/21 cortes) e da lâmina longa (18/47) continuam **abertas** em
[problemas-conhecidos.md](problemas-conhecidos.md).

## 11. Testes

| Conjunto | Testes |
|---|---|
| Total | **119, 0 falhas** |
| Anteriores | 98 |
| `GameplayTest` (novo) | 21 |

Cobertura do `GameplayTest`:
- FPS e aceleração;
- giro e animação pela velocidade;
- obstáculos, parede, degraus e desnível;
- câmera estável, parede, corredor, morro e limites;
- cena real e relevo desenhado;
- passeio sem entrar em prop nem ficar preso de verdade;
- água e alcance até a Kelyra;
- interação, diálogo, pausa e mouse;
- aparências.

Regressão:

| Verificação | Resultado |
|---|---|
| `--check` | TODOS OK |
| `--occlusion` | só a exceção conhecida do arco |
| Gate dos presets | 51 pares, 0 diferenças |
| Referências canônicas | 22/22 a 0/255 (o `World` agora usa o `Terrain`) |
| Criador | 33 capturas idênticas às da 07.2, pixel a pixel (`UiKit`, fonte) |

Em `round-08/regressao/resumo.txt`.

## 12. QA de gameplay

Feito por roteiros de entrada: tecla segurada, mouse movido, clique, e o comando `ir`, que mira com o mouse
enquanto segura W. Os eventos passam pela mesma fila e pelo mesmo código do jogo interativo. Junto, o teste de
fumaça na janela real.

**Não houve jogo manual com mãos no teclado** (veja §19); isso fica no roteiro físico.

Achados e correções, nesta ordem:

| Achado | Correção |
|---|---|
| Câmera nunca voltava à distância pedida | folga só quando bloqueia |
| Escolha mostrava as costas do herói | primeiro quadro já no enquadramento |
| Postes na linha de visão da escolha e na rota | movidos |
| Câmera dentro da cabeça junto à estalagem | desvio de rumo e inclinação; mínimo de 0,9 u |
| Câmera atravessando a parede por alguns quadros | encosta na hora (pego no teste) |
| Herói "dentro" do lago | margem rente à água deixa de ser pisável |
| Picos de 20 a 30 ms na medição | eram aquecimento da GPU; a medição agora aquece 7 s, como o bench |

Detalhes no `relatorio-gameplay.md` do pacote.

## 13. Desempenho

Medido em 1920×1080, qualidade padrão (FXAA), Iris Xe, janela oculta. Antes de medir, 7 s de aquecimento.

| | Mediana | p95 | p99 / pior 1% | Máximo | Draws | Triângulos |
|---|---|---|---|---|---|---|
| Jogo, percurso de ~25 s (lógica + desenho + interface, `glFinish`) | 8,44 ms | 10,60 ms | 11,38 / 11,72 ms | 13,33 ms | 94 | 373 mil |
| Viewer na **mesma cena** (`--layout jogo_vila_e --bench`, herói andando, câmera orbitando) | 9,04 ms | 10,32 ms | 1% low 82 FPS (≈ 12,2 ms) | — | 74 | 318 mil |

- **Custo da lógica do jogo:** média de 0,42 ms e p95 de 1,9 ms. Inclui entrada, corpo, colisão, câmera com a
  busca de desvio, NPCs e composição.
- **Janela real:** 1600×900 com vsync, 59,7 FPS.
- **Comparação:** as duas medições usam a mesma cena, mas não a mesma vista. O jogo segue o herói e desenha a
  Kelyra, o herói composto e a interface (+20 draws); o viewer orbita. Por isso a comparação é de custo
  equivalente, não de regressão quadro a quadro.
- **Sem otimização prematura:** nada foi otimizado nesta rodada.

## 14. Documentação e skills

- **Novos:**
  - `docs/architecture/jogo.md`;
  - ADR `0013-modulo-game.md`;
  - `docs/validation/roteiro-de-teste-fisico.md`;
  - este relatório;
  - skill `gameplay` (procedimentos de colisão, NPC, roteiros e testes).
- **Atualizados:**
  - README e CLAUDE.md;
  - índice de documentos e de ADRs;
  - arquitetura;
  - contrato de qualidade (seção "Jogabilidade");
  - checklist (seção "Gameplay");
  - problemas conhecidos;
  - skills `game-ui` e `visual-qa`.

## 15. Entrega

Em `output/visual-review/round-08/`:
- **Relatórios:** relatório técnico, relatório de gameplay, controles, testes (`testes.txt`), medição
  (`desempenho/`) e regressão (`regressao/`);
- **Imagens:** 14 capturas;
- **Vídeos:** 6, cada um com prancha de 12 quadros e um registro de estado:
  1. movimento;
  2. encosta;
  3. obstáculos;
  4. câmera;
  5. Kelyra;
  6. aparência.
- **Teste físico:** o roteiro para o proprietário.

O zip `rodada-08-imagens-e-documentos.zip` tem as imagens, os documentos e as pranchas (os MP4 ficam na pasta).

## 16. Limitações abertas

Em [problemas-conhecidos.md](problemas-conhecidos.md):
- entrada física não validada nesta máquina;
- câmera a 0,9 u em cantos apertados, e atrás de um poste no começo de uma conversa;
- sem pulo, e props não são superfícies;
- só a Kelyra conversa;
- NPCs parados;
- segundo renderer enquanto o criador está aberto.

Seguem abertas, sem mudança: as interrupções por HIT e o restante da lista da rodada 07.2.

## 17. Recomendação

Antes da rodada 09, o proprietário roda o [roteiro físico](roteiro-de-teste-fisico.md) (≈ 15 min) e decide:
- se a distância de câmera (4,6 u) e a velocidade do mouse estão boas;
- se a vila jogável é a base certa para o combate.

A próxima rodada só começa com pedido.

## Adendo — rodada 08.1

Depois da entrega, o proprietário jogou e aprovou a fundação. Ele apontou dois defeitos, corrigidos na
[rodada 08.1](relatorio-rodada-08-1.md):
- **Dentes:** viravam mancha branca; agora usam a cor `TEETH`.
- **Tremor nas bordas entre cores:** era z-fighting da margem anti-fresta; a malha conforme o elimina
  (ADR 0014).

Por isso:
- os números de desempenho acima são da malha antiga;
- os vídeos e capturas corrigidos estão em `output/visual-review/round-08-1/entrega/`.

