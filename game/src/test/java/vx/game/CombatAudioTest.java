package vx.game;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vx.engine.audio.AudioBackend;
import vx.engine.audio.SilentBackend;
import vx.engine.audio.Wav;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 09.1 — o som do combate, sem dispositivo de áudio: um backend que só registra ({@link Rec}) no lugar do
 * OpenAL. Propriedades: o som corresponde a eventos reais da lógica (acerto, bloqueio, dano), um por evento, nunca muda
 * a luta, independe da taxa de quadros, respeita pausa/recomeço/morte, e o jogo funciona sem dispositivo.
 */
class CombatAudioTest {
    static final float H = GameLogic.TICK;
    static final Path A = Path.of("assets");

    /** Backend registrador: guarda cada chamada; nunca toca nada. */
    static final class Rec implements AudioBackend {
        final List<String> calls = new ArrayList<>();
        int loads, playing, stopAll;
        float gain = -1;
        boolean paused;
        @Override public String describe() { return "registrador"; }
        @Override public boolean audible() { return false; }
        @Override public int load(String id, short[] mono, int rate) { loads++; return loads - 1; }
        @Override public int play(int sound, float g, float pitch, boolean spatial, float x, float y, float z) {
            calls.add("play " + sound);
            return ++playing;
        }
        @Override public void stop(int voice) { calls.add("stop " + voice); }
        @Override public void stopAll() { stopAll++; calls.add("stopAll"); }
        @Override public void pauseAll(boolean p) { paused = p; calls.add("pause " + p); }
        @Override public void listener(float x, float y, float z, float fx, float fy, float fz, float ux, float uy, float uz) { }
        @Override public void masterGain(float g) { gain = g; }
        @Override public int activeVoices() { return 0; }
        @Override public double reportedLatency() { return -1; }
        @Override public void close() { }
    }

    static Sfx attach(GameLogic g, Rec r) {
        Sfx s = new Sfx(r, A);
        r.calls.clear();                                                  // o aquecimento silencioso da criação não conta
        g.audio = new CombatAudio(s, g.combat);
        return s;
    }

    static long count(Sfx s, String cue) { return s.played.stream().filter(p -> p.cue().equals(cue)).count(); }

    static long events(Combat c, String kind) { return c.events.stream().filter(e -> e.kind().equals(kind)).count(); }

    /** Passos de combate com o som ligado (como o GameLogic faz: som depois de cada passo). */
    static void run(GameLogic g, float secs, java.util.function.BiFunction<Float, Combat, Hero.Intent> in) {
        int n = Math.round(secs / H);
        for (int i = 0; i < n; i++) {
            g.player.snapshot();
            g.combat.tick(H, in.apply(i * H, g.combat), g.player);
            g.audio.afterTick(g.combat);
        }
    }

    // ------------------------------------------------------------------ correspondência com a lógica

    @Test
    void aMissedSwingWhooshesButNeverSoundsLikeAHit() {
        GameLogic g = CombatTest.arena();
        Sfx s = attach(g, new Rec());
        g.player.place(-1.8f, 9.6f, 0, g.collision);
        CombatTest.putEnemy(g.combat, -1.8f, 12.4f, -1.8f, 9.6f, Enemy.State.IDLE);   // longe: fora de alcance
        g.combat.enemy.def.sight = 0f;
        g.combat.enemy.def.hearing = 0f;
        boolean[] once = {false};
        run(g, 1.3f, (t, c) -> { Hero.Intent i = once[0] ? CombatTest.idle() : CombatTest.click(); once[0] = true; return i; });
        assertEquals(0, events(g.combat, "acerto"));
        assertEquals(1, count(s, "golpe_ar_h"), "o corte no ar soa: " + s.played);
        assertEquals(0, count(s, "golpe_acerto"), "golpe que errou não tem som de acerto");
        assertEquals(0, count(s, "fungo_dano"));
    }

