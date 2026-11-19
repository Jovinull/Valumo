package vx.viewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 07.2 — layout do criador (só geometria; sem OpenGL, testável). Tudo em pixels do framebuffer,
 * proporcional a uma escala {@code s} derivada do tamanho da janela (base 1920 × 1080), então os controles
 * mantêm o mesmo tamanho relativo em 720p, 1080p, ultrawide e depois de redimensionar.
 *
 * <p>Variante A ("fantasia compacta", escolhida): abas à esquerda, painel de opções à direita, ações abaixo
 * do painel, poses e câmera sob o personagem. Variante B ("ateliê"): abas no topo, opções numa faixa
 * inferior. Ver {@code docs/architecture/criador-de-personagens.md}.
 */
public final class CreatorLayout {
    public enum Kind { PANEL, LABEL, TAB, THUMB, SWATCH, BUTTON, TOGGLE, FIELD, LIST_ITEM, DIALOG }

    /** Retângulo em pixels. */
    public record Rect(float x, float y, float w, float h) {
        public boolean contains(float px, float py) { return px >= x && py >= y && px < x + w && py < y + h; }
        public boolean inside(Rect o) { return x >= o.x - 0.5f && y >= o.y - 0.5f && x + w <= o.x + o.w + 0.5f && y + h <= o.y + o.h + 0.5f; }
        public boolean overlaps(Rect o) { return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h; }
    }

    /** Controle: id = ação ("hair:cabelo.curto", "save"...); payload para miniatura/cor. */
    public static final class Widget {
        public final String id;
        public final Kind kind;
        public final Rect r;
        public String label, payload, reason;
        public boolean selected, enabled = true, scrolls;
        public int size = 0;                       // 0 normal, 1 título, 2 pequeno
        /** Tamanho do texto escolhido pelo layout (o desenho usa este; cabe no controle). */
        public int textPx;
        String fullLabel;

        Widget(String id, Kind kind, Rect r, String label) {
            this.id = id; this.kind = kind; this.r = r; this.label = label;
        }

        public boolean interactive() { return kind != Kind.PANEL && kind != Kind.LABEL && kind != Kind.DIALOG; }
    }

    public final char variant;
    public final int w, h;
    public final float s;
    public final List<Widget> widgets = new ArrayList<>();
    public Rect scene, options, optionsContent;
    public float contentHeight;

    /** Métrica de texto (largura em px para uma fonte de tamanho px); o layout corta rótulos que não cabem. */
    public interface Measure { float width(String text, int px, boolean bold); }

    public CreatorLayout(char variant, int w, int h, CreatorState st, ComponentCatalog cat, Measure m) {
        this.variant = variant;
        this.w = w;
        this.h = h;
        this.s = Math.clamp(Math.min(w / 1920f, h / 1080f), 0.6f, 1.8f);
        if (st.showcase) { buildShowcase(); sizeTexts(m); return; }
        if (variant == 'B') buildB(st, cat, m); else buildA(st, cat, m);
        buildDialog(st, m);
        sizeTexts(m);
    }

    /** Tamanho-base (antes da escala), negrito e folga horizontal do texto de cada tipo de controle. */
    static int basePx(Kind k) {
        return switch (k) {
            case TAB, FIELD -> 24; case THUMB, TOGGLE -> 20; case SWATCH -> 17; case BUTTON, LIST_ITEM -> 22; default -> 0;
        };
    }

    static boolean boldText(Kind k) { return k != Kind.SWATCH; }

    float textRoom(Widget w) {
        return switch (w.kind) {
            case TAB -> w.r.w() - 62 * s - 28 * s;
            case THUMB -> w.r.w() - 10 * s;
            case SWATCH -> w.r.w() + 10 * s;
            case BUTTON, TOGGLE -> w.r.w() - 14 * s;
            case LIST_ITEM -> w.r.w() - 40 * s;
            case FIELD -> w.r.w() - 64 * s;
            default -> w.r.w();
        };
    }

