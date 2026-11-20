# Integração do PR #8 (estudo de NPCs de ofício e ruínas) — rodada 12.2

**Como entrou:** por um merge LOCAL do commit `f92e6a2` do ramo `estudo/npcs-e-ruinas` em `feature/world-generation-v2`
(commit de merge `b7e0347`, título "chore: incorpora a parte aprovada do estudo de NPCs e ruínas do PR #8 até f92e6a2").

- **Os commits:** entram os 25 do PR até `f92e6a2` (de `7e7fab5` a `f92e6a2`), com os SHAs originais, como ancestrais do
  ramo.
- **O que não foi feito:**
  - nada foi reescrito, rebaseado ou empurrado;
  - o ramo do artista e o PR #8 continuam como estavam (aberto, rascunho);
  - a `main` não foi tocada.

**O que ficou de fora: o Acapela** (candidato, não aprovado), nos commits de `218f504` a `103d50c` (9 commits, 12
arquivos):

- o gerador `tools/npc_acapela_e.py` e o perfil `assets/anim/npc_acapela.json`;
- `AcapelaTest`, as evidências `docs/art/conteudo/acapela/` e as linhas dele em `build_assets.py`, `determinismo.py`,
  `NpcClipProbe.java` e na skill `character-design`.

A separação é exata porque o PR foi feito em ordem: tudo o que é do Acapela vem depois de `f92e6a2`. Os quatro arquivos
compartilhados entram na versão de `f92e6a2`, que é a de antes do Acapela. O candidato fica inteiro no ramo do artista,
para avaliação posterior.

**Para fechar o PR #8 sem perder rastro:** quando este ramo for publicado, o PR pode ser fechado com a nota "a parte
aprovada (até `f92e6a2`) entrou na rodada 12.2 pelo merge `b7e0347`; o Acapela segue no ramo `estudo/npcs-e-ruinas` como
candidato". Os 25 commits estarão no histórico com os SHAs de origem.

## Conflito resolvido no merge

`viewer/src/test/java/vx/viewer/ConteudoMundoTest.java`: as duas regras valem juntas.

- **Do PR:** aprovado, conceito ou rejeitado exige a decisão do proprietário registrada. Ela substitui a regra antiga do
  "lote ≥ 3".
- **Da 12.1:** asset com formas pode estar liberado, desde que tenha o perfil composto em `colisao_e.json`. A regra do PR
  "com formas, nunca liberado" é anterior à colisão composta.

## Ajustes da integração (depois do merge, em commits próprios)

| Arquivo | Ajuste | Por quê |
|---|---|---|
| `tools/conteudo_props.py` | a vaga `feirante_lateral` 1 voxel mais para fora (25 → 26) | a conferência do jogo pede 0,55 u livres em volta do morador posto no chão; na vaga do PR eram 0,518 do poste da frente. Mesmo lado, mesmo rumo, fora da aba do toldo; a barraca não muda |
| `tools/conteudo_construcoes.py` | vaga `herbalista_lado` na cabana da herbalista | o spawn do catálogo (lote 1) ficava a 0,40 u da porta: a Índira bloquearia a entrada |
| `tools/conteudo_ruinas.py` | abóbada e sítio `liberado` | formas compostas medidas e percorridas pelo `CompositeCollisionTest` |
| `assets/scenes/colisao_e.json` | perfis compostos da abóbada (356 formas, 6 só de câmera sobre a cobertura) e do sítio (88) | medidos por `formas_compostas.py`; os outros 35 perfis saíram iguais |
| `assets/scenes/habitantes_e.json` | Brásia, Quelmo, Arvel e Índira com o ofício, papéis sem `caminhos`/`trilhas` (não mudam quem dá a missão) e falas originais | o vínculo conjunto → ofício → morador |
| `tools/conteudo/perfil_regiao_v2.py` → `regiao_v2_e.json` | o ponto do trabalhador por conjunto (spawn da âncora ou vaga recomendada) e as ruínas novas | o gerador lê o perfil congelado |

## Arquivos incorporados (33, a versão de `f92e6a2`)

- `assets/anim/npc_cacador.json`
- `assets/anim/npc_feirante.json`
- `assets/anim/npc_ferreira.json`
- `assets/scenes/vitrine_estudo_npcs.json`
- `assets/scenes/vitrine_estudo_ruinas.json`
- `.claude/skills/character-design/SKILL.md`
- `.claude/skills/conteudo-mundo/SKILL.md`
- `docs/architecture/catalogo-de-conteudo.md`
- `docs/art/conteudo/estudo-npcs-e-ruinas/abobada-interior.png`
- `docs/art/conteudo/estudo-npcs-e-ruinas/npcs-no-elenco.png`
- `docs/art/conteudo/estudo-npcs-e-ruinas/npcs.png`
- `docs/art/conteudo/estudo-npcs-e-ruinas/quelmo-gesto-na-vaga.png`
- `docs/art/conteudo/estudo-npcs-e-ruinas/quelmo-vaga.png`
- `docs/art/conteudo/estudo-npcs-e-ruinas/README.md`
- `docs/art/conteudo/estudo-npcs-e-ruinas/ruinas-na-biblioteca.png`
- `docs/art/conteudo-mundo.md`
- `tools/build_assets.py`
- `tools/conteudo/determinismo.py`
- `tools/conteudo_e.py`
- `tools/conteudo_lib.py`
- `tools/conteudo/npc_clip.sh`
- `tools/conteudo/npc_vistas.sh`
- `tools/conteudo/pacote_estudo.sh`
- `tools/conteudo/prancha.py`
- `tools/conteudo_props.py`
- `tools/conteudo_ruinas.py`
- `tools/conteudo/vaga_feirante.py`
- `tools/conteudo/videos_revisao.py`
- `tools/conteudo/vitrine_estudo.py`
- `tools/npcs_mundo_e.py`
- `viewer/src/test/java/vx/viewer/ConteudoMundoTest.java`
- `viewer/src/test/java/vx/viewer/MundoNpcTest.java`
- `viewer/src/test/java/vx/viewer/NpcClipProbe.java`

## Arquivos fora (12, só do Acapela ou na versão com o Acapela)

- `assets/anim/npc_acapela.json`
- `.claude/skills/character-design/SKILL.md`
- `docs/art/conteudo/acapela/elenco.png`
- `docs/art/conteudo/acapela/expressoes.png`
- `docs/art/conteudo/acapela/README.md`
- `docs/art/conteudo/acapela/rodada1-x-rodada2.png`
- `docs/art/conteudo/acapela/vistas.png`
- `tools/build_assets.py`
- `tools/conteudo/determinismo.py`
- `tools/npc_acapela_e.py`
- `viewer/src/test/java/vx/viewer/AcapelaTest.java`
- `viewer/src/test/java/vx/viewer/NpcClipProbe.java`

Quatro arquivos aparecem nas duas listas (a skill `character-design`, `build_assets.py`, `determinismo.py` e
`NpcClipProbe.java`): entram na versão de antes do Acapela, e as linhas dele ficam de fora.
