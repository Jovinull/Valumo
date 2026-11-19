package vx.engine;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/** Textura 2D RGBA8. Pixel art usa {@code nearest=true} (sem mipmaps, sem suavização). */
public final class Texture implements AutoCloseable {
    public final int id, width, height;
    /** Texturas vivas no processo (QA de recursos: sessão longa, vazamento do criador). Thread do GL. */
    public static int live;
    private boolean closed;

    public Texture(int width, int height, ByteBuffer rgba, boolean nearest) {
        this.width = width;
        this.height = height;
        id = glGenTextures();
        live++;
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        int f = nearest ? GL_NEAREST : GL_LINEAR;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, f);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, f);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    /** Carrega PNG; a linha 0 da imagem (topo) fica em v=0. */
    public static Texture load(Path png, boolean nearest) {
        try {
            BufferedImage img = ImageIO.read(png.toFile());
            if (img == null) throw new IOException("formato não suportado");
            int w = img.getWidth(), h = img.getHeight();
            ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int p = img.getRGB(x, y);
                    buf.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
                }
            buf.flip();
            return new Texture(w, h, buf, nearest);
        } catch (IOException e) {
            throw new IllegalStateException("falha ao ler " + png + ": " + e.getMessage(), e);
        }
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public static void bind(int unit, int textureId) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    @Override
    public void close() {
        if (closed) return;                                    // fechar duas vezes não apaga o id de outra textura
        closed = true;
        live--;
        glDeleteTextures(id);
    }
}
