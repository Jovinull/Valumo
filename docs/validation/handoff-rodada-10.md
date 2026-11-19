# Handoff para a Rodada 10

Leia isto antes de começar a Rodada 10. Vale sem nenhum contexto da auditoria nem do fechamento do QA.

## Estado

- **Checkpoint:** tag `engineering-foundation-v0.3` na `main`
  ([relatório do fechamento](relatorio-fechamento-qa.md)). Tags anteriores: `combat-foundation-v0.2`,
  `playable-foundation-v0.1`.
- **O que existe:**
  - exploração da vila, conversa com 6 NPCs, criador de aparência e personalização (presets idênticos ao canônico);
  - combate com espada e broquel, com golpe andando, esquiva R2, salto, mira opcional, grupos, ensaio de chefe e morte
    voxel D2;
  - áudio por síntese própria (músicas A e B aprovadas; efeitos com a escuta pendente);
  - validadores, roteiros de captura e integração contínua.
- **O que não existe:** missões, inventário, progressão, savegame de jogo, mundo maior que a vila.
- **Titã Celeste (Velária):** candidato num ramo LOCAL `candidato/tita-celeste`, fora da `main`. A revisão de originalidade
  é do proprietário. Não use o Titã em conteúdo antes dela.

## Comandos

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
python3 -m venv .venv && .venv/bin/pip install -r tools/requirements.txt   # uma vez (numpy gera os efeitos do combate)
./gradlew test :viewer:installDist :game:installDist                       # testes + distribuições
./gradlew jogo                                                             # jogar
V=viewer/build/install/viewer/bin/viewer
$V --check && $V --check interrupcao-fina && $V --check golpe-andando && $V --check esquiva && $V --check-cabelos && $V --occlusion
tools/review7.sh gate                                                      # presets × canônico, 0 pixel
tools/reference.sh output/tmp/ref && .venv/bin/python tools/repro_compare.py imgs docs/art/reference output/tmp/ref   # 22/22, desvio 0
tools/qa/medir_desempenho.sh BINARIOS ROTULO 3                            # desempenho antes × depois (mesma sessão)
```

Portão completo: skill `engineering-qa` §7 e §7b. O CI (`.github/workflows/ci.yml`) roda só o que não precisa de GPU.

## Arquitetura em uma tela

- **Módulos:** `engine` (OpenGL, malha, áudio) ← `viewer` (assets, animação, compositor, captura, criador) ← `game`
  (lógica em passo fixo de 1/120 s, colisão, câmera, combate, interface).
- **Assets:** gerados por `tools/*.py` (determinísticos, fora do git). Feitos à mão e versionados: `assets/scenes/*.json`,
  `assets/anim/*.json` e `assets/audio/`.
- **Regras que viraram teste:**
  - o desenho não escreve em estado da lógica;
  - quem cria recurso de GPU fecha;
  - o som só lê a lógica;
  - o validador conta o que conferiu;
  - o leitor recusa arquivo malformado com o motivo;
  - preferência ilegível volta aos padrões e o original fica guardado em `.invalido`.

## Correções recentes que importam

- Combate (`CombatTest`): o clique dado durante a pausa de impacto vale; a coleira do fungo é respeitada (na borda, quem
  cede é o herói). **O teste físico pendente é o P48 em `problemas-conhecidos.md`.**
- Broquel 1 voxel mais aberto na esquiva de frente (roçava o chanel). Só no `sword_shield.json`.
- Música e ambiente não somem ao abrir o criador: a fila de áudio cresce até 4 s.
- Validadores reprovam o que não conferiram. A exceção do arco é explícita (`OcclusionCheck.FACE_EXCEPTIONS`).
- As 8 referências do repouso do cajado foram refeitas, com a causa provada.

## Limitações aceitas

- **Escuta:** o agente não ouve. Efeitos, passos, interface e ambiente aguardam a escuta do proprietário.
- **Medição:** feita só em Iris Xe com Mesa (Linux). A 60 Hz o jogo está limitado pelo vsync: a folga aparece como tempo
  de quadro, não como FPS.
- **Áudio:** dois sistemas lado a lado (catálogo e combate) no mesmo dispositivo. Unificar é a refatoração de áudio mais
  valiosa, mas não é urgente.
- **Validadores:** `--occlusion` e `--grip` são triagens por cápsulas e não substituem o vídeo.

## Cuidados para a nova missão e o savegame

- **Preferências do jogador** (`output/preferencias*.json`): leitura tolerante, e o arquivo inválido é guardado em
  `.invalido`. Um savegame novo deve seguir o mesmo contrato:
  - versão no arquivo (como `valumo.aparencia` v2, que migra v1);
  - leitura que nunca derruba o jogo;
  - original preservado;
  - testes de arquivo malformado, como `CorruptPrefsTest`.
- **Números do jogo** só em `assets/scenes/combate_e.json`, e o validador lê do JSON, nunca de uma cópia no código.
- **Antes de mexer em desempenho:** guarde os binários de antes (`*/build/install/*`) e meça A/B na mesma sessão.
- **Antes de aprovar conteúdo com arma:** `--check-cabelos`, que já mede o broquel, chapéus, capuzes e o salto.

## O que não deve ser refeito

- **A caça aos "220 problemas" do VS Code:** eram cache velho do Java Language Server. Se reaparecer:
  *Java: Clean Java Language Server Workspace* → *Reload and delete*.
- **A investigação de desempenho da vila:** a causa era o áudio mudo, e está corrigida.
- **A integração do Titã:** já está no ramo local. Falta a decisão, não o trabalho técnico.
- **A unificação completa do áudio e o vértice compacto e indexado:** pertencem a rodadas técnicas próprias, se o
  proprietário pedir.
