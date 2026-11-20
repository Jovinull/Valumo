package vx.game;

import org.joml.Vector3f;
import vx.game.quest.QuestDefinition;
import vx.game.quest.QuestEvent;
import vx.game.quest.QuestProgress;
import vx.game.quest.QuestSystem;
import vx.viewer.Animator;
import vx.viewer.Appearance;
import vx.viewer.ComponentCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 08 — regras do protótipo jogável, sem OpenGL (testáveis). Estados: escolher a aparência → explorar
 * → conversar → pausar. O desenho e a janela ficam no {@link Game}; aqui só posição, câmera, NPCs, diálogo e
 * escolha de aparência.
 */
public final class GameLogic {
    /** Rodada 10: JOURNAL = diário aberto (J); a lógica para como na pausa. */
    public enum State { SELECT, EXPLORE, DIALOG, PAUSE, JOURNAL, MENU }

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
    public boolean quit, openCreator, started;
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
    /**
     * Fechamento da 10 — modo técnico ({@code -Dvx.tecnico}, {@code ./gradlew jogo -Ptecnico}): só nele a pausa troca a arena
     * (um / três / chefe / trilha) e a sensação (A/B/C). No jogo normal essas comparações não existem para o jogador — a
     * missão nunca perde os fungos porque alguém abriu a pausa. Roteiros e testes continuam com o acesso direto
     * ({@link #setEncounter}, {@link #applyFeel}, {@code -Dvx.encontro}, {@code -Dvx.sensacao}).
     */
    public boolean technical = Boolean.getBoolean("vx.tecnico");
    private boolean victoryShown;
    /** Avisado quando a aparência escolhida muda (o Game compõe o modelo). */
    public Consumer<Appearance> onAppearance = a -> { };
    /** Rodada 09.4 — avisado a cada ação do jogador ("comecar", "pausar", "fala", "escolha", …): o som da interface
     *  sai daqui (GameAudio), sem a regra de jogo saber de áudio. */
    public Consumer<String> onAction = a -> { };

    // ------------------------------------------------------------------ rodada 10: missões, falas por contexto, objetos, save
    /** Missões da cena (null = cena sem missões). Trocado inteiro ao continuar um jogo salvo. */
    public QuestSystem quests;
    public final Map<String, QuestDefinition> questDefs;
    public final DialogueBook dialogues;
    /** Falas e escolhas da conversa atual (do contexto, ou as de sempre da cena). */
    public List<SceneDef.Line> dialogLines = List.of();
    public List<DialogueBook.Choice> dialogChoices = List.of();
    public int choiceSelected;
    /** Contexto da conversa atual com quem dá a missão (null = falas de sempre). */
    public String dialogContext;
    /** Objeto do mundo ao alcance do E (quando nenhum NPC está mais perto). */
    public SceneDef.WorldObject objectCandidate;
    /** Instante (tempo de quadro) em que cada objeto acendeu nesta sessão: o efeito discreto do desenho. */
    public final Map<String, Float> litAt = new HashMap<>();
    /** Tempo de jogo (s): soma dos passos fixos da exploração (vai no save e carimba o diário). */
    public float playTime;
    /** Aviso de missão (canto, abaixo do rastreador). */
    public String toast;
    public float toastUntil;
    /** Avisado a cada mudança de missão (som e interface); a regra não sabe de áudio. */
    public Consumer<QuestSystem.Notice> onQuest = n -> { };
    /** Pasta do save ({@code -Dvx.savegame}; null = desligado), id deste jogo e o último save lido (Continuar). */
    public Path saveDir = SaveGame.dir();
    public String saveId = SaveGame.newId(new java.util.Random());
    public SaveGame.Loaded saved = new SaveGame.Loaded(null, null, null);
    public ComponentCatalog catalog;
    /** Saves gravados nesta sessão (manual + automático) e o último erro de gravação. */
    public int saves;
    public String saveError;
    private boolean autosavePending;
    /**
     * Rodada 11.1 — a aventura veio do menu principal (a pausa oferece "Menu principal" e "Sair do jogo" COM confirmação:
     * nenhum progresso se perde em silêncio), a confirmação aberta ({@code leaving} = "menu" ou "sair"; null = nenhuma) e o
     * pedido de voltar ao menu (o {@code Game} troca a sessão no próximo quadro).
     */
    public boolean menuAvailable, wantMenu;
    public String leaving;
    /** O estado de antes da confirmação aberta pelo X da janela (Cancelar volta a ele: diálogo, diário, exploração). */
    private State resumeState;
    /** O que o save guarda (progresso das missões + posição em quartos de u) no último salvamento, Continuar ou começo. */
    private String savedSnapshot;
    private Combat.Outcome lastOutcome = Combat.Outcome.NONE;

