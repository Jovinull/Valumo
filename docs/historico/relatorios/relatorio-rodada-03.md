# Relatório da rodada 03 — polimento artístico e consolidação da direção E

Data: 16/11/2026. Máquina: i5-1335U + Iris Xe (Mesa 26.2.6), Fedora 45. Entregas em
`output/visual-review/round-03/`; estudos em [../experiments/rodada-03-estudos.md](../experimentos/rodada-03-estudos.md).
Nenhum commit foi feito. **As escolhas abaixo são propostas: a aprovação artística é do proprietário.**

## 1. Rodada 02 × rodada 03 (o que mudou)

| Área | Rodada 02 | Rodada 03 | Evidência |
|---|---|---|---|
| Aventureiro | topete com 2 pontas na coroa (antenas de costas) | topete-onda que termina em cacho (H2), rosto `tilt` + meio sorriso | `personagens/antes-depois-*.png` |
| Exploradora | capuz verde pontudo levantado | capuz azul-petróleo abaixado no ombro, faixa terracota, pena, bússola (C3), flecha | idem + `estudo-capuz-exploradora.png` |
| Maga | aba pesada colada ao cabelo, cajado de meia-lua | aba menor com avesso escuro (M1), cajado de galhas em Y, braço aberto em repouso | idem + `estudo-chapeu-maga.png` |
| Corpo | membros inteiriços | cotovelo e joelho, IK de pé, velocidade casada com a passada | `animacoes/`, `videos/` |
| Relevo | T0 (curvas de nível) | T1 (limiar irregular); T3 com chanfro estudado e rejeitado como padrão | `terreno/` |
| Carvalhos | novelos + tufos (encaroçado) | massas achatadas com avesso escuro e franja de cachos (O3) | `vegetacao/` |
| Criaturas | só o pompom refeito | lumi novo, fungo refeito a 1/32, tortuga S3, olhos de bicho no pompom | `criaturas/` |
| Fim de tarde | verdes oliva | verde preservado por luz (sem saturar) | `iluminacao/tarde-antes-depois.png` |
| AA | nenhum na PADRÃO | FXAA na PADRÃO | `iluminacao/antisserrilhado-*.png` |
| Defeitos técnicos | pontos claros no gramado | margem anti-fresta | ADR 0010 |

Antes × depois de cena com as mesmas câmeras da rodada 02: `comparacoes/antes-depois-*.png`.
Cena-objetivo com os três heróis: `comparacoes/cena-equipe.png`, `cena-equipe-tortuga.png`.

## 2. Protagonistas

Proporção preservada (V2 aprovada): aventureiro 2,44 cabeças / 1,18 u / 6 092 faces (R02: 5 902);
exploradora 2,44 / 1,23 u / 6 556 (R02: 2,53 / 6 198); maga 2,53 / 1,25 u / 7 666 (R02: 7 974).
Todos ≤ 1,25 u e dentro de 2,35–2,60 (teste `consolidatedHeroesFollowTheIdentity`). Silhueta isolada
(fundo uniforme, sem chão nem sombra, pés visíveis) de frente, perfil e costas:
`personagens/herois-silhueta-frente-perfil-costas.png`; escala de exploração **renderizada** em
480 × 200 e ampliada: `herois-tamanho-exploracao.png`, `herois-silhueta-tamanho-exploracao.png`.
Botas, mãos e mochila continuam legíveis nessa escala; as mãos viram 2–3 pixels (aceitável no
gênero). Rostos: 4 expressões × 3 heróis em close e em escala de exploração; na escala de
exploração os olhos e a boca ainda leem, as sobrancelhas quase não.

## 3. Cabelo, capuz e chapéu

- **Cabelo (4 opções, só o cabelo muda):** R02, H1 natural, **H2 topete refinado (proposta)**, H3 vento.
  H2 mantém a assinatura e elimina as "antenas"; H1 perde a assinatura; H3 é ruidosa de costas.
- **Capuz (4 opções):** C0 (R02), C1 refinado, C2 folha azul-petróleo, **C3 capuz abaixado (proposta)**.
  C1/C2 continuam no arquétipo "capuz + arco"; C3 muda a silhueta e mostra o cabelo.
- **Chapéu (3 opções) + cajado:** M0 (R02), **M1 aba menor com avesso escuro (proposta)**, M2 aba virada.
  Cajado de galhas: silhueta em Y legível na câmera de exploração; não passa mais atrás da cabeça.

Detalhes e tabelas: `rodada-03-estudos.md` §1–4.

## 4. Vídeos (reais, MP4 + GIF, 30 fps)

