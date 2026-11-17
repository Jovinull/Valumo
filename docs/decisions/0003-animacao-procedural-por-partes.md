# ADR 0003 — Animação procedural por partes rígidas

**Estado:** aceito (rodada 01); limitações revistas pelo 0009 (articulações, IK de pé) e pelo 0011
(armas por trajetória e IK de braço). Locomoção, criaturas e expressões continuam procedurais.

## Contexto
Personagens voxel compactos, sem deformação de malha. Precisamos de idle, caminhada, corrida e, se
possível, ataque, dano e uma expressão — para quatro direções, com rigs de proporções diferentes.

## Decisão
- Cada personagem é uma **hierarquia de partes rígidas** com pivôs (`rig.json`).
- Os clipes são **funções do tempo** em Java (`Animator`), que escrevem rotação, deslocamento e
  escala por nome de parte. Não há arquivo de animação nesta rodada.
- Deslocamentos são medidos em **alturas do personagem**, então o mesmo clipe serve a rigs de
  tamanhos e proporções diferentes.
- **Squash & stretch** na raiz e partes secundárias com atraso de fase (capa, rabo de cavalo, chapéu,
  orelhas) dão vida sem ossos extras.
- A pose é um vetor de parâmetros; a troca de clipe é uma interpolação linear entre poses.
- Cada direção tem um fator de exagero (`StyleProfile.animExaggeration`).

## Por quê
- Partes rígidas preservam a leitura voxel (nada estica ou borra) e são o método natural do gênero.
- Clipes procedurais custam pouco, são determinísticos (bons para captura) e reaproveitados por
  todos os personagens e estilos.

## Limitações conhecidas
- Sem cotovelos nem joelhos: gestos largos (ataque por cima da cabeça) dependem do giro do tronco.
- Sem cinemática inversa: os pés não se ajustam ao degrau do terreno; o personagem segue uma altura suavizada.
- Sem piscar/trocar expressão: exigiria variantes da parte `head` ou uma parte `eyes`.
- Animações autorais (cutscenes, golpes específicos) vão pedir keyframes em arquivo e um editor.

**Nota (rodada 05):** desde a rodada 04 as ações com arma vêm de perfis escritos à mão em `assets/anim/*.json` (ADR 0011); a locomoção continua procedural.
