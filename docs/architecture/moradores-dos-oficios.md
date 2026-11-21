# Moradores dos ofícios (rodada 12.1, P76; personagens na 12.2)

**Status:** implementado no gerador v2. Na rodada 12.2, os cinco ofícios têm personagem: Brásia, Quelmo e Arvel vêm do
PR #8, e a Índira e o Bramor já existiam ([integração](../validation/integracao-pr8.md)). Cada conjunto de ofício ganha
quem trabalha nele: um morador por conjunto, escolhido pelo **ofício**, nunca pelo nome do personagem.

## 1. O contrato técnico

| Elo | Onde está | O que é |
|---|---|---|
| ofício do conjunto | catálogo (`tools/conteudo_conjuntos.py` → `"oficio"`), copiado ao perfil congelado (`regiao_v2_e.json → conjuntos.*.oficio`) | nome técnico: `ferreiro`, `herbalista`, `comerciante`, `cacador`, `guardiao_de_trilhas` |
| ponto do trabalhador | perfil `regiao_v2_e.json → conjuntos.*.trabalhador` = `[x, z, y, rumo, origem]`, escrito por `perfil_regiao_v2.py` | rodada 12.2: a vaga RECOMENDADA da âncora (`pontos.vagas`, a da herbalista) ou de um ITEM do conjunto (a `feirante_lateral` da barraca, convertida para o referencial da âncora); sem vaga, o spawn `npc` da âncora. `y` = altura do ponto (a laje da oficina); `rumo` = o da vaga, somado ao do conjunto |
| quem exerce o ofício | `assets/scenes/habitantes_e.json → <id>.oficio` (escrito à mão) → perfil congelado `regiao_v2_e.json → oficios` (`tools/conteudo/perfil_regiao_v2.py`) | `{ofício: [moradores em ordem]}` |
| área de interação | a conversa de sempre: alcance 1,8 u, placa acima da cabeça (altura do rig + 0,3 u) | a conferência de alcance da região exige `falar:<id>` |

O gerador (`RegionV2.settlement`) segue, para cada conjunto posto, na ordem das construções:

1. Lê o ofício do conjunto.
2. Separa os moradores do perfil com aquele ofício que ainda não estão na região. Rodada 12.2: um morador só de ofício
   (fora da lista dos moradores comuns, `RegionGenerator.RESIDENTS`, que a v1 também usa e não pode mudar) entra direto.
3. Sorteia um deles com o RNG próprio (`"oficios"`).
4. Põe o sorteado no ponto do trabalhador do perfil (`RegionLayout.Worker` com a altura + `RegionLayout.Npc`).

**No jogo** (12.2, só desenho):

- O morador de ponto elevado fica de pé no chão de colisão (a Brásia na laje, não afundada nela), e o corpo dele vai até
  a cabeça lá em cima.
- O morador com `gesto` toca o gesto de ofício do perfil de animação dele a cada 9 s, com fase pela posição, nunca durante
  a conversa (`GameLogic.npcMotion`): a Brásia bate o punho na palma, o Quelmo arruma a mercadoria e o Arvel confere o
  equipamento. A Índira e o Bramor só acenam (as animações de sempre).

**A conferência do corpo:**

- **Morador posto pelo gerador em chão aberto:** quer 0,55 u livres em volta.
- **Morador de ofício:** fica num ponto AUTORADO do catálogo, conferido à parte (`CompositeCollisionTest.p73`: entrar, forja,
  bigorna, bancada, conversar, sair). Para ele vale o corpo (o círculo de 0,3 u) não encostar em nada acima dos pés. No
  galpão da oficina, nenhum ponto cumpre 0,55 u sem tomar o lugar de quem usa a forja; o P73 aprovado não cumpre, e a tina
  fica a 0,44 u do centro da Brásia, sem tocar nela.

Sem ninguém para o ofício, o spawn fica vazio: o conjunto se lê pelos objetos.

**Garantias** (`GeneratorV2Test.oMoradorDoOficioFicaNoConjuntoSemBloquearNada`; o Quelmo visível na barraca em 5 rumos:
`CompositeCollisionTest.oFeiranteNaVagaLateralFicaVisivelEAlcancavel`):

- **Exclusividade:** um morador por conjunto, um conjunto por morador, um morador por ponto; TODO conjunto posto tem o seu
  (os cinco ofícios têm personagem), no ponto do perfil.
- **Corpo livre:** o corpo dele (0,3 u) não encosta em sólido nenhum acima dos pés.
- **Sem bloqueio:** fica a ≥ 0,6 u de toda entrada e a ≥ 0,56 u de todo ponto de interação, e dá para falar com ele na
  colisão real.
- **Isolamento:** tirar os moradores de ofício do perfil não move construção, POI nem inimigo.
- **Sem nomes no gerador:** nada de `if nome == …`. O antigo "Bramor na torre" virou dado: `bramor.oficio =
  guardiao_de_trilhas`.

## 2. Quem exerce cada ofício (rodada 12.2)

