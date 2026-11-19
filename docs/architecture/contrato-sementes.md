# Contrato de sementes (rodada 11)

- **Formato:** um `long` sem sinal, mostrado e salvo como texto decimal (`"1024"`). `-Dvx.semente=N`; o padrão é 1024.
- **O mundo:** id `s<semente>` (`s1024`), usado nos ids de instância e na pasta do save.
- **Reprodutibilidade:** a MESMA semente com a MESMA versão do gerador (`valumo.regiao` v1, `RegionGenerator.VERSION`)
  dá os mesmos dados. Teste: `ProceduralWorldTest.mesmaSementeGeraOMesmoMundoEmQualquerOrdem`. Valem:
  - o relevo (o campo e a grade T1, com cache pela identidade `proc.<semente>.v<versão>.<tentativa>`);
  - os lugares e a trilha;
  - os props, os moradores, o encontro e o marco;
  - os ids, a missão instanciada e o vínculo dela.
- **Gerador:** `vx.game.world.Rng`, um SplitMix64 escrito no projeto (não `java.util.Random`), com o mesmo resultado em
  toda JVM. Floats com 24 bits do `long`.
- **Derivação:** cada decisão usa `Rng.of(semente, regiao, categoria, ...)`: rótulos em UTF-8 com separador (FNV-1a 64),
  misturados à semente. Categorias de hoje: `relevo`, `lugares`, `trilha`, `agua`, `colina`, `assentamento`, `posto`,
  `vegetacao`, `mata`, `missao`, `nuvens`.
  - Uma categoria nova não muda as outras.
  - Mudar a vegetação não move o encontro nem o marco (`vegetacaoNaoMoveLugaresEncontroNemMarco`).
  - A tentativa entra no rótulo (`t0`, `t1`…), e cada tentativa sorteia de novo.
- **Proibido:**
  - relógio do sistema depois de a semente estar definida;
  - iteração de `HashMap`/`HashSet` para decidir algo (o gerador usa listas e `TreeMap`);
  - depender da ordem de carga ou de consulta;
  - coordenadas em float concatenadas em ids.
- **Mudou o resultado de alguma semente** (até mover uma flor): suba `RegionGenerator.VERSION`. Saves de outra versão são
  recusados com o motivo (nada de "migrar" no escuro).
