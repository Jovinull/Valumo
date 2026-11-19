package vx.viewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fechamento do QA de engenharia — arquivo de aparência malformado vira {@link Appearance.InvalidAppearance} (a escolha do
 * jogo e o criador mostram "arquivo inválido"), nunca outra exceção que derruba o criador. Pasta temporária.
 */
@Timeout(10)
class AppearanceRobustnessTest {
    static final ComponentCatalog CAT = ComponentCatalog.load(Path.of("assets"));

    @Test
    void wrongTypesMissingFieldsAndOddRootsAreInvalidAppearances() {
        String ok = Appearance.preset("aventureiro").toJson();
        assertNotNull(Appearance.fromJson(ok, CAT), "a aparência válida continua lendo");
        String[] bad = {"", "[]", "3", "null", "{", "{\"contrato\": {\"a\": 1}}",
                ok.replaceFirst("\"versao\": *2", "\"versao\": \"dois\""),
                ok.replaceFirst("\"versao\": *2", "\"versao\": NaN"),
                ok.replaceFirst("\"versao\": *2", "\"versao\": 2.5e400"),
                ok.replaceFirst("\"preset\": *\"aventureiro\"", "\"preset\": [1, 2]"),
                ok.replaceFirst("\"preset\": *\"aventureiro\"", "\"preset\": {}")};
        for (String json : bad) {
            if (json.equals(ok)) fail("substituição não aconteceu (formato do toJson mudou?)");
            assertThrows(Appearance.InvalidAppearance.class, () -> Appearance.fromJson(json, CAT), json);
        }
    }

    @Test
    void aHugeFileIsRefusedBeforeReading(@TempDir Path d) throws IOException {
        Path f = d.resolve("grande.json");
        Files.writeString(f, "{\"x\": \"" + "a".repeat(400_000) + "\"}");
        assertThrows(Appearance.InvalidAppearance.class, () -> Appearance.load(f, CAT));
    }
}
