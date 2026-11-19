package vx.viewer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vx.engine.Window.InputEvent;
import vx.engine.Window.InputEvent.Kind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 07.2 — criador de personagens do jogador, sem OpenGL: layout (hitboxes, limites, resoluções),
 * entrada (clique, arrasto, roda, teclado, texto), ações (seleção, restaurar, salvar/carregar, indisponível)
 * e composição assíncrona (cache, pedidos rápidos, descarte de obsoletos). Os eventos sintéticos são os mesmos
 * registros que o GLFW põe na fila da janela.
 */
class CreatorTest {
    static ComponentCatalog CAT;
    static CreatorLayout.Measure M;

    @BeforeAll
    static void setup() {
        CAT = ComponentCatalog.load(Path.of("assets"));
        M = CreatorView.measure();                                      // a mesma métrica da fonte própria usada no desenho
    }

    /** Controlador ligado a um "compositor" que só registra os pedidos (a composição real é testada abaixo). */
    static final class Rig {
        final CreatorState st = new CreatorState();
        final CreatorCamera cam = new CreatorCamera();
        final List<Appearance> requests = new ArrayList<>();
        final CreatorController c;
        char variant = 'A';
        int w = 1920, h = 1080;

        Rig(Path saves) {
            c = new CreatorController(st, CAT, cam, a -> requests.add(a.copy()));
            c.saveDir = saves;
            c.refreshSaves();
            relayout();
        }

        void relayout() { c.layout = new CreatorLayout(variant, w, h, st, CAT, M); }

        CreatorLayout.Widget w(String id) {
            CreatorLayout.Widget x = c.layout.get(id);
            assertNotNull(x, "controle ausente: " + id);
            return x;
        }

        void click(String id) {
            CreatorLayout.Widget x = w(id);
            float px = x.r.x() + x.r.w() / 2, py = x.r.y() + x.r.h() / 2;
            c.handle(new InputEvent(Kind.MOVE, px, py, 0, 0, 0));
            c.handle(new InputEvent(Kind.DOWN, px, py, 0, 0, 0));
            c.handle(new InputEvent(Kind.UP, px, py, 0, 0, 0));
            relayout();
        }

        void key(int k) { c.handle(new InputEvent(Kind.KEY, 0, 0, 0, 0, k)); relayout(); }

        Appearance last() { return requests.getLast(); }
    }

    // ------------------------------------------------------------------ layout

    @Test
    void layoutFitsEveryResolutionAndCategory(@TempDir Path tmp) {
        int[][] sizes = {{1280, 720}, {1920, 1080}, {2560, 1080}, {3440, 1440}, {1024, 768}, {1600, 900}};
        for (char v : new char[]{'A', 'B'})
            for (int[] sz : sizes)
                for (CreatorState.Category cat : CreatorState.Category.values()) {
                    Rig r = new Rig(tmp);
                    r.variant = v; r.w = sz[0]; r.h = sz[1];
                    r.st.category = cat;
                    r.relayout();
                    String where = v + " " + sz[0] + "x" + sz[1] + " " + cat;
                    CreatorLayout l = r.c.layout;
                    CreatorLayout.Rect screen = new CreatorLayout.Rect(0, 0, sz[0], sz[1]);
                    List<CreatorLayout.Widget> fixed = new ArrayList<>();
                    for (CreatorLayout.Widget x : l.widgets) {
                        if (!x.scrolls) assertTrue(x.r.inside(screen), where + ": fora da tela " + x.id + " " + x.r);
                        if (x.interactive() && !x.scrolls) fixed.add(x);
                        if (x.scrolls) assertTrue(x.r.x() >= l.options.x() && x.r.x() + x.r.w() <= l.options.x() + l.options.w() + 0.5f,
                                where + ": opção sai do painel na horizontal " + x.id);
                        // nenhum texto sai do seu controle (rótulos de miniatura/amostra/botão)
                        if (x.label != null && x.interactive()) {
                            assertTrue(x.textPx > 0, where + ": sem tamanho de texto " + x.id);
                            assertTrue(M.width(x.label, x.textPx, CreatorLayout.boldText(x.kind)) <= l.textRoom(x),
                                    where + ": texto não cabe em " + x.id + " '" + x.label + "'");
                            // nome amigável inteiro (sem "…") nas resoluções suportadas, exceto o campo de nome livre
                            if (sz[1] >= 720 && x.kind != CreatorLayout.Kind.FIELD && x.kind != CreatorLayout.Kind.LIST_ITEM)
                                assertFalse(x.label.endsWith("…"), where + ": rótulo cortado em " + x.id + " '" + x.label + "'");
                        }
                    }
                    for (int i = 0; i < fixed.size(); i++)
                        for (int j = i + 1; j < fixed.size(); j++)
                            assertFalse(fixed.get(i).r.overlaps(fixed.get(j).r), where + ": sobreposição " + fixed.get(i).id + " × " + fixed.get(j).id);
                    // painéis não escondem o centro da área do personagem
                    float cx = l.scene.x() + l.scene.w() / 2, cy = sz[1] * 0.5f;
                    for (CreatorLayout.Widget x : l.widgets)
                        if (x.kind == CreatorLayout.Kind.PANEL) assertFalse(x.r.contains(cx, cy), where + ": painel sobre o personagem");
                    assertTrue(l.scene.w() >= sz[0] * 0.3f, where + ": área do personagem estreita demais");
                }
    }

