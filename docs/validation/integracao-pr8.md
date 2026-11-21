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

## Rodada 12.3 — o Acapela entrou na biblioteca

**Decisão do proprietário** (adendo da 12.3): o Acapela não estava rejeitado; ele seria avaliado e, no nível do
projeto, incorporado à biblioteca, sem profissão inventada e sem distribuição nas vilas.

**Avaliação:**

- **Contrato:** `AcapelaTest` 11/11 e `MundoNpcTest` 13/13, num clone isolado com o ramo do artista.
- **Capturas do renderer real:** seis rumos, o rabo parado, andando e correndo (de 3/4 e de trás), o aceno de perto, a
  alegria e a tomada na câmera de jogo ao lado do elenco.
- **O que se viu:**
  - silhueta própria; 2,14 cabeças, 1,125 u (a altura da Kelyra);
  - 4.224 faces (Kelyra 4.852, Pluma 4.248);
  - o rabo nasce no corpo, descansa no chão parado, balança andando e sobe correndo, sem atravessar as pernas nas
    tomadas;
  - o aceno passa à frente das franjas.
- **Defeito concreto:** nenhum achado, então nada foi mudado (a identidade é a da rodada 2 do artista).
- **Varredura:** sem referência externa, autor noreply, mensagens só com título.

**Como entrou:** merge LOCAL do ramo `estudo/npcs-e-ruinas` (ponta `103d50c`) em `feature/world-generation-v2`, no commit
`d2a7134` ("chore: incorpora o Acapela do PR #8 à biblioteca de personagens").

- **Os commits:** os 9 do artista depois de `f92e6a2` (de `218f504` a `103d50c`) entram com os SHAs originais; nada foi
  reescrito.
- **O que traz:** o gerador, o perfil de animação, o contrato, a sonda do rabo, a prova de determinismo, as evidências
  e a seção da skill `character-design`. Os quatro arquivos compartilhados recebem as linhas dele.

**Papel:** os documentos do artista não definem profissão (o `meta.role` é `morador`, genérico). Ele fica na biblioteca
(`assets/characters/npc/acapela`, gerado), fora de `habitantes_e.json` e do perfil da v2: nenhuma região muda.
Participação futura sugerida no relatório da 12.3.

**O PR #8:** com este merge, todos os commits do PR estão no ramo. Ele pode ser fechado quando este ramo entrar na `main`.

## Publicação da v2 — fechamento do PR #8

**Conferido antes da publicação** (ponta do artista `103d50c`):

- **Commits:** os **34 commits** do PR #8 são ancestrais do ramo do PR #9, com os SHAs originais. Entraram pelos dois
  merges: `b7e0347` (até `f92e6a2`, 25 commits) e `d2a7134` (o Acapela, 9 commits).
- **Arquivos:** dos **41** que o PR #8 toca, 34 estão idênticos no PR #9. Os outros 7 receberam commits de integração
  depois do merge, todos registrados:

  | Arquivo | Por quê |
  |---|---|
  | `tools/conteudo_ruinas.py` | a abóbada e o sítio liberados com formas compostas (12.2) |
  | `tools/conteudo_props.py`, `tools/conteudo_lib.py` | o pote do achado, os degraus de pedra, a copa fosca, as vagas (12.1–12.2) |
  | `viewer/src/test/java/vx/viewer/ConteudoMundoTest.java` | a regra do perfil composto junto com a do artista (conflito resolvido no merge) |
  | `docs/architecture/catalogo-de-conteudo.md`, `.claude/skills/conteudo-mundo/SKILL.md`, `docs/art/conteudo/acapela/README.md` | documentação do gerador v2, da publicação e da entrada do Acapela |

- **O Acapela:** está na biblioteca (`tools/npc_acapela_e.py`, `assets/anim/npc_acapela.json`, `AcapelaTest`), sem
  profissão e fora da distribuição procedural.

**Encerramento:**

- o PR #8 é fechado com um comentário que aponta o PR #9, **sem um segundo merge**;
- o ramo `estudo/npcs-e-ruinas` do artista não é alterado nem apagado.
