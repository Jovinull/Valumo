package vx.viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import vx.engine.VoxModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rodada 05 — contrato de qualidade dos assets canônicos. Testes de regressão para os defeitos que a
 * auditoria encontrou e corrigiu (voxels soltos, "bastão branco", broquel pequeno, HIT/EMOTE fora da
 * coreografia, segunda conjuração igual à primeira). Rode build_assets antes.
 */
class QualityTest {
    static final String[] HEROES = {"characters/e/adventurer", "characters/e/ranger", "characters/e/mage",
            "characters/r4/adventurer_shield", "characters/r4/adventurer_longblade"};

    @Test
    void canonicalHeroesHaveNoStraySpecks() throws IOException {
        // voxel solto ou fragmento < 4 voxels desconectado da peça = ruído ou peça flutuando (auditoria da rodada 05)
        for (String h : HEROES) {
            try (var files = Files.list(Path.of("assets").resolve(h))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".vox")).toList()) {
                    VoxModel m = VoxModel.load(f);
                    Set<Long> cells = new HashSet<>();
                    for (int x = 0; x < m.sx; x++)
                        for (int y = 0; y < m.sy; y++)
                            for (int z = 0; z < m.sz; z++)
                                if (m.get(x, y, z) != 0) cells.add(key(x, y, z));
                    List<Integer> comps = components(cells);
                    for (int i = 1; i < comps.size(); i++)
                        assertTrue(comps.get(i) >= 4, f + ": fragmento solto de " + comps.get(i) + " voxel(s)");
                }
            }
        }
    }

    static long key(int x, int y, int z) { return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512); }
    static int y(long k) { return (int) ((k >>> 20) & 0xFFFFF) - 512; }
    static int z(long k) { return (int) (k & 0xFFFFF) - 512; }

    static List<Integer> components(Set<Long> cells) {
        Set<Long> seen = new HashSet<>();
        java.util.ArrayList<Integer> sizes = new java.util.ArrayList<>();
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (long c : cells) {
            if (!seen.add(c)) continue;
            int n = 0;
            ArrayDeque<Long> q = new ArrayDeque<>(List.of(c));
            while (!q.isEmpty()) {
                long k = q.pop();
                n++;
                int x = (int) (k >> 40) - 512, y = (int) ((k >> 20) & 0xFFFFF) - 512, z = (int) (k & 0xFFFFF) - 512;
                for (int[] dd : d) {
                    long nb = key(x + dd[0], y + dd[1], z + dd[2]);
                    if (cells.contains(nb) && seen.add(nb)) q.push(nb);
                }
            }
            sizes.add(n);
        }
        sizes.sort(java.util.Comparator.reverseOrder());
        return sizes;
    }

    @Test
    void longBladeReadsAsSteelWithBackBodyAndEdge() throws IOException {
        // regressão do "bastão branco": a seção da lâmina tem três tons distintos (dorso escuro, corpo, fio claro)
        VoxModel m = VoxModel.load(Path.of("assets/characters/r4/adventurer_longblade/weapon.vox"));
        int y = m.sy / 2;                                   // meio da lâmina
        Set<Integer> tones = new HashSet<>();
        int darkest = 255 * 3, brightest = 0;
        for (int x = 0; x < m.sx; x++)
            for (int z = 0; z < m.sz; z++) {
                int v = m.get(x, y, z);
                if (v == 0) continue;
                int rgb = v & 0xFFFFFF, lum = ((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255);
                tones.add(rgb);
                darkest = Math.min(darkest, lum);
                brightest = Math.max(brightest, lum);
            }
        assertTrue(tones.size() >= 3, "dorso, corpo e fio: " + tones.size() + " tons");
        assertTrue(brightest - darkest > 150, "contraste fio × dorso (soma RGB): " + (brightest - darkest));
    }

    @Test
    void bucklerIsLargeEnoughToReadAsAShield() throws IOException {
        VoxModel m = VoxModel.load(Path.of("assets/characters/r4/adventurer_shield/shield.vox"));
        assertTrue(Math.min(m.sx, m.sy) >= 12, "diâmetro do broquel ≥ 12 voxels (0,3 u): " + m.sx + " × " + m.sy);
    }

    @Test
    void hitAndEmoteClipsUseTheChoreographedGestures() {
        // HIT/EMOTE eram FK e atravessavam a cabeça; agora o clipe usa o gesto do perfil
        for (String h : new String[]{"characters/e/adventurer", "characters/e/ranger", "characters/e/mage"}) {
            CharacterModel m = ChoreoTest.rig(h);
            Animator an = new Animator(m, 1f);
            for (String[] g : new String[][]{{"HIT", "hit"}, {"EMOTE", "emote"}}) {
                assertTrue(an.prof.moves.containsKey(g[1]), h + ": gesto " + g[1]);
                float t = an.moveDuration(g[1]) * 0.4f;
                Animator.Pose byClip = an.newPose(), byMove = an.newPose();
                an.evaluate(new Animator.Motion().set(Animator.Clip.valueOf(g[0]), t), byClip, new Matrix4f(), null);
                an.evaluate(ChoreoTest.motion(g[1], t), byMove, new Matrix4f(), null);
                assertArrayEquals(byMove.v, byClip.v, 1e-5f, h + ": clipe " + g[0] + " = gesto do perfil");
            }
        }
    }

    @Test
    void secondStaffCastIsADifferentGesture() {
        // as duas conjurações precisam diferir de verdade: trajetória do cristal comparada no tempo normalizado
        CharacterModel m = ChoreoTest.rig("characters/e/mage");
        Animator an = new Animator(m, 1f);
        float d1 = an.moveDuration("staff_cast"), d2 = an.moveDuration("staff_cast_wide"), sum = 0, minY1 = 1e9f, minY2 = 1e9f;
        int n = 40;
        for (int i = 0; i <= n; i++) {
            Vector3f a = crystal(an, "staff_cast", d1 * i / n), b = crystal(an, "staff_cast_wide", d2 * i / n);
            sum += a.distance(b);
            minY1 = Math.min(minY1, a.z);
            minY2 = Math.min(minY2, b.z);
        }
        float vs = m.voxelSize;
        assertTrue(sum / (n + 1) / vs > 5f, "distância média entre as trajetórias do cristal: " + sum / (n + 1) / vs + " voxels");
        assertTrue(minY2 < minY1 - 3 * vs, "a conjuração circular leva o cristal para trás do corpo (preparação circular)");
    }

    static Vector3f crystal(Animator an, String mv, float t) {
        an.evaluate(ChoreoTest.motion(mv, t), an.newPose(), new Matrix4f(), null);
        return new Vector3f(an.dbgEmit);
    }

    @Test
    void longBladeTwoHandedGuardKeepsBothHandsOnTheHandle() {
        CharacterModel m = ChoreoTest.rig("characters/r4/adventurer_longblade");
        Animator an = new Animator(m, 1f);
        assertTrue(an.prof.moves.containsKey("guard_two_hands"));
        for (float t = 0.9f; t <= 1.8f; t += 0.1f) {          // trecho da guarda sustentada
            an.evaluate(ChoreoTest.motion("guard_two_hands", t), an.newPose(), new Matrix4f(), null);
            assertTrue(an.reachErrL < WeaponCheck.TOL_REACH, "mão esquerda no cabo em " + t + ": " + an.reachErrL);
            assertTrue(an.dbgHandL.distance(an.dbgHandR) / m.voxelSize < 5.5f, "as duas mãos juntas no cabo");
        }
    }

    // ------------------------------------------------------------------ rodada 06: atuação das expressões

    /** Voxels (no espaço do modelo, via origin do rig) cuja cor muda entre a cabeça neutra e a expressão. */
    static Set<Long> changedCells(String hero, String expr) throws IOException {
        var doc = com.google.gson.JsonParser.parseString(Files.readString(Path.of("assets", hero, "rig.json"))).getAsJsonObject();
        com.google.gson.JsonObject head = null;
        for (var p : doc.getAsJsonArray("parts")) if (p.getAsJsonObject().get("name").getAsString().equals("head")) head = p.getAsJsonObject();
        var o0 = head.getAsJsonArray("origin");
        var alt = head.getAsJsonObject("alts").getAsJsonObject(expr);
        var o1 = alt.getAsJsonArray("origin");
        VoxModel n = VoxModel.load(Path.of("assets", hero, head.get("file").getAsString()));
        VoxModel e = VoxModel.load(Path.of("assets", hero, alt.get("file").getAsString()));
        java.util.Map<Long, Integer> a = new java.util.HashMap<>(), b = new java.util.HashMap<>();
        for (int x = 0; x < n.sx; x++) for (int y = 0; y < n.sy; y++) for (int z = 0; z < n.sz; z++)
            if (n.get(x, y, z) != 0) a.put(key(x + o0.get(0).getAsInt(), y + o0.get(1).getAsInt(), z + o0.get(2).getAsInt()), n.get(x, y, z));
        for (int x = 0; x < e.sx; x++) for (int y = 0; y < e.sy; y++) for (int z = 0; z < e.sz; z++)
            if (e.get(x, y, z) != 0) b.put(key(x + o1.get(0).getAsInt(), y + o1.get(1).getAsInt(), z + o1.get(2).getAsInt()), e.get(x, y, z));
        Set<Long> out = new HashSet<>();
        for (var en : b.entrySet()) if (!en.getValue().equals(a.get(en.getKey()))) out.add(en.getKey());
        return out;
    }

    @Test
    void expressionsActDifferentlyPerHeroInsteadOfOneTemplate() throws IOException {
        // rodada 06: o proprietário achou as expressões "cópia uma da outra" — alegre/surpresa eram o mesmo
        // desenho nos três heróis. Cada expressão precisa mudar o rosto e não pode repetir o carimbo de outro herói.
        String[] heroes = {"characters/e/adventurer", "characters/e/ranger", "characters/e/mage"};
        for (String e : List.of("joy", "determined", "surprise")) {
            List<Set<Long>> sets = new java.util.ArrayList<>();
            for (String h : heroes) {
                Set<Long> d = changedCells(h, e);
                assertTrue(d.size() >= 10, h + " " + e + ": só " + d.size() + " voxels mudam em relação ao neutro");
                sets.add(d);
            }
            for (int i = 0; i < 3; i++)
                for (int j = i + 1; j < 3; j++) {
                    Set<Long> inter = new HashSet<>(sets.get(i));
                    inter.retainAll(sets.get(j));
                    Set<Long> uni = new HashSet<>(sets.get(i));
                    uni.addAll(sets.get(j));
                    double jac = inter.size() / (double) uni.size();
                    assertTrue(jac < 0.6, e + ": " + heroes[i] + " × " + heroes[j] + " pintam o mesmo desenho (Jaccard " + jac + ")");
                }
        }
    }
}
