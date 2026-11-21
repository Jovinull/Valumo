package vx.game.item;

import java.util.Map;
import java.util.Set;

/**
 * Rodada 13 — os ícones de pixel dos itens (14 × 14), na mesma linguagem do coração e do escudo do HUD: um mapa de
 * caracteres por ícone e uma paleta da direção E (madeira, folha, pedra, pano, couro, tinta). Sem GL: o {@code Game}
 * desenha cada pixel com o {@code UiKit}. Ícone novo = um mapa aqui (e o nome dele no catálogo); arte autoral do agente
 * artista pode substituir um mapa sem mudar o contrato (docs/architecture/itens.md).
 */
public final class ItemIcons {
    private ItemIcons() { }

    public static final int SIZE = 14;

    /** Cor (RGBA) de cada caractere; '.' é transparente. */
    public static final Map<Character, Integer> PALETTE = Map.ofEntries(
            Map.entry('G', 0x5f8a5aff), Map.entry('g', 0x8cbf68ff), Map.entry('D', 0x3f6239ff), Map.entry('Y', 0xecb84cff),
            Map.entry('W', 0x9a6a44ff), Map.entry('w', 0x6b4630ff), Map.entry('K', 0x45291cff),
            Map.entry('S', 0xa9a39aff), Map.entry('s', 0x7a746dff), Map.entry('L', 0xd4cec3ff),
            Map.entry('C', 0xf1e6c9ff), Map.entry('c', 0xcdb98fff), Map.entry('B', 0x8e5230ff), Map.entry('b', 0x5c331fff),
            Map.entry('P', 0xf7eed6ff), Map.entry('I', 0x3a2a26ff), Map.entry('R', 0xc8643cff));

    static final Map<String, String[]> ICONS = Map.of(
            "ervas", new String[]{
                    "......g.......",
                    "...g..Gg..g...",
                    "..gGg.GG.gGg..",
                    "..GgGgGgGgG...",
                    "...GDGgGDGg...",
                    "....GDGDGG....",
                    ".....DGDG.....",
                    "......RR......",
                    "......DD......",
                    "......DD......",
                    ".....D..D.....",
                    ".....D..D.....",
                    "....D....D....",
                    ".............."},
            "cataplasma", new String[]{
                    "..............",
                    "..........g...",
                    "........ggG...",
                    ".......gGGD...",
                    "....cCDGDc....",
                    "...cCCDCCCc...",
                    "..cCCCCCCCCc..",
                    "..RRRRRRRRRR..",
                    "..CCCCCCCCCC..",
                    "..CCCCCCCCCC..",
                    "..cCCCCCCCCc..",
                    "...cCCCCCCc...",
                    "....cccccc....",
                    ".............."},
            "pedra", new String[]{
                    "..............",
                    "......sSL.....",
                    ".....sSSSL....",
                    "....sSSSSSL...",
                    "...sSSIISSSL..",
                    "...sSSSSISSL..",
                    "..sSSSSIISSL..",
                    "..sSSSSSSSSL..",
                    "..ssSSSSSSL...",
                    "...ssSSSSSL...",
                    "....sssSSL....",
                    "......sss.....",
                    "..............",
                    ".............."},
            "madeira", new String[]{
                    "..............",
                    "..wW......wW..",
                    "..wWW....wWW..",
                    "..wWW....wWW..",
                    "..KKK....KKK..",
                    "..wWW....wWW..",
                    "..wWW....wWW..",
                    "..wWW....wWW..",
                    "..wWW....wWW..",
                    "..KKK....KKK..",
                    "..wWW....wWW..",
                    "..wW......wW..",
                    "..............",
                    ".............."},
            "registro", new String[]{
                    "..............",
                    "...bBBBBBBB...",
                    "...bBBBBBBBP..",
                    "...bBBBBBBBP..",
                    "...bBYYYBBBP..",
                    "...bBBBBBBBP..",
                    "...bBBBBBBBP..",
                    "...KKKKKKKKP..",
                    "...bBBBBBBBP..",
                    "...bBBBBBBBP..",
                    "...bbbbbbbbP..",
                    "....PPPPPPPP..",
                    "..............",
                    ".............."});

    public static Set<String> names() { return ICONS.keySet(); }

    /** O mapa do ícone {@code name} (null se não existe). */
    public static String[] map(String name) { return ICONS.get(name); }
}
