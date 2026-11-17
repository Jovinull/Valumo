package vx.engine;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Rodada 07.2 — textura da {@link PixelFont} (fonte própria) e desenho de texto como quads do {@link UiBatch}.
 * Um atlas por peso (regular/negrito) em escala 1, amostrado com NEAREST; o tamanho na tela é sempre um
 * múltiplo inteiro do pixel da fonte, então o traço fica nítido em qualquer resolução.
 */
public final class FontAtlas implements AutoCloseable {
    private static final int PAD = 1, ROW_W = 512;
    public final boolean bold;
    private final Texture tex;
    private final Map<Character, int[]> cells = new HashMap<>();              // {x, y, largura}
    private final int atlasW, atlasH;

    public FontAtlas(boolean bold) {
        this.bold = bold;
        String cs = PixelFont.charset();
        int x = PAD, y = PAD, cellH = PixelFont.ROWS;
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            int w = PixelFont.glyph(c, bold).width();
            if (x + w + PAD > ROW_W) { x = PAD; y += cellH + 2 * PAD; }
            cells.put(c, new int[]{x, y, w});
            x += w + 2 * PAD;
        }
        atlasW = ROW_W;
        int h = 1;
        while (h < y + cellH + PAD) h <<= 1;
        atlasH = h;
        byte[] a = new byte[atlasW * atlasH];
        for (var e : cells.entrySet()) {
            PixelFont.Glyph g = PixelFont.glyph(e.getKey(), bold);
            int[] c = e.getValue();
            for (int yy = 0; yy < PixelFont.ROWS; yy++)
                for (int xx = 0; xx < g.width(); xx++) if (g.on(xx, yy)) a[(c[1] + yy) * atlasW + c[0] + xx] = (byte) 255;
        }
        ByteBuffer buf = BufferUtils.createByteBuffer(atlasW * atlasH * 4);
        for (byte v : a) buf.put((byte) 255).put((byte) 255).put((byte) 255).put(v);
        buf.flip();
        tex = new Texture(atlasW, atlasH, buf, true);
    }

    public float width(String s, int px) { return PixelFont.width(s, px, bold); }

    /**
     * Desenha a linha no tamanho nominal px; y é o topo da caixa de texto (o centro da maiúscula fica em
     * y + 0,62·px, como as interfaces do viewer esperam).
     */
    public void draw(UiBatch b, float x, float y, String s, int px, int rgba) {
        int k = PixelFont.scale(px);
        float top = Math.round(y + 0.62f * px - PixelFont.CAP_CENTER * k);
        float cx = Math.round(x);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int[] c = cells.get(ch);
            if (c == null) { ch = '?'; c = cells.get('?'); }
            if (ch != ' ')
                b.quad(tex.id, cx, top, c[2] * k, PixelFont.ROWS * k, (float) c[0] / atlasW, (float) c[1] / atlasH,
                        (float) (c[0] + c[2]) / atlasW, (float) (c[1] + PixelFont.ROWS) / atlasH, rgba);
            cx += PixelFont.advance(ch, bold) * k;
        }
    }

    @Override
    public void close() { tex.close(); }
}
