---
name: visual-qa
description: Revisar visualmente qualquer mudança do jogo com capturas reais do viewer — comparação controlada no modo LINHA, silhueta, argila, luz neutra, tamanho reduzido, close de rosto, cena, vídeo de animação (MP4/GIF), pacote --review2 e checklist de aceitação. Use sempre que algo visual mudar, antes de declarar pronto.
---

# QA visual (rodada 02 em diante)

Processo completo: `docs/validation/processo-de-revisao-visual.md`. Critérios:
`docs/validation/checklist-de-aceitacao.md`. **Nada é "aprovado" sem captura inspecionada** — e a
aprovação final da identidade é do proprietário, não do agente.

## Comandos

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
tools/snap.sh "<spec>" ["<spec>" ...]          # REBUILD=1 após mudar Java/GLSL; MODULES="chars_e" regenera
tools/anim.sh output/tmp/x "<spec>" 60 30      # vídeo real: MP4 + GIF
.venv/bin/python tools/sheet.py grid out.png 3 a.png b.png c.png   # prancha
.venv/bin/python tools/sheet.py seq out.png 6 "output/tmp/f_###.png"
viewer/build/install/viewer/bin/viewer --review2 [personagens,arquetipos,mundo,criaturas,performance]
```

## Chaves de captura (Main.java)

`style=a..e` · `mode=scene|char|side|prop|lineup` · `set=<App.LINEUPS>` (variantes, v1-v2-v3, herois,
herois-b-e-d, criaturas, criaturas-b-e) · `hero=characters/e/ranger` · `char=N` · `clip=` · `t=` ·
`yaw= pitch= dist= fov= ty=` · `myaw=` (gira os modelos) · `hx= hz= hyaw= lift=` · `preset=dia|tarde|neutra`
· `outline=0..3` · `fog=0..3` · `sil=1` · `notex=1` (argila) · `expr=neutral|joy|determined|surprise`
· `quality=baixa|padrao|alta` · `frames=N,fps=F,move=1` · `nohud=1` · `size=LxA` · `out=`.

## Regras de comparação

1. Uma variável por vez. 2. Modo LINHA (câmera tele fixa); para ângulos, `myaw`, nunca câmera em 3/4.
3. Sempre também em luz neutra. 4. Tamanho reduzido = render real em baixa resolução, não só reduzir.
5. Movimento = vídeo ou prancha de quadros consecutivos, não um quadro solto.
6. Toda câmera nova: confira se não está dentro de um objeto.

## Defeitos que já apareceram (procure primeiro)

Rosto fragmentado (decalque em superfície curva) · xadrez na nuca (mechas finas) · faixa escura na
testa (sombra profunda em cabelo claro) · forro claro de capuz = tiara · arma atravessando cabeça ou
aba · trança horizontal na corrida · degrau serrilhado escuro no terreno · névoa no plano do herói ·
contorno riscando tufos/degraus · copa "couve-flor".

## Entrega

Iteração em `output/tmp/` (ignorado); entrega em `output/visual-review/round-NN/` (nunca sobrescrever
rodadas anteriores). Registre defeito, severidade, correção e situação no relatório da rodada.

## Rodada 03

- Pacote completo: `viewer --review3 [personagens,animacoes,criaturas,terreno,vegetacao,iluminacao,comparacoes,performance]`.
- Silhueta válida = fundo uniforme, sem chão/sombra, figura inteira, frente/perfil/costas.
- Escala de exploração: renderize **em baixa resolução** e amplie sem filtro; não reduza a captura grande.
- Compare variantes por **diferença de pixels** antes de julgar: se duas variantes diferem < 1%, a
  mudança provavelmente não está sendo renderizada (o chanfro do relevo ficou invisível por
  culling e passou despercebido até essa medição).
- Movimento: `tools/anim.sh` (MP4/GIF) + `party=1` para os três heróis juntos na cena.

## Rodada 05 — referências canônicas e prova de reprodução

- **Compare com as referências**: `tools/reference.sh output/tmp/ref` refaz as 22 imagens oficiais com as
  mesmas specs (`docs/art/reference/manifesto.tsv`); `.venv/bin/python tools/repro_compare.py imgs
  docs/art/reference output/tmp/ref` mede a diferença (critério declarado: < 0,5% dos pixels acima de 8/255).
  Mudou de propósito → refaça as referências e registre o motivo; nunca troque uma referência sem aprovação.
- **Reprovados** (`docs/art/reference/reprovados/`): procure esses defeitos primeiro.
- **Auditoria estrutural** antes do olho: `python3 tools/asset_audit.py` (voxels soltos, fragmentos, órfãos).
- **Vídeos**: velocidade normal **e** câmera de jogo (`mode=scene`, câmera padrão) — a meia velocidade só
  como apoio. A câmera de jogo é onde o jogador vê (`ref 22`).
- **Validado ≠ aprovado**: o agente registra evidências; aprovação artística é do proprietário.
- Câmera de close: `dist=1.3,ty=0.93` (maga `1.45/0.98`); câmera nova sempre conferida (dentro da casa já aconteceu).

## Rodada 05.1 — inspeção OBRIGATÓRIA de animação em vídeo

Pranchas de 12 quadros escondem defeitos de transição (a lâmina sumindo atrás do braço na rodada 05 só
aparecia no MP4). Procedimento para qualquer animação nova ou alterada:

1. **Grave**: `tools/anim.sh <saída> "<spec>" <quadros> 30` em 4 câmeras — frente (`yaw=0`), perfil
   (`yaw=±90`), costas (`yaw=180`) e 3/4 (`yaw=35` e `yaw=-40`) — ou a grade 2×2 de `tools/review51.sh` (`quad`).
2. **Assista o MP4 inteiro em velocidade normal**, depois quadro a quadro nas transições (repouso → guarda,
   guarda → repouso, golpe → HIT). Para quadro a quadro denso:
   `ffmpeg -i x.mp4 -vf "select='not(mod(n,3))',scale=240:-1,tile=6x3" -frames:v 1 x.png`.
3. **Procure**: arma entrando na silhueta ou sumindo atrás do braço/tronco; arma diante do rosto; item
   girando sem a mão (ou mão girando em volta do item parado); estalos de orientação; mochila/cachecol/trança
   atravessando o corpo; pés deslizando.
4. **Meça o que suspeitar**: `viewer --occlusion rig golpe`, `viewer --grip rig golpe`, `viewer --check`.
5. **Antes/depois equivalentes**: mesmo binário, mesma spec, lado a lado — perfis antigos com
   `JAVA_OPTS=-Dvx.anim=<pasta>`; camada secundária com `-Dvx.secondary=false`.
6. **Escala real de jogo**: grave também com a câmera de exploração (`mode=scene`, `yaw=18,pitch=13,dist=6.4`,
   herói ~1/8 da altura do quadro) e com a câmera ATRÁS do herói (`hyaw=200`). Julgue a leitura nessa escala,
   não no close. Registre por câmera onde lê e onde não lê (ex.: arco no relatório 05.1).
7. Registre no relatório: o que viu, em que vídeo e instante, e se é defeito, limitação aceita ou ok.

## Rodada 06 — vitrine e pacote de expansão

- `layout=vitrine_e` carrega `assets/scenes/vitrine_e.json` (mesmo relevo/luz; assets novos ao lado dos
  aprovados). As 22 referências e a clareira continuam a linha de base: depois de qualquer asset novo,
  `tools/reference.sh output/tmp/ref && repro_compare.py imgs` tem de dar 22/22 idênticas.
- Pacote completo: `tools/review6.sh [npc|criatura|arvore|estrutura|cena|videos|todos]`.
- Câmera de cena = órbita em torno do ponto `hx,hz`: confira se ela não caiu dentro de um tronco, morro ou
  parede (aconteceu várias vezes nesta rodada) antes de usar a imagem.
- Silhueta de prop: `tools/prop_silhouette.py` (o `sil=1` em modo cena pinta o chão também).
