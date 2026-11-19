---
name: audio
description: Criar, integrar, revisar e validar efeitos sonoros do Valumo (rodada 09.1 em diante) — síntese por script em tools/audio_e.py (ruído filtrado, modal, voz por formantes, Karplus-Strong), mistura escrita à mão em assets/scenes/sons_e.json por alvo de nível, diretor que só lê a lógica (CombatAudio, Footsteps), regras de reprodução do Sfx, OpenAL pelo LWJGL com queda para mudo, gravação do som da aplicação por loopback, prancha de espectrograma, medição de sincronia e teste no dispositivo sem tocar nos alto-falantes. Use ao mexer em qualquer som, volume, evento sonoro ou ao gravar vídeo com som. Rodada 09.2: esquiva_rolar, esquiva_recuo, salto, pouso e fungo_desfaz; diretor com estado por inimigo (grupo).
---

# Som (rodada 09.1 em diante)

Arquitetura, números e proveniência: `docs/architecture/audio.md`; decisão: ADR 0016. Mistura:
`assets/scenes/sons_e.json` (à mão). Sons: `assets/audio/sintese/` (GERADOS, fora do git; o resto de `assets/audio/` é o catálogo versionado da 09.4).

## Regras que não se negociam

- **O som só lê a lógica.**
  - O diretor roda DEPOIS do passo e não escreve em `Combat`, `Hero`, `Enemy` nem `Player`.
  - A variação vem do hash (semente, efeito, passo), nunca do `Random` da lógica.
  - Teste obrigatório: a luta é idêntica com e sem som, e os sons são iguais a 30, 60, 120 e 240 FPS.
- **Som = evento real.**
  - Impacto só no passo em que a lógica confirmou o contato (`Combat.impacts`), nunca no clique.
  - O "fuu" de golpe sai pela trajetória: o pico do arquivo cai no meio da janela `ativo`.
- **Origem:** só síntese em `tools/audio_e.py`. Nada de amostras de terceiros, de outros jogos nem de IA geradora.
  Proveniência por efeito na tabela de `audio.md` §4.
- **Falha segura:** sem dispositivo, o jogo segue mudo (`Sfx.backendFor`). Testes e roteiros não dependem de
  dispositivo.
- **Vídeo com som:** o som do vídeo é o que o jogo gravou (`-Dvx.som=loopback`), nunca uma trilha montada depois. Se não
  der para gravar, diga e não afirme sincronia.
- **O ouvido é do proprietário:** números e espectrograma não aprovam timbre.

## Criar um efeito

1. **Síntese** em `tools/audio_e.py`: uma função por efeito, com semente fixa e parâmetros nomeados.
   - Blocos: `whoosh` (ruído em passa-faixa que segue a velocidade), `modal` (madeira/metal), `voice` (harmônicos sem
     aliasing + formantes), `pluck` (corda), `env_ad`, `gate`, `svf`.
   - Termine com `finish` (DC fora, bordas sem estalo, −3 dBFS).
   - Variações (2–4) para o que se repete; uma só para sinais únicos.
   - Entrada em `catalog()`.
2. **Revise:**
   - rode `python3 tools/audio_e.py` e veja os números (`analise.json`): centroide coerente com o material, 0 cortes e
     DC ~0;
   - rode `python3 tools/audio_board.py prancha.png` e olhe:
     - ataque (o contato é imediato? o "fuu" cresce?);
     - decaimento;
     - faixa no espectrograma;
     - **linhas verticais** (estalos);
     - **listras finas espelhadas** (aliasing).
3. **Mistura:** linha em `sons_e.json`.
   - `ganho` por alvo de nível no jogo: impactos ≈ −18,5 dB, vozes ≈ −22/−23, "fuu" e avisos ≈ −25, passos ≈ −34.
   - Calcule o nível com o trecho de `audio.md` §4.
   - `espacial` só para o que é do inimigo; o que é do herói sai colado ao ouvinte, para dar clareza.
   - `vozes` e `intervalo` contra repetição.
4. **Evento:** no diretor (`CombatAudio` ou outro observador), lendo estado e listas de eventos.
   - **Sons longos ligados a um estado** (como `fungo_preparo`): guarde a voz e chame `stop` se o estado for
     interrompido.
