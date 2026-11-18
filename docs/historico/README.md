# Histórico (rastreabilidade — NÃO é especificação atual)

Tudo aqui documenta **decisões e estudos já encerrados**. Para criar ou alterar assets hoje, use a
documentação oficial: [../art/visual-identity.md](../art/visual-identity.md),
[../art/contrato-de-qualidade.md](../art/contrato-de-qualidade.md), [../art/reference/](../art/reference/README.md)
e as skills em `.claude/skills/`.

| Pasta | Conteúdo | Por que não é atual |
|---|---|---|
| `relatorios/` | relatórios das rodadas 01–04 | registram o estado e as escolhas de cada rodada; números e propostas foram superados (ver `docs/validation/relatorio-rodada-05.md`) |
| `rodada-01-direcoes/` | direções A–D, guia geral e resultados da rodada 01 | as quatro direções foram comparadas e a **E** foi escolhida; A–D seguem no código só para comparação |
| `experimentos/` | proporções (rodada 01), variantes do aventureiro (rodada 02), estudos da rodada 03 | os estudos produziram decisões (V2, H2, C3, M1, O3, S3, T1) já incorporadas à identidade |
| `pesquisa/` | proporções, referências visuais, riscos legais | pesquisa de base; as conclusões vigentes estão na identidade e no contrato de qualidade (a análise de riscos legais continua válida como fundamento) |
| `skills-rodada-01/` | skills da rodada 01 (asset-pipeline, java-rendering, pixel-art, visual-qa, voxel-modeling) | substituídas pelas skills de `.claude/skills/`; o conteúdo útil foi transferido na rodada 05 |
| `esquivas-09-3/` | poses e gestos do rolamento da 09.2 (`roll`, `roll_*`) e do rolamento diagonal R1 (`roll_r1`, `r1_*`), dos perfis `sword.json` e `sword_shield.json` | na rodada 09.3 o proprietário escolheu a esquiva R2; para reusar, copie as poses de volta ao perfil e rode `viewer --check esquiva` (o R1 precisava de janelas próprias, ver o adendo do ADR 0018) |
