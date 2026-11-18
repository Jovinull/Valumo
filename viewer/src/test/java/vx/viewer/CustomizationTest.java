package vx.viewer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vx.engine.VoxGrid;
import vx.engine.VoxModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 07 — personalização em tempo de execução (Composer, ComponentCatalog, Appearance). Rode build_assets
 * antes. O gate principal: um preset sem mudanças é idêntico ao rig canônico em voxels, cores, malha, pivôs,
 * hierarquia e expressões — sem tolerância.
 */
class CustomizationTest {
    static final Path A = Path.of("assets");
    static final Map<String, String> CANON = Map.of("aventureiro", "characters/e/adventurer",
            "exploradora", "characters/e/ranger", "maga", "characters/e/mage");
    static final Composer C = new Composer(A, 4);

    static JsonObject rig(String dir) throws IOException {
        return JsonParser.parseString(Files.readString(A.resolve(dir).resolve("rig.json"))).getAsJsonObject();
    }

    static VoxGrid canonGrid(String dir, JsonObject p, String alt) {
        JsonObject src = alt == null ? p : p.getAsJsonObject("alts").getAsJsonObject(alt);
        var o = src.getAsJsonArray("origin");
        return VoxGrid.of(VoxModel.load(A.resolve(dir).resolve(src.get("file").getAsString())), o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt());
    }

    @Test
    void presetsRecomposeToTheCanonicalRigExactly() throws IOException {
        for (var e : CANON.entrySet()) {
            Composer.ComposedRig r = C.compose(Appearance.preset(e.getKey()));
            JsonObject doc = rig(e.getValue());
            List<String> names = new ArrayList<>();
            for (var pe : doc.getAsJsonArray("parts")) names.add(pe.getAsJsonObject().get("name").getAsString());
            List<String> got = new ArrayList<>();
            for (Composer.Piece p : r.pieces) got.add(p.name);
            assertEquals(names, got, e.getKey() + ": mesmas partes na mesma ordem");
            for (var pe : doc.getAsJsonArray("parts")) {
                JsonObject p = pe.getAsJsonObject();
                Composer.Piece c = r.piece(p.get("name").getAsString());
                assertEquals(canonGrid(e.getValue(), p, null).cells(), c.grid.cells(), e.getKey() + "/" + c.name + ": voxels e cores");
                var pv = p.getAsJsonArray("pivot");
                assertArrayEquals(new float[]{pv.get(0).getAsFloat(), pv.get(1).getAsFloat(), pv.get(2).getAsFloat()}, c.pivot, 0f, c.name + ": pivô");
                assertEquals(p.get("parent").isJsonNull() ? null : p.get("parent").getAsString(), c.parent, c.name + ": pai");
                if (p.has("alts")) {
                    assertEquals(Set.of("joy", "determined", "surprise"), c.alts.keySet());
                    for (String ex : c.alts.keySet())
                        assertEquals(canonGrid(e.getValue(), p, ex).cells(), c.alts.get(ex).cells(), e.getKey() + "/head:" + ex);
                }
            }
        }
    }

    @Test
    void presetMeshDataIsIdenticalSoThereIsNoSeamNorInternalFace() throws IOException {
        // a costura da 06.1 vinha de malhas separadas para cabeça e cabelo; aqui a cabeça é UMA grade fundida
        for (var e : CANON.entrySet()) {
            Composer.ComposedRig r = C.compose(Appearance.preset(e.getKey()));
            for (var pe : rig(e.getValue()).getAsJsonArray("parts")) {
                JsonObject p = pe.getAsJsonObject();
                Composer.Piece c = r.piece(p.get("name").getAsString());
                assertArrayEquals(Composer.meshData(canonGrid(e.getValue(), p, null), r.voxelSize, false),
                        Composer.meshData(c.grid, r.voxelSize, false), e.getKey() + "/" + c.name + ": malha idêntica");
            }
        }
    }

