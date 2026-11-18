# Roteiro de teste físico — combate (rodadas 09 e 09.1, 12–15 minutos)

Para o proprietário, com **teclado, mouse e alto-falante ou fone de verdade** na janela real. Nenhum teste físico foi
feito pelo assistente. As rodadas 09 e 09.1 foram validadas por:

- eventos injetados na mesma fila da janela;
- som gravado pela própria aplicação (*loopback* do OpenAL) e conferido no dispositivo PipeWire por captura.

## O que já foi verificado e o que falta

| Nível | Quem | O que cobre |
|---|---|---|
| Testes automatizados | `./gradlew test` (176 testes) | regras de dano, defesa, morte e FPS; câmera em toda a arena e em movimento; som certo no evento certo, uma vez, igual em qualquer FPS, mudo sem dispositivo |
| Fumaça na janela real | roteiros `tools/combate/`, `tools/combate91/` | o jogo abre, a luta acontece, capturas e vídeos (com som) são do próprio jogo |
| **Teste físico** | **você** | mouse, teclado e alto-falante de verdade; latência sentida do clique e do som |
| **Sensação** | **você** | se é divertido, se o golpe pesa, se a câmera ajuda, se o som combina — a aprovação é sua |

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo            # ou ./gradlew jogo -Ptecnico (F3 mostra as caixas de golpe)
```

**Antes de começar:** volume do sistema num nível confortável. O som do jogo sai em 80%; o volume e o mudo ficam na
pausa (Esc), e a tecla **M** liga e desliga o som.

**Controles:**
- WASD: andar;
- Shift: correr;
- mouse: câmera;
- **clique esquerdo: atacar** (de novo, no ritmo, encadeia horizontal → diagonal → descendente);
- **botão direito segurado: defender**;
- R: recomeçar;
- Esc: pausa;
- M: mudo.

## 1. Chegar (2 min)

1. **Entrar:** clique em "Entrar na vila" e desça a trilha para o sul até a clareira com a pedra grande.
   - [ ] Os **passos** acompanham os pés?
   - [ ] São discretos (não cansam)?
2. **Alerta:** chegue a cerca de 5 u de frente para o fungo.
   - [ ] O "hup-hup!" de alerta comunica que ele te viu sem soar como monstro?
3. **Recuar:** recue para a vila.
   - [ ] Ele volta para a toca?

## 2. Ler e evitar (3 min)

1. **Preparação:** deixe-o chegar e preparar a cabeçada.
   - [ ] O **resmungo crescente** ajuda a perceber o perigo junto com a animação?
   - [ ] Está alto demais ou baixo demais?
2. **Esquiva:** quando ele tremer, corra de lado.
   - [ ] A cabeçada sai com um "fuu" grave e erra?
3. **Dano:** fique parado e leve uma.
   - [ ] O "bonc" + "uf!" é claro sem ser violento?
   - [ ] O herói reage e volta logo?
4. **Defesa:** de frente, segure o botão direito antes da cabeçada.
   - [ ] O "tunc" do escudo soa **diferente** do dano e satisfaz?

## 3. Atacar (3 min)

1. **Fora de alcance:** ataque de longe.
   - [ ] Só o "fuu" do corte, **sem** som de acerto?
2. **Na recuperação:** acerte o fungo quando ele cambalear.
   - [ ] O som de acerto cai **junto** com o contato (pausa, lampejo, faíscas)?
   - [ ] Acertar e errar se distinguem de olhos fechados?
3. **Encadear:** três cliques no ritmo.
   - [ ] Os três cortes soam diferentes (horizontal, diagonal, descendente mais pesado)?
   - [ ] Repetidos, cansam?
4. **Latência:** clique várias vezes, observando.
   - [ ] O golpe sai no clique?
   - [ ] Sentiu atraso entre o clique e o "fuu"?
5. **Vencer:** derrote o fungo.
   - [ ] O "piiuuu-puf" de derrota e o sinal de vitória são claros?
   - [ ] Os sinais combinam com a vila?

## 4. Câmera (3 min) — o foco da 09.1

1. **Campo aberto:** lute no meio da clareira.
   - [ ] Você vê herói, espada, escudo e o fungo ao mesmo tempo?
   - [ ] Com o fungo colado, a câmera olha mais de cima: incomoda ou ajuda?
2. **Carvalho:** lute de costas para o carvalho e contorne-o lutando.
   - [ ] A cabeça do herói toma a tela em algum momento?
   - [ ] A copa atrapalha?
3. **Pedra e toco:** lute encostado na pedra grande e depois no toco.
   - [ ] O fungo some?
   - [ ] A câmera dá algum giro grande que troca a direção do WASD?
4. **Encosta:** lute na descida ao sul.
5. **Mouse:**
   - gire o mouse devagar com o fungo à frente;
   - [ ] a vista acompanha sempre, sem "travar" nem pular de lado?
   - dê um giro rápido de 360°;
   - [ ] a câmera entra na pedra?
   - [ ] Pula?
6. **Sensibilidade:** ajuste na pausa.
   - [ ] Fica confortável?

## 5. Perder, recomeçar, pausar (2 min)

1. **Perder:** deixe-o vencer.
   - [ ] O sinal de derrota é claro e curto?
2. **Recomeçar:** use "Tentar de novo".
   - [ ] Fica algum som preso tocando?
3. **Pausar:** pause (Esc) no meio de uma preparação e retome.
   - [ ] O som congela e volta sem uma pilha de sons atrasados?
4. **Volume:** na pausa, teste Volume − / + e "Som: mudo".
   - [ ] Funcionam?
   - [ ] Os passos e o jogo continuam normais no mudo?

## Anote

| Pergunta | Nota (1–5) | Comentário |
|---|---|---|
| É divertido enfrentar o fungo-cardo? | | |
| O golpe pesa (contato, som) sem travar? | | |
| Acerto × erro × bloqueio × dano se distinguem pelo som? | | |
| O som está sincronizado com a animação? | | |
| Volume e equilíbrio confortáveis? (algum som alto/baixo demais?) | | |
| Os sons combinam com a Fábula Voxel? Algum soa "eletrônico" demais? | | |
| A preparação dele é legível e justa? | | |
| A defesa é útil sem ser trivial? | | |
| A câmera mostra a luta (inclusive perto do carvalho e da pedra)? | | |
| A câmera obriga você a brigar com ela? | | |
| Sensibilidade do mouse e latência do clique | | |
| Dificuldade inicial (muito fácil · certa · difícil) | | |
| Bugs, travamentos, sons presos | | |
