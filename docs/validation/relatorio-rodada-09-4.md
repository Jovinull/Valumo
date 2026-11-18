# Relatório — Rodada 09.4: direção sonora, primeiros estudos de áudio, integração e dois estudos de cabelo

Pacote local (fora do git): `output/visual-review/round-09-4/` (`LEIA-ME.md`). Regenerável com
`tools/review94.sh tudo` (as tomadas de efeitos sonoros NÃO são re-renderizadas: ver §4).

> **Nada desta rodada está aprovado.** Música, efeitos e cabelos são estudos. **O agente não ouviu nenhum som**:
> tudo o que se diz sobre áudio aqui é medição (forma de onda, espectro, sonoridade, sincronia), não escuta.
> A avaliação artística do áudio depende do proprietário.

> **Integração com a `main` (rodadas 09–09.3: combate, câmera, mobilidade, esquiva R2, som do combate).** Esta rodada foi
> feita em paralelo como "rodada 09", sobre a `main` antiga (`24e83df`). Ao integrar:
> - virou **09.4**: relatório, `tools/review94.sh`, pacote `output/visual-review/round-09-4/`;
> - a decisão de áudio virou **ADR 0023**, porque 0015–0022 já são do combate;
> - os dois sistemas de som viraram um dispositivo só (adendo do ADR 0023: `OpenAlBackend.shared`, ouvinte na câmera,
>   passos do catálogo, volume por voz, `assets/audio/sintese/`);
> - a pausa juntou Música, Efeitos, mudo e as opções de combate, com rótulos que cabem em 720p;
> - o `--check-cabelos` ganhou a tabela do equipamento de combate (espada e broquel em toda aparência).
>
> Números depois da integração:
> - **240 testes**, 0 falhas (os 215 da `main` e os 25 desta rodada);
> - validadores e gate (51/0) verdes;
> - QA de áudio: 43 arquivos, 0 alertas;
> - loopback do jogo: uma mistura por quadro (6,900 s para 207 quadros).
>
> **Decisões do proprietário depois da integração (todas aplicadas):**
> 1. **Síntese própria** no lugar do banco FluidR3 GM. As duas músicas e a interface foram refeitas nos sintetizadores
>    do LMMS (`tools/audio/sintese.py`), com as mesmas notas, forma e mistura; o equilíbrio foi medido de novo
>    (`tools/audio/nivelar.py`). QA: 43 arquivos, 0 alertas, emendas iguais à passagem natural. Achado no caminho: a
>    sonda de calibração do laço dependia do banco e, sem ele, adiantava o laço 50 ms em silêncio; agora usa clique
>    próprio e falha se sair muda.
> 2. **Música A padrão e B alternativa**, as duas aprovadas.
> 3. **Estudos aprovados:** crespo, coque alto, formatos de olho e botas viajante (`"aprovado": "rodada 09.4"`). Continuam
>    protegidos pela regra de componente novo.
> 4. **EMOTE corrigido** na espada e no cajado: 0 voxel em todo penteado. Isso revelou o roçar do corte vertical no ápice
>    (1–2 voxels), corrigido com 1 voxel na mão de `v_drive` e validado por toda a bateria de combate. Ficam registrados
>    o `combo` (vitrine, 1 voxel no chanel) e o `staff_cast_wide` da maga.

