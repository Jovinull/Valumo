package vx.game;

import org.junit.jupiter.api.Test;
import vx.viewer.Animator;
import vx.viewer.World;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Rodada 09.4 — o som do jogo sai dos fatos certos: pisada no instante do pé, superfície e ações. */
class GameAudioTest {
    static Collision flat() {
        Collision c = new Collision((x, z) -> 0f);
        c.waterLevel = -100;
        return c;
    }

    /** Anda reto por {@code secs} e devolve os instantes (s) de cada pisada. */
    static List<Float> steps(boolean run, float secs, float dt, List<Float> phasesAtStep) {
        Collision c = flat();
        Player p = new Player();
        p.place(0, 0, 0, c);
        List<Float> t = new ArrayList<>();
        int last = p.steps;
        for (float time = 0; time < secs; time += dt) {
            p.update(dt, 0, 1, run, c);
            if (p.steps != last) {
                last = p.steps;
                t.add(time);
                if (phasesAtStep != null) phasesAtStep.add(p.animT * (p.clip == Animator.Clip.RUN ? Animator.RUN_CADENCE : Animator.WALK_CADENCE) * 2);
            }
        }
        return t;
    }

    @Test
    void footstepsFollowTheGaitCadenceAndLandOnTheContactPhase() {
        List<Float> ph = new ArrayList<>();
        List<Float> walk = steps(false, 6, 1 / 60f, ph);
        // regime (depois de acelerar): meia passada = 1 / (2 × cadência)
        List<Float> tail = walk.subList(walk.size() / 2, walk.size());
        float mean = (tail.getLast() - tail.getFirst()) / (tail.size() - 1);
        assertEquals(1f / (2 * Animator.WALK_CADENCE), mean, 0.01f, "intervalo entre passos andando");
        for (float f : ph) {
            float frac = f - (float) Math.floor(f);
            assertTrue(frac < 0.12f, "pisada longe do contato do pé (fração " + frac + " da meia passada)");
        }
        List<Float> run = steps(true, 6, 1 / 60f, null);
        List<Float> rt = run.subList(run.size() / 2, run.size());
        float rmean = (rt.getLast() - rt.getFirst()) / (rt.size() - 1);
        assertEquals(1f / (2 * Animator.RUN_CADENCE), rmean, 0.01f, "intervalo entre passos correndo");
    }

    @Test
    void sameCountAtDifferentFrameRates() {
        int a = steps(false, 5, 1 / 30f, null).size(), b = steps(false, 5, 1 / 144f, null).size();
        assertTrue(Math.abs(a - b) <= 1, "30 × 144 FPS: " + a + " × " + b + " passos");
    }

    @Test
    void standingStillMakesNoFootsteps() {
        Collision c = flat();
        Player p = new Player();
        p.place(0, 0, 0, c);
        for (int i = 0; i < 600; i++) p.update(1 / 60f, 0, 0, false, c);
        assertEquals(0, p.steps);
        for (int i = 0; i < 120; i++) p.update(1 / 60f, 0, 1, false, c);  // anda 2 s e para
        int moved = p.steps;
        assertTrue(moved >= 6);
        for (int i = 0; i < 240; i++) p.update(1 / 60f, 0, 0, false, c);
        assertTrue(p.steps - moved <= 1, "depois de parar no máximo o pé que já estava descendo");
    }

    @Test
    void surfaceAndActionMapping() {
        assertEquals("passo.grama.andar", GameAudio.stepEvent(World.SURF_GRASS, false));
        assertEquals("passo.grama.correr", GameAudio.stepEvent(World.SURF_FOREST, true));
        assertEquals("passo.terra.andar", GameAudio.stepEvent(World.SURF_PATH, false));
        assertEquals("passo.areia.correr", GameAudio.stepEvent(World.SURF_SAND, true));
        assertEquals("passo.pedra.andar", GameAudio.stepEvent(World.SURF_ROCK, false));
        // o caminho da vila é terra; longe dele, grama
        assertEquals(World.SURF_PATH, World.surfaceAt(-2.5f, 0.5f), "ponto do eixo do caminho");
        assertEquals(World.SURF_GRASS, World.surfaceAt(5f, -3f));
        assertEquals("ui.confirmar", GameAudio.uiEvent("comecar"));
        assertEquals("ui.abrir", GameAudio.uiEvent("pausar"));
        assertEquals("ui.cancelar", GameAudio.uiEvent("continuar"));
        assertEquals("ui.selecionar", GameAudio.uiEvent("escolha"));
        assertNull(GameAudio.uiEvent("encerrar-fala"), "o fim da fala avisa sozinho (fala-fim)");
    }

    @Test
    void volumePreferencesRoundTripClampAndSurviveABadFile() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("vx-prefs"), f = dir.resolve("audio.json");
        AudioPrefs p = AudioPrefs.load(f);
        assertEquals(1f, p.music);                                        // sem arquivo: 100%
        assertFalse(p.nudge("musica", 1), "não passa de 100%");
        for (int i = 0; i < 3; i++) assertTrue(p.nudge("musica", -1));
        for (int i = 0; i < 20; i++) p.nudge("efeitos", -1);
        assertEquals(0.7f, p.music, 1e-6f);
        assertEquals(0f, p.effects, 1e-6f, "não passa de 0%");
        p.save(f);
        AudioPrefs q = AudioPrefs.load(f);
        assertEquals(0.7f, q.music, 1e-6f);
        assertEquals(0f, q.effects, 1e-6f);
        java.nio.file.Files.writeString(f, "{ quebrado");
        assertEquals(1f, AudioPrefs.load(f).music, "arquivo inválido → padrão, sem exceção");
    }

    /** Auditoria de engenharia: `tools/review94.sh pausa` passava -Dvx.prefs=ARQUIVO e gravava no áudio REAL do jogador. */
    @Test
    void redirectedPreferencesNeverTouchThePlayersAudioFile() {
        java.nio.file.Path real = java.nio.file.Path.of("output/preferencias/audio.json");
        assertEquals(real, AudioPrefs.fileFor(null, null), "sem propriedades: o arquivo do jogador");
        assertNull(AudioPrefs.fileFor("-", null), "roteiros e testes: nem lê nem grava");
        assertNull(AudioPrefs.fileFor("-", "x/audio.json"));
        assertEquals(java.nio.file.Path.of("x/audio.json"), AudioPrefs.fileFor(null, "x/audio.json"));
        java.nio.file.Path redirected = AudioPrefs.fileFor("output/tmp/prefs-revisao.json", null);
        assertNotEquals(real, redirected, "preferências redirecionadas levam o áudio junto");
        assertEquals(java.nio.file.Path.of("output/tmp/prefs-revisao.json.audio.json"), redirected);
    }
}
