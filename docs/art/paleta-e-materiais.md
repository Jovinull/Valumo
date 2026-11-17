# Paleta semântica e materiais (direção E)

Cores reproduzíveis por papel. Fonte no código: `tools/charlib_e.py`, `tools/chars_e.py`,
`tools/env_e.py` (`E_PALETTE`), `tools/env_b.py` (cores herdadas), terreno em
`viewer/.../World.java` (`TerrainStyle "e"`). Cada cor-base registrada com `P()` ganha uma rampa:
**sombra** = multiplicar por roxo-vinho (0,78 0,66 0,82); **luz** = somar amarelo-pêssego; tom
**profundo** = sombra × 1,7 (só em tecido e pele; nunca em cabelo).

## Ambiente

| Papel | Base | Rampa (luz / base / sombra) | Observação |
|---|---|---|---|
| Folhagem — carvalho | `#76ad47` | `#9bc064` / `#76ad47` / `#617e3d`, profundo `#4c4f33` | 4 tons por normal, manchas de 4 voxels |
| Folhagem — outono (acento) | `#e98f3a` | — | 1 árvore em ~9 |
| Folhagem — pinheiro / bétula / marco / arbusto | `#3f8a62` / `#b8cb55` / `#68a64a` / `#6a9f47` | — | |
| Grama (tufos) / musgo | `#86b04a` / `#92b54e` | | mais verde que a B (`#a9b854` virava palha) |
| Gramado (terreno) | `#7eab58`, `#72a051`, `#8bb65f`; seco `#9fbf66` | manchas a 1/5 u | floresta `#66924f` `#5e8949` |
| Caminho / borda gasta | `#dcbd8c` `#d3b283` / `#c9bc86` `#b8bd7c` | | faixa de transição de 0,1–0,2 u |
| Terra (lateral) / rocha | `#a47a58` / `#a89da0` | | |
| Casca | `#8f5d40` `#6f4336`; bétula `#f1e6cf` | | estrias verticais |
| Pedra (lilás-quente) | `#b8aaa6`, escura `#9a8a90`, clara `#d2c6bc` | | nunca cinza neutro |
| Madeira | `#b07a48` `#84533a` `#d3a066`; enxaimel `#7a4a3a` | | |
| Estuque / telha | `#f4e4c4` / `#cb553f` (linha `#a2403b`) | telha: `#db8160` / `#cb553f` / `#9e3834` | sem manchas aleatórias |
| Porta / veneziana | `#3f8a8c` `#2f6a74` | | |
| Palha | `#e0b057` `#bd8a44` | | |
| Água | `#63b2c6` | | material água |

## Personagens

| Papel | Valor | Rampa |
|---|---|---|
| Pele / morena / clara | `#f6c9a0` / `#e8b185` / `#f9d8ba` | `#f9d7b1` / `#f6c9a0` / `#c08583` (pele com sombra a 55%) |
| Blush / boca / boca interna | `#f2988a` / `#a9524a` / `#7a3440` | |
| Cílio / pupila / brilho | `#3a2238` / `#2a1c34` / `#fffaf0` | ameixa no lugar do preto |
| Couro / escuro / camurça | `#9a6238` / `#6e4130` / `#c98f55` | |
| Creme (debruns) | `#f3e3c0` | |
| Aventureiro | túnica `#3a958a`, cachecol `#eeaa3a`, calça `#6b4a5e`, cabelo `#c2582c`, íris `#3d9c78` | túnica `#75b09e`/`#3a958a`/`#2d6271` |
| Exploradora (C3, rodada 03) | capuz/capinha azul-petróleo `#2f6f73`/`#24575c`, faixa terracota `#c8643c`, túnica `#f0dcae`, perneira `#8c5340`, cabelo `#e3a64a`, bússola bronze `#c08a3e` com mostrador `#7fe0d0`, íris `#3d8fd8` | rodada 02: capuz `#6f9a44`/`#557a38`, forro `#3f5f2e` |
| Maga | manto `#7a4a8c`/`#5a3670`, ouro `#ecb84c`, cabelo `#e2d4f2`, meia `#4a3058`, íris `#8a58c8` | |

## Criaturas (rodada 03)

| Criatura | Valores |
|---|---|
| Pompom | pelo `#f2dcb2`, barriga `#fbefd6`, ferrugem `#c66a3c`, rosa `#f2a294`, pé `#9e5a3a`, broto `#7cb04a` |
| Lumi | luz quente `#ffd27a`/`#fff2c8`/`#ffb05a` (emissivo), pétalas `#4fbf9c`/`#2f8a78` com ponta `#f08a7a` |
| Fungo | corpo `#ecdcb8`, chapéu `#b3475c` com pintas `#f6e6c6`, lamelas `#d9b48e`, cipó `#4f6b3e` |
| Tortuga | pele `#a8b574`/`#8a9a62`, barriga `#eadfb4`, carapaça S3 `#9a6a52` (costura `#734a3e`), borda `#ecd29c`, fungo-coral `#e8835a`/`#f4b07a`, cápsulas `#9ff0c8` (emissivo); S2 ardósia `#8b8597` |

## Destaques e magia

| Papel | Valor | Material |
|---|---|---|
| Coral / mostarda / rosa / amarelo / azul-flor | `#e2644c` / `#eeaa3a` / `#f2a0a6` / `#f4c653` / `#8098e0` | fosco |
| Ouro / ouro escuro | `#f2c14e` / `#c98f3a` | metal |
| Aço / escuro / fio | `#d2d8e2` / `#9aa4b8` / `#eef1f4` | metal |
| Cristal / núcleo | `#7ff0dc` / `#e6fff8` | emissivo |
| Luz habitada / fogo | `#ffd77a` `#fff0c0` / `#ff9340` | emissivo |

## Materiais

| Material | Faixa de paleta `.vox` | Shader | Regra de uso |
|---|---|---|---|
| fosco | 1–175 | difusa + ambiente + preenchimento | padrão |
| metal | 176–199 | especular + reflexo do céu (força 0,30 na E) | lâmina, guarda, fivela, ferragem; nunca grande área clara |
| emissivo | 200–219 | ignora sombra; ×2,2 na E; alimenta o bloom (limiar 1,3) | ponto focal: ≤ 3% das faces de um modelo |
| folhagem | 220–249 | vento afim + translucidez 0,22 | tudo que balança; o prop declara `affineWind` |
| água | 250–255 | ondas em grade, Fresnel | lago |

## Regras

- 3–5 matizes por modelo; 1 destaque por personagem; sombra fria/roxa, luz quente.
- Sem preto ou branco puros em área > 4 voxels (exceção: brilho do olho, 1 voxel).
- Azul saturado só em íris, magia e flores.
- Uma cor nova precisa de papel nesta tabela; se não couber, use a mais próxima.