    public GameLogic(SceneDef scene, Collision collision) {
        this.scene = scene;
        this.collision = collision;
        for (SceneDef.Interactive i : scene.interactives) npcs.add(new Npc(i));
        prefs = scene.combat != null
                ? new Prefs(Prefs.defaultFile(), scene.combat.barDefault, scene.combat.feelDefault, scene.combat.encounterDefault).load()
                : new Prefs(null, "dano", "A", "um");
        if (System.getProperty("vx.sensacao") != null) { prefs.feel = System.getProperty("vx.sensacao"); prefs.feelFromCommandLine = true; }
        if (System.getProperty("vx.encontro") != null) { prefs.encounter = System.getProperty("vx.encontro"); prefs.encounterFromCommandLine = true; }
        if (System.getProperty("vx.barras") != null) prefs.bars = Prefs.valid(System.getProperty("vx.barras"), prefs.bars);
        if (!technical && scene.combat != null) {                         // jogo normal: arena e sensação de comparação
            if (!prefs.encounterFromCommandLine) {                         // gravadas antes não valem (nem são apagadas do arquivo)
                prefs.encounter = scene.combat.encounterDefault;
                prefs.encounterFromCommandLine = true;
            }
            if (!prefs.feelFromCommandLine) {
                prefs.feel = scene.combat.feelDefault;
                prefs.feelFromCommandLine = true;
            }
        }
        combat = scene.combat != null ? new Combat(scene.combat, collision, scene.assets, prefs.encounter) : null;
        if (combat != null) {
            combat.player = player;
            prefs.encounter = combat.encounter;
            applyFeel(prefs.feel);
            player.applyJump(combat.def);
        }
        player.place(scene.startX, scene.startZ, (float) Math.toRadians(scene.startYawDeg), collision);
        cam.yaw = player.yaw + (float) Math.PI;                           // atrás do herói, olhando para onde ele olha
        if (scene.procedural != null) {                                   // rodada 11: a missão instanciada da região
            ProceduralScene ps = scene.procedural;
            questDefs = ps.quest != null ? Map.of(ps.quest.id(), ps.quest) : Map.of();
            quests = new QuestSystem(questDefs.values());
            dialogues = ps.dialogues;
            saveDir = SaveGame.procDir(ps.worldId());
        } else if (scene.questsFile != null) {                            // rodada 10
            questDefs = QuestDefinition.load(scene.assets.resolve(scene.questsFile));
            quests = new QuestSystem(questDefs.values());
            dialogues = scene.dialoguesFile != null ? DialogueBook.load(scene.assets.resolve(scene.dialoguesFile)) : null;
            Set<String> ids = objectIds();
            for (QuestDefinition d : questDefs.values())
                for (QuestDefinition.Objective o : d.objectives())
                    if (o.kind() == QuestDefinition.Kind.INTERAGIR && !ids.contains(o.object()))
                        throw new IllegalArgumentException(d.id() + ": objeto " + o.object() + " não está na cena");
        } else {
            questDefs = Map.of();
            quests = null;
            dialogues = null;
        }
        frameSelect();
    }

