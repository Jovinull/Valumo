package vx.game.item;

/**
 * Rodada 13 — o uso de um item: qual efeito do CÓDIGO ({@link ItemEffects}) e com que número ({@code amount}: a vida que
 * a cataplasma devolve), mais o rótulo do botão ("Aplicar"). O dado escolhe; quem faz é o código validado.
 */
public record ItemUse(String effect, int amount, String label) { }
