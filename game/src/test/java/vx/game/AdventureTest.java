package vx.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.engine.Window.InputEvent;
import vx.engine.Window.InputEvent.Kind;
import vx.game.quest.QuestProgress;
import vx.game.quest.QuestState;
import vx.game.quest.QuestSystem;
import vx.viewer.ComponentCatalog;
import vx.viewer.Terrain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 10 — "As Luzes do Posto" de ponta a ponta na lógica do jogo (sem janela): oferta, trilha, fungos com
 * identidade estável, marco, volta, recompensa única, save e continuar; e a matriz de travamentos e trapaças do §16.
 */
class AdventureTest {
    static final Path A = Path.of("assets");
    static final ComponentCatalog CAT = ComponentCatalog.load(A);
    static final String Q = QuestTest.Q, MARCO = QuestTest.MARCO;

    static GameLogic adventure(Path saves) {
        SceneDef s = SceneDef.load(A, "jogo_vila_e");
        Collision c = Collision.build(Terrain.e(), Collision.profiles(A), s.handPlaced, s.bodies, s);
        GameLogic g = new GameLogic(s, c);
        g.saveDir = saves;
        g.loadChoices(CAT, saves.resolve("sem-aparencias"));
        return g;
    }

    static GameLogic started(Path saves) {
        GameLogic g = adventure(saves);
        g.action("comecar");
        assertEquals(GameLogic.State.EXPLORE, g.state);
        return g;
    }

    static final Input IN = new Input();

    static void frames(GameLogic g, int n) {
        for (int i = 0; i < n; i++) { IN.feed(List.of()); g.update(1 / 60f, IN); }
    }

    static void press(GameLogic g, int key) {
        IN.feed(GameplayTest.key(key));
        g.update(1 / 60f, IN);
    }

    static GameLogic.Npc npc(GameLogic g, String id) {
        for (GameLogic.Npc n : g.npcs) if (n.def.id().equals(id)) return n;
        throw new AssertionError(id);
    }

    /** Diante do NPC, E, e todas as falas; na escolha, a opção pedida (0 aceitar, 1 recusar). Devolve o contexto. */
    static String talk(GameLogic g, String id, int choice) {
        GameLogic.Npc n = npc(g, id);
        float a = (float) Math.toRadians(n.def.yawDeg());
        float x = n.def.x() + (float) Math.sin(a) * 1.1f, z = n.def.z() + (float) Math.cos(a) * 1.1f;
        g.player.place(x, z, (float) Math.atan2(n.def.x() - x, n.def.z() - z), g.collision);
        g.player.snapshot();
        for (int i = 0; i < 1200 && g.combat.engaged(); i++) frames(g, 1);    // fungo que perseguia volta para casa
        frames(g, 1);
        assertSame(n, g.candidate, "E alcança " + id);
        press(g, GLFW_KEY_E);
        assertEquals(GameLogic.State.DIALOG, g.state);
        String ctx = g.dialogContext;
        for (int i = 0; i < 40 && g.state == GameLogic.State.DIALOG; i++) {
            if (g.lastLine() && !g.dialogChoices.isEmpty()) g.choiceSelected = choice;
            g.action("continuar-fala");
        }
        assertEquals(GameLogic.State.EXPLORE, g.state);
        return ctx;
    }

    static SceneDef.WorldObject marco(GameLogic g) { return g.scene.objects.getFirst(); }

    /** Vai até o marco e aperta E. */
    static QuestSystem.Use useBeacon(GameLogic g) {
        SceneDef.WorldObject o = marco(g);
        g.player.place(o.x(), o.z() - 1.15f, 0f, g.collision);
        g.player.snapshot();
        frames(g, 1);
        assertFalse(g.combat.engaged(), "o marco só se usa fora da luta");
        assertSame(o, g.objectCandidate, "E alcança o marco");
        assertNull(g.candidate);
        boolean before = g.quests.objectOn(MARCO);
        int saves = g.saves;
        press(g, GLFW_KEY_E);
        if (g.quests.objectOn(MARCO) && !before) return QuestSystem.Use.ATIVOU;
        assertEquals(saves, g.saves);
        return g.quests.objectOn(MARCO) ? QuestSystem.Use.JA_ATIVO : null;
    }

    static Enemy enemy(GameLogic g, String key) {
        for (Enemy e : g.combat.enemies) if (e.key.equals(key)) return e;
        return null;
    }

