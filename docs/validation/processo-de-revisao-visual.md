# Processo de revisão visual (rodada 02 em diante)

Uma mudança visual só está pronta depois de **capturada pelo viewer real e inspecionada** — compilar
e passar nos testes não basta. Este é o roteiro.

## 1. Comparação controlada

Mude **uma** coisa por vez. Para comparar modelos, use o modo LINHA: mesma luz, câmera tele (FOV 16°,
pouca perspectiva), chão, pose e tempo para todos; os ângulos giram os **modelos** (`myaw`), não a
câmera — com a câmera em 3/4 o modelo da ponta fica maior e a comparação mente.

```bash
tools/snap.sh "mode=lineup,style=e,set=variantes,myaw=0,out=..."    # conjuntos em App.LINEUPS
```

## 2. Vistas obrigatórias

| Vista | Spec | Procura |
|---|---|---|
| Frente / perfil / costas / 3/4 | `myaw=0/90/180/35,pitch=22` | silhueta, costas, equipamento |
| Silhueta | `sil=1` | leitura sem cor |
| Sem cor (argila) | `notex=1` | volume e forma |
| Luz neutra | `preset=neutra` | cor e defeitos sem atmosfera |
| Tamanho reduzido | render real em baixa resolução, ampliado sem filtro | leitura em jogo |
| Close de rosto | `mode=char,hero=...,dist=1.25,ty=0.92` | olhos, decalques, cabelo na testa |
| No mundo | `mode=scene,hero=...` (câmeras de `Review2.SCENES`) | escala e coesão |
| Movimento | `tools/anim.sh` (MP4/GIF) + prancha `tools/sheet.py seq` | interseções, pés, armas |

## 3. O que procurar (lista de defeitos reais desta rodada)

- **Decalque em superfície curva** → rosto fragmentado (pompom). Rosto sempre em placa plana.
- **Muitas mechas finas** → padrão cruzado nas costas. Massa + sulcos pintados.
- **Sombra profunda em cor clara** → faixa na testa (cabelo lavanda).
- **Forro claro em capuz** → tiara/capacete.
- **Arma atravessando chapéu/cabeça** → conferir o ataque quadro a quadro.
- **Parte pendurada longa** → horizontal na corrida (trança).
- **Degraus serrilhados** no terreno com faces na sombra (caminho rebaixado em 1/8 u).
- **Névoa velando o plano do herói**; contorno riscando degraus e tufos; copa "couve-flor".
- Câmera de captura dentro de objeto (conferir toda câmera nova antes de fixá-la no `Review2`).

## 4. Registro

Iteração em `output/tmp/` (ignorado). Entrega em `output/visual-review/round-NN/` via
`viewer --review2 [seções]` — nunca sobrescrever rodadas anteriores. Cada defeito encontrado vai para
o relatório da rodada com severidade, correção e situação final.

## 5. Ferramentas

- `tools/snap.sh` — captura offscreen (SSAA 2×), chaves em `Main.java`.
- `tools/anim.sh` — vídeo real (sequência offscreen → ffmpeg).
- `tools/sheet.py` — pranchas e redução (Pillow do `.venv`).
- `viewer --review2` — pacote determinístico completo.
- `tools/review72.sh` — interface do jogador: capturas por roteiro de entrada (mesma fila de eventos do
  GLFW), vídeo do fluxo com cursor, medição de quadros e vazamento. Roteiros em `tools/criador/`.
- Viewer interativo: `L` luz, `O` contorno, `F7` névoa, `K` silhueta, `X` expressão, `M` métricas,
  `P` qualidade, `V` conjunto da LINHA, `F12` captura.
