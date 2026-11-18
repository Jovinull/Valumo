package vx.game;

import org.joml.Vector3f;
import vx.viewer.Animator;
import vx.viewer.Appearance;
import vx.viewer.ComponentCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 08 — regras do protótipo jogável, sem OpenGL (testáveis). Estados: escolher a aparência → explorar
 * → conversar → pausar. O desenho e a janela ficam no {@link Game}; aqui só posição, câmera, NPCs, diálogo e
 * escolha de aparência.
 */
public final class GameLogic {
    public enum State { SELECT, EXPLORE, DIALOG, PAUSE }

    /** NPC com quem dá para conversar. */
    public static final class Npc {
        public final SceneDef.Interactive def;
        public float yaw;
        public float emoteStart = -10f;

        Npc(SceneDef.Interactive def) {
            this.def = def;
            yaw = (float) Math.toRadians(def.yawDeg());
        }

        public float baseYaw() { return (float) Math.toRadians(def.yawDeg()); }
    }

    /** Opção da tela de escolha: preset ou aparência salva (inválida aparece com o motivo, sem poder escolher). */
    public record Choice(String id, String label, Appearance appearance, String reason) {
        public boolean valid() { return appearance != null; }
    }

    public static final float INTERACT_ANGLE = (float) Math.toRadians(80);
    public static final float EMOTE_TIME = 1.55f;

    public State state = State.SELECT;
    public final Player player = new Player();
    public final FollowCamera cam = new FollowCamera();
    public final Collision collision;
    public final SceneDef scene;
    public final List<Npc> npcs = new ArrayList<>();
    public Npc candidate, talking;
    public int line;
    public final List<Choice> choices = new ArrayList<>();
    public int selected;
    public Appearance appearance;
    public float time;
    public boolean quit, openCreator, showHelp = true, started;
    public String message;
    public float messageUntil;
    /** Avisado quando a aparência escolhida muda (o Game compõe o modelo). */
    public Consumer<Appearance> onAppearance = a -> { };
    /** Rodada 09 — avisado a cada ação do jogador ("comecar", "pausar", "fala", "escolha", …): o som da interface
     *  sai daqui (GameAudio), sem a regra de jogo saber de áudio. */
    public Consumer<String> onAction = a -> { };

    public GameLogic(SceneDef scene, Collision collision) {
        this.scene = scene;
        this.collision = collision;
        for (SceneDef.Interactive i : scene.interactives) npcs.add(new Npc(i));
        player.place(scene.startX, scene.startZ, (float) Math.toRadians(scene.startYawDeg), collision);
        cam.yaw = player.yaw + (float) Math.PI;                           // atrás do herói, olhando para onde ele olha
        frameSelect();
    }

    /** Escolha de aparência: herói de frente (3/4), deslocado para a esquerda do painel. */
    void frameSelect() {
        float yaw = player.yaw + 0.35f, rx = (float) Math.cos(yaw), rz = -(float) Math.sin(yaw);
        float g = collision.ground.height(player.x, player.z);
        cam.frame(true, new Vector3f(player.x + rx * 0.75f, g + 0.68f, player.z + rz * 0.75f), yaw, 0.10f, 3.4f);
    }

    // ------------------------------------------------------------------ aparências

    /** Monta a lista: três presets e as aparências salvas (válidas ou com o motivo do erro). */
    public void loadChoices(ComponentCatalog cat, Path saveDir) {
        choices.clear();
        for (String p : cat.presets.keySet()) choices.add(new Choice("preset:" + p, presetName(p), Appearance.preset(p), null));
        if (Files.isDirectory(saveDir)) {
            try (var fs = Files.list(saveDir)) {
                for (Path f : fs.filter(p -> p.toString().endsWith(".json")).sorted(Comparator.comparing(Path::toString)).toList()) {
                    String label = f.getFileName().toString().replace(".json", "");
                    try {
                        Appearance a = Appearance.load(f, cat);
                        choices.add(new Choice("salva:" + f.getFileName(), a.name != null ? a.name : label, a, null));
                    } catch (IOException | RuntimeException e) {
                        choices.add(new Choice("salva:" + f.getFileName(), label, null, "arquivo inválido"));
                    }
                }
            } catch (IOException e) {
                message("Não consegui ler " + saveDir, 4f);
            }
        }
        if (appearance == null) select(0);
    }

