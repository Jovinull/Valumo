# Contrato — modelos voxel dos itens (para o agente artista)

**Status:** pedido em aberto. Rodada 13.1 ([ADR 0032](../decisions/0032-mochila-viva.md)): os itens deixaram de ser
ícones de pixel e passaram a ser **modelos voxel** desenhados pelo jogo, na grade da mochila, no cartão do item recebido
e na prévia que gira no detalhe. Os cinco modelos de `tools/itens_e.py` são a primeira versão funcional. **Não são arte
final aprovada.** O proprietário decide se encomenda este lote. (O nome do arquivo ficou o da rodada 13, para não quebrar
os links.)

## O que já existe

- **Modelos:** 5 em `tools/itens_e.py` (gerados em `assets/items/e/`): `ervas`, `cataplasma`, `pedra`, `madeira`,
  `registro`. A silhueta e a cor de cada um estão em [itens](../architecture/itens.md) §Os modelos.
- **A tela que os usa** ([inventário](../architecture/inventario.md)):
  - **Grade:** espaço de 84 px em 1080p (56 px em 720p), em 3/4 de frente, parado.
  - **Cartão:** 46 px em 1080p.
  - **Prévia:** 186 px em 1080p, girando devagar sob uma luz de estúdio fixa.
  - **Fundo:** os três ficam sobre um painel de tinta translúcida, com o mundo aparecendo atrás.
- **Prancha real:** o comando de roteiro `itens-prancha` mostra cada modelo sobre o painel (com grama e com pedra atrás),
  sobre o pergaminho, em silhueta e a 64 e 40 px. Ela está em `output/visual-review/round-13-1/capturas/modelos/`.

## O que se pede (lote pequeno; um item por vez é aceito)

| Item | Leitura que precisa ter |
|---|---|
| Ervas da trilha | um maço de folhas amarrado (verde, caule, um ponto de flor) |
| Cataplasma de ervas | um rolo de pano claro amarrado, com as ervas à mostra; NÃO uma cruz de farmácia |
| Fragmento de pedra antiga | uma lasca cinza quebrada, com metade de um entalhe |
| Madeira envelhecida | aduelas de barril curvas, com um pedaço de cinta escura |
| Registro de viagem | caderno de couro com fita ou tira e a borda clara das páginas |

## Identidade

- **Direção E, Fábula Voxel:** sombra pintada em bandas, 3–5 matizes por modelo, sem preto nem branco puros, nada de ruído
  de 1 voxel em área grande ([voxel-art](../../.claude/skills/voxel-art/SKILL.md)).
- **Uma silhueta clara por item**, que não se confunde com as outras quatro nem pequena.
- **Paleta:** a da E (`tools/itens_e.py` usa as famílias do mundo: folha, pano cru, pedra, madeira, couro, latão, coral).
  Cor nova = proposta à parte, com o motivo.
- **Folha de item é fosca,** não folhagem: não balança com o vento.
- **P1 (o verde-água):** é acento da INTERFACE, não do item.
- **Proibido:** nada de IA geradora de imagem, de asset de terceiros ou de referência a outro jogo.

## Formato da entrega

1. **Modelo:**
   - uma função em `tools/itens_e.py`, ou um `.vox` desenhado num editor e carregado por `voxlib.load_vox`
     ([pipeline](pipeline-de-producao.md));
   - voxel 1/40 u, até 0,4 u na maior medida (o gerador recusa maior);
   - apoiado no chão (y = 0), centrado; sem rig.
2. **Custo:** até 1 500 faces expostas (o teste confere).
3. **Prancha:** a do jogo (`itens-prancha`), antes e depois.

## Testes de legibilidade (o que a revisão confere)

- **Silhueta:** na prancha, em preto, os cinco se distinguem. `ItemCatalogTest` também confere que a caixa de cada par
  difere em proporção ou em ocupação.
- **Fundos:** lê sobre o painel translúcido (com grama e com pedra atrás) e sobre o pergaminho.
- **Tamanho pequeno:** a 40 px (o cartão em 720p) ainda se sabe o que é.
- **Girando:** a prévia não corta nem fica escura de nenhum lado (o enquadramento é calculado pela caixa do modelo).
- **Testes:** `ItemCatalogTest.osModelosDosItensSaoObjetosDeMaoPequenosEDistintos`.
- **Captura real:** a mesma rota, `tools/itens/03-resolucoes.txt`.

## Integração

- **Onde entra:** o modelo substitui a função do mesmo nome em `tools/itens_e.py`. O catálogo, o save e a tela não mudam.
- **Quem trabalha:** o agente artista trabalha no clone isolado dele; a integração é do agente principal, com as capturas
  antes e depois.