    @Test
    void dialogsAndShowcaseAreSizedAndInsideTheScreen(@TempDir Path tmp) throws Exception {
        java.nio.file.Files.writeString(tmp.resolve("a.json"), Appearance.preset("maga").toJson());
        for (int[] sz : new int[][]{{1280, 720}, {1920, 1080}, {2560, 1080}}) {
            Rig r = new Rig(tmp);
            r.w = sz[0]; r.h = sz[1];
            for (CreatorState.Dialog d : CreatorState.Dialog.values()) {
                r.st.dialog = d;
                r.relayout();
                for (CreatorLayout.Widget x : r.c.layout.widgets) {
                    if (x.label != null && x.interactive()) assertTrue(x.textPx > 0, d + ": sem tamanho de texto " + x.id);
                    if (!x.scrolls) assertTrue(x.r.inside(new CreatorLayout.Rect(0, 0, sz[0], sz[1])), d + ": fora da tela " + x.id);
                }
            }
            r.st.dialog = CreatorState.Dialog.NONE;
            r.st.showcase = true;
            r.relayout();
            CreatorLayout.Widget exit = r.w("showcase-exit");
            assertTrue(exit.textPx >= r.c.layout.px(22) * 0.8f, "vitrine: texto do botão no tamanho normal");
            assertEquals("Voltar ao criador", exit.label);
            r.click("showcase-exit");
            assertFalse(r.st.showcase);
        }
    }

    @Test
    void scaleIsProportionalAndBounded() {
        CreatorState st = new CreatorState();
        CreatorLayout a = new CreatorLayout('A', 1280, 720, st, CAT, M), b = new CreatorLayout('A', 1920, 1080, st, CAT, M),
                c = new CreatorLayout('A', 3840, 2160, st, CAT, M);
        assertEquals(1280f / 1920f, a.s, 1e-4);
        assertEquals(1f, b.s, 1e-4);
        assertEquals(1.8f, c.s, 1e-4);                                  // 4K: maior, mas com teto
        // o mesmo controle ocupa a mesma fração da tela em 720p e 1080p
        CreatorLayout.Rect ra = a.get("save").r, rb = b.get("save").r;
        assertEquals(ra.w() / 1280, rb.w() / 1920, 1e-3);
        assertEquals(ra.x() / 1280, rb.x() / 1920, 1e-3);
    }

