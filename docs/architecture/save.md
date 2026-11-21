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
- **Pedido de sair** (Menu principal, Sair do jogo, X da janela): numa aventura, sempre confirma. Sem alterações, a
  confirmação só oferece Sair e Cancelar; com alterações, Salvar e sair / Sair sem salvar / Cancelar.
  - "Salvar e sair" usa o salvamento de sempre (atômico, validado). Falhou: não sai.
  - "Sair sem salvar" não toca no arquivo: o último save (manual ou automático dos marcos) fica como estava.

## Rodada 12 — descobertas (campo opcional)

`progresso.descobertas`: os ids das descobertas de exploração desta aventura (ex.: `desc.r0.ruina.0`, a lareira da ruína
num mundo v2).

- **Quando aparece:** só quando há alguma. Um save sem descoberta é byte a byte o de antes, e um save sem o campo abre
  igual.
- **Validação:** id que a cena não tem = save recusado com o motivo (como objeto desconhecido).
- **Formato:** a versão do formato não mudou; continuam v1 (vila) e v2 (procedural). Detalhes:
  [persistência do mundo procedural](persistencia-mundo-procedural.md).


**Rodada 12.1 — tipos de lugar, o mesmo campo.** A v2 passou a ter três descobertas possíveis por região:

| Tipo | Id | Lugar |
|---|---|---|
| casa | `desc.r0.ruina.0` | o pote na lareira |
| torre | `desc.r0.torre.0` | o mirante |
| árvore | `desc.r0.arvore.0` | a árvore retorcida |

- **Nada novo no save:** os ids continuam em `progresso.descobertas`. O que o jogador viu (o pote vazio, "Lugares" no
  diário, "Lugar novo" só na primeira vez de um tipo) é **derivado** desses ids e da cena.
- **Sem migração:** não há campo novo, versão nova nem migração.
- **Saves da rodada 12** (v2 ainda não publicada; medido com o binário das duas rodadas):
  - **Abre:** quando os ids batem. O mundo abre com a região da 12.1, e a posição salva que caiu dentro de algo volta ao
    início.
  - **Recusa:** quando a mesma semente agora tem outra ruína e a descoberta salva não existe mais, o save é recusado com
    o motivo ("descoberta desconhecida: desc.r0.ruina.0"). O arquivo fica como estava, com a cópia `.invalido` ao lado,
    como todo save recusado. Não há migração que invente a descoberta.

Por isso a v2 só se publica **depois** de aprovada. Depois disso, mudar o resultado é versão nova (ADR 0029).

## Rodada 12.2 — save íntegro de outra região NÃO é corrompido

Antes, todo motivo de recusa virava "Jogo salvo inválido" e o arquivo era copiado para `.invalido`. O mundo "RUINAS"
(semente 31337, feito com a v2 da rodada 12) era um save íntegro, recusado só porque a região daquela semente mudou antes
da publicação da v2 (a casa virou torre). O leitor agora separa os casos:

| Caso | Exemplo | O que acontece | `.invalido` | Mensagem |
|---|---|---|---|---|
| 1. JSON corrompido | arquivo cortado no meio | recusado | sim (cópia; o original fica) | "Jogo salvo inválido: JSON ilegível (…)" |
| 2. formato inválido | `formato` de outro programa, campo fora de faixa | recusado | sim | "Jogo salvo inválido: …" |
| 3. versão futura | `versao` 3 ou mais (inteira) | recusado, nada tocado | **não** | "Esta aventura foi salva por uma versão mais nova do jogo. Ela continua guardada como estava" |
| 4. versão antiga válida | v1 da vila, v2 de região | abre | — | — |
| 5. mundo de versão de testes que mudou | v2 não publicada; mundo, vínculos, missão, objeto, descoberta ou inimigo que a região de agora não tem | recusado, nada tocado | **não** | "Esta aventura é de uma versão de testes deste mundo, que mudou antes do lançamento. Ela continua guardada como estava; crie outro mundo para jogar" |

- **Onde:**
  - `SaveGame.Incompatible` (casos 3 e 5; o detalhe técnico vai para o log, nunca para a tela);
  - `QuestSystem.UnknownReference` (referência que a cena não tem);
  - `RegionGenerator.published(versão)`: v1 e v2 (a v2 entrou na publicação, §Publicação da v2).
- **Versão publicada que não bate** (a v1 procedural): a mesma recusa sem `.invalido`, com "não corresponde a este
  mundo". Na **vila fixa**, referência desconhecida continua dado inválido, porque não há geração que possa ter mudado.
- **Nada é sobrescrito:** com o save existente, "Entrar" sempre tenta CONTINUAR. A aventura recusada não começa de novo
  por cima. Outro mundo pode ser criado na tela de mundos.
- **Sem migração:** a lareira não tem equivalente na torre, e a região mudou inteira. Migrar seria inventar progresso
  (decisão: estratégia C, incompatibilidade não destrutiva).
