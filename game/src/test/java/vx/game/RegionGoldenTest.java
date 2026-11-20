package vx.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 *
 * <p>Rodada 12.1 — o documento de combate da região é uma CÓPIA do {@code combate_e.json} inteiro com a arena e o encontro
 * gerados por cima; a impressão digital cobria então os números globais de combate e movimento (sensação, salto,
 * esquiva…), e ajustar o salto "mudava" as regiões v1 sem que nada do gerador mudasse (conferido: o mesmo binário, com o
 * {@code combate_e.json} de antes, bate as 8 sementes das duas versões). Agora a impressão digital usa os números globais
 * COMO ESTAVAM na referência ({@code combate-da-referencia.json}, o {@code combate_e.json} da rodada 12 byte a byte) com
 * as partes GERADAS da região reaplicadas na mesma ordem (centro, raio e recomeço da arena; os encontros): mudar o
 * gerador continua mudando o hash; ajustar o combate não. Com os números de antes o documento é idêntico ao antigo, por
 * isso nenhuma linha das referências mudou.
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
            String fp = ProceduralWorldTest.fingerprint(ps, referenceCombat(ps.combatDoc)) + "|quest=" + (ps.quest == null ? "-" : ps.quest.id());
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

    /** O {@code combate_e.json} da referência com as partes geradas desta região (as mesmas operações do ProceduralScene). */
    static JsonObject referenceCombat(JsonObject generated) throws Exception {
        JsonObject c = JsonParser.parseString(Files.readString(Path.of("game/src/test/resources/combate-da-referencia.json"))).getAsJsonObject();
        JsonObject arena = c.getAsJsonObject("arena"), g = generated.getAsJsonObject("arena");
        arena.add("centro", g.get("centro").deepCopy());
        arena.add("raio", g.get("raio").deepCopy());
        arena.add("recomeco", g.get("recomeco").deepCopy());
        c.add("encontros", generated.get("encontros").deepCopy());
        return c;
    }

    /** As partes do documento de combate da região que NÃO vêm do gerador são cópia do combate_e.json (base da regra acima). */
    @Test
    void oDocumentoDeCombateDaRegiaoSoDifereDaBaseNasPartesGeradas() throws Exception {
        JsonObject base = JsonParser.parseString(Files.readString(ProceduralWorldTest.A.resolve("scenes/combate_e.json"))).getAsJsonObject();
        for (int v : new int[]{RegionGenerator.V1, RegionGenerator.V2}) {
            ProceduralScene ps = v == RegionGenerator.V1 ? ProceduralWorldTest.scene(1024) : ProceduralScene.create(ProceduralWorldTest.A, 1024, v);
            JsonObject doc = ps.combatDoc.deepCopy();
            JsonObject arena = doc.getAsJsonObject("arena"), b = base.getAsJsonObject("arena");
            for (String k : new String[]{"centro", "raio", "recomeco"}) { arena.remove(k); }
            JsonObject b2 = base.deepCopy();
            for (String k : new String[]{"centro", "raio", "recomeco"}) b2.getAsJsonObject("arena").remove(k);
            doc.remove("encontros");
            b2.remove("encontros");
            assertEquals(b2, doc, "v" + v + ": fora da arena e dos encontros, o documento de combate é o combate_e.json");
            assertNotNull(b);
        }
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