`videos/`: por herói `caminhada`, `corrida`, `acao` (espada / arco com flecha / cajado) e `repouso`;
lado a lado `herois-caminhada`, `-corrida`, `-golpe`, `-emote`, `herois-r02-r03-caminhada`;
exploração `exploracao-caminhada`, `-corrida` (`move=1`, velocidade da passada), `exploracao-equipe`;
`criaturas`, `criaturas-no-cenario`. 22 vídeos. Quadros conferidos: a flecha aparece ao puxar e some
ao soltar; a maga ergue e aponta o cajado; o pé de apoio fica parado na caminhada. **Ressalva:** a
prancha `animacoes/pes-em-terreno-irregular.png` é evidência fraca (vista de trás); falta um vídeo de
perfil numa encosta.

## 5. Relevo (T0–T4)

Mesmas câmeras, luz, casa, vegetação e herói: `terreno/comparacao-{encosta-norte,casa,cena-panorama,cena-rumo-a-casa}.png`.
**Proposta: T1** (`World.E_TERRAIN = 1`): bordas de degrau irregulares, custo zero, compatível com
chunks (função de posição). T2/T4 têm intenção geológica, mas as faixas de pedra repetem.
**O T3 foi a primeira escolha e foi revertido:** as capturas que o justificavam não mostravam o chanfro
(bug de ordem de vértices → culling). Com o chanfro visível, apareceram "dentes" em todo degrau em
zigue-zague (`terreno/chanfro-dentes.png`); exigindo trecho reto de 3 colunas os dentes quase somem,
mas o ganho sobre o T1 fica só nas listras dos morros distantes, por +0,5 ms, +20 mil triângulos e
+44 MB de heap. Continua disponível em `terrain=3`.

## 6. Árvores

Carvalho de referência em três estudos (O1 novelos lisos, O2 novelos + tufos, **O3 massas com avesso
escuro e franja de cachos — proposta**), aplicado aos 4 carvalhos; pinheiro, bétula e árvore-marco
mantêm forma própria. Fusão de faces preservada (vento afim). `vegetacao/estudo-carvalho-*.png`,
`floresta-antes-depois.png`, `panorama-antes-depois.png`.

## 7. Bestiário

| Criatura | Nível | Faces R02 → R03 | Mudança |
|---|---|---|---|
| Pompom | 2 | 2 744 → 2 744 | preservado; olhos de bicho em vez da íris de herói |
| Lumi | 1 | 1 668 → 1 728 | refeito: broto-espírito flutuante, luz quente sutil, pétalas, antena |
| Fungo | 1 | 2 640 → 2 362 | refeito a 1/32 u (era 1/28 herdado), chapéu-capuz, postura inclinada |
| Tortuga | 3 | 8 862 → 8 690 | carapaça S3: fungo-coral, frondes em espiral, cápsulas de luz, marco — **sem árvore** |

Carapaça em 3 estudos (S1 refinado, S2 mineral/musgo, S3 ecossistema próprio): `criaturas/estudo-carapaca-*.png`.
Defeito aberto: o rosto do fungo fica quase escondido sob a aba em câmera baixa (`criaturas/antes-depois.png`).

## 8. Presets de luz

Dia continua o padrão. Tarde corrigida pela luz (não pela saturação): matiz mediano das copas 69,8°
→ 86,5°, gramado 81° → 88°, saturação 0,50 → 0,45. Neutra inalterada. Mesmo enquadramento:
`iluminacao/presets.png`, `tarde-antes-depois.png`.

## 9. Desempenho

1920 × 1080, E, 3 × 300 quadros + aquecimento, **mesmo binário**, execuções intercaladas; R02 =
assets congelados da rodada 02 + relevo T0 + sem FXAA. Máquina ocupada (Roblox/Sober, RDD Pro,
Firefox): os números valem entre si, não como absolutos. Tabela completa: `performance/resumo.md`.

| Configuração | média ms (FPS) | p95 ms | 1% low FPS | tri cena | MB vértices |
|---|---|---|---|---|---|
| R02 PADRÃO (1ª / repetição) | 11,87 / 11,76 (84 / 85) | 14,37 / 14,49 | 63,1 / 60,4 | 369 370 | 48,6 |
| R03 PADRÃO + FXAA (1ª / repetição) | 11,95 / 11,54 (84 / 87) | 14,47 / 14,41 | 63,1 / 61,7 | 367 158 | 49,1 |
| R03 PADRÃO sem FXAA | 11,89 (84) | 14,55 | 61,1 | 367 158 | 49,1 |
| R03 PADRÃO, relevo T3 | 12,44 (80) | 14,84 | 62,5 | 386 772 | 52,0 |
| R02 ALTA (SSAA 1,5×) | 21,70 (46) | 25,31 | 37,0 | 369 370 | 48,6 |
| R03 ALTA (SSAA 1,5×) | 21,68 (46) | 24,78 | 37,0 | 367 158 | 49,1 |

