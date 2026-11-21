# Relatório da Rodada 13.1 — A mochila viva

**Situação:** ramo LOCAL `feature/inventory-and-loot`, sobre a rodada 13 (`26806fa`). Sem push, sem PR e sem tag; o
candidato `inventory-foundation-v0.7` não foi criado. Publicar só depois da revisão do pacote e do teste físico.

**Pacote:** `output/visual-review/round-13-1/`, reproduzível com `tools/review131.sh`.

## 1. O pedido

O proprietário testou a rodada 13. Aprovou as fontes, a cataplasma e o save ("as demais coisas estão boas") e reprovou a
mochila. Pediu:

- um visual mais bonito e reconhecível, no estilo das referências que separou (RPG voxel de painel translúcido, RPG
  sombrio de itens sobre pedestal), sem copiar;
- inventário infinito;
- transparência (o mundo aparece atrás);
- poder se mover com a mochila aberta;
- o seletor seguindo o mouse;
- o jogo continuando a rodar;
- itens mais reconhecíveis.

O plano foi aprovado antes do código ([ADR 0032](../decisions/0032-mochila-viva.md)).

## 2. O que mudou

| | Rodada 13 | Rodada 13.1 |
|---|---|---|
| **Onde** | painel de pergaminho opaco no centro, tela escurecida | painel de tinta translúcida na metade direita; o mundo e o herói à vista |
| **Jogo** | parado (`State.INVENTORY`) | correndo (`GameLogic.bagOpen`, camada sobre a exploração) |
| **Herói** | parado | anda, corre, pula e esquiva (WASD, Shift, Espaço, Alt); E usa o mundo |
| **Mouse** | clica nos espaços | é da mochila: o seletor segue o mouse, o clique usa, o direito também, a roda rola; câmera parada, sem golpe nem escudo |
| **Espaços** | 24 | sem limite (lista de pilhas que rola; teto técnico de 4096 só para o leitor de save) |
| **Organização** | — | abas: Tudo e as quatro categorias |
| **Itens** | ícones de pixel 14 × 14 | modelos voxel (`tools/itens_e.py`) pelo mesmo renderizador da cena, com fundo transparente |
| **Detalhe** | ícone grande e botão | o modelo girando, nome, categoria, quantidade, descrição e etiqueta de uso (verde ou o motivo) |
| **Pilhas** | ervas 20, cataplasma 5, pedra 10, madeira 20 | 99, 20, 99, 99 (sem limite de espaços, a pilha só organiza) |

**O que ficou igual:**

- **Regras e conteúdo:** as fontes, a regra da cataplasma (só fora da luta), tudo ou nada e uma vez por fonte;
- **Save:** o formato, com os campos opcionais, sem versão nova;
- **Mundos:** os congelados, e o combate.

## 3. Como foi feito

- **Os modelos** (`tools/itens_e.py` → `assets/items/e/`, asset gerado, fora do git):
  - cinco objetos de mão em voxel 1/40 u, até 0,4 u, na paleta e na sombra em bandas da E;
  - silhueta e cor dominante distintas (maço em leque verde, rolo deitado creme, lasca cinza de pé, X de aduelas castanho,
    caderno vinho);
  - o gerador recusa modelo maior que 0,4 u.
- **O fundo transparente** (`Renderer.Toggles.alpha` + `uAlphaOut` em `post.frag`): sem céu, e o alfa sai da cobertura (4
  amostras de profundidade com o supersampling 2×) e do contorno. Desligado, o caminho é o de sempre, pixel a pixel
  (§6).
- **`ItemRenders`:**
  - **Renderizador próprio:** um `Renderer` só dos itens, para nunca redimensionar os alvos do mundo.
  - **Atlas:** uma célula de 192 px por modelo, enquadrada pelos 8 cantos da caixa, feito UMA vez no primeiro quadro da
    aventura.
  - **Prévia:** 384 px, o modelo girando sob luz fixa, refeita só com a mochila aberta e um item selecionado.
  - **Ordem:** tudo antes do lote 2D.
- **A mochila sem limite** (`Inventory`):
  - lista de pilhas em ordem de chegada, que se compacta;
  - tudo ou nada e determinística como antes;
  - o save da rodada 13 (24 espaços com buracos) abre compactado.