    @Test
    void hitboxesRouteToTheRightControl(@TempDir Path tmp) {
        Rig r = new Rig(tmp);
        for (CreatorLayout.Widget x : r.c.layout.widgets) {
            if (!x.interactive()) continue;
            CreatorLayout.Widget hit = r.c.layout.hit(x.r.x() + x.r.w() / 2, x.r.y() + x.r.h() / 2, r.st);
            assertNotNull(hit, x.id);
            assertEquals(x.id, hit.id);
            assertNull(r.c.layout.hit(x.r.x() - 0.5f, -5, r.st));
        }
        // fora de qualquer controle: nada (área do personagem)
        CreatorLayout l = r.c.layout;
        assertNull(l.hit(l.scene.x() + l.scene.w() / 2, r.h * 0.4f, r.st));
    }

    @Test
    void clicksFollowTheLayoutAfterResize(@TempDir Path tmp) {
        Rig r = new Rig(tmp);
        int[][] sizes = {{1920, 1080}, {1280, 720}, {2560, 1080}, {1000, 700}, {1920, 1080}};
        String[] presets = {"maga", "exploradora", "aventureiro", "maga", "exploradora"};
        for (int i = 0; i < sizes.length; i++) {
            r.w = sizes[i][0]; r.h = sizes[i][1];
            r.relayout();                                               // a janela mudou: layout novo, mesmas ações
            r.click("preset:" + presets[i]);
            assertEquals(presets[i], r.st.appearance.preset, sizes[i][0] + "x" + sizes[i][1]);
        }
    }

    // ------------------------------------------------------------------ ações por clique

    @Test
    void clickingSelectsAndRecomposes(@TempDir Path tmp) {
        Rig r = new Rig(tmp);
        assertEquals("aventureiro", r.last().preset);
        r.click("preset:maga");
        assertEquals("maga", r.st.appearance.preset);
        assertEquals("maga", r.last().preset);
        assertTrue(r.w("preset:maga").selected);
        assertFalse(r.w("preset:aventureiro").selected);
        assertEquals("Maga", r.st.name);

        r.click("cat:CABELO");
        assertEquals(CreatorState.Category.CABELO, r.st.category);
        assertTrue(r.w("cat:CABELO").selected);
        r.click("hair:cabelo.curto");
        assertEquals("cabelo.curto", r.last().hair);
        assertTrue(r.w("hair:cabelo.curto").selected);
        r.click("hairColor:castanho");
        assertEquals(hex("cabelo", "castanho"), r.last().hairColor);
        assertTrue(r.w("hairColor:castanho").selected);

        r.click("cat:PELE");
        r.click("skin:morena");
        assertEquals(hex("pele", "morena"), r.last().skin);

        r.click("cat:ROSTO");
        r.click("face:rosto.aventureiro");
        r.click("iris:violeta");
        assertEquals("rosto.aventureiro", r.last().face);
        assertEquals(hex("iris", "violeta"), r.last().iris);
        int before = r.requests.size();
        r.click("expr:joy");                                         // prévia: não muda a aparência nem recompõe
        assertEquals("joy", r.st.expr);
        assertEquals(before, r.requests.size());
        assertTrue(r.w("expr:joy").selected);

        r.click("cat:CHAPEU");
        r.click("cover:capuz.c3");
        assertEquals("capuz.c3", r.last().cover);
        r.click("cover:nenhum");
        assertEquals(Appearance.NO_COVER, r.last().cover);
        r.last().validate(CAT);
    }

    static String hex(String ch, String name) {
        for (String[] c : CAT.colors.get(ch)) if (c[0].equals(name)) return c[1];
        throw new AssertionError(name);
    }

    @Test
    void pressAndReleaseMustHitTheSameControl(@TempDir Path tmp) {
        Rig r = new Rig(tmp);
        CreatorLayout.Widget a = r.w("preset:maga"), b = r.w("preset:exploradora");
        int n = r.requests.size();
        r.c.handle(new InputEvent(Kind.DOWN, a.r.x() + 5, a.r.y() + 5, 0, 0, 0));
        r.c.handle(new InputEvent(Kind.UP, b.r.x() + 5, b.r.y() + 5, 0, 0, 0));   // arrastou para outro: cancela
        assertEquals(n, r.requests.size());
        assertEquals("aventureiro", r.st.appearance.preset);
        r.c.handle(new InputEvent(Kind.DOWN, a.r.x() + 5, a.r.y() + 5, 1, 0, 0));  // botão direito não clica
        r.c.handle(new InputEvent(Kind.UP, a.r.x() + 5, a.r.y() + 5, 1, 0, 0));
        assertEquals(n, r.requests.size());
    }