    public Set<String> objectIds() {
        Set<String> s = new HashSet<>();
        for (SceneDef.WorldObject o : scene.objects) s.add(o.id());
        return s;
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
        catalog = cat;
        refreshSaved();
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
                if (in.hit(GLFW_KEY_C) && !started && quests != null && continueBlocker() == null) action("continuar-jogo");
                if (in.hit(GLFW_KEY_ESCAPE)) action("sair");
                player.update(dt, 0, 0, false, collision);
                cam.update(dt, player.x, player.visualY, player.z, collision);
            }
            case EXPLORE -> explore(dt, in);
            case DIALOG -> dialog(dt, in);
            case PAUSE -> {
                if (in.hit(GLFW_KEY_ESCAPE)) action(leaving != null ? "menu-cancelar" : "continuar");
                cam.update(dt, player.x, player.visualY, player.z, collision);
            }
            case MENU -> {                                                 // rodada 11.1: apresentação do menu (nada de jogo corre)
                player.update(dt, 0, 0, false, collision);
                cam.update(dt, player.x, player.visualY, player.z, collision);
            }
            case JOURNAL -> {                                              // rodada 10: diário (a lógica para)
                if (in.hit(GLFW_KEY_ESCAPE, GLFW_KEY_J)) action("fechar-diario");
                if (in.hit(GLFW_KEY_T)) action("rastreador");
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
        if (in.hit(GLFW_KEY_J) && quests != null) { action("diario"); return; }
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
                questsAfterTick();                                           // rodada 10: mortes e derrota viram eventos
                targeting.update(TICK, player, combat, (float) Math.atan2(cam.forwardX(), cam.forwardZ()));
                if (audio != null) audio.afterTick(combat);                  // depois do passo: o som não muda a lógica
            } else {
                if (pendingJump) player.requestJump();
                pendingJump = false;
                player.update(TICK, ix, iz, run, collision);
            }
            if (steps != null) steps.afterTick(player);
            playTime += TICK;
            if (quests != null) quests.clock = playTime;
            if (autosavePending && saveBlocker() == null) save(true);         // rodada 10: no primeiro passo seguro
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
        objectCandidate = combat != null && combat.engaged() ? null : findObject();
        if (candidate != null && objectCandidate != null) {                // rodada 10: o mais perto dos dois
            if (Math.hypot(objectCandidate.x() - player.x, objectCandidate.z() - player.z)
                    < Math.hypot(candidate.def.x() - player.x, candidate.def.z() - player.z)) candidate = null;
            else objectCandidate = null;
        }
        if (candidate != null && in.hit(GLFW_KEY_E, GLFW_KEY_ENTER)) startDialog(candidate);
        else if (objectCandidate != null && in.hit(GLFW_KEY_E, GLFW_KEY_ENTER)) useObject(objectCandidate);
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

    /** Rodada 10 — objeto do mundo mais perto, dentro do alcance e mais ou menos à frente (mesma regra dos NPCs). */
    public SceneDef.WorldObject findObject() {
        if (quests == null) return null;
        SceneDef.WorldObject best = null;
        float bestD = Float.MAX_VALUE;
        float fx = (float) Math.sin(player.yaw), fz = (float) Math.cos(player.yaw);
        for (SceneDef.WorldObject o : scene.objects) {
            float dx = o.x() - player.x, dz = o.z() - player.z, d = (float) Math.hypot(dx, dz);
            if (d > o.range() || d < 1e-4f) continue;
            if ((dx * fx + dz * fz) / d < Math.cos(INTERACT_ANGLE)) continue;
            if (d < bestD) { bestD = d; best = o; }
        }
        return best;
    }

    /** Rodada 10 — usar (E) um objeto: a missão decide; a frase vem do arquivo de falas. */
    public QuestSystem.Use useObject(SceneDef.WorldObject o) {
        QuestSystem.Use r = quests.use(o.id());
        DialogueBook.ObjectText t = dialogues != null ? dialogues.object(o.id()) : null;
        if (t != null) message(t.results().get(r), 5f);
        if (r == QuestSystem.Use.ATIVOU) litAt.put(o.id(), time);
        onAction.accept(r == QuestSystem.Use.ATIVOU ? "objeto-ativou" : "objeto-usar");
        processNotices();
        return r;
    }

