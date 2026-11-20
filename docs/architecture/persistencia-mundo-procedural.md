# Persistência do mundo procedural (rodada 11)

Contrato geral do save: [save.md](save.md). Decisão: [ADR 0026](../decisions/0026-save-v2-mundo-procedural.md).

## Princípio

Guardar a semente não basta. O save do mundo gerado guarda três coisas:
1. **Que mundo é:** a semente, o gerador e a versão, a tentativa aceita, a região.
2. **O que o jogador mudou:** missões, inimigos derrotados, objetos acesos, moedas, posição e aparência. É o mesmo bloco
   `progresso` da v1.
3. **Os vínculos resolvidos de cada missão instanciada:** modelo e versão, POI, doador, inimigos, o objeto e a posição
   dele, o rumo.

Ao abrir, a região é regenerada da semente, e o save só é aceito se o mundo for o mesmo e os vínculos forem exatamente os
que a região gera agora. Assim, uma versão nova do gerador que movesse o marco de uma missão aceita não reposiciona a
missão em silêncio: o save é recusado com o motivo, e o original fica em `.invalido`.

## Formato (v2)

```json
{
  "formato": "valumo.save", "versao": 2, "id": "…", "cena": "procedural",
  "mundo": {"tipo": "procedural", "semente": "1024", "gerador": "valumo.regiao", "versaoGerador": 1, "tentativa": 0, "regiao": "r0"},
  "vinculos": [{"modelo": "quest.template.restaurar_sinal", "versaoModelo": 1, "missao": "quest.instance.s1024.r0.posto_trilha_0.1",
                "poi": "poi.r0.posto_trilha.0", "doador": "bramor", "inimigos": ["r0.posto_0.fungo.1", "r0.posto_0.fungo.2"],
                "objeto": "obj.r0.posto_0.marco", "objetoX": -4.592, "objetoZ": -14.876, "rumo": "norte"}],
  "aparencia": {"…": "…"}, "jogador": {"x": 0, "z": 0, "direcao": 0}, "tempoDeJogo": 0,
  "progresso": {"moedas": 30, "objetosAtivos": ["obj.r0.posto_0.marco"], "missoes": {"…": "…"}}
}
```

- **Coordenadas dos vínculos:** `double` já arredondado em 3 casas. Assim o número relido é igual ao escrito: com
  `float`, a comparação falhava e todo salvamento automático era recusado pela própria validação (defeito achado pelo
  teste e corrigido).
- **Pasta:** `output/saves-procedural/s<semente>/aventura.json`. Com `-Dvx.savegame=PASTA`, fica em
  `PASTA/s<semente>/`. Nunca se mistura com o save principal, e cada semente tem o seu.

## Compatibilidade

| Situação | Resultado |
|---|---|
| v1 (mundo fixo) aberto no mundo fixo | igual à rodada 10 |
| v1 aberto no procedural | recusado: "é um save do mundo fixo (v1): abra sem -Dvx.mundo" |
| v2 aberto no mundo fixo | recusado: "é um save de mundo procedural (v2): abra com -Dvx.mundo=procedural" |
| v2 de outra semente | não acontece (pastas separadas); se copiado, "mundo salvo diferente deste (semente…)" |
| v2 de outra versão do gerador ou outra tentativa | recusado, com o campo citado (rodada 12: só acontece se o save e o `mundo.json` discordarem, porque a região é sempre gerada pela versão do mundo) |
| rodada 12: mundo v1 aberto por um jogo que já cria v2 | abre **igual**: a região é gerada pela v1 (a versão do `mundo.json`), e o save confere |
| rodada 12: mundo de uma versão que este jogo não conhece | não abre, com "Este mundo foi criado por uma versão mais nova do Valumo…"; **nenhum arquivo é tocado**, sem `.invalido` |
| rodada 12: save sem `progresso.descobertas` | abre igual (ausente = nenhuma descoberta) |
| rodada 12: descoberta que a região não tem | recusado ("descoberta desconhecida"), original guardado em `.invalido` (é corrupção de verdade) |
| vínculos diferentes dos gerados agora | recusado: "os vínculos da missão salvos não batem com a região gerada agora" |

- **Migração:** nenhuma. A v1 é o mundo fixo e continua sendo; não se inventam POIs para um save antigo.
- **Escrita:** a mesma da rodada 10: temporário, `force`, reler e validar, movimento atômico.
- **Testes:** `ProceduralWorldTest.saveDoMundoProcedural…` e `saveV1DaRodada10ESaveV2NaoSeConfundem`, mais toda a
  `SaveGameTest` da rodada 10.

## Rodada 12 — versões do gerador e descobertas

- **A versão fica gravada em dois lugares:** no `mundo.json` (`versaoGerador`) e no save v2 (`mundo.versaoGerador`).
  - Quem abre gera a região pela versão do MUNDO (`ProceduralScene.create(assets, semente, versão)`). O save confere
    contra essa região, então um mundo v1 continua abrindo, para sempre, num jogo que cria v2.
- **Adoção de saves antigos por semente** (rodada 11): lê a versão gravada no próprio save (`SaveGame.peekGenerator`, só
  leitura) e cria o mundo do catálogo nela.
- **Descobertas** (`QuestSystem.discover`):
  - Ficam em `progresso.descobertas`, a lista de ids estáveis (`desc.r0.ruina.0`).
  - O campo só é escrito quando há alguma. Um save sem descoberta sai byte a byte igual ao de antes, e um save sem o
    campo abre igual.
  - Cada descoberta conta uma vez e paga uma vez.
  - Pertencem à aventura (personagem × mundo): outro personagem no mesmo mundo descobre por conta própria.
- **Sem v3:** nada disso mudou a versão do formato do save.
- **Testes:** `GeneratorVersionTest`, `DiscoveryTest`, `RegionGoldenTest` (v1 e v2) e a matriz `tools/qa/saves_antigos.sh`.

## Mais regiões (planejado)

- **O bloco `mundo`:** passa a ter a lista de regiões visitadas.
- **O progresso de cada região:** objetos e inimigos já têm ids com o prefixo da região, então o formato atual não
  precisa mudar.
- **Ids de instância:** já carregam o mundo e a região.
