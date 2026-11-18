# Ambiente de validação

Máquina **atual** (rodadas 02–05), lida em 2026-11-16. Nada foi instalado com privilégio de
administrador: JDK por SDKMAN, editores por Flatpak de usuário, bibliotecas Python de QA numa `.venv`.

| Item | Valor |
|---|---|
| Sistema | Fedora Linux 45 Workstation, kernel 7.3.3, sessão Wayland |
| CPU / GPU | Intel Core i5-1335U (Raptor Lake) / Intel Iris Xe (RPL-U), Mesa 26.2.6, OpenGL 4.6 (o viewer cria 3.3 core) |
| Memória | 15 GiB |
| JDK | Temurin 25 via SDKMAN (`~/.sdkman/candidates/java/current`); o OpenJDK 25.0.4 do sistema também funciona |
| Gradle | wrapper do projeto (`gradle/wrapper/gradle-wrapper.properties`) |
| Python | 3.15.2 — scripts de assets só com a biblioteca padrão; `.venv` com numpy e Pillow para QA (`tools/sheet.py`, `tools/repro_compare.py`) |
| ffmpeg | 9.0.2 (`ffmpeg-free`, sem libx264: `tools/anim.sh` usa libopenh264) |
| git | 2.56.1 |
| Editores | Goxel 0.13.0, LibreSprite 1.1-dev, Blockbench 5.2.2 (Flatpak de usuário) — ver [ADR 0008](../decisions/0008-ferramentas-de-arte.md) |

## Bibliotecas (fixadas em `build.gradle.kts`)

| Biblioteca | Versão | Uso |
|---|---|---|
| LWJGL (`lwjgl`, `lwjgl-glfw`, `lwjgl-opengl`) | 3.4.3, natives `linux` | janela, entrada, OpenGL |
| JOML | 1.10.8 | vetores e matrizes |
| Gson | 2.13.2 | leitura de `rig.json`, `props.json`, cena, perfis de arma |
| JUnit Jupiter | 5.11.4 | testes |

## Editores externos (testado na rodada 05)

O `.vox` gerado abre e é reexportado pelo Goxel sem perda de posições nem cores
(`flatpak run --command=goxel io.github.guillaumechereau.Goxel in.vox --export out.vox`). O Goxel
**reescreve a paleta**, e com ela perde a faixa de material (metal, emissivo...); `voxlib.load_vox(path,
like=original)` restaura o material pela cor. Prova: `output/visual-review/round-05/reproducao/pipeline-de-edicao.md`;
procedimento: [../art/pipeline-de-producao.md](../art/pipeline-de-producao.md).

## Reprodutibilidade

Com Python 3.15, os PNGs da direção C saem com bytes diferentes dos da rodada 01 e **pixels idênticos**
(versão do zlib); a comparação de PNG é por pixel (`tools/repro_compare.py`). Uma cópia limpa do
repositório regenera todos os assets idênticos e as 22 referências idênticas pixel a pixel nesta máquina.
Em outra GPU/driver as capturas podem variar alguns níveis de cor.

## Observações de execução

- LWJGL 3.4.3 usa o backend Wayland nativo do GLFW nesta sessão. Wayland não permite posicionar
  janelas; o viewer só centraliza a janela em outras plataformas.
- Em Java 25 a inicialização do LWJGL imprime avisos `sun.misc.Unsafe ... will be removed`; não
  afetam a execução ([ADR 0001](../decisions/0001-stack-java-lwjgl.md)).
- Capturas offscreen usam uma janela GLFW invisível; precisam de sessão gráfica ativa (não rodam em
  servidor sem display, a menos de `xvfb-run` com Mesa por software — não testado).

## Rodada 01 — máquina anterior (histórico; números não comparáveis com os atuais)


