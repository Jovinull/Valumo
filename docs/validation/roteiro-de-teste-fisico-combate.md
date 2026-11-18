# Roteiro de teste físico — combate (rodada 09.2, 15–20 minutos)

Para o proprietário, com mouse, teclado e alto-falante ou fone. Ninguém além de você testou isso com as mãos. Os vídeos
do pacote mostram que funciona; só você diz se é **gostoso de controlar**. A versão da 09.1 está em
[`docs/historico/roteiro-de-teste-fisico-combate-09-1.md`](../historico/roteiro-de-teste-fisico-combate-09-1.md).

```bash
./gradlew jogo            # Esc = pausa (Sensação, Arena, Barras de inimigos, Volume)
./gradlew jogo -Ptecnico  # F3 = hitboxes, hurtboxes, estado, "INVULNERÁVEL" na esquiva
```

Anote para cada bloco: **gostei / indiferente / incomoda** e uma frase. O que incomodar vale mais que o que agradar.

## Controles

| | |
|---|---|
| WASD / Shift | andar / correr |
| Espaço | pular |
| Alt esquerdo ou botão do meio | esquivar (com direção: rola; sem: recua) |
| Clique esquerdo | atacar (de novo, encadeia) |
| Clique direito segurado | defender |
| Q | travar / soltar a mira |
| Tab / Shift+Tab | trocar de alvo (direita / esquerda na tela) |
| Esc / M / F1 | pausa / mudo / ajuda |

## 1. Andar e correr (2 min) — perfil C, o padrão

1. Ande e corra pela vila e pela trilha até a arena.
2. É rápido o bastante? Rápido demais? O pé parece plantado ou patinando?
3. Pausa → **Sensação: A** (o movimento da 09.1) e depois **B**. Volte ao **C**. Qual dos três você prefere andando?

## 2. Atacar andando (4 min) — o ponto principal

Na arena (Pausa → **Arena: um fungo**):

1. Segure W e clique três vezes: os três cortes saem sem parar de andar?
2. Ataque andando de lado (A ou D), recuando (S) e depois de correr (Shift + W, solte o Shift, clique).
3. Mude de direção entre um corte e outro.
4. **Perguntas:**
   - consigo me movimentar enquanto ataco?
   - os golpes ainda têm peso, ou ficou "deslizando"?
   - os comandos respondem rápido?
5. Repita com **Sensação A** e **B**. Qual tem o peso certo?

## 3. Esquivar (3 min)

1. Espere a cabeçada (o chapéu treme e ele se encolhe) e esquive para o lado com A/D + Alt.
2. Esquive para a frente, para trás e sem direção (recua de frente).
3. Ataque e esquive no meio do corte. Em alguns instantes a esquiva espera um pouco: a execução do corte não pode ser
   cancelada.
4. Aperte a esquiva sem parar: não deve dar para ficar invulnerável o tempo todo.
5. **Perguntas:**
   - a esquiva é satisfatória (distância, velocidade, sensação de escapar)?
   - o salto da esquiva (R2) é bonito e legível?
   - consigo sair de uma situação perigosa?
   - dá para usar o botão do meio no lugar do Alt? Algum conflita no seu sistema?

## 4. Pular (2 min)

1. Pule no chão, correndo e na encosta.
2. Na vila, suba no caixote perto do barril. Tente subir na casa: não deve conseguir.
3. Na luta, pule para o lado da cabeçada e clique no ar: o golpe sai ao pousar.
4. **Pergunta:** o salto é agradável (altura, duração, resposta)?

## 5. Câmera livre × mira travada (3 min)

1. **Livre:**
   - lute só com o mouse;
   - a câmera não deve girar nem subir sozinha;
   - com o fungo colado bem atrás da sua cabeça, ele some até você mexer o mouse (o preço de não girar sozinha, P41).
2. **Q trava:**
   - ande em volta dele com A/D (você fica de frente para ele);
   - recue com S;
   - role de lado;
   - Q de novo solta (o WASD não deve mudar de direção de repente).
3. **Perguntas:**
   - a câmera livre é confortável?
   - a mira travada ajuda?
   - a câmera travada olha de cima demais (até ~40°) ou está boa?

## 6. Três fungos e o ensaio de chefe (4 min)

1. Pausa → **Arena: três fungos**. Lute sem travar:
   - dois atacam por vez, o terceiro espera;
   - um corte largo pode acertar dois.
2. Trave (Q), troque com Tab e Shift+Tab, solte e ataque livre.
3. Pausa → **Arena: chefe + 2**. O grandão é um **protótipo** (o mesmo fungo ampliado). Trave nele, solte, role para
   longe dos pequenos, trave num pequeno, Tab de volta para o chefe.
4. **Perguntas:**
   - consigo enfrentar três?
   - troco de alvo sem confusão?
   - consigo soltar a mira e atacar livremente?

## 7. Mortes e interface (2 min)

1. Veja os fungos morrerem: o corpo se desfaz nos próprios blocos, com sementinhas de luz subindo (D2). Ficou bonito?
   - Para comparar, rode `./gradlew jogo -Dvx.morte=D1` (só blocos) e `./gradlew jogo -Dvx.morte=antiga` (a da 09.1).
2. **Barras:**
   - inimigo comum: barra pequena sobre ele só quando leva dano ou está travado;
   - chefe: barra grande no topo;
   - Pausa → **Barras**: com dano ou alvo / sempre / nunca. A preferência fica gravada.
3. **Pergunta:** a interface atrapalha pouco?

## 7.1 Rodada 09.3 — esquiva e salto polidos (5 min)

1. **Esquiva para a frente** (mira livre, W + botão do meio ou Alt): um salto baixo e rápido (R2, a esquiva escolhida).
   - **Pergunta:** ainda sai rápido e protege como antes?
2. **De lado com a mira travada** (Q, depois A ou D + esquiva): um salto lateral inclinado, sem virar o corpo, ainda de
   frente para o fungo.
   - **Pergunta:** dá a sensação de controle?
3. **Recuo** (esquiva sem direção): o mesmo da 09.2.
4. **Saltos:**
   - parado, andando e correndo;
   - para cima da pedra pequena e descendo da encosta.

   O impulso aparece, mas o salto deve sair no mesmo instante do Espaço. O pouso correndo não deve frear.
   - **Pergunta:** o salto responde na hora? O pouso tem peso sem travar?
5. **Câmera colada:** encoste no carvalho ou entre a pedra grande e a encosta. O herói fica pontilhado/translúcido
   quando a câmera entra nele.
   - **Pergunta:** isso incomoda ou ajuda?

## 8. Ao final

- Tenho vontade de continuar lutando?
- Qual perfil (A/B/C), qual morte (D1/D2), qual barra padrão? A esquiva R2 ficou boa em todas as direções?
- O que mais incomodou?

O que medir de novo depois do teste: só o que você apontar. Os números estão em `docs/architecture/combate.md` §14–19 e
no relatório da rodada.