    public void startDialog(Npc n) {
        onAction.accept("fala-inicio");
        talking = n;
        line = 0;
        choiceSelected = 0;
        dialogContext = null;
        DialogueBook.Entry entry = dialogues != null ? dialogues.linesFor(n.def.id(), n.def.lines(), quests) : null;
        if (entry == null) entry = new DialogueBook.Entry(n.def.lines(), List.of());
        dialogLines = entry.lines();
        dialogChoices = entry.choices();
        if (quests != null) {                                              // rodada 10: o contexto antes da entrega
            String q = dialogues != null ? dialogues.questOf(n.def.id(), quests) : null;
            if (q != null && dialogues.gives(n.def.id()) && quests.get(q) != null) {
                dialogContext = DialogueBook.giverContext(quests.get(q));
                if (dialogContext.equals("caiu")) quests.forget(q, QuestSystem.FACT_FELL);
            }
            quests.talkTo(n.def.id());                                      // entrega na hora (sair no meio não perde nada)
            processNotices();
        }
        candidate = null;
        state = State.DIALOG;
        n.emoteStart = time;
        // enquadra os dois de lado, um pouco atrás do herói, do lado onde a câmera já estava
        float lx = n.def.x() - player.x, lz = n.def.z() - player.z, l = (float) Math.hypot(lx, lz);
        lx /= l; lz /= l;
        float g = collision.ground.height((player.x + n.def.x()) / 2, (player.z + n.def.z()) / 2);
        // integração da 09.4: NPC mais alto que o padrão (1,25 u) — alvo mais alto e câmera mais longe, em proporção
        float k = Math.max(1f, n.def.height() / 1.25f), dist = 3.1f * k;
        Vector3f target = new Vector3f((player.x + n.def.x()) / 2, g + 0.8f * k, (player.z + n.def.z()) / 2);
        // lado da câmera: o mais perto de onde ela já estava ENTRE os que têm a vista livre (uma pedra ou parede de um
        // lado empurrava a câmera para dentro do rosto); o recuo maior, por cima do ombro, só se os dois lados fecharem
        float best = 0, bestScore = -99;
        for (float lean : new float[]{0.45f, 1.3f})
            for (int side : new int[]{1, -1}) {
                float dx = -lz * side - lx * lean, dz = lx * side - lz * lean;
                float y = (float) Math.atan2(dx, dz), cp = (float) Math.cos(0.16f);
                Vector3f eye = new Vector3f(target.x + dist * cp * (float) Math.sin(y), target.y + dist * (float) Math.sin(0.16f),
                        target.z + dist * cp * (float) Math.cos(y));
                float clear = collision.rayClear(target, eye, 0.18f);
                float score = (float) Math.cos(y - cam.curYaw) + (clear >= 0.999f ? 4f : 4f * clear - 2f) - (lean > 1f ? 1.5f : 0f);
                if (score > bestScore) { bestScore = score; best = y; }
            }
        cam.frame(true, target, best, 0.16f, dist);
    }

    private void dialog(float dt, Input in) {
        player.update(dt, 0, 0, false, collision);                        // para (com frenagem)
        player.face(talking.def.x(), talking.def.z(), dt);
        cam.update(dt, player.x, player.visualY, player.z, collision);
        if (lastLine() && !dialogChoices.isEmpty()) {                     // rodada 10: escolha na última fala
            if (in.hit(GLFW_KEY_LEFT, GLFW_KEY_UP, GLFW_KEY_A, GLFW_KEY_W)) choiceSelected = Math.floorMod(choiceSelected - 1, dialogChoices.size());
            if (in.hit(GLFW_KEY_RIGHT, GLFW_KEY_DOWN, GLFW_KEY_D, GLFW_KEY_S)) choiceSelected = Math.floorMod(choiceSelected + 1, dialogChoices.size());
        }
        if (in.hit(GLFW_KEY_E, GLFW_KEY_ENTER, GLFW_KEY_SPACE)) action("continuar-fala");
        if (in.hit(GLFW_KEY_ESCAPE)) endDialog();
    }

