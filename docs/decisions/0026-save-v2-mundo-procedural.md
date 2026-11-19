# 0026 — Save v2 só para o mundo procedural: semente, gerador e vínculos resolvidos (rodada 11)

**Status:** aceita (rodada 11). O save v1 da rodada 10 continua exatamente como era.

## Contexto

O save v1 (`valumo.save` 1, ADR 0024) guarda o mundo fixo: cena, aparência, posição e progresso. O mundo procedural
precisa saber QUE mundo é e precisa garantir que uma missão aceita não mude de lugar se o gerador mudar. Guardar só a
semente não basta.

## Decisão

- **v2 (`versao: 2`) só para o mundo procedural:**
  - ganha os blocos `mundo` (semente, gerador, versão, tentativa, região) e `vinculos` (os `Binding` das missões
    instanciadas: modelo e versão, POI, doador, inimigos, objeto e posição, rumo);
  - o `progresso` é o mesmo da v1.
- **v1 continua sendo o formato do mundo fixo:** o jogo escreve v1 na vila e v2 na região gerada.
- **Ao ler:** v1 só no mundo fixo; v2 só no procedural, e só se o mundo for o mesmo e os vínculos forem exatamente os
  que a região gera agora. Qualquer diferença: recusado com o motivo e o original em `.invalido`.
- **Sem migração:** a v1 não vira v2 (o mundo fixo é outro mundo, não uma semente).
- **Pasta separada por mundo:** `output/saves-procedural/s<semente>/`. Nunca se mistura com o save principal.

## Alternativas descartadas

- **Guardar só a semente:** perde o que o jogador mudou e não protege os vínculos.
- **Guardar a região inteira** (props, relevo): arquivos grandes e uma segunda fonte da verdade; a semente e a versão
  reproduzem tudo.
- **Migrar v1 para v2 inventando um POI para a vila fixa:** é exatamente o que o pedido proíbe.
- **Aceitar vínculos diferentes e "corrigir" a missão:** reposicionaria em silêncio uma missão aceita.

## Consequências

- Mudar o gerador invalida os saves da versão anterior, e o jogador vê o motivo. Uma migração real, região por região,
  pode vir quando houver mundo maior e motivo.
- Defeito achado pelo teste e corrigido: coordenadas do vínculo em `float` não batiam ao reler (4,592 × 4,592000007…).
  Agora são gravadas como `double` arredondado.
