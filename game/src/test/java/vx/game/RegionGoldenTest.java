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
    /** Rodada 12: a referência da v2, fixada quando a composição dela foi aprovada nesta rodada (ADR 0029). */
    static final Path GOLDEN_V2 = Path.of("game/src/test/resources/regioes-v2.txt");

    static Map<Long, String> current() throws Exception { return current(RegionGenerator.V1); }

    /**
     * Impressão digital de cada semente na versão pedida. A da v1 é EXATAMENTE a de antes (a referência foi escrita com
     * ela); a da v2 acrescenta o estilo do assentamento, as construções e as trilhas secundárias.
     */
    static Map<Long, String> current(int version) throws Exception {
        Map<Long, String> out = new LinkedHashMap<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (long s : SEEDS) {
            ProceduralScene ps = version == RegionGenerator.V1 ? ProceduralWorldTest.scene(s) : ProceduralScene.create(ProceduralWorldTest.A, s, version);
            if (ps.version() != version) throw new AssertionError("versão errada");
            String fp = ProceduralWorldTest.fingerprint(ps) + "|quest=" + (ps.quest == null ? "-" : ps.quest.id());
            if (version >= RegionGenerator.V2) {
                StringBuilder b = new StringBuilder("|estilo=").append(ps.layout.style);
                for (var bd : ps.layout.buildings) b.append('|').append(bd);
                for (var br : ps.layout.branches) { b.append("|ramo"); for (float[] q : br.points()) b.append(',').append(q[0]).append(',').append(q[1]); }
                for (var cp : ps.layout.checkpoints) b.append('|').append(cp);
                fp += b;
            }
            out.put(s, HexFormat.of().formatHex(md.digest(fp.getBytes(StandardCharsets.UTF_8))) + " tentativa=" + ps.layout.attempt
                    + " arranjo=" + ps.layout.arrangement + " props=" + ps.layout.props.size() + " pois=" + ps.layout.pois.size());
        }
        return out;
    }

    static void check(Path golden, int version) throws Exception {
        Map<Long, String> now = current(version);
        List<String> lines = Files.readAllLines(golden);
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
        assertTrue(diff.isEmpty(), "regiões do gerador v" + version + " mudaram:\n" + String.join("\n", diff));
    }

    @Test
    void asRegioesDoGeradorV1NaoMudam() throws Exception {
        assertTrue(RegionGenerator.supports(RegionGenerator.V1), "a v1 continua suportada: os mundos v1 abrem para sempre (ADR 0029)");
        check(GOLDEN, RegionGenerator.V1);
    }

    /** Rodada 12 — a v2 também fica congelada a partir da aprovação: mudou um hash = um mundo v2 do jogador mudou. */
    @Test
    void asRegioesDoGeradorV2NaoMudam() throws Exception {
        assertTrue(RegionGenerator.supports(RegionGenerator.V2));
        check(GOLDEN_V2, RegionGenerator.V2);
    }

    /**
     * Escreve a referência de UMA versão nova, uma vez, quando a composição dela é aprovada:
     * {@code VX_REFERENCIA_ESCREVER=2 ./gradlew --no-daemon :game:test --tests vx.game.RegionGoldenTest} (variável de ambiente: o
     * teste roda num processo à parte, que a herda). Recusa a v1 (ela é a
     * proteção dos mundos que já existem) e recusa sobrescrever uma referência que já existe.
     */
    @Test
    void escreveAReferenciaDeUmaVersaoNovaQuandoPedido() throws Exception {
        String v = System.getenv("VX_REFERENCIA_ESCREVER");
        org.junit.jupiter.api.Assumptions.assumeTrue(v != null, "só quando pedido");
        int version = Integer.parseInt(v);
        assertNotEquals(RegionGenerator.V1, version, "a referência da v1 nunca é reescrita");
        Path out = Path.of("game/src/test/resources/regioes-v" + version + ".txt");
        assertFalse(Files.exists(out), out + " já existe: uma versão aprovada não muda (ADR 0029)");
        StringBuilder b = new StringBuilder("# Gerador v" + version + " (" + RegionGenerator.ID + ") — referência fixada na aprovação da composição. Não edite à mão.\n");
        for (var e : current(version).entrySet()) b.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        Files.writeString(out, b.toString());
    }
}