    @Test
    void composedPresetsAndCustomCharactersKeepValidAnimation() {
        for (String pid : CANON.keySet()) {
            for (Appearance a : List.of(Appearance.preset(pid), custom(pid, "cabelo.curto", "#2e2632", "#c98a5e", "chapeu.maga"))) {
                CharacterModel m = C.build(C.compose(a), false);
                Animator an = new Animator(m, 1f);
                List<String> seqs = new ArrayList<>(List.of("clip:IDLE", "clip:WALK", "clip:RUN", "hit", "emote"));
                seqs.add(an.prof.attack);
                for (String s : seqs) assertTrue(WeaponCheck.validate(m, s).ok(), pid + " " + a.signature() + " / " + s);
            }
        }
    }

    static Appearance custom(String preset, String hair, String color, String skin, String cover) {
        Appearance a = Appearance.preset(preset);
        a.hair = hair;
        a.hairColor = color;
        a.skin = skin;
        a.cover = cover;
        return a;
    }

    static Set<Long> hairCells(String style) {
        JsonObject h = C.catalog.components.get(style).doc.getAsJsonObject("cabeca");
        var o = h.getAsJsonArray("origin");
        return VoxGrid.of(VoxModel.load(C.catalog.root.resolve(h.get("file").getAsString())), o.get(0).getAsInt(), o.get(1).getAsInt(), o.get(2).getAsInt()).cells().keySet();
    }

    @Test
    void atLeastFourHairStylesWithDifferentShapes() {
        List<String> ids = C.catalog.ids("cabelo");
        assertTrue(ids.size() >= 4, "estilos: " + ids);
        for (int i = 0; i < ids.size(); i++)
            for (int j = i + 1; j < ids.size(); j++) {
                Set<Long> a = hairCells(ids.get(i)), b = hairCells(ids.get(j));
                Set<Long> in = new HashSet<>(a); in.retainAll(b);
                Set<Long> un = new HashSet<>(a); un.addAll(b);
                assertTrue(in.size() / (double) un.size() < 0.8, ids.get(i) + " × " + ids.get(j) + ": volume quase igual (recoloração?)");
            }
    }

    @Test
    void hairColorKeepsTheRampAndTheOriginalColorChangesNothing() {
        Appearance base = Appearance.preset("aventureiro");
        Appearance same = base.copy();
        same.hairColor = "#c2582c";                                           // a cor do próprio preset
        assertEquals(C.compose(base).piece("head").grid.cells(), C.compose(same).piece("head").grid.cells());
        for (String[] col : C.catalog.colors.get("cabelo")) {
            Appearance a = base.copy();
            a.hairColor = col[1];
            Map<Long, Integer> orig = C.compose(base).piece("head").grid.cells(), got = C.compose(a).piece("head").grid.cells();
            assertEquals(orig.keySet(), got.keySet(), "a cor não muda a forma");
            Set<Integer> before = new HashSet<>(), after = new HashSet<>();
            for (long k : hairCells("cabelo.topete")) { before.add(orig.get(k)); after.add(got.get(k)); }
            assertTrue(after.size() >= before.size() - 1, col[0] + ": rampa achatada (" + before.size() + " → " + after.size() + " tons)");
        }
    }

    @Test
    void recolorFollowsShadowAndLightRelationsExactlyOnTheOriginalBase() {
        int base = 0xc2582c;
        for (int c : new int[]{0x973e21, 0xd98a52, base, 0x5a2a18})
            assertEquals((1 << 24) | c, Composer.recolor((1 << 24) | c, base, base));
        int dark = Composer.recolor((1 << 24) | 0x973e21, base, 0x6b4630), lit = Composer.recolor((1 << 24) | 0xd98a52, base, 0x6b4630);
        assertTrue(lum(dark) < lum((1 << 24) | 0x6b4630) && lum(lit) > lum((1 << 24) | 0x6b4630), "sombra continua mais escura e luz mais clara");
    }

    static double lum(int c) { return 0.3 * ((c >> 16) & 255) + 0.59 * ((c >> 8) & 255) + 0.11 * (c & 255); }

