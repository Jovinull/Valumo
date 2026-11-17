package vx.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Rodada 07.2 — fonte de pixel própria do Valumo (desenhada aqui, como os ícones; nenhuma fonte de terceiros).
 * Proporcional, maiúsculas de 7 linhas, minúsculas de 5 com ascendentes e descendentes de 2, acentos do
 * português compostos (agudo, grave, circunflexo, til, trema e cedilha). Negrito = cada pixel engrossado um
 * pixel à direita, sem fechar os vãos de um pixel. Sem OpenGL: dá os bitmaps e as larguras; {@link FontAtlas} faz a textura.
 *
 * <p>Grade de cada glifo: 12 linhas — 0–1 acento de maiúscula, 2 folga, 3–9 altura da maiúscula, 10–11
 * descendente. Minúsculas ocupam 5–9; o acento delas fica em 2–3.
 */
public final class PixelFont {
    public static final int ROWS = 12, CAP_TOP = 3, CAP_ROWS = 7;
    /** Centro da maiúscula na grade (linhas): usado para centralizar o texto verticalmente. */
    public static final float CAP_CENTER = CAP_TOP + CAP_ROWS / 2f;

    /** Glifo: linhas de '#' (largura = maior linha), na grade de 12. */
    public record Glyph(boolean[][] px, int width) {
        boolean on(int x, int y) { return y >= 0 && y < ROWS && x >= 0 && x < width && px[y][x]; }
    }

    private static final Map<Character, String[]> BASE = new HashMap<>();
    private static final Map<Character, Glyph> REGULAR = new HashMap<>(), BOLD = new HashMap<>();

    /** Linhas 0–6 = altura da maiúscula; 7–8 = descendente. */
    private static void def(char c, String... rows) { BASE.put(c, rows); }

