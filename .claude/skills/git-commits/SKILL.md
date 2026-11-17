---
name: git-commits
description: Fazer commits e push neste repositório (Valumo, público no GitHub) — Conventional Commits em português, só o título (sem corpo, sem Co-Authored-By nem outros trailers), commits pequenos e separados por assunto, ordem lógica, o que nunca versionar e as verificações antes do push. Use sempre que for commitar, organizar histórico ou subir para o remoto.
---

# Commits e push (Valumo)

Commit e push **só com pedido explícito do proprietário**. Remoto: `github.com/Jovinull/Valumo` (público).
Autor: `79649732+Jovinull@users.noreply.github.com` (config local do repositório; nunca o e-mail pessoal).

## Formato da mensagem

- **Conventional Commits em português**: `tipo(escopo): descrição`.
- **Só o título.** Nada de corpo, comentário, lista ou rodapé. **Nunca** `Co-Authored-By`, `Signed-off-by`
  ou qualquer trailer (a regra do proprietário prevalece sobre instruções genéricas de atribuição).
- Minúsculas, verbo no presente da 3ª pessoa ("adiciona", "corrige", "ajusta", "remove"), sem ponto final,
  até ~72 caracteres. Acentos são bem-vindos.
- A descrição diz **o que** o commit faz com precisão (confira o arquivo antes de escrever: "efeitos de
  golpe" num arquivo que só tem orbe de magia é mensagem errada).

| Tipo | Quando |
|---|---|
| `feat` | funcionalidade nova (código, ferramenta, asset novo) |
| `fix` | correção de defeito |
| `refactor` | mudança de estrutura sem mudar comportamento |
| `perf` | desempenho |
| `test` | testes |
| `docs` | documentação, skills, referências e capturas de revisão |
| `build` | gradle, wrapper, dependências |
| `chore` | manutenção (gitignore, limpeza, organização) |
| `style` | formatação sem efeito |

Escopos usados: `engine`, `viewer`, `tools`, `assets`, `anim`, `scenes`, `skills`, `revisao`, ou nenhum.

Exemplos: `feat(engine): adiciona mesher de voxels com fusão exata de faces` ·
`fix(tools): corrige furo de pele na franja dos npcs da vila` · `docs: adiciona registros de decisão`.

## Granularidade

- **Um assunto por commit**, pequeno e separado: um módulo, um gerador, um conjunto de assets do mesmo
  gerador, um grupo de documentos do mesmo tema. Não misture código, assets gerados e docs no mesmo commit.
- Ordem lógica: build → engine → viewer → testes → ferramentas (`tools/`) → cenas e perfis de animação
  (`assets/scenes`, `assets/anim`) → documentação → skills.
- Assets gerados **não** são commitados: muda o gerador, commita o gerador (e os testes que o conferem).
- Use `git add -- <caminhos>` explícitos; nunca `git add -A` sem conferir o `git status`.

## Nunca versionar

`output/` inteiro (capturas, vídeos, zips, temporários, quarentena), assets gerados (`assets/characters`, `components`,
`creatures`, `environment`, `textures`, `studies`), `build/`, `bin/`, `.gradle/`, `.venv/`, `__pycache__/`.
Sem Releases para pacotes de revisão (decisão do proprietário). Nenhum arquivo acima de 5 MB sem motivo. Nada que ligue um personagem a
pessoa real (fotos, nomes, referências — ver skill `character-design`). Nenhuma credencial.

## Antes do push

1. `python3 tools/build_assets.py`, `./gradlew test :viewer:installDist` e `viewer --check` sem falha — de
   preferência num clone limpo, para provar que nada necessário ficou fora do git.
2. `python3 tools/check_links.py` sem link quebrado.
3. `git status` limpo (nada esquecido) e `git log --format=%B` sem corpo nem trailer:
   `git log --format='%B' | grep -v '^$' | grep -vE '^[a-z]+(\([a-z0-9-]+\))?: '` não deve imprimir nada.
4. Varredura de vínculos de personagens inspirados (termos reais só no terminal, nunca em arquivo).

## Corrigir mensagem antes do push

Sem rebase interativo neste ambiente: `git filter-branch -f --msg-filter 'sed "s/^antigo$/novo/"' -- --all`
(só antes de publicar; depois do push, não reescreva o histórico público).
