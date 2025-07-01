package io.pillopl.fraud_detencion;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimulationTest {

    @Test
    void wouldHaveBeenDifferentIfStrategyChanged() {
        // Given
        Rule r1 = new TestRule("R1");
        Rule r2 = new TestRule("R2");
        Rule r3 = new TestRule("R3");

        Map<String, RuleStats> map = Map.of(
                r1.id(), stats(150, 10, 99, r1),
                r2.id(), stats(150, 20, 20, r2),
                r3.id(), stats(200, 33, 4, r3)
        );
        RulesStats stats = new RulesStats(map);

        // When
        SimulationResult ifTimeIsMainFactor = Simulation.of(List.of(r1, r2, r3))
                .and(new ExecutionStrategy(0, 10, 0)) // sort by time
                .after(Duration.ofMillis(200))
                .andParallelizationOf(2)
                .runWith(stats);

        SimulationResult ifScoreIsMainFactor = Simulation.of(List.of(r1, r2, r3))
                .and(new ExecutionStrategy(0, 0, 10)) // sort by score
                .after(Duration.ofMillis(200))
                .andParallelizationOf(2)
                .runWith(stats);


        // Then
        assertEquals(30, ifTimeIsMainFactor.totalScore()); // 10 + 20
        assertEquals(119, ifTimeIsMainFactor.totalCost());

        assertEquals(53, ifScoreIsMainFactor.totalScore()); // 33+20
        assertEquals(24, ifScoreIsMainFactor.totalCost());

    }

    @Test
    void ruleExceedsDeadlineDueToThreadWait() {
        // Given
        Rule a = new TestRule("A");
        Rule b = new TestRule("B");
        Rule c = new TestRule("C");

        RulesStats stats = new RulesStats(Map.of(
                a.id(), stats(300, 10, a),
                b.id(), stats(300, 20, b),
                c.id(), stats(300, 30, c)
        ));

        // When
        int total = Simulation.of(List.of(a, b, c))
                .and(new ExecutionStrategy(0, 10, 0))
                .after(Duration.ofMillis(500))
                .andParallelizationOf(2)
                .runWith(stats).totalScore();

        // Then
        // A + B only
        assertEquals(30, total);
    }


    @Test
    void singleThreadExecutesSequentially() {
        // Given
        Rule x = new TestRule("X");
        Rule y = new TestRule("Y");
        Rule z = new TestRule("Z");

        RulesStats stats = new RulesStats(Map.of(
                x.id(), stats(100, 5, x),
                y.id(), stats(100, 6, y),
                z.id(), stats(100, 7, z)
        ));

        // When
        int total = Simulation.of(List.of(x, y, z))
                .and(new ExecutionStrategy(0, 1, 0))
                .after(Duration.ofMillis(250))
                .andParallelizationOf(1)
                .runWith(stats).totalScore();

        // Then
        // X: 0-100, Y: 100-200, Z: 200-300 (too late)
        assertEquals(11, total); // X + Y
    }

    @Test
    void scoreDefaultsToZeroWhenMissingStats() {
        Rule r1 = new TestRule("R1");

        RulesStats stats = RulesStats.empty();

        int total = Simulation.of(List.of(r1))
                .and(new ExecutionStrategy(0, 1, 0))
                .after(Duration.ofMillis(200))
                .andParallelizationOf(1)
                .runWith(stats).totalScore();

        assertEquals(0, total);
    }

    RuleStats stats(long duration, int score, Rule rule) {
        return stats(duration, score, 0, rule);
    }

    RuleStats stats(long duration, int score, int cost, Rule rule) {
        RuleStats rs = new RuleStats(rule.id());
        return rs.update(new RuleExecution(rule.id(), Duration.ofMillis(duration), Score.of(score), cost));
    }

}