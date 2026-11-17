package vx.game;

import org.joml.Vector3f;
import vx.engine.Camera;

/**
 * Rodada 08 — câmera de exploração em terceira pessoa (lógica, sem OpenGL).
 *
 * <ul>
 *   <li>Órbita em volta de um ponto acima da cabeça do herói; mouse gira (sensibilidade ajustável), roda dá
 *       zoom entre {@link #MIN_DIST} e {@link #MAX_DIST}.</li>
 *   <li>O ponto acompanha o herói rápido no plano e devagar na altura: degraus de 1 voxel e encostas não
 *       fazem a câmera tremer.</li>
 *   <li>Linha de visão contra o relevo, paredes, troncos e copas: encurta na hora quando algo entra na frente
 *       e volta devagar quando libera (sem "bombear"). Com a vista curta, desvia o rumo/inclinação o mínimo
 *       necessário (parede logo atrás do herói); o rumo escolhido pelo jogador não muda.</li>
 *   <li>Modo de enquadramento (diálogo): alvo, rumo, inclinação e distância próprios, com transição suave.</li>
 * </ul>
 */
public final class FollowCamera {
    public static final float MIN_DIST = 2.2f, MAX_DIST = 8.0f, DEFAULT_DIST = 4.6f, MIN_PITCH = -0.10f, MAX_PITCH = 1.05f;
    public static final float PIVOT_HEIGHT = 0.95f, FOV = 45f;

    public float yaw, pitch = 0.30f, wantDist = DEFAULT_DIST, sensitivity = 0.0032f;
    public boolean invertY = false;
    /** Atuais (suavizados). */
    public float curYaw, curPitch, dist = DEFAULT_DIST;
    public final Vector3f pivot = new Vector3f();
    private boolean first = true;
    // enquadramento de diálogo
    private boolean framing;
    private final Vector3f frameTarget = new Vector3f();
    private float frameYaw, framePitch, frameDist;
    private float lastClear = 1f;
    /** Desvio automático (rumo, inclinação) quando a vista fica curta junto a paredes; volta a 0 quando libera. */
    public float avoidYaw, avoidPitch;
    private float tAvoidYaw, tAvoidPitch;
    static final float COMFORT = 1.7f, MIN_SHOWN = 0.9f;

    public void look(float dxPixels, float dyPixels) {
        if (framing) return;
        yaw -= dxPixels * sensitivity;
        pitch = Math.clamp(pitch + (invertY ? -dyPixels : dyPixels) * sensitivity, MIN_PITCH, MAX_PITCH);
    }

    public void zoom(float wheel) {
        if (framing) return;
        wantDist = Math.clamp(wantDist * (float) Math.pow(0.88, wheel), MIN_DIST, MAX_DIST);
    }

    /** Liga o enquadramento de diálogo (alvo e rumo fixos) ou volta à exploração. */
    public void frame(boolean on, Vector3f target, float yaw, float pitch, float dist) {
        framing = on;
        if (on) {
            frameTarget.set(target);
            frameYaw = unwrapNear(yaw, curYaw);
            framePitch = pitch;
            frameDist = dist;
        } else {
            this.yaw = curYaw - avoidYaw;                                // a exploração continua de onde a conversa deixou
            this.pitch = Math.clamp(curPitch, MIN_PITCH, MAX_PITCH);
        }
    }

    public boolean framing() { return framing; }

    static float unwrapNear(float a, float ref) {
        return ref + (float) Math.atan2(Math.sin(a - ref), Math.cos(a - ref));
    }

