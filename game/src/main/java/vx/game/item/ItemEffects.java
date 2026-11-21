package vx.game.item;

import java.util.Set;

/**
 * Rodada 13 — os efeitos de item que o CÓDIGO sabe fazer. O catálogo só escolhe um destes nomes e o número; nada é
 * executado por reflexão nem por texto do JSON. Efeito novo = um caso aqui + o nome em {@link #KNOWN} + teste.
 * <p>
 * {@code curar}: devolve {@code amount} de vida (2 = um coração), sem passar da vida máxima; só fora da luta engajada,
 * com o herói de pé e ferido. Sem efeito possível, o item NÃO é gasto (a mensagem diz por quê).
 */
public final class ItemEffects {
    private ItemEffects() { }

    public static final String HEAL = "curar";
    public static final Set<String> KNOWN = Set.of(HEAL);

    /** O que o efeito precisa do jogo (a vida do herói e se há luta), sem depender do GL nem do combate inteiro. */
    public interface Target {
        /** Há herói com vida nesta cena (no mundo sem combate, não). */
        boolean hasHero();

        int hp();

        int maxHp();

        boolean alive();

        /** Algum inimigo vivo engajado (a luta está acontecendo). */
        boolean engaged();

        void setHp(int hp);
    }

    /** {@code consumed}: o item foi gasto; {@code message}: o que o jogador lê. */
    public record Result(boolean consumed, String message) { }

    /** Limites do número de cada efeito (o catálogo é recusado fora deles). */
    public static boolean validAmount(String effect, int amount) {
        return HEAL.equals(effect) && amount >= 1 && amount <= 12;
    }

    /** Usa o item {@code d} (que tem uso) no alvo {@code t}. Nunca muda nada quando não consome. */
    public static Result apply(ItemDefinition d, Target t) {
        if (d.use() == null) return new Result(false, d.name() + " não se usa");
        return switch (d.use().effect()) {
            case HEAL -> heal(d, t);
            default -> new Result(false, "Efeito desconhecido");          // o catálogo já recusa; defesa em profundidade
        };
    }

    private static Result heal(ItemDefinition d, Target t) {
        if (!t.hasHero()) return new Result(false, "Não há o que curar aqui");
        if (!t.alive()) return new Result(false, "Não dá para aplicar caído");
        if (t.engaged()) return new Result(false, "Não dá para aplicar no meio da luta");
        if (t.hp() >= t.maxHp()) return new Result(false, "Você não está ferido");
        int before = t.hp(), after = Math.min(t.maxHp(), before + d.use().amount());
        t.setHp(after);
        int healed = after - before;
        return new Result(true, healed >= 2 ? "Um coração de vida de volta" : "Um pouco de vida de volta");
    }
}
