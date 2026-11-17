package vx.viewer;

/**
 * Rodada 07.2 — câmera do criador. {@code yaw} é o GIRO DO PERSONAGEM em relação à câmera (arrastar gira o
 * herói; a câmera fica no ângulo composto da cena, então nunca atravessa o cenário e o fundo é sempre o
 * planejado). {@code pitch}, {@code dist} e {@code height} são da câmera, com limites. Valores-alvo mudam por
 * clique/arrasto/roda; os atuais se aproximam deles a cada quadro (sem saltos). Foco "rosto" aproxima a
 * cabeça; "corpo" mostra o corpo inteiro. Sem OpenGL (testável).
 */
public final class CreatorCamera {
    public static final float MIN_DIST = 1.6f, MAX_DIST = 6.2f, MIN_PITCH = -0.05f, MAX_PITCH = 0.42f;
    public float yaw, pitch, dist, height;                 // atuais
    public float tYaw, tPitch, tDist, tHeight;             // alvos
    private float bodyDist = 4.4f, faceDist = 2.45f;

    public CreatorCamera() { reset(); snap(); }

    public void reset() {
        tYaw = 0.32f;
        tPitch = 0.14f;
        tDist = bodyDist;
        tHeight = BODY_H;
    }

    static final float FRONT = 0.22f;
    static final float BODY_H = 0.50f, FACE_H = 0.86f;

    public void snap() { yaw = tYaw; pitch = tPitch; dist = tDist; height = tHeight; }

    /** Arrasto em pixels → giro; a escala da UI mantém a sensibilidade igual em qualquer resolução. */
    public void drag(float dx, float dy, float uiScale) {
        float k = 0.0062f / Math.max(0.6f, uiScale);
        tYaw -= dx * k;
        tPitch = Math.clamp(tPitch + dy * k, MIN_PITCH, MAX_PITCH);
    }

    /** Arrasto com o botão direito: altura do alvo (olhar os pés ou o rosto), dentro do corpo. */
    public void pan(float dy, float uiScale) {
        tHeight = Math.clamp(tHeight + dy * 0.0024f / Math.max(0.6f, uiScale) * (tDist / 3f), 0.15f, 1.15f);
    }

    public void zoom(float wheel) {
        tDist = Math.clamp(tDist * (float) Math.pow(0.88, wheel), MIN_DIST, MAX_DIST);
        // perto do rosto, o alvo sobe para a cabeça; longe, volta ao meio do corpo
        float k = (MAX_DIST - tDist) / (MAX_DIST - MIN_DIST);
        tHeight = BODY_H + (FACE_H - BODY_H) * k * k;
    }

    public void focusFace(boolean face) {
        tDist = face ? faceDist : bodyDist;
        tHeight = face ? FACE_H : BODY_H;
        tPitch = Math.clamp(face ? 0.08f : tPitch, MIN_PITCH, MAX_PITCH);
        if (face) tYaw = FRONT + (float) (2 * Math.PI * Math.round((tYaw - FRONT) / (2 * Math.PI)));   // de frente, pelo caminho curto
    }

    /** Aproxima os valores atuais dos alvos (amortecimento exponencial independente do FPS). */
    public void update(float dt) {
        float a = 1f - (float) Math.exp(-dt * 9f);
        yaw += (tYaw - yaw) * a;
        pitch += (tPitch - pitch) * a;
        dist += (tDist - dist) * a;
        height += (tHeight - height) * a;
    }
}
