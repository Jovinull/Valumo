package vx.viewer;

import vx.engine.FontAtlas;
import vx.engine.UiBatch;

import static org.lwjgl.opengl.GL33.*;

/**
 * Rodada 08 — peças de interface no estilo Valumo, compartilhadas pelo criador de personagens e pelo jogo:
 * molduras de madeira com cantos em degrau, pergaminho, texto na fonte de pixel própria, semente P1, marca de
 * seleção, ícones de pixel e cursor. Extraído do {@link CreatorView} sem mudar o desenho.
 */
public final class UiKit implements AutoCloseable {
    // paleta da interface (família da direção E: madeira da casa, pergaminho do posto, latão, P1)
    public static final int PARCH = 0xf3e6c8f0, PARCH_D = 0xe6d3abff, PARCH_L = 0xfbf3dfff, INK = 0x3a2a26ff, INK_SOFT = 0x6b5444ff,
            WOOD = 0x6b4630ff, WOOD_D = 0x45291cff, WOOD_L = 0x8f6040ff, GOLD = 0xecb84cff, GOLD_D = 0xb8862eff,
            MOSS = 0x5f8a5aff, MOSS_D = 0x45683fff, P1 = 0x4fd6aeff, CORAL = 0xc8643cff, SHADOW = 0x00000055;

    public final UiBatch ui = new UiBatch();
    private final FontAtlas bold = new FontAtlas(true), regular = new FontAtlas(false);

    public void begin(int w, int h) { ui.begin(w, h); }

    public void end() { ui.end(); }

    public void rect(float x, float y, float w, float h, int rgba) { ui.rect(x, y, w, h, rgba); }

    /** Painel de madeira com cantos chanfrados (degraus de voxel) e miolo de pergaminho. */
    public void panel(float x, float y, float w, float h, float s, boolean strong) {
        float b = Math.max(3, 5 * s), c = Math.max(3, 6 * s);
        ui.rect(x + 4 * s, y + 6 * s, w, h, SHADOW);
        chamfer(x, y, w, h, c, WOOD_D);
        chamfer(x + b * 0.5f, y + b * 0.5f, w - b, h - b, c, WOOD);
        chamfer(x + b, y + b, w - 2 * b, h - 2 * b, c * 0.7f, strong ? PARCH_L : PARCH);
        ui.rect(x + b + c, y + b, w - 2 * (b + c), Math.max(1, 2 * s), 0xffffff40);
    }

    public void chamfer(float x, float y, float w, float h, float c, int col) {
        ui.rect(x + c, y, w - 2 * c, h, col);
        ui.rect(x, y + c, c, h - 2 * c, col);
        ui.rect(x + w - c, y + c, c, h - 2 * c, col);
        float c2 = c * 0.5f;
        ui.rect(x + c2, y + c2, c2, h - c, col);
        ui.rect(x + w - c, y + c2, c2, h - c, col);
    }

    public float width(String t, int px, boolean b) { return (b ? bold : regular).width(t, px); }

    public void text(float x, float y, String t, int px, boolean b, int col) { (b ? bold : regular).draw(ui, x, y, t, px, col); }

    /** P1: semente de luz (acento, um só ponto). */
    public void seed(float x, float y, float s) {
        ui.rect(x - 2 * s, y - 2 * s, 12 * s, 12 * s, 0x4fd6ae40);
        ui.rect(x, y, 8 * s, 8 * s, P1);
        ui.rect(x + 2 * s, y + 1 * s, 3 * s, 3 * s, 0xe6fff8ff);
    }

    public void check(float x, float y, float s) {
        ui.rect(x, y, 18 * s, 18 * s, GOLD_D);
        ui.rect(x + 2 * s, y + 2 * s, 14 * s, 14 * s, GOLD);
        String[] m = {"......X", ".....XX", "X...XX.", "XX.XX..", ".XXX...", "..X...."};
        bitmap(m, x + 3 * s, y + 4 * s, 1.8f * s, INK);
    }

    public void cursor(float x, float y, float s, boolean down) {
        String[] m = {"X.........", "XX........", "XWX.......", "XWWX......", "XWWWX.....", "XWWWWX....", "XWWWWWX...", "XWWWWWWX..",
                "XWWWWXXXX.", "XWXWWX....", "XX.XWWX...", ".....XWX..", "......XX.."};
        float k = 2.4f * s * (down ? 0.92f : 1f);
        for (int r = 0; r < m.length; r++)
            for (int q = 0; q < m[r].length(); q++) {
                char ch = m[r].charAt(q);
                if (ch != '.') ui.rect(x + q * k, y + r * k, k, k, ch == 'X' ? INK : (down ? GOLD : 0xfffaf0ff));
            }
    }

    public void bitmap(String[] m, float x, float y, float k, int col) {
        for (int r = 0; r < m.length; r++)
            for (int q = 0; q < m[r].length(); q++)
                if (m[r].charAt(q) == 'X') ui.rect(x + q * k, y + r * k, k, k, col);
    }

    /** Recorte retangular (pixels do framebuffer, origem no topo). Descarrega o lote antes. */
    public void scissor(float x, float y, float w, float h, int fbH) {
        ui.flush();
        glEnable(GL_SCISSOR_TEST);
        glScissor(Math.round(x), Math.round(fbH - y - h), Math.max(0, Math.round(w)), Math.max(0, Math.round(h)));
    }

    public void noScissor() {
        ui.flush();
        glDisable(GL_SCISSOR_TEST);
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t), g = Math.round(((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t),
                bl = Math.round(((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        return (r << 24) | (g << 16) | (bl << 8) | 0xff;
    }

    public static int mul(int a, float k) {
        return (Math.round(((a >>> 24) & 255) * k) << 24) | (Math.round(((a >>> 16) & 255) * k * 0.92f) << 16)
                | (Math.round(((a >>> 8) & 255) * k * 1.04f) << 8) | 0xff;
    }

    @Override
    public void close() {
        ui.close();
        bold.close();
        regular.close();
    }
}