    @Test
    void dragRotatesWheelZoomsOrScrollsByRegion(@TempDir Path tmp) {
        Rig r = new Rig(tmp);
        CreatorLayout l = r.c.layout;
        float sx = l.scene.x() + l.scene.w() / 2, sy = r.h * 0.45f, yaw0 = r.cam.tYaw;
        r.c.handle(new InputEvent(Kind.DOWN, sx, sy, 0, 0, 0));
        r.c.handle(new InputEvent(Kind.MOVE, sx + 200, sy, 0, 0, 0));
        // o arrasto continua mesmo saindo da área do personagem (captura até soltar)
        r.c.handle(new InputEvent(Kind.MOVE, r.w - 10, sy, 0, 0, 0));
        r.c.handle(new InputEvent(Kind.UP, r.w - 10, sy, 0, 0, 0));
        assertTrue(r.cam.tYaw < yaw0 - 1f, "arrasto deve girar o personagem");
        // arrasto que termina sobre um controle não o aciona
        int n = r.requests.size();
        CreatorLayout.Widget maga = r.w("preset:maga");
        r.c.handle(new InputEvent(Kind.DOWN, sx, sy, 0, 0, 0));
        r.c.handle(new InputEvent(Kind.MOVE, maga.r.x() + 10, maga.r.y() + 10, 0, 0, 0));
        r.c.handle(new InputEvent(Kind.UP, maga.r.x() + 10, maga.r.y() + 10, 0, 0, 0));
        assertEquals(n, r.requests.size(), "soltar sobre um botão depois de arrastar não clica");
        assertEquals("aventureiro", r.st.appearance.preset);

        float d0 = r.cam.tDist;
        r.c.handle(new InputEvent(Kind.SCROLL, sx, sy, 0, 1, 0));
        assertTrue(r.cam.tDist < d0, "roda sobre o personagem aproxima");
        for (int i = 0; i < 50; i++) r.c.handle(new InputEvent(Kind.SCROLL, sx, sy, 0, 1, 0));
        assertEquals(CreatorCamera.MIN_DIST, r.cam.tDist, 1e-4, "limite de aproximação");
        for (int i = 0; i < 80; i++) r.c.handle(new InputEvent(Kind.SCROLL, sx, sy, 0, -1, 0));
        assertEquals(CreatorCamera.MAX_DIST, r.cam.tDist, 1e-4, "limite de afastamento");

        // roda sobre o painel rola as opções (janela baixa: o conteúdo de Rosto passa da altura do painel)
        r.w = 1920; r.h = 420; r.st.category = CreatorState.Category.ROSTO;
        r.relayout();
        l = r.c.layout;
        float dist = r.cam.tDist;
        assertTrue(l.contentHeight > l.options.h(), "caso de teste precisa de conteúdo maior que o painel");
        r.c.handle(new InputEvent(Kind.SCROLL, l.options.x() + 30, l.options.y() + 30, 0, -3, 0));
        assertTrue(r.st.scroll > 0, "roda sobre o painel rola");
        assertEquals(dist, r.cam.tDist, "roda sobre o painel não dá zoom");
        r.c.handle(new InputEvent(Kind.SCROLL, l.options.x() + 30, l.options.y() + 30, 0, -100, 0));
        assertEquals(l.contentHeight - l.options.h(), r.st.scroll, 1e-3, "rolagem tem fim");
        r.c.handle(new InputEvent(Kind.SCROLL, l.options.x() + 30, l.options.y() + 30, 0, 100, 0));
        assertEquals(0, r.st.scroll, 1e-3);
    }

