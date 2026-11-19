# 0028 — Personagens, mundos e aventuras: identidade estável, aventura = personagem × mundo, sem save v3 (rodada 11.1)

**Status:** aceita (rodada 11.1).

## Contexto

O jogo passou a ter vários personagens e vários mundos. O progresso precisa ser do PAR (um personagem não herda o save de
outro; um mundo não é de um personagem só), os saves v1 (vila, rodada 10) e v2 (semente, rodada 11) precisam continuar
acessíveis, e nada pode apagar ou sobrescrever o que o proprietário já tem.

## Decisão

- **Personagem** (`valumo.personagem` v1, `CharacterStore`):
  - um arquivo por perfil, `output/personagens/<id>.json`;
  - id `p-` + 12 hexadecimais sorteados; o nome é só exibição, pode repetir e nunca vira caminho;
  - aparência no contrato do compositor (`valumo.aparencia` v2), preset de origem, datas;
  - editar regrava o mesmo arquivo; criar nunca reaproveita id.
- **Mundo** (`valumo.mundo` v1, `WorldStore`):
  - `output/mundos/<id>/mundo.json` + `previa.png`; id `m-` + 12 hexadecimais, independente da semente (a mesma semente
    pode estar em dois mundos);
  - guarda semente, gerador e versão, datas e a chave da prévia;
  - a vila fixa é o mundo `vila`, sempre listado, sem `mundo.json`.
- **Aventura = pasta do par.**
  - O save fica em `output/mundos/<mundo>/aventuras/<personagem>/aventura.json`, nos formatos que já existem: **v1** na
    vila, **v2** num mundo gerado.
  - Não há save v3: a identidade do mundo e do personagem é a própria pasta, e o v2 já confere semente, gerador e
    vínculos.
- **A aparência é do personagem.** Continuar uma aventura usa a aparência atual do perfil. A gravada no save é só a da
  época; o formato não muda.
- **Saves antigos: adotados uma vez, por CÓPIA, sem botão.** Revisto na 11.1, a pedido do proprietário: o botão
  "Trazer…" era texto de desenvolvimento.
  - Ao abrir o menu, cada save antigo vira a aventura de um personagem com a aparência gravada nele, na vila ou no mundo
    da semente.
  - A cópia é validada contra a cena (mesma leitura do Continuar) ANTES de existir, movida de forma atômica, e só entra se
    o par ainda não tem aventura: progresso novo nunca é substituído por um antigo.
  - O original fica onde estava. O conteúdo (sha256) é registrado em `output/mundos/importacoes.json`, adotado ou
    recusado: o mesmo arquivo nunca entra nem é tentado duas vezes (a recompensa não duplica).
  - Com o registro ilegível, nada é adotado.
- **Aparências soltas** (`output/aparencias/`): não viram personagens. Continuam no **Carregar** do criador (revisto na
  11.1: o botão "Trazer aparências" não fazia sentido para o jogador).
- **Mundo de outra versão do gerador:** entrar é recusado com a explicação. A semente daria outra região; o v2 recusaria
  o save de qualquer jeito.

## Alternativas descartadas

- **Save v3 com personagem e mundo dentro do arquivo:** duplicaria o que a pasta já diz e exigiria migração de v1/v2.
- **Migrar os saves antigos no lugar:** mexe nos arquivos do proprietário; o pedido proíbe sobrescrever.
- **Importação por botão** ("Trazer o jogo salvo da vila (rodada 10)"): foi a primeira versão. O proprietário reprovou o
  texto de desenvolvimento e o botão. O dono do save é a aparência gravada nele.
- **Id = nome do personagem ou semente do mundo:** quebra com renomear, nomes iguais e mundos de mesma semente.