    @Test
    void hatSitsOnEveryHairStyleWithoutPokingThroughOrLeavingAGap() {
        for (String style : C.catalog.ids("cabelo")) {
            Appearance a = custom("aventureiro", style, null, null, "chapeu.maga");
            Composer.ComposedRig r = C.compose(a);
            VoxGrid head = r.piece("head").grid, hat = r.piece("hat").grid;
            Map<Long, Integer> topOfHat = new java.util.HashMap<>(), bottom = new java.util.HashMap<>(), headTop = new java.util.HashMap<>();
            int lowest = Integer.MAX_VALUE;
            for (long k : hat.cells().keySet()) {
                long col = VoxGrid.key(VoxGrid.kx(k), 0, VoxGrid.kz(k));
                topOfHat.merge(col, VoxGrid.ky(k), Math::max);
                bottom.merge(col, VoxGrid.ky(k), Math::min);
                lowest = Math.min(lowest, VoxGrid.ky(k));
            }
            for (long k : head.cells().keySet()) {
                assertFalse(hat.has(k), style + ": cabelo e chapéu no mesmo voxel");
                Integer t = topOfHat.get(VoxGrid.key(VoxGrid.kx(k), 0, VoxGrid.kz(k)));
                assertFalse(t != null && VoxGrid.ky(k) > t, style + ": cabelo sai por cima do chapéu");
                headTop.merge(VoxGrid.key(VoxGrid.kx(k), 0, VoxGrid.kz(k)), VoxGrid.ky(k), Math::max);
            }
            // assento: na frente a aba encosta na cabeça (folga mínima 0) — nada de chapéu flutuando
            int gap = Integer.MAX_VALUE;
            for (var e : bottom.entrySet()) {
                Integer t = headTop.get(e.getKey());
                if (t != null && VoxGrid.kz(e.getKey()) >= 1) gap = Math.min(gap, e.getValue() - t - 1);
            }
            assertTrue(gap <= 0, style + ": aba flutuando " + gap + " voxel(s) acima da cabeça");
            // a variante só remove cabelo; abaixo da aba o cabelo fica intacto
            Map<Long, Integer> without = C.compose(custom("aventureiro", style, null, null, Appearance.NO_COVER)).piece("head").grid.cells();
            for (var e : without.entrySet())
                if (VoxGrid.ky(e.getKey()) < lowest - 1)
                    assertEquals(e.getValue(), head.cells().get(e.getKey()), style + ": cabelo abaixo da aba mudou");
            assertTrue(without.keySet().containsAll(head.cells().keySet()), style + ": a variante acrescentou voxels");
        }
        assertNull(C.compose(Appearance.preset("aventureiro")).piece("hat"), "sem chapéu, nenhuma parte hat");
        assertNull(C.compose(custom("maga", null, null, null, Appearance.NO_COVER)).piece("hat"), "maga sem chapéu");
    }

    @Test
    void hoodPaintsTheBandOnAnyHairAndTheRangerHoodIsSeparable() {
        Appearance noHood = custom("exploradora", null, null, null, Appearance.NO_COVER);
        Composer.ComposedRig r = C.compose(noHood);
        int lining = (1 << 24) | Composer.rgb(C.catalog.components.get("capuz.c3").doc.getAsJsonObject("faixa").get("cor").getAsString());
        assertFalse(r.piece("head").grid.cells().containsValue(lining), "sem capuz, sem faixa");
        Appearance hooded = custom("aventureiro", "cabelo.curto", null, null, "capuz.c3");
        assertTrue(C.compose(hooded).piece("head").grid.cells().containsValue(lining), "faixa pintada no outro cabelo");
    }

