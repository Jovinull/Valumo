# 0030 — Catálogo de props sob demanda: registrar não é carregar

**Status:** aceita e implementada (integração dos lotes 1 e 2 de conteúdo).

## Contexto

A biblioteca de conteúdo acrescenta ao `props.json` da E os modelos aprovados dos lotes 1 e 2 e as variantes
publicadas: 41 → 81 entradas. Até aqui, o `Assets.props` lia, malhava e enviava à GPU **todo** o `props.json` na
primeira chamada. Isso valia mesmo para os props que nenhuma cena usa.

| Momento | `main` | Integração sem a mudança | Diferença |
|---|---|---|---|
| No menu | 212 malhas, 112,7 MB de vértices | 252 malhas, 147,3 MB | +40 malhas, +34,6 MB |
| Depois de abrir o criador | 283 malhas, 192,4 MB | 363 malhas, 261,5 MB | +80 malhas, ~+69 MB |

O criador (outro `App`, com o seu cache de assets) carregava a biblioteca de novo, por isso a diferença dobra. E a
biblioteca vai crescer.

## Decisão

O `Assets.props(estilo)` devolve um **catálogo sob demanda** (`Assets.PropSet`).

- **Registro:** lê o `props.json` inteiro. O catálogo continua completo: `containsKey`, `keySet` e `size` respondem sem
  carregar nada.
- **Carga:** a malha de um prop é construída e enviada à GPU **na primeira vez que uma cena a pede por nome** (`get`).
  O `World` monta a cena pedindo cada prop posicionado; o jogo pede o marco.
- **Liberação:** o `close` libera só o que foi carregado.
- **Atlas:** o atlas dos props de cuboides (direção C) também só é lido quando o primeiro desses props é pedido.

Os seis níveis ficam separados:

1. registrado no catálogo (`props.json`, sempre);
2. gerado em disco (`.vox`, pelo `build_assets.py`);
3. decodificado;
4. malhado na CPU;
5. na GPU;
6. instanciado numa cena.

Os níveis 3–5 só acontecem para o que a cena usa.

## Consequências

- **Vila e menu:** carregam menos do que antes, inclusive em relação à `main`, porque nem os 41 aprovados entram
  inteiros se a cena não os usa. Números no relatório de integração.
- **Vitrines** (`--layout vitrine_conteudo_*`): carregam o que mostram. O custo de uma composição densa é dela, não da
  biblioteca.
- **Iterar `entrySet`/`values` carregaria tudo**, e ninguém faz isso. Teste: `AssetsLazyTest` (registrar não malha).
- **Momento da carga:** o prop é carregado ao montar a cena, na thread do GL, como antes. Não há thread nova nem gerente
  de recursos.

## Alternativas descartadas

- **Um `props.json` separado para a biblioteca:** esconde o problema até a próxima biblioteca; o catálogo é um só.
- **Gerar só os props usados:** o build não sabe o que as regiões futuras vão usar, e as vitrines precisam de todos.
- **Gerente de recursos com contagem de referência e descarte:** desproporcional. As cenas montam uma vez e fecham
  inteiras.
