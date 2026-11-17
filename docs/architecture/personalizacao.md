# Personalização modular de personagens — estudo e proposta (rodada 06.1)

Objetivo futuro: o jogador escolhe a aparência do seu pequeno aventureiro (pele, cabelo, olhos, sobrancelhas,
chapéus, capuzes, roupas, acessórios, equipamentos) sem um modelo completo por combinação, e os três
protagonistas continuam existindo como **presets** idênticos às referências aprovadas.

**Não existe criador de personagens.** Este documento parte do código real e marca cada item:

| Marca | Significa |
|---|---|
| **[IMPLEMENTADO]** | existe e é usado pelo jogo/visualizador hoje |
| **[VALIDADO]** | validado experimentalmente na prova técnica `tools/custom_e.py` (testes em `ModularTest`) |
| **[PLANEJADO]** | proposta; nada no código ainda |
| **[NÃO IMPLEMENTADO]** | fora do escopo até decisão contrária |

## 1. O que o código tem hoje

| Peça | Situação real | Onde |
|---|---|---|
| Esqueleto bípede articulado | partes rígidas com pivô (`torso, head, arm/farm, leg/shin`), hierarquia por `parent`; mesmo esqueleto para os três heróis (constantes de `charlib_e`); NPCs usam outro (`npcs_e`) **[IMPLEMENTADO]** | `voxlib.Rig`, `rig.json`, `Animator.biped2` |
| Animação por nome de parte | o `Animator` anima o que existir; partes desconhecidas seguem o pai rigidamente; `tail`, `hat`, `pack`, `scarf`, `skirt_*` têm movimento secundário **[IMPLEMENTADO]** | `Animator.secondary`, `applySecondaryLag` |
| Expressões | variantes da parte `head` (`alts`: `joy`, `determined`, `surprise`), troca de malha inteira **[IMPLEMENTADO]** | `rig.json` → `alts` |
| Cabelo | **fundido na cabeça** e em cada expressão (`head_with_expressions` faz `skull + rosto + cabelo`) — não troca sem regerar a cabeça | `charlib_e.head_with_expressions` |
| Chapéu | a maga tem `hat` como **parte própria filha da cabeça**; o cabelo dela foi desenhado sabendo do chapéu | `chars_e.mage` |
| Capuz | o da exploradora é **fundido** na cabeça (`extra=hood`) | `chars_e.ranger` |
| Cores | gravadas na paleta de cada `.vox`; não há troca de paleta em tempo de execução | `voxlib.write_vox`, `VoxModel` |
| Mãos e itens | itens são partes filhas de `farm_r`/`farm_l`; pegada e golpe por perfil (`assets/anim/*.json`) | `Choreo`, `weaponProfile` |
| Volumes de colisão | cápsulas por parte em `meta.volumes` (validação de golpe) | `charlib_e.vol`, `WeaponCheck` |
| Atuação por personagem | postura de repouso e andamento do gesto (`meta.acting`) **[IMPLEMENTADO 06.1]** | `Animator`, `Choreo.withTempo` |
| Parâmetros de rosto | `FACE_DEFAULT`/`acting` no gerador (olho, sobrancelha, boca, sardas, atuação) | `charlib_e.paint_face`, `act_face` |

Conclusão da auditoria: **os geradores já são paramétricos** (cabelo `h1/h2/h3/r02`, chapéus `m0/m1/m2`,
capuzes `c0–c3`, rostos por dicionário), mas a **saída é monolítica por personagem** (cabelo e capuz
fundidos na cabeça, cores no arquivo). A personalização precisa separar componentes na saída, não
reescrever os geradores.

## 2. Modelo de dados

### 2.1 Componente **[PLANEJADO; formato validado na prova]**

Um componente é um conjunto de partes voxel com um **slot**, um **id estável** e regras:

```json
{
  "id": "cabelo.topete", "slot": "cabelo", "versao": 1,
  "esqueleto": "heroi-e",                 // só encaixa em rigs com esse esqueleto (pivôs de charlib_e)
  "partes": [{"nome": "hair", "pai": "head", "arquivo": "hair.vox"}],
  "variantes": {"sob_chapeu": "regra:corte_por_colunas", "sob_capuz": "hair_hood.vox"},
  "cores": {"cabelo": "rampa"},           // canais recoloríveis
  "ocupa": ["cabeca.topo", "cabeca.nuca"] // regiões do corpo que o componente cobre
}
```

### 2.2 Slots

| Slot | Partes | Notas |
|---|---|---|
| `corpo` | `torso`, `arm/farm`, `leg/shin` (pele, base) | define o esqueleto |
| `rosto` | `head` + `alts` (expressões) | crânio + placa do rosto; forma do olho, sobrancelha, boca, atuação |
| `cabelo` | `hair` (filha de `head`) | variantes por cobertura de cabeça |
| `cabeca` | `hat` / `hood` / `helm` (filha de `head`) | regra de ocultação do cabelo |
| `torso_roupa`, `ombros`, `bracos`, `maos`, `pernas`, `pes`, `costas` | partes ou sobreposições por região | ver §4 |
| `mao_direita`, `mao_esquerda` | itens filhos de `farm_*` | perfil de animação próprio |

