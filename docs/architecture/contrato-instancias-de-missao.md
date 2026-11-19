# Contrato de modelos e instâncias de missão (rodada 11)

A Rodada 10 tem UMA missão autoral fixa (`quest.trilha.luzes_do_posto`, no mundo fixo), e ela continua igual. Para o
mundo gerado, a mesma aventura vira um **modelo** que o mundo **instancia** a partir de fatos.

## Modelo (`assets/scenes/modelos_missao_e.json`, escrito à mão)

| Campo | O quê |
|---|---|
| `id`, `versao` | `quest.template.restaurar_sinal`, 1 |
| `nome`, `descricao` | texto com parâmetros |
| `requer` | fatos exigidos: `poi` (tipo), `estado`, `inimigos` (variante), `alcancavel`, `doador.papeis` |
| `objetivos` | os da missão, com vínculos: `"alvos": "$encontro"`, `"objeto": "$marco"`, `"npc": "$doador"` |
| `recompensa` | `{"moedas": 30}` |
| `vozes.<papel>` | os 10 contextos + as reações (as falas de quem dá a missão), uma voz por papel. Hoje: `caminhos` (a voz da estalajadeira) e `trilhas` (a do guardião) |
| `comentarios.<npc>` | uma fala a mais de quem mora ali e não é o doador (`missao_ativa`, `missao_concluida`) |

**Parâmetros:**
- `{rumo}`: `sul`, `sudeste`… (oito, todos masculinos: "trilha do {rumo}", "para o {rumo}");
- `{doador}`: nome;
- `{ao_doador}`: "à Kelyra" ou "ao Bramor", pelo `artigo` do morador.

Parâmetro sem valor é erro (`ProceduralScene.fill`), e texto com lacuna nunca chega à tela. As variações são autorais (uma
voz por papel), sem gramática gerativa. Regra: o texto corresponde aos fatos (o rumo é o real, e ninguém afirma ver o
que não vê).

## Instância (gerada, em memória, e o vínculo no save)

- **id:** `quest.instance.<mundo>.<regiao>.<poi>.<ocorrência>`, por exemplo
  `quest.instance.s1024.r0.posto_trilha_0.1`. Montado com ids estáveis, nunca com coordenadas; a ocorrência só
  aumenta.
- **definição:** um `QuestDefinition` comum (o mesmo leitor da rodada 10 valida). O `QuestSystem`, o diário, o
  rastreador, o save e o som são os da rodada 10, sem nenhuma mudança.
- **doador:** entre os moradores com um dos papéis exigidos, em ordem de id, a semente da missão escolhe
  (`Rng.of(semente, regiao, "missao", modelo, poi)`). A voz é a do papel dele.
- **inimigos:** os `spawns` do POI, que viram o encontro `posto` do combate da região (as mesmas identidades).
- **objeto:** o primeiro `features` do POI.
- **vínculo** (`ProceduralScene.Binding`, salvo): o modelo e a versão, o id, o POI, o doador, os inimigos, o objeto e a
  posição dele, o rumo.

**Sem os fatos não há instância:** o POI pode estar em outro estado, os inimigos ser de outra variante, faltar um
morador com o papel, ou o lugar não ser alcançável (a região inteira é recusada). A região continua jogável, só não
oferece esta missão (`semOsFatosNaoHaMissao`).

**Disponibilidade:** uma ocorrência por POI enquanto ele estiver no estado exigido. Depois de concluída, o marco fica
aceso no save, e o modelo não se aplica de novo àquele lugar: não há fluxo infinito de tarefas.

## O que a fundação deixa aberto (sem implementar agora)

- **Fim da missão:** `conclusao: "ultimo_objetivo"` termina e paga no último objetivo (descobrir, derrotar, usar). O
  padrão continua o da Rodada 10 (`falar_doador`).
- **Um NPC, várias missões:** em `dialogos`, `"missoes": [...]` guarda várias missões em sequência; vale a primeira não
  concluída.
- **Tipos de objetivo:** derrotar, interagir e falar. Descobrir um lugar, escoltar e defender são planejados, cada um com
  um evento novo em `QuestEvent`.
