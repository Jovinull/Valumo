package vx.game;

import org.joml.Vector3f;
import vx.viewer.CharacterModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rodada 09 — o confronto, em passos lógicos FIXOS de {@link GameLogic#TICK} s (nada depende da taxa de quadros nem da
 * janela; o desenho só interpola entre dois passos). Rodada 09.2: herói × um GRUPO de inimigos (encontro), cada um com
 * vida, estado, posição, dano e morte próprios; a mesma classe {@link Enemy} para todos (nada duplicado por inimigo).
 *
 * <p>Ordem de um passo (sempre a mesma, para um golpe não acertar num quadro e falhar no outro):
 * <ol>
 *   <li>pausa de impacto: se ativa, só ela corre (combatentes congelados; mundo e câmera seguem);</li>
 *   <li>herói: intenção → estado → corpo (movimento, avanço, esquiva) → trajetória da lâmina → dano em CADA inimigo que
 *       ela cruzar (no máximo uma vez por golpe);</li>
 *   <li>inimigos, na ordem do encontro: percepção → vez no grupo → estado → corpo → hitbox → dano/bloqueio no herói;</li>
 *   <li>separação dos corpos (herói × inimigos e inimigo × inimigo) e o resultado do confronto.</li>
 * </ol>
 *
 * <p>Coordenação leve do grupo (sem árvore de comportamento nem busca de caminho): no máximo {@code grupo.atacantes}
 * inimigos preparando/atacando ao mesmo tempo, com {@code grupo.intervalo} s entre o começo de duas preparações; quem não
 * tem a vez espera no anel em volta do herói; corpos de inimigos se afastam uns dos outros ({@code grupo.espaco}).
 */
public final class Combat {
    public enum Outcome { NONE, VICTORY, DEFEAT }

    /** Evento lógico (testes, relatório, vídeos): passo, tipo e texto. */
    public record Event(long tick, String kind, String text) { }

    /** Impacto para o desenho (acento visual): onde, quando (relógio do jogo), de que tipo e em que inimigo (null = herói). */
    public record Impact(float time, Vector3f at, String kind, Enemy who) {
        public Impact(float time, Vector3f at, String kind) { this(time, at, kind, null); }
    }

    public final CombatDef def;
    public final Collision collision;
    public final Hero hero;
    /** Rodada 09.2: os inimigos do encontro (ordem fixa = ordem do arquivo). */
    public final List<Enemy> enemies = new ArrayList<>();
    /** O primeiro inimigo do encontro (compatibilidade com o confronto 1 × 1 da rodada 09). */
    public Enemy enemy;
    public String encounter;
    public final List<Event> events = new ArrayList<>();
    /**
     * Impactos recentes (acento visual e som). A lista é da LÓGICA: entra em {@link #impact} e sai no passo, depois de
     * {@link #IMPACT_SHOW} s — o desenho só lê (antes ele mesmo podava a lista a cada quadro, e o diretor de som, que a
     * percorria por índice, podia perder ou repetir um contato conforme o instante do quadro: auditoria de engenharia).
     */
    public final List<Impact> impacts = new ArrayList<>();
    /** Total de impactos desde o começo (nunca diminui): o som toca os que ainda não viu, sem depender do tamanho da lista. */
    public long impactTotal;
    public static final float IMPACT_SHOW = 0.2f;
    public float hitstop;
    public long ticks;
    public Outcome outcome = Outcome.NONE;
    public float clock;
    /** Liga o registro de eventos no console (roteiros). */
    public boolean echo;
    /** Medição (ns acumulados, passos medidos): herói (estado + corpo + golpe), inimigos (IA + corpo + golpe), só detecção. */
    public long heroNanos, enemyNanos, hitNanos, measuredTicks;
    /** Rodada 09.2 — último começo de preparação de um inimigo (relógio do confronto). */
    private float lastAttackStart = -99f;
    private final Path assets;
    private final Map<String, CharacterModel> rigs = new HashMap<>();

    public Combat(CombatDef def, Collision collision, Path assets) { this(def, collision, assets, def.encounterDefault); }

    public Combat(CombatDef def, Collision collision, Path assets, String encounter) {
        this.def = def;
        this.collision = collision;
        this.assets = assets;
        hero = new Hero(def, CharacterModel.loadSkeleton(assets.resolve(def.heroKit)));
        setEncounter(encounter);
    }

    /** Troca o encontro (inimigos novos em casa). */
    public void setEncounter(String name) {
        encounter = def.encounters.containsKey(name) ? name : def.encounterDefault;
        enemies.clear();
        int id = 0;
        for (CombatDef.Spawn s : def.encounter(encounter)) {
            CombatDef ed = def.forSpawn(s);
            CharacterModel skel = rigs.computeIfAbsent(ed.enemyRig, r -> CharacterModel.loadSkeleton(assets.resolve(r)));
            enemies.add(new Enemy(ed, skel, collision, id++));
        }
        enemy = enemies.get(0);
        outcome = Outcome.NONE;
        lastAttackStart = -99f;
    }

