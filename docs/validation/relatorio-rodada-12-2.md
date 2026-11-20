# Relatório da Rodada 12.2 — Integração artística, compatibilidade de mundos experimentais e fechamento da v2

**Situação:** entregue para o teste físico no ramo LOCAL `feature/world-generation-v2`. São 63 commits sobre o
relatório da 12.1 (`8f2c3eb`): os 25 do artista (PR #8, até `f92e6a2`), o merge que os traz e 37 desta rodada. São 175
sobre a `main` `4cbde86`.

- **Não feito:** push, PR, merge na `main`, tag e publicação da v2. O PR #8 e o ramo do artista não foram tocados.
- **Não criados:** v3, escalada, planador, inventário, comércio, ferraria, NPC novo, missão nova.

**Pacote:** `output/visual-review/round-12-2/` (LEIA-ME, roteiro de teste físico, ZIP sem os vídeos), reproduzível com
`tools/review122.sh`.

## 1. Saves: o mundo RUINAS

**Diagnóstico** (`SaveGame.read/parse`, `QuestSystem.fromJson`):

- **O que é avaliado:**
  - o formato e a versão;
  - o bloco `mundo` (semente, gerador, versão, tentativa, região);
  - os vínculos da missão;
  - o progresso (missões, objetos, descobertas, inimigos).
- **A falha:** todo motivo de recusa virava `SaveGame.Invalid`, e o `read` sempre copiava o arquivo para `.invalido`.
- **O caso do RUINAS:**
  - o save é íntegro: JSON e formato certos, o mesmo mundo e os mesmos vínculos;
  - a única recusa era "descoberta desconhecida: desc.r0.ruina.0";
  - a causa: a região da semente 31337 mudou na 12.1, e a casa virou torre antes da publicação da v2;
  - o resultado: o leitor tratava como corrompido.
- **Versão futura:** um save de formato futuro também ia para `.invalido`.

**Estratégias avaliadas:**

| | O que seria | Decisão |
|---|---|---|
| A. reproduzir a composição antiga | congelar o gerador da rodada 12 com o perfil e os modelos daquela época (a casa sem degraus, as formas antigas) | inviável e desproporcional: é uma v3 escondida, para uma semente experimental |
| B. migrar | manter moedas, missão e posição, e largar a lareira | a lareira não tem equivalente na torre, a vila inteira mudou, e a região muda de novo nesta rodada (ruínas novas): seria migrar para um alvo móvel e inventar progresso |
| **C. incompatibilidade não destrutiva** | recusar sem `.invalido`, com a frase certa, sem tocar no arquivo e sem sobrescrever | **escolhida** |

**O que foi feito:**

| Caso | Agora |
|---|---|
| 1. JSON corrompido | "Jogo salvo inválido: …" + cópia `.invalido` (como antes) |
| 2. formato inválido | idem |
| 3. versão futura do formato | "Esta aventura foi salva por uma versão mais nova do jogo. Ela continua guardada como estava"; **sem** `.invalido` |
| 4. versão antiga válida | abre (v1 da vila; v2 de região) |
| 5. mundo de versão de testes que mudou | "Esta aventura é de uma versão de testes deste mundo, que mudou antes do lançamento. Ela continua guardada como estava; crie outro mundo para jogar"; **sem** `.invalido`; o detalhe técnico vai para o log |

- **Onde:** `SaveGame.Incompatible`, `QuestSystem.UnknownReference`, `RegionGenerator.published` (hoje só a v1).
- **Nada se perde:**
  - "Entrar" sempre tenta CONTINUAR um save existente: a aventura recusada nunca recomeça por cima;
  - outro mundo pode ser criado na mesma tela;
  - a faixa de aviso do menu passou a quebrar em até 3 linhas: a frase saía cortada.
- **Os seus dados** (sempre numa CÓPIA; os originais registrados por sha256 no começo e no fim):
  - o RUINAS é recusado com a frase de versão de testes;
  - nenhum arquivo muda, e nenhum `.invalido` novo aparece (`registros/ruinas-recusado-sem-invalido.png`);
  - o `aventura.json.invalido` que o binário da 12.1 criou continua lá: removê-lo é decisão sua.
- **O seu mundo "31337"** (criado depois da 12.1) também mudou de região nesta rodada (P90). Para testar, crie um mundo
  novo.

## 2. PR #8

- **Auditoria:**
  - o PR tinha 34 commits e 41 arquivos;
  - a parte aprovada são os 25 commits até `f92e6a2`;
  - o Acapela começa em `218f504`: 9 commits, 12 arquivos, incluindo linhas em `build_assets.py`, `determinismo.py`,
    `NpcClipProbe` e na skill `character-design`.
- **Integração:** por merge LOCAL de `f92e6a2` (`b7e0347`). Entram os 25 commits com os SHAs de origem (33 arquivos).
- **Fora:** o Acapela, inteiro no ramo do artista.
- **Conflito:** um só, em `ConteudoMundoTest`. As regras do PR e da 12.1 ficaram juntas.
- **Rastro para fechar o PR:** [integracao-pr8.md](integracao-pr8.md).

## 3. Moradores dos ofícios

| Ofício | Morador | Onde | Prova |
|---|---|---|---|
| ferreiro | **Brásia** | galpão da oficina, o ponto P73, de pé na laje | `CompositeCollisionTest.p73` com ela no lugar: entrar, forja, bigorna, bancada, conversar, sair (5 rumos); vídeo 01 |
| comerciante | **Quelmo** | vaga `feirante_lateral` da barraca, rumo −35° (1 voxel mais para fora que no PR: 0,58 u do poste) | `oFeiranteNaVagaLateralFicaVisivelEAlcancavel` (5 rumos: a câmera de jogo vê 5 de 5 pontos do corpo chegando pela rua e 3 de 5 na conversa; o spawn antigo, 2 de 5); vídeo 01 |
| caçador | **Arvel** | porta da cabana (≥ 0,6 u da porta, patamar plano) | `GeneratorV2Test`; vídeo 02 |
| herbalista | **Índira** (reaproveitada) | vaga nova `herbalista_lado` (o spawn do catálogo ficava a 0,40 u da porta) | `GeneratorV2Test`; vídeo 01/02 |
| guardião de trilhas | **Bramor** (reaproveitado) | o posto da torre de vigia | vídeo 02 |

- **O vínculo é por dado:** conjunto → ofício → ponto do perfil → morador. Nada de `if nome ==`.
- **Moradores comuns:** `RESIDENTS` (que a v1 usa) não mudou; os novos entram pelo perfil.
- **Missão:** os papéis deles não mudam quem dá a missão.
- **Comportamento** (só desenho, animações existentes): parado, andando, aceno ao começar a conversa e o gesto de ofício
  a cada 9 s (Brásia, Quelmo, Arvel). Sem comércio, sem ferraria, sem missão nova; falas curtas e originais.
- **Regra do corpo:** o morador de ofício fica num ponto AUTORADO, conferido à parte; para ele, a conferência usa o raio
  do corpo (0,3 u) acima dos pés, e não a margem de 0,55 u do morador posto em chão aberto. Motivo: no galpão, nenhum
  ponto cumpre 0,55 sem tomar o lugar de quem usa a forja, e a tina fica a 0,44 u da Brásia sem tocar nela (P91).
- **Várias vilas:** o perfil já lista `{ofício: [moradores]}`. O gerador nunca repete um morador na região, e nada supõe
  uma herbalista só no mundo.

## 4. As duas ruínas novas e a distribuição

- **Abóbada** (`ruin_vault`):
  - 356 formas compostas, 6 só de câmera, exatamente sobre a cobertura medida pelo PR: o teto segura a câmera e não é corpo;
  - o rasgo de luz fica aberto;
  - o morro é pisável.
- **Sítio** (`ruin_farmstead`): 88 formas; portão em arco, porteira do curral, cocho e pedra do marco alcançáveis.
- **Liberação:** as duas foram percorridas pelo `CompositeCollisionTest` em 5 rumos (entradas, interior, pontos, câmera,
  saltos).
- **Variações:** nenhuma é publicada como prop, então só a base vai ao jogo. A `formas_iguais: false` da abóbada fica
  registrada.
- **Distribuição no gerador v2** (uma ruína por região; ordem sorteada com peso, encaixe pelo terreno):
  - a abóbada só onde o terreno sobe ≥ 0,3 u para os fundos;
  - o sítio só em chão aberto (≤ 0,6 u de desnível na pegada);
  - a casa continua a mais comum.

  | Ruína | Sementes 1–200 |
  |---|---|
  | casa | 94 |
  | torre | 71 |
  | sítio | 21 |
  | abóbada | 14 |

  0 sementes recusadas.
- **Descobertas:** a abóbada (o pote entre os barris, 5 moedas) e o sítio (a pedra do marco, 0). Tipo, instância,
  descoberta e recompensa são coisas separadas ([descobertas](../architecture/descobertas.md) §2).

## 5. A câmera dentro da torre (P85)

**Medido andando**, com a física e a câmera reais (porta → centro → escada → porta → fora): a câmera fica colada (< 1,2 u)
em **52% do tempo**, em trechos de até 1,43 s (85% dentro do vão).

| Candidata | Colada | Maior trecho | Desvio de rumo |
|---|---|---|---|
| câmera de hoje | 52% | 1,43 s | até 54° |
| A: zona com braço curto e inclinação mínima, sem giro (WASD intocado) | 46–47% | 1,5 s | 26–32° |
| A: com giro | 47–49% | 1,5 s | 20–80° |
| A: câmera quase vertical (até 83°) só no vão | 38% | 1,57 s | 33° |
| A: quase vertical num raio de 3 u (inclui a porta e fora) | 26% | 0,75 s | 36° |

- **B** (parede que não segura a câmera): sem esmaecer a parede no desenho, a parede tapa o herói.
- **C** (geometria): alargar o vão muda a silhueta aprovada.

**Decisão:** nenhuma candidata resolve sem prejudicar outro cenário. A melhor corta o tempo colado pela metade, mas exige a
câmera quase vertical em volta da torre, inclusive do lado de fora. **Nada foi adotado.** A câmera continua idêntica à da
rodada 08 fora da luta. As saídas de verdade são decisões suas: um vão mais largo (arte, com capturas antes) ou um recurso
de desenho que esmaeça a parede entre a câmera e o herói.

## 6. Salto e escalada

- **O salto não mudou:** 1,0 u, ~0,6 s no ar, 85% de controle.
- **Escalada e planador:** não implementados.
- **A física de colisão ganhou um caso:** ao descer de nível ANDANDO (o morro em degraus da abóbada), o empurrão lateral é
  refeito na altura nova. O teste de "nunca dentro" achou o corpo 6 cm dentro de um degrau.

## 7. Defeitos achados e corrigidos na rodada

1. Save íntegro de região mudada e save de versão futura iam para `.invalido`.
2. A faixa de aviso do menu cortava a frase.
3. A herbalista ficava na frente da porta (spawn do catálogo nunca usado antes).
4. A Brásia era recusada pela própria laje: a conferência comparava a altura do mundo com uma altura relativa ao chão.
5. O Quelmo ficava 3 cm perto demais do poste.
6. Desceu de degrau andando → 6 cm dentro do degrau de cima.
7. As sementes de exemplo dos testes mudaram de ruína; foram escolhidas de novo pela região real.
8. Roteiros: o andador em linha reta prendia entre a oficina e a cabana. Os roteiros passam pela praça e esperam 1,4 s entre
   os E da conversa.

## 8. Números

**Testes:** **439, 0 falhas, 0 erros** (engine 45, viewer 145, game 249; 1 pulado: o gravador da referência). Rodada 12.1: 413. Entram os testes da parte aprovada do PR #8 (`MundoNpcTest`, os acréscimos ao `ConteudoMundoTest`, a sonda `NpcClipProbe`) e os novos da 12.2 (`SaveCompatTest`, o feirante na barraca, as quatro ruínas, as descobertas novas). A soma com os testes do PR não serve de critério, porque inclui o `AcapelaTest`, que ficou fora.
Esta é a suíte combinada real.

**Determinismo:**

- **v1:** 300 sementes iguais às da rodada 12, com o binário final; `regioes-v1.txt` intocado.
- **v2:**
  - 200 sementes iguais em dois processos, 0 recusadas (172 na 1ª tentativa, 23 na 2ª, 4 na 3ª, 1 na 4ª);
  - `regioes-v2.txt` refeito uma vez nesta rodada, no fim, com o motivo;
  - os rótulos novos (`"ruina-tipos"`) e os moradores não movem missão nem inimigos.

**Sementes:**

- 300 da v1 e 200 da v2 (a distribuição de ruínas e moradores);
- 16 capturadas em 20 tomadas cada: 31, 1024, 14, 77, 31337, 1, 7, 2048, 4096, 54, 28, 19, 2, 44, 101, 3;
- 9 com antes × depois (o binário da 12.1);
- 5 vídeos.

**Desempenho** (1080p, janela oculta, mudo, glFinish por quadro; os DOIS binários — 12.1 num clone compilado de `8f2c3eb` ×
12.2 — intercalados na mesma sessão, 3 repetições, mediana; `round-12-2/desempenho/resumo.md`). A máquina estava mais
lenta nesta sessão (p95 perto de 17–18 ms nos dois binários); a comparação vale porque é intercalada:

| Cenário | quadro mediana (ms) 12.1 → 12.2 | p99 | lógica | GPU soma | desenhos | formas | malhas (MB) |
|---|---|---|---|---|---|---|---|
| vila fixa | 10,25 → 10,19 | 19,29 → 19,23 | 0,28 → 0,28 | 8,70 → 8,69 | 114 → 114 | 185 | 206 (106,1) igual |
| região v1 1024 | 10,04 → 10,01 | 19,79 → 19,73 | 0,38 → 0,36 | 8,79 → 8,81 | 86 → 86 | 212 | 96 (77,2) igual |
| região v2 1024 (região nova) | 9,49 → 9,90 | 18,45 → 19,29 | 0,49 → 0,52 | 7,82 → 7,85 | 90 → 84 | 343 → 558 | 126 → 143 (87,2 → 91,7) |
| região v2 54 (abóbada, 3 moradores) | 9,99 → 10,60 | 19,96 → 21,03 | 0,54 → 0,62 | 8,16 → 8,29 | 70 → 80 | 716 → 915 | 116 → 133 (83,3 → 85,6) |
| região v2 28 (rural, 3 moradores) | 9,60 → 9,82 | 18,67 → 19,29 | 0,44 → 0,44 | 8,08 → 8,11 | 77 → 77 | 369 → 371 | 130 → 158 (88,4 → 94,6) |

- **Iguais:** a vila fixa e a v1, que não mudam de conteúdo.
- **Na v2:** +0,2 a +0,6 ms de quadro, pelo conteúdo a mais.
  - Os moradores de ofício são até 3 por vila, com rigs novos: +10 desenhos, até +6 MB de malhas.
  - As formas da abóbada (356): a lógica +0,08 ms.
  - A GPU muda +0,03 a +0,13 ms.
  - Nenhuma coleta de lixo a mais.
- **Menu:**
  - primeiro quadro: 3,74 → 3,92 s;
  - região 1024: 284 → 300 malhas (175,9 → 180,0 MB);
  - gerar a 1024: 231 → 276 ms;
  - montar: 2,42 → 2,31 s.

**Validadores e portão** (`round-12-2/validadores.txt`):

- **Armas e esquiva:** `viewer --check` TODOS OK (129 animações); interrupção fina, golpe andando e esquiva seguros;
  cabelos sem piora.
- **Oclusão:** só a exceção documentada do arco.
- **Imagem:** presets 51 pares, 0 de diferença; referências canônicas 22 equivalentes.
- **Documentação e áudio:** links 0 quebrados; áudio 47 arquivos sem alerta.
- **Compilação:** `javac -Xlint:all` 0 avisos (os dois que a rodada introduziu foram corrigidos); IDE num import limpo 0
  diagnósticos.
- **Clone limpo** (`.venv` nova): 439 testes, 0 falhas, instalação e `--check` ok; os 1754 arquivos gerados são idênticos
  aos do checkout; `formas_compostas.py` e `perfil_regiao_v2.py` reproduzem os arquivos versionados.

**Saves:**

- os 8 cenários feitos pela `main` publicada abrem iguais, sem `.invalido`;
- o `SaveCompatTest` cobre os cinco casos, o mesmo personagem em dois mundos e a preservação dos arquivos;
- os seus dados reais, em cópia: ver §1.

## 9. As 22 respostas

1. **A incompatibilidade do RUINAS foi diagnosticada?** Sim. Era um save íntegro, recusado por "descoberta desconhecida",
   porque a região da semente mudou antes da publicação. O leitor tratava toda recusa como corrupção e criava `.invalido`.
2. **Qual tratamento?** C, incompatibilidade não destrutiva: `SaveGame.Incompatible`, sem `.invalido`, com a frase de
   versão de testes, e nada é gravado por cima. A (reproduzir) e B (migrar) foram avaliadas e descartadas (§1).
3. **O save original foi preservado?** Sim, byte a byte (sha256 antes e depois; testes só em cópia). O `.invalido` antigo,
   da 12.1, não foi removido.
4. **Os mundos v1 continuam idênticos?** Sim: 300 sementes, a referência das 8, `RESIDENTS` e os saves antigos (8/8).
5. **Mudanças na v2:**
   - os cinco ofícios com morador, no ponto do perfil;
   - quatro ruínas escolhidas pelo terreno;
   - as descobertas da abóbada e do sítio;
   - o morador de pé no ponto, com gesto;
   - a referência refeita.
6. **Assets do PR #8 incorporados:** os 5 aprovados (Brásia, Quelmo, Arvel, `ruin_vault`, `ruin_farmstead`), com os 25
   commits e 33 arquivos até `f92e6a2`.
7. **Acapela ficou de fora?** Sim: nem gerador, nem build, nem mundo. Segue no ramo do artista.
8. **Brásia aparece na oficina?** Sim, no galpão, no ponto P73 preservado, de pé na laje.
9. **Quelmo aparece ao lado do balcão?** Sim, na vaga lateral (1 voxel mais para fora), visível nos 5 rumos.
10. **Arvel aparece na cabana?** Sim, na porta, sem bloqueá-la, no patamar plano.
11. **Índira e Bramor foram reaproveitados?** Sim. A Índira é a herbalista, numa vaga nova fora da porta; o Bramor
    continua o guardião.
12. **As quatro ruínas estão disponíveis?** Sim (94/71/21/14 em 200 sementes).
13. **Todas têm colisão correta?** Sim, pelas conferências: as 6 construções compostas passam no percurso, na câmera, no
    teto e no "nunca dentro". O teste físico é seu.
14. **A câmera da torre foi corrigida?** **Não.** As candidatas foram medidas e nenhuma resolve sem prejudicar outros
    cenários (§5). Fica decisão sua: vão mais largo ou parede que esmaece.
15. **As descobertas funcionam?** Sim (`DiscoveryTest`, vídeos 01 e 03).
16. **As recompensas são persistentes?** Sim: salvar e reabrir não devolve o pote cheio nem as moedas (teste e vídeo 01).
17. **Quantas sementes foram testadas?** 300 da v1; 200 da v2 (duas vezes); 16 capturadas; 9 com antes × depois.
18. **Quantos testes passaram?** 439, 0 falhas (no checkout e num clone limpo)..
19. **O desempenho mudou?** Na vila fixa e na v1, não. Na v2, +0,2 a +0,6 ms de quadro (até +6%) e até +6 MB de malhas, pelos moradores e pelas formas das ruínas; nenhuma regressão sem causa (§8).
20. **Quais problemas permanecem?**
    - P85: a câmera na torre;
    - a câmera curta perto dos barris da abóbada (o teto segurando);
    - P86: aprovar o pote;
    - P90: os seus mundos v2 antigos;
    - P91: a Brásia a 0,44 u da tina;
    - P92: a miniatura antiga do mundo;
    - a leitura da Índira junto da placa e da cerca;
    - P94: o teste físico.
21. **Está pronto para o teste físico?** Sim, num mundo novo, com o roteiro do pacote.
22. **A v2 pode ser congelada sem ressalvas críticas?** **Ainda não.** Falta o seu teste físico, e a decisão do P85 pode
    mudar a torre (geometria ou colisão), o que mudaria regiões: isso precisa vir ANTES do congelamento. Fora isso, não há
    pendência crítica conhecida.