    /** Derruba o inimigo e deixa a lógica correr (a morte sai no passo). */
    static void kill(GameLogic g, String key) {
        Enemy e = enemy(g, key);
        assertNotNull(e, key + " no encontro " + g.combat.encounter);
        assertTrue(e.alive(), key + " vivo");
        e.receive(999, g.player.x, g.player.z);
        frames(g, 2);
    }

    static void heroFalls(GameLogic g) {
        g.combat.hero.hp = 1;
        g.combat.hero.receive(99, g.player.x + 1, g.player.z, g.player, g.combat);
        frames(g, 150);                                                    // 2,5 s: a derrota sai em 1,6 s
        assertEquals(Combat.Outcome.DEFEAT, g.combat.outcome);
    }

    static GameLogic reload(Path saves) {
        GameLogic g = adventure(saves);
        assertNull(g.continueBlocker(), "Continuar disponível");
        g.action("continuar-jogo");
        assertEquals(GameLogic.State.EXPLORE, g.state);
        return g;
    }

    // ------------------------------------------------------------------ caminho completo

    @Test
    void aAventuraInteiraComSaveECarregar(@TempDir Path saves) throws Exception {
        GameLogic g = started(saves);
        assertNotNull(g.continueBlocker(), "sem save: Continuar indisponível com motivo");
        List<QuestSystem.NoticeKind> heard = new ArrayList<>();
        g.onQuest = n -> heard.add(n.kind());
        assertEquals("oferta", talk(g, "kelyra", 0));
        QuestProgress p = g.quests.get(Q);
        assertEquals(QuestState.ACEITA, p.state());
        assertEquals("trilha", g.combat.encounter, "a missão traz os fungos dela");
        assertEquals(2, g.combat.alive());
        assertEquals(1, g.saves, "salvo ao aceitar");
        kill(g, "trilha.fungo.1");
        assertEquals(1, p.done(0));
        kill(g, "trilha.fungo.2");
        frames(g, 30);
        assertEquals("acender_marco", p.current().id());
        assertEquals(2, g.saves, "salvo ao limpar a trilha");
        assertEquals(Combat.Outcome.NONE, g.combat.outcome, "trilha limpa não vira 'vitória — R recomeça'");
        assertEquals(QuestSystem.Use.ATIVOU, useBeacon(g));
        assertEquals(QuestState.PRONTA_PARA_ENTREGAR, p.state());
        assertEquals(3, g.saves, "salvo ao acender");
        assertTrue(g.litAt.containsKey(MARCO));
        assertEquals("entrega", talk(g, "kelyra", 0));
        assertEquals(QuestState.CONCLUIDA, p.state());
        assertEquals(30, g.quests.coins());
        assertEquals(4, g.saves, "salvo ao concluir");
        assertEquals(List.of(QuestSystem.NoticeKind.ACEITA, QuestSystem.NoticeKind.PROGRESSO, QuestSystem.NoticeKind.PROGRESSO,
                QuestSystem.NoticeKind.OBJETIVO, QuestSystem.NoticeKind.OBJETO, QuestSystem.NoticeKind.OBJETIVO,
                QuestSystem.NoticeKind.RECOMPENSA, QuestSystem.NoticeKind.CONCLUIDA), heard);
        // novo lançamento → Continuar
        GameLogic r = reload(saves);
        assertEquals(QuestState.CONCLUIDA, r.quests.get(Q).state());
        assertEquals(30, r.quests.coins());
        assertTrue(r.quests.objectOn(MARCO));
        assertEquals(g.appearance, r.appearance);
        assertEquals("concluida", talk(r, "kelyra", 0));
        assertEquals(30, r.quests.coins(), "falar de novo não paga de novo");
        assertTrue(Files.exists(saves.resolve(SaveGame.FILE)));
        try (var s = Files.list(saves)) {
            assertTrue(s.noneMatch(f -> f.toString().endsWith(".tmp")), "nenhum temporário sobrando");
        }
    }

    // ------------------------------------------------------------------ §16 antes da missão

