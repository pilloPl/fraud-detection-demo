package io.pillopl.fraud_detencion;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

record RulesStats(Map<String, RuleStats> stats) {

    static RulesStats empty() {
        return new RulesStats(new HashMap<>());
    }

    void update(RuleExecution ruleExecution) {
        stats.compute(ruleExecution.id(), (id, stat) -> {
            if (stat == null) {
                stat = new RuleStats(ruleExecution.id());
            }
            return stat.update(ruleExecution);
        });
    }

    Optional<RuleStats> get(String id) {
        return Optional.ofNullable(stats.get(id));
    }
}

class RuleStats {
    String id;
    int executions = 0;
    long totalDurationMillis = 0;
    int totalCost = 0;
    int totalScore = 0;

    public RuleStats(String id) {
        this.id  = id;
    }

    RuleStats update(RuleExecution execution) {
        executions++;
        totalDurationMillis += execution.duration().toMillis();
        totalCost += execution.cost();
        totalScore += execution.score().score();
        return this;
    }

    double avgDurationMillis() {
        return executions == 0 ? 0 : (double) totalDurationMillis / executions;
    }

    double avgCost() {
        return executions == 0 ? 0 : (double) totalCost / executions;
    }

    double avgScore() {
        return executions == 0 ? 0 : (double) totalScore / executions;
    }
}
