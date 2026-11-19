# Catálogo de mundos, sementes, prévias e aventuras (rodada 11.1)

`vx.game.menu.WorldStore`, contrato `valumo.mundo` versão **1**. Decisão:
[ADR 0028](../decisions/0028-personagens-mundos-e-aventuras.md). O gerador é o da rodada 11, sem mudança
([mundo procedural](mundo-procedural.md); `RegionGenerator.VERSION` continua 1).

| Propriedade | Padrão | `-` |
|---|---|---|
| `-Dvx.mundos=PASTA` | `output/mundos` | desligado (só a vila; criar avisa) |
| `-Dvx.savegame=PASTA` | (só para OFERECER saves antigos) `output/saves` e `output/saves-procedural` | nada é oferecido |
| `-Dvx.qa.recusar=SEMENTE` | — | QA: simula a recusa do gerador para essa semente (exercita o caminho de falha) |

## Pastas

```text
output/mundos/
  importacoes.json                       registro dos saves antigos trazidos (sha256, origem, destino, data)
  m-2b975814a093/
    mundo.json                           o mundo (abaixo)
    previa.png                           vista geral 640×360 da região gerada
    aventuras/p-3f9c0a1b2c4d/aventura.json    save v2 do par (mundo gerado × personagem)
  vila/
    aventuras/p-3f9c0a1b2c4d/aventura.json    save v1 do par (vila fixa × personagem)
```

## `mundo.json`

```json
{ "formato": "valumo.mundo", "versao": 1, "id": "m-2b975814a093", "nome": "Vale das Brumas", "tipo": "procedural",
  "semente": "1024", "gerador": "valumo.regiao", "versaoGerador": 1, "criadoEm": 1795000000000, "usadoEm": 0,
  "previa": "1024.g1.v1" }
```

- **id** `m-` + 12 hexadecimais, independente da semente: dois mundos podem ter a mesma semente, cada um com as
  próprias aventuras. Pasta com id diferente do arquivo, formato ou versão desconhecidos: fica de fora com o motivo.
- **semente:** texto decimal sem sinal de 64 bits (de 0 a 18446744073709551615).
- **A vila fixa** é o mundo `vila`:
  - sempre está na lista, no fim, marcada "legado";
  - não tem semente nem `mundo.json`.

## Criar mundo (tudo dentro do jogo)

Formulário modal:

- **Nome:** até 32 caracteres. Vazio vira "Mundo &lt;semente&gt;".
- **Semente (opcional):** só algarismos, até 20. Fora da faixa: "A semente vai de 0 a …".
- **Vazia:** uma semente é sorteada **uma vez** (1 a 999 999 999, `SecureRandom`), aparece no campo antes de criar e fica
  gravada.
- **"Gerar outra seed":** sorteia de novo, no campo.
- **"Ver prévia":** gera a região da semente digitada e desenha a vista geral (não grava nada). Semente vazia sorteia
  primeiro, para a prévia nunca ser de outra semente.
- **Criar:**
  1. Gera e confere a região na colisão real (`ProceduralScene.create`, até 4 tentativas, ADR 0025) **antes** de
     qualquer arquivo.
  2. Grava `mundo.json`.
  3. Desenha e grava `previa.png` com troca atômica.
- **Falha de geração:**
  - mensagem amigável no formulário: "A semente N não gerou uma região jogável. Tente outra";
  - nome e semente ficam; nenhuma pasta, registro ou save parcial;
  - o motivo técnico vai para o console.

## Prévia real

É a mesma região que a aventura vai usar, desenhada pelo motor:

- **Câmera:** alvo entre o assentamento e o posto, inclinação de 50°, distância que cabe a trilha inteira.
- **Imagem:** 640×360, SSAA 1,5, renderer próprio.
- **Recursos:** o mundo temporário e a grade de relevo fecham logo depois.

A chave de cache `<semente>.g<versão do gerador>.v<versão da prévia>` fica no `mundo.json`.

- **Arquivo sumido ou chave diferente** (o gerador ou o desenho da prévia mudou): a lista mostra "preparando a
  prévia...", e a prévia é refeita quando o mundo é selecionado.