    @Test
    void aHitSoundsOnceOnTheContactTickAndTheWhooshPeaksInsideTheDamageWindow() {
        GameLogic g = CombatTest.arena();
        Sfx s = attach(g, new Rec());
        Player p = g.player;
        p.place(-1.8f, 10.4f, 0, g.collision);
        CombatTest.putEnemy(g.combat, -1.8f, 11.25f, p.x, p.z, Enemy.State.IDLE);
        CombatTest.face(p, g.combat.enemy.x, g.combat.enemy.z);
        g.combat.enemy.def.sight = 0f;
        List<Float> moveT = new ArrayList<>();
        boolean[] once = {false};
        int n = Math.round(1.3f / H);
        for (int i = 0; i < n; i++) {
            p.snapshot();
            g.combat.tick(H, once[0] ? CombatTest.idle() : CombatTest.click(), p);
            once[0] = true;
            g.audio.afterTick(g.combat);
            moveT.add(g.combat.hero.state == Hero.State.ATTACK ? g.combat.hero.layer.moveT() : -1f);
        }
        assertEquals(1, events(g.combat, "acerto"));
        assertEquals(1, count(s, "golpe_acerto"), "um acerto, um som: " + s.played);
        assertEquals(1, count(s, "fungo_dano"));
        long hitTick = g.combat.events.stream().filter(e -> e.kind().equals("acerto")).findFirst().orElseThrow().tick();
        Sfx.Played hit = s.played.stream().filter(q -> q.cue().equals("golpe_acerto")).findFirst().orElseThrow();
        assertEquals(hitTick, hit.tick(), "o som de acerto sai no passo em que a lógica confirmou o contato");
        // o pico do "fuu" (instante de tocar + pico do arquivo) cai dentro da janela de dano do golpe
        Sfx.Played wh = s.played.stream().filter(q -> q.cue().equals("golpe_ar_h")).findFirst().orElseThrow();
        CombatDef.Attack sh = g.combat.def.attacks.get("slash_h");
        int idx = (int) (wh.tick() - 1);
        float atPeak = moveT.get(idx) + s.peakTime("golpe_ar_h") / wh.pitch();
        assertTrue(atPeak >= sh.activeFrom() - H && atPeak <= sh.activeTo() + H,
                "pico do corte no ar fora da janela de dano: " + atPeak + " (" + sh.activeFrom() + "–" + sh.activeTo() + ")");
    }

    @Test
    void blockedAndReceivedHeadbuttsSoundDifferentAndOncePerContact() {
        GameLogic g = CombatTest.arena();
        Sfx s = attach(g, new Rec());
        Player p = g.player;
        p.place(-1.8f, 10.0f, 0, g.collision);
        CombatTest.putEnemy(g.combat, -1.8f, 10.9f, p.x, p.z, Enemy.State.CHASE);
        CombatTest.face(p, g.combat.enemy.x, g.combat.enemy.z);
        g.combat.hero.hp = 999;
        // 4 s parado (leva cabeçadas), depois 6 s de escudo erguido de frente para o fungo (bloqueia)
        run(g, 10f, (t, c) -> {
            float toE = (float) Math.atan2(c.enemy.x - p.x, c.enemy.z - p.z);
            return t < 4f ? CombatTest.idle() : new Hero.Intent().set(0, 0, false, false, true, toE);
        });
        Combat c = g.combat;
        long blocks = events(c, "defesa"), hurts = c.events.stream().filter(e -> e.text().startsWith("dano no herói")).count();
        assertTrue(blocks > 0 && hurts > 0, "a luta precisa ter bloqueio e dano: " + c.events);
        assertEquals(blocks, count(s, "escudo_bloqueio"), "um som de escudo por bloqueio: " + s.played);
        assertEquals(hurts, count(s, "cabecada_acerto"), "um som de cabeçada que pegou por dano");
        assertEquals(hurts, count(s, "heroi_dano"));
        assertEquals(blocks + hurts + events(c, "cabeçada sem efeito (invulnerável)"), count(s, "fungo_cabecada"),
                "cada cabeçada que saiu soa uma vez (movimento), com ou sem contato");
        for (Sfx.Played q : s.played)                                    // bloqueio nunca soa como dano no mesmo passo
            if (q.cue().equals("escudo_bloqueio")) assertTrue(s.played.stream().noneMatch(o -> o.tick() == q.tick() && o.cue().equals("heroi_dano")));
        for (int i = 1; i < s.played.size(); i++)                        // nunca o mesmo efeito duas vezes no mesmo passo
            for (int j = 0; j < i; j++)
                assertFalse(s.played.get(i).tick() == s.played.get(j).tick() && s.played.get(i).cue().equals(s.played.get(j).cue()),
                        "repetido: " + s.played.get(i));
    }