    @Test
    void cameraIsSmoothBoundedAndFocusesTheHead() {
        CreatorCamera c = new CreatorCamera();
        float d = c.dist;
        c.focusFace(true);
        c.update(1 / 60f);
        assertTrue(c.dist < d && c.dist > c.tDist, "aproxima suavemente, sem salto");
        for (int i = 0; i < 240; i++) c.update(1 / 60f);
        assertEquals(c.tDist, c.dist, 1e-3);
        assertTrue(c.height > 0.8f, "foco na cabeça");
        for (int i = 0; i < 100; i++) c.drag(0, 500, 1f);
        assertEquals(CreatorCamera.MAX_PITCH, c.tPitch, 1e-4);
        c.reset();
        assertEquals(0.32f, c.tYaw, 1e-4);
    }

    // ------------------------------------------------------------------ restaurar, salvar, carregar

    @Test
    void restoreAsksForConfirmationAndReturnsTheCanonicalPreset(@TempDir Path tmp) {
        Rig r = new Rig(tmp);
        assertFalse(r.w("restore").enabled, "já é o original: indisponível");
        assertNotNull(r.w("restore").reason);
        r.click("restore");                                           // clique no indisponível: explica, não age
        assertEquals("erro", r.st.messageKind);
        r.click("cat:CABELO");
        r.click("hair:cabelo.rabo");
        assertTrue(r.w("restore").enabled);
        r.click("restore");
        assertEquals(CreatorState.Dialog.CONFIRM_RESTORE, r.st.dialog);
        // com diálogo aberto, o resto da tela não responde
        assertNull(r.c.layout.hit(r.w("save").r.x() + 4, r.w("save").r.y() + 4, r.st));
        r.click("dialog-cancel");
        assertEquals("cabelo.rabo", r.st.appearance.hair);
        r.click("restore");
        r.click("restore-confirm");
        assertEquals(CreatorState.Dialog.NONE, r.st.dialog);
        assertTrue(r.st.isOriginal());
        assertEquals(Appearance.preset("aventureiro").signature(), r.last().signature());
    }

    @Test
    void saveAndLoadKeepTheV1FormatAndNeverOverwrite(@TempDir Path tmp) throws Exception {
        Rig r = new Rig(tmp);
        assertFalse(r.w("load").enabled, "sem aparências salvas: carregar indisponível");
        r.click("preset:exploradora");
        r.click("name");
        assertTrue(r.st.editingName);
        for (int i = 0; i < 20; i++) r.key(GLFW_KEY_BACKSPACE);
        for (char ch : "Ária Sol".toCharArray()) r.c.handle(new InputEvent(Kind.CHAR, 0, 0, 0, 0, ch));
        r.key(GLFW_KEY_ENTER);
        assertEquals("Ária Sol", r.st.name);
        r.click("cat:CABELO");
        r.click("hair:cabelo.curto");
        r.click("save");
        r.click("save");                                              // segundo arquivo, nunca sobrescreve
        List<Path> files;
        try (var fs = Files.list(tmp)) { files = fs.sorted().toList(); }
        assertEquals(List.of("aria-sol-001.json", "aria-sol-002.json"), files.stream().map(p -> p.getFileName().toString()).toList());
        String json = Files.readString(files.getFirst());
        assertTrue(json.contains("\"contrato\": \"valumo.aparencia\"") || json.contains("\"contrato\":\"valumo.aparencia\""), json);
        Appearance back = Appearance.load(files.getFirst(), CAT);
        assertEquals(r.st.appearance.signature(), back.signature());
        assertEquals("Ária Sol", back.name);

        r.click("restore");
        r.click("restore-confirm");
        assertTrue(r.w("load").enabled);
        r.click("load");
        assertEquals(CreatorState.Dialog.LOAD, r.st.dialog);
        r.click("loadfile:0");
        assertEquals(back.signature(), r.st.appearance.signature());
        assertEquals(back.signature(), r.last().signature());
        assertEquals("Ária Sol", r.st.name);

        // arquivo inválido: mensagem compreensível, aparência intacta
        Files.writeString(tmp.resolve("zz-quebrado.json"), "{\"contrato\":\"valumo.aparencia\",\"versao\":1,\"preset\":\"dragao\"}");
        r.c.refreshSaves();
        int idx = r.st.saves.indexOf(tmp.resolve("zz-quebrado.json"));
        r.click("load");
        r.click("loadfile:" + idx);
        assertEquals("erro", r.st.messageKind);
        assertEquals(back.signature(), r.st.appearance.signature());
    }

