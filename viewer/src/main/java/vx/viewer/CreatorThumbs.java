package vx.viewer;

import vx.engine.Framebuffer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.GL_RGBA8;

/**
 * Rodada 07.2 — miniaturas REAIS do criador: cada uma é o modelo composto da aparência candidata (a atual com
 * só aquela escolha trocada), renderizado pelo mesmo renderizador da cena num FBO pequeno. Nada de imagem
 * pré-desenhada: se o asset muda, a miniatura muda. Composição na thread de trabalho ({@link AsyncComposer}),
 * envio + render na thread do GL, modelo liberado logo depois (só a textura fica).
 */
public final class CreatorThumbs implements AutoCloseable {
    public static final int SIZE = 320;

    private static final class Slot {
        Framebuffer fbo;
        String sig;            // assinatura+enquadramento já renderizados
    }

    private final App app;
    private final AsyncComposer async;
    private final Map<String, Slot> slots = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    /** Prontas para render (thread do GL): no máximo uma por quadro, para não somar picos. */
    private final java.util.ArrayDeque<Runnable> toRender = new java.util.ArrayDeque<>();
    public int rendered;

    public CreatorThumbs(App app, AsyncComposer async) {
        this.app = app;
        this.async = async;
    }

    /** Aparência candidata e enquadramento (cabeça/corpo) de uma miniatura do layout. */
    static Appearance candidate(String id, Appearance cur) {
        String v = id.substring(id.indexOf(':') + 1);
        Appearance a = cur.copy();
        switch (id.substring(0, id.indexOf(':'))) {
            case "preset" -> { a = Appearance.preset(v); }
            case "hair" -> a.hair = v;
            case "face" -> a.face = v;
            case "cover" -> a.cover = v;
            default -> { return null; }
        }
        a.name = null;
        return a;
    }

    static boolean head(String id) { return !id.startsWith("preset:"); }

    /** Coberturas em 3/4 lateral e com folga em cima: o laço e a pena do C3 ficam atrás da cabeça, a copa do
     *  chapéu sobe além dela. */
    static String framing(String id) {
        return id.startsWith("preset:") ? "corpo" : id.startsWith("cover:") ? "cobertura" : id.startsWith("hair:") ? "cabelo" : "cabeca";
    }

    /** Pede as miniaturas que faltam para o layout atual (as já pedidas não se repetem). */
    public void update(CreatorLayout l, CreatorState st) {
        for (CreatorLayout.Widget w : l.widgets) {
            if (w.kind != CreatorLayout.Kind.THUMB) continue;
            Appearance a = candidate(w.id, st.appearance);
            if (a == null) continue;
            String key = a.signature() + "#" + framing(w.id);
            Slot s = slots.computeIfAbsent(w.id, k -> new Slot());
            if (key.equals(s.sig) || pending.contains(w.id + "=" + key)) continue;
            pending.add(w.id + "=" + key);
            final String id = w.id;
            async.requestThumb(a, p -> toRender.add(() -> {
                pending.remove(id + "=" + key);
                CharacterModel m = async.upload(p);
                try {
                    if (s.fbo == null) s.fbo = new Framebuffer(SIZE, SIZE, false, GL_RGBA8);
                    app.renderThumb(m, s.fbo, framing(id));
                    s.sig = key;
                    rendered++;
                } finally {
                    Composer.release(m);
                }
            }));
        }
    }

    /** Thread do GL: renderiza até {@code max} miniaturas prontas neste quadro. */
    public void pump(int max) {
        for (int i = 0; i < max && !toRender.isEmpty(); i++) toRender.poll().run();
    }

    /** Textura da miniatura (só depois que a versão certa existir; a anterior continua até lá). */
    public Integer texture(String id) {
        Slot s = slots.get(id);
        return s == null || s.fbo == null ? null : s.fbo.color[0];
    }

    /** A miniatura exibida corresponde à aparência candidata ATUAL? (teste/QA: nada desatualizado). */
    public boolean current(String id, Appearance cur) {
        Slot s = slots.get(id);
        Appearance a = candidate(id, cur);
        return s != null && a != null && (a.signature() + "#" + framing(id)).equals(s.sig);
    }

    public boolean idle() { return pending.isEmpty() && toRender.isEmpty(); }

    @Override
    public void close() {
        for (Slot s : slots.values()) if (s.fbo != null) s.fbo.close();
        slots.clear();
    }
}
