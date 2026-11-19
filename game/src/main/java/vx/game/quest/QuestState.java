package vx.game.quest;

/** Rodada 10 — estado de uma missão, sempre DERIVADO do progresso ({@link QuestProgress#state()}), nunca guardado à parte. */
public enum QuestState {
    /** Ainda não aceita (inclusive depois de recusar: a oferta continua de pé). */
    DISPONIVEL("Disponível"),
    /** Aceita, sem nenhum progresso contado ainda. */
    ACEITA("Aceita"),
    /** Algum progresso contado; objetivos de campo pendentes. */
    EM_ANDAMENTO("Em andamento"),
    /** Todos os objetivos de campo cumpridos: falta voltar e falar com quem deu a missão. */
    PRONTA_PARA_ENTREGAR("Pronta para entregar"),
    /** Entregue; a recompensa foi dada uma única vez. Nunca reabre. */
    CONCLUIDA("Concluída");

    public final String label;

    QuestState(String label) { this.label = label; }

    /** Nome no save (minúsculas, estável). */
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
}
