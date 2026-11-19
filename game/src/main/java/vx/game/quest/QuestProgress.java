package vx.game.quest;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rodada 10 — progresso de UMA missão. O estado ({@link #state()}) é derivado destes campos, então não existe combinação
 * "aceita e disponível" ou "concluída sem recompensa": o {@link QuestSystem} é o único que escreve aqui, e o save
 * recusa um arquivo cujos campos não fecham ({@link #check()}).
 */
public final class QuestProgress {
    public final QuestDefinition def;
    boolean accepted, declined, completed, rewardGiven;
    /** Índice do objetivo atual (0..n-1); n quando concluída. */
    int objective;
    /** Inimigos contados (identidades estáveis), na ordem em que caíram. */
    final LinkedHashSet<String> defeated = new LinkedHashSet<>();
    /**
     * Lembranças para as falas (não mudam o estado): {@code viu_objeto_antes}, {@code lutou_antes}, {@code caiu}.
     * Ordenadas para o save sair igual.
     */
    final TreeSet<String> facts = new TreeSet<>();
    /** Tempo de jogo (s) em que foi aceita e concluída; -1 = ainda não. */
    float acceptedAt = -1f, completedAt = -1f;

    QuestProgress(QuestDefinition def) { this.def = def; }

    public QuestState state() {
        if (completed) return QuestState.CONCLUIDA;
        if (!accepted) return QuestState.DISPONIVEL;
        if (def.completion() == QuestDefinition.Completion.FALAR_DOADOR && objective >= def.objectives().size() - 1)
            return QuestState.PRONTA_PARA_ENTREGAR;
        if (objective == 0 && defeated.isEmpty()) return QuestState.ACEITA;
        return QuestState.EM_ANDAMENTO;
    }

    /** Objetivo atual, ou null se não aceita ou concluída. */
    public QuestDefinition.Objective current() {
        return accepted && !completed && objective < def.objectives().size() ? def.objectives().get(objective) : null;
    }

    public int objectiveIndex() { return objective; }

    /** Quanto do objetivo {@code i} foi feito (inimigos contados, ou 0/1). */
    public int done(int i) {
        QuestDefinition.Objective o = def.objectives().get(i);
        if (i < objective || completed) return o.total();
        if (i > objective || !accepted) return 0;
        if (o.kind() == QuestDefinition.Kind.DERROTAR) {
            int n = 0;
            for (String t : o.targets()) if (defeated.contains(t)) n++;
            return n;
        }
        return 0;
    }

    public boolean accepted() { return accepted; }

    public boolean declined() { return declined; }

    public boolean completed() { return completed; }

    public boolean rewardGiven() { return rewardGiven; }

    public Set<String> defeated() { return Collections.unmodifiableSet(defeated); }

    public Set<String> facts() { return Collections.unmodifiableSet(facts); }

    public boolean fact(String f) { return facts.contains(f); }

    public float acceptedAt() { return acceptedAt; }

    public float completedAt() { return completedAt; }

    /**
     * Os campos fecham? null = sim; senão o motivo. Regras: objetivo dentro de 0..n; concluída ⇔ objetivo n, aceita e
     * recompensa dada; recompensa só com concluída; progresso só com aceita; inimigo contado tem de ser alvo da missão;
     * objetivo de derrotar já passado tem TODOS os alvos contados; o atual, se for de derrotar, não pode estar completo.
     */
    public String check() {
        int n = def.objectives().size();
        if (objective < 0 || objective > n) return "objetivo fora de 0.." + n;
        if (completed != (objective == n)) return "concluída e índice do objetivo não batem";
        if (completed && (!accepted || !rewardGiven)) return "concluída sem ser aceita ou sem recompensa";
        if (rewardGiven && !completed) return "recompensa dada sem concluir";
        if (!accepted && (objective > 0 || !defeated.isEmpty())) return "progresso sem a missão ter sido aceita";
        if (accepted && declined) return "aceita e recusada ao mesmo tempo";
        if (!def.allTargets().containsAll(defeated)) return "inimigo contado que não é alvo da missão";
        for (int i = 0; i < Math.min(objective, n); i++) {
            QuestDefinition.Objective o = def.objectives().get(i);
            if (o.kind() == QuestDefinition.Kind.DERROTAR && !defeated.containsAll(o.targets())) return "objetivo \"" + o.id() + "\" passado sem todos os alvos";
        }
        if (objective < n) {
            QuestDefinition.Objective o = def.objectives().get(objective);
            if (o.kind() == QuestDefinition.Kind.DERROTAR && defeated.containsAll(o.targets())) return "objetivo \"" + o.id() + "\" completo mas não avançou";
            for (int i = objective + 1; i < n; i++) {
                QuestDefinition.Objective later = def.objectives().get(i);
                if (later.kind() == QuestDefinition.Kind.DERROTAR)
                    for (String t : later.targets()) if (defeated.contains(t)) return "inimigo contado num objetivo que ainda não começou";
            }
        }
        return null;
    }
}
