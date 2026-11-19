package vx.game.menu;

import vx.game.SaveGame;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Rodada 11.1 — a porta de entrada: menu principal → personagens → mundos (→ criar mundo) → aventura, e configurações.
 * Sem OpenGL: guarda a tela, as listas, as seleções e os campos de texto, e transforma cliques e teclas em mudanças de
 * tela ou em {@link Request PEDIDOS} que só o {@code Game} sabe cumprir (abrir o criador, renderizar uma prévia, gerar
 * a região, entrar no mundo). O {@code Game} consome o pedido e devolve o resultado por {@link #done}/{@link #failed}.
 * Contrato: {@code docs/architecture/menus.md}.
 */
public final class MenuFlow {
    public enum Screen { MAIN, CHARACTERS, WORLDS, CREATE_WORLD, SETTINGS, LOADING }

    /** O que a tela pediu e só o jogo cumpre. {@code profile}/{@code world} = de quem; {@code name}/{@code seed} = do formulário. */
    public enum RequestKind { QUIT, NEW_CHARACTER, EDIT_CHARACTER, PREVIEW, CREATE_WORLD, ENTER }

    public record Request(RequestKind kind, CharacterStore.Profile profile, WorldStore.World world, String name, long seed) { }

    public Screen screen = Screen.MAIN;
    /** Para onde as configurações voltam. */
    public Screen settingsFrom = Screen.MAIN;
    public final CharacterStore characters;
    public final WorldStore worlds;

    public List<CharacterStore.Profile> profiles = List.of();
    public final List<CharacterStore.Broken> brokenProfiles = new ArrayList<>();
    public int profileSel;
    public String chosenProfile;                                          // id escolhido (sobrevive ao recarregar a lista)

    public List<WorldStore.World> worldList = List.of();
    public final List<CharacterStore.Broken> brokenWorlds = new ArrayList<>();
    public int worldSel;
    public String chosenWorld;

    /** Formulário de criar mundo: campos, foco (0 nome, 1 semente), erro, semente da prévia mostrada. */
    public final StringBuilder nameField = new StringBuilder(), seedField = new StringBuilder();
    public int focus;
    public String error;
    public Long previewSeed;
    public boolean previewFailed;

    /** Tela de carregamento: o passo atual (texto real, sem porcentagem inventada). */
    public String loading;
    public Request pending;
    public String message;
    public float messageUntil, time;

    public MenuFlow(CharacterStore characters, WorldStore worlds) {
        this.characters = characters;
        this.worlds = worlds;
    }

    // ------------------------------------------------------------------ listas

    public void refreshCharacters() {
        brokenProfiles.clear();
        profiles = characters.list(brokenProfiles);
        profileSel = 0;
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).id().equals(chosenProfile)) profileSel = i;
    }

    public void refreshWorlds() {
        brokenWorlds.clear();
        worldList = worlds.list(brokenWorlds);
        worldSel = 0;
        for (int i = 0; i < worldList.size(); i++) if (worldList.get(i).id().equals(chosenWorld)) worldSel = i;
    }

    public CharacterStore.Profile profile() { return profiles.isEmpty() ? null : profiles.get(Math.clamp(profileSel, 0, profiles.size() - 1)); }

    public WorldStore.World world() { return worldList.isEmpty() ? null : worldList.get(Math.clamp(worldSel, 0, worldList.size() - 1)); }

    /** O personagem escolhido tem aventura no mundo selecionado? (Continuar × Iniciar aventura) */
    public boolean hasAdventure() {
        CharacterStore.Profile p = profile();
        WorldStore.World w = world();
        return p != null && w != null && worlds.hasAdventure(w.id(), p.id());
    }

    // ------------------------------------------------------------------ ações (botões e teclas chegam aqui)

    public void action(String id) {
        if (screen == Screen.LOADING) return;                              // carregando: nada muda por baixo
        switch (id) {
            case "jogar" -> { refreshCharacters(); screen = Screen.CHARACTERS; }
            case "config" -> { settingsFrom = screen; screen = Screen.SETTINGS; }
            case "sair" -> pending = new Request(RequestKind.QUIT, null, null, null, 0);
            case "voltar" -> back();
            case "criar-personagem" -> pending = new Request(RequestKind.NEW_CHARACTER, null, null, null, 0);
            case "editar-personagem" -> { if (profile() != null) pending = new Request(RequestKind.EDIT_CHARACTER, profile(), null, null, 0); }
            case "escolher-personagem" -> {
                if (profile() == null) { say("Crie um personagem primeiro", 3f); return; }
                chosenProfile = profile().id();
                refreshWorlds();
                screen = Screen.WORLDS;
            }
            case "criar-mundo" -> openCreate();
            case "gerar-semente" -> {
                seedField.setLength(0);
                seedField.append(Long.toUnsignedString(worlds.randomSeed()));
                error = null;
                focus = 1;
            }
            case "ver-previa" -> requestPreview();
            case "confirmar-mundo" -> confirmCreate();
            case "cancelar-mundo" -> { screen = Screen.WORLDS; error = null; }
            case "entrar" -> {
                if (profile() == null || world() == null) return;
                chosenWorld = world().id();
                pending = new Request(RequestKind.ENTER, profile(), world(), null, 0);
            }
            default -> {
                if (id.startsWith("personagem:")) { profileSel = Math.clamp(Integer.parseInt(id.substring(11)), 0, Math.max(0, profiles.size() - 1)); chosenProfile = profile() != null ? profile().id() : null; }
                else if (id.startsWith("mundo:")) { worldSel = Math.clamp(Integer.parseInt(id.substring(6)), 0, Math.max(0, worldList.size() - 1)); chosenWorld = world() != null ? world().id() : null; }
                else if (id.startsWith("campo:")) focus = Integer.parseInt(id.substring(6));
            }
        }
    }

    void back() {
        switch (screen) {
            case CHARACTERS -> screen = Screen.MAIN;
            case WORLDS -> { refreshCharacters(); screen = Screen.CHARACTERS; }
            case CREATE_WORLD -> { screen = Screen.WORLDS; error = null; }
            case SETTINGS -> screen = settingsFrom;
            default -> { }
        }
    }

    void openCreate() {
        nameField.setLength(0);
        seedField.setLength(0);
        focus = 0;
        error = null;
        previewSeed = null;
        previewFailed = false;
        screen = Screen.CREATE_WORLD;
    }

    /** A prévia é da semente DIGITADA (vazia = sorteia uma agora e a mostra no campo: a prévia nunca é de outra semente). */
    void requestPreview() {
        try {
            if (seedField.toString().isBlank()) action("gerar-semente");
            long s = worlds.parseSeed(seedField.toString());
            error = null;
            pending = new Request(RequestKind.PREVIEW, null, null, null, s);
        } catch (WorldStore.CreateFailed e) {
            error = e.getMessage();
        }
    }

    void confirmCreate() {
        try {
            if (seedField.toString().isBlank()) action("gerar-semente");   // a semente sorteada aparece antes de criar
            long s = worlds.parseSeed(seedField.toString());
            error = null;
            pending = new Request(RequestKind.CREATE_WORLD, null, null, nameField.toString(), s);
        } catch (WorldStore.CreateFailed e) {
            error = e.getMessage();
        }
    }

    // ------------------------------------------------------------------ teclado e texto

    /** Tecla da tela atual (Esc volta; Enter confirma; ↑ ↓ escolhem; Tab troca o campo). */
    public void key(int code) {
        if (screen == Screen.LOADING) return;
        if (screen == Screen.CREATE_WORLD) {
            switch (code) {
                case GLFW_KEY_ESCAPE -> action("cancelar-mundo");
                case GLFW_KEY_TAB, GLFW_KEY_DOWN, GLFW_KEY_UP -> focus = 1 - focus;
                case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> action("confirmar-mundo");
                case GLFW_KEY_BACKSPACE -> {
                    StringBuilder f = focus == 0 ? nameField : seedField;
                    if (!f.isEmpty()) f.setLength(f.offsetByCodePoints(f.length(), -1));
                    error = null;
                }
                default -> { }
            }
            return;
        }
        switch (code) {
            case GLFW_KEY_ESCAPE -> { if (screen == Screen.MAIN) return; action("voltar"); }
            case GLFW_KEY_UP, GLFW_KEY_W -> move(-1);
            case GLFW_KEY_DOWN, GLFW_KEY_S -> move(1);
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER, GLFW_KEY_SPACE -> {
                switch (screen) {
                    case MAIN -> action(mainItems()[mainFocus]);
                    case CHARACTERS -> action("escolher-personagem");
                    case WORLDS -> action("entrar");
                    default -> { }
                }
            }
            case GLFW_KEY_N -> { if (screen == Screen.CHARACTERS) action("criar-personagem"); else if (screen == Screen.WORLDS) action("criar-mundo"); }
            default -> { }
        }
    }

    /** Foco do menu principal (teclado). */
    public int mainFocus;

    public static String[] mainItems() { return new String[]{"jogar", "config", "sair"}; }

    void move(int d) {
        switch (screen) {
            case MAIN -> mainFocus = Math.floorMod(mainFocus + d, mainItems().length);
            case CHARACTERS -> { if (!profiles.isEmpty()) { profileSel = Math.floorMod(profileSel + d, profiles.size()); chosenProfile = profile().id(); } }
            case WORLDS -> { if (!worldList.isEmpty()) { worldSel = Math.floorMod(worldSel + d, worldList.size()); chosenWorld = world().id(); } }
            default -> { }
        }
    }

    /** Caractere digitado (só na tela de criar mundo; a semente só aceita algarismos). */
    public void chr(int codepoint) {
        if (screen != Screen.CREATE_WORLD || Character.isISOControl(codepoint)) return;
        if (focus == 1) {
            if (codepoint < '0' || codepoint > '9' || seedField.length() >= 20) return;
            seedField.appendCodePoint(codepoint);
        } else {
            if (nameField.codePointCount(0, nameField.length()) >= WorldStore.MAX_NAME) return;
            nameField.appendCodePoint(codepoint);
        }
        error = null;
    }

    /** O campo de texto está com foco (as teclas de jogo não valem). */
    public boolean typing() { return screen == Screen.CREATE_WORLD; }

    // ------------------------------------------------------------------ resultado dos pedidos (o Game chama)

    public Request take() {
        Request r = pending;
        pending = null;
        return r;
    }

    /** Pedido cumprido: a tela se atualiza (personagem novo selecionado, mundo novo selecionado, prévia pronta). */
    public void done(Request r, String newId) {
        switch (r.kind()) {
            case NEW_CHARACTER, EDIT_CHARACTER -> {
                if (newId != null) chosenProfile = newId;
                refreshCharacters();
                screen = Screen.CHARACTERS;
            }
            case PREVIEW -> { previewSeed = r.seed(); previewFailed = false; }
            case CREATE_WORLD -> {
                chosenWorld = newId;
                refreshWorlds();
                screen = Screen.WORLDS;
                say("Mundo criado", 3f);
            }
            default -> { }
        }
        loading = null;
        if (screen == Screen.LOADING) screen = Screen.WORLDS;
    }

    /** Pedido falhou: a mensagem é para o jogador; o formulário fica como estava (nome e semente preservados). */
    public void failed(Request r, String why) {
        loading = null;
        switch (r.kind()) {
            case PREVIEW -> { previewSeed = null; previewFailed = true; error = why; screen = Screen.CREATE_WORLD; }
            case CREATE_WORLD -> { error = why; screen = Screen.CREATE_WORLD; }
            case ENTER -> { screen = Screen.WORLDS; say(why, 6f); }
            default -> say(why, 6f);
        }
    }

    public void say(String m, float secs) {
        message = m;
        messageUntil = time + secs;
    }

    public void tick(float dt) { time += dt; }

    /** Arquivo do save da aventura (personagem × mundo) escolhida. */
    public Path adventureDir() {
        return profile() == null || world() == null ? null : worlds.adventureDir(world().id(), profile().id());
    }

    public static boolean hasSave(Path dir) { return dir != null && java.nio.file.Files.isRegularFile(dir.resolve(SaveGame.FILE)); }
}
