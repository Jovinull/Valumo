# Perfis de personagem (rodada 11.1)

`vx.game.menu.CharacterStore`, contrato `valumo.personagem` versão **1**. Um personagem é o QUE o jogador escolhe na
tela de personagens. A aparência é parte dele; não define classe nem combate. Decisão:
[ADR 0028](../decisions/0028-personagens-mundos-e-aventuras.md).

| Propriedade | Padrão | `-` |
|---|---|---|
| `-Dvx.personagens=PASTA` | `output/personagens` | desligado (lista vazia, criar avisa) |

## Arquivo `output/personagens/<id>.json`

```json
{
  "formato": "valumo.personagem", "versao": 1,
  "id": "p-3f9c0a1b2c4d",
  "nome": "Brisa",
  "origem": "exploradora",
  "criadoEm": 1795000000000, "usadoEm": 1795000500000,
  "aparencia": { "contrato": "valumo.aparencia", "versao": 2, "...": "o JSON do compositor (Appearance.toJson)" }
}
```

- **id** `p-` + 12 hexadecimais, sorteado.
  - É a identidade: o nome do arquivo, a pasta das aventuras e a seleção usam o id.
  - Arquivo cujo id interno difere do nome do arquivo é recusado.
- **nome**
  - É só exibição: até 32 caracteres, sem controles, Unicode permitido; pode repetir.
  - Nunca vira caminho. `"../../etc"` é só um nome (`MenuStoresTest`).
- **origem:** o preset de partida (aventureiro, exploradora, maga). Os presets são modelos de criação, não classes.
- **datas:**
  - `usadoEm` é anotado ao entrar numa aventura.
  - A lista vem do usado mais recentemente.
  - O cartão diz "ainda sem aventura" enquanto `usadoEm` não passou de `criadoEm`.

## Operações

| Operação | Regra |
|---|---|
| criar | id novo sempre; o nome do criador, ou "Personagem N" se vazio |
| editar | o MESMO arquivo (id, datas e vínculos com mundos ficam); só aparência e nome mudam; id inexistente = erro, nada criado |
| cancelar no criador | nada gravado (`CreatorState.cancelled`) |
| ler a lista | ilegível, grande demais (> 256 KB), de outro formato ou com id trocado: fica de fora **com o motivo** (aviso no rodapé da lista) |
| gravar | escrita atômica (`SaveGame.writeFileAtomic`): temporário na mesma pasta, `force`, relido e validado, movimento atômico |

## Aparências antigas (`output/aparencias/`)

O criador grava aparências soltas. Elas **não** viram personagens sozinhas e não há botão para isso na tela de
personagens. Ficam acessíveis pelo **Carregar** do próprio criador, ao criar ou editar um personagem. Os arquivos não
mudam.

Os jogos salvos de antes, esses sim, viram personagens sozinhos, uma vez, com a aparência gravada neles
([catálogo de mundos](catalogo-de-mundos.md#compatibilidade-saves-das-rodadas-10-e-11)).

## Retratos e vitrine

- **Retrato do cartão:** o modelo real, composto pelo `AsyncComposer`, o mesmo cache do criador. É desenhado uma vez por
  assinatura de aparência num FBO de 192 px, com `App.renderPortrait` (o enquadramento "cabeça" das miniaturas do
  criador).
- **Prévia grande:** o herói de verdade na vila, em idle, na vitrine (`apresentacao_e.json → vitrine`).
  - Arrastar com o mouse gira só o desenho.
  - Malhas não são refeitas por quadro: o modelo vem do cache do compositor.

## Criador aberto pelo menu (fechamento da 11.1)

- **Cache:** o `App` do criador é criado na primeira abertura e fica vivo até o jogo fechar.
  - A cena dele (~2,8 s na primeira vez) é montada num passo de carregamento ("Abrindo o criador"); as aberturas seguintes
    levam ~50–110 ms.
  - `Creator.close` esquece todos os modelos que injetou no `App`.
  - Medido: 6 ciclos criador → Cancelar sem crescer malhas, texturas nem heap (`tools/menu/12-estabilidade.txt`).
- **X da janela:** sem alterações, fecha. Com alterações, "Sair do jogo?" (Salvar e sair / Sair sem salvar / Cancelar).
  Sair sem salvar deixa o perfil idêntico (hash conferido no roteiro `13-navegacao-d`).