    @Test
    void appearanceRoundTripsAndRejectsBadInputWithClearMessages() throws IOException {
        Appearance a = custom("maga", "cabelo.rabo", "#5f8a5a", "#8f5c3e", "capuz.c3");
        a.iris = "#c08a2a";
        a.face = "rosto.aventureiro";
        a.eyes = "olhos.redondos";                                       // rodada 09: formato dos olhos (v2)
        a.name = "Lia";
        Appearance b = Appearance.fromJson(a.toJson(), C.catalog);
        assertEquals(a, b, "round-trip");
        assertEquals(a.signature(), b.signature());
        Appearance d = Appearance.fromJson(Appearance.preset("exploradora").toJson(), C.catalog);
        assertNull(d.hair, "preset padrão salva só a origem");
        String good = a.toJson();
        for (String[] bad : new String[][]{
                {good.replace("cabelo.rabo", "cabelo.inexistente"), "componente desconhecido"},
                {good.replace("\"rosto.aventureiro\"", "\"cabelo.topete\""), "não é do slot"},
                {good.replace("#5f8a5a", "verde"), "inválida"},
                {good.replace("\"versao\": 2", "\"versao\": 3"), "não suportada"},
                {good.replace("\"olhos.redondos\"", "\"rosto.maga\""), "não é do slot"},
                {good.replace("valumo.aparencia", "outro.contrato"), "contrato desconhecido"},
                {good.replace("\"maga\"", "\"paladino\""), "preset desconhecido"},
                {"{nao é json", "JSON inválido"}}) {
            Appearance.InvalidAppearance ex = assertThrows(Appearance.InvalidAppearance.class, () -> Appearance.fromJson(bad[0], C.catalog));
            assertTrue(ex.getMessage().contains(bad[1]), ex.getMessage());
        }
        Path f = Files.createTempDirectory("aparencia").resolve("lia.json");
        a.save(f, false);
        assertThrows(IOException.class, () -> a.save(f, false), "não sobrescreve em silêncio");
        assertEquals(a, Appearance.load(f, C.catalog));
    }

    @Test
    void canonicalAssetsAreNotTouchedByComposition() throws Exception {
        // o compositor só lê: os rigs canônicos continuam byte a byte iguais depois de compor tudo
        Map<String, String> before = new java.util.TreeMap<>();
        for (String dir : CANON.values())
            try (var fs = Files.list(A.resolve(dir))) {
                for (Path p : fs.toList()) before.put(p.toString(), Arrays.toString(Files.readAllBytes(p)).hashCode() + "");
            }
        // rodada 09: o catálogo passou a ter uma regra "X" (crespo × capuz C3, a cobertura da exploradora); a
        // premissa antiga "toda combinação compõe" deixou de valer. Compõe toda combinação válida e exige que a
        // incompatível seja RECUSADA (nunca composta pela metade).
        int composed = 0, refused = 0;
        for (String pid : CANON.keySet())
            for (String h : C.catalog.ids("cabelo")) {
                try {
                    C.compose(custom(pid, h, "#a3a0ab", "#c98a5e", null));
                    composed++;
                } catch (Appearance.InvalidAppearance e) {
                    assertTrue(e.getMessage().contains("incompatível"), e.getMessage());
                    refused++;
                }
            }
        assertTrue(composed >= CANON.size() * (C.catalog.ids("cabelo").size() - 1), "compôs " + composed);
        assertEquals(1, refused, "só crespo × capuz C3 é incompatível no catálogo atual");
        for (var e : before.entrySet())
            assertEquals(e.getValue(), Arrays.toString(Files.readAllBytes(Path.of(e.getKey()))).hashCode() + "", e.getKey());
    }

    // ------------------------------------------------------------------ rodada 09: formato dos olhos

    @Test
    void version1FilesStillOpenThroughTheMigration() {
        String v1 = """
                {"contrato": "valumo.aparencia", "versao": 1, "identidade": {"nome": "Antiga"},
                 "origem": {"preset": "maga"}, "aparencia": {"rosto": "rosto.exploradora", "cabelo": {"estilo": "cabelo.curto"}},
                 "equipamento_visual": {"cabeca": "nenhum"}}""";
        Appearance a = Appearance.fromJson(v1, C.catalog);
        assertNull(a.eyes, "v1 não tinha olhos: fica o formato do rosto");
        assertEquals("olhos.amendoados", C.catalog.eyes(a), "o nativo da exploradora");
        assertTrue(a.toJson().contains("\"versao\": 2"), "grava sempre na versão atual");
        assertEquals(a, Appearance.fromJson(a.toJson(), C.catalog));
    }

