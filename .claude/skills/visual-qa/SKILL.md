---
name: visual-qa
description: Revisar visualmente qualquer mudança do jogo com capturas reais do viewer — comparação controlada no modo LINHA, silhueta, argila, luz neutra, tamanho reduzido, close de rosto, cena, vídeo de animação (MP4/GIF), pacote --review2, revisão de interface por roteiro de entrada (tools/review72.sh) e checklist de aceitação. Use sempre que algo visual mudar, antes de declarar pronto. Rodada 09.2: vídeos de combate com som da aplicação (tools/review92.sh), comparações no mesmo roteiro (binário 09.1 × 09.2, perfil A × C, morte antiga × D1 × D2 lado a lado), capturas em 1080p e 720p e pranchas de animação com pernas. Rodada 11.1: menu, personagens, mundos e aventura por roteiro com dados temporários, resoluções e leiautes A × B (tools/review111.sh).
---

# QA visual (rodada 02 em diante)

Processo completo: `docs/validation/processo-de-revisao-visual.md`. Critérios:
`docs/validation/checklist-de-aceitacao.md`. **Nada é "aprovado" sem captura inspecionada** — e a
aprovação final da identidade é do proprietário, não do agente.

## Comandos

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
tools/snap.sh "<spec>" ["<spec>" ...]          # REBUILD=1 após mudar Java/GLSL; MODULES="chars_e" regenera
tools/anim.sh output/tmp/x "<spec>" 60 30      # vídeo real: MP4 + GIF
.venv/bin/python tools/sheet.py grid out.png 3 a.png b.png c.png   # prancha
.venv/bin/python tools/sheet.py seq out.png 6 "output/tmp/f_###.png"
viewer/build/install/viewer/bin/viewer --review2 [personagens,arquetipos,mundo,criaturas,performance]
```

## Chaves de captura (Main.java)

`style=a..e` · `mode=scene|char|side|prop|lineup` · `set=<App.LINEUPS>` (variantes, v1-v2-v3, herois,
herois-b-e-d, criaturas, criaturas-b-e) · `hero=characters/e/ranger` · `char=N` · `clip=` · `t=` ·
`yaw= pitch= dist= fov= ty=` · `myaw=` (gira os modelos) · `hx= hz= hyaw= lift=` · `preset=dia|tarde|neutra`
· `outline=0..3` · `fog=0..3` · `sil=1` · `notex=1` (argila) · `expr=neutral|joy|determined|surprise`
· `quality=baixa|padrao|alta` · `frames=N,fps=F,move=1` · `nohud=1` · `size=LxA` · `out=`.

## Regras de comparação

1. Uma variável por vez. 2. Modo LINHA (câmera tele fixa); para ângulos, `myaw`, nunca câmera em 3/4.
3. Sempre também em luz neutra. 4. Tamanho reduzido = render real em baixa resolução, não só reduzir.
5. Movimento = vídeo ou prancha de quadros consecutivos, não um quadro solto.
6. Toda câmera nova: confira se não está dentro de um objeto.

## Defeitos que já apareceram (procure primeiro)

Rosto fragmentado (decalque em superfície curva) · xadrez na nuca (mechas finas) · faixa escura na
testa (sombra profunda em cabelo claro) · forro claro de capuz = tiara · arma atravessando cabeça ou
aba · trança horizontal na corrida · degrau serrilhado escuro no terreno · névoa no plano do herói ·
contorno riscando tufos/degraus · copa "couve-flor".

## Entrega

Iteração em `output/tmp/` (ignorado); entrega em `output/visual-review/round-NN/` (nunca sobrescrever
rodadas anteriores). Registre defeito, severidade, correção e situação no relatório da rodada.

## Rodada 03

- Pacote completo: `viewer --review3 [personagens,animacoes,criaturas,terreno,vegetacao,iluminacao,comparacoes,performance]`.
- Silhueta válida = fundo uniforme, sem chão/sombra, figura inteira, frente/perfil/costas.
- Escala de exploração: renderize **em baixa resolução** e amplie sem filtro; não reduza a captura grande.
- Compare variantes por **diferença de pixels** antes de julgar: se duas variantes diferem < 1%, a
  mudança provavelmente não está sendo renderizada (o chanfro do relevo ficou invisível por
  culling e passou despercebido até essa medição).
- Movimento: `tools/anim.sh` (MP4/GIF) + `party=1` para os três heróis juntos na cena.

## Rodada 05 — referências canônicas e prova de reprodução

- **Compare com as referências**: `tools/reference.sh output/tmp/ref` refaz as 22 imagens oficiais com as
  mesmas specs (`docs/art/reference/manifesto.tsv`); `.venv/bin/python tools/repro_compare.py imgs
  docs/art/reference output/tmp/ref` mede a diferença (critério declarado: < 0,5% dos pixels acima de 8/255).
  Mudou de propósito → refaça as referências e registre o motivo; nunca troque uma referência sem aprovação.
- **Reprovados** (`docs/art/reference/reprovados/`): procure esses defeitos primeiro.
- **Auditoria estrutural** antes do olho: `python3 tools/asset_audit.py PASTA` (voxels soltos, fragmentos, órfãos). Ela só
  relata — sai sempre com 0: leia a última linha, não use em cadeia com `&&`.
- **Vídeos**: velocidade normal **e** câmera de jogo (`mode=scene`, câmera padrão) — a meia velocidade só
  como apoio. A câmera de jogo é onde o jogador vê (`ref 22`).
- **Validado ≠ aprovado**: o agente registra evidências; aprovação artística é do proprietário.
- Câmera de close: `dist=1.3,ty=0.93` (maga `1.45/0.98`); câmera nova sempre conferida (dentro da casa já aconteceu).

## Rodada 05.1 — inspeção OBRIGATÓRIA de animação em vídeo

Pranchas de 12 quadros escondem defeitos de transição (a lâmina sumindo atrás do braço na rodada 05 só
aparecia no MP4). Procedimento para qualquer animação nova ou alterada:

1. **Grave**: `tools/anim.sh <saída> "<spec>" <quadros> 30` em 4 câmeras — frente (`yaw=0`), perfil
   (`yaw=±90`), costas (`yaw=180`) e 3/4 (`yaw=35` e `yaw=-40`) — ou a grade 2×2 de `tools/review51.sh` (`quad`).
2. **Assista o MP4 inteiro em velocidade normal**, depois quadro a quadro nas transições (repouso → guarda,
   guarda → repouso, golpe → HIT). Para quadro a quadro denso:
   `ffmpeg -i x.mp4 -vf "select='not(mod(n,3))',scale=240:-1,tile=6x3" -frames:v 1 x.png`.
3. **Procure**: arma entrando na silhueta ou sumindo atrás do braço/tronco; arma diante do rosto; item
   girando sem a mão (ou mão girando em volta do item parado); estalos de orientação; mochila/cachecol/trança
   atravessando o corpo; pés deslizando.
4. **Meça o que suspeitar**: `viewer --occlusion rig golpe`, `viewer --grip rig golpe`, `viewer --check`.
5. **Antes/depois equivalentes**: mesmo binário, mesma spec, lado a lado — perfis antigos com
   `JAVA_OPTS=-Dvx.anim=<pasta>`; camada secundária com `-Dvx.secondary=false`.
6. **Escala real de jogo**: grave também com a câmera de exploração (`mode=scene`, `yaw=18,pitch=13,dist=6.4`,
   herói ~1/8 da altura do quadro) e com a câmera ATRÁS do herói (`hyaw=200`). Julgue a leitura nessa escala,
   não no close. Registre por câmera onde lê e onde não lê (ex.: arco no relatório 05.1).
7. Registre no relatório: o que viu, em que vídeo e instante, e se é defeito, limitação aceita ou ok.

## Rodada 06 — vitrine e pacote de expansão

- `layout=vitrine_e` carrega `assets/scenes/vitrine_e.json` (mesmo relevo/luz; assets novos ao lado dos
  aprovados). As 22 referências e a clareira continuam a linha de base: depois de qualquer asset novo,
  `tools/reference.sh output/tmp/ref && repro_compare.py imgs` tem de dar 22/22 idênticas.
- Pacote completo: `tools/review6.sh [npc|criatura|arvore|estrutura|cena|videos|todos]`.
- Câmera de cena = órbita em torno do ponto `hx,hz`: confira se ela não caiu dentro de um tronco, morro ou
  parede (aconteceu várias vezes nesta rodada) antes de usar a imagem.
- Silhueta de prop: `tools/prop_silhouette.py` (o `sil=1` em modo cena pinta o chão também).

## Rodada 07.2 — interface do jogador

- `tools/review72.sh [capturas|video|desempenho|testes|tudo]` → `output/visual-review/round-07-2/`.
  - As capturas e o vídeo usam roteiros de entrada (`tools/criador/*.txt`) que entram na mesma fila de
    eventos do GLFW.
  - O cursor do vídeo é desenhado pela interface. Diga isso no relatório e não chame de "automação do mouse
    do sistema".
- Olhe **em tamanho real**, não só na prancha:
  - texto em 720p;
  - borda de hover × selecionado;
  - sangria de glifo (marca solta perto das letras);
  - listras em degradês (faixas sobrepostas).
- Confira que a miniatura mostra o que o personagem vira depois do clique. Cobertura discreta (C3) precisa
  de ângulo que a identifique.
- Tempo de quadro: separe o aquecimento (primeiro quadro, trabalho de GPU enfileirado antes da medição) do
  custo real. Anote cada quadro > 25 ms com a causa (miniatura, envio, atlas novo…).
- Prancha com o ffmpeg: imagens de tamanhos diferentes não entram em `-pattern_type glob` + `tile` (vira
  sequência); escale cada uma.

## Rodada 08 — jogo

- `tools/review8.sh` → `output/visual-review/round-08/`: capturas, vídeos de movimento, encosta, obstáculos,
  câmera, Kelyra e aparência personalizada, com pranchas de 12 quadros, medição e zip.
- O que olhar e os defeitos já vistos estão na skill `gameplay`.
- Separe o que foi validado por roteiro (fila de eventos) do que só o teste físico do proprietário valida
  (`docs/validation/roteiro-de-teste-fisico.md`).

## Rodada 08.1 — defeitos temporais e atualização de referências

**Cintilação, z-fighting e serrilhado rastejando não aparecem em captura parada.**
- Grave a câmera andando devagar (`tools/temporal/*.txt`).
- Meça a alternância (`alternancia.py`) e compare com a malha sem fusão (`-Dvx.merge=false`) antes de chamar algo
  de defeito do mesher.
- Diferencie o deslocamento normal (monótono) do pixel que vai e volta.

**Atualizar referências canônicas**, só com causa conhecida:
1. Gere as novas em pasta separada (`tools/reference.sh PASTA`).
2. Rode `tools/temporal/comparar_referencias.py ANTIGA NOVA DIFERENCAS`: pixels alterados, mapa e pontos claros
   isolados.
3. Inspecione cada mudança ampliada:
   - borda mais limpa ou cor esperada (dentes) → aceite;
   - fresta, buraco ou perda de detalhe → corrija o renderer antes.
4. Mova as antigas para `docs/art/reference/anteriores/rNN/`, copie as novas e anote no `manifesto.tsv`
   (commit, data, motivo).
5. Rode de novo o gate e a comparação (0/255).


## Rodada 09.1 — câmera de luta e som

**Câmera (antes × depois honesto):**
- **Mesmo roteiro, mesmo instante lógico, dois binários:** o binário da rodada anterior fica guardado em
  `output/tmp/game-09/`. A IA não depende da câmera, então o mundo é o mesmo nos dois.
- **Números do antes no binário novo:** use o diagnóstico (`debug camera-09 on`) e confira pixel a pixel que ele
  reproduz o binário antigo (`tools/review91.sh camera` faz e registra).
- **Não escolha só posições boas:**
  - inclua os piores casos da varredura (`CombatCameraTest`);
  - rode o roteiro exato que mostrou o defeito (`tools/combate/camera.txt`).
- **Junto de cada captura:** imprima `enquadramento` (herói % da tela, fungo % visível) e olhe a imagem. O medidor é
  aproximado.

**Som:**
- **Vídeo com som = som gravado pela aplicação:** `-Dvx.som=loopback` grava `audio.wav` no relógio do jogo. Nunca
  monte trilha por cima do vídeo e chame isso de sincronia.
- **Sincronia:** `tools/audio_sync.py` mede por correlação do envelope; sons sobrepostos não entram na conta.
- **Sons isolados:** `tools/audio_board.py` dá forma de onda + espectrograma + números. Linhas verticais no
  espectrograma são estalos.
- **O ouvido é do proprietário:** a revisão por números não aprova timbre.
- **Dispositivo real sem barulho:** coletor nulo temporário (`pactl load-module module-null-sink` + `set-default-sink`),
  captura com `parec`, e restaure o padrão no fim. `PULSE_SINK` não é respeitado pelo OpenAL Soft.

**Zip:** só capturas, pranchas e documentos. Sem MP4, sem WAV e sem imagens de regressão (resumos em texto).

## Rodada 09.2 — o que revisar e como (`tools/review92.sh`)

- **Movimento se julga em vídeo contínuo, não em prancha.** As pranchas 4×4 servem para achar o trecho; a decisão é no
  MP4.
- **Antes × depois no MESMO roteiro:**
  - binário da 09.1 guardado (`output/tmp/game-091`, com `output/tmp/assets-091`) × atual;
  - perfil A × C no mesmo binário (`-Dvx.sensacao`);
  - morte antiga × D1 × D2 lado a lado (`-Dvx.morte`).
- **Animação de perto:** use o viewer com câmera fixa (`tt=0`, `myaw=90`), não a câmera do jogo. Ela segue o herói e
  desvia de obstáculos, então de trás não dá para julgar as pernas.
  - Pranchas: rolamento, recuo, golpes com pernas (`review92.sh animacao`).
- **Capturas** em 1920×1080 e 1280×720 nos mesmos instantes (`review92.sh capturas`; o tamanho é trocado no roteiro).
- **Casos difíceis entram no pacote:** carvalho e pedra (22), encurralado, fungo colado, giro rápido.
  - A câmera é medida com `CameraModesTest` (livre × travada × 09.1).
- **Som:** os vídeos usam o loopback da aplicação. Nunca toque teste nos alto-falantes.

## Rodada 09.3 — comparação de gestos

- **`tools/review93.sh animacao`:** grava o visualizador com a câmera fixa, em 1× e 0,25×.
  - Gestos da esquiva R2 e o recuo (perfil, três quartos, frente, costas). Na entrega da 09.3 houve 09.2 × R1 × R2 lado
    a lado: compare variantes assim antes de escolher.
  - Salto antes × depois (parado, andando, correndo).
  - Pranchas de 12 instantes.
- **`antes`:** os mesmos roteiros do jogo no binário da 09.2 guardado, lado a lado com a 09.3.
- **Quadros soltos enganam no rolamento:** veja o vídeo lento. Procure cabeça no chão, perna atravessando o pomo e
  subida mecânica.

## Rodada 09.4 — áudio e personalização

- `tools/review94.sh [audio|contexto|ambiente|criador|cabelos|armas|olhos|botas|pausa|gate|testes|tudo]` →
  `output/visual-review/round-09-4/`. Vídeos do jogo saem COM SOM (mistura gravada pelo loopback, em sincronia).
- Captura de evidência pode ser sobrescrita pela próxima rodada de capturas: copie o "antes" para `comparacoes/`
  antes de corrigir (na 09.4 a imagem do defeito de layout se perdeu).
- `--check-cabelos` (arma × cabeça composta) entra no portão de qualquer mudança de cabelo, rosto ou golpe.

## Fechamento do QA — interface e captura

- Interface em 7 resoluções (inclui 16:10 e 4:3) com lista longa e NPC alto: `tools/qa/resolucoes.txt` (pasta de aparências
  temporária com `-Dvx.saves=`). Nada cortado, nada sobreposto, nada fora do painel; nome longo reduz o passo da fonte e
  só então termina em "…".
- `snap` aceita `kit=DIR` (equipamento da aparência composta, como no jogo).
- Referência canônica: só se refaz com a causa PROVADA (código final × arquivo suspeito revertido, desvio 0) — skill
  `engineering-qa` §7b.

## Rodada 11.1 — porta de entrada (`tools/review111.sh`)

- **Dados SEMPRE temporários:** cópias das aparências e do save do proprietário. O script confere por sha256 os
  originais antes e depois e escreve "dados do proprietário: intactos".
- **Roteiros** (`tools/menu/`), na mesma pasta, em sequência:
  - `01` menu principal e tomadas;
  - `02` personagens (criar, girar, editar, cancelar);
  - `03` mundos (prévia, criar, semente sorteada, recusa simulada com `-Dvx.qa.recusar=4242`, mesma semente em dois
    mundos);
  - `04` aventura (o save antigo adotado sozinho, continuar, voltar ao menu salvando ou não);
  - `07` resoluções (720p, 1080p, 16:10, 21:9, leiautes A × B);
  - `08` vídeo com som por loopback.
- **Fundo do menu:** olhe cada tomada (`menu-tomada T`). O teste de colisão não pega morador colado na borda do quadro nem
  um prop que domina a imagem.
- **Vitrine dos personagens:** o herói precisa ler sozinho. Morador colado ou atrás confunde a silhueta (escondidos só no
  desenho); relevo na frente da câmera vira um bloco enorme (aconteceu no ponto de início da vila: a vitrine tem ponto
  próprio, testado).

## Rodada 12 — mundos v2 (`tools/review12.sh`)

- **Vistas:** `vistas` roda `tools/mundo/04-vistas-v2.txt` nas 8 sementes, com 15 tomadas cada:
  - geral, assentamento e três construções;
  - trilha, ruína e posto;
  - câmera do jogador, entrada, morador, entrada e interior da ruína;
  - tarde.
  Cada semente sai numa prancha. As tomadas são calculadas da região, nunca coordenada à mão.
- **Comparações:** `comparacoes` monta v1 × v2 lado a lado, pelo MESMO binário (`-Dvx.gerador=1|2`).
- **O que olhar:**
  - conjunto encostado em outro ou no chafariz;
  - morador sem espaço ou de frente para uma parede;
  - porta bloqueada;
  - herói afundado em piso elevado;
  - árvore sobre a trilha;
  - prop em desnível.
- **Vídeos:** `demo` (mundo v2 do menu à descoberta, salvar e continuar) e `demo-v1` (mundo v1 antigo no executável
  novo). Quadros em `output/tmp`, nunca no `/tmp` (tmpfs: o vídeo de 1080p estoura a cota).
