# Moradores dos ofícios (rodada 12.1, P76)

**Status:** implementado no gerador v2. Os conjuntos de ofício ganham quem trabalha neles: um morador por conjunto,
escolhido pelo **ofício**, nunca pelo nome do personagem.

## 1. O contrato técnico

| Elo | Onde está | O que é |
|---|---|---|
| ofício do conjunto | catálogo (`tools/conteudo_conjuntos.py` → `"oficio"`), copiado ao perfil congelado (`regiao_v2_e.json → conjuntos.*.oficio`) | nome técnico: `ferreiro`, `herbalista`, `comerciante`, `cacador`, `guardiao_de_trilhas` |
| spawn do trabalhador | a âncora do conjunto, `pontos.spawns` com tipo `npc` (catálogo → perfil) | posição no espaço do modelo; o morador fica nele, de frente para a praça (o rumo do conjunto) |
| quem exerce o ofício | `assets/scenes/habitantes_e.json → <id>.oficio` (escrito à mão) → perfil congelado `regiao_v2_e.json → oficios` (`tools/conteudo/perfil_regiao_v2.py`) | `{ofício: [moradores em ordem]}` |
| área de interação | a conversa de sempre: alcance 1,8 u, placa acima da cabeça (altura do rig + 0,3 u) | a conferência de alcance da região exige `falar:<id>` |

O gerador (`RegionV2.settlement`) segue, para cada conjunto posto, na ordem das construções:

1. Lê o ofício do conjunto.
2. Separa os moradores do perfil com aquele ofício que ainda não estão na região.
3. Sorteia um deles com o RNG próprio (`"oficios"`).
4. Põe o sorteado no spawn da âncora (`RegionLayout.Worker` + `RegionLayout.Npc`).

Sem ninguém para o ofício, o spawn fica vazio: o conjunto se lê pelos objetos.

**Garantias** (`GeneratorV2Test.oMoradorDoOficioFicaNoConjuntoSemBloquearNada`):

- **Exclusividade:** um morador por conjunto, um conjunto por morador, um morador por spawn.
- **Corpo livre:** o corpo dele (0,3 u) não encosta em sólido nenhum.
- **Sem bloqueio:** fica a ≥ 0,6 u de toda entrada e a ≥ 0,56 u de todo ponto de interação, e dá para falar com ele na
  colisão real.
- **Isolamento:** tirar os moradores de ofício do perfil não move construção, POI nem inimigo.
- **Sem nomes no gerador:** nada de `if nome == …`. O antigo "Bramor na torre" virou dado: `bramor.oficio =
  guardiao_de_trilhas`.

## 2. Quem existe hoje (auditoria da rodada 12.1)

| NPC | Papel nos dados | Ofício de conjunto? |
|---|---|---|
| Bramor | guardião de trilhas | **sim**: `guardiao_de_trilhas` (o conjunto da torre de vigia) |
| Kelyra | estalajadeira | não (a estalagem não é conjunto; ela já fica na porta) |
| Teodor | estudioso | não |
| Pluma | cronista | não |
| Ondina | guia costeira (só com lago) | não |
| Lunara | cantora noturna | não |
| Índira, Súvia, cartógrafa | figurantes da vila fixa, **sem papel nos dados** | não: dar a Índira o ofício de herbalista seria uma decisão narrativa (do proprietário), não uma descoberta |

Faltam **ferreiro**, **herbalista**, **comerciante** (e o feirante da barraca) e **caçador**.

## 3. Para o agente artista: o que entregar por personagem novo

Os modelos são **originais**, num ramo separado. A integração só acontece depois da aprovação do proprietário, e quem
integra é o desenvolvedor.

| Item | Valor |
|---|---|
| nome técnico do papel | o ofício: `ferreiro`, `herbalista`, `comerciante`, `cacador` (ou um ofício novo, junto com o conjunto) |
| id e pasta | `characters/npc/<id>/` (gerado por um módulo em `tools/`, como `npcs_oficios_e.py`; nunca editado à mão) |
| proporção | NPC da direção E: 2,0–3,0 cabeças, rosto em placa plana, olho livre em placa (`contrato-de-qualidade.md`); nada das medidas congeladas dos protagonistas |
| altura | narrativa, como os NPCs de ofício: 0,75 u por metro (Pluma 1,60 m … Bramor 2,30 m); altura pelo reamostrar do rig pronto, nunca esticando o corpo |
| rig | o esqueleto dos NPCs da vila (`npcs_vila_e.npc`: quadril 14, pescoço 29, voxel 1/40 u); `meta.escala` quando reamostrado |
| animações | as do `Animator` para NPC: `idle`, `walk` e `emote` (atuação `meta.acting`); as 4 cabeças de expressão de quem tem diálogo — `neutral`, `joy`, `determined`, `surprise` (as falas usam `"expressao"`), como os NPCs da vila e de ofício |
| ponto de spawn | o do conjunto (o desenvolvedor confere: corpo livre, conversa alcançável, nada bloqueado) |
| metadados | entrada em `habitantes_e.json`: `rig`, `nome`, `titulo`, `artigo`, `papeis` (para missões), **`oficio`**, `falas` (3, sem fato fixo; `{rumo_posto}` permitido), `falasSemLago` se citar o lago, `requer` se depender de um fato |
| testes do artista | `OficiosNpcTest` (proporção, faces, escala), `QualityTest`, `AssetContractTest`; captura no modo LINHA ao lado dos NPCs existentes |

O desenvolvedor, ao integrar:

1. revisa as fontes (nada de terceiros, nada que ligue o modelo a pessoa real);
2. confere o contrato e as animações;
3. compara as silhuetas;
4. põe o `oficio`;
5. regera o perfil da v2;
6. roda `GeneratorV2Test` e a conferência de alcance;
7. refaz a referência da v2 (enquanto ela não estiver publicada).

**Depois de a v2 ser publicada, um morador novo no perfil muda regiões, então vira versão nova do gerador** (ADR 0029).
Por isso, a decisão de quais personagens entram no perfil congelado vem **antes** da publicação.