    /** Rodada 10 — escolha da última fala: aceitar/recusar a missão do NPC e mostrar a reação. */
    void choose(int i) {
        if (talking == null || i < 0 || i >= dialogChoices.size() || !lastLine()) return;
        DialogueBook.Choice c = dialogChoices.get(i);
        String npc = talking.def.id(), q = dialogues.questOf(npc, quests);
        boolean ok = c.action().equals("aceitar") ? quests.accept(q) : quests.decline(q);
        onAction.accept(c.action().equals("aceitar") ? "fala-aceitar" : "fala-recusar");
        processNotices();
        DialogueBook.Entry e = ok ? dialogues.entry(npc, q, c.next()) : null;
        if (e == null) { endDialog(); return; }
        dialogLines = e.lines();
        dialogChoices = e.choices();
        choiceSelected = 0;
        line = 0;
        talking.emoteStart = time;
    }

    public void endDialog() {
        if (talking != null) onAction.accept("fala-fim");
        talking = null;
        state = State.EXPLORE;
        cam.frame(false, null, 0, 0, 0);
    }

    public SceneDef.Line currentLine() { return talking == null || line >= dialogLines.size() ? null : dialogLines.get(line); }

    public boolean lastLine() { return talking != null && line >= dialogLines.size() - 1; }

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
                if (lastLine() && !dialogChoices.isEmpty()) choose(choiceSelected);
                else if (lastLine()) endDialog(); else line++;
            }
            case "diario" -> { if (state == State.EXPLORE && quests != null) state = State.JOURNAL; }
            case "menu-principal" -> { if (menuAvailable && state == State.PAUSE) leaving = "menu"; }   // sempre pergunta (o texto diz se há o que perder)
            case "menu-salvar" -> {                                    // salva e sai (só se dá para salvar agora)
                if (leaving == null) return;
                String why = saveBlocker();
                if (why != null) { message(why, 4f); return; }
                if (save(false)) leave();
            }
            case "menu-sem-salvar" -> { if (leaving != null) leave(); }
            case "menu-cancelar" -> {
                leaving = null;
                if (resumeState != null) { state = resumeState; resumeState = null; }   // de volta ao que estava (X cancelado)
            }
            case "diario-pausa" -> { if (state == State.PAUSE && quests != null) state = State.JOURNAL; }
            case "fechar-diario" -> { if (state == State.JOURNAL) state = State.EXPLORE; }
            case "rastreador" -> { prefs.tracker = !prefs.tracker; prefs.save(); }
            case "salvar" -> {
                String why = saveBlocker();
                if (why != null) message(why, 4f); else save(false);
            }
            case "continuar-jogo" -> continueSaved();
            case "encerrar-fala" -> endDialog();
            case "pausar" -> { if (state == State.EXPLORE) state = State.PAUSE; }
            case "continuar" -> { if (state == State.PAUSE) { state = State.EXPLORE; leaving = null; resumeState = null; } }
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
            case "sair" -> {                                           // rodada 11.1: na aventura do menu, confirma antes
                if (menuAvailable && state == State.PAUSE && inAdventure()) leaving = "sair";
                else quit = true;
            }
            case "barras" -> prefs.cycleBars();                       // rodada 09.2: barras de inimigos comuns
            case "sensacao" -> {                                       // rodada 09.2: próximo perfil de sensação
                if (combat == null || !technical) return;                // fechamento da 10: só no modo técnico
                List<String> ks = new ArrayList<>(combat.def.feels.keySet());
                applyFeel(ks.get((ks.indexOf(prefs.feel) + 1) % ks.size()));
                prefs.feelFromCommandLine = false;                     // escolha do jogador: passa a valer no arquivo
                prefs.save();
            }
            case "encontro" -> {                                       // rodada 09.2: próximo encontro da arena
                if (combat == null || !technical) return;                // fechamento da 10: só no modo técnico
                List<String> ks = new ArrayList<>(combat.def.encounters.keySet());
                setEncounter(ks.get((ks.indexOf(combat.encounter) + 1) % ks.size()));
            }
            default -> {
                if (id.startsWith("escolha:")) select(Integer.parseInt(id.substring(8)));
                else if (id.startsWith("fala-escolha:")) { choiceSelected = Integer.parseInt(id.substring(13)); action("continuar-fala"); }
                else if (id.startsWith("sensacao:") && technical) { applyFeel(id.substring(9)); prefs.feelFromCommandLine = false; prefs.save(); }
                else if (id.startsWith("encontro:") && technical) setEncounter(id.substring(9));
                else if (id.startsWith("barras:")) { prefs.bars = Prefs.valid(id.substring(7), prefs.bars); prefs.save(); }
            }
        }
    }

    // ------------------------------------------------------------------ rodada 10: missões no passo, avisos, save

    /** Depois de cada passo do combate: mortes (uma vez cada) e derrota do herói viram eventos de missão. */
    void questsAfterTick() {
        if (combat.outcome != lastOutcome) {
            if (combat.outcome == Combat.Outcome.DEFEAT && quests != null) quests.handle(new QuestEvent.HeroDefeated());
            lastOutcome = combat.outcome;
        }
        if (combat.defeated.isEmpty()) return;
        if (quests != null) for (String k : combat.defeated) quests.handle(new QuestEvent.EnemyDefeated(k));
        combat.defeated.clear();
        if (quests != null) {
            combat.retired.addAll(quests.retiredEnemies());              // o que contou não volta ao recomeçar
            processNotices();
        }
    }

    /** Avisos das missões: texto na tela, som (onQuest), encontro da missão ao aceitar e salvamento automático nos marcos. */
    void processNotices() {
        if (quests == null) return;
        int reward = 0;                                                    // a recompensa sai junto com "concluída": um aviso só
        for (QuestSystem.Notice n : quests.drain()) {
            QuestDefinition d = questDefs.get(n.quest());
            onQuest.accept(n);
            switch (n.kind()) {
                case ACEITA -> {
                    toast("Missão aceita: " + d.name(), 5f);
                    if (combat != null && d.encounter() != null) questEncounter(d.encounter());
                    autosavePending = true;
                }
                case PROGRESSO -> {
                    QuestProgress p = quests.get(n.quest());
                    int i = d.index(n.objective());
                    toast(d.objectives().get(i).text() + ": " + p.done(i) + "/" + d.objectives().get(i).total(), 4f);
                }
                case OBJETIVO -> {
                    toast("Objetivo cumprido: " + d.objectives().get(d.index(n.objective())).text(), 5f);
                    autosavePending = true;
                }
                case RECOMPENSA -> {
                    reward += n.amount();
                    toast("Recompensa: +" + n.amount() + " moedas", 6f);
                }
                case CONCLUIDA -> {
                    toast("Missão concluída: " + d.name() + (reward > 0 ? " · +" + reward + " moedas" : ""), 7f);
                    autosavePending = true;
                }
                case RECUSADA, OBJETO -> { }
            }
        }
        if (autosavePending && saveBlocker() == null) save(true);
    }

    public void toast(String t, float secs) {
        toast = t;
        toastUntil = time + secs;
    }

    /**
     * Encontro da arena que pertence à missão (sem gravar preferência: é estado do mundo, não escolha de comparação).
     * Só fora da luta; os inimigos que já contaram nascem fora de jogo.
     */
    void questEncounter(String name) {
        if (combat == null || combat.engaged()) return;
        combat.retired.addAll(quests.retiredEnemies());
        targeting.clear();
        cam.lockOn(false, 0, 0, 0);
        combat.setEncounter(name);
        combat.restart(player);
        if (audio != null) audio.reset(combat);
        victoryShown = false;
        lastOutcome = combat.outcome;
    }

    /** Rodada 11.1 — cumpre a saída confirmada: volta ao menu ou fecha o jogo. */
    private void leave() {
        if ("sair".equals(leaving)) quit = true; else wantMenu = true;
        leaving = null;
        resumeState = null;
    }

    /** Há uma aventura em andamento que pode ser salva (é dela que o jogador sai)? */
    public boolean inAdventure() { return started && quests != null && saveDir != null; }

    /**
     * Rodada 11.1 — há progresso que ainda não está no disco? A aventura começou, tem o que salvar, o save está ligado e o
     * progresso ou a posição mudaram desde o último salvamento (manual, automático dos marcos), o Continuar ou o começo.
     */
    public boolean unsaved() {
        return started && quests != null && saveDir != null && !progressSnapshot().equals(savedSnapshot);
    }

    /**
     * O que um save novo mudaria: o progresso (missões, objetivos, moedas, objetos acesos) e a posição do herói com
     * tolerância de 0,25 u. O tempo de jogo não conta: ficar parado não é progresso a perder.
     */
    String progressSnapshot() {
        return (quests == null ? "" : quests.toJson().toString()) + "|" + Math.round(player.x * 4) + "," + Math.round(player.z * 4);
    }

    /**
     * Rodada 11.1 — o X da janela: fora de uma aventura, fecha; numa aventura, SEMPRE abre a mesma confirmação da pausa
     * ("Sair do jogo?"), de qualquer estado (exploração, luta, diálogo, diário, pausa); o texto e os botões dizem se há algo
     * a perder ({@link #unsaved}). Cancelar volta ao estado de antes. Revisto no teste físico do proprietário: perguntar só
     * às vezes (quando havia progresso fora do disco) parecia aleatório.
     */
    public void requestQuit() {
        if (!inAdventure()) { quit = true; return; }
        if (leaving == null && state != State.PAUSE) resumeState = state;
        state = State.PAUSE;
        leaving = "sair";
    }

    /**
     * Rodada 11.1 — começa a aventura escolhida no menu: o PERSONAGEM manda na aparência (também ao continuar: o save guarda
     * a aparência da época, o perfil guarda a atual). {@code resume} = continuar o save da pasta {@link #saveDir}. Devolve o
     * motivo quando não dá para continuar (save inválido), sem mudar nada.
     */
    public String beginAdventure(Appearance a, String name, boolean resume) {
        menuAvailable = true;
        if (resume) {
            refreshSaved();
            String why = continueBlocker();
            if (why != null) return why;
        }
        useCreated(a, name);
        if (!resume) { action("comecar"); savedSnapshot = progressSnapshot(); return null; }
        boolean lookProblem = saved.data().appearanceProblem() != null;
        continueSaved();
        savedSnapshot = progressSnapshot();                               // o que acabou de vir do disco
        appearance = a.copy();
        onAppearance.accept(appearance);
        if (lookProblem) message("Jogo carregado", 3f);                 // a aparência do save não importa: vale a do personagem
        return null;
    }

    /** Por que não dá para salvar agora (null = dá). Nunca no meio da luta, na pausa de impacto, caído ou no ar. */
    public String saveBlocker() {
        if (quests == null) return "Esta cena não tem jogo para salvar";
        if (saveDir == null) return "Salvar está desligado nesta sessão";
        if (appearance == null || catalog == null) return "Escolha uma aparência antes de salvar";
        if (!started) return "Entre na vila antes de salvar";
        if (combat != null) {
            if (combat.hitstop > 0f) return "Não dá para salvar no meio de um golpe";
            if (combat.engaged()) return "Não dá para salvar no meio da luta";
            if (!combat.hero.alive() || combat.outcome == Combat.Outcome.DEFEAT) return "Não dá para salvar caído";
        }
        if (!player.grounded) return "Não dá para salvar no ar";
        return null;
    }

    /** Grava (atômico, validado ao reler). {@code auto} = marco da missão (silencioso se der certo). */
    public boolean save(boolean auto) {
        autosavePending = false;
        try {
            var json = SaveGame.compose(saveId, scene.layout, appearance, player.x, player.z, (float) Math.toDegrees(player.yaw), playTime, quests,
                    scene.procedural);
            SaveGame.write(saveDir, SaveGame.text(json), t -> SaveGame.parse(t, catalog, scene.layout, questDefs.values(), objectIds(), scene.procedural));
            saves++;
            saveError = null;
            savedSnapshot = progressSnapshot();
            saved = SaveGame.read(saveDir, catalog, scene.layout, questDefs.values(), objectIds(), scene.procedural);
            onAction.accept(auto ? "salvo-auto" : "salvo");
            if (auto) toastIfQuiet("Jogo salvo"); else message("Jogo salvo", 3f);
            return true;
        } catch (IOException | RuntimeException e) {
            saveError = e.getMessage();
            message("Não consegui salvar: " + e.getMessage(), 6f);
            return false;
        }
    }

    private void toastIfQuiet(String t) {
        if (toast == null || time >= toastUntil) toast(t, 2.5f);
    }

    /** Relê o save (tela inicial: habilita ou explica o Continuar). */
    public void refreshSaved() {
        saved = quests == null || catalog == null ? new SaveGame.Loaded(null, null, null)
                : SaveGame.read(saveDir, catalog, scene.layout, questDefs.values(), objectIds(), scene.procedural);
    }

    /** Por que o Continuar está indisponível (null = disponível). */
    public String continueBlocker() {
        if (quests == null) return "Esta cena não tem jogo salvo";
        if (saveDir == null) return "Salvar está desligado nesta sessão";
        if (saved.data() != null) return null;
        if (saved.problem() != null) return "Jogo salvo inválido: " + saved.problem();
        return "Nenhum jogo salvo ainda";
    }

    /**
     * Continua o jogo salvo: missões, moedas, objetos, aparência embutida, tempo de jogo e posição. Posição fora do
     * chão pisável ou dentro de um sólido volta ao ponto seguro documentado (o início da vila, diante da estalagem).
     */
    void continueSaved() {
        String why = started ? "O jogo já começou" : continueBlocker();
        if (why != null) { message(why, 4f); onAction.accept("indisponivel"); return; }
        SaveGame.Data d = saved.data();
        quests = d.quests();
        playTime = d.playTime();
        saveId = d.id();
        appearance = d.appearance().copy();
        onAppearance.accept(appearance);
        float yaw = (float) Math.toRadians(d.yawDeg());
        boolean safe = safeSpot(d.x(), d.z());
        if (safe) player.place(d.x(), d.z(), yaw, collision);
        else player.place(scene.startX, scene.startZ, (float) Math.toRadians(scene.startYawDeg), collision);
        if (combat != null) {
            combat.retired.addAll(quests.retiredEnemies());
            String enc = null;
            for (QuestProgress p : quests.all()) if (p.accepted() && p.def.encounter() != null) { enc = p.def.encounter(); break; }
            if (enc != null) questEncounter(enc);
            else { combat.restart(player); if (audio != null) audio.reset(combat); }
            player.place(player.x, player.z, player.yaw, collision);
        }
        player.snapshot();
        action("comecar");
        if (d.appearanceProblem() != null) message("Aparência salva incompatível; usando o Aventureiro (" + d.appearanceProblem() + ")", 7f);
        else if (!safe) message("Posição salva inválida: voltando ao início da vila", 5f);
        else message("Jogo carregado", 3f);
    }

    /** Ponto pisável, dentro do limite e fora de qualquer sólido (o círculo do herói não é empurrado). */
    public boolean safeSpot(float x, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(z) || !collision.walkable(x, z)) return false;
        float[] p = {x, z};
        float y = collision.floor(x, z, 99f, Player.RADIUS);
        float[] d = collision.resolve(p, Player.RADIUS, y, 1.2f, Player.STEP);
        return Math.hypot(d[0], d[1]) < 0.02f;
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
        prefs.encounterFromCommandLine = false;                        // escolha do jogador (pausa ou roteiro)
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
