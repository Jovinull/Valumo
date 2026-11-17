# Skills

> **Rodada 05: HISTÓRICO.** Estas skills foram retiradas de `skills/`; o conteúdo útil (pipeline, convenções OpenGL) foi transferido para `.claude/skills/voxel-art` e `.claude/skills/rendering`.
>
> **Rodada 02:** as skills da direção consolidada (E) estão em `.claude/skills/` (carregadas pelo
> Claude Code): `character-design`, `voxel-art`, `visual-qa`, `rendering`. As skills abaixo
> continuam válidas para as direções A–D e para o pipeline comum; onde houver conflito, valem as de
> `.claude/skills/` e `docs/art/visual-identity.md`.

Procedimentos reutilizáveis por agentes (e por pessoas) **neste** repositório. Cada skill descreve
uma atividade com os comandos e arquivos reais do projeto; se o pipeline mudar, a skill muda junto.

| Skill | Use quando |
|---|---|
| [voxel-modeling](voxel-modeling/SKILL.md) | criar ou alterar um personagem, criatura ou prop voxel |
| [pixel-art](pixel-art/SKILL.md) | desenhar texturas e modelos de cuboides da direção C |
| [java-rendering](java-rendering/SKILL.md) | mexer no renderer, shaders, perfis de estilo ou desempenho |
| [visual-qa](visual-qa/SKILL.md) | capturar, comparar e julgar o resultado visual |
| [asset-pipeline](asset-pipeline/SKILL.md) | acrescentar um tipo de asset, um prop novo na cena ou uma direção nova |

Pré-requisito comum:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current   # JDK 25
python3 tools/build_assets.py                         # assets/ em dia
./gradlew :viewer:installDist                         # viewer usado por tools/snap.sh
```