## Como executar

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo                      # jogo com som (música da vila A; -Dvx.musica=vila_b|nenhuma)
./gradlew editor                    # criador com sons da interface e música
./gradlew test                      # 149 testes
tools/review94.sh tudo               # pacote: áudio, contexto (vídeo com som), criador, cabelos, gate, testes
```

Para ouvir sem abrir o jogo: `output/visual-review/round-09-4/musica/vila_{a,b}/*-escuta.wav`,
`efeitos-sonoros/*/contexto-proposta-*.wav` e `integracao/jogo-contexto-com-som.mp4`.

## 1. Estado inicial (linha de base)

- **Git:** `main` em `24e83df`, árvore limpa, remoto `Jovinull/Valumo`. Sem `output/` e sem assets gerados (clone
  limpo). Rodada mais recente: **08.1**. Não havia combate, esquiva, inimigos nem chefes (a rodada 09.2 citada no
  pedido não existe neste clone).
- **Linha de base** (`round-09-4/validacao/01–05`): 124 testes verdes (engine 15, viewer 88, game 21);
  `viewer --check` TODOS OK; `--occlusion` só a exceção conhecida (`bow_shot`); gate dos presets 51 pares, 0 pixel;
  auditoria dos heróis sem voxels soltos.
- **Áudio:** inexistente — nenhuma classe, recurso, arquivo, dependência ou evento. A visão listava áudio como fora
  do escopo.
- **Ambiente:** Ubuntu 24.04.4, 8 núcleos, 15 GB, PipeWire 1.0.5 (saída padrão: caixa Bluetooth). Divergências com
  `ambiente.md`/ADR 0008: Goxel e `.venv` não estavam instalados nesta máquina.

## 2. Ferramentas

| Ferramenta | Origem | Situação | Verificação |
|---|---|---|---|
| LMMS 1.2.2 | apt | **DAW adotada** | abre a sessão real (`ferramentas/lmms-sessao-vila-a.png`); render por linha de comando; amostras idênticas em dois renders |
| FluidR3 GM 3.1 | apt (`fluid-soundfont-gm`, MIT) | instrumentos do sf2player | afinação conferida (A4 = 440 Hz) |
| ffmpeg 6.1.1 / ffprobe, sox 14.4.2, vorbis-tools | apt | medida, limitador, OGG | usados no pipeline |
| Audacity 3.4.2 | apt | instalado | **sem** `mod-script-pipe` no pacote; abertura com interface NÃO verificada |
| Goxel 0.13.0 | Flatpak de usuário | reinstalado | exporta o `hair.vox` do crespo sem erro |
| xvfb, xdotool, imagemagick | apt | inspeção de interface | capturas da interface do LMMS |
| numpy, Pillow, imageio, soundfile, scipy | `.venv` | QA | — |

Não instalados, com motivo: **Ardour** (montar sessão sem interface exigiria Lua headless com estado de plugin —
risco alto para a primeira rodada), **Surge XT** (o LMMS 1.2 no Linux não hospeda LV2/VST3), **Hydrogen** (a
percussão leve coube no LMMS). Configuração do LMMS: `~/Documents/lmms/` e `~/.lmmsrc.xml` (`configured="1"`).

Correção registrada: `lmms upgrade` só reescreve o XML (não carrega os modelos), então NÃO serve como prova de
ida e volta; a prova é a sessão aberta na interface e o render.

Defeitos do LMMS 1.2.2 encontrados e contornados (detalhes na skill `music-composition`): notas fora de ordem
param o padrão; sf2player dispara na grade de 256 quadros; o Delay escrito em XML não produz eco; o Mallets (STK)
desafina e travou o render; o Kicker só soa enquanto a nota está ligada; o ruído do Kicker muda a cada render.

## 3. Música

Dois estudos da atmosfera da vila, com **decisões diferentes** (não a mesma melodia com outro instrumento).
Ambos usam o *motivo do Valumo* (5̣ → 3, 2, 1, 2), tratado de modo diferente em cada um.

| | A — exploração acolhedora | B — fantasia misteriosa |
|---|---|---|
| Compasso, andamento | 6/8, semínima pontuada ≈ 67 | 4/4, semínima 75 |
| Harmonia | Ré maior funcional; Mi maior (II lídio) como momento de "maravilha" | Ré lídio sobre pedal; seção central em Si menor com Fá# maior |
| Textura | violão dedilhado + flauta; clarinete e baixo na B; cordas, glockenspiel e chocalho na A' | harpa em ostinato, celesta com o motivo aumentado, violoncelo em pedal, cordas, flauta de pã só na seção central, sino grave, cristal |
| Forma | introdução 4 + A 8 + B 8 + A' 8 (laço = 24 compassos) | introdução 2 + A 8 + B 8 + A'' 4 (laço = 20) |
| Escuta / laço | 57,6 s / corpo de 43,2 s | 86,4 s / corpo de 64,0 s |

Fontes: `fontes/audio/musica/vila/vila_{a,b}.py` (partitura com as decisões escritas) → `vila_{a,b}/*.mmp` (sessão
editável: faixas, notas, mixer com EQ, retorno de reverberação, automação de volume nas cordas da A) + `.mid`.
Runtime: `assets/audio/musica/vila_{a,b}.ogg` (1,7 e 2,6 MB).

Iterações (cada uma por um problema medido):
1. Stems: glockenspiel e chocalho 25–35 dB abaixo do resto → +17 e +21 dB.
2. Arpejo quantizado → atraso determinístico de até 2 ticks no violão e nas madeiras.
3. Espectro: 120–250 Hz pesado e acima de 4 kHz ~8 dB escuro → −3 dB a 250 Hz no violão, prateleira de +2,5 dB a
   6 kHz no master (A); 4–8 kHz de −25,5 para −22,8 dB.
4. Laço: violão mudo na 2ª passada (notas fora de ordem) → reordenação; passadas diferentes (grade de buffer) →
   andamento 99 → 100 (A), 75 (B).
5. B: celesta (o motivo) abaixo da harpa → +5 dB; flauta de pã −3 dB; sinos +9/+11 dB; eco escrito no lugar do
   Delay; vibrafone e "FX cristal" no lugar do Mallets.

QA final (`musica/*/qa-*.md`): −18,0 LUFS na escuta, pico real ≤ −3,2 dBFS, 0 saturação; laço com salto igual ao da
passagem natural (A 0,21×; B 2,91× = o mesmo ataque do tempo forte), fim da 2ª passada a ≤ 0,62 dB e ≤ 0,93 dB de
envelope por oitava do fim da 1ª. A métrica do laço foi corrigida duas vezes, com o motivo escrito no
`audio_qa.py` (correlação de forma de onda não serve com reverberação modulada); nenhum limite foi afrouxado para passar.

## 4. Efeitos sonoros

Famílias com eventos reais no clone: **passos** (pisada do herói; grama e terra pela superfície do relevo) e
**interface** (criador e menus do jogo: selecionar, confirmar, cancelar, abrir, salvar). Combate não existe no clone.

- **Passos:** camada de peso (Kicker, tom com queda) + camada de material (grãos de ruído escritos à mão + EQ de
  banda). Quatro propostas (Grama A macia × B seca; Terra A batida × B cascalho), 4 variações × andar/correr.
  Escolha: **Grama A + Terra A**. Grama A + Terra B teriam o mesmo centroide (2,2 kHz) e o jogador não distinguiria
  as superfícies; Grama A × Terra A dá 2,8 × 0,9 kHz. A Terra A foi refinada (+2 dB de rangido).
- **Interface:** A "madeira e papel" (bloco de madeira, kalimba, grãos de papel; sininho só no salvar) ×
  B "vidro e dourado" (celesta, caixa de música). Recomendada a **A**, por coerência com a madeira e o pergaminho da
  interface e porque o selecionar toca dezenas de vezes por minuto. Tudo em Ré maior, sem variação de tom.
- Defeitos corrigidos no caminho:
  - passo "baque" sem material (95% da energia abaixo de 300 Hz);
  - clique no fim (Kicker cortado pelo note-off);
  - variação 15 dB mais fraca no mesmo grupo;
  - cauda cortada;
  - DC;
  - pico real acima de 0 dBFS.
- Runtime: `assets/audio/sfx/` (16 passos + 7 interface, mono 48 kHz 16 bits, 520 KB). Propostas e sequências de
  contexto (cadência real do jogo; sessão de cliques) em `round-09-4/efeitos-sonoros/`.
- QA do catálogo inteiro: **25 arquivos, 0 alertas**. A Grama B (não escolhida) teve pico real −0,8 dBFS.
- Cada render do ruído é uma tomada diferente: as tomadas avaliadas são as versionadas; não re-renderize depois
  do QA.

## 5. Personalização

Dois estudos, **experimentais** (`"experimental": true` no catálogo), escolhidos por silhueta e por risco baixo de
atravessar o corpo:
- **Crespo**: nuvem de cachos em lóbulos, a silhueta mais LARGA (26 voxels; os outros 20–23).
- **Coque alto**: casca rente, coque redondo com espiral e amarração de couro (fora do canal do cabelo: não
  recolore), mechas que emolduram o rosto, a silhueta mais ALTA (22 voxels acima do pescoço).

Longos e tranças ficaram fora: fundidos à cabeça, atravessariam ombros e mochila (só `tail` e `hat` articulam).

| Versão | O que se viu | O que mudou |
|---|---|---|
| v1 | crespo: têmpora careca, costas com pontos escuros soltos, franja em "viseira"; coque: têmpora careca, coque em andares, nuca chapada | — |
| v2 | têmporas cobertas; contorno dos cachos por entalhe geométrico; 13 lóbulos maiores; coque esférico; `hair_strands` nas costas | escolhida |
| v2.1 | hipótese: a borda da testa do coque lia como tiara | 40 voxels mudados sem diferença visível → **revertida** |

Medidas (`personalizacao/medidas-cabelos.md`): Jaccard de volume máximo do crespo 0,41 e do coque 0,50 contra
qualquer outro estilo (os estilos aprovados chegam a 0,63); critério do `CustomizationTest` < 0,80. Cabeça fundida:
1 componente e 0 voxels soltos nas 4 expressões.

Compatibilidade:
- 10 cores com a rampa preservada. No preto, os entalhes do crespo perdem contraste.
- 4 expressões legíveis.
- Chapéu de aba ok nos dois estilos.
- Capuz C3 ok no coque. No crespo o nó fica **enterrado** → regra `X` no catálogo (primeira regra de
  incompatibilidade real): opção indisponível com cadeado e motivo.
- Escala de jogo: os três penteados (topete, crespo, coque) se distinguem pela silhueta.
- Vídeos de correr e golpe em 4 ângulos: nada atravessa mochila, cachecol ou ombros.

**Risco (fechado na parte 2, §10):** no golpe vertical o punho e a lâmina passam rente à lateral do crespo, com folga visivelmente
menor que no topete (`comparacoes/golpe-vertical-vista-de-cima-crespo-x-topete.png`). O `viewer --check` mede a
arma contra os volumes do rig canônico, não contra o cabelo personalizado. Não comprovado nem descartado.

Criador:
- nomes "Crespo" e "Coque alto";
- 6 miniaturas em 2 colunas empurravam "Cor do cabelo" para fora do painel em 1080p e 720p (o clique em "loiro"
  caía num controle recortado) → **3 colunas quando houver mais de 4 penteados**;
- a dica de penteado indisponível dizia "penteado atual" → "Não combina com o chapéu atual".

A captura do estado com defeito foi sobrescrita pela nova rodada de capturas; o defeito foi visto e descrito, mas
a imagem não foi preservada.

Proposta de evolução (rosto por partes com migração `valumo.aparencia` v2, roupas por região, peça experimental
recomendada = botas): [personalizacao-evolucao.md](../architecture/personalizacao-evolucao.md) — só planejamento.

## 6. Integração

Arquitetura: [audio.md](../architecture/audio.md); decisão: [ADR 0023](../decisions/0023-audio-openal-stb-vorbis.md)
(dependências novas: `lwjgl-openal`, `lwjgl-stb`).

| Arquivo | Mudança |
|---|---|
| `engine/build.gradle.kts` | módulos OpenAL e stb do LWJGL |
| `engine/.../audio/*` (novo) | `Audio`, `SoundCatalog`, `SoundSystem`, `AudioOut`, `OpenAlOut`, `SilentOut`, `Pcm`, `PcmSource`, `VorbisSource`, `LoopingReader` |
| `game/.../GameAudio.java` (novo), `Game.java`, `GameMain.java` | pisadas, ações e música; áudio aberto no jogo interativo, mudo nos roteiros; gravação sincronizada |
| `game/.../Player.java` | contador de pisadas na fase de contato do pé |
| `game/.../GameLogic.java` | ouvinte `onAction` |
| `viewer/.../World.java` | `surfaceAt` (gancho genérico) |
| `viewer/.../CreatorController.java`, `Creator.java`, `Main.java` | eventos semânticos da interface, áudio no `--editor`, roteiro mudo com loopback opcional |
| `viewer/.../CreatorState.java`, `CreatorLayout.java` | nomes, ordem, 3 colunas, texto da dica |
| `assets/audio/` (novo) | `sons.json` (à mão), OGG e WAV |
| `tools/chars_e.py`, `tools/components_e.py` | `hair_crespo`, `hair_coque`, exportação, regra `X` |

Contexto real (`integracao/`):
- o jogo por roteiro com o loopback do OpenAL gravou 27,00 s para 810 quadros a 30 FPS, em sincronia exata;
- eventos: 21 passos de grama andando e 12 correndo, todos sobre grama; 13 de terra, todos sobre o caminho;
  confirmar, abrir ×2, cancelar ×2, selecionar ×3;
- atraso evento → som: −32,3 ± 0,8 ms em 54 eventos, constante, igual a 1 quadro de deslocamento do `t0` do roteiro;
  latência real ≈ 1 ms;
- mistura do jogo: −24,2 LUFS (alvo usual de console);
- criador: sessão de 16,17 s com som.

## 7. Validação

| Comando | Resultado |
|---|---|
| `./gradlew test` | **141/141** (engine 28 = 15 + 13 de áudio; viewer 88; game 25 = 21 + 4 de áudio) |
| `tools/review7.sh gate` (`R=…/round-09-4/validacao/regressao`) | 51 pares, **0** com diferença |
| hashes de `assets/characters/e` e `assets/components` | idênticos à linha de base, salvo os arquivos novos e o `catalog.json` |
| `viewer --check` | TODOS OK |
| `viewer --occlusion` | só a exceção conhecida (`bow_shot`) |
| `audio_qa.py --catalogo` | 25 arquivos, 0 alertas |
| `CreatorTest.rapidRequestsEndOnTheLastChoice` (já aberto) | falhou 1× com a máquina em carga 4,8; depois, 5/5 na rodada 09.4 e 5/5 no commit base num worktree, na mesma faixa de carga (`validacao/flaky-*.txt`) |

Testes alterados, com o motivo: `AudioTest` e `GameAudioTest` são novos. No `CustomizationTest.canonicalAssetsAreNotTouchedByComposition`,
a premissa implícita "toda combinação compõe" deixou de valer com a regra `X`. O teste agora compõe toda
combinação válida e EXIGE que exatamente uma seja recusada (ficou mais estrito, não mais frouxo). No `AudioTest`,
o "silêncio" do loopback admite ±2 LSB porque o OpenAL Soft aplica dither na saída de 16 bits.

## 8. Crítica artística (do agente, sem escuta)

**Fortes**
- O processo é editável de verdade: partitura com decisões → sessão LMMS → render → QA.
- Os dois estudos de música diferem em compasso, harmonia, textura e forma.
- A integração é sincronizada por construção.
- O crespo tem identidade clara, a silhueta mais distinta do catálogo.
- O contraste entre os passos de grama e de terra foi escolhido por medida.

**Fracos ou incertos**
- Os timbres do FluidR3 tendem a soar "de banco GM": o maior risco de a música parecer genérica.
- Não sei se as melodias funcionam; só a escuta dirá.
- O farfalhar dos passos é feito de grãos de ruído de 13 ms e pode soar sintético.
- O coque tem a coroa em degraus (comum aos domos do catálogo) e a costa ainda simples.
- O crespo perde definição no preto e tem o risco do golpe vertical.

**Precisa de revisão do proprietário:** música A × B, interface A × B, passos (as duas superfícies), crespo e coque
no criador e no jogo.

## 9. Próximas rodadas (prioridade) — ver a revisão na §10

1. **Escuta do proprietário** (fones e caixa) dos estudos; decidir direção musical e timbres antes de produzir mais.
2. Estender o `WeaponCheck` com o volume do cabelo composto (risco do crespo) e capturar os golpes em 4 ângulos com
   cada penteado.
3. Se a escuta pedir timbres melhores: avaliar outro banco livre de qualidade ou síntese própria no LMMS (e, se
   for o caso, Ardour com sessão montada à mão pelo proprietário).
4. Ambiente da vila (vento, água, pássaros) como camada posicional; menu de volume.
5. Rosto por partes, começando por `olhos.*` com migração v2 (proposta pronta).
6. Primeira peça de roupa por região (botas), com validação de pé, IK e animação.

## 10. Parte 2 — pendências fechadas na mesma rodada

Pedido do proprietário: fechar as pendências apontadas e deixar documentação e skills redondas. A escuta continua
sendo dele (o agente segue sem ouvir).

| Pendência | O que foi feito | Prova |
|---|---|---|
| Arma × crespo (risco aberto) | `viewer --check-cabelos` + `HairWeaponTest`: voxels da arma × cabeça COMPOSTA em todo golpe/gesto, cada preset × penteado. Nenhum estudo é atravessado mais que o penteado do preset (aventureiro 21 = 21; exploradora 0 × 7; maga 34 × 47; coque 4/0/19) | `validacao/09-arma-x-cabelos.md` |
| Achado novo | o EMOTE do aventureiro atravessa o TOPETE canônico (21 voxels, t = 0,77 s; visível no perfil); o validador antigo não via (cápsula de raio 8,6). Registrado; corrigir muda coreografia aprovada | `comparacoes/`, problemas conhecidos |
| Teste instável do criador | causa real: `request()` tirava pedidos obsoletos da fila sem contá-los; com a CPU cheia a thread de trabalho só acordava depois dos cliques. Corrigido na contagem (um gancho de latência foi testado, não resolvia, e foi removido) | 3/3 com carga 6,7–10,2 (`validacao/10-*`) |
| Passos de areia e pedra | duas superfícies novas no mesmo gerador; distinção medida (`tools/audio/superficies.py`: pares ≥ 3,8 dB; areia × terra 97 × 48 ms); `GameAudio` mapeia areia → areia, rocha → pedra | `efeitos-sonoros/passos-superficies/`, 4 passos de areia no jogo |
| Volume do jogador | pausa com Música e Efeitos (0–100%), `AudioPrefs` em `output/preferencias/audio.json`; fileiras com rótulo que cabe (o "Sensibilidade do mouse" era atravessado pelo "−" em 720p — corrigido junto) | `integracao/pausa/`, `GameAudioTest` |
| Ambiente com posição | vento (cama estéreo, 24 s) e lago (mono posicional no centro do lago, 16 s) compostos no LMMS por síntese; laço por crossfade; fluxo posicional e ouvinte no OpenAL | loopback: lago a 12 u < metade do RMS a 2 u (`AudioTest`); no jogo o marulho cai ~8 dB longe (`integracao/ambiente/`) |
| Rosto por partes — 1º passo | slot `olhos` (redondos, amendoados, inclinados) gerado pelo próprio pintor por rosto; Appearance **v2** com `migrate1to2`; criador com miniaturas no enquadramento novo `olhos` (o de cabeça cortava os olhos) e botões na variante B | `personalizacao/olhos/`, 3 testes novos |
| Primeira roupa por região | botas "viajante" (slot `pes`, só as canelas, mesmo pivô e sola). v1 com o tom claro da bota não lia na câmera de jogo → v2 com forro de lã creme, que lê; categoria "Roupa" no criador | `personalizacao/botas/`, `bootsReplaceOnlyTheShins…` |

Validação final: **149/149** testes (engine 29, viewer 94, game 26); gate 51 pares, 0 pixel; hashes dos heróis
canônicos idênticos; `--check` TODOS OK; `--occlusion` só `bow_shot`; `--check-cabelos` sem estudo pior que a
referência; QA do catálogo de áudio 43 arquivos, 0 alertas; contexto do jogo 810 quadros ↔ 27,00 s.

Próximas rodadas (revisadas):
1. Escuta do proprietário (música A × B, interface A × B, passos nas 4 superfícies, ambiente).
2. Decidir o EMOTE do aventureiro (mão mais para fora) e rodar `--check-cabelos` como portão de golpes.
3. Timbres melhores se a escuta pedir; efeitos curtos posicionais quando houver NPC/criatura que soe.
4. Rosto por partes: sobrancelha e boca (mesma técnica dos olhos); roupas: calças/mangas com dobra de joelho/cotovelo.