| Item | Valor | Como foi obtido |
|---|---|---|
| Sistema | Ubuntu 24.04.4 LTS, kernel 7.0.0-42-generic, x86-64 | `/etc/os-release`, `uname -rm` |
| Sessão gráfica | GNOME em Wayland (XWayland disponível) | `$XDG_SESSION_TYPE` |
| CPU | Intel Core i5-1135G7 (4 núcleos / 8 threads) | `lscpu` |
| Memória | 15 GiB | `free -h` |
| GPU | Intel Iris Xe (Tiger Lake GT2), integrada | `lspci` |
| Driver / API | Mesa 25.2.8 — OpenGL 4.6 core, GLSL 4.60 | `viewer --info` |
| Monitores | 2 × 1920×1080 (interno 120 Hz, externo 60 Hz) | `xrandr --current` |

O contexto criado pelo viewer é **OpenGL 3.3 core** (piso do projeto); o driver oferece 4.6.

### Ferramentas (rodada 01)

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | Temurin 25.0.3+9 LTS | já instalado via SDKMAN (`~/.sdkman/candidates/java/current`) |
| Gradle | 9.9.1 (wrapper) | `gradle/wrapper/gradle-wrapper.properties` |
| Python | 3.12.3 | só biblioteca padrão nos scripts de assets |
| git | 2.43.0 | repositório iniciado, sem commits (regra da rodada) |

### Ferramentas artísticas (rodada 01 — superado; ver "Editores externos" acima)

Nenhum editor voxel ou de pixel art estava instalado (Blender, Goxel, MagicaVoxel, Blockbench,
Aseprite, LibreSprite, Krita: ausentes) e a instalação pelo `apt` exige senha de administrador, que
não foi fornecida nesta sessão. A rodada foi feita inteiramente com **modelagem e pixel art por
script** ([ADR 0002](../decisions/0002-assets-por-script.md)); os `.vox` gerados seguem o formato do
MagicaVoxel e podem ser abertos em Goxel (`sudo apt install goxel`, versão candidata 0.11.0 no
Ubuntu 24.04) ou MagicaVoxel — **essa abertura em editor externo não foi testada nesta rodada**; o
que foi testado é a leitura pelo carregador do projeto (`VoxModelTest` e o próprio viewer).

A biblioteca Pillow (10.2.0) existente na máquina foi usada apenas para montar mosaicos temporários
durante a iteração; não é dependência do projeto (as pranchas de entrega são montadas pelo viewer).


## Áudio e ferramentas da rodada 09.4

Instalados nesta máquina (Ubuntu 24.04.4, PipeWire 1.0.5): LMMS 1.2.2, FluidR3 GM 3.1 (`fluid-soundfont-gm`, MIT),
Audacity 3.4.2 (sem `mod-script-pipe`), sox 14.4.2, ffmpeg/ffprobe 6.1.1, vorbis-tools, plugins LADSPA (swh, tap,
caps), xvfb + xdotool + imagemagick (inspeção de interfaces), Goxel 0.13.0 (Flatpak de usuário). `.venv` recriada com
numpy, Pillow, imageio, soundfile e scipy. Áudio do jogo: OpenAL Soft embutido nos natives do LWJGL 3.4.3 (ADR 0023).
Detalhes e verificações: `docs/validation/relatorio-rodada-09-4.md` §2 e as skills de áudio.

**Fedora (máquina principal do projeto, integração da 09.4):**
- **LMMS 1.2.2:** a mesma versão, pelo Flatpak do usuário, sem privilégio:
  `flatpak install --user flathub io.lmms.LMMS`.
  - `tools/audio/render_musica.py` acha o LMMS sozinho: `VX_LMMS`, `lmms` no PATH ou o Flatpak (com acesso ao disco e
    ao `/tmp`).
  - Verificado: `render_sfx.py fontes/audio/sfx/passos/passos.mmp` gera os 48 passos.
- **FluidR3 GM:** não é mais usado. Por decisão do proprietário, os timbres são de síntese própria
  (`tools/audio/sintese.py`). Tudo renderiza só com o LMMS: as duas músicas, a interface, os passos e o ambiente.
- **Python:** `soundfile` e `scipy` no `.venv` (o QA de áudio usa).