    /*
     * Fonte de pixel tem escala inteira: escolhe, por controle, o maior passo que cabe; a fileira inteira (mesmo
     * tipo, mesma linha) usa o menor deles, para não misturar tamanhos lado a lado. Só então corta com "…".
     */
    /** Fileira (mesma linha) ou, para as abas, a coluna inteira. */
    private static String groupKey(Widget w) { return w.kind == Kind.TAB ? "TAB" : w.kind + ":" + Math.round(w.r.y()); }

    private void sizeTexts(Measure m) {
        Map<String, Integer> group = new HashMap<>();
        for (Widget w : widgets) {
            int base = basePx(w.kind);
            if (base == 0 || w.label == null) continue;
            w.fullLabel = w.label;
            int px = px(base);
            boolean b = boldText(w.kind);
            while (m.width(w.label, px, b) > textRoom(w) && vx.engine.PixelFont.scale(px) > 1)
                px = 9 * (vx.engine.PixelFont.scale(px) - 1);
            w.textPx = px;
            group.merge(groupKey(w), px, Math::min);
        }
        for (Widget w : widgets) {
            if (w.textPx == 0) continue;
            w.textPx = group.get(groupKey(w));
            w.label = fit(w.fullLabel, textRoom(w), w.textPx, boldText(w.kind), m);
        }
    }

    public int px(float base) { return Math.max(9, Math.round(base * s)); }

    private Widget add(String id, Kind k, float x, float y, float ww, float hh, String label) {
        Widget wd = new Widget(id, k, new Rect(x, y, ww, hh), label);
        widgets.add(wd);
        return wd;
    }

    /** Rótulo que cabe na largura (com reticências), nunca sai do controle. */
    static String fit(String t, float maxW, int px, boolean bold, Measure m) {
        if (m.width(t, px, bold) <= maxW) return t;
        String e = "…";
        for (int n = t.length() - 1; n > 0; n--) {
            String c = t.substring(0, n).trim() + e;
            if (m.width(c, px, bold) <= maxW) return c;
        }
        return e;
    }

    /** Rodada 11.1 — aberto pelo menu: "Cancelar" no canto superior direito (fecha sem gravar nada). */
    private void cancelButton(CreatorState st, float mg) {
        if (st.cancelLabel != null) add("cancel", Kind.BUTTON, w - mg - 260 * s, mg, 260 * s, 60 * s, st.cancelLabel);
    }

    // ------------------------------------------------------------------ variante A