    static {
        // ---- maiúsculas
        def('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        def('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        def('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        def('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        def('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        def('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        def('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
        def('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        def('I', "###", ".#.", ".#.", ".#.", ".#.", ".#.", "###");
        def('J', "..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##..");
        def('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        def('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        def('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        def('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        def('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        def('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        def('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        def('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        def('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        def('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        def('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        def('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        def('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        def('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        def('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        // ---- minúsculas (x-altura nas linhas 2–6)
        def('a', "", "", ".###.", "....#", ".####", "#...#", ".####");
        def('b', "#....", "#....", "####.", "#...#", "#...#", "#...#", "####.");
        def('c', "", "", ".###", "#...", "#...", "#...", ".###");
        def('d', "....#", "....#", ".####", "#...#", "#...#", "#...#", ".####");
        def('e', "", "", ".###.", "#...#", "#####", "#....", ".###.");
        def('f', "..##", ".#..", "###.", ".#..", ".#..", ".#..", ".#..");
        def('g', "", "", ".####", "#...#", "#...#", "#...#", ".####", "....#", ".###.");
        def('h', "#....", "#....", "####.", "#...#", "#...#", "#...#", "#...#");
        def('i', "#", "", "#", "#", "#", "#", "#");
        def('ı', "", "", "#", "#", "#", "#", "#");                      // i sem pingo (base dos acentos)
        def('j', "..#", "", "..#", "..#", "..#", "..#", "..#", "..#", "##.");
        def('k', "#...", "#...", "#..#", "#.#.", "##..", "#.#.", "#..#");
        def('l', "#.", "#.", "#.", "#.", "#.", "#.", ".#");
        def('m', "", "", "####.", "#.#.#", "#.#.#", "#.#.#", "#.#.#");
        def('n', "", "", "####.", "#...#", "#...#", "#...#", "#...#");
        def('o', "", "", ".###.", "#...#", "#...#", "#...#", ".###.");
        def('p', "", "", "####.", "#...#", "#...#", "#...#", "####.", "#....", "#....");
        def('q', "", "", ".####", "#...#", "#...#", "#...#", ".####", "....#", "....#");
        def('r', "", "", "#.##", "##..", "#...", "#...", "#...");
        def('s', "", "", ".####", "#....", ".###.", "....#", "####.");
        def('t', ".#..", ".#..", "###.", ".#..", ".#..", ".#..", "..##");
        def('u', "", "", "#...#", "#...#", "#...#", "#...#", ".####");
        def('v', "", "", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        def('w', "", "", "#...#", "#...#", "#.#.#", "#.#.#", ".#.#.");
        def('x', "", "", "#...#", ".#.#.", "..#..", ".#.#.", "#...#");
        def('y', "", "", "#...#", "#...#", "#...#", "#...#", ".####", "....#", ".###.");
        def('z', "", "", "#####", "...#.", "..#..", ".#...", "#####");
        // ---- algarismos
        def('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        def('1', ".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###");
        def('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        def('3', ".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###.");
        def('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        def('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        def('6', ".###.", "#....", "#....", "####.", "#...#", "#...#", ".###.");
        def('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        def('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        def('9', ".###.", "#...#", "#...#", ".####", "....#", "....#", ".###.");
        // ---- pontuação e sinais
        def(' ', "...");
        def('.', "", "", "", "", "", "", "#");
        def(',', "", "", "", "", "", "", ".#", "#.");
        def(':', "", "", "#", "", "", "", "#");
        def(';', "", "", ".#", "", "", "", ".#", "#.");
        def('!', "#", "#", "#", "#", "#", "", "#");
        def('?', ".###.", "#...#", "....#", "...#.", "..#..", "", "..#..");
        def('\'', "#", "#");
        def('"', "#.#", "#.#");
        def('(', ".#", "#.", "#.", "#.", "#.", "#.", ".#");
        def(')', "#.", ".#", ".#", ".#", ".#", ".#", "#.");
        def('[', "##", "#.", "#.", "#.", "#.", "#.", "##");
        def(']', "##", ".#", ".#", ".#", ".#", ".#", "##");
        def('-', "", "", "", "###");
        def('_', "", "", "", "", "", "", "#####");
        def('/', "....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#....");
        def('+', "", "", ".#.", "###", ".#.");
        def('=', "", "", "###", "", "###");
        def('%', "##..#", "##.#.", "...#.", "..#..", ".#...", ".#.##", "#..##");
        def('#', "", ".#.#.", "#####", ".#.#.", "#####", ".#.#.");
        def('&', ".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#");
        def('*', "", "#.#", ".#.", "#.#");
        def('<', "", "..#", ".#.", "#..", ".#.", "..#");
        def('>', "", "#..", ".#.", "..#", ".#.", "#..");
        def('@', ".###.", "#...#", "#.###", "#.#.#", "#.###", "#....", ".###.");
        def('|', "#", "#", "#", "#", "#", "#", "#", "#");
        def('°', ".#.", "#.#", ".#.");                                   // grau
        def('…', "", "", "", "", "", "", "#.#.#");                       // reticências
        def('–', "", "", "", "####");                                    // meia-risca
        def('—', "", "", "", "#####");                                   // travessão
        def('•', "", "", "", "##", "##");                                // marcador
        def('‘', "#", "#");
        def('’', "#", "#");
        def('“', "#.#", "#.#");
        def('”', "#.#", "#.#");
        def('↑', "..#..", ".###.", "#.#.#", "..#..", "..#..", "..#..", "..#..");     // setas (rodada 08: controles)
        def('↓', "..#..", "..#..", "..#..", "..#..", "#.#.#", ".###.", "..#..");
        def('←', "", "..#....", ".#.....", "#######", ".#.....", "..#....");
        def('→', "", "....#..", ".....#.", "#######", ".....#.", "....#..");
        def('·', "", "", "", "##", "##");                                  // ponto médio (separador)
    }

    // acentos (2 linhas) e a base de cada letra acentuada
    private static final String[] ACUTE = {".#", "#."}, GRAVE = {"#.", ".#"}, CIRC = {".#.", "#.#"},
            TILDE = {".#.#", "#.#."}, DIAER = {"", "#.#"};
    private static final Map<Character, Object[]> ACCENTED = new HashMap<>();

    private static void acc(String chars, String bases, String[] mark) {
        for (int i = 0; i < chars.length(); i++) ACCENTED.put(chars.charAt(i), new Object[]{bases.charAt(i), mark});
    }

    static {
        acc("áéíóú", "aeıou", ACUTE);
        acc("ÁÉÍÓÚ", "AEIOU", ACUTE);
        acc("àèìòù", "aeıou", GRAVE);
        acc("ÀÈÌÒÙ", "AEIOU", GRAVE);
        acc("âêîôû", "aeıou", CIRC);
        acc("ÂÊÎÔÛ", "AEIOU", CIRC);
        acc("ãõñ", "aon", TILDE);
        acc("ÃÕÑ", "AON", TILDE);
        acc("äëïöü", "aeıou", DIAER);
        acc("ÄËÏÖÜ", "AEIOU", DIAER);
    }

    private static Glyph build(String[] capRows) {
        int w = 0;
        for (String r : capRows) w = Math.max(w, r.length());
        boolean[][] px = new boolean[ROWS][Math.max(1, w)];
        for (int r = 0; r < capRows.length; r++)
            for (int x = 0; x < capRows[r].length(); x++) px[CAP_TOP + r][x] = capRows[r].charAt(x) == '#';
        return new Glyph(px, w);
    }

    private static Glyph compose(char c) {
        if (c == 'ç' || c == 'Ç') {                                           // cedilha: gancho sob a base
            Glyph b = build(BASE.get(c == 'ç' ? 'c' : 'C'));
            boolean[][] px = copy(b.px(), b.width());
            int x0 = b.width() / 2 - (c == 'ç' ? 1 : 0);
            px[CAP_TOP + 7][x0 + 1] = true;
            px[CAP_TOP + 8][x0] = true;
            return new Glyph(px, b.width());
        }
        Object[] a = ACCENTED.get(c);
        if (a == null) return null;
        char base = (Character) a[0];
        String[] mark = (String[]) a[1];
        Glyph b = build(BASE.get(base));
        int mw = 0;
        for (String r : mark) mw = Math.max(mw, r.length());
        int w = Math.max(b.width(), mw), bx = (w - b.width()) / 2, mx = (w - mw + 1) / 2;
        boolean[][] px = new boolean[ROWS][w];
        for (int y = 0; y < ROWS; y++) for (int x = 0; x < b.width(); x++) px[y][x + bx] = b.px()[y][x];
        int top = Character.isUpperCase(c) ? 0 : 2;                           // maiúscula: linhas 0–1; minúscula: 2–3
        for (int r = 0; r < mark.length; r++)
            for (int x = 0; x < mark[r].length(); x++) if (mark[r].charAt(x) == '#') px[top + r][x + mx] = true;
        return new Glyph(px, w);
    }

    private static boolean[][] copy(boolean[][] p, int w) {
        boolean[][] o = new boolean[ROWS][w];
        for (int y = 0; y < ROWS; y++) System.arraycopy(p[y], 0, o[y], 0, w);
        return o;
    }

    private static Glyph bold(Glyph g) {
        int w = g.width() + 1;
        boolean[][] px = new boolean[ROWS][w];
        for (int y = 0; y < ROWS; y++)
            for (int x = 0; x < g.width(); x++) {
                if (!g.px()[y][x]) continue;
                px[y][x] = true;
                if (!g.on(x + 2, y) || g.on(x + 1, y)) px[y][x + 1] = true;     // engrossa sem fechar vão de 1 pixel (M, W, m)
            }
        return new Glyph(px, w);
    }

    /** Glifo do caractere (desconhecido → '?'). */
    public static synchronized Glyph glyph(char c, boolean bold) {
        Map<Character, Glyph> cache = bold ? BOLD : REGULAR;
        Glyph g = cache.get(c);
        if (g != null) return g;
        Glyph r = BASE.containsKey(c) ? build(BASE.get(c)) : compose(c);
        if (r == null) r = build(BASE.get('?'));
        g = bold && c != ' ' ? bold(r) : r;
        cache.put(c, g);
        return g;
    }

    public static boolean has(char c) { return BASE.containsKey(c) || ACCENTED.containsKey(c) || c == 'ç' || c == 'Ç'; }

    /** Todos os caracteres desenhados (para o atlas). */
    public static String charset() {
        StringBuilder sb = new StringBuilder();
        for (char c : BASE.keySet()) sb.append(c);
        for (char c : ACCENTED.keySet()) sb.append(c);
        return sb.append("çÇ").toString();
    }

    /** Escala inteira para um tamanho nominal em pixels (maiúscula ≈ 0,75 do tamanho; nunca abaixo de 1). */
    public static int scale(int px) { return Math.max(1, Math.round(px / 9f)); }

    /** Avanço do caractere em pixels de grade (largura + 1 de espaço). */
    public static int advance(char c, boolean bold) { return glyph(c, bold).width() + 1; }

    /** Largura do texto em pixels de tela para o tamanho nominal px. */
    public static float width(String s, int px, boolean bold) {
        int k = scale(px), w = 0;
        for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i), bold);
        return s.isEmpty() ? 0 : (w - 1) * k;
    }
}