    static String presetName(String p) {
        return switch (p) { case "aventureiro" -> "Aventureiro"; case "exploradora" -> "Exploradora"; case "maga" -> "Maga"; default -> p; };
    }

    public void select(int i) {
        if (i < 0 || i >= choices.size()) return;
        Choice c = choices.get(i);
        if (!c.valid()) { message(c.label() + ": " + c.reason(), 3.5f); onAction.accept("indisponivel"); return; }
        if (started || i != selected) onAction.accept("escolha");
        selected = i;
        appearance = c.appearance().copy();
        onAppearance.accept(appearance);
    }

    /** Aparência vinda do criador (em memória): entra na lista e fica escolhida. */
    public void useCreated(Appearance a, String label) {
        choices.add(new Choice("criada:" + choices.size(), label, a.copy(), null));
        select(choices.size() - 1);
    }

    // ------------------------------------------------------------------ quadro

    public void update(float dt, Input in) {
        time += dt;
        switch (state) {
            case SELECT -> {
                if (in.hit(GLFW_KEY_DOWN, GLFW_KEY_S)) select(nextValid(selected, 1));
                if (in.hit(GLFW_KEY_UP, GLFW_KEY_W)) select(nextValid(selected, -1));
                if (in.hit(GLFW_KEY_ENTER, GLFW_KEY_SPACE)) action("comecar");
                if (in.hit(GLFW_KEY_ESCAPE)) action("sair");
                player.update(dt, 0, 0, false, collision);
                cam.update(dt, player.x, player.visualY, player.z, collision);
            }
            case EXPLORE -> explore(dt, in);
            case DIALOG -> dialog(dt, in);
            case PAUSE -> {
                if (in.hit(GLFW_KEY_ESCAPE)) action("continuar");
                cam.update(dt, player.x, player.visualY, player.z, collision);
            }
        }
        for (Npc n : npcs) {                                              // quem conversa olha para o herói; depois volta
            float want = n == talking ? (float) Math.atan2(player.x - n.def.x(), player.z - n.def.z()) : n.baseYaw();
            float d = (float) Math.atan2(Math.sin(want - n.yaw), Math.cos(want - n.yaw));
            n.yaw += d * (1f - (float) Math.exp(-dt * (n == talking ? 6f : 2.5f)));
        }
    }

    private int nextValid(int from, int dir) {
        for (int k = 1; k <= choices.size(); k++) {
            int i = Math.floorMod(from + dir * k, choices.size());
            if (choices.get(i).valid()) return i;
        }
        return from;
    }

    private void explore(float dt, Input in) {
        if (in.hit(GLFW_KEY_ESCAPE)) { action("pausar"); return; }
        if (in.hit(GLFW_KEY_F1)) showHelp = !showHelp;
        cam.look(in.mdx, in.mdy);
        if (in.wheel != 0) cam.zoom(in.wheel);
        float[] ax = in.moveAxes();
        float fx = cam.forwardX(), fz = cam.forwardZ();
        // intenção relativa à câmera: frente = para onde ela olha; direita = (−fz, fx)
        float ix = fx * ax[1] - fz * ax[0], iz = fz * ax[1] + fx * ax[0];
        player.update(dt, ix, iz, in.running(), collision);
        cam.update(dt, player.x, player.visualY, player.z, collision);
        candidate = findCandidate();
        if (candidate != null && in.hit(GLFW_KEY_E, GLFW_KEY_ENTER)) startDialog(candidate);
    }

    /** NPC mais perto dentro do alcance e mais ou menos à frente do herói. */
    public Npc findCandidate() {
        Npc best = null;
        float bestD = Float.MAX_VALUE;
        float fx = (float) Math.sin(player.yaw), fz = (float) Math.cos(player.yaw);
        for (Npc n : npcs) {
            float dx = n.def.x() - player.x, dz = n.def.z() - player.z, d = (float) Math.hypot(dx, dz);
            if (d > n.def.range() || d < 1e-4f) continue;
            float cos = (dx * fx + dz * fz) / d;
            if (cos < Math.cos(INTERACT_ANGLE)) continue;
            if (d < bestD) { bestD = d; best = n; }
        }
        return best;
    }