### 2.3 Regiões do corpo e ocultação **[PLANEJADO]**

Regiões: `cabeca.topo`, `cabeca.nuca`, `rosto`, `torso`, `ombros`, `bracos`, `maos`, `pernas`, `pes`,
`costas`. Cada peça declara `ocupa` (onde aparece) e `oculta` (o que esconde). Regras, em ordem:

1. Peça que **substitui** remove a base daquela região (ex.: luva substitui `maos` da roupa base).
2. Peça que **oculta** pede ao componente oculto a **variante** declarada; sem variante → incompatível.
3. Nunca desenhar duas peças no mesmo volume; nunca aumentar uma peça para "cobrir" clipping.

## 3. Cabelo × chapéu, capacete e capuz

| Cobertura | Regra | Situação |
|---|---|---|
| sem chapéu | cabelo completo | **[VALIDADO]** |
| chapéu cosmético (aba) | variante `sob_chapeu`: o cabelo perde só o que está nas colunas cobertas pelo chapéu, da aba para cima; o que sai por baixo da aba continua | **[VALIDADO]** (`c3`, `c4`) |
| capuz | variante `sob_capuz` desenhada (franja e mechas da frente ficam, volume de trás some); capuz da exploradora hoje é fundido | **[PLANEJADO]** |
| capacete aberto | `sob_capacete_aberto`: só franja/mechas laterais desenhadas para sair do capacete | **[PLANEJADO]** |
| capacete fechado | substitui `cabelo` inteiro (oculta) | **[PLANEJADO]** |
| equipamento que substitui cabelo | declara `oculta: ["cabelo"]` | **[PLANEJADO]** |
| cabelo com mecha incompatível (ex.: topete alto × capacete) | variante desenhada ou incompatível — **nunca** distorcer | regra |

A variante por corte de colunas é segura **só** para chapéus de aba sobre cabelo curto/médio. Achado da
prova: nas costas fica uma fresta fina entre a aba e o cabelo cortado. Cada chapéu precisa declarar seu
**assento** (altura da aba sobre o crânio) para o cabelo `sob_chapeu` preencher até ela.

## 4. Matriz de compatibilidade (documental)

Legenda: **D** compatível diretamente · **V** mediante variante · **O** ocultar componente · **S** substituir
componente · **X** incompatível.

| Combinação | Regra | Observação |
|---|---|---|
| cabelo curto/médio × chapéu de aba | V (`sob_chapeu`) | validado na prova |
| cabelo com topete alto × chapéu de aba | V | o corte remove o topete (validado em `c3`) |
| cabelo longo × chapéu de aba | V | o comprimento de trás sai por baixo da aba |
| cabelo × capacete aberto | V (`sob_capacete_aberto`) | variante desenhada por estilo |
| cabelo × capacete fechado | O | cabelo oculto inteiro |
| capuz × cabelo | V (`sob_capuz`) | franja fica, volume de trás some |
| capuz × chapéu | X | dois itens de `cabeca` |
| orelhas de fantasia × chapéu/capuz | X ou variante de chapéu com furos | decidir por item |
| roupa base × armadura de torso | S | armadura substitui `torso_roupa` |
| manga longa × luva | V | punho da manga em variante curta sob a luva |
| manga × braçadeira | D | sobre a manga, sem ocupar a mão |
| calça × bota alta | V | cano da calça recolhido (já é assim nos heróis) |
| calça × bota baixa | D | |
| mochila × capa curta | X | as duas ocupam `costas` (capa longa é proibida pela direção E) |
| mochila × aljava | X ou V | aljava no quadril como variante |
| arma × rig do herói | D | mesmo esqueleto; perfil de animação por arma |
| arma × rig de NPC | X | esqueleto e perfis diferentes (NPC tem `npc_vila`) |
| mecha longa presa ao tronco (`lock`) × armadura de torso | V | a mecha precisa de variante sobre a armadura |

## 5. Presets

**Preset = configuração visual, não classe.** O preset é um documento de aparência como qualquer outro,
com o rótulo de origem. A arma inicial **não** vem do preset (o estilo de combate é escolha separada).

- **[VALIDADO]** O aventureiro recomposto por componentes (`c0_preset`) é **idêntico voxel a voxel** ao
  canônico nas 4 expressões; as outras 12 partes são idênticas byte a byte (`ModularTest`).