    @Test
    void antesDaMissaoMatarOlharERecusarNaoTravam(@TempDir Path saves) {
        GameLogic g = started(saves);
        // a arena de teste (encontro padrão) antes de falar com a Kelyra: não conta
        String first = g.combat.enemies.getFirst().key;
        kill(g, first);
        assertEquals(QuestState.DISPONIVEL, g.quests.get(Q).state());
        // o marco antes de aceitar
        assertNull(useBeacon(g), "nada acende");
        assertFalse(g.quests.objectOn(MARCO));
        // a trilha escolhida na pausa antes de aceitar: os fungos da missão caem, mas não contam
        g.setEncounter("trilha");
        kill(g, "trilha.fungo.1");
        assertTrue(g.quests.get(Q).defeated().isEmpty());
        assertEquals("oferta_ja_lutou", talk(g, "kelyra", 1), "a Kelyra lembra da luta (vale mais que ter visto o marco)");
        assertTrue(g.quests.get(Q).declined());
        assertEquals(0, g.saves, "recusar não salva");
        assertEquals("oferta_apos_recusa", talk(g, "kelyra", 0), "a oferta continua de pé");
        assertEquals(QuestState.ACEITA, g.quests.get(Q).state());
        assertEquals(2, g.combat.alive(), "aceitar traz os dois fungos de volta, inteiros");
        assertTrue(g.quests.get(Q).defeated().isEmpty(), "nada do que caiu antes contou");
    }

    @Test
    void ofertaLembraDoMarcoEDaLuta(@TempDir Path saves) {
        GameLogic g = started(saves);
        kill(g, "um.0");                                                   // o fungo de teste fica perto do marco
        assertNull(useBeacon(g));
        assertEquals("oferta_ja_viu", talk(g, "kelyra", 1), "fungo de teste não é alvo: não vira 'já lutou'");
        GameLogic h = started(saves.resolve("b"));
        h.setEncounter("trilha");
        kill(h, "trilha.fungo.2");
        assertEquals("oferta_ja_lutou", talk(h, "kelyra", 1));
    }

    // ------------------------------------------------------------------ §16 durante

    @Test
    void duranteCairRecomecarSalvarCarregarNaoApagaNemDuplica(@TempDir Path saves) {
        GameLogic g = started(saves);
        talk(g, "kelyra", 0);
        kill(g, "trilha.fungo.1");
        heroFalls(g);
        g.action("recomecar");
        assertFalse(enemy(g, "trilha.fungo.1").alive(), "o fungo contado não volta");
        assertTrue(enemy(g, "trilha.fungo.1").gone);
        assertTrue(enemy(g, "trilha.fungo.2").alive(), "o outro continua lá (alcançável)");
        assertEquals(enemy(g, "trilha.fungo.2").def.enemyHp, enemy(g, "trilha.fungo.2").hp);
        assertEquals(1, g.quests.get(Q).done(0), "cair não apaga progresso");
        assertEquals("caiu", talk(g, "kelyra", 0));
        assertEquals("em_andamento", talk(g, "kelyra", 0), "a lembrança da queda vale uma conversa");
        // marco cedo (o fungo vivo guarda o marco: com ele engajado nem há E; a regra recusa de qualquer jeito)
        assertEquals(QuestSystem.Use.CEDO, g.useObject(marco(g)));
        assertFalse(g.quests.objectOn(MARCO));
        // salvar no meio (pausa) e "sair": o progresso volta igual
        g.action("salvar");
        assertNull(g.saveError);
        GameLogic r = reload(saves);
        assertEquals(1, r.quests.get(Q).done(0));
        assertEquals("trilha", r.combat.encounter);
        assertFalse(enemy(r, "trilha.fungo.1").alive());
        assertTrue(enemy(r, "trilha.fungo.2").alive());
        // o mesmo fungo não conta duas vezes nem por evento repetido
        r.quests.handle(new vx.game.quest.QuestEvent.EnemyDefeated("trilha.fungo.1"));
        assertEquals(1, r.quests.get(Q).done(0));
        kill(r, "trilha.fungo.2");
        frames(r, 30);
        assertEquals("acender_marco", r.quests.get(Q).current().id());
        // grupo e chefe no meio da missão não contam e não estragam
        r.setEncounter("grupo");
        for (Enemy e : List.copyOf(r.combat.enemies)) kill(r, e.key);
        assertEquals("acender_marco", r.quests.get(Q).current().id());
        r.setEncounter("trilha");
        assertEquals(0, r.combat.alive(), "a trilha limpa continua limpa");
        assertEquals(QuestSystem.Use.ATIVOU, useBeacon(r));
        // sair antes de voltar à Kelyra: o save do marco aceso fica
        GameLogic r2 = reload(saves);
        assertEquals(QuestState.PRONTA_PARA_ENTREGAR, r2.quests.get(Q).state());
        assertTrue(r2.quests.objectOn(MARCO));
        assertEquals(0, r2.quests.coins());
        assertEquals("entrega", talk(r2, "kelyra", 0));
        assertEquals(30, r2.quests.coins());
    }

