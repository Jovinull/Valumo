# Como adicionar uma tomada ao fundo do menu (rodada 11.1)

As tomadas do menu principal ficam em `assets/scenes/apresentacao_e.json` (escrito à mão).

## Campos

| Campo | Significado |
|---|---|
| `nome` | aparece só em logs e testes |
| `alvo` | `[x, y, z]` em u, o ponto para onde a câmera olha (y = altura absoluta, não do chão) |
| `rumo` | `[início, fim]` em graus; o olho fica em alvo + dist·(sen rumo·cos incl, sen incl, cos rumo·cos incl): **0 = olho ao sul (+z) olhando para o norte**, 90 = olho a leste, 180 = ao norte, 270 = a oeste |
| `inclinacao` | `[início, fim]` em graus (acima do horizonte) |
| `distancia` | `[início, fim]` em u |
| `duracao` | segundos (> 1) |

O movimento é suavizado nas pontas. A troca escurece por `transicao` s de cada lado; o laço volta ao começo no escuro.

## Passos

1. **Escolha o alvo num lugar livre.** O alvo não pode estar dentro de uma forma de colisão (casa, posto, poço, copa).
   As formas e os moradores aparecem no próprio teste quando ele falha.
2. **Escolha rumos com vista livre.** A cena tem o sul em +z. Uma tomada de frente para a estalagem tem o olho ao sul
   (rumo perto de 0 ou 330–360).
3. **Rode o teste:**

   ```bash
   ./gradlew :game:test --tests vx.game.ApresentacaoTest
   ```

   Ele confere 61 instantes por tomada contra a colisão REAL da vila: o olho fora de tudo, a mais de 0,5 u de qualquer
   forma e a mais de 1,6 u de um morador, e a linha do olho ao alvo livre. A falha diz a tomada, o instante e o que
   bloqueou.
4. **Olhe o resultado.** `tools/menu/01-menu-principal.txt` captura um instante de cada tomada (`menu-tomada T`, T = soma
   das durações anteriores + metade). Rode `tools/review111.sh capturas`, ou o roteiro direto com `-Dvx.menu=sim` e pastas
   temporárias.

## Regras

- **Nada de protótipo nem de estudo não aprovado no fundo** (o Titã não entra). Os inimigos não são desenhados no menu.
- **Tomada nova não muda a vila.** Se um prop atrapalha, mude a tomada, não o cenário.
- **Uma cena diferente da vila** (uma região gerada, por exemplo) exige outra sessão de fundo. Isso é decisão de
  arquitetura: registre uma ADR.

A vitrine da tela de personagens (`vitrine.heroi`: x, z, rumo) segue a mesma regra e tem teste próprio
(`vitrineDosPersonagensComVistaLivre`).
