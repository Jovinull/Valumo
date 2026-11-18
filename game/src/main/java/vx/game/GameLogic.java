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
    /** Rodada 09: passo lógico fixo da exploração e do combate (s). O desenho interpola entre dois passos. */
    public static final float TICK = 1f / 120f;
    static final double TICK_D = 1.0 / 120.0;
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
    /** Rodada 09: o confronto da cena (null = cena sem combate). */
    public final Combat combat;
    /** Rodada 09.1: som do combate (só lê o confronto depois de cada passo); null = sem som (testes, roteiros mudos). */
    public CombatAudio audio;
    /** Rodada 09.1: passos (som discreto no pouso de cada pé); null = sem som. */
    public Footsteps steps;
    /** Tempo acumulado ainda não simulado e fração do passo para o desenho (0..1). */
    public double acc;
    public float alpha = 1f;
    private boolean pendingAttack, pendingDodge, pendingJump;
    private final Hero.Intent intent = new Hero.Intent();
    /** Rodada 09.2: mira opcional (livre por padrão) e preferências do jogador. */
    public final Targeting targeting = new Targeting();
    /** Rodada 09.2 — medição: ns na câmera (passo fixo) e passos medidos. */
    public long camNanos, camTicks;
    public Prefs prefs;
    private boolean victoryShown;
    /** Avisado quando a aparência escolhida muda (o Game compõe o modelo). */
    public Consumer<Appearance> onAppearance = a -> { };
    /** Rodada 09.4 — avisado a cada ação do jogador ("comecar", "pausar", "fala", "escolha", …): o som da interface
     *  sai daqui (GameAudio), sem a regra de jogo saber de áudio. */
    public Consumer<String> onAction = a -> { };

    public GameLogic(SceneDef scene, Collision collision) {
        this.scene = scene;
        this.collision = collision;
        for (SceneDef.Interactive i : scene.interactives) npcs.add(new Npc(i));
        prefs = scene.combat != null
                ? new Prefs(Prefs.defaultFile(), scene.combat.barDefault, scene.combat.feelDefault, scene.combat.encounterDefault).load()
                : new Prefs(null, "dano", "A", "um");
        if (System.getProperty("vx.sensacao") != null) prefs.feel = System.getProperty("vx.sensacao");
        if (System.getProperty("vx.encontro") != null) prefs.encounter = System.getProperty("vx.encontro");
        if (System.getProperty("vx.barras") != null) prefs.bars = Prefs.valid(System.getProperty("vx.barras"), prefs.bars);
        combat = scene.combat != null ? new Combat(scene.combat, collision, scene.assets, prefs.encounter) : null;
        if (combat != null) {
            combat.player = player;
            prefs.encounter = combat.encounter;
            applyFeel(prefs.feel);
            player.applyJump(combat.def);
        }
        player.place(scene.startX, scene.startZ, (float) Math.toRadians(scene.startYawDeg), collision);
        cam.yaw = player.yaw + (float) Math.PI;                           // atrás do herói, olhando para onde ele olha
        frameSelect();
    }

    /** Rodada 09.2 — perfil de sensação (A = 09.1, B, C) no corpo e no golpe. */
    public void applyFeel(String name) {
        if (combat == null) return;
        CombatDef.Feel f = combat.def.feels.get(name);
        if (f == null) f = combat.def.feels.get(combat.def.feelDefault);
        player.applyFeel(f);
        combat.hero.feel = f;
        prefs.feel = f.name();
    }

    public CombatDef.Feel feel() { return combat == null ? null : combat.hero.feel; }

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
        if (state != State.EXPLORE) { player.snapshot(); cam.snapshot(); alpha = 1f; acc = 0f; }
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
        boolean run = in.running(), locked = combat != null && !combat.hero.alive();
        if (in.leftPressed > 0) pendingAttack = true;                  // o aperto vale para o próximo passo, mesmo sem passo neste quadro
        if (in.hit(GLFW_KEY_LEFT_ALT) || in.middlePressed > 0) pendingDodge = true;   // rodada 09.2: esquiva
        if (in.hit(GLFW_KEY_SPACE)) pendingJump = true;                              // rodada 09.2: salto
        if (combat != null && !locked) {                                             // rodada 09.2: mira opcional
            float fy = (float) Math.atan2(cam.forwardX(), cam.forwardZ());
            if (in.hit(GLFW_KEY_Q)) { targeting.toggle(player, combat, fy); combat.log(targeting.locked() ? "mira: travada em " + targetName() : "mira: livre"); }
            if (in.hit(GLFW_KEY_TAB)) {
                Enemy before = targeting.target;
                targeting.cycle(in.held(GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT) ? -1 : 1, player, combat, fy);
                if (targeting.target != before) combat.log("mira: alvo " + targetName());
            }
        }
        acc += dt;                                                      // double: sem deriva em milhares de quadros
        while (acc >= TICK_D - 1e-9) {                                  // passos fixos: nada depende da taxa de quadros
            acc -= TICK_D;
            player.snapshot();
            cam.snapshot();
            // intenção relativa à câmera DESTE passo: frente = para onde ela olha; direita = (−fz, fx). Rodada 09.2: a
            // referência é o rumo do mouse + o desvio automático com giro limitado (FollowCamera.inputYaw)
            float fx = cam.inputForwardX(), fz = cam.inputForwardZ();
            float ix = fx * ax[1] - fz * ax[0], iz = fz * ax[1] + fx * ax[0];
            if (combat != null) {
                float len = Math.min(1f, (float) Math.hypot(ix, iz)), max = run ? player.run : player.walk, k = len > 1e-6f ? len / (float) Math.hypot(ix, iz) : 0f;
                intent.set(locked ? 0f : ix * k * max, locked ? 0f : iz * k * max, !locked && len > 0.01f, !locked && pendingAttack,
                        !locked && in.rightDown, (float) Math.atan2(fx, fz));
                intent.run = run;
                intent.dodge = !locked && pendingDodge;
                intent.jump = !locked && pendingJump;
                intent.target = locked ? null : targeting.target;
                pendingAttack = pendingDodge = pendingJump = false;
                combat.tick(TICK, intent, player);
                targeting.update(TICK, player, combat, (float) Math.atan2(cam.forwardX(), cam.forwardZ()));
                if (audio != null) audio.afterTick(combat);                  // depois do passo: o som não muda a lógica
            } else {
                if (pendingJump) player.requestJump();
                pendingJump = false;
                player.update(TICK, ix, iz, run, collision);
            }
            if (steps != null) steps.afterTick(player);
            if (combat != null) {
                Enemy t = targeting.target;
                cam.lockOn(t != null, t != null ? t.x : 0f, t != null ? t.y + 0.4f * t.scale : 0f, t != null ? t.z : 0f);
                if (t != null) { cam.combatFocus(t.x, t.y, t.z, 1f); cam.freeFight(0f); }   // travada: enquadra o alvo
                else if (FollowCamera.combatAssist) {                       // rodada 09.1 (só para comparar): ajuste automático
                    Enemy e = combat.enemy;
                    boolean near = combat.engaged() && Math.hypot(e.x - player.x, e.z - player.z) < 4.5f;
                    cam.combatFocus(e.x, e.y, e.z, near ? 1f : 0f);
                } else {                                                    // livre: composição e busca de vista da luta,
                    Enemy n = nearestEngaged(4.5f);                         // sem giro nem inclinação automáticos
                    cam.freeFight(n != null ? 1f : 0f);
                    if (n != null) cam.combatFocus(n.x, n.y, n.z, 1f); else cam.combatFocus(0f, 0f, 0f, 0f);
                }
            }
            long c0 = System.nanoTime();
            cam.update(TICK, player.x, player.visualY, player.z, collision);   // câmera no passo fixo: o rumo entra na lógica
            camNanos += System.nanoTime() - c0;
            camTicks++;
        }
        acc = Math.max(0.0, acc);
        alpha = (float) Math.clamp(acc / TICK_D, 0.0, 1.0);
        candidate = combat != null && combat.engaged() ? null : findCandidate();   // sem conversa no meio da luta
        if (candidate != null && in.hit(GLFW_KEY_E, GLFW_KEY_ENTER)) startDialog(candidate);
        if (combat != null) {
            if (combat.outcome == Combat.Outcome.VICTORY && !victoryShown) {
                victoryShown = true;
                message(combat.enemies.size() == 1 ? combat.def.enemyName + " derrotado! R recomeça o confronto"
                        : "Grupo derrotado! R recomeça o confronto", 6f);
            }
            if (combat.outcome != Combat.Outcome.NONE && in.hit(GLFW_KEY_R)) action("recomecar");
        }
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
            case "recomecar" -> {                                      // rodada 09: recomeça o confronto
                if (combat == null) return;
                targeting.clear();
                cam.lockOn(false, 0, 0, 0);
                combat.restart(player);
                if (audio != null) audio.reset(combat);                      // nada preso tocando do confronto anterior
                victoryShown = false;
                pendingAttack = false;
                player.snapshot();
                state = State.EXPLORE;
                cam.yaw = player.yaw + (float) Math.PI;
            }
            case "voltar-vila" -> {
                if (combat == null) return;
                targeting.clear();
                cam.lockOn(false, 0, 0, 0);
                combat.restart(player);
                if (audio != null) audio.reset(combat);
                victoryShown = false;
                player.place(scene.startX, scene.startZ, (float) Math.toRadians(scene.startYawDeg), collision);
                player.snapshot();
                state = State.EXPLORE;
                cam.yaw = player.yaw + (float) Math.PI;
            }
            case "sair" -> quit = true;
            case "barras" -> prefs.cycleBars();                       // rodada 09.2: barras de inimigos comuns
            case "sensacao" -> {                                       // rodada 09.2: próximo perfil de sensação
                if (combat == null) return;
                List<String> ks = new ArrayList<>(combat.def.feels.keySet());
                applyFeel(ks.get((ks.indexOf(prefs.feel) + 1) % ks.size()));
                prefs.save();
            }
            case "encontro" -> {                                       // rodada 09.2: próximo encontro da arena
                if (combat == null) return;
                List<String> ks = new ArrayList<>(combat.def.encounters.keySet());
                setEncounter(ks.get((ks.indexOf(combat.encounter) + 1) % ks.size()));
            }
            default -> {
                if (id.startsWith("escolha:")) select(Integer.parseInt(id.substring(8)));
                else if (id.startsWith("sensacao:")) { applyFeel(id.substring(9)); prefs.save(); }
                else if (id.startsWith("encontro:")) setEncounter(id.substring(9));
                else if (id.startsWith("barras:")) { prefs.bars = Prefs.valid(id.substring(7), prefs.bars); prefs.save(); }
            }
        }
    }

    /** Rodada 09.2 — troca o encontro da arena (inimigos novos em casa, mira solta, som do confronto esquecido). */
    public void setEncounter(String name) {
        if (combat == null) return;
        targeting.clear();
        cam.lockOn(false, 0, 0, 0);
        combat.setEncounter(name);
        combat.restart(player);
        if (audio != null) audio.reset(combat);
        victoryShown = false;
        prefs.encounter = combat.encounter;
        prefs.save();
    }

    /**
     * Rodada 09.2 — barra pequena sobre um inimigo COMUM (regra testável; o desenho também confere visibilidade e tela):
     * nunca; "dano" = levou dano há menos de {@code show} s ou é o alvo travado; "sempre" = engajado ou ferido. Chefe
     * nunca tem a barra pequena (tem a grande).
     */
    public static boolean smallBar(String mode, Enemy e, Enemy target, float show) {
        if (!e.alive() || e.boss() || mode.equals("nunca")) return false;
        if (mode.equals("sempre")) return e.engaged() || e.hp < e.def.enemyHp;
        return e.hurtT < show || e == target;
    }

    /** Rodada 09.2 — barra grande no topo: só para inimigo classificado como chefe, vivo, engajado ou ferido. */
    public static boolean bossBar(Enemy e) {
        return e != null && e.boss() && e.alive() && (e.engaged() || e.hp < e.def.enemyHp);
    }

    /** O inimigo vivo e engajado mais perto do herói, a menos de r u (ou null). */
    Enemy nearestEngaged(float r) {
        Enemy best = null;
        float bd = r;
        for (Enemy e : combat.enemies) {
            if (!e.alive() || !e.engaged()) continue;
            float d = (float) Math.hypot(e.x - player.x, e.z - player.z);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    String targetName() {
        Enemy t = targeting.target;
        return t == null ? "-" : t.name() + "#" + t.id;
    }

    public void message(String m, float secs) {
        message = m;
        messageUntil = time + secs;
    }
}
