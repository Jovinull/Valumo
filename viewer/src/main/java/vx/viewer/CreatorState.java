package vx.viewer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rodada 07.2 — estado do criador de personagens do jogador (sem OpenGL; testável). A aparência é a mesma
 * {@link Appearance} da rodada 07 (formato salvo intacto); aqui ficam só as escolhas de navegação e apresentação.
 */
public final class CreatorState {
    public enum Category {
        MODELO("Modelo"), CABELO("Cabelo"), ROSTO("Rosto"), PELE("Pele"), CHAPEU("Chapéu"), ROUPA("Roupa");
        public final String label;
        Category(String l) { label = l; }
    }

    public enum Dialog { NONE, CONFIRM_RESTORE, LOAD, DONE }

    public Category category = Category.MODELO;
    public Appearance appearance = Appearance.preset("aventureiro");
    /** Prévia (não salva): expressão e pose. */
    public String expr = null;
    public Animator.Clip pose = Animator.Clip.IDLE;
    public String name = "Aventureiro";
    public boolean editingName = false;
    public Dialog dialog = Dialog.NONE;
    /** Vitrine depois de "Pronto": esconde os painéis e mostra o personagem em movimento. */
    public boolean showcase = false;
    public String message, messageKind = "info";
    public float messageUntil;
    public float scroll, loadScroll;
    public java.util.List<java.nio.file.Path> saves = new java.util.ArrayList<>();
    /** Rodada 08: aberto pelo jogo — "Pronto" oferece este botão e encerra o criador devolvendo a aparência. */
    public String finishLabel;
    public boolean finished;

    // ------------------------------------------------------------------ nomes para o jogador (nunca IDs técnicos)

    static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("aventureiro", "Aventureiro");
        NAMES.put("exploradora", "Exploradora");
        NAMES.put("maga", "Maga");
        NAMES.put("cabelo.topete", "Topete");
        NAMES.put("cabelo.curto", "Curto");
        NAMES.put("cabelo.chanel", "Médio");
        NAMES.put("cabelo.rabo", "Preso");
        NAMES.put("cabelo.crespo", "Crespo");               // rodada 09.4: estudo experimental (catálogo: "experimental")
        NAMES.put("cabelo.coque", "Coque alto");            // rodada 09.4: estudo experimental
        NAMES.put("rosto.aventureiro", "Destemido");
        NAMES.put("rosto.exploradora", "Atento");
        NAMES.put("rosto.maga", "Gentil");
        NAMES.put("botas.originais", "Do modelo");          // rodada 09.4: botas (primeira peça de roupa)
        NAMES.put("botas.viajante", "Viajante");
        NAMES.put("olhos.redondos", "Redondos");            // rodada 09.4: formato dos olhos
        NAMES.put("olhos.amendoados", "Amendoados");
        NAMES.put("olhos.inclinados", "Inclinados");
        NAMES.put(Appearance.NO_COVER, "Sem chapéu");
        NAMES.put("chapeu.maga", "Chapéu de aba");
        NAMES.put("capuz.c3", "Laço e pena");
        NAMES.put("cobre", "Cobre"); NAMES.put("mel", "Mel"); NAMES.put("lilas", "Lilás"); NAMES.put("castanho", "Castanho");
        NAMES.put("preto", "Preto"); NAMES.put("loiro", "Loiro"); NAMES.put("ruivo", "Ruivo"); NAMES.put("cinza", "Cinza");
        NAMES.put("musgo", "Musgo"); NAMES.put("anil", "Anil");
        NAMES.put("pessego", "Pêssego"); NAMES.put("bronzeada", "Bronzeada"); NAMES.put("clara", "Clara");
        NAMES.put("morena", "Morena"); NAMES.put("escura", "Escura");
        NAMES.put("verde", "Verde"); NAMES.put("azul", "Azul"); NAMES.put("violeta", "Violeta"); NAMES.put("ambar", "Âmbar");
        NAMES.put("neutral", "Neutra"); NAMES.put("joy", "Alegre"); NAMES.put("determined", "Decidida"); NAMES.put("surprise", "Surpresa");
    }

    public static String name(String id) { return NAMES.getOrDefault(id, id); }

    /** A aparência atual já é o preset original? (desabilita "Restaurar"). */
    public boolean isOriginal() {
        Appearance p = Appearance.preset(appearance.preset);
        return p.signature().equals(appearance.signature());
    }
}
