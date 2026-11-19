---
name: engineering-qa
description: Auditar a saúde de engenharia do Valumo — erros e avisos da IDE (aba Problems do VS Code) e do compilador, build Gradle (incremental, clone limpo, dependências), vazamento de recursos de GPU/áudio, regressão de desempenho com medição antes/depois e perfil JFR, testes frágeis ou vazios, validadores que passam sem conferir nada. Use antes de publicar uma rodada, depois de integrar ramos paralelos, quando o editor mostrar muitos problemas, quando o jogo "ficar lento" ou quando um teste/validador verde parecer bom demais.
---

# QA de engenharia

Não é revisão visual (skill `visual-qa`) nem de som (`audio-qa`): é a pergunta "o que está verde está verde de verdade?".
Primeira auditoria completa e os números de referência: `output/qa/engineering-audit/` (local) e
`docs/validation/problemas-conhecidos.md` (linhas "Auditoria").

## Regras

1. **Reproduza antes de corrigir.** Teste que falha, roteiro que mede ou contagem que cresce — depois a correção, depois o
   mesmo teste verde. Achado só de leitura de código vai para o relatório como "confirmado por leitura", não como medido.
2. **Meça antes de otimizar**, com os binários de antes e de depois na MESMA sessão (a GPU integrada muda com carga e
   temperatura). Nunca compare número de hoje com número de relatório antigo.
3. **Nunca esconda diagnóstico**: nada de `@SuppressWarnings` em massa, de baixar severidade, de aumentar tolerância de
   validador ou de alterar referência canônica para passar.
4. **Isolamento:** `git worktree add -b audit/<assunto> ../<pasta> main`; sem commit nem push sem pedido do proprietário.
5. **Som:** testes e roteiros são mudos; para som real sem alto-falante use `-Dvx.som=loopback`. `--demo` é mudo.

## 1. Estado de partida

```bash
git status --short && git worktree list && git log --oneline -5
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew test --console=plain          # conte os testes: */build/test-results/test/*.xml (engine, viewer, game)
```

Guarde os binários antes de mexer: `./gradlew :viewer:installDist :game:installDist` e copie `*/build/install/*` para uma
pasta em `output/` (servem de "antes" para desempenho, validadores e vazamento).

## 2. Aba Problems do VS Code

Não confie na contagem da aba sem saber a origem. Três medidas independentes:

| Medida | Comando | O que diz |
|---|---|---|
| O que a IDE está mostrando agora | `python3 tools/qa/ide_marcadores.py <…/jdt_ws/.metadata/.plugins/org.eclipse.core.resources/.projects> saida.json` | marcadores do Java LS por projeto; **projetos listados = módulos importados** |
| O que um import limpo mostraria | `python3 tools/qa/ide_diagnosticos.py . /tmp/jdt/ws saida.json` | o mesmo servidor da extensão, workspace novo |
| O que o compilador diz | init script com `-Xlint:all` em `tasks.withType<JavaCompile>` (ver o relatório da auditoria) | avisos do `javac` (a IDE usa o ECJ: avisa mais) |

Classifique: **A** erro real · **B** aviso real · **C** diagnóstico velho da IDE · **D** ambiente · **E** extensão ·
**F** informativo. Sinais de C/D: erros "cannot be resolved" em bloco num pacote cuja dependência entrou depois do último
import (foi o caso do `vx.engine.audio` com OpenAL/STB: 170 erros falsos); módulo do `settings.gradle.kts` ausente da lista
de projetos; diagnóstico "The build file has been changed and may need reload". Correção de C/D é do lado da IDE:
**Java: Clean Java Language Server Workspace** (ou `"java.configuration.updateBuildConfiguration": "automatic"` no
`.vscode/settings.json` local). Nesta máquina (Wayland/GNOME, sem `ydotool`/`wtype`) não há automação da interface: diga
isso em vez de inventar a contagem.

## 3. Gradle

- **Incremental:** altere uma fonte escrita à mão que os testes leem (`assets/scenes/combate_e.json`) e rode
  `./gradlew test`: a tarefa do módulo tem de EXECUTAR (não `UP-TO-DATE`). `assets/` inteiro é entrada dos testes.
- **Entradas da geração:** `TRACE_OUT=x.json python3 tools/qa/entradas_dos_assets.py` lista os módulos e arquivos que
  `build_assets.py` realmente lê. Hoje só `tools/*.py` do primeiro nível + numpy; módulo de subpasta que entrar na geração
  precisa ser declarado em `build.gradle.kts`.
- **Determinismo:** hash de todos os arquivos gerados antes e depois de regenerar (remova uma saída e rode `./gradlew assets`).
- **Clone limpo:** `git clone` para uma pasta nova, PATH **sem** a `.venv` do checkout, e `./gradlew test :viewer:installDist
  :game:installDist`. A geração precisa de numpy (`tools/requirements.txt`); o build usa `-Pvx.python=`, `VX_PYTHON`, a
  `.venv` da raiz ou o `python3` do PATH, nessa ordem, e falha com mensagem clara se faltar.
- `./gradlew build --warning-mode all` sem depreciação; `./gradlew :engine:dependencies --configuration runtimeClasspath`
  sem duplicata. Não use `--scan` (publica dados) nem `--refresh-dependencies` sem motivo.

## 4. Recursos nativos