    /** Recomeça o confronto: inimigos em casa, herói inteiro; reposiciona o herói se ele caiu. */
    public void restart(Player p) {
        boolean wasDead = !hero.alive();
        hero.reset();
        for (Enemy e : enemies) e.reset(collision);
        hitstop = 0f;
        outcome = Outcome.NONE;
        impacts.clear();
        lastAttackStart = -99f;
        if (wasDead) p.place(def.restartX, def.restartZ, (float) Math.toRadians(def.restartYawDeg), collision);
        log("recomeço");
    }

    /** Algum inimigo vivo engajado. */
    public boolean engaged() {
        for (Enemy e : enemies) if (e.alive() && e.engaged()) return true;
        return false;
    }

    public int alive() {
        int n = 0;
        for (Enemy e : enemies) if (e.alive()) n++;
        return n;
    }

    /** Chefe do encontro (barra grande) ou null. */
    public Enemy boss() {
        for (Enemy e : enemies) if (e.boss()) return e;
        return null;
    }

    /** Herói dentro da arena (dica de controles, barra do inimigo). */
    public boolean inArena(Player p) { return Math.hypot(p.x - def.arenaX, p.z - def.arenaZ) <= def.arenaR + 0.5f; }

    void tick(float h, Hero.Intent in, Player p) {
        ticks++;
        clock += h;
        while (!impacts.isEmpty() && clock - impacts.getFirst().time() > IMPACT_SHOW) impacts.removeFirst();   // em ordem de tempo
        if (hitstop > 0f) {
            hitstop = Math.max(0f, hitstop - h);
            hero.pressDuringPause(in, p);                                // o aperto dado na pausa de impacto não se perde
            hero.shown.copyFrom(hero.layer);
            hero.pLegW = hero.legW;
            hero.pLocoDir = hero.locoDir;
            for (Enemy e : enemies) e.snapshot();
            return;
        }
        long t0 = System.nanoTime();
        hero.tick(h, in, p, this);
        long t1 = System.nanoTime();
        for (Enemy e : enemies) e.tick(h, hero, p, this);
        heroNanos += t1 - t0;
        enemyNanos += System.nanoTime() - t1;
        measuredTicks++;
        separate(p);
        if (outcome == Outcome.NONE) {
            boolean allDead = !enemies.isEmpty();
            float lastDeath = 99f;
            for (Enemy e : enemies) {
                if (e.alive()) { allDead = false; break; }
                lastDeath = Math.min(lastDeath, e.deadT);
            }
            if (allDead && lastDeath >= 1.2f) { outcome = Outcome.VICTORY; log("vitória"); }
            if (!hero.alive() && hero.deadT >= 1.6f) { outcome = Outcome.DEFEAT; log("derrota"); }
        }
    }

    // ------------------------------------------------------------------ grupo (rodada 09.2)

    /**
     * Vez no grupo: só os {@code grupo.atacantes} inimigos engajados mais perto do herói (quem já está preparando/atacando
     * conta primeiro) podem chegar ao alcance; os outros esperam no anel. Ordem por distância, não por criação.
     */
    boolean hasTurn(Enemy me) {
        if (def.maxAttackers >= enemies.size()) return true;
        Player p = player;
        float md = (float) Math.hypot(me.x - p.x, me.z - p.z);
        boolean meBusy = me.state == Enemy.State.WINDUP || me.state == Enemy.State.ATTACK;
        if (meBusy) return true;
        int ahead = 0;
        for (Enemy e : enemies) {
            if (e == me || !e.alive() || !e.engaged()) continue;
            boolean busy = e.state == Enemy.State.WINDUP || e.state == Enemy.State.ATTACK;
            float d = (float) Math.hypot(e.x - p.x, e.z - p.z);
            if (busy || d < md || (d == md && e.id < me.id)) ahead++;
        }
        return ahead < def.maxAttackers;
    }

    /** O inimigo pode começar a preparar um ataque agora (tem a vez, há vaga de atacante e passou o intervalo). */
    boolean mayAttack(Enemy me) {
        if (!hasTurn(me)) return false;
        int busy = 0;
        for (Enemy e : enemies)
            if (e != me && e.alive() && (e.state == Enemy.State.WINDUP || e.state == Enemy.State.ATTACK)) busy++;
        return busy < def.maxAttackers && clock - lastAttackStart >= def.attackGap;
    }

    void attackStarted(Enemy e) { lastAttackStart = clock; }

    /** Empurrão de separação (direção unitária × força 0–1) dos outros inimigos vivos perto demais. */
    float[] separation(Enemy me) {
        float sx = 0f, sz = 0f;
        if (def.spacing <= 0f) return new float[]{0f, 0f};
        for (Enemy e : enemies) {
            if (e == me || !e.alive()) continue;
            float dx = me.x - e.x, dz = me.z - e.z, d = (float) Math.hypot(dx, dz), want = def.spacing * Math.max(me.scale, e.scale);
            if (d >= want || d < 1e-4f) continue;
            float k = (want - d) / want;
            sx += dx / d * k * 1.4f;
            sz += dz / d * k * 1.4f;
        }
        return new float[]{sx, sz};
    }

