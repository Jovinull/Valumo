package vx.viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coreografia de armas (rodada 04, ADR 0011). Um PERFIL de arma ({@code assets/anim/<perfil>.json}, escrito
 * à mão) descreve poses nomeadas e golpes como chaves no tempo. Uma pose não guarda ângulos: guarda o que se
 * vê — a POSIÇÃO da mão, a DIREÇÃO da ponta da arma e o FIO (lado que corta), o polo do cotovelo, o giro do
 * tronco, a base dos pés. O animador resolve os braços por IK analítico de dois ossos; assim a trajetória da
 * arma é a curva autorada, não um efeito colateral de rotações somadas (o defeito das rodadas 01–03).
 *
 * <p>Espaço: o da RAIZ do personagem (quadril sobre os pés), em voxels do herói; +Z = frente, +X = esquerda
 * do personagem (o braço direito fica em x < 0), +Y = cima. Ângulos do tronco/cabeça em graus.
 *
 * <p>Interpolação: Hermite cúbica com tempo real como parâmetro e tangentes de Catmull-Rom (velocidade
 * contínua ao passar pelas chaves). {@code "stop": true} zera a velocidade na chave (antecipação, guarda);
 * {@code "snap": k} multiplica a tangente (golpe mais seco). Direções são interpoladas como vetores e
 * renormalizadas: a ponta descreve um arco, não um atalho pelo corpo. Canais discretos (modo da mão
 * esquerda, estado da flecha) valem o da chave de início do trecho.
 */
public final class Choreo {
    // canais contínuos
    static final int R_POS = 0, R_DIR = 3, R_EDGE = 6, R_ELBOW = 9, L_POS = 12, L_DIR = 15, L_EDGE = 18, L_ELBOW = 21,
            TORSO = 24, HEAD = 27, HIP = 30, FEET = 31, ROOTZ = 33, TILT = 34, DRAW = 35, GLOW = 36, L_GRIP = 37, GRIP_W = 38, YAW = 39, LIFT = 40, N = 41;
    // canais discretos
    static final int D_LMODE = 0, D_ARROW = 1, ND = 2;
    static final int LM_IK = 0, LM_GRIP = 1, LM_FREE = 2;
    static final int AR_HIDDEN = 0, AR_HAND = 1, AR_STRING = 2;

    static final int[] VECTOR_DIRS = {R_DIR, R_EDGE, R_ELBOW, L_DIR, L_EDGE, L_ELBOW};

    /** Uma chave resolvida: tempo, valores completos e tangentes por canal. */
    static final class Key {
        float t;
        final float[] v = new float[N];
        final float[] m = new float[N];
        final int[] d = new int[ND];
        boolean stop;
        float snap = 1f;
        String pose;
    }

    /** Golpe/gesto: chaves, fases nomeadas (documentação e testes) e eventos. */
    public static final class Move {
        public String name, label;
        final List<Key> keys = new ArrayList<>();
        public final Map<String, float[]> phases = new LinkedHashMap<>();
        public float release = Float.NaN, emit = Float.NaN;
        /** Rodada 05.1: cortado na execução, o golpe segue em frente (embalo) em vez de rebobinar ("interrupt": "forward"). */
        public boolean interruptForward;

        public float duration() { return keys.get(keys.size() - 1).t; }
    }

    public String name, description, rightItem, leftItem, attack;
    /** Comprimento do punho até a ponta (voxels, eixo +Y do item) e ponto de emissão (espaço do item). */
    public float tipLength;
    public float[] emit, rest;
    public float swingWalk = 2.0f, swingRun = 3.2f, arrowSpeed = 9f, orbSpeed = 4.5f;
    /** Mão que fica livre (balanço de locomoção) no transporte: 'l', 'r' ou 0 (as duas presas ao item). */
    public char carryFree;
    final Map<String, float[]> poses = new HashMap<>();
    final Map<String, int[]> posesD = new HashMap<>();
    public final Map<String, Move> moves = new LinkedHashMap<>();
    final Map<String, String> carry = new HashMap<>();

