# 0009 — Cotovelo, joelho e IK analítico de pé (rodada 03)

**Estado:** aceito pelo agente, sujeito à revisão do proprietário. Revê parcialmente o 0003.

## Contexto

Na rodada 02 os membros eram peças inteiriças: o golpe dependia só do giro do tronco, o arco não
tinha como ser puxado até o rosto e o pé deslizava ~10% do passo. A missão da rodada 03 pediu
cotovelos/joelhos "na proporção" e pés corretos em terreno irregular, sem física de pano.

## Decisão

- Os heróis da E ganham **antebraço** (`farm_*`) e **canela** (`shin_*`) como partes filhas, cortadas
  na manga/barra da calça, com tampa de dobra pintada na cor da roupa (`charlib_e.limb_parts`).
- O animador articulado (`meta.joints`) usa **ciclo de pé por fase** (apoio fixo no mundo, balanço em
  arco) e **IK analítico de 2 ossos** por perna; a velocidade de deslocamento é derivada da passada.
- Uma **sonda de chão** (`Animator.Ground`) dá a altura sob cada pé; o quadril desce para o pé mais
  baixo e o joelho do outro dobra.

## Alternativas descartadas

- Skinning/ossos com pesos: exigiria malha deformável e sairia da linguagem de peças rígidas.
- IK numérico (CCD/FABRIK): desnecessário para 2 ossos, a solução fechada é exata e barata.
- Física de pano/capas: fora do escopo por decisão do proprietário.

## Consequências

- Rigs antigos (A–D, variantes) continuam no bípede da rodada 02 — nada foi alterado neles.
- Desnível entre os pés acima de 0,131 u (0,55 da perna) deixa o pé alto no ar (não há escalada). Revisto na rodada 05.1: o "~0,2 u" citado antes era a altura do degrau da bancada de teste, não o limite (ver `guia-de-animacao.md`, tabela "Pés").
- `AssetContractTest.consolidatedHeroesHaveElbowsAndKnees` protege a hierarquia.
