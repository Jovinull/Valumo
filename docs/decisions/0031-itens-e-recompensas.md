# 0031 — Itens e recompensas: uma camada de jogo sobre os mundos, no mesmo save

**Status:** aceita e implementada na rodada 13. A mochila de 24 espaços, a tela que pausa a lógica e os ícones de pixel
foram substituídos na rodada 13.1 pela [ADR 0032](0032-mochila-viva.md); o resto continua valendo.

## Contexto

O Valumo ganha a primeira progressão de RPG: encontrar um item, guardar, ver, usar e continuar com tudo preservado. Duas
restrições valem desde a publicação da v2 (`procedural-world-v0.6`):

- **Geradores congelados:** a v1 e a v2 não podem mudar o resultado de nenhuma semente (ADR 0029). Isso inclui pôr baús,
  depósitos ou pontos novos na região e mudar os textos e moedas das descobertas e as falas dos moradores.
- **Saves compatíveis:** os saves de antes (v1 da vila e v2 das regiões) precisam abrir sem perder nada e sem ganhar nada
  inventado.

## Decisão

1. **Itens são dados; efeitos são código.**
   - **O catálogo** (`assets/scenes/itens_e.json`) diz o que cada item é: id estável, nome, categoria, pilha, ícone e o
     uso.
   - **O efeito** é escolhido por NOME entre os que o código conhece (`ItemEffects`); nada se executa por reflexão nem por
     texto.
2. **A recompensa é uma camada de jogo separada da geração.** A tabela `assets/scenes/recompensas_e.json` reage a eventos
   estáveis que já existem:
   - **Presente:** terminar a conversa com um morador (o presente da Índira).
   - **Achado:** descobrir um lugar de um tipo (o caderno da casa, as ervas da árvore…).

   Ela não toca no gerador, nas descobertas nem nas falas congeladas.
3. **Cada fonte entrega uma vez por aventura, tudo ou nada.** A fonte (`presente:indira`, `achado:<id da descoberta>`)
   é marcada coletada se e somente se tudo entrou na mochila. O que não cabe fica disponível.
4. **O save ganha dois campos OPCIONAIS no bloco `progresso`:** `inventario` e `fontesColetadas`.
   - **Sem versão nova do formato.** Ausentes, a mochila fica vazia; vazios, não são escritos, e o save sai byte a byte
     o de antes.
   - **Item que este jogo não conhece:** é de um jogo mais novo, e o save é recusado sem `.invalido` (o arquivo está
     íntegro).
   - **Estrutura ou número errado:** é dado inválido, que vai para `.invalido`.
5. **Sem sorteio nesta versão.** A recompensa é fixa por fonte. Quando houver sorteio, a semente vem da fonte, do mundo e
   da versão da tabela (nunca do relógio, da ordem dos NPCs nem do RNG do gerador). O que já foi entregue está no save,
   então mudar a tabela não muda o passado.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| Baús ou depósitos postos pela geração | mudaria o resultado de sementes publicadas (seria a v3) |
| Mudar `descobertas_e.json` para dar itens | os textos e moedas da v2 estão congelados e entram na referência |
| Versão 3 do formato do save | campos opcionais bastam e mantêm os saves antigos idênticos |
| Uma classe por item | não cresce por dados; o catálogo já descreve o que muda entre itens |
| Efeito descrito em JSON e executado por reflexão | inseguro e sem validação; o catálogo só escolhe entre efeitos do código |

## Consequências

- **Para crescer:** itens e fontes novos entram por dados. Um efeito novo é um caso em `ItemEffects`, com teste.
- **Um limite aceito:** um save com itens aberto num binário anterior (v0.6) perde os itens se for salvo lá (o binário
  antigo ignora os campos que não conhece). Voltar de versão não é caminho suportado; fica registrado.
  - **Medido:** as fontes coletadas somem junto com os itens. De volta à rodada 13, o presente e os achados podem ser
    recolhidos de novo, então há perda recuperável e nenhuma duplicação.
- **Contratos:** [itens](../architecture/itens.md), [inventário](../architecture/inventario.md),
  [recompensas e loot](../architecture/recompensas-e-loot.md), [save](../architecture/save.md) §Rodada 13.