    @Test
    void naoSalvaNaLutaNemNaPausaDeImpacto(@TempDir Path saves) {
        GameLogic g = started(saves);
        talk(g, "kelyra", 0);
        int n = g.saves;
        g.combat.hitstop = 0.05f;
        assertNotNull(g.saveBlocker());
        g.action("salvar");
        assertEquals(n, g.saves);
        g.combat.hitstop = 0f;
        Enemy e = enemy(g, "trilha.fungo.1");
        g.player.place(e.x, e.z - 1.2f, 0f, g.collision);
        g.player.snapshot();
        for (int i = 0; i < 240 && !g.combat.engaged(); i++) frames(g, 1);
        assertTrue(g.combat.engaged());
        assertNotNull(g.saveBlocker(), "no meio da luta");
        g.action("salvar");
        assertEquals(n, g.saves);
    }

    // ------------------------------------------------------------------ §16 depois

    @Test
    void depoisDeConcluirNadaReabreNemPagaDeNovo(@TempDir Path saves) {
        GameLogic g = started(saves);
        talk(g, "kelyra", 0);
        kill(g, "trilha.fungo.1");
        kill(g, "trilha.fungo.2");
        frames(g, 30);
        useBeacon(g);
        talk(g, "kelyra", 0);
        assertEquals(30, g.quests.coins());
        for (int k = 0; k < 3; k++) assertEquals("concluida", talk(g, "kelyra", 0));
        g.action("recomecar");
        assertEquals(0, g.combat.alive(), "recomeçar a trilha não traz os fungos da missão");
        assertEquals(Combat.Outcome.NONE, g.combat.outcome);
        frames(g, 120);
        assertEquals(Combat.Outcome.NONE, g.combat.outcome, "trilha vazia não vira vitória");
        assertEquals(QuestSystem.Use.JA_ATIVO, useBeacon(g), "reativar não muda nada");
        // cair numa arena de teste depois de concluir
        g.setEncounter("chefe");
        g.player.place(-1.6f, 11.2f, 0f, g.collision);
        heroFalls(g);
        g.action("recomecar");
        assertEquals(QuestState.CONCLUIDA, g.quests.get(Q).state());
        assertEquals(30, g.quests.coins());
        // recarregar e tentar tudo de novo
        g.setEncounter("trilha");
        g.action("salvar");
        GameLogic r = reload(saves);
        assertEquals(30, r.quests.coins());
        assertEquals("concluida", talk(r, "kelyra", 0));
        assertEquals(30, r.quests.coins());
        assertEquals(0, r.combat.alive());
        assertEquals("missao_concluida", DialogueBook.commentContext(r.quests.get(Q)));
    }

    @Test
    void bramorComentaSemSegundaMissao(@TempDir Path saves) {
        GameLogic g = started(saves);
        talk(g, "bramor", 0);
        int base = g.dialogLines.size();
        talk(g, "kelyra", 0);
        talk(g, "bramor", 0);
        assertEquals(base + 1, g.dialogLines.size(), "uma fala a mais durante a missão");
        assertEquals(1, g.quests.all().size());
    }

    // ------------------------------------------------------------------ interface (lógica)

    @Test
    void diarioParaALogicaEDevolveACamera(@TempDir Path saves) {
        GameLogic g = started(saves);
        talk(g, "kelyra", 0);
        g.setEncounter("trilha");                                       // fungos vivos: o diário não deixa a luta correr
        Enemy e = enemy(g, "trilha.fungo.1");
        g.player.place(e.x, e.z - 1.3f, 0f, g.collision);
        g.player.snapshot();
        frames(g, 30);
        float yaw = g.cam.curYaw, dist = g.cam.dist, play = g.playTime;
        long ticks = g.combat.ticks;
        int hp = g.combat.hero.hp;
        press(g, GLFW_KEY_J);
        assertEquals(GameLogic.State.JOURNAL, g.state);
        IN.feed(List.of(new InputEvent(Kind.KEY, 0, 0, 0, 0, GLFW_KEY_W)));
        for (int i = 0; i < 180; i++) { IN.feed(List.of()); g.update(1 / 60f, IN); }
        assertEquals(ticks, g.combat.ticks, "combate parado com o diário aberto");
        assertEquals(play, g.playTime, 1e-6f);
        assertEquals(hp, g.combat.hero.hp);
        press(g, GLFW_KEY_T);
        assertFalse(g.prefs.tracker, "T esconde o rastreador");
        press(g, GLFW_KEY_J);
        assertEquals(GameLogic.State.EXPLORE, g.state);
        assertEquals(yaw, g.cam.curYaw, 1e-4f, "a câmera volta como estava");
        assertEquals(dist, g.cam.dist, 0.05f);
        IN.feed(List.of(new InputEvent(Kind.KEY_UP, 0, 0, 0, 0, GLFW_KEY_W)));
    }