    private void separate(Player p) {
        for (Enemy e : enemies) {
            if (!e.alive()) continue;
            float dx = e.x - p.x, dz = e.z - p.z, d = (float) Math.hypot(dx, dz), min = Player.RADIUS + e.def.enemyRadius;
            if (d >= min || d < 1e-5f) continue;
            float push = min - d, nx = dx / d, nz = dz / d, heroShare = 0.2f;
            float[] q = {e.x + nx * push * 0.8f, e.z + nz * push * 0.8f};
            collision.resolve(q, e.def.enemyRadius, e.y, Enemy.HEIGHT, Enemy.STEP);
            // o inimigo só cede para onde ele mesmo poderia andar (chão, degrau, coleira); senão quem cede é o herói —
            // antes dava para empurrar um fungo para fora da coleira, onde ele não conseguia mais se mexer
            if (e.allowed(q[0], q[1], collision)) { e.x = q[0]; e.z = q[1]; } else heroShare = 1f;
            float[] r = {p.x - nx * push * heroShare, p.z - nz * push * heroShare};
            collision.resolve(r, Player.RADIUS, p.y, Player.HEIGHT, Player.STEP);
            if (collision.walkable(r[0], r[1])) { p.x = r[0]; p.z = r[1]; }
        }
        for (int i = 0; i < enemies.size(); i++) {                         // inimigo × inimigo: os dois cedem igual
            Enemy a = enemies.get(i);
            if (!a.alive()) continue;
            for (int j = i + 1; j < enemies.size(); j++) {
                Enemy b = enemies.get(j);
                if (!b.alive()) continue;
                float dx = b.x - a.x, dz = b.z - a.z, d = (float) Math.hypot(dx, dz), min = a.def.enemyRadius + b.def.enemyRadius;
                if (d >= min || d < 1e-5f) continue;
                float push = (min - d) / 2f, nx = dx / d, nz = dz / d;
                float[] qa = {a.x - nx * push, a.z - nz * push}, qb = {b.x + nx * push, b.z + nz * push};
                collision.resolve(qa, a.def.enemyRadius, a.y, Enemy.HEIGHT, Enemy.STEP);
                collision.resolve(qb, b.def.enemyRadius, b.y, Enemy.HEIGHT, Enemy.STEP);
                if (a.allowed(qa[0], qa[1], collision)) { a.x = qa[0]; a.z = qa[1]; }
                if (b.allowed(qb[0], qb[1], collision)) { b.x = qb[0]; b.z = qb[1]; }
            }
        }
    }

    // ------------------------------------------------------------------ dano (chamado por Hero e Enemy)

    void impact(Impact im) {
        impacts.add(im);
        impactTotal++;
    }

    void heroHits(Hero h, Enemy e, int damage, Vector3f at) {
        boolean died = e.receive(damage, player.x, player.z);
        hitstop = Math.max(hitstop, def.pauseHit);
        impact(new Impact(clock, at, "acerto", e));
        log(String.format("acerto: %s −%d em %s#%d (vida %d)%s", h.attack == null ? "?" : h.attack.move(), damage, e.name(), e.id, e.hp,
                died ? ", derrotado" : ""));
    }

    void enemyHits(Enemy e, Hero h, Vector3f at) {
        Player p = player;
        Hero.Outcome o = h.receive(e.def.enemyDamage, e.x, e.z, p, this);
        switch (o) {
            case BLOCKED -> {
                e.bounce(p.x, p.z);
                hitstop = Math.max(hitstop, def.pauseBlock);
                impact(new Impact(clock, at, "defesa"));
                log("defesa: cabeçada bloqueada pelo escudo");
            }
            case HURT, KILLED -> {
                hitstop = Math.max(hitstop, def.pauseHurt);
                impact(new Impact(clock, at, "dano"));
                log(String.format("dano no herói −%d (vida %d)%s", e.def.enemyDamage, h.hp, o == Hero.Outcome.KILLED ? ", caiu" : ""));
            }
            case DODGED -> log("esquivou: cabeçada evitada na janela da esquiva");
            case IGNORED -> log("cabeçada sem efeito (invulnerável)");
        }
    }

    /** O Player do confronto (o GameLogic liga). */
    Player player;

    /** Eventos guardados: os mais recentes (a lista crescia a sessão inteira — fechamento do QA, sessão longa). */
    static final int EVENTS_KEPT = 4096;

    public void log(String text) {
        events.add(new Event(ticks, text.split(":")[0], text));
        if (events.size() > 2 * EVENTS_KEPT) events.subList(0, events.size() - EVENTS_KEPT).clear();
        if (echo) System.out.printf("combate[%d %.3fs]: %s%n", ticks, ticks * GameLogic.TICK, text);
    }
}