| Ofício (técnico) | Morador | Onde fica | Gesto | Em 200 sementes |
|---|---|---|---|---|
| `ferreiro` | Brásia (PR #8) | no galpão da oficina, o ponto P73 (laje, 0,44 u) | punho na palma | 60 |
| `comerciante` | Quelmo (PR #8) | vaga `feirante_lateral` da barraca, rumo −35° | arruma e oferece a mercadoria | 49 |
| `cacador` | Arvel (PR #8) | porta da cabana, fora do varal | confere o equipamento | 69 |
| `herbalista` | Índira (vila fixa; papel decidido pelo proprietário) | vaga `herbalista_varal` (rodada 12.3), junto ao varal de ervas, 2,45 u da porta, virada 20° para a rua | — (acena) | 88 |
| `guardiao_de_trilhas` | Bramor | posto da torre de vigia | — (acena) | 73 |

- **Cobertura:** 190 de 200 regiões têm pelo menos um morador de ofício; cada conjunto posto tem o seu.
- **A missão não muda:** os papéis dos novos não incluem `caminhos`/`trilhas`, que são os do doador da missão (Kelyra,
  Bramor).
- **Fora dos ofícios:** Kelyra, Teodor, Pluma, Ondina e Lunara continuam moradores comuns. Súvia e a cartógrafa continuam
  sem papel.
- **Várias vilas no futuro:** nada aqui supõe que só existe uma herbalista. O perfil lista `{ofício: [moradores]}` e o
  gerador não repete um morador na mesma região; com várias vilas, entram mais nomes por ofício (personagens novos, com
  aprovação). Uma vila sem nome disponível deixa o ponto vazio, e o conjunto se lê pelos objetos.

## 3. Para o agente artista: o que entregar por personagem novo

Os modelos são **originais**, num ramo separado. A integração só acontece depois da aprovação do proprietário, e quem
integra é o desenvolvedor.

| Item | Valor |
|---|---|
| nome técnico do papel | o ofício: `ferreiro`, `herbalista`, `comerciante`, `cacador`, `guardiao_de_trilhas` (ou um ofício novo, junto com o conjunto) |
| id e pasta | `characters/npc/<id>/` (gerado por um módulo em `tools/`, como `npcs_oficios_e.py`; nunca editado à mão) |
| proporção | NPC da direção E: 2,0–3,0 cabeças, rosto em placa plana, olho livre em placa (`contrato-de-qualidade.md`); nada das medidas congeladas dos protagonistas |
| altura | narrativa, como os NPCs de ofício: 0,75 u por metro (Pluma 1,60 m … Bramor 2,30 m); altura pelo reamostrar do rig pronto, nunca esticando o corpo |
| rig | o esqueleto dos NPCs da vila (`npcs_vila_e.npc`: quadril 14, pescoço 29, voxel 1/40 u); `meta.escala` quando reamostrado |
| animações | as do `Animator` para NPC: `idle`, `walk` e `emote` (atuação `meta.acting`); as 4 cabeças de expressão de quem tem diálogo — `neutral`, `joy`, `determined`, `surprise` (as falas usam `"expressao"`), como os NPCs da vila e de ofício |
| ponto | o do conjunto: spawn `npc` da âncora ou uma VAGA recomendada (`pontos.vagas`: id, posição, rumo, papel, `recomendada`), como a do feirante; o desenvolvedor confere (corpo livre, conversa alcançável, porta livre, visível pela câmera de jogo) |
| gesto de ofício | opcional: o perfil `assets/anim/npc_<papel>.json` com `"attack": "oficio"` (o clipe ATTACK toca o gesto) e `"gesto": "oficio"` na entrada de `habitantes_e.json` |
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

## Rodada 12.3 — a vaga da herbalista

- **O defeito:** na vaga `herbalista_lado` (12.2, ao lado da porta), a placa do ofício do conjunto fica 0,8 u à frente
  da Índira, com a tabuleta na altura da cabeça. Vista da rua e de lado ela some, e na conversa a placa entra na frente
  da câmera. As capturas são do roteiro `tools/mundo/18-indira.txt`, semente 28.
- **Comparadas** (referencial da âncora, conjunto em 0°, 37°, 90°, 180° e 270°):

  | Vaga | Folga do corpo | Porta | Fala | Nas capturas |
  |---|---|---|---|---|
  | `herbalista_lado` (0,69; 2,56; rumo 0) | 0,60 u | 0,71 u, passa com folga | alcançável | atrás da placa: some da rua e de lado |
  | **`herbalista_varal`** (2,31; 1,56; rumo 20) | 0,48 u | 2,45 u | alcançável | inteira da rua, dos dois lados e na conversa |
  | canteiro (−1,5; 2,6; rumo −15) | 0,24 u | 1,52 u | alcançável | atrás da cerca; espaço do gesto 0,5 u |

- **Ressalva:** a conta por raios (`rayClear`) dava 5/5 de visibilidade para as três; a caixa de colisão da placa não
  representa a tabuleta pendurada, e quem decidiu foram as capturas.
- **A escolhida:** a do varal (`tools/conteudo_construcoes.py`, recomendada). A do lado fica registrada, não
  recomendada.
- **O efeito no gerador:** a v2 muda só no ponto da herbalista (88 de 200 regiões). Em 3 de 200 (52, 54, 138) muda
  também a decoração miúda em volta (flores, capim, samambaia), porque o espalhamento evita o lugar dela. Construções,
  POIs, inimigos e missões ficam iguais; `regioes-v2.txt` foi refeito (só a 2048 mudou entre as 8).
