# Teste físico da porta de entrada (rodada 11.1) — ~15 min, para o proprietário

O que os roteiros automáticos não cobrem: **mouse, roda e teclado de verdade**, foco da janela, ouvido e gosto. Os
roteiros injetam eventos na fila da janela; aqui é a sua mão.

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo
```

Os seus dados ficam em `output/`, e nada antigo é apagado:

- `output/personagens/` e `output/mundos/` são novos;
- `output/saves/aventura.json` é **copiado** uma vez (vira a aventura do personagem "Aventureiro" na Vila do Posto);
- `output/aparencias/` não muda: as aparências ficam no **Carregar** do criador.

Para testar sem tocar em nada seu, use uma pasta à parte:
`./gradlew jogo -Dvx.personagens=/tmp/v/p -Dvx.mundos=/tmp/v/m -Dvx.savegame=/tmp/v/s -Dvx.saves=/tmp/v/a`.

## 1. Menu principal (3 min)

1. Abriu direto no menu? O logotipo "VALUMO" e o fundo da vila em movimento agradam?
2. Deixe o fundo correr um minuto inteiro: cinco tomadas (panorama, estalagem, lago, trilha, marco). Alguma atravessa
   algo, treme ou corta de um jeito feio? A troca escurece sem clarão?
3. Mouse: passar sobre os botões acende e move a semente verde? Clique em **Configurações**.
4. Configurações:
   - mude **Volume geral**, **Música** e **Efeitos**: o som muda na hora?
   - **Sensibilidade**, **Inverter vertical**, **Barras**;
   - **Voltar** e Esc;
   - feche e abra o jogo: ficou guardado?
5. Teclado: ↑ ↓ e Enter no menu principal.

## 2. Personagens (4 min)

1. **Jogar**. Seu jogo salvo de antes já aparece como o personagem **Aventureiro** (com "última aventura em…").
2. **Novo personagem:** o criador abre; mude cabelo e chapéu, dê um nome com acento (ex.: "Iára"), **Pronto** →
   **Criar personagem**. Volta para a lista com ele escolhido e o retrato certo?
3. **Arraste** o herói à direita com o mouse: ele gira? A roda do mouse anda na lista?
4. **Editar aparência:** mude algo e **Salvar personagem**. O mesmo cartão mudou (sem duplicar)?
5. **Editar aparência** de novo e **Cancelar** (ou Esc): nada mudou?

## 3. Mundos (5 min)

1. **Escolher.** A **Vila do Posto** (legado) está na lista, com prévia?
2. **Criar mundo:**
   - clique no nome, digite;
   - clique na semente, digite `1024`;
   - **Ver prévia**: aparece a região? A tela de carregamento diz o que está fazendo?
3. **Criar mundo**: ele entra na lista, escolhido, com a prévia.
4. **Criar mundo** com a semente **vazia**: a semente sorteada aparece no campo e fica gravada no mundo?
5. Digitando no campo, as teclas W, M, E, Q não fazem nada no jogo (o M não silencia)?
6. Esc no formulário cancela; Esc na lista volta aos personagens com a mesma escolha.

## 4. Aventura (3 min)

1. Com o **Aventureiro**, na Vila do Posto, o botão é **Continuar aventura**, com o resumo (tempo, missão, moedas).
2. **Continuar aventura:** a vila abre com o progresso e com a aparência do personagem.
3. Esc → **Menu principal** → **Salvar e voltar**. De volta aos mundos, o resumo mudou?
4. Entre no mundo gerado com outro personagem: é outra aventura (começa do zero).
5. Esc → **Sair do jogo**: pergunta antes? **Cancelar** volta à pausa.

Anote o que estranhou, o que achou feio e o que achou lento. A aprovação artística do menu é sua.

## 5. Fechar e voltar (fechamento da 11.1, ~5 min)

1. Na aventura, **ande um pouco** e clique no **X da janela**. Abre "Sair do jogo?" (Salvar e sair / Sair sem salvar /
   Cancelar)? **Cancelar** volta ao jogo do jeito que estava?
2. Converse com alguém e, no meio da fala, clique no X → **Cancelar**: volta à MESMA fala?
3. X → **Salvar e sair**: o jogo fecha. Abra de novo, escolha o mesmo personagem e mundo → **Continuar**: você está onde
   estava?
4. Entre e, sem andar, clique no X: pergunta, dizendo "Tudo desta aventura já está salvo." (Sair do jogo / Cancelar)?
5. Na pausa → **Menu principal**: volta à TELA INICIAL (Jogar, Configurações, Sair)? **Jogar** mostra o personagem e o
   mundo de antes já escolhidos?
6. **Editar aparência**: aparece "Abrindo o criador" antes da espera (na primeira vez)? No criador, mude o cabelo e clique
   no X: pergunta? **Sair sem salvar** fecha sem mudar o
   personagem?
7. X no menu principal, em personagens e em mundos: fecha sem perguntar?
8. Os botões vizinhos têm o mesmo tamanho de letra (720p e 1080p)? A tela de carregamento ("Preparando a vila", "Gerando
   a região…") agrada?
