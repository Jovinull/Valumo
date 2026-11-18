package vx.viewer;

import vx.engine.FontAtlas;
import vx.engine.UiBatch;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;

/**
 * Rodada 07.2 — desenho da interface do criador no estilo Valumo: molduras de madeira, pergaminho, seleção
 * dourada, a semente verde-água (P1) só como acento da escolha ativa; ícones de pixel desenhados no próprio
 * código (nenhum asset de terceiros). Só desenha: o que fazer com cada controle está no {@link CreatorController}.
 */
public final class CreatorView implements AutoCloseable {
    // paleta da interface (família da direção E: madeira da casa, pergaminho do posto, latão, P1)
    // paleta e peças: UiKit (rodada 08: compartilhadas com o jogo)
    static final int PARCH = UiKit.PARCH, PARCH_D = UiKit.PARCH_D, PARCH_L = UiKit.PARCH_L, INK = UiKit.INK, INK_SOFT = UiKit.INK_SOFT,
            WOOD = UiKit.WOOD, WOOD_D = UiKit.WOOD_D, WOOD_L = UiKit.WOOD_L, GOLD = UiKit.GOLD, GOLD_D = UiKit.GOLD_D,
            MOSS = UiKit.MOSS, MOSS_D = UiKit.MOSS_D, P1 = UiKit.P1, CORAL = UiKit.CORAL, SHADOW = UiKit.SHADOW;

    private final UiKit kit = new UiKit();
    private final UiBatch ui = kit.ui;
    public boolean drawCursor;

    /** Métrica para o layout (as mesmas fontes do desenho). */
    public static CreatorLayout.Measure measure() { return vx.engine.PixelFont::width; }

    public void draw(CreatorLayout l, CreatorController c, CreatorThumbs thumbs, AsyncComposer async, int fbH) {
        CreatorState st = c.st;
        ui.begin(l.w, l.h);
        // faixa de sombra quente no topo: o título lê sobre qualquer fundo do cenário
        if (l.get("title") != null) for (int i = 0; i < 24; i++) {
            int y0 = Math.round(i * 7.5f * l.s), y1 = Math.round((i + 1) * 7.5f * l.s);   // faixas inteiras, sem sobreposição
            ui.rect(0, y0, l.w, y1 - y0, 0x2a1a1200 | Math.round(150 * (1 - i / 24f) * (1 - i / 24f)));
        }
        // véu suave nas bordas: o painel não briga com o cenário e o texto sempre lê
        boolean modal = st.dialog != CreatorState.Dialog.NONE;
        for (CreatorLayout.Widget w : l.widgets) {
            if (w.kind == CreatorLayout.Kind.DIALOG) continue;
            boolean clip = w.scrolls && l.options != null && !w.id.startsWith("loadfile:");
            if (clip) ui.flush();
            if (clip) scissor(l.options.x() + 6 * l.s, l.options.y() + 6 * l.s, l.options.w() - 12 * l.s, l.options.h() - 12 * l.s, fbH);
            widget(w, l, c, thumbs);
            if (clip) { ui.flush(); glDisable(GL_SCISSOR_TEST); }
        }
        if (l.options != null && l.contentHeight > l.options.h()) scrollbar(l, st);
        if (async.busy() && async.waitingMs() > 120) busy(l, c.time);
        message(l, st, c.time);
        if (modal) {
            ui.rect(0, 0, l.w, l.h, 0x1a1210a0);
            for (CreatorLayout.Widget w : l.widgets) if (w.kind == CreatorLayout.Kind.DIALOG) panel(w.r, l.s, true);
            for (CreatorLayout.Widget w : l.widgets) {
                if (w.kind == CreatorLayout.Kind.DIALOG || !inDialog(w, l)) continue;
                boolean clip = w.id.startsWith("loadfile:");
                if (clip) { ui.flush(); CreatorLayout.Widget d = l.get("dialog");
                    scissor(d.r.x(), d.r.y() + 90 * l.s, d.r.w(), d.r.h() - 190 * l.s, fbH); }
                widget(w, l, c, thumbs);
                if (clip) { ui.flush(); glDisable(GL_SCISSOR_TEST); }
            }
        }
        tooltip(l, c);
        if (drawCursor && c.mouseX >= 0) cursor(c.mouseX, c.mouseY, l.s, c.pressed != null || c.dragging);
        ui.end();
    }