    /**
     * Atualiza para o herói em (px, py, pz) — py = altura visual dos pés. Colisão opcional (null nos testes de
     * órbita pura).
     */
    public void update(float dt, float px, float py, float pz, Collision c) {
        float tx = framing ? frameTarget.x : px, ty = framing ? frameTarget.y : py + PIVOT_HEIGHT, tz = framing ? frameTarget.z : pz;
        float ty0 = framing ? frameTarget.y : py + PIVOT_HEIGHT;
        if (first) {                                                      // primeiro quadro: já no enquadramento pedido
            pivot.set(tx, ty0, tz);
            curYaw = framing ? frameYaw : yaw;
            curPitch = framing ? framePitch : pitch;
            dist = framing ? frameDist : wantDist;
            first = false;
        }
        float kxz = framing ? 1f - (float) Math.exp(-dt * 4f) : 1f - (float) Math.exp(-dt * 16f);
        float ky = 1f - (float) Math.exp(-dt * (framing ? 4f : 6f));
        pivot.x += (tx - pivot.x) * kxz;
        pivot.z += (tz - pivot.z) * kxz;
        pivot.y += (ty - pivot.y) * ky;
        float want = framing ? frameDist : wantDist;
        // desvio automático: com a vista curta (parede logo atrás do herói), procura o menor desvio de rumo e
        // inclinação que dê vista livre; o rumo do jogador não muda, o desvio volta a 0 quando a vista libera
        if (c != null && !framing) {
            float clear0 = clearAt(c, yaw, pitch, want);
            if (clear0 * want < Math.min(COMFORT, want)) {
                float bestScore = clear0 * want, by = 0, bp = 0;
                for (float dp : new float[]{0f, 0.2f, 0.4f, 0.6f, 0.8f})
                    for (float dy : new float[]{0f, 0.35f, -0.35f, 0.7f, -0.7f, 1.05f, -1.05f, 1.4f, -1.4f}) {
                        if (dp == 0 && dy == 0) continue;
                        float p = Math.min(MAX_PITCH, pitch + dp);
                        float d = clearAt(c, yaw + dy, p, want) * want - 0.6f * (Math.abs(dy) + dp);   // prefere o desvio menor
                        if (d > bestScore + 0.3f) { bestScore = d; by = dy; bp = p - pitch; }
                    }
                tAvoidYaw = by;
                tAvoidPitch = bp;
            } else if (clear0 >= 0.999f) {
                tAvoidYaw = 0;
                tAvoidPitch = 0;
            }
        } else {
            tAvoidYaw = 0;
            tAvoidPitch = 0;
        }
        float ka = 1f - (float) Math.exp(-dt * 3.5f);
        avoidYaw += (tAvoidYaw - avoidYaw) * ka;
        avoidPitch += (tAvoidPitch - avoidPitch) * ka;
        float ky2 = 1f - (float) Math.exp(-dt * (framing ? 3.5f : 18f));
        float targetYaw = framing ? frameYaw : yaw + avoidYaw, targetPitch = framing ? framePitch : Math.min(MAX_PITCH, pitch + avoidPitch);
        curYaw += (unwrapNear(targetYaw, curYaw) - curYaw) * ky2;
        curPitch += (targetPitch - curPitch) * ky2;
        float allowed = want;
        if (c != null) {
            Vector3f desired = position(want, new Vector3f());
            lastClear = c.rayClear(pivot, desired, 0.18f);
            if (lastClear < 1f) allowed = Math.max(MIN_SHOWN, lastClear * want - 0.15f);   // folga só quando algo bloqueia
        }
        if (allowed < dist) dist = allowed;                       // bloqueou: encosta na hora (suavizar atravessaria a parede)
        else if (allowed - dist < 1e-4f) dist = allowed;          // assentou: para de verdade (nada de deriva de milionésimos)
        else dist += (allowed - dist) * (1f - (float) Math.exp(-dt * (framing ? 3.5f : 3f)));    // sai devagar
    }

    private float clearAt(Collision c, float yaw, float pitch, float d) {
        float cp = (float) Math.cos(pitch);
        Vector3f to = new Vector3f(pivot.x + d * cp * (float) Math.sin(yaw), pivot.y + d * (float) Math.sin(pitch), pivot.z + d * cp * (float) Math.cos(yaw));
        return c.rayClear(pivot, to, 0.18f);
    }

    /** Posição da câmera para a distância d (mesma convenção da {@link Camera} da engine). */
    public Vector3f position(float d, Vector3f out) {
        float cp = (float) Math.cos(curPitch);
        return out.set(pivot.x + d * cp * (float) Math.sin(curYaw), pivot.y + d * (float) Math.sin(curPitch),
                pivot.z + d * cp * (float) Math.cos(curYaw));
    }

    public Vector3f position(Vector3f out) { return position(dist, out); }

    /** Copia para a câmera da engine. */
    public void apply(Camera cam) {
        cam.target.set(pivot);
        cam.yaw = curYaw;
        cam.pitch = curPitch;
        cam.distance = dist;
        cam.fovDeg = FOV;
        cam.near = 0.1f;                                         // câmera nunca a menos de 0,9 u do herói: dobra a precisão de profundidade
    }

    /** Direção "para frente" no chão (para onde a câmera olha) e "direita". */
    public float forwardX() { return -(float) Math.sin(curYaw); }
    public float forwardZ() { return -(float) Math.cos(curYaw); }

    public float lastClearFraction() { return lastClear; }
}
