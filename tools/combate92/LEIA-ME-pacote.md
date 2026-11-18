# Rodada 09.2 — mobilidade, esquiva, salto, mira opcional, grupos e morte voxel (pacote de revisão)

- **Relatório técnico:** `relatorio-tecnico.md` (cópia de `docs/validation/relatorio-rodada-09-2.md`).
- **Teste físico, para o proprietário:** `roteiro-de-teste-fisico.md`.
- **Origem:** tudo foi gerado por `tools/review92.sh` a partir do jogo real, com o som gravado pela própria aplicação
  (loopback; nada tocou nos alto-falantes).

## O que ver primeiro (vídeos contínuos: o movimento é o que se avalia)

1. **`videos/03-golpe-andando-A-x-C.mp4`:** o mesmo roteiro com o perfil A (o golpe da 09.1) e o C (padrão), lado a
   lado. É o centro da rodada.
2. **`videos/16-tres-inimigos.mp4`:** três fungos; anda, ataca, rola para sair do cerco e ataca de novo.
3. **`videos/14-mira-travada.mp4` e `15-troca-de-alvo.mp4`:** Q trava, anda em volta, recua, rola; Tab e Shift+Tab.
4. **`videos/18-chefe-e-menores.mp4`:** o protótipo de chefe (o fungo ampliado, recurso de teste) com dois menores.
5. **`videos/19-morte-lado-a-lado.mp4`:** a morte antiga × D1 × D2 (padrão), com o mesmo golpe final.
6. **`animacao/`:** pranchas do rolamento, do recuo e dos três cortes com as pernas andando (câmera fixa do
   visualizador, a mesma pose do jogo).

## Pastas

| Pasta | Conteúdo |
|---|---|
| `videos/` | 01–23 (ver a lista abaixo) + `*-registro.txt` (o que aconteceu, passo a passo) |
| `comparacoes` (em `videos/`) | `01-02-golpe-parado-antes-depois` (binário 09.1 × 09.2), `03-golpe-andando-A-x-C`, `19-morte-lado-a-lado` |
| `pranchas/` | 16 quadros de cada vídeo (JPEG) |
| `animacao/` | rolamento, recuo, golpes com pernas (rumo 0, 90, 180) |
| `capturas/` | 10 instantes em 1920×1080 e 1280×720: livre, travada, golpe com barra pequena, três fungos, troca, barras sempre, chefe com barra grande, chefe travado, pausa com as opções de combate |
| `desempenho/` | `medicao.txt`: um fungo, três, chefe + 2 (duas repetições cada) e o binário da 09.1 |
| `validadores/` | `check`, `interrupcao-fina`, `golpe-andando`, `esquiva`, `oclusao` |
| `regressao/` | `gate.txt` (51 presets pixel a pixel) |
| `camera-modos.txt` | as três câmeras nos 10 cenários em movimento da 09.1 |
| `testes.txt` | testes por classe |
| `documentos/` | ADRs 0017–0022, arquitetura do combate, do jogo e do som, problemas conhecidos, skills |

## Vídeos

| | |
|---|---|
| 01 / 02 | golpe parado: binário da 09.1 (antes) × 09.2 (depois), mesmo roteiro |
| 03 | três cortes andando para a frente (+ versões perfil A e B) |
| 04 | golpe andando de lado |
| 05 | golpe recuando (mira travada, S) |
| 06 | três golpes em movimento: correndo, mudando de direção entre os cortes |
| 07 | esquivas: frente, esquerda, direita, trás, recuo sem direção |
| 10 | esquiva contra a cabeçada (a segunda cai na janela de invulnerabilidade: "esquivou" no registro) |
| 11 | salto na exploração: no lugar, para cima da pedra pequena (pisável), descendo, correndo |
| 12 | salto no combate (o clique no ar sai no pouso) |
| 13 | câmera livre contra um fungo (o mouse manda) |
| 14 | mira travada contra um fungo |
| 15 | troca de alvo (três fungos) |
| 16 | três fungos ao mesmo tempo |
| 17 | um corte acerta dois fungos na trajetória; o de trás não (registro: dois acertos no mesmo golpe) |
| 18 | ensaio de chefe com dois menores |
| 19 | morte antiga × D1 × D2 |
| 20 | barras de inimigos comuns: padrão, sempre, nunca |
| 21 | barra de chefe |
| 22 | luta perto do carvalho e da pedra grande (livre e travada) |
| 23 | derrota do herói e reinício |

## Limites honestos

- **Teste físico:** nada foi testado com mouse, teclado e alto-falante físicos. A diversão e o peso dos golpes são
  julgamento do proprietário.
- **Câmera travada:** ~19% do tempo "ruim" nos cenários difíceis, contra 11% do ajuste automático da 09.1, que subia
  até 52° e girava sozinho.
- **Câmera livre:** com o fungo colado atrás da cabeça, ele some até o jogador mexer o mouse. Ver o relatório, P37 e
  P41–P46.
- **Chefe:** o "chefe" é o fungo ampliado, marcado como protótipo; não é um chefe artístico.
- **Salto:** golpe no ar não existe nesta rodada.