    private boolean inDialog(CreatorLayout.Widget w, CreatorLayout l) {
        CreatorLayout.Widget d = l.get("dialog");
        return d != null && w.r.inside(d.r) || w.id.startsWith("loadfile:");
    }

    private void scissor(float x, float y, float w, float h, int fbH) {
        glEnable(GL_SCISSOR_TEST);
        glScissor(Math.round(x), Math.round(fbH - y - h), Math.max(0, Math.round(w)), Math.max(0, Math.round(h)));
    }

    // ------------------------------------------------------------------ peças

    /** Painel de madeira com cantos chanfrados (degraus de voxel) e miolo de pergaminho. */
    private void panel(CreatorLayout.Rect r, float s, boolean strong) { kit.panel(r.x(), r.y(), r.w(), r.h(), s, strong); }

    private void chamfer(float x, float y, float w, float h, float c, int col) { kit.chamfer(x, y, w, h, c, col); }

    private void widget(CreatorLayout.Widget w, CreatorLayout l, CreatorController c, CreatorThumbs thumbs) {
        float s = l.s;
        boolean hover = w.id.equals(c.hover) && w.enabled, down = w.id.equals(c.pressed);
        CreatorLayout.Rect r = w.r;
        switch (w.kind) {
            case PANEL -> panel(r, s, false);
            case LABEL -> {
                int px = w.size == 1 ? l.px(48) : w.size == 2 ? l.px(20) : l.px(26);
                boolean onScene = w.id.equals("title") || w.id.equals("subtitle");
                int col = onScene ? 0xfff8eaff : w.size == 2 ? INK_SOFT : INK;
                boolean b = !w.id.startsWith("h2:") && !w.id.equals("subtitle") && !w.id.equals("d:text");
                if (onScene) text(r.x() + 2 * s, r.y() + 3 * s, w.label, px, b, 0x20140eb0);
                text(r.x(), r.y(), w.label, px, b, col);
            }
            case TAB -> {
                boolean sel = w.selected;
                float lift = sel ? 0 : hover ? -2 * s : 0;
                chamfer(r.x(), r.y() + lift, r.w(), r.h(), 5 * s, sel ? GOLD : WOOD_D);
                chamfer(r.x() + 3 * s, r.y() + 3 * s + lift, r.w() - 6 * s, r.h() - 6 * s, 4 * s, sel ? PARCH_L : hover ? WOOD_L : WOOD);
                icon(w.id.substring(4), r.x() + 16 * s, r.y() + r.h() / 2 - 14 * s + lift, 3.2f * s, sel ? INK : PARCH_L);
                text(r.x() + 62 * s, r.y() + r.h() / 2 - w.textPx * 0.62f + lift, w.label, w.textPx, true, sel ? INK : PARCH_L);
                if (sel) seed(r.x() + r.w() - 20 * s, r.y() + r.h() / 2 - 4 * s, s);
            }
            case THUMB -> {
                float lift = hover && !w.selected ? -4 * s : 0, lh = 34 * s;
                CreatorLayout.Rect img = new CreatorLayout.Rect(r.x() + 6 * s, r.y() + 6 * s + lift, r.w() - 12 * s, r.h() - lh - 10 * s);
                if (hover && !w.selected) chamfer(r.x() - 3 * s, r.y() + lift - 3 * s, r.w() + 6 * s, r.h() + 6 * s, 7 * s, 0xf6cd6a90);   // brilho quente
                chamfer(r.x(), r.y() + lift, r.w(), r.h(), 5 * s, w.selected ? GOLD : hover ? WOOD_L : WOOD);
                chamfer(r.x() + 3 * s, r.y() + 3 * s + lift, r.w() - 6 * s, r.h() - 6 * s, 4 * s, w.selected ? PARCH_L : PARCH_D);
                Integer tex = thumbs.texture(w.id);
                if (tex != null) {
                    // FBO quadrado (origem embaixo): recorte central na proporção do controle, v invertido
                    float ar = img.h() / img.w(), u0 = 0, u1 = 1, vt = 1, vb = 0;
                    if (ar < 1) { vt = 0.5f + ar / 2; vb = 0.5f - ar / 2; } else { u0 = 0.5f - 0.5f / ar; u1 = 0.5f + 0.5f / ar; }
                    ui.quad(tex, img.x(), img.y(), img.w(), img.h(), u0, vt, u1, vb, w.enabled ? 0xffffffff : 0x8a8a8aff);
                }
                else pending(img, c.time);
                text(r.x() + (r.w() - measureW(w.label, w.textPx, true)) / 2, r.y() + r.h() - lh + lift, w.label, w.textPx, true,
                        w.enabled ? INK : INK_SOFT);
                if (w.selected) check(r.x() + r.w() - 26 * s, r.y() + 8 * s + lift, s);
                if (!w.enabled) lock(img.x() + img.w() / 2 - 12 * s, img.y() + img.h() / 2 - 14 * s, s);
            }
            case SWATCH -> {
                float sh = r.h() - 30 * s, lift = hover && !w.selected ? -3 * s : 0;
                int base = 0xff | (Integer.parseInt(w.payload.substring(1), 16) << 8);
                if (hover && !w.selected) chamfer(r.x() - 3 * s, r.y() + lift - 3 * s, r.w() + 6 * s, sh + 6 * s, 7 * s, 0xf6cd6a90);
                chamfer(r.x(), r.y() + lift, r.w(), sh, 5 * s, w.selected ? GOLD : hover ? WOOD_L : WOOD_D);
                float ix = r.x() + 4 * s, iy = r.y() + 4 * s + lift, iw = r.w() - 8 * s, ih = sh - 8 * s;
                // amostra em três faixas (luz, base, sombra) — a mesma ideia de rampa do material
                ui.rect(ix, iy, iw, ih * 0.28f, mix(base, 0xffffffff, 0.30f));
                ui.rect(ix, iy + ih * 0.28f, iw, ih * 0.47f, base);
                ui.rect(ix, iy + ih * 0.75f, iw, ih * 0.25f, mul(base, 0.72f));
                if (w.selected) check(r.x() + r.w() - 24 * s, r.y() + 6 * s + lift, s);
                text(r.x() + (r.w() - measureW(w.label, w.textPx, false)) / 2, r.y() + sh + 4 * s, w.label, w.textPx, false,
                        w.selected ? INK : INK_SOFT);
            }
            case BUTTON, TOGGLE, LIST_ITEM -> {
                boolean primary = w.kind == CreatorLayout.Kind.BUTTON && w.selected;
                boolean on = w.kind == CreatorLayout.Kind.TOGGLE && w.selected;
                float off = down ? 2 * s : 0;
                int edge = !w.enabled ? 0x5a4a40ff : primary ? MOSS_D : on ? GOLD_D : WOOD_D;
                int fill = !w.enabled ? 0x8a7a6aff : primary ? (hover ? 0x74a26cff : MOSS) : on ? (hover ? 0xf6cd6aff : GOLD)
                        : w.kind == CreatorLayout.Kind.LIST_ITEM ? (hover ? PARCH_L : PARCH_D) : hover ? WOOD_L : WOOD;
                if (w.kind != CreatorLayout.Kind.LIST_ITEM) ui.rect(r.x() + 3 * s, r.y() + 5 * s, r.w(), r.h(), SHADOW);
                chamfer(r.x(), r.y() + off, r.w(), r.h(), 5 * s, edge);
                chamfer(r.x() + 3 * s, r.y() + 3 * s + off, r.w() - 6 * s, r.h() - 7 * s, 4 * s, fill);
                ui.rect(r.x() + 9 * s, r.y() + 4 * s + off, r.w() - 18 * s, Math.max(1, 2 * s), 0xffffff38);
                int px = w.textPx;
                int tc = !w.enabled ? 0xd8cdbfff : on || w.kind == CreatorLayout.Kind.LIST_ITEM ? INK : PARCH_L;
                float tw = measureW(w.label, px, true);
                float tx = w.kind == CreatorLayout.Kind.LIST_ITEM ? r.x() + 20 * s : r.x() + (r.w() - tw) / 2;
                text(tx, r.y() + r.h() / 2 - px * 0.66f + off, w.label, px, true, tc);
            }
            case FIELD -> {
                chamfer(r.x(), r.y(), r.w(), r.h(), 4 * s, w.selected ? GOLD : WOOD);
                chamfer(r.x() + 3 * s, r.y() + 3 * s, r.w() - 6 * s, r.h() - 6 * s, 3 * s, w.selected ? 0xfffaf0ff : PARCH_L);
                int px = w.textPx;
                text(r.x() + 16 * s, r.y() + r.h() / 2 - px * 0.66f, w.label, px, true, INK);
                if (w.selected && ((int) (c.time * 2.2f)) % 2 == 0)
                    ui.rect(r.x() + 18 * s + measureW(w.label.trim(), px, true), r.y() + 12 * s, 2.5f * s, r.h() - 24 * s, INK);
                if (!w.selected) icon("PENA", r.x() + r.w() - 40 * s, r.y() + r.h() / 2 - 12 * s, 2.6f * s, INK_SOFT);
            }
            default -> { }
        }
    }

