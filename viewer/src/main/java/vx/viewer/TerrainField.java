package vx.viewer;

/**
 * Rodada 11 — o relevo como dado: altura contínua, distância ao caminho e à água de UM mundo. A clareira e a vila de
 * sempre usam {@link World#LEGACY} (as mesmas contas de antes, número por número: referências e gate não mudam); o mundo
 * procedural (módulo game, {@code vx.game.world}) fornece um campo gerado por semente. O {@link Terrain} e o
 * {@link World} só leem daqui; nada aqui depende de OpenGL.
 */
public interface TerrainField {
    /** Identidade estável do campo (chave do cache de {@link Terrain}): "legado" ou "proc.&lt;semente&gt;.v&lt;versão&gt;". */
    String id();

    /** Altura contínua (u). {@code carvePath}: rebaixar o caminho (só as direções A–D; a E nunca rebaixa). */
    float height(float x, float z, boolean carvePath);

    /**
     * Rodada 12.1 — o relevo aqui é PLANO por projeto (patamar de construção)? A grade quantiza esses pontos sem a ondulação
     * de ruído do modo de relevo, para o chão debaixo de uma construção ficar num degrau só. Padrão: não (o relevo legado e
     * a v1 não mudam).
     */
    default boolean flat(float x, float z) { return false; }

    /** Distância (u) ao eixo do caminho mais perto (grande se não houver caminho). */
    float pathDistance(float x, float z);

    /** "Distância" ao lago (com o ruído da margem); grande se não houver lago. */
    float pondDistance(float x, float z);

    /** Centro do lago no nível da água, ou null (mundo sem lago). */
    float[] pondCenter();

    /** Raio (u) a partir do qual o chão vira floresta (a borda da região). */
    default float forestRadius() { return 14.5f; }

    /** Centro da região (a floresta conta a distância a partir daqui). */
    default float[] center() { return new float[]{0f, 0f}; }
}
