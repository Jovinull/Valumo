# Roteiro de teste físico — "As Luzes do Posto" (rodada 10)

Os roteiros da rodada injetam teclado e mouse na fila de eventos da janela. Isso prova a lógica, mas **não**
substitui as suas mãos, o cursor preso pelo sistema e o som nos seus alto-falantes. O agente **não ouviu** nada: os sons
da missão são estudos medidos.

Leva uns **20 minutos**. Anote o que falhar (passo, o que aconteceu, resolução da janela) em
`docs/validation/problemas-conhecidos.md`, ou mande para o agente.

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo                                            # o seu jogo (save em output/saves/)
./gradlew jogo -Dvx.savegame=/tmp/valumo-teste            # o mesmo, num save de teste (não mexe no seu)
```

## A. Abertura

1. **Tela inicial:**
   - aparecem **Jogo novo** (verde) e **Continuar — nenhum jogo salvo**, apagado;
   - clique no Continuar: a mensagem explica o motivo;
   - Enter também começa o jogo novo.
2. Escolha uma aparência e entre. A dica de baixo agora diz **J diário**.

## B. A oferta

3. **A Kelyra:** vá até ela, junto à estalagem.
   - A placa **E Conversar com Kelyra** aparece.
   - E abre a conversa, e as falas falam da trilha e do marco apagado.
4. **A escolha:** na última fala aparecem **Aceitar** e **Agora não**.
   - ← → mudam a escolha, e o mouse também serve.
   - Escolha **Agora não**: ela responde e encerra.
5. **A oferta de novo:** fale de novo. A fala muda ("Pensou melhor?"), e a oferta continua de pé. **Aceite.**
   - Toca o som de missão aceita.
   - Aparece "Missão aceita: As Luzes do Posto".
   - O **rastreador** aparece no canto superior direito com "Afaste os fungos-cardo da trilha do sul 0/2".
6. **J abre o diário** com:
   - o nome e o estado "Aceita";
   - a descrição e os três objetivos;
   - "Recompensa prometida: 30 moedas";
   - "Aceita aos … de jogo".

   **T** esconde e mostra o rastreador. **J** ou **Esc** fecha.

## C. A trilha

7. **O caminho:** siga a trilha para o sul, passando pela placa. Na clareira há **dois** fungos-cardo, e no fim dela fica
   o **marco**: uma estela de pedra com uma lanterna apagada.
8. **O marco antes da hora:** com um fungo vivo perto dele, o E não aparece (nada se usa no meio da luta). Se conseguir
   chegar sem luta, a mensagem diz para limpar a clareira antes, e nada acende.
9. **A luta:** derrote os dois fungos. O rastreador passa a 1/2 e depois a "Reacenda o marco".
   - Toca o som de objetivo cumprido.
   - **Não** aparece "vitória — R recomeça".
10. **Opcional:** deixe um fungo vivo e caia (derrota). **Tentar de novo:** o fungo que você já derrotou **não volta**, e
    o outro está inteiro.

## D. O marco

11. **Acender:** chegue ao marco. A placa diz **E Acender o marco**. Aperte E.
    - A lanterna acende com luz quente e a semente verde-água no alto.
    - Por ~2 s, sementes de luz sobem em espiral.
    - Toca o som do marco.
    - A placa passa a dizer **Examinar o marco**.
12. **De novo:** aperte E outra vez. "A lanterna do marco brilha firme", e nada muda.
13. **De longe:** olhe o marco aceso de longe e no escuro da sombra das árvores. Ele lê como **aceso**? É discreto
    demais, ou forte demais?

## E. A volta

14. Volte **a pé** até a Kelyra. Pelo seu retorno ela entende que o marco está aceso e entrega **30 moedas**: aviso
    "+30 moedas" e o som da recompensa.
15. **O diário:** "Concluída", "Recompensa recebida: 30 moedas", "Moedas: 30". O rastreador some.
16. **Depois da entrega:** fale com ela de novo (agradece e não paga de novo) e com o **Bramor** (uma fala a mais: ele
    soube que o marco acendeu).

## F. Salvar e continuar

17. **Na luta:** Esc → **Salvar jogo** no meio de uma luta com os fungos da trilha (num jogo novo, antes de derrotá-los):
    a mensagem recusa.
    Fora da luta, salve: aparece "Jogo salvo".
18. **Continuar:** feche o jogo (Sair do jogo) e abra de novo.
    - A tela inicial mostra **Continuar o jogo salvo**.
    - Continue: você volta onde salvou, com a mesma aparência, o marco aceso, a missão concluída e 30 moedas.
    - A trilha continua limpa.
19. **Jogo novo por cima** (no save de teste):
    - Comece um **Jogo novo** e aceite a missão.
    - O save antigo fica em `aventura.anterior.json`, na mesma pasta.

## G. Som (o que o agente não pôde fazer)

20. **Ouça os quatro sons da missão:** aceitar, objetivo, marco e recompensa. Confira:
    - se combinam com a interface (madeira e kalimba);
    - se o do marco se destaca sem assustar;
    - se a música da vila continua por baixo sem mudar.