    private float measureW(String t, int px, boolean b) { return kit.width(t, px, b); }

    private void text(float x, float y, String t, int px, boolean b, int col) { kit.text(x, y, t, px, b, col); }

    private void seed(float x, float y, float s) { kit.seed(x, y, s); }

    private void check(float x, float y, float s) { kit.check(x, y, s); }

    private void lock(float x, float y, float s) {
        String[] m = {"..XXXX..", ".X....X.", ".X....X.", "XXXXXXXX", "XXX..XXX", "XXX..XXX", "XXXXXXXX"};
        ui.rect(x - 6 * s, y - 6 * s, 36 * s, 36 * s, 0x2a1e18a0);
        bitmap(m, x, y, 3f * s, PARCH_L);
    }

    private void pending(CreatorLayout.Rect r, float t) {
        ui.rect(r.x(), r.y(), r.w(), r.h(), 0xd8c49aff);
        for (int i = 0; i < 3; i++) {
            float a = 0.35f + 0.65f * (float) Math.max(0, Math.sin(t * 5 - i * 0.9));
            ui.rect(r.x() + r.w() / 2 + (i - 1) * r.w() * 0.12f - 4, r.y() + r.h() / 2 - 4, 8, 8, (WOOD & 0xffffff00) | (int) (a * 255));
        }
    }

