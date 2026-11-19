package vx.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fechamento do QA de engenharia — preferências e configuração de combate malformadas. Preferência do jogador ilegível:
 * os padrões, sem exceção, e o ORIGINAL guardado ao lado ({@code .invalido}) antes da próxima gravação. Configuração de
 * combate do projeto (combate_e.json) quebrada: erro claro com o arquivo, nunca um NaN que "funciona". Tudo em pasta
 * temporária.
 */
@Timeout(10)
class CorruptPrefsTest {

    static Prefs prefs(Path f) { return new Prefs(f, "dano", "C", "um").load(); }

    @Test
    void unreadablePlayerPrefsFallBackToDefaultsAndKeepTheOriginal(@TempDir Path d) throws IOException {
        String[] bad = {"", "{", "[1,2,3]", "{\"sensacao\": 3}", "{\"barrasInimigos\": {\"x\": 1}}", "{\"encontro\": null}", "nada"};
        for (String content : bad) {
            Path f = d.resolve("p.json");
            Files.writeString(f, content);
            Prefs p = prefs(f);
            assertEquals("dano", p.bars, content);
            assertEquals("C", p.feel, content);
            assertEquals("um", p.encounter, content);
            Path keep = d.resolve("p.json.invalido");
            assertTrue(Files.isRegularFile(keep), "original guardado: " + content);
            assertEquals(content, Files.readString(keep));
            p.save();                                                    // a gravação seguinte não apaga o diagnóstico
            assertEquals(content, Files.readString(keep));
        }
    }

    @Test
    void aHugePrefsFileIsNotRead(@TempDir Path d) throws IOException {
        Path f = d.resolve("p.json");
        Files.writeString(f, "{\"sensacao\": \"" + "A".repeat(200_000) + "\"}");
        assertEquals("C", prefs(f).feel);
    }

    @Test
    void aComparisonFromTheCommandLineIsNotSavedAsThePlayersChoice(@TempDir Path d) throws IOException {
        Path f = d.resolve("p.json");
        Files.writeString(f, "{\"barrasInimigos\": \"sempre\", \"sensacao\": \"B\", \"encontro\": \"grupo\"}");
        Prefs p = prefs(f);
        p.feel = "A";                                                    // -Dvx.sensacao=A, -Dvx.encontro=chefe
        p.feelFromCommandLine = true;
        p.encounter = "chefe";
        p.encounterFromCommandLine = true;
        p.cycleBars();                                                   // um ajuste qualquer na pausa grava o arquivo
        Prefs again = prefs(f);
        assertEquals("nunca", again.bars, "o ajuste feito vale");
        assertEquals("B", again.feel, "a comparação da sessão não virou preferência");
        assertEquals("grupo", again.encounter);
        p.feelFromCommandLine = false;                                   // o jogador escolheu na pausa: aí vale
        p.save();
        assertEquals("A", prefs(f).feel);
    }

    @Test
    void unreadableAudioPrefsFallBackToFullVolumeAndKeepTheOriginal(@TempDir Path d) throws IOException {
        String[] bad = {"{\"contrato\": \"valumo.preferencias\", \"versao\": 1, \"musica\": NaN, \"efeitos\": 0.5}",
                "{\"contrato\": \"valumo.preferencias\", \"versao\": 1, \"musica\": \"alto\", \"efeitos\": 0.5}",
                "{\"contrato\": \"valumo.preferencias\", \"versao\": 1, \"musica\": Infinity, \"efeitos\": 0.5}",
                "{\"contrato\": [], \"versao\": 1}", "", "{"};
        for (String content : bad) {
            Path f = d.resolve("a.json");
            Files.writeString(f, content);
            AudioPrefs a = AudioPrefs.load(f);
            assertEquals(1f, a.music, content);
            assertEquals(1f, a.effects, content);
            assertTrue(Float.isFinite(a.music) && Float.isFinite(a.effects));
            assertEquals(content, Files.readString(d.resolve("a.json.invalido")), content);
        }
        Path ok = d.resolve("ok.json");
        Files.writeString(ok, "{\"contrato\": \"valumo.preferencias\", \"versao\": 1, \"musica\": 0.3, \"efeitos\": 7}");
        AudioPrefs a = AudioPrefs.load(ok);
        assertEquals(0.3f, a.music, 1e-6f);
        assertEquals(1f, a.effects, "fora da faixa é limitado, não recusado");
    }

    @Test
    void aBrokenCombatConfigFailsClearlyNamingTheFile(@TempDir Path d) throws IOException {
        Files.createDirectories(d.resolve("scenes"));
        String good = Files.readString(Path.of("assets/scenes/combate_e.json"));
        assertNotNull(CombatDef.load(Path.of("assets"), "combate_e"), "o arquivo real carrega");
        String[][] breaks = {
                {"\"vida\":", "\"vida\": NaN, \"_x\":"},
                {"\"vida\":", "\"vida\": \"seis\", \"_x\":"},
        };
        for (String[] b : breaks) {
            assertTrue(good.contains(b[0]), "o arquivo real tem " + b[0]);
            Files.writeString(d.resolve("scenes/quebrado.json"), good.replaceFirst(java.util.regex.Pattern.quote(b[0]), b[1]));
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> CombatDef.load(d, "quebrado"), b[1]);
            assertTrue(e.getMessage().contains("quebrado.json"), e.getMessage());
        }
        Files.writeString(d.resolve("scenes/quebrado.json"), "[]");
        assertThrows(IllegalStateException.class, () -> CombatDef.load(d, "quebrado"));
    }
}
