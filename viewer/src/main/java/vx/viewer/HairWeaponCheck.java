package vx.viewer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import vx.engine.VoxGrid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rodada 09.4 — {@code viewer --check-cabelos [saida.md]}: arma × cabeça COMPOSTA (crânio + cabelo personalizado),
 * voxel a voxel. O {@link WeaponCheck} usa uma cápsula de raio 8,6 para a cabeça — nenhum penteado é medido além
 * dela (o topete aprovado vai a ±11,5; o crespo a ±13). Aqui, a cada 1/120 s de cada golpe do perfil, os voxels reais
 * da arma são levados ao espaço da cabeça e comparados com as células reais da cabeça composta:
 * <ul>
 *   <li><b>penetração</b>: voxels da arma cujo centro cai dentro de uma célula da cabeça (pior instante);</li>
 *   <li><b>folga</b>: menor distância (em voxels) entre o centro de um voxel da arma e uma célula da cabeça.</li>
 * </ul>
 * Veredito por penteado e corpo, contra o penteado de referência do preset (aprovado):
 * <ul>
 *   <li><b>OK</b>: penetração ≤ max(TOL, referência);</li>
 *   <li><b>REPROVA</b>: penteado EXPERIMENTAL (estudo) penetra mais que a referência — sai com 1;</li>
 *   <li><b>PRÉ-EXISTENTE</b>: penteado já aprovado acima da referência (relatado, não reprova: a primeira medição,
 *       na rodada 09.4, achou o gesto EMOTE do aventureiro atravessando o topete canônico — ver problemas conhecidos).</li>
 * </ul>
 */
final class HairWeaponCheck {
    static final float DT = 1f / 120f;
    static final int TOL = 0;                                 // nenhum voxel da arma dentro da cabeça
    static final int FIELD = 4;                               // alcance do campo de distância (voxels)
    /** Itens medidos: os do catálogo, derivados do compositor (uma lista só; o broquel entrou na auditoria). */
    static final java.util.Set<String> ITEMS = Composer.ITEM_PARTS;
    /** Alvos: a cabeça composta e o chapéu, quando a cobertura é uma parte própria. */
    static final String[] TARGETS = {"head", "hat"};

    record Result(String preset, String hair, int worstPen, String worstWhere, float minGap, String gapWhere, int samples) { }

    private HairWeaponCheck() { }

    /** Campo de distância em volta da cabeça: distância (voxels) da célula ao voxel de cabeça mais próximo, até FIELD. */
    static final class Field {
        final int x0, y0, z0, nx, ny, nz;
        final float[] d;

        Field(VoxGrid head) {
            int mnx = Integer.MAX_VALUE, mny = mnx, mnz = mnx, mxx = Integer.MIN_VALUE, mxy = mxx, mxz = mxx;
            for (long k : head.cells().keySet()) {
                int x = VoxGrid.kx(k), y = VoxGrid.ky(k), z = VoxGrid.kz(k);
                mnx = Math.min(mnx, x); mny = Math.min(mny, y); mnz = Math.min(mnz, z);
                mxx = Math.max(mxx, x); mxy = Math.max(mxy, y); mxz = Math.max(mxz, z);
            }
            x0 = mnx - FIELD; y0 = mny - FIELD; z0 = mnz - FIELD;
            nx = mxx - mnx + 1 + 2 * FIELD; ny = mxy - mny + 1 + 2 * FIELD; nz = mxz - mnz + 1 + 2 * FIELD;
            d = new float[nx * ny * nz];
            java.util.Arrays.fill(d, Float.MAX_VALUE);
            for (long k : head.cells().keySet()) {
                int cx = VoxGrid.kx(k) - x0, cy = VoxGrid.ky(k) - y0, cz = VoxGrid.kz(k) - z0;
                for (int dz = -FIELD; dz <= FIELD; dz++)
                    for (int dy = -FIELD; dy <= FIELD; dy++)
                        for (int dx = -FIELD; dx <= FIELD; dx++) {
                            float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                            if (r > FIELD) continue;
                            int i = idx(cx + dx, cy + dy, cz + dz);
                            if (i >= 0 && r < d[i]) d[i] = r;
                        }
            }
        }

        int idx(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) return -1;
            return x + nx * (y + ny * z);
        }