    @Test
    void keyboardAlternativeCoversTheFlow(@TempDir Path tmp) {
        Rig r = new Rig(tmp);
        r.key(GLFW_KEY_RIGHT);                                         // próximo preset
        assertNotEquals("aventureiro", r.st.appearance.preset);
        r.key(GLFW_KEY_TAB);
        assertEquals(CreatorState.Category.CABELO, r.st.category);
        String hair = CAT.hair(r.st.appearance);
        r.key(GLFW_KEY_RIGHT);
        assertNotEquals(hair, CAT.hair(r.st.appearance));
        String color = CAT.hairColor(r.st.appearance);
        r.key(GLFW_KEY_DOWN);
        assertNotEquals(color, CAT.hairColor(r.st.appearance));
        r.key(GLFW_KEY_F2);
        assertEquals(1, r.st.saves.size());
        r.key(GLFW_KEY_BACKSPACE);
        assertEquals(CreatorState.Dialog.CONFIRM_RESTORE, r.st.dialog);
        r.key(GLFW_KEY_ENTER);
        assertTrue(r.st.isOriginal());
        r.key(GLFW_KEY_F3);
        assertEquals(CreatorState.Dialog.LOAD, r.st.dialog);
        r.key(GLFW_KEY_ESCAPE);
        assertEquals(CreatorState.Dialog.NONE, r.st.dialog);
    }

    // ------------------------------------------------------------------ indisponível

    @Test
    void incompatibleOptionsAreDisabledWithAReasonAndNeverApplied(@TempDir Path tmp) {
        // o catálogo atual não tem combinação proibida; o teste marca uma ("X") só em memória
        var rules = CAT.components.get("chapeu.maga").doc.getAsJsonObject("cabelo");
        rules.addProperty("curto", "X");
        try {
            Rig r = new Rig(tmp);
            r.click("cat:CABELO");
            r.click("hair:cabelo.curto");
            r.click("cat:CHAPEU");
            CreatorLayout.Widget hat = r.w("cover:chapeu.maga");
            assertFalse(hat.enabled);
            assertEquals("Não combina com o penteado atual", hat.reason);
            int n = r.requests.size();
            r.click("cover:chapeu.maga");
            assertEquals(n, r.requests.size(), "opção indisponível não recompõe");
            assertNotEquals("chapeu.maga", CAT.cover(r.st.appearance));
            assertEquals("erro", r.st.messageKind);
            // o caminho inverso também é barrado: com chapéu, o penteado proibido fica indisponível
            r.click("cat:CABELO");
            r.click("hair:cabelo.topete");
            r.click("cat:CHAPEU");
            r.click("cover:chapeu.maga");
            r.click("cat:CABELO");
            assertFalse(r.w("hair:cabelo.curto").enabled);
            r.c.action("hair:cabelo.curto");
            assertEquals("cabelo.topete", r.st.appearance.hair);
        } finally {
            rules.remove("curto");
        }
    }

    // ------------------------------------------------------------------ composição assíncrona

    static AsyncComposer async(int cache) {
        AsyncComposer a = new AsyncComposer(Path.of("assets"), cache);
        a.uploadFn = p -> {                                           // sem OpenGL: modelo só com o esqueleto
            CharacterModel m = new CharacterModel();
            m.name = p.rig.name;
            m.parts.add(new CharacterModel.Part());
            return m;
        };
        return a;
    }

