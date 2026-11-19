# Documentação

> Caminhos `output/visual-review/...` citados nos relatórios são **locais** (fora do git): as capturas de
> cada rodada são regeneradas pelos scripts `tools/review*.sh`. Os assets em `assets/characters`, `creatures`,
> `environment`, `textures`, `studies`, `components` e `audio/sintese` também saem de `python3 tools/build_assets.py`.

## Oficial (atual — é isto que vale para criar ou alterar qualquer coisa)

| Documento | Para quê |
|---|---|
| [vision/visao.md](vision/visao.md) | o que estamos construindo, onde o projeto está, restrições |
| [art/visual-identity.md](art/visual-identity.md) | identidade da direção **E — Fábula Voxel** (§0: invariantes × parâmetros flexíveis) |
| [art/contrato-de-qualidade.md](art/contrato-de-qualidade.md) | critérios de aceite por categoria (obrigatório / flexível / boa prática / preferência) |
| [art/reference/](art/reference/README.md) | **imagens canônicas** (capturas reais) e galeria de reprovados |
| [art/contrato-de-assets.md](art/contrato-de-assets.md) | contrato técnico: nomes, eixos, escalas, formatos, rig, meta |
| [art/especificacao-de-personagem.md](art/especificacao-de-personagem.md), [art/sistema-de-proporcoes.md](art/sistema-de-proporcoes.md) | construção de personagem |
| [art/guia-de-animacao.md](art/guia-de-animacao.md) | locomoção, coreografia de armas, perfis `assets/anim`, IK, limites |
| [art/paleta-e-materiais.md](art/paleta-e-materiais.md), [art/regras-de-vegetacao.md](art/regras-de-vegetacao.md), [art/estilo-arquitetonico.md](art/estilo-arquitetonico.md), [art/presets-graficos.md](art/presets-graficos.md), [art/assinatura-visual.md](art/assinatura-visual.md) | guias por tema |
| [art/pipeline-de-producao.md](art/pipeline-de-producao.md) | fonte → geração → viewer → QA → correção; edição manual (Goxel) sem perda |
| [architecture/arquitetura.md](architecture/arquitetura.md) | módulos Java, renderização, assets |
| [architecture/personalizacao.md](architecture/personalizacao.md) | personalização modular: estudo, modelo de dados, compatibilidade, persistência (implementado × validado × planejado) |
| [architecture/renderizacao.md](architecture/renderizacao.md) | renderer: passes, sombra, pós, qualidade |
| [architecture/assets.md](architecture/assets.md) | fluxo dos assets gerados |
| [architecture/personalizacao-evolucao.md](architecture/personalizacao-evolucao.md) | evolução da personalização: rosto por partes (olhos feitos na 09.4), roupas por região (botas feitas na 09.4), criador |
| [architecture/criador-de-personagens.md](architecture/criador-de-personagens.md) | criador de personagens do jogador: layout, componentes, mouse, escala, fonte própria, miniaturas, composição assíncrona, roteiros e testes; guia das próximas interfaces |
| [decisions/](decisions/README.md) | ADRs (decisões vigentes e revistas) |
| [validation/checklist-de-aceitacao.md](validation/checklist-de-aceitacao.md), [validation/processo-de-revisao-visual.md](validation/processo-de-revisao-visual.md) | QA |
| [processo/commits.md](processo/commits.md) | commits, histórico e repositório público (regras do proprietário) |
| [validation/problemas-conhecidos.md](validation/problemas-conhecidos.md) | abertos, corrigidos, limitações aceitas, fora do escopo |
| [validation/matriz-de-problemas.md](validation/matriz-de-problemas.md) | rastreabilidade de todos os problemas (rodadas 01–05.1) com evidência verificada |
| [architecture/combate.md](architecture/combate.md) | o combate (rodada 09; câmera refeita na 09.1; mobilidade, esquiva, mira, grupos, morte voxel e barras na 09.2 §14–19): tempo e ordem, herói, golpes e janelas de dano, inimigo e IA, interrupções, vida, defesa, câmera de luta e sua medição, retorno visual e sonoro, testes |
| [architecture/audio.md](architecture/audio.md) | o som: um dispositivo por processo; parte A (09.4) catálogo de eventos, música, ambiente, interface, pisada por superfície, laço, gravação; parte B (09.1–09.3) efeitos do combate, diretor que só lê a lógica, síntese, mistura, sincronia |
| [architecture/mundo-procedural.md](architecture/mundo-procedural.md) | mundo procedural (rodada 11): o estudo do que existia, as camadas (regras × semente × gerado × mudado pelo jogador), peças, fluxo, implementado × protótipo × planejado |
| [architecture/contrato-sementes.md](architecture/contrato-sementes.md), [contrato-regioes.md](architecture/contrato-regioes.md), [contrato-pontos-de-interesse.md](architecture/contrato-pontos-de-interesse.md), [contrato-instancias-de-missao.md](architecture/contrato-instancias-de-missao.md), [persistencia-mundo-procedural.md](architecture/persistencia-mundo-procedural.md) | contratos do mundo procedural |
| [processo/reproduzir-sementes.md](processo/reproduzir-sementes.md), [processo/como-adicionar-um-tipo-de-poi.md](processo/como-adicionar-um-tipo-de-poi.md) | guias: reproduzir uma semente; novo tipo de POI |
| [architecture/menus.md](architecture/menus.md) | **porta de entrada (rodada 11.1):** fluxo de menus, navegação, pedidos, transições em passos, ciclo de vida das telas e da sessão, sair da aventura, fundo 3D, como acrescentar botão/tela/etapa |
| [architecture/perfis-de-personagem.md](architecture/perfis-de-personagem.md), [architecture/catalogo-de-mundos.md](architecture/catalogo-de-mundos.md) | personagens (id estável, aparência, importação das aparências antigas) e mundos (criação por semente, prévia real, aventura = personagem × mundo, persistência, compatibilidade dos saves v1/v2) |
| [processo/como-adicionar-uma-tomada.md](processo/como-adicionar-uma-tomada.md) | guia: tomada nova no fundo do menu (e a vitrine dos personagens), com o teste na colisão real |
| [validation/relatorio-fechamento-rodadas-11.md](validation/relatorio-fechamento-rodadas-11.md) | fechamento das rodadas 11 e 11.1: X da janela, menu principal de verdade, tipografia, carregamento, câmera da prévia, commits e procedimento de publicação |
| [processo/handoff-laboratorio-de-assets.md](processo/handoff-laboratorio-de-assets.md) | handoff: como os assets do laboratório entram no catálogo e no gerador (colisão, alcance, sementes, ids, desempenho, revisão de PR) |
| [validation/relatorio-rodada-11-1.md](validation/relatorio-rodada-11-1.md) | relatório da rodada 11.1 (menu principal, personagens, mundos, aventuras) |
| [validation/relatorio-rodada-11.md](validation/relatorio-rodada-11.md) | relatório da rodada 11 (fundação procedural) |
| [architecture/missoes.md](architecture/missoes.md) | missões (rodada 10): definição, estados derivados, objetivos, identidade estável de inimigo, objetos com estado, falas por contexto, recompensa, avisos, salvamento automático, testes |
| [architecture/missao-luzes-do-posto.md](architecture/missao-luzes-do-posto.md) | contrato da primeira missão "As Luzes do Posto": passos, lugares, marco, contextos, sons |
| [architecture/save.md](architecture/save.md) | jogo salvo v1: conteúdo, quando grava, escrita atômica, leitura de arquivo ruim |
| [processo/como-adicionar-uma-missao.md](processo/como-adicionar-uma-missao.md) | guia: dados, teste, evidência para uma missão nova |
| [validation/relatorio-rodada-10.md](validation/relatorio-rodada-10.md) | relatório da rodada 10 (a primeira aventura completa: missão, marco, diário, save) |
| [validation/roteiro-de-teste-fisico-aventura.md](validation/roteiro-de-teste-fisico-aventura.md) | teste físico da aventura (20 min, para o proprietário) |
| [architecture/jogo.md](architecture/jogo.md) | o jogo: módulos, estados, controle do herói, colisão, câmera, cena, interação, aparência, interface, testes |
| [validation/roteiro-de-teste-fisico.md](validation/roteiro-de-teste-fisico.md) | roteiro de teste com teclado e mouse físicos (criador e jogo) para o proprietário |
| [validation/relatorio-fechamento-qa.md](validation/relatorio-fechamento-qa.md) | fechamento do QA de engenharia: auditoria incorporada, validadores, robustez, sessão longa, interface, referências, desempenho, CI (checkpoint `engineering-foundation-v0.3`) |
| [validation/handoff-rodada-10.md](validation/handoff-rodada-10.md) | handoff para a Rodada 10: estado, comandos, arquitetura, limitações, cuidados com o savegame |
| [validation/relatorio-integracoes-09-4.md](validation/relatorio-integracoes-09-4.md) | relatório das integrações da 09.4: PR #1 (áudio e personalização) na `main` e os cinco NPCs de ofício na vila |
| [validation/relatorio-rodada-09-4.md](validation/relatorio-rodada-09-4.md) | relatório da rodada 09.4 (desenvolvida em paralelo como "rodada 09": direção sonora, estudos de música e efeitos, integração de áudio, estudos de cabelo, olhos e botas) |
| [validation/relatorio-rodada-09-3.md](validation/relatorio-rodada-09-3.md) | relatório da 09.3 ( esquiva R1/R2 com a mesma função, salto com impulso e pouso, herói pontilhado perto da câmera) |
| [validation/relatorio-rodada-09-2.md](validation/relatorio-rodada-09-2.md) | relatório da 09.2 ( mobilidade, esquiva, salto, mira opcional, grupos, ensaio de chefe, morte voxel, barras) |
| [validation/relatorio-rodada-09-1.md](validation/relatorio-rodada-09-1.md) | relatório da rodada 09.1 (câmera de luta e primeiro som) |
| [validation/relatorio-rodada-09.md](validation/relatorio-rodada-09.md) | relatório da rodada 09 (primeiro combate) |
| [validation/roteiro-de-teste-fisico-combate.md](validation/roteiro-de-teste-fisico-combate.md) | teste físico do combate com câmera e som (15–20 min, para o proprietário) |
| [validation/relatorio-rodada-08-1.md](validation/relatorio-rodada-08-1.md) | relatório da rodada 08.1 (malha conforme — fim do tremor — e dentes) |
| [validation/relatorio-rodada-08.md](validation/relatorio-rodada-08.md) | relatório da rodada 08 (primeira exploração jogável) |
| [validation/relatorio-rodada-07-2.md](validation/relatorio-rodada-07-2.md) | relatório da rodada 07.2 (criador de personagens do jogador) |
| [validation/relatorio-rodada-07.md](validation/relatorio-rodada-07.md) | relatório da rodada 07 (personalização modular em tempo de execução) |
| [validation/relatorio-rodada-06-1.md](validation/relatorio-rodada-06-1.md) | relatório da rodada 06.1 (: refinamento dos NPCs, desempenho, personalização) |
| [validation/relatorio-rodada-06.md](validation/relatorio-rodada-06.md) | relatório da rodada 06 (primeira expansão controlada) |
| [validation/relatorio-rodada-05-1.md](validation/relatorio-rodada-05-1.md) | relatório da rodada 05.1 (gate de qualidade) |
| [validation/relatorio-rodada-05.md](validation/relatorio-rodada-05.md) | relatório da rodada 05 |
| [validation/ambiente.md](validation/ambiente.md) | máquina e versões |

Procedimentos para agentes: `.claude/skills/` (character-design, character-customization, voxel-art, visual-qa,
rendering, weapon-animation, game-ui, gameplay, combat, audio, git-commits, music-composition, sound-design, audio-qa,
engineering-qa, quest-design, mundo-procedural; menus na skill game-ui). Ponto de entrada curto: [../CLAUDE.md](../CLAUDE.md).

## Histórico (rastreabilidade — não é especificação atual)

[historico/](historico/README.md): relatórios das rodadas 01–04, direções A–D, experimentos, pesquisa e
as skills da rodada 01.

Convenção: documentos oficiais descrevem o que **existe no repositório agora**. Pendências aparecem em
`problemas-conhecidos.md`, nunca como decisão aberta dentro de um guia.