- **Testes:** `SaveCompatTest` (os cinco casos; o mesmo personagem em outro mundo não é afetado; o arquivo byte a byte igual
  em todas as recusas; "Entrar" de novo não grava nada); `ProceduralWorldTest` (vínculos diferentes); `DiscoveryTest`.
- **O que já existe nos dados do proprietário:** o `aventura.json.invalido` que o binário da 12.1 criou ao lado da aventura
  do mundo "RUINAS" continua lá. É uma cópia; o original nunca mudou. Removê-lo é decisão do proprietário.

## Publicação da v2 — as duas versões congeladas

- **As versões:** `RegionGenerator.published` passa a valer para a v1 e a v2.
- **Saves v2 feitos durante as rodadas 12 a 12.3:** abrem, se a região da semente for a mesma de agora.
  - **Prova:** a matriz `tools/qa/saves_antigos.sh` com o binário da 12.3 como antigo (regiões v2) e o da publicação
    como novo: 8 de 8 iguais, 0 `.invalido`.
- **A frase do caso 5:**
  - **Mundos v2 das versões de testes** (o "RUINAS"): quem não bate continua recebendo a mesma frase,
    "Esta aventura é de uma versão de testes deste mundo…", por `RegionGenerator.hadTestBuilds(2)`.
  - **A v1:** continua "não corresponde a este mundo".
  - **Teste:** `SaveCompatTest`.
- **Os mundos experimentais do proprietário** ("RUINAS", "31337"):
  - não foram migrados nem abertos pela publicação;
  - os arquivos foram conferidos por sha256 antes e depois;
  - a cópia `.invalido` histórica continua lá.

## Rodada 13 — a mochila e as fontes coletadas (campos opcionais)

```json
"progresso": {
  "moedas": 5, "objetosAtivos": [], "descobertas": ["desc.r0.ruina.0"], "missoes": {"…": "…"},
  "inventario": [{"espaco": 0, "item": "item.ervas_trilha", "qtd": 2}, {"espaco": 1, "item": "item.cataplasma", "qtd": 1}],
  "fontesColetadas": ["achado:desc.r0.ruina.0", "presente:indira"]
}
```

**Os campos:**

- **Quando aparecem:** só quando há o que gravar. Um save sem item é byte a byte o de antes, e um save sem os campos abre
  com a mochila vazia (nada inventado).
  - **Medido:** a mesma partida salva pela v0.6 e pela rodada 13 (vila e região s7) dá arquivos do mesmo tamanho, que só
    diferem no id aleatório da aventura (`round-13/registros/save-igual-ao-da-v06.txt`).
- **Formato:** a versão não mudou; continuam v1 (vila) e v2 (procedural).
- **O que se grava:** as pilhas em ordem (posição, id, quantidade) e os ids das fontes; nunca o nome da tela nem dado
  derivado.
- **Rodada 13.1 (sem limite de espaços, [ADR 0032](../decisions/0032-mochila-viva.md)):** os campos e o formato são os
  mesmos; `espaco` passa a ser a posição da pilha na lista (0 a 4095, o teto técnico do leitor). Um save da rodada 13
  (espaços 0–23, com buracos) abre compactado, na mesma ordem, e é gravado compactado (`InventoryTest`,
  `tools/itens/04-r13-cria.txt` + `05-r13-aberto.txt`).

**Leitura** (`SaveGame.readItems`):

| Caso | Exemplo | Resultado | `.invalido` |
|---|---|---|---|
| estrutura ou número inválido | quantidade 0, acima da pilha ou fracionária; espaço fora de 0–4095 ou repetido; mais de 4096 pilhas; fonte repetida | recusado, "Jogo salvo inválido: …" | sim |
| item que este jogo não conhece | `item.espada_de_cristal` | recusado, "Esta aventura tem itens de uma versão mais nova do jogo…" | **não** |
| presente que a tabela não tem | `presente:quelmo` | igual ao item desconhecido (jogo mais novo) | **não** |
| achado de uma descoberta que a região não tem | `achado:desc.r9.ruina.0` | região v2: outra região ("versão de testes…"); vila fixa: inválido | não / sim |

- **Atomicidade:** a coleta e o uso mudam a mochila e as fontes no mesmo passo lógico, e o save grava os dois no mesmo
  arquivo atômico.
- **Salvamento automático:** receber um item marca o salvamento automático no primeiro passo seguro, como os marcos.
- **Alterações não salvas:** usar um item conta (`GameLogic.unsaved`).
- **Voltar de versão:** um save com itens aberto pelo binário da v0.6 abre sem os itens (ele ignora campos que não
  conhece); salvar lá os perde. As fontes coletadas somem junto, então de volta à rodada 13 o presente e os achados podem
  ser recolhidos de novo, sem duplicação. Não é caminho suportado ([ADR 0031](../decisions/0031-itens-e-recompensas.md)).
- **Testes:** `InventoryFlowTest`, `InventoryTest`, `SaveCompatTest` e a matriz `tools/qa/saves_antigos.sh`.
