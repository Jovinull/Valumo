package vx.viewer;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Rodada 07.2 — composição FORA da thread do OpenGL para o editor do jogador.
 *
 * <ul>
 *   <li>Uma thread de trabalho com o seu PRÓPRIO {@link Composer} (caches não são compartilhados entre threads):
 *       compõe e gera os vértices ({@link Composer#prepare}).</li>
 *   <li>A thread do OpenGL só envia ({@link Composer#upload}) em {@link #poll()}.</li>
 *   <li>Cada pedido do personagem principal ganha uma GERAÇÃO; resultado de geração antiga é descartado — o
 *       modelo exibido é sempre o da última escolha, mesmo com cliques rápidos.</li>
 *   <li>Pedidos do personagem passam na frente dos de miniatura (fila com prioridade).</li>
 *   <li>Modelos enviados ficam num LRU por assinatura; ao sair dele, as malhas são fechadas (sem vazamento).</li>
 * </ul>
 */
public final class AsyncComposer implements AutoCloseable {
    private record Job(long gen, Appearance a, boolean main, Consumer<Composer.Prepared> onMain) { }

    private final BlockingDeque<Job> queue = new LinkedBlockingDeque<>();
    private final Deque<Runnable> ready = new ArrayDeque<>();                 // resultados para a thread do GL
    private final Composer worker;                                            // só a thread de trabalho usa
    private final Composer uploader;                                          // só a thread do GL usa (upload)
    private final Thread thread;
    private final AtomicLong mainGen = new AtomicLong();
    private final LinkedHashMap<String, CharacterModel> models;
    private volatile boolean running = true;
    /** Assinatura do último pedido principal e do modelo pronto mais recente. */
    public volatile String wanted, shown;
    public volatile double lastComposeMs, lastMeshMs, lastUploadMs, lastTotalMs;
    public int discarded, completed;
    private long requestedAt;

    public AsyncComposer(Path assets, int cacheSize) {
        worker = new Composer(assets, 1);
        uploader = new Composer(assets, 1);
        models = new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, CharacterModel> e) {
                if (size() <= cacheSize) return false;
                evicted.accept(e.getKey(), e.getValue());
                Composer.release(e.getValue());
                return true;
            }
        };
        uploadFn = uploader::upload;
        thread = new Thread(this::loop, "composicao");
        thread.setDaemon(true);
        thread.start();
    }

    /** Envio à GPU (thread do GL). Os testes trocam por um envio sem OpenGL. */
    public java.util.function.Function<Composer.Prepared, CharacterModel> uploadFn;

    /** Avisado quando um modelo sai do cache (o editor tira a referência da cena). */
    public java.util.function.BiConsumer<String, CharacterModel> evicted = (k, m) -> { };

    public ComponentCatalog catalog() { return worker.catalog; }

    /** Pede o personagem principal; devolve true se já estava pronto no cache (troca instantânea). */
    public synchronized boolean request(Appearance a) {
        String sig = a.signature();
        wanted = sig;
        long gen = mainGen.incrementAndGet();
        int pending = queue.size();
        queue.removeIf(j -> j.main);                                          // pedidos antigos nem começam
        discarded += pending - queue.size();                                  // rodada 09: também são descartes (antes não
                                                                              // contavam: com a CPU cheia a thread de trabalho só
                                                                              // acordava depois dos cliques e o teste falhava)
        if (models.get(sig) != null) {                                        // get: renova a posição no LRU
            shown = sig;
            return true;
        }
        requestedAt = System.nanoTime();
        queue.addFirst(new Job(gen, a.copy(), true, null));
        return false;
    }

    /** Pede uma miniatura (prioridade baixa); onMain recebe o Prepared na thread do GL. */
    public void requestThumb(Appearance a, Consumer<Composer.Prepared> onMain) {
        queue.addLast(new Job(0, a.copy(), false, onMain));
    }

    public boolean busy() { return wanted != null && !wanted.equals(shown); }

    /** Há quanto tempo o pedido principal atual espera (ms; 0 se pronto). */
    public double waitingMs() { return busy() ? (System.nanoTime() - requestedAt) / 1e6 : 0; }

    public synchronized CharacterModel model(String sig) { return models.get(sig); }

    public synchronized int cachedModels() { return models.size(); }

    private void loop() {
        while (running) {
            try {
                Job j = queue.takeFirst();
                if (j.main && j.gen != mainGen.get()) { synchronized (this) { discarded++; } continue; }
                long t0 = System.nanoTime();
                Composer.Prepared p = worker.prepare(j.a);
                double total = (System.nanoTime() - t0) / 1e6;
                synchronized (this) {
                    if (j.main) {
                        if (j.gen != mainGen.get()) { discarded++; continue; }  // o jogador já escolheu outra coisa
                        ready.add(() -> {
                            if (j.gen != mainGen.get()) { discarded++; return; }
                            long u0 = System.nanoTime();
                            CharacterModel m = uploadFn.apply(p);
                            lastUploadMs = (System.nanoTime() - u0) / 1e6;
                            lastComposeMs = p.rig.composeNanos / 1e6;
                            lastMeshMs = p.meshMs;
                            lastTotalMs = total + lastUploadMs;
                            models.put(j.a.signature(), m);
                            shown = j.a.signature();
                            completed++;
                        });
                    } else ready.add(() -> j.onMain.accept(p));
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                System.err.println("composição falhou: " + e.getMessage());
            }
        }
    }

    /** Thread do GL: aplica os resultados prontos (envio à GPU). Devolve quantos aplicou. */
    public int poll() {
        int n = 0;
        while (true) {
            Runnable r;
            synchronized (this) { r = ready.poll(); }
            if (r == null) return n;
            synchronized (this) { r.run(); }
            n++;
        }
    }

    /** Envia um Prepared avulso (miniaturas) — o chamador fecha com {@link Composer#release}. */
    public CharacterModel upload(Composer.Prepared p) { return uploadFn.apply(p); }

    /** Espera tudo terminar (roteiros de captura e testes). */
    public void drain(long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            poll();
            boolean idle;
            synchronized (this) { idle = queue.isEmpty() && ready.isEmpty() && !busy(); }
            if (idle) return;
            try { Thread.sleep(2); } catch (InterruptedException e) { return; }
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        thread.interrupt();
        for (CharacterModel m : models.values()) Composer.release(m);
        models.clear();
    }
}