Conclusões: a rodada 03 **não mudou o custo** (diferença dentro da deriva de ~0,4 ms entre a 1ª
execução e a repetição); o FXAA custa menos que o ruído da medição e é ~10 ms mais barato que o SSAA
1,5×; fusão exata de faces mantida. 156 draws (157 com o chanfro).

## 10. Defeitos

Corrigidos: antenas do aventureiro; arquétipo do capuz verde; aba colada ao cabelo; cajado atrás da
cabeça e inclinando para dentro; sobrancelha `thin` sob a franja; pálpebra "sonolenta"; arco sem flecha;
pé deslizando; pé atravessando encosta (degraus pequenos); tarde oliva; silhueta com chão preto;
pontos claros de T-junction; chanfro invisível (bug); dentes do chanfro (contornado); nativo sem AA.

Abertos: rosto do fungo sob a aba; degrau > 0,2 u deixa um pé no ar; morros distantes ainda
listrados; lumi fora do motivo P1; evidência fraca dos pés na encosta; fonte do HUD sem acentos;
sombra sem cascatas; água plana. Lista completa: [problemas-conhecidos.md](../../validation/problemas-conhecidos.md).

## 11. Documentação

Atualizados: `visual-identity.md` (novo §0 invariantes × parâmetros flexíveis), `especificacao-de-personagem.md`,
`sistema-de-proporcoes.md`, `guia-de-animacao.md`, `regras-de-vegetacao.md`, `paleta-e-materiais.md`,
`presets-graficos.md`, `pipeline-de-producao.md`, `contrato-de-assets.md` (§8),
`checklist-de-aceitacao.md`, `problemas-conhecidos.md`, `README.md`, `CLAUDE.md`, `.claude/skills/*`.
Novos: `assinatura-visual.md` (3 propostas + revisão de semelhança), `experiments/rodada-03-estudos.md`,
ADRs 0009 (articulações + IK) e 0010 (FXAA + margem anti-fresta), este relatório.

Assinatura visual (≤ 3, aguardam decisão): P1 "sementes de luz" verde-água (recomendada), P2 "espiral de
broto", P3 "borda em folha". Originalidade (avaliação artística, não jurídica): exploradora e tortuga
eram os maiores riscos de semelhança; C3 e S3 afastam os dois dos arquétipos conhecidos.

## 12. Comandos

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
python3 tools/build_assets.py                      # inclui chars_e_study (estudos r3)
./gradlew test :viewer:installDist                 # 19 testes, 0 falhas
viewer/build/install/viewer/bin/viewer --review3   # pacote completo (≈ 110 imagens)
tools/anim.sh output/tmp/x "mode=char,style=e,char=1,clip=attack,yaw=40" 96 30
viewer/build/install/viewer/bin/viewer --bench "1920x1080,style=e,runs=3,frames=300,quality=padrao"
JAVA_OPTS=-Dvx.assets=output/visual-review/round-03/snapshot-r02 \
  viewer/build/install/viewer/bin/viewer --bench "1920x1080,style=e,runs=3,frames=300,terrain=0,fxaa=0"
```

## 13. Arquivos para revisão externa

`output/visual-review/round-03/`: `destaques-rodada-03.zip` (≈ 20 imagens-chave + 4 vídeos),
`capturas-rodada-03.zip` (todas as imagens), `videos-rodada-03.zip` (MP4 + GIF),
`documentacao-rodada-03.zip` (docs, skills, ADRs, CSV de desempenho). Snapshot da rodada 02 para
comparação: `round-03/snapshot-r02/`. A rodada 02 não foi alterada.

## 14. Avaliação crítica de maturidade

- **Personagens: maduros para produção de mais NPCs.** Proporção, rosto, cabelo e rig articulado têm
  regras escritas, testes e estudos. O que falta é validação humana do C3 e do M1 (gosto).
- **Animação: boa para protótipo, não para jogo.** IK de pé e articulações resolvem o deslize e o
  arco, mas não há transições (parar/virar), nem IK de mão, nem combate real.
- **Mundo: estável, sem salto de qualidade nesta rodada.** O relevo continua o ponto mais fraco ao
  longe; a tentativa de chanfro mostrou que o problema pede outra técnica (ex.: variação de cor por
  altura ou patamares autorados), não mais geometria.
- **Criaturas: coerentes, mas finas.** Quatro criaturas com níveis claros; o fungo precisa de mais um
  ajuste de rosto.
- **Processo: o maior aprendizado foi de verificação.** O chanfro passou duas decisões sem ser
  renderizado; agora a skill de QA manda medir diferença de pixels entre variantes antes de julgar.
- Recomendação: aprovar ou ajustar H2, C3, M1, O3, S3 e T1; decidir a assinatura P1; depois disso a E
  pode ser congelada como identidade e a próxima rodada pode sair da arte para o jogo.
