# Commits, histórico e repositório público

Repositório: `github.com/Jovinull/Valumo` (público). O histórico foi refeito do zero em 2026-11-17, só com o
necessário. Os históricos anteriores estão preservados localmente, fora do git: `voxel-rpg` privado e
Valumo v1, que tinha capturas e assets gerados. Ficam em `output/quarentena/`.

## O que entra no git

| Entra | Não entra |
|---|---|
| `engine/`, `viewer/`, `game/` (fontes e build Gradle), wrapper | `build/`, `bin/`, `.gradle/` |
| `tools/` (geradores e utilitários) | `__pycache__/`, `.venv/` |
| `assets/scenes/*.json`, `assets/anim/*.json`, `assets/audio/` (catálogo e arquivos de áudio) e `fontes/` (feitos à mão) | assets **gerados**: `assets/characters`, `creatures`, `environment`, `textures`, `studies`, `components`, `audio/sintese` |
| `docs/` (incluindo as referências canônicas em `docs/art/reference/`) | `output/` inteiro (capturas, vídeos, zips, temporários, quarentena) |
| `.claude/skills/`, `CLAUDE.md`, `README.md` | credenciais e qualquer vínculo de personagem com pessoa real |

Depois de clonar não é preciso gerar nada à mão: `./gradlew :viewer:run`, `editor`, `test` e `installDist`
dependem da tarefa `assets` (scripts de `tools/` → assets gerados; pulada quando nada mudou). As imagens canônicas ficam no git porque
são a prova da aprovação do proprietário. Os pacotes de revisão são locais e não vão para Releases.

## Regras do proprietário

1. Commit e push só com pedido explícito.
2. **Conventional Commits em português**, no formato `tipo(escopo): descrição`.
3. **Só o título**: sem corpo, sem `Co-Authored-By` nem outro trailer.
4. **Commits pequenos e separados por assunto**, em ordem lógica.
5. Autor com o e-mail `noreply` do GitHub (configuração local do repositório).

Tipos, escopos, exemplos e o checklist antes do push estão na skill `.claude/skills/git-commits/SKILL.md`.
