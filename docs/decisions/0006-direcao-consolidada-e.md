# ADR 0006 — Direção consolidada E ("Fábula Voxel")

**Estado:** **aprovado** pelo proprietário como base oficial (rodada 02); polimento da rodada 03
aprovado na abertura da rodada 04 (H2, C3, M1, O3, S3, T1, P1). Não haverá direção F.

## Contexto
A rodada 01 produziu quatro direções concorrentes. O proprietário escolheu a B como base e pediu uma
linguagem única que incorpore o acabamento de personagem da D, a legibilidade da A e a disciplina
técnica da C, sem virar uma colagem dos quatro conjuntos.

## Decisão
- Nova direção **E**, com seus próprios scripts (`chars_e`, `creatures_e`, `env_e`, biblioteca
  `charlib_e`), perfil de luz (`StyleProfile.e` com presets dia/tarde/neutra), terreno e composição
  (`clearing_e.json`). As direções A–D ficam intactas e navegáveis para comparação.
- Personagem a 1/40 u (entre B 1/32 e D 1/44), 2,35–2,60 cabeças, rosto em placa plana com olho
  3 × 5 e quatro expressões. Escolhido por experimento controlado com três variantes
  ([../experiments/variantes-do-aventureiro.md](../historico/experimentos/variantes-do-aventureiro.md)).
- Ambiente: formas da B, reaproveitando seu código (`env_b`) com paleta trocada por contexto; copas
  repintadas, plantas pequenas a 1/24 u, casa refinada.

## Por quê
B tem a identidade mais própria; D, os melhores personagens; a fusão foi testada (não inferida, como na
rodada 01) e venceu as alternativas nos critérios do proprietário. Reaproveitar `env_b` evita
reescrever o que já funcionava.

## Riscos
- `env_e` depende de `env_b` (troca de globais): mudar `env_b` muda a E. Mitigação: testes de contrato
  e o pacote de revisão `--review2`.
- O preset "tarde" ainda é mais fraco que o "dia" (verdes puxam para o oliva).
