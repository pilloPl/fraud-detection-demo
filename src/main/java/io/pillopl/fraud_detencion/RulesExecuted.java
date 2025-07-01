package io.pillopl.fraud_detencion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

record RulesExecuted(Instant when, List<RuleExecution> ruleExecutions) {
    public RulesExecuted(List<RuleExecution> build) {
        this(Instant.now(), build);
    }
}

record RuleExecution(String id, Duration duration, Score score, int cost) {

}