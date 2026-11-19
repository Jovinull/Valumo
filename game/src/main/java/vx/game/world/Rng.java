package vx.game.world;

import java.nio.charset.StandardCharsets;

/**
 * Rodada 11 — gerador pseudoaleatório do mundo: SplitMix64, escrito aqui para o resultado não depender da JVM nem de
 * {@code java.util.Random}. Cada decisão usa um gerador DERIVADO da semente e de um rótulo estável
 * ({@code derive(seed, "regiao", "r0", "vegetacao")}): acrescentar uma árvore não muda os encontros nem a missão, e a
 * ordem em que as coisas são consultadas não muda nada.
 */
public final class Rng {
    private long state;

    public Rng(long seed) { state = seed; }

    /** Semente derivada: FNV-1a 64 dos rótulos (UTF-8, com separador) misturada à semente e passada pelo SplitMix. */
    public static long derive(long seed, String... labels) {
        long h = 0xcbf29ce484222325L ^ mix(seed);
        for (String l : labels) {
            for (byte b : l.getBytes(StandardCharsets.UTF_8)) { h ^= b & 0xff; h *= 0x100000001b3L; }
            h ^= 0x1f; h *= 0x100000001b3L;                               // separador: ("ab","c") ≠ ("a","bc")
        }
        return mix(h);
    }

    public static Rng of(long seed, String... labels) { return new Rng(derive(seed, labels)); }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        state += 0x9e3779b97f4a7c15L;
        return mix(state);
    }

    /** [0, 1) com 24 bits (igual em toda JVM). */
    public float next() { return (nextLong() >>> 40) / (float) (1 << 24); }

    public float range(float a, float b) { return a + (b - a) * next(); }

    public int below(int n) { return (int) ((nextLong() >>> 33) % n); }

    public boolean chance(float p) { return next() < p; }

    public <T> T pick(java.util.List<T> l) { return l.get(below(l.size())); }
}