    @Test
    void theScriptedFightHitsSoundOncePerConfirmedHit() {
        Sfx[] s = new Sfx[1];
        GameLogic[] gg = new GameLogic[1];
        CombatTest.scriptedFight(60, 0, g -> { gg[0] = g; s[0] = attach(g, new Rec()); });
        Combat c = gg[0].combat;
        assertTrue(events(c, "acerto") >= 3, c.events.toString());
        assertEquals(events(c, "acerto"), count(s[0], "golpe_acerto"), "um som por acerto da espada");
        assertEquals(1, count(s[0], "fungo_derrota"));
        assertEquals(1, count(s[0], "vitoria"));
    }

    @Test
    void soundNeverChangesTheFightAndIsTheSameAtAnyFrameRate() {
        List<String> mute = CombatTest.scriptedFight(60, 0);
        List<List<String>> sounds = new ArrayList<>();
        for (float fps : new float[]{60, 30, 120, 240}) {
            Sfx[] s = new Sfx[1];
            List<String> withSound = CombatTest.scriptedFight(fps, 0, g -> s[0] = attach(g, new Rec()));
            assertEquals(mute, withSound, "o som mudou a luta a " + fps + " quadros/s");
            sounds.add(s[0].played.stream().map(q -> q.tick() + " " + q.cue() + " " + q.file() + " " + q.pitch()).toList());
        }
        for (List<String> l : sounds) assertEquals(sounds.getFirst(), l, "os sons dependem da taxa de quadros");
        assertTrue(sounds.getFirst().size() > 8, "luta com poucos sons: " + sounds.getFirst());
    }

    // ------------------------------------------------------------------ fim, pausa, recomeço

    @Test
    void aDeadFungusGoesQuietAndAWindupInterruptedByAHitIsCut() {
        GameLogic g = CombatTest.arena();
        Rec r = new Rec();
        Sfx s = attach(g, r);
        Player p = g.player;
        p.place(-1.8f, 10.4f, 0, g.collision);
        CombatTest.putEnemy(g.combat, -1.8f, 11.25f, p.x, p.z, Enemy.State.CHASE);
        CombatTest.face(p, g.combat.enemy.x, g.combat.enemy.z);
        g.combat.hero.hp = 999;
        g.combat.enemy.hp = 2;
        // espera a preparação começar e corta no meio dela
        run(g, 3f, (t, c) -> c.enemy.state == Enemy.State.WINDUP && c.enemy.stateT > 0.2f ? CombatTest.click() : CombatTest.idle());
        assertTrue(count(s, "fungo_preparo") >= 1, s.played.toString());
        int windupVoice = s.played.stream().filter(q -> q.cue().equals("fungo_preparo")).findFirst().orElseThrow().voice();
        assertTrue(r.calls.contains("stop " + windupVoice), "preparação interrompida continuou soando: " + r.calls);
        // termina a luta
        run(g, 6f, (t, c) -> c.enemy.alive() ? CombatTest.click() : CombatTest.idle());
        assertFalse(g.combat.enemy.alive());
        assertEquals(1, count(s, "fungo_derrota"));
        long deadAt = s.played.stream().filter(q -> q.cue().equals("fungo_derrota")).findFirst().orElseThrow().tick();
        run(g, 4f, (t, c) -> CombatTest.idle());
        // rodada 09.2: o único som depois da morte é o do corpo se desfazendo, uma vez, no instante em que ele se desfaz
        for (Sfx.Played q : s.played)
            if (q.tick() > deadAt && !q.cue().equals("fungo_desfaz")) assertFalse(q.cue().startsWith("fungo_"), "fungo morto ainda soando: " + q);
        assertEquals(1, count(s, "fungo_desfaz"));
        long broke = s.played.stream().filter(q -> q.cue().equals("fungo_desfaz")).findFirst().orElseThrow().tick();
        // a pausa de impacto do golpe final congela o morto também: BREAK s de jogo + a pausa
        assertEquals(Math.round((DeathFx.BREAK + g.combat.def.pauseHit) / GameLogic.TICK), broke - deadAt, 2, "desfaz-se BREAK s depois da morte");
        assertEquals(1, count(s, "vitoria"));
    }