5. **Teste** em `CombatAudioTest`:
   - um som por evento real;
   - nada no evento errado (errou ≠ acertou, bloqueio ≠ dano);
   - silêncio depois da morte;
   - pausa e recomeço.
   - Mantenha `everyEffectIsGeneratedCleanAndEveryGeneratedFileIsUsed` passando.
6. **Grave e meça:**
   - `tools/review91.sh videos` gera o MP4 com o som do jogo e `*-sincronia.txt`: sons isolados devem ficar a
     ~1 ms do quadro do evento;
   - `tools/review91.sh sons` gera o catálogo.

## Testar no dispositivo real sem tocar nos alto-falantes

```bash
ORIG=$(pactl get-default-sink); MOD=$(pactl load-module module-null-sink sink_name=valumo_teste)
pactl set-default-sink valumo_teste          # OpenAL Soft ignora PULSE_SINK: troque o padrão
parec -d valumo_teste.monitor --file-format=wav captura.wav & P=$!
JAVA_OPTS=-Dvx.som=dispositivo game/build/install/game/bin/game --roteiro R.txt SAIDA 30
kill -INT $P; pactl set-default-sink "$ORIG"; pactl unload-module $MOD   # SEMPRE restaure
```

Comandos de roteiro:
- `som-tocar EFEITO`, `esperar-real S`: tempo de parede e latência informada pelo dispositivo;
- `som-medir N`: custo por pedido em regime;
- `sons`: o que tocou, com passo e tom;
- `record on`: marca o passo inicial para a sincronia.

## Armadilhas já encontradas (não repita)

- **Osciladores de pulso ou dente-de-serra ingênuos:** aliasing áspero nas vozes agudas. Use `glottal` (soma de
  harmônicos).
- **Ruído branco num passa-faixa só:** chiado (centroide em 6 kHz no "fuu"). Use filtro em cascata e passa-baixas
  antes.
- **Cortar um som por máscara retangular** (`t < x`): estalo. Use `gate` (cosseno). O mesmo vale para o fim de cada
  nota do Karplus-Strong.
- **Acerto espacial preso ao inimigo:** a atenuação pela distância o deixava abaixo do "fuu". Sons do herói ficam
  colados ao ouvinte.
- **Aviso mais alto que impacto** (a preparação estava acima do acerto): meça o nível, não confie no pico.
- **Diretor criado sem ver o confronto:** engolia o que aconteceu no primeiro passo. `CombatAudio(sfx, combat)` já
  nasce olhando.
- **Pedido frio:** ~0,3 ms por som e 12 ms no primeiro passo. O `Sfx` aquece em silêncio na criação.

## Rodada 09.2

- **Efeitos novos:** `esquiva_rolar`, `esquiva_recuo`, `salto`, `pouso`, `fungo_desfaz` (funções `roll`, `backstep`,
  `jump`, `land`, `crumble` em `tools/audio_e.py`).
- **Mistura:** por alvo de nível, como os da 09.1. Os efeitos da 09.1 não mudaram (arquivos e ganhos
  iguais; escuta do proprietário pendente — estado em `docs/architecture/audio.md`).
- **Diretor (`CombatAudio`):**
  - guarda o estado de CADA inimigo;
  - o som de acerto no fungo sai do inimigo atingido (`Combat.Impact.who`);
  - salto e pouso vêm dos contadores do `Player`; a esquiva, de `Hero.dodges`.
- **O som não muda a luta:** continua só lendo. O teste "o som não muda a luta" segue valendo.

## Rodada 09.4 — um dispositivo com o catálogo (ADR 0023)

- **O `Audio` (catálogo: música, ambiente, passos, interface) abre o OpenAL.** O `Sfx` do combate toca no mesmo
  contexto (`OpenAlBackend.shared(Audio.output())`). Nunca abra um segundo dispositivo: seriam duas misturas, e no
  loopback a gravação correria em dobro.
- **Volume do combate por voz:** o ganho do ouvinte é de todo o contexto (baixaria a música).
- **Um ouvinte:** a câmera (o `GameAudio` usa o mesmo).
- **Sons gerados:** `tools/audio_e.py` → `assets/audio/sintese/` (fora do git). O resto de `assets/audio/` é versionado.
- **Passos:** os do catálogo. `Footsteps` (`passo_terra`) só age sem catálogo.
- **Gravação:** `-Dvx.som=loopback` (ou `-Dvx.audio=loopback`) → `SAIDA/audio.wav`, mistura feita pelo `Audio.update`.

