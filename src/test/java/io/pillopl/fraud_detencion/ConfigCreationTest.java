package io.pillopl.fraud_detencion;

import org.junit.jupiter.api.Test;

import java.util.*;

import static io.pillopl.fraud_detencion.RuleDependency.Type.NeedsData;
import static org.junit.jupiter.api.Assertions.*;

class ConfigCreationTest {

    @Test
    void testNoQueryRulesAreIncluded() {
        // Given
        RequestedRule<String> ruleWithoutQuery = RequestedRuleBuilder.<String>create()
                .id("R1")
                .scoreCheck(params -> new Score(10))
                .build();
        RulesRequest request = new RulesRequest(Set.of(ruleWithoutQuery));

        // When
        RulesConfig result = request.createConfig();

        // Then
        assertEquals(1, result.rules().size());
        assertEquals("R1", result.rules().iterator().next().id());
        assertTrue(result.rules().iterator().next() instanceof NonQueriedRule);
    }

    @Test
    void testGroupBySameDataSourceAndView() {
        // Given
        RequestedRule<String> r1 = RequestedRuleBuilder.<String>create()
                .id("A")
                .query(() -> "data", "db1", "view1")
                .scoreCheck(data -> new Score(10))
                .build();
        RequestedRule<String> r2 = RequestedRuleBuilder.<String>create()
                .id("B")
                .query(() -> "data2", "db1", "view1")
                .scoreCheck(data -> new Score(15))
                .build();
        RulesRequest request = new RulesRequest(Set.of(r1, r2));

        // When
        RulesConfig config = request.createConfig();

        // Then
        assertEquals(1, config.rules().size());
        assertTrue(config.rules().iterator().next() instanceof SingleSourceQueriedRule<?>);
    }

    @Test
    void testDependentRulesAreGrouped() {
        // Given
        RequestedRule<String> parent = RequestedRuleBuilder.<String>create()
                .id("P")
                .query(() -> "data", "redis", "transactions")
                .scoreCheck(data -> new Score(10))
                .build();
        RequestedRule<String> child = RequestedRuleBuilder.<String>create()
                .id("C")
                .query(() -> "data", "clickhouse", "somewhere")
                .scoreCheck(data -> new Score(12))
                .dependsOn("P", NeedsData)
                .build();
        RulesRequest request = new RulesRequest(Set.of(parent, child));

        // When
        RulesConfig config = request.createConfig();

        // Then
        assertEquals(1, config.rules().size());
        assertTrue(config.rules().iterator().next() instanceof DependentRule<?, ?>);
    }

    @Test
    void testRemainingRulesAreQueriedRules() {
        // Given
        RequestedRule<String> solo = RequestedRuleBuilder.<String>create()
                .id("X")
                .query(() -> "data", "dbX", "vX")
                .scoreCheck(data -> new Score(10))
                .build();
        RulesRequest request = new RulesRequest(Set.of(solo));

        // When
        RulesConfig config = request.createConfig();

        // Then
        assertEquals(1, config.rules().size());
        assertTrue(config.rules().iterator().next() instanceof QueriedRule<?>);
    }

    @Test
    void testInvalidGraphReturnsEmptyRules() {
        // Given
        ScoreCheck<String> check = data -> new Score(10);

        RequestedRule<String> a = RequestedRuleBuilder.<String>create()
                .id("A")
                .query(() -> "data", "db1", "view1")
                .scoreCheck(check)
                .dependsOn("B", NeedsData)
                .build();

        RequestedRule<String> b = RequestedRuleBuilder.<String>create()
                .id("B")
                .query(() -> "data", "db1", "view1")
                .scoreCheck(check)
                .dependsOn("A", NeedsData)
                .build();

        RulesRequest request = new RulesRequest(Set.of(a, b));

        // When
        RulesConfig config = request.createConfig();

        // Then
        assertTrue(config.rules().isEmpty());
    }

    @Test
    void testComplexIntegrationScenario() {
        // Given:

        // clickhouse: CH1, CH2 → SingleSourceQueriedRule
        RequestedRule<String> ch1 = RequestedRuleBuilder.<String>create()
                .id("CH1")
                .query(() -> "data ch", "clickhouse", "users_view")
                .scoreCheck(d -> new Score(42))
                .build();

        RequestedRule<String> ch2 = RequestedRuleBuilder.<String>create()
                .id("CH2")
                .query(() -> "data ch", "clickhouse", "users_view")
                .scoreCheck(d -> new Score(42))
                .build();

        // redis: R1, R2 → DependentRule (R2 depends on R1)
        RequestedRule<String> r1 = RequestedRuleBuilder.<String>create()
                .id("R1")
                .query(() -> "data redis", "redis", "active_view")
                .scoreCheck(d -> new Score(42))
                .build();

        RequestedRule<String> r2 = RequestedRuleBuilder.<String>create()
                .id("R2")
                .query(() -> "data redis", "redis", "transactions")
                .scoreCheck(d -> new Score(42))
                .dependsOn("R1", NeedsData)
                .build();

        // mongo rule
        RequestedRule<String> solo = RequestedRuleBuilder.<String>create()
                .id("SOLO")
                .query(() -> "orphan", "mongo", "audit_log")
                .scoreCheck(d -> new Score(42))
                .build();

        // non query rule
        RequestedRule<String> noQueryRule = RequestedRuleBuilder.<String>create()
                .id("X")
                .scoreCheck(d -> new Score(42))
                .build();

        RulesRequest request = new RulesRequest(Set.of(ch1, ch2, r1, r2, solo, noQueryRule));

        // When
        RulesConfig config = request.createConfig();

        // Then
        List<Rule> result = config.rules();
        assertEquals(4, result.size());

        assertTrue(result.stream().anyMatch(r -> r instanceof SingleSourceQueriedRule<?>),
                "Should contain grouped clickhouse rule");

        assertTrue(result.stream().anyMatch(r -> r instanceof DependentRule<?, ?>),
                "Should contain dependent redis rule");

        assertTrue(result.stream().anyMatch(r -> r instanceof QueriedRule<?>),
                "Should contain solo mongo rule");

        assertTrue(result.stream().anyMatch(r -> r instanceof NonQueriedRule),
                "Rule without query should be taken into account");
    }
}