    @Test
    void pauseFreezesSoundAndResumingDoesNotReplayAPileOfLateEffects() {
        GameLogic g = CombatTest.arena();
        Rec r = new Rec();
        Sfx s = attach(g, r);
        g.player.place(-1.8f, 9.6f, 0, g.collision);
        CombatTest.putEnemy(g.combat, -1.8f, 10.5f, -1.8f, 9.6f, Enemy.State.CHASE);
        Input in = new Input();
        for (int f = 0; f < 60; f++) { in.feed(List.of()); g.update(1f / 60f, in); }
        g.action("pausar");
        s.setPaused(g.state == GameLogic.State.PAUSE);                   // o Game faz isso a cada quadro
        assertTrue(r.paused);
        int before = s.played.size();
        for (int f = 0; f < 300; f++) { in.feed(List.of()); g.update(1f / 60f, in); }   // 5 s em pausa
        assertEquals(before, s.played.size(), "som novo durante a pausa");
        assertEquals(-1, s.cue("golpe_ar_h", g.combat.ticks + 1, 0, 0, 0), "a pausa aceita pedido de som");
        g.action("continuar");
        s.setPaused(false);
        assertFalse(r.paused);
        in.feed(List.of());
        g.update(1f / 60f, in);
        assertTrue(s.played.size() - before <= 2, "avalanche ao retomar: " + s.played.subList(before, s.played.size()));
    }

    @Test
    void restartingTheFightStopsEverythingAndForgetsThePast() {
        GameLogic g = CombatTest.arena();
        Rec r = new Rec();
        Sfx s = attach(g, r);
        g.player.place(-1.8f, 10.4f, 0, g.collision);
        CombatTest.putEnemy(g.combat, -1.8f, 11.25f, -1.8f, 10.4f, Enemy.State.CHASE);
        g.combat.enemy.hp = 1;
        run(g, 4f, (t, c) -> c.enemy.alive() ? CombatTest.click() : CombatTest.idle());
        assertEquals(1, count(s, "vitoria"), s.played.toString());
        int stops = r.stopAll;
        g.action("recomecar");
        assertTrue(r.stopAll > stops, "o recomeço não parou os sons");
        int after = s.played.size();
        Input in = new Input();
        for (int f = 0; f < 10; f++) { in.feed(List.of()); g.update(1f / 60f, in); }
        assertTrue(s.played.subList(after, s.played.size()).stream().noneMatch(q -> q.cue().equals("vitoria") || q.cue().equals("fungo_derrota")),
                "o passado tocou de novo: " + s.played.subList(after, s.played.size()));
    }

    @Test
    void rapidClicksNeverStackWhooshesBeyondTheSwingsThatHappened() {
        GameLogic g = CombatTest.arena();
        Sfx s = attach(g, new Rec());
        g.player.place(-1.8f, 9.6f, 0, g.collision);
        CombatTest.putEnemy(g.combat, -1.8f, 12.6f, -1.8f, 9.6f, Enemy.State.IDLE);
        g.combat.enemy.def.sight = 0f;
        g.combat.enemy.def.hearing = 0f;
        run(g, 3f, (t, c) -> CombatTest.click());                        // clique em TODO passo (360 cliques)
        long whooshes = s.played.stream().filter(q -> q.cue().startsWith("golpe_ar_")).count();
        assertTrue(whooshes <= g.combat.hero.swing && whooshes >= g.combat.hero.swing - 1,
                whooshes + " cortes no ar para " + g.combat.hero.swing + " golpes");
    }