        /** Distância da posição (voxels, espaço do modelo) à cabeça; 0 = dentro de uma célula; FIELD+ = longe. */
        float at(float px, float py, float pz) {
            int i = idx((int) Math.floor(px) - x0, (int) Math.floor(py) - y0, (int) Math.floor(pz) - z0);
            return i < 0 || d[i] == Float.MAX_VALUE ? FIELD + 1 : d[i];
        }
    }

    static Result measure(Composer comp, String preset, String hair) { return measure(comp, preset, hair, Appearance.NO_COVER); }

    /**
     * Mede um preset × penteado × cobertura ({@link Appearance#NO_COVER}, chapéu ou capuz). Alvos: a cabeça composta
     * (união das expressões: um sorriso ou uma sobrancelha erguida também conta) e, se houver, o chapéu (parte própria,
     * filha da cabeça). Itens: todos os do catálogo ({@link Composer#ITEM_PARTS}: arma, broquel, arco, flecha) — antes só
     * a espada, o arco e a flecha (o broquel ficava de fora). Instantes: cada golpe do perfil, os clipes e o salto.
     * Devolve null se a combinação é incompatível pelo catálogo (regra "X").
     */
    static Result measure(Composer comp, String preset, String hair, String cover) {
        Appearance a = Appearance.preset(preset);
        a.hair = hair;
        a.cover = cover;
        Composer.ComposedRig rig;
        try {
            rig = comp.compose(a);
        } catch (Appearance.InvalidAppearance e) {
            return null;
        }
        CharacterModel m = comp.build(rig, false);
        List<int[]> items = new ArrayList<>();                                // [parte, x, y, z] voxels dos itens
        for (String it : ITEMS) {
            Composer.Piece p = rig.piece(it);
            Integer pi = m.index.get(it);
            if (p == null || pi == null) continue;
            for (long k : p.grid.cells().keySet()) items.add(new int[]{pi, VoxGrid.kx(k), VoxGrid.ky(k), VoxGrid.kz(k)});
        }
        List<Integer> targetPart = new ArrayList<>();
        List<Field> targetField = new ArrayList<>();
        for (String t : TARGETS) {
            Composer.Piece p = rig.piece(t);
            Integer pi = m.index.get(t);
            if (p == null || pi == null) continue;
            VoxGrid all = new VoxGrid();
            for (long k : p.grid.cells().keySet()) all.put(k, 1);
            for (VoxGrid alt : p.alts.values())
                for (long k : alt.cells().keySet()) all.put(k, 1);
            targetPart.add(pi);
            targetField.add(new Field(all));
        }
        Animator an = new Animator(m, 1f);
        Animator.Pose pose = an.newPose();
        Matrix4f[] mats = new Matrix4f[m.parts.size()];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        Matrix4f id = new Matrix4f();
        Matrix4f[] inv = new Matrix4f[targetPart.size()];
        for (int i = 0; i < inv.length; i++) inv[i] = new Matrix4f();
        Animator.Motion mo = new Animator.Motion();
        float vs = m.voxelSize;
        List<String> what = new ArrayList<>(an.prof == null ? List.of() : an.prof.moves.keySet());
        what.addAll(List.of("clip:IDLE", "clip:WALK", "clip:RUN", "clip:HIT", "clip:EMOTE", "salto:IDLE", "salto:RUN"));
        int worst = 0, samples = 0;
        float gap = FIELD + 1;
        String worstWhere = "—", gapWhere = "—";
        Vector3f p = new Vector3f();
        for (String w : what) {
            boolean jump = w.startsWith("salto:");
            Animator.Clip clip = w.startsWith("clip:") ? Animator.Clip.valueOf(w.substring(5))
                    : jump ? Animator.Clip.valueOf(w.substring(6)) : null;
            float dur = jump ? JUMP_S : clip != null ? an.duration(clip) : an.moveDuration(w);
            if (dur <= 0) throw new IllegalArgumentException("golpe inexistente no perfil: " + w);
            for (float t = 0; t <= dur; t += DT) {
                if (jump) jump(mo, clip, t);
                else if (clip != null) mo.set(clip, t);
                else {
                    mo.set(Animator.Clip.IDLE, t);
                    mo.move = w;
                    mo.moveT = t;
                    mo.weight = 1f;
                }
                an.evaluate(mo, pose, id, null);
                an.compose(pose, id, mats);
                for (int i = 0; i < inv.length; i++) mats[targetPart.get(i)].invert(inv[i]);
                int pen = 0;
                for (int[] c : items) {
                    boolean inside = false;
                    for (int i = 0; i < inv.length; i++) {
                        p.set((c[1] + 0.5f) * vs, (c[2] + 0.5f) * vs, (c[3] + 0.5f) * vs);
                        mats[c[0]].transformPosition(p);
                        inv[i].transformPosition(p);
                        float dd = targetField.get(i).at(p.x / vs, p.y / vs, p.z / vs);
                        if (dd == 0) inside = true;
                        if (dd < gap) { gap = dd; gapWhere = String.format(Locale.ROOT, "%s t=%.2f", w, t); }
                    }
                    if (inside) pen++;
                }
                samples++;
                if (pen > worst) { worst = pen; worstWhere = String.format(Locale.ROOT, "%s t=%.2f", w, t); }
            }
        }
        return new Result(preset, hair, worst, worstWhere, gap, gapWhere, samples);
    }

    /** Duração do salto medido (s): impulso, subida, queda e pouso. */
    static final float JUMP_S = 0.9f;

    /**
     * Salto sobre o clipe de base (IDLE = parado, RUN = correndo): os mesmos canais da rodada 09.3 que o jogo preenche
     * ({@code takeoff} nos primeiros 0,10 s no ar, {@code air}, {@code fall} quando o chão chega, {@code land} no pouso).
     */
    static void jump(Animator.Motion mo, Animator.Clip base, float t) {
        mo.set(base, t);
        float airEnd = 0.62f;
        if (t < airEnd) {
            mo.air = Math.min(1f, t / 0.08f);
            mo.takeoff = t < 0.10f ? t / 0.10f : -1f;
            mo.fall = t > airEnd - 0.14f ? (t - (airEnd - 0.14f)) / 0.14f : 0f;
        } else {
            float u = (t - airEnd) / (JUMP_S - airEnd);
            mo.land = (float) Math.sin(Math.PI * Math.min(1f, u));
            mo.landRun = base == Animator.Clip.RUN ? 1f : 0f;
        }
    }

    /**
     * Componente NOVO (depois do conjunto canônico): estudo `experimental` ou já `aprovado` pelo proprietário. Nunca pode
     * ser atravessado pela arma mais que o penteado do preset — aprovar não afrouxa a regra.
     */
    public static boolean isNew(ComponentCatalog cat, String id) {
        var doc = cat.components.get(id).doc;
        return doc.has("experimental") || doc.has("aprovado");
    }

    /** O equipamento de combate do jogo (espada e broquel, o mesmo para toda aparência; ver {@link Composer#kit}). */
    public static final String COMBAT_KIT = "characters/r4/adventurer_shield";

    static boolean run(String out) {
        Path assets = Path.of(System.getProperty("vx.assets", "assets"));
        StringBuilder md = new StringBuilder("# Arma × cabeça composta (voxel a voxel) — viewer --check-cabelos\n\n"
                + "Penetração = voxels da arma dentro da cabeça no pior instante (critério: 0 e nunca pior que o penteado do\n"
                + "preset). Folga = menor distância arma–cabeça em voxels (" + (FIELD + 1) + " = mais longe que " + FIELD + ").\n"
                + "Duas tabelas: a arma de cada herói (o perfil dele) e o equipamento de COMBATE do jogo (espada e broquel em toda\n"
                + "aparência: três cortes, guarda, reação, esquiva R2 e recuo).\n");
        boolean ok = true;
        for (String kit : new String[]{null, COMBAT_KIT}) {
            Composer comp = new Composer(assets, 2);
            comp.kit = kit;
            String title = kit == null ? "Arma de cada herói" : "Equipamento de combate (" + kit + ")";
            System.out.println("== " + title);
            md.append("\n## ").append(title).append("\n\n| preset | penteado | penetração | pior instante | folga mín. | onde | amostras | situação |\n|---|---|---|---|---|---|---|---|\n");
            ok &= table(comp, md);
        }
        String end = ok ? "cabelos: nenhum estudo pior que a referência" : "cabelos: ESTUDO ATRAVESSADO PELA ARMA ALÉM DA REFERÊNCIA";
        md.append("\n**").append(end).append("**\n");
        System.out.println(end);
        if (out != null) {
            try {
                Path f = Path.of(out);
                if (f.getParent() != null) Files.createDirectories(f.getParent());
                Files.writeString(f, md.toString());
            } catch (IOException e) {
                System.err.println("não consegui gravar " + out + ": " + e.getMessage());
            }
        }
        return ok;
    }

    /**
     * Uma linha por preset × penteado × cobertura. Referência = o penteado do preset com a MESMA cobertura. Combinação
     * proibida pelo catálogo (regra "X") aparece como "incompatível" e não conta. Sem nenhuma medição: falha.
     */
    private static boolean table(Composer comp, StringBuilder md) {
        ComponentCatalog cat = comp.catalog;
        boolean ok = true;
        int measured = 0;
        List<String> covers = new ArrayList<>(List.of(Appearance.NO_COVER));
        covers.addAll(cat.ids("cabeca"));
        for (String preset : cat.presets.keySet()) {
            String ref = cat.presets.get(preset).hair;
            for (String cover : covers) {
                Result r0 = measure(comp, preset, ref, cover);
                for (String hair : cat.ids("cabelo")) {
                    Result r = hair.equals(ref) ? r0 : measure(comp, preset, hair, cover);
                    String cov = cover.equals(Appearance.NO_COVER) ? "" : " + " + cover;
                    if (r == null || r0 == null) {
                        String line = String.format(Locale.ROOT, "| %s | %s%s | — | — | — | — | 0 | incompatível (catálogo) |", preset, hair, cov);
                        System.out.println(line);
                        md.append(line).append('\n');
                        continue;
                    }
                    measured++;
                    boolean pass = r.worstPen <= Math.max(TOL, r0.worstPen);
                    boolean novo = isNew(cat, hair);                // estudo (experimental ou aprovado): protegido
                    ok &= pass || !novo;
                    String verdict = pass ? "OK" : novo ? "REPROVA" : "PRÉ-EXISTENTE";
                    String line = String.format(Locale.ROOT, "| %s | %s%s%s | %d | %s | %.2f | %s | %d | %s |", preset, hair, cov,
                            hair.equals(ref) ? " (referência)" : "", r.worstPen, r.worstWhere, r.minGap(), r.gapWhere, r.samples,
                            verdict);
                    System.out.println(line);
                    md.append(line).append('\n');
                }
            }
        }
        if (measured == 0) {
            System.out.println("cabelos: NADA FOI CONFERIDO");
            return false;
        }
        return ok;
    }
}
