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
   - em **Sensibilidade** (− e +, a do mouse), teste se a mudança se sente;
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

## C. Rodada 09.4 — o que só você pode validar (escuta e decisões)

Nenhum som desta rodada foi avaliado por escuta. Use fones e depois a caixa; o pacote local
`output/visual-review/round-09-4/LEIA-ME.md` lista os arquivos na ordem (regenerável com `tools/review94.sh tudo`).

1. **Música da vila** — ouça `musica/vila_a/vila_a-escuta.wav` e `musica/vila_b/vila_b-escuta.wav`, depois jogue
   (`./gradlew jogo` toca a A; a B: `JAVA_OPTS=-Dvx.musica=vila_b game/build/install/game/bin/game`).
   - Soa como o Valumo ou como banco de instrumentos genérico? A, B ou nenhuma?
   - O laço passa sem emenda depois de ~1 min (A) / ~1 min 10 s (B)? A música cansa em 5 minutos de jogo?
2. **Interface** — no criador (`./gradlew editor`) clique bastante: "madeira e papel" incomoda depois de muitos
   cliques? Compare com `efeitos-sonoros/interface/contexto-proposta-B-sessao.wav` (vidro e dourado).
3. **Passos** — ande e corra na grama, no caminho de terra, na areia da margem do lago e (se alcançar) na pedra das
   encostas. Leem como superfícies diferentes? O passo coincide com o pé? Fica repetitivo?
4. **Ambiente** — vento sempre presente e água do lago aumentando ao chegar perto da margem e sumindo ao se afastar.
   O vento é discreto o bastante? A água soa como água?
5. **Volume** — Esc → Música e Efeitos (−/+). O equilíbrio padrão (música × passos × interface × ambiente) está bom?
   As preferências voltam ao reabrir o jogo?
6. **Personalização** — no criador: cabelos Crespo e Coque alto, Formato dos olhos (Rosto) e botas Viajante
   (categoria Roupa). Aprovar, pedir ajuste ou recusar cada estudo (todos marcados como experimentais).
7. **Decisão pendente** — o gesto de comemoração (EMOTE) do aventureiro atravessa o topete canônico (achado pela
   validação nova `viewer --check-cabelos`). Autorizar a correção da coreografia (muda as referências aprovadas)?
8. **Ambiente de trabalho** — o Audacity do Ubuntu foi instalado mas não teve a abertura com interface verificada:
   abra um WAV qualquer da pasta acima e confirme.

Responda o que soar mal com o arquivo e o instante; a correção volta às fontes em `fontes/audio/`.

## O que já foi verificado sem você (para comparar)

| Item | Como foi verificado |
|---|---|
| Lógica de movimento, colisão, câmera, interação e diálogo | testes `GameplayTest` (eventos sintéticos) |
| Criador | testes `CreatorTest` (eventos sintéticos) |
| Janela real abre, desenha e fecha | teste de fumaça (`--demo N`) |
| Fluxo completo | vídeos por roteiro de entrada (`tools/review8.sh`, `tools/review72.sh`) |
| Áudio (rodada 09.4) | `AudioTest` (laço exato, regras, catálogo, mixagem real e atenuação por distância no loopback do OpenAL), `GameAudioTest`, mistura do jogo gravada em sincronia com os quadros (`tools/review94.sh contexto ambiente`), QA técnico (`tools/audio/audio_qa.py`) — medição, não escuta |
