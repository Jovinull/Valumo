package vx.game;

import vx.engine.Framebuffer;
import vx.engine.PixelFont;
import vx.engine.Texture;
import vx.game.menu.CharacterStore;
import vx.game.menu.Cinematic;
import vx.game.menu.MenuFlow;
import vx.game.menu.WorldStore;
import vx.viewer.App;
import vx.viewer.CharacterModel;
import vx.viewer.Composer;
import vx.viewer.Renderer;
import vx.viewer.UiKit;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.GL_RGBA8;

/**
 * Rodada 11.1 — o desenho da porta de entrada (menu principal, personagens, mundos, criar mundo, configurações) e os
 * recursos de GL que só ela usa: retratos dos personagens (o mesmo caminho das miniaturas do criador: composição
 * assíncrona + {@link App#renderPortrait}) e prévias dos mundos (PNG por mundo, FBO da vila e do formulário). O estado e
 * as regras ficam no {@link MenuFlow} (sem OpenGL); o {@link Game} cumpre os pedidos. Fecha tudo em {@link #close()}.
 */
final class MenuView implements AutoCloseable {
    /**
     * Leiaute do menu principal. A (lateral) é o oficial; B (central) existe só para a comparação da rodada 11.1
     * ({@code -Dvx.menu.layout=B}), como as outras comparações do projeto.
     */
    static String layout = System.getProperty("vx.menu.layout", "A");
    static final int PORTRAIT = 192, PREVIEW_W = 640, PREVIEW_H = 360;
    /** Tamanho base dos botões das telas de menu (escala 3 da fonte em 1080p, 2 em 720p; o menu principal usa 30). */
    static final float MENU_BUTTON = 27f;

    final Game g;
    final MenuFlow flow;
    final Cinematic cine;
    Cinematic.Pose pose;
    /** Giro do herói na tela de personagens (só desenho: arrastar com o mouse). */
    float previewYaw;
    boolean dragging;

    MenuView(Game g, MenuFlow flow, Cinematic cine) {
        this.g = g;
        this.flow = flow;
        this.cine = cine;
        pose = cine.at(0f);
    }

    // ------------------------------------------------------------------ retratos (miniaturas dos personagens)

    private static final class Slot { String sig, failed; Framebuffer fbo; }

    private final Map<String, Slot> portraits = new HashMap<>();
    private final Set<String> pendingPortraits = new HashSet<>();
    private final ArrayDeque<Runnable> toRender = new ArrayDeque<>();
    private Renderer thumbRenderer;
    int portraitsRendered;

    /** Textura do retrato (o anterior continua até o novo ficar pronto); pede a composição se faltar. */
    Integer portrait(CharacterStore.Profile p) {
        String key = p.appearance().signature();
        Slot s = portraits.computeIfAbsent(p.id(), k -> new Slot());
        if (!key.equals(s.sig) && !key.equals(s.failed) && pendingPortraits.add(p.id() + "=" + key)) {
            g.async.requestThumb(p.appearance(), prep -> toRender.add(() -> {
                pendingPortraits.remove(p.id() + "=" + key);
                if (prep == null) { s.failed = key; return; }
                CharacterModel m = g.async.upload(prep);
                try {
                    if (s.fbo == null) s.fbo = new Framebuffer(PORTRAIT, PORTRAIT, false, GL_RGBA8);
                    if (thumbRenderer == null) thumbRenderer = new Renderer();
                    App.renderPortrait(thumbRenderer, m, s.fbo, "cabeca");
                    s.sig = key;
                    portraitsRendered++;
                } finally {
                    Composer.release(m);
                }
            }));
        }
        return s.fbo == null ? null : s.fbo.color[0];
    }

    /** Thread do GL, antes do quadro: até dois retratos prontos por quadro. */
    void pump() {
        for (int i = 0; i < 2 && !toRender.isEmpty(); i++) toRender.poll().run();
    }

    boolean portraitsIdle() { return pendingPortraits.isEmpty() && toRender.isEmpty(); }

    // ------------------------------------------------------------------ prévias dos mundos

    private record Pic(String key, Texture tex) { }

    private final Map<String, Pic> pics = new HashMap<>();
    /** Mundos cuja prévia falhou nesta sessão (não tenta de novo a cada quadro). */
    final Set<String> previewFailed = new HashSet<>();
    /** Prévia da vila fixa (desenhada do cenário do menu) e a do formulário de criar mundo (da semente {@link #formSeed}). */
    Framebuffer legacyPic, formPic;
    Long formSeed;

