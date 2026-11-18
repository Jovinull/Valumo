# Rodada 09.3 — polimento da esquiva, do salto e das transições (pacote de revisão)

> **Depois da revisão:** o proprietário escolheu a esquiva **R2**. O rolamento da 09.2 e o R1 saíram do jogo. Este
> pacote (`round-09-3/`) é o registro da comparação: os vídeos 01–09 dele foram gravados com R1+R2 e **não** mostram a
> versão atual; os da R2 final estão em `round-09-3-r2/`.

- **Relatório técnico:** `relatorio-tecnico.md`, cópia de `docs/validation/relatorio-rodada-09-3.md`.
- **Teste físico, para o proprietário:** `roteiro-de-teste-fisico.md`, seção 7.1.
- **Origem:** tudo foi gerado por `tools/review93.sh`.
  - Os vídeos do jogo têm o som gravado pela própria aplicação (loopback); nada tocou nos alto-falantes.
  - Os vídeos `A*` vêm do visualizador: câmera fixa, a mesma pose do jogo.
- **MP4:** ficam fora do zip, na pasta `videos/` de `output/visual-review/round-09-3/`.

## O que ver primeiro (o movimento é o que se avalia; quadros soltos enganam)

1. **`videos/A1-rolamento-09.2-x-R1-x-R2-perfil-lento.mp4`:** o rolamento da 09.2, o R1 e o R2 lado a lado, em 0,25×.
   É o centro da rodada. Depois veja a versão `normal`.
2. **`videos/A2-…`:** o R1 em três quartos, de frente e de costas.
3. **`videos/A3-…`:** os saltos laterais do R2 (esquerda, direita) e o recuo.
4. **`videos/A4-salto-*-antes-x-depois-*.mp4`:** o salto da 09.2 × 09.3, parado, andando e correndo.
5. **`videos/B-*-antes-x-depois.mp4`:** o mesmo roteiro no binário da 09.2 e no da 09.3, lado a lado. Cobre as esquivas
   livres, as travadas e os saltos.
6. **`videos/09-demonstracao.mp4`:** demonstração com o binário final e o som da aplicação.
7. **`animacao/`:** pranchas de 12 instantes por gesto (perfil, frente, três quartos) e do salto (antes e depois).

## Vídeos do jogo

| | |
|---|---|
| 01 | esquivas com a mira livre: frente, esquerda, direita, trás, recuo sem direção (na entrega: R1 à frente; hoje: R2) |
| 02 | esquivas com a mira travada: lados (R2, encarando o fungo), frente e trás |
| 03 | esquiva de lado contra a cabeçada (dentro da janela; "esquivou" no registro) |
| 04 | esquiva contra o poço (a colisão para o corpo; a invulnerabilidade não atravessa) |
| 05 | a Maga (chapéu, robe) com espada e escudo: rolamento, salto lateral e saltos |
| 06 | saltos: parado, andando, correndo, para cima da pedra pequena, descendo |
| 07 | salto no combate: pula de lado travado, clique no ar (sai no pouso), esquiva e ataque |
| 08 | câmera nos casos P37/P41/P42 (toco, fungo colado, pedra grande + encosta) |
| 09 | demonstração final |

## Pastas

| Pasta | Conteúdo |
|---|---|
| `videos/` | MP4 do jogo (01–09), comparações `A*` (visualizador) e `B*` (09.2 × 09.3), `*-registro.txt` |
| `pranchas/` | 16 quadros de cada vídeo do jogo (JPEG) |
| `animacao/` | pranchas temporais de cada gesto e do salto |
| `camera-p37-p41-p42/` | capturas dos casos de câmera, com o pontilhado do herói perto da lente |
| `desempenho/` | `medicao.txt`: binário 09.3 × 09.2, um fungo e três, duas repetições |
| `validadores/` | `check`, `interrupcao-fina`, `golpe-andando`, `esquiva`, `oclusao` |
| `regressao/` | `gate.txt` (51 presets pixel a pixel) |
| `camera-modos.txt` | as três câmeras nos 10 cenários em movimento |
| `testes.txt` | testes por classe |
| `documentos/` | adendos dos ADRs 0018 e 0019, arquitetura do combate e do jogo, guia de animação, problemas conhecidos, skills |

## Limites honestos

- **Rolamento para a frente, melhora parcial:** o R1 fica 13% mais baixo que o da 09.2, mas a cabeça ainda é a peça mais
  baixa por ~0,13 s (encosta sem afundar). Ver o relatório, §1: três correções foram medidas e descartadas.
- **Escolha estética:** na entrega, R1+R2 era a recomendação técnica e `-Dvx.esquiva` trocava o estilo. **O proprietário
  escolheu a R2**; a opção e as alternativas saíram. Vídeos do jogo com a R2 final: `output/visual-review/round-09-3-r2/`
  (`tools/review93.sh videos` com `R=output/visual-review/round-09-3-r2`).
- **Câmera:** os números são os da 09.2. O ajuste localizado foi testado e revertido. O que entrou foi só o herói
  pontilhado perto da lente.
- **Teste físico:** nada foi testado com mouse, teclado e alto-falante físicos.