    private void scrollbar(CreatorLayout l, CreatorState st) {
        float s = l.s, track = l.options.h() - 40 * s, frac = l.options.h() / l.contentHeight;
        float max = l.contentHeight - l.options.h(), pos = max <= 0 ? 0 : st.scroll / max;
        float bx = l.options.x() + l.options.w() - 14 * s, by = l.options.y() + 20 * s;
        ui.rect(bx, by, 6 * s, track, 0x6b463040);
        ui.rect(bx, by + (track - track * frac) * pos, 6 * s, track * frac, WOOD);
    }

    private void busy(CreatorLayout l, float t) {
        float s = l.s, w = 230 * s, x = l.scene.x() + (l.scene.w() - w) / 2, y = l.h - 200 * s;
        chamfer(x, y, w, 46 * s, 5 * s, 0x2a1e18c0);
        int dots = ((int) (t * 3)) % 4;
        text(x + 22 * s, y + 9 * s, "Preparando" + ".".repeat(dots), l.px(20), true, PARCH_L);
    }

    private void message(CreatorLayout l, CreatorState st, float t) {
        if (st.message == null || t > st.messageUntil) return;
        float s = l.s;
        int px = l.px(21);
        float tw = measureW(st.message, px, true), w = tw + 60 * s, h = 52 * s;
        float cx = l.scene != null ? l.scene.x() + l.scene.w() / 2 : l.w / 2f, x = cx - w / 2, y = 150 * s;
        boolean err = "erro".equals(st.messageKind);
        chamfer(x, y, w, h, 6 * s, err ? CORAL : WOOD_D);
        chamfer(x + 3 * s, y + 3 * s, w - 6 * s, h - 6 * s, 5 * s, PARCH_L);
        text(x + 30 * s, y + h / 2 - px * 0.66f, st.message, px, true, err ? 0x8a3a20ff : INK);
    }