    // ------------------------------------------------------------------ carga

    private static final Map<String, Choreo> CACHE = new HashMap<>();
    /** Pasta dos perfis; testes e ferramentas podem apontar para outra raiz. */
    /** Pasta dos perfis; {@code -Dvx.anim=...} aponta para outra versão (comparação antes/depois, rodada 05.1). */
    public static Path root = Path.of(System.getProperty("vx.anim", "assets/anim"));

    public static synchronized Choreo get(String name) {
        if (name == null) return null;
        return CACHE.computeIfAbsent(root.toAbsolutePath() + "/" + name, k -> load(root.resolve(name + ".json")));
    }

    public static synchronized void clearCache() { CACHE.clear(); }

    public static Choreo load(Path file) {
        try {
            return parse(resolveExtends(file, JsonParser.parseString(Files.readString(file)).getAsJsonObject()));
        } catch (IOException e) {
            throw new IllegalStateException("perfil de arma ilegível: " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Herança entre perfis: {@code "extends": "sword"} parte do perfil base; poses e golpes do filho
     * substituem os de mesmo nome, e uma pose com {@code "merge": true} só acrescenta/troca campos da pose base
     * (ex.: o escudo muda a mão esquerda dos golpes da espada sem reescrevê-los).
     */
    static JsonObject resolveExtends(Path file, JsonObject doc) throws IOException {
        if (!doc.has("extends")) return doc;
        Path bf = file.resolveSibling(doc.get("extends").getAsString() + ".json");
        JsonObject base = resolveExtends(bf, JsonParser.parseString(Files.readString(bf)).getAsJsonObject()).deepCopy();
        for (String k : doc.keySet()) {
            if (k.equals("extends")) continue;
            if (k.equals("poses") || k.equals("moves")) {
                JsonObject dst = base.has(k) ? base.getAsJsonObject(k) : new JsonObject();
                JsonObject src = doc.getAsJsonObject(k);
                for (String n : src.keySet()) {
                    JsonObject o = src.getAsJsonObject(n);
                    if (o.has("merge") && o.get("merge").getAsBoolean() && dst.has(n)) {
                        JsonObject merged = dst.getAsJsonObject(n).deepCopy();
                        for (String f : o.keySet()) if (!f.equals("merge")) merged.add(f, o.get(f));
                        dst.add(n, merged);
                    } else dst.add(n, o);
                }
                base.add(k, dst);
            } else base.add(k, doc.get(k));
        }
        return base;
    }

    static Choreo parse(JsonObject doc) {
        Choreo c = new Choreo();
        c.name = doc.get("name").getAsString();
        c.description = str(doc, "description", "");
        c.rightItem = str(doc, "rightItem", null);
        c.leftItem = str(doc, "leftItem", null);
        c.attack = str(doc, "attack", null);
        c.tipLength = doc.has("tipLength") ? doc.get("tipLength").getAsFloat() : 0f;
        c.emit = doc.has("emit") ? vec(doc.getAsJsonArray("emit")) : null;
        c.rest = doc.has("arrowRest") ? vec(doc.getAsJsonArray("arrowRest")) : null;
        if (doc.has("arrowSpeed")) c.arrowSpeed = doc.get("arrowSpeed").getAsFloat();
        if (doc.has("orbSpeed")) c.orbSpeed = doc.get("orbSpeed").getAsFloat();
        float[] base = defaults();
        int[] baseD = {LM_IK, AR_HIDDEN};
        if (doc.has("defaults")) apply(doc.getAsJsonObject("defaults"), base, baseD);
        JsonObject ps = doc.getAsJsonObject("poses");
        Map<String, JsonObject> raw = new HashMap<>();
        for (String k : ps.keySet()) raw.put(k, ps.getAsJsonObject(k));
        for (String k : raw.keySet()) c.resolve(k, raw, base, baseD, new ArrayList<>());
        if (doc.has("carry")) {
            JsonObject cy = doc.getAsJsonObject("carry");
            for (String k : new String[]{"idle", "walk", "run"}) if (cy.has(k)) c.carry.put(k, cy.get(k).getAsString());
            if (cy.has("swingWalk")) c.swingWalk = cy.get("swingWalk").getAsFloat();
            if (cy.has("swingRun")) c.swingRun = cy.get("swingRun").getAsFloat();
            if (cy.has("free")) c.carryFree = cy.get("free").getAsString().charAt(0);
        }
        JsonObject ms = doc.getAsJsonObject("moves");
        for (String mn : ms.keySet()) {
            JsonObject mo = ms.getAsJsonObject(mn);
            Move mv = new Move();
            mv.name = mn;
            mv.label = str(mo, "label", mn);
            for (JsonElement ke : mo.getAsJsonArray("keys")) {
                JsonObject ko = ke.getAsJsonObject();
                Key k = new Key();
                k.t = ko.get("t").getAsFloat();
                k.pose = ko.has("pose") ? ko.get("pose").getAsString() : null;
                float[] src = k.pose != null ? c.poses.get(k.pose) : base;
                if (src == null) throw new IllegalStateException(c.name + "/" + mn + ": pose inexistente " + k.pose);
                System.arraycopy(src, 0, k.v, 0, N);
                System.arraycopy(k.pose != null ? c.posesD.get(k.pose) : baseD, 0, k.d, 0, ND);
                apply(ko, k.v, k.d);                          // ajustes locais na própria chave
                k.stop = ko.has("stop") && ko.get("stop").getAsBoolean();
                if (ko.has("snap")) k.snap = ko.get("snap").getAsFloat();
                if (!mv.keys.isEmpty() && k.t <= mv.keys.get(mv.keys.size() - 1).t)
                    throw new IllegalStateException(c.name + "/" + mn + ": tempos das chaves devem crescer");
                mv.keys.add(k);
            }
            if (mv.keys.size() < 2) throw new IllegalStateException(c.name + "/" + mn + ": um golpe precisa de 2+ chaves");
            mv.interruptForward = mo.has("interrupt") && mo.get("interrupt").getAsString().equals("forward");
            if (mo.has("phases")) {
                JsonObject ph = mo.getAsJsonObject("phases");
                for (String pn : ph.keySet()) mv.phases.put(pn, vec(ph.getAsJsonArray(pn)));
            }
            if (mo.has("release")) mv.release = mo.get("release").getAsFloat();
            if (mo.has("emit")) mv.emit = mo.get("emit").getAsFloat();
            tangents(mv);
            c.moves.put(mn, mv);
        }
        if (c.attack == null && !c.moves.isEmpty()) c.attack = c.moves.keySet().iterator().next();
        return c;
    }

    private void resolve(String name, Map<String, JsonObject> raw, float[] base, int[] baseD, List<String> stack) {
        if (poses.containsKey(name)) return;
        if (stack.contains(name)) throw new IllegalStateException(this.name + ": herança circular de poses " + stack);
        JsonObject o = raw.get(name);
        if (o == null) throw new IllegalStateException(this.name + ": pose inexistente " + name);
        float[] v = base.clone();
        int[] d = baseD.clone();
        if (o.has("base")) {
            stack.add(name);
            String b = o.get("base").getAsString();
            resolve(b, raw, base, baseD, stack);
            v = poses.get(b).clone();
            d = posesD.get(b).clone();
        }
        apply(o, v, d);
        poses.put(name, v);
        posesD.put(name, d);
    }

    static float[] defaults() {
        float[] v = new float[N];
        set(v, R_POS, -8.5f, 13f, 2f);
        set(v, R_DIR, 0, 1, 0);
        set(v, R_EDGE, 1, 0, 0);
        set(v, R_ELBOW, -1f, -0.6f, -1f);
        set(v, L_POS, 8.5f, 13f, 2f);
        set(v, L_DIR, 0, 1, 0);
        set(v, L_EDGE, 1, 0, 0);
        set(v, L_ELBOW, 1f, -0.6f, -1f);
        v[HIP] = 0.94f;
        v[FEET] = 1.0f;
        v[FEET + 1] = -0.6f;
        v[L_GRIP] = 4.5f;
        return v;
    }

    private static void apply(JsonObject o, float[] v, int[] d) {
        put(o, "r", v, R_POS, 3);
        put(o, "rDir", v, R_DIR, 3);
        put(o, "rEdge", v, R_EDGE, 3);
        put(o, "rElbow", v, R_ELBOW, 3);
        put(o, "l", v, L_POS, 3);
        put(o, "lDir", v, L_DIR, 3);
        put(o, "lEdge", v, L_EDGE, 3);
        put(o, "lElbow", v, L_ELBOW, 3);
        put(o, "torso", v, TORSO, 3);
        put(o, "head", v, HEAD, 3);
        put(o, "feet", v, FEET, 2);
        if (o.has("hip")) v[HIP] = o.get("hip").getAsFloat();
        if (o.has("rootZ")) v[ROOTZ] = o.get("rootZ").getAsFloat();
        if (o.has("tilt")) v[TILT] = o.get("tilt").getAsFloat();
        if (o.has("yaw")) v[YAW] = o.get("yaw").getAsFloat();
        if (o.has("lift")) v[LIFT] = o.get("lift").getAsFloat();      // pulo: corpo e pés sobem (voxels)        // giro do corpo inteiro (graus), ex.: arqueiro de lado
        if (o.has("draw")) v[DRAW] = o.get("draw").getAsFloat();
        if (o.has("glow")) v[GLOW] = o.get("glow").getAsFloat();
        if (o.has("lGrip")) v[L_GRIP] = o.get("lGrip").getAsFloat();
        // duas mãos: peso contínuo da empunhadura da mão esquerda no cabo (0 = alvo próprio "l", 1 = no cabo)
        if (o.has("lMode")) v[GRIP_W] = o.get("lMode").getAsString().equals("grip") ? 1f : 0f;
        if (o.has("lGripW")) v[GRIP_W] = o.get("lGripW").getAsFloat();
        if (o.has("lMode")) d[D_LMODE] = switch (o.get("lMode").getAsString()) {
            case "grip" -> LM_GRIP;
            case "free" -> LM_FREE;
            default -> LM_IK;
        };
        if (o.has("arrow")) d[D_ARROW] = switch (o.get("arrow").getAsString()) {
            case "hand" -> AR_HAND;
            case "string" -> AR_STRING;
            default -> AR_HIDDEN;
        };
        for (int i : VECTOR_DIRS) if (i != R_ELBOW && i != L_ELBOW) norm(v, i);
    }

    /**
     * Rodada 06.1 — cópia do perfil com um golpe/gesto em outro andamento (atuação por personagem, ex.: aceno
     * mais lento de uma NPC calma). Tempos das chaves e fases multiplicados por {@code k} (> 1 = mais lento);
     * poses e eventos são os mesmos. O perfil original (cache) não muda.
     */
    public Choreo withTempo(String move, float k) {
        Move src = moves.get(move);
        if (src == null || Math.abs(k - 1f) < 1e-4f) return this;
        Choreo c = new Choreo();
        c.name = name; c.description = description; c.rightItem = rightItem; c.leftItem = leftItem; c.attack = attack;
        c.tipLength = tipLength; c.emit = emit; c.rest = rest;
        c.swingWalk = swingWalk; c.swingRun = swingRun; c.arrowSpeed = arrowSpeed; c.orbSpeed = orbSpeed;
        c.carryFree = carryFree;
        c.poses.putAll(poses); c.posesD.putAll(posesD); c.carry.putAll(carry);
        c.moves.putAll(moves);
        Move mv = new Move();
        mv.name = src.name; mv.label = src.label; mv.interruptForward = src.interruptForward;
        mv.release = src.release * k; mv.emit = src.emit * k;
        for (Key o : src.keys) {
            Key n = new Key();
            n.t = o.t * k;
            System.arraycopy(o.v, 0, n.v, 0, N);
            System.arraycopy(o.d, 0, n.d, 0, ND);
            n.stop = o.stop; n.snap = o.snap; n.pose = o.pose;
            mv.keys.add(n);
        }
        for (var e : src.phases.entrySet()) mv.phases.put(e.getKey(), new float[]{e.getValue()[0] * k, e.getValue()[1] * k});
        tangents(mv);
        c.moves.put(move, mv);
        return c;
    }

    /** Tangentes de Catmull-Rom com o tempo como parâmetro (velocidade contínua nas chaves). */
    private static void tangents(Move mv) {
        List<Key> ks = mv.keys;
        for (int i = 0; i < ks.size(); i++) {
            Key k = ks.get(i);
            if (k.stop || i == 0 || i == ks.size() - 1) continue;
            Key a = ks.get(i - 1), b = ks.get(i + 1);
            float dt = b.t - a.t;
            for (int c = 0; c < N; c++) k.m[c] = k.snap * (b.v[c] - a.v[c]) / dt;
        }
    }

    // ------------------------------------------------------------------ avaliação

    /** Amostra o golpe no tempo t (s): escreve os canais em out e os discretos em outD. */
    public void sample(Move mv, float t, float[] out, int[] outD) {
        List<Key> ks = mv.keys;
        Key first = ks.get(0), last = ks.get(ks.size() - 1);
        if (t <= first.t) { copy(first, out, outD); return; }
        if (t >= last.t) { copy(last, out, outD); return; }
        int i = 0;
        while (ks.get(i + 1).t < t) i++;
        Key a = ks.get(i), b = ks.get(i + 1);
        float dt = b.t - a.t, s = (t - a.t) / dt, s2 = s * s, s3 = s2 * s;
        float h00 = 2 * s3 - 3 * s2 + 1, h10 = s3 - 2 * s2 + s, h01 = -2 * s3 + 3 * s2, h11 = s3 - s2;
        for (int c = 0; c < N; c++) out[c] = h00 * a.v[c] + h10 * dt * a.m[c] + h01 * b.v[c] + h11 * dt * b.m[c];
        for (int i2 : VECTOR_DIRS) norm(out, i2);
        System.arraycopy(a.d, 0, outD, 0, ND);
    }

    public float[] pose(String name) { return poses.get(name); }
    public int[] poseD(String name) { return posesD.get(name); }

    /** Pose de transporte da locomoção (idle/walk/run) ou null se o perfil não define. */
    String carryPose(Animator.Clip clip) {
        return switch (clip) {
            case WALK -> carry.getOrDefault("walk", carry.get("idle"));
            case RUN -> carry.getOrDefault("run", carry.getOrDefault("walk", carry.get("idle")));
            default -> carry.get("idle");
        };
    }

    /** Interpola dois vetores de controle (direções renormalizadas). */
    static void lerp(float[] a, float[] b, float w, float[] out) {
        for (int c = 0; c < N; c++) out[c] = a[c] + (b[c] - a[c]) * w;
        for (int i : VECTOR_DIRS) norm(out, i);
    }

    private static void copy(Key k, float[] out, int[] outD) {
        System.arraycopy(k.v, 0, out, 0, N);
        System.arraycopy(k.d, 0, outD, 0, ND);
    }

    // ------------------------------------------------------------------ utilidades

    private static void put(JsonObject o, String key, float[] v, int at, int n) {
        if (!o.has(key)) return;
        JsonArray a = o.getAsJsonArray(key);
        if (a.size() != n) throw new IllegalStateException("campo " + key + " precisa de " + n + " números");
        for (int i = 0; i < n; i++) v[at + i] = a.get(i).getAsFloat();
    }

    private static void set(float[] v, int at, float x, float y, float z) {
        v[at] = x;
        v[at + 1] = y;
        v[at + 2] = z;
    }

    static void norm(float[] v, int at) {
        float l = (float) Math.sqrt(v[at] * v[at] + v[at + 1] * v[at + 1] + v[at + 2] * v[at + 2]);
        if (l < 1e-6f) return;
        v[at] /= l;
        v[at + 1] /= l;
        v[at + 2] /= l;
    }

    private static float[] vec(JsonArray a) {
        float[] v = new float[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
        return v;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }
}
