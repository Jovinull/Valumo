# Documentação

> Caminhos `output/visual-review/...` citados nos relatórios são **locais** (fora do git): as capturas de
> cada rodada são regeneradas pelos scripts `tools/review*.sh`. Os assets em `assets/characters`, `creatures`,
> `environment`, `textures` e `studies` também saem de `python3 tools/build_assets.py`.

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
| [architecture/personalizacao-evolucao.md](architecture/personalizacao-evolucao.md) | evolução da personalização: rosto por partes (olhos feitos na 09), roupas por região (botas feitas na 09), criador |
| [architecture/criador-de-personagens.md](architecture/criador-de-personagens.md) | criador de personagens do jogador: layout, componentes, mouse, escala, fonte própria, miniaturas, composição assíncrona, roteiros e testes; guia das próximas interfaces |
| [decisions/](decisions/README.md) | ADRs (decisões vigentes e revistas) |
| [validation/checklist-de-aceitacao.md](validation/checklist-de-aceitacao.md), [validation/processo-de-revisao-visual.md](validation/processo-de-revisao-visual.md) | QA |
| [processo/commits.md](processo/commits.md) | commits, histórico e repositório público (regras do proprietário) |
| [validation/problemas-conhecidos.md](validation/problemas-conhecidos.md) | abertos, corrigidos, limitações aceitas, fora do escopo |
| [validation/matriz-de-problemas.md](validation/matriz-de-problemas.md) | rastreabilidade de todos os problemas (rodadas 01–05.1) com evidência verificada |
| [architecture/audio.md](architecture/audio.md) | áudio (rodada 09): catálogo de eventos, regras de reprodução, laço, pisada sincronizada, falhas, gravação em loopback; fontes editáveis em `fontes/audio/` |
| [validation/relatorio-rodada-09.md](validation/relatorio-rodada-09.md) | relatório da rodada atual (09: direção sonora, primeiros estudos de música e efeitos, integração de áudio, dois estudos de cabelo) |
| [architecture/jogo.md](architecture/jogo.md) | o jogo: módulos, estados, controle do herói, colisão, câmera, cena, interação, aparência, interface, testes |
| [validation/roteiro-de-teste-fisico.md](validation/roteiro-de-teste-fisico.md) | roteiro de teste com teclado e mouse físicos (criador e jogo) para o proprietário |
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
rendering, weapon-animation, game-ui, gameplay, git-commits, music-composition, sound-design, audio-qa). Ponto de entrada curto: [../CLAUDE.md](../CLAUDE.md).

## Histórico (rastreabilidade — não é especificação atual)

[historico/](historico/README.md): relatórios das rodadas 01–04, direções A–D, experimentos, pesquisa e
as skills da rodada 01.

Convenção: documentos oficiais descrevem o que **existe no repositório agora**. Pendências aparecem em
`problemas-conhecidos.md`, nunca como decisão aberta dentro de um guia.