- **Vila:** a prévia é desenhada do próprio cenário do menu (a primeira tomada), sem arquivo.
- **Resumo:** quando a prévia é feita, o `mundo.json` ganha um `resumo` tirado dos fatos da região que o jogo não muda
  (`ProceduralScene.summary`): moradores, rumo da trilha até o posto, lago ou colina. Exemplo: "Uma estalagem com 3
  moradores. A trilha segue para o norte até um posto. Uma colina se ergue ao lado." A vila tem a descrição do lugar
  (`WorldStore.LEGACY_SUMMARY`).
- **Espera:** enquanto a prévia não existe, três sementes acendem em sequência no lugar dela (sem texto).
- **Câmera da prévia (fechamento da 11.1):** comparada com uma alternativa mais baixa e inclinada, com o olho atrás da
  estalagem, nas sementes 1024, 2048, 4096 e 77 (`previa-comparar`). A alternativa perdeu em todas: o telhado invade o
  primeiro plano, e na 2048 e na 77 não houve rumo livre na colisão real. **Mantida a atual**; `PREVIEW_VERSION`
  continua 1.
- **Custo medido:** ~0,1 s de geração + ~1,7 s de malha + desenho, na tela de carregamento.

## Associação personagem × mundo (aventuras)

A aventura é a pasta `mundos/<mundo>/aventuras/<personagem>/`.

- **Continuar ou começar:** o botão diz **Continuar aventura** se a pasta tem `aventura.json`; senão, **Iniciar
  aventura**.
- **Isolamento:**
  - outro personagem no mesmo mundo, ou o mesmo personagem em outro mundo (mesmo com a mesma semente), é outra aventura;
  - um não sobrescreve o outro (`MenuStoresTest`, `MenuAdventureTest`).
- **Formato:**
  - vila = save **v1**; mundo gerado = save **v2** (semente, gerador e vínculos conferidos);
  - as regras são as da rodada 10/11 ([save](save.md), [persistência do mundo procedural](persistencia-mundo-procedural.md));
  - não há v3.
- **Aparência:** ao continuar, vale a do personagem (o perfil pode ter sido editado); o progresso vem do save.
- **Gerador:** mundo criado por outra versão do gerador é recusado ao entrar, com a explicação.
- **Resumo na lista:** "12 min de jogo · missão concluída · 30 moedas", lido do save sem validar a cena
  (`WorldStore.progress`). Ilegível = "jogo salvo ilegível".

## Compatibilidade: saves das rodadas 10 e 11

Os saves antigos **não são movidos nem regravados**, e não há botão nem texto sobre eles. Ao abrir o menu, o passo
"Preparando seus jogos salvos" os **adota uma vez** (`Game.adoptOldSaves`):

| Save antigo | Onde | Vira |
|---|---|---|
| v1 da vila | `output/saves/aventura.json` | a aventura de um personagem na **Vila do Posto** |
| v2 de uma semente | `output/saves-procedural/s<N>/aventura.json` | a aventura de um personagem no mundo dessa semente (um "Mundo N" novo, se não houver) |

1. **Leitura:** o save é lido com o mesmo leitor do Continuar (`Game.readSave`): v1 só vale na vila, v2 só na região da
   mesma semente. Inválido: registrado como recusado (com o motivo), nada criado, o arquivo fica.
2. **Personagem:** é o da aparência gravada no save. Pode ser um que já tenha essa aparência e ainda não tenha aventura
   nesse mundo, ou um novo com o nome da aparência ou do modelo ("Aventureiro"). A "última aventura" é a data do save.
3. **Cópia:** entra por movimento atômico. O original fica onde estava; o sha256 vai para `importacoes.json`.
4. **Sem duplicar:** o mesmo conteúdo nunca entra duas vezes, nem é tentado de novo (a recompensa não duplica). Um par que
   já tem aventura nunca recebe um save antigo.
5. **Registro ilegível:** nada é adotado.

A linha de comando antiga continua: `-Dvx.mundo=procedural -Dvx.semente=N` abre a região direto, sem menu, com o save em
`output/saves-procedural/s<N>/`.
