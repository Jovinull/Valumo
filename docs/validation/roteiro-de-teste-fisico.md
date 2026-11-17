# Roteiro de teste físico (teclado e mouse de verdade)

As rodadas 07.2 e 08 validam a entrada por roteiros que injetam eventos na **mesma fila** da janela que o GLFW
preenche (`Window.inject`). Isso prova que o código do jogo responde aos eventos, mas **não** substitui mãos no
teclado e no mouse. Nesta máquina (sessão Wayland, sem ferramenta de automação do sistema) não houve clique
físico, cursor preso pelo sistema nem tela HiDPI. Este roteiro é para o proprietário. Leva uns 15 minutos.

Anote o que falhar em `docs/validation/problemas-conhecidos.md` (ou mande para o agente) com: passo, o que
aconteceu, resolução da janela e escala da tela.

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo      # protótipo jogável (rodada 08)
./gradlew editor    # criador de personagens (rodada 07.2)
```

## A. Criador (`./gradlew editor`)

1. Passe o mouse sobre as abas, os cartões e as cores: cada um sobe e brilha. Clique em **Cabelo** e depois em
   **Curto**: o herói muda em menos de meio segundo.
2. Arraste com o botão esquerdo sobre o herói, para os dois lados: ele gira. Solte fora da área: o giro para.
   Arraste com o direito: o enquadramento sobe e desce.
3. Use a roda sobre o herói (aproxima e afasta, com limite) e sobre o painel numa janela baixa (o painel rola).
4. Clique em **Salvar**, **Restaurar** (aparece a confirmação), **Carregar** (lista) e **Pronto**.
5. Redimensione a janela, maximize e volte. Os botões continuam clicáveis onde aparecem.
6. Com tela HiDPI ou escala de 125–200%: o clique cai exatamente sobre o controle desenhado?

## B. Jogo (`./gradlew jogo`)

1. **Escolha:** clique em cada aparência (o herói muda) e em **Entrar na vila**. As setas e o Enter também
   funcionam.
2. **Câmera:**
   - ao entrar, o cursor fica preso;
   - mova o mouse em círculos: a câmera gira suave, sem saltos;
   - a roda aproxima e afasta;
   - perto da estalagem e das árvores, a câmera não deve atravessar paredes nem entrar no herói.
3. **Movimento:**
   - **Andar e correr:** `W A S D` andam e `Shift` corre. O herói acelera e freia em menos de meio segundo,
     e os pés não devem patinar.
   - **Contornos e degraus:** ande junto às pedras, à cerca e aos caixotes, e suba a encosta até o patamar da
     estalagem pelo lado do poço.
   - **Água:** tente entrar no lago; a margem molhada não é pisável.
4. **Kelyra** (perto do posto de trilha):
   - ao chegar perto e de frente, aparece **E Conversar com Kelyra**;
   - `E` (ou clique em **Continuar**) avança as falas;
   - `Esc` encerra;
   - durante a conversa o herói não anda.
5. **Pausa:**
   - `Esc` pausa e solta o cursor; **Continuar** volta ao mesmo lugar;
   - em **Sensibilidade do mouse** (− e +), teste se a mudança se sente;
   - experimente **Inverter vertical**.
6. **Trocar aparência / Abrir o criador:** abra o criador pela pausa, mude o cabelo, clique em **Pronto** e
   **Levar para a vila**. O herói da vila deve ter a aparência nova.
7. **Saída:**
   - **Sair do jogo** fecha a janela;
   - fechar pelo X da janela também deve sair sem erro no terminal.
8. Jogue livremente por 3–5 minutos. Anote:
   - o herói preso em algum ponto;
   - a câmera tremendo;
   - os pés atravessando o chão;
   - degraus que deveriam subir e não sobem;
   - a velocidade do mouse estranha.

## O que já foi verificado sem você (para comparar)

| Item | Como foi verificado |
|---|---|
| Lógica de movimento, colisão, câmera, interação e diálogo | testes `GameplayTest` (eventos sintéticos) |
| Criador | testes `CreatorTest` (eventos sintéticos) |
| Janela real abre, desenha e fecha | teste de fumaça (`--demo N`) |
| Fluxo completo | vídeos por roteiro de entrada (`tools/review8.sh`, `tools/review72.sh`) |
