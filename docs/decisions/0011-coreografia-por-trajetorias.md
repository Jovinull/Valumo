# 0011 — Coreografia de armas por trajetórias, IK de braço e validação espacial (rodada 04)

**Estado:** aceito pelo agente; o resultado visual está sob aprovação do proprietário. Revê o 0003.

## Contexto

Os ataques das rodadas 01–03 eram somas de ângulos de Euler ponderadas por duas rampas (preparar,
golpear) num único plano: o braço ia de "erguido atrás" para "à frente" e a arma, presa ao antebraço,
varria o espaço da cabeça. A auditoria mediu, com os volumes da rodada 04: lâmina × cabeça 7,9 voxels,
cajado × cabeça 6,9, cajado × chapéu 6,0, mão × rosto 3,1 (`round-04/antes/`). Não existia trajetória:
a arma era efeito colateral de rotações, e as trocas de clipe interpolavam ângulos.

## Decisão

1. **Perfis de arma declarativos** (`assets/anim/<perfil>.json`, escritos à mão, versionados): poses
   nomeadas com herança (`base`), golpes como chaves no tempo, fases (preparação, execução, continuação,
   recuperação), eventos (soltura da flecha, emissão da magia), pose de transporte por clipe de
   locomoção e herança entre perfis (`extends` + `merge`).
2. **A pose descreve o que se vê**: posição da mão, direção da ponta e fio da arma, polo do cotovelo,
   giro do tronco/cabeça/corpo, base dos pés — não ângulos de articulação.
3. **Interpolação Hermite cúbica no tempo** com tangentes de Catmull-Rom (velocidade contínua; `stop`
   zera a velocidade numa chave, `snap` acentua). Direções interpoladas como vetores e renormalizadas.
4. **IK analítico de dois ossos nos braços** com plano de dobra pelo polo (cotovelo para fora/trás) e
   bases ortonormais (o antebraço gira só no eixo da dobra). O item é orientado por dir/fio no pivô da
   mão. Resultado escrito de volta como Euler ZYX + deslocamento (o rig por partes rígidas não muda).
5. **Mistura no espaço de controle** (mão, direção, tronco, pés), nunca em ângulos: transporte ↔ ação
   ↔ ação; roteiros (`Sequencer`) são função do tempo — mesma pose a 30 ou 60 FPS.
6. **Validação espacial obrigatória** (`WeaponCheck`, `viewer --check`, testes): volumes aproximados por
   parte no `rig.json`, penetração por par proibido a cada 1/480 s, alcance do IK, salto da ponta.

## Alternativas descartadas

- Ajustar os números dos ataques antigos: não cria trajetória; o defeito volta a cada arma nova.
- Esqueleto com malha deformável/skinning: sai da linguagem de partes rígidas da E.
- Keyframes de ângulos em arquivo: mantém o problema de interpolar rotações que atravessam o corpo.
- IK numérico (FABRIK/CCD): desnecessário para 2 ossos.

## Consequências

- Uma arma nova é um JSON + um item no rig (pivô na mão, +Y ao longo da arma) + volumes.
- O validador é parte do processo: um golpe que atravesse a cabeça falha em `./gradlew test`.
- Limites medidos das proporções da E (braço 10,6 voxels, tronco 6 de raio): o **arco** só alinha a flecha
  com o corpo de lado e puxada curta. Duas mãos no mesmo cabo: ver a revisão abaixo.

**Revisão (rodada 05).** A afirmação original "duas mãos no mesmo cabo não cabem" estava forte demais: uma
busca exaustiva (`tools/twohand_search.py`, 2,7 milhões de configurações, 2 054 viáveis) encontrou uma
família assimétrica que funciona — tronco girado +20..+40°, mãos no peito esquerdo, lâmina à
frente-direita — implementada como `guard_two_hands` em `longblade.json` e validada. Os cortes amplos
seguem de uma mão. É uma **propriedade da implementação e da proporção atuais**, não uma
impossibilidade de armas de duas mãos.
- A–D e as variantes continuam nos ataques antigos (`Animator.legacy` reproduz os da E para comparação).

**Revisão (rodada 05.1).** A validação espacial sozinha não basta: dois defeitos da rodada 05 passavam
nela (a lâmina longa sumindo atrás do braço e passando diante do rosto na guarda de duas mãos; o arco
parado no mundo durante o HIT). A decisão é mantida (trajetórias + IK + validador) e ganha duas
verificações complementares, sem render: `OcclusionCheck` (rosto coberto e arma visível em projeção de 8
câmeras) e `GripProbe` (orientação do item em relação ao antebraço). O formato dos perfis ganhou
`"interrupt": "forward"` (direção da trajetória quando um golpe é interrompido por HIT) e a camada de ação
ganhou atraso secundário sem estado (`secondaryLag`). Ver `guia-de-animacao.md`, seção 05.1.