    @Test
    void recompensaApareceNaTelaEMoverDepoisDeCarregar(@TempDir Path saves) {
        GameLogic g = started(saves);
        talk(g, "kelyra", 0);
        assertTrue(g.toast.startsWith("Missão aceita"), g.toast);
        kill(g, "trilha.fungo.1");
        assertTrue(g.toast.contains("1/2"), g.toast);
        kill(g, "trilha.fungo.2");
        useBeacon(g);
        List<String> toasts = new ArrayList<>();
        g.onQuest = n -> { };
        String before = g.toast;
        talk(g, "kelyra", 0);
        assertNotEquals(before, g.toast);
        assertEquals("Missão concluída: As Luzes do Posto · +30 moedas", g.toast, "a recompensa aparece no aviso da entrega");
        toasts.add(g.toast);
        // carregar e andar: o WASD move o herói (nada preso do carregamento)
        GameLogic r = reload(saves);
        float x = r.player.x, z = r.player.z;
        IN.feed(List.of(new InputEvent(Kind.KEY, 0, 0, 0, 0, GLFW_KEY_W)));
        for (int i = 0; i < 40; i++) { IN.feed(List.of()); r.update(1 / 60f, IN); }
        IN.feed(List.of(new InputEvent(Kind.KEY_UP, 0, 0, 0, 0, GLFW_KEY_W)));
        r.update(1 / 60f, IN);
        assertTrue(Math.hypot(r.player.x - x, r.player.z - z) > 0.5f, "andou depois de carregar");
        assertEquals(1, toasts.size());
    }

    @Test
    void telaInicialOfereceContinuarSoComSaveValido(@TempDir Path saves) {
        GameLogic g = adventure(saves);
        assertEquals("Nenhum jogo salvo ainda", g.continueBlocker());
        g.action("continuar-jogo");
        assertEquals(GameLogic.State.SELECT, g.state, "sem save: fica na tela inicial");
        g.action("comecar");
        talk(g, "kelyra", 0);                                              // salva ao aceitar
        GameLogic r = adventure(saves);
        assertNull(r.continueBlocker());
        IN.feed(GameplayTest.key(GLFW_KEY_C));                              // C na tela inicial continua
        r.update(1 / 60f, IN);
        assertEquals(GameLogic.State.EXPLORE, r.state);
        assertEquals(vx.game.quest.QuestState.ACEITA, r.quests.get(Q).state());
        assertThrows(IllegalStateException.class, () -> r.quests.fromJson(new com.google.gson.JsonObject(), java.util.Set.of()),
                "não se carrega por cima de um jogo em andamento");
        r.action("continuar-jogo");                                         // já começou: recusa
        assertEquals(vx.game.quest.QuestState.ACEITA, r.quests.get(Q).state());
    }

    // ------------------------------------------------------------------ ferramentas técnicas fora do jogo normal

    @Test
    void jogoNormalNaoTrocaArenaNemSensacaoPelaPausa(@TempDir Path saves) {
        GameLogic g = started(saves);
        assertFalse(g.technical, "testes rodam sem -Dvx.tecnico");
        talk(g, "kelyra", 0);
        assertEquals("trilha", g.combat.encounter);
        String feel = g.prefs.feel;
        for (String a : List.of("encontro", "encontro:grupo", "encontro:chefe", "encontro:um", "sensacao", "sensacao:A")) g.action(a);
        assertEquals("trilha", g.combat.encounter, "a missão nunca perde os fungos pela pausa");
        assertEquals(2, g.combat.alive());
        assertEquals(feel, g.prefs.feel);
        assertEquals(g.combat.def.feelDefault, g.prefs.feel, "jogo normal usa a sensação aprovada");
        // modo técnico: as comparações continuam
        g.technical = true;
        g.action("encontro:grupo");
        assertEquals("grupo", g.combat.encounter);
        g.action("sensacao:A");
        assertEquals("A", g.prefs.feel);
    }

    // ------------------------------------------------------------------ posição salva inválida

