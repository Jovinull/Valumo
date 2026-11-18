# Registros de decisão (ADR)

| Nº | Decisão |
|---|---|
| [0001](0001-stack-java-lwjgl.md) | Java 25 + LWJGL 3 + OpenGL 3.3 core, sem engine |
| [0002](0002-assets-por-script.md) | Assets modelados por script Python, exportados em `.vox` |
| [0003](0003-animacao-procedural-por-partes.md) | Animação procedural por partes rígidas |
| [0004](0004-proporcao-dos-personagens.md) | Proporção dos personagens: 2,3 a 2,6 cabeças |
| [0005](0005-tres-escalas-de-voxel-sem-greedy.md) | Três escalas de voxel e malha sem greedy meshing (fusão revista no 0007) |
| [0006](0006-direcao-consolidada-e.md) | Direção consolidada E — **aprovada** como base oficial |
| [0007](0007-fusao-exata-de-faces.md) | Fusão exata de faces e vento afim |
| [0008](0008-ferramentas-de-arte.md) | Ferramentas de arte instaladas sem privilégio (Flatpak, SDKMAN, .venv) |
| [0009](0009-articulacoes-e-ik-de-pe.md) | Cotovelo, joelho e IK analítico de pé nos heróis da E (rodada 03) |
| [0010](0010-fxaa-e-margem-anti-fresta.md) | FXAA na qualidade padrão e margem anti-fresta na fusão de faces (rodada 03) |
| [0011](0011-coreografia-por-trajetorias.md) | Coreografia de armas por trajetórias, IK de braço e validação espacial (rodada 04; limite de duas mãos revisto na 05) |
| [0012](0012-interface-do-jogo.md) | Interface do jogo na engine própria: lote 2D, fonte de pixel própria, fila de eventos, composição assíncrona (rodada 07.2) |
| [0013](0013-modulo-game.md) | Módulo `game` sobre o runtime do viewer, sem duplicar renderer nem assets (rodada 08) |
| [0014](0014-malha-conforme-sem-t-junctions.md) | Malha conforme: fusão sem T-junctions e sem sobreposição; fim da margem anti-fresta (rodada 08.1) |
| [0015](0015-combate-por-trajetoria-e-passo-fixo.md) | Combate: passo fixo de 1/120 s, dano pela trajetória da arma, hitbox/hurtbox/colisão separadas, interrupções autoradas (rodada 09) |
| [0016](0016-audio-openal-e-sons-sintetizados.md) | Áudio: OpenAL Soft pelo LWJGL, som que só lê a lógica, efeitos sintetizados por script, gravação por loopback (rodada 09.1) |
| [0017](0017-locomocao-durante-o-golpe.md) | Locomoção durante o golpe: teto por fase, pernas na passada real sob a ação, perfis de sensação A/B/C (rodada 09.2) |
| [0018](0018-esquiva-direcional.md) | Esquiva: rolamento e recuo como gestos do perfil, invulnerabilidade no estado lógico, janelas medidas (rodada 09.2) |
| [0019](0019-salto.md) | Salto: física no passo fixo, coyote e buffer, props pisáveis por perfil, sem golpe aéreo (rodada 09.2) |
| [0020](0020-mira-opcional.md) | Mira opcional: livre por padrão, trava com Q, troca com Tab pela ordem na tela (rodada 09.2) |
| [0021](0021-varios-inimigos.md) | Vários inimigos: uma classe, encontros por dados, coordenação leve, cada alvo uma vez por golpe (rodada 09.2) |
| [0022](0022-morte-por-desagregacao-voxel.md) | Morte por desagregação voxel: o corpo se desfaz nos próprios blocos (rodada 09.2) |
| [0023](0023-audio-openal-stb-vorbis.md) | Áudio com OpenAL Soft e stb_vorbis (módulos do LWJGL), um dispositivo por processo com os efeitos do combate, catálogo de eventos, laço exato, loopback para testes e gravação (rodada 09.4) |

Só entram aqui decisões que mudam o rumo do projeto ou que alguém poderia querer desfazer. Escolhas
locais ficam em comentários no código ou nos guias de arte.
