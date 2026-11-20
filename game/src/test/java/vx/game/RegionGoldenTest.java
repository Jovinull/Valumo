package vx.game;

import org.junit.jupiter.api.Test;
import vx.game.world.RegionGenerator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Referência permanente do gerador v1 (fixada na main publicada {@code worlds-and-menus-v0.5}, antes da integração dos
 * lotes de conteúdo): para 8 sementes, o sha256 da impressão digital completa da região — tentativa, arranjo, mata, lago,
 * colina, documentos de mundo/cena/combate (props, moradores, encontros), vínculo e id da missão, POIs e uma grade do
 * relevo. Os mundos salvos dependem disso: se este teste falhar, uma mudança alterou mundos existentes. Nunca atualize o
 * arquivo para fazer o teste passar; uma composição nova é OUTRA versão do gerador (ver ADR 0029).
 */
class RegionGoldenTest {
    static final long[] SEEDS = {1024, 2048, 4096, 77, 1, 555, 9001, 31337};
    static final Path GOLDEN = Path.of("game/src/test/resources/regioes-v1.txt");

    static Map<Long, String> current() throws Exception {
        Map<Long, String> out = new LinkedHashMap<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (long s : SEEDS) {
            ProceduralScene ps = ProceduralWorldTest.scene(s);                // gerador v1
            if (ps.version() != RegionGenerator.V1) throw new AssertionError("a referência é da v1");
            String fp = ProceduralWorldTest.fingerprint(ps) + "|quest=" + (ps.quest == null ? "-" : ps.quest.id());
            out.put(s, HexFormat.of().formatHex(md.digest(fp.getBytes(StandardCharsets.UTF_8))) + " tentativa=" + ps.layout.attempt
                    + " arranjo=" + ps.layout.arrangement + " props=" + ps.layout.props.size() + " pois=" + ps.layout.pois.size());
        }
        return out;
    }

    @Test
    void asRegioesDoGeradorV1NaoMudam() throws Exception {
        assertTrue(RegionGenerator.supports(RegionGenerator.V1), "a v1 continua suportada: os mundos v1 abrem para sempre (ADR 0029)");
        Map<Long, String> now = current();
        List<String> lines = Files.readAllLines(GOLDEN);
        List<String> diff = new ArrayList<>();
        int checked = 0;
        for (String l : lines) {
            if (l.isBlank() || l.startsWith("#")) continue;
            String[] p = l.split(" ", 2);
            long seed = Long.parseLong(p[0]);
            checked++;
            if (!p[1].equals(now.get(seed))) diff.add("semente " + seed + ": era " + p[1] + ", agora " + now.get(seed));
        }
        assertEquals(SEEDS.length, checked, "a referência cobre as 8 sementes");
        assertTrue(diff.isEmpty(), "regiões do gerador v1 mudaram:\n" + String.join("\n", diff));
    }
}