    @Test
    void footstepsLandWithTheFeetAndStopWhenTheHeroStops() {
        GameLogic g = CombatTest.arena();
        Sfx s = new Sfx(new Rec(), A);
        g.steps = new Footsteps(s);
        g.combat.enemy.def.sight = 0f;
        g.combat.enemy.def.hearing = 0f;
        g.player.place(-1.0f, 3.0f, 0f, g.collision);
        g.cam.yaw = (float) Math.PI;
        Input in = new Input();
        in.feed(List.of(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY, 0, 0, 0, 0, org.lwjgl.glfw.GLFW.GLFW_KEY_W)));
        for (int f = 0; f < 180; f++) { g.update(1f / 60f, in); in.feed(List.of()); }        // 3 s andando
        long walking = count(s, "passo_terra");
        // dois pés por ciclo; o ciclo anda pela distância (rodada 09.2: velocidade e passada do perfil de sensação), menos a
        // aceleração do começo: 2 × cadência × velocidade / (velocidade de desenho × passada) por segundo
        double exp = 2 * vx.viewer.Animator.WALK_CADENCE * g.player.walk / (Player.WALK * g.player.stride) * 3;
        assertTrue(walking >= exp - 3 && walking <= exp + 1, walking + " passos em 3 s (esperado ~" + Math.round(exp) + ")");
        in.feed(List.of(new vx.engine.Window.InputEvent(vx.engine.Window.InputEvent.Kind.KEY_UP, 0, 0, 0, 0, org.lwjgl.glfw.GLFW.GLFW_KEY_W)));
        for (int f = 0; f < 120; f++) { g.update(1f / 60f, in); in.feed(List.of()); }
        assertTrue(count(s, "passo_terra") - walking <= 1, "passos com o herói parado");
    }

    // ------------------------------------------------------------------ volume, recursos, falha segura

    @Test
    void volumeAndMuteReachTheOutputAndNothingLoadsDuringPlay() {
        Rec r = new Rec();
        Sfx s = new Sfx(r, A);
        int loads = r.loads;
        assertTrue(loads >= s.cues().size(), "todos os efeitos carregados na criação");
        assertEquals(s.master * s.effects, r.gain, 1e-6f);
        s.setVolumes(0.5f, 0.8f, false);
        assertEquals(0.4f, r.gain, 1e-6f);
        s.setVolumes(0.5f, 0.8f, true);
        assertEquals(0f, r.gain, "mudo zera a saída");
        s.setVolumes(1.5f, -1f, false);
        assertEquals(0f, r.gain, 1e-6f, "volume fora de 0–1 é limitado");
        for (String cue : s.cues()) s.cue(cue, 1000 + cue.hashCode() % 7, 0, 0, 0);
        assertEquals(loads, r.loads, "tocar não pode carregar nada");
    }

    @Test
    void everyEffectIsGeneratedCleanAndEveryGeneratedFileIsUsed() throws Exception {
        var mix = JsonParser.parseString(Files.readString(A.resolve("scenes/sons_e.json"))).getAsJsonObject().getAsJsonObject("efeitos");
        var gen = JsonParser.parseString(Files.readString(A.resolve("audio/sintese/variantes.json"))).getAsJsonObject().getAsJsonObject("efeitos");
        assertEquals(mix.keySet(), gen.keySet(), "mistura × sons gerados");
        for (String cue : gen.keySet())
            for (var f : gen.getAsJsonArray(cue)) {
                Wav w = Wav.read(A.resolve("audio/sintese").resolve(f.getAsString()));
                assertEquals(1, w.channels, f + " precisa ser mono (espacial)");
                assertEquals(48000, w.rate, f.toString());
                int peak = 0;
                long sum = 0;
                for (short v : w.samples) { peak = Math.max(peak, Math.abs(v)); sum += v; }
                assertTrue(peak < 32767 * 0.72, f + ": pico acima de −2,9 dBFS (" + peak + ")");
                assertTrue(peak > 32767 * 0.69, f + ": não normalizado a −3 dBFS");
                assertTrue(Math.abs(sum / (double) w.samples.length) < 0.002 * 32767, f + ": DC");
                assertTrue(Math.abs(w.samples[0]) < 300 && Math.abs(w.samples[w.samples.length - 1]) < 300, f + ": estalo na borda");
            }
        for (String c : new String[]{"golpe_ar_h", "golpe_ar_d", "golpe_ar_v", "golpe_acerto", "escudo_bloqueio", "fungo_alerta", "fungo_preparo",
                "fungo_cabecada", "cabecada_acerto", "heroi_dano", "fungo_dano", "fungo_derrota", "vitoria", "derrota"})
            assertTrue(mix.has(c), "o diretor usa " + c);
    }

    @Test
    void withoutAnAudioDeviceTheGameKeepsGoingSilently() {
        AudioBackend b = Sfx.backendFor("dispositivo", () -> { throw new IllegalStateException("nenhum dispositivo OpenAL disponível"); }, null);
        assertInstanceOf(SilentBackend.class, b);
        assertTrue(b.describe().contains("nenhum dispositivo"), b.describe());
        AudioBackend c = Sfx.backendFor("dispositivo", () -> { throw new UnsatisfiedLinkError("libopenal.so"); }, null);
        assertInstanceOf(SilentBackend.class, c);
        // a luta inteira roda com o backend mudo
        Sfx[] s = new Sfx[1];
        List<String> fight = CombatTest.scriptedFight(60, 0, g -> { s[0] = new Sfx(b, A); g.audio = new CombatAudio(s[0], g.combat); });
        assertEquals(CombatTest.scriptedFight(60, 0), fight);
        assertFalse(s[0].played.isEmpty(), "o diretor continua decidindo (registro), só não há saída");
        assertTrue(s[0].played.stream().allMatch(q -> q.voice() == -1));
    }
}