    private void tooltip(CreatorLayout l, CreatorController c) {
        if (c.hover == null || c.mouseX < 0) return;
        CreatorLayout.Widget w = null;
        for (CreatorLayout.Widget x : l.widgets) if (x.id.equals(c.hover)) w = x;
        CreatorLayout.Widget raw = l.hit(c.mouseX, c.mouseY, c.st);
        if (raw != null && !raw.enabled && raw.reason != null) w = raw;
        if (w == null || w.enabled || w.reason == null) return;
        float s = l.s;
        int px = l.px(19);
        float tw = measureW(w.reason, px, true), x = Math.min(c.mouseX + 18 * s, l.w - tw - 40 * s), y = c.mouseY + 22 * s;
        chamfer(x, y, tw + 28 * s, 42 * s, 4 * s, WOOD_D);
        text(x + 14 * s, y + 21 * s - px * 0.66f, w.reason, px, true, PARCH_L);
    }

    private void cursor(float x, float y, float s, boolean down) { kit.cursor(x, y, s, down); }

    // ------------------------------------------------------------------ ícones de pixel (desenhados aqui)

    private static final Map<String, String[]> ICONS = Map.of(
            "MODELO", new String[]{"...XXX...", "..XXXXX..", "..XXXXX..", "...XXX...", ".XXXXXXX.", "X.XXXXX.X", "..XX.XX..", "..XX.XX..", ".XXX.XXX."},
            "CABELO", new String[]{"..XXXXX..", ".XXXXXXX.", "XXXXXXXXX", "XX.....XX", "X..X.X..X", "X.......X", "X.......X", ".X.....X.", "..XXXXX.."},
            "ROSTO", new String[]{"..XXXXX..", ".X.....X.", "X.......X", "X.XX.XX.X", "X.......X", "X.X...X.X", "X..XXX..X", ".X.....X.", "..XXXXX.."},
            "PELE", new String[]{"....X....", "...XXX...", "..XXXXX..", ".XXXXXXX.", ".XXXXXXX.", "XXXXX.XXX", "XXXX.XXXX", ".XXXXXXX.", "..XXXXX.."},
            "CHAPEU", new String[]{"....XX...", "...XXX...", "...XXXX..", "..XXXXX..", "..XXXXXX.", ".XXXXXXX.", "XXXXXXXXX", ".........", "........."},
            "ROUPA", new String[]{".XXX.....", ".XXX.....", ".XXX.....", ".XXX.....", ".XXXX....", ".XXXXXX..", "XXXXXXXX.", "XXXXXXXXX", "........."},
            "PENA", new String[]{".......XX", "......XXX", ".....XXX.", "....XXX..", "...XXX...", "..XXX....", ".XX......", "X........"});

    private void icon(String name, float x, float y, float k, int col) {
        String[] m = ICONS.get(name);
        if (m != null) bitmap(m, x, y, k, col);
    }

    private void bitmap(String[] m, float x, float y, float k, int col) { kit.bitmap(m, x, y, k, col); }

    static int mix(int a, int b, float t) { return UiKit.mix(a, b, t); }

    static int mul(int a, float k) { return UiKit.mul(a, k); }

    @Override
    public void close() { kit.close(); }
}