    private void buildA(CreatorState st, ComponentCatalog cat, Measure m) {
        float mg = 28 * s, tabW = 230 * s, optW = 584 * s, top = 132 * s;
        add("title", Kind.LABEL, mg, mg, 900 * s, 54 * s, "Criar aventureiro").size = 1;
        add("subtitle", Kind.LABEL, mg, mg + 52 * s, 900 * s, 30 * s, "Escolha a aparência do seu pequeno herói").size = 2;
        cancelButton(st, mg);
        // abas
        add("tabs-panel", Kind.PANEL, mg, top, tabW, (82 * s) * CreatorState.Category.values().length + 20 * s, null);
        float ty = top + 10 * s;
        for (CreatorState.Category c : CreatorState.Category.values()) {
            Widget t = add("cat:" + c.name(), Kind.TAB, mg + 10 * s, ty, tabW - 20 * s, 72 * s, c.label);
            t.selected = st.category == c;
            ty += 82 * s;
        }
        // painel de opções + nome
        float ox = w - mg - optW, actH = 70 * s, actY = h - mg - actH;
        options = new Rect(ox, top, optW, actY - 16 * s - top);
        add("options-panel", Kind.PANEL, options.x, options.y, options.w, options.h, null);
        buildOptions(st, cat, m, options);
        // ações (abaixo do painel)
        float bw = (optW - 3 * 12 * s) / 4;
        String[][] acts = {{"restore", "Restaurar"}, {"save", "Salvar"}, {"load", "Carregar"}, {"done", "Pronto"}};
        for (int i = 0; i < 4; i++) {
            Widget b = add(acts[i][0], Kind.BUTTON, ox + i * (bw + 12 * s), actY, bw, actH, acts[i][1]);
            if (acts[i][0].equals("restore") && st.isOriginal()) { b.enabled = false; b.reason = "Já é o modelo original"; }
            if (acts[i][0].equals("load") && st.saves.isEmpty()) { b.enabled = false; b.reason = "Nenhuma aparência salva ainda"; }
            if (acts[i][0].equals("done")) b.selected = true;
        }
        // área do personagem: entre as abas e o painel
        float sx = mg + tabW + 24 * s, sw = ox - 24 * s - sx;
        scene = new Rect(sx, 0, sw, h);
        // poses + câmera sob o personagem
        String[][] poses = {{"pose:IDLE", "Parado"}, {"pose:WALK", "Andar"}, {"pose:RUN", "Correr"}, {"pose:ATTACK", "Ação"}, {"pose:EMOTE", "Aceno"}};
        float pb = Math.min(118 * s, (sw - 4 * 10 * s) / 5f), ph = 52 * s, py = h - mg - ph;
        float px0 = sx + (sw - (5 * pb + 4 * 10 * s)) / 2;
        for (int i = 0; i < poses.length; i++) {
            Widget p = add(poses[i][0], Kind.TOGGLE, px0 + i * (pb + 10 * s), py, pb, ph, poses[i][1]);
            p.selected = st.pose.name().equals(poses[i][0].substring(5));
        }
        String[][] cams = {{"cam:face", "Rosto"}, {"cam:body", "Corpo"}, {"cam:reset", "Centralizar"}};
        float cb = 150 * s, cy = py - ph - 12 * s, cx0 = sx + (sw - (3 * cb + 2 * 10 * s)) / 2;
        for (int i = 0; i < 3; i++) add(cams[i][0], Kind.TOGGLE, cx0 + i * (cb + 10 * s), cy, cb, ph * 0.86f, cams[i][1]);
    }

    // ------------------------------------------------------------------ variante B (ateliê)

    private void buildB(CreatorState st, ComponentCatalog cat, Measure m) {
        float mg = 28 * s, stripH = 330 * s;
        add("title", Kind.LABEL, mg, mg, 700 * s, 54 * s, "Ateliê do aventureiro").size = 1;
        cancelButton(st, mg);
        float tw = 214 * s, tx = (w - CreatorState.Category.values().length * (tw + 10 * s)) / 2, ty = mg + 64 * s;
        for (CreatorState.Category c : CreatorState.Category.values()) {
            Widget t = add("cat:" + c.name(), Kind.TAB, tx, ty, tw, 60 * s, c.label);
            t.selected = st.category == c;
            tx += tw + 10 * s;
        }
        options = new Rect(mg, h - mg - stripH, w - 2 * mg - 260 * s, stripH);
        add("options-panel", Kind.PANEL, options.x, options.y, options.w, options.h, null);
        buildOptions(st, cat, m, options);
        float ax = w - mg - 240 * s, bh = 70 * s;
        String[][] acts = {{"restore", "Restaurar"}, {"save", "Salvar"}, {"load", "Carregar"}, {"done", "Pronto"}};
        for (int i = 0; i < 4; i++) {
            Widget b = add(acts[i][0], Kind.BUTTON, ax, options.y + i * (bh + 12 * s), 240 * s, bh, acts[i][1]);
            if (acts[i][0].equals("restore") && st.isOriginal()) { b.enabled = false; b.reason = "Já é o modelo original"; }
            if (acts[i][0].equals("load") && st.saves.isEmpty()) { b.enabled = false; b.reason = "Nenhuma aparência salva ainda"; }
        }
        scene = new Rect(0, ty + 70 * s, w, options.y - ty - 80 * s);
        String[][] poses = {{"pose:IDLE", "Parado"}, {"pose:WALK", "Andar"}, {"pose:RUN", "Correr"}, {"pose:ATTACK", "Ação"}, {"pose:EMOTE", "Aceno"}};
        float pb = 118 * s, ph = 48 * s, py = options.y - ph - 14 * s, px0 = (w - (5 * pb + 40 * s)) / 2;
        for (int i = 0; i < 5; i++) {
            Widget p = add(poses[i][0], Kind.TOGGLE, px0 + i * (pb + 10 * s), py, pb, ph, poses[i][1]);
            p.selected = st.pose.name().equals(poses[i][0].substring(5));
        }
    }

