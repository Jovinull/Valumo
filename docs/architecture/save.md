# Jogo salvo v1 (rodada 10)

`vx.game.SaveGame`, contrato `valumo.save` versão **1**. Um arquivo só (`aventura.json`), separado das aparências
(`output/aparencias/`) e das preferências (`output/preferencias*.json`). Decisão: [ADR 0024](../decisions/0024-missoes-e-save.md).

| Propriedade | Padrão | `-` |
|---|---|---|
| `-Dvx.savegame=PASTA` | `output/saves` | desligado: nada lido nem gravado (testes, medição de desempenho) |

**QA nunca usa a pasta do proprietário.** `tools/review10.sh` cria pastas temporárias para o save, as preferências do
jogo, as do som e as aparências, e o Gradle passa `vx.savegame=-` a todos os testes.

## Conteúdo

```json
{
  "formato": "valumo.save", "versao": 1,
  "id": "16 hexadecimais — muda a cada jogo novo",
  "cena": "jogo_vila_e",
  "aparencia": { "contrato": "valumo.aparencia", "versao": 2, "…": "o mesmo JSON do compositor (Appearance.toJson)" },
  "jogador": { "x": 0.6, "z": 4.4, "direcao": 200.0 },
  "tempoDeJogo": 123.4,
  "progresso": {
    "moedas": 30,
    "objetosAtivos": ["obj.posto.marco"],
    "missoes": {
      "quest.trilha.luzes_do_posto": {
        "estado": "concluida", "aceita": true, "recusada": false, "objetivo": 3, "concluida": true,
        "recompensaEntregue": true, "derrotados": ["trilha.fungo.1", "trilha.fungo.2"], "lembrancas": [],
        "aceitaEm": 12.5, "concluidaEm": 95.0
      }
    }
  }
}
```

- **Aparência embutida:** usa o contrato do compositor. Um preset sem mudança volta idêntico (`SaveGameTest`, os três
  presets), e o gate 51/0 continua valendo porque nada no compositor mudou.
- **`estado` é informativo:** é conferido contra os campos (estado gravado diferente do derivado = recusado).
- **Inimigos derrotados:** as identidades estáveis contadas. Não voltam ao carregar.
- **Recompensas entregues:** `recompensaEntregue` por missão, mais as moedas.

## Quando grava

- **Na pausa ("Salvar jogo")** e **nos marcos da missão**: aceitar, objetivo cumprido, marco aceso, concluir.
- **O que bloqueia** (`GameLogic.saveBlocker`, com o motivo na tela): pausa de impacto, luta, herói caído, herói no ar,
  jogo não começado, save desligado.
- **Automático:** espera o primeiro **passo lógico** seguro, nunca a cada quadro.

## Escrita atômica

1. Arquivo temporário **na mesma pasta** (`aventura-*.tmp`).
2. Escrito e sincronizado (`FileChannel.force`).
3. Fechado.
4. **Relido e validado** com o mesmo leitor do Continuar. Se falhar, o save antigo fica intacto e o temporário some.
5. **Jogo novo por cima de outro jogo** (id diferente): o anterior é copiado para `aventura.anterior.json`.
6. Movimento atômico por cima (`ATOMIC_MOVE`). Sem suporte no sistema de arquivos, vira movimento com substituição.

## Leitura

| Situação | Resultado |
|---|---|
| sem arquivo | sem save: Continuar apagado, "nenhum jogo salvo" |
| JSON ilegível, vazio, binário, lista, campo ausente ou de tipo errado | recusado com o motivo curto; original em `aventura.json.invalido` |
| `formato` diferente, `versao` ≠ 1 (inclusive texto) | recusado (sem migração falsa: só a v1 existe) |
| id inválido, cena desconhecida, coordenada fora de ±64 u ou não finita, tempo negativo | recusado |
| missão desconhecida, estado que não fecha (recompensa sem concluir, objetivo fora de faixa, objetivo passado sem todos os alvos, marco aceso fora de hora, inimigo que não é alvo, moedas negativas) | recusado (`QuestSystem.InvalidState`) |
| arquivo maior que 512 KB | recusado sem ler |
| aparência incompatível (catálogo mudou) | **o progresso continua**: volta o modelo Aventureiro, com aviso na tela |
| posição fora do chão pisável, fora do limite ou dentro de um sólido | ponto seguro: o início da vila, diante da estalagem (`jogo.inicio`), com aviso |
| missão nova que o save não conhece | disponível (save de antes dela existir) |
| campo desconhecido | ignorado |

Um save recusado não impede o "Jogo novo". O primeiro salvamento dele substitui o arquivo inválido; a cópia em
`.invalido` fica.

Testes: `SaveGameTest` (ida e volta, escrita atômica, 19 corrupções, aparência, versão) e `AdventureTest` (posição
inválida, save inválido, continuar em cada fase).

## Rodada 11 — v2 para o mundo procedural

O mundo gerado usa `versao: 2`, com os blocos `mundo` (semente, gerador, versão, tentativa, região) e `vinculos` (as
missões instanciadas resolvidas), numa pasta própria (`output/saves-procedural/s<semente>/`). A v1 acima continua sendo
o formato do mundo fixo, sem mudança e sem migração. Detalhes: [persistência do mundo
procedural](persistencia-mundo-procedural.md), [ADR 0026](../decisions/0026-save-v2-mundo-procedural.md).

## Rodada 11.1 — onde os saves moram agora

A aventura é a pasta do par personagem × mundo: `output/mundos/<mundo>/aventuras/<personagem>/aventura.json`.

- **Formatos:** v1 na vila (`mundos/vila/…`), v2 num mundo gerado. Nenhum formato novo (sem v3).
- **Saves antigos** (`output/saves/aventura.json`, `output/saves-procedural/s<N>/`):
  - ficam onde estão, intactos;
  - entram no catálogo por **importação explícita por cópia**, validada antes, com registro por sha256 (nunca duas
    vezes);
  - nunca por cima de uma aventura existente.
- **Continuar:** a aparência vem do personagem, não do save; o resto (missões, moedas, objetos, posição, tempo) vem do
  save, pelas mesmas regras de leitura acima.

Detalhes: [catálogo de mundos](catalogo-de-mundos.md), [ADR 0028](../decisions/0028-personagens-mundos-e-aventuras.md).

## Fechamento da 11.1 — sair sem perder

- **Alterações não salvas:** existem quando o progresso das missões ou a posição do herói (0,25 u) mudaram desde o último
  salvamento, o Continuar ou o começo (`GameLogic.unsaved`). O tempo de jogo sozinho não conta.
- **Pedido de sair** (Menu principal, Sair do jogo, X da janela): sem alterações, sai direto. Com alterações, confirma.
  - "Salvar e sair" usa o salvamento de sempre (atômico, validado). Falhou: não sai.
  - "Sair sem salvar" não toca no arquivo: o último save (manual ou automático dos marcos) fica como estava.
