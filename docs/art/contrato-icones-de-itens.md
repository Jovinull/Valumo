# Contrato — ícones e pequenos modelos de itens (para o agente artista)

**Status:** pedido em aberto. Os ícones de pixel da rodada 13 são a primeira versão funcional, desenhados no mesmo idioma
do coração e do escudo do HUD. **Não são arte final aprovada.** O proprietário decide se encomenda este lote.

## O que já existe

- 5 ícones de pixel, 14 × 14, em `game/src/main/java/vx/game/item/ItemIcons.java`: `ervas`, `cataplasma`, `pedra`,
  `madeira`, `registro`.
- **A tela que os usa** ([inventário](../architecture/inventario.md)):
  - espaço da grade, desenhado a 3–7 px por pixel;
  - painel de detalhe, a 6 px por pixel em 1080p;
  - cartão do item recebido, a 2–3 px por pixel.
- **Capturas reais** (720p, 1080p, 16:10, ultrawide): `output/visual-review/round-13/capturas/resolucoes/`.

## O que se pede (lote pequeno; um item por vez é aceito)

| Item | Leitura que precisa ter | Estados |
|---|---|---|
| Ervas da trilha | um maço de folhas amarrado (verde, caule escuro, um ponto de flor) | normal |
| Cataplasma de ervas | um rolo de pano claro amarrado, com folha à mostra; NÃO uma cruz de farmácia | normal; "não dá agora" é só o botão, o ícone não muda |
| Fragmento de pedra antiga | uma lasca cinza com metade de um entalhe | normal |
| Madeira envelhecida | duas aduelas de barril curvas com cinta escura | normal |
| Registro de viagem | caderno de couro com fita/cinta e a borda das páginas | normal |

## Identidade

- **Direção E — Fábula Voxel:** o mesmo idioma de pixel do coração e do escudo do HUD (`Game.heart`, `Game.shieldIcon`) e
  da paleta da interface (`UiKit`).
- **Uma silhueta clara**, 1 a 2 tons de sombra e 1 ponto de destaque no máximo; sem contorno preto em volta (a sombra
  suave é do desenho).
- **Paleta:** as cores de `ItemIcons.PALETTE` (madeira, folha, pedra, pano, couro, tinta, coral). Cor nova = proposta à
  parte, com o motivo.
- **P1 (o verde-água):** é acento da INTERFACE (a seleção), não do item.
- **Proibido:** nada de IA geradora de imagem, de asset de terceiros ou de referência a outro jogo.

## Formato da entrega

1. **Ícone:** um mapa 14 × 14 (16 × 16 aceito, avisando) no formato de `ItemIcons.ICONS`: uma linha por fila, um
   caractere por pixel, `.` transparente.
2. **Prancha:** os ícones em 1×, 3× e 6× sobre o pergaminho claro (`PARCH_L`) e o escuro (`PARCH_D`), mais a silhueta em
   preto.
3. **Modelo voxel do item:** opcional, se o proprietário pedir, para um dia aparecer no mundo ou virar ícone renderizado.
   - **Escala:** voxel 1/40 u, até 0,4 u, em `tools/` como os props.
   - **Rig:** sem rig.
   - **Origem e pegada:** no chão, centrado.

## Testes de legibilidade (o que a revisão confere)

- **Silhueta:** em preto, a 3× (o espaço da grade em 720p), os cinco se distinguem. Na primeira versão, a cataplasma e a
  pedra são o par mais parecido (duas formas redondas; só a folha separa): a arte final deve afastá-las.
- **Fundos:** lê no pergaminho claro e no escuro.
- **Tamanho pequeno:** a 2× (o cartão em 720p) ainda se sabe o que é.
- **Testes:** `ItemCatalogTest.osIconesSoUsamCoresDaPaleta` e o tamanho 14 × 14 continuam passando.
- **Captura real:** a mesma rota, `tools/itens/03-resolucoes.txt`.

## Integração

- **Onde entra:** o mapa entra em `ItemIcons.ICONS` com o mesmo nome. O catálogo, o save e a tela não mudam.
- **Quem trabalha:** o agente artista trabalha no clone isolado dele; a integração é do agente principal, com as
  capturas antes e depois.