    @Test
    void choosingTheNativeEyesChangesNothing() {
        for (String p : CANON.keySet()) {
            Appearance a = Appearance.preset(p), b = a.copy();
            b.eyes = C.catalog.eyes(a);
            assertEquals(C.compose(a).piece("head").grid.cells(), C.compose(b).piece("head").grid.cells(), p);
        }
    }

    @Test
    void eyeShapesOnlyRepaintTheFacePlateAndKeepFourDistinctExpressions() {
        for (String p : CANON.keySet())
            for (String eyes : C.catalog.ids("olhos")) {
                Appearance base = Appearance.preset(p), a = base.copy();
                a.eyes = eyes;
                Composer.Piece h0 = C.compose(base).piece("head"), h1 = C.compose(a).piece("head");
                assertEquals(h0.grid.cells().keySet(), h1.grid.cells().keySet(), p + " × " + eyes + ": a forma da cabeça não muda");
                Set<Long> hairCells = new HashSet<>();
                if (C.catalog.components.get(C.catalog.hair(base)).doc.has("cabeca")) hairCells.addAll(hairCells(C.catalog.hair(base)));
                int changed = 0;
                for (var e : h1.grid.cells().entrySet()) {
                    if (e.getValue().equals(h0.grid.cells().get(e.getKey()))) continue;
                    changed++;
                    assertFalse(hairCells.contains(e.getKey()), p + " × " + eyes + ": mudou célula de cabelo");
                }
                if (!eyes.equals(C.catalog.eyes(base))) assertTrue(changed > 0, p + " × " + eyes + ": nada mudou");
                Set<Map<Long, Integer>> exprs = new HashSet<>();
                exprs.add(h1.grid.cells());
                for (VoxGrid g : h1.alts.values()) exprs.add(g.cells());
                assertEquals(4, exprs.size(), p + " × " + eyes + ": as 4 expressões têm de ser diferentes");
            }
    }

    // ------------------------------------------------------------------ rodada 09: botas (primeira peça por região)

    @Test
    void bootsReplaceOnlyTheShinsKeepPivotsAndSole() {
        for (String p : CANON.keySet()) {
            Appearance base = Appearance.preset(p), orig = base.copy(), boots = base.copy();
            orig.feet = "botas.originais";
            boots.feet = "botas.viajante";
            Composer.ComposedRig r0 = C.compose(base), r1 = C.compose(orig), r2 = C.compose(boots);
            int changed = 0;
            for (Composer.Piece a : r0.pieces) {
                Composer.Piece same = r1.piece(a.name), b = r2.piece(a.name);
                assertEquals(a.grid.cells(), same.grid.cells(), p + " " + a.name + ": 'do modelo' não muda nada");
                assertArrayEquals(a.pivot, b.pivot, 1e-6f, p + " " + a.name + ": pivô");
                boolean shin = a.name.startsWith("shin_");
                if (!shin) assertEquals(a.grid.cells(), b.grid.cells(), p + " " + a.name + ": só as canelas mudam");
                else {
                    assertNotEquals(a.grid.cells(), b.grid.cells(), p + " " + a.name + ": a bota mudou");
                    changed++;
                    int minA = Integer.MAX_VALUE, minB = Integer.MAX_VALUE;
                    for (long k : a.grid.cells().keySet()) minA = Math.min(minA, VoxGrid.ky(k));
                    for (long k : b.grid.cells().keySet()) minB = Math.min(minB, VoxGrid.ky(k));
                    assertEquals(minA, minB, p + " " + a.name + ": sola na mesma altura (IK do pé)");
                }
            }
            assertEquals(2, changed, p);
        }
    }
}
