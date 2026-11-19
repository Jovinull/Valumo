package vx.game.quest;

/**
 * Rodada 10 — o que aconteceu no mundo e interessa às missões. O jogo produz os eventos no passo lógico (nunca no
 * desenho) e o {@link QuestSystem} decide se contam. Repetir um evento nunca conta duas vezes.
 */
public sealed interface QuestEvent {
    /** Um inimigo morreu. {@code key}: identidade estável do spawn ({@code trilha.fungo.1}), não a ordem na lista. */
    record EnemyDefeated(String key) implements QuestEvent { }

    /** O herói caiu (derrota). Não desfaz progresso; só vira lembrança para a fala. */
    record HeroDefeated() implements QuestEvent { }
}
