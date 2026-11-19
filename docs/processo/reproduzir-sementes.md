# Como reproduzir uma semente

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./gradlew jogo -Dvx.mundo=procedural -Dvx.semente=1024              # joga a região da semente 1024 (save em output/saves-procedural/s1024/)
./gradlew jogo -Dvx.mundo=procedural -Dvx.semente=2048 -Dvx.savegame=/tmp/teste   # save de teste em /tmp/teste/s2048/
./gradlew jogo -Dvx.mundo=procedural -Dvx.semente=77 -Ptecnico      # + F3 (hitboxes) e as comparações na pausa
tools/review11.sh vistas        # 8 vistas reais por semente (SEEDS="1024 2048 4096 77" por padrão) + comparações
tools/review11.sh videos        # a aventura na região (VIDEO_SEED=1024), duas execuções com o mesmo save v2
```

- **Onde aparece a semente:** no subtítulo da tela inicial ("Mundo procedural (experimental) · semente 1024 · As Luzes do
  Posto") e no canto da pausa.
- **No console:** `mundo: procedural, semente 1024, valumo.regiao v1, tentativa 0, gerado e conferido em … ms`.
- **Nos roteiros:**
  - `regiao` imprime o que a semente gerou: arranjo, lago, assentamento, posto, marco, doador, moradores e props;
  - `vista-lugar geral|assentamento|trilha|posto` e `ir-lugar assentamento|trilha|posto|marco|doador|casa [andando]` acham
    os lugares da região, nada é escrito por semente;
  - `seguir-trilha ida|volta [correndo]` anda de verdade, com W e mouse;
  - `conversar aceitar|recusar` usa E até o fim;
  - `medir-geracao N` mede N gerações completas.
- **Mesmo mundo:** a mesma semente na mesma versão do gerador reproduz tudo. Se uma mudança no gerador altera o
  resultado, suba `RegionGenerator.VERSION` ([contrato](../architecture/contrato-sementes.md)).
- **Semente recusada:** se nenhuma das 4 tentativas passa na conferência, o jogo não abre e o console diz por quê
  (`ProceduralScene.Rejected`). Não é erro de leitura: é a região que não ficou jogável.
