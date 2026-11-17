# Checklist de aceitação de asset (direção E)

Marque cada item com evidência (caminho da captura). Um "não" em item **obrigatório** reprova.

## Personagem / NPC

Os itens 1 e 4 usam os valores dos protagonistas; para um NPC valem as faixas de "NPCs humanoides
futuros" em `docs/art/contrato-de-qualidade.md` (2,0–3,0 cabeças, olho em placa com forma livre).

| # | Item | Obrig. | Como verificar |
|---|---|---|---|
| 1 | 2,35–2,60 cabeças; ≤ 1,25 u; voxel 1/40 | sim | `./gradlew test` + HUD |
| 2 | Silhueta distinta dos heróis existentes (frente e perfil) | sim | `sil=1` no modo LINHA |
| 3 | Legível em tamanho reduzido (≈ 60 px de altura) | sim | `*-tamanho-reduzido.png` |
| 4 | Rosto em placa plana; olho 3 × 5; brilho do mesmo lado | sim | close de rosto |
| 5 | Quatro expressões presentes e legíveis | sim (heróis) | `expressoes.png` |
| 6 | Cabelo: massa + mechas grossas; sem capacete; costas revisadas | sim | costas + close |
| 7 | 3–5 matizes; 1 destaque; sem preto/branco puros em área | sim | luz neutra |
| 8 | Hierarquia de detalhe respeitada (camada 1 funciona sozinha) | sim | sem cor + silhueta |
| 9 | Sem interseção visível nos 6 clipes (arma, capa, trança, manto) | sim | `anim.sh` + pranchas |
| 10 | `meta.weaponMode`, `footPlant`, `faces` preenchidos; ≤ 9 mil faces | sim | `rig.json` |
| 11 | Bom em luz neutra (não depende de atmosfera) | sim | `preset=neutra` |
| 12 | Coerente ao lado de criaturas e da casa | sim | cena: estrutura/criatura |
| 13 | Silhueta **isolada**: fundo uniforme, sem chão nem sombra, figura inteira com os pés; frente, perfil e costas | sim | `--review3 personagens` (`*-silhueta-*.png`) |
| 14 | Escala de exploração renderizada de verdade em baixa resolução (não reduzida depois) | sim | `herois-tamanho-exploracao.png` |
| 15 | Rig articulado (rodada 03): `farm_*`/`shin_*`, `meta.joints`; arma no antebraço; tampa de dobra sem fresta | sim (heróis E) | `AssetContractTest` + close do cotovelo |
| 16 | Pé de apoio parado na caminhada e na corrida; pés no chão em encosta | sim (heróis E) | vídeo `move=1` + `animacoes/pes-em-terreno-irregular.png` |
| 17 | Nada que leia como antena, capacete ou elemento reconhecível de outra obra | sim | silhueta + [assinatura-visual.md](../art/assinatura-visual.md) |
| 18 | Arma/item: perfil em `assets/anim/`, `viewer --check` sem falhas (interseções, alcance, salto) | sim (armados) | `validacao/check.txt` |
| 19 | Golpe revisado em 5 ângulos + câmera de exploração, em vídeo e a meia velocidade; arma nunca cobre o rosto, nem na guarda | sim (armados) | `tools/review4.sh armas`, `videos/` |
| 20 | Transições locomoção ↔ golpe sem salto (roteiro `seq=`) e transporte da arma sem atravessar pernas | sim (armados) | `ChoreoTest`, `animacoes/transicao-*` |
| 21 | Nenhum voxel solto nem fragmento < 4 voxels; nenhum `.vox` órfão no rig (rodada 05) | sim | `tools/asset_audit.py`, `QualityTest` |
| 22 | HIT e EMOTE coreografados pelo perfil (sem FK atravessando a cabeça) | sim (armados) | `viewer --check` (`clip:HIT`, `clip:EMOTE`) |
| 23 | Comparado lado a lado com a referência canônica equivalente (`docs/art/reference/`) | sim | captura com a mesma spec do `manifesto.tsv` |

## Criatura

Itens 2, 3, 7, 9, 11, 12 acima + nível de complexidade declarado (`meta.tier`) e dentro do orçamento
(1: ≤ 3 mil faces; 2: ≤ 6 mil; 3: ≤ 15 mil); rosto em placa plana.

## Prop / vegetação / construção

| # | Item | Obrig. |
|---|---|---|
| 1 | Escala da tabela de [visual-identity.md §9](../art/visual-identity.md#9-escala-do-mundo) | sim |
| 2 | Voxel da família (1/8 grande, 1/16 médio, 1/24 pequeno) | sim |
| 3 | Sombra pintada em bandas, sem ruído de 1 voxel | sim |
| 4 | Folhagem com `affineWind` | sim |
| 5 | Construção: luz quente + toque vegetal + cisalhamento | sim (construções) |
| 6 | Árvore nova com silhueta distinta das 7 da biblioteca | sim (árvores) |
| 7 | Legível no panorama e no close | sim |

## Cena

Hierarquia (circulação → interesse → secundário → decoração) visível no panorama; áreas de descanso;
névoa não vela o plano do herói; contorno discreto; ≥ 60 FPS em 1080p nativo na Iris Xe
(`viewer --bench "1920x1080,style=e"`).

## Interface do jogador

- [ ] Todo controle visível funciona com mouse **e** tem alternativa de teclado; clique = apertar e soltar no mesmo controle.
- [ ] Miniaturas são modelos reais da opção e mudam junto com a aparência atual; nenhuma imagem pré-desenhada.
- [ ] Nenhuma métrica ou ID técnico na tela do jogador.
- [ ] Hover, selecionado, pressionado e indisponível visíveis; indisponível diz o motivo.
- [ ] Ação destrutiva pede confirmação; salvar nunca sobrescreve; erro em linguagem compreensível.
- [ ] Capturas em 720p, 1080p e ultrawide sem texto cortado nem controle sobreposto; personagem nunca coberto.
- [ ] Vídeo do fluxo completo com entrada pelo roteiro (`tools/review72.sh video`) revisado quadro a quadro nos pontos de troca.
- [ ] `CreatorTest` e os testes de identidade dos presets passam; gate pixel a pixel 0 diferenças.
- [ ] Medição de cliques rápidos sem quadro > 25 ms e malhas vivas estáveis (`desempenho`, `vazamento`).

## Gameplay

- [ ] Movimento independente de FPS, com aceleração e frenagem curtas; animação pela velocidade real (pé plantado).
- [ ] Colisão: não entra em prop, desliza em parede, sobe degrau ≤ 0,30 u, bloqueia desnível maior e água; nunca fica preso sem saída.
- [ ] Câmera não atravessa relevo nem parede, não entra no herói, não quica em degraus; zoom e sensibilidade com limites.
- [ ] Interação só perto e de frente; conversa entra, avança e sai; pausa e conversa preservam posição, rumo e câmera.
- [ ] Prop novo tem perfil em `assets/scenes/colisao_e.json` (ou aceita o padrão conscientemente).
- [ ] `GameplayTest` passa; vídeos por roteiro (`tools/review8.sh videos`) revisados; teste físico pedido ao proprietário.