    // ------------------------------------------------------------------ conteúdo de cada categoria

    // coluna corrente do painel de opções (no B a faixa tem duas colunas: miniaturas | cores e extras)
    private float cx, cw, cTop, cMax;
    private boolean split;

    /** B: passa para a segunda coluna; A: segue na mesma. */
    private float nextColumn(float y, Rect box, float pad) {
        if (variant != 'B' || split) return y;
        split = true;
        cMax = Math.max(cMax, y);
        float left = (box.w - 2 * pad) * 0.56f;
        cx = box.x + pad + left + 26 * s;
        cw = box.w - 2 * pad - left - 26 * s;
        return cTop;
    }

    private void buildOptions(CreatorState st, ComponentCatalog cat, Measure m, Rect box) {
        float pad = 22 * s;
        cx = box.x + pad;
        cw = variant == 'B' ? (box.w - 2 * pad) * 0.56f : box.w - 2 * pad;
        float y = box.y + pad - st.scroll;
        cTop = y;
        cMax = y;
        split = false;
        boolean b = variant == 'B';
        Appearance a = st.appearance;
        switch (st.category) {
            case MODELO -> {
                y = heading("Modelo-base", "Aparência inicial (não define classe)", cx, y, cw);
                y = thumbs(cat.presets.keySet().stream().map(p -> "preset:" + p).toList(), "preset", a.preset, cx, y, cw, 3,
                        b ? 0.62f : 1.25f, st, cat, m);
                y = nextColumn(y, box, pad);
                // nome do aventureiro (identidade; não muda a geometria)
                y = heading("Nome", null, cx, y + (b ? 0 : 10 * s), cw);
                Widget f = add("name", Kind.FIELD, cx, y, cw, 54 * s, st.name.isEmpty() ? " " : st.name);
                f.selected = st.editingName;
                f.scrolls = true;
                y += 54 * s + 16 * s;
            }
            case CABELO -> {
                y = heading("Penteado", null, cx, y, cw);
                // rodada 09.4: com mais de 4 penteados, 2 colunas viravam 3 fileiras e empurravam "Cor do cabelo" para
                // fora do painel (o clique em "loiro" caía num controle recortado); 3 colunas mantêm as cores à vista
                List<String> hairIds = ordered(cat.ids("cabelo"), HAIR_ORDER);
                y = thumbs(hairIds.stream().map(i -> "hair:" + i).toList(), "hair", cat.hair(a),
                        cx, y, cw, b ? 4 : hairIds.size() > 4 ? 3 : 2, b ? 0.9f : 0.66f, st, cat, m);
                y = nextColumn(y, box, pad);
                y = heading("Cor do cabelo", null, cx, y + (b ? 0 : 8 * s), cw);
                y = swatches("hairColor", cat.colors.get("cabelo"), cat.hairColor(a), cx, y, cw, 5, m);
            }
            case ROSTO -> {
                y = heading("Rosto", null, cx, y, cw);
                y = thumbs(cat.ids("rosto").stream().map(i -> "face:" + i).toList(), "face", cat.face(a), cx, y, cw, 3,
                        b ? 0.62f : 1.0f, st, cat, m);
                y = nextColumn(y, box, pad);
                // rodada 09.4: formato dos olhos (primeiro passo do rosto por partes); o desenho segue a atuação do rosto.
                // Na 2ª coluna do layout largo (B), junto da cor — antes da troca de coluna ele saía da tela em 2560×1080
                y = heading(b ? "Olhos" : "Formato dos olhos", null, cx, y + (b ? 0 : 6 * s), cw);
                if (!b) y = thumbs(cat.ids("olhos").stream().map(i -> "eyes:" + i).toList(), "eyes", cat.eyes(a), cx, y, cw, 3,
                        0.62f, st, cat, m);
                else {                                                   // ateliê (B): pouca altura → botões de alternância
                    List<String> eyes = cat.ids("olhos");
                    float ew = (cw - (eyes.size() - 1) * 10 * s) / eyes.size();
                    for (int i = 0; i < eyes.size(); i++) {
                        Widget t = add("eyes:" + eyes.get(i), Kind.TOGGLE, cx + i * (ew + 10 * s), y, ew, 48 * s, CreatorState.name(eyes.get(i)));
                        t.selected = eyes.get(i).equals(cat.eyes(a));
                        t.scrolls = true;
                    }
                    y += 48 * s + 8 * s;
                }
                if (!b) y = heading("Cor dos olhos", null, cx, y + 8 * s, cw);   // B: um título só para formato e cor
                y = swatches("iris", cat.colors.get("iris"), cat.iris(a), cx, y, cw, 5, m);
                y = heading("Expressão (prévia)", null, cx, y + 8 * s, cw);
                String[] ex = {"neutral", "joy", "determined", "surprise"};
                float bw = (cw - 3 * 10 * s) / 4;
                for (int i = 0; i < 4; i++) {
                    Widget t = add("expr:" + ex[i], Kind.TOGGLE, cx + i * (bw + 10 * s), y, bw, 48 * s, CreatorState.name(ex[i]));
                    t.selected = ex[i].equals(st.expr) || (st.expr == null && i == 0);
                    t.scrolls = true;
                }
                y += 48 * s + 12 * s;
            }
            case PELE -> {
                y = heading("Tom de pele", null, cx, y, cw);
                y = swatches("skin", cat.colors.get("pele"), cat.skin(a), cx, y, cw, b ? 5 : 3, m);
            }
            case ROUPA -> {                                              // rodada 09.4: roupa por região (botas)
                y = heading("Botas", null, cx, y, cw);
                y = thumbs(cat.ids("pes").stream().map(i -> "feet:" + i).toList(), "feet", cat.feet(a), cx, y, cw, b ? 3 : 2,
                        b ? 0.62f : 0.82f, st, cat, m);
            }
            case CHAPEU -> {
                y = heading("Chapéu", null, cx, y, cw);
                List<String> ids = new ArrayList<>(List.of("cover:" + Appearance.NO_COVER));
                for (String c : cat.ids("cabeca")) ids.add("cover:" + c);
                String cur = cat.cover(a);
                y = thumbs(ids, "cover", cur == null ? Appearance.NO_COVER : cur, cx, y, cw, b ? 3 : 2, b ? 0.62f : 0.82f, st, cat, m);
            }
        }
        y = Math.max(y, cMax);
        contentHeight = y + st.scroll - box.y + pad;
        optionsContent = new Rect(box.x, box.y, box.w, box.h);
    }

