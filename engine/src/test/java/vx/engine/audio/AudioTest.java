package vx.engine.audio;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Rodada 09.4 — áudio sem dispositivo: laço exato, regras de reprodução, catálogo real e mixagem por loopback. */
class AudioTest {
    static final Path ASSETS = Path.of("assets");

    // ------------------------------------------------------------------ fonte sintética: amostra = índice do quadro

    static class Ramp implements PcmSource {
        final int n;
        int pos;
        Ramp(int n) { this.n = n; }
        public int channels() { return 1; }
        public int rate() { return 48000; }
        public long frames() { return n; }
        public void seek(long f) { pos = (int) f; }
        public int read(short[] out, int max) {
            int k = Math.min(max, n - pos);
            for (int i = 0; i < k; i++) out[i] = (short) (pos + i);
            pos += k;
            return k;
        }
        public void close() { }
    }

    @Test
    void loopIsSampleExactAcrossManyWraps() {
        LoopingReader r = new LoopingReader(new Ramp(1000), 300, 900, true);
        short[] buf = new short[137];                                     // pedaço que não divide o laço
        List<Integer> seq = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            int got = r.read(buf, buf.length);
            assertEquals(buf.length, got, "laço nunca encurta a leitura");
            for (int i = 0; i < got; i++) seq.add((int) buf[i]);
        }
        assertEquals(0, seq.getFirst());
        for (int i = 1; i < seq.size(); i++) {
            int a = seq.get(i - 1), b = seq.get(i);
            assertTrue(b == a + 1 || (a == 899 && b == 300), "salto inesperado " + a + " → " + b + " na posição " + i);
        }
        assertTrue(seq.stream().filter(v -> v == 899).count() > 5, "deu várias voltas");
        assertFalse(seq.contains(900), "nada depois do fim do laço toca");
    }

    @Test
    void withoutLoopTheReaderEnds() {
        LoopingReader r = new LoopingReader(new Ramp(500), 100, 500, false);
        short[] buf = new short[300];
        assertEquals(300, r.read(buf, 300));
        assertEquals(200, r.read(buf, 300));
        assertEquals(0, r.read(buf, 300));
    }

    // ------------------------------------------------------------------ saída falsa (registra chamadas)

    static final class FakeOut implements AudioOut {
        int next = 1;
        final Map<Integer, float[]> voices = new HashMap<>();             // voz → {buffer, ganho, tom}
        final List<Integer> plays = new ArrayList<>();
        final Map<Integer, Float> streams = new HashMap<>();
        int stoppedStreams;
        public int load(Pcm pcm) { return next++; }
        public void unload(int b) { }
        public int play(int b, float g, float p) { int v = next++; voices.put(v, new float[]{b, g, p}); plays.add(b); return v; }
        public boolean playing(int v) { return voices.containsKey(v); }
        public void gain(int v, float g) { if (voices.containsKey(v)) voices.get(v)[1] = g; }
        public void stop(int v) { voices.remove(v); }
        public int stream(LoopingReader r, float g) { int s = next++; streams.put(s, g); r.close(); return s; }
        public void streamGain(int s, float g) { if (streams.containsKey(s)) streams.put(s, g); }
        public void stopStream(int s) { streams.remove(s); stoppedStreams++; }
        final Map<Integer, float[]> positions = new HashMap<>();
        float[] listener;
        public void streamPosition(int s, float x, float y, float z, float ref, float max) { positions.put(s, new float[]{x, y, z, ref, max}); }
        public void listener(float x, float y, float z, float fx, float fy, float fz) { listener = new float[]{x, y, z, fx, fy, fz}; }
        public void update() { }
        public String name() { return "falsa"; }
        public void close() { }
    }

    static SoundSystem system(FakeOut out, long seed) throws Exception {
        return new SoundSystem(SoundCatalog.load(ASSETS.resolve("audio/sons.json")), ASSETS.resolve("audio"), out, seed);
    }

    @Test
    void realCatalogLoadsWithoutProblems() throws Exception {
        SoundSystem s = system(new FakeOut(), 1);
        assertEquals(List.of(), s.problems(), "todo arquivo do catálogo existe e é WAV PCM 16 bits mono");
    }

    @Test
    void variationNeverRepeatsBackToBackAndIsDeterministic() throws Exception {
        List<Integer> a = new ArrayList<>(), b = new ArrayList<>();
        for (List<Integer> seq : List.of(a, b)) {
            SoundSystem s = system(new FakeOut(), 42);
            for (int i = 0; i < 60; i++) {
                assertTrue(s.event("passo.grama.andar"));
                seq.add(s.lastVariation);
                s.update(0.23f);
            }
        }
        assertEquals(a, b, "mesma semente → mesma sequência (roteiros reproduzíveis)");
        for (int i = 1; i < a.size(); i++) assertNotEquals(a.get(i - 1), a.get(i), "variação repetida em seguida em " + i);
        for (int v = 0; v < 4; v++) assertTrue(a.contains(v), "variação " + v + " nunca tocou");
    }

    @Test
    void pitchAndGainStayInsideTheCatalogRange() throws Exception {
        SoundSystem s = system(new FakeOut(), 7);
        for (int i = 0; i < 200; i++) {
            s.event("passo.terra.correr");
            float semis = (float) (12 * Math.log(s.lastPitch) / Math.log(2));
            assertTrue(Math.abs(semis) <= 0.5f + 1e-4f, "tom " + semis);
            float db = (float) (20 * Math.log10(s.lastGain));
            assertTrue(db >= -3 - 1.5f - 1e-3f && db <= -3 + 1.5f + 1e-3f, "ganho " + db);
            s.update(0.2f);
        }
        s.event("ui.confirmar");
        assertEquals(1f, s.lastPitch, 0f, "interface sem variação de tom (fica em Ré maior)");
    }

    @Test
    void duplicateTriggerInTheSameFrameIsSuppressed() throws Exception {
        FakeOut out = new FakeOut();
        SoundSystem s = system(out, 3);
        assertTrue(s.event("ui.selecionar"));
        assertFalse(s.event("ui.selecionar"), "mesmo quadro: suprimido");
        s.update(0.05f);
        assertTrue(s.event("ui.selecionar"), "depois do intervalo mínimo toca");
        assertEquals(2, out.plays.size());
        assertEquals(1, s.suppressedInterval);
    }

    @Test
    void perEventVoiceLimitStealsTheOldest() throws Exception {
        FakeOut out = new FakeOut();
        SoundSystem s = system(out, 5);
        for (int i = 0; i < 6; i++) { s.event("passo.grama.correr"); s.update(0.1f); }
        assertEquals(3, s.activeVoices(), "max_vozes = 3");
        assertEquals(3, s.stolen);
    }

    @Test
    void globalLimitKeepsHigherPriority() throws Exception {
        FakeOut out = new FakeOut();
        SoundSystem s = system(out, 9);
        // enche com sons de interface (prioridade 3) até o limite global, alternando eventos para não bater no limite próprio
        String[] ui = {"ui.selecionar", "ui.confirmar", "ui.cancelar", "ui.abrir", "ui.salvar"};
        int k = 0;
        while (s.activeVoices() < SoundSystem.MAX_VOICES && k < 500) {
            for (String e : ui) s.event(e);
            s.update(0.25f);
            k++;
            if (s.activeVoices() >= 7) break;                            // limites próprios somam 6; o resto é a prova abaixo
        }
        // força o limite global com passos (prioridade 1): nenhum passo pode roubar voz de interface
        int before = (int) out.voices.values().stream().filter(v -> v[0] > 0).count();
        for (int i = 0; i < 100; i++) { s.event(i % 2 == 0 ? "passo.grama.andar" : "passo.terra.andar"); s.update(0.09f); }
        assertTrue(s.activeVoices() <= SoundSystem.MAX_VOICES);
        assertTrue(before > 0);
    }

    @Test
    void busVolumeScalesPlayingVoicesAndMusicDoesNotRestart() throws Exception {
        FakeOut out = new FakeOut();
        SoundSystem s = system(out, 11);
        s.event("ui.confirmar");
        float g0 = out.voices.values().iterator().next()[1];
        s.volume("interface", 0.375f);                                   // metade de 0,75
        float g1 = out.voices.values().iterator().next()[1];
        assertEquals(g0 / 2, g1, 1e-5f);
        s.music("vila_a");
        int streams = out.streams.size();
        s.music("vila_a");
        assertEquals(streams, out.streams.size(), "a mesma música pedida de novo não reinicia");
        for (int i = 0; i < 100; i++) s.update(0.02f);                    // 2 s: entrou por inteiro
        float full = out.streams.values().iterator().next();
        assertTrue(full > 0.5f, "música entrou: " + full);
        s.music("vila_b");                                                // transição: a A sai em 1,2 s
        for (int i = 0; i < 80; i++) s.update(0.02f);
        assertEquals(1, out.stoppedStreams, "a anterior parou depois da transição");
        s.duck(0.5f);
        for (int i = 0; i < 100; i++) s.update(0.02f);
        float ducked = out.streams.values().iterator().next();
        assertEquals(full * 0.5f, ducked, 0.02f, "abafamento da pausa");
    }

    @Test
    void missingFileIsReportedAndDoesNotCrash() throws Exception {
        Path dir = Files.createTempDirectory("vx-audio");
        Files.writeString(dir.resolve("sons.json"), """
                {"contrato":"valumo.sons","versao":1,"barramentos":{"efeitos":1},
                 "eventos":{"x":{"barramento":"efeitos","arquivos":["nao-existe.wav"]}}}""");
        try (SoundSystem s = new SoundSystem(SoundCatalog.load(dir.resolve("sons.json")), dir, new FakeOut(), 1)) {
            assertEquals(1, s.problems().size());
            assertFalse(s.event("x"));
            assertFalse(s.event("evento-que-nao-existe"));
            s.music("tambem-nao");
            assertEquals(3, s.problems().size());
        }
    }

    // ------------------------------------------------------------------ auditoria de engenharia: mudo barato e falha segura

    /** Saída que não consegue tocar fluxos (como a muda antes da correção): conta quantas vezes o sistema tentou abrir um. */
    static final class NoStreamOut implements AudioOut {
        final boolean claims;
        int opened;
        NoStreamOut(boolean claimsStreams) { claims = claimsStreams; }
        public int load(Pcm pcm) { return 0; }
        public void unload(int b) { }
        public int play(int b, float g, float p) { return 0; }
        public boolean playing(int v) { return false; }
        public void gain(int v, float g) { }
        public void stop(int v) { }
        public boolean streams() { return claims; }
        public int stream(LoopingReader r, float g) { opened++; r.close(); return 0; }
        public void streamGain(int s, float g) { }
        public void stopStream(int s) { }
        public void streamPosition(int s, float x, float y, float z, float ref, float max) { }
        public void listener(float x, float y, float z, float fx, float fy, float fz) { }
        public void update() { }
        public String name() { return "sem fluxos"; }
        public void close() { }
    }

    /** O jogo pede música e ambientes a CADA quadro (GameAudio.tick): 600 quadros = 10 s de jogo. */
    private static void tenSecondsOfGame(SoundSystem s) {
        for (int f = 0; f < 600; f++) {
            s.music("vila_a");
            s.ambient("vento", null);
            s.ambient("lago", new float[]{4, -0.4f, 6});
            s.update(1f / 60f);
        }
    }

    @Test
    void aMuteOutputNeverOpensMusicOrAmbienceFiles() throws Exception {
        NoStreamOut mute = new NoStreamOut(false);
        try (SoundSystem s = new SoundSystem(SoundCatalog.load(ASSETS.resolve("audio/sons.json")), ASSETS.resolve("audio"), mute, 1)) {
            tenSecondsOfGame(s);
            assertEquals(0, mute.opened, "saída muda: nenhum OGG aberto (antes: 3 arquivos decodificados por quadro)");
            assertNull(s.currentMusic());
            assertEquals(List.of(), s.problems(), "ficar mudo não é problema");
        }
        try (AudioOut silent = new SilentOut("teste")) {
            assertFalse(silent.streams(), "a saída muda do jogo declara que não toca fluxos");
        }
    }

    @Test
    void aStreamThatFailsToStartIsNotRetriedEveryFrame() throws Exception {
        NoStreamOut broken = new NoStreamOut(true);                        // diz que toca, mas não consegue (sem fonte livre…)
        try (SoundSystem s = new SoundSystem(SoundCatalog.load(ASSETS.resolve("audio/sons.json")), ASSETS.resolve("audio"), broken, 1)) {
            tenSecondsOfGame(s);
            assertEquals(3, broken.opened, "uma tentativa por pedido (música, vento, lago), não uma por quadro");
            s.music("vila_b");                                             // um pedido DIFERENTE tenta de novo
            s.music("vila_b");
            assertEquals(4, broken.opened);
            s.stopAmbient("lago");                                         // parar e pedir de novo também
            s.ambient("lago", null);
            s.ambient("lago", null);
            assertEquals(5, broken.opened);
        }
    }

    @Test
    void aStoppedAmbienceAskedAgainComesBackInsteadOfFadingAway() throws Exception {
        FakeOut out = new FakeOut();
        try (SoundSystem s = system(out, 3)) {
            s.ambient("vento", null);
            for (int i = 0; i < 100; i++) s.update(0.05f);
            float full = out.streams.values().iterator().next();
            s.stopAmbient("vento");
            for (int i = 0; i < 10; i++) s.update(0.05f);                  // meio da saída
            s.ambient("vento", null);                                     // o jogo pede de novo
            for (int i = 0; i < 100; i++) s.update(0.05f);
            assertEquals(1, out.streams.size(), "o mesmo fluxo, sem reabrir o arquivo");
            assertEquals(full, out.streams.values().iterator().next(), 1e-5f, "voltou ao nível cheio");
        }
    }

    @Test
    void aMalformedSoundFileIsAProblemNeverACrash() throws Exception {
        Path dir = Files.createTempDirectory("vx-audio");
        java.nio.ByteBuffer cut = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        cut.putInt(0x46464952).putInt(12).putInt(0x45564157).putInt(0x20746d66).putInt(16);    // RIFF/WAVE + "fmt " sem corpo
        Files.write(dir.resolve("cortado.wav"), cut.array());
        Files.writeString(dir.resolve("sons.json"), """
                {"contrato":"valumo.sons","versao":1,"barramentos":{"efeitos":1},
                 "eventos":{"x":{"barramento":"efeitos","arquivos":["cortado.wav"]},
                            "zero":{"barramento":"efeitos","max_vozes":0,"arquivos":["cortado.wav"]}}}""");
        try (SoundSystem s = new SoundSystem(SoundCatalog.load(dir.resolve("sons.json")), dir, new FakeOut(), 1)) {
            assertEquals(2, s.problems().size(), "arquivo truncado vira problema: " + s.problems());
            assertFalse(s.event("x"));
            assertFalse(s.event("zero"), "limite de vozes 0 não derruba o jogo");
        }
    }

    @Test
    void aSourceThatDriesUpDoesNotHangTheLoop() {
        PcmSource dry = new Ramp(1000) {
            @Override public int read(short[] out, int max) { return pos >= 500 ? 0 : super.read(out, Math.min(max, 500 - pos)); }
            @Override public void seek(long f) { pos = 700; }                // volta a um trecho sem dados (arquivo corrompido)
        };
        LoopingReader r = new LoopingReader(dry, 300, 900, true);
        short[] buf = new short[2000];
        int got = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> r.read(buf, 2000));
        assertEquals(500, got, "entrega o que havia e devolve o controle (antes: laço infinito na thread do jogo)");
    }

    @Test
    void ambientLayersLoopFadeInAndOnlyTheLakeIsPlaced() throws Exception {
        FakeOut out = new FakeOut();
        SoundSystem s = system(out, 13);
        s.ambient("vento", null);
        s.ambient("lago", new float[]{4, -0.4f, 6});
        s.ambient("vento", null);                                          // pedir de novo não reinicia
        assertEquals(2, out.streams.size());
        assertEquals(1, out.positions.size(), "só o lago tem posição (vento é cama)");
        assertArrayEquals(new float[]{4, -0.4f, 6, 2.5f, 14}, out.positions.values().iterator().next(), 1e-6f);
        s.update(0.5f);
        float early = out.streams.values().stream().max(Float::compare).orElse(0f);
        for (int i = 0; i < 100; i++) s.update(0.05f);
        float full = out.streams.values().stream().max(Float::compare).orElse(0f);
        assertTrue(early > 0 && early < full, "entra em rampa: " + early + " → " + full);
        s.volume("ambiente", 0.35f);
        float half = out.streams.values().stream().max(Float::compare).orElse(0f);
        assertEquals(full / 2, half, 1e-4f, "barramento de ambiente");
        s.stopAmbient("lago");
        for (int i = 0; i < 60; i++) s.update(0.05f);
        assertEquals(1, out.streams.size(), "o lago saiu em rampa e parou");
        assertEquals(List.of(), s.problems());
    }

    // ------------------------------------------------------------------ arquivos reais

    @Test
    void everyCatalogFileHasTheContractFormatAndDuration() throws Exception {
        SoundCatalog c = SoundCatalog.load(ASSETS.resolve("audio/sons.json"));
        for (SoundCatalog.Event e : c.events.values())
            for (String f : e.files()) {
                Pcm p = Pcm.readWav(ASSETS.resolve("audio").resolve(f));
                assertEquals(48000, p.rate(), f);
                assertEquals(1, p.channels(), f + ": efeito é mono (pronto para posição no OpenAL)");
                assertTrue(p.seconds() >= e.minDur() && p.seconds() <= e.maxDur(),
                        f + ": " + p.seconds() + " s fora de [" + e.minDur() + ", " + e.maxDur() + "]");
                int peak = 0;
                for (short v : p.data()) peak = Math.max(peak, Math.abs(v));
                assertTrue(peak < 32000, f + ": pico " + peak + " (saturação)");
                assertTrue(peak > 1000, f + ": quase silêncio");
            }
        for (SoundCatalog.Ambient a : c.ambient.values())
            try (VorbisSource v = new VorbisSource(ASSETS.resolve("audio").resolve(a.file()))) {
                assertEquals(48000, v.rate(), a.file());
                assertEquals(a.positional() ? 1 : 2, v.channels(), a.file() + ": posicional = mono; cama = estéreo");
                assertTrue(v.frames() >= 48000 * 8, a.file() + ": laço curto demais (repetição perceptível)");
            }
        for (SoundCatalog.Music m : c.music.values()) {
            try (VorbisSource v = new VorbisSource(ASSETS.resolve("audio").resolve(m.file()))) {
                assertEquals(48000, v.rate(), m.file());
                assertEquals(2, v.channels(), m.file());
                assertTrue(m.loopStart() > 0 && m.loopEnd() <= v.frames() && m.loopEnd() - m.loopStart() > 48000 * 20,
                        m.file() + ": laço " + m.loopStart() + "–" + m.loopEnd() + " de " + v.frames());
            }
        }
    }

    @Test
    void vorbisSeekIsSampleExactAtTheLoopStart() throws Exception {
        SoundCatalog c = SoundCatalog.load(ASSETS.resolve("audio/sons.json"));
        for (SoundCatalog.Music m : c.music.values()) {
            Path f = ASSETS.resolve("audio").resolve(m.file());
            short[] linear = new short[4096 * 2], seeked = new short[4096 * 2];
            try (VorbisSource v = new VorbisSource(f)) {                 // leitura contínua até o início do laço
                short[] skip = new short[48000 * 2];
                long left = m.loopStart();
                while (left > 0) left -= v.read(skip, (int) Math.min(48000, left));
                assertEquals(4096, v.read(linear, 4096));
            }
            try (VorbisSource v = new VorbisSource(f)) {                 // salto direto
                v.seek(m.loopStart());
                assertEquals(4096, v.read(seeked, 4096));
            }
            int maxDiff = 0;
            for (int i = 0; i < linear.length; i++) maxDiff = Math.max(maxDiff, Math.abs(linear[i] - seeked[i]));
            assertTrue(maxDiff <= 2, m.id() + ": busca no laço difere da leitura contínua em " + maxDiff);
        }
    }

    // ------------------------------------------------------------------ mixagem real (OpenAL Soft em loopback)

    /** Senoide mono de 220 Hz (amplitude 8000), {@code seconds} de duração. */
    static PcmSource sine(int seconds) {
        return new PcmSource() {
            int pos;
            public int channels() { return 1; }
            public int rate() { return 48000; }
            public long frames() { return 48000L * seconds; }
            public void seek(long f) { pos = (int) f; }
            public int read(short[] out, int max) {
                int k = (int) Math.min(max, frames() - pos);
                for (int i = 0; i < k; i++) out[i] = (short) (8000 * Math.sin(2 * Math.PI * 220 * (pos + i) / 48000.0));
                pos += k;
                return k;
            }
            public void close() { }
        };
    }

    @Test
    void musicSurvivesTheGameThreadStallingForThreeSeconds() {
        // fechamento do QA: montar o criador de dentro do jogo deixa a thread do jogo ~2 s sem quadro. Com a fila fixa de
        // 1 s a música e o ambiente sumiam (1,9 s de silêncio medidos em tempo real); a fila cresce até 4 s
        OpenAlOut al = OpenAlOut.loopback(32);
        Assumptions.assumeTrue(al != null, "OpenAL loopback indisponível nesta máquina");
        try {
            int st = al.stream(new LoopingReader(sine(10), 0, 48000L * 10, true), 1f);
            assertTrue(st > 0);
            for (int i = 0; i < 30; i++) { al.update(); al.render(800); }    // meio segundo de quadros: a fila cresce
            short[] stall = al.render(48000 * 3);                             // 3 s sem nenhum quadro
            int tailPeak = 0;
            for (int i = stall.length - 2 * 4800; i < stall.length; i++) tailPeak = Math.max(tailPeak, Math.abs(stall[i]));
            assertTrue(tailPeak > 1000, "a música continua até o fim do travamento: pico dos últimos 0,1 s " + tailPeak);
            for (int i = 0; i < 10; i++) { al.update(); al.render(800); }    // e segue depois dele
            short[] after = al.render(4800);
            int peak = 0;
            for (short v : after) peak = Math.max(peak, Math.abs(v));
            assertTrue(peak > 1000, "depois do travamento: pico " + peak);
        } finally {
            al.close();
        }
    }

    @Test
    void openAlLoopbackMixesEventsAndReleasesEverything() throws Exception {
        OpenAlOut al = OpenAlOut.loopback(32);
        Assumptions.assumeTrue(al != null, "OpenAL loopback indisponível nesta máquina");
        SoundSystem s = new SoundSystem(SoundCatalog.load(ASSETS.resolve("audio/sons.json")), ASSETS.resolve("audio"), al, 1);
        try {
            assertEquals(List.of(), s.problems());
            assertTrue(al.loadedBuffers() > 20);
            short[] silence = al.render(4800);
            for (short v : silence) assertTrue(Math.abs(v) <= 2, "nada toca antes do evento (só o dither de 16 bits do OpenAL Soft, ±1 LSB): " + v);
            assertTrue(s.event("ui.confirmar"));
            s.update(0.01f);
            short[] mix = al.render(48000 / 4);
            int peak = 0;
            for (short v : mix) peak = Math.max(peak, Math.abs(v));
            assertTrue(peak > 2000 && peak < 32767, "o evento soou sem saturar: pico " + peak);
            for (int i = 0; i < 40; i++) { al.render(4800); s.update(0.1f); }
            assertEquals(0, s.activeVoices(), "vozes recolhidas depois de terminar");
            assertEquals(0, al.busySources(), "fontes devolvidas ao pool");
            // posição de verdade: o mesmo lago perto e longe do ouvinte
            s.listener(0, 0, 0, 0, 0, -1);
            double[] rms = new double[2];
            float[][] places = {{0, 0, -2}, {0, 0, -12}};
            for (int k = 0; k < 2; k++) {
                s.ambient("lago", places[k]);
                for (int i = 0; i < 30; i++) { s.update(0.1f); al.render(4800); }      // entrou por inteiro
                short[] mixk = al.render(48000);
                double acc = 0;
                for (short v : mixk) acc += v * (double) v;
                rms[k] = Math.sqrt(acc / mixk.length);
                s.stopAmbient("lago");
                for (int i = 0; i < 30; i++) { s.update(0.1f); al.render(4800); }
            }
            assertTrue(rms[1] < rms[0] * 0.5, "longe soa bem mais baixo que perto: " + rms[0] + " × " + rms[1]);
            assertEquals(0, al.busySources(), "fluxos devolvidos ao pool");
        } finally {
            s.close();
        }
    }
}