    /** O mundo precisa de prévia (nunca teve, arquivo sumiu, ou é de outra versão do gerador/da prévia)? */
    boolean needsPreview(WorldStore.World w) {
        if (w.legacy()) return legacyPic == null;
        return !w.expectedPreviewKey().equals(w.previewKey()) || w.summary() == null || !Files.isRegularFile(flow.worlds.previewFile(w.id()));
    }

    /** Textura da prévia (null = ainda não há); {@code fbo} diz se a textura é de FBO (v invertido). */
    private Integer preview(WorldStore.World w, boolean[] fbo) {
        fbo[0] = w.legacy();
        if (w.legacy()) return legacyPic == null ? null : legacyPic.color[0];
        if (needsPreview(w)) return null;
        String key = w.id() + "#" + w.previewKey();
        Pic p = pics.get(w.id());
        if (p == null || !p.key().equals(key)) {
            if (p != null) p.tex().close();
            try {
                p = new Pic(key, Texture.load(flow.worlds.previewFile(w.id()), false));
            } catch (RuntimeException e) {
                previewFailed.add(w.id());
                pics.remove(w.id());
                return null;
            }
            pics.put(w.id(), p);
        }
        return p.tex().id;
    }

    /** Esquece a textura de um mundo (a prévia foi refeita). */
    void forget(String worldId) {
        Pic p = pics.remove(worldId);
        if (p != null) p.tex().close();
    }

    /** Resumos de progresso (personagem × mundo), lidos uma vez por lista. */
    private final Map<String, String> progress = new HashMap<>();

    void invalidate() { progress.clear(); }

    private String progressOf(WorldStore.World w, CharacterStore.Profile p) {
        if (p == null) return null;
        return progress.computeIfAbsent(w.id() + "/" + p.id(), k -> {
            String s = WorldStore.progress(flow.worlds.adventureDir(w.id(), p.id()));
            return s == null ? "" : s;
        });
    }

    @Override
    public void close() {
        for (Slot s : portraits.values()) if (s.fbo != null) s.fbo.close();
        portraits.clear();
        for (Pic p : pics.values()) p.tex().close();
        pics.clear();
        if (legacyPic != null) legacyPic.close();
        if (formPic != null) formPic.close();
        legacyPic = formPic = null;
        if (thumbRenderer != null) thumbRenderer.close();
        thumbRenderer = null;
    }

    // ------------------------------------------------------------------ desenho

    private UiKit kit() { return g.kit; }

    private float s() { return g.s; }

    /** Troca de tela: um véu curto que clareia (0,18 s), sem atrasar a navegação (a tela nova já responde). */
    private MenuFlow.Screen lastScreen;
    private float changedAt = -9f;
    static final float SCREEN_FADE = 0.18f;

    void draw(int w, int h) {
        float s = s();
        if (flow.screen != lastScreen) { lastScreen = flow.screen; changedAt = flow.time; }
        switch (flow.screen) {
            case MAIN -> drawMain(w, h);
            case CHARACTERS -> drawCharacters(w, h);
            case WORLDS -> drawWorlds(w, h);
            case CREATE_WORLD -> { drawWorlds(w, h); g.buttons.clear(); drawCreate(w, h); }   // modal: só os controles dele valem
            case SETTINGS -> drawSettings(w, h);
            case LOADING -> { }
        }
        if (flow.message != null && flow.time < flow.messageUntil) {
            int p = g.px(21);
            String msg = g.fit(flow.message, p, w - 120 * s);
            float bw = kit().width(msg, p, true) + 60 * s, y0 = 40 * s;      // no topo: embaixo ficam a plaqueta e os botões
            kit().chamfer((w - bw) / 2, y0, bw, 52 * s, 6 * s, UiKit.WOOD_D);
            kit().chamfer((w - bw) / 2 + 3 * s, y0 + 3 * s, bw - 6 * s, 46 * s, 5 * s, UiKit.PARCH_L);
            kit().text((w - bw) / 2 + 30 * s, y0 + 26 * s - p * 0.66f, msg, p, true, UiKit.INK);
        }
        float k = 1f - (flow.time - changedAt) / SCREEN_FADE;
        if (k > 0f) kit().rect(0, 0, w, h, 0x0e0a0800 | Math.round(110 * k * k));
    }