    public void startDialog(Npc n) {
        onAction.accept("fala-inicio");
        talking = n;
        line = 0;
        candidate = null;
        state = State.DIALOG;
        n.emoteStart = time;
        // enquadra os dois de lado, um pouco atrás do herói, do lado onde a câmera já estava
        float lx = n.def.x() - player.x, lz = n.def.z() - player.z, l = (float) Math.hypot(lx, lz);
        lx /= l; lz /= l;
        float best = 0, bestScore = -9;
        for (int side : new int[]{1, -1}) {
            float dx = -lz * side - lx * 0.45f, dz = lx * side - lz * 0.45f;
            float y = (float) Math.atan2(dx, dz);
            float score = (float) Math.cos(y - cam.curYaw);
            if (score > bestScore) { bestScore = score; best = y; }
        }
        float g = collision.ground.height((player.x + n.def.x()) / 2, (player.z + n.def.z()) / 2);
        cam.frame(true, new Vector3f((player.x + n.def.x()) / 2, g + 0.8f, (player.z + n.def.z()) / 2), best, 0.16f, 3.1f);
    }

    private void dialog(float dt, Input in) {
        player.update(dt, 0, 0, false, collision);                        // para (com frenagem)
        player.face(talking.def.x(), talking.def.z(), dt);
        cam.update(dt, player.x, player.visualY, player.z, collision);
        if (in.hit(GLFW_KEY_E, GLFW_KEY_ENTER, GLFW_KEY_SPACE)) action("continuar-fala");
        if (in.hit(GLFW_KEY_ESCAPE)) endDialog();
    }

    public void endDialog() {
        if (talking != null) onAction.accept("fala-fim");
        talking = null;
        state = State.EXPLORE;
        cam.frame(false, null, 0, 0, 0);
    }

    public SceneDef.Line currentLine() { return talking == null ? null : talking.def.lines().get(line); }

    public boolean lastLine() { return talking != null && line >= talking.def.lines().size() - 1; }

    /** Clipe e tempo do NPC (aceno ao começar a conversa, depois parado). */
    public Animator.Motion npcMotion(Npc n, Animator.Motion out) {
        float t = time - n.emoteStart;
        if (t >= 0 && t < EMOTE_TIME) return out.set(Animator.Clip.EMOTE, t);
        return out.set(Animator.Clip.IDLE, time + n.def.x());
    }

    // ------------------------------------------------------------------ ações (teclado e cliques chegam aqui)

    public void action(String id) {
        if (!id.startsWith("escolha:") && !id.equals("encerrar-fala") && !(id.equals("continuar-fala") && lastLine()))
            onAction.accept(id);                                          // escolha e fim de fala avisam sozinhos
        switch (id) {
            case "comecar" -> {
                if (appearance == null) { message("Escolha uma aparência", 3f); return; }
                state = State.EXPLORE;
                started = true;
                cam.frame(false, null, 0, 0, 0);
                cam.yaw = player.yaw + (float) Math.PI;                    // gira suave para trás do herói
                cam.pitch = 0.30f;
            }
            case "continuar-fala" -> {
                if (talking == null) return;
                if (lastLine()) endDialog(); else line++;
            }
            case "encerrar-fala" -> endDialog();
            case "pausar" -> { if (state == State.EXPLORE) state = State.PAUSE; }
            case "continuar" -> { if (state == State.PAUSE) state = State.EXPLORE; }
            case "trocar-aparencia" -> { state = State.SELECT; frameSelect(); }
            case "criador" -> openCreator = true;
            case "sair" -> quit = true;
            default -> {
                if (id.startsWith("escolha:")) select(Integer.parseInt(id.substring(8)));
            }
        }
    }

    public void message(String m, float secs) {
        message = m;
        messageUntil = time + secs;
    }
}