- Captura: 0,28% dos pixels diferem (máx. 193/255) — costura entre as malhas `head` e `hair` (faces internas
  agora desenhadas, contorno). Para os presets ficarem **pixel-idênticos** às referências, o jogo deve
  **fundir as partes rígidas do mesmo pai numa malha só** depois de compor (ver §8).
- Exploradora e maga ainda não foram recompostas: o capuz da exploradora é fundido na cabeça e precisaria
  virar componente (`sob_capuz`) primeiro.

## 6. Persistência **[PLANEJADO; formato usado pela prova]**

O save guarda **ids e parâmetros**, nunca geometria:

```json
{
  "contrato": "valumo.aparencia/1",
  "versao": 1,
  "identidade": {"id": "b7c1…", "nome": "Lia"},
  "origem": {"preset": "aventureiro"},
  "aparencia": {
    "pele": "pele.03",
    "cabelo": {"estilo": "natural", "cor": "#5b3a2c"},
    "olhos": {"forma": "tilt", "cor": "#3d9c78"},
    "sobrancelha": "grossa",
    "atuacao": "bold"
  },
  "equipamento_visual": {"cabeca": "chapeu_maga", "costas": null, "mao_direita": "espada.curta"}
}
```

- **Identidade** (id, nome) separada de **aparência** (cosmética) e de **equipamento visual** (o que está
  vestido); `origem.preset` registra de onde partiu.
- Só o que difere do preset precisa ser salvo (a prova grava assim e resolve com `resolve()`).
- **Migração:** `contrato` + `versao`; cada versão nova traz uma função `migrar_N_para_N+1` pura (renomear id,
  trocar estilo removido pelo substituto declarado, preencher campo novo com o valor do preset). Ids nunca
  são reaproveitados para outra coisa; ids removidos ganham um `substituto`.

## 7. Integração com rig e animação

- O componente declara o **esqueleto** em que encaixa; o compositor recusa outro (heróis × NPCs).
- Partes novas entram com nome conhecido pelo `Animator` (`hair`, `hat`, `hood`, `lock`…) ou rígidas
  seguindo o pai. **[VALIDADO]** a combinação mais carregada (`c4`: outro cabelo, outra cor, chapéu) passa no
  validador de golpe/locomoção sem mudar perfis.
- Volumes de colisão (`meta.volumes`) continuam os do corpo base; chapéus grandes precisariam de volume
  próprio para o validador de oclusão.
- Itens de mão continuam com perfil por arma; trocar aparência não troca perfil.

## 8. Adaptações necessárias antes do criador de personagens

1. **Separar cabelo da cabeça na saída dos geradores** (como a prova faz) para os três heróis; capuz da
   exploradora como componente.
2. **Composição em tempo de execução** (Java): carregar componentes, aplicar regras de ocultação/variante e
   **fundir as partes rígidas do mesmo pai** numa malha (remove as faces internas: a separação levou o
   aventureiro de 6 286 para 7 996 faces expostas, +27%) — **[PLANEJADO]**.
3. **Recolorir por canal**: a paleta precisa marcar canais (`pele`, `cabelo`, `olhos`, `roupa1`…) para trocar
   cor sem regerar; hoje a prova regera o `.vox` — **[PLANEJADO]**.
4. **Tom de pele**: `paint_face` e sombras derivam de `SKIN` fixo; precisa receber a pele como parâmetro.
5. **Assento de chapéu** declarado por item (fresta da prova).
6. **Roupas por região** com variantes de manga/cano.
7. QA automático por combinação (abaixo).

## 9. Critérios de QA para componentes

- Preset recomposto idêntico ao canônico (voxels) e referências canônicas pixel a pixel.
- Nenhum voxel compartilhado entre componentes; variante só **remove** (nunca move/estica) — `ModularTest`.
- Proporção (`headsTall`) do corpo base preservada; chapéu fora da medida.
- Validador de golpe/locomoção e oclusão para cada combinação nova de cabeça/mão.
- Captura frente/costas/rosto de cada combinação + silhueta.
- Sem fragmentos (`asset_audit.py`).

## 10. Prova técnica (rodada 06.1) **[VALIDADO]**

`tools/custom_e.py` (módulo `custom_e` do `build_assets`) → `assets/characters/custom/`:

| Id | Combinação |
|---|---|
| `c0_preset` | aventureiro recomposto (prova de identidade) |
| `c1_cabelo_castanho` | só a cor do cabelo |
| `c2_cabelo_natural` | estilo `natural` (h1, estudo da rodada 03) |
| `c3_chapeu` | chapéu da maga sobre o topete (`sob_chapeu`) |
| `c4_natural_castanho_chapeu` | estilo + cor + chapéu |

Capturas: `output/visual-review/round-06-1/modularidade/`. A composição é em **tempo de geração**
(Python); a de tempo de execução é o próximo passo (§8). **[NÃO IMPLEMENTADO]**: criador, inventário,
atributos de armadura, interface.