- **A mochila sobre o jogo** (`GameLogic`, `Game`, `Input`):
  - **Camada:** `bagOpen` sobre `EXPLORE`, sem estado novo.
  - **Mouse:** com ela aberta a câmera não lê o mouse, o golpe e o escudo ignoram os botões e o cursor fica solto.
  - **Teclado:** só WASD anda (as setas e o Enter são da grade).
  - **Ações:** `pairar:i`, `clique:i`, `usar:i` e `aba:x` são ações da lógica, testadas sem GL.

## 4. A interface

- **Painel:** tinta quente a ~70% sobre o mundo, filete de pergaminho dourado, cantos em degrau de pixel, a fonte de
  sempre.
- **Grade:** 7 × 5 visíveis; cada item sobre um disco de sombra, com a quantidade no canto. O selecionado tem borda
  dourada com brilho e fica um pouco maior.
- **Fora da tela:**
  - nenhum texto de tecla (regra da 11.1);
  - o rastreador some enquanto a mochila está aberta;
  - a placa de um objeto do mundo que cairia sob o painel não aparece.
- **Resoluções conferidas:** 1280×720, 1920×1080, 1680×1050, 2560×1440 e 2560×1080.
- **Corrigido nas capturas:** o "×" que a fonte não tem, itens pequenos nos espaços, a prévia que escurecia de um lado, a
  placa por trás do painel e a grade reaberta ainda rolada (`round-13-1/problemas.md`).

## 5. Desempenho

Medido em `round-13-1/desempenho/resumo.md` (1080p, mudo, `glFinish` por quadro, binários da 13 e da 13.1 alternados).

**A máquina estava em uso:** outro programa ocupava 160% da CPU e o navegador decodificava vídeo na GPU (carga 3,6–5,9).
A mediana de cada execução oscilou muito (a MESMA vila fixa da rodada 13 deu de 9 a 49 ms). Por isso a comparação usa três
coisas:
- **a melhor repetição** de cada binário;
- **o que é determinista** (draws, triângulos, malhas, lógica, coletas);
- **medidas em PARES** no mesmo processo (fechada e aberta alternadas a cada 3 s), cuja diferença cancela a carga que muda
  devagar.

**Mochila fechada (13 × 13.1):**

| Cenário | Melhor repetição (13 → 13.1) | Determinista |
|---|---|---|
| vila fixa (6 vezes cada) | 9,28 → 8,71 ms | draws 114, triângulos 492 840, lógica 0,18 → 0,17 ms, 0 coletas: iguais |
| região v2 s7 (5 vezes cada) | 7,94 → 8,16 ms | draws 96, triângulos 367 210, lógica 0,27 → 0,30 ms, 0 coletas |
| malhas | 206 → 211 (106,1 → 107,0 MB) | os 5 modelos dos itens, feitos no primeiro quadro da aventura |
| menu | iguais até entrar num mundo | depois, as mesmas +5 malhas |

Fechada, a mochila não tem custo relevante: dentro do ruído, com a mesma cena.

**Mochila aberta sobre o jogo correndo:**

- **Em pares** (12 pares, mesmo lugar, com a prévia girando): **+1,26 ms** de mediana.
- **Andando** (7 s em linha reta, melhor de 2): fechada 18,92 ms; aberta numa aba vazia (sem prévia) 19,77 ms (+0,85);
  aberta com a prévia 20,17 ms (+1,25).
- **A mochila grande** (283 pilhas): fechada 19,24 ms, aberta 20,69 ms (+1,45).
- **Lote 2D:** desenhar em camadas levou de **88 para 6** chamadas de desenho por quadro, com a mesma imagem (0 pixel de
  diferença).
- **Recursos:** abrir e fechar 20 vezes não muda nada: 10 FBOs e 146 malhas vivos antes e depois, 0 coletas.
- **O atlas:** 75–230 ms, uma vez por processo, no primeiro quadro da aventura (ainda sob o esmaecer da entrada).
- **O save com 283 pilhas** (28,8 KB): gravar e validar 7,9 ms de mediana, ler 1,5 ms.

O alvo do plano (até +1,5 ms aberta, 0 coletas) foi cumprido nas três medidas.

## 6. Verificação

