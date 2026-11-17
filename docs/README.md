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
| [decisions/](decisions/README.md) | ADRs (decisões vigentes e revistas) |
| [validation/checklist-de-aceitacao.md](validation/checklist-de-aceitacao.md), [validation/processo-de-revisao-visual.md](validation/processo-de-revisao-visual.md) | QA |
| [processo/commits.md](processo/commits.md) | commits, histórico e repositório público (regras do proprietário) |
| [validation/problemas-conhecidos.md](validation/problemas-conhecidos.md) | abertos, corrigidos, limitações aceitas, fora do escopo |
| [validation/matriz-de-problemas.md](validation/matriz-de-problemas.md) | rastreabilidade de todos os problemas (rodadas 01–05.1) com evidência verificada |
| [validation/relatorio-rodada-06-1.md](validation/relatorio-rodada-06-1.md) | relatório da rodada atual (06.1: refinamento dos NPCs, desempenho, personalização) |
| [validation/relatorio-rodada-06.md](validation/relatorio-rodada-06.md) | relatório da rodada 06 (primeira expansão controlada) |
| [validation/relatorio-rodada-05-1.md](validation/relatorio-rodada-05-1.md) | relatório da rodada 05.1 (gate de qualidade) |
| [validation/relatorio-rodada-05.md](validation/relatorio-rodada-05.md) | relatório da rodada 05 |
| [validation/ambiente.md](validation/ambiente.md) | máquina e versões |

Procedimentos para agentes: `.claude/skills/` (character-design, voxel-art, visual-qa, rendering,
weapon-animation). Ponto de entrada curto: [../CLAUDE.md](../CLAUDE.md).

## Histórico (rastreabilidade — não é especificação atual)

[historico/](historico/README.md): relatórios das rodadas 01–04, direções A–D, experimentos, pesquisa e
as skills da rodada 01.

Convenção: documentos oficiais descrevem o que **existe no repositório agora**. Pendências aparecem em
`problemas-conhecidos.md`, nunca como decisão aberta dentro de um guia.