    @Test
    void posicaoInvalidaVoltaAoPontoSeguro(@TempDir Path saves) throws Exception {
        GameLogic g = started(saves);
        talk(g, "kelyra", 0);
        Path f = saves.resolve(SaveGame.FILE);
        String json = Files.readString(f);
        // dentro da estalagem e do poço (sólidos) e fora do limite: todas voltam ao início
        for (float[] bad : new float[][]{{-6.5f, -8.0f}, {-1.8f, -6.4f}, {30f, 30f}}) {   // estalagem, poço, fora
            var o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            o.getAsJsonObject("jogador").addProperty("x", bad[0]);
            o.getAsJsonObject("jogador").addProperty("z", bad[1]);
            Files.writeString(f, SaveGame.text(o));
            GameLogic r = reload(saves);
            assertEquals(r.scene.startX, r.player.x, 1e-4f, "x " + bad[0]);
            assertEquals(r.scene.startZ, r.player.z, 1e-4f);
            assertTrue(r.safeSpot(r.player.x, r.player.z));
            assertEquals(QuestState.ACEITA, r.quests.get(Q).state(), "o progresso continua");
        }
    }

    @Test
    void saveInvalidoDesabilitaContinuarComMotivoEPreserva(@TempDir Path saves) throws Exception {
        Files.createDirectories(saves);
        Files.writeString(saves.resolve(SaveGame.FILE), "{ quebrado");
        GameLogic g = adventure(saves);
        String why = g.continueBlocker();
        assertNotNull(why);
        assertTrue(why.startsWith("Jogo salvo inválido"), why);
        g.action("continuar-jogo");
        assertEquals(GameLogic.State.SELECT, g.state);
        assertTrue(Files.exists(saves.resolve(SaveGame.FILE + ".invalido")));
        // jogo novo continua possível
        g.action("comecar");
        assertEquals(GameLogic.State.EXPLORE, g.state);
    }

    // ------------------------------------------------------------------ determinismo

    /** Luta na trilha com cliques a cada 0,4 s (chegando no começo de quadros de 1/30 s) e fungos de 1 de vida. */
    static String scripted(float fps, Path saves) {
        GameLogic g = started(saves);
        talk(g, "kelyra", 0);
        for (Enemy e : g.combat.enemies) e.hp = 1;
        g.player.place(-1.6f, 10.4f, 0f, g.collision);
        g.player.yaw = 0f;
        g.player.snapshot();
        g.cam.yaw = g.cam.curYaw = (float) Math.PI;
        g.acc = 0;
        List<long[]> ticks = new ArrayList<>();
        g.onQuest = n -> ticks.add(new long[]{g.combat.ticks, n.kind().ordinal()});
        Input in = new Input();
        double dt = 1.0 / fps;
        List<double[]> tl = new ArrayList<>();
        tl.add(new double[]{0.0, 0});
        tl.add(new double[]{0.7, 1});
        for (int k = 0; k < 20; k++) tl.add(new double[]{0.8 + 0.4 * k, 2});
        for (double[] ev : tl) ev[0] = Math.ceil(ev[0] * 30 - 1e-9) / 30;
        int next = 0;
        for (int f = 0; f * dt < 10.0; f++) {
            double t0 = f * dt;
            List<InputEvent> ev = new ArrayList<>();
            while (next < tl.size() && tl.get(next)[0] <= t0 + 1e-9) {
                int kind = (int) tl.get(next++)[1];
                switch (kind) {
                    case 0 -> ev.add(new InputEvent(Kind.KEY, 0, 0, 0, 0, GLFW_KEY_W));
                    case 1 -> ev.add(new InputEvent(Kind.KEY_UP, 0, 0, 0, 0, GLFW_KEY_W));
                    default -> ev.add(new InputEvent(Kind.DOWN, 0, 0, 0, 0, 0));
                }
            }
            in.feed(ev);
            g.update((float) dt, in);
        }
        StringBuilder sb = new StringBuilder(g.quests.toJson().toString());
        for (long[] t : ticks) sb.append(' ').append(t[0]).append(':').append(t[1]);
        return sb.toString();
    }

    @Test
    void mesmaMissaoA30EA144Quadros(@TempDir Path saves) {
        String a = scripted(30, saves.resolve("a")), b = scripted(144, saves.resolve("b"));
        assertEquals(a, b);
        assertTrue(a.contains("trilha.fungo"), "a luta derrubou fungo da missão (o teste não é vazio): " + a);
    }
}