    /** Escurecimento da troca de tomada (desenhado por cima da cena, antes da interface). */
    void drawFade(int w, int h) {
        if (pose == null || flow.screen == MenuFlow.Screen.CHARACTERS) return;
        int a = Math.round(255 * Math.clamp(pose.fade(), 0f, 1f));
        if (a > 0) kit().rect(0, 0, w, h, 0x0e0a0800 | a);
    }

    /**
     * O logotipo: "VALUMO" na fonte de pixel própria, em blocos com profundidade de voxel (camadas de madeira descendo à
     * direita), contorno de tinta, face dourada, faixa clara no topo de cada letra e a semente P1 como acento. Tratamento
     * tipográfico original do projeto; nenhum logotipo de outro jogo como referência.
     */
    void logo(float x, float y, int p, boolean center) { logo(g, x, y, p, center); }

    /** O logotipo sem precisar de um menu aberto (a tela de carregamento entre sessões também o usa). */
    static void logo(Game g, float x, float y, int p, boolean center) {
        UiKit k = g.kit;
        String t = "VALUMO";
        int u = PixelFont.scale(p);
        float tw = k.width(t, p, true), x0 = center ? x - tw / 2 : x;
        for (int d = 5; d >= 1; d--) k.text(x0 + d * u * 0.5f, y + d * u * 0.75f, t, p, true, d > 3 ? 0x20140eff : UiKit.WOOD_D);
        for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) k.text(x0 + o[0] * u, y + o[1] * u, t, p, true, 0x2a1a12ff);
        k.text(x0, y, t, p, true, UiKit.GOLD);
        k.text(x0, y - u, t, p, true, 0xf6d27aff);                       // face superior (luz de cima)
        k.text(x0, y, t, p, true, UiKit.GOLD);
        k.ui.rect(x0, y + p * 0.98f, tw, Math.max(2, u), 0x2a1a1290);   // linha de base
        k.seed(x0 + tw + u * 1.5f, y + u, Math.max(1f, u / 3f));
    }

    private void subtitle(float x, float y, boolean center) {
        String t = "Um RPG de aventura em voxel";
        int p = g.px(24);
        float tw = kit().width(t, p, false), x0 = center ? x - tw / 2 : x;
        kit().text(x0 + 2 * s(), y + 2 * s(), t, p, false, 0x20140ec0);
        kit().text(x0, y, t, p, false, 0xfff3dcff);
    }

    private void drawMain(int w, int h) {
        float s = s();
        String[] ids = MenuFlow.mainItems(), labels = {"Jogar", "Configurações", "Sair"};
        boolean b = layout.equals("B");
        float bw = 440 * s, bh = 76 * s, gap = 16 * s, bx, by;
        if (!b) {                                                          // A: coluna à esquerda sobre um véu de leitura
            float vw = Math.max(620 * s, w * 0.42f);
            int cols = 32;
            for (int i = 0; i < cols; i++) {
                float k = 1f - i / (float) cols;
                kit().rect(i * vw / cols, 0, vw / cols + 1, h, 0x140e0a00 | Math.round(185 * k * k));
            }
            float lx = 72 * s, ly = Math.max(70 * s, h * 0.16f);
            logo(lx, ly, g.px(128), false);
            subtitle(lx + 4 * s, ly + g.px(128) * 1.25f, false);
            bx = lx;
            by = Math.max(ly + g.px(128) * 1.25f + 90 * s, h * 0.50f);
        } else {                                                           // B: centro, véus no topo e embaixo
            for (int i = 0; i < 24; i++) {
                float k = 1f - i / 24f;
                kit().rect(0, i * h * 0.4f / 24, w, h * 0.4f / 24 + 1, 0x140e0a00 | Math.round(160 * k * k));
                kit().rect(0, h - (i + 1) * h * 0.45f / 24, w, h * 0.45f / 24 + 1, 0x140e0a00 | Math.round(170 * k * k));
            }
            logo(w / 2f, Math.max(48 * s, h * 0.12f), g.px(128), true);
            subtitle(w / 2f, Math.max(48 * s, h * 0.12f) + g.px(128) * 1.25f, true);
            bx = (w - bw) / 2;
            by = Math.max(h * 0.52f, Math.max(48 * s, h * 0.12f) + g.px(128) * 1.25f + 80 * s);
        }
        by = Math.min(by, h - 28 * s - 60 * s - 3 * (bh + gap));
        g.groupPx = g.groupPxFrom(30, labels[0], bw, labels[1], bw, labels[2], bw);
        for (int i = 0; i < ids.length; i++) {
            float y = by + i * (bh + gap);
            g.button(ids[i], bx, y, bw, bh, labels[i], i == 0);
            if (i == flow.mainFocus) kit().seed(bx - 26 * s, y + bh / 2 - 4 * s, s);
        }
        g.groupPx = 0;
    }

    /** Cabeçalho das telas (título + linha) sobre a faixa escura do topo. */
    private void header(String t, String sub) { g.title(t, sub); }

    private static String date(long ms) {
        if (ms <= 0) return "—";
        return DateTimeFormatter.ofPattern("dd/MM/yyyy").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    /** Janela da lista que segue a seleção: índice do primeiro visível. */
    private static int first(int sel, int rows, int n) { return Math.clamp(sel - rows + 1, 0, Math.max(0, n - rows)); }

    private void image(int tex, float x, float y, float w, float h, boolean fbo, int tint) {
        kit().ui.quad(tex, x, y, w, h, 0f, fbo ? 1f : 0f, 1f, fbo ? 0f : 1f, tint);
    }

    private void drawCharacters(int w, int h) {
        float s = s();
        UiKit k = kit();
        header("Personagens", "Escolha quem vai viver a aventura");
        float pw = Math.min(640 * s, w * 0.46f), x0 = 28 * s, y0 = 132 * s, ph = h - y0 - 28 * s;
        k.panel(x0, y0, pw, ph, s, false);
        float x = x0 + 22 * s, iw = pw - 44 * s, y = y0 + 22 * s;
        float bh = 58 * s, gap = 10 * s, hw = (iw - gap) / 2;
        // escala comum dos quatro botões; se "Novo personagem / Editar aparência" lado a lado forçariam o grupo a encolher,
        // cada um ganha uma linha inteira (legibilidade antes de economia de espaço)
        int side = g.groupPxFrom(MENU_BUTTON, "Voltar", hw, "Escolher", hw, "Novo personagem", hw, "Editar aparência", hw);
        int stack = g.groupPxFrom(MENU_BUTTON, "Voltar", hw, "Escolher", hw, "Novo personagem", iw, "Editar aparência", iw);
        boolean stacked = stack > side;
        int bRows = stacked ? 3 : 2;
        float bottom = y0 + ph - 22 * s - bh * bRows - gap * (bRows - 1) - 14 * s;
        float ch = 108 * s, cg = 10 * s;
        List<CharacterStore.Profile> l = flow.profiles;
        if (l.isEmpty()) {
            for (String line : g.wrap("Nenhum personagem ainda.", g.px(21), iw)) {
                k.text(x, y, line, g.px(21), false, UiKit.INK_SOFT);
                y += g.px(21) * 1.35f;
            }
        }
        int rows = Math.max(1, (int) ((bottom - y) / (ch + cg)));
        int f = first(flow.profileSel, rows, l.size());
        if (f > 0) k.text(x + iw - k.width("↑ " + f, g.px(18), false), y0 + 2 * s, "↑ " + f, g.px(18), false, UiKit.PARCH_L);
        for (int i = f; i < Math.min(l.size(), f + rows); i++) {
            CharacterStore.Profile p = l.get(i);
            boolean sel = i == flow.profileSel, hv = ("personagem:" + i).equals(g.hover);
            g.buttons.add(new Game.Button("personagem:" + i, x, y, iw, ch, p.name(), false));
            k.chamfer(x, y, iw, ch, 4 * s, sel ? UiKit.GOLD : hv ? UiKit.WOOD_L : UiKit.WOOD);
            k.chamfer(x + 3 * s, y + 3 * s, iw - 6 * s, ch - 6 * s, 3 * s, sel ? UiKit.PARCH_L : UiKit.PARCH_D);
            float q = ch - 20 * s;
            k.rect(x + 10 * s, y + 10 * s, q, q, UiKit.WOOD_D);
            Integer tex = portrait(p);
            if (tex != null) image(tex, x + 12 * s, y + 12 * s, q - 4 * s, q - 4 * s, true, 0xffffffff);
            else waiting(x + 10 * s, y + 10 * s, q, q);
            float tx = x + q + 26 * s, room = iw - q - 40 * s;
            int pn = g.textPx(p.name(), room, 26);
            k.text(tx, y + 16 * s, g.fit(p.name(), pn, room), pn, true, UiKit.INK);
            String origin = GameLogic.presetName(p.origin()) + " · criado em " + date(p.createdAt());
            k.text(tx, y + 54 * s, g.fit(origin, g.px(18), room), g.px(18), false, UiKit.INK_SOFT);
            k.text(tx, y + 78 * s, p.usedAt() != p.createdAt() ? "última aventura em " + date(p.usedAt()) : "ainda sem aventura", g.px(18), false, UiKit.INK_SOFT);
            if (sel) k.seed(x + iw - 26 * s, y + 14 * s, s);
            y += ch + cg;
        }
        int below = l.size() - (f + rows);
        if (below > 0) k.text(x, y, "+" + below, g.px(18), false, UiKit.INK_SOFT);
        if (!flow.brokenProfiles.isEmpty())
            k.text(x, bottom - g.px(18) * 1.2f, flow.brokenProfiles.size() + " arquivo(s) de personagem ilegível(is) ficaram de fora",
                    g.px(18), false, UiKit.CORAL);
        float by = y0 + ph - 22 * s - bh;
        g.groupPx = Math.max(side, stack);
        g.button("voltar", x, by, hw, bh, "Voltar", false);
        if (!l.isEmpty()) g.button("escolher-personagem", x + hw + gap, by, hw, bh, "Escolher", true);
        else g.disabledButton("escolher-personagem", x + hw + gap, by, hw, bh, "Escolher");
        by -= bh + gap;
        float ex = stacked ? x : x + hw + gap, ey = by, ew = stacked ? iw : hw;
        if (stacked) by -= bh + gap;
        g.button("criar-personagem", x, by, stacked ? iw : hw, bh, "Novo personagem", l.isEmpty());
        if (!l.isEmpty()) g.button("editar-personagem", ex, ey, ew, bh, "Editar aparência", false);
        else g.disabledButton("editar-personagem", ex, ey, ew, bh, "Editar aparência");
        g.groupPx = 0;
        // à direita: o modelo de verdade, no cenário (arrastar gira)
        CharacterStore.Profile p = flow.profile();
        if (p != null) {
            int pn = g.px(30);
            float cx = x0 + pw + (w - x0 - pw) / 2f, ny = h - 28 * s - 110 * s;
            float nw = k.width(p.name(), pn, true) + 60 * s;
            k.chamfer(cx - nw / 2, ny, nw, 58 * s, 5 * s, UiKit.GOLD_D);
            k.chamfer(cx - nw / 2 + 3 * s, ny + 3 * s, nw - 6 * s, 52 * s, 4 * s, UiKit.GOLD);
            k.text(cx - k.width(p.name(), pn, true) / 2, ny + 29 * s - pn * 0.66f, p.name(), pn, true, UiKit.INK);
        }
    }

    private void drawWorlds(int w, int h) {
        float s = s();
        UiKit k = kit();
        CharacterStore.Profile prof = flow.profile();
        header("Mundos", prof != null ? "Aventura de " + prof.name() + " — escolha onde" : "Escolha o mundo");
        float pw = Math.min(660 * s, w * 0.45f), x0 = 28 * s, y0 = 132 * s, ph = h - y0 - 28 * s;
        k.panel(x0, y0, pw, ph, s, false);
        float x = x0 + 22 * s, iw = pw - 44 * s, y = y0 + 22 * s;
        float bh = 58 * s, gap = 10 * s, bottom = y0 + ph - 22 * s - bh - 14 * s;
        float ch = 112 * s, cg = 10 * s, tw = (ch - 20 * s) * 16f / 9f;
        List<WorldStore.World> l = flow.worldList;
        int rows = Math.max(1, (int) ((bottom - y) / (ch + cg)));
        int f = first(flow.worldSel, rows, l.size());
        if (f > 0) k.text(x + iw - k.width("↑ " + f, g.px(18), false), y0 + 2 * s, "↑ " + f, g.px(18), false, UiKit.PARCH_L);
        boolean[] fbo = new boolean[1];
        for (int i = f; i < Math.min(l.size(), f + rows); i++) {
            WorldStore.World wd = l.get(i);
            boolean sel = i == flow.worldSel, hv = ("mundo:" + i).equals(g.hover);
            g.buttons.add(new Game.Button("mundo:" + i, x, y, iw, ch, wd.name(), false));
            k.chamfer(x, y, iw, ch, 4 * s, sel ? UiKit.GOLD : hv ? UiKit.WOOD_L : UiKit.WOOD);
            k.chamfer(x + 3 * s, y + 3 * s, iw - 6 * s, ch - 6 * s, 3 * s, sel ? UiKit.PARCH_L : UiKit.PARCH_D);
            float q = ch - 20 * s;
            k.rect(x + 10 * s, y + 10 * s, tw, q, UiKit.WOOD_D);
            Integer tex = preview(wd, fbo);
            if (tex != null) image(tex, x + 12 * s, y + 12 * s, tw - 4 * s, q - 4 * s, fbo[0], 0xffffffff);
            else if (!previewFailed.contains(wd.id())) waiting(x + 10 * s, y + 10 * s, tw, q);
            float tx = x + tw + 26 * s, room = iw - tw - 40 * s;
            int pn = g.textPx(wd.name(), room, 24);
            k.text(tx, y + 14 * s, g.fit(wd.name(), pn, room), pn, true, UiKit.INK);
            String info = wd.legacy() ? "Feito à mão" : "Semente " + wd.seedText();
            k.text(tx, y + 50 * s, g.fit(info, g.px(18), room), g.px(18), false, UiKit.INK_SOFT);
            String pr = progressOf(wd, prof);
            String line3 = pr != null && !pr.isEmpty() ? pr : wd.usedAt() > 0 ? "última visita em " + date(wd.usedAt())
                    : wd.legacy() ? "ainda não visitado" : "criado em " + date(wd.createdAt());
            k.text(tx, y + 76 * s, g.fit(line3, g.px(17), room), g.px(17), false, pr != null && !pr.isEmpty() ? UiKit.MOSS_D : UiKit.INK_SOFT);
            if (sel) k.seed(x + iw - 26 * s, y + ch - 26 * s, s);
            y += ch + cg;
        }
        int below = l.size() - (f + rows);
        if (below > 0) k.text(x, y, "+" + below, g.px(18), false, UiKit.INK_SOFT);
        if (!flow.brokenWorlds.isEmpty())
            k.text(x, bottom - g.px(18) * 1.2f, flow.brokenWorlds.size() + " mundo(s) ilegível(is) ficaram de fora", g.px(18), false, UiKit.CORAL);
        float hw = (iw - gap) / 2, by = y0 + ph - 22 * s - bh;
        g.groupPx = g.groupPxFrom(MENU_BUTTON, "Voltar", hw, "Criar mundo", hw);
        g.button("voltar", x, by, hw, bh, "Voltar", false);
        g.button("criar-mundo", x + hw + gap, by, hw, bh, "Criar mundo", false);
        g.groupPx = 0;
        drawWorldDetail(w, h, x0 + pw + 24 * s, y0, w - 28 * s - (x0 + pw + 24 * s), ph);
    }

    /** Painel do mundo escolhido: prévia grande, dados, progresso do personagem e as ações. */
    private void drawWorldDetail(int w, int h, float x0, float y0, float pw, float ph) {
        float s = s();
        UiKit k = kit();
        WorldStore.World wd = flow.world();
        CharacterStore.Profile prof = flow.profile();
        if (wd == null || pw < 300 * s) return;
        k.panel(x0, y0, pw, ph, s, true);
        float x = x0 + 26 * s, iw = pw - 52 * s, y = y0 + 26 * s;
        float bh = 62 * s, gap = 10 * s;
        float actions = bh + gap;
        float imgH = Math.min(iw * 9f / 16f, ph - 52 * s - actions - 200 * s), imgW = imgH * 16f / 9f;
        if (imgH > 60 * s) {
            float ix = x + (iw - imgW) / 2;
            k.rect(ix - 4 * s, y - 4 * s, imgW + 8 * s, imgH + 8 * s, UiKit.WOOD_D);
            boolean[] fbo = new boolean[1];
            Integer tex = preview(wd, fbo);
            if (tex != null) image(tex, ix, y, imgW, imgH, fbo[0], 0xffffffff);
            else {
                k.rect(ix, y, imgW, imgH, 0x3a2a20ff);
                if (!previewFailed.contains(wd.id())) waiting(ix, y, imgW, imgH);
            }
            y += imgH + 22 * s;
        }
        int pn = g.textPx(wd.name(), iw, 32);
        k.text(x, y, g.fit(wd.name(), pn, iw), pn, true, UiKit.INK);
        y += pn * 1.4f;
        int pt = g.px(19);
        if (wd.summary() != null) {                                       // o que a região É (fatos da geração; a vila: o lugar)
            for (String line : g.wrap(wd.summary(), pt, iw)) { k.text(x, y, line, pt, false, UiKit.INK); y += pt * 1.35f; }
            y += 6 * s;
        }
        String l1 = (wd.legacy() ? "Feito à mão" : "Semente " + wd.seedText() + " · criado em " + date(wd.createdAt()))
                + (wd.usedAt() > 0 ? " · última visita em " + date(wd.usedAt()) : "");
        for (String line : g.wrap(l1, pt, iw)) { k.text(x, y, line, pt, false, UiKit.INK_SOFT); y += pt * 1.35f; }
        boolean has = flow.hasAdventure();
        String pr = progressOf(wd, prof);
        String l2 = prof == null ? "" : has ? "Aventura de " + prof.name() + ": " + (pr == null || pr.isEmpty() ? "salva" : pr)
                : prof.name() + " ainda não tem aventura neste mundo";
        for (String line : g.wrap(l2, pt, iw)) { k.text(x, y, line, pt, false, has ? UiKit.MOSS_D : UiKit.INK_SOFT); y += pt * 1.35f; }
        float by = y0 + ph - 26 * s - bh;
        g.groupPx = g.groupPxFrom(MENU_BUTTON, "Continuar aventura", iw);
        g.button("entrar", x, by, iw, bh, has ? "Continuar aventura" : "Iniciar aventura", true);
        g.groupPx = 0;
    }

    /** Espera (retrato ou prévia sendo preparados): três sementes que acendem em sequência, sem texto. */
    private void waiting(float x, float y, float w, float h) { waiting(g, flow.time, x, y, w, h, 1f); }

    static void waiting(Game g, float time, float x, float y, float w, float h, float k) {
        float s = g.s * k;
        int lit = ((int) (time * 4)) % 3;
        for (int i = 0; i < 3; i++) {
            float cx = x + w / 2 + (i - 1) * 18 * s, cy = y + h / 2 - 4 * s;
            if (i == lit) g.kit.seed(cx - 4 * s, cy, s * 0.8f);
            else g.kit.rect(cx - 3 * s, cy + 1 * s, 6 * s, 6 * s, 0xfff3dc60);
        }
    }

    private void field(String id, float x, float y, float w, float h, String text, String placeholder, boolean focus) {
        float s = s();
        UiKit k = kit();
        g.buttons.add(new Game.Button(id, x, y, w, h, text, false));
        k.chamfer(x, y, w, h, 4 * s, focus ? UiKit.GOLD_D : UiKit.WOOD_D);
        k.chamfer(x + 3 * s, y + 3 * s, w - 6 * s, h - 6 * s, 3 * s, focus ? 0xfffaecff : UiKit.PARCH_D);
        int p = g.px(24);
        boolean empty = text.isEmpty();
        String t = empty ? placeholder : text;
        float room = w - 40 * s;
        while (!empty && k.width(t, p, true) > room && t.length() > 1) t = t.substring(1);   // mostra o fim do que se digita
        if (empty && k.width(t, g.px(19), false) > room) t = g.fit(t, g.px(19), room);
        if (empty) k.text(x + 18 * s, y + h / 2 - g.px(19) * 0.66f, t, g.px(19), false, UiKit.INK_SOFT);
        else k.text(x + 18 * s, y + h / 2 - p * 0.66f, t, p, true, UiKit.INK);
        if (focus && (flow.time % 1f) < 0.55f) {
            float cx = x + 18 * s + (empty ? 0 : k.width(t, p, true)) + 3 * s;
            k.rect(cx, y + 12 * s, Math.max(2, 3 * s), h - 24 * s, UiKit.INK);
        }
    }

    private void drawCreate(int w, int h) {
        float s = s();
        UiKit k = kit();
        k.rect(0, 0, w, h, 0x1a1210b0);
        float pw = Math.min(1240 * s, w - 56 * s), ph = Math.min(700 * s, h - 56 * s), x0 = (w - pw) / 2, y0 = (h - ph) / 2;
        k.panel(x0, y0, pw, ph, s, true);
        k.text(x0 + 40 * s, y0 + 30 * s, "Criar mundo", g.px(40), true, UiKit.INK);
        float lw = pw * 0.44f, x = x0 + 40 * s, y = y0 + 110 * s, iw = lw - 40 * s;
        k.text(x, y, "Nome do mundo", g.px(21), true, UiKit.INK);
        field("campo:0", x, y + 34 * s, iw, 62 * s, flow.nameField.toString(), "", flow.focus == 0);
        y += 130 * s;
        k.text(x, y, "Semente (opcional)", g.px(21), true, UiKit.INK);
        field("campo:1", x, y + 34 * s, iw, 62 * s, flow.seedField.toString(), "Aleatória", flow.focus == 1);
        y += 108 * s;
        float bwC = 300 * s;
        g.groupPx = g.groupPxFrom(MENU_BUTTON, "Gerar outra seed", iw, "Ver prévia", bwC, "Cancelar", bwC, "Criar mundo", bwC);
        g.button("gerar-semente", x, y, iw, 54 * s, "Gerar outra seed", false);
        y += 72 * s;
        if (flow.error != null) {
            y += 10 * s;
            for (String line : g.wrap(flow.error, g.px(19), iw)) { k.text(x, y, line, g.px(19), false, UiKit.CORAL); y += g.px(19) * 1.35f; }
        }
        // prévia (da semente digitada; mudou a semente = a prévia some até ser pedida de novo)
        float rx = x0 + lw + 20 * s, rw = pw - lw - 60 * s, rh = Math.min(rw * 9f / 16f, ph - 260 * s), ry = y0 + 110 * s;
        float iwp = rh * 16f / 9f, ix = rx + (rw - iwp) / 2;
        k.rect(ix - 4 * s, ry - 4 * s, iwp + 8 * s, rh + 8 * s, UiKit.WOOD_D);
        Long typed = null;
        try { if (!flow.seedField.isEmpty()) typed = Long.parseUnsignedLong(flow.seedField.toString()); } catch (NumberFormatException ignored) { }
        boolean shown = formPic != null && formSeed != null && formSeed.equals(typed) && formSeed.equals(flow.previewSeed);
        if (shown) image(formPic.color[0], ix, ry, iwp, rh, true, 0xffffffff);
        else k.rect(ix, ry, iwp, rh, 0x3a2a20ff);
        g.button("ver-previa", ix, ry + rh + 20 * s, iwp, 56 * s, "Ver prévia", false);
        float bw = 300 * s, bh = 64 * s, by = y0 + ph - 34 * s - bh;
        g.button("confirmar-mundo", x0 + pw - 40 * s - bw, by, bw, bh, "Criar mundo", true);
        g.button("cancelar-mundo", x0 + pw - 40 * s - 2 * bw - 16 * s, by, bw, bh, "Cancelar", false);
        g.groupPx = 0;
    }

    private void drawSettings(int w, int h) {
        float s = s();
        UiKit k = kit();
        if (flow.settingsFrom == MenuFlow.Screen.MAIN) k.rect(0, 0, w, h, 0x1a121090);
        float pw = 680 * s, ph = Math.min(820 * s, h - 40 * s), x0 = (w - pw) / 2, y0 = (h - ph) / 2;
        k.panel(x0, y0, pw, ph, s, true);
        k.text(x0 + 40 * s, y0 + 30 * s, "Configurações", g.px(40), true, UiKit.INK);
        float bx = x0 + 40 * s, bw = pw - 80 * s, y = y0 + 110 * s;
        k.text(bx, y, "Som", g.px(22), true, UiKit.GOLD_D);
        y += 40 * s;
        g.stepperRow("cfg:geral", "Volume geral", AudioPrefs.percent(g.audioPrefs.master), bx, bw, y);
        g.stepperRow("cfg:musica", "Música", AudioPrefs.percent(g.audioPrefs.music), bx, bw, y + 62 * s);
        g.stepperRow("cfg:efeitos", "Efeitos", AudioPrefs.percent(g.audioPrefs.effects), bx, bw, y + 124 * s);
        y += 206 * s;
        k.text(bx, y, "Controles", g.px(22), true, UiKit.GOLD_D);
        y += 40 * s;
        Prefs pr = g.logic.prefs;
        g.stepperRow("cfg:sens", "Sensibilidade", String.format(java.util.Locale.ROOT, "%.1f", pr.sensitivity / 0.0032f), bx, bw, y);
        g.groupPx = g.groupPxFrom(MENU_BUTTON, "Inverter vertical: não", bw, "Barras: com dano ou alvo", bw, "Voltar", bw);
        g.button("cfg:inverter", bx, y + 64 * s, bw, 50 * s, pr.invertY ? "Inverter vertical: sim" : "Inverter vertical: não", false);
        String bars = switch (pr.bars) { case "nunca" -> "nunca"; case "sempre" -> "sempre"; default -> "com dano ou alvo"; };
        g.button("cfg:barras", bx, y + 126 * s, bw, 50 * s, "Barras: " + bars, false);
        g.button("voltar", bx, y0 + ph - 34 * s - 62 * s, bw, 62 * s, "Voltar", true);
        g.groupPx = 0;
    }
}