    @Test
    void rapidRequestsEndOnTheLastChoice() {
        try (AsyncComposer a = async(4)) {
            List<Appearance> clicks = new ArrayList<>();
            for (String p : List.of("aventureiro", "exploradora", "maga"))
                for (String hcut : CAT.ids("cabelo"))
                    for (String cov : List.of(Appearance.NO_COVER, "chapeu.maga", "capuz.c3")) {
                        Appearance x = Appearance.preset(p);
                        x.hair = hcut;
                        x.cover = cov;
                        x.hairColor = CAT.colors.get("cabelo").get(clicks.size() % 10)[1];
                        clicks.add(x);
                    }
            int fromCache = 0;
            for (Appearance x : clicks) { if (a.request(x)) fromCache++; a.poll(); }     // um "quadro" por clique
            a.drain(20000);
            // rodada 09: invariante sem tempo — todo pedido termina EXATAMENTE uma vez: mostrado ou descartado (na fila,
            // ao começar ou ao ficar pronto). Antes, os tirados da fila sumiam da conta e, sob carga (a thread de composição
            // sem CPU), "descartados > 0" falhava mesmo com o comportamento certo.
            // (auditoria de engenharia: três dos cliques são combinações que não compõem — crespo × capuz C3; se a thread
            // de composição pegasse uma delas como pedido atual, ela não contava em lugar nenhum. Agora conta em failed.)
            assertEquals(clicks.size() - fromCache, a.completed + a.discarded + a.failed, "pedido sem destino ou contado duas vezes");
            Appearance lastClick = clicks.getLast();
            assertEquals(lastClick.signature(), a.wanted);
            assertEquals(lastClick.signature(), a.shown, "o personagem mostrado é o da última escolha");
            assertTrue(a.discarded > 0, "pedidos obsoletos são descartados");
            assertTrue(a.completed < clicks.size(), "nem todo pedido chega à GPU");
            assertTrue(a.cachedModels() <= 4, "cache limitado (malhas liberadas ao sair)");
        }
    }

    /** Auditoria de engenharia — uma composição que falha TERMINA o pedido (antes: "Preparando…" e miniatura pendente para sempre). */
    @Test
    void aCompositionThatFailsEndsTheRequestInsteadOfHangingIt() {
        try (AsyncComposer a = async(4)) {
            Appearance ok = Appearance.preset("aventureiro");
            a.request(ok);
            a.drain(20000);
            assertEquals(ok.signature(), a.shown);
            Appearance bad = Appearance.preset("exploradora");            // capuz C3 × crespo: a regra do catálogo recusa
            bad.hair = "cabelo.crespo";
            assertThrows(RuntimeException.class, () -> new Composer(Path.of("assets"), 1).prepare(bad), "a combinação precisa ser inválida");
            a.request(bad);
            a.drain(20000);
            assertFalse(a.busy(), "o pedido que falhou não fica pendente");
            assertEquals(1, a.failed);
            assertNotNull(a.lastError);
            assertEquals(ok.signature(), a.shown, "continua mostrando o último personagem válido");
            assertEquals(ok.signature(), a.wanted);
            // miniatura da mesma combinação: o chamador é avisado (null), não esquecido
            List<Object> got = new ArrayList<>();
            a.requestThumb(bad, p -> got.add(p == null ? "falhou" : p));
            long end = System.currentTimeMillis() + 20000;
            while (got.isEmpty() && System.currentTimeMillis() < end) { a.poll(); Thread.onSpinWait(); }
            assertEquals(List.of("falhou"), got);
            // e a thread de composição continua viva para o pedido seguinte
            Appearance next = Appearance.preset("maga");
            a.request(next);
            a.drain(20000);
            assertEquals(next.signature(), a.shown);
        }
    }

    @Test
    void cacheHitIsInstantAndEvictionReleases() {
        try (AsyncComposer a = async(2)) {
            List<String> evicted = new ArrayList<>();
            a.evicted = (k, m) -> evicted.add(k);
            Appearance p1 = Appearance.preset("aventureiro"), p2 = Appearance.preset("maga"), p3 = Appearance.preset("exploradora");
            assertFalse(a.request(p1));
            a.drain(20000);
            assertNotNull(a.model(p1.signature()));
            a.request(p2);
            a.drain(20000);
            assertTrue(a.request(p1), "voltar a uma escolha recente é instantâneo (cache)");
            assertEquals(p1.signature(), a.shown);
            a.request(p3);
            a.drain(20000);
            assertEquals(List.of(p2.signature()), evicted, "sai o menos usado; o recém-usado fica");
            assertEquals(2, a.cachedModels());
        }
    }
}