    /** Ordem de exibição dos penteados (do mais curto e espetado ao preso); ids fora da lista vão ao fim. */
    static final List<String> HAIR_ORDER = List.of("cabelo.topete", "cabelo.curto", "cabelo.chanel", "cabelo.rabo",
            "cabelo.crespo", "cabelo.coque");                  // rodada 09.4: dois estudos experimentais no fim

    static List<String> ordered(List<String> ids, List<String> order) {
        List<String> out = new ArrayList<>();
        for (String o : order) if (ids.contains(o)) out.add(o);
        for (String i : ids) if (!out.contains(i)) out.add(i);
        return out;
    }

    private float heading(String t, String sub, float x, float y, float iw) {
        add("h:" + t, Kind.LABEL, x, y, iw, 34 * s, t);
        y += 36 * s;
        if (sub != null) {
            add("h2:" + t, Kind.LABEL, x, y, iw, 26 * s, sub).size = 2;
            y += 30 * s;
        }
        return y + 4 * s;
    }

    /** Grade de miniaturas reais (payload = ação; o desenho busca a miniatura pelo id). */
    private float thumbs(List<String> ids, String kind, String current, float x, float y, float iw, int cols, float aspect,
                         CreatorState st, ComponentCatalog cat, Measure m) {
        float gap = 12 * s, tw = (iw - (cols - 1) * gap) / cols, th = tw * aspect, lh = 34 * s;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i), value = id.substring(id.indexOf(':') + 1);
            float tx = x + (i % cols) * (tw + gap), tyy = y + (i / cols) * (th + lh + gap);
            Widget t = add(id, Kind.THUMB, tx, tyy, tw, th + lh, CreatorState.name(value));
            t.payload = value;
            t.selected = value.equals(current);
            t.scrolls = true;
            Appearance cand = st.appearance.copy();
            switch (kind) {
                case "preset" -> { cand.preset = value; cand.reset(); }
                case "hair" -> cand.hair = value;
                case "face" -> cand.face = value;
                case "eyes" -> cand.eyes = value;
                case "feet" -> cand.feet = value;
                case "cover" -> cand.cover = value;
                default -> { }
            }
            try {
                cand.validate(cat);
            } catch (Appearance.InvalidAppearance e) {
                t.enabled = false;
                // rodada 09.4: num PENTEADO o conflito é com a cobertura atual (antes a frase falava do penteado)
                t.reason = kind.equals("hair") && e.getMessage().contains("incompatível") ? "Não combina com o chapéu atual"
                        : reasonFor(e.getMessage());
            }
        }
        int rows = (ids.size() + cols - 1) / cols;
        return y + rows * (th + lh + gap) + 6 * s;
    }

    static String reasonFor(String msg) {
        if (msg.contains("incompatível")) return "Não combina com o penteado atual";
        return msg;
    }

    private float swatches(String kind, List<String[]> colors, String current, float x, float y, float iw, int cols, Measure m) {
        float gap = 12 * s, sw = (iw - (cols - 1) * gap) / cols, sh = sw * 0.62f, lh = 30 * s;
        for (int i = 0; i < colors.size(); i++) {
            String[] c = colors.get(i);
            Widget t = add(kind + ":" + c[0], Kind.SWATCH, x + (i % cols) * (sw + gap), y + (i / cols) * (sh + lh + gap), sw, sh + lh,
                    CreatorState.name(c[0]));
            t.payload = c[1];
            t.selected = c[1].equalsIgnoreCase(current);
            t.scrolls = true;
        }
        int rows = (colors.size() + cols - 1) / cols;
        return y + rows * (sh + lh + gap) + 6 * s;
    }

    // ------------------------------------------------------------------ diálogos e vitrine

    private void buildDialog(CreatorState st, Measure m) {
        if (st.dialog == CreatorState.Dialog.NONE) return;
        float dw = 760 * s, dh = st.dialog == CreatorState.Dialog.LOAD ? 620 * s : 330 * s;
        float dx = (w - dw) / 2, dy = (h - dh) / 2;
        add("dialog", Kind.DIALOG, dx, dy, dw, dh, null);
        float bw = 300 * s, bh = 66 * s, by = dy + dh - bh - 30 * s;
        switch (st.dialog) {
            case CONFIRM_RESTORE -> {
                add("d:title", Kind.LABEL, dx + 40 * s, dy + 36 * s, dw - 80 * s, 50 * s, "Voltar ao modelo original?");
                add("d:text", Kind.LABEL, dx + 40 * s, dy + 96 * s, dw - 80 * s, 30 * s,
                        fit("As escolhas que não foram salvas serão perdidas.", dw - 80 * s, px(22), false, m)).size = 2;
                add("restore-confirm", Kind.BUTTON, dx + dw / 2 - bw - 12 * s, by, bw, bh, "Voltar ao original").selected = true;
                add("dialog-cancel", Kind.BUTTON, dx + dw / 2 + 12 * s, by, bw, bh, "Cancelar");
            }
            case DONE -> {
                add("d:title", Kind.LABEL, dx + 40 * s, dy + 36 * s, dw - 80 * s, 50 * s, "Aparência pronta!");
                add("d:text", Kind.LABEL, dx + 40 * s, dy + 96 * s, dw - 80 * s, 30 * s,
                        fit("A aparência de " + (st.name.isBlank() ? "seu aventureiro" : st.name.trim()) + " está pronta para a aventura.",
                                dw - 80 * s, px(22), false, m)).size = 2;
                if (st.finishLabel != null) add("finish", Kind.BUTTON, dx + dw / 2 - bw - 12 * s, by, bw, bh, st.finishLabel).selected = true;
                else add("showcase", Kind.BUTTON, dx + dw / 2 - bw - 12 * s, by, bw, bh, "Ver em movimento").selected = true;
                add("dialog-cancel", Kind.BUTTON, dx + dw / 2 + 12 * s, by, bw, bh, "Continuar editando");
            }
            case CONFIRM_QUIT -> {                                         // rodada 11.1: o X da janela com alterações
                float b3 = (dw - 80 * s - 2 * 16 * s) / 3;
                add("d:title", Kind.LABEL, dx + 40 * s, dy + 36 * s, dw - 80 * s, 50 * s, "Sair do jogo?");
                add("d:text", Kind.LABEL, dx + 40 * s, dy + 96 * s, dw - 80 * s, 30 * s,
                        fit("As mudanças deste personagem ainda não foram salvas.", dw - 80 * s, px(22), false, m)).size = 2;
                add("quit-save", Kind.BUTTON, dx + 40 * s, by, b3, bh, "Salvar e sair").selected = true;
                add("quit-discard", Kind.BUTTON, dx + 40 * s + b3 + 16 * s, by, b3, bh, "Sair sem salvar");
                add("dialog-cancel", Kind.BUTTON, dx + 40 * s + 2 * (b3 + 16 * s), by, b3, bh, "Cancelar");
            }
            case LOAD -> {
                add("d:title", Kind.LABEL, dx + 40 * s, dy + 30 * s, dw - 80 * s, 50 * s, "Carregar aparência");
                float ly = dy + 96 * s - st.loadScroll, lh = 64 * s;
                for (int i = 0; i < st.saves.size(); i++) {
                    String file = st.saves.get(i).getFileName().toString();
                    Widget it = add("loadfile:" + i, Kind.LIST_ITEM, dx + 40 * s, ly + i * (lh + 8 * s), dw - 80 * s, lh,
                            file.replace(".json", ""));
                    it.scrolls = true;
                }
                add("dialog-cancel", Kind.BUTTON, dx + (dw - bw) / 2, by, bw, bh, "Fechar");
            }
            default -> { }
        }
    }

    private void buildShowcase() {
        float mg = 28 * s;
        add("title", Kind.LABEL, mg, mg, 900 * s, 54 * s, "Pronto para a aventura").size = 1;
        add("showcase-exit", Kind.BUTTON, w - mg - 330 * s, h - mg - 70 * s, 330 * s, 70 * s, "Voltar ao criador").selected = true;
        scene = new Rect(0, 0, w, h);
        options = null;
    }

    /** Controle interativo sob o ponto (de cima para baixo: diálogo primeiro). */
    public Widget hit(float x, float y, CreatorState st) {
        boolean modal = st.dialog != CreatorState.Dialog.NONE;
        Rect dialog = null;
        for (Widget wd : widgets) if (wd.kind == Kind.DIALOG) dialog = wd.r;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget wd = widgets.get(i);
            if (!wd.interactive() || !wd.r.contains(x, y)) continue;
            if (wd.scrolls && options != null && !wd.id.startsWith("loadfile:") && !options.contains(x, y)) continue;
            if (modal && (dialog == null || !wd.r.inside(dialog))) continue;
            return wd;
        }
        return null;
    }

    public Widget get(String id) {
        for (Widget wd : widgets) if (wd.id.equals(id)) return wd;
        return null;
    }
}
