# Como adicionar uma missão

Sistema: [../architecture/missoes.md](../architecture/missoes.md). Exemplo completo: "As Luzes do Posto"
([contrato](../architecture/missao-luzes-do-posto.md)). Uma missão nova é **dados + um teste**. Código só se precisar de
um tipo de objetivo ou de recompensa que ainda não existe.

## 1. Defina antes de escrever

- **Quem dá:** um NPC da cena; o id é a pasta do rig (`characters/npc/bramor` → `bramor`).
- **Objetivos em ordem**, dos três tipos que existem: derrotar (identidades estáveis), interagir (objeto do mundo) e
  falar. O último é falar com quem deu a missão.
- **Recompensa:** só `moedas` hoje.
- **Onde cada passo acontece**, e se o jogador consegue voltar a pé.
- **Matriz de travamentos (§16 da rodada 10):** o que acontece se o jogador fizer cada passo antes da hora, cair, recomeçar,
  salvar, sair e voltar. Nada pode duplicar recompensa, contar duas vezes, reabrir, apagar progresso, deixar inimigo
  inalcançável nem prender o jogador.

## 2. Dados

1. **`assets/scenes/missoes_e.json`:** uma entrada nova em `missoes`.
   - `id` no formato `quest.<lugar>.<nome>` (minúsculas, `_`).
   - `doador`, `objetivos`, `recompensa` e, se tiver luta, `encontro`.
2. **Inimigos:** um encontro novo em `assets/scenes/combate_e.json → encontros`.
   - Cada inimigo com o **5º item = identidade estável** (`<lugar>.<inimigo>.<n>`).
   - Use os mesmos ids em `alvos`.
   - Nunca reaproveite identidades de outra missão nem dos encontros de teste.
3. **Objeto do mundo** (se houver): em `assets/scenes/jogo_vila_e.json → jogo.objetos`: `id`, posição, rumo, `alcance`,
   `modelos`, `raio` e `placa`.
   - Os modelos são props gerados em `tools/env_e.py` (nunca `.vox` à mão), registrados com `ps.add(...)`.
   - Regenere com `python3 tools/build_assets.py env_e`.
4. **Falas:** em `assets/scenes/dialogos_e.json → npcs.<doador>`.
   - `missao` e os **10 contextos** de `DialogueBook.GIVER_CONTEXTS` (o leitor recusa se faltar um).
   - As reações citadas em `escolhas.segue`.
   - Quem só comenta: `acrescenta: true`, com `missao_ativa`/`missao_concluida`.
   - Frases do objeto em `objetos.<id>`: `nome`, `acao`, `acaoAtivo`, `ativou`, `jaAtivo`, `semMissao`, `cedo` e `nada`.
5. **Som:** reaproveite `missao.*`. Um som novo segue a skill `sound-design` (fonte editável em `fontes/audio/sfx/`,
   catálogo `assets/audio/sons.json`, `audio_qa` 0 alertas).

Limitação de hoje: um NPC dá **uma** missão (o `DialogueBook` liga o NPC a uma missão). Duas missões do mesmo NPC pedem
estender o arquivo de falas (contexto por missão) e a regra de escolha.

## 3. Teste (obrigatório)

Num teste novo, ou em `QuestTest`/`AdventureTest`:
- **Arquivo:** a missão carrega com os objetivos e a recompensa esperados.
- **Caminho feliz** pela lógica do jogo (`AdventureTest.started`, `talk`, `kill`, `useBeacon` servem de modelo): cada
  estado, cada salvamento automático, as moedas uma vez.
- **A matriz §16 da sua missão:** antes, durante e depois.
- **Save:** ida e volta em cada fase (`QuestSystem.toJson`/`fromJson`); continuar num GameLogic novo.
- **Contextos:** cada um dos 10 alcançável pela regra.

```bash
./gradlew :game:test --tests 'vx.game.QuestTest' --tests 'vx.game.AdventureTest' --tests 'vx.game.SaveGameTest'
```

## 4. Olhe e meça

- **Roteiro de entrada real** em `tools/aventura/` (modelo: `01-aventura.txt`), com saves temporários
  (`tools/review10.sh` mostra como).
- **Capturas** do objeto antes/depois do mesmo ponto de câmera, a placa do E, as escolhas, o rastreador e o diário.
- **Vídeo** com o som da aplicação (`-Dvx.som=loopback`).
- **Desempenho** com os mesmos roteiros da rodada anterior (`tools/qa/medir_desempenho.sh`).
- **Portão** da skill `engineering-qa`.

Nunca rode QA com a pasta de saves do proprietário: `-Dvx.savegame=PASTA_TEMPORARIA -Dvx.prefs=- -Dvx.prefs.audio=-`.
