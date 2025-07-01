package io.pillopl.fraud_detencion;

import java.time.Duration;
import java.util.*;

import static io.pillopl.fraud_detencion.ExecutionConfig.SortingAlgorithm.Greedy;

//na tym etapie Rule jest nierozrywalny (pogrupowany w SingleSource Albo w DependentRule)
record ExecutionPlan(List<Rule> independentRules) {
}


record ExecutionStrategy(int costFactor,
                         int timeFactor,
                         int scoreFactor) {
}


class ExecutionConfig {

    enum SortingAlgorithm {
        Knapsack, ML, Greedy
    }

    private ExecutionStrategy executionStrategy;
    private RulesConfig config;
    private RulesStats rulesStats = RulesStats.empty();
    private SortingAlgorithm sortingAlgorithm = Greedy;


    ExecutionConfig(ExecutionStrategy executionStrategy, RulesConfig config, RulesStats rulesStats) {
        this.executionStrategy = executionStrategy;
        this.config = config;
        this.rulesStats = rulesStats;
    }

    void handle(RulesExecuted event) {
        event.ruleExecutions().forEach(ruleExecution -> {
            rulesStats.update(ruleExecution);
        });
    }

    ExecutionPlan calculatePlan() {
        List<Rule> independentRules = switch (sortingAlgorithm) {
            case Knapsack -> null;
            case ML -> null;
            case Greedy -> applyGreedyHeuristic();
        };
        return new ExecutionPlan(independentRules);
    }

    private List<Rule> applyGreedyHeuristic() {
        List<Rule> rules = config.rules().stream().toList();
        return rules.stream()
                .sorted(Comparator.comparingDouble(rule -> {
                    RuleStats stat = rulesStats.get(rule.id()).orElse(new RuleStats(rule.id()));
                    return executionStrategy.costFactor() * stat.avgCost()
                            + executionStrategy.timeFactor() * stat.avgDurationMillis()
                            - executionStrategy.scoreFactor() * stat.avgScore();
                }))
                .toList();
    }


}

class Simulation {

    private RulesConfig rules;
    private ExecutionStrategy strategy;
    private Duration deadline;
    private int parallelism;

    private Simulation(List<Rule> rules) {
        this.rules = new RulesConfig(rules);
    }

    public static Simulation of(List<Rule> rules) {
        return new Simulation(rules);
    }

    public Simulation and(ExecutionStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    public Simulation after(Duration deadline) {
        this.deadline = deadline;
        return this;
    }

    public Simulation andParallelizationOf(int parallelism) {
        this.parallelism = parallelism;
        return this;
    }

    public SimulationResult runWith(RulesStats stats) {
        ExecutionConfig executionConfig = new ExecutionConfig(strategy, rules, stats);
        ExecutionPlan executionPlan = executionConfig.calculatePlan();
        PriorityQueue<Double> threads = new PriorityQueue<>(parallelism); // holds finish times
        int totalScore = 0;
        int totalCost = 0;

        for (Rule entry : executionPlan.independentRules()) {
            double duration = stats.get(entry.id()).map(s -> s.avgDurationMillis()).orElse(0d);
            double score = stats.get(entry.id()).map(s -> s.avgScore()).orElse(0d);
            double cost =  stats.get(entry.id()).map(s -> s.avgCost()).orElse(0d);


            // find the earliest available thread (or use now if threads < parallelism)
            double startAt;
            if (threads.size() < parallelism) {
                startAt = 0;
            } else {
                startAt = threads.poll();
            }

            double finishAt = startAt + duration;

            if (finishAt <= deadline.toMillis()) {
                totalScore += score;
                totalCost += cost;
                threads.add(finishAt); // mark thread as busy until finishAt
            } else {
                break; // can't finish in time
            }
        }

        return new SimulationResult(totalScore, totalCost);
    }
}

record SimulationResult(int totalScore, int totalCost) {}

