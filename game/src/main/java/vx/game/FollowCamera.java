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
    /** Intervalo da busca de desvio (s de jogo): 60 Hz, como era com a câmera por quadro na rodada 08. */
    static final float SEARCH_DT = 1f / 60f;
    private float searchAcc = SEARCH_DT;
    /**
     * Rodada 09 — ajuste de combate (não é trava de mira): com um inimigo engajado e perto, o alvo da câmera anda
     * FOCUS_SHIFT do caminho até ele, desloca SHOULDER u para a direita da vista (por cima do ombro: o herói não cobre
     * o inimigo baixo à frente) e a inclinação desce para pelo menos COMBAT_PITCH. Entra e sai suave; o rumo continua
     * do jogador. Peso 0 = câmera da rodada 08.
     */
    public static final float FOCUS_SHIFT = 0.35f, SHOULDER = 0.30f, ASSIST_RATE = 3f, COMBAT_PITCH = 0.62f;
    /**
     * Rodada 09.1 — com o inimigo colado (a cabeçada acontece a 0,5–0,9 u), nenhum giro lateral o tira de trás da
     * cabeça grande do herói: só olhar mais de cima. A inclinação mínima de combate sobe de COMBAT_PITCH (inimigo a
     * NEAR_FROM u ou mais) até NEAR_PITCH (a NEAR_TO u ou menos), suavizada (NEAR_RATE) para não balançar a cada avanço.
     * Escolhida por varredura (CombatCameraTest, junto com o viés): sem subir, 25% do tempo ruim nos cenários em
     * movimento; subindo a 0,9 a partir de 1,7 u, 5,7%. 0,9 rad ≈ 52°: olhar de cima, só com o fungo colado.
     */
    public static final float NEAR_PITCH = 0.9f;
    static final float NEAR_FROM = 1.7f, NEAR_TO = 0.6f, NEAR_RATE = 2.5f;
    private float near;
    /** Rodada 09.1 — taxas (1/s) do desvio e da volta à distância durante a luta (exploração: 3,5 e 3; ~0,2 s, sem salto). */
    static final float FIGHT_AVOID_RATE = 6f, FIGHT_OUT_RATE = 5f;
    /**
     * Rodada 09.1 — distância mínima na luta com a vista bloqueada perto: o piso de 0,9 u da exploração punha o olho
     * DENTRO do tronco do carvalho ou da pedra quando o herói encostava neles (a parede ficava a menos de 0,9 u). Na
     * luta o pivô fica fora da cabeça (ombro + caminho até o inimigo), então dá para chegar mais perto sem entrar nela.
     */
    static final float FIGHT_MIN_SHOWN = 0.55f;
    /** Rodada 09 (só no diagnóstico {@link #legacy09}): giro até o inimigo ficar a SIDE_ANGLE da vista, do lado mais perto. */
    public static final float SIDE_ANGLE = (float) Math.toRadians(35);
    public float combatYaw;
    private final Vector3f focus = new Vector3f();
    private float focusWant;
    public float assist;

    /** Liga (peso 1) ou desliga (0) o ajuste de combate para um inimigo em (x, y, z). */
    public void combatFocus(float x, float y, float z, float weight) {
        if (weight > 0f) focus.set(x, y, z);
        focusWant = combatAssist || lockOn || freeFight() ? weight : 0f;
    }

    /**
     * Ajuste automático de combate da rodada 09/09.1 (viés de 35°, inclinação até 52° com o inimigo colado). Rodada 09.2:
     * DESLIGADO por padrão — na mira livre o mouse manda e a câmera é a da exploração; o enquadramento de luta só existe
     * com a mira travada ({@link #lockOn}). {@code -Dvx.camera=091} (ou true aqui) volta ao ajuste da 09.1 para comparar.
     */
    public static boolean combatAssist = "091".equals(System.getProperty("vx.camera"));

    // ------------------------------------------------------------------ rodada 09.2: mira travada

    /**
     * Com a mira travada, o rumo do JOGADOR (não um ajuste por cima) gira para trás do herói em relação ao alvo, com o alvo
     * de lado ({@link #lockSide}: 35° com ele colado — a cabeça grande do herói esconde um fungo a 1 u com menos que isso,
     * medido na 09.1 — até 14° com ele longe), velocidade angular limitada a LOCK_TURN rad/s (sem giro brusco) e
     * suavização LOCK_RATE. O enquadramento é o da luta da 09.1 (pivô entre os dois + ombro, busca de vista que vê o alvo e
     * desvia de pedra/tronco/copa, distância mínima da luta), mas com a inclinação limitada a terceira pessoa: no máximo
     * LOCK_NEAR_PITCH (0,70 rad ≈ 40°) com o alvo colado e LOCK_PITCH (0,50 ≈ 29°) normal (a 09.1 subia até 0,9 rad ≈ 52°).
     * Escolhidos por varredura nos 10 cenários em movimento (CameraModesTest): 0,42/0,62 → 22% do tempo ruim e 96 quadros
     * com a câmera dentro de obstáculo; 0,50/0,70 → 19,5% e 24. O mouse desloca a vista até LOCK_OFF rad (volta
     * sozinho a LOCK_BACK/s) e ajusta a inclinação em [LOCK_MIN_PITCH, LOCK_MAX_PITCH]. Soltar a mira deixa o rumo onde
     * está: o WASD não muda de referencial na transição.
     */
    public static final float LOCK_TURN = 4.0f, LOCK_RATE = 7f, LOCK_OFF = 0.40f, LOCK_BACK = 1.5f;
    public static final float LOCK_MIN_PITCH = 0.12f, LOCK_MAX_PITCH = 0.58f;
    public static float LOCK_NEAR_PITCH = 0.70f, LOCK_PITCH = 0.50f;
    static float LOCK_SIDE_NEAR = (float) Math.toRadians(35), LOCK_SIDE_FAR = (float) Math.toRadians(22);

    /** Lado do alvo na tela com a mira travada, pela distância (u): 35° colado (≤ 1,2 u) → 22° longe (≥ 3,5 u). */
    static float lockSide(float d) {
        float k = Math.clamp((d - 1.2f) / 2.3f, 0f, 1f);
        k = k * k * (3 - 2 * k);
        return LOCK_SIDE_NEAR + (LOCK_SIDE_FAR - LOCK_SIDE_NEAR) * k;
    }

    /**
     * Rodada 09.2 — mira LIVRE em luta: só a composição muda — o pivô vai FREE_SHOULDER u para a direita da vista (o herói
     * fica um pouco à esquerda e o centro da tela, para onde o jogador olha, passa ao lado da cabeça dele). Nada de giro nem
     * de inclinação automáticos: o rumo e a inclinação são os do mouse. Entra e sai suave ({@link #shoulderW}).
     */
    public static float FREE_SHOULDER = 0.65f;
    private float shoulderWant;
    public float shoulderW;

    /** Liga (1) ou desliga (0) a composição de luta da mira livre (inimigo engajado por perto). */
    public void freeFight(float weight) { shoulderWant = weight; }

    /**
     * Mira livre em luta (não travada, sem o modo de comparação da 09.1): a busca de vista e as distâncias da luta valem
     * (desvia de pedra, tronco e copa; o olho nunca entra em nada), mas sem giro de combate e sem subir a inclinação.
     */
    boolean freeFight() { return shoulderWant > 0f && !lockOn && !combatAssist; }
    private boolean lockOn;
    private float lockX, lockZ, lockOff;
    /** Peso suave da mira (0 livre → 1 travada): pivô e inclinação entram e saem sem salto. */
    public float lockW;

    /** Trava (alvo em x, y, z) ou solta ({@code on} = false). */
    public void lockOn(boolean on, float x, float y, float z) {
        lockOn = on;
        if (on) { lockX = x; lockZ = z; }                               // a altura do alvo entra pelo combatFocus
        else lockOff = 0f;
    }

    public boolean locked() { return lockOn; }

    /**
     * Rodada 09.2 — referência do WASD: o rumo do mouse na hora, mais o desvio automático (parede, mira) seguido no máximo a
     * INPUT_RATE rad/s. Um desvio grande da câmera não vira o controle de uma vez (P38: esquiva para o lado errado).
     */
    public float inputYaw;
    static final float INPUT_RATE = 2.5f;
    private float inputAvoid;
    private boolean inputInit;

    /**
     * Giro de combate desejado: separa herói e inimigo na tela quando estão quase na mesma linha da vista. Rodada 09.1:
     * calculado sobre o desvio-ALVO (o mesmo que a busca avaliou), não sobre o desvio suavizado a caminho — senão o
     * lado do giro trocava no meio do trajeto e a busca, vendo outra vista final, trocava de desvio (vaivém).
     */
    private float combatYawTarget(float px, float pz) { return combatYawFor(yaw + (legacy09 ? avoidYaw : tAvoidYaw), px, pz); }

    /**
     * Diagnóstico (comparação antes/depois, como {@link #combatAssist}): true volta à câmera de combate da rodada 09 —
     * busca de desvio cega ao giro/inclinação de combate, giro em cone com troca de lado, inclinação fixa e taxas da
     * exploração. Só para capturas e testes antes × depois.
     */
    public static boolean legacy09 = false;

    /**
     * Giro de combate para uma vista de rumo {@code base} (sem o próprio giro: sem realimentação).
     * <ul>
     *   <li>Rodada 09 ({@link #legacy09}): se o inimigo estava a menos de SIDE_ANGLE da vista, girava até ele ficar a
     *       SIDE_ANGLE, do lado mais perto. Dentro do cone o mouse não mexia a vista (zona morta) e, ao cruzar o meio, o
     *       lado trocava: a câmera varria 70° passando com o inimigo atrás do herói.</li>
     *   <li>Rodada 09.1: viés contínuo de um lado só ({@link #biasedAngle}).</li>
     * </ul>
     */
    private float combatYawFor(float base, float px, float pz) {
        if (focusWant <= 0f || lockOn || freeFight()) return 0f;       // travada: o lado já está no rumo; livre: o mouse manda
        float ex = focus.x - px, ez = focus.z - pz;
        if (Math.hypot(ex, ez) < 1e-3f) return 0f;
        float a = (float) Math.atan2(Math.sin(Math.atan2(ex, ez) - Math.atan2(-Math.sin(base), -Math.cos(base))),
                Math.cos(Math.atan2(ex, ez) - Math.atan2(-Math.sin(base), -Math.cos(base))));
        if (!legacy09) return a - biasedAngle(a);
        if (Math.abs(a) >= SIDE_ANGLE) return 0f;
        float side = a >= 0f ? 1f : -1f;
        return -side * (SIDE_ANGLE - Math.abs(a));
    }

    /**
     * Rodada 09.1 — viés de combate (inimigo à direita do herói com o mouse nele) e onde ele termina de sumir. 35° é o
     * mesmo ângulo que a rodada 09 mediu em vídeo como mínimo para o herói não cobrir o fungo; a diferença é que agora
     * ele é contínuo (sem zona morta nem troca de lado). Varredura: 30° → 7,6%, 35° → 5,7%, 40° → 4,9% de tempo ruim;
     * mais que 35° gira demais a vista em relação ao mouse.
     */
    public static final float BIAS = (float) Math.toRadians(35), BIAS_FAR = (float) Math.toRadians(60);

    /**
     * Rodada 09.1 — ângulo final (vista × inimigo; negativo = inimigo à DIREITA do herói na tela, o lado do ombro) para
     * o ângulo a da vista do jogador. Contínuo e crescente: todo movimento do mouse move a vista (inclinação mínima 1/2,
     * sem zona morta nem troca de lado). Com o jogador mirando o inimigo (a = 0) ele aparece a BIAS à direita; o viés
     * some aos poucos quando o próprio jogador já o deixou de lado (a ≤ −2·BIAS ou a ≥ BIAS_FAR).
     */
    static float biasedAngle(float a) {
        if (a <= -2 * BIAS || a >= BIAS_FAR) return a;
        if (a <= 0f) return -BIAS + a / 2f;
        return -BIAS + a * (1f + BIAS / BIAS_FAR);
    }

    public void look(float dxPixels, float dyPixels) {
        if (framing) return;
        if (lockOn) {                                                     // travada: o mouse só desloca a vista um pouco
            lockOff = Math.clamp(lockOff - dxPixels * sensitivity, -LOCK_OFF, LOCK_OFF);
            pitch = Math.clamp(pitch + (invertY ? -dyPixels : dyPixels) * sensitivity, LOCK_MIN_PITCH, LOCK_MAX_PITCH);
            return;
        }
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

    /**
     * Rodada 09.1 — esquece o estado suavizado (próxima atualização começa já no enquadramento pedido). Montagem
     * técnica de cena (roteiros, testes de varredura); o jogo não usa.
     */
    public void reset() {
        first = true;
        dist = wantDist;
        avoidYaw = avoidPitch = tAvoidYaw = tAvoidPitch = combatYaw = assist = near = hold = 0f;
        lockW = lockOff = inputAvoid = shoulderW = 0f;
        inputInit = false;
        searchAcc = SEARCH_DT;
        lastClear = 1f;
        hasPrev = false;
    }

    static float unwrapNear(float a, float ref) {
        return ref + (float) Math.atan2(Math.sin(a - ref), Math.cos(a - ref));
    }

    /**
     * Atualiza para o herói em (px, py, pz) — py = altura visual dos pés. Colisão opcional (null nos testes de
     * órbita pura).
     */
    public void update(float dt, float px, float py, float pz, Collision c) {
        lockW += ((lockOn && !framing ? 1f : 0f) - lockW) * (1f - (float) Math.exp(-dt * LOCK_RATE));
        if (lockW < 1e-4f && !lockOn) lockW = 0f;
        if (lockOn && !framing) {                                         // rodada 09.2: o rumo do jogador segue o alvo
            float want = (float) Math.atan2(px - lockX, pz - lockZ) + lockSide((float) Math.hypot(lockX - px, lockZ - pz)) + lockOff;
            float d = (float) Math.atan2(Math.sin(want - yaw), Math.cos(want - yaw)) * (1f - (float) Math.exp(-dt * LOCK_RATE));
            yaw += Math.clamp(d, -LOCK_TURN * dt, LOCK_TURN * dt);
            pitch += (Math.clamp(pitch, LOCK_MIN_PITCH, LOCK_MAX_PITCH) - pitch) * (1f - (float) Math.exp(-dt * 4f));
            lockOff -= Math.signum(lockOff) * Math.min(Math.abs(lockOff), LOCK_BACK * dt * 0.25f);
        }
        assist += (((framing ? 0f : focusWant) - assist)) * (1f - (float) Math.exp(-dt * ASSIST_RATE));
        if (assist < 1e-4f && focusWant == 0f) assist = 0f;
        float tx = framing ? frameTarget.x : px, ty = framing ? frameTarget.y : py + PIVOT_HEIGHT, tz = framing ? frameTarget.z : pz;
        if (!framing && assist > 0f) {
            boolean free = freeFight();                                   // livre: só o ombro (composição), sem puxar para o inimigo
            float fs = free ? 0f : FOCUS_SHIFT, sh = free ? FREE_SHOULDER : SHOULDER;
            float sx = ((focus.x - px) * fs + (float) Math.cos(curYaw) * sh) * assist;
            float sz = ((focus.z - pz) * fs - (float) Math.sin(curYaw) * sh) * assist;
            if (c != null) {                                              // o alvo nunca entra em parede/pedra/tronco
                Vector3f from = new Vector3f(px, ty, pz), to = new Vector3f(px + sx, ty, pz + sz);
                float k = c.rayClear(from, to, 0.18f);
                if (k < 1f) { sx *= Math.max(0f, k - 0.1f); sz *= Math.max(0f, k - 0.1f); }
            }
            tx += sx;
            tz += sz;
        }
        shoulderW += (((framing || lockOn ? 0f : shoulderWant)) - shoulderW) * (1f - (float) Math.exp(-dt * ASSIST_RATE));
        if (shoulderW < 1e-4f && shoulderWant == 0f) shoulderW = 0f;
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
        searchAcc += dt;
        boolean search = searchAcc >= SEARCH_DT - 1e-6f;                  // rodada 09: a busca (dezenas de raios) a 60 Hz de
        if (search) searchAcc = 0f;                                        // tempo de jogo, mesmo com a câmera no passo de 1/120 s
        if (c != null && !framing && !search) {
            // mantém o desvio decidido na última busca
        } else if (c != null && !framing && focusWant > 0f && !legacy09) {
            combatSearch(c, px, pz, want);                                 // rodada 09.1: luta — vista final, sem esconder o inimigo
        } else if (c != null && !framing) {
            float clear0 = clearAt(c, yaw, pitch, want);
            if (clear0 * want < Math.min(COMFORT, want)) {
                float bestScore = clear0 * want, by = 0, bp = 0;
                for (float dp : new float[]{0f, 0.2f, 0.4f, 0.6f, 0.8f})
                    for (float dy : lockOn ? LOCK_DY : EXPLORE_DY) {
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
        if (!legacy09) {                                                    // rodada 09.1: o quanto o inimigo está colado (suave)
            float ed = focusWant > 0f ? (float) Math.hypot(focus.x - px, focus.z - pz) : NEAR_FROM;
            float k = Math.clamp((NEAR_FROM - ed) / (NEAR_FROM - NEAR_TO), 0f, 1f);
            k = k * k * (3f - 2f * k);
            near += (k - near) * (1f - (float) Math.exp(-dt * NEAR_RATE));
        }
        boolean fight = !legacy09 && !framing && assist > 0.5f;   // rodada 09.1: na luta o desvio e a volta são mais rápidos
        float ka = 1f - (float) Math.exp(-dt * (fight ? FIGHT_AVOID_RATE : 3.5f));
        combatYaw += ((framing ? 0f : combatYawTarget(px, pz)) - combatYaw) * (1f - (float) Math.exp(-dt * ASSIST_RATE));
        avoidYaw += (tAvoidYaw - avoidYaw) * ka;
        avoidPitch += (tAvoidPitch - avoidPitch) * ka;
        float ky2 = 1f - (float) Math.exp(-dt * (framing ? 3.5f : 18f));
        float targetYaw = framing ? frameYaw : yaw + avoidYaw + combatYaw, targetPitch;
        if (framing) targetPitch = framePitch;
        else if (assist > 0f && legacy09) {
            targetPitch = Math.min(MAX_PITCH, pitch + avoidPitch);
            targetPitch += (Math.max(targetPitch, COMBAT_PITCH) - targetPitch) * assist;
        } else if (assist > 0f)                                            // rodada 09.1: a luta sobe a inclinação e o desvio
            targetPitch = Math.clamp(combatPitch(assist) + avoidPitch, MIN_PITCH, MAX_PITCH);   // vem por cima (pode baixar: copas)
        else targetPitch = Math.min(MAX_PITCH, pitch + avoidPitch);
        curYaw += (unwrapNear(targetYaw, curYaw) - curYaw) * ky2;
        curPitch += (targetPitch - curPitch) * ky2;
        float allowed = want;
        if (c != null) {
            Vector3f desired = position(want, new Vector3f());
            lastClear = c.rayClear(pivot, desired, 0.18f);
            if (lastClear < 1f) {
                allowed = Math.max(fight ? FIGHT_MIN_SHOWN : MIN_SHOWN, lastClear * want - 0.15f);   // folga só quando algo bloqueia
                // rodada 09.2: o piso não pode pôr o olho DENTRO de pedra/tronco/relevo (encosta junto à pedra grande): se
                // puser, chega mais perto até sair (na luta; a exploração fica idêntica à da rodada 08)
                if (fight && !legacy09 && allowed > lastClear * want - 0.15f && c.cameraInside(position(allowed, tmpEye)))
                    allowed = Math.max(0.25f, lastClear * want - 0.15f);
            }
        }
        // rodada 09.2: referência do WASD (mouse na hora + desvio automático com giro limitado)
        float autoDev = (float) Math.atan2(Math.sin(curYaw - yaw), Math.cos(curYaw - yaw));
        if (!inputInit || framing) { inputAvoid = autoDev; inputInit = true; }
        else inputAvoid += Math.clamp((float) Math.atan2(Math.sin(autoDev - inputAvoid), Math.cos(autoDev - inputAvoid)), -INPUT_RATE * dt, INPUT_RATE * dt);
        inputYaw = yaw + inputAvoid;
        if (allowed < dist) dist = allowed;                       // bloqueou: encosta na hora (suavizar atravessaria a parede)
        else if (allowed - dist < 1e-4f) dist = allowed;          // assentou: para de verdade (nada de deriva de milionésimos)
        else dist += (allowed - dist) * (1f - (float) Math.exp(-dt * (framing ? 3.5f : fight ? FIGHT_OUT_RATE : 3f)));    // sai devagar
    }

    /** Rodada 09.2 — desvios de rumo da busca de parede: exploração (rodada 08) e mira travada (só pequenos: o alvo manda). */
    static final float[] EXPLORE_DY = {0f, 0.35f, -0.35f, 0.7f, -0.7f, 1.05f, -1.05f, 1.4f, -1.4f}, LOCK_DY = {0f, 0.35f, -0.35f};

    /** Inclinação de combate com peso w: pelo menos {@link #COMBAT_PITCH} (vê o inimigo baixo por cima do herói), mais com ele colado. */
    private float combatPitch(float w) {
        if (freeFight()) return pitch;                                    // livre: a inclinação é a do mouse
        float base = lockOn ? LOCK_PITCH : COMBAT_PITCH;
        float min = base + ((lockOn ? LOCK_NEAR_PITCH : NEAR_PITCH) - base) * near;
        return pitch + (Math.max(pitch, min) - pitch) * w;
    }

    /**
     * Rodada 09.1 — distância mínima confortável na luta: mais perto que isso, o herói ocupa mais de ~12% da tela e a
     * cabeça esconde o que acontece à frente (a 1,7 u da exploração ele passa de 20%). Medido com {@link CameraFraming}.
     */
    public static final float COMBAT_COMFORT = 2.8f;
    /** Inclinações extras da busca na luta: para baixo sai de debaixo das copas; para cima passa por cima de pedras. */
    static final float[] COMBAT_DP = {0f, -0.25f, -0.45f, 0.2f, 0.4f};
    static final float[] COMBAT_DY = {0f, 0.35f, -0.35f, 0.7f, -0.7f, 1.05f, -1.05f, 1.4f, -1.4f};

    /**
     * Rodada 09.1 — busca de desvio durante a luta. Na rodada 09 a busca olhava a vista do jogador SEM o giro e a
     * inclinação de combate, que eram somados depois: junto ao carvalho a vista final entrava na copa e a câmera
     * encostava a 0,96 u (a cabeça tomava a tela). Agora cada candidato é avaliado já como vai ficar (giro de combate
     * dele + inclinação de combate + desvio) e pontua pela distância livre, por ver o inimigo e pelo menor desvio. O
     * desvio atual leva vantagem (histerese): só troca por um claramente melhor.
     */
    private void combatSearch(Collision c, float px, float pz, float want) {
        float cur = combatScore(c, px, pz, want, tAvoidYaw, tAvoidPitch);
        boolean curHidden = lastHidden;
        if (hold > 0f) {                                                   // acabou de trocar: segura a escolha (sem vaivém)
            hold -= SEARCH_DT;
            if (cur > MIN_SHOWN + 0.6f) return;                            // ... a menos que tenha desabado de vez
        }
        float zero = tAvoidYaw == 0f && tAvoidPitch == 0f ? cur : combatScore(c, px, pz, want, 0f, 0f);
        if (zero >= want - 1e-3f && zero >= cur - 1e-3f) { tAvoidYaw = 0f; tAvoidPitch = 0f; return; }   // a vista do jogador já serve: volta a ela
        if (cur >= Math.min(COMBAT_COMFORT, want) && !curHidden) return;         // a atual serve: não mexe
        float best = cur, by = tAvoidYaw, bp = tAvoidPitch;
        for (float dp : COMBAT_DP)
            for (float dy : COMBAT_DY) {
                if (dy == tAvoidYaw && dp == tAvoidPitch) continue;
                float sc = combatScore(c, px, pz, want, dy, dp);
                if (sc > best + SWITCH_GAIN) { best = sc; by = dy; bp = dp; }
            }
        if (by != tAvoidYaw || bp != tAvoidPitch) hold = HOLD;
        tAvoidYaw = by;
        tAvoidPitch = bp;
    }

    /** Rodada 09.1 — ganho mínimo (u de vista livre) para trocar de desvio e tempo mínimo (s) com a escolha nova. */
    static final float SWITCH_GAIN = 0.5f, HOLD = 0.4f;
    private float hold;

    private boolean lastHidden;

    /** Pontuação de um desvio (dy, dp) na luta: distância livre da vista final − desvio − inimigo escondido pelo cenário. */
    private float combatScore(Collision c, float px, float pz, float want, float dy, float dp) {
        float y = yaw + dy, cy = combatYawFor(y, px, pz), p = Math.clamp(combatPitch(1f) + dp, MIN_PITCH, MAX_PITCH);
        float cp = (float) Math.cos(p), fy = y + cy;
        tmpTo.set(pivot.x + want * cp * (float) Math.sin(fy), pivot.y + want * (float) Math.sin(p), pivot.z + want * cp * (float) Math.cos(fy));
        float k = c.rayClear(pivot, tmpTo, 0.18f), d = k >= 1f ? want : Math.max(MIN_SHOWN, k * want - 0.15f);
        tmpEye.set(pivot).lerp(tmpTo, d / want);                          // onde o olho fica de verdade
        tmpTo.set(focus.x, focus.y + 0.45f, focus.z);                     // meio do chapéu do inimigo
        boolean hidden = c.rayClear(tmpEye, tmpTo, 0f) < 0.98f;
        if (dy == tAvoidYaw && dp == tAvoidPitch) lastHidden = hidden;
        return d - 0.6f * (Math.abs(dy) + Math.abs(dp)) - (hidden ? 1.5f : 0f);
    }

    private final Vector3f tmpTo = new Vector3f(), tmpEye = new Vector3f();

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

    /** Rodada 09: retrato do passo lógico anterior (na exploração a câmera anda no passo fixo; o desenho interpola). */
    private final Vector3f pPivot = new Vector3f();
    private float pYaw, pPitch, pDist;
    private boolean hasPrev;

    public void snapshot() {
        pPivot.set(pivot);
        pYaw = curYaw;
        pPitch = curPitch;
        pDist = dist;
        hasPrev = true;
    }

    /** Copia para a câmera da engine. */
    public void apply(Camera cam) { apply(cam, 1f); }

    /** Copia para a câmera da engine interpolando entre o passo anterior e o atual (a = 0..1). */
    public void apply(Camera cam, float a) {
        if (!hasPrev || a >= 1f) {
            cam.target.set(pivot);
            cam.yaw = curYaw;
            cam.pitch = curPitch;
            cam.distance = dist;
        } else {
            cam.target.set(pPivot).lerp(pivot, a);
            cam.yaw = pYaw + (float) Math.atan2(Math.sin(curYaw - pYaw), Math.cos(curYaw - pYaw)) * a;
            cam.pitch = pPitch + (curPitch - pPitch) * a;
            cam.distance = Math.min(pDist + (dist - pDist) * a, dist);   // nunca mais longe que o atual (parede)
        }
        cam.fovDeg = FOV;
        cam.near = 0.1f;                                         // câmera nunca a menos de 0,9 u do herói: dobra a precisão de profundidade
    }

    /** Direção "para frente" no chão (para onde a câmera olha) e "direita". */
    public float forwardX() { return -(float) Math.sin(curYaw); }
    public float forwardZ() { return -(float) Math.cos(curYaw); }

    /** Rodada 09.2 — "para frente" do WASD (referência {@link #inputYaw}). */
    public float inputForwardX() { return -(float) Math.sin(inputYaw); }
    public float inputForwardZ() { return -(float) Math.cos(inputYaw); }

    public float lastClearFraction() { return lastClear; }
}
