package vx.viewer;

import vx.engine.Window.InputEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 07.2 — entrada e ações do criador (sem OpenGL; testável com eventos sintéticos). Consome a MESMA
 * fila de eventos que o GLFW preenche ({@link vx.engine.Window#events()}).
 *
 * <p>Mouse: clique = apertar e soltar sobre o mesmo controle; arrastar na área do personagem gira a câmera
 * (captura até soltar, mesmo saindo da área); roda sobre o painel rola as opções, sobre o personagem dá zoom.
 * Teclado (alternativa): Tab = próxima categoria, ←/→ = opção principal, ↑/↓ = cor, Enter = confirmar,
 * Esc = fechar, F2 = salvar, F3 = carregar, Backspace = restaurar, R = recentralizar.
 */
public final class CreatorController {
    public final CreatorState st;
    private final ComponentCatalog cat;
    private final Consumer<Appearance> compose;
    public final CreatorCamera cam;
    public Path saveDir = Path.of("output/aparencias");
    /** Rodada 09 — som da interface: recebe eventos semânticos ("ui.selecionar"…); o Creator liga ao áudio. */
    public java.util.function.Consumer<String> sound = e -> { };
    public CreatorLayout layout;
    public String hover, pressed;
    public boolean dragging, panning;
    private float lastX, lastY;
    public float mouseX = -1, mouseY = -1, time;

    public CreatorController(CreatorState st, ComponentCatalog cat, CreatorCamera cam, Consumer<Appearance> compose) {
        this.st = st;
        this.cat = cat;
        this.cam = cam;
        this.compose = compose;
        refreshSaves();
        compose.accept(st.appearance);
    }

    public void handle(List<InputEvent> events) {
        for (InputEvent e : events) handle(e);
    }

    public void handle(InputEvent e) {
        if (layout == null) return;
        switch (e.kind()) {
            case MOVE -> {
                if (dragging) cam.drag(e.x() - lastX, e.y() - lastY, layout.s);
                if (panning) cam.pan(e.y() - lastY, layout.s);
                mouseX = e.x();
                mouseY = e.y();
                lastX = e.x();
                lastY = e.y();
                CreatorLayout.Widget w = layout.hit(e.x(), e.y(), st);
                hover = w == null ? null : w.id;
            }
            case DOWN -> {
                mouseX = lastX = e.x();
                mouseY = lastY = e.y();
                CreatorLayout.Widget w = layout.hit(e.x(), e.y(), st);
                if (e.button() == 1) {                                     // direito: sobe/desce o enquadramento
                    panning = w == null && st.dialog == CreatorState.Dialog.NONE && layout.scene != null
                            && layout.scene.contains(e.x(), e.y()) && !overPanel(e.x(), e.y());
                    return;
                }
                if (e.button() != 0) return;
                if (w != null) {
                    pressed = w.id;
                } else if (st.dialog == CreatorState.Dialog.NONE && layout.scene != null && layout.scene.contains(e.x(), e.y())
                        && !overPanel(e.x(), e.y())) {
                    dragging = true;
                    if (st.editingName) st.editingName = false;
                } else if (st.editingName) {
                    st.editingName = false;
                }
            }
            case UP -> {
                if (e.button() == 1) { panning = false; return; }
                if (e.button() != 0) return;
                CreatorLayout.Widget w = layout.hit(e.x(), e.y(), st);
                if (pressed != null && w != null && pressed.equals(w.id)) click(w);
                pressed = null;
                dragging = false;
            }
            case SCROLL -> {
                if (st.dialog == CreatorState.Dialog.LOAD) {
                    st.loadScroll = Math.clamp(st.loadScroll - e.scroll() * 60 * layout.s, 0, Math.max(0, st.saves.size() * 72 * layout.s - 380 * layout.s));
                } else if (layout.options != null && layout.options.contains(e.x(), e.y())) {
                    float max = Math.max(0, layout.contentHeight - layout.options.h());
                    st.scroll = Math.clamp(st.scroll - e.scroll() * 60 * layout.s, 0, max);
                } else if (layout.scene != null && layout.scene.contains(e.x(), e.y())) {
                    cam.zoom(e.scroll());
                }
            }
            case CHAR -> {
                if (st.editingName && st.name.length() < 24 && Character.isLetterOrDigit(e.code()) || (st.editingName && e.code() == ' '))
                    st.name += new String(Character.toChars(e.code()));
            }
            case KEY -> key(e.code());
        }
    }

    private boolean overPanel(float x, float y) {
        for (CreatorLayout.Widget w : layout.widgets)
            if (w.kind == CreatorLayout.Kind.PANEL && w.r.contains(x, y)) return true;
        return false;
    }

    private void key(int k) {
        if (st.editingName) {
            if (k == GLFW_KEY_BACKSPACE && !st.name.isEmpty()) st.name = st.name.substring(0, st.name.length() - 1);
            if (k == GLFW_KEY_ENTER || k == GLFW_KEY_ESCAPE || k == GLFW_KEY_TAB) st.editingName = false;
            return;
        }
        if (st.dialog != CreatorState.Dialog.NONE) {
            if (k == GLFW_KEY_ESCAPE) { st.dialog = CreatorState.Dialog.NONE; sound.accept("ui.cancelar"); }
            if (k == GLFW_KEY_ENTER) {
                if (st.dialog == CreatorState.Dialog.CONFIRM_RESTORE) action("restore-confirm");
                else if (st.dialog == CreatorState.Dialog.DONE) action(st.finishLabel != null ? "finish" : "showcase");
                else { st.dialog = CreatorState.Dialog.NONE; sound.accept("ui.cancelar"); }
            }
            return;
        }
        if (st.showcase) { if (k == GLFW_KEY_ESCAPE || k == GLFW_KEY_ENTER) action("showcase-exit"); return; }
        switch (k) {
            case GLFW_KEY_TAB -> {
                CreatorState.Category[] c = CreatorState.Category.values();
                action("cat:" + c[(st.category.ordinal() + 1) % c.length].name());
            }
            case GLFW_KEY_RIGHT -> cycle(1, true);
            case GLFW_KEY_LEFT -> cycle(-1, true);
            case GLFW_KEY_DOWN -> cycle(1, false);
            case GLFW_KEY_UP -> cycle(-1, false);
            case GLFW_KEY_F2 -> action("save");
            case GLFW_KEY_F3 -> action("load");
            case GLFW_KEY_BACKSPACE -> action("restore");
            case GLFW_KEY_R -> action("cam:reset");
            case GLFW_KEY_ENTER -> action("done");
            default -> { }
        }
    }

    /** Teclado: anda pelos controles da categoria (principal = miniaturas; secundário = amostras). */
    private void cycle(int d, boolean primary) {
        List<CreatorLayout.Widget> opts = layout.widgets.stream()
                .filter(w -> primary ? w.kind == CreatorLayout.Kind.THUMB : w.kind == CreatorLayout.Kind.SWATCH)
                .filter(w -> w.enabled).toList();
        if (opts.isEmpty()) return;
        int i = 0;
        for (int j = 0; j < opts.size(); j++) if (opts.get(j).selected) i = j;
        action(opts.get(Math.floorMod(i + d, opts.size())).id);
    }

    private void click(CreatorLayout.Widget w) {
        if (!w.enabled) {
            message(w.reason == null ? "Indisponível" : w.reason, "erro");
            sound.accept("ui.cancelar");
            return;
        }
        action(w.id);
    }

    /** Executa uma ação pelo id do controle (o mesmo que o clique dispara). */
    public void action(String id) {
        String v = id.contains(":") ? id.substring(id.indexOf(':') + 1) : null;
        String op = v == null ? id : id.substring(0, id.indexOf(':'));
        Appearance a = st.appearance.copy();
        switch (op) {
            case "cat" -> {
                sound.accept("ui.selecionar");
                st.category = CreatorState.Category.valueOf(v);
                st.scroll = 0;
                cam.focusFace(st.category == CreatorState.Category.CABELO || st.category == CreatorState.Category.ROSTO
                        || st.category == CreatorState.Category.CHAPEU);
                return;
            }
            case "preset" -> { a.preset = v; a.reset(); st.name = CreatorState.name(v); }
            case "hair" -> a.hair = v;
            case "hairColor" -> a.hairColor = hex("cabelo", v);
            case "skin" -> a.skin = hex("pele", v);
            case "iris" -> a.iris = hex("iris", v);
            case "face" -> a.face = v;
            case "eyes" -> a.eyes = v;                                   // rodada 09
            case "feet" -> a.feet = v;                                   // rodada 09
            case "cover" -> a.cover = v;
            case "expr" -> { st.expr = v.equals("neutral") ? null : v; sound.accept("ui.selecionar"); return; }
            case "pose" -> { st.pose = Animator.Clip.valueOf(v); sound.accept("ui.selecionar"); return; }
            case "cam" -> {
                sound.accept("ui.selecionar");
                if (v.equals("face")) cam.focusFace(true);
                else if (v.equals("body")) cam.focusFace(false);
                else cam.reset();
                return;
            }
            case "name" -> { st.editingName = true; sound.accept("ui.selecionar"); return; }
            case "restore" -> {
                if (st.isOriginal()) { message("Já é o modelo original", "info"); sound.accept("ui.cancelar"); return; }
                st.dialog = CreatorState.Dialog.CONFIRM_RESTORE;
                sound.accept("ui.abrir");
                return;
            }
            case "restore-confirm" -> {
                sound.accept("ui.confirmar");
                st.dialog = CreatorState.Dialog.NONE;
                a.reset();
                st.appearance = a;
                compose.accept(a);
                message("Modelo original restaurado: " + CreatorState.name(a.preset), "info");
                return;
            }
            case "save" -> { save(); return; }
            case "load" -> {
                refreshSaves();
                if (st.saves.isEmpty()) { message("Nenhuma aparência salva ainda", "erro"); sound.accept("ui.cancelar"); return; }
                st.loadScroll = 0;
                st.dialog = CreatorState.Dialog.LOAD;
                sound.accept("ui.abrir");
                return;
            }
            case "loadfile" -> { sound.accept("ui.confirmar"); load(st.saves.get(Integer.parseInt(v))); return; }
            case "done" -> { st.dialog = CreatorState.Dialog.DONE; sound.accept("ui.abrir"); return; }
            case "showcase" -> { st.dialog = CreatorState.Dialog.NONE; st.showcase = true; st.pose = Animator.Clip.WALK; cam.focusFace(false); sound.accept("ui.confirmar"); return; }
            case "finish" -> { st.dialog = CreatorState.Dialog.NONE; st.finished = true; sound.accept("ui.confirmar"); return; }
            case "showcase-exit" -> { st.showcase = false; st.pose = Animator.Clip.IDLE; sound.accept("ui.cancelar"); return; }
            case "dialog-cancel" -> { st.dialog = CreatorState.Dialog.NONE; sound.accept("ui.cancelar"); return; }
            default -> { return; }
        }
        try {
            a.validate(cat);
        } catch (Appearance.InvalidAppearance ex) {
            message(CreatorLayout.reasonFor(ex.getMessage()), "erro");            // nunca aplica combinação inválida
            sound.accept("ui.cancelar");
            return;
        }
        st.appearance = a;
        compose.accept(a);
        sound.accept("ui.selecionar");
    }

    /** Cor da paleta curada pelo nome ("castanho" → #rrggbb). */
    private String hex(String channel, String name) {
        for (String[] c : cat.colors.get(channel)) if (c[0].equals(name)) return c[1];
        throw new IllegalArgumentException("cor desconhecida em " + channel + ": " + name);
    }

    public void message(String m, String kind) {
        st.message = m;
        st.messageKind = kind;
        st.messageUntil = time + 4.5f;
    }

    // ------------------------------------------------------------------ salvar / carregar (formato valumo.aparencia v1)

    static String slug(String name) {
        String n = Normalizer.normalize(name.isBlank() ? "aventureiro" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return n.isEmpty() ? "aventureiro" : n;
    }

    void save() {
        try {
            Appearance a = st.appearance.copy();
            a.name = st.name.isBlank() ? null : st.name.trim();
            int n = 1;
            Path f;
            do f = saveDir.resolve(String.format("%s-%03d.json", slug(st.name), n++)); while (Files.exists(f));
            a.save(f, false);                                          // nunca sobrescreve
            refreshSaves();
            message("Aparência salva: " + f.getFileName(), "info");
            sound.accept("ui.salvar");                                    // só quando o arquivo foi gravado
        } catch (IOException e) {
            message("Não consegui salvar: " + e.getMessage(), "erro");
            sound.accept("ui.cancelar");
        }
    }

    void load(Path f) {
        st.dialog = CreatorState.Dialog.NONE;
        try {
            Appearance a = Appearance.load(f, cat);
            st.appearance = a;
            st.name = a.name != null ? a.name : CreatorState.name(a.preset);
            compose.accept(a);
            message("Aparência carregada: " + f.getFileName(), "info");
        } catch (IOException e) {
            message("Não consegui ler " + f.getFileName(), "erro");
        } catch (Appearance.InvalidAppearance e) {
            message("Arquivo " + f.getFileName() + " não é válido: " + e.getMessage(), "erro");
        }
    }

    void refreshSaves() {
        try (var fs = Files.exists(saveDir) ? Files.list(saveDir) : java.util.stream.Stream.<Path>empty()) {
            st.saves = fs.filter(p -> p.toString().endsWith(".json"))
                    .sorted(java.util.Comparator.comparing((Path p) -> p.toFile().lastModified()).reversed().thenComparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            st.saves = List.of();
        }
    }
}