| O quê | Resultado |
|---|---|
| Testes | **498** passam, 0 falhas, 1 pulado (o de sempre). Eram 489; `InventoryFlowTest` foi refeito para o jogo correndo (17 → 24), `InventoryTest` 10 → 12 |
| Clone limpo | compila e testa sem a `.venv` do checkout, **0 avisos** do `-Xlint:all`, 498 testes (`registros/clone-limpo.txt`) |
| Mundos congelados | 300 sementes v1 e 200 v2 **idênticas** à rodada 13 (arranjo completo); gerador, perfil da v2, `descobertas_e.json`, `habitantes_e.json` e `regioes-v1/v2.txt` sem diferença desde `procedural-world-v0.6` |
| Render intocado com o modo transparente desligado | gate dos presets **51 pares, 0 pixel**; **22 referências** equivalentes |
| Validadores | armas, interrupção fina, golpe andando, esquiva e cabelos ok; oclusão só com a exceção conhecida do arco |
| Documentação e áudio | links 0 quebrados; áudio 47 arquivos, 0 alerta |
| Saves | as matrizes da v0.6 e da rodada 13 dão 8/8 iguais, 0 `.invalido`; o save da 13 com itens abre igual (vídeo 03) |
| Combate, câmera, colisão, jogador | nenhum arquivo mudou desde a rodada 13 |
| Dados do proprietário | só copiados; sha256 iguais antes e depois em todas as etapas |
| Clone do artista | não tocado (nenhum arquivo mais novo que o início da rodada; status limpo) |

**Vídeos** (com o som da aplicação):

- **01, 2 min 39 s:** do menu ao continuar, com a mochila aberta andando e na luta. Vida 6 → 4 com ela aberta, a
  cataplasma recusada, a luta vencida a 2/6 e a cataplasma aplicada: 4/6.
- **02:** saves da v0.6.
- **03:** o save da rodada 13 com itens.

Os encostos curtos da câmera perto da barraca da Índira e dentro da ruína, logo depois de continuar, são os mesmos já
vistos na rodada 13; a câmera não mudou.

## 7. Respostas ao pedido

1. **"Pegada" das referências, sem copiar?**
   - **Das referências:** o painel translúcido sobre o mundo, as abas, a grade que rola com o item em 3D e a quantidade
     no canto, o item sobre um disco de sombra e a faixa de nome e descrição embaixo.
   - **Do Valumo:** a tinta quente, o filete de pergaminho dourado, os cantos em degrau, a fonte de pixel própria e os
     modelos voxel na paleta da E.
   - O repositório não cita jogo nenhum.
2. **Inventário infinito?** Sim. Mil cadernos entram (teste). O único teto é técnico (4096 pilhas), para o leitor de save.
3. **Transparência?** Sim. O painel é tinta a ~70%, e o mundo aparece atrás (capturas).
4. **Mover-se com o inventário aberto?** Sim: WASD, Shift, Espaço e Alt (teste e vídeo 01).
5. **O seletor segue o mouse?** Sim, ao passar; o clique usa (teste e vídeo 01).
6. **O jogo continua rodando?** Sim: o tempo de jogo corre, o fungo continua atacando e o salvamento automático acontece
   com ela aberta (testes; no vídeo 01 o herói perde dois corações com a mochila aberta).
7. **Itens mais reconhecíveis?** Cinco modelos voxel com silhuetas distintas, renderizados pelo próprio jogo
   (prancha dos modelos; teste de silhuetas).
   - **Arte:** são a primeira versão; a final é do agente artista, pelo contrato novo.
8. **O resto ficou igual?** Sim:
   - **Fontes:** presente e achados, uma vez, tudo ou nada.
   - **Cataplasma:** só fora da luta.
   - **Save:** mesmo formato; os da v0.6 e os da rodada 13 abrem (vídeos 02 e 03).
   - **Mundos:** v1 e v2 idênticos (§6).
   - **Combate, câmera e colisão:** nenhum arquivo mudou.
9. **Ficou mais pesado?** Fechada, não. Aberta, cerca de +1,3 ms (§5).
10. **O que fica para o teste físico e depois:**
    - **Teste físico:** P107, pendente.
    - **Painel na luta (P108):** cobre o lado direito da tela.
    - **Rolagem (P109):** rolando com o mouse parado, a seleção pode mudar.
    - **Itens no mundo (P110):** os modelos ainda não aparecem no chão nem na mão.
    - **Rodada 14 recomendada:** o drop do fungo-cardo. Daria item à vila fixa e à v1 (P100) e usaria os modelos no chão.
