package vx.game;

/**
 * Rodada 12.3 (P85) — o recorte contextual de uma construção de interior estreito e aberto (a torre caída). Com o herói
 * dentro da zona dela (perfil {@code "recorte"} em colisao_e.json → {@link Collision#cutaways}):
 * <ul>
 *   <li>as consultas da CÂMERA ignoram as formas daquela construção, e só dela ({@link #skip} → {@link Collision#cameraSkip}):
 *   a câmera fica com o braço que o jogador escolheu, em vez de colar na nuca;</li>
 *   <li>o desenho recorta, só naquela construção, o que fica entre a câmera e o herói (um cone com o vértice na câmera,
 *   {@code scene.frag}), com peso {@link #weight} que sobe e desce em {@link #RATE}/s (entrada e saída suaves).</li>
 * </ul>
 * Há {@link #HYST} u de histerese na borda da zona, para o recorte não piscar quando o herói para no vão, e a saída
 * ({@link #EXIT}, {@link #EXIT_T}). A colisão do corpo
 * não muda nunca; fora de toda zona (a vila, a v1, os outros compostos) nada muda: a câmera é a da rodada 08.
 */
public final class CameraCutaway {
    public static final float HYST = 0.35f, RATE = 5f;
    /**
     * Raio do cone na altura do herói (u) e o miolo sem pontilhado (fração do raio; o resto é a borda pontilhada) — os mesmos
     * números de {@code scene.frag}. 1,4 u: o herói inteiro e o piso em volta dele; o cone (vértice na câmera) faz na tela um
     * disco de tamanho constante, sem buraco maior no que está perto da câmera.
     */
    public static final float RADIUS = 1.4f, CORE = 0.75f;
    /** A câmera só atravessa a torre com o recorte já aberto até aqui (antes disso a parede ainda taparia o herói). */
    public static final float OPEN = 0.5f;
    /**
     * Saída: quem estava DENTRO e sai pela porta leva o recorte por mais {@link #EXIT} u além da zona ou {@link #EXIT_T} s, o
     * que vier antes — a câmera, que estava atrás do herói do outro lado da torre, termina de sair sem encostar no batente.
     * Quem chega de fora nunca ganha recorte (só ao entrar na zona).
     */
    public static final float EXIT = 2.5f, EXIT_T = 1.5f;

    /** A zona em que o herói está (ou de onde acabou de sair, enquanto o peso desce); null = nenhuma. */
    public Collision.Cutaway zone;
    /** Peso do recorte no desenho: 0 = nada; 1 = o cone inteiro. */
    public float weight;
    /** Neste passo o herói está na zona (ou na histerese, ou na saída): a câmera pode ignorar a construção. */
    private boolean holding;
    private float outT;

    /** Um passo: a zona em que (x, z) está, com histerese e a saída, e o peso. */
    public void update(float dt, Collision c, float x, float z) {
        if (c.cutaways.isEmpty()) { reset(); return; }
        Collision.Cutaway in = c.cutawayAt(x, z, 0f);
        if (in != null) outT = 0f;
        else if (zone != null && weight > 0.5f) {
            float d = (float) Math.hypot(x - zone.x(), z - zone.z());
            if (d <= zone.r() + HYST) { in = zone; outT = 0f; }                       // histerese: parado no vão
            else if (d <= zone.r() + EXIT && outT < EXIT_T) { in = zone; outT += dt; } // saída
        }
        holding = in != null;
        if (in != null) zone = in;
        weight += ((in != null ? 1f : 0f) - weight) * (1f - (float) Math.exp(-dt * RATE));
        if (in == null && weight < 0.01f) { weight = 0f; zone = null; }
    }

    /** O id da zona cujas formas a câmera ignora agora ({@link Collision#cameraSkip}); -1 = nenhuma. */
    public int skip() {
        return zone != null && holding && weight >= OPEN ? zone.id() : -1;
    }

    /** Volta ao começo (troca de sessão, recomeço). */
    public void reset() {
        zone = null;
        weight = 0f;
        holding = false;
        outT = 0f;
    }
}
