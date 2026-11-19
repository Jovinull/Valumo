# Relatório — Fechamento do QA de engenharia (checkpoint `engineering-foundation-v0.3`)

Fecha a fase que vai da auditoria de engenharia independente (feita sobre `8243b59`) até um checkpoint estável para a
Rodada 10. Pacote completo, local e fora do git: `output/qa/final-closure/` (relatórios A–L, medições, capturas,
sessão longa). Handoff da próxima etapa: [handoff-rodada-10.md](handoff-rodada-10.md).

## 1. O que entrou

**Da auditoria:** as 17 correções, revisadas no diff e não só reexecutadas.
- som mudo sem decodificar música a cada quadro;
- criador sem vazar a cena na GPU (reproduzido de novo aqui: 210 → 422 malhas antes, 210 constante depois);
- build incremental que reexecuta os testes quando `assets/` muda;
- clone limpo com numpy declarado;
- três defeitos de combate (clique na pausa de impacto, coleira empurrada, fungo preso fora da coleira);
- validadores contando o que conferem;
- o buffer só de posição da sombra;
- o medidor de GPU por passo;
- 51 avisos do compilador e da IDE;
- documentos e skills, mais a skill `engineering-qa`.

**Separado:** o Titã Celeste (Velária) fica num ramo local de candidato, fora da `main` e da tag, até a revisão de
originalidade do proprietário. As melhorias gerais de recursos que vieram com ele ficaram na `main` sem depender dele.

## 2. Corrigido neste fechamento

| Área | Defeito | Prova |
|---|---|---|
| Referências canônicas | 8 de 22 não batiam com a `main` (repouso do cajado aprovado na 09.4) | o código final com o `staff.json` anterior reproduz as antigas com desvio 0; refeitas; 22/22 com desvio 0 numa geração nova; anteriores em `docs/art/reference/anteriores/r094/` |
| Arma × cabelo | a sonda não media o broquel; ampliada (broquel, chapéu, capuz, expressões, salto), achou o broquel roçando o chanel na esquiva de frente | pose do broquel corrigida em `sword_shield.json`: 0 voxel em todo corpo; `HairWeaponTest` |
| Validadores | `--occlusion` e `--grip` aprovavam golpe inexistente; `WeaponCheck` aprovava rig sem volumes; 36 linhas "OK" de NPC sem nada conferido; a exceção do arco era "sai com 1, ignore" | reprovam o que não conferiram; exceção explícita (`OcclusionCheck.FACE_EXCEPTIONS`); `ValidatorContractTest` |
| Arquivos malformados | `.vox`/`.wav` com laço infinito por tamanho negativo, alocação sem limite; preferência ilegível sobrescrita; volume `NaN`; aparência com tipo errado derrubava o criador; `NaN` no combate virava 0 | `CorruptFilesTest`, `CorruptPrefsTest`, `AppearanceRobustnessTest` |
| Áudio | abrir o criador de dentro do jogo deixava a música e o ambiente 1,9 s em silêncio (fila de 1 s) | medido em tempo real pelo backend `wave` do OpenAL Soft, sem alto-falante; fila cresce até 4 s; 0 buraco em 3 gravações e em 31 min de sessão longa |
| Interface | frase fora do painel em 1366×768; lista de aparências sem rolagem além de 9; nome longo por cima da etiqueta; tecla presa na volta do criador; dica de controles contando desde a escolha; placa dentro da cabeça do NPC alto; comparação gravada como preferência | capturas em 7 resoluções (incluindo 16:10 e 4:3); `GameplayTest` |
| Estabilidade | `Combat.events` crescia a sessão inteira | guarda os 4.096 mais recentes |
| Desempenho | `UiBatch.quad` alocava um array por retângulo; `Shader.loc` alocava uma lambda por uniforme | alocação em regime no combate −63% (JFR); interface idêntica pixel a pixel |

## 3. Números do checkpoint

| | Resultado |
|---|---|
| Testes | **290, 0 falhas, 0 pulados** (engine 45, viewer 116, game 129); build limpo `--warning-mode all` sem depreciação |
| Compilador e IDE | `javac -Xlint:all`: 0 avisos; import limpo do Java Language Server: 0 diagnósticos nos 4 projetos |
| Validadores | `--check` (129 animações), `interrupcao-fina`, `golpe-andando`, `esquiva`, `--check-cabelos`, `--occlusion` (60 golpes com arma, 1 exceção explícita), `--grip`: todos saem 0 pela via certa, e entradas inválidas saem 1 |
| Gate dos presets | 51 pares, 0 pixel |
| Referências canônicas | 22/22, desvio 0 |
| Geração de assets | determinística (1.316 arquivos, mesmo hash depois de apagar e regenerar) |
| Áudio | QA do catálogo: 43 arquivos, 0 alertas; tempo real sem buraco |
| Sessão longa (31 min, 160 ciclos) | malhas, texturas, FBOs, OpenAL e threads estáveis; nenhum recurso nativo subindo; quadro sem piorar |
| Desempenho, sem som (roteiros) | vila 23,7 → **9,3 ms** (mediana); combate 22,7–23,5 → 8,4–8,7 ms |
| Desempenho com som (loopback) | vila 10,45 → 10,12 ms; combate de três 9,57 → 9,16 ms |
| Bench do viewer (vila, 1080p) | 10,0 → **9,2 ms**; passo de sombra 2,03 → 1,31 ms (+23% de memória de vértices) |
| Build | `distributionSha256Sum` no wrapper e `verification-metadata.xml` (53 artefatos conferidos contra o Maven Central); CI em `.github/workflows/ci.yml` |

A 60 Hz o jogador continua vendo 16,7 ms por quadro (vsync): o ganho é folga, não FPS nesta tela.

## 4. Pendências do proprietário

1. **Teste físico (P48):** combate depois das correções (pausa de impacto, coleira, broquel), teclado, mouse e som de
   verdade.
2. **Escuta:** efeitos, passos, interface e ambiente (as músicas A e B já estão aprovadas).
3. **Titã Celeste:** revisão de originalidade e aprovação artística. A avaliação visual do agente está no pacote local.
4. **VS Code:** se a aba Problems mostrar erros antigos, *Java: Clean Java Language Server Workspace* → *Reload and delete*.

## 5. Ficou no backlog

- unificar os dois sistemas de áudio;
- `App` do criador reaproveitado, para não remontar a cena em ~1–2 s a cada abertura;
- `Draw` reaproveitável no `Actor.submit`;
- vértice compacto e indexado e sombra estática em cache (rodada técnica própria);
- números de jogo fora do `combate_e.json`;
- inversão do fio da espada no `slash_v`/`combo`, a conferir em vídeo;
- excluir o `kotlin-stdlib` que o JOML arrasta;
- plugin `java` no projeto raiz.