- **Malhas de GPU:** `Mesh.liveMeshes/liveBytes` saem no relatório `perf`. Vazamento do criador:
  `game --roteiro tools/qa/vazamento-criador.txt SAIDA 30 && grep -o "malhas vivas.*" SAIDA/volta-*.txt` → a contagem tem de
  ser igual nas cinco voltas. Quem cria `App`, `World` ou `Assets` é dono: feche (`AutoCloseable`).
- **Áudio:** um dispositivo por processo (ADR 0023). Sem saída (`SilentOut`) nada abre arquivo de música/ambiente
  (`AudioOut.streams()`); um pedido que falha não se repete a cada quadro. Confira com os testes de `AudioTest` da auditoria.
- **Outras threads:** só a "composicao" (`AsyncComposer`); toda falha dela precisa terminar o pedido (`failed`), nunca sumir.

## 5. Desempenho

```bash
tools/qa/medir_desempenho.sh output/.../bin-antes antes 3        # vila, combate um|grupo|chefe, criador, bench do viewer
tools/qa/medir_desempenho.sh output/.../bin-depois depois 3      # mesma sessão, nada compilando em paralelo
JAVA_OPTS="-Dvx.som=mudo -XX:StartFlightRecording=filename=x.jfr,settings=profile" game/build/install/game/bin/game --roteiro tools/jogo/desempenho.txt SAIDA 60
tools/qa/jfr_resumo.py x.jfr                                     # métodos e alocações da thread main
JAVA_OPTS=-Dvx.gpu=true viewer/build/install/viewer/bin/viewer --layout jogo_vila_e --bench "1920x1080,style=e,quality=padrao,runs=3"   # + tempo de GPU por passo
```

**Por passo:** `-Dvx.gpu=true` cerca sombra, cena, bloom e composição com consultas `GL_TIME_ELAPSED` e o bench imprime
`GPU por passo (ms/quadro)`. Para decompor um passo, desligue uma coisa por vez (`noshadow`, `outline=0`, `fxaa=0`,
`far=0`, `LxA`) e compare **o passo**, não o quadro: a GPU integrada baixa a frequência quando a carga cai
(`/sys/class/drm/card*/gt_cur_freq_mhz`: 1250 MHz a 1080p, ~980 a 360p), então quadro a 360p não é "custo sem pixels".
Sempre A/B/A/B na mesma sessão. Prancha de contato de um conjunto de capturas: `tools/qa/prancha.py SAIDA COLUNAS LARGURA IMG...`.

Leia sempre **lógica × desenho** em separado e as coletas de lixo. Referência da auditoria (1080p, Iris Xe, vila com 6
NPCs): quadro ≈ 10,3 ms antes do fluxo de posição da sombra (bench do viewer: 10,4 → 9,6 ms), lógica ≈ 0,30 ms, 0 coletas na medição. Lógica acima de ~1 ms de mediana ou coletas a cada segundo
= algo está alocando ou lendo arquivo por quadro (foi o caso da saída muda reabrindo três OGG por quadro: 8,4 ms). Roteiro
mudo e jogo com som não são o mesmo caminho: meça também com `-Dvx.som=loopback`.

## 6. Testes e validadores

- **Verde vazio:** variável local não usada num teste costuma ser uma verificação esquecida (o aviso do ECJ aponta).
  Validador de linha de comando precisa contar o que conferiu e falhar com zero (`Check.nothing`): rode-o numa pasta sem
  assets e confira o código de saída 1.
- **Número duplicado:** validador que copia janela/limite de um JSON valida o valor antigo quando o JSON muda — leia do JSON.
- **Ordem e estado global:** `-Djunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer\$Random` em
  `:game:test`/`:viewer:test` algumas vezes; estáticos mutáveis conhecidos: `VoxelMesher.conforming/crackMargin`,
  `FollowCamera.combatAssist`, `World.layout`, `Game.deathStyle`, propriedades `vx.*`.
- **Preferências do jogador:** testes e roteiros com `-Dvx.prefs=-`; roteiro que precisa gravar usa `-Dvx.prefs=` **e**
  `-Dvx.prefs.audio=` temporários.

## 7. Portão antes de entregar

```bash
./gradlew test :viewer:installDist :game:installDist
V=viewer/build/install/viewer/bin/viewer
$V --check && $V --check interrupcao-fina && $V --check golpe-andando && $V --check esquiva && $V --check-cabelos
tools/review7.sh gate                                   # presets × canônico: 0 pixel
tools/reference.sh output/tmp/ref && .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/ref
python3 tools/check_links.py && .venv/bin/python tools/audio/audio_qa.py --catalogo assets/audio/sons.json
python3 tools/qa/ide_diagnosticos.py . /tmp/jdt/ws output/tmp/ide.json     # 0 erros
```

`$V --occlusion` sai com 1 por causa da exceção conhecida do arco: compare a lista com a do binário de antes.

## 8. Relatório

Para cada achado: evidência → hipótese → reprodução → causa → correção mínima → teste → medida do efeito. Severidade:
**P0** build quebrado, corrupção, crash · **P1** erro Java real, corrida, bug de jogo reproduzível, vazamento, falso verde do
build, gargalo grande · **P2** aviso legítimo, robustez, teste frágil, documento oficial errado · **P3** limpeza. O que não
foi reproduzido fica como "não confirmado", com a condição que faltou. Atualize `problemas-conhecidos.md`.
