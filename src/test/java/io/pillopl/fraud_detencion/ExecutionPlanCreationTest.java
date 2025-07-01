package io.pillopl.fraud_detencion;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.pillopl.fraud_detencion.RuleExecutionBuilder.executionOf;
import static io.pillopl.fraud_detencion.RulesStats.empty;
import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionPlanCreationTest {

    @Test
    void testGreedySortByCost() {
        // Given
        Rule a = new TestRule("A");
        Rule b = new TestRule("B");

        ExecutionConfig config = new ExecutionConfig(new ExecutionStrategy(1, 0, 0), new RulesConfig(List.of(a, b)), empty());

        config.handle(new RulesExecuted(List.of(
                executionOf("A").took(ofMillis(300)).scored(new Score(5)).costed(50).build(),
                executionOf("B").took(ofMillis(100)).scored(new Score(5)).costed(5).build()
        )));

        // When
        List<Rule> sorted = config.calculatePlan().independentRules();

        // Then
        assertEquals(List.of(b, a), sorted);
    }

    @Test
    void testGreedySortByTime() {
        // Given
        Rule x = new TestRule("X");
        Rule y = new TestRule("Y");
        ExecutionConfig config = new ExecutionConfig(new ExecutionStrategy(0, 1, 0), new RulesConfig(List.of(x, y)), empty());

        // And
        config.handle(new RulesExecuted(List.of(
                executionOf("X").took(ofMillis(300)).scored(new Score(5)).costed(5).build(),
                executionOf("Y").took(ofMillis(100)).scored(new Score(5)).costed(5).build()
        )));

        // When
        List<Rule> sorted = config.calculatePlan().independentRules();

        // Then
        assertEquals(List.of(y, x), sorted); // Y faster than X
    }

    @Test
    void testGreedySortByScoreDescending() {
        // Given
        Rule x = new TestRule("X");
        Rule y = new TestRule("Y");

        ExecutionConfig config = new ExecutionConfig(new ExecutionStrategy(0, 0, 1), new RulesConfig(List.of(x, y)), empty());

        // And
        config.handle(new RulesExecuted(List.of(
                executionOf("X").took(ofMillis(300)).scored(new Score(50)).costed(5).build(),
                executionOf("Y").took(ofMillis(100)).scored(new Score(5)).costed(5).build()
        )));

        // When
        List<Rule> sorted = config.calculatePlan().independentRules();

        // Then
        assertEquals(List.of(x, y), sorted);
    }

    @Test
    void testFullGreedySortWithAllFactors() {
        // Given
        Rule a = new TestRule("A");
        Rule b = new TestRule("B");
        Rule c = new TestRule("C");
        Rule d = new TestRule("D");
        Rule e = new TestRule("E");
        Rule f = new TestRule("F");

        ExecutionConfig config = new ExecutionConfig(new ExecutionStrategy(2, 1, 3), new RulesConfig(List.of(a, b, c, d, e, f)), empty());

        // And
        config.handle(new RulesExecuted(List.of(
                executionOf("A").costed(10).took(Duration.ofMillis(300)).scored(new Score(5)).build(),
                executionOf("B").costed(20).took(Duration.ofMillis(100)).scored(new Score(15)).build(),
                executionOf("C").costed(5).took(Duration.ofMillis(500)).scored(new Score(5)).build(),
                executionOf("D").costed(15).took(Duration.ofMillis(150)).scored(new Score(7)).build(),
                executionOf("E").costed(12).took(Duration.ofMillis(200)).scored(new Score(8)).build(),
                executionOf("F").costed(8).took(Duration.ofMillis(120)).scored(new Score(12)).build()
        )));

        // When
        List<Rule> sorted = config.calculatePlan().independentRules();

        // A: 2*10 + 300 - 3*5 = 320
        // B: 2*20 + 100 - 3*15 = 100
        // C: 2*5 + 500 - 3*2 = 506
        // D: 2*15 + 150 - 3*7 = 164
        // E: 2*12 + 200 - 3*8 = 188
        // F: 2*8 + 120 - 3*12 = 112

        // Then
        List<String> expectedOrder = List.of("B", "F", "D", "E", "A", "C");
        List<String> actualOrder = sorted.stream().map(Rule::id).toList();
        assertEquals(expectedOrder, actualOrder);
    }
}

record TestRule(String id) implements Rule {

    @Override
    public Score calculate(Map<String, String> params) {
        return null;
    }

    @Override
    public String id() {
        return id;
    }
}

class RuleExecutionBuilder {
    private final String id;
    private Duration duration = Duration.ZERO;
    private Score score = new Score(0);
    private int cost = 0;

    private RuleExecutionBuilder(String id) {
        this.id = id;
    }

    public static RuleExecutionBuilder executionOf(String id) {
        return new RuleExecutionBuilder(id);
    }

    public RuleExecutionBuilder took(Duration duration) {
        this.duration = duration;
        return this;
    }

    public RuleExecutionBuilder scored(Score score) {
        this.score = score;
        return this;
    }

    public RuleExecutionBuilder costed(int cost) {
        this.cost = cost;
        return this;
    }

    public RuleExecution build() {
        return new RuleExecution(id, duration, score, cost);
    }
}